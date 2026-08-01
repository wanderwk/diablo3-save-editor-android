package com.wanderwk.d3saveeditor.core

import java.io.File
import kotlin.random.Random

/**
 * Hero inventory/equipment (hero.dat field 6) and shared stash
 * (account.dat field 21 -> field 2) item read/write.
 * Ported from item_editor.py.
 *
 * Item entry layout:
 *   field 1 (message) uid: field 1 = player id (const 1), field 2 = serial
 *   field 4 (varint)  const 0
 *   field 5 (varint)  const 544 (world/location constant)
 *   field 6 (varint)  slot
 *   field 7 (varint)  const 0
 *   field 8 (message) blob: field 1 = gbid (varint),
 *                           field 2 (message) sub: field 1 = quality (varint)
 *                                                   field 4 = quality (fixed32, alt encoding)
 *                                                   field 9 = level (varint)
 */
object ItemRepository {

    data class D3Item(
        val index: Int,
        val slot: Int,
        val gbid: Long,
        val quality: Int,
        val level: Int,
        val uidSerial: Long,
    ) {
        val name: String get() = ItemCatalog.lookup(gbid)
        val rarity: String get() = qualityToRarity(quality)
        val slotLabel: String get() = ItemRepository.slotLabel(slot)
    }

    private val EQUIP_SLOTS = mapOf(
        0 to "Cabeça", 1 to "Pescoço", 2 to "Pés", 3 to "Mãos", 4 to "Cinturão",
        5 to "Mão Dir.", 6 to "Mão Esq.", 7 to "Ombro Esq.", 8 to "Ombro Dir.",
        9 to "Pernas", 10 to "Braçadeiras", 11 to "Anel 1", 12 to "Anel 2",
        13 to "Pescoço", 14 to "Arma Principal", 15 to "Arma Secundária", 20 to "Poção",
    )

    fun isEquipSlot(slot: Int) = EQUIP_SLOTS.containsKey(slot)

    fun slotLabel(slot: Int): String {
        EQUIP_SLOTS[slot]?.let { return it }
        if (slot in 16 until 200) return "Inventário #${(slot - 16) / 2 + 1}"
        if (slot >= 200) return "Baú #${(slot - 200) / 2 + 1}"
        return "Slot $slot"
    }

    fun qualityToRarity(quality: Int): String = when (quality) {
        0 -> "normal"
        1 -> "magic"
        2 -> "rare"
        3 -> "legendary"
        4 -> "set"
        5, 6 -> "ancient"
        7 -> "primal"
        else -> "normal"
    }

    // ── Parsing a single item entry ─────────────────────────────────────

    private fun parseItemEntry(entryBytes: ByteArray, idx: Int): D3Item? {
        var slot = -1
        var rawBlob: ByteArray? = null
        var uidSerial = 0L

        for (f in parseFields(entryBytes)) {
            when {
                f.fieldNumber == 6 && f.wireType == 0 -> slot = f.longValue().toInt()
                f.fieldNumber == 8 && f.wireType == 2 -> rawBlob = f.bytesValue()
                f.fieldNumber == 1 && f.wireType == 2 -> {
                    uidSerial = parseFields(f.bytesValue())
                        .firstOrNull { it.fieldNumber == 2 && it.wireType == 0 }
                        ?.longValue() ?: 0L
                }
            }
        }
        val blob = rawBlob ?: return null

        var gbid = 0L
        var quality = 0
        var level = 0
        for (f in parseFields(blob)) {
            when {
                f.fieldNumber == 1 && f.wireType == 0 -> gbid = f.longValue() and 0xFFFFFFFFL
                f.fieldNumber == 2 && f.wireType == 2 -> {
                    for (sub in parseFields(f.bytesValue())) {
                        when {
                            sub.fieldNumber == 1 && sub.wireType == 0 -> quality = sub.longValue().toInt()
                            sub.fieldNumber == 4 && sub.wireType == 5 -> {
                                val b = sub.bytesValue()
                                quality = (b[0].toInt() and 0xFF) or ((b[1].toInt() and 0xFF) shl 8) or
                                    ((b[2].toInt() and 0xFF) shl 16) or ((b[3].toInt() and 0xFF) shl 24)
                            }
                            sub.fieldNumber == 9 && sub.wireType == 0 -> level = sub.longValue().toInt()
                        }
                    }
                }
            }
        }
        return D3Item(idx, slot, gbid, quality, level, uidSerial)
    }

    // ── Hero items ───────────────────────────────────────────────────────

    private data class HeroItemsHandle(
        val topFields: MutableList<PField>,
        val field6Index: Int,
        val itemFields: MutableList<PField>,
    )

