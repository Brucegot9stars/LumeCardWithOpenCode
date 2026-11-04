import codecs
import importlib.util
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest import mock
import zipfile

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("extract_document", ROOT / "scripts/extract_document.py")
extractor = importlib.util.module_from_spec(spec)
spec.loader.exec_module(extractor)
HAS_PDF = importlib.util.find_spec("pypdf") is not None


def make_pdf(pages):
    """Small real PDF fixture with page tree, Helvetica and an accurate xref table."""
    objects = [b"<< /Type /Catalog /Pages 2 0 R >>", b"", b"<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>"]
    page_ids = []
    for value in pages:
        page_id = len(objects) + 1
        stream_id = page_id + 1
        page_ids.append(page_id)
        objects.append(("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 3 0 R >> >> /Contents %d 0 R >>" % stream_id).encode())
        stream = b"BT /F1 12 Tf 72 720 Td (" + value.encode("ascii") + b") Tj ET" if value else b""
        objects.append(b"<< /Length " + str(len(stream)).encode() + b" >>\nstream\n" + stream + b"\nendstream")
    objects[1] = ("<< /Type /Pages /Kids [%s] /Count %d >>" % (" ".join("%d 0 R" % x for x in page_ids), len(page_ids))).encode()
    result = bytearray(b"%PDF-1.4\n")
    offsets = [0]
    for index, obj in enumerate(objects, 1):
        offsets.append(len(result))
        result += ("%d 0 obj\n" % index).encode() + obj + b"\nendobj\n"
    xref = len(result)
    result += ("xref\n0 %d\n0000000000 65535 f \n" % len(offsets)).encode()
    for offset in offsets[1:]:
        result += ("%010d 00000 n \n" % offset).encode()
    result += ("trailer\n<< /Size %d /Root 1 0 R >>\nstartxref\n%d\n%%%%EOF\n" % (len(offsets), xref)).encode()
    return bytes(result)


class DocumentTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="document 中文 ")
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)

    def file(self, name, data):
        path = self.root / name
        path.write_bytes(data)
        return path

    def test_markdown_preserves_code_and_lines(self):
        path = self.file("笔记.MD", "# 标题\r\n```python\r\n  x = 1\r\n```".encode())
        result = extractor.extract(path)
        self.assertEqual(result["segments"][2], {"locator": "line:3", "text": "  x = 1"})
        self.assertEqual(result["source"], "笔记.MD")

    def test_bom_and_explicit_encoding(self):
        for encoding in ("utf-8-sig", "utf-16", "utf-32"):
            with self.subTest(encoding=encoding):
                path = self.file("notes.txt", "中文 outline".encode(encoding))
                self.assertEqual(extractor.extract(path)["segments"][0]["text"], "中文 outline")
        path = self.file("gb.txt", "中文".encode("gb18030"))
        with self.assertRaises(UnicodeDecodeError):
            extractor.extract(path)
        self.assertEqual(extractor.extract(path, "gb18030")["segments"][0]["text"], "中文")

    def test_html_text_no_active_content(self):
        source = '<html><head><script>steal()</script></head><body><h1>DNA &amp; RNA</h1><p>遗传<b>信息</b></p><script>upload()</script><style>bad css</style><div hidden>secret</div><pre>  val x = 1\n  x</pre></body></html>'
        result = extractor.extract(self.file("article.html", source.encode()))
        value = "\n".join(x["text"] for x in result["segments"])
        self.assertIn("DNA & RNA", value)
        self.assertIn("遗传信息", value)
        self.assertIn("  val x = 1", value)
        for hidden in ("steal", "upload", "bad css", "secret"):
            self.assertNotIn(hidden, value)
        self.assertEqual(result["coverage"], "text-only")

    def test_html_declared_encoding(self):
        source = '<meta charset="gb18030"><p>知识</p>'.encode("gb18030")
        result = extractor.extract(self.file("article.htm", source))
        self.assertTrue(any("知识" in s["text"] for s in result["segments"]))

    def test_html_optional_end_tags_preserve_visible_siblings(self):
        cases = (
            ("<h1>Lesson</h1><p hidden>secret<p>visible", ["Lesson", "visible"]),
            ("<ul><li hidden>secret<li>visible</ul>", ["visible"]),
            ("<dl><dt hidden>secret<dd>definition<dt>term<dd>explanation</dl>", ["definition", "term", "explanation"]),
            ("<table><tr hidden><td>secret<tr><td>A<td>B</table>", ["A\tB"]),
            ("<head><title>secret</title><body><p>visible", ["visible"]),
            ("<p hidden>secret<div>visible</div>", ["visible"]),
        )
        for source, expected in cases:
            with self.subTest(source=source):
                result = extractor.extract(self.file("optional.html", source.encode()))
                lines = [s["text"].strip() for s in result["segments"] if s["text"].strip()]
                self.assertEqual(lines, expected)

    def test_html_nested_lists_and_inert_content_keep_scope(self):
        source = '<ul><li hidden>secret<ul><li>also secret</ul><li>visible</ul><template><p>private<p>private2</template><p>outside'
        result = extractor.extract(self.file("nested.html", source.encode()))
        lines = [s["text"].strip() for s in result["segments"] if s["text"].strip()]
        self.assertEqual(lines, ["visible", "outside"])

    def test_html_definition_and_list_boundaries(self):
        result = extractor.extract(self.file("blocks.html", b'<ul><li>one<li>two</ul><dl><dt>Term<dd>Definition</dl>'))
        lines = [s["text"].strip() for s in result["segments"] if s["text"].strip()]
        self.assertEqual(lines, ["one", "two", "Term", "Definition"])

    def make_docx(self, xml):
        path = self.root / "教材.docx"
        with zipfile.ZipFile(path, "w") as archive:
            archive.writestr("[Content_Types].xml", '<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/></Types>')
            archive.writestr("_rels/.rels", '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>')
            archive.writestr("word/document.xml", xml)
        return path

    def test_docx_paragraphs_tables_and_runs(self):
        xml = '<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body><w:p><w:r><w:t>中文</w:t></w:r><w:r><w:t>知识</w:t><w:br/><w:t>下一行</w:t></w:r></w:p><w:tbl><w:tr><w:tc><w:p><w:r><w:t>术语</w:t></w:r></w:p></w:tc><w:tc><w:p><w:r><w:t>解释</w:t></w:r></w:p></w:tc></w:tr></w:tbl></w:body></w:document>'
        result = extractor.extract(self.make_docx(xml))
        self.assertEqual(result["segments"], [{"locator": "paragraph:1", "text": "中文知识\n下一行"}, {"locator": "table:1/row:1", "text": "术语\t解释"}])
        self.assertTrue(result["warnings"])

    def test_docx_rejects_dtd_and_corrupt_archives(self):
        with self.assertRaisesRegex(ValueError, "DTD"):
            extractor.extract(self.make_docx('<!DOCTYPE x [<!ENTITY x "bad">]><x/>'))
        with self.assertRaises(zipfile.BadZipFile):
            extractor.extract(self.file("bad.docx", b"not a zip"))

    def test_empty_binary_and_unsupported(self):
        for name, value in (("empty.txt", b" \n"), ("binary.txt", b"\0binary"), ("legacy.doc", b"binary")):
            with self.subTest(name=name), self.assertRaises(ValueError):
                extractor.extract(self.file(name, value))

    def test_size_limit(self):
        with mock.patch.object(extractor, "MAX_BYTES", 3):
            with self.assertRaisesRegex(ValueError, "exceeds"):
                extractor.extract(self.file("large.txt", b"1234"))

    def test_missing_pdf_dependency_is_actionable(self):
        with mock.patch.object(extractor.importlib, "import_module", side_effect=ImportError):
            with self.assertRaisesRegex(ValueError, "PDF_DEPENDENCY_MISSING"):
                extractor.extract(self.file("paper.pdf", make_pdf(["Hello"])))

    @unittest.skipUnless(HAS_PDF, "optional pypdf unavailable; PDF extraction not verified in this run")
    def test_real_pdf_text_and_page_coverage(self):
        result = extractor.extract(self.file("paper.pdf", make_pdf(["JVM runs bytecode", ""])))
        self.assertIn("JVM runs bytecode", result["segments"][0]["text"])
        self.assertEqual(result["segments"][1]["locator"], "page:2")
        self.assertEqual(result["coverage"], "partial")
        self.assertIn("page:2", " ".join(result["warnings"]))

    @unittest.skipUnless(HAS_PDF, "optional pypdf unavailable")
    def test_pdf_without_text_requires_ocr(self):
        with self.assertRaisesRegex(ValueError, "NO_TEXT"):
            extractor.extract(self.file("scan.pdf", make_pdf([""])))

    def test_cli_output_is_intermediate_and_never_overwritten(self):
        source = self.file("outline.txt", b"JVM executes Java bytecode.")
        out = self.root / "extracted.json"
        args = [sys.executable, "-S", str(ROOT / "scripts/extract_document.py"), str(source), str(out)]
        result = subprocess.run(args, capture_output=True)
        self.assertEqual(result.returncode, 0, result.stderr)
        data = json.loads(out.read_text(encoding="utf-8"))
        self.assertNotIn("exportType", data)
        original = out.read_bytes()
        self.assertEqual(subprocess.run(args, capture_output=True).returncode, 2)
        self.assertEqual(out.read_bytes(), original)


if __name__ == "__main__":
    unittest.main()
