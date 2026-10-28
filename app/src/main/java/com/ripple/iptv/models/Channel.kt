package com.ripple.iptv.models

data class Channel(
    val name: String,
    val streamUrl: String,
    val logoUrl: String = "",
    val group: String = "Uncategorized",
    val tvgId: String = "", // EPG mapping ke liye
    var isFavorite: Boolean = false
)
