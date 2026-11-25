package com.aryan.directsim

import android.Manifest
import android.annotation.SuppressLint
import android.app.role.RoleManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.telecom.TelecomManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    private var resumeTick by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            val neutralDarkColors = darkColorScheme(
                primary = Color(0xFF10B981),
                onPrimary = Color(0xFF0A0A0B),
                secondary = Color(0xFFA1A1AA),
                background = Color(0xFF111113),
                surface = Color(0xFF1A1A1E),
                surfaceVariant = Color(0xFF242429),
                onBackground = Color(0xFFF4F4F6),
                onSurface = Color(0xFFF4F4F6),
                onSurfaceVariant = Color(0xFFA1A1AA),
                outline = Color(0xFF323238)
            )

            MaterialTheme(colorScheme = neutralDarkColors) {
                DirectSimApp(resumeTick = resumeTick)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        resumeTick++
    }
}

private fun checkIsDefaultPhoneApp(context: Context): Boolean {
    val telecom = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
    val isTelecomDefault = telecom.defaultDialerPackage == context.packageName
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val roleManager = context.getSystemService(RoleManager::class.java)
        isTelecomDefault || roleManager.isRoleHeld(RoleManager.ROLE_DIALER)
    } else {
        isTelecomDefault
    }
}

private fun getMissingRuntimePermissions(context: Context): List<String> {
    val required = mutableListOf(Manifest.permission.RECORD_AUDIO)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        required.add(Manifest.permission.POST_NOTIFICATIONS)
    }
    return required.filter { context.checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
}

@SuppressLint("MissingPermission")
private fun triggerCarrierMmiCode(context: Context, mmiCode: String) {
    try {
        val telecom = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
        val uri = Uri.fromParts("tel", mmiCode, null)
        telecom.placeCall(uri, Bundle())
    } catch (e: Exception) {
        Toast.makeText(context, "Dial $mmiCode to update forwarding", Toast.LENGTH_LONG).show()
    }
}

private suspend fun syncGreetingToBackend(backendUrl: String, greeting: String) {
    if (backendUrl.isBlank()) return
    withContext(Dispatchers.IO) {
        try {
            val cleanBase = backendUrl.trimEnd('/')
            val conn = (URL("$cleanBase/config").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                connectTimeout = 5000
                readTimeout = 5000
                doOutput = true
            }
            val payload = JSONObject().put("greeting", greeting).toString()
            conn.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
            conn.responseCode
            conn.disconnect()
        } catch (_: Exception) {
        }
    }
}

private suspend fun fetchNotesFromBackend(context: Context, backendUrl: String) {
    if (backendUrl.isBlank()) return
    withContext(Dispatchers.IO) {
        try {
            val cleanBase = backendUrl.trimEnd('/')
            val conn = (URL("$cleanBase/notes").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5000
                readTimeout = 5000
            }
            if (conn.responseCode == 200) {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val arr = JSONArray(body)
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    Storage.saveCallNote(
                        context,
                        CallNote(
                            id = obj.optLong("id", System.currentTimeMillis() + i),
                            callerNumber = obj.optString("callerNumber", "Unknown Caller"),
                            timestampMs = obj.optLong("timestampMs", System.currentTimeMillis()),
                            category = obj.optString("category", "General"),
                            summary = obj.optString("summary", ""),
                            transcript = obj.optString("transcript", "")
                        )
                    )
                }
            }
            conn.disconnect()
        } catch (_: Exception) {
        }
    }
}

