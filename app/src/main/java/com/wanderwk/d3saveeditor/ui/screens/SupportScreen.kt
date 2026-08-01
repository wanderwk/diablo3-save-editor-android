package com.wanderwk.d3saveeditor.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.QrCode2
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
import com.wanderwk.d3saveeditor.ui.theme.BrandRed
import com.wanderwk.d3saveeditor.ui.theme.CinzelFamily
import com.wanderwk.d3saveeditor.ui.theme.PrimaryAccent
import com.wanderwk.d3saveeditor.ui.theme.SecondaryGold
import com.wanderwk.d3saveeditor.ui.theme.SurfaceContainer
import com.wanderwk.d3saveeditor.ui.theme.SurfaceContainerHigh1
import com.wanderwk.d3saveeditor.ui.theme.TextMuted

@Composable
fun SupportScreen() {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(SurfaceContainer).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(BrandRed.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Favorite, contentDescription = null, tint = BrandRed)
            }
            Spacer(Modifier.height(16.dp))
            Text("Apoie o Desenvolvimento", color = SecondaryGold, fontFamily = CinzelFamily, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
            Spacer(Modifier.height(10.dp))
            Text(
                "Este app é gratuito, feito nas horas vagas. Qualquer valor via Pix é muito bem-vindo " +
                    "e ajuda a manter o projeto vivo.",
                color = TextMuted,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp,
            )
            Spacer(Modifier.height(20.dp))
            Box(
                Modifier
                    .size(180.dp)
                    .background(PrimaryAccent, RoundedCornerShape(20.dp))
                    .padding(10.dp),
            ) {
                Box(
                    Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)).background(SurfaceContainerHigh1),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.QrCode2, contentDescription = "QR Code Pix", tint = TextMuted, modifier = Modifier.size(72.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("Escaneie o QR Code com o app do seu banco", color = TextMuted, fontSize = 11.sp, textAlign = TextAlign.Center)
        }
    }
}
