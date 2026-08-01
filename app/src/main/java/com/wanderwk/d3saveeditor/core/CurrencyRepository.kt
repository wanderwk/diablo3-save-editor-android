package com.wanderwk.d3saveeditor.core

import java.io.File

/**
 * Reads/writes account.dat currencies.
 *
 * Structure (ported from account_editor.py, confirmed against real saves):
 *   account.dat
 *     field 20 (message, "account entry")
 *       field 9 (message, "currency list")
 *         field 1 (repeated message, "currency entry")
 *           field 2 (varint) = value
 *           field 3 (varint) = currency id
 */
object CurrencyRepository {

    data class Currency(val name: String, val id: Int, val maxValue: Long)

    val CURRENCIES: List<Currency> = listOf(
        Currency("Gold", 0, 999_999_999L),
        Currency("Blood Shards", 1, 5_000L),
        Currency("Reusable Parts", 3, 999_999L),
        Currency("Arcane Dust", 4, 999_999L),
        Currency("Veiled Crystals", 5, 999_999L),
        Currency("Deaths Breath", 6, 999_999L),
        Currency("Forgotten Souls", 7, 999_999L),
        Currency("Khanduran Runes", 8, 999L),
        Currency("Caldeum Nightshade", 9, 999L),
        Currency("Arreat War Tapestries", 10, 999L),
        Currency("Corrupted Angel Flesh", 11, 999L),
        Currency("Westmarch Holy Water", 12, 999L),
        Currency("Hearts of Fright", 13, 999L),
        Currency("Vials of Putridness", 14, 999L),
        Currency("Idols of Terror", 15, 999L),
        Currency("Leorics Regrets", 16, 999L),
        Currency("Vengeful Eyes", 17, 999L),
        Currency("Writhing Spines", 18, 999L),
        Currency("Devils Fangs", 19, 999L),
    )

    private val NAME_TO_ID = CURRENCIES.associate { it.name to it.id }
    private val ID_TO_NAME = CURRENCIES.associate { it.id to it.name }

    /** Reads all known currency values, defaulting to 0 for any not found. */
    fun readCurrencies(accountFile: File): MutableMap<String, Long> {
        val result = LinkedHashMap<String, Long>()
        CURRENCIES.forEach { result[it.name] = 0L }

        val decrypted = SaveCipher.decrypt(accountFile.readBytes())
        val entry20 = parseFields(decrypted).firstOrNull { it.fieldNumber == 20 && it.wireType == 2 }
            ?: return result
        val list9 = parseFields(entry20.bytesValue()).firstOrNull { it.fieldNumber == 9 && it.wireType == 2 }
            ?: return result

        for (f in parseFields(list9.bytesValue())) {
            if (f.fieldNumber != 1 || f.wireType != 2) continue
            var value: Long? = null
            var currencyId: Int? = null
            for (inner in parseFields(f.bytesValue())) {
                when {
                    inner.fieldNumber == 2 && inner.wireType == 0 -> value = inner.longValue()
                    inner.fieldNumber == 3 && inner.wireType == 0 -> currencyId = inner.longValue().toInt()
                }
            }
            val name = currencyId?.let { ID_TO_NAME[it] }
            if (name != null && value != null) {
                result[name] = value
            }
        }
        return result
    }

    /**
     * Writes a single currency's value back into account.dat, preserving
     * every other byte/field untouched.
     */
    fun writeCurrency(accountFile: File, currencyName: String, newValue: Long): Boolean {
        val currencyId = NAME_TO_ID[currencyName] ?: return false
        val decrypted = SaveCipher.decrypt(accountFile.readBytes())
        val top = parseFields(decrypted)

        val entry20Idx = top.indexOfFirst { it.fieldNumber == 20 && it.wireType == 2 }
        if (entry20Idx < 0) return false
        val entry20Fields = parseFields(top[entry20Idx].bytesValue())

        val list9Idx = entry20Fields.indexOfFirst { it.fieldNumber == 9 && it.wireType == 2 }
        if (list9Idx < 0) return false
        val currencyEntries = parseFields(entry20Fields[list9Idx].bytesValue())

        var found = false
        for (i in currencyEntries.indices) {
            val entry = currencyEntries[i]
            if (entry.fieldNumber != 1 || entry.wireType != 2) continue
            val innerFields = parseFields(entry.bytesValue())
            val idField = innerFields.firstOrNull { it.fieldNumber == 3 && it.wireType == 0 }
            if (idField?.longValue()?.toInt() != currencyId) continue

            val valueIdx = innerFields.indexOfFirst { it.fieldNumber == 2 && it.wireType == 0 }
            if (valueIdx < 0) continue
            innerFields[valueIdx] = PField(2, 0, newValue)
            currencyEntries[i] = PField(1, 2, serializeFields(innerFields))
            found = true
            break
        }
        if (!found) return false

        entry20Fields[list9Idx] = PField(9, 2, serializeFields(currencyEntries))
        top[entry20Idx] = PField(20, 2, serializeFields(entry20Fields))

        accountFile.writeBytes(SaveCipher.encrypt(serializeFields(top)))
        return true
    }
}
