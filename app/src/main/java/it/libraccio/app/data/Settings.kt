package it.libraccio.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class NightMode(val label: String) { AUTO("Automatica"), ON("Sempre"), OFF("Mai") }

enum class SortMode(val label: String) {
    AUTHOR("Autore"), TITLE("Titolo"), COLOR("Colore"), ADDED("Arrivo"), HEIGHT("Altezza"), GENRE("Genere")
}

data class AppSettings(
    val nightMode: NightMode = NightMode.AUTO,
    val sounds: Boolean = true,
    val roomTheme: String = "STUDIO",
    val yearlyGoal: Int = 24,
    val sort: SortMode = SortMode.AUTHOR,
    val pileOnDesk: Boolean = true,
)

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("libraccio_settings", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(read())
    val state: StateFlow<AppSettings> = _state.asStateFlow()

    private fun read() = AppSettings(
        nightMode = NightMode.entries.firstOrNull { it.name == prefs.getString("night", null) } ?: NightMode.AUTO,
        sounds = prefs.getBoolean("sounds", true),
        roomTheme = prefs.getString("roomTheme", "STUDIO") ?: "STUDIO",
        yearlyGoal = prefs.getInt("goal", 24),
        sort = SortMode.entries.firstOrNull { it.name == prefs.getString("sort", null) } ?: SortMode.AUTHOR,
        pileOnDesk = prefs.getBoolean("pile", true),
    )

    fun update(transform: (AppSettings) -> AppSettings) {
        val s = transform(_state.value)
        prefs.edit()
            .putString("night", s.nightMode.name)
            .putBoolean("sounds", s.sounds)
            .putString("roomTheme", s.roomTheme)
            .putInt("goal", s.yearlyGoal)
            .putString("sort", s.sort.name)
            .putBoolean("pile", s.pileOnDesk)
            .apply()
        _state.value = s
    }
}
