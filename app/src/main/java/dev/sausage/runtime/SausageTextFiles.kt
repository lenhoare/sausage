package dev.sausage.runtime

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.JavascriptInterface
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

internal data class SausageTextFile(
    val name: String,
    val type: String,
    val size: Int,
    val text: String,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("name", name)
        .put("type", type)
        .put("size", size)
        .put("text", text)
}

internal class SausageTextFileException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

internal class SausageFilesBridge(
    private val capabilities: Set<String>,
    private val openTextFile: (requestId: String, extensions: Set<String>) -> Unit,
) {
    @JavascriptInterface
    fun openText(
        requestId: String,
        encodedExtensions: String,
    ): String = response {
        if (FILES_CAPABILITY !in capabilities) {
            throw SecurityException("This document does not declare the files capability.")
        }
        if (!REQUEST_ID.matches(requestId)) {
            throw IllegalArgumentException("The host request ID is invalid.")
        }
        if (encodedExtensions.length > MAX_EXTENSIONS_JSON_LENGTH) {
            throw IllegalArgumentException("The text file extension request is too large.")
        }
        val values = JSONArray(encodedExtensions)
        if (values.length() !in 1..SUPPORTED_EXTENSIONS.size) {
            throw IllegalArgumentException("Choose between one and three supported text file extensions.")
        }
        val extensions = buildSet {
            repeat(values.length()) { index ->
                val extension = values.optString(index, "").lowercase()
                if (extension !in SUPPORTED_EXTENSIONS) {
                    throw IllegalArgumentException("Supported text file extensions are .json, .md and .txt.")
                }
                if (!add(extension)) {
                    throw IllegalArgumentException("Text file extensions may not be repeated.")
                }
            }
        }
        openTextFile(requestId, extensions)
        true
    }

    private fun response(block: () -> Any): String = try {
        JSONObject()
            .put("ok", true)
            .put("value", block())
            .toString()
    } catch (error: Exception) {
        JSONObject()
            .put("ok", false)
            .put("error", error.message?.take(MAX_ERROR_LENGTH) ?: "The file operation failed.")
            .toString()
    }

    companion object {
        const val JAVASCRIPT_NAME = "__sausageFiles"
        const val FILES_CAPABILITY = "files"
        val SUPPORTED_EXTENSIONS = setOf(".json", ".md", ".txt")

        private val REQUEST_ID = Regex("host-[1-9][0-9]{0,14}")
        private const val MAX_EXTENSIONS_JSON_LENGTH = 64
        private const val MAX_ERROR_LENGTH = 300
    }
}

internal object SausageTextFileReader {
    fun fromUri(
        contentResolver: ContentResolver,
        uri: Uri,
        extensions: Set<String>,
    ): SausageTextFile {
        val name = queryDisplayName(contentResolver, uri)
        if (name.length > MAX_FILE_NAME_LENGTH) {
            throw SausageTextFileException("The selected text file name is too long.")
        }
        val extension = extensions.firstOrNull { name.endsWith(it, ignoreCase = true) }
            ?: throw SausageTextFileException(
                "Choose a ${extensions.sorted().joinToString(" or ")} text file.",
            )
        val input = try {
            contentResolver.openInputStream(uri)
        } catch (error: Exception) {
            throw SausageTextFileException("Android could not open $name.", error)
        } ?: throw SausageTextFileException("Android could not open $name.")

        val bytes = try {
            input.use { stream ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var total = 0
                while (true) {
                    val count = stream.read(buffer)
                    if (count < 0) break
                    total += count
                    if (total > MAX_TEXT_FILE_BYTES) {
                        throw SausageTextFileException("Text data files may be at most 1 MB.")
                    }
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
        } catch (error: SausageTextFileException) {
            throw error
        } catch (error: Exception) {
            throw SausageTextFileException("$name could not be read.", error)
        }

        val text = try {
            StandardCharsets.UTF_8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
                .removePrefix("\uFEFF")
        } catch (error: Exception) {
            throw SausageTextFileException("$name must use UTF-8 text encoding.", error)
        }
        val type = contentResolver.getType(uri)
            ?.takeIf { it.startsWith("text/") || it == "application/json" }
            ?: when (extension) {
                ".json" -> "application/json"
                ".md" -> "text/markdown"
                else -> "text/plain"
            }
        return SausageTextFile(name, type, bytes.size, text)
    }

    private fun queryDisplayName(
        contentResolver: ContentResolver,
        uri: Uri,
    ): String = try {
        contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    } catch (_: Exception) {
        null
    } ?: uri.lastPathSegment?.substringAfterLast('/') ?: "selected file"

    private const val MAX_TEXT_FILE_BYTES = 1024 * 1024
    private const val MAX_FILE_NAME_LENGTH = 255
}
