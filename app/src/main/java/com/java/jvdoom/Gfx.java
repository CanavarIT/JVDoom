package com.java.jvdoom;

import java.util.HashMap;
import java.util.Map;

/** Palette, colormaps, patches, composite wall textures, flats and sprite frames decoded from a WAD. */
public final class Gfx {

    /** Column-major picture: pix[x*h+y] = palette index, op[x*h+y] = 1 if opaque (null when fully opaque). */
    public static final class Pic {
        public int w, h, lo, to;
        public byte[] pix;
        public byte[] op;
    }

    /** One sprite (4-letter prefix) with all frames and rotations. */
    public static final class SpriteSet {
        public final Pic[][] pic = new Pic[29][8];
        public final boolean[][] flip = new boolean[29][8];
        public final boolean[] rotates = new boolean[29];
        public final boolean[] present = new boolean[29];
        public int maxFrame = -1;
    }

    public final Wad wad;
    public final byte[] playpal;          // 14*768
    public final byte[] colormap;         // 34*256
    public final int[][] lit = new int[34][256];   // ARGB per colormap level
    public final Map<String, Pic> textures = new HashMap<>();
    public final Map<String, byte[]> flats = new HashMap<>();
    public final Map<String, SpriteSet> sprites = new HashMap<>();
    private final Map<String, Pic> patchCache = new HashMap<>();

    public Gfx(Wad w) {
        wad = w;
        playpal = w.bytes("PLAYPAL");
        byte[] cm = w.bytes("COLORMAP");
        if (playpal == null || playpal.length < 768) throw new IllegalStateException("PLAYPAL not found - this is not a Doom IWAD");
        if (cm == null || cm.length < 34 * 256) {
            // Fallback: generate colormap from palette
            cm = new byte[34 * 256];
            for (int l = 0; l < 32; l++)
                for (int i = 0; i < 256; i++) cm[l * 256 + i] = (byte) nearest(playpal,
                        (playpal[i * 3] & 255) * (32 - l) / 32, (playpal[i * 3 + 1] & 255) * (32 - l) / 32, (playpal[i * 3 + 2] & 255) * (32 - l) / 32);
            for (int i = 0; i < 256; i++) { cm[32 * 256 + i] = (byte) i; cm[33 * 256 + i] = (byte) i; }
        }
        colormap = cm;
        for (int l = 0; l < 34; l++)
            for (int i = 0; i < 256; i++) {
                int c = colormap[l * 256 + i] & 255;
                lit[l][i] = 0xFF000000 | (playpal[c * 3] & 255) << 16 | (playpal[c * 3 + 1] & 255) << 8 | (playpal[c * 3 + 2] & 255);
            }
        loadFlats();
        loadTextures();
        loadSprites();
    }

    private static int nearest(byte[] pal, int r, int g, int b) {
        int best = 0, bd = Integer.MAX_VALUE;
        for (int i = 0; i < 256; i++) {
            int dr = (pal[i * 3] & 255) - r, dg = (pal[i * 3 + 1] & 255) - g, db = (pal[i * 3 + 2] & 255) - b;
            int d = dr * dr + dg * dg + db * db;
            if (d < bd) { bd = d; best = i; }
        }
        return best;
    }

    // ---------------------------------------------------------------- patches
    public Pic decodePatch(byte[] d) {
        if (d == null || d.length < 8) return null;
        Pic p = new Pic();
        p.w = Wad.u16(d, 0); p.h = Wad.u16(d, 2); p.lo = Wad.s16(d, 4); p.to = Wad.s16(d, 6);
        if (p.w <= 0 || p.h <= 0 || p.w > 4096 || p.h > 4096 || d.length < 8 + p.w * 4) return null;
        p.pix = new byte[p.w * p.h];
        p.op = new byte[p.w * p.h];
        boolean tall = p.h > 254;
        for (int x = 0; x < p.w; x++) {
            int o = Wad.i32(d, 8 + x * 4);
            if (o < 0 || o >= d.length) continue;
            int last = -1;
            while (o < d.length) {
                int td = d[o] & 255;
                if (td == 255) break;
                if (o + 1 >= d.length) break;
                int len = d[o + 1] & 255;
                if (tall && td <= last) td += last;
                last = td;
                int src = o + 3;
                for (int i = 0; i < len; i++) {
                    int y = td + i;
                    if (y >= 0 && y < p.h && src + i < d.length) {
                        p.pix[x * p.h + y] = d[src + i];
                        p.op[x * p.h + y] = 1;
                    }
                }
                o += len + 4;
            }
        }
        return p;
    }

    public Pic patch(String name) {
        name = name.toUpperCase();
        if (patchCache.containsKey(name)) return patchCache.get(name);
        int i = wad.find(name);
        Pic p = i < 0 ? null : decodePatch(wad.bytes(i));
        patchCache.put(name, p);
        return p;
    }

