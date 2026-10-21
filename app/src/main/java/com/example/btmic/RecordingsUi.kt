package com.example.btmic

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileInputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val Rose = Color(0xFFFF6B8B)
val Green = Color(0xFF2ED573)
val Cyan = Color(0xFF1E90FF)

@Composable
fun Glass(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(24.dp))
            .padding(16.dp)
    ) {
        content()
    }
}

class RecordingsUiActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HideCameraMainScreen()
        }
    }
}

@Composable
fun HideCameraMainScreen() {
    val ctx = LocalContext.current
    var isCameraRunning by remember { mutableStateOf(false) }
    var showPasscodeScreen by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0C20))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(40.dp))
        Text("System Controller", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("Manage system tools and background utilities.", color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp)
        
        Spacer(Modifier.height(40.dp))

        Glass {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Hide Camera Engine", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("Record completely hidden video", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                }
                
                Button(
                    onClick = {
                        val serviceIntent = Intent(ctx, BackgroundRecorderService::class.java)
                        if (!isCameraRunning) {
                            if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                                ctx.startService(serviceIntent)
                                isCameraRunning = true
                                Toast.makeText(ctx, "Hidden Camera Engine Started", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(ctx, "Camera Permission Required!", Toast.LENGTH_LONG).show()
                            }
                        } else {
                            ctx.stopService(serviceIntent)
                            isCameraRunning = false
                            Toast.makeText(ctx, "Hidden Camera Engine Stopped", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = if (isCameraRunning) Green else Rose)
                ) {
                    Text(if (isCameraRunning) "ACTIVE" else "START", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        Button(
            onClick = { showPasscodeScreen = true },
            modifier = Modifier.fillMaxWidth().height(55.dp),
            shape = RoundedCornerShape(24.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f))
        ) {
            Text("Open Secret Video Locker", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }

        if (showPasscodeScreen) {
            SecurePasscodeDialog(
                onDismiss = { showPasscodeScreen = false },
                onSuccess = {
                    showPasscodeScreen = false
                }
            )
        }
    }
}

@Composable
fun SecurePasscodeDialog(onDismiss: () -> Unit, onSuccess: () -> Unit) {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("AppSecurityPrefs", Context.MODE_PRIVATE) }
    
    var inputCode by remember { mutableStateOf("") }
    var failedAttempts by remember { mutableStateOf(prefs.getInt("failed_attempts", 0)) }
    var lockoutEndTime by remember { mutableStateOf(prefs.getLong("lockout_end_time", 0)) }
    var showGalleryView by remember { mutableStateOf(false) }

    val isLockedOut = System.currentTimeMillis() < lockoutEndTime

    if (showGalleryView) {
        SecureVideoGalleryView(onClose = { 
            showGalleryView = false 
            onDismiss()
        })
        return
    }

    AlertDialog(
        onDismissRequest = { onDismiss() },
        containerColor = Color(0xFF1B1733),
        titleContentColor = Color.White,
        textContentColor = Color.White.copy(alpha = 0.7f),
        title = { Text("Security Verification", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                if (isLockedOut) {
                    val remainingHours = ((lockoutEndTime - System.currentTimeMillis()) / (60 * 60 * 1000)) + 1
                    Text("App is locked due to 5 wrong passcode entry. Try again after $remainingHours hours.", color = Rose)
                } else {
                    Text("Enter master passcode to unlock encrypted background recordings database.", modifier = Modifier.padding(bottom = 12.dp))
                    TextField(
                        value = inputCode,
                        onValueChange = { if (it.length <= 4) inputCode = it },
                        label = { Text("4-Digit Code") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent)
                    )
                }
            }
        },
        confirmButton = {
            if (!isLockedOut) {
                TextButton(onClick = {
                    if (inputCode == "1615") {
                        prefs.edit().putInt("failed_attempts", 0).putLong("lockout_end_time", 0).apply()
                        showGalleryView = true
                    } else {
                        val currentFailed = failedAttempts + 1
                        failedAttempts = currentFailed
                        prefs.edit().putInt("failed_attempts", currentFailed).apply()

                        if (currentFailed >= 5) {
                            val unlockTime = System.currentTimeMillis() + (24 * 60 * 60 * 1000)
                            prefs.edit().putLong("lockout_end_time", unlockTime).apply()
                            lockoutEndTime = unlockTime
                            Toast.makeText(ctx, "Brute-force lockout triggered! App locked for 24 hours.", Toast.LENGTH_LONG).show()
                        } else {
                            val attemptsLeft = 5 - currentFailed
                            Toast.makeText(ctx, "Incorrect code! Attempts left: $attemptsLeft", Toast.LENGTH_SHORT).show()
                        }
                    }
                }) {
                    Text("Verify", color = Green, fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = { onDismiss() }) {
                Text("Cancel", color = Color.White.copy(alpha = 0.5f))
            }
        }
    )
}

@Composable
fun SecureVideoGalleryView(onClose: () -> Unit) {
    val ctx = LocalContext.current
    var privateVideos by remember { mutableStateOf(listOf<File>()) }
    var refreshTick by remember { mutableStateOf(0) }

    LaunchedEffect(refreshTick) {
        val storageDir = ctx.getExternalFilesDir(null)
        privateVideos = storageDir?.listFiles { file -> file.name.startsWith("SEC_VID_") && file.name.endsWith(".mp4") }?.toList() ?: emptyList()
    }
