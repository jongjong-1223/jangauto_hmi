package com.example.hmi

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

/**
 * Custom View to draw real-time Latency (Ping) graph.
 */
class LatencyGraphView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private val data = mutableListOf<Long>()
    private val maxPoints = 60 // Show 30 seconds if 2Hz
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4CAF50")
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E0E0E0")
        strokeWidth = 2f
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.GRAY
        textSize = 24f
    }

    fun addData(ping: Long) {
        data.add(ping)
        if (data.size > maxPoints) data.removeAt(0)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (data.isEmpty()) return

        val w = width.toFloat()
        val h = height.toFloat()
        val padding = 40f
        
        // Draw Y axis labels (0ms, 100ms, 200ms)
        val maxLatency = maxOf(200L, data.maxOrNull() ?: 0L)
        val hScale = (h - 2 * padding) / maxLatency

        for (i in 0..2) {
            val yVal = i * 100f
            val y = h - padding - (yVal * hScale)
            canvas.drawLine(padding, y, w - padding, y, gridPaint)
            canvas.drawText("${yVal.toInt()}ms", 5f, y + 10f, textPaint)
        }

        // Draw Line
        val xStep = (w - 2 * padding) / (maxPoints - 1)
        val path = Path()
        
        for ((i, ping) in data.withIndex()) {
            val x = padding + i * xStep
            val y = h - padding - (ping * hScale)
            if (i == 0) path.moveTo(x, y)
            else path.lineTo(x, y)
        }
        
        canvas.drawPath(path, linePaint)
    }
}
