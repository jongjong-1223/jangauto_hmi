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
    private val tagPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#005EB8"); style = Paint.Style.FILL
    }
    private val velPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#D32F2F"); style = Paint.Style.STROKE; strokeWidth = 4f
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#495057"); textSize = 24f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val robotPath = Path()
    private val trailPath = Path()

    fun setMapData(mapPoints: List<Pt>, obstacles: List<List<Pt>>, path: List<Pt>) {
        this.mapPoints = mapPoints
        this.obstacles = obstacles
        this.path = path
        invalidate()
    }

    fun setRobotState(tag: Pt?, ori: Float, vel: Float, history: List<Pt>, hasTag: Boolean) {
        this.tag = tag; this.tagOri = ori; this.tagVel = vel; this.history = history; this.hasTag = hasTag
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.parseColor("#F8F9FA")) // Off-white/Gray surface

        val currentTag = tag
        if (!hasTag || currentTag == null) return

        val pad = 40f
        val w = width - 2 * pad; val h = height - 2 * pad
        var minX: Float; var maxX: Float; var minY: Float; var maxY: Float
        val scale: Float; val offX: Float; val offY: Float

        if (isZoomMode) {
            val zoomSize = 5f
            minX = currentTag.x - zoomSize/2f; maxX = currentTag.x + zoomSize/2f
            minY = currentTag.y - zoomSize/2f; maxY = currentTag.y + zoomSize/2f
            scale = minOf(w / zoomSize, h / zoomSize)
        } else {
            val allPts = mapPoints + obstacles.flatten() + path + currentTag
            minX = (allPts.minOfOrNull { it.x } ?: -2f) - 1f; maxX = (allPts.maxOfOrNull { it.x } ?: 2f) + 1f
            minY = (allPts.minOfOrNull { it.y } ?: -2f) - 1f; maxY = (allPts.maxOfOrNull { it.y } ?: 2f) + 1f
            scale = minOf(w / (maxX - minX), h / (maxY - minY))
        }

        offX = pad + (w - scale * (maxX - minX)) / 2f
        offY = pad + (h - scale * (maxY - minY)) / 2f
        fun sx(x: Float) = offX + (x - minX) * scale
        fun sy(y: Float) = height - (offY + (y - minY) * scale)

        // Draw Path & History
        if (history.size >= 2) {
            trailPath.reset()
            trailPath.moveTo(sx(history[0].x), sy(history[0].y))
            for (i in 1 until history.size) trailPath.lineTo(sx(history[i].x), sy(history[i].y))
            canvas.drawPath(trailPath, historyPaint)
        }
        
        if (path.size >= 2) for (i in 0 until path.size - 1) canvas.drawLine(sx(path[i].x), sy(path[i].y), sx(path[i+1].x), sy(path[i+1].y), pathPaint)
        
        for (obs in obstacles) {
            if (obs.size < 2) continue
            for (i in 0 until obs.size - 1) canvas.drawLine(sx(obs[i].x), sy(obs[i].y), sx(obs[i+1].x), sy(obs[i+1].y), obstaclePaint)
        }

        for ((i, a) in mapPoints.withIndex()) {
            canvas.drawCircle(sx(a.x), sy(a.y), 6f, mapPaint)
            canvas.drawText("M${i+1}", sx(a.x) + 12f, sy(a.y) - 12f, textPaint)
        }

        // Draw Robot
        canvas.save()
        val tx = sx(currentTag.x); val ty = sy(currentTag.y)
        canvas.translate(tx, ty); canvas.rotate(-tagOri)
        
        robotPath.reset()
        robotPath.moveTo(22f, 0f); robotPath.lineTo(-18f, -16f); robotPath.lineTo(-18f, 16f); robotPath.close()
        canvas.drawPath(robotPath, tagPaint)
        
        if (tagVel > 0.05f) canvas.drawLine(0f, 0f, tagVel * 60f, 0f, velPaint)
        canvas.restore()

        canvas.drawText("(${ "%.1f".format(currentTag.x) }, ${ "%.1f".format(currentTag.y) })", tx + 30f, ty + 10f, textPaint)
    }
}
