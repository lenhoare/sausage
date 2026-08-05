package dev.sausage.runtime

import android.net.Uri
import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream

internal class SausageDocumentLoader(
    private val document: SausageDocument,
) {

    fun responseFor(uri: Uri): WebResourceResponse {
        if (uri.toString() != DOCUMENT_URL) {
            return textResponse(
                statusCode = 403,
                reason = "Blocked",
                message = "This Sausage slice only loads self-contained documents.",
            )
        }

        return WebResourceResponse(
            SVG_MIME_TYPE,
            UTF_8,
            200,
            "OK",
            mapOf(
                "Cache-Control" to "no-store",
                "Content-Security-Policy" to "default-src 'none'; style-src 'unsafe-inline'; script-src 'unsafe-inline'",
            ),
            ByteArrayInputStream(document.content),
        )
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
        const val DOCUMENT_URL = "https://app.sausage.local/document.svge"
        private const val SVG_MIME_TYPE = "image/svg+xml"
        private const val UTF_8 = "utf-8"
    }
}
