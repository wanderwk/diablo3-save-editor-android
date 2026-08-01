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
    // Re-resolve the selected item after every edit so the sheet reflects the latest socketed gems.
    val selectedFresh = selected?.let { sel -> items.firstOrNull { it.index == sel.index } }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text(
            "Toque em um item para gerenciar as gemas socketadas nele (soquete real do item, via " +
                "Generator.contents — não substitui mais o item).",
            color = TextFaint,
            fontSize = 11.sp,
        )
        Spacer(Modifier.height(12.dp))
        items.forEach { item ->
            GemItemRow(item) { selected = item }
            Spacer(Modifier.height(10.dp))
        }
    }

    selectedFresh?.let { item ->
        GemSocketSheet(
            item = item,
            onDismiss = { selected = null },
            onAdd = { gbid ->
                viewModel.addGemToItem(hero, item.index, gbid) { refreshTick++ }
            },
            onRemove = { slot ->
                viewModel.removeGemFromItem(hero, item.index, slot) { refreshTick++ }
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
        if (item.socketedGbids.isEmpty()) {
            Text("Sem gemas socketadas", color = TextFaint, fontSize = 11.sp)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                item.socketedGemNames.forEach { gemName ->
                    val cat = GEM_TYPES.firstOrNull { gemName.contains(it, ignoreCase = true) }
                    Box(
                        Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (cat != null) gemColor(cat).copy(alpha = 0.3f) else SurfaceContainerHigh1),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("●", color = if (cat != null) gemColor(cat) else TextFaint, fontSize = 14.sp)
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Text("${item.socketedGbids.size} gema(s) socketada(s)", color = TextMuted, fontSize = 10.sp)
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun GemSocketSheet(
    item: ItemRepository.D3Item,
    onDismiss: () -> Unit,
    onAdd: (Long) -> Unit,
    onRemove: (Int) -> Unit,
) {
    var type by remember { mutableStateOf(GEM_TYPES[0]) }
    var chosen by remember { mutableStateOf<ItemCatalog.Entry?>(null) }

    val qualities = remember(type) { ItemCatalog.all().filter { it.category.equals(type, ignoreCase = true) } }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = SurfaceContainerHigh1) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Text("Gemas em ${item.name}", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Spacer(Modifier.height(10.dp))

            if (item.socketedGemNames.isEmpty()) {
                Text("Nenhuma gema socketada ainda.", color = TextFaint, fontSize = 12.sp)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    item.socketedGemNames.forEachIndexed { slot, name ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(SurfaceContainer)
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(name, color = TextPrimary, fontSize = 12.sp)
                            Text(
                                "Remover",
                                color = com.wanderwk.d3saveeditor.ui.theme.ErrorOrange,
                                fontSize = 11.sp,
                                modifier = Modifier.clickable { onRemove(slot) },
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Text("Adicionar gema", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Spacer(Modifier.height(10.dp))
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
                text = "Adicionar gema",
                modifier = Modifier.fillMaxWidth(),
                onClick = { chosen?.let { onAdd(it.gbid); chosen = null } },
            )
            Spacer(Modifier.height(12.dp))
        }
    }
}
