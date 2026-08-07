package dev.sausage.runtime

import android.annotation.SuppressLint
import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.webkit.RenderProcessGoneDetail
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONObject
import java.util.ArrayDeque

class MainActivity : Activity() {
    private lateinit var root: FrameLayout
    private var webView: WebView? = null
    private var databaseBridge: SausageDatabaseBridge? = null
    private var photoFileCallback: ValueCallback<Array<Uri>>? = null
    private var currentDocument: SausageDocument? = null
    private val documentBackStack = ArrayDeque<SausageDocument>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var pendingLocationRequest: PendingLocationRequest? = null
    private var activeLocationListener: LocationListener? = null
    private var locationFallback: Location? = null
    private val pendingNotificationActions = ArrayDeque<PendingNotificationAction>()
    private var notificationPermissionRequestActive = false
    private var screen = Screen.HOME
    private var keyboardInsetBottom = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureWindow()

        root = FrameLayout(this).apply {
            setBackgroundColor(BACKGROUND)
        }
        setContentView(root)
        configureKeyboardInsets()
        configureBackNavigation()
        showHome()
    }

    private fun configureKeyboardInsets() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return

        root.setOnApplyWindowInsetsListener { _, insets ->
            keyboardInsetBottom = if (insets.isVisible(WindowInsets.Type.ime())) {
                insets.getInsets(WindowInsets.Type.ime()).bottom
            } else {
                0
            }
            applyKeyboardInset()
            insets
        }
    }

    private fun applyKeyboardInset() {
        val view = webView ?: return
        val params = view.layoutParams as? FrameLayout.LayoutParams ?: return
        if (params.bottomMargin == keyboardInsetBottom) return

        params.bottomMargin = keyboardInsetBottom
        view.layoutParams = params
    }

    private fun configureBackNavigation() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                ::handleBack,
            )
        }
    }

    @Suppress("DEPRECATION")
    private fun configureWindow() {
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = BACKGROUND

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
        } else {
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        }
    }

    private fun showHome() {
        clearScreen()
        documentBackStack.clear()
        screen = Screen.HOME

        val scrollView = ScrollView(this).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(32), dp(64), dp(32), dp(40))
        }

        content.addView(
            ImageView(this).apply {
                setImageResource(R.drawable.ic_sausage)
                contentDescription = null
            },
            linearParams(dp(88), dp(88)),
        )
        content.addView(
            TextView(this).apply {
                text = getString(R.string.home_eyebrow)
                setTextColor(ACCENT)
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                letterSpacing = 0.22f
                gravity = Gravity.CENTER
            },
            linearParams(
                width = ViewGroup.LayoutParams.MATCH_PARENT,
                height = ViewGroup.LayoutParams.WRAP_CONTENT,
                topMargin = dp(24),
            ),
        )
        content.addView(
            TextView(this).apply {
                text = getString(R.string.home_title)
                setTextColor(Color.WHITE)
                textSize = 32f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
            },
            linearParams(
                width = ViewGroup.LayoutParams.MATCH_PARENT,
                height = ViewGroup.LayoutParams.WRAP_CONTENT,
                topMargin = dp(10),
            ),
        )
        content.addView(
            TextView(this).apply {
                text = getString(R.string.home_description)
                setTextColor(MUTED_TEXT)
                textSize = 16f
                gravity = Gravity.CENTER
                setLineSpacing(0f, 1.25f)
            },
            linearParams(
                width = ViewGroup.LayoutParams.MATCH_PARENT,
                height = ViewGroup.LayoutParams.WRAP_CONTENT,
                topMargin = dp(16),
            ),
        )

        content.addView(
            actionButton(
                label = getString(R.string.open_document),
                primary = true,
                onClick = ::openDocumentPicker,
            ).apply {
                contentDescription = getString(R.string.open_document_accessibility)
            },
            linearParams(
                width = ViewGroup.LayoutParams.MATCH_PARENT,
                height = dp(58),
                topMargin = dp(38),
            ),
        )
        content.addView(
            actionButton(
                label = getString(R.string.open_sample),
                primary = false,
                onClick = ::openBundledDocument,
            ),
            linearParams(
                width = ViewGroup.LayoutParams.MATCH_PARENT,
                height = dp(58),
                topMargin = dp(14),
            ),
        )
        content.addView(
            actionButton(
                label = getString(R.string.open_input_sample),
                primary = false,
                onClick = ::openInputSample,
            ),
            linearParams(
                width = ViewGroup.LayoutParams.MATCH_PARENT,
                height = dp(58),
                topMargin = dp(14),
            ),
        )
        content.addView(
            actionButton(
                label = getString(R.string.open_photo_sample),
                primary = false,
                onClick = ::openPhotoSample,
            ),
            linearParams(
                width = ViewGroup.LayoutParams.MATCH_PARENT,
                height = dp(58),
                topMargin = dp(14),
            ),
        )
        content.addView(
            actionButton(
                label = getString(R.string.open_device_sample),
                primary = false,
                onClick = ::openDeviceSample,
            ),
            linearParams(
                width = ViewGroup.LayoutParams.MATCH_PARENT,
                height = dp(58),
                topMargin = dp(14),
            ),
        )
        content.addView(
            TextView(this).apply {
                text = getString(R.string.home_footer)
                setTextColor(SUBTLE_TEXT)
                textSize = 12f
                gravity = Gravity.CENTER
            },
            linearParams(
                width = ViewGroup.LayoutParams.MATCH_PARENT,
                height = ViewGroup.LayoutParams.WRAP_CONTENT,
                topMargin = dp(28),
            ),
        )

        scrollView.addView(
            content,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        root.addView(
            scrollView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
    }

    private fun openDocumentPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }

        try {
            startActivityForResult(intent, OPEN_DOCUMENT_REQUEST)
        } catch (error: Exception) {
            showError("Android could not open the document picker.")
            Log.e(TAG, "Unable to open document picker", error)
        }
    }

    @Deprecated("Kept dependency-free for the initial Android runtime slices")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?,
    ) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == OPEN_PHOTO_REQUEST) {
            val callback = photoFileCallback
            photoFileCallback = null
            callback?.onReceiveValue(
                WebChromeClient.FileChooserParams.parseResult(resultCode, data),
            )
            return
        }
        if (requestCode != OPEN_DOCUMENT_REQUEST || resultCode != RESULT_OK) return

        val uri = data?.data ?: run {
            showError("Android did not return a document.")
            return
        }

        try {
            val canPersist = data.flags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION != 0
            val canRead = data.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0
            if (canPersist && canRead) {
                try {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                } catch (error: SecurityException) {
                    Log.w(TAG, "The document provider did not offer persistent access", error)
                }
            }

            val document = SausageDocumentReader.fromUri(contentResolver, uri)
            Log.i(TAG, "Opening external document: ${document.displayName}")
            showInitialDocument(document)
        } catch (error: SausageDocumentException) {
            Log.w(TAG, "Rejected external document", error)
            showError(error.message ?: "The selected document could not be opened.")
        }
    }

    private fun openBundledDocument() {
        openBundledDocument(BUNDLED_DOCUMENT)
    }

    private fun openInputSample() {
        openBundledDocument(INPUT_DOCUMENT)
    }

    private fun openPhotoSample() {
        openBundledDocument(PHOTO_DOCUMENT)
    }

    private fun openDeviceSample() {
        openBundledDocument(DEVICE_DOCUMENT)
    }

    private fun openBundledDocument(assetName: String) {
        try {
            val document = SausageDocumentReader.fromAsset(assets, assetName)
            Log.i(TAG, "Opening bundled document: ${document.displayName}")
            showInitialDocument(document)
        } catch (error: SausageDocumentException) {
            Log.e(TAG, "Unable to open bundled document", error)
            showError(error.message ?: getString(R.string.document_load_error_message))
        }
    }

    @Suppress("SetJavaScriptEnabled")
    private fun showInitialDocument(document: SausageDocument) {
        documentBackStack.clear()
        showDocument(document)
    }

    @Suppress("SetJavaScriptEnabled")
    private fun showDocument(document: SausageDocument) {
        clearScreen()
        screen = Screen.DOCUMENT
        currentDocument = document
        val loader = SausageDocumentLoader(document)
        val allowsPhotoSelection = document.flow
            ?.screens
            ?.flatMap(SausageScreen::slices)
            ?.any { it is SausagePhoto } == true
        val documentDatabase = SausageDatabaseBridge(this, document.storageScope)
        databaseBridge = documentDatabase
        val documentNavigation = SausageNavigationBridge(
            assets = assets,
            currentDocument = document,
            openDocument = { destination ->
                runOnUiThread {
                    if (screen == Screen.DOCUMENT && currentDocument === document) {
                        documentBackStack.addLast(document)
                        showDocument(destination)
                    }
                }
            },
            goBack = {
                runOnUiThread {
                    if (screen == Screen.DOCUMENT && currentDocument === document) {
                        navigateDocumentBack()
                    }
                }
            },
        )
        val documentDevice = SausageDeviceBridge(
            capabilities = document.capabilities,
            requestLocation = { requestId, precise ->
                runOnUiThread { requestCurrentLocation(document, requestId, precise) }
            },
            onShowNotification = { requestId, title, body ->
                runOnUiThread {
                    requestNotification(
                        PendingNotificationAction.Show(document, requestId, title, body),
                    )
                }
            },
            onScheduleNotification = { requestId, notificationId, title, body, atMillis ->
                runOnUiThread {
                    requestNotification(
                        PendingNotificationAction.Schedule(
                            document,
                            requestId,
                            notificationId,
                            title,
                            body,
                            atMillis,
                        ),
                    )
                }
            },
            onCancelNotification = { requestId, notificationId ->
                runOnUiThread {
                    requestNotification(
                        PendingNotificationAction.Cancel(document, requestId, notificationId),
                    )
                }
            },
            onReadClipboard = { requestId ->
                runOnUiThread { readClipboard(document, requestId) }
            },
            onWriteClipboard = { requestId, text ->
                runOnUiThread { writeClipboard(document, requestId, text) }
            },
            onShareText = { requestId, title, text ->
                runOnUiThread { shareText(document, requestId, title, text) }
            },
            onPerformHaptic = { requestId, pattern ->
                runOnUiThread { performHaptic(document, requestId, pattern) }
            },
        )

        val view = WebView(this).apply {
            contentDescription = getString(R.string.document_accessibility, document.displayName)
            setBackgroundColor(Color.TRANSPARENT)
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER

            settings.apply {
                javaScriptEnabled = true
                allowFileAccess = false
                allowContentAccess = false
                domStorageEnabled = false
                javaScriptCanOpenWindowsAutomatically = false
                setSupportMultipleWindows(false)
                builtInZoomControls = false
                displayZoomControls = false
                loadWithOverviewMode = true
                useWideViewPort = true
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                mediaPlaybackRequiresUserGesture = true
            }

            addJavascriptInterface(
                SausageStorageBridge(context, document.storageScope),
                SausageStorageBridge.JAVASCRIPT_NAME,
            )
            addJavascriptInterface(
                documentDatabase,
                SausageDatabaseBridge.JAVASCRIPT_NAME,
            )
            addJavascriptInterface(
                documentNavigation,
                SausageNavigationBridge.JAVASCRIPT_NAME,
            )
            addJavascriptInterface(
                documentDevice,
                SausageDeviceBridge.JAVASCRIPT_NAME,
            )

            webChromeClient = object : WebChromeClient() {
                override fun onShowFileChooser(
                    webView: WebView,
                    filePathCallback: ValueCallback<Array<Uri>>,
                    fileChooserParams: FileChooserParams,
                ): Boolean {
                    if (!allowsPhotoSelection) {
                        filePathCallback.onReceiveValue(null)
                        return false
                    }
                    photoFileCallback?.onReceiveValue(null)
                    photoFileCallback = filePathCallback

                    val intent = fileChooserParams.createIntent().apply {
                        type = "image/*"
                        putExtra(Intent.EXTRA_ALLOW_MULTIPLE, false)
                    }
                    return try {
                        startActivityForResult(intent, OPEN_PHOTO_REQUEST)
                        true
                    } catch (error: Exception) {
                        photoFileCallback = null
                        filePathCallback.onReceiveValue(null)
                        Log.e(TAG, "Unable to open photo picker", error)
                        false
                    }
                }
            }

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest,
                ): Boolean = request.url.toString() != SausageDocumentLoader.DOCUMENT_URL

                override fun shouldInterceptRequest(
                    view: WebView,
                    request: WebResourceRequest,
                ): WebResourceResponse = loader.responseFor(request.url)

                override fun onPageFinished(view: WebView, url: String) {
                    view.evaluateJavascript(APPLY_RUNTIME_SCRIPT) {
                        Log.i(TAG, "Rendered ${document.displayName}")
                    }
                }

                override fun onReceivedError(
                    view: WebView,
                    request: WebResourceRequest,
                    error: WebResourceError,
                ) {
                    if (request.isForMainFrame) {
                        showError(error.description.toString())
                    }
                }

                override fun onRenderProcessGone(
                    view: WebView,
                    detail: RenderProcessGoneDetail,
                ): Boolean {
                    root.removeView(view)
                    view.destroy()
                    webView = null
                    showError("The document renderer stopped unexpectedly.")
                    return true
                }
            }
        }

        webView = view
        root.addView(
            view,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ).apply {
                bottomMargin = keyboardInsetBottom
            },
        )
        root.requestApplyInsets()
        view.loadUrl(SausageDocumentLoader.DOCUMENT_URL)
    }

    private fun requestCurrentLocation(
        document: SausageDocument,
        requestId: String,
        precise: Boolean,
    ) {
        if (screen != Screen.DOCUMENT || currentDocument !== document) return
        if (pendingLocationRequest != null) {
            completeHostRequest(document, requestId, error = "A location request is already in progress.")
            return
        }

        val request = PendingLocationRequest(document, requestId, precise)
        pendingLocationRequest = request
        if (hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)) {
            startCurrentLocation(request)
            return
        }

        val permissions = if (precise) {
            arrayOf(
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION,
            )
        } else {
            arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        requestPermissions(permissions, LOCATION_PERMISSION_REQUEST)
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    private fun startCurrentLocation(request: PendingLocationRequest) {
        if (pendingLocationRequest !== request) return
        val manager = getSystemService(LocationManager::class.java)
        val preciseGranted = request.precise && hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        val preferredProviders = if (preciseGranted) {
            listOf(LocationManager.GPS_PROVIDER, FUSED_LOCATION_PROVIDER, LocationManager.NETWORK_PROVIDER)
        } else {
            listOf(FUSED_LOCATION_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
        }
        val provider = preferredProviders.firstOrNull { candidate ->
            manager.allProviders.contains(candidate) && manager.isProviderEnabled(candidate)
        }
        if (provider == null) {
            finishLocationRequest(error = "Location is turned off or unavailable on this device.")
            return
        }

        locationFallback = manager.getProviders(true)
            .mapNotNull { candidate ->
                try {
                    manager.getLastKnownLocation(candidate)
                } catch (_: Exception) {
                    null
                }
            }
            .maxByOrNull(Location::getTime)

        val requestedProvider = provider
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                finishLocationRequest(location = location, cached = false)
            }

            override fun onProviderDisabled(provider: String) {
                if (provider == requestedProvider) {
                    finishLocationRequest(
                        location = locationFallback,
                        cached = locationFallback != null,
                        error = "The selected location provider was turned off.",
                    )
                }
            }
        }
        activeLocationListener = listener
        try {
            manager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
        } catch (error: Exception) {
            Log.w(TAG, "Could not request a current location", error)
            finishLocationRequest(error = "Android could not start a location request.")
            return
        }

        mainHandler.postDelayed({
            if (pendingLocationRequest === request) {
                finishLocationRequest(
                    location = locationFallback,
                    cached = locationFallback != null,
                    error = "A current location was not available in time.",
                )
            }
        }, LOCATION_TIMEOUT_MILLIS)
    }

    private fun finishLocationRequest(
        location: Location? = null,
        cached: Boolean = false,
        error: String? = null,
    ) {
        val request = pendingLocationRequest ?: return
        pendingLocationRequest = null
        activeLocationListener?.let { listener ->
            try {
                getSystemService(LocationManager::class.java).removeUpdates(listener)
            } catch (_: Exception) {
                // The listener is already detached or permission changed during the request.
            }
        }
        activeLocationListener = null
        locationFallback = null

        if (location == null) {
            completeHostRequest(request.document, request.requestId, error = error ?: "Location is unavailable.")
            return
        }
        completeHostRequest(
            request.document,
            request.requestId,
            value = JSONObject()
                .put("latitude", location.latitude)
                .put("longitude", location.longitude)
                .put("accuracy", location.accuracy.toDouble())
                .put("timestamp", location.time)
                .put("cached", cached),
        )
    }

    private fun requestNotification(action: PendingNotificationAction) {
        if (screen != Screen.DOCUMENT || currentDocument !== action.document) return
        if (action is PendingNotificationAction.Cancel) {
            performNotificationAction(action)
            return
        }
        if (
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            hasPermission(Manifest.permission.POST_NOTIFICATIONS)
        ) {
            performNotificationAction(action)
            return
        }

        pendingNotificationActions.addLast(action)
        if (!notificationPermissionRequestActive) {
            notificationPermissionRequestActive = true
            requestPermissions(
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                NOTIFICATION_PERMISSION_REQUEST,
            )
        }
    }

    private fun performNotificationAction(action: PendingNotificationAction) {
        if (screen != Screen.DOCUMENT || currentDocument !== action.document) return
        val applicationName = action.document.displayName.removeSuffix(".svge")
        try {
            val result = when (action) {
                is PendingNotificationAction.Show -> {
                    SausageNotifications.show(
                        this,
                        SausageNotificationSpec(
                            action.document.storageScope,
                            applicationName,
                            action.requestId,
                            action.title,
                            action.body,
                        ),
                    )
                    JSONObject().put("shown", true)
                }

                is PendingNotificationAction.Schedule -> {
                    SausageNotifications.schedule(
                        this,
                        SausageNotificationSpec(
                            action.document.storageScope,
                            applicationName,
                            action.notificationId,
                            action.title,
                            action.body,
                        ),
                        action.atMillis,
                    )
                    JSONObject()
                        .put("id", action.notificationId)
                        .put("scheduledAt", action.atMillis)
                }

                is PendingNotificationAction.Cancel -> {
                    SausageNotifications.cancel(
                        this,
                        action.document.storageScope,
                        action.notificationId,
                    )
                    JSONObject().put("cancelled", true)
                }
            }
            completeHostRequest(action.document, action.requestId, value = result)
        } catch (error: Exception) {
            Log.w(TAG, "Device notification operation failed", error)
            completeHostRequest(
                action.document,
                action.requestId,
                error = error.message ?: "The notification operation failed.",
            )
        }
    }

    private fun completeHostRequest(
        document: SausageDocument,
        requestId: String,
        value: Any? = null,
        error: String? = null,
    ) {
        if (screen != Screen.DOCUMENT || currentDocument !== document) return
        val view = webView ?: return
        val result = JSONObject().apply {
            put("ok", error == null)
            if (error == null) put("value", value ?: JSONObject.NULL)
            else put("error", error.take(300))
        }.toString()
        view.evaluateJavascript(
            "window.__sausageCompleteHostRequest(${JSONObject.quote(requestId)}, ${JSONObject.quote(result)})",
            null,
        )
    }

    private fun readClipboard(
        document: SausageDocument,
        requestId: String,
    ) {
        if (screen != Screen.DOCUMENT || currentDocument !== document) return
        try {
            val clipboard = getSystemService(ClipboardManager::class.java)
            val text = clipboard.primaryClip
                ?.takeIf { it.itemCount > 0 }
                ?.getItemAt(0)
                ?.coerceToText(this)
                ?.toString()
            if (text != null && text.length > MAX_CLIPBOARD_TEXT_LENGTH) {
                throw IllegalStateException("Clipboard text is too large for a Sausage document.")
            }
            completeHostRequest(document, requestId, value = text)
        } catch (error: Exception) {
            Log.w(TAG, "Clipboard read failed", error)
            completeHostRequest(document, requestId, error = error.message ?: "The clipboard could not be read.")
        }
    }

    private fun writeClipboard(
        document: SausageDocument,
        requestId: String,
        text: String,
    ) {
        if (screen != Screen.DOCUMENT || currentDocument !== document) return
        try {
            getSystemService(ClipboardManager::class.java).setPrimaryClip(
                ClipData.newPlainText(document.displayName.removeSuffix(".svge"), text),
            )
            completeHostRequest(document, requestId, value = JSONObject().put("written", true))
        } catch (error: Exception) {
            Log.w(TAG, "Clipboard write failed", error)
            completeHostRequest(document, requestId, error = error.message ?: "The clipboard could not be written.")
        }
    }

    private fun shareText(
        document: SausageDocument,
        requestId: String,
        title: String,
        text: String,
    ) {
        if (screen != Screen.DOCUMENT || currentDocument !== document) return
        try {
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
                if (title.isNotEmpty()) putExtra(Intent.EXTRA_SUBJECT, title)
            }
            val chooserTitle = title.ifEmpty { getString(R.string.share_text) }
            startActivity(Intent.createChooser(sendIntent, chooserTitle))
            completeHostRequest(document, requestId, value = JSONObject().put("opened", true))
        } catch (error: Exception) {
            Log.w(TAG, "Text share failed", error)
            completeHostRequest(document, requestId, error = "Android could not open the share sheet.")
        }
    }

    @Suppress("DEPRECATION")
    private fun performHaptic(
        document: SausageDocument,
        requestId: String,
        pattern: String,
    ) {
        if (screen != Screen.DOCUMENT || currentDocument !== document) return
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                getSystemService(VibratorManager::class.java).defaultVibrator
            } else {
                getSystemService(Vibrator::class.java)
            }
            if (!vibrator.hasVibrator()) {
                throw IllegalStateException("This device does not provide haptic feedback.")
            }
            val effect = when (pattern) {
                "light" -> VibrationEffect.createOneShot(18L, 70)
                "medium" -> VibrationEffect.createOneShot(32L, 125)
                "success" -> VibrationEffect.createWaveform(
                    longArrayOf(0L, 22L, 55L, 34L),
                    intArrayOf(0, 80, 0, 145),
                    -1,
                )
                else -> throw IllegalArgumentException("Unknown haptic pattern.")
            }
            vibrator.vibrate(effect)
            completeHostRequest(
                document,
                requestId,
                value = JSONObject().put("performed", true).put("pattern", pattern),
            )
        } catch (error: Exception) {
            Log.w(TAG, "Haptic feedback failed", error)
            completeHostRequest(document, requestId, error = error.message ?: "Haptic feedback is unavailable.")
        }
    }

    private fun hasPermission(permission: String): Boolean =
        checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            LOCATION_PERMISSION_REQUEST -> {
                val request = pendingLocationRequest ?: return
                if (hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)) {
                    startCurrentLocation(request)
                } else {
                    finishLocationRequest(error = "Location permission was not granted.")
                }
            }

            NOTIFICATION_PERMISSION_REQUEST -> {
                notificationPermissionRequestActive = false
                val allowed = hasPermission(Manifest.permission.POST_NOTIFICATIONS)
                val actions = pendingNotificationActions.toList()
                pendingNotificationActions.clear()
                actions.forEach { action ->
                    if (allowed) performNotificationAction(action)
                    else completeHostRequest(
                        action.document,
                        action.requestId,
                        error = "Notification permission was not granted.",
                    )
                }
            }
        }
    }

    private fun showError(message: String) {
        clearScreen()
        documentBackStack.clear()
        screen = Screen.ERROR

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(32), dp(64), dp(32), dp(40))

            addView(TextView(context).apply {
                text = getString(R.string.document_load_error_title)
                setTextColor(Color.WHITE)
                textSize = 24f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
            })

            addView(TextView(context).apply {
                text = message
                setTextColor(MUTED_TEXT)
                textSize = 16f
                gravity = Gravity.CENTER
                setPadding(0, dp(12), 0, 0)
            })

            addView(
                actionButton(
                    label = getString(R.string.back_home),
                    primary = true,
                    onClick = ::showHome,
                ),
                linearParams(
                    width = ViewGroup.LayoutParams.MATCH_PARENT,
                    height = dp(58),
                    topMargin = dp(30),
                ),
            )
        }

        root.addView(
            content,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
    }

    private fun actionButton(
        label: String,
        primary: Boolean,
        onClick: () -> Unit,
    ): Button = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 15f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(if (primary) BACKGROUND else Color.WHITE)
        setPadding(dp(18), 0, dp(18), 0)
        minHeight = 0
        minimumHeight = 0
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(18).toFloat()
            setColor(if (primary) ACCENT else PANEL)
            if (!primary) setStroke(dp(1), BORDER)
        }
        setOnClickListener { onClick() }
    }

    private fun linearParams(
        width: Int,
        height: Int,
        topMargin: Int = 0,
    ) = LinearLayout.LayoutParams(width, height).apply {
        this.topMargin = topMargin
    }

    private fun clearScreen() {
        val departingDocument = currentDocument
        if (pendingLocationRequest?.document === departingDocument) {
            activeLocationListener?.let { listener ->
                try {
                    getSystemService(LocationManager::class.java).removeUpdates(listener)
                } catch (_: Exception) {
                    // The listener is already detached or permission changed.
                }
            }
            pendingLocationRequest = null
            activeLocationListener = null
            locationFallback = null
        }
        if (departingDocument != null) {
            pendingNotificationActions.removeIf { it.document === departingDocument }
        }
        photoFileCallback?.onReceiveValue(null)
        photoFileCallback = null
        webView?.let { view ->
            webView = null
            root.removeView(view)
            view.stopLoading()
            view.removeJavascriptInterface(SausageStorageBridge.JAVASCRIPT_NAME)
            view.removeJavascriptInterface(SausageDatabaseBridge.JAVASCRIPT_NAME)
            view.removeJavascriptInterface(SausageNavigationBridge.JAVASCRIPT_NAME)
            view.removeJavascriptInterface(SausageDeviceBridge.JAVASCRIPT_NAME)
            view.destroy()
        }
        databaseBridge?.close()
        databaseBridge = null
        currentDocument = null
        root.removeAllViews()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    @SuppressLint("GestureBackNavigation")
    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        handleBack()
    }

    private fun handleBack() {
        when (screen) {
            Screen.DOCUMENT -> {
                val currentView = webView
                if (currentView == null) {
                    showHome()
                    return
                }
                currentView.evaluateJavascript(HANDLE_DOCUMENT_BACK_SCRIPT) { consumed ->
                    if (consumed != "true" && screen == Screen.DOCUMENT && webView === currentView) {
                        navigateDocumentBack()
                    }
                }
            }

            Screen.ERROR -> showHome()
            Screen.HOME -> finishAfterTransition()
        }
    }

    private fun navigateDocumentBack() {
        if (documentBackStack.isEmpty()) {
            showHome()
        } else {
            showDocument(documentBackStack.removeLast())
        }
    }

    override fun onDestroy() {
        clearScreen()
        super.onDestroy()
    }

    private enum class Screen {
        HOME,
        DOCUMENT,
        ERROR,
    }

    private data class PendingLocationRequest(
        val document: SausageDocument,
        val requestId: String,
        val precise: Boolean,
    )

    private sealed interface PendingNotificationAction {
        val document: SausageDocument
        val requestId: String

        data class Show(
            override val document: SausageDocument,
            override val requestId: String,
            val title: String,
            val body: String,
        ) : PendingNotificationAction

        data class Schedule(
            override val document: SausageDocument,
            override val requestId: String,
            val notificationId: String,
            val title: String,
            val body: String,
            val atMillis: Long,
        ) : PendingNotificationAction

        data class Cancel(
            override val document: SausageDocument,
            override val requestId: String,
            val notificationId: String,
        ) : PendingNotificationAction
    }

    companion object {
        private val APPLY_RUNTIME_SCRIPT = """
            (() => {
              document.documentElement.style.setProperty('-webkit-tap-highlight-color', 'transparent');

              const nativeStorage = window.${SausageStorageBridge.JAVASCRIPT_NAME};
              const nativeDatabase = window.${SausageDatabaseBridge.JAVASCRIPT_NAME};
              const nativeNavigation = window.${SausageNavigationBridge.JAVASCRIPT_NAME};
              const nativeDevice = window.${SausageDeviceBridge.JAVASCRIPT_NAME};
              const nativeControls = window.__sausageControls || null;
              const requireKey = (key, kind) => {
                const value = String(key);
                if (!/^[A-Za-z0-9][A-Za-z0-9._-]{0,127}${'$'}/.test(value)) {
                  throw new TypeError(`${'$'}{kind} keys must contain only letters, numbers, dot, underscore or hyphen.`);
                }
                return value;
              };

              const storage = Object.freeze({
                get(key) {
                  return Promise.resolve().then(() => {
                    const encoded = nativeStorage.get(requireKey(key, 'Storage'));
                    return encoded == null ? null : JSON.parse(encoded);
                  });
                },
                set(key, value) {
                  return Promise.resolve().then(() => {
                    const encoded = JSON.stringify(value);
                    if (encoded === undefined) {
                      throw new TypeError('Storage values must be JSON-compatible.');
                    }
                    if (!nativeStorage.set(requireKey(key, 'Storage'), encoded)) {
                      throw new Error('Sausage could not store that value.');
                    }
                    return value;
                  });
                },
                remove(key) {
                  return Promise.resolve().then(() => {
                    if (!nativeStorage.remove(requireKey(key, 'Storage'))) {
                      throw new Error('Sausage could not remove that value.');
                    }
                  });
                },
              });

              const controls = Object.freeze({
                getValue(key) {
                  if (!nativeControls) {
                    throw new Error('This document does not declare standard controls.');
                  }
                  return nativeControls.getValue(requireKey(key, 'Control'));
                },
                setValue(key, value) {
                  if (!nativeControls) {
                    throw new Error('This document does not declare standard controls.');
                  }
                  return nativeControls.setValue(requireKey(key, 'Control'), value);
                },
                onChange(key, listener) {
                  if (!nativeControls) {
                    throw new Error('This document does not declare standard controls.');
                  }
                  if (typeof listener !== 'function') {
                    throw new TypeError('Control change listeners must be functions.');
                  }
                  return nativeControls.onChange(requireKey(key, 'Control'), listener);
                },
              });

              const databaseParameters = (parameters) => {
                if (!Array.isArray(parameters)) {
                  throw new TypeError('Database parameters must be an array.');
                }
                return parameters.map((value, index) => {
                  if (
                    value === null ||
                    typeof value === 'string' ||
                    typeof value === 'boolean'
                  ) {
                    return value;
                  }
                  if (typeof value === 'number' && Number.isFinite(value)) {
                    if (Number.isInteger(value) && !Number.isSafeInteger(value)) {
                      throw new RangeError(
                        `Database parameter ${'$'}{index + 1} is outside JavaScript's safe integer range.`
                      );
                    }
                    return value;
                  }
                  throw new TypeError(
                    `Database parameter ${'$'}{index + 1} must be null, Boolean, a finite number or a string.`
                  );
                });
              };
              const runDatabaseCall = (method, sql, parameters) => Promise.resolve().then(() => {
                if (typeof sql !== 'string' || sql.trim() === '') {
                  throw new TypeError('Database SQL must be a non-empty string.');
                }
                const encodedParameters = JSON.stringify(databaseParameters(parameters));
                const encodedResult = nativeDatabase[method](sql, encodedParameters);
                const result = JSON.parse(encodedResult);
                if (!result || result.ok !== true) {
                  throw new Error(
                    result && typeof result.error === 'string'
                      ? result.error
                      : 'The database operation failed.'
                  );
                }
                return result.value;
              });
              const db = Object.freeze({
                execute(sql, parameters = []) {
                  return runDatabaseCall('execute', sql, parameters);
                },
                query(sql, parameters = []) {
                  return runDatabaseCall('query', sql, parameters);
                },
              });

              const navigationResult = (encodedResult) => {
                const result = JSON.parse(encodedResult);
                if (!result || result.ok !== true) {
                  throw new Error(
                    result && typeof result.error === 'string'
                      ? result.error
                      : 'The navigation operation failed.'
                  );
                }
                return result.value;
              };
              const navigation = Object.freeze({
                open(relativePath) {
                  return Promise.resolve().then(() => {
                    if (typeof relativePath !== 'string' || relativePath.trim() === '') {
                      throw new TypeError('A linked document path must be a non-empty string.');
                    }
                    return navigationResult(nativeNavigation.open(relativePath));
                  });
                },
                back() {
                  return Promise.resolve().then(() => navigationResult(nativeNavigation.back()));
                },
              });

              let nextHostRequestId = 1;
              const pendingHostRequests = new Map();
              const completeHostRequest = (requestId, encodedResult) => {
                const pending = pendingHostRequests.get(String(requestId));
                if (!pending) return false;
                pendingHostRequests.delete(String(requestId));
                try {
                  const result = JSON.parse(encodedResult);
                  if (!result || result.ok !== true) {
                    pending.reject(new Error(
                      result && typeof result.error === 'string'
                        ? result.error
                        : 'The device operation failed.'
                    ));
                  } else {
                    pending.resolve(result.value);
                  }
                } catch (error) {
                  pending.reject(error);
                }
                return true;
              };
              Object.defineProperty(window, '__sausageCompleteHostRequest', {
                value: completeHostRequest,
                configurable: false,
              });
              const hostRequest = (invoke) => new Promise((resolve, reject) => {
                if (nextHostRequestId > Number.MAX_SAFE_INTEGER) {
                  reject(new Error('The device request counter is exhausted.'));
                  return;
                }
                const requestId = `host-${'$'}{nextHostRequestId++}`;
                pendingHostRequests.set(requestId, { resolve, reject });
                try {
                  const acknowledgement = JSON.parse(invoke(requestId));
                  if (!acknowledgement || acknowledgement.ok !== true) {
                    pendingHostRequests.delete(requestId);
                    reject(new Error(
                      acknowledgement && typeof acknowledgement.error === 'string'
                        ? acknowledgement.error
                        : 'The device operation was rejected.'
                    ));
                  }
                } catch (error) {
                  pendingHostRequests.delete(requestId);
                  reject(error);
                }
              });

              const location = Object.freeze({
                current(options = {}) {
                  if (!options || typeof options !== 'object' || Array.isArray(options)) {
                    return Promise.reject(new TypeError('Location options must be an object.'));
                  }
                  const accuracy = options.accuracy == null ? 'balanced' : options.accuracy;
                  if (accuracy !== 'balanced' && accuracy !== 'precise') {
                    return Promise.reject(
                      new RangeError('Location accuracy must be balanced or precise.')
                    );
                  }
                  return hostRequest((requestId) =>
                    nativeDevice.currentLocation(requestId, accuracy === 'precise')
                  );
                },
              });

              const requireNotificationText = (value, name, maxLength, allowEmpty) => {
                if (typeof value !== 'string') {
                  throw new TypeError(`${'$'}{name} must be a string.`);
                }
                if ((!allowEmpty && value.trim() === '') || value.length > maxLength) {
                  throw new RangeError(`${'$'}{name} has an invalid length.`);
                }
                return value;
              };
              const requireNotificationOptions = (options) => {
                if (!options || typeof options !== 'object' || Array.isArray(options)) {
                  throw new TypeError('Notification options must be an object.');
                }
                return {
                  title: requireNotificationText(options.title, 'Notification title', 80, false),
                  body: requireNotificationText(options.body == null ? '' : options.body, 'Notification body', 300, true),
                };
              };
              const notifications = Object.freeze({
                show(options) {
                  return Promise.resolve().then(() => {
                    const value = requireNotificationOptions(options);
                    return hostRequest((requestId) =>
                      nativeDevice.showNotification(requestId, value.title, value.body)
                    );
                  });
                },
                schedule(options) {
                  return Promise.resolve().then(() => {
                    const value = requireNotificationOptions(options);
                    const id = requireKey(options.id, 'Notification');
                    const at = options.at instanceof Date ? options.at.getTime() : options.at;
                    if (!Number.isSafeInteger(at)) {
                      throw new TypeError('A notification time must be a safe whole-number timestamp.');
                    }
                    return hostRequest((requestId) =>
                      nativeDevice.scheduleNotification(requestId, id, value.title, value.body, at)
                    );
                  });
                },
                cancel(id) {
                  return Promise.resolve().then(() => {
                    const notificationId = requireKey(id, 'Notification');
                    return hostRequest((requestId) =>
                      nativeDevice.cancelNotification(requestId, notificationId)
                    );
                  });
                },
              });

              const requireBoundedText = (value, name, maxLength, allowEmpty) => {
                if (typeof value !== 'string') {
                  throw new TypeError(`${'$'}{name} must be a string.`);
                }
                if ((!allowEmpty && value.trim() === '') || value.length > maxLength) {
                  throw new RangeError(`${'$'}{name} has an invalid length.`);
                }
                return value;
              };
              const clipboard = Object.freeze({
                readText() {
                  return hostRequest((requestId) => nativeDevice.readClipboard(requestId));
                },
                writeText(text) {
                  return Promise.resolve().then(() => hostRequest((requestId) =>
                    nativeDevice.writeClipboard(
                      requestId,
                      requireBoundedText(text, 'Clipboard text', 16384, true)
                    )
                  ));
                },
              });
              const share = Object.freeze({
                text(options) {
                  return Promise.resolve().then(() => {
                    if (!options || typeof options !== 'object' || Array.isArray(options)) {
                      throw new TypeError('Share options must be an object.');
                    }
                    const title = requireBoundedText(
                      options.title == null ? '' : options.title,
                      'Share title',
                      80,
                      true
                    );
                    const value = requireBoundedText(options.text, 'Shared text', 20000, false);
                    return hostRequest((requestId) =>
                      nativeDevice.shareText(requestId, title, value)
                    );
                  });
                },
              });
              const haptics = Object.freeze({
                perform(pattern = 'light') {
                  return Promise.resolve().then(() => {
                    if (pattern !== 'light' && pattern !== 'medium' && pattern !== 'success') {
                      throw new RangeError('A haptic pattern must be light, medium or success.');
                    }
                    return hostRequest((requestId) => nativeDevice.performHaptic(requestId, pattern));
                  });
                },
              });

              Object.defineProperty(window, 'sausage', {
                value: Object.freeze({
                  storage,
                  controls,
                  db,
                  navigation,
                  location,
                  notifications,
                  clipboard,
                  share,
                  haptics,
                }),
                writable: false,
                configurable: false,
              });
              const prepareDocument = window.__sausagePrepareDocument;
              const announceReady = () => window.dispatchEvent(new Event('sausage-ready'));
              Promise.resolve(
                typeof prepareDocument === 'function' ? prepareDocument() : undefined
              ).then(announceReady, (error) => {
                console.error('Sausage could not prepare this document', error);
                announceReady();
              });
            })();
        """.trimIndent()
        private const val HANDLE_DOCUMENT_BACK_SCRIPT =
            "typeof window.__sausageHandleBack === 'function' && window.__sausageHandleBack()"
        private const val TAG = "Sausage"
        private const val OPEN_DOCUMENT_REQUEST = 1001
        private const val OPEN_PHOTO_REQUEST = 1002
        private const val LOCATION_PERMISSION_REQUEST = 1003
        private const val NOTIFICATION_PERMISSION_REQUEST = 1004
        private const val FUSED_LOCATION_PROVIDER = "fused"
        private const val BUNDLED_DOCUMENT = "first-card.svge"
        private const val INPUT_DOCUMENT = "dream-note.svge"
        private const val PHOTO_DOCUMENT = "dream-token.svge"
        private const val DEVICE_DOCUMENT = "night-beacon.svge"
        private const val LOCATION_TIMEOUT_MILLIS = 15_000L
        private const val MAX_CLIPBOARD_TEXT_LENGTH = 16_384

        private val BACKGROUND = Color.rgb(7, 16, 30)
        private val PANEL = Color.rgb(16, 36, 59)
        private val BORDER = Color.rgb(55, 77, 103)
        private val ACCENT = Color.rgb(246, 191, 118)
        private val MUTED_TEXT = Color.rgb(174, 190, 213)
        private val SUBTLE_TEXT = Color.rgb(116, 139, 166)
    }
}
