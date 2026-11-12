package com.grafkom.render;

import java.util.HashMap;
import java.util.Map;

/**
 * Data geometri murni (tanpa OpenGL): posisi, koordinat UV, dan indeks segitiga
 * (urutan CCW dilihat dari luar). Dari data ini dibangun dua versi array vertex:
 *  - Smooth : normal vertex = rata-rata normal semua face yang berbagi posisi vertex
 *  - Flat   : normal = normal face, vertex diduplikasi per segitiga
 * Format interleaved per vertex: x y z | nx ny nz | u v (8 float).
 */
public final class MeshData {

    public static final int FLOATS_PER_VERTEX = 8;

    public final float[] positions; // 3 float per vertex
    public final float[] uvs;       // 2 float per vertex
    public final int[] indices;     // 3 indeks per segitiga

    public MeshData(float[] positions, float[] uvs, int[] indices) {
        this.positions = positions;
        this.uvs = uvs;
        this.indices = indices;
    }

    public int triangleCount() { return indices.length / 3; }

    // ------------------------------------------------------------------ normal

    /** Normal face (tidak dinormalisasi; panjangnya = 2 x luas segitiga). */
    private float[] faceNormalRaw(int t) {
        int i0 = indices[3 * t], i1 = indices[3 * t + 1], i2 = indices[3 * t + 2];
        float ax = positions[3 * i1] - positions[3 * i0];
        float ay = positions[3 * i1 + 1] - positions[3 * i0 + 1];
        float az = positions[3 * i1 + 2] - positions[3 * i0 + 2];
        float bx = positions[3 * i2] - positions[3 * i0];
        float by = positions[3 * i2 + 1] - positions[3 * i0 + 1];
        float bz = positions[3 * i2 + 2] - positions[3 * i0 + 2];
        return new float[]{ ay * bz - az * by, az * bx - ax * bz, ax * by - ay * bx };
    }

    private static float[] normalized(float[] n) {
        float len = (float) Math.sqrt(n[0] * n[0] + n[1] * n[1] + n[2] * n[2]);
        if (len < 1e-8f) return new float[]{ 0f, 1f, 0f };
        return new float[]{ n[0] / len, n[1] / len, n[2] / len };
    }

    private record Key(int x, int y, int z) {}

    /** Kunci posisi (di-weld) supaya vertex kembar di jahitan UV tetap berbagi normal. */
    private Key keyOf(int vertex) {
        return new Key(Math.round(positions[3 * vertex] * 10000f),
                       Math.round(positions[3 * vertex + 1] * 10000f),
                       Math.round(positions[3 * vertex + 2] * 10000f));
    }

    // ------------------------------------------------------------ interleaved

    /** Smooth shading: N_vertex = sum(N_face) / ||sum(N_face)||. */
    public float[] buildSmoothInterleaved() {
        Map<Key, float[]> sum = new HashMap<>();
        for (int t = 0; t < triangleCount(); t++) {
            float[] n = faceNormalRaw(t); // bobot = luas face
            for (int k = 0; k < 3; k++) {
                float[] acc = sum.computeIfAbsent(keyOf(indices[3 * t + k]), key -> new float[3]);
                acc[0] += n[0]; acc[1] += n[1]; acc[2] += n[2];
            }
        }
        float[] out = new float[indices.length * FLOATS_PER_VERTEX];
        for (int c = 0; c < indices.length; c++) {
            int v = indices[c];
            float[] n = normalized(sum.get(keyOf(v)));
            write(out, c, v, n);
        }
        return out;
    }

    /** Flat shading: satu normal face untuk ketiga vertex segitiga. */
    public float[] buildFlatInterleaved() {
        float[] out = new float[indices.length * FLOATS_PER_VERTEX];
        for (int t = 0; t < triangleCount(); t++) {
            float[] n = normalized(faceNormalRaw(t));
            for (int k = 0; k < 3; k++) {
                write(out, 3 * t + k, indices[3 * t + k], n);
            }
        }
        return out;
    }

    private void write(float[] out, int corner, int v, float[] n) {
        int o = corner * FLOATS_PER_VERTEX;
        out[o]     = positions[3 * v];
        out[o + 1] = positions[3 * v + 1];
        out[o + 2] = positions[3 * v + 2];
        out[o + 3] = n[0];
        out[o + 4] = n[1];
        out[o + 5] = n[2];
        out[o + 6] = uvs[2 * v];
        out[o + 7] = uvs[2 * v + 1];
    }

    // ---------------------------------------------------------------- factory

