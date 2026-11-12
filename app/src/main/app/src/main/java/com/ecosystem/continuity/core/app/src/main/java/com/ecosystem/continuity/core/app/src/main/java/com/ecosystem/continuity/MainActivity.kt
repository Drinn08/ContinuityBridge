package com.ecosystem.continuity

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ecosystem.continuity.core.ConnectionForegroundService
import com.ecosystem.continuity.core.EcosystemPacket

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ContinuitySetupScreen()
                }
            }
        }
    }

    @SuppressLint("BatteryLife")
    @Composable
    fun ContinuitySetupScreen() {
        val status by ConnectionForegroundService.connectionStatus.collectAsState()
        val lastMessage by ConnectionForegroundService.lastReceivedMessage.collectAsState()

        val permissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) {
            val serviceIntent = Intent(this, ConnectionForegroundService::class.java)
            startForegroundService(serviceIntent)
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "Continuity Bridge", style = MaterialTheme.typography.headlineMedium)
            Text(text = "Пристрій: ${Build.MANUFACTURER} ${Build.MODEL}")
            
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "Статус: $status", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "Останній сигнал: $lastMessage")
                }
            }

            Button(
                onClick = {
                    val perms = mutableListOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        perms.add(Manifest.permission.BLUETOOTH_SCAN)
                        perms.add(Manifest.permission.BLUETOOTH_ADVERTISE)
                        perms.add(Manifest.permission.BLUETOOTH_CONNECT)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        perms.add(Manifest.permission.NEARBY_WIFI_DEVICES)
                        perms.add(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    permissionLauncher.launch(perms.toTypedArray())
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("1. Надати дозволи та запустити міст")
            }

            OutlinedButton(
                onClick = {
                    val pm = getSystemService(PowerManager::class.java)
                    if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = Uri.parse("package:$packageName")
                        }
                        startActivity(intent)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("2. Зняти обмеження батареї (Обхід сну)")
            }

            Button(
                onClick = {
                    ConnectionForegroundService.sendPacket(
                        EcosystemPacket(
                            type = "PING",
                            senderDevice = Build.MODEL,
                            body = "Привіт! Екосистема працює миттєво!"
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("3. Надіслати тестовий сигнал (Ping)")
            }
        }
    }
}
