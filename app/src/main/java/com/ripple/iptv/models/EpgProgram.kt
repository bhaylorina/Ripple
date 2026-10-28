package com.ripple.iptv.models

data class EpgProgram(
    val channelId: String, // tvg-id ke sath match karne ke liye
    val title: String,
    val startTime: Long, // Unix timestamp (milliseconds)
    val endTime: Long,
    val description: String = ""
)
