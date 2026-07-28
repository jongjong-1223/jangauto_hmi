package com.example.hmi

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.sin

/**
 * Advanced MapView that supports:
 * - Dual Mode (Full/Zoom 5m x 5m)
 * - Robot heading (Orientation)
 * - Velocity vector
 * - History trail
 * - Vector walls/obstacles
 */
class MapView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : View(context, attrs, defStyle) {

    data class Pt(val x: Float, val y: Float)

    // Configuration
    var isZoomMode = false

    // Data
    private var anchors: List<Pt> = emptyList()
    private var walls: List<List<Pt>> = emptyList()
    private var path: List<Pt> = emptyList()
    private var history: List<Pt> = emptyList()
    private var tag: Pt? = null
    private var tagOri = 0f // Degrees
    private var tagVel = 0f // m/s
    private var hasTag = false

    // Paints
    private val anchorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#5E35B1"); style = Paint.Style.STROKE; strokeWidth = 4f
    }
    private val wallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.GRAY; style = Paint.Style.STROKE; strokeWidth = 6f
    }
    private val pathPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1565C0"); style = Paint.Style.STROKE; strokeWidth = 5f
    }
    private val historyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.LTGRAY; style = Paint.Style.STROKE; strokeWidth = 3f
        pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)
    }
    private val tagPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2E7D32"); style = Paint.Style.FILL
    }
    private val velPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#C62828"); style = Paint.Style.STROKE; strokeWidth = 4f
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.DKGRAY; textSize = 28f
    }

    fun setMapData(anchors: List<Pt>, walls: List<List<Pt>>, path: List<Pt>) {
        this.anchors = anchors
        this.walls = walls
        this.path = path
        invalidate()
    }

    fun setRobotState(tag: Pt?, ori: Float, vel: Float, history: List<Pt>, hasTag: Boolean) {
        this.tag = tag
        this.tagOri = ori
        this.tagVel = vel
        this.history = history
        this.hasTag = hasTag
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.WHITE)

        val currentTag = tag
        if (!hasTag || currentTag == null) {
            canvas.drawText("Waiting for robot...", 24f, height / 2f, textPaint)
            return
        }

        // Calculate Scale and Offset
        val pad = 40f
        val w = width - 2 * pad
        val h = height - 2 * pad
        
        var minX: Float; var maxX: Float; var minY: Float; var maxY: Float
        val scale: Float; val offX: Float; val offY: Float

        if (isZoomMode) {
            // 5m x 5m centered on robot
            val zoomSize = 5f
            minX = currentTag.x - zoomSize/2f; maxX = currentTag.x + zoomSize/2f
            minY = currentTag.y - zoomSize/2f; maxY = currentTag.y + zoomSize/2f
            scale = minOf(w / zoomSize, h / zoomSize)
        } else {
            // Full map: auto-scale based on anchors, walls, and path
            val allPts = anchors + walls.flatten() + path + (tag ?: Pt(0f,0f))
            minX = allPts.minOfOrNull { it.x } ?: -5f; maxX = allPts.maxOfOrNull { it.x } ?: 5f
            minY = allPts.minOfOrNull { it.y } ?: -5f; maxY = allPts.maxOfOrNull { it.y } ?: 5f
            if (maxX - minX < 1f) { minX -= 1f; maxX += 1f }
            if (maxY - minY < 1f) { minY -= 1f; maxY += 1f }
            scale = minOf(w / (maxX - minX), h / (maxY - minY))
        }

        offX = pad + (w - scale * (maxX - minX)) / 2f
        offY = pad + (h - scale * (maxY - minY)) / 2f

        fun sx(x: Float) = offX + (x - minX) * scale
        fun sy(y: Float) = height - (offY + (y - minY) * scale)

        // 1. Draw Anchors
        for ((i, a) in anchors.withIndex()) {
            canvas.drawCircle(sx(a.x), sy(a.y), 8f, anchorPaint)
            canvas.drawText("A${i+1}", sx(a.x) + 10f, sy(a.y) - 10f, textPaint)
        }

        // 2. Draw Walls
        for (wall in walls) {
            if (wall.size < 2) continue
            for (i in 0 until wall.size - 1) {
                canvas.drawLine(sx(wall[i].x), sy(wall[i].y), sx(wall[i+1].x), sy(wall[i+1].y), wallPaint)
            }
        }

        // 3. Draw Global Path
        if (path.size >= 2) {
            for (i in 0 until path.size - 1) {
                canvas.drawLine(sx(path[i].x), sy(path[i].y), sx(path[i+1].x), sy(path[i+1].y), pathPaint)
            }
        }

        // 4. Draw History (Trail)
        if (history.size >= 2) {
            val hPath = android.graphics.Path()
            hPath.moveTo(sx(history[0].x), sy(history[0].y))
            for (i in 1 until history.size) {
                hPath.lineTo(sx(history[i].x), sy(history[i].y))
            }
            canvas.drawPath(hPath, historyPaint)
        }

        // 5. Draw Robot (Triangle)
        canvas.save()
        val tx = sx(currentTag.x)
        val ty = sy(currentTag.y)
        canvas.translate(tx, ty)
        canvas.rotate(-tagOri) // rotate needs negative for counter-clockwise in screen coords if Y is flipped

        val rPath = android.graphics.Path().apply {
            moveTo(20f, 0f)
            lineTo(-15f, -15f)
            lineTo(-15f, 15f)
            close()
        }
        canvas.drawPath(rPath, tagPaint)

        // 6. Draw Velocity Vector
        if (tagVel > 0.1f) {
            val velLen = tagVel * 50f // scale velocity line
            canvas.drawLine(0f, 0f, velLen, 0f, velPaint)
        }
        canvas.restore()

        // 7. Labels
        canvas.drawText("(${ "%.2f".format(currentTag.x) }, ${ "%.2f".format(currentTag.y) }) ${ "%.1f".format(tagOri) }°", 
            tx + 25f, ty + 10f, textPaint)
    }
}
