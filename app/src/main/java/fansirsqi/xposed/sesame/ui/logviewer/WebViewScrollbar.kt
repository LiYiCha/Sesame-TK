package fansirsqi.xposed.sesame.ui.logviewer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import android.webkit.WebView

/**
 * WebView 自定义可拖拽滚动条（原生 View 实现，解决 Compose overlay 触摸事件被拦截问题）
 */
class WebViewScrollbar(context: Context) : View(context) {

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x25FFFFFF.toInt()
        strokeWidth = 3f
    }
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x70FFFFFF.toInt()
    }
    private var webView: WebView? = null
    private var scrollFraction = 0f
    private var barHeight = 0f
    private var isDragging = false

    fun attachWebView(wv: WebView) {
        webView = wv
        wv.setOnScrollChangeListener { _, _, scrollY, _, _ ->
            val contentH = (wv.contentHeight * wv.scale).toFloat()
            val viewH = wv.height.toFloat()
            val maxScroll = contentH - viewH
            scrollFraction = if (maxScroll > 0) (scrollY / maxScroll).coerceIn(0f, 1f) else 0f
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        barHeight = (h * 0.15f).coerceAtLeast(36f)
        val trackX = w / 2
        val barX = trackX - 6f
        val barY = scrollFraction * (h - barHeight)
        val barPxW = 12f
        val barAlpha = if (isDragging) 200 else 120
        barPaint.alpha = barAlpha

        // 轨道
        canvas.drawLine(trackX, 0f, trackX, h, trackPaint)
        // 滑块
        canvas.drawRoundRect(barX, barY, barX + barPxW, barY + barHeight, 6f, 6f, barPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val wv = webView ?: return false
        val contentH = (wv.contentHeight * wv.scale).toFloat()
        val viewH = wv.height.toFloat()
        val maxScroll = contentH - viewH
        if (maxScroll <= 0) return false

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                isDragging = true
                scrollFraction = (event.y / height).coerceIn(0f, 1f)
                wv.scrollTo(0, (scrollFraction * maxScroll).toInt().coerceIn(0, maxScroll.toInt()))
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                scrollFraction = (event.y / height).coerceIn(0f, 1f)
                wv.scrollTo(0, (scrollFraction * maxScroll).toInt().coerceIn(0, maxScroll.toInt()))
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isDragging = false
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
