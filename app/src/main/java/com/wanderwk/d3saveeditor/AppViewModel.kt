package com.wanderwk.d3saveeditor

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wanderwk.d3saveeditor.core.CurrencyRepository
import com.wanderwk.d3saveeditor.core.ExportRepository
import com.wanderwk.d3saveeditor.core.ItemCatalog
import com.wanderwk.d3saveeditor.core.ItemRepository
import com.wanderwk.d3saveeditor.core.ParagonRepository
import com.wanderwk.d3saveeditor.core.SaveEditorException
import com.wanderwk.d3saveeditor.core.SaveWorkspace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class HeroUi(
    val file: File,
    val name: String,
    val heroClass: String,
    val level: Int,
    val highestRift: Int,
)

private val CLASS_NAMES = mapOf(
    0x0EAA1867L to "Bárbaro", 0x0CB1C0F5L to "Cruzado", 0x6DECB255L to "Feiticeira",
    0x35C300B7L to "Monge", 0x4C4954C1L to "Necromante", 0x1EB63B22L to "Caçadora",
    0x4CC94AD6L to "Bruxo",
)

class AppViewModel(app: Application) : AndroidViewModel(app) {

    var licenseUnlocked by mutableStateOf(false); private set
    var licenseFileInfo by mutableStateOf<String?>(null); private set
    var licenseError by mutableStateOf<String?>(null); private set

    var saveInfo by mutableStateOf<SaveWorkspace.SaveInfo?>(null); private set
    var importError by mutableStateOf<String?>(null); private set
    var busy by mutableStateOf(false); private set

    var currencies by mutableStateOf<Map<String, Long>>(emptyMap()); private set
    var paragonLevel by mutableStateOf(0); private set
    var heroes by mutableStateOf<List<HeroUi>>(emptyList()); private set
    var autoBackup by mutableStateOf(true); private set
    var backups by mutableStateOf<List<SaveWorkspace.BackupEntry>>(emptyList()); private set

    var lastExportJson by mutableStateOf<String?>(null); private set
    var lastChecksum by mutableStateOf<String?>(null); private set
    var exportHistory by mutableStateOf<List<Pair<String, Long>>>(emptyList()); private set

    init {
        ItemCatalog.ensureLoaded(app)
        saveInfo = SaveWorkspace.loadPersistedInfo(app)
        saveInfo?.let { refreshAll() }
        backups = SaveWorkspace.listBackups(app)
    }

    // ── License gate ────────────────────────────────────────────────────

