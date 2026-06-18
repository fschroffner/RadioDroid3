package net.programmierecke.radiodroid2.views

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.OverScroller
import androidx.core.view.ViewCompat
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class RecyclerAwareNestedScrollView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : NestedScrollView(context, attrs, defStyleAttr) {

    private val mScroller: OverScroller? = getOverScroller()
    var isFling = false

    override fun fling(velocityY: Int) {
        super.fling(velocityY)
        if (childCount > 0) {
            ViewCompat.postInvalidateOnAnimation(this)
            isFling = true
        }
    }

    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt)
        if (isFling) {
            if (Math.abs(t - oldt) <= 3 || t == 0 || t == (getChildAt(0).measuredHeight - measuredHeight)) {
                isFling = false
                mScroller?.abortAnimation()
            }
        }
    }

    override fun onNestedPreScroll(target: View, dx: Int, dy: Int, consumed: IntArray, type: Int) {
        val rv = target as RecyclerView
        if ((dy < 0 && isRvScrolledToTop(rv)) || (dy > 0 && !isNsvScrolledToBottom(this))) {
            scrollBy(0, dy)
            consumed[1] = dy
            return
        }
        super.onNestedPreScroll(target, dx, dy, consumed, type)
    }

    override fun onNestedPreFling(target: View, velX: Float, velY: Float): Boolean {
        val rv = target as RecyclerView
        if ((velY < 0 && isRvScrolledToTop(rv)) || (velY > 0 && !isNsvScrolledToBottom(this))) {
            fling(velY.toInt())
            return true
        }
        return super.onNestedPreFling(target, velX, velY)
    }

    private fun getOverScroller(): OverScroller? = try {
        val fs = this.javaClass.superclass.getDeclaredField("mScroller").also { it.isAccessible = true }
        fs.get(this) as OverScroller
    } catch (_: Throwable) { null }

    companion object {
        private fun isNsvScrolledToBottom(nsv: NestedScrollView) = !nsv.canScrollVertically(1)
        private fun isRvScrolledToTop(rv: RecyclerView): Boolean {
            val lm = rv.layoutManager as LinearLayoutManager
            return lm.findFirstVisibleItemPosition() == 0 && lm.findViewByPosition(0)?.top == 0
        }
    }
}
