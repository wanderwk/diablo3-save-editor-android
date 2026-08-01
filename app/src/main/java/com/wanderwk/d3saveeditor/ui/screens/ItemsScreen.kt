package com.wanderwk.d3saveeditor.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wanderwk.d3saveeditor.AppViewModel
import com.wanderwk.d3saveeditor.HeroUi
import com.wanderwk.d3saveeditor.core.ItemCatalog
import com.wanderwk.d3saveeditor.core.ItemRepository
import com.wanderwk.d3saveeditor.ui.components.PillButton
import com.wanderwk.d3saveeditor.ui.components.SmallPill
import com.wanderwk.d3saveeditor.ui.theme.ChipDark1
import com.wanderwk.d3saveeditor.ui.theme.PrimaryAccent
import com.wanderwk.d3saveeditor.ui.theme.PrimaryContainer
import com.wanderwk.d3saveeditor.ui.theme.SurfaceContainer
import com.wanderwk.d3saveeditor.ui.theme.SurfaceContainerHigh1
import com.wanderwk.d3saveeditor.ui.theme.TextFaint
import com.wanderwk.d3saveeditor.ui.theme.TextMuted
import com.wanderwk.d3saveeditor.ui.theme.TextPrimary
import com.wanderwk.d3saveeditor.ui.theme.rarityColor

private val RARITY_FILTERS = listOf("Todos", "legendary", "set", "rare", "magic", "normal")
private val RARITY_LABELS = mapOf(
    "Todos" to "Todos", "legendary" to "Lendário", "set" to "Set", "rare" to "Raro",
    "magic" to "Mágico", "normal" to "Comum",
)

