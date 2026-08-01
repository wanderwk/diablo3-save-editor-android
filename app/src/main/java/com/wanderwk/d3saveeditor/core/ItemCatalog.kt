package com.wanderwk.d3saveeditor.core

import android.content.Context
import android.util.Log

/**
 * Full item catalog (~14.7k entries: gems, legendaries, sets, materials,
 * crafting plans, etc.), bundled as a plain TSV asset generated from the
 * reference tool's merged catalog (item_catalog_data.py + D3Studio GBID
 * lists). Columns: name \t gbid(uint32) \t rarity \t category.
 *
 * Shipped uncompressed on purpose: AGP's asset packaging silently
 * decompresses/renames a committed item_catalog.tsv.gz to item_catalog.tsv
 * inside the built APK (observed directly by inspecting a release APK), so
 * relying on a .gz extension + GZIPInputStream at runtime is fragile --
 * a FileNotFoundException from the wrong filename here crashed app startup
 * entirely, since this is loaded from AppViewModel's init block.
 */
object ItemCatalog {

    data class Entry(val name: String, val gbid: Long, val rarity: String, val category: String)

    @Volatile private var entries: List<Entry> = emptyList()
    @Volatile private var byGbid: Map<Long, Entry> = emptyMap()
    @Volatile private var loaded = false

    val categories: List<String> get() = entries.map { it.category }.distinct()

    fun ensureLoaded(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            val list = ArrayList<Entry>(15_000)
            try {
                context.assets.open("item_catalog.tsv").use { raw ->
                    raw.bufferedReader(Charsets.UTF_8).useLines { lines ->
                        for (line in lines) {
                            if (line.isBlank()) continue
                            val parts = line.split('\t')
                            if (parts.size < 4) continue
                            val gbid = parts[1].toLongOrNull() ?: continue
                            list.add(Entry(parts[0], gbid, parts[2], parts[3]))
                        }
                    }
                }
            } catch (e: Exception) {
                // Item browsing/adding degrades gracefully to an empty catalog
                // rather than taking down app startup over an asset problem.
                Log.e("ItemCatalog", "Failed to load item_catalog.tsv", e)
            }
            entries = list
            byGbid = list.associateBy { it.gbid }
            loaded = true
        }
    }

    fun all(): List<Entry> = entries

    fun lookup(gbid: Long): String = byGbid[gbid and 0xFFFFFFFFL]?.name ?: "Item Desconhecido"

    fun search(query: String, rarity: String?, limit: Int = 400): List<Entry> {
        val q = query.trim().lowercase()
        val seq = entries.asSequence()
            .filter { rarity == null || rarity == "Todos" || it.rarity.equals(rarity, ignoreCase = true) }
            .filter { q.isEmpty() || it.name.lowercase().contains(q) }
        return seq.take(limit).toList()
    }
}
