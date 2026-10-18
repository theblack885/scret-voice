package com.example.btmic

object BtMicState {
    @Volatile var running = false
    @Volatile var gain = 1.5f
    @Volatile var voiceOnly = true
    @Volatile var blockOthers = true
    @Volatile var level = 0f
}
