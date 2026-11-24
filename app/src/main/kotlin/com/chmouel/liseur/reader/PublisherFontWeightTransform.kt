package com.chmouel.liseur.reader

import kotlin.math.roundToInt

/**
 * Applies the reader's requested weight to publisher rules which explicitly
 * declare normal. Embedded @font-face descriptors are deliberately untouched:
 * their normal and bold files must remain available for font matching.
 *
 * The transform only changes the live document's CSSOM. The EPUB archive is
 * never rewritten. ReaderScreen checks for pending work and applies the
 * transform inside its existing reflow/locator preservation scope.
 */
internal object PublisherFontWeightTransform {

    fun targetWeight(multiplier: Double?): Int {
        val safeMultiplier = multiplier?.takeIf { it.isFinite() && it >= 0.0 } ?: 1.0
        return (400.0 * safeMultiplier).roundToInt().coerceIn(1, 1000)
    }

    fun needsApplyScript(multiplier: Double?): String =
        NEEDS_APPLY_SCRIPT.replace("__PAPA_FONT_WEIGHT__", targetWeight(multiplier).toString())

    fun script(multiplier: Double?): String =
        APPLY_SCRIPT.replace("__PAPA_FONT_WEIGHT__", targetWeight(multiplier).toString())

    private val NEEDS_APPLY_SCRIPT = """
        (function () {
          var root = document.documentElement;
          if (!root) return false;
          var target = __PAPA_FONT_WEIGHT__;
          var state = window.__papaPublisherFontWeight;
          if (!state || state.target !== target) return true;

          function hasNormal(style) {
            if (!style || !style.getPropertyValue) return false;
            var value = style.getPropertyValue("font-weight");
            return !!value && value.trim().toLowerCase() === "normal";
          }

          function rulesNeedWork(rules) {
            if (!rules) return false;
            for (var i = 0; i < rules.length; i++) {
              var rule = rules[i];
              if (!rule) continue;
              try {
                if (rule.type === 1 && rule.style && hasNormal(rule.style)) return true;
                if (rule.cssRules && rulesNeedWork(rule.cssRules)) return true;
                if (rule.styleSheet && sheetNeedsWork(rule.styleSheet)) return true;
              } catch (ignored) {
                // An inaccessible nested rule does not block other sheets.
              }
            }
            return false;
          }

          function sheetNeedsWork(sheet) {
            try {
              return !!(sheet && sheet.cssRules && rulesNeedWork(sheet.cssRules));
            } catch (ignored) {
              return false;
            }
          }

          var sheets = document.styleSheets || [];
          for (var i = 0; i < sheets.length; i++) {
            if (sheetNeedsWork(sheets[i])) return true;
          }
          var inlineStyles = document.querySelectorAll("[style]");
          for (var j = 0; j < inlineStyles.length; j++) {
            if (hasNormal(inlineStyles[j].style)) return true;
          }
          return false;
        })();
    """.trimIndent()

    private val APPLY_SCRIPT = """
        (function () {
          var root = document.documentElement;
          if (!root) return false;

          var state = window.__papaPublisherFontWeight;
          if (!state) {
            state = { target: null, apply: null };
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
                // CSSStyleRule only. @font-face descriptors must retain their
                // explicit 400/700 mapping for embedded static font files.
                if (rule.type === 1 && rule.style) {
                  changed = rewriteDeclaration(rule.style) || changed;
                }
                if (rule.cssRules) changed = rewriteRules(rule.cssRules) || changed;
                if (rule.styleSheet) changed = rewriteSheet(rule.styleSheet) || changed;
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

          var changed = state.apply() || previousTarget !== target;
          return changed;
        })();
    """.trimIndent()
}
