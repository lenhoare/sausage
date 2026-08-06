package dev.sausage.runtime

import android.content.res.AssetManager
import android.webkit.JavascriptInterface
import org.json.JSONObject

internal class SausageNavigationBridge(
    private val assets: AssetManager,
    private val currentDocument: SausageDocument,
    private val openDocument: (SausageDocument) -> Unit,
    private val goBack: () -> Unit,
) {
    @JavascriptInterface
    fun open(relativePath: String): String = response {
        val applicationId = currentDocument.applicationId
            ?: throw SausageDocumentException(
                "Document navigation requires a Sausage application manifest ID.",
            )
        val destination = SausageDocumentReader.fromBundledRelative(
            assets,
            currentDocument,
            relativePath,
        )
        if (destination.applicationId != applicationId) {
            throw SausageDocumentException(
                "A linked Sausage document must declare the same application ID.",
            )
        }

        openDocument(destination)
        destination.displayName
    }

    @JavascriptInterface
    fun back(): String = response {
        goBack()
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
            .put("error", error.message?.take(MAX_ERROR_LENGTH) ?: "Navigation failed.")
            .toString()
    }

    companion object {
        const val JAVASCRIPT_NAME = "__sausageNavigation"

        private const val MAX_ERROR_LENGTH = 300
    }
}
