package com.wanderwk.d3saveeditor.core

import android.content.Context
import java.util.zip.GZIPInputStream

/**
 * Full item catalog (~14.7k entries: gems, legendaries, sets, materials,
 * crafting plans, etc.), bundled as a gzipped TSV asset generated from the
 * reference tool's merged catalog (item_catalog_data.py + D3Studio GBID
 * lists). Columns: name \t gbid(uint32) \t rarity \t category.
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
            context.assets.open("item_catalog.tsv.gz").use { raw ->
                GZIPInputStream(raw).bufferedReader(Charsets.UTF_8).useLines { lines ->
                    for (line in lines) {
                        if (line.isBlank()) continue
                        val parts = line.split('\t')
                        if (parts.size < 4) continue
                        val gbid = parts[1].toLongOrNull() ?: continue
                        list.add(Entry(parts[0], gbid, parts[2], parts[3]))
                    }
                }
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
