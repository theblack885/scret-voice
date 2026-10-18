package com.example.btmic

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlin.math.sin

val Cyan = Color(0xFF4FD1FF)
val Violet = Color(0xFF8B5CF6)
val Pink = Color(0xFFFF4ECD)
val Green = Color(0xFF34E39B)
val BgBrush = Brush.verticalGradient(
    listOf(Color(0xFF070712), Color(0xFF130F2B), Color(0xFF05060F))
)

fun hasMicPermission(ctx: Context): Boolean =
    ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        BtMicState.load(this)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Cyan, secondary = Violet,
                    background = Color(0xFF070712), surface = Color(0xFF130F2B)
                )
            ) { BtMicScreen() }
        }
    }
}

@Composable
fun BtMicScreen() {
    val ctx = LocalContext.current
    var tab by remember { mutableIntStateOf(0) }
    var btName by remember { mutableStateOf<String?>(null) }
    val player = remember { AudioPlayer() }
    DisposableEffect(Unit) { onDispose { player.stop() } }

    // Earbuds connect / disconnect dekhna. Connect hote hi app apne aap shuru.
    DisposableEffect(Unit) {
        val am = ctx.getSystemService(AudioManager::class.java)
        fun update(allowAutoStart: Boolean) {
            val d = findBtOutput(am)
            val name = d?.let { it.productName?.toString()?.ifBlank { null } ?: "Bluetooth earbuds" }
            val justConnected = btName == null && name != null
            btName = name
            if (justConnected && allowAutoStart && BtMicState.autoStart &&
                !BtMicState.running && hasMicPermission(ctx)
            ) {
                try {
                    ctx.startForegroundService(Intent(ctx, MicService::class.java))
                } catch (_: Exception) {
                }
            }
        }
        val cb = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>?) = update(true)
            override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>?) = update(false)
        }
        am.registerAudioDeviceCallback(cb, Handler(Looper.getMainLooper()))
        update(false)
        onDispose { am.unregisterAudioDeviceCallback(cb) }
    }

    Box(Modifier.fillMaxSize().background(BgBrush)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Box(Modifier.weight(1f)) {
                when (tab) {
                    0 -> LiveTab(btName)
                    1 -> RecordingsTab(player)
                    else -> HistoryTab(player)
                }
            }
            BottomBar(tab) { player.stop(); tab = it }
        }
    }
}

