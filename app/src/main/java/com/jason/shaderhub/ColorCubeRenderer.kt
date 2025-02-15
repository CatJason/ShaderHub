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

class ColorCubeRenderer : GLSurfaceView.Renderer {
    private var program: Int = 0

    // 立方体的8个顶点
    private val vertices = floatArrayOf(
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

    // 每个顶点的颜色
    private val colors = floatArrayOf(
        1.0f, 0.0f, 0.0f, 1.0f,  // 红色
        0.0f, 1.0f, 0.0f, 1.0f,  // 绿色
        0.0f, 0.0f, 1.0f, 1.0f,  // 蓝色
        1.0f, 1.0f, 0.0f, 1.0f,  // 黄色
        1.0f, 0.0f, 1.0f, 1.0f,  // 紫色
        0.0f, 1.0f, 1.0f, 1.0f,  // 青色
        1.0f, 1.0f, 1.0f, 1.0f,  // 白色
        0.0f, 0.0f, 0.0f, 1.0f   // 黑色
    )

    // 立方体的索引数据，表示12个三角形
    private val indices = shortArrayOf(
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

    private var vertexBuffer: FloatBuffer? = null
    private var colorBuffer: FloatBuffer? = null
    private var indexBuffer: ShortBuffer? = null

    // 旋转矩阵
    private val rotationMatrix = FloatArray(16)
    private val projectionMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)

    init {
        // 使用 allocateDirect 创建直接缓冲区
        vertexBuffer = ByteBuffer.allocateDirect(vertices.size * 4) // 4 bytes per float
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(vertices)
            .position(0) as FloatBuffer?

        colorBuffer = ByteBuffer.allocateDirect(colors.size * 4) // 4 bytes per float
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(colors)
            .position(0) as FloatBuffer?

        indexBuffer = ByteBuffer.allocateDirect(indices.size * 2) // 2 bytes per short
            .order(ByteOrder.nativeOrder())
            .asShortBuffer()
            .put(indices)
            .position(0) as ShortBuffer?
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        // 设置背景颜色为黑色
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)

        // 启用深度测试
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)

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

        // 绘制顶点
        val positionHandle = GLES20.glGetAttribLocation(program, "vPosition")
        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, vertexBuffer)
        GLES20.glEnableVertexAttribArray(positionHandle)

        // 绘制颜色
        val colorHandle = GLES20.glGetAttribLocation(program, "vColor")
        GLES20.glVertexAttribPointer(colorHandle, 4, GLES20.GL_FLOAT, false, 0, colorBuffer)
        GLES20.glEnableVertexAttribArray(colorHandle)

        // 绘制立方体
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, indices.size, GLES20.GL_UNSIGNED_SHORT, indexBuffer)

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