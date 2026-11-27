package it.libraccio.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import it.libraccio.app.ui.LibraryViewModel
import it.libraccio.app.ui.add.EditBookScreen
import it.libraccio.app.ui.add.ScanScreen
import it.libraccio.app.ui.add.SearchScreen
import it.libraccio.app.ui.detail.BookDetailScreen
import it.libraccio.app.ui.list.ListScreen
import it.libraccio.app.ui.room.RoomScreen
import it.libraccio.app.ui.settings.SettingsScreen
import it.libraccio.app.ui.stats.StatsScreen
import it.libraccio.app.ui.theme.LibraccioTheme
import it.libraccio.app.ui.theme.rememberIsNight

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: LibraryViewModel = viewModel()
            val settings by vm.settings.collectAsStateWithLifecycle()
            val night = rememberIsNight(settings.nightMode)
            LaunchedEffect(night) {
                val style = if (night) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT), navigationBarStyle = style)
            }
            LibraccioTheme(dark = night) {
                AppNavigation(vm, night)
            }
        }
    }
}

@Composable
private fun AppNavigation(vm: LibraryViewModel, night: Boolean) {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "room") {
        composable("room") {
            RoomScreen(
                vm = vm,
                night = night,
                onOpenBook = { nav.navigate("detail/$it") },
                onScan = { nav.navigate("scan") },
                onSearch = { nav.navigate("search") },
                onManual = { nav.navigate("manual") },
                onList = { nav.navigate("list") },
                onStats = { nav.navigate("stats") },
                onSettings = { nav.navigate("settings") },
            )
        }
        composable("list") {
            ListScreen(vm, onBack = { nav.popBackStack() }, onOpenBook = { nav.navigate("detail/$it") })
        }
        composable("detail/{id}") { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            BookDetailScreen(vm, id, onBack = { nav.popBackStack() }, onEdit = { nav.navigate("edit/$id") })
        }
        composable("edit/{id}") { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            EditBookScreen(vm, bookId = id, prefillIsbn = null, onBack = { nav.popBackStack() }, onSaved = { nav.popBackStack() })
        }
        composable(
            "manual?isbn={isbn}",
            arguments = listOf(navArgument("isbn") { type = NavType.StringType; nullable = true; defaultValue = null }),
        ) { entry ->
            EditBookScreen(
                vm,
                bookId = null,
                prefillIsbn = entry.arguments?.getString("isbn"),
                onBack = { nav.popBackStack() },
                onSaved = { id ->
                    nav.popBackStack()
                    nav.navigate("detail/$id")
                },
            )
        }
        composable("scan") {
            ScanScreen(
                vm,
                onBack = { nav.popBackStack() },
                onManual = { isbn -> nav.navigate("manual?isbn=$isbn") },
                onOpenBook = { nav.navigate("detail/$it") },
            )
        }
        composable("search") {
            SearchScreen(
                vm,
                onBack = { nav.popBackStack() },
                onManual = { nav.navigate("manual") },
                onOpenBook = { nav.navigate("detail/$it") },
            )
        }
        composable("stats") {
            StatsScreen(vm, onBack = { nav.popBackStack() }, onOpenBook = { nav.navigate("detail/$it") })
        }
        composable("settings") {
            SettingsScreen(vm, onBack = { nav.popBackStack() })
        }
    }
}
