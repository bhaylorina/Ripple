package com.ripple.iptv.core

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import okhttp3.OkHttpClient

object PlayerEngine {
    
    // Clean standard OkHttp Client without custom DNS/Socket hacks
    val okHttpClient: OkHttpClient = OkHttpClient.Builder().build()

    fun createPlayer(context: Context): ExoPlayer {
        // Virgin ExoPlayer instance without custom renderers or sync offsets
        return ExoPlayer.Builder(context).build()
    }

    fun playChannel(context: Context, player: ExoPlayer, channel: Channel) {
        val streamUrl = channel.url.trim()
        
        // Setup basic headers from M3U
        val headers = channel.getHeadersMap().toMutableMap()
        if (!channel.cookie.isNullOrBlank()) headers["Cookie"] = channel.cookie!!
        if (!channel.userAgent.isNullOrBlank()) headers["User-Agent"] = channel.userAgent!!

        // Standard HTTP Data Source Factory
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(headers)

        val mediaSourceFactory = DefaultMediaSourceFactory(httpDataSourceFactory)

        // Build MediaItem
        val mediaItemBuilder = MediaItem.Builder().setUri(streamUrl)

        // Optional: Set MimeType if known (helps player start faster)
        val lowerUrl = streamUrl.lowercase()
        if (lowerUrl.contains(".m3u8")) {
            mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_M3U8)
        } else if (lowerUrl.contains(".mpd")) {
            mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_MPD)
        }

        // Apply MediaSource to Player
        val mediaSource = mediaSourceFactory.createMediaSource(mediaItemBuilder.build())
        
        player.setMediaSource(mediaSource)
        player.prepare()
        player.playWhenReady = true
    }
}
