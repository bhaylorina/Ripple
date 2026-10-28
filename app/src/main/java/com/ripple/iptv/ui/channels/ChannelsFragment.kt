package com.ripple.iptv.ui.channels

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.ripple.iptv.databinding.FragmentChannelsBinding

class ChannelsFragment : Fragment() {

    private var _binding: FragmentChannelsBinding? = null
    // ViewBinding se humein findViewById nahi likhna padega
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChannelsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Abhi hum test karne ke liye "ADD PLAYLIST" button par click listener lagayenge
        binding.btnAddPlaylist.setOnClickListener {
            
            // Dummy HLS stream URL test karne ke liye
            val dummyStreamUrl = "http://sample.vodobox.net/skate_phantom_flex_4k/skate_phantom_flex_4k.m3u8"
            
            // Navigation Component aur SafeArgs ka use karke stream URL doosre fragment mein bhejna
            val action = ChannelsFragmentDirections.actionChannelsFragmentToPlayerFragment(dummyStreamUrl)
            findNavController().navigate(action)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null // Memory leak se bachne ke liye zaruri hai
    }
}
