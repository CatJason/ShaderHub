package com.jason.shaderhub

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class FogCubeTextureRenderer : GLSurfaceView.Renderer {
    private var program: Int = 0

    // 立方体的8个顶点
    private val cubeVertices = floatArrayOf(
        // 前面
        -0.5f,  0.5f,  0.5f,  // 前左上
        -0.5f, -0.5f,  0.5f,  // 前左下
        0.5f, -0.5f,  0.5f,  // 前右下
        0.5f,  0.5f,  0.5f,  // 前右上
        // 后面
        -0.5f,  0.5f, -0.5f,  // 后左上
        -0.5f, -0.5f, -0.5f,  // 后左下
        0.5f, -0.5f, -0.5f,  // 后右下
        0.5f,  0.5f, -0.5f   // 后右上
    )

    // 水的表面顶点（位于 y = 0）
    private val waterVertices = floatArrayOf(
        -0.5f, 0.0f,  0.5f,  // 前左
        0.5f, 0.0f,  0.5f,  // 前右
        0.5f, 0.0f, -0.5f,  // 后右
        -0.5f, 0.0f, -0.5f   // 后左
    )

    // 立方体的颜色（灰色）
    private val cubeColor = floatArrayOf(
        0.5f, 0.5f, 0.5f, 1.0f  // 灰色
    )

    // 水的颜色（半透明蓝色）
    private val waterColor = floatArrayOf(
        0.0f, 0.5f, 1.0f, 0.5f  // 半透明蓝色
    )

    // 立方体的索引数据
    private val cubeIndices = shortArrayOf(
        // 前面
        0, 1, 2, 0, 2, 3,
        // 后面
        4, 5, 6, 4, 6, 7,
        // 左面
        4, 0, 3, 4, 3, 7,
        // 右面
        1, 5, 6, 1, 6, 2,
        // 上面
        0, 4, 5, 0, 5, 1,
        // 下面
        3, 7, 6, 3, 6, 2
    )

    // 水的表面索引数据
    private val waterIndices = shortArrayOf(
        0, 1, 2, 0, 2, 3
    )

    private var cubeVertexBuffer: FloatBuffer? = null
    private var waterVertexBuffer: FloatBuffer? = null
    private var cubeColorBuffer: FloatBuffer? = null
    private var waterColorBuffer: FloatBuffer? = null
    private var cubeIndexBuffer: ShortBuffer? = null
    private var waterIndexBuffer: ShortBuffer? = null

    // 旋转矩阵
    private val rotationMatrix = FloatArray(16)
    private val projectionMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)

    init {
        // 使用 allocateDirect 创建直接缓冲区
        cubeVertexBuffer = ByteBuffer.allocateDirect(cubeVertices.size * 4) // 4 bytes per float
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(cubeVertices)
            .position(0) as FloatBuffer?

        waterVertexBuffer = ByteBuffer.allocateDirect(waterVertices.size * 4) // 4 bytes per float
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(waterVertices)
            .position(0) as FloatBuffer?

        cubeColorBuffer = ByteBuffer.allocateDirect(cubeColor.size * 4) // 4 bytes per float
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(cubeColor)
            .position(0) as FloatBuffer?

        waterColorBuffer = ByteBuffer.allocateDirect(waterColor.size * 4) // 4 bytes per float
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(waterColor)
            .position(0) as FloatBuffer?

        cubeIndexBuffer = ByteBuffer.allocateDirect(cubeIndices.size * 2) // 2 bytes per short
            .order(ByteOrder.nativeOrder())
            .asShortBuffer()
            .put(cubeIndices)
            .position(0) as ShortBuffer?

        waterIndexBuffer = ByteBuffer.allocateDirect(waterIndices.size * 2) // 2 bytes per short
            .order(ByteOrder.nativeOrder())
            .asShortBuffer()
            .put(waterIndices)
            .position(0) as ShortBuffer?
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        // 设置背景颜色为白色
        GLES20.glClearColor(1.0f, 1.0f, 1.0f, 1.0f)

        // 启用深度测试
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)

        // 启用混合（用于水的半透明效果）
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

        // 加载和编译着色器
        val vertexShaderCode = """
            attribute vec4 vPosition;
            attribute vec4 vColor;
            varying vec4 color;
            uniform mat4 uMVPMatrix;
            void main() {
                gl_Position = uMVPMatrix * vPosition;
                color = vColor;
            }
        """.trimIndent()

        val fragmentShaderCode = """
            precision mediump float;
            varying vec4 color;
            void main() {
                gl_FragColor = color;
            }
        """.trimIndent()

        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode)

        // 创建程序并链接着色器
        program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vertexShader)
        GLES20.glAttachShader(program, fragmentShader)
        GLES20.glLinkProgram(program)

        // 使用程序
        GLES20.glUseProgram(program)
    }

    override fun onDrawFrame(gl: GL10?) {
        // 清空画面和深度缓冲区
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        GLES20.glUseProgram(program)

        // 设置旋转角度
        val time = System.currentTimeMillis() % 10000L
        val angle = 360.0f * time.toFloat() / 10000.0f
        Matrix.setRotateM(rotationMatrix, 0, angle, 1.0f, 1.0f, 1.0f)

        // 设置相机位置
        Matrix.setLookAtM(viewMatrix, 0, 0f, 0f, 5f, 0f, 0f, 0f, 0f, 1.0f, 0.0f)

        // 计算 MVP 矩阵
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, viewMatrix, 0)
        Matrix.multiplyMM(mvpMatrix, 0, mvpMatrix, 0, rotationMatrix, 0)

        // 获取旋转矩阵的uniform位置并设置
        val mvpMatrixHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix")
        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0)

        // 绘制立方体
        val positionHandle = GLES20.glGetAttribLocation(program, "vPosition")
        val colorHandle = GLES20.glGetAttribLocation(program, "vColor")

        // 绘制立方体
        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, cubeVertexBuffer)
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(colorHandle, 4, GLES20.GL_FLOAT, false, 0, cubeColorBuffer)
        GLES20.glEnableVertexAttribArray(colorHandle)
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, cubeIndices.size, GLES20.GL_UNSIGNED_SHORT, cubeIndexBuffer)

        // 绘制水的表面
        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, waterVertexBuffer)
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(colorHandle, 4, GLES20.GL_FLOAT, false, 0, waterColorBuffer)
        GLES20.glEnableVertexAttribArray(colorHandle)
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, waterIndices.size, GLES20.GL_UNSIGNED_SHORT, waterIndexBuffer)

        // 禁用属性数组
        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(colorHandle)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        // 设置视口大小
        GLES20.glViewport(0, 0, width, height)

        // 设置投影矩阵
        val ratio = width.toFloat() / height.toFloat()
        Matrix.frustumM(projectionMatrix, 0, -ratio, ratio, -1f, 1f, 3f, 7f)
    }

    // 加载着色器
    private fun loadShader(type: Int, shaderCode: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, shaderCode)
        GLES20.glCompileShader(shader)
        return shader
    }
}