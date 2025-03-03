package com.jason.shaderhub

import android.content.Context
import android.widget.Scroller
import android.view.VelocityTracker
import android.view.ViewConfiguration
import kotlin.math.abs

class PhysicsScroller(context: Context) {
    private val scroller = Scroller(context)
    private val minFlingVelocity = ViewConfiguration.get(context).scaledMinimumFlingVelocity
    private val maxFlingVelocity = ViewConfiguration.get(context).scaledMaximumFlingVelocity
    private var velocityTracker: VelocityTracker? = null
    private var lastX = 0f
    var isDragging = false

    fun startTracking(x: Float) {
        lastX = x
        velocityTracker = VelocityTracker.obtain().apply {
            addMovement(android.view.MotionEvent.obtain(0, 0, android.view.MotionEvent.ACTION_DOWN, x, 0f, 0))
        }
    }

    fun updateTracking(x: Float) {
        velocityTracker?.addMovement(android.view.MotionEvent.obtain(0, 0, android.view.MotionEvent.ACTION_MOVE, x, 0f, 0))
        lastX = x
    }

    fun endTracking(x: Float): Float {
        velocityTracker?.apply {
            addMovement(android.view.MotionEvent.obtain(0, 0, android.view.MotionEvent.ACTION_UP, x, 0f, 0))
            computeCurrentVelocity(1000, maxFlingVelocity.toFloat())
            val velocityX = xVelocity
            recycle()
            velocityTracker = null
            return if (abs(velocityX) > minFlingVelocity) velocityX else 0f
        }
        return 0f
    }

    fun fling(velocityX: Float, maxTranslation: Float) {
        scroller.fling(
            scroller.currX, 0,
            velocityX.toInt(), 0,
            -maxTranslation.toInt(), maxTranslation.toInt(),
            0, 0
        )
    }

    fun computeScrollOffset(): Float {
        return if (scroller.computeScrollOffset()) {
            (scroller.currX - scroller.startX).toFloat()
        } else 0f
    }

    fun isFlingFinished() = scroller.isFinished
}