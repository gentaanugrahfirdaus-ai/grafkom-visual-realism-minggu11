package com.grafkom.viewing3d;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;

import java.io.File;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.GL_TEXTURE1;
import static org.lwjgl.opengl.GL13.glActiveTexture;
import static org.lwjgl.system.MemoryUtil.NULL;

/**
 * 3D Visual Realism Suite (Minggu 11)
 * Kontrol:
 *   B = Phong <-> Blinn-Phong      F = Flat <-> Smooth
 *   Z = Z-Buffer ON/OFF            C = Back-Face Culling ON/OFF
 *   T = Texture ON/OFF             N = Nearest <-> Bilinear+Mipmap
 *   S = Shadow ON/OFF              V = Shadow Bias ON/OFF (lihat Shadow Acne)
 *   L = Animasi cahaya ON/OFF      Panah = putar kamera, Q/E atau scroll = zoom
 */
public class Main {

    private long window;
    private int fbW = 1280, fbH = 720;

    // Status fitur
    private boolean blinn = true, smooth = true, depthOn = true, cullOn = true;
    private boolean useTex = true, mipmap = true, shadowOn = true, biasOn = true, lightAnim = true;

    // Kamera & animasi
    private float yaw = (float) (Math.PI / 2), pitch = 0.5f, dist = 13f;
    private float lightAngle = 0.8f, spin = 0f;
    private final Vector3f lightPos = new Vector3f();

    // Resource
    private ShaderProgram sceneShader, depthShader;
    private Texture2D texture;
    private ShadowMapFBO shadowFbo;
    private Mesh sphereSmooth, sphereFlat, torusSmooth, torusFlat, plane;

    // FPS
    private int frames = 0, fps = 0;
    private double fpsTimer = 0;

    public static void main(String[] args) {
        new Main().run();
    }

    private void run() {
        init();
        loop();
        glfwDestroyWindow(window);
        glfwTerminate();
    }

    private void init() {
        GLFWErrorCallback.createPrint(System.err).set();
        if (!glfwInit()) throw new IllegalStateException("Gagal inisialisasi GLFW");

        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE); // wajib di macOS

        window = glfwCreateWindow(fbW, fbH, "3D Visual Realism Suite", NULL, NULL);
        if (window == NULL) throw new RuntimeException("Gagal membuat window (butuh OpenGL 3.3)");
        glfwMakeContextCurrent(window);
        glfwSwapInterval(0); // 0 = FPS tidak dibatasi VSync (untuk pengukuran FPS)
        GL.createCapabilities();

        int[] w = new int[1], h = new int[1];
        glfwGetFramebufferSize(window, w, h);
        fbW = w[0];
        fbH = h[0];
        glfwSetFramebufferSizeCallback(window, (win, nw, nh) -> { fbW = nw; fbH = nh; });
        glfwSetScrollCallback(window, (win, xo, yo) -> dist = clamp(dist - (float) yo * 0.8f, 4f, 30f));
        glfwSetKeyCallback(window, (win, key, sc, action, mods) -> {
            if (action != GLFW_PRESS) return;
            switch (key) {
                case GLFW_KEY_B -> blinn = !blinn;
                case GLFW_KEY_F -> smooth = !smooth;
                case GLFW_KEY_Z -> depthOn = !depthOn;
                case GLFW_KEY_C -> cullOn = !cullOn;
                case GLFW_KEY_T -> useTex = !useTex;
                case GLFW_KEY_N -> { mipmap = !mipmap; texture.setMipmapFiltering(mipmap); }
                case GLFW_KEY_S -> shadowOn = !shadowOn;
                case GLFW_KEY_V -> biasOn = !biasOn;
                case GLFW_KEY_L -> lightAnim = !lightAnim;
                case GLFW_KEY_ESCAPE -> glfwSetWindowShouldClose(win, true);
                default -> { }
            }
        });

        // Shader
        sceneShader = new ShaderProgram("phong.vert", "phong.frag");
        depthShader = new ShaderProgram("depth.vert", "depth.frag");

        // Tekstur: pakai texture.png/jpg kalau ada di folder kerja, kalau tidak -> papan catur prosedural
        if (new File("texture.png").exists()) texture = new Texture2D("texture.png");
        else if (new File("texture.jpg").exists()) texture = new Texture2D("texture.jpg");
        else texture = new Texture2D(256, 32);

        shadowFbo = new ShadowMapFBO();

        // Mesh (versi smooth & flat)
        Geometry.MeshData sph = Geometry.sphere(16, 24, 1.0f);
        Geometry.MeshData tor = Geometry.torus(24, 12, 1.0f, 0.4f);
        sphereSmooth = new Mesh(sph, true);
        sphereFlat = new Mesh(sph, false);
        torusSmooth = new Mesh(tor, true);
        torusFlat = new Mesh(tor, false);
        plane = new Mesh(Geometry.plane(7f, 4f), true);

