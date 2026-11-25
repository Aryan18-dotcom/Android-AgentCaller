package com.aryan.directsim

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

private val Ink = Color(0xFF10111A)
private val Panel = Color(0xFF1B1D2A)
private val Purple = Color(0xFF9B87F5)

class MainActivity : ComponentActivity() {
    private var dialerSelected by mutableStateOf(false)
    private val roleLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        dialerSelected = isDefaultDialer()
    }
    private var pendingEnable by mutableStateOf(false)
    private val micPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && pendingEnable) Storage.setEnabled(this, true)
        pendingEnable = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dialerSelected = isDefaultDialer()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Purple, background = Ink, surface = Panel)) {
                var enabled by remember { mutableStateOf(Storage.enabled(this)) }
                var refresh by remember { mutableIntStateOf(0) }
                Surface(Modifier.fillMaxSize(), color = Ink) {
                    Column(Modifier.fillMaxSize().padding(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("Personal Call Assistant", style = MaterialTheme.typography.headlineSmall, color = Color.White)
                        Text("Direct SIM · Experimental build", color = Color.LightGray)

                        Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(22.dp)) {
                            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    Column(Modifier.weight(1f)) {
                                        Text("Agent mode", color = Color.White, style = MaterialTheme.typography.titleLarge)
                                        Text(if (enabled) "ON · incoming calls will be auto-answered" else "OFF · incoming calls should ring normally", color = Color.LightGray)
                                    }
                                    Switch(checked = enabled, onCheckedChange = { turnOn ->
                                        if (!turnOn) {
                                            enabled = false
                                            Storage.setEnabled(this@MainActivity, false)
                                        } else if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                            enabled = true
                                            Storage.setEnabled(this@MainActivity, true)
                                        } else {
                                            pendingEnable = true
                                            micPermission.launch(Manifest.permission.RECORD_AUDIO)
                                        }
                                    })
                                }
                                Button(onClick = {
                                    if (Build.VERSION.SDK_INT >= 29) {
                                        val rm = getSystemService(Context.ROLE_SERVICE) as RoleManager
                                        if (rm.isRoleAvailable(RoleManager.ROLE_DIALER) && !rm.isRoleHeld(RoleManager.ROLE_DIALER)) {
                                            roleLauncher.launch(rm.createRequestRoleIntent(RoleManager.ROLE_DIALER))
                                        }
                                    }
                                }, modifier = Modifier.fillMaxWidth()) {
                                    Text(if (dialerSelected) "Default phone app is selected" else "Set as default phone app")
                                }
                                Text("The app must be your default phone app to receive call events and answer or end SIM calls. You can switch back to Motorola Phone in Android Settings.", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                            }
                        }

                        Text("Notes & reminders", color = Color.White, style = MaterialTheme.typography.titleLarge)
                        val entries = remember(refresh) { Storage.all(this) }
                        if (entries.isEmpty()) Text("No notes or reminders saved yet.", color = Color.LightGray)
                        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(entries) { entry ->
                                Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(16.dp)) {
                                    Column(Modifier.fillMaxWidth().padding(14.dp)) {
                                        Text(entry.first.uppercase(), color = Purple, style = MaterialTheme.typography.labelLarge)
                                        Text(entry.second, color = Color.White)
                                        Text(entry.third, color = Color.LightGray, style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                        Text("Audio experiment: the app uses speakerphone acoustics and Android speech recognition. Motorola/Android may block microphone access during SIM calls. Spoken choices only; DTMF keypad input is not implemented.", color = Color(0xFFFFC875), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        dialerSelected = isDefaultDialer()
    }

    private fun isDefaultDialer(): Boolean {
        if (Build.VERSION.SDK_INT < 29) return false
        val rm = getSystemService(Context.ROLE_SERVICE) as RoleManager
        return rm.isRoleHeld(RoleManager.ROLE_DIALER)
    }
}
