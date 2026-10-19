package com.example.btmic

import android.content.Context

object WalkieState {
    // Live state
    @Volatile var running = false
    @Volatile var ready = false
    @Volatile var transmitting = false
    @Volatile var txLevel = 0f
    @Volatile var rxLevel = 0f
    @Volatile var lastRxMs = 0L
    @Volatile var lastPeerMs = 0L
    @Volatile var error: String? = null
    @Volatile var pttDown = false

    // Settings
    @Volatile var mute = false
    @Volatile var room = 1234
    @Volatile var ptt = false
    @Volatile var rxGain = 1.5f

    fun load(ctx: Context) {
        val p = ctx.getSharedPreferences("btmic", Context.MODE_PRIVATE)
        room = p.getInt("wk_room", 1234)
        ptt = p.getBoolean("wk_ptt", false)
        rxGain = p.getFloat("wk_gain", 1.5f)
    }

    fun save(ctx: Context) {
        ctx.getSharedPreferences("btmic", Context.MODE_PRIVATE).edit()
            .putInt("wk_room", room)
            .putBoolean("wk_ptt", ptt)
            .putFloat("wk_gain", rxGain)
            .apply()
    }
}
