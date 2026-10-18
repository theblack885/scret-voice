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
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import kotlin.math.PI
import kotlin.math.sqrt

fun findBtOutput(am: AudioManager): AudioDeviceInfo? =
    am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull {
        it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
            it.type == AudioDeviceInfo.TYPE_HEARING_AID ||
            (Build.VERSION.SDK_INT >= 31 && it.type == AudioDeviceInfo.TYPE_BLE_HEADSET)
    }

class MicService : Service() {

    // Is se kam awaaz wala shor "silence" maana jayega (0 se 32768). Zarurat ho to badlo.
    private val gateThreshold = 450.0

    private lateinit var am: AudioManager
    private val handler = Handler(Looper.getMainLooper())
    @Volatile private var loop = false
    @Volatile private var focusWanted = false
    private var focusReq: AudioFocusRequest? = null
    private var worker: Thread? = null

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        if (change != AudioManager.AUDIOFOCUS_GAIN && focusWanted && BtMicState.running) {
            handler.postDelayed({ if (focusWanted && BtMicState.running) requestFocus() }, 1200)
        }
    }

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            // Earbuds hat gaye to band kar do, warna awaaz phone speaker se aayegi
            if (findBtOutput(am) == null) stopSelf()
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
        if (BtMicState.running) return START_NOT_STICKY

        val btOut = findBtOutput(am)
        if (btOut == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        val channelId = "btmic"
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(channelId, "BT Mic", NotificationManager.IMPORTANCE_LOW)
        )
        val stopPi = PendingIntent.getService(
            this, 0,
            Intent(this, MicService::class.java).setAction("STOP"),
            PendingIntent.FLAG_IMMUTABLE
        )
        val stopAction = Notification.Action.Builder(
            Icon.createWithResource(this, android.R.drawable.ic_media_pause), "Band karo", stopPi
        ).build()
        val notification = Notification.Builder(this, channelId)
            .setContentTitle("BT Mic chal raha hai")
            .setContentText("Aapki awaaz earbuds me ja rahi hai")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .addAction(stopAction)
            .build()

        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(1, notification)
        }

        am.registerAudioDeviceCallback(deviceCallback, handler)
        BtMicState.record = BtMicState.autoRecord
        BtMicState.running = true
        loop = true
        worker = Thread { runAudio(btOut) }.also { it.start() }
        return START_NOT_STICKY
    }

    private fun requestFocus() {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
            .setAudioAttributes(attrs)
            .setOnAudioFocusChangeListener(focusListener, handler)
            .setWillPauseWhenDucked(true)
            .build()
        focusReq = req
        am.requestAudioFocus(req)
    }

    private fun abandonFocus() {
        focusReq?.let { am.abandonAudioFocusRequest(it) }
        focusReq = null
    }

    private fun runAudio(btOut: AudioDeviceInfo) {
        val rate = 44100
        val inMin = AudioRecord.getMinBufferSize(
            rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val outMin = AudioTrack.getMinBufferSize(
            rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
        )

        val rec = AudioRecord(
            MediaRecorder.AudioSource.MIC, rate,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, inMin * 2
        )
        am.getDevices(AudioManager.GET_DEVICES_INPUTS)
            .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_MIC }
            ?.let { rec.setPreferredDevice(it) }

        val fx = mutableListOf<android.media.audiofx.AudioEffect>()
        try {
            if (NoiseSuppressor.isAvailable())
                NoiseSuppressor.create(rec.audioSessionId)?.also { it.enabled = true; fx.add(it) }
            if (AutomaticGainControl.isAvailable())
                AutomaticGainControl.create(rec.audioSessionId)?.also { it.enabled = true; fx.add(it) }
            if (AcousticEchoCanceler.isAvailable())
                AcousticEchoCanceler.create(rec.audioSessionId)?.also { it.enabled = true; fx.add(it) }
        } catch (_: Exception) {}

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(rate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(outMin)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .build()
        track.setPreferredDevice(btOut)

        val dt = 1.0 / rate
        val rcHp = 1.0 / (2 * PI * 120.0)
        val hpA = (rcHp / (rcHp + dt)).toFloat()
        val rcLp = 1.0 / (2 * PI * 6000.0)
        val lpA = (dt / (rcLp + dt)).toFloat()
        var x1 = 0f
        var yHp = 0f
        var yLp = 0f
        var gate = 0f
        var lastVoice = 0L

        var wav: WavWriter? = null
        val buf = ShortArray(inMin / 2)
        try {
            rec.startRecording()
            track.play()
            while (loop) {
                val want = BtMicState.blockOthers
                if (want != focusWanted) {
                    focusWanted = want
                    handler.post { if (want) requestFocus() else abandonFocus() }
                }

                val n = rec.read(buf, 0, buf.size)
                if (n <= 0) continue
                val g = BtMicState.gain

                // Awaaz aa rahi hai ya nahi (processing se pehle ki awaaz par)
                var s0 = 0.0
                for (i in 0 until n) s0 += buf[i].toDouble() * buf[i]
                val rms0 = sqrt(s0 / n)
                val now = SystemClock.elapsedRealtime()
                val voiceNow = rms0 > gateThreshold
                if (voiceNow) lastVoice = now

                var outSum = 0.0
                if (BtMicState.voiceOnly) {
                    val target = if (now - lastVoice < 350) 1f else 0f
                    for (i in 0 until n) {
                        val x = buf[i].toFloat()
                        val hp = hpA * (yHp + x - x1)
                        x1 = x
                        yHp = hp
                        yLp += lpA * (hp - yLp)
                        gate += (target - gate) * (if (target > gate) 0.02f else 0.002f)
                        val v = (yLp * gate * g).toInt().coerceIn(-32768, 32767)
                        buf[i] = v.toShort()
                        outSum += v.toDouble() * v
                    }
                } else {
                    for (i in 0 until n) {
                        val v = (buf[i] * g).toInt().coerceIn(-32768, 32767)
                        buf[i] = v.toShort()
                        outSum += v.toDouble() * v
                    }
                }

                BtMicState.level = ((sqrt(outSum / n) / 32768.0) * 6.0).toFloat().coerceIn(0f, 1f)
                track.write(buf, 0, n)

                // Recording: pehli awaaz aate hi apne aap shuru, "Band karo" dabane par band
                try {
                    if (BtMicState.record) {
                        if (wav == null && voiceNow) {
                            val f = RecStore.newFile(this)
                            wav = WavWriter(f, rate)
                            BtMicState.currentRecPath = f.path
                            BtMicState.recStartMs = now
                        }
                        wav?.write(buf, n)
                    } else if (wav != null) {
                        wav?.close()
                        wav = null
                        BtMicState.currentRecPath = null
                        BtMicState.recStartMs = 0L
                    }
                } catch (_: Exception) {
                    wav = null
                    BtMicState.currentRecPath = null
                    BtMicState.recStartMs = 0L
                    BtMicState.record = false
                }
            }
        } catch (_: Exception) {
        } finally {
            try { wav?.close() } catch (_: Exception) {}
            BtMicState.currentRecPath = null
            BtMicState.recStartMs = 0L
            try { rec.stop() } catch (_: Exception) {}
            rec.release()
            try { track.stop() } catch (_: Exception) {}
            track.release()
            fx.forEach { try { it.release() } catch (_: Exception) {} }
        }
    }

    override fun onDestroy() {
        loop = false
        focusWanted = false
        worker?.join(1000)
        handler.removeCallbacksAndMessages(null)
        abandonFocus()
        try { am.unregisterAudioDeviceCallback(deviceCallback) } catch (_: Exception) {}
        BtMicState.running = false
        BtMicState.level = 0f
        super.onDestroy()
    }
}
