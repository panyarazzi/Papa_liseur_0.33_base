package com.chmouel.liseur.reader

import kotlin.math.roundToInt

/**
 * Applies the reader's requested weight to publisher rules which explicitly
 * declare normal. Embedded @font-face descriptors are deliberately untouched:
 * their normal and bold files must remain available for font matching.
 *
 * The transform only changes the live document's CSSOM. The EPUB archive is
 * never rewritten. ReaderScreen runs it inside the existing reflow/locator
 * preservation scope for each visible reflowable resource.
 */
internal object PublisherFontWeightTransform {

    fun targetWeight(multiplier: Double?): Int {
        val safeMultiplier = multiplier?.takeIf { it.isFinite() && it >= 0.0 } ?: 1.0
        return (400.0 * safeMultiplier).roundToInt().coerceIn(1, 1000)
    }

    fun script(multiplier: Double?): String =
        SCRIPT.replace("__PAPA_FONT_WEIGHT__", targetWeight(multiplier).toString())

    private val SCRIPT = """
        (function () {
          var root = document.documentElement;
          if (!root) return false;

          var state = window.__papaPublisherFontWeight;
          if (!state) {
            state = { observer: null, pending: false, apply: null, schedule: null };
            window.__papaPublisherFontWeight = state;
          }

          var target = __PAPA_FONT_WEIGHT__;
          var previousTarget = state.target;
          state.target = target;
          root.style.setProperty("--papa-reader-font-weight", String(target));

          function rewriteDeclaration(style) {
            if (!style || !style.getPropertyValue) return false;
            var value = style.getPropertyValue("font-weight");
            if (!value || value.trim().toLowerCase() !== "normal") return false;
            var priority = style.getPropertyPriority("font-weight");
            style.setProperty(
              "font-weight",
              "var(--papa-reader-font-weight, 400)",
              priority
            );
            return true;
          }

          function rewriteRules(rules) {
            if (!rules) return false;
            var changed = false;
            for (var i = 0; i < rules.length; i++) {
              var rule = rules[i];
              if (!rule) continue;
              try {
                // CSSStyleRule only. @font-face descriptors are not ordinary
                // style rules and must retain their explicit 400/700 mapping.
                if (rule.type === 1 && rule.style) {
                  changed = rewriteDeclaration(rule.style) || changed;
                }
                if (rule.cssRules) {
                  changed = rewriteRules(rule.cssRules) || changed;
                }
                if (rule.styleSheet) {
                  changed = rewriteSheet(rule.styleSheet) || changed;
                }
              } catch (ignored) {
                // One inaccessible nested rule must not stop other sheets.
              }
            }
            return changed;
          }

          function rewriteSheet(sheet) {
            try {
              return !!(sheet && sheet.cssRules && rewriteRules(sheet.cssRules));
            } catch (ignored) {
              // Cross-origin or browser-protected stylesheets remain intact.
              return false;
            }
          }

          state.apply = function () {
            var changed = false;
            var sheets = document.styleSheets || [];
            for (var i = 0; i < sheets.length; i++) {
              changed = rewriteSheet(sheets[i]) || changed;
            }
            var inlineStyles = document.querySelectorAll("[style]");
            for (var j = 0; j < inlineStyles.length; j++) {
              changed = rewriteDeclaration(inlineStyles[j].style) || changed;
            }
            return changed;
          };

          state.schedule = function () {
            if (state.pending) return;
            state.pending = true;
            var run = function () {
              state.pending = false;
              if (state.apply) state.apply();
            };
            if (window.requestAnimationFrame) {
              window.requestAnimationFrame(run);
            } else {
              window.setTimeout(run, 0);
            }
          };

          var changed = state.apply() || previousTarget !== target;

          // Publisher XHTML may append a stylesheet after its first layout.
          // Keep the CSSOM transform current; the initial application itself
          // is coordinated with ReaderScreen's locator-preserving reflow.
          if (!state.observer && window.MutationObserver) {
            state.observer = new MutationObserver(function () {
              state.schedule();
            });
            state.observer.observe(root, {
              childList: true,
              subtree: true,
              attributes: true,
              attributeFilter: ["style", "href", "rel"]
            });
            document.addEventListener("load", state.schedule, true);
          }

          return changed;
        })();
    """.trimIndent()
}
