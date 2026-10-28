package com.ripple.iptv.core

import android.util.Log
import com.ripple.iptv.models.Channel
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.io.PushbackInputStream
import java.net.URLDecoder
import java.util.regex.Pattern

class IptvParser {

    companion object {
        private const val TAG = "IptvParser"
        private val ATTR_PATTERN = Pattern.compile("""([a-zA-Z0-9_.\-]+)="([^"]*)"""")
        private val KEYID_PATTERN = Pattern.compile("keyid=([a-fA-F0-9\\-]+)", Pattern.CASE_INSENSITIVE)
        private val KEY_PATTERN = Pattern.compile("(?:^|[&\\s])key=([a-fA-F0-9]+)", Pattern.CASE_INSENSITIVE)
        private val KID_KEY_COLON = Pattern.compile("^([a-fA-F0-9\\-]{32,36})[=:]([a-fA-F0-9]{32})$")
    }

    private var currentTitle: String? = null
    private var currentTvgId: String? = null
    private var currentLogo: String? = null
    private var currentGroup: String? = null
    private var currentLicenseKey: String? = null
    private var currentKeyId: String? = null
    private var currentKey: String? = null
    private var currentUa: String? = null
    private var currentCookie: String? = null
    private var currentReferer: String? = null
    private var currentDrmScheme: String? = null
    private val currentHeaders = HashMap<String, String>()

    fun parse(input: InputStream): ArrayList<Channel> {
        val pbis = PushbackInputStream(input, 1)
        var c = pbis.read()
        
        while (c != -1 && c.toChar().isWhitespace()) {
            c = pbis.read()
        }
        
        if (c != -1) pbis.unread(c)

        return when (c.toChar()) {
            '[', '{' -> parseJson(pbis)
            else -> parseM3U(pbis)
        }
    }

    private fun parseM3U(input: InputStream): ArrayList<Channel> {
        val channels = ArrayList<Channel>(1000)
        resetState()

        try {
            input.use { stream ->
                stream.bufferedReader(Charsets.UTF_8).forEachLine { line ->
                    val trim = line.trim()
                    if (trim.isNotEmpty()) processLine(trim, channels)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "M3U parse failed: ${e.message}")
        }
        return channels
    }

    private fun processLine(line: String, channels: ArrayList<Channel>) {
        when {
            line.startsWith("#EXTINF:") -> handleExtInf(line)
            line.startsWith("#EXTGRP:") -> handleExtGrp(line)
            line.startsWith("#EXTHTTP:") -> handleExtHttp(line)
            line.startsWith("#EXTM3U") || line.startsWith("#") -> { }
            else -> handleUrl(line, channels)
        }
    }

    private fun handleExtInf(line: String) {
        val lastComma = line.lastIndexOf(',')
        val fallbackTitle = if (lastComma != -1 && lastComma < line.length - 1) {
            line.substring(lastComma + 1).trim().ifEmpty { null }
        } else null

        val matcher = ATTR_PATTERN.matcher(line)
        while (matcher.find()) {
            val attrKey = matcher.group(1)?.lowercase()?.trim() ?: continue
            val attrVal = matcher.group(2)?.trim() ?: continue
            if (attrVal.isEmpty()) continue

            when (attrKey) {
                "tvg-id" -> currentTvgId = attrVal
                "tvg-logo", "logo" -> currentLogo = attrVal
                "group-title" -> currentGroup = attrVal
                "tvg-name" -> currentTitle = attrVal 
                "keyid", "kid", "drm-keyid" -> currentKeyId = attrVal.replace("-", "")
                "key", "license_key", "licence_key", "drm-key" -> currentKey = attrVal
                "user-agent" -> if (currentUa == null) currentUa = attrVal
            }
        }
        if (currentTitle.isNullOrEmpty()) currentTitle = fallbackTitle
    }

    private fun handleExtGrp(line: String) {
        val grp = line.substringAfter(":").trim()
        if (grp.isNotEmpty() && currentGroup.isNullOrEmpty()) currentGroup = grp
    }

    private fun handleExtHttp(line: String) {
        val jsonStr = line.removePrefix("#EXTHTTP:").trim()
        if (jsonStr.isEmpty()) return
        try {
            val json = JSONObject(jsonStr)
            val keys = json.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val v = json.optString(k, "").trim()
                if (v.isEmpty()) continue
                when (k.lowercase()) {
                    "user-agent" -> if (currentUa == null) currentUa = v
                    "referer", "referrer" -> if (currentReferer == null) currentReferer = v
                    "cookie" -> if (currentCookie == null) currentCookie = v
                    "origin" -> currentHeaders.putIfAbsent("Origin", v)
                    else -> currentHeaders[k] = v 
                }
            }
        } catch (e: Exception) { }
    }

    private fun handleUrl(line: String, channels: ArrayList<Channel>) {
        val pipeIdx = line.indexOf('|')
        val rawUrl = if (pipeIdx != -1) line.substring(0, pipeIdx).trim() else line.trim()

        if (rawUrl.isEmpty() || !isValidScheme(rawUrl)) {
            resetState()
            return
        }

        val finalTitle = currentTitle?.takeIf { it.isNotBlank() } ?: rawUrl.substringAfterLast("/").substringBefore("?").substringBefore(".").ifEmpty { "Unknown" }
        val finalGroup = currentGroup?.takeIf { it.isNotBlank() } ?: "Others"

        channels.add(
            Channel(
                name = finalTitle,
                url = rawUrl,
                tvgId = currentTvgId,
                logoUrl = currentLogo,
                group = finalGroup,
                userAgent = currentUa,
                cookie = currentCookie,
                referer = currentReferer,
                drmLicenseUrl = currentLicenseKey,
                drmKeyId = currentKeyId,
                drmKey = currentKey,
                drmScheme = currentDrmScheme,
                customHeaders = if (currentHeaders.isEmpty()) null else java.util.HashMap(currentHeaders)
            )
        )
        resetState()
    }

    private fun parseJson(input: InputStream): ArrayList<Channel> {
        val channels = ArrayList<Channel>()
        try {
            val text = input.use { it.readBytes() }.toString(Charsets.UTF_8).trim()
            val array: JSONArray = when {
                text.startsWith("[") -> JSONArray(text)
                text.startsWith("{") -> {
                    val obj = JSONObject(text)
                    when {
                        obj.has("channels") -> obj.getJSONArray("channels")
                        obj.has("data") -> obj.getJSONArray("data")
                        obj.has("playlist") -> obj.getJSONArray("playlist")
                        obj.has("items") -> obj.getJSONArray("items")
                        else -> return channels
                    }
                }
                else -> return channels
            }

            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                
                val streamUrl = obj.optString("url", "").trim().ifBlank {
                    obj.optString("stream_url", "").trim()
                }
                if (streamUrl.isBlank() || !isValidScheme(streamUrl)) continue

                val name = obj.optString("name", "Unknown").trim()
                
                channels.add(
                    Channel(
                        name = name,
                        url = streamUrl,
                        tvgId = obj.optString("tvg_id", "").trim().ifBlank { null },
                        logoUrl = obj.optString("logo", "").trim().ifBlank { null },
                        group = obj.optString("group", "Others").trim(),
                        userAgent = obj.optString("user_agent", "").trim().ifBlank { null },
                        drmLicenseUrl = obj.optString("drm_license_url", "").trim().ifBlank { null },
                        drmKeyId = obj.optString("key_id", "").trim().ifBlank { null }?.replace("-", ""),
                        drmKey = obj.optString("key", "").trim().ifBlank { null }
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "JSON parse failed: ${e.message}")
        }
        return channels
    }

    private fun isValidScheme(url: String): Boolean {
        val lower = url.lowercase()
        return lower.startsWith("http://") || lower.startsWith("https://")
    }

    private fun resetState() {
        currentTitle = null; currentTvgId = null; currentLogo = null; currentGroup = null
        currentLicenseKey = null; currentKeyId = null; currentKey = null
        currentUa = null; currentCookie = null; currentReferer = null; currentDrmScheme = null
        currentHeaders.clear()
    }
}
