package com.grafkom.render;

import static org.lwjgl.opengl.GL11.*;

public class StereoAnaglyphRenderer {

    /** Callback penggambaran satu mata. */
    public interface EyeScene {
        /**
         * @param eyeOffset   pergeseran kamera sepanjang sumbu kanan (kiri < 0, kanan > 0, mono = 0)
         * @param focalLength jarak bidang zero parallax
         */
        void draw(float eyeOffset, float focalLength);
    }

    private float ipd = 0.2f;          // Interpupillary Distance (satuan dunia)
    private float focalLength = 5.0f;  // Jarak bidang layar (zero parallax)

    public void renderAnaglyph(EyeScene scene) {
        glColorMask(true, true, true, true);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

        // PASS 1: mata kiri -> hanya kanal merah
        glColorMask(true, false, false, true);
        scene.draw(-ipd / 2.0f, focalLength);

        // PASS 2: mata kanan -> kanal hijau + biru (cyan)
        glColorMask(false, true, true, true);
        glClear(GL_DEPTH_BUFFER_BIT);
        scene.draw(ipd / 2.0f, focalLength);

        glColorMask(true, true, true, true);
    }

    /** Render biasa (satu kamera, tanpa stereo). */
    public void renderMono(EyeScene scene) {
        glColorMask(true, true, true, true);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        scene.draw(0.0f, focalLength);
    }

    public float getIpd() { return ipd; }

    public void setIpd(float ipd) { this.ipd = Math.max(0.0f, Math.min(1.0f, ipd)); }

    public void setFocalLength(float focalLength) { this.focalLength = focalLength; }
}
