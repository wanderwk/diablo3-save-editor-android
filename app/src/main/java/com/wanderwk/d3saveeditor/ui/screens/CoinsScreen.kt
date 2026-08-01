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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wanderwk.d3saveeditor.AppViewModel
import com.wanderwk.d3saveeditor.core.CurrencyRepository
import com.wanderwk.d3saveeditor.ui.components.SmallPill
import com.wanderwk.d3saveeditor.ui.components.formatThousands
import com.wanderwk.d3saveeditor.ui.theme.ChipDark2
import com.wanderwk.d3saveeditor.ui.theme.PrimaryAccent
import com.wanderwk.d3saveeditor.ui.theme.PrimaryContainer
import com.wanderwk.d3saveeditor.ui.theme.SurfaceContainer
import com.wanderwk.d3saveeditor.ui.theme.TextMuted
import com.wanderwk.d3saveeditor.ui.theme.TextPrimary

private val CURRENCY_COLORS = listOf(
    Color(0xFFF5C563), Color(0xFFA06BD9), Color(0xFF5FAE6A), Color(0xFF5B9BD5),
    Color(0xFFE8956B), Color(0xFFE05C7A), Color(0xFFC9A227),
)

@Composable
fun CoinsScreen(viewModel: AppViewModel) {
    if (viewModel.saveInfo == null) {
        EmptyStateHint()
        return
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CurrencyRepository.CURRENCIES.forEachIndexed { i, meta ->
            val value = viewModel.currencies[meta.name] ?: 0L
            CurrencyCard(
                name = meta.name,
                value = value,
                max = meta.maxValue,
                color = CURRENCY_COLORS[i % CURRENCY_COLORS.size],
                onChange = { viewModel.setCurrency(meta.name, it) },
            )
        }
    }
}

@Composable
private fun CurrencyCard(name: String, value: Long, max: Long, color: Color, onChange: (Long) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toString()) }

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(SurfaceContainer).padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(36.dp).clip(RoundedCornerShape(14.dp)).background(color.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(name.take(1), color = color, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(name, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(formatThousands(value), color = TextMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StepButton("−") {
                val step = (value / 20).coerceAtLeast(1)
                onChange((value - step).coerceIn(0, max))
            }
            BasicTextField(
                value = text,
                onValueChange = { input ->
                    val digits = input.filter { it.isDigit() }
                    text = digits
                    digits.toLongOrNull()?.let { onChange(it.coerceIn(0, max)) }
                },
                singleLine = true,
                textStyle = TextStyle(color = TextPrimary, fontSize = 14.sp, textAlign = TextAlign.Center),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(ChipDark2)
                    .padding(vertical = 10.dp, horizontal = 8.dp),
            )
            StepButton("+") {
                val step = (value / 20).coerceAtLeast(1)
                onChange((value + step).coerceIn(0, max))
            }
            SmallPill("MAX", bg = PrimaryContainer, fg = PrimaryAccent, onClick = { onChange(max) })
        }
    }
}

@Composable
private fun StepButton(symbol: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(30.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(ChipDark2)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(symbol, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun EmptyStateHint() {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            "Importe um save na aba Home para começar a editar.",
            color = TextMuted,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
        )
    }
}
