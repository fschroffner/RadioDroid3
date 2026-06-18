package net.programmierecke.radiodroid2.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.ReplacementSpan
import android.util.AttributeSet
import android.view.View
import androidx.appcompat.widget.AppCompatTextView
import net.programmierecke.radiodroid2.R

class TagsView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : AppCompatTextView(context, attrs, defStyle) {

    interface TagSelectionCallback { fun onTagSelected(tag: String) }

    private inner class RoundedBackgroundSpan(
        private val mHeight: Int, private val mCornerRadius: Int,
        private val mTextHorizontalPadding: Int, private val mTextVerticalMargin: Int,
        private val mBackgroundColor: Int, private val mTextColor: Int
    ) : ReplacementSpan() {
        override fun draw(canvas: Canvas, text: CharSequence, start: Int, end: Int, x: Float, top: Int, y: Int, bottom: Int, paint: Paint) {
            var t = top + (bottom - top) / 2 - mHeight / 2
            val b = t + mHeight
            val fm = paint.fontMetrics
            val adjustedY = t + mHeight / 2f + (-fm.top - fm.bottom) / 2f
            val rect = RectF(x, t.toFloat(), x + measureText(paint, text, start, end) + 2 * mTextHorizontalPadding, b.toFloat())
            paint.color = mBackgroundColor
            canvas.drawRoundRect(rect, mCornerRadius.toFloat(), mCornerRadius.toFloat(), paint)
            paint.color = mTextColor
            canvas.drawText(text, start, end, x + mTextHorizontalPadding, adjustedY, paint)
        }

        override fun getSize(paint: Paint, text: CharSequence, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int {
            fm?.let {
                paint.getFontMetricsInt(it)
                val textHeight = it.descent - it.ascent
                val space = (mHeight - textHeight) / 2
                val topOfContent = minOf(it.top, it.top - space)
                val bottomOfContent = maxOf(it.bottom, it.bottom + space)
                it.ascent = topOfContent - mTextVerticalMargin
                it.descent = bottomOfContent + mTextVerticalMargin
                it.top = it.ascent; it.bottom = it.descent
            }
            return Math.round(paint.measureText(text, start, end)) + mTextHorizontalPadding * 2
        }

        private fun measureText(paint: Paint, text: CharSequence, start: Int, end: Int) = paint.measureText(text, start, end)
    }

    private var mTagBackgroundColor = Color.RED
    private var mCornerRadius = 16
    private var mTagHeight = 20
    private var mTextHorizontalPadding = 8
    private var mTextVerticalMargin = 4
    var mTagSelectionCallback: TagSelectionCallback? = null

    init {
        attrs?.let {
            val a = context.obtainStyledAttributes(it, R.styleable.TagsView, defStyle, 0)
            mTagBackgroundColor = a.getColor(R.styleable.TagsView_tagBackgroundColor, mTagBackgroundColor)
            mCornerRadius = a.getDimensionPixelSize(R.styleable.TagsView_cornerRadius, mCornerRadius)
            mTagHeight = a.getDimensionPixelSize(R.styleable.TagsView_tagHeight, mTagHeight)
            mTextHorizontalPadding = a.getDimensionPixelSize(R.styleable.TagsView_textHorizontalPadding, mTextHorizontalPadding)
            mTextVerticalMargin = a.getDimensionPixelSize(R.styleable.TagsView_textVerticalMargin, mTextVerticalMargin)
            a.recycle()
        }
    }

    fun setTags(tags: List<String>) {
        val sb = SpannableStringBuilder()
        val spacing = "  "
        for (tag in tags) {
            val tagWithSpace = tag + spacing
            sb.append(tagWithSpace)
            val start = sb.length - tagWithSpace.length
            val end = sb.length - spacing.length
            sb.setSpan(RoundedBackgroundSpan(mTagHeight, mCornerRadius, mTextHorizontalPadding, mTextVerticalMargin, mTagBackgroundColor, currentTextColor), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.setSpan(object : ClickableSpan() {
                override fun onClick(view: View) { mTagSelectionCallback?.onTagSelected(tag) }
            }, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        text = sb
        movementMethod = LinkMovementMethod.getInstance()
    }
}
