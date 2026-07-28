package com.example.hmi

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot
import kotlin.math.min

class JoystickView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private var centerX = 0f
    private var centerY = 0f
    private var baseRadius = 0f
    private var hatRadius = 0f

    private var hatX = 0f
    private var hatY = 0f

    private val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.LTGRAY
        style = Paint.Style.FILL
        alpha = 150
    }

    private val hatPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#5E35B1") // Deep Purple
        style = Paint.Style.FILL
    }

    // Callback for X, Y movement (-1.0 to 1.0)
    var onMoveListener: ((Float, Float) -> Unit)? = null

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        centerX = w / 2f
        centerY = h / 2f
        baseRadius = min(w, h) / 3f
        hatRadius = baseRadius / 2.5f
        resetHat()
    }

    private fun resetHat() {
        hatX = centerX
        hatY = centerY
        invalidate()
        onMoveListener?.invoke(0f, 0f)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // Draw base
        canvas.drawCircle(centerX, centerY, baseRadius, basePaint)
        // Draw hat
        canvas.drawCircle(hatX, hatY, hatRadius, hatPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
            resetHat()
            return true
        }

        val dx = event.x - centerX
        val dy = event.y - centerY
        val distance = hypot(dx, dy)

        if (distance < baseRadius) {
            hatX = event.x
            hatY = event.y
        } else {
            // Constraint within base radius
            val ratio = baseRadius / distance
            hatX = centerX + dx * ratio
            hatY = centerY + dy * ratio
        }

        invalidate()

        // Calculate normalized values (-1.0 to 1.0)
        val normX = (hatX - centerX) / baseRadius
        val normY = (hatY - centerY) / baseRadius
        onMoveListener?.invoke(normX, normY)

        return true
    }
}