    private fun readHeroItemsHandle(heroFile: File): HeroItemsHandle {
        val decrypted = SaveCipher.decrypt(heroFile.readBytes())
        val top = parseFields(decrypted)
        val idx = top.indexOfFirst { it.fieldNumber == 6 && it.wireType == 2 }
        if (idx < 0) return HeroItemsHandle(top, -1, ArrayList())
        return HeroItemsHandle(top, idx, parseFields(top[idx].bytesValue()))
    }

    fun readHeroItems(heroFile: File): List<D3Item> {
        val handle = readHeroItemsHandle(heroFile)
        val result = ArrayList<D3Item>()
        var idx = 0
        for (f in handle.itemFields) {
            if (f.fieldNumber == 1 && f.wireType == 2) {
                parseItemEntry(f.bytesValue(), idx)?.let { result.add(it) }
                idx++
            }
        }
        return result
    }

    private fun writeHeroItemFields(heroFile: File, handle: HeroItemsHandle): Boolean {
        if (handle.field6Index < 0) return false
        handle.topFields[handle.field6Index] = PField(6, 2, serializeFields(handle.itemFields))
        heroFile.writeBytes(SaveCipher.encrypt(serializeFields(handle.topFields)))
        return true
    }

    fun addItemsToHero(heroFile: File, gbid: Long, quantity: Int): Int {
        val handle = readHeroItemsHandle(heroFile)
        if (handle.field6Index < 0) return 0

        val items = readHeroItems(heroFile)
        val used = items.map { it.slot }.toHashSet()
        val freeSlots = findFreeSlots(used, startSlot = 16, count = quantity)
        if (freeSlots.isEmpty()) return 0

        val maxUid = maxUidSerial(items)
        var added = 0
        for (slot in freeSlots) {
            val serial = maxUid + added + 1
            handle.itemFields.add(PField(1, 2, makeItemEntry(serial, slot, gbid)))
            added++
        }
        return if (writeHeroItemFields(heroFile, handle)) added else 0
    }

    fun replaceHeroItemGbid(heroFile: File, itemIndex: Int, newGbid: Long): Boolean {
        val handle = readHeroItemsHandle(heroFile)
        if (handle.field6Index < 0) return false

        var count = 0
        for (i in handle.itemFields.indices) {
            val f = handle.itemFields[i]
            if (f.fieldNumber != 1 || f.wireType != 2) continue
            if (count == itemIndex) {
                val entryFields = parseFields(f.bytesValue())
                val blobIdx = entryFields.indexOfFirst { it.fieldNumber == 8 && it.wireType == 2 }
                if (blobIdx >= 0) {
                    val blobFields = parseFields(entryFields[blobIdx].bytesValue())
                    val gbidIdx = blobFields.indexOfFirst { it.fieldNumber == 1 && it.wireType == 0 }
                    if (gbidIdx >= 0) {
                        blobFields[gbidIdx] = PField(1, 0, newGbid and 0xFFFFFFFFL)
                        entryFields[blobIdx] = PField(8, 2, serializeFields(blobFields))
                    }
                }
                handle.itemFields[i] = PField(1, 2, serializeFields(entryFields))
                return writeHeroItemFields(heroFile, handle)
            }
            count++
        }
        return false
    }

    /**
     * Edits an existing item's level (blob field 2 -> field 9). No real
     * "stack count" field was reverse-engineered for this save format (the
     * reference Python tool doesn't have one either) -- adding N copies of
     * an item creates N separate slot entries (see [addItemsToHero]); this
     * only edits the item's own level field.
     */
    fun updateHeroItemLevel(heroFile: File, itemIndex: Int, newLevel: Int): Boolean {
        val handle = readHeroItemsHandle(heroFile)
        if (handle.field6Index < 0) return false

        var count = 0
        for (i in handle.itemFields.indices) {
            val f = handle.itemFields[i]
            if (f.fieldNumber != 1 || f.wireType != 2) continue
            if (count == itemIndex) {
                val entryFields = parseFields(f.bytesValue())
                val blobIdx = entryFields.indexOfFirst { it.fieldNumber == 8 && it.wireType == 2 }
                if (blobIdx >= 0) {
                    val blobFields = parseFields(entryFields[blobIdx].bytesValue())
                    val subIdx = blobFields.indexOfFirst { it.fieldNumber == 2 && it.wireType == 2 }
                    if (subIdx >= 0) {
                        val subFields = parseFields(blobFields[subIdx].bytesValue())
                        val levelIdx = subFields.indexOfFirst { it.fieldNumber == 9 && it.wireType == 0 }
                        if (levelIdx >= 0) {
                            subFields[levelIdx] = PField(9, 0, newLevel.toLong())
                        } else {
                            subFields.add(PField(9, 0, newLevel.toLong()))
                        }
                        blobFields[subIdx] = PField(2, 2, serializeFields(subFields))
                        entryFields[blobIdx] = PField(8, 2, serializeFields(blobFields))
                    }
                }
                handle.itemFields[i] = PField(1, 2, serializeFields(entryFields))
                return writeHeroItemFields(heroFile, handle)
            }
            count++
        }
        return false
    }

