package com.example.hmi

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

class MapView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : View(context, attrs, defStyle) {

    data class Pt(val x: Float, val y: Float)

    var isZoomMode = false

    private var mapPoints: List<Pt> = emptyList()
    private var obstacles: List<List<Pt>> = emptyList()
    private var path: List<Pt> = emptyList()
    private var history: List<Pt> = emptyList()
    private var tag: Pt? = null
    private var tagOri = 0f
    private var tagVel = 0f
    private var tagYawRate = 0f
    private var hasTag = false

    private val mapPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#005EB8"); style = Paint.Style.STROKE; strokeWidth = 3f
    }
    private val obstaclePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#343A40"); style = Paint.Style.STROKE; strokeWidth = 5f; strokeCap = Paint.Cap.ROUND
    }
    private val pathPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#0077C8"); style = Paint.Style.STROKE; strokeWidth = 4f
    }
    private val historyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#ADB5BD"); style = Paint.Style.STROKE; strokeWidth = 2f
        pathEffect = DashPathEffect(floatArrayOf(8f, 8f), 0f)
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E9ECEF"); style = Paint.Style.STROKE; strokeWidth = 1f
    }
    private val tagPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#005EB8"); style = Paint.Style.FILL
    }
    private val velPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#D32F2F"); style = Paint.Style.STROKE; strokeWidth = 4f
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#495057"); textSize = 24f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val robotInfoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#005EB8"); textSize = 20f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    }

    private val robotPath = Path()
    private val trailPath = Path()

    fun setMapData(mapPoints: List<Pt>, obstacles: List<List<Pt>>, path: List<Pt>) {
        this.mapPoints = mapPoints
        this.obstacles = obstacles
        this.path = path
        invalidate()
    }

    fun setRobotState(tag: Pt?, ori: Float, vel: Float, yawRate: Float, history: List<Pt>, hasTag: Boolean) {
        this.tag = tag; this.tagOri = ori; this.tagVel = vel; this.tagYawRate = yawRate; this.history = history; this.hasTag = hasTag
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.parseColor("#F8F9FA")) // Off-white/Gray surface

        val currentTag = tag
        if (!hasTag || currentTag == null) return

        val pad = 40f
        val w = width - 2 * pad
        val h = height - 2 * pad
        
        // Robot coordinate system: X is UP, Y is LEFT
        // Canvas: X is Right, Y is Down
        // So: Canvas X corresponds to Robot -Y, Canvas Y corresponds to Robot -X
        
        val minX: Float
        val maxX: Float
        val minY: Float
        val maxY: Float
        val scale: Float
        val offX: Float
        val offY: Float

        if (isZoomMode) {
            val zoomSize = 8f
            // In Robot frame
            minX = tag!!.x - zoomSize/2f
            maxX = tag!!.x + zoomSize/2f
            minY = tag!!.y - zoomSize/2f
            maxY = tag!!.y + zoomSize/2f
            scale = minOf(w / zoomSize, h / zoomSize)
        } else {
            val allPts = mapPoints + obstacles.flatten() + path + tag!!
            minX = (allPts.minOfOrNull { it.x } ?: -2f) - 1f
            maxX = (allPts.maxOfOrNull { it.x } ?: 2f) + 1f
            minY = (allPts.minOfOrNull { it.y } ?: -2f) - 1f
            maxY = (allPts.maxOfOrNull { it.y } ?: 2f) + 1f
            val rangeX = maxX - minX
            val rangeY = maxY - minY
            scale = minOf(w / rangeY, h / rangeX) // Swapped range because of orientation
        }

        // Translation to center
        offX = pad + (w - scale * (maxY - minY)) / 2f
        offY = pad + (h - scale * (maxX - minX)) / 2f
        
        // Transform functions:
        // sx maps Robot Y to Canvas X (Y_robot + -> Canvas Left -> X_canvas decreases)
        // Robot Y max -> Canvas Left, Robot Y min -> Canvas Right
        fun sx(y: Float) = offX + (maxY - y) * scale
        // sy maps Robot X to Canvas Y (X_robot + -> Canvas Top -> Y_canvas decreases)
        // Robot X max -> Canvas Top, Robot X min -> Canvas Bottom
        fun sy(x: Float) = offY + (maxX - x) * scale

        // Draw Grids
        drawGrids(canvas, minX, maxX, minY, maxY, ::sx, ::sy)

        // Draw History
        if (history.size >= 2) {
            trailPath.reset()
            trailPath.moveTo(sx(history[0].y), sy(history[0].x))
            for (i in 1 until history.size) trailPath.lineTo(sx(history[i].y), sy(history[i].x))
            canvas.drawPath(trailPath, historyPaint)
        }
        
        if (path.size >= 2) for (i in 0 until path.size - 1) canvas.drawLine(sx(path[i].y), sy(path[i].x), sx(path[i+1].y), sy(path[i+1].x), pathPaint)
        
        for (obs in obstacles) {
            if (obs.size < 2) continue
            for (i in 0 until obs.size - 1) canvas.drawLine(sx(obs[i].y), sy(obs[i].x), sx(obs[i+1].y), sy(obs[i+1].x), obstaclePaint)
        }

        // Map Loop
        if (mapPoints.isNotEmpty()) {
            for (i in mapPoints.indices) {
                val p1 = mapPoints[i]
                val p2 = mapPoints[(i + 1) % mapPoints.size]
                canvas.drawLine(sx(p1.y), sy(p1.x), sx(p2.y), sy(p2.x), mapPaint)
            }
        }

        for ((i, a) in mapPoints.withIndex()) {
            canvas.drawCircle(sx(a.y), sy(a.x), 6f, mapPaint)
            canvas.drawText("M${i+1}", sx(a.y) + 12f, sy(a.x) - 12f, textPaint)
        }

        // Draw Robot
        canvas.save()
        val tx = sx(currentTag.y); val ty = sy(currentTag.x)
        canvas.translate(tx, ty)
        // Orientation: Robot X is 0 deg (Up). 
        // tagOri is CCW radian from X axis.
        // Canvas rotate uses degrees CW. 
        // So rotate by -Math.toDegrees(tagOri)
        val deg = Math.toDegrees(tagOri.toDouble()).toFloat()
        canvas.rotate(-deg)
        
        robotPath.reset()
        // Define triangle pointing UP (in local frame where X is Up)
        // Note: Canvas local frame after translate/rotate
        robotPath.moveTo(0f, -22f) 
        robotPath.lineTo(-16f, 18f)
        robotPath.lineTo(16f, 18f)
        robotPath.close()
        canvas.drawPath(robotPath, tagPaint)
        
        if (tagVel > 0.05f) {
            // Velocity vector line pointing UP
            canvas.drawLine(0f, 0f, 0f, -tagVel * 60f, velPaint)
        }
        canvas.restore()

        // Robot Status Info
        val infoX = tx + 35f
        canvas.drawText("(${ "%.1f".format(currentTag.x) }, ${ "%.1f".format(currentTag.y) })", infoX, ty, textPaint)
        canvas.drawText("${ "%.2f".format(tagVel) } m/s", infoX, ty + 30f, robotInfoPaint)
        canvas.drawText("${ "%.1f".format(Math.toDegrees(tagYawRate.toDouble())) } deg/s", infoX, ty + 55f, robotInfoPaint)
    }

    private fun drawGrids(canvas: Canvas, minX: Float, maxX: Float, minY: Float, maxY: Float, sx: (Float) -> Float, sy: (Float) -> Float) {
        val step = if (isZoomMode) 1f else {
            val range = maxOf(maxX - minX, maxY - minY)
            when {
                range < 10f -> 1f
                range < 50f -> 5f
                else -> 10f
            }
        }
        
        // Vertical lines (constant Robot Y)
        var yStart = (kotlin.math.ceil(minY.toDouble() / step) * step).toFloat()
        while (yStart <= maxY) {
            canvas.drawLine(sx(yStart), sy(minX), sx(yStart), sy(maxX), gridPaint)
            yStart += step
        }
        
        // Horizontal lines (constant Robot X)
        var xStart = (kotlin.math.ceil(minX.toDouble() / step) * step).toFloat()
        while (xStart <= maxX) {
            canvas.drawLine(sx(minY), sy(xStart), sx(maxY), sy(xStart), gridPaint)
            xStart += step
        }
    }
}
