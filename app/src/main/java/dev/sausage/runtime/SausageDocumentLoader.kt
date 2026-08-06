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
        val screenHtml = renderScreens(flow)
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
                  .sausage-screen[hidden] { display: none; }
                  .sausage-screen-entering { animation: screen-in 240ms ease-out; }
                  .sausage-svg-source {
                    position: absolute;
                    width: 0;
                    height: 0;
                    overflow: hidden;
                    pointer-events: none;
                  }
                  .sausage-svg-source > svg {
                    width: 0 !important;
                    height: 0 !important;
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
                  .sausage-control + .sausage-control { margin-top: 22px; }
                  .sausage-text-area-control > label {
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
                  .sausage-text-area {
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
                  .sausage-text-area::placeholder { color: #7c8994; opacity: 1; }
                  .sausage-text-area:focus {
                    border-color: #efaa65;
                    box-shadow: 0 0 0 4px rgba(239, 170, 101, .17);
                  }
                  .sausage-choice {
                    min-width: 0;
                    margin: 0;
                    padding: 0;
                    border: 0;
                  }
                  .sausage-choice legend {
                    display: block;
                    width: 100%;
                    margin: 0 0 14px;
                    padding: 0;
                    color: #f5dfbb;
                    font-size: 17px;
                    font-weight: 700;
                    letter-spacing: .01em;
                  }
                  .sausage-choice-options {
                    display: grid;
                    gap: 10px;
                  }
                  .sausage-choice-option {
                    position: relative;
                    display: flex;
                    align-items: center;
                    min-height: 54px;
                    margin: 0;
                    padding: 12px 15px;
                    border: 1px solid rgba(184, 207, 233, .18);
                    border-radius: 17px;
                    background: rgba(255, 255, 255, .045);
                    color: #dce7f4;
                    font-size: 15px;
                    font-weight: 650;
                    line-height: 1.3;
                    cursor: pointer;
                    transition: border-color 160ms ease, background 160ms ease, transform 120ms ease;
                  }
                  .sausage-choice-option:active { transform: scale(.99); }
                  .sausage-choice-option:has(.sausage-choice-input:checked) {
                    border-color: rgba(239, 170, 101, .7);
                    background: rgba(239, 170, 101, .12);
                    color: #fff4dc;
                  }
                  .sausage-choice-option:has(.sausage-choice-input:focus-visible) {
                    outline: 3px solid rgba(239, 170, 101, .28);
                    outline-offset: 2px;
                  }
                  .sausage-choice-input {
                    width: 20px;
                    height: 20px;
                    flex: 0 0 auto;
                    margin: 0 13px 0 0;
                    accent-color: #efaa65;
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
                  @keyframes screen-in {
                    from { opacity: .18; transform: translateY(7px); }
                    to { opacity: 1; transform: translateY(0); }
                  }
                  @media (prefers-reduced-motion: reduce) {
                    .sausage-screen-entering { animation: none; }
                  }
                </style>
              </head>
              <body>
                <div id="sausage-svg-source" class="sausage-svg-source" aria-hidden="true">
                  $sourceSvg
                </div>
                <main id="sausage-flow">$screenHtml</main>
                <script>
                  (() => {
                    const svgNamespace = 'http://www.w3.org/2000/svg';
                    const source = document.querySelector('#sausage-svg-source > svg');
                    if (!source) return;

                    const viewBox = source.getAttribute('viewBox');
                    const preserveAspectRatio = source.getAttribute('preserveAspectRatio');

                    document.querySelectorAll('.sausage-graphic[data-graphic-ref]').forEach((slice) => {
                      const graphic = document.getElementById(slice.dataset.graphicRef);
                      if (!graphic || graphic.namespaceURI !== svgNamespace) return;

                      const svg = document.createElementNS(svgNamespace, 'svg');
                      if (viewBox) svg.setAttribute('viewBox', viewBox);
                      if (preserveAspectRatio) svg.setAttribute('preserveAspectRatio', preserveAspectRatio);
                      svg.setAttribute('width', '100%');
                      svg.setAttribute('aria-hidden', 'false');
                      svg.appendChild(graphic);
                      slice.appendChild(svg);
                    });

                    let activeScreen = document.querySelector('.sausage-screen:not([hidden])');
                    const screenHistory = [];

                    const showScreen = (target, scrollY) => {
                      if (!activeScreen || !target || target === activeScreen) return false;
                      if (document.activeElement instanceof HTMLElement) {
                        document.activeElement.blur();
                      }
                      activeScreen.hidden = true;
                      activeScreen.setAttribute('aria-hidden', 'true');
                      target.hidden = false;
                      target.removeAttribute('aria-hidden');
                      target.classList.remove('sausage-screen-entering');
                      void target.offsetWidth;
                      target.classList.add('sausage-screen-entering');
                      activeScreen = target;
                      window.scrollTo(0, scrollY);
                      return true;
                    };

                    const findScreen = (id) => document.querySelector(
                      `.sausage-screen[data-screen-id="${'$'}{CSS.escape(String(id))}"]`
                    );

                    const navigateTo = (targetId) => {
                      const target = findScreen(targetId);
                      if (!target || target === activeScreen) return false;

                      const previous = screenHistory[screenHistory.length - 1];
                      if (previous && previous.id === target.dataset.screenId) {
                        screenHistory.pop();
                        return showScreen(target, previous.scrollY);
                      }

                      screenHistory.push({
                        id: activeScreen.dataset.screenId,
                        scrollY: window.scrollY,
                      });
                      return showScreen(target, 0);
                    };

                    const navigateBack = () => {
                      const previous = screenHistory.pop();
                      if (!previous) return false;
                      return showScreen(findScreen(previous.id), previous.scrollY);
                    };

                    Object.defineProperty(window, '__sausageNavigateTo', {
                      value: navigateTo,
                      configurable: false,
                    });
                    Object.defineProperty(window, '__sausageHandleBack', {
                      value: navigateBack,
                      configurable: false,
                    });

                    const controlAdapters = new Map();

                    document.querySelectorAll('.sausage-text-area[data-control-key]').forEach((input) => {
                      controlAdapters.set(input.dataset.controlKey, Object.freeze({
                        root: input,
                        getValue: () => input.value,
                        restore: (value) => {
                          if (typeof value === 'string') input.value = value;
                        },
                        setValue: (value) => {
                          const nextValue = value == null ? '' : String(value);
                          input.value = nextValue;
                          input.dispatchEvent(new Event('input', { bubbles: true }));
                          input.dispatchEvent(new Event('change', { bubbles: true }));
                          return nextValue;
                        },
                      }));
                    });

                    document.querySelectorAll('.sausage-choice[data-control-key]').forEach((choice) => {
                      const options = Array.from(choice.querySelectorAll('.sausage-choice-input'));
                      const applyValue = (value, notify) => {
                        const nextValue = value == null ? null : String(value);
                        const selected = nextValue == null
                          ? null
                          : options.find((option) => option.value === nextValue);
                        if (nextValue != null && !selected) {
                          throw new RangeError(
                            `Invalid value for Sausage control ${'$'}{choice.dataset.controlKey}: ${'$'}{nextValue}`
                          );
                        }
                        const previous = options.find((option) => option.checked) || null;
                        options.forEach((option) => {
                          option.checked = option === selected;
                        });
                        if (notify && selected !== previous) {
                          const eventTarget = selected || previous;
                          if (eventTarget) {
                            eventTarget.dispatchEvent(new Event('input', { bubbles: true }));
                            eventTarget.dispatchEvent(new Event('change', { bubbles: true }));
                          }
                        }
                        return nextValue;
                      };
                      controlAdapters.set(choice.dataset.controlKey, Object.freeze({
                        root: choice,
                        getValue: () => {
                          const selected = options.find((option) => option.checked);
                          return selected ? selected.value : null;
                        },
                        restore: (value) => applyValue(value, false),
                        setValue: (value) => applyValue(value, true),
                      }));
                    });

                    const requireControl = (key) => {
                      const control = controlAdapters.get(String(key));
                      if (!control) {
                        throw new RangeError(`Unknown Sausage control: ${'$'}{key}`);
                      }
                      return control;
                    };

                    const controlBridge = Object.freeze({
                      getValue(key) {
                        return requireControl(key).getValue();
                      },
                      setValue(key, value) {
                        return requireControl(key).setValue(value);
                      },
                    });

                    Object.defineProperty(window, '__sausageControls', {
                      value: controlBridge,
                      configurable: false,
                    });
                    Object.defineProperty(window, '__sausageControlAdapters', {
                      value: Object.freeze({
                        get(key) {
                          return requireControl(key);
                        },
                      }),
                      configurable: false,
                    });
                  })();

                  Object.defineProperty(window, '__sausagePrepareDocument', {
                    value: async () => {
                    const valueControls = document.querySelectorAll('.sausage-value-control');
                    for (const input of valueControls) {
                      const adapter = window.__sausageControlAdapters.get(input.dataset.controlKey);
                      const control = input.closest('.sausage-control');
                      const status = control.querySelector('.sausage-save-status');
                      const key = input.dataset.controlKey;
                      const description = input.dataset.controlDescription;
                      let saveTimer;

                      try {
                        const saved = await window.sausage.storage.get(key);
                        if (saved !== null) adapter.restore(saved);
                      } catch (error) {
                        status.textContent = `Could not restore this ${'$'}{description}`;
                        console.error(`Could not restore Sausage ${'$'}{description}`, error);
                      }

                      const save = async () => {
                        clearTimeout(saveTimer);
                        try {
                          await window.sausage.storage.set(key, adapter.getValue());
                          status.textContent = 'Saved on this device';
                        } catch (error) {
                          status.textContent = `Could not save this ${'$'}{description}`;
                          console.error(`Could not save Sausage ${'$'}{description}`, error);
                        }
                      };

                      if (input.classList.contains('sausage-text-area')) {
                        input.addEventListener('input', () => {
                          status.textContent = 'Saving…';
                          clearTimeout(saveTimer);
                          saveTimer = setTimeout(save, 180);
                        });
                        input.addEventListener('change', save);
                      } else {
                        input.addEventListener('change', () => {
                          status.textContent = 'Saving…';
                          save();
                        });
                      }
                    }

                    const inputs = document.querySelectorAll('.sausage-text-area');
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

                      const input = document.activeElement;
                      if (!input || !input.classList.contains('sausage-text-area')) return;

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

                    inputs.forEach((input) => {
                      input.addEventListener('focus', () => {
                        setTimeout(updateForKeyboard, 80);
                        setTimeout(updateForKeyboard, 320);
                      });
                      input.addEventListener('blur', updateForKeyboard);
                    });
                    window.addEventListener('resize', updateForKeyboard);
                    if (window.visualViewport) {
                      window.visualViewport.addEventListener('resize', updateForKeyboard);
                    }

                    document.querySelectorAll('.sausage-action-button').forEach((actionButton) => {
                      const control = actionButton.closest('.sausage-control');
                      const actionStatus = control.querySelector('.sausage-action-status');
                      actionButton.addEventListener('click', async () => {
                        if (document.activeElement instanceof HTMLElement) {
                          document.activeElement.blur();
                        }
                        const targetScreen = actionButton.dataset.targetScreen;
                        if (targetScreen) {
                          window.__sausageNavigateTo(targetScreen);
                          return;
                        }
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
                    });
                    },
                    configurable: false,
                  });
                </script>
              </body>
            </html>
        """.trimIndent()

        return DocumentResponse(
            mimeType = HTML_MIME_TYPE,
            content = html.toByteArray(StandardCharsets.UTF_8),
        )
    }

    private fun renderScreens(flow: SausageFlow): String = flow.screens
        .mapIndexed { index, screen ->
            val hidden = if (index == 0) "" else " hidden aria-hidden=\"true\""
            """
                <section class="sausage-screen"
                         data-screen-id="${screen.id.escapeHtml()}"$hidden>
                  ${renderSlices(screen)}
                </section>
            """.trimIndent()
        }
        .joinToString(separator = "")

    private fun renderSlices(screen: SausageScreen): String {
        val html = StringBuilder()
        val pendingControls = mutableListOf<Pair<Int, SausageSlice>>()

        fun flushControls() {
            if (pendingControls.isEmpty()) return
            html.append("<section class=\"sausage-controls\"><div class=\"sausage-control-card\">")
            pendingControls.forEach { (index, control) ->
                html.append(renderControl(control, screen.id, index))
            }
            html.append("</div></section>")
            pendingControls.clear()
        }

        screen.slices.forEachIndexed { index, slice ->
            when (slice) {
                is SausageGraphic -> {
                    flushControls()
                    html.append(
                        "<section class=\"sausage-graphic\" data-graphic-ref=\"${slice.ref.escapeHtml()}\"></section>",
                    )
                }

                is SausageTextArea,
                is SausageChoice,
                is SausageButton,
                -> pendingControls += index to slice
            }
        }
        flushControls()
        return html.toString()
    }

    private fun renderControl(
        control: SausageSlice,
        screenId: String,
        index: Int,
    ): String = when (control) {
        is SausageTextArea -> {
            val id = "sausage-text-area-$screenId-$index"
            val hint = control.hint?.let {
                "<p class=\"sausage-hint\">${it.escapeHtml()}</p>"
            }.orEmpty()
            """
                <div class="sausage-control sausage-text-area-control">
                  <label for="$id">${control.label.escapeHtml()}</label>
                  $hint
                  <textarea id="$id"
                            class="sausage-text-area sausage-value-control"
                            data-control-key="${control.key.escapeHtml()}"
                            data-control-description="text"
                            placeholder="${control.placeholder.orEmpty().escapeHtml()}"
                            spellcheck="true"></textarea>
                  <p class="sausage-save-status" aria-live="polite">Saved automatically on this device</p>
                </div>
            """.trimIndent()
        }

        is SausageChoice -> {
            val id = "sausage-choice-$screenId-$index"
            val options = control.options.mapIndexed { optionIndex, option ->
                """
                    <label class="sausage-choice-option" for="$id-$optionIndex">
                      <input id="$id-$optionIndex"
                             class="sausage-choice-input"
                             type="radio"
                             name="$id"
                             value="${option.escapeHtml()}">
                      <span>${option.escapeHtml()}</span>
                    </label>
                """.trimIndent()
            }.joinToString(separator = "")
            """
                <div class="sausage-control sausage-choice-control">
                  <fieldset class="sausage-choice sausage-value-control"
                            data-control-key="${control.key.escapeHtml()}"
                            data-control-description="choice">
                    <legend>${control.label.escapeHtml()}</legend>
                    <div class="sausage-choice-options">$options</div>
                  </fieldset>
                  <p class="sausage-save-status" aria-live="polite">Saved automatically on this device</p>
                </div>
            """.trimIndent()
        }

        is SausageButton -> {
            val behaviour = control.action?.let {
                "data-action=\"${it.escapeHtml()}\""
            } ?: "data-target-screen=\"${checkNotNull(control.targetScreen).escapeHtml()}\""
            val status = if (control.action != null) {
                "<p class=\"sausage-action-status\" aria-live=\"polite\">Ready</p>"
            } else {
                ""
            }
            """
                <div class="sausage-control sausage-button-control">
                  <button id="sausage-action-button-$screenId-$index"
                          class="sausage-action-button"
                          type="button"
                          $behaviour>${control.label.escapeHtml()}</button>
                  $status
                </div>
            """.trimIndent()
        }

        is SausageGraphic -> error("A graphical slice cannot be rendered as a control.")
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
