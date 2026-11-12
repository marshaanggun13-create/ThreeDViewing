package com.grafkom.render;

import static org.lwjgl.opengl.GL33.*;

/**
 * Mesh 3D di GPU (VAO/VBO, Modern OpenGL). Menyimpan DUA versi data vertex:
 * normal smooth (rata-rata per vertex) dan normal flat (per face), sehingga
 * toggle Flat/Smooth cukup mengganti VAO tanpa upload ulang.
 *
 * Layout atribut: location 0 = posisi, 1 = normal, 2 = UV (sesuai phong.vert).
 */
public class Mesh3D {

    private final int vaoSmooth, vboSmooth;
    private final int vaoFlat, vboFlat;
    private final int vertexCount;

    public Mesh3D(MeshData data) {
        this.vertexCount = data.indices.length;
        int[] smooth = upload(data.buildSmoothInterleaved());
        int[] flat = upload(data.buildFlatInterleaved());
        vaoSmooth = smooth[0]; vboSmooth = smooth[1];
        vaoFlat = flat[0];     vboFlat = flat[1];
    }

    private static int[] upload(float[] interleaved) {
        int vao = glGenVertexArrays();
        int vbo = glGenBuffers();
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, interleaved, GL_STATIC_DRAW);

        int stride = MeshData.FLOATS_PER_VERTEX * Float.BYTES;
        glVertexAttribPointer(0, 3, GL_FLOAT, false, stride, 0L);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(1, 3, GL_FLOAT, false, stride, 3L * Float.BYTES);
        glEnableVertexAttribArray(1);
        glVertexAttribPointer(2, 2, GL_FLOAT, false, stride, 6L * Float.BYTES);
        glEnableVertexAttribArray(2);

        glBindVertexArray(0);
        return new int[]{ vao, vbo };
    }

    public void draw(boolean flatShading) {
        glBindVertexArray(flatShading ? vaoFlat : vaoSmooth);
        glDrawArrays(GL_TRIANGLES, 0, vertexCount);
        glBindVertexArray(0);
    }

    public void cleanup() {
        glDeleteBuffers(vboSmooth);
        glDeleteBuffers(vboFlat);
        glDeleteVertexArrays(vaoSmooth);
        glDeleteVertexArrays(vaoFlat);
    }

    // ---- pabrik mesh siap pakai --------------------------------------------

    public static Mesh3D createSphere(float radius, int stacks, int slices) {
        return new Mesh3D(MeshData.sphere(radius, stacks, slices));
    }

    public static Mesh3D createTorus(float ringRadius, float tubeRadius, int rings, int sides) {
        return new Mesh3D(MeshData.torus(ringRadius, tubeRadius, rings, sides, 3f));
    }

    public static Mesh3D createCube() {
        return new Mesh3D(MeshData.cube());
    }

    public static Mesh3D createPlane(float halfSize, float uvTile) {
        return new Mesh3D(MeshData.plane(halfSize, uvTile));
    }
}
