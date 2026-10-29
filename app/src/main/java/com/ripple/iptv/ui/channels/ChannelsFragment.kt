package com.ripple.iptv.ui.channels

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.ripple.iptv.R
import com.ripple.iptv.models.Channel // 🔥 Fixed import reference
import com.ripple.iptv.core.IptvParser
import com.ripple.iptv.databinding.FragmentChannelsBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class ChannelsFragment : Fragment() {

    private var _binding: FragmentChannelsBinding? = null
    private val binding get() = _binding!!

    private val allChannels = ArrayList<Channel>()
    private val displayChannels = ArrayList<Channel>()
    private lateinit var channelAdapter: ChannelAdapter

    private val httpClient = OkHttpClient.Builder().build()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentChannelsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        channelAdapter = ChannelAdapter(displayChannels) { selectedChannel ->
            playChannel(selectedChannel)
        }
        binding.channelRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.channelRecyclerView.adapter = channelAdapter

        binding.btnAddPlaylist.setOnClickListener {
            loadPlaylist("https://iptv-org.github.io/iptv/languages/hin.m3u")
        }

        binding.searchBar.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterChannels(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun loadPlaylist(url: String) {
        Toast.makeText(requireContext(), "Loading Playlist...", Toast.LENGTH_SHORT).show()
        
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val request = Request.Builder().url(url).build()
                val response = httpClient.newCall(request).execute()
                val inputStream = response.body?.byteStream() ?: throw Exception("Empty stream")

                val parsedChannels = IptvParser().parse(inputStream)

                withContext(Dispatchers.Main) {
                    if (parsedChannels.isNotEmpty()) {
                        allChannels.clear()
                        allChannels.addAll(parsedChannels)
                        filterChannels("")
                        binding.listFlipper.displayedChild = 1
                    } else {
                        Toast.makeText(requireContext(), "No channels found!", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Failed to load: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun filterChannels(query: String) {
        displayChannels.clear()
        if (query.isEmpty()) {
            displayChannels.addAll(allChannels)
        } else {
            val lower = query.lowercase()
            displayChannels.addAll(allChannels.filter { it.name.lowercase().contains(lower) })
        }
        channelAdapter.notifyDataSetChanged()
    }

    private fun playChannel(channel: Channel) {
        val action = ChannelsFragmentDirections.actionChannelsFragmentToPlayerFragment(
            streamUrl = channel.url,
            channelName = channel.name,
            drmData = channel.drmLicenseUrl ?: ""
        )
        findNavController().navigate(action)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    inner class ChannelAdapter(
        private val items: List<Channel>,
        private val onItemClick: (Channel) -> Unit
    ) : RecyclerView.Adapter<ChannelAdapter.ChannelViewHolder>() {

        inner class ChannelViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val tvName: TextView = itemView.findViewById(R.id.tvChannelName)
            val imgLogo: ImageView = itemView.findViewById(R.id.imgLogo)
            
            init {
                itemView.setOnClickListener { onItemClick(items[adapterPosition]) }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChannelViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.list_item_channel, parent, false)
            return ChannelViewHolder(view)
        }

        override fun onBindViewHolder(holder: ChannelViewHolder, position: Int) {
            val channel = items[position]
            holder.tvName.text = channel.name
            
            if (!channel.logoUrl.isNullOrEmpty()) {
                Glide.with(holder.itemView.context)
                    .load(channel.logoUrl)
                    .apply(RequestOptions().placeholder(android.R.drawable.ic_menu_gallery))
                    .into(holder.imgLogo)
            } else {
                holder.imgLogo.setImageResource(android.R.drawable.ic_menu_gallery)
            }
        }

        override fun getItemCount(): Int = items.size
    }
}
