package com.grafkom.viewing3d;

import org.lwjgl.system.MemoryStack;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;

import static org.lwjgl.opengl.GL33.*;
import static org.lwjgl.stb.STBImage.*;
import static org.lwjgl.system.MemoryUtil.*;

/** Memuat PNG/JPG dengan STBImage dari classpath ke GPU, lengkap dengan mipmap. */
public class Texture2D {

    public enum FilterMode {
        NEAREST("Nearest"),
        BILINEAR_MIPMAP("Bilinear + Mipmap");

        public final String label;
        FilterMode(String label) { this.label = label; }
    }

    private final int textureID;

    /** @param resourcePath path classpath, mis. "/wood.jpg" */
    public Texture2D(String resourcePath) {
        textureID = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, textureID);

        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);

        if (!loadFromResource(resourcePath)) {
            System.err.println("Failed to load texture: " + resourcePath + " -> memakai tekstur checkerboard pengganti");
            uploadCheckerboard();
        }
        glGenerateMipmap(GL_TEXTURE_2D);
        setFilterMode(FilterMode.BILINEAR_MIPMAP);
    }

    private boolean loadFromResource(String resourcePath) {
        ByteBuffer fileData = null;
        try (InputStream in = Texture2D.class.getResourceAsStream(resourcePath)) {
            if (in == null) return false;
            byte[] bytes = in.readAllBytes();
            fileData = memAlloc(bytes.length);
            fileData.put(bytes).flip();
        } catch (IOException e) {
            return false;
        }

        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer width = stack.mallocInt(1);
            IntBuffer height = stack.mallocInt(1);
            IntBuffer channels = stack.mallocInt(1);

            stbi_set_flip_vertically_on_load(true); // v=0 di bawah gambar (konvensi OpenGL)
            ByteBuffer pixels = stbi_load_from_memory(fileData, width, height, channels, 4);
            if (pixels == null) return false;

            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width.get(0), height.get(0), 0,
                    GL_RGBA, GL_UNSIGNED_BYTE, pixels);
            stbi_image_free(pixels);
            return true;
        } finally {
            memFree(fileData);
        }
    }

    private void uploadCheckerboard() {
        int size = 64;
        ByteBuffer data = memAlloc(size * size * 4);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                boolean light = ((x / 8) + (y / 8)) % 2 == 0;
                int c = light ? 220 : 90;
                data.put((byte) c).put((byte) (c * 3 / 4)).put((byte) (c / 2)).put((byte) 255);
            }
        }
        data.flip();
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, size, size, 0, GL_RGBA, GL_UNSIGNED_BYTE, data);
        memFree(data);
    }

    /** Nearest Neighbor (blocky) vs Bilinear + Mipmapping (halus, bebas moire). */
    public void setFilterMode(FilterMode mode) {
        glBindTexture(GL_TEXTURE_2D, textureID);
        if (mode == FilterMode.NEAREST) {
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        } else {
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        }
    }

    public void bind(int unit) {
        glActiveTexture(GL_TEXTURE0 + unit);
        glBindTexture(GL_TEXTURE_2D, textureID);
    }

    public int getTextureID() { return textureID; }

    public void cleanup() { glDeleteTextures(textureID); }
}
