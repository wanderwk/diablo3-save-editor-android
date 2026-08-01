package com.wanderwk.d3saveeditor.core

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Exercises ItemCatalog.ensureLoaded against a REAL Android Context
 * (Robolectric simulates AssetManager reading from app/src/main/assets/,
 * the same code path a real device uses) instead of a plain JVM file read.
 * Written to pin down a real-device bug where every item/gem name showed
 * as "Item Desconhecido" despite the .tsv asset itself being byte-correct.
 */
@RunWith(RobolectricTestRunner::class)
class ItemCatalogTest {

    @Test
    fun `catalog loads all entries via real Android AssetManager`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        ItemCatalog.ensureLoaded(context)

        val all = ItemCatalog.all()
        assertTrue("expected thousands of catalog entries, got ${all.size}", all.size > 10_000)

        val rubi = all.first { it.name == "Rubi Lascado" }
        assertEquals(1063202121L, rubi.gbid)
        assertEquals("Rubi Lascado", ItemCatalog.lookup(rubi.gbid))
    }

    @Test
    fun `lookup resolves gbids the same way ItemRepository parses them`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        ItemCatalog.ensureLoaded(context)

        // Same masking ItemRepository.parseItemEntry applies to a parsed varint field.
        val rawVarintValue = 1063202121L
        val gbidAsParsedFromSave = rawVarintValue and 0xFFFFFFFFL
        assertEquals("Rubi Lascado", ItemCatalog.lookup(gbidAsParsedFromSave))
    }
}
