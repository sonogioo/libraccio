package it.libraccio.app.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.libraccio.app.data.NightMode
import it.libraccio.app.sound.Sfx
import it.libraccio.app.ui.LibraryViewModel
import it.libraccio.app.ui.theme.RoomThemes
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(vm: LibraryViewModel, onBack: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch { message = vm.exportTo(uri) }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch { message = vm.importFrom(uri) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Impostazioni") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Indietro") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Section("La stanza")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RoomThemes.all.forEach { t ->
                    FilterChip(
                        selected = settings.roomTheme == t.key,
                        onClick = { vm.updateSettings { it.copy(roomTheme = t.key) } },
                        label = { Text(t.label) },
                    )
                }
            }

            Section("Modalità notte")
            Text(
                "Di notte le luci si abbassano e si accende la lampada. In automatico dalle 20 alle 7.",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NightMode.entries.forEach { m ->
                    FilterChip(
                        selected = settings.nightMode == m,
                        onClick = { vm.updateSettings { it.copy(nightMode = m) } },
                        label = { Text(m.label) },
                    )
                }
            }

            SwitchRow("Effetti sonori", "Pagine, scaffali, scansioni", settings.sounds) { on ->
                vm.updateSettings { it.copy(sounds = on) }
                if (on) vm.play(Sfx.PAGE)
            }
            SwitchRow("Pila sulla scrivania", "I libri in lettura e da leggere stanno impilati sulla scrivania", settings.pileOnDesk) { on ->
                vm.updateSettings { it.copy(pileOnDesk = on) }
            }

            Section("Obiettivo annuale")
            Text("${settings.yearlyGoal} libri all'anno", style = MaterialTheme.typography.bodyLarge)
            Slider(
                value = settings.yearlyGoal.toFloat(),
                onValueChange = { v -> vm.updateSettings { it.copy(yearlyGoal = v.roundToInt().coerceIn(1, 150)) } },
                valueRange = 1f..150f,
            )

            HorizontalDivider()
            Section("Ricerca libri")
            Text(
                "Libraccio cerca su Google Books e, se non risponde, su Open Library. " +
                    "Senza chiave Google condivide una quota che a volte si esaurisce: " +
                    "puoi crearne una gratuita su console.cloud.google.com (API \"Books\") e incollarla qui.",
                style = MaterialTheme.typography.bodySmall,
            )
            var keyField by remember(settings.googleApiKey) { mutableStateOf(settings.googleApiKey) }
            OutlinedTextField(
                value = keyField,
                onValueChange = { v ->
                    keyField = v
                    vm.updateSettings { it.copy(googleApiKey = v.trim()) }
                },
                label = { Text("Chiave API Google Books (facoltativa)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            HorizontalDivider()
            Section("Backup")
            Text(
                "Esporta tutta la libreria (libri, ricordi, citazioni) in un file JSON da tenere al sicuro, per esempio su Google Drive.",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = {
                    val stamp = SimpleDateFormat("yyyyMMdd", Locale.ITALY).format(Date())
                    exportLauncher.launch("libraccio-backup-$stamp.json")
                }) { Text("Esporta") }
                OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }) {
                    Text("Importa")
                }
            }
            message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }

            HorizontalDivider()
            Text(
                "Libraccio · dati dei libri da Google Books e Open Library. Tutto resta sul tuo telefono.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Section(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
