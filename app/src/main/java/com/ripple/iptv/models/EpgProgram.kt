package com.ripple.iptv.models

data class EpgProgram(
    val title: String,
    val startMs: Long,
    val endMs: Long
)
