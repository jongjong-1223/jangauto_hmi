package com.example.hmi

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

class VelocityGraphView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : View(context, attrs, defStyle) {

    enum class GraphType { LINEAR, ANGULAR }

    private var history: List<CommandState.VelocityPoint> = emptyList()
    private var timeRangeMs = 90_000L
    private var graphType = GraphType.LINEAR

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#DEE2E6")
        strokeWidth = 2f
    }
    
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#495057")
        textSize = 36f // Increased font size
        typeface = Typeface.DEFAULT_BOLD
    }

    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#ADB5BD")
        textSize = 30f
    }

    fun setData(history: List<CommandState.VelocityPoint>, rangeMs: Long, type: GraphType) {
        this.history = history
        this.timeRangeMs = rangeMs
        this.graphType = type
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (history.isEmpty()) return

        val w = width.toFloat(); val h = height.toFloat()
        val padLeft = 120f // Increased for Y-axis labels
        val padRight = 50f
        val padTop = 80f
        val padBottom = 80f
        val graphW = w - padLeft - padRight
        val graphH = h - padTop - padBottom

        val now = System.currentTimeMillis()
        val visibleHistory = history.filter { now - it.timeMs <= timeRangeMs }
        if (visibleHistory.isEmpty()) return

        // Set color based on type
        linePaint.color = if (graphType == GraphType.LINEAR) Color.parseColor("#005EB8") else Color.parseColor("#D32F2F")

        // Max values for scaling
        val rawMax = visibleHistory.maxOfOrNull { pt ->
            if (graphType == GraphType.LINEAR) Math.abs(pt.linearVel)
            else Math.abs(Math.toDegrees(pt.angularVel.toDouble())).toFloat()
        } ?: 0f
        val maxVal = maxOf(1.0f, Math.ceil(rawMax.toDouble()).toFloat())

        // Draw Grids & Ticks (Time X-axis)
        val timeDivisions = 5
        for (i in 0..timeDivisions) {
            val x = padLeft + (i.toFloat() / timeDivisions) * graphW
            canvas.drawLine(x, padTop, x, h - padBottom, gridPaint)
            val sec = (timeRangeMs / 1000) - (i.toFloat() / timeDivisions * (timeRangeMs / 1000)).toInt()
            canvas.drawText("${sec}s", x - 25f, h - 30f, tickPaint)
        }

        // Draw Grids & Ticks (Value Y-axis)
        val yDivisions = 4
        for (i in -yDivisions..yDivisions) {
            val yVal = (i.toFloat() / yDivisions) * maxVal
            val y = padTop + graphH / 2f - (yVal / maxVal) * (graphH / 2f)
            
            canvas.drawLine(padLeft, y, w - padRight, y, if (i == 0) linePaint.apply { alpha = 100 } else gridPaint)
            if (i == 0) linePaint.alpha = 255 // Reset alpha
            
            val unit = if (graphType == GraphType.LINEAR) "" else "°"
            canvas.drawText("${"%.1f".format(yVal)}$unit", 10f, y + 10f, tickPaint)
        }

        // Draw Data Line
        val path = Path()
        visibleHistory.forEachIndexed { _, pt ->
            val x = w - padRight - ((now - pt.timeMs).toFloat() / timeRangeMs) * graphW
            if (x < padLeft) return@forEachIndexed

            val value = if (graphType == GraphType.LINEAR) pt.linearVel 
                        else Math.toDegrees(pt.angularVel.toDouble()).toFloat()
            
            val y = padTop + graphH / 2f - (value / maxVal) * (graphH / 2f)

            if (path.isEmpty) path.moveTo(x, y) else path.lineTo(x, y)
        }
        canvas.drawPath(path, linePaint)

        // Title
        val title = if (graphType == GraphType.LINEAR) "Linear Velocity (m/s)" else "Angular Velocity (deg/s)"
        canvas.drawText(title, padLeft, padTop - 25f, textPaint)
    }
}
