package dev.sausage.runtime

import android.content.ContentResolver
import android.content.res.AssetManager
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.StringReader
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

internal data class SausageDocument(
    val displayName: String,
    val content: ByteArray,
)

internal class SausageDocumentException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

internal object SausageDocumentReader {
    fun fromAsset(
        assets: AssetManager,
        assetName: String,
    ): SausageDocument = try {
        assets.open(assetName).use { input ->
            createDocument(assetName, input)
        }
    } catch (error: SausageDocumentException) {
        throw error
    } catch (error: Exception) {
        throw SausageDocumentException("The bundled Sausage document could not be read.", error)
    }

    fun fromUri(
        contentResolver: ContentResolver,
        uri: Uri,
    ): SausageDocument {
        val displayName = queryDisplayName(contentResolver, uri)
        val input = try {
            contentResolver.openInputStream(uri)
        } catch (error: Exception) {
            throw SausageDocumentException("Android could not open $displayName.", error)
        } ?: throw SausageDocumentException("Android could not open $displayName.")

        return try {
            input.use { createDocument(displayName, it) }
        } catch (error: SausageDocumentException) {
            throw error
        } catch (error: Exception) {
            throw SausageDocumentException("The selected document could not be read.", error)
        }
    }

    private fun createDocument(
        displayName: String,
        input: InputStream,
    ): SausageDocument {
        val bytes = input.readWithLimit(MAX_DOCUMENT_BYTES)
        if (bytes.isEmpty()) {
            throw SausageDocumentException("$displayName is empty.")
        }

        val source = decodeUtf8(bytes, displayName)
        validateSvg(source, displayName)

        return SausageDocument(
            displayName = displayName,
            content = source.toByteArray(StandardCharsets.UTF_8),
        )
    }

    private fun decodeUtf8(
        bytes: ByteArray,
        displayName: String,
    ): String = try {
        StandardCharsets.UTF_8
            .newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
            .removePrefix("\uFEFF")
    } catch (error: Exception) {
        throw SausageDocumentException("$displayName must use UTF-8 text encoding.", error)
    }

    private fun validateSvg(
        source: String,
        displayName: String,
    ) {
        if (source.contains("<!DOCTYPE", ignoreCase = true)) {
            throw SausageDocumentException("$displayName contains a document type declaration, which is not supported.")
        }

        try {
            val parser = Xml.newPullParser().apply {
                setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
                setInput(StringReader(source))
            }

            var event = parser.eventType
            while (event != XmlPullParser.START_TAG && event != XmlPullParser.END_DOCUMENT) {
                event = parser.next()
            }

            if (
                event != XmlPullParser.START_TAG ||
                parser.name != SVG_ROOT ||
                parser.namespace != SVG_NAMESPACE
            ) {
                throw SausageDocumentException("$displayName is not an SVG document.")
            }

            while (event != XmlPullParser.END_DOCUMENT) {
                event = parser.next()
            }
        } catch (error: SausageDocumentException) {
            throw error
        } catch (error: Exception) {
            throw SausageDocumentException("$displayName is not well-formed SVG XML.", error)
        }
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
    } ?: uri.lastPathSegment ?: "selected document"

    private fun InputStream.readWithLimit(limit: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0

        while (true) {
            val count = read(buffer)
            if (count < 0) break

            total += count
            if (total > limit) {
                throw SausageDocumentException("The selected document is larger than 5 MB.")
            }
            output.write(buffer, 0, count)
        }

        return output.toByteArray()
    }

    private const val MAX_DOCUMENT_BYTES = 5 * 1024 * 1024
    private const val SVG_ROOT = "svg"
    private const val SVG_NAMESPACE = "http://www.w3.org/2000/svg"
}

