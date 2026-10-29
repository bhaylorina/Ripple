package com.ripple.iptv.core

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.widget.Toast
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter

object BackupHelper {
    fun backupData(context: Context, uri: Uri) {
        try {
            val masterJson = JSONObject()
            val prefs = context.getSharedPreferences("iptv_settings", Context.MODE_PRIVATE)
            if (prefs.all.isNotEmpty()) {
                masterJson.put("iptv_settings", prefsToJson(prefs))
            }
            val outputStream = context.contentResolver.openOutputStream(uri)
            val writer = OutputStreamWriter(outputStream)
            writer.write(masterJson.toString())
            writer.close()
            outputStream?.close()
            Toast.makeText(context, "Backup Successful!", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Backup Failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun restoreData(context: Context, uri: Uri): Boolean {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val reader = BufferedReader(InputStreamReader(inputStream))
            val stringBuilder = StringBuilder()
            var line: String? = reader.readLine()
            while (line != null) {
                stringBuilder.append(line)
                line = reader.readLine()
            }
            reader.close()
            inputStream?.close()

            val masterJson = JSONObject(stringBuilder.toString())
            if (masterJson.has("iptv_settings")) {
                val prefs = context.getSharedPreferences("iptv_settings", Context.MODE_PRIVATE)
                jsonToPrefs(prefs, masterJson.getJSONObject("iptv_settings"))
            }
            Toast.makeText(context, "Restore Complete!", Toast.LENGTH_SHORT).show()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Invalid Backup File!", Toast.LENGTH_SHORT).show()
            false
        }
    }

    private fun prefsToJson(prefs: SharedPreferences): JSONObject {
        val jsonObject = JSONObject()
        for ((key, value) in prefs.all) {
            when (value) {
                is Boolean -> jsonObject.put(key, value)
                is Float -> jsonObject.put(key, value.toDouble())
                is Int -> jsonObject.put(key, value)
                is Long -> jsonObject.put(key, value)
                is String -> jsonObject.put(key, value)
            }
        }
        return jsonObject
    }

    private fun jsonToPrefs(prefs: SharedPreferences, jsonObject: JSONObject) {
        val editor = prefs.edit()
        val keys = jsonObject.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = jsonObject.get(key)
            when (value) {
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Double -> editor.putFloat(key, value.toFloat())
                is String -> editor.putString(key, value)
            }
        }
        editor.apply()
    }
}
