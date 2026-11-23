package com.lumecard.shared.data

/**
 * The single definition of "a media path we are willing to store".
 *
 * Three places used to answer this question differently (export validation, orphan cleanup, and
 * the archive writer), which meant a crafted file could pass `validate` with `C:/Windows/x` or
 * `a\..\b` and only be rejected after the row was already written — leaving a card that points at
 * a file the app will never package, load, or report as missing.
 *
 * Rules: media-root relative, `/`-separated, no empty/`.`/`..` segments, no scheme, no absolute
 * path, no drive letter, no backslash, no control characters, no Windows-reserved names, no
 * trailing dot/space (Win32 would silently rewrite the name), and bounded length.
 */
object MediaPathRules {
    private const val MAX_SEGMENT_LENGTH = 128
    private const val MAX_PATH_LENGTH = 255

    // Windows also reserves the superscript spellings (COM¹, COM², COM³, LPT¹ …): writing to one
    // addresses the device, not a file, so such a name can never be archived or loaded back.
    private val WINDOWS_RESERVED = Regex(
        "^(CON|PRN|AUX|NUL|COM[1-9¹²³]|LPT[1-9¹²³])(\\..*)?$",
        RegexOption.IGNORE_CASE,
    )

    fun isSafeRelativePath(path: String): Boolean {
        if (path.isBlank() || path.length > MAX_PATH_LENGTH) return false
        if (path.any { it.code < 32 }) return false
        if (path.contains('\\')) return false
        if (path.startsWith("/")) return false
        // Panes such as `C:` are absolute URIs to the platform, never media-root relative.
        if (path.length >= 2 && path[1] == ':' && path[0].isLetter()) return false

        val lower = path.lowercase()
        if (lower.startsWith("http://") || lower.startsWith("https://") ||
            lower.startsWith("file://") || lower.startsWith("content://") ||
            lower.startsWith("data:")
        ) return false

        val segments = path.split('/')
        if (segments.any { it.isEmpty() || it == "." || it == ".." }) return false
        if (segments.any { it.length > MAX_SEGMENT_LENGTH }) return false
        // Win32 strips these, so `a.png.` and `a.png` address the same file on Windows.
        if (segments.any { it.endsWith('.') || it.endsWith(' ') }) return false
        // `:` also enables NTFS alternate data streams (`a.png:hidden`).
        if (segments.any { it.contains(':') }) return false
        if (segments.any { WINDOWS_RESERVED.matches(it) }) return false
        return true
    }
}
