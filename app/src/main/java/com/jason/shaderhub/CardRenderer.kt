package com.jason.shaderhub

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.graphics.Color
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class CardRenderer : GLSurfaceView.Renderer {
    companion object {
        var CARD_COUNT = 5
    }

    private lateinit var vertexBuffer: FloatBuffer
    private lateinit var colorBuffer: FloatBuffer
    private var mProgram = 0
    private val projectionMatrix = FloatArray(16)
    private var screenWidth = 0
    private var screenHeight = 0
    private var translationX = 0f // 横向平移量

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

    // 速度因子，用于控制滑动速度
    private val speedFactor = 0.5f

    // 当前滑动速度
    private var currentVelocity = 0f

    // 最大滑动速度
    private val maxVelocity = 30f

    // 更新平移量
    fun updateTranslation(deltaX: Float) {
        // 根据速度因子调整滑动速度
        currentVelocity = (deltaX * speedFactor).coerceIn(-maxVelocity, maxVelocity)
        translationX = (translationX - currentVelocity).coerceIn(0f, getMaxTranslation())
    }

    private fun getMaxTranslation(): Float {
        if (screenWidth == 0) return 0f
        val cardWidth = screenWidth * 4f / 5f
        val spacing = screenWidth * 1f / 10f
        val totalWidth = CARD_COUNT * cardWidth + (CARD_COUNT - 1) * spacing
        return (totalWidth - screenWidth).coerceAtLeast(0f)
    }

    override fun onSurfaceCreated(gl: GL10, config: EGLConfig) {
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
        initShaders()
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
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glUseProgram(mProgram)

        // 创建模型矩阵并应用平移
        val modelMatrix = FloatArray(16).apply {
            Matrix.setIdentityM(this, 0)
            Matrix.translateM(this, 0, -translationX, 0f, 0f)
        }

        // 组合矩阵
        val mvpMatrix = FloatArray(16).apply {
            Matrix.multiplyMM(this, 0, projectionMatrix, 0, modelMatrix, 0)
        }

        // 传递矩阵到Shader
        val matrixHandle = GLES20.glGetUniformLocation(mProgram, "uMVPMatrix")
        GLES20.glUniformMatrix4fv(matrixHandle, 1, false, mvpMatrix, 0)

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
        // 卡片宽度为屏幕宽度的 4/5
        val cardWidth = screenWidth * 4f / 5f

        // 卡片高度为宽度的 16/9
        val cardHeight = cardWidth * 16f / 9f

        // 卡片之间的间距为屏幕宽度的 1/10
        val spacing = screenWidth * 1f / 10f

        // 第一张卡片距离左边的间距为屏幕宽度的 1/10
        val startX = spacing

        // 生成顶点数据
        val vertices = FloatArray(CARD_COUNT * 8).apply {
            for (i in 0 until CARD_COUNT) {
                val left = startX + i * (cardWidth + spacing)
                val right = left + cardWidth
                val top = (screenHeight - cardHeight) / 2f // 垂直居中
                val bottom = top + cardHeight

                val offset = i * 8
                // 顶点顺序：左上 → 左下 → 右上 → 右下
                this[offset] = left
                this[offset + 1] = top
                this[offset + 2] = left
                this[offset + 3] = bottom
                this[offset + 4] = right
                this[offset + 5] = top
                this[offset + 6] = right
                this[offset + 7] = bottom
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
                val color = Color.HSVToColor(floatArrayOf(hue, 1f, 1f))

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