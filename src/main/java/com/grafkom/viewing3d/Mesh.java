package com.grafkom.viewing3d;

import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

/**
 * Mesh 3D dengan atribut: posisi(3) + normal(3) + UV(2).
 * smooth = true  -> Smooth Shading: normal vertex = rata-rata normal semua face yang berbagi vertex.
 * smooth = false -> Flat Shading: tiap segitiga punya 3 vertex sendiri dengan normal face yang sama.
 */
public class Mesh {
    private final int vao;
    private final int vbo;
    private int ebo = 0;
    private final int count;
    private final boolean indexed;

    public Mesh(Geometry.MeshData d, boolean smooth) {
        float[] data;
        if (smooth) {
            float[] normals = computeSmoothNormals(d.pos(), d.idx());
            data = new float[(d.pos().length / 3) * 8];
            for (int i = 0; i < d.pos().length / 3; i++) {
                put(data, i * 8, d.pos(), i, normals, i, d.uv(), i);
            }
            count = d.idx().length;
            indexed = true;
        } else {
            int[] idx = d.idx();
            data = new float[idx.length * 8];
            for (int t = 0; t < idx.length; t += 3) {
                float[] fn = faceNormal(d.pos(), idx[t], idx[t + 1], idx[t + 2]);
                if (fn[0] == 0 && fn[1] == 0 && fn[2] == 0) fn = new float[]{0, 1, 0};
                for (int k = 0; k < 3; k++) {
                    int v = idx[t + k];
                    put(data, (t + k) * 8, d.pos(), v, fn, 0, d.uv(), v);
                }
            }
            count = idx.length;
            indexed = false;
        }

        vao = glGenVertexArrays();
        glBindVertexArray(vao);
        vbo = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, data, GL_STATIC_DRAW);
        if (indexed) {
            ebo = glGenBuffers();
            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ebo);
            glBufferData(GL_ELEMENT_ARRAY_BUFFER, d.idx(), GL_STATIC_DRAW);
        }
        int stride = 8 * Float.BYTES;
        glVertexAttribPointer(0, 3, GL_FLOAT, false, stride, 0);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(1, 3, GL_FLOAT, false, stride, 3L * Float.BYTES);
        glEnableVertexAttribArray(1);
        glVertexAttribPointer(2, 2, GL_FLOAT, false, stride, 6L * Float.BYTES);
        glEnableVertexAttribArray(2);
        glBindVertexArray(0);
    }

    private static void put(float[] out, int o, float[] pos, int pi, float[] nrm, int ni, float[] uv, int ui) {
        out[o] = pos[pi * 3];     out[o + 1] = pos[pi * 3 + 1]; out[o + 2] = pos[pi * 3 + 2];
        out[o + 3] = nrm[ni * 3]; out[o + 4] = nrm[ni * 3 + 1]; out[o + 5] = nrm[ni * 3 + 2];
        out[o + 6] = uv[ui * 2];  out[o + 7] = uv[ui * 2 + 1];
    }

    /** Normal face (ternormalisasi) = normalize(cross(p1-p0, p2-p0)). Nol jika segitiga degenerate. */
    private static float[] faceNormal(float[] p, int a, int b, int c) {
        float ux = p[b * 3] - p[a * 3],     uy = p[b * 3 + 1] - p[a * 3 + 1], uz = p[b * 3 + 2] - p[a * 3 + 2];
        float vx = p[c * 3] - p[a * 3],     vy = p[c * 3 + 1] - p[a * 3 + 1], vz = p[c * 3 + 2] - p[a * 3 + 2];
        float nx = uy * vz - uz * vy;
        float ny = uz * vx - ux * vz;
        float nz = ux * vy - uy * vx;
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len < 1e-8f) return new float[]{0, 0, 0};
        return new float[]{nx / len, ny / len, nz / len};
    }

    private static String key(float[] p, int i) {
        return Math.round(p[i * 3] * 10000f) + "," + Math.round(p[i * 3 + 1] * 10000f) + "," + Math.round(p[i * 3 + 2] * 10000f);
    }

    /** N_vertex = (sum N_face_i) / ||sum N_face_i||  (vertex dengan posisi sama digabung agar tidak ada seam). */
    private static float[] computeSmoothNormals(float[] pos, int[] idx) {
        Map<String, float[]> acc = new HashMap<>();
        for (int t = 0; t < idx.length; t += 3) {
            float[] fn = faceNormal(pos, idx[t], idx[t + 1], idx[t + 2]);
            for (int k = 0; k < 3; k++) {
                float[] s = acc.computeIfAbsent(key(pos, idx[t + k]), x -> new float[3]);
                s[0] += fn[0]; s[1] += fn[1]; s[2] += fn[2];
            }
        }
        int vc = pos.length / 3;
        float[] out = new float[vc * 3];
        for (int i = 0; i < vc; i++) {
            float[] s = acc.get(key(pos, i));
            float len = s == null ? 0 : (float) Math.sqrt(s[0] * s[0] + s[1] * s[1] + s[2] * s[2]);
            if (len < 1e-8f) {
                out[i * 3 + 1] = 1f;
            } else {
                out[i * 3] = s[0] / len; out[i * 3 + 1] = s[1] / len; out[i * 3 + 2] = s[2] / len;
            }
        }
        return out;
    }

    public void draw() {
        glBindVertexArray(vao);
        if (indexed) glDrawElements(GL_TRIANGLES, count, GL_UNSIGNED_INT, 0);
        else glDrawArrays(GL_TRIANGLES, 0, count);
        glBindVertexArray(0);
    }
}
