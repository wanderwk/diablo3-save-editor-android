package com.wanderwk.d3saveeditor.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wanderwk.d3saveeditor.AppViewModel
import com.wanderwk.d3saveeditor.ui.components.PillButton
import com.wanderwk.d3saveeditor.ui.theme.BgBase
import com.wanderwk.d3saveeditor.ui.theme.CinzelFamily
import com.wanderwk.d3saveeditor.ui.theme.DividerOutline
import com.wanderwk.d3saveeditor.ui.theme.ErrorOrange
import com.wanderwk.d3saveeditor.ui.theme.PrimaryAccent
import com.wanderwk.d3saveeditor.ui.theme.PrimaryContainer
import com.wanderwk.d3saveeditor.ui.theme.SecondaryGold
import com.wanderwk.d3saveeditor.ui.theme.SuccessGreen
import com.wanderwk.d3saveeditor.ui.theme.SurfaceContainer
import com.wanderwk.d3saveeditor.ui.theme.TextFaint
import com.wanderwk.d3saveeditor.ui.theme.TextMuted
import com.wanderwk.d3saveeditor.ui.theme.TextPrimary

@Composable
fun LicenseGateScreen(viewModel: AppViewModel) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { viewModel.onLicenseFileSelected(it) }
    }

    Box(Modifier.fillMaxSize().background(BgBase), contentAlignment = Alignment.Center) {
        Column(
            Modifier.fillMaxWidth().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier.size(56.dp).clip(RoundedCornerShape(18.dp)).background(PrimaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Lock, contentDescription = null, tint = PrimaryAccent)
            }
            androidx.compose.foundation.layout.Spacer(Modifier.size(20.dp))
            Text(
                "Arquivo de Licença",
                color = SecondaryGold,
                fontFamily = CinzelFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 22.sp,
            )
            androidx.compose.foundation.layout.Spacer(Modifier.size(12.dp))
            Text(
                "Não afiliado à Blizzard Entertainment. Ferramenta de uso pessoal para edição de " +
                    "saves offline do Diablo III. Use por sua conta e risco.",
                color = TextMuted,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp,
            )
            androidx.compose.foundation.layout.Spacer(Modifier.size(24.dp))

            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(SurfaceContainer)
                    .border(1.dp, DividerOutline, RoundedCornerShape(20.dp))
                    .clickable { picker.launch("*/*") }
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(Icons.Filled.UploadFile, contentDescription = null, tint = PrimaryAccent, modifier = Modifier.size(28.dp))
                androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
                Text("Selecionar prod.key / .bin", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                androidx.compose.foundation.layout.Spacer(Modifier.size(4.dp))
                Text("Toque para escolher o arquivo de licença", color = TextFaint, fontSize = 12.sp)

                viewModel.licenseFileInfo?.let {
                    androidx.compose.foundation.layout.Spacer(Modifier.size(10.dp))
                    Text(it, color = SuccessGreen, fontSize = 12.sp)
                }
                viewModel.licenseError?.let {
                    androidx.compose.foundation.layout.Spacer(Modifier.size(10.dp))
                    Text(it, color = ErrorOrange, fontSize = 12.sp)
                }
            }

            androidx.compose.foundation.layout.Spacer(Modifier.size(20.dp))
            PillButton(
                text = "Desbloquear",
                modifier = Modifier.fillMaxWidth(),
                onClick = { viewModel.unlock() },
            )
            androidx.compose.foundation.layout.Spacer(Modifier.size(10.dp))
            Text(
                "Demo: qualquer prod.key/.bin não vazio funciona",
                color = TextFaint,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}
