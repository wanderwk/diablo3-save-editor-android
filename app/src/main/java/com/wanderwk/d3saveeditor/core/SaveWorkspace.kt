package com.wanderwk.d3saveeditor.core

import android.content.Context
import android.net.Uri
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class SaveEditorException(message: String) : Exception(message)

/**
 * Owns the on-device working copy of an imported save: extraction from a
 * user-picked .zip (Storage Access Framework), locating account.dat /
 * heroes/*.dat inside it, re-zipping for export, and simple timestamped
 * backups. Mirrors main.py's zip_member_map / write_structure_to_dir /
 * backup_source from the reference tool.
 */
object SaveWorkspace {
    private val REQUIRED_FILES = listOf("account.dat", "profile.dat", "prefs.dat")

    data class SaveInfo(
        val rootName: String,
        val root: File,
        val importedAtMillis: Long,
    )

    private fun baseDir(context: Context) = File(context.filesDir, "current_save").also { it.mkdirs() }
    private fun backupsDir(context: Context) = File(context.filesDir, "backups").also { it.mkdirs() }

    private fun prefs(context: Context) =
        context.getSharedPreferences("d3save_state", Context.MODE_PRIVATE)

    fun loadPersistedInfo(context: Context): SaveInfo? {
        val p = prefs(context)
        val rootName = p.getString("root_name", null) ?: return null
        val importedAt = p.getLong("imported_at", 0L)
        val root = File(baseDir(context), rootName)
        if (!root.isDirectory) return null
        return SaveInfo(rootName, root, importedAt)
    }

    private fun persist(context: Context, info: SaveInfo) {
        prefs(context).edit()
            .putString("root_name", info.rootName)
            .putLong("imported_at", info.importedAtMillis)
            .apply()
    }

    fun heroFiles(saveRoot: File): List<File> =
        File(saveRoot, "heroes").listFiles { f -> f.isFile && f.name.endsWith(".dat") }
            ?.sortedBy { it.name } ?: emptyList()

    /** Imports a save .zip picked via SAF. The zip must contain a single root
     * folder with account.dat/profile.dat/prefs.dat and a heroes/ subfolder. */
    fun importZip(context: Context, uri: Uri): SaveInfo {
        val resolver = context.contentResolver
        val entriesByRelativePath = LinkedHashMap<String, ByteArray>()
        var rootName: String? = null

        resolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val normalized = entry.name.trim('/').replace('\\', '/')
                        val parts = normalized.split('/')
                        if (parts.isNotEmpty() && parts[0].isNotBlank()) {
                            val root = parts[0]
                            if (rootName == null) rootName = root
                            if (root == rootName && parts.size > 1) {
                                val relative = parts.drop(1).joinToString("/")
                                entriesByRelativePath[relative] = zis.readBytes()
                            }
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        } ?: throw SaveEditorException("Não foi possível abrir o arquivo selecionado.")

        val root = rootName ?: throw SaveEditorException("O ZIP não possui arquivos.")
        val missing = REQUIRED_FILES.filter { it !in entriesByRelativePath }
        if (missing.isNotEmpty()) {
            throw SaveEditorException("ZIP inválido. Arquivos obrigatórios ausentes: ${missing.joinToString(", ")}")
        }
        if (entriesByRelativePath.keys.none { it.startsWith("heroes/") }) {
            throw SaveEditorException("ZIP inválido. Pasta heroes/ ausente.")
        }

        // Clear any previous working copy.
        val base = baseDir(context)
        base.listFiles()?.forEach { it.deleteRecursively() }

        val saveRoot = File(base, root)
        for ((relative, bytes) in entriesByRelativePath) {
            val dest = File(saveRoot, relative)
            dest.parentFile?.mkdirs()
            dest.writeBytes(bytes)
        }

        val info = SaveInfo(root, saveRoot, System.currentTimeMillis())
        persist(context, info)
        return info
    }

    /** Re-zips the current working save into the destination Uri (SAF create-document). */
    fun exportZip(context: Context, info: SaveInfo, destUri: Uri) {
        val resolver = context.contentResolver
        resolver.openOutputStream(destUri)?.use { out ->
            ZipOutputStream(out).use { zos ->
                info.root.walkTopDown().filter { it.isFile }.forEach { file ->
                    val relative = file.relativeTo(info.root).path.replace(File.separatorChar, '/')
                    zos.putNextEntry(ZipEntry("${info.rootName}/$relative"))
                    file.inputStream().use { it.copyTo(zos) }
                    zos.closeEntry()
                }
            }
        } ?: throw SaveEditorException("Não foi possível criar o arquivo de exportação.")
    }

    data class BackupEntry(val file: File, val label: String, val dateMillis: Long)

    fun backupNow(context: Context, info: SaveInfo): BackupEntry {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val dest = File(backupsDir(context), "${stamp}_${info.rootName}.zip")
        ZipOutputStream(dest.outputStream()).use { zos ->
            info.root.walkTopDown().filter { it.isFile }.forEach { file ->
                val relative = file.relativeTo(info.root).path.replace(File.separatorChar, '/')
                zos.putNextEntry(ZipEntry("${info.rootName}/$relative"))
                file.inputStream().use { it.copyTo(zos) }
                zos.closeEntry()
            }
        }
        return BackupEntry(dest, dest.name, dest.lastModified())
    }

    fun listBackups(context: Context): List<BackupEntry> =
        backupsDir(context).listFiles { f -> f.isFile && f.extension == "zip" }
            ?.sortedByDescending { it.lastModified() }
            ?.map { BackupEntry(it, it.name, it.lastModified()) }
            ?: emptyList()
}
