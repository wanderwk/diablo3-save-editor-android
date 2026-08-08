package com.wanderwk.d3saveeditor.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * Regression test for the "every item/gem shows as Item Desconhecido" bug.
 *
 * Root cause: the item entry's Generator sub-message (SavedItem field 8)
 * was parsed with a guessed layout (field1=gbid varint, field2={field1
 * =quality, field9=level}). The real layout (confirmed against
 * https://github.com/GoobyCorp/D3Edit's Items.proto and cross-checked
 * byte-for-byte against a real Switch save, see project history) is:
 *   Generator.field1  = seed (NOT gbid)
 *   Generator.field2  = gb_handle { field1=game_balance_type, field2=gbid (SFIXED32 LE) }
 *   Generator.field8  = stack_size (the real "quantity")
 *   Generator.field10 = item_quality_level (SINT32, zigzag-encoded)
 *
 * This test builds a synthetic (not a real user save) item entry with that
 * exact real layout and asserts ItemRepository extracts every field
 * correctly, so a future refactor can't silently reintroduce the old
 * (wrong) layout without a test failure.
 */
class ItemRepositoryTest {

    private fun zigzagEncode(n: Int): Long = ((n shl 1) xor (n shr 31)).toLong() and 0xFFFFFFFFL

    private fun leFixed32(value: Long): ByteArray {
        val v = value.toInt()
        return byteArrayOf(
            (v and 0xFF).toByte(),
            ((v ushr 8) and 0xFF).toByte(),
            ((v ushr 16) and 0xFF).toByte(),
            ((v ushr 24) and 0xFF).toByte(),
        )
    }

    @Test
    fun `readHeroItems extracts gbid, quality and stack size from the real Generator layout`() {
        val expectedGbid = 0x3F5F2D49L // "Rubi Lascado"
        val expectedQuality = 3 // legendary
        val expectedStack = 7L
        val expectedSlot = 16

        val handle = serializeFields(listOf(PField(1, 0, 4L), PField(2, 5, leFixed32(expectedGbid))))
        val generator = serializeFields(
            listOf(
                PField(1, 0, 987654321L), // seed -- must NOT be read as gbid
                PField(2, 2, handle),
                PField(8, 0, expectedStack),
                PField(10, 0, zigzagEncode(expectedQuality)),
            )
        )
        val uid = serializeFields(listOf(PField(1, 0, 1L), PField(2, 0, 42L)))
        val entry = serializeFields(
            listOf(
                PField(1, 2, uid),
                PField(5, 0, 544L),
                PField(6, 0, zigzagEncode(expectedSlot)),
                PField(8, 2, generator),
            )
        )
        val itemsList = serializeFields(listOf(PField(1, 2, entry)))
        val heroBytes = serializeFields(listOf(PField(6, 2, itemsList)))

        val heroFile = File.createTempFile("regression_hero", ".dat")
        heroFile.deleteOnExit()
        heroFile.writeBytes(SaveCipher.encrypt(heroBytes))

        val items = ItemRepository.readHeroItems(heroFile)
        assertEquals(1, items.size)
        val item = items[0]
        assertEquals(expectedGbid, item.gbid)
        assertEquals(expectedQuality, item.quality)
        assertEquals(expectedStack, item.quantity)
        assertEquals(expectedSlot, item.slot)
    }