@Composable
fun BottomBar(selected: Int, onSelect: (Int) -> Unit) {
    val items = listOf("Live", "Recordings", "History")
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(28.dp))
            .padding(6.dp)
    ) {
        items.forEachIndexed { i, label ->
            val sel = i == selected
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(22.dp))
                    .background(
                        if (sel) Brush.linearGradient(listOf(Cyan, Violet))
                        else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
                    )
                    .clickable { onSelect(i) }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    color = if (sel) Color.White else Color.White.copy(alpha = 0.6f),
                    fontSize = 14.sp, fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
fun LiveTab(btName: String?) {
    val ctx = LocalContext.current
    var running by remember { mutableStateOf(BtMicState.running) }
    var level by remember { mutableFloatStateOf(0f) }
    var recording by remember { mutableStateOf(false) }
    var recordOn by remember { mutableStateOf(BtMicState.record) }
    var recSecs by remember { mutableIntStateOf(0) }

    var voiceOnly by remember { mutableStateOf(BtMicState.voiceOnly) }
    var blockOthers by remember { mutableStateOf(BtMicState.blockOthers) }
    var autoRecord by remember { mutableStateOf(BtMicState.autoRecord) }
    var autoStart by remember { mutableStateOf(BtMicState.autoStart) }
    var gain by remember { mutableFloatStateOf(BtMicState.gain) }

    val perms = remember {
        buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= 31) add(Manifest.permission.BLUETOOTH_CONNECT)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    fun missing() = perms.filter {
        ContextCompat.checkSelfPermission(ctx, it) != PackageManager.PERMISSION_GRANTED
    }.toTypedArray()

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    LaunchedEffect(Unit) {
        val m = missing()
        if (m.isNotEmpty()) permLauncher.launch(m)
    }

    LaunchedEffect(Unit) {
        while (true) {
            running = BtMicState.running
            recordOn = BtMicState.record
            recording = BtMicState.currentRecPath != null
            val start = BtMicState.recStartMs
            recSecs = if (start > 0L) ((SystemClock.elapsedRealtime() - start) / 1000).toInt() else 0
            level = level * 0.6f + BtMicState.level * 0.4f
            delay(50)
        }
    }

    fun toggle() {
        if (running) {
            ctx.startService(Intent(ctx, MicService::class.java).setAction("STOP"))
            return
        }
        val m = missing()
        if (m.isNotEmpty()) { permLauncher.launch(m); return }
        if (btName == null) {
            Toast.makeText(ctx, "Pehle Bluetooth earbuds connect karo", Toast.LENGTH_LONG).show()
            return
        }
        ctx.startForegroundService(Intent(ctx, MicService::class.java))
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(8.dp))
        Text("BT Mic", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
        Text(
            "Sirf aapki awaaz. Seedha earbuds me.",
            color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp
        )
        Spacer(Modifier.height(18.dp))

        Glass {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(12.dp).clip(CircleShape)
                        .background(if (btName != null) Green else Color.Gray)
                )
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(
                        if (btName != null) "Connected" else "Koi earbuds connected nahi",
                        color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp
                    )
                    Text(
                        btName ?: "Pair karo aur connect karo",
                        color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold
                    )
                }
                TextButton(onClick = {
                    ctx.startActivity(
                        Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }) { Text("Pair", color = Cyan, fontWeight = FontWeight.Bold) }
            }
        }

        Hero(running = running, level = level, onClick = { toggle() })

        Text(
            when {
                running -> "Live hai. Ab bolo, awaaz earbuds me aa rahi hai"
                btName == null -> "Pehle Bluetooth earbuds connect karo"
                else -> "Shuru karne ke liye mic dabao"
            },
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 15.sp, textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(10.dp))
        Wave(running, level)
        Spacer(Modifier.height(14.dp))

        RecBar(
            running = running,
            recording = recording,
            recordOn = recordOn,
            secs = recSecs,
            onStop = { BtMicState.record = false; recordOn = false },
            onStart = { BtMicState.record = true; recordOn = true }
        )

        Glass {
            ToggleRow(
                title = "Auto record",
                sub = "Awaaz aate hi recording apne aap shuru ho",
                checked = autoRecord
            ) { autoRecord = it; BtMicState.autoRecord = it; BtMicState.save(ctx) }
            Spacer(Modifier.height(14.dp))
            ToggleRow(
                title = "Earbuds connect hote hi shuru",
                sub = "App khula ho to apne aap chalu ho jayega",
                checked = autoStart
            ) { autoStart = it; BtMicState.autoStart = it; BtMicState.save(ctx) }
            Spacer(Modifier.height(14.dp))
            ToggleRow(
                title = "Sirf voice mode",
                sub = "Shor hatao, sirf insaani awaaz jaane do",
                checked = voiceOnly
            ) { voiceOnly = it; BtMicState.voiceOnly = it; BtMicState.save(ctx) }
            Spacer(Modifier.height(14.dp))
            ToggleRow(
                title = "Gaane aur dusri awaaz roko",
                sub = "Music/video apps ko pause karwata hai",
                checked = blockOthers
            ) { blockOthers = it; BtMicState.blockOthers = it; BtMicState.save(ctx) }
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Awaaz badhao", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text("${"%.1f".format(gain)}x", color = Cyan, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
            Slider(
                value = gain,
                onValueChange = { gain = it; BtMicState.gain = it },
                onValueChangeFinished = { BtMicState.save(ctx) },
                valueRange = 1f..4f,
                colors = SliderDefaults.colors(
                    thumbColor = Cyan,
                    activeTrackColor = Violet,
                    inactiveTrackColor = Color.White.copy(alpha = 0.12f)
                )
            )
        }
        Spacer(Modifier.height(14.dp))
        Text(
            "Bluetooth me thoda delay hona normal hai.\nKuch apps focus ignore karte hain, unhe manually pause karo.",
            color = Color.White.copy(alpha = 0.4f), fontSize = 12.sp, textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
fun RecBar(
    running: Boolean,
    recording: Boolean,
    recordOn: Boolean,
    secs: Int,
    onStop: () -> Unit,
    onStart: () -> Unit
) {
    if (!running) return
    val inf = rememberInfiniteTransition(label = "rec")
    val blink by inf.animateFloat(
        1f, 0.25f,
        infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "blink"
    )
    Glass {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(14.dp).clip(CircleShape)
                    .background(if (recording) Rose.copy(alpha = blink) else Color.Gray)
            )
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(
                    when {
                        recording -> "REC  %d:%02d".format(secs / 60, secs % 60)
                        recordOn -> "Awaaz ka intezaar..."
                        else -> "Recording band hai"
                    },
                    color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold
                )
                Text(
                    when {
                        recording -> "Awaaz record ho rahi hai"
                        recordOn -> "Awaaz aate hi recording apne aap shuru hogi"
                        else -> "Dabao to nayi recording shuru hogi"
                    },
                    color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp
                )
            }
            val pillColor = if (recordOn) Rose else Green
            Box(
                Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(pillColor.copy(alpha = 0.18f))
                    .border(1.dp, pillColor.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
                    .clickable { if (recordOn) onStop() else onStart() }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (recordOn) "Band karo" else "Shuru karo",
                    color = pillColor, fontSize = 14.sp, fontWeight = FontWeight.Bold
                )
            }
        }
    }
    Spacer(Modifier.height(14.dp))
}

