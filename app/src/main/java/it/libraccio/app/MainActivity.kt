@file:OptIn(ExperimentalSharedTransitionApi::class)

package it.libraccio.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import it.libraccio.app.data.NightMode
import it.libraccio.app.data.ReadingStatus
import it.libraccio.app.data.readingStatus
import it.libraccio.app.ui.LibraryViewModel
import it.libraccio.app.ui.LocalNavScope
import it.libraccio.app.ui.LocalSharedScope
import it.libraccio.app.ui.add.EditBookScreen
import it.libraccio.app.ui.add.ScanScreen
import it.libraccio.app.ui.add.SearchScreen
import it.libraccio.app.ui.detail.BookDetailScreen
import it.libraccio.app.ui.list.ListScreen
import it.libraccio.app.ui.room.RoomCommand
import it.libraccio.app.ui.room.RoomScreen
import it.libraccio.app.ui.settings.SettingsScreen
import it.libraccio.app.ui.stats.StatsScreen
import it.libraccio.app.ui.theme.LibraccioTheme
import it.libraccio.app.ui.theme.rememberIsNight
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {
    private val demoAction = MutableStateFlow<String?>(null)
    private val demoSeed = MutableStateFlow(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleDemo(intent)
        setContent {
            val vm: LibraryViewModel = viewModel()
            val settings by vm.settings.collectAsStateWithLifecycle()
            val night = rememberIsNight(settings.nightMode)
            val seed by demoSeed.collectAsState()
            val action by demoAction.collectAsState()
            LaunchedEffect(seed) { if (seed) vm.seedDemo() }
            LaunchedEffect(night) {
                val nav = if (night) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT), navigationBarStyle = nav)
            }
            LibraccioTheme(dark = night) {
                AppNavigation(vm, night, action) { demoAction.value = null }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDemo(intent)
    }

    /** Modalità dimostrativa: esiste solo nella build usata per gli screenshot automatici. */
    private fun handleDemo(intent: Intent?) {
        if (!BuildConfig.DEMO || intent == null) return
        if (intent.getBooleanExtra("demo", false)) demoSeed.value = true
        intent.getStringExtra("demoAction")?.let { demoAction.value = it }
    }
}

private fun NavGraphBuilder.screen(
    route: String,
    arguments: List<NamedNavArgument> = emptyList(),
    content: @Composable (NavBackStackEntry) -> Unit,
) = composable(route, arguments) { entry ->
    CompositionLocalProvider(LocalNavScope provides this) { content(entry) }
}

@Composable
private fun AppNavigation(vm: LibraryViewModel, night: Boolean, demoAction: String?, onDemoHandled: () -> Unit) {
    val nav = rememberNavController()
    var roomCommand by remember { mutableStateOf<RoomCommand?>(null) }

    LaunchedEffect(demoAction) {
        val a = demoAction ?: return@LaunchedEffect
        fun toRoom() { nav.popBackStack("room", inclusive = false) }
        when (a) {
            "pick" -> { toRoom(); roomCommand = RoomCommand.PickFirst }
            "closeup" -> { toRoom(); roomCommand = RoomCommand.CloseUp }
            "home" -> { toRoom(); roomCommand = RoomCommand.Home }
            "night" -> { toRoom(); vm.updateSettings { it.copy(nightMode = NightMode.ON) } }
            "day" -> { toRoom(); vm.updateSettings { it.copy(nightMode = NightMode.OFF) } }
            "open" -> {
                val b = vm.books.value.firstOrNull { it.readingStatus == ReadingStatus.READ && it.coverUrl != null }
                    ?: vm.books.value.firstOrNull()
                b?.let { nav.navigate("detail/${it.id}") }
            }
            "list" -> nav.navigate("list")
            "stats" -> nav.navigate("stats")
        }
        onDemoHandled()
    }

    SharedTransitionLayout {
        CompositionLocalProvider(LocalSharedScope provides this) {
            NavHost(
                navController = nav,
                startDestination = "room",
                enterTransition = { fadeIn(tween(380)) },
                exitTransition = { fadeOut(tween(380)) },
                popEnterTransition = { fadeIn(tween(380)) },
                popExitTransition = { fadeOut(tween(380)) },
            ) {
                screen("room") {
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
                        command = roomCommand,
                        onCommandHandled = { roomCommand = null },
                    )
                }
                screen("list") {
                    ListScreen(vm, onBack = { nav.popBackStack() }, onOpenBook = { nav.navigate("detail/$it") })
                }
                screen("detail/{id}") { entry ->
                    val id = entry.arguments?.getString("id").orEmpty()
                    BookDetailScreen(vm, id, onBack = { nav.popBackStack() }, onEdit = { nav.navigate("edit/$id") })
                }
                screen("edit/{id}") { entry ->
                    val id = entry.arguments?.getString("id").orEmpty()
                    EditBookScreen(vm, bookId = id, prefillIsbn = null, onBack = { nav.popBackStack() }, onSaved = { nav.popBackStack() })
                }
                screen(
                    "manual?isbn={isbn}",
                    listOf(navArgument("isbn") { type = NavType.StringType; nullable = true; defaultValue = null }),
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
                screen("scan") {
                    ScanScreen(
                        vm,
                        onBack = { nav.popBackStack() },
                        onManual = { isbn -> nav.navigate("manual?isbn=$isbn") },
                        onOpenBook = { nav.navigate("detail/$it") },
                    )
                }
                screen("search") {
                    SearchScreen(
                        vm,
                        onBack = { nav.popBackStack() },
                        onManual = { nav.navigate("manual") },
                        onOpenBook = { nav.navigate("detail/$it") },
                    )
                }
                screen("stats") {
                    StatsScreen(vm, onBack = { nav.popBackStack() }, onOpenBook = { nav.navigate("detail/$it") })
                }
                screen("settings") {
                    SettingsScreen(vm, onBack = { nav.popBackStack() })
                }
            }
        }
    }
}