        System.out.println("""
                === 3D Visual Realism Suite ===
                B: Phong/Blinn-Phong | F: Flat/Smooth | Z: Z-Buffer | C: Back-Face Culling
                T: Texture | N: Nearest/Bilinear+Mipmap | S: Shadow | V: Shadow Bias | L: Animasi cahaya
                Panah: putar kamera | Q/E atau scroll: zoom | ESC: keluar""");
    }

    private void loop() {
        double last = glfwGetTime();
        fpsTimer = last;
        while (!glfwWindowShouldClose(window)) {
            double now = glfwGetTime();
            float dt = (float) (now - last);
            last = now;

            handleCamera(dt);
            if (lightAnim) lightAngle += dt * 0.6f;
            spin += dt * 0.8f;
            lightPos.set((float) (7 * Math.cos(lightAngle)), 9f, (float) (7 * Math.sin(lightAngle)));

            Matrix4f sphereModel = new Matrix4f().translate(-1.8f, 1.0f, 0f);
            Matrix4f torusModel = new Matrix4f().translate(2.0f, 1.7f, 0f).rotateZ(0.6f).rotateX(spin);
            Matrix4f planeModel = new Matrix4f();

            // ---------- Pass 1: Depth dari sudut pandang cahaya ----------
            Matrix4f lightProj = new Matrix4f().ortho(-10f, 10f, -10f, 10f, 1f, 25f);
            Matrix4f lightView = new Matrix4f().lookAt(lightPos, new Vector3f(0, 0, 0), new Vector3f(0, 1, 0));
            Matrix4f lightSpace = new Matrix4f(lightProj).mul(lightView);

            shadowFbo.bindForWriting();
            glEnable(GL_DEPTH_TEST);
            glDepthFunc(GL_LESS);
            glDisable(GL_CULL_FACE);
            depthShader.use();
            depthShader.setMat4("lightSpaceMatrix", lightSpace);
            depthShader.setMat4("model", sphereModel);
            (smooth ? sphereSmooth : sphereFlat).draw();
            depthShader.setMat4("model", torusModel);
            (smooth ? torusSmooth : torusFlat).draw();
            shadowFbo.unbind(fbW, fbH);

            // ---------- Pass 2: Render normal dari kamera ----------
            glClearColor(0.12f, 0.14f, 0.18f, 1f);
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
            HSRManager.setupDepthTest(depthOn);
            HSRManager.setupBackFaceCulling(cullOn);

            Vector3f target = new Vector3f(0, 1, 0);
            Vector3f camPos = new Vector3f(
                    target.x + dist * (float) (Math.cos(pitch) * Math.cos(yaw)),
                    target.y + dist * (float) Math.sin(pitch),
                    target.z + dist * (float) (Math.cos(pitch) * Math.sin(yaw)));
            Matrix4f view = new Matrix4f().lookAt(camPos, target, new Vector3f(0, 1, 0));
            Matrix4f proj = new Matrix4f().perspective((float) Math.toRadians(45), (float) fbW / Math.max(fbH, 1), 0.1f, 100f);

            sceneShader.use();
            sceneShader.setMat4("view", view);
            sceneShader.setMat4("projection", proj);
            sceneShader.setMat4("lightSpaceMatrix", lightSpace);
            sceneShader.setVec3("lightPos", lightPos);
            sceneShader.setVec3("viewPos", camPos);
            sceneShader.setVec3("lightColor", new Vector3f(1f, 1f, 1f));
            sceneShader.setBool("useTexture", useTex);
            sceneShader.setBool("useBlinnPhong", blinn);
            sceneShader.setBool("useShadow", shadowOn);
            sceneShader.setBool("useBias", biasOn);
            sceneShader.setInt("textureSampler", 0);
            sceneShader.setInt("shadowMap", 1);
            texture.bind(0);
            glActiveTexture(GL_TEXTURE1);
            glBindTexture(GL_TEXTURE_2D, shadowFbo.getDepthTextureID());

            // Urutan gambar: objek dulu, lantai terakhir (supaya efek Z-Buffer OFF terlihat jelas)
            drawObject(torusModel, new Vector3f(0.3f, 0.65f, 1.0f), smooth ? torusSmooth : torusFlat);
            drawObject(sphereModel, new Vector3f(1.0f, 0.4f, 0.3f), smooth ? sphereSmooth : sphereFlat);
            drawObject(planeModel, new Vector3f(0.85f, 0.85f, 0.85f), plane);

            glfwSwapBuffers(window);
            glfwPollEvents();
            updateFps();
        }
    }

    private void drawObject(Matrix4f model, Vector3f color, Mesh mesh) {
        sceneShader.setMat4("model", model);
        sceneShader.setVec3("objectColor", color);
        mesh.draw();
    }

    private void handleCamera(float dt) {
        float rot = 1.6f * dt;
        if (glfwGetKey(window, GLFW_KEY_LEFT) == GLFW_PRESS) yaw -= rot;
        if (glfwGetKey(window, GLFW_KEY_RIGHT) == GLFW_PRESS) yaw += rot;
        if (glfwGetKey(window, GLFW_KEY_UP) == GLFW_PRESS) pitch += rot;
        if (glfwGetKey(window, GLFW_KEY_DOWN) == GLFW_PRESS) pitch -= rot;
        if (glfwGetKey(window, GLFW_KEY_Q) == GLFW_PRESS) dist += 6f * dt;
        if (glfwGetKey(window, GLFW_KEY_E) == GLFW_PRESS) dist -= 6f * dt;
        pitch = clamp(pitch, -1.2f, 1.5f);
        dist = clamp(dist, 4f, 30f);
    }

    private void updateFps() {
        frames++;
        double now = glfwGetTime();
        if (now - fpsTimer >= 0.5) {
            fps = (int) (frames / (now - fpsTimer));
            frames = 0;
            fpsTimer = now;
            glfwSetWindowTitle(window, String.format(
                    "FPS %d | %s | %s | Z-Buffer:%s Cull:%s | Tekstur:%s (%s) | Shadow:%s Bias:%s",
                    fps,
                    blinn ? "Blinn-Phong" : "Phong",
                    smooth ? "Smooth" : "Flat",
                    onOff(depthOn), onOff(cullOn),
                    onOff(useTex), mipmap ? "Bilinear+Mipmap" : "Nearest",
                    onOff(shadowOn), onOff(biasOn)));
        }
    }

    private static String onOff(boolean b) { return b ? "ON" : "OFF"; }

    private static float clamp(float v, float lo, float hi) { return Math.max(lo, Math.min(hi, v)); }
}
