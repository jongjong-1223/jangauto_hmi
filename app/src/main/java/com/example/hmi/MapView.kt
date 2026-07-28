package com.example.hmi

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.sin

class MapView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : View(context, attrs, defStyle) {

    data class Pt(val x: Float, val y: Float)

    var isZoomMode = false

    private var anchors: List<Pt> = emptyList()
    private var walls: List<List<Pt>> = emptyList()
    private var path: List<Pt> = emptyList()
    private var history: List<Pt> = emptyList()
    private var tag: Pt? = null
    private var tagOri = 0f
    private var tagVel = 0f
    private var hasTag = false

    private val anchorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#005EB8"); style = Paint.Style.STROKE; strokeWidth = 3f
    }
    private val wallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
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

    fun setMapData(anchors: List<Pt>, walls: List<List<Pt>>, path: List<Pt>) {
        this.anchors = anchors
        this.walls = walls
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
            val allPts = anchors + walls.flatten() + path + currentTag
            minX = (allPts.minOfOrNull { it.x } ?: -2f) - 1f; maxX = (allPts.maxOfOrNull { it.x } ?: 2f) + 1f
            minY = (allPts.minOfOrNull { it.y } ?: -2f) - 1f; maxY = (allPts.maxOfOrNull { it.y } ?: 2f) + 1f
            scale = minOf(w / (maxX - minX), h / (maxY - minY))
        }

        offX = pad + (w - scale * (maxX - minX)) / 2f
        offY = pad + (h - scale * (maxY - minY)) / 2f
        fun sx(x: Float) = offX + (x - minX) * scale
        fun sy(y: Float) = height - (offY + (y - minY) * scale)

        // Draw Path & History
        val drawLine = { p1: Pt, p2: Pt, paint: Paint -> canvas.drawLine(sx(p1.x), sy(p1.y), sx(p2.x), sy(p2.y), paint) }
        
        if (history.size >= 2) for (i in 0 until history.size - 1) drawLine(history[i], history[i+1], historyPaint)
        if (path.size >= 2) for (i in 0 until path.size - 1) drawLine(path[i], path[i+1], pathPaint)
        for (wall in walls) if (wall.size >= 2) for (i in 0 until wall.size - 1) drawLine(wall[i], wall[i+1], wallPaint)
        for ((i, a) in anchors.withIndex()) {
            canvas.drawCircle(sx(a.x), sy(a.y), 6f, anchorPaint)
            canvas.drawText("A${i+1}", sx(a.x) + 12f, sy(a.y) - 12f, textPaint)
        }

        // Draw Robot
        canvas.save()
        val tx = sx(currentTag.x); val ty = sy(currentTag.y)
        canvas.translate(tx, ty); canvas.rotate(-tagOri)
        val rPath = Path().apply { moveTo(22f, 0f); lineTo(-18f, -16f); lineTo(-18f, 16f); close() }
        canvas.drawPath(rPath, tagPaint)
        if (tagVel > 0.05f) canvas.drawLine(0f, 0f, tagVel * 60f, 0f, velPaint)
        canvas.restore()

        canvas.drawText("(${ "%.1f".format(currentTag.x) }, ${ "%.1f".format(currentTag.y) })", tx + 30f, ty + 10f, textPaint)
    }
}
