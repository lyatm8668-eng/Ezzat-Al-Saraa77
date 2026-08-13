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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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

        MaterialTheme(colorScheme = lightColorScheme(
            primary = Color(0xFF075E54),
            secondary = Color(0xFF25D366)
        )) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Column {
                                Text("تطوير وصيانة عزت السراء")
                                Text(status, style = MaterialTheme.typography.labelSmall)
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color(0xFF075E54),
                            titleContentColor = Color.White
                        )
                    )
                }
            ) { pad ->
                Column(Modifier.fillMaxSize().padding(pad)) {
                    if (devices.isNotEmpty()) {
                        Text("الأجهزة المقترنة", modifier = Modifier.padding(12.dp))
                        LazyColumn(Modifier.heightIn(max = 130.dp)) {
                            items(devices) { d ->
                                Button(
                                    onClick = { chat.connect(d) },
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp)
                                ) { Text(d.name ?: d.address) }
                            }
                        }
                    } else {
                        Text(
                            "اقترن بالجهاز الآخر من إعدادات البلوتوث أولاً، ثم افتح التطبيق على الجهازين.",
                            modifier = Modifier.padding(16.dp),
                            textAlign = TextAlign.Center
                        )
                    }

                    LazyColumn(
                        state = listState,
                        modifier = Modifier.weight(1f).fillMaxWidth().padding(8.dp)
                    ) {
                        items(messages) { msg ->
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = if (msg.mine) Arrangement.End else Arrangement.Start
                            ) {
                                Surface(
                                    color = if (msg.mine) Color(0xFFD9FDD3) else Color.White,
                                    shape = RoundedCornerShape(14.dp),
                                    tonalElevation = 2.dp,
                                    modifier = Modifier.padding(vertical = 4.dp).widthIn(max = 310.dp)
                                ) { Text(msg.text, modifier = Modifier.padding(12.dp)) }
                            }
                        }
                    }

                    Row(
                        Modifier.fillMaxWidth().padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = text, onValueChange = { text = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("اكتب رسالة") },
                            singleLine = true
                        )
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = {
                            val t = text.trim()
                            if (t.isNotEmpty()) {
                                chat.send(t); messages.add(Msg(t, true)); text = ""
                            }
                        }) { Text("إرسال") }
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
