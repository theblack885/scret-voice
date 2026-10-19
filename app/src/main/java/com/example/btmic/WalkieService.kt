package com.example.btmic

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AudioEffect
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.SocketTimeoutException
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.math.sqrt
import kotlin.random.Random

private const val WK_PORT = 50505
private const val WK_RATE = 16000
private const val WK_FRAME = 320          // 20 ms
private const val WK_HEADER = 8

// Earbuds ke mic / speaker ka Bluetooth call (SCO) route
fun findScoIn(am: AudioManager): AudioDeviceInfo? =
    am.getDevices(AudioManager.GET_DEVICES_INPUTS).firstOrNull {
        it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
            (Build.VERSION.SDK_INT >= 31 && it.type == AudioDeviceInfo.TYPE_BLE_HEADSET)
    }

fun findScoOut(am: AudioManager): AudioDeviceInfo? =
    am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull {
        it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
            (Build.VERSION.SDK_INT >= 31 && it.type == AudioDeviceInfo.TYPE_BLE_HEADSET)
    }

class WalkieService : Service() {

    // Is se kam awaaz "khamoshi" maani jayegi (hands-free mode me)
    private val voxThreshold = 350.0

    private lateinit var am: AudioManager
    private val handler = Handler(Looper.getMainLooper())
    @Volatile private var loop = false
    private var focusReq: AudioFocusRequest? = null
    private var mainThread: Thread? = null
    private var rxThread: Thread? = null
    private var playThread: Thread? = null
    private var socket: DatagramSocket? = null
    private val queue = LinkedBlockingQueue<ShortArray>()
    private val selfId = Random.nextInt()

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            if (WalkieState.ready && findBtOutput(am) == null && findScoOut(am) == null) {
                stopSelf()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            stopSelf()
            return START_NOT_STICKY
        }

