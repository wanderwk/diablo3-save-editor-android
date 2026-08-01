package com.wanderwk.d3saveeditor.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wanderwk.d3saveeditor.ui.theme.NavCoins
import com.wanderwk.d3saveeditor.ui.theme.NavExport
import com.wanderwk.d3saveeditor.ui.theme.NavGems
import com.wanderwk.d3saveeditor.ui.theme.NavHome
import com.wanderwk.d3saveeditor.ui.theme.NavInactive
import com.wanderwk.d3saveeditor.ui.theme.NavItems
import com.wanderwk.d3saveeditor.ui.theme.NavParagon
import com.wanderwk.d3saveeditor.ui.theme.NavSupport
import com.wanderwk.d3saveeditor.ui.theme.SurfaceContainerHigh3

private data class NavItem(val tab: AppTab, val icon: ImageVector, val accent: Color)

private val NAV_ITEMS = listOf(
    NavItem(AppTab.HOME, Icons.Filled.Home, NavHome),
    NavItem(AppTab.COINS, Icons.Filled.AttachMoney, NavCoins),
    NavItem(AppTab.ITEMS, Icons.Filled.ShoppingBag, NavItems),
    NavItem(AppTab.GEMS, Icons.Filled.Diamond, NavGems),
    NavItem(AppTab.PARAGON, Icons.Filled.MilitaryTech, NavParagon),
    NavItem(AppTab.EXPORT, Icons.Filled.FileUpload, NavExport),
    NavItem(AppTab.SUPPORT, Icons.Filled.Favorite, NavSupport),
)

@Composable
fun BottomNavBar(current: AppTab, onSelect: (AppTab) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(SurfaceContainerHigh3, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .padding(horizontal = 6.dp, vertical = 10.dp),
    ) {
        NAV_ITEMS.forEach { item ->
            NavTabItem(item, active = item.tab == current, onSelect = { onSelect(item.tab) })
        }
    }
}

@Composable
private fun RowScope.NavTabItem(item: NavItem, active: Boolean, onSelect: () -> Unit) {
    val pillScale by animateFloatAsState(if (active) 1f else 0f, tween(220), label = "pill-scale")
    val tint = if (active) item.accent else NavInactive

    Column(
        Modifier
            .weight(1f)
            .clickable(
                interactionSource = androidx.compose.runtime.remember { MutableInteractionSource() },
                indication = null,
                onClick = onSelect,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .width(36.dp)
                .height(26.dp)
                .background(
                    if (pillScale > 0.01f) item.accent.copy(alpha = 0.16f * pillScale) else Color.Transparent,
                    RoundedCornerShape(14.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(item.icon, contentDescription = item.tab.label, tint = tint, modifier = Modifier.size(18.dp))
        }
        Text(
            item.tab.label,
            color = tint,
            fontSize = 9.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
