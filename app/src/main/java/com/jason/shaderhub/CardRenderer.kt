package com.jason.shaderhub

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.graphics.Color
import android.opengl.Matrix
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.ViewConfiguration
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.abs

class CardRenderer(context: Context) : GLSurfaceView.Renderer {
    companion object {
        var CARD_COUNT = 5
        private const val SCROLL_FRICTION = 0.95f
        private const val EDGE_SPRING = 0.3f
        private const val TOUCH_SLOP = 0.5f
    }

    // OpenGL 相关变量
    private lateinit var vertexBuffer: FloatBuffer
    private lateinit var colorBuffer: FloatBuffer
    private var mProgram = 0
    private val projectionMatrix = FloatArray(16)
    private val modelMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)

    // 屏幕参数
    private var screenWidth = 0
    private var screenHeight = 0
    private val density = context.resources.displayMetrics.density

    // 滚动参数
    private var translationX = 0f
    private var currentVelocity = 0f
    private var lastTouchX = 0f
    private var isDragging = false

    // 物理滚动工具
    private val minFlingVelocity = ViewConfiguration.get(context).scaledMinimumFlingVelocity
    private val maxFlingVelocity = ViewConfiguration.get(context).scaledMaximumFlingVelocity
    private val velocityTracker = VelocityTracker.obtain()
    private val scroller = android.widget.Scroller(context)

    // 顶点着色器
    private val vertexShaderCode = """
        uniform mat4 uMVPMatrix;
        attribute vec2 vPosition;
        attribute vec4 aColor;
        varying vec4 vColor;
        void main() {
            gl_Position = uMVPMatrix * vec4(vPosition, 0.0, 1.0);
            vColor = aColor;
        }
    """.trimIndent()

    // 片段着色器
    private val fragmentShaderCode = """
        precision mediump float;
        varying vec4 vColor;
        void main() {
            gl_FragColor = vColor;
        }
    """.trimIndent()

    override fun onSurfaceCreated(gl: GL10, config: EGLConfig) {
        GLES20.glClearColor(0.1f, 0.1f, 0.1f, 1.0f)
        initShaders()
        Matrix.setIdentityM(modelMatrix, 0)
    }

    override fun onSurfaceChanged(gl: GL10, width: Int, height: Int) {
        screenWidth = width
        screenHeight = height
        GLES20.glViewport(0, 0, width, height)
        Matrix.orthoM(projectionMatrix, 0, 0f, width.toFloat(), 0f, height.toFloat(), -1f, 1f)
        generateVertexData()
        generateColorData()
    }

    override fun onDrawFrame(gl: GL10) {
        updatePhysics()
        applyEdgeSpring()

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glUseProgram(mProgram)

        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, -translationX, 0f, 0f)
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, modelMatrix, 0)

        val matrixHandle = GLES20.glGetUniformLocation(mProgram, "uMVPMatrix")
        GLES20.glUniformMatrix4fv(matrixHandle, 1, false, mvpMatrix, 0)

        drawCards()
    }

    fun handleTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                scroller.forceFinished(true)
                lastTouchX = event.x
                velocityTracker.clear()
                velocityTracker.addMovement(event)
                isDragging = true
            }
            MotionEvent.ACTION_MOVE -> {
                val deltaX = (event.x - lastTouchX) * TOUCH_SLOP
                translationX -= deltaX * density
                translationX = translationX.coerceIn(0f, getMaxTranslation())
                lastTouchX = event.x
                velocityTracker.addMovement(event)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isDragging = false
                velocityTracker.computeCurrentVelocity(1000, maxFlingVelocity.toFloat())
                val velocityX = velocityTracker.xVelocity

                if (abs(velocityX) > minFlingVelocity) {
                    scroller.fling(
                        translationX.toInt(), 0,
                        (-velocityX).toInt(), 0,
                        0, getMaxTranslation().toInt(),
                        0, 0
                    )
                }
                velocityTracker.clear()
            }
        }
        return true
    }

    private fun updatePhysics() {
        if (!isDragging && !scroller.isFinished) {
            if (scroller.computeScrollOffset()) {
                translationX = scroller.currX.toFloat()
            } else {
                currentVelocity *= SCROLL_FRICTION
                translationX += currentVelocity * density
                translationX = translationX.coerceIn(0f, getMaxTranslation())
                if (abs(currentVelocity) < 0.5f) currentVelocity = 0f
            }
        }
    }

    private fun applyEdgeSpring() {
        val max = getMaxTranslation()
        when {
            translationX < 0 -> translationX -= translationX * EDGE_SPRING
            translationX > max -> translationX -= (translationX - max) * EDGE_SPRING
        }
    }

    private fun drawCards() {
        val positionHandle = GLES20.glGetAttribLocation(mProgram, "vPosition")
        val colorHandle = GLES20.glGetAttribLocation(mProgram, "aColor")

        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glEnableVertexAttribArray(colorHandle)

        repeat(CARD_COUNT) { i ->
            vertexBuffer.position(i * 8)
            GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer)

            colorBuffer.position(i * 16)
            GLES20.glVertexAttribPointer(colorHandle, 4, GLES20.GL_FLOAT, false, 0, colorBuffer)

            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        }

        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(colorHandle)
    }

    private fun generateVertexData() {
        val cardWidth = screenWidth * 4f / 5f
        val cardHeight = cardWidth * 16f / 9f
        val spacing = screenWidth * 1f / 10f
        val startX = spacing  // 左边距

        val vertices = FloatArray(CARD_COUNT * 8).apply {
            for (i in 0 until CARD_COUNT) {
                val left = startX + i * (cardWidth + spacing)
                val right = left + cardWidth
                val top = (screenHeight - cardHeight) / 2f
                val bottom = top + cardHeight

                val offset = i * 8
                this[offset] = left       // 左上x
                this[offset + 1] = top    // 左上y
                this[offset + 2] = left   // 左下x
                this[offset + 3] = bottom // 左下y
                this[offset + 4] = right  // 右上x
                this[offset + 5] = top    // 右上y
                this[offset + 6] = right // 右下x
                this[offset + 7] = bottom // 右下y
            }
        }

        vertexBuffer = ByteBuffer
            .allocateDirect(vertices.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply { put(vertices).position(0) }
    }

    private fun generateColorData() {
        val colors = FloatArray(CARD_COUNT * 16).apply {
            for (i in 0 until CARD_COUNT) {
                val hue = i * 360f / CARD_COUNT
                val color = Color.HSVToColor(floatArrayOf(hue, 0.8f, 0.8f))

                val red = Color.red(color) / 255f
                val green = Color.green(color) / 255f
                val blue = Color.blue(color) / 255f

                for (j in 0 until 4) {
                    val offset = i * 16 + j * 4
                    this[offset] = red
                    this[offset + 1] = green
                    this[offset + 2] = blue
                    this[offset + 3] = 1.0f
                }
            }
        }

        colorBuffer = ByteBuffer
            .allocateDirect(colors.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply { put(colors).position(0) }
    }

    private fun getMaxTranslation(): Float {
        val cardWidth = screenWidth * 4f / 5f
        val spacing = screenWidth * 1f / 10f
        return (CARD_COUNT * (cardWidth + spacing) - spacing - screenWidth).coerceAtLeast(0f)
    }

    private fun initShaders() {
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode)

        mProgram = GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, vertexShader)
            GLES20.glAttachShader(it, fragmentShader)
            GLES20.glLinkProgram(it)
        }
    }

    private fun loadShader(type: Int, shaderCode: String): Int {
        return GLES20.glCreateShader(type).also { shader ->
            GLES20.glShaderSource(shader, shaderCode)
            GLES20.glCompileShader(shader)
        }
    }
}