        val channelId = "walkie"
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(channelId, "Walkie Talkie", NotificationManager.IMPORTANCE_LOW)
        )
        val stopPi = PendingIntent.getService(
            this, 1,
            Intent(this, WalkieService::class.java).setAction("STOP"),
            PendingIntent.FLAG_IMMUTABLE
        )
        val stopAction = Notification.Action.Builder(
            Icon.createWithResource(this, android.R.drawable.ic_media_pause), "Band karo", stopPi
        ).build()
        val notification = Notification.Builder(this, channelId)
            .setContentTitle("Walkie mode ON")
            .setContentText("Earbuds se earbuds me baat ho rahi hai")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .addAction(stopAction)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(2, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(2, notification)
        }

        if (WalkieState.running) return START_NOT_STICKY

        if (findBtOutput(am) == null) {
            WalkieState.error = "Pehle Bluetooth earbuds connect karo"
            stopSelf()
            return START_NOT_STICKY
        }

        WalkieState.error = null
        WalkieState.ready = false
        WalkieState.running = true
        am.registerAudioDeviceCallback(deviceCallback, handler)
        loop = true
        mainThread = Thread { runWalkie() }.also { it.start() }
        return START_NOT_STICKY
    }

    private fun fail(msg: String) {
        WalkieState.error = msg
        stopSelf()
    }

    @Suppress("DEPRECATION")
    private fun routeToSco(): Boolean {
        if (Build.VERSION.SDK_INT >= 31) {
            val dev = am.availableCommunicationDevices.firstOrNull {
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                    it.type == AudioDeviceInfo.TYPE_BLE_HEADSET
            } ?: return false
            if (!am.setCommunicationDevice(dev)) return false
        } else {
            am.startBluetoothSco()
            am.isBluetoothScoOn = true
        }
        // Earbuds ke mic wala Bluetooth link banne ka intezaar
        var t = 0
        while (t < 60 && loop && findScoIn(am) == null) {
            Thread.sleep(50)
            t++
        }
        Thread.sleep(600)
        return true
    }

    @Suppress("DEPRECATION")
    private fun releaseSco() {
        try {
            if (Build.VERSION.SDK_INT >= 31) {
                am.clearCommunicationDevice()
            } else {
                am.isBluetoothScoOn = false
                am.stopBluetoothSco()
            }
        } catch (_: Exception) {
        }
        am.mode = AudioManager.MODE_NORMAL
    }

    private fun runWalkie() {
        var rec: AudioRecord? = null
        var track: AudioTrack? = null
        val fx = mutableListOf<AudioEffect>()
        try {
            // Live mic mode chal raha ho to uske band hone ka intezaar
            var w = 0
            while (BtMicState.running && w < 40) {
                Thread.sleep(50)
                w++
            }
            if (BtMicState.running) {
                fail("Pehle Live mic band karo")
                return
            }

            // Dusre apps (music/video) ko pause karo
            handler.post {
                val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setOnAudioFocusChangeListener { }
                    .build()
                focusReq = req
                am.requestAudioFocus(req)
            }

            // Phone ka mic/speaker nahi, earbuds ka mic aur speaker
            am.mode = AudioManager.MODE_IN_COMMUNICATION
            if (!routeToSco()) {
                fail("Earbuds ka mic nahi mila")
                return
            }
            if (!loop) return

            val inMin = AudioRecord.getMinBufferSize(
                WK_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
            )
            rec = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION, WK_RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                maxOf(inMin, WK_FRAME * 2) * 2
            )
            if (rec.state != AudioRecord.STATE_INITIALIZED) {
                fail("Mic shuru nahi ho paya")
                return
            }
            findScoIn(am)?.let { rec.setPreferredDevice(it) }

            try {
                if (NoiseSuppressor.isAvailable())
                    NoiseSuppressor.create(rec.audioSessionId)?.also { it.enabled = true; fx.add(it) }
                if (AcousticEchoCanceler.isAvailable())
                    AcousticEchoCanceler.create(rec.audioSessionId)?.also { it.enabled = true; fx.add(it) }
                if (AutomaticGainControl.isAvailable())
                    AutomaticGainControl.create(rec.audioSessionId)?.also { it.enabled = true; fx.add(it) }
            } catch (_: Exception) {
            }

            val outMin = AudioTrack.getMinBufferSize(
                WK_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
            )
            track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(WK_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(maxOf(outMin, WK_FRAME * 2 * 4))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            findScoOut(am)?.let { track.setPreferredDevice(it) }

            // Network: dono phone ek hi WiFi / hotspot par UDP se baat karenge
            val sock = DatagramSocket(null)
            sock.reuseAddress = true
            sock.broadcast = true
            sock.soTimeout = 500
            sock.bind(InetSocketAddress(WK_PORT))
            socket = sock

            rxThread = Thread { rxLoop(sock) }.also { it.start() }
            val tr = track
            playThread = Thread { playLoop(tr) }.also { it.start() }

            rec.startRecording()
            track.play()
            WalkieState.ready = true

            txLoop(sock, rec)
        } catch (e: Exception) {
            if (loop) fail("Walkie shuru nahi ho paya")
        } finally {
            loop = false
            try { socket?.close() } catch (_: Exception) {}
            try { rxThread?.join(800) } catch (_: Exception) {}
            try { playThread?.join(800) } catch (_: Exception) {}
            try { rec?.stop() } catch (_: Exception) {}
            rec?.release()
            try { track?.stop() } catch (_: Exception) {}
            track?.release()
            fx.forEach { try { it.release() } catch (_: Exception) {} }
            releaseSco()
            queue.clear()
            WalkieState.ready = false
            WalkieState.transmitting = false
            WalkieState.txLevel = 0f
            WalkieState.rxLevel = 0f
            WalkieState.pttDown = false
        }
    }

    private fun broadcastAddrs(): List<InetAddress> {
        val out = mutableListOf<InetAddress>()
        try {
            for (ni in NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()) {
                if (!ni.isUp || ni.isLoopback) continue
                for (ia in ni.interfaceAddresses) {
                    ia.broadcast?.let { out.add(it) }
                }
            }
        } catch (_: Exception) {
        }
        try {
            out.add(InetAddress.getByName("255.255.255.255"))
        } catch (_: Exception) {
        }
        return out.distinct()
    }

    private fun fillHeader(buf: ByteArray) {
        val room = WalkieState.room
        buf[0] = 'W'.code.toByte()
        buf[1] = 'K'.code.toByte()
        buf[2] = (room shr 8).toByte()
        buf[3] = room.toByte()
        buf[4] = (selfId shr 24).toByte()
        buf[5] = (selfId shr 16).toByte()
        buf[6] = (selfId shr 8).toByte()
        buf[7] = selfId.toByte()
    }

    private fun send(sock: DatagramSocket, addrs: List<InetAddress>, buf: ByteArray, len: Int) {
        for (a in addrs) {
            try {
                sock.send(DatagramPacket(buf, len, a, WK_PORT))
            } catch (_: Exception) {
            }
        }
    }

    // Earbuds ke mic se awaaz leke dusre phone ko bhejna
    private fun txLoop(sock: DatagramSocket, rec: AudioRecord) {
        val frame = ShortArray(WK_FRAME)
        val buf = ByteArray(WK_HEADER + WK_FRAME * 2)
        var lastVoice = 0L
        var lastHb = 0L
        var lastAddr = 0L
        var addrs = broadcastAddrs()

        while (loop) {
            val n = rec.read(frame, 0, WK_FRAME)
            if (n <= 0) continue
            val now = SystemClock.elapsedRealtime()

            var s = 0.0
            for (i in 0 until n) s += frame[i].toDouble() * frame[i]
            val rms = sqrt(s / n)
            val receiving = now - WalkieState.lastRxMs < 500

            val talk = when {
                WalkieState.mute -> false
                WalkieState.ptt -> WalkieState.pttDown
                else -> {
                    if (!receiving && rms > voxThreshold) lastVoice = now
                    now - lastVoice < 600
                }
            }

            if (now - lastAddr > 3000) {
                addrs = broadcastAddrs()
                lastAddr = now
            }

            WalkieState.transmitting = talk
            if (talk) {
                fillHeader(buf)
                for (i in 0 until n) {
                    val v = frame[i].toInt()
                    buf[WK_HEADER + i * 2] = (v and 0xFF).toByte()
                    buf[WK_HEADER + i * 2 + 1] = ((v shr 8) and 0xFF).toByte()
                }
                send(sock, addrs, buf, WK_HEADER + n * 2)
                WalkieState.txLevel = ((rms / 32768.0) * 8.0).toFloat().coerceIn(0f, 1f)
            } else {
                WalkieState.txLevel = 0f
                if (now - lastHb > 1000) {
                    fillHeader(buf)
                    send(sock, addrs, buf, WK_HEADER)
                    lastHb = now
                }
            }
        }
    }

    // Dusre phone se aayi awaaz receive karna
    private fun rxLoop(sock: DatagramSocket) {
        val data = ByteArray(2048)
        val pkt = DatagramPacket(data, data.size)
        while (loop) {
            try {
                pkt.length = data.size
                sock.receive(pkt)
            } catch (_: SocketTimeoutException) {
                continue
            } catch (_: Exception) {
                if (!loop) break
                try { Thread.sleep(50) } catch (_: Exception) {}
                continue
            }
            val len = pkt.length
            if (len < WK_HEADER) continue
            if (data[0] != 'W'.code.toByte() || data[1] != 'K'.code.toByte()) continue
            val room = ((data[2].toInt() and 0xFF) shl 8) or (data[3].toInt() and 0xFF)
            val id = ((data[4].toInt() and 0xFF) shl 24) or
                ((data[5].toInt() and 0xFF) shl 16) or
                ((data[6].toInt() and 0xFF) shl 8) or
                (data[7].toInt() and 0xFF)
            if (room != (WalkieState.room and 0xFFFF) || id == selfId) continue

            val now = SystemClock.elapsedRealtime()
            WalkieState.lastPeerMs = now
            val n = (len - WK_HEADER) / 2
            if (n <= 0) continue

            val s = ShortArray(n)
            for (i in 0 until n) {
                val o = WK_HEADER + i * 2
                s[i] = ((data[o + 1].toInt() shl 8) or (data[o].toInt() and 0xFF)).toShort()
            }
            WalkieState.lastRxMs = now
            queue.offer(s)
            while (queue.size > 12) queue.poll()
        }
    }

    // Aayi hui awaaz earbuds ke speaker me chalana
    private fun playLoop(track: AudioTrack) {
        while (loop) {
            val p = try {
                queue.poll(60, TimeUnit.MILLISECONDS)
            } catch (_: InterruptedException) {
                null
            } ?: continue
            val g = WalkieState.rxGain
            var sum = 0.0
            for (i in p.indices) {
                val v = (p[i] * g).toInt().coerceIn(-32768, 32767)
                p[i] = v.toShort()
                sum += v.toDouble() * v
            }
            WalkieState.rxLevel = ((sqrt(sum / p.size) / 32768.0) * 8.0).toFloat().coerceIn(0f, 1f)
            try {
                track.write(p, 0, p.size)
            } catch (_: Exception) {
            }
        }
    }

    override fun onDestroy() {
        loop = false
        try { socket?.close() } catch (_: Exception) {}
        try { mainThread?.join(2000) } catch (_: Exception) {}
        handler.removeCallbacksAndMessages(null)
        focusReq?.let { am.abandonAudioFocusRequest(it) }
        focusReq = null
        try { am.unregisterAudioDeviceCallback(deviceCallback) } catch (_: Exception) {}
        WalkieState.running = false
        WalkieState.ready = false
        super.onDestroy()
    }
}
