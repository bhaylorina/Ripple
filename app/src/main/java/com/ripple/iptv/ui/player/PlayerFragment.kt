package com.ripple.iptv.ui.player

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.navArgs
import com.ripple.iptv.databinding.FragmentPlayerBinding
import com.ripple.iptv.player.RipplePlayerManager

class PlayerFragment : Fragment() {

    private var _binding: FragmentPlayerBinding? = null
    private val binding get() = _binding!!
    private lateinit var playerManager: RipplePlayerManager
    
    // SafeArgs plugin ye parameter (streamUrl) automatic le aayega
    private val args: PlayerFragmentArgs by navArgs()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPlayerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Engine initialize karna
        playerManager = RipplePlayerManager(requireContext())
        
        // ExoPlayer ko UI (PlayerView) ke sath link karna
        binding.playerView.player = playerManager.player

        // Yahan par aage chalkar hum Jio/SonyLIV ke liye custom User-Agent aur tokens daal sakte hain
        val customHeaders = mapOf(
            "User-Agent" to "Ripple-IPTV-Player/1.0"
        )
        playerManager.initializePlayer(customHeaders)

        // SafeArgs se URL nikalna aur play karna
        val streamUrl = args.streamUrl
        if (streamUrl.isNotEmpty()) {
            playerManager.playStream(streamUrl)
        }

        setupCustomControls()
    }

    private fun setupCustomControls() {
        binding.btnPlay.setOnClickListener {
            playerManager.player?.let { exo ->
                if (exo.isPlaying) exo.pause() else exo.play()
            }
        }

        binding.btnBackToPlaylists.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Fragment band hote hi player release kar dena chahiye warna aawaz piche chalti rahegi
        playerManager.releasePlayer()
        _binding = null
    }
}
