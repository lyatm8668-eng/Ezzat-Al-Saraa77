package com.azzat.bluetoothchat

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.concurrent.thread

private val APP_UUID: UUID = UUID.fromString("8d6e9b70-6a61-4f9c-a5b1-7f5e4c0a7712")

data class Msg(
    val text: String,
    val mine: Boolean,
    val time: String
)

class BluetoothChat(
    private val context: Context,
    private val onMessage: (String) -> Unit,
    private val onStatus: (String) -> Unit
) {

    private val adapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter

    private var socket: BluetoothSocket? = null
    private var serverSocket: BluetoothServerSocket? = null
    private var writer: PrintWriter? = null

    @Volatile
    private var running = false

    private fun hasConnectPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                context.checkSelfPermission(
                    Manifest.permission.BLUETOOTH_CONNECT
                ) == PackageManager.PERMISSION_GRANTED
    }

    fun startServer() {
        if (running) return
        if (!hasConnectPermission()) {
            onStatus("تحتاج صلاحية Bluetooth")
            return
        }
        running = true
        thread {
            try {
                serverSocket = adapter?.listenUsingRfcommWithServiceRecord("عزت السراء", APP_UUID)
                onStatus("بانتظار الاتصال…")
                val acceptedSocket = serverSocket?.accept()
                serverSocket?.close()
                serverSocket = null
                if (acceptedSocket != null) {
                    attach(acceptedSocket)
                }
            } catch (_: Exception) {
                if (running) onStatus("تعذر انتظار الاتصال")
            }
        }
    }

    fun connect(device: BluetoothDevice) {
        if (!hasConnectPermission()) {
            onStatus("تحتاج صلاحية Bluetooth")
            return
        }
        thread {
            try {
                adapter?.cancelDiscovery()
                onStatus("جاري الاتصال…")
                closeSocketOnly()
                val newSocket = device.createRfcommSocketToServiceRecord(APP_UUID)
                newSocket.connect()
                attach(newSocket)
            } catch (_: Exception) {
                onStatus("فشل الاتصال")
                closeSocketOnly()
            }
        }
    }

    private fun attach(newSocket: BluetoothSocket) {
        try {
            socket = newSocket
            writer = PrintWriter(OutputStreamWriter(newSocket.outputStream), true)
            onStatus("متصل")
            thread {
                try {
                    val reader = BufferedReader(InputStreamReader(newSocket.inputStream))
                    while (running) {
                        val line = reader.readLine() ?: break
                        if (line.isNotEmpty()) {
                            onMessage(line)
                        }
                    }
                } catch (_: Exception) {
                    if (running) onStatus("انقطع الاتصال")
                }
            }
        } catch (_: Exception) {
            onStatus("تعذر إنشاء الاتصال")
        }
    }

    fun send(text: String) {
        if (text.isBlank()) return
        thread {
            try {
                writer?.println(text)
            } catch (_: Exception) {
                onStatus("تعذر إرسال الرسالة")
            }
        }
    }

    private fun closeSocketOnly() {
        try { writer?.close() } catch (_: Exception) {}
        writer = null
        try { socket?.close() } catch (_: Exception) {}
        socket = null
    }

    fun close() {
        running = false
        try { serverSocket?.close() } catch (_: Exception) {}
        serverSocket = null
        closeSocketOnly()
    }
}

class MainActivity : ComponentActivity() {

    private lateinit var chat: BluetoothChat
    private val messages = mutableStateListOf<Msg>()
    private var status by mutableStateOf("غير متصل")
    private var devices by mutableStateOf<List<BluetoothDevice>>(emptyList())

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { loadDevices() }

    private fun getCurrentTime(): String {
        val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
        return sdf.format(Date())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        chat = BluetoothChat(
            this,
            onMessage = { text ->
                runOnUiThread { messages.add(Msg(text = text, mine = false, time = getCurrentTime())) }
            },
            onStatus = { newStatus ->
                runOnUiThread { status = newStatus }
            }
        )
        setContent { App() }
        requestBluetoothPermissions()
    }

