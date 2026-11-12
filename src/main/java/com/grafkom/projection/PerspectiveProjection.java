package com.grafkom.projection;

import org.joml.Matrix4f;

public class PerspectiveProjection {
    private float fovDegree;
    private float aspectRatio;
    private float zNear;
    private float zFar;

    public PerspectiveProjection(float fovDegree, float aspectRatio, float zNear, float zFar) {
        this.fovDegree = fovDegree;
        this.aspectRatio = aspectRatio;
        this.zNear = zNear;
        this.zFar = zFar;
    }

    // Matriks perspektif simetris (satu kamera)
    public Matrix4f calculateProjectionMatrix() {
        float fovRad = (float) Math.toRadians(fovDegree);
        float f = 1.0f / (float) Math.tan(fovRad / 2.0f);

        Matrix4f proj = new Matrix4f();
        proj.zero();
        proj.m00(f / aspectRatio);
        proj.m11(f);
        proj.m22(-(zFar + zNear) / (zFar - zNear));
        proj.m32(-(2.0f * zFar * zNear) / (zFar - zNear));
        proj.m23(-1.0f);
        proj.m33(0.0f);
        return proj;
    }

    // Matriks perspektif off-axis (frustum asimetris) untuk stereo.
    // eyeOffset: pergeseran kamera sepanjang sumbu kanan (kiri < 0, kanan > 0)
    // focalLength: jarak bidang zero parallax (bidang layar)
    public Matrix4f calculateProjectionMatrix(float eyeOffset, float focalLength) {
        Matrix4f proj = calculateProjectionMatrix();
        float f = 1.0f / (float) Math.tan(Math.toRadians(fovDegree) / 2.0);
        proj.m20(-eyeOffset * f / (focalLength * aspectRatio));
        return proj;
    }

    public float getFovDegree() { return fovDegree; }

    public void setFovDegree(float fovDegree) { this.fovDegree = fovDegree; }

    public void setAspectRatio(float aspectRatio) { this.aspectRatio = aspectRatio; }

    public float getNear() { return zNear; }

    public float getFar() { return zFar; }

    // Near/far dijaga agar selalu 0.05 <= near < far <= 100
    public void setNear(float near) {
        this.zNear = Math.max(0.05f, Math.min(near, zFar - 0.5f));
    }

    public void setFar(float far) {
        this.zFar = Math.max(zNear + 0.5f, Math.min(far, 100.0f));
    }
}
