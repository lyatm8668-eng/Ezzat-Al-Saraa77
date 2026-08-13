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
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.util.UUID
import kotlin.concurrent.thread

private val APP_UUID: UUID = UUID.fromString("8d6e9b70-6a61-4f9c-a5b1-7f5e4c0a7712")

class BluetoothChat(private val context: Context, private val onMessage: (String) -> Unit,
                    private val onStatus: (String) -> Unit) {
    private val adapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    private var socket: BluetoothSocket? = null
    private var writer: PrintWriter? = null

    fun startServer() {
        thread {
            try {
                if (!hasConnectPermission()) return@thread
                val server: BluetoothServerSocket =
                    adapter!!.listenUsingRfcommWithServiceRecord("عزت السراء", APP_UUID)
                onStatus("بانتظار اتصال جهاز مقترن…")
                val s = server.accept()
                server.close()
                attach(s)
            } catch (e: Exception) { onStatus("تعذر فتح الاتصال: ${e.message}") }
        }
    }

    fun connect(device: BluetoothDevice) {
        thread {
            try {
                if (!hasConnectPermission()) return@thread
                adapter?.cancelDiscovery()
                onStatus("جاري الاتصال بـ ${device.name ?: "الجهاز"}…")
                val s = device.createRfcommSocketToServiceRecord(APP_UUID)
                s.connect()
                attach(s)
            } catch (e: Exception) { onStatus("فشل الاتصال: ${e.message}") }
        }
    }

    private fun attach(s: BluetoothSocket) {
        socket = s
        writer = PrintWriter(OutputStreamWriter(s.outputStream), true)
        onStatus("متصل")
        thread {
            try {
                val reader = BufferedReader(InputStreamReader(s.inputStream))
                while (true) {
                    val line = reader.readLine() ?: break
                    onMessage(line)
                }
            } catch (_: Exception) { onStatus("انقطع الاتصال") }
        }
    }

    fun send(text: String) { writer?.println(text) }
    fun close() { try { socket?.close() } catch (_: Exception) {} }
    private fun hasConnectPermission() =
        android.os.Build.VERSION.SDK_INT < 31 ||
        context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
}

data class Msg(val text: String, val mine: Boolean)

class MainActivity : ComponentActivity() {
    private lateinit var chat: BluetoothChat
    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { loadDevices() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        chat = BluetoothChat(this, { text -> messages.add(Msg(text, false)) },
            { status = it })
        requestBluetoothPermissions()
        setContent { App() }
    }

    private val messages = mutableStateListOf<Msg>()
    private var status by mutableStateOf("غير متصل")
    private var devices by mutableStateOf<List<BluetoothDevice>>(emptyList())

    private fun requestBluetoothPermissions() {
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            permissionLauncher.launch(arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT))
        } else loadDevices()
    }

    private fun loadDevices() {
        try {
            val adapter = (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter ?: return
            if (!adapter.isEnabled) {
                startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            }
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED ||
                android.os.Build.VERSION.SDK_INT < 31) {
                devices = adapter.bondedDevices.toList()
                chat.startServer()
            }
        } catch (_: Exception) {}
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable fun App() {
        var text by remember { mutableStateOf("") }
        val listState = rememberLazyListState()

        LaunchedEffect(messages.size) {
            if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
        }

        // الألوان الاحترافية الجديدة
        MaterialTheme(colorScheme = lightColorScheme(
            primary = Color(0xFF1E293B), // أزرق داكن ليلي
            secondary = Color(0xFF3B82F6), // أزرق ساطع للأزرار
            background = Color(0xFFF1F5F9) // خلفية مريحة للعين
        )) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Column {
                                Text("عزت السراء | Peak Mentality", fontWeight = FontWeight.Bold)
                                Text(
                                    status, 
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (status == "متصل") Color(0xFF4ADE80) else Color(0xFF94A3B8)
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            titleContentColor = Color.White
                        )
                    )
                }
            ) { pad ->
                Column(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .padding(pad)
                ) {
                    // قسم الأجهزة المقترنة بتصميم محسّن
                    if (devices.isNotEmpty()) {
                        Text(
                            "الأجهزة المتاحة للاقتران", 
                            fontWeight = FontWeight.SemiBold,
                            color = Color.Gray,
                            modifier = Modifier.padding(top = 12.dp, start = 16.dp, end = 16.dp, bottom = 4.dp)
                        )
                        LazyColumn(Modifier.heightIn(max = 100.dp)) {
                            items(devices) { d ->
                                ElevatedCard(
                                    onClick = { chat.connect(d) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 4.dp),
                                    colors = CardDefaults.elevatedCardColors(containerColor = Color.White)
                                ) {
                                    Text(
                                        d.name ?: d.address, 
                                        modifier = Modifier.padding(16.dp),
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    } else {
                        Text(
                            "الرجاء الاقتران بالجهاز الآخر من إعدادات البلوتوث أولاً.",
                            modifier = Modifier.padding(24.dp),
                            textAlign = TextAlign.Center,
                            color = Color.Gray
                        )
                    }

                    // منطقة الدردشة
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        items(messages) { msg ->
                            val isMine = msg.mine
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start
                            ) {
                                Surface(
                                    color = if (isMine) MaterialTheme.colorScheme.secondary else Color.White,
                                    shape = if (isMine) {
                                        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 4.dp)
                                    } else {
                                        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 16.dp)
                                    },
                                    shadowElevation = 2.dp,
                                    modifier = Modifier.widthIn(min = 60.dp, max = 280.dp)
                                ) { 
                                    Text(
                                        text = msg.text, 
                                        color = if (isMine) Color.White else Color.Black,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                                    ) 
                                }
                            }
                        }
                    }

                    // مربع الإدخال الأنيق
                    Surface(
                        color = Color.White,
                        shadowElevation = 8.dp
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                                .navigationBarsPadding(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = text, 
                                onValueChange = { text = it },
                                modifier = Modifier.weight(1f),
                                placeholder = { Text("اكتب رسالة ملهمة...") },
                                shape = RoundedCornerShape(24.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    unfocusedBorderColor = Color.LightGray,
                                    focusedBorderColor = MaterialTheme.colorScheme.secondary
                                ),
                                maxLines = 4
                            )
                            Spacer(Modifier.width(8.dp))
                            IconButton(
                                onClick = {
                                    val t = text.trim()
                                    if (t.isNotEmpty()) {
                                        chat.send(t)
                                        messages.add(Msg(t, true))
                                        text = ""
                                    }
                                },
                                modifier = Modifier
                                    .size(50.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.secondary)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Send,
                                    contentDescription = "إرسال",
                                    tint = Color.White,
                                    modifier = Modifier.padding(start = 4.dp) // لضبط مركز السهم بصرياً
                                )
                            }
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