    /** Bola UV. v=1 di kutub atas, v=0 di kutub bawah. */
    public static MeshData sphere(float radius, int stacks, int slices) {
        int cols = slices + 1;
        float[] pos = new float[(stacks + 1) * cols * 3];
        float[] uv = new float[(stacks + 1) * cols * 2];
        for (int i = 0; i <= stacks; i++) {
            double phi = Math.PI * i / stacks;
            for (int j = 0; j <= slices; j++) {
                double theta = 2.0 * Math.PI * j / slices;
                int v = i * cols + j;
                pos[3 * v]     = (float) (radius * Math.sin(phi) * Math.cos(theta));
                pos[3 * v + 1] = (float) (radius * Math.cos(phi));
                pos[3 * v + 2] = (float) (radius * Math.sin(phi) * Math.sin(theta));
                uv[2 * v]     = (float) j / slices;
                uv[2 * v + 1] = 1f - (float) i / stacks;
            }
        }
        IntList idx = new IntList();
        for (int i = 0; i < stacks; i++) {
            for (int j = 0; j < slices; j++) {
                int a = i * cols + j, b = a + 1, d = (i + 1) * cols + j, c = d + 1;
                if (i != 0)          idx.add(a, b, c); // di kutub atas a==b (degenerate) -> dilewati
                if (i != stacks - 1) idx.add(a, c, d); // di kutub bawah c==d (degenerate) -> dilewati
            }
        }
        return new MeshData(pos, uv, idx.toArray());
    }

    /** Torus: R = jari-jari cincin, r = jari-jari tabung. Sumbu simetri = Y. */
    public static MeshData torus(float R, float r, int rings, int sides, float uTile) {
        int cols = sides + 1;
        float[] pos = new float[(rings + 1) * cols * 3];
        float[] uv = new float[(rings + 1) * cols * 2];
        for (int i = 0; i <= rings; i++) {
            double alpha = 2.0 * Math.PI * i / rings;
            for (int j = 0; j <= sides; j++) {
                double beta = 2.0 * Math.PI * j / sides;
                int v = i * cols + j;
                double ring = R + r * Math.cos(beta);
                pos[3 * v]     = (float) (ring * Math.cos(alpha));
                pos[3 * v + 1] = (float) (r * Math.sin(beta));
                pos[3 * v + 2] = (float) (ring * Math.sin(alpha));
                uv[2 * v]     = uTile * i / rings;
                uv[2 * v + 1] = (float) j / sides;
            }
        }
        IntList idx = new IntList();
        for (int i = 0; i < rings; i++) {
            for (int j = 0; j < sides; j++) {
                int a = i * cols + j, d = a + 1, b = (i + 1) * cols + j, c = b + 1;
                idx.add(a, c, b);
                idx.add(a, d, c);
            }
        }
        return new MeshData(pos, uv, idx.toArray());
    }

    /** Kubus 1x1x1 berpusat di origin; tiap sisi memiliki UV 0..1 penuh. */
    public static MeshData cube() {
        // tiap sisi: BL, BR, TR, TL (CCW dilihat dari luar)
        float[][] faces = {
            {-.5f,-.5f, .5f,   .5f,-.5f, .5f,   .5f, .5f, .5f,  -.5f, .5f, .5f}, // depan  (+Z)
            { .5f,-.5f,-.5f,  -.5f,-.5f,-.5f,  -.5f, .5f,-.5f,   .5f, .5f,-.5f}, // belakang (-Z)
            { .5f,-.5f, .5f,   .5f,-.5f,-.5f,   .5f, .5f,-.5f,   .5f, .5f, .5f}, // kanan  (+X)
            {-.5f,-.5f,-.5f,  -.5f,-.5f, .5f,  -.5f, .5f, .5f,  -.5f, .5f,-.5f}, // kiri   (-X)
            {-.5f, .5f, .5f,   .5f, .5f, .5f,   .5f, .5f,-.5f,  -.5f, .5f,-.5f}, // atas   (+Y)
            {-.5f,-.5f,-.5f,   .5f,-.5f,-.5f,   .5f,-.5f, .5f,  -.5f,-.5f, .5f}  // bawah  (-Y)
        };
        float[] cornerUV = { 0, 0, 1, 0, 1, 1, 0, 1 };
        float[] pos = new float[6 * 4 * 3];
        float[] uv = new float[6 * 4 * 2];
        IntList idx = new IntList();
        for (int f = 0; f < 6; f++) {
            for (int k = 0; k < 4; k++) {
                int v = f * 4 + k;
                pos[3 * v]     = faces[f][3 * k];
                pos[3 * v + 1] = faces[f][3 * k + 1];
                pos[3 * v + 2] = faces[f][3 * k + 2];
                uv[2 * v]     = cornerUV[2 * k];
                uv[2 * v + 1] = cornerUV[2 * k + 1];
            }
            int b = f * 4;
            idx.add(b, b + 1, b + 2);
            idx.add(b, b + 2, b + 3);
        }
        return new MeshData(pos, uv, idx.toArray());
    }

    /** Bidang datar di y=0 menghadap +Y; UV diulang sebanyak 'tile' kali (GL_REPEAT). */
    public static MeshData plane(float halfSize, float tile) {
        float s = halfSize;
        float[] pos = { -s,0, s,   s,0, s,   s,0,-s,  -s,0,-s };
        float[] uv  = { 0,0,  tile,0,  tile,tile,  0,tile };
        int[] idx = { 0, 1, 2, 0, 2, 3 };
        return new MeshData(pos, uv, idx);
    }

    // ------------------------------------------------------------------ util

    private static final class IntList {
        private int[] data = new int[64];
        private int size = 0;
        void add(int... vals) {
            for (int v : vals) {
                if (size == data.length) data = java.util.Arrays.copyOf(data, size * 2);
                data[size++] = v;
            }
        }
        int[] toArray() { return java.util.Arrays.copyOf(data, size); }
    }
}
