#!/usr/bin/env python3
"""Extract local documents to source segments, not flashcards. PDF optionally uses pypdf."""
import argparse
import codecs
from html.parser import HTMLParser
import importlib
import io
import json
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET
import zipfile

MAX_BYTES = 32 * 1024 * 1024
W = "{http://schemas.openxmlformats.org/wordprocessingml/2006/main}"


def decode(raw, encoding=None, html=False):
    if encoding:
        return raw.decode(encoding, errors="strict"), encoding
    if raw.startswith((codecs.BOM_UTF32_LE, codecs.BOM_UTF32_BE)):
        return raw.decode("utf-32"), "utf-32"
    if raw.startswith((codecs.BOM_UTF16_LE, codecs.BOM_UTF16_BE)):
        return raw.decode("utf-16"), "utf-16"
    if html:
        match = re.search(br'<meta\b[^>]*charset\s*=\s*["\x27]?\s*([\w-]+)', raw[:8192], re.I)
        if match:
            name = match.group(1).decode("ascii")
            return raw.decode(name, errors="strict"), name
    return raw.decode("utf-8-sig", errors="strict"), "utf-8"


class HTMLText(HTMLParser):
    BLOCKS = {"p", "div", "section", "article", "li", "h1", "h2", "h3", "h4", "h5", "h6", "tr", "blockquote", "pre", "br", "hr"}
    VOID = {"area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta", "param", "source", "track", "wbr"}

    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.stack = []
        self.parts = []

    def hidden(self):
        return any(hidden for _, hidden in self.stack)

    def handle_starttag(self, tag, attrs):
        attributes = dict(attrs)
        hide = tag in {"script", "style", "head", "template", "noscript", "iframe", "object"} or "hidden" in attributes or attributes.get("aria-hidden") == "true"
        if not self.hidden() and not hide:
            if tag in self.BLOCKS:
                self.parts.append("\n")
            elif tag in {"td", "th"}:
                self.parts.append("\t")
        if tag not in self.VOID:
            self.stack.append((tag, hide))

    def handle_startendtag(self, tag, attrs):
        self.handle_starttag(tag, attrs)
        if tag not in self.VOID:
            self.handle_endtag(tag)

    def handle_endtag(self, tag):
        if not self.hidden() and tag in self.BLOCKS:
            self.parts.append("\n")
        for index in range(len(self.stack) - 1, -1, -1):
            if self.stack[index][0] == tag:
                del self.stack[index:]
                break

    def handle_data(self, data):
        if not self.hidden():
            self.parts.append(data if any(tag == "pre" for tag, _ in self.stack) else re.sub(r"\s+", " ", data))


def docx(raw):
    segments = []
    with zipfile.ZipFile(io.BytesIO(raw)) as archive:
        infos = archive.infolist()
        if len(infos) > 10000 or sum(x.file_size for x in infos) > 64 * 1024 * 1024:
            raise ValueError("DOCX exceeds expanded-size/member limit")
        xml = archive.read("word/document.xml")
        source, _ = decode(xml)
        if "<!DOCTYPE" in source.upper() or "<!ENTITY" in source.upper():
            raise ValueError("DOCX XML declarations of entities/DTD are not supported")
        root = ET.fromstring(source)
        body = root.find(W + "body")
        if body is None:
            raise ValueError("DOCX body missing")
        def inline(element):
            parts = []
            for node in element.iter():
                if node.tag == W + "t":
                    parts.append(node.text or "")
                elif node.tag == W + "tab":
                    parts.append("\t")
                elif node.tag in {W + "br", W + "cr"}:
                    parts.append("\n")
            return "".join(parts)
        paragraph = table = 0
        for child in body:
            if child.tag == W + "p":
                paragraph += 1
                segments.append({"locator": "paragraph:" + str(paragraph), "text": inline(child)})
            elif child.tag == W + "tbl":
                table += 1
                for row_index, row in enumerate(child.findall(W + "tr"), 1):
                    cells = ["\n".join(inline(p) for p in cell.findall(W + "p")) for cell in row.findall(W + "tc")]
                    segments.append({"locator": "table:%d/row:%d" % (table, row_index), "text": "\t".join(cells)})
    return segments


