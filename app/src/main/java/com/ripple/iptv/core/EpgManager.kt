package com.ripple.iptv.core

import android.content.Context
import android.util.Log
import com.ripple.iptv.models.EpgProgram
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.*
import java.util.zip.GZIPInputStream
import kotlin.concurrent.thread

object EpgManager {
    private const val TAG = "EpgManager"

    val epgData = HashMap<String, ArrayList<EpgProgram>>()
    private val searchIndex = HashMap<String, String>()
    private val resolveCache = HashMap<String, String?>()
    private val lock = Any()

    @Volatile var isEpgLoaded = false
    @Volatile private var isLoading = false
    var lastEpgLoadTime = 0L

    private const val CACHE_EXPIRY_MS = 24 * 60 * 60 * 1000L
    private const val KEEP_PAST_MS = 2 * 60 * 60 * 1000L
    private const val KEEP_FUTURE_MS = 48 * 60 * 60 * 1000L
    private const val PROGRESS_NOTIFY_MS = 5000L

    private const val CACHE_FILE = "epg_cache_clean_v1.bin"
    private const val CACHE_MAGIC = 0x45504731 
    private const val MAX_COUNT = 5_000_000

    private const val DEFAULT_EPG_URL = "https://dl.dropboxusercontent.com/scl/fi/s986xqczdsn77t1umeqdu/epg.xml.gz?rlkey=z7tlltik2tu75jjb35lh1vci5&st=fm6xgo12"

    var onEpgLoaded: (() -> Unit)? = null
    
    // Clean standard network client for EPG
    private val okHttpClient = OkHttpClient.Builder().build()