@Composable
fun Glass(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(28.dp))
            .padding(18.dp)
    ) { content() }
}

@Composable
fun ToggleRow(title: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(sub, color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Violet,
                uncheckedThumbColor = Color.White.copy(alpha = 0.7f),
                uncheckedTrackColor = Color.White.copy(alpha = 0.12f),
                uncheckedBorderColor = Color.Transparent
            )
        )
    }
}

@Composable
fun Hero(running: Boolean, level: Float, onClick: () -> Unit) {
    val inf = rememberInfiniteTransition(label = "pulse")
    val pulse by inf.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "p"
    )
    Box(Modifier.size(290.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val base = size.minDimension * 0.24f
            if (running) {
                for (k in 0..2) {
                    val t = (pulse + k / 3f) % 1f
                    drawCircle(
                        Cyan.copy(alpha = (1f - t) * 0.4f),
                        radius = base + t * size.minDimension * 0.24f,
                        style = Stroke(3.dp.toPx())
                    )
                }
                val r = base * (1.6f + level)
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(Violet.copy(alpha = 0.35f + 0.4f * level), Color.Transparent),
                        radius = r
                    ),
                    radius = r
                )
            } else {
                drawCircle(Color.White.copy(alpha = 0.04f), radius = base * 1.5f)
            }
        }
        Box(
            Modifier
                .size(136.dp)
                .scale(1f + level * 0.08f)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        if (running) listOf(Cyan, Violet, Pink)
                        else listOf(Color(0xFF2B2B4A), Color(0xFF1A1A30))
                    )
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) { MicIcon(Modifier.size(58.dp), Color.White) }
    }
}

@Composable
fun MicIcon(modifier: Modifier, color: Color) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val sw = w * 0.09f
        drawRoundRect(
            color, topLeft = Offset(w * 0.36f, h * 0.08f),
            size = Size(w * 0.28f, h * 0.46f), cornerRadius = CornerRadius(w * 0.14f)
        )
        drawArc(
            color, startAngle = 0f, sweepAngle = 180f, useCenter = false,
            topLeft = Offset(w * 0.24f, h * 0.24f), size = Size(w * 0.52f, h * 0.5f),
            style = Stroke(sw, cap = StrokeCap.Round)
        )
        drawLine(color, Offset(w * 0.5f, h * 0.74f), Offset(w * 0.5f, h * 0.9f), sw, StrokeCap.Round)
        drawLine(color, Offset(w * 0.36f, h * 0.9f), Offset(w * 0.64f, h * 0.9f), sw, StrokeCap.Round)
    }
}

@Composable
fun Wave(running: Boolean, level: Float) {
    val inf = rememberInfiniteTransition(label = "wave")
    val ph by inf.animateFloat(
        0f, 6.2832f,
        infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart), label = "ph"
    )
    Canvas(Modifier.fillMaxWidth().height(70.dp)) {
        val n = 28
        val gap = size.width / n
        val bw = gap * 0.5f
        val brush = Brush.verticalGradient(listOf(Cyan, Violet))
        for (i in 0 until n) {
            val amp = if (running) 0.08f + level * 0.92f * (0.5f + 0.5f * sin(ph + i * 0.55f)) else 0.06f
            val bh = size.height * amp.coerceIn(0.05f, 1f)
            drawRoundRect(
                brush = brush,
                topLeft = Offset(i * gap + (gap - bw) / 2f, (size.height - bh) / 2f),
                size = Size(bw, bh),
                cornerRadius = CornerRadius(bw / 2f)
            )
        }
    }
}
