package com.example.hmi

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

class LatencyGraphView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private val data = mutableListOf<Long>()
    private val maxPoints = 50
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#005EB8") // UOS Blue
        style = Paint.Style.STROKE
        strokeWidth = 6f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#DEE2E6")
        strokeWidth = 2f
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#6C757D")
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

        val w = width.toFloat(); val h = height.toFloat()
        val pad = 50f
        val maxLatency = maxOf(150L, (data.maxOrNull() ?: 0L) + 50L)
        val hScale = (h - 2 * pad) / maxLatency
        val xStep = (w - 2 * pad) / (maxPoints - 1)

        // Grid
        for (i in 0..3) {
            val yVal = i * 50f
            val y = h - pad - (yVal * hScale)
            canvas.drawLine(pad, y, w - pad, y, gridPaint)
            canvas.drawText("${yVal.toInt()}ms", 5f, y + 10f, textPaint)
        }

        // Smooth Path
        val path = Path()
        val fillPath = Path()
        
        for ((i, ping) in data.withIndex()) {
            val x = pad + i * xStep
            val y = h - pad - (ping * hScale)
            if (i == 0) {
                path.moveTo(x, y)
                fillPath.moveTo(x, h - pad)
                fillPath.lineTo(x, y)
            } else {
                path.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
            if (i == data.size - 1) {
                fillPath.lineTo(x, h - pad)
                fillPath.close()
            }
        }

        // Gradient Fill
        fillPaint.shader = LinearGradient(0f, pad, 0f, h - pad, 
            Color.parseColor("#40005EB8"), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        
        canvas.drawPath(fillPath, fillPaint)
        canvas.drawPath(path, linePaint)
    }
}