    fun fetchEpgInBackground(context: Context, epgUrl: String = DEFAULT_EPG_URL) {
        val now = System.currentTimeMillis()
        if (isEpgLoaded && (now - lastEpgLoadTime < CACHE_EXPIRY_MS)) return
        if (isLoading) return 
        isLoading = true

        val cacheFile = File(context.applicationContext.cacheDir, CACHE_FILE)

        thread(name = "EpgLoader") {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)
            val t0 = System.currentTimeMillis()
            try {
                if (!isEpgLoaded && loadFromDisk(cacheFile)) {
                    if (System.currentTimeMillis() - lastEpgLoadTime < CACHE_EXPIRY_MS) return@thread
                }

                val request = Request.Builder().url(epgUrl).build()

                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use
                    val body = response.body ?: return@use

                    val contentType = response.header("Content-Type", "") ?: ""
                    val isGzip = epgUrl.contains(".gz") || contentType.contains("gzip")

                    val stream: InputStream = if (isGzip) {
                        GZIPInputStream(body.byteStream(), 64 * 1024)
                    } else {
                        BufferedInputStream(body.byteStream(), 64 * 1024)
                    }

                    val firstLoad = !isEpgLoaded
                    val targetEpg = if (firstLoad) epgData else HashMap<String, ArrayList<EpgProgram>>()
                    val targetIndex = if (firstLoad) searchIndex else HashMap<String, String>()

                    if (firstLoad) {
                        synchronized(lock) {
                            epgData.clear()
                            searchIndex.clear()
                            resolveCache.clear()
                        }
                    }

                    parseXmltv(stream, targetEpg, targetIndex, firstLoad)

                    synchronized(lock) {
                        if (!firstLoad) {
                            epgData.clear()
                            epgData.putAll(targetEpg)
                            searchIndex.clear()
                            searchIndex.putAll(targetIndex)
                        }
                        resolveCache.clear()
                    }

                    isEpgLoaded = true
                    lastEpgLoadTime = System.currentTimeMillis()
                    onEpgLoaded?.invoke()

                    saveToDisk(cacheFile)
                }
            } catch (e: Exception) {
                Log.e(TAG, "EPG Error: ${e.message}")
            } finally {
                isLoading = false
            }
        }
    }

    private fun loadFromDisk(file: File): Boolean {
        if (!file.exists()) return false
        return try {
            DataInputStream(BufferedInputStream(FileInputStream(file), 64 * 1024)).use { input ->
                if (input.readInt() != CACHE_MAGIC) throw IOException("bad magic")
                val savedAt = input.readLong()
                val minEnd = System.currentTimeMillis() - KEEP_PAST_MS

                val idxCount = input.readInt()
                val idx = HashMap<String, String>(idxCount * 2)
                repeat(idxCount) { idx[input.readUTF()] = input.readUTF() }

                val chCount = input.readInt()
                val epg = HashMap<String, ArrayList<EpgProgram>>(chCount * 2)
                repeat(chCount) {
                    val id = input.readUTF()
                    val n = input.readInt()
                    val list = ArrayList<EpgProgram>(n)
                    repeat(n) {
                        val title = input.readUTF()
                        val s = input.readLong()
                        val e = input.readLong()
                        if (e >= minEnd) list.add(EpgProgram(title, s, e))
                    }
                    if (list.isNotEmpty()) epg[id] = list
                }

                synchronized(lock) {
                    epgData.clear()
                    epgData.putAll(epg)
                    searchIndex.clear()
                    searchIndex.putAll(idx)
                    resolveCache.clear()
                }
                lastEpgLoadTime = savedAt
                isEpgLoaded = true
            }
            onEpgLoaded?.invoke()
            true
        } catch (e: Exception) {
            file.delete()
            false
        }
    }

    private fun saveToDisk(file: File) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        try {
            DataOutputStream(BufferedOutputStream(FileOutputStream(tmp), 64 * 1024)).use { out ->
                out.writeInt(CACHE_MAGIC)
                out.writeLong(lastEpgLoadTime)

                out.writeInt(searchIndex.size)
                for ((key, id) in searchIndex) {
                    out.writeUTF(key)
                    out.writeUTF(id)
                }

                out.writeInt(epgData.size)
                for ((id, list) in epgData) {
                    out.writeUTF(id)
                    out.writeInt(list.size)
                    for (p in list) {
                        out.writeUTF(p.title.take(300))
                        out.writeLong(p.startMs)
                        out.writeLong(p.endMs)
                    }
                }
            }
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
        } catch (e: Exception) {
            tmp.delete()
        }
    }

    private fun cleanName(name: String): String {
        val sb = StringBuilder(name.length)
        for (c in name) {
            if ((c in 'a'..'z') || (c in '0'..'9')) sb.append(c)
            else if (c in 'A'..'Z') sb.append(c + 32)
        }
        return sb.toString()
    }

    private fun parseTime(s: String?): Long {
        if (s == null || s.length < 14) return 0L
        return try {
            val year = num(s, 0, 4)
            val month = num(s, 4, 2)
            val day = num(s, 6, 2)
            val hour = num(s, 8, 2)
            val minute = num(s, 10, 2)
            val second = num(s, 12, 2)

            var offsetMin = 330 
            var i = 14
            while (i < s.length && s[i] == ' ') i++
            if (i + 5 <= s.length && (s[i] == '+' || s[i] == '-')) {
                val sign = if (s[i] == '-') -1 else 1
                offsetMin = sign * (num(s, i + 1, 2) * 60 + num(s, i + 3, 2))
            }

            val days = daysFromCivil(year, month, day)
            ((((days * 24 + hour) * 60 + minute) * 60 + second) - offsetMin * 60L) * 1000L
        } catch (e: Exception) {
            0L
        }
    }

    private fun num(s: String, start: Int, len: Int): Int {
        var r = 0
        for (i in start until start + len) {
            val d = s[i] - '0'
            r = r * 10 + d
        }
        return r
    }

    private fun daysFromCivil(y0: Int, m: Int, d: Int): Long {
        val y = if (m <= 2) y0 - 1 else y0
        val era = (if (y >= 0) y else y - 399) / 400
        val yoe = y - era * 400
        val doy = (153 * (m + (if (m > 2) -3 else 9)) + 2) / 5 + d - 1
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return era * 146097L + doe - 719468L
    }

    private fun parseXmltv(
        inputStream: InputStream,
        targetEpg: HashMap<String, ArrayList<EpgProgram>>,
        targetIndex: HashMap<String, String>,
        notifyProgress: Boolean
    ): Int {
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = false
        val parser = factory.newPullParser()
        parser.setInput(inputStream, null)

        val minEnd = System.currentTimeMillis() - KEEP_PAST_MS
        val maxStart = System.currentTimeMillis() + KEEP_FUTURE_MS
        var lastNotify = System.currentTimeMillis()
        var kept = 0

        var eventType = parser.eventType
        var currentChannelId = ""
        var currentTitle = ""
        var startMs = 0L
        var endMs = 0L
        var insideProgramme = false
        var insideChannel = false
        var skipProgramme = false

        while (eventType != XmlPullParser.END_DOCUMENT) {
            val tagName = parser.name ?: ""
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    if (tagName.equals("channel", ignoreCase = true)) {
                        insideChannel = true
                        currentChannelId = parser.getAttributeValue(null, "id") ?: ""

                        if (currentChannelId.isNotEmpty()) {
                            val key = cleanName(currentChannelId)
                            if (key.isNotEmpty()) {
                                synchronized(lock) { targetIndex[key] = currentChannelId }
                            }
                        }
                    } else if (insideChannel && tagName.equals("display-name", ignoreCase = true)) {
                        val dName = parser.nextText()
                        if (currentChannelId.isNotEmpty() && dName.isNotEmpty()) {
                            val key = cleanName(dName)
                            if (key.isNotEmpty()) {
                                synchronized(lock) { targetIndex[key] = currentChannelId }
                            }
                        }
                    } else if (tagName.equals("programme", ignoreCase = true)) {
                        insideProgramme = true
                        currentChannelId = parser.getAttributeValue(null, "channel") ?: ""
                        startMs = parseTime(parser.getAttributeValue(null, "start"))
                        endMs = parseTime(parser.getAttributeValue(null, "stop"))
                        currentTitle = ""
                        skipProgramme = (endMs != 0L && endMs < minEnd) || startMs > maxStart
                    } else if (insideProgramme && !skipProgramme && tagName.equals("title", ignoreCase = true)) {
                        currentTitle = parser.nextText()
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (tagName.equals("channel", ignoreCase = true)) {
                        insideChannel = false
                    } else if (tagName.equals("programme", ignoreCase = true)) {
                        if (!skipProgramme && currentChannelId.isNotEmpty() && currentTitle.isNotEmpty() && startMs > 0L) {
                            synchronized(lock) {
                                targetEpg.getOrPut(currentChannelId) { ArrayList() }
                                    .add(EpgProgram(currentTitle, startMs, endMs))
                            }
                            kept++

                            if (notifyProgress) {
                                val t = System.currentTimeMillis()
                                if (t - lastNotify >= PROGRESS_NOTIFY_MS) {
                                    lastNotify = t
                                    onEpgLoaded?.invoke()
                                }
                            }
                        }
                        insideProgramme = false
                    }
                }
            }
            eventType = parser.next()
        }
        return kept
    }

    private fun resolveChannelId(tvgId: String?, channelName: String): String? {
        if (!tvgId.isNullOrEmpty() && epgData.containsKey(tvgId)) return tvgId
        val clean = cleanName(channelName)
        if (clean.isEmpty()) return null
        if (resolveCache.containsKey(clean)) return resolveCache[clean]

        var id = searchIndex[clean]
        if (id == null) {
            var possibleId: String? = null
            for ((key, value) in searchIndex) {
                if (key.contains(clean) || clean.contains(key)) {
                    possibleId = value
                    if (!clean.endsWith("hd") && key.endsWith("hd")) continue 
                    break 
                }
            }
            id = possibleId
        }
        if (id != null || isEpgLoaded) resolveCache[clean] = id
        return id
    }

    fun getCurrentProgram(tvgId: String?, channelName: String): EpgProgram? {
        val now = System.currentTimeMillis()
        return synchronized(lock) {
            val id = resolveChannelId(tvgId, channelName)
            if (id == null) null else epgData[id]?.firstOrNull { now in it.startMs..it.endMs }
        }
    }

    fun getCurrentAndNextProgram(tvgId: String?, channelName: String): Pair<EpgProgram?, EpgProgram?> {
        val now = System.currentTimeMillis()
        return synchronized(lock) {
            val programs = resolveChannelId(tvgId, channelName)?.let { epgData[it] }
            if (programs == null) {
                Pair(null, null)
            } else {
                val idx = programs.indexOfFirst { now in it.startMs..it.endMs }
                if (idx != -1) {
                    Pair(programs[idx], programs.getOrNull(idx + 1))
                } else {
                    Pair(null, programs.firstOrNull { it.startMs > now })
                }
            }
        }
    }
}