def pdf(raw):
    try:
        library = importlib.import_module("pypdf")
    except ImportError as error:
        raise ValueError("PDF_DEPENDENCY_MISSING: use an available Agent PDF reader, or install optional pypdf with user authorization; scanned pages need OCR") from error
    try:
        reader = library.PdfReader(io.BytesIO(raw))
        if reader.is_encrypted:
            raise ValueError("encrypted PDF: provide an authorized decrypted copy")
        if len(reader.pages) > 1000:
            raise ValueError("PDF exceeds 1000-page limit; split the document")
        segments = [{"locator": "page:" + str(index), "text": page.extract_text() or ""}
                    for index, page in enumerate(reader.pages, 1)]
    except ValueError:
        raise
    except Exception as error:
        raise ValueError("PDF extraction failed: " + str(error)) from error
    return segments


def extract(path, encoding=None):
    path = Path(path)
    kind = path.suffix.lower().lstrip(".")
    if kind not in {"txt", "md", "markdown", "html", "htm", "docx", "pdf"}:
        raise ValueError("unsupported format: " + kind + "; use a local converter/Agent reader or provide text")
    if path.stat().st_size > MAX_BYTES:
        raise ValueError("input exceeds 32 MiB; split the document")
    with path.open("rb") as stream:
        raw = stream.read(MAX_BYTES + 1)
    if len(raw) > MAX_BYTES:
        raise ValueError("input exceeds 32 MiB")
    warnings = []
    if kind in {"docx", "pdf"}:
        if encoding:
            raise ValueError("--encoding is for text/HTML, not DOCX/PDF")
        if kind == "docx":
            segments = docx(raw)
            warnings.append("DOCX body paragraphs and simple table rows only; headers, footers, footnotes, comments, images, equations, tracked changes and embedded objects are not fully represented")
            extractor = "stdlib-ooxml"
        else:
            if not raw.startswith(b"%PDF-"):
                raise ValueError("file does not have a PDF signature")
            segments = pdf(raw)
            warnings.append("PDF text layer only: verify column order, formulas, tables, charts and OCR accuracy against source")
            extractor = "pypdf"
    else:
        source, used_encoding = decode(raw, encoding, html=kind in {"html", "htm"})
        if "\0" in source:
            raise ValueError("binary/NUL text: verify format or specify the correct --encoding")
        source = source.replace("\r\n", "\n").replace("\r", "\n")
        if kind in {"html", "htm"}:
            parser = HTMLText()
            parser.feed(source)
            parser.close()
            source = "".join(parser.parts)
            warnings.append("HTML static text only: no JavaScript/CSS execution or remote fetching; CSS visibility, layout and images require source review")
        segments = [{"locator": "line:" + str(index), "text": line}
                    for index, line in enumerate(source.splitlines(), 1)]
        extractor = "stdlib/" + used_encoding
    empty = [s["locator"] for s in segments if not s["text"].strip()]
    if not any(s["text"].strip() for s in segments):
        raise ValueError("NO_TEXT: document has no extracted text; PDF scans/images require OCR or a readable source")
    coverage = "partial" if kind == "pdf" and empty else "text-only" if kind in {"pdf", "docx", "html", "htm"} else "text"
    if kind == "pdf" and empty:
        warnings.append("Pages without text (blank, scanned or unsupported): " + ", ".join(empty))
    return {"source": path.name, "format": kind, "extractor": extractor,
            "coverage": coverage, "segments": segments, "warnings": warnings}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("input")
    parser.add_argument("output", help="new intermediate JSON file; not a LumeCard import file")
    parser.add_argument("--encoding", help="explicit text encoding, for example gb18030")
    args = parser.parse_args()
    try:
        result = extract(args.input, args.encoding)
        with Path(args.output).open("x", encoding="utf-8", newline="\n") as stream:
            json.dump(result, stream, ensure_ascii=False, indent=2)
            stream.write("\n")
        print(json.dumps({"format": result["format"], "segments": len(result["segments"]), "coverage": result["coverage"], "warnings": result["warnings"]}, ensure_ascii=False))
        return 0
    except (OSError, ValueError, LookupError, zipfile.BadZipFile, ET.ParseError, KeyError, RuntimeError) as error:
        print("error: " + str(error), file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main())
