package com.wanderwk.d3saveeditor.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wanderwk.d3saveeditor.AppViewModel
import com.wanderwk.d3saveeditor.ui.components.EyebrowLabel
import com.wanderwk.d3saveeditor.ui.components.PillButton
import com.wanderwk.d3saveeditor.ui.components.SmallPill
import com.wanderwk.d3saveeditor.ui.theme.PrimaryAccent
import com.wanderwk.d3saveeditor.ui.theme.PrimaryContainer
import com.wanderwk.d3saveeditor.ui.theme.SuccessGreen
import com.wanderwk.d3saveeditor.ui.theme.SurfaceContainer
import com.wanderwk.d3saveeditor.ui.theme.TextMuted
import com.wanderwk.d3saveeditor.ui.theme.TextPrimary

@Composable
fun ExportScreen(viewModel: AppViewModel) {
    if (viewModel.saveInfo == null) {
        EmptyStateHint()
        return
    }
    var showJson by remember { mutableStateOf(false) }
    var exportMessage by remember { mutableStateOf<String?>(null) }

    val zipExporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) {
            viewModel.exportZip(uri) { ok, err ->
                exportMessage = if (ok) "Save exportado (.zip) com sucesso." else "Falha ao exportar: $err"
            }
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(SurfaceContainer).padding(18.dp)) {
            Text("Exportar Save", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Spacer(Modifier.height(6.dp))
            Text(
                "Gera um snapshot JSON com moedas/itens/paragon + checksum, e permite salvar o " +
                    "save (.zip) já criptografado de volta, pronto para copiar para o dispositivo.",
                color = TextMuted,
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(14.dp))
            PillButton(
                text = "Exportar JSON + Checksum",
                modifier = Modifier.fillMaxWidth(),
                onClick = { viewModel.generateExportPreview() },
            )
            Spacer(Modifier.height(10.dp))
            PillButton(
                text = "Salvar save (.zip)",
                modifier = Modifier.fillMaxWidth(),
                bg = SurfaceContainer,
                fg = PrimaryAccent,
                onClick = {
                    val name = "${viewModel.saveInfo?.rootName ?: "save"}_export.zip"
                    zipExporter.launch(name)
                },
            )
            exportMessage?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = SuccessGreen, fontSize = 12.sp)
            }
        }

        viewModel.lastChecksum?.let { checksum ->
            Spacer(Modifier.height(14.dp))
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(SurfaceContainer).padding(18.dp)) {
                Row {
                    Text("Checksum", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Spacer(Modifier.weight(1f))
                    SmallPill("✓ Válido", bg = SuccessGreen.copy(alpha = 0.18f), fg = SuccessGreen)
                }
                Spacer(Modifier.height(8.dp))
                Text(checksum, color = TextMuted, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }
        }

        viewModel.lastExportJson?.let { json ->
            Spacer(Modifier.height(14.dp))
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(SurfaceContainer).padding(18.dp)) {
                Row {
                    Text("Prévia JSON", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Spacer(Modifier.weight(1f))
                    SmallPill(if (showJson) "Ocultar" else "Mostrar", bg = PrimaryContainer, fg = PrimaryAccent, onClick = { showJson = !showJson })
                }
                if (showJson) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        json,
                        color = TextMuted,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()),
                    )
                }
            }
        }

        if (viewModel.exportHistory.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            EyebrowLabel("Histórico de exportações")
            Spacer(Modifier.height(8.dp))
            viewModel.exportHistory.take(10).forEach { (name, _) ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(SurfaceContainer).padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(name, color = TextPrimary, fontSize = 12.sp)
                    SmallPill("✓ OK", bg = SuccessGreen.copy(alpha = 0.18f), fg = SuccessGreen)
                }
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}
