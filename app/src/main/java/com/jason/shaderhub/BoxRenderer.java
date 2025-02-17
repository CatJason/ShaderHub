package com.jason.shaderhub;

import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class BoxRenderer implements GLSurfaceView.Renderer {

    private float[] projectionMatrix = new float[16];
    private float[] viewMatrix = new float[16];
    private float[] modelMatrix = new float[16];

    private int shaderProgram;
    private int positionHandle;
    private int normalHandle;
    private int mvpMatrixHandle;
    private int boxXHandle;
    private int boxYHandle;
    private int boxZHandle;

    private static final float box_x = 2.0f;   // 盒子宽度
    private static final float box_y = 1.0f;   // 盒子高度
    private static final float box_z = 2.0f;   // 盒子深度

    // 旋转角度
    private float rotationX = 0f;
    private float rotationY = 0f;

    public BoxRenderer() {
        // 构造器，初始化参数
    }

    // 旋转方法
    public void rotate(float dx, float dy) {
        rotationY += dx;
        rotationX -= dy;
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        // 清屏颜色设置
        GLES20.glClearColor(1.0f, 1.0f, 1.0f, 1.0f);

        // 启用深度测试
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);

        // 编译和链接着色器
        String vertexShaderCode =
                "#version 300 es\n" +
                        "in vec4 a_Position;\n" +
                        "in vec3 a_Normal;\n" +
                        "uniform mat4 u_MVPMatrix;\n" +
                        "out vec3 fragNormal;\n" +
                        "void main() {\n" +
                        "    gl_Position = u_MVPMatrix * a_Position;\n" +
                        "    fragNormal = a_Normal;\n" +
                        "}";

        String fragmentShaderCode =
                "#version 300 es\n" +
                        "precision mediump float;\n" +
                        "in vec3 fragNormal;\n" +
                        "out vec4 fragColor;\n" +
                        "uniform float box_x;\n" +
                        "uniform float box_y;\n" +
                        "uniform float box_z;\n" +
                        "vec4 intersect_box(vec3 ro, vec3 rd) {\n" +
                        "    float t_min = 1000.0;\n" +
                        "    vec3 t_normal;\n" +
                        "    float t = (-box_x - ro.x) / rd.x;\n" +
                        "    vec3 p = ro + t * rd;\n" +
                        "    if (p.y > -box_y && p.z < box_z && p.z > -box_z) {\n" +
                        "        t_normal = vec3(-1.0, 0.0, 0.0);\n" +
                        "        t_min = t;\n" +
                        "    }\n" +
                        "    t = (box_x - ro.x) / rd.x;\n" +
                        "    p = ro + t * rd;\n" +
                        "    if (p.y > -box_y && p.z < box_z && p.z > -box_z) {\n" +
                        "        if (t < t_min) {\n" +
                        "            t_normal = vec3(1.0, 0.0, 0.0);\n" +
                        "            t_min = t;\n" +
                        "        }\n" +
                        "    }\n" +
                        "    t = (-box_z - ro.z) / rd.z;\n" +
                        "    p = ro + t * rd;\n" +
                        "    if (p.y > -box_y && p.x < box_x && p.x > -box_x) {\n" +
                        "        if (t < t_min) {\n" +
                        "            t_normal = vec3(0.0, 0.0, -1.0);\n" +
                        "            t_min = t;\n" +
                        "        }\n" +
                        "    }\n" +
                        "    t = (box_z - ro.z) / rd.z;\n" +
                        "    p = ro + t * rd;\n" +
                        "    if (p.y > -box_y && p.x < box_x && p.x > -box_x) {\n" +
                        "        if (t < t_min) {\n" +
                        "            t_normal = vec3(0.0, 0.0, 1.0);\n" +
                        "            t_min = t;\n" +
                        "        }\n" +
                        "    }\n" +
                        "    if (t_min < 1000.0) return vec4(0.4, 0.6, 1.0, 1.0);  // Return a box color\n" +
                        "    return vec4(0.0, 0.0, 0.0, 1.0);  // background color\n" +
                        "}\n" +

                        "void main() {\n" +
                        "    vec3 rayOrigin = vec3(0.0, 0.0, -5.0);\n" +
                        "    vec3 rayDir = normalize(fragNormal);\n" +
                        "    fragColor = intersect_box(rayOrigin, rayDir);\n" +
                        "}";

        // 编译着色器
        int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode);
        int fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode);

        // 创建着色器程序
        shaderProgram = GLES20.glCreateProgram();
        GLES20.glAttachShader(shaderProgram, vertexShader);
        GLES20.glAttachShader(shaderProgram, fragmentShader);
        GLES20.glLinkProgram(shaderProgram);

        // 获取着色器程序中的位置
        positionHandle = GLES20.glGetAttribLocation(shaderProgram, "a_Position");
        normalHandle = GLES20.glGetAttribLocation(shaderProgram, "a_Normal");
        mvpMatrixHandle = GLES20.glGetUniformLocation(shaderProgram, "u_MVPMatrix");
        boxXHandle = GLES20.glGetUniformLocation(shaderProgram, "box_x");
        boxYHandle = GLES20.glGetUniformLocation(shaderProgram, "box_y");
        boxZHandle = GLES20.glGetUniformLocation(shaderProgram, "box_z");
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        // 清除颜色和深度缓存
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        // 设置投影矩阵
        Matrix.setIdentityM(projectionMatrix, 0);
        Matrix.setIdentityM(viewMatrix, 0);
        Matrix.setIdentityM(modelMatrix, 0);
        Matrix.perspectiveM(projectionMatrix, 0, 45.0f, 1.0f, 1.0f, 100.0f);
        Matrix.setLookAtM(viewMatrix, 0, 0.0f, 0.0f, -5.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f);

        // 应用旋转
        Matrix.rotateM(modelMatrix, 0, rotationX, 1.0f, 0.0f, 0.0f); // 旋转 X 轴
        Matrix.rotateM(modelMatrix, 0, rotationY, 0.0f, 1.0f, 0.0f); // 旋转 Y 轴

        // 合并模型、视图和投影矩阵
        float[] mvpMatrix = new float[16];
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, viewMatrix, 0);
        Matrix.multiplyMM(mvpMatrix, 0, mvpMatrix, 0, modelMatrix, 0);

        // 使用着色器程序并设置矩阵
        GLES20.glUseProgram(shaderProgram);
        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0);

        // 设置盒子尺寸
        GLES20.glUniform1f(boxXHandle, box_x);
        GLES20.glUniform1f(boxYHandle, box_y);
        GLES20.glUniform1f(boxZHandle, box_z);

        float[] vertices = {
                // Front face
                -1.0f, -1.0f,  1.0f,  0.0f,  0.0f,  1.0f,  // 0
                1.0f, -1.0f,  1.0f,  0.0f,  0.0f,  1.0f,  // 1
                1.0f,  1.0f,  1.0f,  0.0f,  0.0f,  1.0f,  // 2
                -1.0f,  1.0f,  1.0f,  0.0f,  0.0f,  1.0f,  // 3

                // Back face
                -1.0f, -1.0f, -1.0f,  0.0f,  0.0f, -1.0f,  // 4
                1.0f, -1.0f, -1.0f,  0.0f,  0.0f, -1.0f,  // 5
                1.0f,  1.0f, -1.0f,  0.0f,  0.0f, -1.0f,  // 6
                -1.0f,  1.0f, -1.0f,  0.0f,  0.0f, -1.0f,  // 7

                // Left face
                -1.0f, -1.0f, -1.0f, -1.0f,  0.0f,  0.0f,  // 8
                -1.0f, -1.0f,  1.0f, -1.0f,  0.0f,  0.0f,  // 9
                -1.0f,  1.0f,  1.0f, -1.0f,  0.0f,  0.0f,  // 10
                -1.0f,  1.0f, -1.0f, -1.0f,  0.0f,  0.0f,  // 11

                // Right face
                1.0f, -1.0f, -1.0f,  1.0f,  0.0f,  0.0f,  // 12
                1.0f, -1.0f,  1.0f,  1.0f,  0.0f,  0.0f,  // 13
                1.0f,  1.0f,  1.0f,  1.0f,  0.0f,  0.0f,  // 14
                1.0f,  1.0f, -1.0f,  1.0f,  0.0f,  0.0f,  // 15
        };

        short[] indices = {
                // Front face
                0, 1, 2,  2, 3, 0,
                // Back face
                4, 5, 6,  6, 7, 4,
                // Left face
                8, 9, 10,  10, 11, 8,
                // Right face
                12, 13, 14,  14, 15, 12,
        };

        // 转换顶点数据到 FloatBuffer
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(vertices.length * 4); // 每个 float 占 4 字节
        byteBuffer.order(ByteOrder.nativeOrder()); // 设置字节顺序为本地顺序
        FloatBuffer vertexBuffer = byteBuffer.asFloatBuffer();
        vertexBuffer.put(vertices);
        vertexBuffer.position(0);

        // 转换索引数据到 ShortBuffer
        ByteBuffer indexByteBuffer = ByteBuffer.allocateDirect(indices.length * 2); // 每个 short 占 2 字节
        indexByteBuffer.order(ByteOrder.nativeOrder());
        ShortBuffer indexBuffer = indexByteBuffer.asShortBuffer();
        indexBuffer.put(indices);
        indexBuffer.position(0);

        // 绑定并绘制顶点数据
        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 6 * 4, vertexBuffer);
        GLES20.glEnableVertexAttribArray(positionHandle);

        GLES20.glVertexAttribPointer(normalHandle, 3, GLES20.GL_FLOAT, false, 6 * 4, vertexBuffer);
        GLES20.glEnableVertexAttribArray(normalHandle);

        // 绘制立方体
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, indices.length, GLES20.GL_UNSIGNED_SHORT, indexBuffer);
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        // 设置视口
        GLES20.glViewport(0, 0, width, height);
    }

    private int loadShader(int type, String shaderCode) {
        // 创建着色器
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, shaderCode);
        GLES20.glCompileShader(shader);
        return shader;
    }
}
