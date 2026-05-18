package com.baseproject.aispeech.character.subtitle

import android.graphics.Color
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.View
import android.widget.TextView

/**
 * Karaoke-style implementation of [SubtitleRenderer] that renders into a [TextView].
 *
 * Three colour states are applied via [SpannableString]:
 *   - already spoken (before highlight range) → [spokenColor]
 *   - currently spoken (highlight range)      → [currentColor] + bold
 *   - not yet spoken (after highlight range)  → [pendingColor]
 *
 * The TextView is hidden when [clear] is called so it doesn't reserve layout
 * space when no character is talking.
 *
 * @param textView host view. The renderer assumes exclusive ownership of the
 *   text, visibility, and span styling
 * @param spokenColor   ARGB. Defaults to bright white
 * @param currentColor  ARGB. Defaults to gold
 * @param pendingColor  ARGB. Defaults to translucent white
 */
class KaraokeSubtitleRenderer @JvmOverloads constructor(
    private val textView: TextView,
    private val spokenColor:  Int = Color.WHITE,
    private val currentColor: Int = Color.parseColor("#FFD700"),
    private val pendingColor: Int = Color.parseColor("#77FFFFFF")
) : SubtitleRenderer {

    private var currentText: String = ""

    override fun show(text: String) {
        currentText = text
        textView.visibility = View.VISIBLE
        val span = SpannableString(text)
        span.setSpan(
            ForegroundColorSpan(pendingColor),
            0, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        textView.text = span
    }

    override fun highlight(start: Int, end: Int) {
        val text = currentText
        if (text.isEmpty()) return
        val safeEnd   = end.coerceAtMost(text.length)
        val safeStart = start.coerceAtMost(safeEnd)
        val span = SpannableString(text)

        // Spoken portion
        if (safeStart > 0) {
            span.setSpan(
                ForegroundColorSpan(spokenColor),
                0, safeStart, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        // Current word
        span.setSpan(
            ForegroundColorSpan(currentColor),
            safeStart, safeEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        span.setSpan(
            StyleSpan(Typeface.BOLD),
            safeStart, safeEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        // Pending portion
        if (safeEnd < text.length) {
            span.setSpan(
                ForegroundColorSpan(pendingColor),
                safeEnd, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        textView.text = span
    }

    override fun clear() {
        currentText = ""
        textView.text = ""
        textView.visibility = View.GONE
    }
}
