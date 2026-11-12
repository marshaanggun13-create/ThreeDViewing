package com.grafkom.viewing3d;

import com.grafkom.camera.Camera3D;
import com.grafkom.projection.PerspectiveProjection;
import com.grafkom.render.Mesh3D;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.opengl.GL;

import static org.lwjgl.glfw.Callbacks.glfwFreeCallbacks;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33.*;
import static org.lwjgl.system.MemoryUtil.NULL;

/**
 * 3D Visual Realism Suite (Modul Minggu 11), kelanjutan project ThreeDViewing.
 *
 * Kontrol:
 *   Mouse kiri (drag) / scroll / W,S : orbit & zoom kamera
 *   B : Phong <-> Blinn-Phong        (Indikator 1)
 *   F : Flat <-> Smooth shading      (Indikator 2)
 *   Z : Z-Buffer depth test ON/OFF   (Indikator 3)
 *   C : Back-face culling ON/OFF     (Indikator 3)
 *   T : Filtering tekstur Nearest <-> Bilinear+Mipmap (Indikator 4)
 *   X : Tekstur ON/OFF
 *   H : Shadow mapping ON/OFF        (Indikator 5)
 *   SPACE : jeda animasi cahaya & objek
 *   V : VSync ON/OFF (untuk mengukur FPS maksimum)
 *   F11 : fullscreen,  ESC : keluar
 */
public class ThreeDViewing {

    // Frustum ortografik cahaya (arah cahaya = directional, sumber di lightPos menatap origin)
    private static final float LIGHT_ORTHO_SIZE = 10.0f;
    private static final float LIGHT_NEAR = 1.0f;
    private static final float LIGHT_FAR = 25.0f;

    private long window;
    private Camera3D camera;
    private PerspectiveProjection perspective;

    private ShaderProgram phongShader;
    private ShaderProgram depthShader;
    private ShadowMapFBO shadowMap;
    private Texture2D woodTexture;

    private Mesh3D planeMesh, cubeMesh, sphereMesh, torusMesh, lightMarkerMesh;

    private float cameraRadius = 9.0f;
    private float azimuth = 25.0f;
    private float elevation = 28.0f;

    private boolean mousePressed = false;
    private double lastMouseX = 0, lastMouseY = 0;

    private int windowWidth = 1200, windowHeight = 800;   // ukuran jendela
    private int fbWidth = 1200, fbHeight = 800;           // ukuran framebuffer (piksel)

    // Status fitur
    private boolean useBlinnPhong = true;
    private boolean flatShading = false;
    private boolean depthTestEnabled = true;
    private boolean cullingEnabled = true;
    private boolean textureEnabled = true;
    private boolean shadowsEnabled = true;
    private boolean animate = true;
    private boolean vsync = true;
    private Texture2D.FilterMode filterMode = Texture2D.FilterMode.BILINEAR_MIPMAP;

    private boolean fullscreen = false;
    private final int[] savedWindowRect = new int[4];

    private float animTime = 0f;
    private double fps = 0.0;

    private final Vector3f lightPos = new Vector3f(6, 8, 0);
    private final Vector3f lightColor = new Vector3f(1, 1, 1);
    private final Matrix4f lightSpaceMatrix = new Matrix4f();
    private static final Vector3f ORIGIN = new Vector3f(0, 0, 0);
    private static final Vector3f UP = new Vector3f(0, 1, 0);

    public void run() {
        init();
        loop();
        cleanup();
    }

    // ------------------------------------------------------------------ init

