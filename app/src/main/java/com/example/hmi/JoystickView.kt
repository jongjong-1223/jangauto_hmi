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
        color = Color.parseColor("#E9ECEF")
        style = Paint.Style.FILL
    }

    private val hatPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#005EB8") // UOS Blue
        style = Paint.Style.FILL
        setShadowLayer(10f, 0f, 4f, Color.parseColor("#40000000"))
    }

    var onMoveListener: ((Float, Float) -> Unit)? = null

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null) // Required for shadows on some versions
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        centerX = w / 2f
        centerY = h / 2f
        baseRadius = min(w, h) / 2.5f
        hatRadius = baseRadius / 2.2f
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
        canvas.drawCircle(centerX, centerY, baseRadius, basePaint)
        canvas.drawCircle(hatX, hatY, hatRadius, hatPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
            resetHat()
            performClick()
            return true
        }

        val dx = event.x - centerX
        val dy = event.y - centerY
        val distance = hypot(dx, dy)

        if (distance < baseRadius) {
            hatX = event.x
            hatY = event.y
        } else {
            val ratio = baseRadius / distance
            hatX = centerX + dx * ratio
            hatY = centerY + dy * ratio
        }

        invalidate()

        val normX = (hatX - centerX) / baseRadius
        val normY = (hatY - centerY) / baseRadius
        onMoveListener?.invoke(normX, normY)

        return true
    }

    override fun performClick(): Boolean {
        return super.performClick()
    }
}
