package com.wanderwk.d3saveeditor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.wanderwk.d3saveeditor.ui.AppRoot
import com.wanderwk.d3saveeditor.ui.theme.BgBase
import com.wanderwk.d3saveeditor.ui.theme.D3SaveEditorTheme

class MainActivity : ComponentActivity() {
    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            D3SaveEditorTheme {
                Box(Modifier.fillMaxSize().background(BgBase)) {
                    AppRoot(viewModel)
                }
            }
        }
    }
}
