package com.example.btmic

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay

fun neededPermissions(ctx: Context): Array<String> {
    val list = buildList {
        add(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 31) add(Manifest.permission.BLUETOOTH_CONNECT)
        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
    }
    return list.filter {
        ContextCompat.checkSelfPermission(ctx, it) != PackageManager.PERMISSION_GRANTED
    }.toTypedArray()
}

@Composable
fun WalkieTab(btName: String?) {
    val ctx = LocalContext.current

    var running by remember { mutableStateOf(WalkieState.running) }
    var transmitting by remember { mutableStateOf(false) }
    var receiving by remember { mutableStateOf(false) }
    var peerOnline by remember { mutableStateOf(false) }
    var level by remember { mutableFloatStateOf(0f) }
    var error by remember { mutableStateOf<String?>(null) }

    var room by remember { mutableStateOf(WalkieState.room.toString().padStart(4, '0')) }
    var ptt by remember { mutableStateOf(WalkieState.ptt) }
    var mute by remember { mutableStateOf(WalkieState.mute) }
    var rxGain by remember { mutableFloatStateOf(WalkieState.rxGain) }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    LaunchedEffect(Unit) {
        while (true) {
            val now = SystemClock.elapsedRealtime()
            running = WalkieState.running
            transmitting = running && WalkieState.transmitting
            receiving = running && now - WalkieState.lastRxMs < 500
            peerOnline = running && now - WalkieState.lastPeerMs < 4000
            val target = when {
                transmitting -> WalkieState.txLevel
                receiving -> WalkieState.rxLevel
                else -> 0f
            }
            level = level * 0.6f + target * 0.4f
            error = WalkieState.error
            delay(50)
        }
    }

    fun toggleWalkie(on: Boolean) {
        if (!on) {
            ctx.startService(Intent(ctx, WalkieService::class.java).setAction("STOP"))
            return
        }
        val m = neededPermissions(ctx)
        if (m.isNotEmpty()) { permLauncher.launch(m); return }
        if (btName == null) {
            Toast.makeText(ctx, "Pehle Bluetooth earbuds connect karo", Toast.LENGTH_LONG).show()
            return
        }
        WalkieState.error = null
        if (BtMicState.running) {
            ctx.startService(Intent(ctx, MicService::class.java).setAction("STOP"))
        }
        ctx.startForegroundService(Intent(ctx, WalkieService::class.java))
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(8.dp))
        Text("Walkie Talkie", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
        Text(
            "Earbuds se earbuds me baat karo.",
            color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp
        )
        Spacer(Modifier.height(18.dp))

        Glass {
            ToggleRow(
                title = "Walkie mode",
                sub = "ON hote hi phone ki awaaz band, sab kuch earbuds me",
                checked = running
            ) { toggleWalkie(it) }
        }

        error?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, color = Rose, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(14.dp))

        Glass {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(12.dp).clip(CircleShape)
                        .background(if (btName != null) Green else Color.Gray)
                )
                Text(
                    if (btName != null) "Earbuds: $btName" else "Earbuds connected nahi",
                    color = Color.White, fontSize = 15.sp, modifier = Modifier.padding(start = 12.dp)
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(12.dp).clip(CircleShape)
                        .background(if (peerOnline) Green else Color.Gray)
                )
                Text(
                    when {
                        !running -> "Dusra phone: Walkie mode ON karo"
                        peerOnline -> "Dusra phone: Mil gaya"
                        else -> "Dusra phone: Dhundh raha hai..."
                    },
                    color = Color.White, fontSize = 15.sp, modifier = Modifier.padding(start = 12.dp)
                )
            }
        }

        TalkButton(
            running = running, ptt = ptt,
            transmitting = transmitting, receiving = receiving, level = level
        )

        Text(
            when {
                !running -> "Walkie mode ON karo"
                transmitting -> "Aap bol rahe ho"
                receiving -> "Dusra bol raha hai"
                mute -> "Mute hai"
                ptt -> "Button dabake rakho aur bolo"
                else -> "Hands-free: bolo, awaaz apne aap jayegi"
            },
            color = Color.White.copy(alpha = 0.85f), fontSize = 15.sp, textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(18.dp))

        Glass {
            OutlinedTextField(
                value = room,
                onValueChange = { v ->
                    val d = v.filter { it.isDigit() }.take(4)
                    room = d
                    WalkieState.room = d.toIntOrNull() ?: 0
                    WalkieState.save(ctx)
                },
                label = { Text("Room code (dono phone me same)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Cyan,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                    focusedLabelColor = Cyan,
                    unfocusedLabelColor = Color.White.copy(alpha = 0.6f),
                    cursorColor = Cyan
                )
            )
            Spacer(Modifier.height(16.dp))
            ToggleRow(
                title = "Push to talk",
                sub = "Band ho to hands-free: bolte hi awaaz jayegi",
                checked = ptt
            ) { ptt = it; WalkieState.ptt = it; WalkieState.save(ctx) }
            Spacer(Modifier.height(14.dp))
            ToggleRow(
                title = "Mute",
                sub = "Aapki awaaz nahi jayegi, sunna chalu rahega",
                checked = mute
            ) { mute = it; WalkieState.mute = it }
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Sunne ki awaaz", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text("${"%.1f".format(rxGain)}x", color = Cyan, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
            Slider(
                value = rxGain,
                onValueChange = { rxGain = it; WalkieState.rxGain = it },
                onValueChangeFinished = { WalkieState.save(ctx) },
                valueRange = 1f..3f,
                colors = SliderDefaults.colors(
                    thumbColor = Cyan,
                    activeTrackColor = Violet,
                    inactiveTrackColor = Color.White.copy(alpha = 0.12f)
                )
            )
        }
        Spacer(Modifier.height(14.dp))
        Text(
            "Dono phone ek hi WiFi par hon, ya ek phone ka hotspot ON karke dusra use connect karo.\nAwaaz phone-call quality ki hogi aur thoda delay aayega.",
            color = Color.White.copy(alpha = 0.4f), fontSize = 12.sp, textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
fun TalkButton(
    running: Boolean,
    ptt: Boolean,
    transmitting: Boolean,
    receiving: Boolean,
    level: Float
) {
    val inf = rememberInfiniteTransition(label = "talk")
    val pulse by inf.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(1600, easing = LinearEasing)), label = "p"
    )
    val colors = when {
        !running -> listOf(Color(0xFF2B2B4A), Color(0xFF1A1A30))
        transmitting -> listOf(Pink, Violet)
        receiving -> listOf(Green, Cyan)
        else -> listOf(Cyan, Violet)
    }
    val ringColor = if (transmitting) Pink else Green

    Box(Modifier.size(260.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val base = size.minDimension * 0.30f
            if (running && (transmitting || receiving)) {
                for (k in 0..2) {
                    val t = (pulse + k / 3f) % 1f
                    drawCircle(
                        ringColor.copy(alpha = (1f - t) * 0.45f),
                        radius = base + t * size.minDimension * 0.2f,
                        style = Stroke(3.dp.toPx())
                    )
                }
            } else {
                drawCircle(Color.White.copy(alpha = 0.04f), radius = base * 1.25f)
            }
        }
        Box(
            Modifier
                .size(150.dp)
                .scale(1f + level * 0.1f)
                .clip(CircleShape)
                .background(Brush.linearGradient(colors))
                .pointerInput(running, ptt) {
                    if (running && ptt) {
                        detectTapGestures(onPress = {
                            WalkieState.pttDown = true
                            try {
                                tryAwaitRelease()
                            } finally {
                                WalkieState.pttDown = false
                            }
                        })
                    }
                },
            contentAlignment = Alignment.Center
        ) { MicIcon(Modifier.size(60.dp), Color.White) }
    }
}
