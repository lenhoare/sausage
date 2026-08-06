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
import java.security.MessageDigest

internal data class SausageDocument(
    val displayName: String,
    val storageScope: String,
    val flow: SausageFlow?,
    val content: ByteArray,
)

internal data class SausageFlow(
    val slices: List<SausageSlice>,
)

internal sealed interface SausageSlice

internal data class SausageGraphic(
    val ref: String,
) : SausageSlice

internal data class SausageTextArea(
    val key: String,
    val label: String,
    val hint: String?,
    val placeholder: String?,
) : SausageSlice

internal data class SausageButton(
    val label: String,
    val action: String,
) : SausageSlice

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
        val inspection = inspectSvg(source, displayName)

        return SausageDocument(
            displayName = displayName,
            storageScope = inspection.manifestId ?: "document:${source.sha256()}",
            flow = inspection.flow,
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

    private fun inspectSvg(
        source: String,
        displayName: String,
    ): DocumentInspection {
        if (source.contains("<!DOCTYPE", ignoreCase = true)) {
            throw SausageDocumentException("$displayName contains a document type declaration, which is not supported.")
        }

        return try {
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

            var manifestId: String? = null
            var flowScreenDepth: Int? = null
            var flowScreenCount = 0
            val flowSlices = mutableListOf<SausageSlice>()
            val controlKeys = mutableSetOf<String>()
            val graphicRefs = mutableSetOf<String>()
            val svgIds = mutableSetOf<String>()

            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    if (parser.namespace == SVG_NAMESPACE) {
                        parser.getAttributeValue(null, SVG_ID_ATTRIBUTE)?.let(svgIds::add)
                    }

                    if (parser.namespace == APP_NAMESPACE) {
                        when (parser.name) {
                            MANIFEST_ELEMENT -> {
                                val candidate = parser.getAttributeValue(null, MANIFEST_ID_ATTRIBUTE)
                                if (candidate.isNullOrBlank() || !APPLICATION_ID.matches(candidate)) {
                                    throw SausageDocumentException("$displayName has an invalid Sausage application ID.")
                                }
                                if (manifestId != null && manifestId != candidate) {
                                    throw SausageDocumentException("$displayName declares more than one Sausage application ID.")
                                }
                                manifestId = candidate
                            }

                            SCREEN_ELEMENT -> {
                                flowScreenCount += 1
                                if (flowScreenCount > 1) {
                                    throw SausageDocumentException("$displayName uses more than one flow screen, which this slice does not support yet.")
                                }
                                flowScreenDepth = parser.depth
                            }

                            GRAPHIC_ELEMENT -> if (parser.isDirectChildOf(flowScreenDepth)) {
                                val graphicRef = parser.requiredAttribute(
                                    FLOW_GRAPHIC_REF_ATTRIBUTE,
                                    displayName,
                                )
                                if (!graphicRefs.add(graphicRef)) {
                                    throw SausageDocumentException("$displayName uses the SVG graphic $graphicRef more than once in one flow.")
                                }
                                flowSlices += SausageGraphic(graphicRef)
                            }

                            TEXT_AREA_ELEMENT -> if (parser.isDirectChildOf(flowScreenDepth)) {
                                val key = parser.requiredAttribute(CONTROL_KEY_ATTRIBUTE, displayName)
                                if (!CONTROL_KEY.matches(key)) {
                                    throw SausageDocumentException("$displayName has an invalid text-area storage key.")
                                }
                                if (!controlKeys.add(key)) {
                                    throw SausageDocumentException("$displayName uses the control key $key more than once.")
                                }
                                flowSlices += SausageTextArea(
                                    key = key,
                                    label = parser.requiredAttribute(CONTROL_LABEL_ATTRIBUTE, displayName),
                                    hint = parser.getAttributeValue(null, CONTROL_HINT_ATTRIBUTE),
                                    placeholder = parser.getAttributeValue(null, CONTROL_PLACEHOLDER_ATTRIBUTE),
                                )
                            }

                            BUTTON_ELEMENT -> if (parser.isDirectChildOf(flowScreenDepth)) {
                                val action = parser.requiredAttribute(
                                    BUTTON_ACTION_ATTRIBUTE,
                                    displayName,
                                )
                                if (!ACTION_NAME.matches(action)) {
                                    throw SausageDocumentException("$displayName has an invalid button action name.")
                                }
                                flowSlices += SausageButton(
                                    label = parser.requiredAttribute(CONTROL_LABEL_ATTRIBUTE, displayName),
                                    action = action,
                                )
                            }
                        }
                    }
                } else if (
                    event == XmlPullParser.END_TAG &&
                    parser.namespace == APP_NAMESPACE &&
                    parser.name == SCREEN_ELEMENT
                ) {
                    flowScreenDepth = null
                }
                event = parser.next()
            }

            val flow = if (flowScreenCount == 1) {
                if (flowSlices.isEmpty()) {
                    throw SausageDocumentException("$displayName has an empty flow screen.")
                }
                if (flowSlices.none { it is SausageGraphic }) {
                    throw SausageDocumentException("$displayName has a flow screen without a graphical slice.")
                }
                flowSlices.filterIsInstance<SausageGraphic>().forEach { graphic ->
                    if (graphic.ref !in svgIds) {
                        throw SausageDocumentException("$displayName refers to a missing SVG graphic: ${graphic.ref}.")
                    }
                }
                SausageFlow(flowSlices.toList())
            } else {
                null
            }

            DocumentInspection(manifestId, flow)
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

    private fun XmlPullParser.requiredAttribute(
        name: String,
        displayName: String,
    ): String = getAttributeValue(null, name)?.takeIf(String::isNotBlank)
        ?: throw SausageDocumentException("$displayName is missing the required $name attribute.")

    private fun XmlPullParser.isDirectChildOf(parentDepth: Int?): Boolean =
        parentDepth != null && depth == parentDepth + 1

    private fun String.sha256(): String = MessageDigest
        .getInstance("SHA-256")
        .digest(toByteArray(StandardCharsets.UTF_8))
        .joinToString(separator = "") { byte -> "%02x".format(byte) }

    private const val MAX_DOCUMENT_BYTES = 5 * 1024 * 1024
    private const val SVG_ROOT = "svg"
    private const val SVG_NAMESPACE = "http://www.w3.org/2000/svg"
    private const val APP_NAMESPACE = "https://sausage.dev/ns/app/1"
    private const val MANIFEST_ELEMENT = "manifest"
    private const val MANIFEST_ID_ATTRIBUTE = "id"
    private const val SCREEN_ELEMENT = "screen"
    private const val GRAPHIC_ELEMENT = "graphic"
    private const val TEXT_AREA_ELEMENT = "text-area"
    private const val BUTTON_ELEMENT = "button"
    private const val FLOW_GRAPHIC_REF_ATTRIBUTE = "ref"
    private const val CONTROL_KEY_ATTRIBUTE = "key"
    private const val CONTROL_LABEL_ATTRIBUTE = "label"
    private const val CONTROL_HINT_ATTRIBUTE = "hint"
    private const val CONTROL_PLACEHOLDER_ATTRIBUTE = "placeholder"
    private const val BUTTON_ACTION_ATTRIBUTE = "action"
    private const val SVG_ID_ATTRIBUTE = "id"
    private val APPLICATION_ID = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")
    private val CONTROL_KEY = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")
    private val ACTION_NAME = Regex("[A-Za-z_${'$'}][A-Za-z0-9_${'$'}]{0,63}")

    private data class DocumentInspection(
        val manifestId: String?,
        val flow: SausageFlow?,
    )
}
