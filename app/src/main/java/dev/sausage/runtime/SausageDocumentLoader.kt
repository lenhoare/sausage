package dev.sausage.runtime

import android.content.res.AssetManager
import android.net.Uri
import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream
import java.io.FileNotFoundException

internal class SausageDocumentLoader(
    private val assets: AssetManager,
) {
    fun bundledDocumentExists(): Boolean = try {
        assets.open(DOCUMENT_ASSET).use { }
        true
    } catch (_: FileNotFoundException) {
        false
    }

    fun responseFor(uri: Uri): WebResourceResponse {
        if (uri.toString() != DOCUMENT_URL) {
            return textResponse(
                statusCode = 403,
                reason = "Blocked",
                message = "This first Sausage slice only loads its bundled document.",
            )
        }

        return try {
            WebResourceResponse(
                SVG_MIME_TYPE,
                UTF_8,
                200,
                "OK",
                mapOf(
                    "Cache-Control" to "no-store",
                    "Content-Security-Policy" to "default-src 'none'; style-src 'unsafe-inline'; script-src 'unsafe-inline'",
                ),
                assets.open(DOCUMENT_ASSET),
            )
        } catch (_: FileNotFoundException) {
            textResponse(
                statusCode = 404,
                reason = "Not Found",
                message = "The bundled Sausage document is missing.",
            )
        }
    }

    private fun textResponse(
        statusCode: Int,
        reason: String,
        message: String,
    ) = WebResourceResponse(
        "text/plain",
        UTF_8,
        statusCode,
        reason,
        mapOf("Cache-Control" to "no-store"),
        ByteArrayInputStream(message.toByteArray(Charsets.UTF_8)),
    )

    companion object {
        const val DOCUMENT_URL = "https://app.sausage.local/first-card.svge"
        private const val DOCUMENT_ASSET = "first-card.svge"
        private const val SVG_MIME_TYPE = "image/svg+xml"
        private const val UTF_8 = "utf-8"
    }
}

