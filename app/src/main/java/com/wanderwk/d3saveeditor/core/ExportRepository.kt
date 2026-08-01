package com.wanderwk.d3saveeditor.core

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** JSON export + SHA-256 checksums of the current save, mirroring save_json_io.py. */
object ExportRepository {

    data class FileChecksum(val relativePath: String, val size: Long, val sha256: String)

    fun computeChecksums(saveRoot: File): List<FileChecksum> {
        val digest = MessageDigest.getInstance("SHA-256")
        return saveRoot.walkTopDown()
            .filter { it.isFile }
            .sortedBy { it.relativeTo(saveRoot).path }
            .map { file ->
                digest.reset()
                val bytes = file.readBytes()
                val hash = digest.digest(bytes).joinToString("") { "%02x".format(it) }
                FileChecksum(file.relativeTo(saveRoot).path.replace(File.separatorChar, '/'), bytes.size.toLong(), hash)
            }
            .toList()
    }

    /** A single combined checksum over every file's hash, used as the "save integrity" token shown in the UI. */
    fun combinedChecksum(checksums: List<FileChecksum>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        checksums.sortedBy { it.relativePath }.forEach {
            digest.update(it.relativePath.toByteArray(Charsets.UTF_8))
            digest.update(it.sha256.toByteArray(Charsets.UTF_8))
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun exportToJson(
        saveRoot: File,
        heroFiles: List<File>,
        currencies: Map<String, Long>,
        paragonLevel: Int,
    ): String {
        val root = JSONObject()
        root.put("_schema_version", "1.0")
        root.put(
            "_exported_at",
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(Date())
        )
        root.put("save_root_name", saveRoot.name)

        val currenciesJson = JSONObject()
        currencies.forEach { (k, v) -> currenciesJson.put(k, v) }
        root.put("currencies", currenciesJson)
        root.put("paragon_level", paragonLevel)

        val heroesJson = JSONArray()
        for (hp in heroFiles) {
            val info = ParagonRepository.readHeroInfo(hp)
            val items = ItemRepository.readHeroItems(hp)
            val heroObj = JSONObject()
            heroObj.put("filename", hp.name)
            heroObj.put("name", info.name)
            heroObj.put("level", info.level)
            heroObj.put("class_id", "0x%X".format(info.classId))
            heroObj.put("highest_rift", info.highestRift)
            val itemsArr = JSONArray()
            for (it in items) {
                val itemObj = JSONObject()
                itemObj.put("index", it.index)
                itemObj.put("slot", it.slot)
                itemObj.put("slot_name", it.slotLabel)
                itemObj.put("gbid", it.gbid)
                itemObj.put("gbid_hex", "0x%08X".format(it.gbid))
                itemObj.put("quality", it.quality)
                itemObj.put("level", it.level)
                itemObj.put("quantity", it.quantity)
                itemObj.put("name", it.name)
                itemsArr.put(itemObj)
            }
            heroObj.put("items", itemsArr)
            heroesJson.put(heroObj)
        }
        root.put("heroes", heroesJson)

        val stashArr = JSONArray()
        for (it in ItemRepository.readStashItems(File(saveRoot, "account.dat"))) {
            val itemObj = JSONObject()
            itemObj.put("index", it.index)
            itemObj.put("slot", it.slot)
            itemObj.put("gbid", it.gbid)
            itemObj.put("gbid_hex", "0x%08X".format(it.gbid))
            itemObj.put("quality", it.quality)
            itemObj.put("quantity", it.quantity)
            itemObj.put("name", it.name)
            stashArr.put(itemObj)
        }
        root.put("stash", stashArr)

        val checksums = computeChecksums(saveRoot)
        val checksumsArr = JSONArray()
        checksums.forEach {
            val obj = JSONObject()
            obj.put("relative_path", it.relativePath)
            obj.put("size", it.size)
            obj.put("sha256", it.sha256)
            checksumsArr.put(obj)
        }
        root.put("checksums", checksumsArr)
        root.put("combined_checksum", combinedChecksum(checksums))

        return root.toString(2)
    }
}
