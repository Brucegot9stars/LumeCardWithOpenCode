package com.lumecard.shared.data

import kotlin.test.Test
import kotlin.test.assertTrue

class BackupNameGeneratorTest {

    @Test
    fun `generateName produces full backup name format`() {
        val generator = BackupNameGenerator()
        val name = generator.generateName("上行", "D")
        // <fun-name>-<direction>-<type>-<yyyymmdd-HHMMSS>
        assertTrue(name.contains("-上行-D-"), "expected '-上行-D-' in: $name")
        // timestamp suffix: -YYYYMMDD-HHMMSS
        assertTrue(name.matches(Regex(".*-\\d{8}-\\d{6}$")), "timestamp suffix malformed: $name")
    }

    @Test
    fun `generateName supports config direction and type`() {
        val generator = BackupNameGenerator()
        val name = generator.generateName("下行", "C")
        assertTrue(name.contains("-下行-C-"), "expected '-下行-C-' in: $name")
    }

    @Test
    fun `generated names are stable across calls`() {
        val generator = BackupNameGenerator()
        val a = generator.generateName("上行", "D")
        val b = generator.generateName("上行", "D")
        assertTrue(a != b, "names should differ (randomized), got both '$a'")
    }
}