package com.wanderwk.d3saveeditor.core

import java.io.File
import kotlin.random.Random

/**
 * Hero inventory/equipment (hero.dat field 6) and shared stash
 * (account.dat field 21 -> field 2) item read/write.
 *
 * Field layout confirmed against the real `Items.proto` (compiled Python
 * descriptors from https://github.com/GoobyCorp/D3Edit, `Items_pb2.py`)
 * cross-checked byte-for-byte against a real Switch save sample -- 24/24
 * items resolved to correct catalog names once this was fixed (see
 * project history: the original guessed layout below was wrong, which is
 * why every item/gem showed as "Item Desconhecido").
 *
 * `SavedItem` (one entry inside hero.dat field 6 / stash field 21->2):
 *   field 1 (message) id: ItemId{id_high, id_low} (both uint64 varint)
 *   field 5 (varint)  item_slot (equip-slot enum -- semantics not fully
 *                      mapped yet, not used here)
 *   field 6 (varint)  square_index -- SINT32 (zigzag-encoded, confirmed
 *                      against the real Items.proto same as field 10 below).
 *                      This is what we use as "slot" for bucketing into
 *                      equipped/inventory/stash and finding free cells.
 *                      Existing items round-trip their raw wire bytes
 *                      untouched regardless (we never re-encode a value we
 *                      didn't decode ourselves), but any BRAND NEW item we
 *                      construct (makeItemEntry) must zigzag-*encode* the
 *                      square_index we pick, or the real game decodes it to
 *                      a different (halved) cell than we intended, risking
 *                      a collision with a real item's actual grid position.
 *   field 8 (message) generator: see Generator below
 *
 * `Generator` (SavedItem field 8 -- previously wrongly treated as
 * "field1=gbid, field2={field1=quality,field9=level}"):
 *   field 1  (varint)   seed (per-instance random seed, NOT the gbid --
 *                       this is why the old code, which read this as
 *                       gbid, never matched anything in the catalog)
 *   field 2  (message)  gb_handle: Handle{field1=game_balance_type (varint,
 *                       always 4 for items in real saves), field2=gbid
 *                       (SFIXED32, 4 raw bytes little-endian, signed --
 *                       mask to unsigned 32-bit to match catalog gbids)}
 *   field 8  (varint)   stack_size -- the real "quantity" for stackable
 *                       items (gems, materials)
 *   field 10 (varint)   item_quality_level -- SINT32 (zigzag-encoded), the
 *                       real rarity/quality tier
 *   field 13 (message, repeated) contents -- EmbeddedGenerator{field1=id
 *                       (ItemId), field2=generator (nested Generator, same
 *                       layout as this one)} -- this is where socketed
 *                       gems really live. Confirmed against the real
 *                       `Items.proto` (D3.Items.Generator.contents /
 *                       D3.Items.EmbeddedGenerator, same source that fixed
 *                       the gbid bug above). Replaces the old "apply gem =
 *                       overwrite the item's own gbid" hack.
 *   field 16 (varint)   legendary_item_level -- only meaningful for
 *                       legendaries; used here as the item's displayed
 *                       "level" (0 for everything else)
 *
 * `SavedItem.used_socket_count` (field 7, varint) tracks how many sockets
 * are currently filled. The item's *total* socket capacity is not stored
 * in the save at all (it's static game-balance data looked up by gbid at
 * runtime, same as damage/armor/etc.) -- our catalog doesn't have it
 * either, so we don't enforce a socket cap here.
 */
object ItemRepository {

