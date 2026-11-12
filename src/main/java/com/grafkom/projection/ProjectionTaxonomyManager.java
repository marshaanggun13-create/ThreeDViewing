package com.grafkom.projection;

import org.joml.Matrix4f;

public class ProjectionTaxonomyManager {

    public enum ProjectionType {
        // label, azimuth awal, elevasi awal, apakah proyeksi paralel
        ORTHOGRAPHIC("Ortografik", 25.0f, 20.0f, true),
        ISOMETRIC("Isometrik", 45.0f, 35.264f, true),
        OBLIQUE_CAVALIER("Oblik Cavalier", 0.0f, 0.0f, true),
        PERSPECTIVE_1POINT("Perspektif 1 titik hilang", 0.0f, 0.0f, false),
        PERSPECTIVE_2POINT("Perspektif 2 titik hilang", 35.0f, 0.0f, false),
        PERSPECTIVE_3POINT("Perspektif 3 titik hilang", 35.0f, 25.0f, false);

        public final String label;
        public final float presetAzimuth;
        public final float presetElevation;
        public final boolean parallel;

        ProjectionType(String label, float presetAzimuth, float presetElevation, boolean parallel) {
            this.label = label;
            this.presetAzimuth = presetAzimuth;
            this.presetElevation = presetElevation;
            this.parallel = parallel;
        }
    }

    /**
     * Membangun matriks proyeksi sesuai taksonomi.
     * Jumlah titik hilang pada perspektif ditentukan oleh orientasi kamera
     * (azimuth/elevasi), bukan oleh matriks proyeksinya.
     */
    public static Matrix4f buildProjection(ProjectionType type, float aspect, float viewDistance,
                                           PerspectiveProjection persp, float eyeOffset, float focalLength) {
        if (type.parallel) {
            // Ukuran volume pandang mengikuti jarak kamera agar zoom tetap bekerja
            float halfH = viewDistance * (float) Math.tan(Math.toRadians(persp.getFovDegree()) / 2.0);
            Matrix4f ortho = new Matrix4f().ortho(-halfH * aspect, halfH * aspect, -halfH, halfH,
                    persp.getNear(), persp.getFar());

            if (type == ProjectionType.OBLIQUE_CAVALIER) {
                // Shear terhadap bidang z = -viewDistance: sumbu kedalaman miring 45 derajat
                float k = 0.707f;
                Matrix4f shear = new Matrix4f()
                        .m20(-k).m21(-k)
                        .m30(-k * viewDistance).m31(-k * viewDistance);
                ortho.mul(shear);
            }
            return ortho;
        }

        persp.setAspectRatio(aspect);
        return persp.calculateProjectionMatrix(eyeOffset, focalLength);
    }
}
