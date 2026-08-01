package com.wanderwk.d3saveeditor.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wanderwk.d3saveeditor.AppViewModel
import com.wanderwk.d3saveeditor.ui.screens.CoinsScreen
import com.wanderwk.d3saveeditor.ui.screens.ExportScreen
import com.wanderwk.d3saveeditor.ui.screens.GemsScreen
import com.wanderwk.d3saveeditor.ui.screens.HomeScreen
import com.wanderwk.d3saveeditor.ui.screens.ItemsScreen
import com.wanderwk.d3saveeditor.ui.screens.ParagonScreen
import com.wanderwk.d3saveeditor.ui.screens.SupportScreen
import com.wanderwk.d3saveeditor.ui.theme.D3SaveEditorTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented Compose UI tests for the 7 main screens (Home, Coins, Items,
 * Gems, Paragon, Export, Support), run on a real device/emulator via
 * `gradle connectedAndroidTest`.
 *
 * No save is imported in any of these tests (that requires a real SAF file
 * picker result, not reproducible headlessly) -- each screen's "no save
 * loaded" state is exactly what most users see on first install, and it's
 * the state most likely to regress silently (a crash here blocks everyone,
 * not just people mid-edit). What's verified per screen:
 *   - it renders without throwing
 *   - its expected placeholder/empty-state text is actually on screen
 * `AppRootNavigationTest` below additionally exercises the bottom nav bar
 * itself, tapping through all 7 tabs in one real AppViewModel/AppRoot tree.
 */
@RunWith(AndroidJUnit4::class)
class ScreensUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun newViewModel(): AppViewModel =
        AppViewModel(ApplicationProvider.getApplicationContext())

    @Test
    fun homeScreen_showsDisclaimerAndImportDropzone() {
        composeRule.setContent { D3SaveEditorTheme { HomeScreen(newViewModel()) } }
        composeRule.onNodeWithText("Importar Save").assertIsDisplayed()
        composeRule.onNodeWithText(
            "Ferramenta de uso pessoal para edição de saves offline do Diablo III. Não afiliado à Blizzard Entertainment.",
        ).assertIsDisplayed()
    }

    @Test
    fun coinsScreen_withNoSave_showsEmptyStateHint() {
        composeRule.setContent { D3SaveEditorTheme { CoinsScreen(newViewModel()) } }
        composeRule.onNodeWithText("Importe um save na aba Home para começar a editar.").assertIsDisplayed()
    }

    @Test
    fun itemsScreen_withNoSave_showsEmptyStateHint() {
        composeRule.setContent { D3SaveEditorTheme { ItemsScreen(newViewModel()) } }
        composeRule.onNodeWithText("Importe um save na aba Home para começar a editar.").assertIsDisplayed()
    }

    @Test
    fun gemsScreen_withNoSave_showsEmptyStateHint() {
        composeRule.setContent { D3SaveEditorTheme { GemsScreen(newViewModel()) } }
        composeRule.onNodeWithText("Importe um save na aba Home para começar a editar.").assertIsDisplayed()
    }

    @Test
    fun paragonScreen_withNoSave_showsEmptyStateHint() {
        composeRule.setContent { D3SaveEditorTheme { ParagonScreen(newViewModel()) } }
        composeRule.onNodeWithText("Importe um save na aba Home para começar a editar.").assertIsDisplayed()
    }

    @Test
    fun exportScreen_withNoSave_showsEmptyStateHint() {
        composeRule.setContent { D3SaveEditorTheme { ExportScreen(newViewModel()) } }
        composeRule.onNodeWithText("Importe um save na aba Home para começar a editar.").assertIsDisplayed()
    }

    @Test
    fun supportScreen_showsDonationCard() {
        composeRule.setContent { D3SaveEditorTheme { SupportScreen() } }
        composeRule.onNodeWithText("Apoie o Desenvolvimento").assertIsDisplayed()
    }
}

/**
 * Exercises the real bottom nav bar (AppRoot), tapping through all 7 tabs
 * in a single tree -- catches regressions in tab switching/state hoisting
 * that per-screen isolated tests above wouldn't see.
 */
@RunWith(AndroidJUnit4::class)
class AppRootNavigationTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun tappingEveryBottomNavTab_switchesScreenWithoutCrashing() {
        val viewModel = AppViewModel(ApplicationProvider.getApplicationContext())
        composeRule.setContent { D3SaveEditorTheme { AppRoot(viewModel) } }

        // Starts on Home.
        composeRule.onNodeWithText("Importar Save").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Moedas").performClick()
        composeRule.onNodeWithText("Importe um save na aba Home para começar a editar.").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Itens").performClick()
        composeRule.onNodeWithText("Importe um save na aba Home para começar a editar.").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Gemas").performClick()
        composeRule.onNodeWithText("Importe um save na aba Home para começar a editar.").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Paragon").performClick()
        composeRule.onNodeWithText("Importe um save na aba Home para começar a editar.").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Exportar").performClick()
        composeRule.onNodeWithText("Importe um save na aba Home para começar a editar.").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Doações").performClick()
        composeRule.onNodeWithText("Apoie o Desenvolvimento").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Home").performClick()
        composeRule.onNodeWithText("Importar Save").assertIsDisplayed()
    }
}