    // ---------------------------------------------------------------- flats
    private void loadFlats() {
        int s = wad.find("F_START"), e = wad.find("F_END");
        if (s < 0) s = wad.find("FF_START");
        if (e < 0) e = wad.find("FF_END");
        if (s < 0 || e < 0) return;
        for (int i = s + 1; i < e; i++) {
            String n = wad.name(i);
            if (n.endsWith("_START") || n.endsWith("_END")) continue;
            if (wad.size(i) >= 4096) flats.put(n, wad.bytes(i));
            else if (wad.size(i) == 64 * 64) flats.put(n, wad.bytes(i));
        }
    }

    // ---------------------------------------------------------------- textures
    private void loadTextures() {
        byte[] pn = wad.bytes("PNAMES");
        if (pn == null) return;
        int np = Wad.i32(pn, 0);
        String[] pnames = new String[np];
        for (int i = 0; i < np && 4 + i * 8 + 8 <= pn.length; i++) pnames[i] = Wad.cstr(pn, 4 + i * 8, 8).toUpperCase();
        for (String tl : new String[]{"TEXTURE1", "TEXTURE2"}) {
            byte[] t = wad.bytes(tl);
            if (t == null || t.length < 4) continue;
            int n = Wad.i32(t, 0);
            for (int i = 0; i < n; i++) {
                int o = Wad.i32(t, 4 + i * 4);
                if (o < 0 || o + 22 > t.length) continue;
                String name = Wad.cstr(t, o, 8).toUpperCase();
                int w = Wad.s16(t, o + 12), h = Wad.s16(t, o + 14), pc = Wad.s16(t, o + 20);
                if (w <= 0 || h <= 0 || w > 2048 || h > 2048) continue;
                Pic tx = new Pic();
                tx.w = w; tx.h = h;
                tx.pix = new byte[w * h];
                byte[] cov = new byte[w * h];
                for (int k = 0; k < pc; k++) {
                    int po = o + 22 + k * 10;
                    if (po + 10 > t.length) break;
                    int ox = Wad.s16(t, po), oy = Wad.s16(t, po + 2), pi = Wad.s16(t, po + 4);
                    if (pi < 0 || pi >= np || pnames[pi] == null) continue;
                    Pic p = patch(pnames[pi]);
                    if (p == null) continue;
                    for (int x = 0; x < p.w; x++) {
                        int tx0 = ox + x;
                        if (tx0 < 0 || tx0 >= w) continue;
                        for (int y = 0; y < p.h; y++) {
                            int ty = oy + y;
                            if (ty < 0 || ty >= h) continue;
                            if (p.op[x * p.h + y] != 0) {
                                tx.pix[tx0 * h + ty] = p.pix[x * p.h + y];
                                cov[tx0 * h + ty] = 1;
                            }
                        }
                    }
                }
                boolean holes = false;
                for (byte c : cov) if (c == 0) { holes = true; break; }
                tx.op = holes ? cov : null;
                textures.put(name, tx);
            }
        }
    }

    public Pic texture(String name) {
        if (name == null || name.isEmpty() || name.charAt(0) == '-') return null;
        return textures.get(name.toUpperCase());
    }

    // ---------------------------------------------------------------- sprites
    private void loadSprites() {
        int s = wad.find("S_START"), e = wad.find("S_END");
        if (s < 0) s = wad.find("SS_START");
        if (e < 0) e = wad.find("SS_END");
        if (s < 0 || e < 0) return;
        for (int i = s + 1; i < e; i++) {
            String n = wad.name(i);
            if (n.length() < 6 || wad.size(i) < 8) continue;
            String pre = n.substring(0, 4);
            Pic pic = null;
            SpriteSet ss = sprites.get(pre);
            if (ss == null) { ss = new SpriteSet(); sprites.put(pre, ss); }
            int f1 = n.charAt(4) - 'A', r1 = n.charAt(5) - '0';
            for (int pass = 0; pass < 2; pass++) {
                int f = pass == 0 ? f1 : (n.length() >= 8 ? n.charAt(6) - 'A' : -1);
                int r = pass == 0 ? r1 : (n.length() >= 8 ? n.charAt(7) - '0' : -1);
                if (f < 0 || f >= 29 || r < 0 || r > 8) continue;
                if (pic == null) pic = decodePatch(wad.bytes(i));
                if (pic == null) continue;
                ss.present[f] = true;
                if (f > ss.maxFrame) ss.maxFrame = f;
                if (r == 0) {
                    for (int k = 0; k < 8; k++) { ss.pic[f][k] = pic; ss.flip[f][k] = pass == 1; }
                    ss.rotates[f] = false;
                } else {
                    ss.pic[f][r - 1] = pic; ss.flip[f][r - 1] = pass == 1;
                    ss.rotates[f] = true;
                }
            }
        }
    }

    public boolean hasSprite(String prefix, int frame) {
        SpriteSet ss = sprites.get(prefix);
        return ss != null && frame >= 0 && frame < 29 && ss.present[frame];
    }
}