    fun removeHeroItem(heroFile: File, itemIndex: Int): Boolean {
        val handle = readHeroItemsHandle(heroFile)
        if (handle.field6Index < 0) return false

        var count = 0
        for (i in handle.itemFields.indices) {
            val f = handle.itemFields[i]
            if (f.fieldNumber != 1 || f.wireType != 2) continue
            if (count == itemIndex) {
                handle.itemFields.removeAt(i)
                return writeHeroItemFields(heroFile, handle)
            }
            count++
        }
        return false
    }

    // ── Stash items (account.dat field 21 -> field 2) ──────────────────

    fun readStashItems(accountFile: File): List<D3Item> {
        val decrypted = SaveCipher.decrypt(accountFile.readBytes())
        val field21 = parseFields(decrypted).firstOrNull { it.fieldNumber == 21 && it.wireType == 2 }
            ?: return emptyList()
        val field2 = parseFields(field21.bytesValue()).firstOrNull { it.fieldNumber == 2 && it.wireType == 2 }
            ?: return emptyList()

        val result = ArrayList<D3Item>()
        var idx = 0
        for (f in parseFields(field2.bytesValue())) {
            if (f.fieldNumber == 1 && f.wireType == 2) {
                parseItemEntry(f.bytesValue(), idx)?.let { result.add(it) }
                idx++
            }
        }
        return result
    }

    fun addItemsToStash(accountFile: File, gbid: Long, quantity: Int): Int {
        val decrypted = SaveCipher.decrypt(accountFile.readBytes())
        val top = parseFields(decrypted)

        val field21Idx = top.indexOfFirst { it.fieldNumber == 21 && it.wireType == 2 }
        if (field21Idx < 0) return 0
        val partFields = parseFields(top[field21Idx].bytesValue())

        val stashIdx = partFields.indexOfFirst { it.fieldNumber == 2 && it.wireType == 2 }
        if (stashIdx < 0) return 0
        val itemFields = parseFields(partFields[stashIdx].bytesValue())

        val parsedItems = ArrayList<D3Item>()
        var pIdx = 0
        for (f in itemFields) {
            if (f.fieldNumber == 1 && f.wireType == 2) {
                parseItemEntry(f.bytesValue(), pIdx)?.let { parsedItems.add(it) }
                pIdx++
            }
        }
        val used = parsedItems.map { it.slot }.toHashSet()
        val maxUid = maxUidSerial(parsedItems)
        val freeSlots = findFreeSlots(used, startSlot = 200, count = quantity, limit = 1000)
        if (freeSlots.isEmpty()) return 0

        var added = 0
        for (slot in freeSlots) {
            val serial = maxUid + added + 1
            itemFields.add(PField(1, 2, makeItemEntry(serial, slot, gbid)))
            added++
        }

        partFields[stashIdx] = PField(2, 2, serializeFields(itemFields))
        top[field21Idx] = PField(21, 2, serializeFields(partFields))
        accountFile.writeBytes(SaveCipher.encrypt(serializeFields(top)))
        return added
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private fun findFreeSlots(used: Set<Int>, startSlot: Int, count: Int, limit: Int = 1000): List<Int> {
        val free = ArrayList<Int>()
        var slot = startSlot
        while (free.size < count && slot <= limit) {
            if (slot !in used) free.add(slot)
            slot += 2
        }
        return free
    }

    private fun maxUidSerial(items: List<D3Item>): Long =
        items.map { it.uidSerial }.filter { it in 1 until (1L shl 32) }.maxOrNull() ?: 2_000_000_000L

    private fun makeUidBytes(serial: Long): ByteArray =
        serializeFields(listOf(PField(1, 0, 1L), PField(2, 0, serial)))

    private fun makeItemBlob(gbid: Long): ByteArray {
        val seed = Random.nextInt(1, Int.MAX_VALUE)
        val seedBytes = byteArrayOf(
            (seed and 0xFF).toByte(),
            ((seed ushr 8) and 0xFF).toByte(),
            ((seed ushr 16) and 0xFF).toByte(),
            ((seed ushr 24) and 0xFF).toByte(),
        )
        val sub = serializeFields(listOf(PField(1, 0, 0L), PField(2, 5, seedBytes)))
        return serializeFields(listOf(PField(1, 0, gbid), PField(2, 2, sub)))
    }

    private fun makeItemEntry(serial: Long, slot: Int, gbid: Long): ByteArray {
        val uid = makeUidBytes(serial)
        val blob = makeItemBlob(gbid)
        return serializeFields(
            listOf(
                PField(1, 2, uid),
                PField(4, 0, 0L),
                PField(5, 0, 544L),
                PField(6, 0, slot.toLong()),
                PField(7, 0, 0L),
                PField(8, 2, blob),
            )
        )
    }
}
