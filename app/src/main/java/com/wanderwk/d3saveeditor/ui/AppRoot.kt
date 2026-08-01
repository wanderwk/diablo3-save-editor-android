package com.wanderwk.d3saveeditor.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.wanderwk.d3saveeditor.AppViewModel
import com.wanderwk.d3saveeditor.ui.screens.CoinsScreen
import com.wanderwk.d3saveeditor.ui.screens.ExportScreen
import com.wanderwk.d3saveeditor.ui.screens.GemsScreen
import com.wanderwk.d3saveeditor.ui.screens.HomeScreen
import com.wanderwk.d3saveeditor.ui.screens.ItemsScreen
import com.wanderwk.d3saveeditor.ui.screens.ParagonScreen
import com.wanderwk.d3saveeditor.ui.screens.SupportScreen
import com.wanderwk.d3saveeditor.ui.theme.BgBase

enum class AppTab(val label: String) {
    HOME("Home"), COINS("Moedas"), ITEMS("Itens"), GEMS("Gemas"),
    PARAGON("Paragon"), EXPORT("Exportar"), SUPPORT("Doações"),
}

@Composable
fun AppRoot(viewModel: AppViewModel) {
    var currentTab by remember { mutableStateOf(AppTab.HOME) }

    Scaffold(
        containerColor = BgBase,
        bottomBar = { BottomNavBar(currentTab) { currentTab = it } },
    ) { padding ->
        AnimatedContent(
            targetState = currentTab,
            transitionSpec = {
                (fadeIn(tween(220)) togetherWith fadeOut(tween(140)))
            },
            label = "screen-transition",
            modifier = Modifier.fillMaxSize().background(BgBase).padding(padding),
        ) { tab ->
            when (tab) {
                AppTab.HOME -> HomeScreen(viewModel)
                AppTab.COINS -> CoinsScreen(viewModel)
                AppTab.ITEMS -> ItemsScreen(viewModel)
                AppTab.GEMS -> GemsScreen(viewModel)
                AppTab.PARAGON -> ParagonScreen(viewModel)
                AppTab.EXPORT -> ExportScreen(viewModel)
                AppTab.SUPPORT -> SupportScreen()
            }
        }
    }
}
