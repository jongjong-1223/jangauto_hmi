package com.example.hmi

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

/**
 * Draws (in world XY coordinates, auto-scaled to fit the view, Y pointing up):
 *  - the 4 UWB anchors connected as a quadrilateral "map"
 *  - the global path polyline + waypoints
 *  - the current tag (robot) position as a dot, with its coordinate label
 */
class MapView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : View(context, attrs, defStyle) {

    data class Pt(val x: Float, val y: Float)

    private var anchors: List<Pt> = emptyList()
    private var path: List<Pt> = emptyList()
    private var tag: Pt? = null
    private var hasTag = false

    private val anchorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#5E35B1"); style = Paint.Style.STROKE; strokeWidth = 4f
    }
    private val anchorDot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#5E35B1"); style = Paint.Style.FILL
    }
    private val pathPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1565C0"); style = Paint.Style.STROKE; strokeWidth = 5f
    }
    private val wpDot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#D32F2F"); style = Paint.Style.FILL
    }
    private val tagPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2E7D32"); style = Paint.Style.FILL
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.DKGRAY; textSize = 30f
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E0E0E0"); style = Paint.Style.STROKE; strokeWidth = 1f
    }

    fun setData(anchors: List<Pt>, path: List<Pt>, tag: Pt?, hasTag: Boolean) {
        this.anchors = anchors
        this.path = path
        this.tag = tag
        this.hasTag = hasTag
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.WHITE)

        val pts = ArrayList<Pt>()
        pts.addAll(anchors)
        pts.addAll(path)
        if (hasTag) tag?.let { pts.add(it) }

        if (pts.size < 2) {
            canvas.drawText("데이터 대기 중...", 24f, height / 2f, textPaint)
            return
        }

        var minX = pts.minOf { it.x }; var maxX = pts.maxOf { it.x }
        var minY = pts.minOf { it.y }; var maxY = pts.maxOf { it.y }
        if (maxX - minX < 1e-3f) { minX -= 1f; maxX += 1f }
        if (maxY - minY < 1e-3f) { minY -= 1f; maxY += 1f }

        val pad = 60f
        val w = width - 2 * pad
        val h = height - 2 * pad
        val scale = minOf(w / (maxX - minX), h / (maxY - minY))
        // center the content
        val offX = pad + (w - scale * (maxX - minX)) / 2f
        val offY = pad + (h - scale * (maxY - minY)) / 2f

        fun sx(x: Float) = offX + (x - minX) * scale
        fun sy(y: Float) = height - (offY + (y - minY) * scale)  // flip Y so up is up

        // anchors -> closed quadrilateral
        if (anchors.size >= 2) {
            for (i in anchors.indices) {
                val a = anchors[i]
                val b = anchors[(i + 1) % anchors.size]
                canvas.drawLine(sx(a.x), sy(a.y), sx(b.x), sy(b.y), anchorPaint)
            }
            for ((i, a) in anchors.withIndex()) {
                canvas.drawCircle(sx(a.x), sy(a.y), 9f, anchorDot)
                canvas.drawText("A${i + 1}", sx(a.x) + 12f, sy(a.y) - 12f, textPaint)
            }
        }

        // global path polyline + waypoints
        if (path.size >= 2) {
            for (i in 0 until path.size - 1) {
                canvas.drawLine(sx(path[i].x), sy(path[i].y),
                    sx(path[i + 1].x), sy(path[i + 1].y), pathPaint)
            }
        }
        for (p in path) canvas.drawCircle(sx(p.x), sy(p.y), 6f, wpDot)

        // current position
        if (hasTag) tag?.let { t ->
            canvas.drawCircle(sx(t.x), sy(t.y), 14f, tagPaint)
            canvas.drawText("(${"%.2f".format(t.x)}, ${"%.2f".format(t.y)})",
                sx(t.x) + 18f, sy(t.y) + 10f, textPaint)
        }
    }
}
