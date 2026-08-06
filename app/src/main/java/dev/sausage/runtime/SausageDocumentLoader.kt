package dev.sausage.runtime

import android.net.Uri
import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets

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

        val response = document.flow?.let(::flowResponse) ?: DocumentResponse(
            mimeType = SVG_MIME_TYPE,
            content = document.content,
        )

        return WebResourceResponse(
            response.mimeType,
            UTF_8,
            200,
            "OK",
            mapOf(
                "Cache-Control" to "no-store",
                "Content-Security-Policy" to "default-src 'none'; style-src 'unsafe-inline'; script-src 'unsafe-inline'",
            ),
            ByteArrayInputStream(response.content),
        )
    }

    private fun flowResponse(flow: SausageFlow): DocumentResponse {
        val sourceSvg = document.content
            .toString(StandardCharsets.UTF_8)
            .replaceFirst(XML_DECLARATION, "")
        val control = flow.textArea
        val hint = control.hint?.let {
            "<p class=\"sausage-hint\">${it.escapeHtml()}</p>"
        }.orEmpty()
        val button = flow.button
        val buttonHtml = button?.let {
            """
                <button id="sausage-action-button"
                        class="sausage-action-button"
                        type="button"
                        data-action="${it.action.escapeHtml()}">${it.label.escapeHtml()}</button>
                <p id="sausage-action-status" class="sausage-action-status" aria-live="polite">Ready</p>
            """.trimIndent()
        }.orEmpty()
        val html = """
            <!doctype html>
            <html lang="en">
              <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
                <title>${document.displayName.escapeHtml()}</title>
                <style>
                  :root {
                    --sausage-keyboard-inset: 0px;
                    color-scheme: dark;
                    font-family: Inter, Roboto, Arial, sans-serif;
                    background: #07101e;
                  }
                  * { box-sizing: border-box; -webkit-tap-highlight-color: transparent; }
                  html, body { margin: 0; min-height: 100%; background: #07101e; }
                  body {
                    color: #f8fafc;
                    overscroll-behavior-y: none;
                  }
                  .sausage-graphic {
                    width: 100%;
                    overflow: hidden;
                    background: #07101e;
                  }
                  .sausage-graphic > svg {
                    display: block;
                    width: 100%;
                    height: auto;
                  }
                  .sausage-controls {
                    position: relative;
                    margin-top: -1px;
                    padding: 24px 24px calc(36px + env(safe-area-inset-bottom) + var(--sausage-keyboard-inset));
                    background: #07101e;
                  }
                  .sausage-control-card {
                    padding: 22px;
                    border: 1px solid rgba(184, 207, 233, .18);
                    border-radius: 24px;
                    background: linear-gradient(155deg, #10243b, #0b1b2e);
                    box-shadow: 0 22px 55px rgba(0, 0, 0, .28);
                  }
                  label {
                    display: block;
                    margin-bottom: 9px;
                    color: #f5dfbb;
                    font-size: 17px;
                    font-weight: 700;
                    letter-spacing: .01em;
                  }
                  .sausage-hint {
                    margin: 0 0 16px;
                    color: #9fb5cf;
                    font-size: 13px;
                    line-height: 1.45;
                  }
                  textarea {
                    display: block;
                    width: 100%;
                    min-height: 156px;
                    resize: vertical;
                    padding: 17px 18px;
                    border: 2px solid transparent;
                    border-radius: 18px;
                    outline: none;
                    background: #fffaf0;
                    color: #172b3d;
                    caret-color: #dd7e59;
                    font: 400 16px/1.5 Inter, Roboto, Arial, sans-serif;
                    box-shadow: inset 0 0 0 1px rgba(23, 43, 61, .12);
                  }
                  textarea::placeholder { color: #7c8994; opacity: 1; }
                  textarea:focus {
                    border-color: #efaa65;
                    box-shadow: 0 0 0 4px rgba(239, 170, 101, .17);
                  }
                  .sausage-save-status {
                    min-height: 18px;
                    margin: 13px 2px 0;
                    color: #8fa7c2;
                    font-size: 12px;
                    font-weight: 600;
                    letter-spacing: .04em;
                  }
                  .sausage-action-button {
                    display: block;
                    width: 100%;
                    min-height: 58px;
                    margin-top: 22px;
                    padding: 13px 20px;
                    border: 0;
                    border-radius: 18px;
                    outline: none;
                    background: linear-gradient(135deg, #f6bf76, #e9895d);
                    color: #172b3d;
                    font: 800 15px/1.2 Inter, Roboto, Arial, sans-serif;
                    letter-spacing: .035em;
                    box-shadow: 0 12px 28px rgba(221, 126, 89, .2);
                    cursor: pointer;
                    transition: transform 120ms ease, box-shadow 180ms ease, filter 180ms ease;
                  }
                  .sausage-action-button:focus-visible {
                    box-shadow: 0 0 0 4px rgba(239, 170, 101, .24), 0 12px 28px rgba(221, 126, 89, .2);
                  }
                  .sausage-action-button:active { transform: scale(.985); }
                  .sausage-action-button[aria-busy="true"] { filter: saturate(.72); }
                  .sausage-action-button.completed { animation: button-confirm 520ms ease; }
                  .sausage-action-status {
                    min-height: 18px;
                    margin: 10px 2px 0;
                    color: #9fb5cf;
                    font-size: 12px;
                    font-weight: 600;
                    letter-spacing: .04em;
                  }
                  @keyframes button-confirm {
                    0%, 100% { transform: scale(1); }
                    42% { transform: scale(.975); filter: brightness(1.16); }
                  }
                </style>
              </head>
              <body>
                <section class="sausage-graphic" aria-label="Graphical introduction">
                  $sourceSvg
                </section>
                <section class="sausage-controls">
                  <div class="sausage-control-card">
                    <label for="sausage-text-area">${control.label.escapeHtml()}</label>
                    $hint
                    <textarea id="sausage-text-area"
                              data-storage-key="${control.key.escapeHtml()}"
                              placeholder="${control.placeholder.orEmpty().escapeHtml()}"
                              spellcheck="true"></textarea>
                    <p id="sausage-save-status" class="sausage-save-status" aria-live="polite">Saved automatically on this device</p>
                    $buttonHtml
                  </div>
                </section>
                <script>
                  window.addEventListener('sausage-ready', async () => {
                    const input = document.getElementById('sausage-text-area');
                    const status = document.getElementById('sausage-save-status');
                    const key = input.dataset.storageKey;
                    let saveTimer;

                    try {
                      const saved = await window.sausage.storage.get(key);
                      if (typeof saved === 'string') input.value = saved;
                    } catch (error) {
                      status.textContent = 'Could not restore this note';
                      console.error('Could not restore Dream Note', error);
                    }

                    const save = async () => {
                      try {
                        await window.sausage.storage.set(key, input.value);
                        status.textContent = 'Saved on this device';
                      } catch (error) {
                        status.textContent = 'Could not save this note';
                        console.error('Could not save Dream Note', error);
                      }
                    };

                    input.addEventListener('input', () => {
                      status.textContent = 'Saving…';
                      clearTimeout(saveTimer);
                      saveTimer = setTimeout(save, 180);
                    });
                    input.addEventListener('change', save);

                    const updateForKeyboard = () => {
                      const viewport = window.visualViewport;
                      const visibleHeight = viewport ? viewport.height : window.innerHeight;
                      const visibleTop = viewport ? viewport.offsetTop : 0;
                      const keyboardInset = Math.max(
                        0,
                        window.innerHeight - visibleHeight - visibleTop
                      );
                      document.documentElement.style.setProperty(
                        '--sausage-keyboard-inset',
                        `${'$'}{keyboardInset}px`
                      );

                      if (document.activeElement !== input) return;

                      requestAnimationFrame(() => {
                        const activeViewport = window.visualViewport;
                        const top = activeViewport ? activeViewport.offsetTop : 0;
                        const height = activeViewport ? activeViewport.height : window.innerHeight;
                        const bottom = top + height;
                        const bounds = input.getBoundingClientRect();
                        const breathingRoom = 20;

                        if (bounds.bottom > bottom - breathingRoom) {
                          window.scrollBy({
                            top: bounds.bottom - bottom + breathingRoom,
                            behavior: 'smooth',
                          });
                        } else if (bounds.top < top + breathingRoom) {
                          window.scrollBy({
                            top: bounds.top - top - breathingRoom,
                            behavior: 'smooth',
                          });
                        }
                      });
                    };

                    input.addEventListener('focus', () => {
                      setTimeout(updateForKeyboard, 80);
                      setTimeout(updateForKeyboard, 320);
                    });
                    input.addEventListener('blur', updateForKeyboard);
                    window.addEventListener('resize', updateForKeyboard);
                    if (window.visualViewport) {
                      window.visualViewport.addEventListener('resize', updateForKeyboard);
                    }

                    const actionButton = document.getElementById('sausage-action-button');
                    const actionStatus = document.getElementById('sausage-action-status');
                    if (actionButton) {
                      actionButton.addEventListener('click', async () => {
                        input.blur();
                        actionButton.classList.remove('completed');
                        actionButton.setAttribute('aria-busy', 'true');
                        actionStatus.textContent = 'Running…';

                        try {
                          const handler = window[actionButton.dataset.action];
                          if (typeof handler !== 'function') {
                            throw new Error(`Unknown Sausage action: ${'$'}{actionButton.dataset.action}`);
                          }
                          const result = await handler.call(window);
                          actionStatus.textContent = typeof result === 'string' ? result : 'Done';
                          actionButton.classList.add('completed');
                        } catch (error) {
                          actionStatus.textContent = 'This action could not run';
                          console.error('Could not run Sausage button action', error);
                        } finally {
                          actionButton.removeAttribute('aria-busy');
                        }
                      });
                    }
                  }, { once: true });
                </script>
              </body>
            </html>
        """.trimIndent()

        return DocumentResponse(
            mimeType = HTML_MIME_TYPE,
            content = html.toByteArray(StandardCharsets.UTF_8),
        )
    }

    private fun String.escapeHtml(): String = this
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")

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
        private const val HTML_MIME_TYPE = "text/html"
        private const val UTF_8 = "utf-8"
        private val XML_DECLARATION = Regex("^\\s*<\\?xml[^?]*\\?>")
    }

    private data class DocumentResponse(
        val mimeType: String,
        val content: ByteArray,
    )
}
