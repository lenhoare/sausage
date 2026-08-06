package dev.sausage.runtime

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.webkit.RenderProcessGoneDetail
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
import java.util.ArrayDeque

class MainActivity : Activity() {
    private lateinit var root: FrameLayout
    private var webView: WebView? = null
    private var databaseBridge: SausageDatabaseBridge? = null
    private var currentDocument: SausageDocument? = null
    private val documentBackStack = ArrayDeque<SausageDocument>()
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
        webView?.let { view ->
            webView = null
            root.removeView(view)
            view.stopLoading()
            view.removeJavascriptInterface(SausageStorageBridge.JAVASCRIPT_NAME)
            view.removeJavascriptInterface(SausageDatabaseBridge.JAVASCRIPT_NAME)
            view.removeJavascriptInterface(SausageNavigationBridge.JAVASCRIPT_NAME)
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

    companion object {
        private val APPLY_RUNTIME_SCRIPT = """
            (() => {
              document.documentElement.style.setProperty('-webkit-tap-highlight-color', 'transparent');

              const nativeStorage = window.${SausageStorageBridge.JAVASCRIPT_NAME};
              const nativeDatabase = window.${SausageDatabaseBridge.JAVASCRIPT_NAME};
              const nativeNavigation = window.${SausageNavigationBridge.JAVASCRIPT_NAME};
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

              Object.defineProperty(window, 'sausage', {
                value: Object.freeze({ storage, controls, db, navigation }),
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
        private const val BUNDLED_DOCUMENT = "first-card.svge"
        private const val INPUT_DOCUMENT = "dream-note.svge"

        private val BACKGROUND = Color.rgb(7, 16, 30)
        private val PANEL = Color.rgb(16, 36, 59)
        private val BORDER = Color.rgb(55, 77, 103)
        private val ACCENT = Color.rgb(246, 191, 118)
        private val MUTED_TEXT = Color.rgb(174, 190, 213)
        private val SUBTLE_TEXT = Color.rgb(116, 139, 166)
    }
}