    /**
     * Regression test for gem sockets. Real layout (same Items.proto source
     * as above): a socketed gem is an EmbeddedGenerator (Generator field 13,
     * repeated) -- {field1=id, field2=nested Generator{...gb_handle{gbid}}}
     * -- NOT a swap of the host item's own gbid (the old "apply gem" hack).
     * SavedItem.used_socket_count (entry field 7) tracks how many are filled.
     */
    @Test
    fun `addGemToItem and removeGemFromItem manage real Generator contents sockets`() {
        val hostGbid = 0x11223344L
        val gemGbid = 0x55667788L

        val handle = serializeFields(listOf(PField(1, 0, 4L), PField(2, 5, leFixed32(hostGbid))))
        val generator = serializeFields(listOf(PField(1, 0, 111L), PField(2, 2, handle), PField(8, 0, 1L)))
        val uid = serializeFields(listOf(PField(1, 0, 1L), PField(2, 0, 7L)))
        val entry = serializeFields(
            listOf(PField(1, 2, uid), PField(5, 0, 544L), PField(6, 0, 14L), PField(8, 2, generator))
        )
        val itemsList = serializeFields(listOf(PField(1, 2, entry)))
        val heroBytes = serializeFields(listOf(PField(6, 2, itemsList)))

        val heroFile = File.createTempFile("regression_hero_sockets", ".dat")
        heroFile.deleteOnExit()
        heroFile.writeBytes(SaveCipher.encrypt(heroBytes))

        // No sockets initially.
        var item = ItemRepository.readHeroItems(heroFile)[0]
        assertEquals(0, item.usedSocketCount)
        assertEquals(emptyList<Long>(), item.socketedGbids)
        assertEquals(hostGbid, item.gbid) // host item's own gbid must be untouched

        // Add a gem: host gbid stays the same, a socketed gem shows up.
        assertEquals(true, ItemRepository.addGemToItem(heroFile, 0, gemGbid))
        item = ItemRepository.readHeroItems(heroFile)[0]
        assertEquals(hostGbid, item.gbid)
        assertEquals(1, item.usedSocketCount)
        assertEquals(listOf(gemGbid), item.socketedGbids)

        // Remove it: back to zero sockets, host gbid still untouched.
        assertEquals(true, ItemRepository.removeGemFromItem(heroFile, 0, 0))
        item = ItemRepository.readHeroItems(heroFile)[0]
        assertEquals(hostGbid, item.gbid)
        assertEquals(0, item.usedSocketCount)
        assertEquals(emptyList<Long>(), item.socketedGbids)
    }

    /**
     * Regression test for a real "save recognized as old/invalid by the game after
     * editing" bug report (2026-08-08). Root cause: `SavedItem.square_index` (entry
     * field 6) is SINT32 (zigzag-encoded) per the real `Items.proto`, same as
     * `item_quality_level` above -- but `addItemsToHero`/`addItemsToStash` wrote the
     * inventory position we pick (16, 18, 20, ...) as a *plain* varint. The real game
     * (a real, schema-compiled protobuf parser) always zigzag-*decodes* that field
     * regardless of how we wrote it, so our intended position 16 landed at real grid
     * cell 8 instead -- risking a collision with an item that's actually there,
     * something a strict save-integrity check could plausibly reject outright. Fixed
     * by zigzag-encoding on write (and correspondingly decoding on read, so our own
     * "used slots" bookkeeping stays consistent with what the game actually sees).
     *
     * This test verifies the fix using an independent, from-scratch varint/zigzag
     * decode (not the code under test) to simulate what a real protobuf sint32 field
     * would decode to -- so a regression that breaks the *encoding* can't hide by
     * also breaking the *decoding* in a matching way.
     */
    @Test
    fun `addItemsToHero writes square_index that a real sint32 parser decodes back to the intended slot`() {
        fun realZigzagDecode(v: Long): Long {
            val n = v.toInt()
            return ((n ushr 1) xor -(n and 1)).toLong()
        }

        val heroBytes = serializeFields(listOf(PField(6, 2, ByteArray(0))))
        val heroFile = File.createTempFile("regression_square_index", ".dat")
        heroFile.deleteOnExit()
        heroFile.writeBytes(SaveCipher.encrypt(heroBytes))

        assertEquals(1, ItemRepository.addItemsToHero(heroFile, gbid = 1L, quantity = 1))

        val top = parseFields(SaveCipher.decrypt(heroFile.readBytes()))
        val itemsList = top.first { it.fieldNumber == 6 && it.wireType == 2 }
        val entry = parseFields(itemsList.bytesValue()).first { it.fieldNumber == 1 && it.wireType == 2 }
        val squareIndexField = parseFields(entry.bytesValue()).first { it.fieldNumber == 6 && it.wireType == 0 }

        // What a real game (real sint32 decode) would see -- must be exactly 16,
        // the logical slot we intended, not 8 (16 misread as a plain varint then
        // zigzag-decoded).
        assertEquals(16L, realZigzagDecode(squareIndexField.longValue()))

        // Our own read path must agree (decode-consistency for collision avoidance).
        assertEquals(16, ItemRepository.readHeroItems(heroFile).single().slot)
    }
}
