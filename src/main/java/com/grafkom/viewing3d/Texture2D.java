package com.grafkom.viewing3d;

import org.lwjgl.BufferUtils;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.glActiveTexture;
import static org.lwjgl.opengl.GL30.glGenerateMipmap;
import static org.lwjgl.stb.STBImage.*;

/** Praktikum 3: Texture loader (STBImage) + UV mapping + filtering. */
public class Texture2D {
    private final int textureID;

    /** Muat dari file PNG/JPG. */
    public Texture2D(String filePath) {
        textureID = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, textureID);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);

        stbi_set_flip_vertically_on_load(true);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer width = stack.mallocInt(1);
            IntBuffer height = stack.mallocInt(1);
            IntBuffer channels = stack.mallocInt(1);
            ByteBuffer data = stbi_load(filePath, width, height, channels, 4);
            if (data != null) {
                glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width.get(0), height.get(0), 0,
                        GL_RGBA, GL_UNSIGNED_BYTE, data);
                glGenerateMipmap(GL_TEXTURE_2D);
                stbi_image_free(data);
            } else {
                System.err.println("Failed to load texture: " + filePath);
            }
        }
        setMipmapFiltering(true);
    }

    /** Tekstur papan catur prosedural (tanpa file gambar). */
    public Texture2D(int size, int tile) {
        textureID = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, textureID);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);

        ByteBuffer buf = BufferUtils.createByteBuffer(size * size * 4);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                boolean light = ((x / tile) + (y / tile)) % 2 == 0;
                byte c = (byte) (light ? 235 : 110);
                buf.put(c).put(c).put(c).put((byte) 255);
            }
        }
        buf.flip();
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, size, size, 0, GL_RGBA, GL_UNSIGNED_BYTE, buf);
        glGenerateMipmap(GL_TEXTURE_2D);
        setMipmapFiltering(true);
    }

    /** true = Bilinear + Mipmap (trilinear), false = Nearest Neighbor. */
    public void setMipmapFiltering(boolean mipmapped) {
        glBindTexture(GL_TEXTURE_2D, textureID);
        if (mipmapped) {
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        } else {
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        }
    }

    public void bind(int unit) {
        glActiveTexture(GL_TEXTURE0 + unit);
        glBindTexture(GL_TEXTURE_2D, textureID);
    }
}
