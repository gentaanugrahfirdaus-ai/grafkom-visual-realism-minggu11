package com.grafkom.viewing3d;

/** Pembangkit data geometri (posisi, UV, indeks). Semua segitiga berurutan CCW (menghadap keluar). */
public class Geometry {

    public record MeshData(float[] pos, float[] uv, int[] idx) {}

    public static MeshData sphere(int stacks, int slices, float r) {
        int vc = (stacks + 1) * (slices + 1);
        float[] pos = new float[vc * 3];
        float[] uv = new float[vc * 2];
        int[] idx = new int[stacks * slices * 6];
        int p = 0, t = 0;
        for (int i = 0; i <= stacks; i++) {
            double phi = Math.PI * i / stacks;
            for (int j = 0; j <= slices; j++) {
                double theta = 2.0 * Math.PI * j / slices;
                pos[p++] = (float) (r * Math.sin(phi) * Math.cos(theta));
                pos[p++] = (float) (r * Math.cos(phi));
                pos[p++] = (float) (r * Math.sin(phi) * Math.sin(theta));
                uv[t++] = (float) j / slices;
                uv[t++] = 1f - (float) i / stacks;
            }
        }
        int k = 0;
        for (int i = 0; i < stacks; i++) {
            for (int j = 0; j < slices; j++) {
                int first = i * (slices + 1) + j;
                int second = first + slices + 1;
                idx[k++] = first;   idx[k++] = first + 1;  idx[k++] = second;
                idx[k++] = first + 1; idx[k++] = second + 1; idx[k++] = second;
            }
        }
        return new MeshData(pos, uv, idx);
    }

    public static MeshData torus(int rings, int sides, float R, float r) {
        int vc = (rings + 1) * (sides + 1);
        float[] pos = new float[vc * 3];
        float[] uv = new float[vc * 2];
        int[] idx = new int[rings * sides * 6];
        int p = 0, t = 0;
        for (int i = 0; i <= rings; i++) {
            double th = 2.0 * Math.PI * i / rings;
            for (int j = 0; j <= sides; j++) {
                double ph = 2.0 * Math.PI * j / sides;
                double ring = R + r * Math.cos(ph);
                pos[p++] = (float) (ring * Math.cos(th));
                pos[p++] = (float) (r * Math.sin(ph));
                pos[p++] = (float) (ring * Math.sin(th));
                uv[t++] = (float) i / rings * 2f;
                uv[t++] = (float) j / sides;
            }
        }
        int k = 0;
        for (int i = 0; i < rings; i++) {
            for (int j = 0; j < sides; j++) {
                int a = i * (sides + 1) + j;
                int b = (i + 1) * (sides + 1) + j;
                int c = a + 1;
                int d = b + 1;
                idx[k++] = a; idx[k++] = c; idx[k++] = b;
                idx[k++] = b; idx[k++] = c; idx[k++] = d;
            }
        }
        return new MeshData(pos, uv, idx);
    }

    /** Bidang lantai di y = 0, normal menghadap atas. */
    public static MeshData plane(float s, float uvRepeat) {
        float[] pos = {
                -s, 0, -s,
                -s, 0,  s,
                 s, 0,  s,
                 s, 0, -s
        };
        float[] uv = {
                0, 0,
                0, uvRepeat,
                uvRepeat, uvRepeat,
                uvRepeat, 0
        };
        int[] idx = {0, 1, 2, 0, 2, 3};
        return new MeshData(pos, uv, idx);
    }
}