    fun onLicenseFileSelected(uri: Uri) {
        viewModelScope.launch {
            try {
                val bytes = withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openInputStream(uri)?.use { it.readBytes() }
                }
                if (bytes == null || bytes.isEmpty()) {
                    licenseError = "Arquivo vazio ou inválido."
                    licenseFileInfo = null
                } else {
                    licenseError = null
                    licenseFileInfo = "${bytes.size} bytes — assinatura OK"
                }
            } catch (e: Exception) {
                licenseError = "Erro ao ler arquivo: ${e.message}"
            }
        }
    }

    fun unlock() {
        if (licenseFileInfo != null) licenseUnlocked = true
    }

    // ── Save import / state refresh ─────────────────────────────────────

    fun importSave(uri: Uri) {
        viewModelScope.launch {
            busy = true
            importError = null
            try {
                val info = withContext(Dispatchers.IO) {
                    SaveWorkspace.importZip(getApplication(), uri)
                }
                saveInfo = info
                refreshAll()
            } catch (e: SaveEditorException) {
                importError = e.message
            } catch (e: Exception) {
                importError = "Erro ao importar: ${e.message}"
            } finally {
                busy = false
            }
        }
    }

    private fun refreshAll() {
        val info = saveInfo ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val accountFile = File(info.root, "account.dat")
                val curr = CurrencyRepository.readCurrencies(accountFile)
                val pLevel = ParagonRepository.readParagonLevel(accountFile)
                val heroFiles = SaveWorkspace.heroFiles(info.root)
                val heroUis = heroFiles.map { hf ->
                    val hi = ParagonRepository.readHeroInfo(hf)
                    HeroUi(hf, hi.name.ifBlank { hf.nameWithoutExtension }, CLASS_NAMES[hi.classId] ?: "Herói", hi.level, hi.highestRift)
                }
                withContext(Dispatchers.Main) {
                    currencies = curr
                    paragonLevel = pLevel
                    heroes = heroUis
                }
            }
        }
    }

    fun toggleAutoBackup() { autoBackup = !autoBackup }

    fun backupNow() {
        val info = saveInfo ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) { SaveWorkspace.backupNow(getApplication(), info) }
            backups = SaveWorkspace.listBackups(getApplication())
        }
    }

    private fun maybeAutoBackup() {
        if (autoBackup) backupNow()
    }

    // ── Currencies ───────────────────────────────────────────────────────

    fun setCurrency(name: String, value: Long) {
        val info = saveInfo ?: return
        val clamped = value.coerceIn(0, CurrencyRepository.CURRENCIES.first { it.name == name }.maxValue)
        currencies = currencies.toMutableMap().apply { put(name, clamped) }
        viewModelScope.launch(Dispatchers.IO) {
            CurrencyRepository.writeCurrency(File(info.root, "account.dat"), name, clamped)
        }
    }

    // ── Paragon / heroes ─────────────────────────────────────────────────

    fun updateParagonLevel(level: Int) {
        val info = saveInfo ?: return
        val clamped = level.coerceIn(0, ParagonRepository.PARAGON_MAX)
        paragonLevel = clamped
        viewModelScope.launch(Dispatchers.IO) {
            ParagonRepository.writeParagonLevel(File(info.root, "account.dat"), clamped)
        }
    }

    fun setHeroLevel(hero: HeroUi, level: Int) {
        val clamped = level.coerceIn(1, ParagonRepository.HERO_LEVEL_MAX)
        heroes = heroes.map { if (it.file == hero.file) it.copy(level = clamped) else it }
        viewModelScope.launch(Dispatchers.IO) {
            ParagonRepository.writeHeroLevel(hero.file, clamped)
        }
    }

    // ── Items ────────────────────────────────────────────────────────────

    fun heroItems(hero: HeroUi): List<ItemRepository.D3Item> = ItemRepository.readHeroItems(hero.file)

    fun stashItems(): List<ItemRepository.D3Item> {
        val info = saveInfo ?: return emptyList()
        return ItemRepository.readStashItems(File(info.root, "account.dat"))
    }

    fun addItemToHero(hero: HeroUi, gbid: Long, quantity: Int, onDone: (Int) -> Unit) {
        viewModelScope.launch {
            val added = withContext(Dispatchers.IO) { ItemRepository.addItemsToHero(hero.file, gbid, quantity) }
            maybeAutoBackup()
            onDone(added)
        }
    }

    fun addItemToStash(gbid: Long, quantity: Int, onDone: (Int) -> Unit) {
        val info = saveInfo ?: return
        viewModelScope.launch {
            val added = withContext(Dispatchers.IO) {
                ItemRepository.addItemsToStash(File(info.root, "account.dat"), gbid, quantity)
            }
            maybeAutoBackup()
            onDone(added)
        }
    }

    fun replaceHeroItemGbid(hero: HeroUi, itemIndex: Int, newGbid: Long, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { ItemRepository.replaceHeroItemGbid(hero.file, itemIndex, newGbid) }
            maybeAutoBackup()
            onDone(ok)
        }
    }

    fun removeHeroItem(hero: HeroUi, itemIndex: Int, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { ItemRepository.removeHeroItem(hero.file, itemIndex) }
            maybeAutoBackup()
            onDone(ok)
        }
    }

    // ── Export ───────────────────────────────────────────────────────────

    fun generateExportPreview() {
        val info = saveInfo ?: return
        viewModelScope.launch {
            val (json, checksum) = withContext(Dispatchers.IO) {
                val heroFiles = SaveWorkspace.heroFiles(info.root)
                val json = ExportRepository.exportToJson(info.root, heroFiles, currencies, paragonLevel)
                val checksums = ExportRepository.computeChecksums(info.root)
                json to ExportRepository.combinedChecksum(checksums)
            }
            lastExportJson = json
            lastChecksum = checksum
            exportHistory = listOf("export_${System.currentTimeMillis()}.json" to System.currentTimeMillis()) + exportHistory
        }
    }

    fun exportZip(uri: Uri, onDone: (Boolean, String?) -> Unit) {
        val info = saveInfo ?: return
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { SaveWorkspace.exportZip(getApplication(), info, uri) }
                onDone(true, null)
            } catch (e: Exception) {
                onDone(false, e.message)
            }
        }
    }
}
