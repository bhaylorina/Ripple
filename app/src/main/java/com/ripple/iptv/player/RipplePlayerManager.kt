package com.ripple.iptv.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.drm.DefaultDrmSessionManagerProvider
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory

@OptIn(UnstableApi::class)
class RipplePlayerManager(private val context: Context) {

    var player: ExoPlayer? = null
        private set

    // Yahan hum Python script se mile hue headers (User-Agent, Token) inject karenge
    fun initializePlayer(headers: Map<String, String> = emptyMap()) {
        if (player != null) return

        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(headers)

        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)
            .setDrmSessionManagerProvider(DefaultDrmSessionManagerProvider())

        player = ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
    }

    // Stream URL aur DRM (ClearKey) parameters chalane ke liye
    fun playStream(url: String, clearKeyId: String? = null, clearKeyKey: String? = null) {
        val mediaItemBuilder = MediaItem.Builder().setUri(url)

        if (!clearKeyId.isNullOrEmpty() && !clearKeyKey.isNullOrEmpty()) {
            val keyString = "{\"keys\":[{\"kty\":\"oct\",\"k\":\"$clearKeyKey\",\"kid\":\"$clearKeyId\"}],\"type\":\"temporary\"}"
            val drmConfiguration = MediaItem.DrmConfiguration.Builder(androidx.media3.common.C.CLEARKEY_UUID)
                .setLicenseUri("data:," + keyString)
                .build()
            
            mediaItemBuilder.setDrmConfiguration(drmConfiguration)
        }

        player?.apply {
            setMediaItem(mediaItemBuilder.build())
            prepare()
            playWhenReady = true
        }
    }

    fun releasePlayer() {
        player?.release()
        player = null
    }
}
