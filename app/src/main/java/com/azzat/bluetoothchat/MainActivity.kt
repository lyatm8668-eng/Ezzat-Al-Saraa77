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
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
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
import androidx.compose.ui.unit.sp
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
                onStatus("بانتظار الاتصال…")
                val s = server.accept()
                server.close()
                attach(s)
            } catch (e: Exception) { onStatus("خطأ بالاتصال: تأكد من اقتران الجهازين") }
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

        // ألوان واتساب الرسمية
        val whatsappDarkGreen = Color(0xFF075E54)
        val whatsappLightGreen = Color(0xFF128C7E)
        val whatsappBackground = Color(0xFFECE5DD)
        val whatsappBubbleMine = Color(0xFFDCF8C6)

        MaterialTheme {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // أيقونة شخص دائرية مثل واتساب
                                Surface(
                                    modifier = Modifier.size(40.dp),
                                    shape = CircleShape,
                                    color = Color.LightGray
                                ) {
                                    Icon(Icons.Filled.Person, contentDescription = "", tint = Color.White, modifier = Modifier.padding(4.dp))
                                }
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text("تطوير وصيانة عزت السراء", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                    Text(
                                        status, 
                                        color = Color.White.copy(alpha = 0.8f), 
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        },
                        actions = {
                            IconButton(onClick = { }) { Icon(Icons.Filled.Call, contentDescription = "", tint = Color.White) }
                            IconButton(onClick = { }) { Icon(Icons.Filled.MoreVert, contentDescription = "", tint = Color.White) }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = whatsappDarkGreen)
                    )
                }
            ) { pad ->
                Column(
                    Modifier
                        .fillMaxSize()
                        .background(whatsappBackground) // خلفية المحادثة
                        .padding(pad)
                ) {
                    if (devices.isNotEmpty() && status != "متصل") {
                        LazyColumn(Modifier.heightIn(max = 120.dp).background(Color.White)) {
                            items(devices) { d ->
                                TextButton(
                                    onClick = { chat.connect(d) },
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                                ) {
                                    Text("اتصل بـ: ${d.name ?: d.address}", color = whatsappDarkGreen, fontSize = 16.sp)
                                }
                            }
                        }
                    }

                    // المحادثة
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
                                    color = if (isMine) whatsappBubbleMine else Color.White,
                                    shape = RoundedCornerShape(
                                        topStart = 16.dp, 
                                        topEnd = 16.dp, 
                                        bottomStart = if (isMine) 16.dp else 4.dp, 
                                        bottomEnd = if (isMine) 4.dp else 16.dp
                                    ),
                                    shadowElevation = 1.dp,
                                    modifier = Modifier.widthIn(min = 60.dp, max = 280.dp)
                                ) { 
                                    Text(
                                        text = msg.text, 
                                        color = Color.Black,
                                        fontSize = 16.sp,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                    ) 
                                }
                            }
                        }
                    }

                    // شريط الكتابة وزر الإرسال
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(8.dp)
                            .navigationBarsPadding(),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Surface(
                            modifier = Modifier.weight(1f).padding(end = 8.dp),
                            shape = RoundedCornerShape(24.dp),
                            color = Color.White,
                            shadowElevation = 1.dp
                        ) {
                            OutlinedTextField(
                                value = text, 
                                onValueChange = { text = it },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("المراسلة", color = Color.Gray, fontSize = 18.sp) },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color.Transparent,
                                    unfocusedBorderColor = Color.Transparent
                                ),
                                maxLines = 6
                            )
                        }
                        
                        FloatingActionButton(
                            onClick = {
                                val t = text.trim()
                                if (t.isNotEmpty()) {
                                    chat.send(t)
                                    messages.add(Msg(t, true))
                                    text = ""
                                }
                            },
                            containerColor = whatsappLightGreen,
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
