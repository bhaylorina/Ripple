package com.ripple.iptv.ui.player

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.ripple.iptv.databinding.FragmentPlayerBinding
import com.ripple.iptv.player.RipplePlayerManager

class PlayerFragment : Fragment() {

    private var _binding: FragmentPlayerBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var playerManager: RipplePlayerManager
    private val args: PlayerFragmentArgs by navArgs() // Gets data sent from ChannelsFragment

    private val handler = Handler(Looper.getMainLooper())
    private val updateProgressRunnable = object : Runnable {
        override fun run() {
            updateSeekbar()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPlayerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize pure Player Engine
        playerManager = RipplePlayerManager(requireContext())
        playerManager.initializePlayer()
        binding.playerView.player = playerManager.player

        // Setup channel info
        binding.epgChannelName.text = args.channelName

        // Start playback
        val url = args.streamUrl
        val drmData = args.drmData
        if (url.isNotEmpty()) {
            playerManager.playStream(url = url, drmLicenseUrlOrJson = drmData)
        }

        setupControls()
        handler.post(updateProgressRunnable)
    }

    private fun setupControls() {
        // Back Button
        binding.btnBackToPlaylists.setOnClickListener {
            findNavController().navigateUp()
        }

        // Play / Pause Toggle
        binding.btnPlay.setOnClickListener {
            playerManager.player?.let { exo ->
                if (exo.isPlaying) {
                    exo.pause()
                    binding.btnPlay.text = "▶"
                } else {
                    exo.play()
                    binding.btnPlay.text = "||"
                }
            }
        }
    }

    private fun updateSeekbar() {
        val player = playerManager.player ?: return
        val duration = player.duration
        val position = player.currentPosition

        if (duration > 0 && duration != androidx.media3.common.C.TIME_UNSET) {
            binding.customSeekbar.max = duration.toInt()
            binding.customSeekbar.progress = position.toInt()
            binding.tvTotalDuration.text = formatTime(duration)
            binding.tvCurrentTime.text = formatTime(position)
        } else {
            // Live Stream
            binding.customSeekbar.progress = 0
            binding.tvTotalDuration.text = "LIVE"
            binding.tvCurrentTime.text = formatTime(position)
        }
    }

    private fun formatTime(ms: Long): String {
        val totalSecs = (ms / 1000).toInt()
        val secs = totalSecs % 60
        val mins = (totalSecs / 60) % 60
        val hrs = totalSecs / 3600
        return if (hrs > 0) String.format("%02d:%02d:%02d", hrs, mins, secs)
        else String.format("%02d:%02d", mins, secs)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacks(updateProgressRunnable)
        playerManager.releasePlayer()
        _binding = null
    }
}
