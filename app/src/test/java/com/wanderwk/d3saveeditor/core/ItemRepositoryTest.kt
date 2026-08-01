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
                PField(6, 0, expectedSlot.toLong()),
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
}
