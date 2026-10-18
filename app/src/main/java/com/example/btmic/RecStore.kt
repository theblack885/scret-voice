package com.example.btmic

import android.content.Context
import android.media.MediaPlayer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object RecStore {
    fun dir(ctx: Context): File = File(ctx.filesDir, "recordings").also { it.mkdirs() }
    fun trash(ctx: Context): File = File(ctx.filesDir, "trash").also { it.mkdirs() }

    fun newFile(ctx: Context): File {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return File(dir(ctx), "rec_$stamp.wav")
    }

    fun list(d: File): List<File> =
        d.listFiles { f -> f.extension == "wav" }
            ?.sortedByDescending { it.lastModified() } ?: emptyList()

    fun moveTo(f: File, destDir: File): Boolean {
        destDir.mkdirs()
        var target = File(destDir, f.name)
        if (target.exists()) {
            target = File(destDir, "${f.nameWithoutExtension}_${System.currentTimeMillis()}.wav")
        }
        return f.renameTo(target)
    }

    fun durationSec(f: File): Int = ((f.length() - 44) / (44100 * 2)).toInt().coerceAtLeast(0)

    fun sizeText(bytes: Long): String =
        if (bytes >= 1024 * 1024) "%.1f MB".format(bytes / 1048576.0)
        else "${bytes / 1024} KB"
}

class WavWriter(private val file: File, private val rate: Int) {
    private val out = BufferedOutputStream(FileOutputStream(file), 32768)
    private var dataBytes = 0L

    init {
        out.write(ByteArray(44))
    }

    fun write(buf: ShortArray, n: Int) {
        val b = ByteArray(n * 2)
        for (i in 0 until n) {
            val s = buf[i].toInt()
            b[i * 2] = (s and 0xFF).toByte()
            b[i * 2 + 1] = ((s shr 8) and 0xFF).toByte()
        }
        out.write(b)
        dataBytes += b.size
    }

    fun close() {
        out.flush()
        out.close()
        val h = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        h.put("RIFF".toByteArray())
        h.putInt((36 + dataBytes).toInt())
        h.put("WAVE".toByteArray())
        h.put("fmt ".toByteArray())
        h.putInt(16)
        h.putShort(1)
        h.putShort(1)
        h.putInt(rate)
        h.putInt(rate * 2)
        h.putShort(2)
        h.putShort(16)
        h.put("data".toByteArray())
        h.putInt(dataBytes.toInt())
        RandomAccessFile(file, "rw").use {
            it.seek(0)
            it.write(h.array())
        }
    }
}

class AudioPlayer {
    private var mp: MediaPlayer? = null
    var playingPath by mutableStateOf<String?>(null)

    fun toggle(f: File) {
        if (playingPath == f.path) {
            stop()
            return
        }
        stop()
        try {
            mp = MediaPlayer().apply {
                setDataSource(f.path)
                setOnCompletionListener { stop() }
                prepare()
                start()
            }
            playingPath = f.path
        } catch (_: Exception) {
            stop()
        }
    }

    fun stop() {
        try { mp?.stop() } catch (_: Exception) {}
        mp?.release()
        mp = null
        playingPath = null
    }
}
