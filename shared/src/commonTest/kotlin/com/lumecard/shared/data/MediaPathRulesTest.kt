package com.lumecard.shared.data

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Guards the one definition of a storable media path shared by export, archive and cleanup. */
class MediaPathRulesTest {

    @Test
    fun acceptsAnOrdinaryContentAddressedName() {
        assertTrue(MediaPathRules.isSafeRelativePath("a1b2c3.png"))
        assertTrue(MediaPathRules.isSafeRelativePath("images/a1b2c3.png"))
    }

    @Test
    fun rejectsTraversalAndAbsoluteForms() {
        for (path in listOf("../a.png", "a/../b.png", "/a.png", "C:/a.png", "a\\b.png", "a.png:stream")) {
            assertFalse(MediaPathRules.isSafeRelativePath(path), "must reject $path")
        }
    }

    @Test
    fun enforcesSegmentAndTotalLengthCaps() {
        assertFalse(MediaPathRules.isSafeRelativePath("a".repeat(129) + ".png"))
        assertFalse(
            MediaPathRules.isSafeRelativePath(
                listOf("a".repeat(100), "b".repeat(100), "c".repeat(100) + ".png").joinToString("/"),
            ),
        )
    }

    @Test
    fun rejectsEveryWindowsDeviceNameIncludingSuperscriptDigits() {
        for (path in listOf("CON.png", "com1.png", "COM¹.png", "LPT²", "lpt³.png")) {
            assertFalse(MediaPathRules.isSafeRelativePath(path), "must reject $path")
        }
    }
}
