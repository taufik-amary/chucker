package com.chuckerteam.chucker.internal.ui.mock

import android.graphics.Rect
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.chuckerteam.chucker.R
import com.chuckerteam.chucker.internal.support.indicesOf

/**
 * Finds a text in the body of a mock, like the search of the Response tab: every match is
 * highlighted, and the current one is scrolled to. Closing the search selects the current match,
 * so that it can be edited right away.
 */
internal class MockBodySearch(
    private val body: EditText,
    private val scrollView: ScrollView,
    private val searchBar: android.view.View,
    private val summary: TextView,
) {
    private val matchColor = ContextCompat.getColor(body.context, R.color.chucker_background_span_color)
    private val currentColor = ContextCompat.getColor(body.context, R.color.chucker_search_current_span_color)
    private val spans = mutableListOf<BackgroundColorSpan>()

    private var query = ""
    private var matches: List<Int> = emptyList()
    private var current = -1

    /** Searches [newQuery] from the start of the body. */
    fun search(newQuery: String) {
        query = newQuery
        matches = if (newQuery.isEmpty()) emptyList() else body.text.indicesOf(newQuery)
        current = if (matches.isEmpty()) -1 else 0
        showCurrent()
    }

    fun next() = move(1)

    fun previous() = move(-1)

    private fun move(step: Int) {
        if (matches.isEmpty()) return
        current = (current + step + matches.size) % matches.size
        showCurrent()
    }

    /** Ends the search, leaving the current match selected in the body. */
    fun close() {
        clearHighlights()
        searchBar.isVisible = false
        val start = matches.getOrNull(current)
        if (start != null && start + query.length <= body.length()) {
            body.requestFocus()
            body.setSelection(start, start + query.length)
        }
        query = ""
        matches = emptyList()
        current = -1
    }

    private fun showCurrent() {
        clearHighlights()
        searchBar.isVisible = query.isNotEmpty()
        summary.text =
            if (matches.isEmpty()) {
                body.context.getString(R.string.chucker_mock_search_no_results)
            } else {
                body.context.getString(R.string.chucker_mock_search_results, current + 1, matches.size)
            }
        if (matches.isEmpty()) return

        // A short query can match a large body thousands of times, which the field couldn't draw quickly.
        matches.take(MAX_HIGHLIGHTS).forEach { highlight(it, matchColor) }
        val start = matches[current]
        highlight(start, currentColor)
        scrollTo(start)
    }

    private fun highlight(
        start: Int,
        color: Int,
    ) {
        val span = BackgroundColorSpan(color)
        body.text.setSpan(span, start, start + query.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        spans += span
    }

    private fun clearHighlights() {
        spans.forEach { body.text.removeSpan(it) }
        spans.clear()
    }

    /** Scrolls the line of [offset] to the upper part of the screen, below the search bar. */
    private fun scrollTo(offset: Int) {
        body.post {
            val layout = body.layout ?: return@post
            val bodyBounds = Rect()
            body.getDrawingRect(bodyBounds)
            scrollView.offsetDescendantRectToMyCoords(body, bodyBounds)
            val lineTop = layout.getLineTop(layout.getLineForOffset(offset))
            val y = bodyBounds.top + body.totalPaddingTop + lineTop - scrollView.height / SCROLL_MARGIN_RATIO
            scrollView.smoothScrollTo(0, y.coerceAtLeast(0))
        }
    }

    private companion object {
        const val MAX_HIGHLIGHTS = 500

        /** Keeps the current match a third of the way down, with some context above it. */
        const val SCROLL_MARGIN_RATIO = 3
    }
}
