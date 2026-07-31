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
    private var coveragePaths: List<com.example.hmi.model.CoveragePath> = emptyList()
    private var selectedPathIndex: Int = -1
    private var history: List<Pt> = emptyList()
    private var singleCoveragePath: com.example.hmi.model.CoveragePath? = null
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
    private val workPathPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1B5E20"); style = Paint.Style.STROKE; strokeWidth = 6f // Dark Green for work
    }
    private val turnPathPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#81C784"); style = Paint.Style.STROKE; strokeWidth = 4f
        pathEffect = DashPathEffect(floatArrayOf(10f, 5f), 0f)
    }
    private val candidatePathPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#BDBDBD"); style = Paint.Style.STROKE; strokeWidth = 3f
    }
    private val historyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#D32F2F"); style = Paint.Style.STROKE; strokeWidth = 2f
        pathEffect = DashPathEffect(floatArrayOf(8f, 8f), 0f)
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#CED4DA"); style = Paint.Style.STROKE; strokeWidth = 1f
    }
    private val tagPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#005EB8"); style = Paint.Style.FILL
    }
    private val velPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#D32F2F"); style = Paint.Style.STROKE; strokeWidth = 4f
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#495057"); textSize = 48f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val robotInfoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#005EB8"); textSize = 40f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    }
    private val scalePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 3f
    }
    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 4f; strokeCap = Paint.Cap.ROUND
    }
    private val startPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4CAF50"); style = Paint.Style.FILL // Green
    }
    private val endPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F44336"); style = Paint.Style.FILL // Red
    }
    private val headlandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#20FFEB3B"); style = Paint.Style.FILL // Very translucent Yellow
    }

    private val robotPath = Path()
    private val trailPath = Path()

    fun setMapData(mapPoints: List<Pt>, obstacles: List<List<Pt>>, path: List<Pt>) {
        this.mapPoints = mapPoints
        this.obstacles = obstacles
        this.path = path
        invalidate()
    }

    fun setCoveragePaths(paths: List<com.example.hmi.model.CoveragePath>, selectedIndex: Int) {
        this.coveragePaths = paths
        this.selectedPathIndex = selectedIndex
        this.singleCoveragePath = null
        invalidate()
    }

    fun setSingleCoveragePath(path: com.example.hmi.model.CoveragePath?) {
        this.singleCoveragePath = path
        this.coveragePaths = emptyList()
        invalidate()
    }

    fun setRobotState(tag: Pt?, ori: Float, vel: Float, yawRate: Float, history: List<Pt>, hasTag: Boolean) {
        this.tag = tag; this.tagOri = ori; this.tagVel = vel; this.tagYawRate = yawRate; this.history = history; this.hasTag = hasTag
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.parseColor("#F8F9FA")) // Off-white/Gray surface

        // 1. Calculate Bounds
        val currentTag = if (hasTag) tag else null
        
        val allPointsForBounds = mutableListOf<Pt>()
        allPointsForBounds.addAll(mapPoints)
        obstacles.forEach { allPointsForBounds.addAll(it) }
        allPointsForBounds.addAll(path)
        currentTag?.let { allPointsForBounds.add(it) }
        coveragePaths.forEach { cp -> allPointsForBounds.addAll(cp.waypoints.map { Pt(it.x.toFloat(), it.y.toFloat()) }) }
        singleCoveragePath?.let { cp -> allPointsForBounds.addAll(cp.waypoints.map { Pt(it.x.toFloat(), it.y.toFloat()) }) }

        if (allPointsForBounds.isEmpty() && currentTag == null) return

        val pad = 120f
        val w = width - 2 * pad
        val h = height - 2 * pad
        
        val minX: Float; val maxX: Float; val minY: Float; val maxY: Float
        val scale: Float; val offX: Float; val offY: Float

        if (isZoomMode && currentTag != null) {
            val zoomSize = 5f
            minX = currentTag.x - zoomSize/2f; maxX = currentTag.x + zoomSize/2f
            minY = currentTag.y - zoomSize/2f; maxY = currentTag.y + zoomSize/2f
            scale = minOf(w / zoomSize, h / zoomSize)
        } else {
            val buffer = 3f
            minX = (allPointsForBounds.minOfOrNull { it.x } ?: -2f) - buffer
            maxX = (allPointsForBounds.maxOfOrNull { it.x } ?: 2f) + buffer
            minY = (allPointsForBounds.minOfOrNull { it.y } ?: -2f) - buffer
            maxY = (allPointsForBounds.maxOfOrNull { it.y } ?: 2f) + buffer
            val rangeX = maxX - minX; val rangeY = maxY - minY
            scale = if (rangeX > 0 && rangeY > 0) minOf(w / rangeY, h / rangeX) else 10f
        }

        offX = pad + (w - scale * (maxY - minY)) / 2f
        offY = pad + (h - scale * (maxX - minX)) / 2f
        
        fun sx(y: Float) = offX + (maxY - y) * scale
        fun sy(x: Float) = offY + (maxX - x) * scale

        // 2. Draw Layers
        drawGrids(canvas, minX, maxX, minY, maxY, getGridStep(minX, maxX, minY, maxY), ::sx, ::sy)

        if (history.size >= 2) {
            trailPath.reset()
            trailPath.moveTo(sx(history[0].y), sy(history[0].x))
            for (i in 1 until history.size) trailPath.lineTo(sx(history[i].y), sy(history[i].x))
            canvas.drawPath(trailPath, historyPaint)
        }
        
        if (path.size >= 2) for (i in 0 until path.size - 1) canvas.drawLine(sx(path[i].y), sy(path[i].x), sx(path[i+1].y), sy(path[i+1].x), pathPaint)
        
        drawCoveragePaths(canvas, ::sx, ::sy)
        drawSingleCoveragePath(canvas, ::sx, ::sy)

        for (obs in obstacles) {
            if (obs.size < 2) continue
            for (i in obs.indices) {
                val p1 = obs[i]; val p2 = obs[(i + 1) % obs.size]
                canvas.drawLine(sx(p1.y), sy(p1.x), sx(p2.y), sy(p2.x), obstaclePaint)
            }
        }

        if (mapPoints.isNotEmpty()) {
            for (i in mapPoints.indices) {
                val p1 = mapPoints[i]; val p2 = mapPoints[(i + 1) % mapPoints.size]
                canvas.drawLine(sx(p1.y), sy(p1.x), sx(p2.y), sy(p2.x), mapPaint)
            }
        }

        for ((i, a) in mapPoints.withIndex()) {
            val cx = sx(a.y); val cy = sy(a.x)
            canvas.drawCircle(cx, cy, 6f, mapPaint)
            canvas.drawText("M${i+1}", cx + 15f, cy - 15f, textPaint)
            canvas.drawText("(${ "%.1f".format(a.x) }, ${ "%.1f".format(a.y) })", cx + 15f, cy + 35f, robotInfoPaint)
        }

        // 3. Draw Robot (Only if tag exists)
        currentTag?.let { tag ->
            canvas.save()
            val tx = sx(tag.y); val ty = sy(tag.x)
            canvas.translate(tx, ty)
            val deg = Math.toDegrees(tagOri.toDouble()).toFloat()
            canvas.rotate(-deg)
            
            robotPath.reset()
            robotPath.moveTo(0f, -22f); robotPath.lineTo(-16f, 18f); robotPath.lineTo(16f, 18f); robotPath.close()
            canvas.drawPath(robotPath, tagPaint)
            
            if (tagVel > 0.05f) canvas.drawLine(0f, 0f, 0f, -tagVel * 60f, velPaint)
            canvas.restore()

            val infoX = tx + 45f
            canvas.drawText("(${ "%.1f".format(tag.x) }, ${ "%.1f".format(tag.y) })", infoX, ty, textPaint)
            canvas.drawText("${ "%.2f".format(tagVel) } m/s", infoX, ty + 50f, robotInfoPaint)
            canvas.drawText("${ "%.1f".format(Math.toDegrees(tagYawRate.toDouble())) } deg/s", infoX, ty + 90f, robotInfoPaint)
        }

        drawScaleAndAxis(canvas, scale, getGridStep(minX, maxX, minY, maxY))
    }

    private fun drawScaleAndAxis(canvas: Canvas, scale: Float, step: Float) {
        val margin = 140f
        val bottomY = height - margin
        val leftX = margin

        // 1. Draw Scale (Dynamic based on step)
        val barWidth = step * scale
        canvas.drawLine(leftX, bottomY, leftX + barWidth, bottomY, scalePaint)
        canvas.drawLine(leftX, bottomY - 10f, leftX, bottomY + 10f, scalePaint)
        canvas.drawLine(leftX + barWidth, bottomY - 10f, leftX + barWidth, bottomY + 10f, scalePaint)
        
        val label = if (step >= 1f) "${step.toInt()}m" else "${ "%.1f".format(step) }m"
        canvas.drawText(label, leftX + barWidth / 2f - 30f, bottomY - 15f, robotInfoPaint)

        // 2. Draw 2D Axis Arrows (X up, Y left)
        val axisLen = 80f
        val axisOriginX = leftX + 50f
        val axisOriginY = bottomY - 80f
        
        // X-axis (Red, pointing UP on screen)
        axisPaint.color = Color.RED
        drawArrow(canvas, axisOriginX, axisOriginY, axisOriginX, axisOriginY - axisLen, axisPaint)
        canvas.drawText("X", axisOriginX - 10f, axisOriginY - axisLen - 15f, robotInfoPaint)
        
        // Y-axis (Green, pointing LEFT on screen)
        axisPaint.color = Color.parseColor("#2E7D32")
        drawArrow(canvas, axisOriginX, axisOriginY, axisOriginX - axisLen, axisOriginY, axisPaint)
        canvas.drawText("Y", axisOriginX - axisLen - 35f, axisOriginY + 10f, robotInfoPaint)
    }

    private fun drawArrow(canvas: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, paint: Paint) {
        // Draw the main line
        canvas.drawLine(x1, y1, x2, y2, paint)

        // Calculate arrow head points
        val angle = Math.atan2((y2 - y1).toDouble(), (x2 - x1).toDouble())
        val headLen = 15f
        val headAngle = Math.PI / 6 // 30 degrees
        
        val x3 = (x2 - headLen * Math.cos(angle - headAngle)).toFloat()
        val y3 = (y2 - headLen * Math.sin(angle - headAngle)).toFloat()
        val x4 = (x2 - headLen * Math.cos(angle + headAngle)).toFloat()
        val y4 = (y2 - headLen * Math.sin(angle + headAngle)).toFloat()

        val arrowPath = Path()
        arrowPath.moveTo(x2, y2)
        arrowPath.lineTo(x3, y3)
        arrowPath.lineTo(x4, y4)
        arrowPath.close()
        
        val prevStyle = paint.style
        paint.style = Paint.Style.FILL
        canvas.drawPath(arrowPath, paint)
        paint.style = prevStyle
    }

    private fun getGridStep(minX: Float, maxX: Float, minY: Float, maxY: Float): Float {
        return if (isZoomMode) 1f else {
            val range = maxOf(maxX - minX, maxY - minY)
            when {
                range < 10f -> 1f
                range < 50f -> 5f
                else -> 10f
            }
        }
    }

    private fun drawGrids(canvas: Canvas, minX: Float, maxX: Float, minY: Float, maxY: Float, step: Float, sx: (Float) -> Float, sy: (Float) -> Float) {
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

    private fun drawCoveragePaths(canvas: Canvas, sx: (Float) -> Float, sy: (Float) -> Float) {
        coveragePaths.forEachIndexed { index, covPath ->
            renderPathWithDetails(canvas, covPath, index == selectedPathIndex, sx, sy)
        }
    }

    private fun drawSingleCoveragePath(canvas: Canvas, sx: (Float) -> Float, sy: (Float) -> Float) {
        singleCoveragePath?.let { renderPathWithDetails(canvas, it, true, sx, sy) }
    }

    private fun renderPathWithDetails(canvas: Canvas, covPath: com.example.hmi.model.CoveragePath, isHighlighted: Boolean, sx: (Float) -> Float, sy: (Float) -> Float) {
        val paint = if (isHighlighted) null else candidatePathPaint
        val wpts = covPath.waypoints
        if (wpts.size < 2) return

        // Draw segments
        for (i in 0 until wpts.size - 1) {
            val p1 = wpts[i]; val p2 = wpts[i + 1]
            val currentPaint = if (isHighlighted) {
                if (p1.kind == "work_start" || p1.kind == "work_end") workPathPaint else turnPathPaint
            } else paint!!
            canvas.drawLine(sx(p1.y.toFloat()), sy(p1.x.toFloat()), sx(p2.y.toFloat()), sy(p2.x.toFloat()), currentPaint)
            
            // Draw headland areas (simple box around turn points)
            if (isHighlighted && (p1.kind == "turn_out" || p1.kind == "turn_in")) {
                canvas.drawCircle(sx(p1.y.toFloat()), sy(p1.x.toFloat()), 25f, headlandPaint)
            }
        }

        // Draw Start/End Markers
        val start = wpts.first()
        val end = wpts.last()
        canvas.drawCircle(sx(start.y.toFloat()), sy(start.x.toFloat()), 12f, startPaint)
        canvas.drawCircle(sx(end.y.toFloat()), sy(end.x.toFloat()), 12f, endPaint)
    }
}
