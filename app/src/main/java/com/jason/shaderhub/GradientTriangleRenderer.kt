package com.jason.shaderhub

import android.opengl.GLES20
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class GradientTriangleRenderer : IRender {
    private lateinit var vertexBuffer: FloatBuffer
    private lateinit var colorBuffer: FloatBuffer
    private var program = 0

    // Matrix 相关
    private val vPMatrix = FloatArray(16)
    private val projectionMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val rotationMatrix = FloatArray(16)
    private val modelMatrix = FloatArray(16)

    // 旋转参数
    private var angleX = 0f
    private var angleY = 0f

    // 顶点着色器
    private val vertexShaderCode = """
        uniform mat4 uMVPMatrix;
        attribute vec4 vPosition;
        attribute vec4 aColor;
        varying vec4 vColor;
        void main() {
            gl_Position = uMVPMatrix * vPosition;
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

    // 顶点坐标（三角形）
    private val triangleCoords = floatArrayOf(
        -0.5f, -0.5f, 0.0f,  // 左下
        0.5f, -0.5f, 0.0f,   // 右下
        0.0f, 0.5f, 0.0f     // 顶部
    )

    // 顶点颜色（RGBA）
    private val colors = floatArrayOf(
        1.0f, 0.0f, 0.0f, 1.0f,  // 左下：红色
        0.0f, 1.0f, 0.0f, 1.0f,  // 右下：绿色
        0.0f, 0.0f, 1.0f, 1.0f   // 顶部：蓝色
    )

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.1f, 0.1f, 0.1f, 1.0f)
        initBuffers()
        createShaderProgram()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        setupProjection(width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        calculateVPMatrix()
        drawTriangle()
    }

    override fun rotate(deltaX: Float, deltaY: Float) {
        angleY += deltaX * ROTATION_FACTOR
        angleX += deltaY * ROTATION_FACTOR
    }

    private fun initBuffers() {
        // 初始化顶点缓冲区
        vertexBuffer = ByteBuffer
            .allocateDirect(triangleCoords.size * 4)
            .apply { order(ByteOrder.nativeOrder()) }
            .asFloatBuffer()
            .apply {
                put(triangleCoords)
                position(0)
            }

        // 初始化颜色缓冲区
        colorBuffer = ByteBuffer
            .allocateDirect(colors.size * 4)
            .apply { order(ByteOrder.nativeOrder()) }
            .asFloatBuffer()
            .apply {
                put(colors)
                position(0)
            }
    }

    private fun createShaderProgram() {
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode)

        program = GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, vertexShader)
            GLES20.glAttachShader(it, fragmentShader)
            GLES20.glLinkProgram(it)
        }
    }

    private fun setupProjection(width: Int, height: Int) {
        val ratio = width.toFloat() / height
        // 使用正交投影
        Matrix.orthoM(projectionMatrix, 0, -ratio, ratio, -1f, 1f, 1f, 10f)
        // 调整相机位置到更远处
        Matrix.setLookAtM(viewMatrix, 0,
            0f, 0f, 5f,   // 相机位置
            0f, 0f, 0f,   // 观察点
            0f, 1f, 0f    // 上方向
        )
    }

    private fun calculateVPMatrix() {
        // 初始化模型矩阵
        Matrix.setIdentityM(modelMatrix, 0)

        // 应用旋转（先X轴后Y轴）
        Matrix.rotateM(modelMatrix, 0, angleX, 1f, 0f, 0f)
        Matrix.rotateM(modelMatrix, 0, angleY, 0f, 1f, 0f)

        // 合并矩阵顺序：projection * view * model
        val tempMatrix = FloatArray(16)
        Matrix.multiplyMM(tempMatrix, 0, viewMatrix, 0, modelMatrix, 0)
        Matrix.multiplyMM(vPMatrix, 0, projectionMatrix, 0, tempMatrix, 0)
    }

    private fun drawTriangle() {
        GLES20.glUseProgram(program)

        val positionHandle = GLES20.glGetAttribLocation(program, "vPosition").also {
            GLES20.glEnableVertexAttribArray(it)
            GLES20.glVertexAttribPointer(
                it, 3,
                GLES20.GL_FLOAT, false,
                3 * 4, vertexBuffer
            )
        }

        val colorHandle = GLES20.glGetAttribLocation(program, "aColor").also {
            GLES20.glEnableVertexAttribArray(it)
            GLES20.glVertexAttribPointer(
                it, 4,
                GLES20.GL_FLOAT, false,
                4 * 4, colorBuffer
            )
        }

        GLES20.glGetUniformLocation(program, "uMVPMatrix").also {
            GLES20.glUniformMatrix4fv(it, 1, false, vPMatrix, 0)
        }

        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 3)

        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(colorHandle)
    }

    private fun loadShader(type: Int, shaderCode: String): Int {
        return GLES20.glCreateShader(type).also { shader ->
            GLES20.glShaderSource(shader, shaderCode)
            GLES20.glCompileShader(shader)

            val compileStatus = IntArray(1)
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0)
            if (compileStatus[0] == 0) {
                GLES20.glDeleteShader(shader)
                throw RuntimeException("Shader compile error: ${GLES20.glGetShaderInfoLog(shader)}")
            }
        }
    }

    companion object {
        private const val ROTATION_FACTOR = 0.5f
    }
}