    data class D3Item(
        val index: Int,
        val slot: Int,
        val gbid: Long,
        val quality: Int,
        val level: Int,
        val quantity: Long,
        val uidSerial: Long,
        val usedSocketCount: Int = 0,
        val socketedGbids: List<Long> = emptyList(),
    ) {
        val name: String get() = ItemCatalog.lookup(gbid)
        val rarity: String get() = qualityToRarity(quality)
        val slotLabel: String get() = ItemRepository.slotLabel(slot)
        val socketedGemNames: List<String> get() = socketedGbids.map { ItemCatalog.lookup(it) }
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

    private fun zigzagDecode32(value: Long): Long {
        val n = value.toInt()
        return ((n ushr 1) xor -(n and 1)).toLong()
    }

    /** Inverse of [zigzagDecode32] -- required for any sint32 field we construct ourselves
     * (as opposed to fields whose raw wire bytes we just copy verbatim from a real item). */
    private fun zigzagEncode32(value: Long): Long {
        val n = value.toInt()
        return (((n shl 1) xor (n shr 31)).toLong()) and 0xFFFFFFFFL
    }

    private fun leFixed32ToLong(b: ByteArray): Long {
        val raw = (b[0].toInt() and 0xFF) or ((b[1].toInt() and 0xFF) shl 8) or
            ((b[2].toInt() and 0xFF) shl 16) or ((b[3].toInt() and 0xFF) shl 24)
        return raw.toLong() and 0xFFFFFFFFL
    }

    private fun longToLeFixed32(value: Long): ByteArray {
        val v = value.toInt()
        return byteArrayOf(
            (v and 0xFF).toByte(),
            ((v ushr 8) and 0xFF).toByte(),
            ((v ushr 16) and 0xFF).toByte(),
            ((v ushr 24) and 0xFF).toByte(),
        )
    }

    // ── Parsing a single item entry ─────────────────────────────────────

    private fun parseItemEntry(entryBytes: ByteArray, idx: Int): D3Item? {
        var slot = -1
        var rawGenerator: ByteArray? = null
        var uidSerial = 0L
        var usedSocketCount = 0

        for (f in parseFields(entryBytes)) {
            when {
                f.fieldNumber == 6 && f.wireType == 0 -> slot = zigzagDecode32(f.longValue()).toInt()
                f.fieldNumber == 7 && f.wireType == 0 -> usedSocketCount = f.longValue().toInt()
                f.fieldNumber == 8 && f.wireType == 2 -> rawGenerator = f.bytesValue()
                f.fieldNumber == 1 && f.wireType == 2 -> {
                    uidSerial = parseFields(f.bytesValue())
                        .firstOrNull { it.fieldNumber == 2 && it.wireType == 0 }
                        ?.longValue() ?: 0L
                }
            }
        }
        val generator = rawGenerator ?: return null

        var gbid = 0L
        var quality = 0
        var legendaryLevel = 0
        var stackSize = 0L
        val socketedGbids = ArrayList<Long>()
        for (f in parseFields(generator)) {
            when {
                f.fieldNumber == 2 && f.wireType == 2 -> { // gb_handle
                    for (h in parseFields(f.bytesValue())) {
                        if (h.fieldNumber == 2 && h.wireType == 5) {
                            gbid = leFixed32ToLong(h.bytesValue())
                        }
                    }
                }
                f.fieldNumber == 8 && f.wireType == 0 -> stackSize = f.longValue()
                f.fieldNumber == 10 && f.wireType == 0 -> quality = zigzagDecode32(f.longValue()).toInt()
                f.fieldNumber == 16 && f.wireType == 0 -> legendaryLevel = f.longValue().toInt()
                f.fieldNumber == 13 && f.wireType == 2 -> { // EmbeddedGenerator = socketed gem
                    extractEmbeddedGbid(f.bytesValue())?.let { socketedGbids.add(it) }
                }
            }
        }
        return D3Item(idx, slot, gbid, quality, legendaryLevel, stackSize, uidSerial, usedSocketCount, socketedGbids)
    }

    /** Reads the gbid out of an EmbeddedGenerator{id, generator{...gb_handle{...gbid}}} blob. */
    private fun extractEmbeddedGbid(embeddedBytes: ByteArray): Long? {
        val nestedGenerator = parseFields(embeddedBytes).firstOrNull { it.fieldNumber == 2 && it.wireType == 2 } ?: return null
        val handle = parseFields(nestedGenerator.bytesValue()).firstOrNull { it.fieldNumber == 2 && it.wireType == 2 } ?: return null
        val gbidField = parseFields(handle.bytesValue()).firstOrNull { it.fieldNumber == 2 && it.wireType == 5 } ?: return null
        return leFixed32ToLong(gbidField.bytesValue())
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

    /** Finds the Generator (field 8) and gb_handle (field 2) sub-fields of an item entry, for in-place edits. */
    private fun withGeneratorFields(
        entryFields: MutableList<PField>,
        edit: (generatorFields: MutableList<PField>) -> Unit,
    ): Boolean {
        val genIdx = entryFields.indexOfFirst { it.fieldNumber == 8 && it.wireType == 2 }
        if (genIdx < 0) return false
        val generatorFields = parseFields(entryFields[genIdx].bytesValue())
        edit(generatorFields)
        entryFields[genIdx] = PField(8, 2, serializeFields(generatorFields))
        return true
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
                withGeneratorFields(entryFields) { generatorFields ->
                    val handleIdx = generatorFields.indexOfFirst { it.fieldNumber == 2 && it.wireType == 2 }
                    if (handleIdx >= 0) {
                        val handleFields = parseFields(generatorFields[handleIdx].bytesValue())
                        val gbidIdx = handleFields.indexOfFirst { it.fieldNumber == 2 && it.wireType == 5 }
                        if (gbidIdx >= 0) {
                            handleFields[gbidIdx] = PField(2, 5, longToLeFixed32(newGbid))
                        } else {
                            handleFields.add(PField(2, 5, longToLeFixed32(newGbid)))
                        }
                        generatorFields[handleIdx] = PField(2, 2, serializeFields(handleFields))
                    }
                }
                handle.itemFields[i] = PField(1, 2, serializeFields(entryFields))
                return writeHeroItemFields(heroFile, handle)
            }
            count++
        }
        return false
    }