@Composable
fun ItemsScreen(viewModel: AppViewModel) {
    if (viewModel.saveInfo == null) {
        EmptyStateHint()
        return
    }
    val hero = viewModel.heroes.firstOrNull()
    var query by remember { mutableStateOf("") }
    var rarityFilter by remember { mutableStateOf("Todos") }
    var showAddSheet by remember { mutableStateOf(false) }
    var selectedItem by remember { mutableStateOf<ItemRepository.D3Item?>(null) }
    var refreshTick by remember { mutableStateOf(0) }

    val heroItems = remember(hero, refreshTick) { hero?.let { viewModel.heroItems(it) } ?: emptyList() }
    val catalogResults = remember(query, rarityFilter) {
        ItemCatalog.search(query, if (rarityFilter == "Todos") null else rarityFilter)
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceContainer)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Search, contentDescription = null, tint = TextFaint, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = TextStyle(color = TextPrimary, fontSize = 13.sp),
                    modifier = Modifier.weight(1f),
                    decorationBox = { inner ->
                        if (query.isEmpty()) Text("Buscar item...", color = TextFaint, fontSize = 13.sp)
                        inner()
                    },
                )
            }
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(PrimaryContainer)
                    .clickable { showAddSheet = true },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Adicionar item", tint = PrimaryAccent)
            }
        }

        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(androidx.compose.foundation.rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RARITY_FILTERS.forEach { r ->
                val active = r == rarityFilter
                SmallPill(
                    text = RARITY_LABELS[r] ?: r,
                    bg = if (active) PrimaryContainer else SurfaceContainer,
                    fg = if (active) PrimaryAccent else TextMuted,
                    onClick = { rarityFilter = r },
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        val catalogSize = ItemCatalog.all().size
        Text(
            if (catalogSize > 0) {
                "${heroItems.size} itens do herói · catálogo com $catalogSize itens"
            } else {
                "${heroItems.size} itens do herói · catálogo NÃO carregado (nomes aparecerão como Desconhecido)"
            },
            color = if (catalogSize > 0) TextFaint else com.wanderwk.d3saveeditor.ui.theme.ErrorOrange,
            fontSize = 11.sp,
        )
        Spacer(Modifier.height(8.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f),
        ) {
            items(heroItems) { item ->
                ItemCard(item) { selectedItem = item }
            }
        }
    }

    selectedItem?.let { item ->
        ItemDetailSheet(
            item = item,
            onDismiss = { selectedItem = null },
            onRemove = {
                hero?.let { h ->
                    viewModel.removeHeroItem(h, item.index) { refreshTick++; selectedItem = null }
                }
            },
        )
    }

    if (showAddSheet) {
        AddItemSheet(
            catalogResults = catalogResults,
            query = query,
            onQueryChange = { query = it },
            hero = hero,
            onDismiss = { showAddSheet = false },
            onAdd = { gbid, qty, toStash ->
                if (toStash) {
                    viewModel.addItemToStash(gbid, qty) { showAddSheet = false }
                } else if (hero != null) {
                    viewModel.addItemToHero(hero, gbid, qty) { refreshTick++; showAddSheet = false }
                }
            },
        )
    }
}

@Composable
private fun ItemCard(item: ItemRepository.D3Item, onClick: () -> Unit) {
    val color = rarityColor(item.rarity)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(SurfaceContainer)
            .clickable(onClick = onClick)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(30.dp).clip(RoundedCornerShape(10.dp)).background(color.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(item.name.take(2).uppercase(), color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(8.dp))
            Text("Nv ${item.level}", color = TextFaint, fontSize = 11.sp)
        }
        Spacer(Modifier.height(8.dp))
        Text(item.name, color = TextPrimary, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(4.dp))
        Text("${item.rarity} · ${item.slotLabel}", color = color, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ItemDetailSheet(item: ItemRepository.D3Item, onDismiss: () -> Unit, onRemove: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = SurfaceContainerHigh1) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(item.name, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Text("${item.rarity} · ${item.slotLabel} · Nível ${item.level}", color = rarityColor(item.rarity), fontSize = 12.sp)
                }
                Icon(Icons.Filled.Close, contentDescription = "Fechar", tint = TextFaint, modifier = Modifier.clickable(onClick = onDismiss))
            }
            Spacer(Modifier.height(16.dp))
            Text("GBID: 0x%08X".format(item.gbid), color = TextFaint, fontSize = 11.sp)
            Spacer(Modifier.height(16.dp))
            PillButton(text = "Remover item", modifier = Modifier.fillMaxWidth(), bg = ChipDark1, fg = com.wanderwk.d3saveeditor.ui.theme.ErrorOrange, onClick = onRemove)
            Spacer(Modifier.height(12.dp))
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun AddItemSheet(
    catalogResults: List<ItemCatalog.Entry>,
    query: String,
    onQueryChange: (String) -> Unit,
    hero: HeroUi?,
    onDismiss: () -> Unit,
    onAdd: (gbid: Long, qty: Int, toStash: Boolean) -> Unit,
) {
    var selected by remember { mutableStateOf<ItemCatalog.Entry?>(null) }
    var qty by remember { mutableStateOf(1) }
    var toStash by remember { mutableStateOf(hero == null) }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = SurfaceContainerHigh1) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Text("Adicionar item", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Spacer(Modifier.height(12.dp))
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(color = TextPrimary, fontSize = 13.sp),
                decorationBox = { inner ->
                    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(SurfaceContainer).padding(12.dp)) {
                        if (query.isEmpty()) Text("Nome do item...", color = TextFaint, fontSize = 13.sp)
                        inner()
                    }
                },
            )
            Spacer(Modifier.height(10.dp))
            Column(Modifier.height(220.dp)) {
                LazyVerticalGrid(columns = GridCells.Fixed(1), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(catalogResults.take(150)) { entry ->
                        val active = entry == selected
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (active) PrimaryContainer else androidx.compose.ui.graphics.Color.Transparent)
                                .clickable { selected = entry }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                        ) {
                            Text(
                                entry.name,
                                color = if (active) PrimaryAccent else rarityColor(entry.rarity),
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Quantidade", color = TextMuted, fontSize = 12.sp)
                Spacer(Modifier.weight(1f))
                SmallPill("−", bg = SurfaceContainer, fg = TextPrimary, onClick = { if (qty > 1) qty-- })
                Text(qty.toString(), color = TextPrimary, fontSize = 14.sp, modifier = Modifier.width(28.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                SmallPill("+", bg = SurfaceContainer, fg = TextPrimary, onClick = { if (qty < 999) qty++ })
            }
            if (hero != null) {
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SmallPill(
                        if (toStash) "Destino: Baú" else "Destino: Herói",
                        bg = SurfaceContainer,
                        fg = PrimaryAccent,
                        onClick = { toStash = !toStash },
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            PillButton(
                text = "Adicionar ao Inventário",
                modifier = Modifier.fillMaxWidth(),
                onClick = { selected?.let { onAdd(it.gbid, qty, toStash) } },
            )
            Spacer(Modifier.height(12.dp))
        }
    }
}
