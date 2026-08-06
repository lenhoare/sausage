package dev.sausage.runtime

import android.content.Context
import android.webkit.JavascriptInterface
import org.json.JSONTokener
import java.security.MessageDigest

internal class SausageStorageBridge(
    context: Context,
    storageScope: String,
) {
    private val preferences = context.getSharedPreferences(
        "sausage.storage.${storageScope.sha256()}",
        Context.MODE_PRIVATE,
    )

    @JavascriptInterface
    fun get(key: String): String? {
        if (!key.isStorageKey()) return null
        return preferences.getString(key, null)
    }

    @JavascriptInterface
    fun set(
        key: String,
        encodedValue: String,
    ): Boolean {
        if (!key.isStorageKey() || !encodedValue.isJsonValue()) return false

        val existingBytes = preferences.all.entries.sumOf { (storedKey, value) ->
            if (storedKey == key) 0 else (value as? String)?.toByteArray(Charsets.UTF_8)?.size ?: 0
        }
        if (existingBytes + encodedValue.toByteArray(Charsets.UTF_8).size > MAX_STORAGE_BYTES) {
            return false
        }

        return preferences.edit().putString(key, encodedValue).commit()
    }

    @JavascriptInterface
    fun remove(key: String): Boolean {
        if (!key.isStorageKey()) return false
        return preferences.edit().remove(key).commit()
    }

    private fun String.isStorageKey(): Boolean = STORAGE_KEY.matches(this)

    private fun String.isJsonValue(): Boolean {
        if (toByteArray(Charsets.UTF_8).size > MAX_VALUE_BYTES) return false

        return try {
            val parser = JSONTokener(this)
            parser.nextValue()
            parser.nextClean() == END_OF_JSON
        } catch (_: Exception) {
            false
        }
    }

    private fun String.sha256(): String = MessageDigest
        .getInstance("SHA-256")
        .digest(toByteArray(Charsets.UTF_8))
        .joinToString(separator = "") { byte -> "%02x".format(byte) }

    companion object {
        const val JAVASCRIPT_NAME = "__sausageStorage"

        private val STORAGE_KEY = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")
        private const val MAX_VALUE_BYTES = 64 * 1024
        private const val MAX_STORAGE_BYTES = 256 * 1024
        private const val END_OF_JSON = '\u0000'
    }
}
