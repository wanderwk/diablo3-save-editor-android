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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wanderwk.d3saveeditor.AppViewModel
import com.wanderwk.d3saveeditor.core.ItemCatalog
import com.wanderwk.d3saveeditor.core.ItemRepository
import com.wanderwk.d3saveeditor.ui.components.PillButton
import com.wanderwk.d3saveeditor.ui.theme.PrimaryAccent
import com.wanderwk.d3saveeditor.ui.theme.PrimaryContainer
import com.wanderwk.d3saveeditor.ui.theme.SurfaceContainer
import com.wanderwk.d3saveeditor.ui.theme.SurfaceContainerHigh1
import com.wanderwk.d3saveeditor.ui.theme.TextFaint
import com.wanderwk.d3saveeditor.ui.theme.TextMuted
import com.wanderwk.d3saveeditor.ui.theme.TextPrimary
import com.wanderwk.d3saveeditor.ui.theme.gemColor
import com.wanderwk.d3saveeditor.ui.theme.rarityColor

private val GEM_TYPES = listOf("Rubi", "Esmeralda", "Topázio", "Ametista", "Diamante")

@Composable
fun GemsScreen(viewModel: AppViewModel) {
    val hero = viewModel.heroes.firstOrNull()
    if (viewModel.saveInfo == null || hero == null) {
        EmptyStateHint()
        return
    }
    var refreshTick by remember { mutableStateOf(0) }
    val items = remember(hero, refreshTick) { viewModel.heroItems(hero) }
    var selected by remember { mutableStateOf<ItemRepository.D3Item?>(null) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text(
            "Toque em um item para aplicar uma gema (substitui o item pela gema escolhida — " +
                "este formato de save não expõe soquetes individuais separadamente).",
            color = TextFaint,
            fontSize = 11.sp,
        )
        Spacer(Modifier.height(12.dp))
        items.forEach { item ->
            GemItemRow(item) { selected = item }
            Spacer(Modifier.height(10.dp))
        }
    }

    selected?.let { item ->
        GemApplySheet(
            item = item,
            onDismiss = { selected = null },
            onApply = { gbid ->
                viewModel.replaceHeroItemGbid(hero, item.index, gbid) { refreshTick++; selected = null }
            },
        )
    }
}

@Composable
private fun GemItemRow(item: ItemRepository.D3Item, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(SurfaceContainer)
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Text(item.name, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("${item.rarity} · ${item.slotLabel}", color = rarityColor(item.rarity), fontSize = 11.sp)
        Spacer(Modifier.height(10.dp))
        val gemCategory = GEM_TYPES.firstOrNull { it.equals(ItemCatalog.all().firstOrNull { e -> e.gbid == item.gbid }?.category, ignoreCase = true) }
        Box(
            Modifier
                .size(58.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(if (gemCategory != null) gemColor(gemCategory).copy(alpha = 0.25f) else SurfaceContainerHigh1),
            contentAlignment = Alignment.Center,
        ) {
            Text(if (gemCategory != null) "●" else "Vazio", color = if (gemCategory != null) gemColor(gemCategory) else TextFaint, fontSize = if (gemCategory != null) 20.sp else 9.sp)
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun GemApplySheet(item: ItemRepository.D3Item, onDismiss: () -> Unit, onApply: (Long) -> Unit) {
    var type by remember { mutableStateOf(GEM_TYPES[0]) }
    var chosen by remember { mutableStateOf<ItemCatalog.Entry?>(null) }

    val qualities = remember(type) { ItemCatalog.all().filter { it.category.equals(type, ignoreCase = true) } }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = SurfaceContainerHigh1) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Text("Aplicar gema em ${item.name}", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GEM_TYPES.forEach { t ->
                    val active = t == type
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(if (active) gemColor(t) else gemColor(t).copy(alpha = 0.3f))
                            .clickable { type = t; chosen = null },
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(type, color = TextMuted, fontSize = 11.sp)
            Spacer(Modifier.height(12.dp))

            LazyColumn(Modifier.height(200.dp)) {
                items(qualities.take(60)) { entry ->
                    val active = entry == chosen
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (active) PrimaryContainer else androidx.compose.ui.graphics.Color.Transparent)
                            .clickable { chosen = entry }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                    ) {
                        Text(entry.name, color = if (active) PrimaryAccent else TextPrimary, fontSize = 12.sp)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            PillButton(
                text = "Aplicar",
                modifier = Modifier.fillMaxWidth(),
                onClick = { chosen?.let { onApply(it.gbid) } },
            )
            Spacer(Modifier.height(12.dp))
        }
    }
}
