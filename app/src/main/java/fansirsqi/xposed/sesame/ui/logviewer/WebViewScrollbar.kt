package fansirsqi.xposed.sesame.ui.logviewer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import android.webkit.WebView

/**
 * WebView 自定义可拖拽滚动条（原生 View 实现）
 *
 * 核心特性：
 * 1. 动态绑定 Compose Theme 主题颜色，确保在浅色/深色模式及节日主题下均清晰可见
 * 2. ACTION_DOWN 时调用 requestDisallowInterceptTouchEvent(true) 防止 Compose 拦截拖拽
 * 3. 缓存 contentHeight 避免页面未加载完时 maxScroll=0 导致触摸无响应
 */
class WebViewScrollbar(context: Context) : View(context) {

    private val density = context.resources.displayMetrics.density

    private var primaryColor: Int = 0xFF2D5A27.toInt()
    private var onSurfaceColor: Int = 0xFF1F2328.toInt()

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var webView: WebView? = null
    private var scrollFraction = 0f
    private var barHeight = 0f
    private var isDragging = false

    /** 缓存的最大滚动值，防止 contentHeight 延迟返回导致触摸被拒绝 */
    private var cachedMaxScroll = 0f

    fun setColors(primary: Int, onSurface: Int) {
        if (this.primaryColor != primary || this.onSurfaceColor != onSurface) {
            this.primaryColor = primary
            this.onSurfaceColor = onSurface
            invalidate()
        }
    }

    fun attachWebView(wv: WebView) {
        webView = wv
        wv.setOnScrollChangeListener { _, _, scrollY, _, _ ->
            val canScrollDown = wv.canScrollVertically(1)
            val canScrollUp = wv.canScrollVertically(-1)

            if (!canScrollDown && scrollY > 0) {
                // 已经触底：精确将 cachedMaxScroll 矫正为真实 scrollY 并且 fraction 设为 1.0
                cachedMaxScroll = scrollY.toFloat()
                scrollFraction = 1.0f
            } else if (!canScrollUp || scrollY <= 0) {
                // 已经触顶：fraction 设为 0.0
                scrollFraction = 0.0f
            } else {
                val maxScroll = computeMaxScroll(wv)
                scrollFraction = if (maxScroll > 0) (scrollY.toFloat() / maxScroll).coerceIn(0f, 1f) else 0f
            }

            if (!isDragging) {
                invalidate()
            }
        }
    }

    private fun computeMaxScroll(wv: WebView): Float {
        @Suppress("DEPRECATION")
        val contentH = (wv.contentHeight * wv.scale)
        val viewH = wv.height.toFloat()
        val rawMax = (contentH - viewH).coerceAtLeast(1f)
        return if (cachedMaxScroll > 0f && kotlin.math.abs(cachedMaxScroll - rawMax) < rawMax * 0.4f) {
            cachedMaxScroll
        } else {
            rawMax
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val minThumbHeightPx = 40f * density
        barHeight = (h * 0.12f).coerceIn(minThumbHeightPx, h / 2f)

        val trackX = w / 2f

        // 绘制轨道（20% 透明度的 onSurface 色）
        trackPaint.color = (onSurfaceColor and 0x00FFFFFF) or 0x33000000
        trackPaint.strokeWidth = 3f * density
        canvas.drawLine(trackX, 0f, trackX, h, trackPaint)

        // 绘制滑块（主题 Primary 色，非拖拽 85% 不透明，拖拽 100% 不透明）
        val thumbWidthPx = (if (isDragging) 8f else 5f) * density
        val barX = trackX - (thumbWidthPx / 2f)
        val barY = scrollFraction * (h - barHeight)
        val thumbAlpha = if (isDragging) 0xFF else 0xD9 // 100% or 85%
        barPaint.color = (primaryColor and 0x00FFFFFF) or (thumbAlpha shl 24)

        val cornerRadius = 4f * density
        canvas.drawRoundRect(
            barX, barY, barX + thumbWidthPx, barY + barHeight,
            cornerRadius, cornerRadius, barPaint
        )
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val wv = webView ?: return false

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                // 关键：通知所有父 View 不要拦截后续触摸事件
                parent?.requestDisallowInterceptTouchEvent(true)
                isDragging = true

                // 重新计算 maxScroll
                applyScroll(wv, event.y)
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDragging) {
                    applyScroll(wv, event.y)
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isDragging = false
                parent?.requestDisallowInterceptTouchEvent(false)
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    /** 根据触摸 Y 坐标计算滚动位置并应用 */
    private fun applyScroll(wv: WebView, touchY: Float) {
        val maxScroll = computeMaxScroll(wv)
        if (maxScroll <= 0) return
        val fraction = (touchY / height).coerceIn(0f, 1f)
        scrollFraction = fraction

        if (fraction >= 0.98f) {
            // 拖到最底部：滚动超量值让 WebView 自行 Clamp 触底
            wv.scrollTo(0, (maxScroll * 1.5f).toInt())
        } else if (fraction <= 0.02f) {
            wv.scrollTo(0, 0)
        } else {
            wv.scrollTo(0, (fraction * maxScroll).toInt())
        }
    }
}


