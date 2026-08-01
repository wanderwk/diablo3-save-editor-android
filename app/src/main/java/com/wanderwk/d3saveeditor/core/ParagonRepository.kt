package com.wanderwk.d3saveeditor.core

import java.io.File

/**
 * Paragon level (account.dat field 21 -> field 1) and per-hero level / name
 * / highest Greater Rift (hero.dat field 2 -> fields 3/4/5/25).
 * Ported from paragon_editor.py.
 */
object ParagonRepository {

    const val PARAGON_XP_PER_LEVEL = 640_000L
    const val PARAGON_MAX = 10_000
    const val GREATER_RIFT_MAX = 150
    const val HERO_LEVEL_MAX = 70

    fun paragonLevelToXp(level: Int): Long = level.coerceAtLeast(0) * PARAGON_XP_PER_LEVEL

    fun readParagonLevel(accountFile: File): Int {
        val decrypted = SaveCipher.decrypt(accountFile.readBytes())
        val field21 = parseFields(decrypted).firstOrNull { it.fieldNumber == 21 && it.wireType == 2 }
            ?: return 0
        val inner = parseFields(field21.bytesValue())
        return inner.firstOrNull { it.fieldNumber == 1 && it.wireType == 0 }?.longValue()?.toInt() ?: 0
    }

    fun writeParagonLevel(accountFile: File, newLevel: Int): Boolean {
        val level = newLevel.coerceIn(0, PARAGON_MAX).toLong()
        val decrypted = SaveCipher.decrypt(accountFile.readBytes())
        val top = parseFields(decrypted)

        val idx = top.indexOfFirst { it.fieldNumber == 21 && it.wireType == 2 }
        if (idx < 0) return false
        val inner = parseFields(top[idx].bytesValue())

        val fieldIdx = inner.indexOfFirst { it.fieldNumber == 1 && it.wireType == 0 }
        if (fieldIdx >= 0) {
            inner[fieldIdx] = PField(1, 0, level)
        } else {
            inner.add(0, PField(1, 0, level))
        }
        top[idx] = PField(21, 2, serializeFields(inner))

        accountFile.writeBytes(SaveCipher.encrypt(serializeFields(top)))
        return true
    }

    data class HeroInfo(
        val name: String,
        val level: Int,
        val classId: Long,
        val highestRift: Int,
    )

    fun readHeroInfo(heroFile: File): HeroInfo {
        val decrypted = SaveCipher.decrypt(heroFile.readBytes())
        val field2 = parseFields(decrypted).firstOrNull { it.fieldNumber == 2 && it.wireType == 2 }
            ?: return HeroInfo("?", 0, 0, 0)
        val inner = parseFields(field2.bytesValue())

        var name = ""
        var level = 0
        var classId = 0L
        var highestRift = 0
        for (f in inner) {
            when {
                f.fieldNumber == 3 && f.wireType == 2 ->
                    name = runCatching { String(f.bytesValue(), Charsets.US_ASCII) }.getOrDefault("")
                f.fieldNumber == 5 && f.wireType == 0 -> level = f.longValue().toInt()
                f.fieldNumber == 4 && f.wireType == 5 -> {
                    val b = f.bytesValue()
                    classId = (b[0].toLong() and 0xFF) or ((b[1].toLong() and 0xFF) shl 8) or
                        ((b[2].toLong() and 0xFF) shl 16) or ((b[3].toLong() and 0xFF) shl 24)
                }
                f.fieldNumber == 25 && f.wireType == 0 -> highestRift = f.longValue().toInt()
            }
        }
        return HeroInfo(name, level, classId, highestRift)
    }

    fun writeHeroLevel(heroFile: File, newLevel: Int): Boolean =
        writeHeroIntField(heroFile, fieldNumber = 5, value = newLevel.coerceIn(1, HERO_LEVEL_MAX).toLong())

    fun writeHeroHighestRift(heroFile: File, newValue: Int): Boolean =
        writeHeroIntField(heroFile, fieldNumber = 25, value = newValue.coerceIn(0, GREATER_RIFT_MAX).toLong())

    private fun writeHeroIntField(heroFile: File, fieldNumber: Int, value: Long): Boolean {
        val decrypted = SaveCipher.decrypt(heroFile.readBytes())
        val top = parseFields(decrypted)

        val idx = top.indexOfFirst { it.fieldNumber == 2 && it.wireType == 2 }
        if (idx < 0) return false
        val inner = parseFields(top[idx].bytesValue())

        val fieldIdx = inner.indexOfFirst { it.fieldNumber == fieldNumber && it.wireType == 0 }
        if (fieldIdx >= 0) {
            inner[fieldIdx] = PField(fieldNumber, 0, value)
        } else {
            inner.add(PField(fieldNumber, 0, value))
        }
        top[idx] = PField(2, 2, serializeFields(inner))

        heroFile.writeBytes(SaveCipher.encrypt(serializeFields(top)))
        return true
    }
}
