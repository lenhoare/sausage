package dev.sausage.runtime

import android.webkit.JavascriptInterface
import org.json.JSONObject
import kotlin.math.floor

internal class SausageDeviceBridge(
    private val capabilities: Set<String>,
    private val requestLocation: (requestId: String, precise: Boolean) -> Unit,
    private val onShowNotification: (requestId: String, title: String, body: String) -> Unit,
    private val onScheduleNotification: (
        requestId: String,
        notificationId: String,
        title: String,
        body: String,
        atMillis: Long,
    ) -> Unit,
    private val onCancelNotification: (requestId: String, notificationId: String) -> Unit,
) {
    @JavascriptInterface
    fun currentLocation(
        requestId: String,
        precise: Boolean,
    ): String = response {
        requireCapability(LOCATION_CAPABILITY)
        requestId.requireRequestId()
        requestLocation(requestId, precise)
        true
    }

    @JavascriptInterface
    fun showNotification(
        requestId: String,
        title: String,
        body: String,
    ): String = response {
        requireCapability(NOTIFICATIONS_CAPABILITY)
        requestId.requireRequestId()
        title.requireText("Notification titles", MAX_TITLE_LENGTH, allowEmpty = false)
        body.requireText("Notification bodies", MAX_BODY_LENGTH, allowEmpty = true)
        onShowNotification(requestId, title, body)
        true
    }

    @JavascriptInterface
    fun scheduleNotification(
        requestId: String,
        notificationId: String,
        title: String,
        body: String,
        atMillis: Double,
    ): String = response {
        requireCapability(NOTIFICATIONS_CAPABILITY)
        requestId.requireRequestId()
        notificationId.requireNotificationId()
        title.requireText("Notification titles", MAX_TITLE_LENGTH, allowEmpty = false)
        body.requireText("Notification bodies", MAX_BODY_LENGTH, allowEmpty = true)
        if (!atMillis.isFinite() || floor(atMillis) != atMillis || atMillis > MAX_SAFE_JAVASCRIPT_INTEGER) {
            throw IllegalArgumentException("A notification time must be a safe whole-number timestamp.")
        }
        val triggerAt = atMillis.toLong()
        val now = System.currentTimeMillis()
        if (triggerAt < now - PAST_TIME_TOLERANCE_MILLIS) {
            throw IllegalArgumentException("A scheduled notification time may not be in the past.")
        }
        if (triggerAt > now + MAX_SCHEDULE_AHEAD_MILLIS) {
            throw IllegalArgumentException("Notifications may be scheduled at most one year ahead.")
        }
        onScheduleNotification(requestId, notificationId, title, body, triggerAt)
        true
    }

    @JavascriptInterface
    fun cancelNotification(
        requestId: String,
        notificationId: String,
    ): String = response {
        requireCapability(NOTIFICATIONS_CAPABILITY)
        requestId.requireRequestId()
        notificationId.requireNotificationId()
        onCancelNotification(requestId, notificationId)
        true
    }

    private fun requireCapability(capability: String) {
        if (capability !in capabilities) {
            throw SecurityException("This document does not declare the $capability capability.")
        }
    }

    private fun String.requireRequestId() {
        if (!REQUEST_ID.matches(this)) {
            throw IllegalArgumentException("The host request ID is invalid.")
        }
    }

    private fun String.requireNotificationId() {
        if (!NOTIFICATION_ID.matches(this)) {
            throw IllegalArgumentException(
                "Notification IDs must contain only letters, numbers, dot, underscore or hyphen.",
            )
        }
    }

    private fun String.requireText(
        kind: String,
        maxLength: Int,
        allowEmpty: Boolean,
    ) {
        if ((!allowEmpty && isBlank()) || length > maxLength) {
            val requirement = if (allowEmpty) "at most $maxLength characters" else "1 to $maxLength characters"
            throw IllegalArgumentException("$kind must contain $requirement.")
        }
    }

    private fun response(block: () -> Any): String = try {
        JSONObject()
            .put("ok", true)
            .put("value", block())
            .toString()
    } catch (error: Exception) {
        JSONObject()
            .put("ok", false)
            .put("error", error.message?.take(MAX_ERROR_LENGTH) ?: "The device operation failed.")
            .toString()
    }

    companion object {
        const val JAVASCRIPT_NAME = "__sausageDevice"
        const val LOCATION_CAPABILITY = "location"
        const val NOTIFICATIONS_CAPABILITY = "notifications"

        private val REQUEST_ID = Regex("host-[1-9][0-9]{0,14}")
        private val NOTIFICATION_ID = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")
        private const val MAX_TITLE_LENGTH = 80
        private const val MAX_BODY_LENGTH = 300
        private const val MAX_ERROR_LENGTH = 300
        private const val PAST_TIME_TOLERANCE_MILLIS = 5_000L
        private const val MAX_SCHEDULE_AHEAD_MILLIS = 366L * 24L * 60L * 60L * 1_000L
        private const val MAX_SAFE_JAVASCRIPT_INTEGER = 9_007_199_254_740_991.0
    }
}
