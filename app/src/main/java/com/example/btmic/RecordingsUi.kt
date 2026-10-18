package com.example.btmic

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val Rose = Color(0xFFFF6B8B)

@Composable
fun RecordingsTab(player: AudioPlayer) {
    val ctx = LocalContext.current
    var files by remember { mutableStateOf(listOf<File>()) }
    var tick by remember { mutableIntStateOf(0) }

    LaunchedEffect(tick) {
        files = RecStore.list(RecStore.dir(ctx)).filter { it.path != BtMicState.currentRecPath }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(12.dp))
        Text("Recordings", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
        Text(
            "${files.size} recording",
            color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp
        )
        Spacer(Modifier.height(14.dp))

        if (files.isEmpty()) {
            Glass {
                Text(
                    "Abhi koi recording nahi hai.\nEarbuds connect karke bolo, recording apne aap shuru hogi.",
                    color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp
                )
            }
        } else {
            LazyColumn(Modifier.weight(1f)) {
                items(files, key = { it.path }) { f ->
                    RecItem(f, player.playingPath == f.path, { player.toggle(f) }) {
                        TextButton(onClick = {
                            if (player.playingPath == f.path) player.stop()
                            if (RecStore.moveTo(f, RecStore.trash(ctx))) {
                                Toast.makeText(ctx, "History me bhej di", Toast.LENGTH_SHORT).show()
                            }
                            tick++
                        }) { Text("Delete", color = Rose, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
    }
}

@Composable
fun HistoryTab(player: AudioPlayer) {
    val ctx = LocalContext.current
    var files by remember { mutableStateOf(listOf<File>()) }
    var tick by remember { mutableIntStateOf(0) }
    var confirmFile by remember { mutableStateOf<File?>(null) }
    var confirmAll by remember { mutableStateOf(false) }

    LaunchedEffect(tick) {
        files = RecStore.list(RecStore.trash(ctx))
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(12.dp))
        Text("History", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
        Text(
            "Delete ki hui recordings. Recover karo ya hamesha ke liye hatao.",
            color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp
        )
        if (files.isNotEmpty()) {
            TextButton(onClick = { confirmAll = true }) {
                Text("Poori History saaf karo", color = Rose, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(8.dp))

        if (files.isEmpty()) {
            Glass {
                Text(
                    "History khali hai. Jo recording delete karoge wo yahan aayegi.",
                    color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp
                )
            }
        } else {
            LazyColumn(Modifier.weight(1f)) {
                items(files, key = { it.path }) { f ->
                    RecItem(f, player.playingPath == f.path, { player.toggle(f) }) {
                        TextButton(onClick = {
                            if (player.playingPath == f.path) player.stop()
                            if (RecStore.moveTo(f, RecStore.dir(ctx))) {
                                Toast.makeText(ctx, "Recover ho gayi", Toast.LENGTH_SHORT).show()
                            }
                            tick++
                        }) { Text("Recover", color = Green, fontWeight = FontWeight.Bold) }
                        TextButton(onClick = { confirmFile = f }) {
                            Text("Delete forever", color = Rose, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    confirmFile?.let { f ->
        AlertDialog(
            onDismissRequest = { confirmFile = null },
            containerColor = Color(0xFF1B1733),
            titleContentColor = Color.White,
            textContentColor = Color.White.copy(alpha = 0.7f),
            title = { Text("Hamesha ke liye delete?") },
            text = { Text("Ye recording phir wapas nahi aa sakti.") },
            confirmButton = {
                TextButton(onClick = {
                    if (player.playingPath == f.path) player.stop()
                    f.delete()
                    confirmFile = null
                    tick++
                }) { Text("Delete forever", color = Rose, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { confirmFile = null }) { Text("Rehne do", color = Cyan) }
            }
        )
    }

    if (confirmAll) {
        AlertDialog(
            onDismissRequest = { confirmAll = false },
            containerColor = Color(0xFF1B1733),
            titleContentColor = Color.White,
            textContentColor = Color.White.copy(alpha = 0.7f),
            title = { Text("Poori History saaf karein?") },
            text = { Text("History ki saari recordings hamesha ke liye hat jayengi.") },
            confirmButton = {
                TextButton(onClick = {
                    player.stop()
                    files.forEach { it.delete() }
                    confirmAll = false
                    tick++
                }) { Text("Sab delete karo", color = Rose, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { confirmAll = false }) { Text("Rehne do", color = Cyan) }
            }
        )
    }
}

@Composable
fun RecItem(
    file: File,
    playing: Boolean,
    onPlay: () -> Unit,
    actions: @Composable RowScope.() -> Unit
) {
    val fmt = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()) }
    val secs = RecStore.durationSec(file)

    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(24.dp))
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            if (playing) listOf(Pink, Violet) else listOf(Cyan, Violet)
                        )
                    )
                    .clickable(onClick = onPlay),
                contentAlignment = Alignment.Center
            ) {
                Text(if (playing) "■" else "▶", color = Color.White, fontSize = 18.sp)
            }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(
                    fmt.format(Date(file.lastModified())),
                    color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold
                )
                Text(
                    "%d:%02d  •  %s".format(secs / 60, secs % 60, RecStore.sizeText(file.length())),
                    color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { actions() }
    }
}
