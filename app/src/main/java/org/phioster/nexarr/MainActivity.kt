package org.phioster.nexarr

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import org.phioster.nexarr.model.ServiceConfig
import org.phioster.nexarr.model.ServiceStatus
import org.phioster.nexarr.model.ServiceType
import org.phioster.nexarr.ui.DashboardViewModel

private val MatrixGreen = Color(0xFF00FF41)
private val Black = Color(0xFF000000)
private val Surface = Color(0xFF0A0A0A)
private val ErrRed = Color(0xFFFF5555)
private val Mono = FontFamily.Monospace

private val NexarrColors = darkColorScheme(
    primary = MatrixGreen,
    onPrimary = Black,
    background = Black,
    onBackground = MatrixGreen,
    surface = Surface,
    onSurface = MatrixGreen,
    surfaceVariant = Surface,
    onSurfaceVariant = MatrixGreen,
    outline = MatrixGreen.copy(alpha = 0.4f),
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = NexarrColors) {
                NexarrApp()
            }
        }
    }
}

@Composable
private fun NexarrApp(vm: DashboardViewModel = viewModel()) {
    var addOpen by remember { mutableStateOf(false) }
    if (addOpen) {
        AddServiceScreen(
            onCancel = { addOpen = false },
            onSave = { vm.addService(it); addOpen = false },
            onTest = { vm.test(it) },
        )
    } else {
        DashboardScreen(vm = vm, onAdd = { addOpen = true })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DashboardScreen(vm: DashboardViewModel, onAdd: () -> Unit) {
    val services by vm.services.collectAsState()
    val statuses by vm.statuses.collectAsState()

    Scaffold(
        containerColor = Black,
        topBar = {
            TopAppBar(
                title = { Text("> nexarr_", fontFamily = Mono, fontWeight = FontWeight.Bold, color = MatrixGreen) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Black, titleContentColor = MatrixGreen),
                actions = {
                    IconButton(onClick = { vm.refreshAll() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh", tint = MatrixGreen)
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd, containerColor = MatrixGreen, contentColor = Black) {
                Icon(Icons.Filled.Add, contentDescription = "Add service")
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            if (services.isEmpty()) {
                Spacer(Modifier.height(48.dp))
                Text(
                    "no services yet\n\ntap + to add a service",
                    fontFamily = Mono,
                    color = MatrixGreen.copy(alpha = 0.6f),
                    fontSize = 14.sp,
                )
            }
            services.forEach { svc ->
                ServiceCard(
                    config = svc,
                    status = statuses[svc.id],
                    onRemove = { vm.removeService(svc.id) },
                )
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun ServiceCard(config: ServiceConfig, status: ServiceStatus?, onRemove: () -> Unit) {
    val accent = Color(config.type.accent)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Surface),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp)) { Text("●", color = accent, fontSize = 12.sp) }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(config.label, fontFamily = Mono, fontWeight = FontWeight.Bold, color = MatrixGreen, fontSize = 18.sp)
                    Text(config.type.label, fontFamily = Mono, color = accent, fontSize = 12.sp)
                }
                val tag = when {
                    status == null -> "[...]"
                    status.ok -> "[ok]"
                    else -> "[err]"
                }
                Text(tag, fontFamily = Mono, color = if (status?.ok == true) MatrixGreen else ErrRed)
                Spacer(Modifier.width(4.dp))
                IconButton(onClick = onRemove) {
                    Icon(Icons.Filled.Delete, contentDescription = "Remove", tint = MatrixGreen.copy(alpha = 0.6f))
                }
            }
            Spacer(Modifier.height(10.dp))
            when {
                status == null -> Text("connecting…", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 13.sp)
                status.ok -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    status.stats.forEach { (k, v) ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(v, fontFamily = Mono, fontWeight = FontWeight.Bold, color = MatrixGreen, fontSize = 20.sp)
                            Text(k.uppercase(), fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 11.sp)
                        }
                    }
                }
                else -> Text(status.error ?: "error", fontFamily = Mono, color = ErrRed, fontSize = 12.sp)
            }
            status?.note?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, fontFamily = Mono, color = accent.copy(alpha = 0.8f), fontSize = 11.sp)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddServiceScreen(
    onCancel: () -> Unit,
    onSave: (ServiceConfig) -> Unit,
    onTest: suspend (ServiceConfig) -> ServiceStatus,
) {
    var type by remember { mutableStateOf(ServiceType.JELLYFIN) }
    var label by remember { mutableStateOf(ServiceType.JELLYFIN.label) }
    var labelEdited by remember { mutableStateOf(false) }
    var url by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var jellyLogin by remember { mutableStateOf(false) }
    var cfId by remember { mutableStateOf("") }
    var cfSecret by remember { mutableStateOf("") }
    var testResult by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // Keep label in sync with the chosen type until the user edits it manually.
    LaunchedEffect(type) { if (!labelEdited) label = type.label }

    val usesLogin = (type == ServiceType.JELLYFIN && jellyLogin) || type == ServiceType.NZBGET

    fun build(): ServiceConfig {
        val headers = buildMap {
            if (cfId.isNotBlank()) put("CF-Access-Client-Id", cfId.trim())
            if (cfSecret.isNotBlank()) put("CF-Access-Client-Secret", cfSecret.trim())
        }
        return ServiceConfig(
            type = type,
            label = label.ifBlank { type.label },
            baseUrl = url.trim(),
            apiKey = apiKey.trim(),
            username = username.trim(),
            password = password,
            useLogin = type == ServiceType.JELLYFIN && jellyLogin,
            customHeaders = headers,
        )
    }

    val canSave = url.isNotBlank() && when (type) {
        ServiceType.JELLYFIN -> if (jellyLogin) username.isNotBlank() && password.isNotBlank() else apiKey.isNotBlank()
        ServiceType.RADARR -> apiKey.isNotBlank()
        ServiceType.NZBGET -> username.isNotBlank() && password.isNotBlank()
    }

    Scaffold(
        containerColor = Black,
        topBar = {
            TopAppBar(
                title = { Text("add service", fontFamily = Mono, color = MatrixGreen) },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MatrixGreen)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Black, titleContentColor = MatrixGreen),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Row {
                ServiceType.entries.forEach { t ->
                    FilterChip(
                        selected = type == t,
                        onClick = { type = t },
                        label = { Text(t.label, fontFamily = Mono) },
                    )
                    Spacer(Modifier.width(8.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
            Field("Label", label) { label = it; labelEdited = true }
            Field("Base URL (https://…)", url) { url = it }

            // Jellyfin can auth by API key or by login.
            if (type == ServiceType.JELLYFIN) {
                Spacer(Modifier.height(8.dp))
                Row {
                    FilterChip(selected = !jellyLogin, onClick = { jellyLogin = false }, label = { Text("API key", fontFamily = Mono) })
                    Spacer(Modifier.width(8.dp))
                    FilterChip(selected = jellyLogin, onClick = { jellyLogin = true }, label = { Text("Login", fontFamily = Mono) })
                }
            }

            if (type == ServiceType.RADARR || (type == ServiceType.JELLYFIN && !jellyLogin)) {
                Field("API key", apiKey) { apiKey = it }
            }
            if (usesLogin) {
                Field("Username", username) { username = it }
                Field("Password", password, isPassword = true) { password = it }
            }

            Spacer(Modifier.height(8.dp))
            Text("Cloudflare Access (optional)", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 12.sp)
            Field("CF-Access-Client-Id", cfId) { cfId = it }
            Field("CF-Access-Client-Secret", cfSecret, isPassword = true) { cfSecret = it }

            Spacer(Modifier.height(16.dp))
            Row {
                OutlinedButton(onClick = {
                    testResult = "testing…"
                    scope.launch {
                        val r = onTest(build())
                        testResult = if (r.ok) "ok: " + r.stats.joinToString { "${it.first}=${it.second}" } else "error: ${r.error}"
                    }
                }) { Text("Test", fontFamily = Mono) }
                Spacer(Modifier.width(12.dp))
                FilledTonalButton(onClick = { onSave(build()) }, enabled = canSave) {
                    Text("Save", fontFamily = Mono)
                }
            }
            testResult?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, fontFamily = Mono, color = if (it.startsWith("ok")) MatrixGreen else Color(0xFFFFAA00), fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    isPassword: Boolean = false,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, fontFamily = Mono) },
        singleLine = true,
        visualTransformation = if (isPassword) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = Mono),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    )
}
