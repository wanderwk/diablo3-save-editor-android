package com.wanderwk.d3saveeditor.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * Regression test for a real "save recognized as old/invalid by the game after
 * editing" bug report (2026-08-08). Root cause: `Hero.Digest.level` (hero.dat
 * field 2 -> field 5) is SINT32 (zigzag-encoded) per the real `Hero.proto`
 * ("required sint32 level = 5") -- but `writeHeroLevel` wrote whatever level the
 * user picked as a *plain* varint. A real protobuf sint32 field is always
 * zigzag-decoded regardless of how it was written, so e.g. setting level=70
 * would actually load in-game as level 35. Fixed by zigzag-encoding on write and
 * decoding on read (field 25, highest_solo_rift_completed, is plain uint32 and
 * must stay untouched -- covered below too, so the two don't get conflated).
 */
class ParagonRepositoryTest {

    private fun realZigzagDecode(v: Long): Long {
        val n = v.toInt()
        return ((n ushr 1) xor -(n and 1)).toLong()
    }

    private fun newHeroFile(): File {
        val digest = serializeFields(listOf(PField(3, 2, "Hero".toByteArray())))
        val top = serializeFields(listOf(PField(2, 2, digest)))
        val f = File.createTempFile("regression_hero_level", ".dat")
        f.deleteOnExit()
        f.writeBytes(SaveCipher.encrypt(top))
        return f
    }

    @Test
    fun `writeHeroLevel writes a level that a real sint32 parser decodes back exactly`() {
        val heroFile = newHeroFile()
        ParagonRepository.writeHeroLevel(heroFile, 70)

        val digestField = parseFields(SaveCipher.decrypt(heroFile.readBytes()))
            .first { it.fieldNumber == 2 && it.wireType == 2 }
        val levelField = parseFields(digestField.bytesValue()).first { it.fieldNumber == 5 && it.wireType == 0 }

        assertEquals(70L, realZigzagDecode(levelField.longValue()))
        assertEquals(70, ParagonRepository.readHeroInfo(heroFile).level)
    }

    @Test
    fun `writeHeroHighestRift stays a plain uint32, unaffected by the zigzag fix`() {
        val heroFile = newHeroFile()
        ParagonRepository.writeHeroHighestRift(heroFile, 45)

        val digestField = parseFields(SaveCipher.decrypt(heroFile.readBytes()))
            .first { it.fieldNumber == 2 && it.wireType == 2 }
        val riftField = parseFields(digestField.bytesValue()).first { it.fieldNumber == 25 && it.wireType == 0 }

        // Plain uint32 -- the raw wire value IS 45, no zigzag transform.
        assertEquals(45L, riftField.longValue())
        assertEquals(45, ParagonRepository.readHeroInfo(heroFile).highestRift)
    }

    @Test
    fun `writing level and highest rift independently do not corrupt one another`() {
        val heroFile = newHeroFile()
        ParagonRepository.writeHeroLevel(heroFile, 70)
        ParagonRepository.writeHeroHighestRift(heroFile, 45)

        val info = ParagonRepository.readHeroInfo(heroFile)
        assertEquals(70, info.level)
        assertEquals(45, info.highestRift)
    }
}