    private void init() {
        GLFWErrorCallback.createPrint(System.err).set();

        if (!glfwInit()) {
            throw new IllegalStateException("Gagal menginisialisasi GLFW");
        }

        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);
        glfwWindowHint(GLFW_SAMPLES, 4);
        // Modern OpenGL: Core Profile 3.3 (sesuai #version 330 core)
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE);

        window = glfwCreateWindow(windowWidth, windowHeight, "3D Visual Realism Suite - Modul 11", NULL, NULL);
        if (window == NULL) {
            throw new RuntimeException("Gagal membuat jendela GLFW (butuh OpenGL 3.3+)");
        }

        glfwSetFramebufferSizeCallback(window, (win, width, height) -> {
            fbWidth = width > 0 ? width : 1;
            fbHeight = height > 0 ? height : 1;
        });

        glfwSetMouseButtonCallback(window, (win, button, action, mods) -> {
            if (button == GLFW_MOUSE_BUTTON_LEFT) {
                if (action == GLFW_PRESS) {
                    mousePressed = true;
                    double[] xpos = new double[1];
                    double[] ypos = new double[1];
                    glfwGetCursorPos(win, xpos, ypos);
                    lastMouseX = xpos[0];
                    lastMouseY = ypos[0];
                } else if (action == GLFW_RELEASE) {
                    mousePressed = false;
                }
            }
        });

        glfwSetCursorPosCallback(window, (win, xpos, ypos) -> {
            if (mousePressed) {
                float dx = (float) (xpos - lastMouseX);
                float dy = (float) (ypos - lastMouseY);
                azimuth -= dx * 0.4f;
                elevation += dy * 0.4f;
                elevation = Math.max(-85.0f, Math.min(85.0f, elevation));
                lastMouseX = xpos;
                lastMouseY = ypos;
            }
        });

        glfwSetScrollCallback(window, (win, xoff, yoff) ->
                cameraRadius = Math.max(2.0f, Math.min(25.0f, cameraRadius - (float) yoff * 0.5f)));

        glfwSetKeyCallback(window, (win, key, scancode, action, mods) -> {
            if (action != GLFW_PRESS) return;
            handleKey(key);
        });

        glfwMakeContextCurrent(window);
        glfwSwapInterval(1);

        // Ukuran framebuffer sebenarnya (bisa berbeda dari ukuran jendela pada layar HiDPI)
        int[] w = new int[1], h = new int[1];
        glfwGetFramebufferSize(window, w, h);
        fbWidth = Math.max(1, w[0]);
        fbHeight = Math.max(1, h[0]);

        glfwShowWindow(window);

        GL.createCapabilities();
        glClearColor(0.08f, 0.08f, 0.12f, 1.0f);
        glEnable(GL_MULTISAMPLE);

        HSRManager.setupDepthTest(depthTestEnabled);
        HSRManager.setupBackFaceCulling(cullingEnabled);

        camera = new Camera3D(new Vector3f(0, 0, 5), new Vector3f(0, 0, 0), new Vector3f(0, 1, 0));
        // zNear 0.1 / zFar 60: rasio proporsional (tidak terlalu dekat ke 0) -> menekan Z-Fighting
        perspective = new PerspectiveProjection(45.0f, (float) fbWidth / fbHeight, 0.1f, 60.0f);

        phongShader = new ShaderProgram("/shaders/phong.vert", "/shaders/phong.frag");
        depthShader = new ShaderProgram("/shaders/depth.vert", "/shaders/depth.frag");
        shadowMap = new ShadowMapFBO();
        woodTexture = new Texture2D("/wood.jpg");

        // Mesh: bidang lantai (diulang 7x), kubus, bola, torus, dan penanda cahaya
        planeMesh = Mesh3D.createPlane(7.0f, 7.0f);
        cubeMesh = Mesh3D.createCube();
        sphereMesh = Mesh3D.createSphere(0.9f, 16, 24);   // resolusi rendah agar beda flat/smooth terlihat
        torusMesh = Mesh3D.createTorus(0.8f, 0.3f, 28, 14);
        lightMarkerMesh = Mesh3D.createSphere(0.18f, 12, 16);

        phongShader.use();
        phongShader.setInt("textureSampler", 0);
        phongShader.setInt("shadowMap", 1);
        phongShader.setVec3("lightColor", lightColor);

        printControls();
        updateTitle();
    }

    private void printControls() {
        System.out.println("=== KONTROL VISUAL REALISM ===");
        System.out.println("Mouse drag / scroll / W,S : Orbit & zoom kamera");
        System.out.println("Tombol B : Toggle Phong / Blinn-Phong Shading");
        System.out.println("Tombol F : Toggle Flat / Smooth Shading");
        System.out.println("Tombol Z : Toggle Z-Buffer Depth Test (HSR)");
        System.out.println("Tombol C : Toggle Back-Face Culling (HSR)");
        System.out.println("Tombol T : Toggle Texture Filtering (Nearest / Bilinear+Mipmap)");
        System.out.println("Tombol X : Toggle Tekstur");
        System.out.println("Tombol H : Toggle Shadow Mapping");
        System.out.println("SPACE    : Jeda animasi | V : VSync | F11 : Fullscreen | ESC : Keluar");
    }

    private void handleKey(int key) {
        switch (key) {
            case GLFW_KEY_ESCAPE -> glfwSetWindowShouldClose(window, true);
            case GLFW_KEY_W -> cameraRadius = Math.max(2.0f, cameraRadius - 0.4f);
            case GLFW_KEY_S -> cameraRadius = Math.min(25.0f, cameraRadius + 0.4f);
            case GLFW_KEY_B -> {
                useBlinnPhong = !useBlinnPhong;
                System.out.println("Shading Model -> " + (useBlinnPhong ? "Blinn-Phong" : "Phong"));
            }
            case GLFW_KEY_F -> {
                flatShading = !flatShading;
                System.out.println("Shading Interpolation -> " + (flatShading ? "Flat (per-face normal)" : "Smooth (vertex normal averaging)"));
            }
            case GLFW_KEY_Z -> {
                depthTestEnabled = !depthTestEnabled;
                HSRManager.setupDepthTest(depthTestEnabled);
                System.out.println("HSR -> Z-Buffer Depth Test: " + depthTestEnabled);
            }
            case GLFW_KEY_C -> {
                cullingEnabled = !cullingEnabled;
                HSRManager.setupBackFaceCulling(cullingEnabled);
                System.out.println("HSR -> Back-Face Culling: " + cullingEnabled);
            }
            case GLFW_KEY_T -> {
                filterMode = (filterMode == Texture2D.FilterMode.NEAREST)
                        ? Texture2D.FilterMode.BILINEAR_MIPMAP : Texture2D.FilterMode.NEAREST;
                woodTexture.setFilterMode(filterMode);
                System.out.println("Texture Filtering -> " + filterMode.label);
            }
            case GLFW_KEY_X -> {
                textureEnabled = !textureEnabled;
                System.out.println("Texture Mapping: " + textureEnabled);
            }
            case GLFW_KEY_H -> {
                shadowsEnabled = !shadowsEnabled;
                System.out.println("Shadow Mapping: " + shadowsEnabled);
            }
            case GLFW_KEY_SPACE -> {
                animate = !animate;
                System.out.println("Animasi: " + (animate ? "jalan" : "jeda"));
            }
            case GLFW_KEY_V -> {
                vsync = !vsync;
                glfwSwapInterval(vsync ? 1 : 0);
                System.out.println("VSync: " + vsync);
            }
            case GLFW_KEY_F11 -> toggleFullscreen();
            default -> { return; }
        }
        updateTitle();
    }

    private void toggleFullscreen() {
        if (!fullscreen) {
            int[] x = new int[1], y = new int[1], w = new int[1], h = new int[1];
            glfwGetWindowPos(window, x, y);
            glfwGetWindowSize(window, w, h);
            savedWindowRect[0] = x[0]; savedWindowRect[1] = y[0];
            savedWindowRect[2] = w[0]; savedWindowRect[3] = h[0];

            long monitor = glfwGetPrimaryMonitor();
            GLFWVidMode mode = glfwGetVideoMode(monitor);
            if (mode == null) return;
            glfwSetWindowMonitor(window, monitor, 0, 0, mode.width(), mode.height(), mode.refreshRate());
        } else {
            glfwSetWindowMonitor(window, NULL, savedWindowRect[0], savedWindowRect[1],
                    savedWindowRect[2], savedWindowRect[3], GLFW_DONT_CARE);
        }
        fullscreen = !fullscreen;
        glfwSwapInterval(vsync ? 1 : 0);
    }

    /** HUD sederhana lewat judul jendela: status semua fitur + FPS (untuk laporan). */
    private void updateTitle() {
        String title = String.format(
                "3D Visual Realism Suite | FPS %.0f%s | %s | %s | Z:%s | Cull:%s | Tex:%s (%s) | Shadow:%s",
                fps, vsync ? "" : " (VSync off)",
                useBlinnPhong ? "Blinn-Phong" : "Phong",
                flatShading ? "Flat" : "Smooth",
                onOff(depthTestEnabled), onOff(cullingEnabled),
                onOff(textureEnabled), filterMode.label, onOff(shadowsEnabled));
        glfwSetWindowTitle(window, title);
    }

    private static String onOff(boolean b) { return b ? "ON" : "OFF"; }

    // ------------------------------------------------------------------ loop

    private void loop() {
        double last = glfwGetTime();
        double fpsTimer = 0;
        int frames = 0;

        while (!glfwWindowShouldClose(window)) {
            double now = glfwGetTime();
            float dt = (float) (now - last);
            last = now;
            if (animate) animTime += dt;

            drawScene(animTime);

            glfwSwapBuffers(window);
            glfwPollEvents();

            frames++;
            fpsTimer += dt;
            if (fpsTimer >= 0.5) {
                fps = frames / fpsTimer;
                frames = 0;
                fpsTimer = 0;
                updateTitle();
            }
        }
    }

    private void drawScene(float t) {
        // Cahaya mengorbit di atas scene
        float a = t * 0.6f;
        lightPos.set(6.0f * (float) Math.cos(a), 8.0f, 6.0f * (float) Math.sin(a));

        // Matriks ruang cahaya: P_ortho * V_light (cahaya menatap origin)
        lightSpaceMatrix.identity()
                .ortho(-LIGHT_ORTHO_SIZE, LIGHT_ORTHO_SIZE, -LIGHT_ORTHO_SIZE, LIGHT_ORTHO_SIZE,
                        LIGHT_NEAR, LIGHT_FAR)
                .lookAt(lightPos, ORIGIN, UP);

        // ===== PASS 1: Depth Pass dari sudut pandang cahaya =====
        if (shadowsEnabled) {
            shadowMap.bindForWriting();
            // Depth test wajib aktif saat membuat shadow map, apa pun status tombol Z
            glEnable(GL_DEPTH_TEST);
            glDepthFunc(GL_LESS);

            depthShader.use();
            depthShader.setMat4("lightSpaceMatrix", lightSpaceMatrix);
            renderObjects(depthShader, true, t);

            shadowMap.unbind(fbWidth, fbHeight);
            HSRManager.setupDepthTest(depthTestEnabled); // kembalikan ke pilihan pengguna
        }

        // ===== PASS 2: Lighting Pass dari sudut pandang kamera =====
        glViewport(0, 0, fbWidth, fbHeight);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

        perspective.setAspectRatio((float) fbWidth / fbHeight);
        Matrix4f projection = perspective.calculateProjectionMatrix();
        camera.updateOrbitalPosition(cameraRadius, azimuth, elevation);
        Matrix4f view = camera.calculateLookAtMatrix();

        phongShader.use();
        phongShader.setMat4("projection", projection);
        phongShader.setMat4("view", view);
        phongShader.setMat4("lightSpaceMatrix", lightSpaceMatrix);
        phongShader.setVec3("viewPos", camera.getEye());
        phongShader.setVec3("lightPos", lightPos);
        phongShader.setBool("useBlinnPhong", useBlinnPhong);
        phongShader.setBool("useTexture", textureEnabled);
        phongShader.setBool("useShadows", shadowsEnabled);
        phongShader.setBool("unlit", false);

        woodTexture.bind(0);
        shadowMap.bindTexture(1);

        renderObjects(phongShader, false, t);
    }

    /**
     * Menggambar seluruh objek scene. Dipakai oleh kedua pass:
     * depthPass = true  -> hanya penghasil bayangan (lantai & penanda cahaya dilewati)
     * depthPass = false -> semua objek dengan material dan pencahayaan penuh
     */
    private void renderObjects(ShaderProgram shader, boolean depthPass, float t) {
        Matrix4f model = new Matrix4f();

        if (!depthPass) {
            // Lantai: hanya penerima bayangan
            drawObject(shader, planeMesh, model.identity(), new Vector3f(0.85f, 0.85f, 0.9f), false);
        }

        // Kubus berputar pelan
        model.identity().translate(0.0f, 0.6f, -1.0f).rotateY(t * 0.3f).scale(1.2f);
        drawObject(shader, cubeMesh, model, new Vector3f(1.0f, 1.0f, 1.0f), depthPass);

        // Bola resolusi rendah (beda Flat vs Smooth paling terlihat di sini)
        model.identity().translate(-2.6f, 0.9f, 1.0f);
        drawObject(shader, sphereMesh, model, new Vector3f(1.0f, 0.85f, 0.75f), depthPass);

        // Torus melayang & berputar (bayangannya berubah bentuk)
        model.identity().translate(2.6f, 1.4f, 0.5f).rotateY(t * 0.8f).rotateX(1.0f);
        drawObject(shader, torusMesh, model, new Vector3f(0.8f, 0.9f, 1.0f), depthPass);

        if (!depthPass) {
            // Penanda posisi cahaya (emisif, tidak membentuk bayangan)
            shader.setBool("unlit", true);
            model.identity().translate(lightPos);
            drawObject(shader, lightMarkerMesh, model, new Vector3f(1.0f, 0.95f, 0.6f), false);
            shader.setBool("unlit", false);
        }
    }

    private void drawObject(ShaderProgram shader, Mesh3D mesh, Matrix4f model, Vector3f color, boolean depthPass) {
        shader.setMat4("model", model);
        if (!depthPass) {
            shader.setVec3("objectColor", color);
        }
        mesh.draw(flatShading);
    }

    // --------------------------------------------------------------- cleanup

    private void cleanup() {
        planeMesh.cleanup();
        cubeMesh.cleanup();
        sphereMesh.cleanup();
        torusMesh.cleanup();
        lightMarkerMesh.cleanup();
        woodTexture.cleanup();
        shadowMap.cleanup();
        phongShader.cleanup();
        depthShader.cleanup();

        glfwFreeCallbacks(window);
        glfwDestroyWindow(window);
        glfwTerminate();
        glfwSetErrorCallback(null).free();
    }

    public static void main(String[] args) {
        new ThreeDViewing().run();
    }
}
