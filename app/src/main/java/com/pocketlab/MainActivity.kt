package com.pocketlab

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketlab.net.LanDevice
import com.pocketlab.net.LanScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val PocketDark = darkColorScheme(
    primary = Color(0xFF00E676),
    onPrimary = Color(0xFF00210F),
    background = Color(0xFF0A0A0A),
    onBackground = Color(0xFFEDEDED),
    surface = Color(0xFF161616),
    onSurface = Color(0xFFEDEDED),
    surfaceVariant = Color(0xFF1F1F1F),
    onSurfaceVariant = Color(0xFFB0B0B0),
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = PocketDark) {
                PocketLabApp()
            }
        }
    }
}

private sealed interface Screen {
    data object Home : Screen
    data object Lan : Screen
}

@Composable
private fun PocketLabApp() {
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    when (screen) {
        Screen.Home -> HomeScreen(onOpenLan = { screen = Screen.Lan })
        Screen.Lan -> LanScreen(onBack = { screen = Screen.Home })
    }
}

@Composable
private fun HomeScreen(onOpenLan: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Text(
            text = "PocketLab",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = "Мультитул для локальной сети",
            fontSize = 14.sp,
            color = Color(0xFF8A8A8A),
        )

        Spacer(Modifier.height(24.dp))

        ModuleCard(
            emoji = "🌐",
            title = "Сканер сети",
            subtitle = "Устройства в Wi-Fi, IP и открытые порты",
            enabled = true,
            onClick = onOpenLan,
        )
        Spacer(Modifier.height(10.dp))
        ModuleCard(
            emoji = "📺",
            title = "Пульт для ТВ",
            subtitle = "Chromecast, DLNA, Samsung, LG",
            enabled = false,
            onClick = {},
        )
        Spacer(Modifier.height(10.dp))
        ModuleCard(
            emoji = "📡",
            title = "BLE-сканер",
            subtitle = "Bluetooth-устройства вокруг",
            enabled = false,
            onClick = {},
        )
        Spacer(Modifier.height(10.dp))
        ModuleCard(
            emoji = "🏷️",
            title = "NFC-инструменты",
            subtitle = "Чтение и запись меток",
            enabled = false,
            onClick = {},
        )
        Spacer(Modifier.height(10.dp))
        ModuleCard(
            emoji = "▦",
            title = "QR-коды",
            subtitle = "Генерация и сканирование",
            enabled = false,
            onClick = {},
        )

        Spacer(Modifier.height(28.dp))
        Text(
            text = "v0.1.0 · Android 8+",
            fontSize = 12.sp,
            color = Color(0xFF5A5A5A),
        )
    }
}

@Composable
private fun ModuleCard(
    emoji: String,
    title: String,
    subtitle: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) MaterialTheme.colorScheme.surface else Color(0xFF121212),
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = emoji, fontSize = 26.sp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface else Color(0xFF6E6E6E),
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = 13.sp,
                    color = Color(0xFF7E7E7E),
                )
            }
            Text(
                text = if (enabled) "›" else "скоро",
                fontSize = if (enabled) 26.sp else 12.sp,
                color = if (enabled) MaterialTheme.colorScheme.primary else Color(0xFF5A5A5A),
            )
        }
    }
}

@Composable
private fun LanScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var scanning by remember { mutableStateOf(false) }
    var scannedOnce by remember { mutableStateOf(false) }
    var devices by remember { mutableStateOf<List<LanDevice>>(emptyList()) }
    var localIp by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        localIp = withContext(Dispatchers.IO) { LanScanner.localIpv4Address() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 20.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack, enabled = !scanning) {
                Text("← Назад", color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = "Сканер сети",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }

        Text(
            text = "Ваш IP: ${localIp ?: "определяется..."}",
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
            color = Color(0xFF8A8A8A),
        )

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = {
                if (!scanning) {
                    scanning = true
                    scannedOnce = true
                    error = null
                    devices = emptyList()
                    scope.launch {
                        try {
                            val ip = localIp ?: withContext(Dispatchers.IO) {
                                LanScanner.localIpv4Address()
                            }
                            if (ip == null) {
                                error = "Не удалось определить IP. Проверьте, что телефон подключён к Wi-Fi."
                            } else {
                                localIp = ip
                                devices = LanScanner.scan(ip.substringBeforeLast("."))
                            }
                        } catch (e: Exception) {
                            error = "Ошибка: ${e.message}"
                        } finally {
                            scanning = false
                        }
                    }
                }
            },
            enabled = !scanning,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (scanning) "Сканирую..." else "Сканировать сеть")
        }

        if (scanning) {
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        error?.let { message ->
            Spacer(Modifier.height(12.dp))
            Text(
                text = message,
                fontSize = 14.sp,
                color = Color(0xFFFF6B6B),
            )
        }

        if (scannedOnce && !scanning && error == null) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Найдено устройств: ${devices.size}",
                fontSize = 13.sp,
                color = Color(0xFF8A8A8A),
            )
        }

        Spacer(Modifier.height(8.dp))

        if (scannedOnce && !scanning && devices.isEmpty() && error == null) {
            Text(
                text = "Ничего не найдено. Часть устройств не отвечает на TCP-порты — это нормально.",
                fontSize = 13.sp,
                color = Color(0xFF8A8A8A),
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(devices, key = { it.ip }) { device ->
                DeviceRow(device)
            }
        }
    }
}

@Composable
private fun DeviceRow(device: LanDevice) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = device.ip,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "${device.openPorts.size} порт(ов)",
                    fontSize = 12.sp,
                    color = Color(0xFF8A8A8A),
                )
            }
            device.hostname?.let { hostname ->
                Text(
                    text = hostname,
                    fontSize = 13.sp,
                    color = Color(0xFF8A8A8A),
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = device.openPorts.joinToString(", "),
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
