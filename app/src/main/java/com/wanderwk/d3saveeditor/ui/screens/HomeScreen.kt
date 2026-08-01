package com.wanderwk.d3saveeditor.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wanderwk.d3saveeditor.AppViewModel
import com.wanderwk.d3saveeditor.ui.components.EyebrowLabel
import com.wanderwk.d3saveeditor.ui.components.PillButton
import com.wanderwk.d3saveeditor.ui.theme.ActiveCardTonal
import com.wanderwk.d3saveeditor.ui.theme.CardHeadingStyle
import com.wanderwk.d3saveeditor.ui.theme.ChipDark1
import com.wanderwk.d3saveeditor.ui.theme.ChipDark2
import com.wanderwk.d3saveeditor.ui.theme.CinzelFamily
import com.wanderwk.d3saveeditor.ui.theme.DividerOutline
import com.wanderwk.d3saveeditor.ui.theme.PrimaryAccent
import com.wanderwk.d3saveeditor.ui.theme.SecondaryGold
import com.wanderwk.d3saveeditor.ui.theme.SurfaceContainer
import com.wanderwk.d3saveeditor.ui.theme.TextFaint
import com.wanderwk.d3saveeditor.ui.theme.TextMuted
import com.wanderwk.d3saveeditor.ui.theme.TextPrimary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(viewModel: AppViewModel) {
    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { viewModel.importSave(it) }
    }
    val dateFmt = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        val info = viewModel.saveInfo
        if (info != null) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(ActiveCardTonal)
                    .padding(20.dp),
            ) {
                EyebrowLabel("Save ativo")
                Spacer(Modifier.height(6.dp))
                Text(info.rootName, color = SecondaryGold, fontFamily = CinzelFamily, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                Spacer(Modifier.height(4.dp))
                Text("Importado em ${dateFmt.format(Date(info.importedAtMillis))}", color = TextMuted, fontSize = 12.sp)
                Spacer(Modifier.height(14.dp))

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val hero = viewModel.heroes.firstOrNull()
                    StatChip(
                        modifier = Modifier.weight(1f),
                        bg = ChipDark1,
                        label = "Herói",
                        value = hero?.let { "${it.name} · ${it.heroClass} · Nv ${it.level}" } ?: "—",
                    )
                    StatChip(
                        modifier = Modifier.weight(1f),
                        bg = ChipDark2,
                        label = "Paragon",
                        value = viewModel.paragonLevel.toString(),
                        valueColor = SecondaryGold,
                    )
                }
            }
        } else {
            viewModel.importError?.let {
                Text(it, color = com.wanderwk.d3saveeditor.ui.theme.ErrorOrange, fontSize = 12.sp)
            }
        }

        // Import dropzone
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(SurfaceContainer)
                .border(1.dp, DividerOutline, RoundedCornerShape(22.dp))
                .clickable { importPicker.launch("application/zip") }
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Filled.CloudUpload, contentDescription = null, tint = PrimaryAccent, modifier = Modifier.size(26.dp))
            Spacer(Modifier.height(8.dp))
            Text(if (viewModel.busy) "Importando..." else "Importar Save", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Spacer(Modifier.height(4.dp))
            Text("Toque para selecionar um arquivo .zip de save", color = TextFaint, fontSize = 12.sp)
        }

        // Auto-backup card
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(SurfaceContainer).padding(16.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Backup automático", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Text("Salva uma cópia a cada edição", color = TextMuted, fontSize = 11.sp)
                }
                Switch(
                    checked = viewModel.autoBackup,
                    onCheckedChange = { viewModel.toggleAutoBackup() },
                    colors = SwitchDefaults.colors(checkedTrackColor = PrimaryAccent, checkedThumbColor = SurfaceContainer),
                )
            }
            Spacer(Modifier.height(12.dp))
            PillButton(
                text = "Fazer backup agora",
                modifier = Modifier.fillMaxWidth(),
                onClick = { viewModel.backupNow() },
            )
        }

        // Recent backups
        if (viewModel.backups.isNotEmpty()) {
            Column {
                EyebrowLabel("Backups recentes")
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    viewModel.backups.take(6).forEach { b ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(SurfaceContainer)
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(b.label, color = TextPrimary, fontSize = 12.sp)
                            Text(dateFmt.format(Date(b.dateMillis)), color = TextMuted, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatChip(modifier: Modifier = Modifier, bg: androidx.compose.ui.graphics.Color, label: String, value: String, valueColor: androidx.compose.ui.graphics.Color = TextPrimary) {
    Column(modifier.clip(RoundedCornerShape(18.dp)).background(bg).padding(12.dp)) {
        Text(label, color = TextMuted, fontSize = 11.sp)
        Spacer(Modifier.height(2.dp))
        Text(value, color = valueColor, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
    }
}