    private fun requestBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissionLauncher.launch(arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT))
        } else {
            loadDevices()
        }
    }

    private fun loadDevices() {
        try {
            val manager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
            val adapter = manager.adapter ?: return
            if (!adapter.isEnabled) {
                startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                return
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                return
            }
            devices = adapter.bondedDevices.toList().sortedBy { it.name ?: it.address }
            chat.startServer()
        } catch (_: Exception) {
            status = "تعذر قراءة الأجهزة"
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun App() {
        var text by remember { mutableStateOf("") }
        var showMenu by remember { mutableStateOf(false) }
        val listState = rememberLazyListState()
        val isDark = isSystemInDarkTheme()

        val topBarColor = if (isDark) Color(0xFF202C33) else Color(0xFF075E54)
        val bgColor = if (isDark) Color(0xFF0B141A) else Color(0xFFEFE7DE)
        val myBubbleColor = if (isDark) Color(0xFF005C4B) else Color(0xFFD9FDD3)
        val otherBubbleColor = if (isDark) Color(0xFF202C33) else Color.White
        val textColor = if (isDark) Color.White else Color.Black
        val secondaryTextColor = if (isDark) Color(0xFFB7C4C9) else Color(0xFF667781)

        LaunchedEffect(messages.size) {
            if (messages.isNotEmpty()) {
                listState.animateScrollToItem(messages.lastIndex)
            }
        }

        MaterialTheme {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box {
                                    Surface(
                                        modifier = Modifier.size(44.dp).clip(CircleShape),
                                        color = if (isDark) Color(0xFF37474F) else Color(0xFFBDBDBD)
                                    ) {
                                        Icon(imageVector = Icons.Filled.Person, contentDescription = null, tint = Color.White, modifier = Modifier.padding(8.dp))
                                    }
                                    if (status == "متصل") {
                                        Surface(
                                            modifier = Modifier.size(12.dp).align(Alignment.BottomEnd),
                                            shape = CircleShape,
                                            color = Color(0xFF25D366)
                                        ) {}
                                    }
                                }
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "تطوير وصيانة عزت السراء",
                                        color = Color.White,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(text = status, color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp, maxLines = 1)
                                }
                            }
                        },
                        actions = {
                            IconButton(onClick = {}) {
                                Icon(imageVector = Icons.Filled.Call, contentDescription = "اتصال", tint = Color.White)
                            }
                            Box {
                                IconButton(onClick = { showMenu = !showMenu }) {
                                    Icon(imageVector = Icons.Filled.MoreVert, contentDescription = "المزيد", tint = Color.White)
                                }
                                DropdownMenu(
                                    expanded = showMenu,
                                    onDismissRequest = { showMenu = false },
                                    modifier = Modifier.background(if (isDark) Color(0xFF233138) else Color.White)
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("الإعدادات", color = textColor) },
                                        leadingIcon = { Icon(Icons.Filled.Settings, contentDescription = null, tint = secondaryTextColor) },
                                        onClick = { showMenu = false }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("مسح الدردشة", color = textColor) },
                                        onClick = { messages.clear(); showMenu = false }
                                    )
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = topBarColor)
                    )
                }
            ) { pad ->
                Column(Modifier.fillMaxSize().background(bgColor).padding(pad)) {
                    if (devices.isNotEmpty() && status != "متصل") {
                        LazyColumn(Modifier.heightIn(max = 120.dp).background(if (isDark) Color(0xFF111B21) else Color.White)) {
                            items(devices) { d ->
                                TextButton(
                                    onClick = { chat.connect(d) },
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                                ) {
                                    Text("اتصل بـ: ${d.name ?: d.address}", color = if (isDark) Color(0xFF00A884) else Color(0xFF075E54), fontSize = 16.sp)
                                }
                            }
                        }
                    }

                    LazyColumn(
                        state = listState,
                        modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        items(messages) { msg ->
                            val isMine = msg.mine
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start
                            ) {
                                Surface(
                                    color = if (isMine) myBubbleColor else otherBubbleColor,
                                    shape = RoundedCornerShape(
                                        topStart = 16.dp, topEnd = 16.dp,
                                        bottomStart = if (isMine) 16.dp else 4.dp,
                                        bottomEnd = if (isMine) 4.dp else 16.dp
                                    ),
                                    shadowElevation = 1.dp,
                                    modifier = Modifier.widthIn(min = 80.dp, max = 290.dp)
                                ) {
                                    Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                        Text(text = msg.text, color = textColor, fontSize = 16.sp)
                                        Text(
                                            text = msg.time,
                                            color = secondaryTextColor,
                                            fontSize = 11.sp,
                                            modifier = Modifier.align(Alignment.End).padding(top = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Row(
                        Modifier.fillMaxWidth().padding(8.dp).navigationBarsPadding(),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Surface(
                            modifier = Modifier.weight(1f).padding(end = 8.dp),
                            shape = RoundedCornerShape(24.dp),
                            color = if (isDark) Color(0xFF2B3940) else Color.White,
                            shadowElevation = 1.dp
                        ) {
                            OutlinedTextField(
                                value = text,
                                onValueChange = { text = it },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("المراسلة", color = secondaryTextColor, fontSize = 18.sp) },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color.Transparent, unfocusedBorderColor = Color.Transparent,
                                    focusedTextColor = textColor, unfocusedTextColor = textColor
                                ),
                                maxLines = 6
                            )
                        }

                        FloatingActionButton(
                            onClick = {
                                val t = text.trim()
                                if (t.isNotEmpty()) {
                                    chat.send(t)
                                    messages.add(Msg(t, true, getCurrentTime()))
                                    text = ""
                                }
                            },
                            containerColor = Color(0xFF00A884),
                            contentColor = Color.White,
                            shape = CircleShape,
                            modifier = Modifier.size(50.dp)
                        ) {
                            Icon(Icons.Filled.Send, contentDescription = "إرسال", modifier = Modifier.padding(start = 4.dp))
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        chat.close()
        super.onDestroy()
    }
}
