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
    private var headlandCorners: List<List<Pt>> = emptyList()
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
        color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 4f
        pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)
    }
    private val workPathPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 6f
        pathEffect = DashPathEffect(floatArrayOf(12f, 8f), 0f)
    }
    private val turnPathPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 4f
        pathEffect = DashPathEffect(floatArrayOf(8f, 12f), 0f)
    }
    private val candidatePathPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#757575"); style = Paint.Style.STROKE; strokeWidth = 3f
        pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)
    }
    private val historyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 2f
        pathEffect = DashPathEffect(floatArrayOf(5f, 5f), 0f)
    }
    private val waypointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK; style = Paint.Style.FILL
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
        color = Color.parseColor("#80FF9800"); style = Paint.Style.FILL // Translucent Orange (Material Orange 500 with 50% Alpha)
    }
    private val startTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4CAF50"); textSize = 32f; typeface = Typeface.DEFAULT_BOLD
    }
    private val endTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F44336"); textSize = 32f; typeface = Typeface.DEFAULT_BOLD
    }

    private val robotPath = Path()
    private val trailPath = Path()

    fun setMapData(mapPoints: List<Pt>, obstacles: List<List<Pt>>, path: List<Pt>) {
        this.mapPoints = mapPoints
        this.obstacles = obstacles
        this.path = path
        invalidate()
    }

    fun setHeadlandCorners(corners: List<List<Pt>>) {
        this.headlandCorners = corners
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

        // 1. Calculate Bounds and Scale
        val currentTag = if (hasTag) tag else null
        
        val points = mutableListOf<Pt>()
        points.addAll(mapPoints)
        obstacles.forEach { points.addAll(it) }
        path.forEach { points.add(it) }
        currentTag?.let { points.add(it) }
        singleCoveragePath?.let { scp -> 
            scp.waypoints.forEach { points.add(Pt(it.x.toFloat(), it.y.toFloat())) }
        }
        coveragePaths.forEach { cp -> 
            cp.waypoints.forEach { points.add(Pt(it.x.toFloat(), it.y.toFloat())) }
        }
        headlandCorners.forEach { corners ->
            corners.forEach { points.add(it) }
        }

        // If no robot tag and no map/path data, don't just exit, show message or empty state if needed
        // but user says "white screen" is bad. 
        // Let's at least calculate bounds if we have points.
        if (points.isEmpty() && currentTag == null) {
            // Draw something so user knows it's not crashed
            canvas.drawText("No Data to Display", width/2f - 100f, height/2f, textPaint)
            return
        }

        val pad = 120f
        val w = (width - 2 * pad).coerceAtLeast(100f)
        val h = (height - 2 * pad).coerceAtLeast(100f)
        
        val minX: Float; val maxX: Float; val minY: Float; val maxY: Float
        val scale: Float; val offX: Float; val offY: Float

        if (isZoomMode && currentTag != null) {
            val zoomSize = 5f
            minX = currentTag.x - zoomSize/2f; maxX = currentTag.x + zoomSize/2f
            minY = currentTag.y - zoomSize/2f; maxY = currentTag.y + zoomSize/2f
            scale = minOf(w / zoomSize, h / zoomSize)
        } else {
            val buffer = 3f
            minX = (points.minOfOrNull { it.x } ?: -2f) - buffer
            maxX = (points.maxOfOrNull { it.x } ?: 2f) + buffer
            minY = (points.minOfOrNull { it.y } ?: -2f) - buffer
            maxY = (points.maxOfOrNull { it.y } ?: 2f) + buffer
            val rangeX = maxX - minX; val rangeY = maxY - minY
            scale = if (rangeX > 0 && rangeY > 0) minOf(w / rangeY, h / rangeX) else 10f
        }

        offX = pad + (w - scale * (maxY - minY)) / 2f
        offY = pad + (h - scale * (maxX - minX)) / 2f
        
        fun sx(y: Float) = offX + (maxY - y) * scale
        fun sy(x: Float) = offY + (maxX - x) * scale

        // 2. Draw Layers
        val currentStep = getGridStep(minX, maxX, minY, maxY)
        drawGrids(canvas, minX, maxX, minY, maxY, currentStep, ::sx, ::sy)

        drawHeadlands(canvas, ::sx, ::sy)

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

        drawScaleAndAxis(canvas, scale, currentStep)
    }

    private fun drawScaleAndAxis(canvas: Canvas, scale: Float, step: Float) {
        val margin = 140f
        val bottomY = height - margin
        val leftX = margin
        val barWidth = step * scale
        canvas.drawLine(leftX, bottomY, leftX + barWidth, bottomY, scalePaint)
        canvas.drawLine(leftX, bottomY - 10f, leftX, bottomY + 10f, scalePaint)
        canvas.drawLine(leftX + barWidth, bottomY - 10f, leftX + barWidth, bottomY + 10f, scalePaint)
        val label = if (step >= 1f) "${step.toInt()}m" else "${ "%.1f".format(step) }m"
        canvas.drawText(label, leftX + barWidth / 2f - 30f, bottomY - 15f, robotInfoPaint)

        val axisLen = 80f
        val axisOriginX = leftX + 50f
        val axisOriginY = bottomY - 80f
        axisPaint.color = Color.RED
        drawArrow(canvas, axisOriginX, axisOriginY, axisOriginX, axisOriginY - axisLen, axisPaint)
        canvas.drawText("X", axisOriginX - 10f, axisOriginY - axisLen - 15f, robotInfoPaint)
        axisPaint.color = Color.parseColor("#2E7D32")
        drawArrow(canvas, axisOriginX, axisOriginY, axisOriginX - axisLen, axisOriginY, axisPaint)
        canvas.drawText("Y", axisOriginX - axisLen - 35f, axisOriginY + 10f, robotInfoPaint)
    }

    private fun drawArrow(canvas: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, paint: Paint) {
        canvas.drawLine(x1, y1, x2, y2, paint)
        val angle = Math.atan2((y2 - y1).toDouble(), (x2 - x1).toDouble())
        val headLen = 15f
        val headAngle = Math.PI / 6
        val x3 = (x2 - headLen * Math.cos(angle - headAngle)).toFloat()
        val y3 = (y2 - headLen * Math.sin(angle - headAngle)).toFloat()
        val x4 = (x2 - headLen * Math.cos(angle + headAngle)).toFloat()
        val y4 = (y2 - headLen * Math.sin(angle + headAngle)).toFloat()
        val arrowPath = Path()
        arrowPath.moveTo(x2, y2); arrowPath.lineTo(x3, y3); arrowPath.lineTo(x4, y4); arrowPath.close()
        val prevStyle = paint.style
        paint.style = Paint.Style.FILL
        canvas.drawPath(arrowPath, paint)
        paint.style = prevStyle
    }

    private fun getGridStep(minX: Float, maxX: Float, minY: Float, maxY: Float): Float {
        return if (isZoomMode) 1f else {
            val range = maxOf(maxX - minX, maxY - minY)
            when { range < 10f -> 1f; range < 50f -> 5f; else -> 10f }
        }
    }

    private fun drawGrids(canvas: Canvas, minX: Float, maxX: Float, minY: Float, maxY: Float, step: Float, sx: (Float) -> Float, sy: (Float) -> Float) {
        var yStart = (kotlin.math.ceil(minY.toDouble() / step) * step).toFloat()
        while (yStart <= maxY) {
            canvas.drawLine(sx(yStart), sy(minX), sx(yStart), sy(maxX), gridPaint)
            yStart += step
        }
        var xStart = (kotlin.math.ceil(minX.toDouble() / step) * step).toFloat()
        while (xStart <= maxX) {
            canvas.drawLine(sx(minY), sy(xStart), sx(maxY), sy(xStart), gridPaint)
            xStart += step
        }
    }

    private fun drawHeadlands(canvas: Canvas, sx: (Float) -> Float, sy: (Float) -> Float) {
        val polyPath = Path()
        for (cornerSet in headlandCorners) {
            if (cornerSet.size < 3) continue
            polyPath.reset()
            polyPath.moveTo(sx(cornerSet[0].y), sy(cornerSet[0].x))
            for (i in 1 until cornerSet.size) {
                polyPath.lineTo(sx(cornerSet[i].y), sy(cornerSet[i].x))
            }
            polyPath.close()
            canvas.drawPath(polyPath, headlandPaint)
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

        for (i in 0 until wpts.size - 1) {
            val p1 = wpts[i]; val p2 = wpts[i + 1]
            val currentPaint = if (isHighlighted) {
                if (p1.kind == "work_start" || p1.kind == "work_end") workPathPaint else turnPathPaint
            } else paint!!
            canvas.drawLine(sx(p1.y.toFloat()), sy(p1.x.toFloat()), sx(p2.y.toFloat()), sy(p2.x.toFloat()), currentPaint)
            canvas.drawCircle(sx(p1.y.toFloat()), sy(p1.x.toFloat()), 5f, waypointPaint)
        }
        if (wpts.isNotEmpty()) {
            val last = wpts.last()
            canvas.drawCircle(sx(last.y.toFloat()), sy(last.x.toFloat()), 5f, waypointPaint)
        }

        val start = wpts.first(); val end = wpts.last()
        val startX = sx(start.y.toFloat()); val startY = sy(start.x.toFloat())
        val endX = sx(end.y.toFloat()); val endY = sy(end.x.toFloat())

        canvas.drawCircle(startX, startY, 12f, startPaint)
        canvas.drawText("START", startX + 15f, startY - 15f, startTextPaint)
        canvas.drawCircle(endX, endY, 12f, endPaint)
        canvas.drawText("END", endX + 15f, endY - 15f, endTextPaint)
    }
}
