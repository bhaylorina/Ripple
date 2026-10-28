package com.ripple.iptv.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.C
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

    fun initializePlayer() {
        if (player != null) return
        
        player = ExoPlayer.Builder(context).build()
    }

    // Playback function with Header and DRM support
    fun playStream(url: String, headers: Map<String, String>? = null, drmLicenseUrlOrJson: String? = null) {
        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            
        // Agar channel me specific headers (User-Agent, Cookie) hain toh lagao
        if (!headers.isNullOrEmpty()) {
            dataSourceFactory.setDefaultRequestProperties(headers)
        }

        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)
            .setDrmSessionManagerProvider(DefaultDrmSessionManagerProvider())
            
        player?.setMediaSourceFactory(mediaSourceFactory)

        val mediaItemBuilder = MediaItem.Builder().setUri(url)

        // Jio aur doosre DRM streams ke liye
        if (!drmLicenseUrlOrJson.isNullOrEmpty()) {
            val drmConfiguration = MediaItem.DrmConfiguration.Builder(C.CLEARKEY_UUID)
            
            // Check if it's a direct JSON string for Jio
            if (drmLicenseUrlOrJson.trim().startsWith("{")) {
                drmConfiguration.setLicenseUri("data:,$drmLicenseUrlOrJson")
            } else {
                drmConfiguration.setLicenseUri(drmLicenseUrlOrJson)
            }
            
            drmConfiguration.setMultiSession(true)
            mediaItemBuilder.setDrmConfiguration(drmConfiguration.build())
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