    /** Edits an existing item's stack_size (Generator field 8) -- the real "quantity" for stackable items. */
    fun updateHeroItemStackSize(heroFile: File, itemIndex: Int, newQuantity: Long): Boolean {
        val handle = readHeroItemsHandle(heroFile)
        if (handle.field6Index < 0) return false

        var count = 0
        for (i in handle.itemFields.indices) {
            val f = handle.itemFields[i]
            if (f.fieldNumber != 1 || f.wireType != 2) continue
            if (count == itemIndex) {
                val entryFields = parseFields(f.bytesValue())
                withGeneratorFields(entryFields) { generatorFields ->
                    val stackIdx = generatorFields.indexOfFirst { it.fieldNumber == 8 && it.wireType == 0 }
                    if (stackIdx >= 0) {
                        generatorFields[stackIdx] = PField(8, 0, newQuantity)
                    } else {
                        generatorFields.add(PField(8, 0, newQuantity))
                    }
                }
                handle.itemFields[i] = PField(1, 2, serializeFields(entryFields))
                return writeHeroItemFields(heroFile, handle)
            }
            count++
        }
        return false
    }

    /** Adds a gem (as an EmbeddedGenerator) into an item's Generator.contents (field 13) and bumps used_socket_count. */
    fun addGemToItem(heroFile: File, itemIndex: Int, gemGbid: Long): Boolean {
        val handle = readHeroItemsHandle(heroFile)
        if (handle.field6Index < 0) return false

        var count = 0
        for (i in handle.itemFields.indices) {
            val f = handle.itemFields[i]
            if (f.fieldNumber != 1 || f.wireType != 2) continue
            if (count == itemIndex) {
                val entryFields = parseFields(f.bytesValue())
                withGeneratorFields(entryFields) { generatorFields ->
                    generatorFields.add(PField(13, 2, makeEmbeddedGenerator(gemGbid)))
                }
                val socketIdx = entryFields.indexOfFirst { it.fieldNumber == 7 && it.wireType == 0 }
                val current = if (socketIdx >= 0) entryFields[socketIdx].longValue() else 0L
                if (socketIdx >= 0) {
                    entryFields[socketIdx] = PField(7, 0, current + 1)
                } else {
                    entryFields.add(PField(7, 0, 1L))
                }
                handle.itemFields[i] = PField(1, 2, serializeFields(entryFields))
                return writeHeroItemFields(heroFile, handle)
            }
            count++
        }
        return false
    }

    /** Removes the [gemSlot]-th socketed gem (0-based, in encounter order) from an item and decrements used_socket_count. */
    fun removeGemFromItem(heroFile: File, itemIndex: Int, gemSlot: Int): Boolean {
        val handle = readHeroItemsHandle(heroFile)
        if (handle.field6Index < 0) return false

        var count = 0
        for (i in handle.itemFields.indices) {
            val f = handle.itemFields[i]
            if (f.fieldNumber != 1 || f.wireType != 2) continue
            if (count == itemIndex) {
                val entryFields = parseFields(f.bytesValue())
                var removed = false
                withGeneratorFields(entryFields) { generatorFields ->
                    val embeddedIndices = generatorFields.withIndex()
                        .filter { it.value.fieldNumber == 13 && it.value.wireType == 2 }
                        .map { it.index }
                    if (gemSlot in embeddedIndices.indices) {
                        generatorFields.removeAt(embeddedIndices[gemSlot])
                        removed = true
                    }
                }
                if (!removed) return false
                val socketIdx = entryFields.indexOfFirst { it.fieldNumber == 7 && it.wireType == 0 }
                if (socketIdx >= 0) {
                    val current = entryFields[socketIdx].longValue()
                    entryFields[socketIdx] = PField(7, 0, (current - 1).coerceAtLeast(0))
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

    /** SavedItem.id = ItemId{id_high, id_low}; id_low doubles as our per-item unique serial. */
    private fun makeUidBytes(serial: Long): ByteArray =
        serializeFields(listOf(PField(1, 0, 1L), PField(2, 0, serial)))

    /**
     * Builds a minimal but valid Generator message for a brand-new item:
     * a random seed, and a gb_handle pointing at [gbid] (game_balance_type
     * 4 is what every real item in a sampled save used).
     */
    private fun makeGenerator(gbid: Long): ByteArray {
        val seed = Random.nextInt(1, Int.MAX_VALUE)
        val handle = serializeFields(listOf(PField(1, 0, 4L), PField(2, 5, longToLeFixed32(gbid))))
        return serializeFields(listOf(PField(1, 0, seed.toLong()), PField(2, 2, handle)))
    }

    /** Builds an EmbeddedGenerator{id, generator} blob for a gem being socketed into another item. */
    private fun makeEmbeddedGenerator(gemGbid: Long): ByteArray {
        val serial = Random.nextInt(1, Int.MAX_VALUE).toLong()
        val id = makeUidBytes(serial)
        val generator = makeGenerator(gemGbid)
        return serializeFields(listOf(PField(1, 2, id), PField(2, 2, generator)))
    }

    private fun makeItemEntry(serial: Long, slot: Int, gbid: Long): ByteArray {
        val uid = makeUidBytes(serial)
        val generator = makeGenerator(gbid)
        return serializeFields(
            listOf(
                PField(1, 2, uid),
                PField(4, 0, 0L),
                PField(5, 0, 544L),
                PField(6, 0, zigzagEncode32(slot.toLong())),
                PField(7, 0, 0L),
                PField(8, 2, generator),
            )
        )
    }
}
