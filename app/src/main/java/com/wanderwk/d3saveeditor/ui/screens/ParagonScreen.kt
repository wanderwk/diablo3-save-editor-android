package com.wanderwk.d3saveeditor.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wanderwk.d3saveeditor.AppViewModel
import com.wanderwk.d3saveeditor.HeroUi
import com.wanderwk.d3saveeditor.ui.components.EyebrowLabel
import com.wanderwk.d3saveeditor.ui.components.SmallPill
import com.wanderwk.d3saveeditor.ui.components.formatThousands
import com.wanderwk.d3saveeditor.ui.theme.ActiveCardTonal
import com.wanderwk.d3saveeditor.ui.theme.CinzelFamily
import com.wanderwk.d3saveeditor.ui.theme.PrimaryAccent
import com.wanderwk.d3saveeditor.ui.theme.PrimaryContainer
import com.wanderwk.d3saveeditor.ui.theme.SecondaryGold
import com.wanderwk.d3saveeditor.ui.theme.SurfaceContainer
import com.wanderwk.d3saveeditor.ui.theme.TextMuted
import com.wanderwk.d3saveeditor.ui.theme.TextPrimary

@Composable
fun ParagonScreen(viewModel: AppViewModel) {
    if (viewModel.saveInfo == null) {
        EmptyStateHint()
        return
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(ActiveCardTonal).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                viewModel.paragonLevel.toString(),
                color = SecondaryGold,
                fontFamily = CinzelFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 42.sp,
            )
            Text(
                "XP equivalente: ${formatThousands(viewModel.paragonLevel * 640_000L)}",
                color = TextMuted,
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(16.dp))
            Slider(
                value = viewModel.paragonLevel.toFloat(),
                onValueChange = { viewModel.updateParagonLevel(it.toInt()) },
                valueRange = 0f..10000f,
                colors = SliderDefaults.colors(thumbColor = PrimaryAccent, activeTrackColor = PrimaryAccent),
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(-10, -1, 1, 10).forEach { delta ->
                    SmallPill(
                        text = if (delta > 0) "+$delta" else "$delta",
                        bg = SurfaceContainer,
                        fg = TextPrimary,
                        onClick = { viewModel.updateParagonLevel(viewModel.paragonLevel + delta) },
                    )
                }
                SmallPill("MAX", bg = PrimaryContainer, fg = PrimaryAccent, onClick = { viewModel.updateParagonLevel(10000) })
            }
        }

        Spacer(Modifier.height(20.dp))
        EyebrowLabel("Heróis")
        Spacer(Modifier.height(10.dp))
        viewModel.heroes.forEach { hero ->
            HeroRow(hero) { viewModel.setHeroLevel(hero, it) }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun HeroRow(hero: HeroUi, onLevelChange: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(SurfaceContainer).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(PrimaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(hero.name.take(1).uppercase(), color = PrimaryAccent, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(hero.name, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(hero.heroClass, color = TextMuted, fontSize = 11.sp)
        }
        SmallPill("−", bg = com.wanderwk.d3saveeditor.ui.theme.ChipDark2, fg = TextPrimary, onClick = { onLevelChange(hero.level - 1) })
        Spacer(Modifier.width(8.dp))
        Text(hero.level.toString(), color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(8.dp))
        SmallPill("+", bg = com.wanderwk.d3saveeditor.ui.theme.ChipDark2, fg = TextPrimary, onClick = { onLevelChange(hero.level + 1) })
    }
}