@Composable
private fun DirectSimApp(resumeTick: Int) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    var stateRefreshTrigger by remember { mutableIntStateOf(0) }

    val isDefaultDialer = remember(resumeTick, stateRefreshTrigger) {
        checkIsDefaultPhoneApp(context)
    }
    val missingPermissions = remember(resumeTick, stateRefreshTrigger) {
        getMissingRuntimePermissions(context)
    }
    val notesList = remember(resumeTick, stateRefreshTrigger, selectedTab) {
        Storage.getCallNotes(context)
    }

    LaunchedEffect(resumeTick, selectedTab) {
        val url = Storage.getBackendUrl(context)
        if (url.isNotBlank()) {
            fetchNotesFromBackend(context, url)
            stateRefreshTrigger++
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        stateRefreshTrigger++
    }

    val dialerRoleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        stateRefreshTrigger++
        val stillMissing = getMissingRuntimePermissions(context)
        if (stillMissing.isNotEmpty()) {
            permissionLauncher.launch(stillMissing.toTypedArray())
        }
    }

    fun launchDefaultDialerDropdown() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val roleManager = context.getSystemService(RoleManager::class.java)
                if (roleManager.isRoleAvailable(RoleManager.ROLE_DIALER) &&
                    !roleManager.isRoleHeld(RoleManager.ROLE_DIALER)
                ) {
                    dialerRoleLauncher.launch(
                        roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER)
                    )
                    return
                }
            } else {
                val intent = Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER).apply {
                    putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, context.packageName)
                }
                dialerRoleLauncher.launch(intent)
                return
            }
        } catch (_: Exception) {
        }

        try {
            context.startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
        } catch (_: Exception) {
            Toast.makeText(context, "Open Settings > Apps > Default apps > Phone app", Toast.LENGTH_LONG).show()
        }
    }

    Scaffold(
        containerColor = Color(0xFF111113),
        bottomBar = {
            NavigationBar(
                containerColor = Color(0xFF161619),
                tonalElevation = 0.dp
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = {
                        Text(
                            text = "●",
                            fontSize = 14.sp,
                            color = if (selectedTab == 0) Color(0xFF10B981) else Color(0xFF71717A)
                        )
                    },
                    label = {
                        Text(
                            text = "Dashboard",
                            fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedTextColor = Color(0xFFF4F4F6),
                        unselectedTextColor = Color(0xFFA1A1AA),
                        indicatorColor = Color(0xFF242429)
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = {
                        Text(
                            text = "≡",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (selectedTab == 1) Color(0xFF10B981) else Color(0xFF71717A)
                        )
                    },
                    label = {
                        val suffix = if (notesList.isNotEmpty()) " (${notesList.size})" else ""
                        Text(
                            text = "Call Notes$suffix",
                            fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedTextColor = Color(0xFFF4F4F6),
                        unselectedTextColor = Color(0xFFA1A1AA),
                        indicatorColor = Color(0xFF242429)
                    )
                )
            }
        }
    ) { innerPadding ->
        if (selectedTab == 0) {
            DashboardScreen(
                innerPadding = innerPadding,
                isDefaultDialer = isDefaultDialer,
                missingPermissions = missingPermissions,
                onOpenDefaultDialerDropdown = { launchDefaultDialerDropdown() },
                onRequestPermissions = {
                    if (missingPermissions.isNotEmpty()) {
                        permissionLauncher.launch(missingPermissions.toTypedArray())
                    }
                }
            )
        } else {
            CallNotesScreen(
                innerPadding = innerPadding,
                notes = notesList,
                onNotesChanged = { stateRefreshTrigger++ }
            )
        }
    }
}

@Composable
private fun DashboardScreen(
    innerPadding: PaddingValues,
    isDefaultDialer: Boolean,
    missingPermissions: List<String>,
    onOpenDefaultDialerDropdown: () -> Unit,
    onRequestPermissions: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isAgentOn by remember { mutableStateOf(Storage.isAssistantEnabled(context)) }
    var greetingText by remember { mutableStateOf(Storage.getGreetingMessage(context)) }
    var forwardNumber by remember { mutableStateOf(Storage.getForwardNumber(context)) }
    var backendUrl by remember { mutableStateOf(Storage.getBackendUrl(context)) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding()
            .padding(
                start = 20.dp,
                end = 20.dp,
                top = 12.dp,
                bottom = innerPadding.calculateBottomPadding()
            )
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column {
            Text(
                text = "DirectSIM Assistant",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFF4F4F6)
            )
            Text(
                text = "Silent Background Call Answering Agent",
                fontSize = 13.sp,
                color = Color(0xFFA1A1AA)
            )
        }

        // Auto-hiding warning card when Default Phone App or Permissions are not set
        if (!isDefaultDialer || missingPermissions.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = Color(0xFF261C10),
                border = BorderStroke(1.dp, Color(0xFFD97706)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (!isDefaultDialer) {
                            "Warning: App Not Set as Default Phone App"
                        } else {
                            "Warning: Permissions Required"
                        },
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFBBF24)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (!isDefaultDialer) {
                            "Tap below to open the app selector dropdown and choose DirectSIM Call Assistant so it can manage silent call forwarding."
                        } else {
                            "Allow permissions to complete background setup."
                        },
                        fontSize = 13.sp,
                        color = Color(0xFFE4E4E7)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            if (!isDefaultDialer) onOpenDefaultDialerDropdown() else onRequestPermissions()
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFD97706),
                            contentColor = Color(0xFF111113)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Text(
                            text = if (!isDefaultDialer) "SELECT AS DEFAULT PHONE APP" else "ALLOW PERMISSIONS",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        // Hero Silent ON/OFF CTA Card
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF1A1A1E),
            border = BorderStroke(
                1.dp,
                if (isAgentOn) Color(0xFF10B981).copy(alpha = 0.5f) else Color(0xFF323238)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = if (isAgentOn) "● SILENT AGENT MODE ACTIVE (ON)" else "● AGENT STANDBY (OFF)",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isAgentOn) Color(0xFF10B981) else Color(0xFFA1A1AA)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (isAgentOn) {
                        "Your phone stays 100% silent. Incoming calls forward directly to your AI voice agent, and callers hear the agent clearly."
                    } else {
                        "Turn ON before a meeting. Zero sound will come from your phone while the agent answers callers in the background."
                    },
                    fontSize = 13.sp,
                    color = Color(0xFFD4D4D8)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        if (!isAgentOn) {
                            if (!isDefaultDialer) {
                                Toast.makeText(
                                    context,
                                    "Select DirectSIM Call Assistant as Default Phone App first.",
                                    Toast.LENGTH_LONG
                                ).show()
                                onOpenDefaultDialerDropdown()
                                return@Button
                            }
                            val targetNumber = forwardNumber.trim()
                            if (targetNumber.isEmpty()) {
                                Toast.makeText(
                                    context,
                                    "Enter your VoIP Agent Forwarding Number below first.",
                                    Toast.LENGTH_LONG
                                ).show()
                                return@Button
                            }
                            Storage.setForwardNumber(context, targetNumber)
                            Storage.setGreetingMessage(context, greetingText)
                            Storage.setBackendUrl(context, backendUrl)
                            Storage.setAssistantEnabled(context, true)
                            isAgentOn = true

                            scope.launch {
                                syncGreetingToBackend(backendUrl, greetingText)
                            }
                            // Silently activate unconditional forwarding (*21*<number>#) so phone never rings aloud
                            triggerCarrierMmiCode(context, "*21*$targetNumber#")
                        } else {
                            Storage.setAssistantEnabled(context, false)
                            isAgentOn = false
                            // Cancel unconditional forwarding (##21#) to restore normal personal calls
                            triggerCarrierMmiCode(context, "##21#")
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isAgentOn) Color(0xFFDC2626) else Color(0xFF10B981),
                        contentColor = if (isAgentOn) Color.White else Color(0xFF0A0A0B)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    Text(
                        text = if (isAgentOn) "TURN OFF SILENT AGENT" else "TURN ON SILENT AGENT",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Greeting Textarea Card
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF1A1A1E),
            border = BorderStroke(1.dp, Color(0xFF323238)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "Agent Greeting Message",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFF4F4F6)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Spoken digitally to the caller (never played on your phone's speaker).",
                    fontSize = 12.sp,
                    color = Color(0xFFA1A1AA)
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = greetingText,
                    onValueChange = { greetingText = it },
                    minLines = 3,
                    maxLines = 5,
                    placeholder = { Text("Enter greeting message...", color = Color(0xFF71717A)) },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color(0xFFF4F4F6),
                        unfocusedTextColor = Color(0xFFF4F4F6),
                        focusedContainerColor = Color(0xFF242429),
                        unfocusedContainerColor = Color(0xFF242429),
                        focusedBorderColor = Color(0xFF10B981),
                        unfocusedBorderColor = Color(0xFF3F3F46)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                        val trimmed = greetingText.trim()
                        if (trimmed.isNotEmpty()) {
                            Storage.setGreetingMessage(context, trimmed)
                            scope.launch { syncGreetingToBackend(backendUrl, trimmed) }
                            Toast.makeText(context, "Greeting synced", Toast.LENGTH_SHORT).show()
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFF4F4F6),
                        contentColor = Color(0xFF111113)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                ) {
                    Text("SAVE & SYNC GREETING", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Silent VoIP Bridge Configuration Card
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF1A1A1E),
            border = BorderStroke(1.dp, Color(0xFF323238)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "Silent Voice Bridge Settings",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFF4F4F6)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Connect your Twilio / Exotel virtual number and backend URL so callers hear the agent clearly.",
                    fontSize = 12.sp,
                    color = Color(0xFFA1A1AA)
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = forwardNumber,
                    onValueChange = { forwardNumber = it },
                    singleLine = true,
                    label = { Text("VoIP Agent Number (e.g. +91...)", fontSize = 12.sp) },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color(0xFFF4F4F6),
                        unfocusedTextColor = Color(0xFFF4F4F6),
                        focusedContainerColor = Color(0xFF242429),
                        unfocusedContainerColor = Color(0xFF242429),
                        focusedBorderColor = Color(0xFF10B981),
                        unfocusedBorderColor = Color(0xFF3F3F46)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = backendUrl,
                    onValueChange = { backendUrl = it },
                    singleLine = true,
                    label = { Text("Backend Server URL (https://...)", fontSize = 12.sp) },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color(0xFFF4F4F6),
                        unfocusedTextColor = Color(0xFFF4F4F6),
                        focusedContainerColor = Color(0xFF242429),
                        unfocusedContainerColor = Color(0xFF242429),
                        focusedBorderColor = Color(0xFF10B981),
                        unfocusedBorderColor = Color(0xFF3F3F46)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = {
                        Storage.setForwardNumber(context, forwardNumber)
                        Storage.setBackendUrl(context, backendUrl)
                        Toast.makeText(context, "Voice bridge settings saved", Toast.LENGTH_SHORT).show()
                    },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFF3F3F46)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                ) {
                    Text(
                        "SAVE BRIDGE SETTINGS",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFF4F4F6)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
private fun CallNotesScreen(
    innerPadding: PaddingValues,
    notes: List<CallNote>,
    onNotesChanged: () -> Unit
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    var newestFirst by remember { mutableStateOf(true) }

    val filteredNotes = remember(notes, searchQuery, selectedCategory, newestFirst) {
        val q = searchQuery.trim().lowercase(Locale.getDefault())
        notes
            .filter { selectedCategory == "All" || it.category.equals(selectedCategory, ignoreCase = true) }
            .filter {
                q.isEmpty() ||
                    it.callerNumber.lowercase(Locale.getDefault()).contains(q) ||
                    it.summary.lowercase(Locale.getDefault()).contains(q) ||
                    it.transcript.lowercase(Locale.getDefault()).contains(q)
            }
            .let { list ->
                if (newestFirst) list.sortedByDescending { it.timestampMs }
                else list.sortedBy { it.timestampMs }
            }
    }

    val formatter = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(
                start = 20.dp,
                end = 20.dp,
                top = 12.dp,
                bottom = innerPadding.calculateBottomPadding()
            )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Call Notes",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFF4F4F6),
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = { newestFirst = !newestFirst }) {
                Text(
                    text = if (newestFirst) "Newest ↓" else "Oldest ↑",
                    color = Color(0xFFD4D4D8),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
            if (notes.isNotEmpty()) {
                TextButton(
                    onClick = {
                        Storage.clearAllCallNotes(context)
                        onNotesChanged()
                        Toast.makeText(context, "All notes cleared", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text(
                        text = "Clear All",
                        color = Color(0xFFF87171),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            singleLine = true,
            placeholder = { Text("Search number or message...", color = Color(0xFF71717A)) },
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color(0xFFF4F4F6),
                unfocusedTextColor = Color(0xFFF4F4F6),
                focusedContainerColor = Color(0xFF1A1A1E),
                unfocusedContainerColor = Color(0xFF1A1A1E),
                focusedBorderColor = Color(0xFF10B981),
                unfocusedBorderColor = Color(0xFF323238)
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(10.dp))

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(listOf("All", "Urgent", "Callback", "General")) { category ->
                FilterChip(
                    selected = selectedCategory == category,
                    onClick = { selectedCategory = category },
                    label = { Text(category, fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFFF4F4F6),
                        selectedLabelColor = Color(0xFF111113),
                        containerColor = Color(0xFF1A1A1E),
                        labelColor = Color(0xFFD4D4D8)
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (filteredNotes.isEmpty()) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = Color(0xFF1A1A1E),
                border = BorderStroke(1.dp, Color(0xFF323238)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "No Call Notes Found",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFF4F4F6)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "When your AI voice agent answers forwarded calls in the background, transcripts and summaries appear here automatically.",
                        fontSize = 13.sp,
                        color = Color(0xFFA1A1AA)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(filteredNotes, key = { it.id }) { note ->
                    val badgeColor = when (note.category) {
                        "Urgent" -> Color(0xFFF87171)
                        "Callback" -> Color(0xFFFBBF24)
                        else -> Color(0xFF34D399)
                    }

                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = Color(0xFF1A1A1E),
                        border = BorderStroke(1.dp, Color(0xFF323238)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = note.callerNumber,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFF4F4F6),
                                    modifier = Modifier.weight(1f)
                                )
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFF242429),
                                    border = BorderStroke(1.dp, Color(0xFF3F3F46))
                                ) {
                                    Text(
                                        text = note.category.uppercase(Locale.getDefault()),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = badgeColor,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = formatter.format(Date(note.timestampMs)),
                                fontSize = 12.sp,
                                color = Color(0xFFA1A1AA)
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFF141417),
                                border = BorderStroke(1.dp, Color(0xFF2A2A30)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = note.transcript.ifBlank { note.summary },
                                    fontSize = 13.sp,
                                    color = Color(0xFFE4E4E7),
                                    modifier = Modifier.padding(12.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        cb.setPrimaryClip(ClipData.newPlainText("Caller", note.callerNumber))
                                        Toast.makeText(context, "Copied ${note.callerNumber}", Toast.LENGTH_SHORT).show()
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, Color(0xFF3F3F46)),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Copy", fontSize = 12.sp, color = Color(0xFFF4F4F6))
                                }

                                OutlinedButton(
                                    onClick = {
                                        context.startActivity(
                                            Intent(Intent.ACTION_DIAL, Uri.parse("tel:${note.callerNumber}"))
                                        )
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, Color(0xFF3F3F46)),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Call Back", fontSize = 12.sp, color = Color(0xFF34D399))
                                }

                                OutlinedButton(
                                    onClick = {
                                        Storage.deleteCallNote(context, note.id)
                                        onNotesChanged()
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, Color(0xFF3F3F46)),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Delete", fontSize = 12.sp, color = Color(0xFFF87171))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}