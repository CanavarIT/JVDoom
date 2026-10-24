package com.java.jvdoom;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Draws the original Doom (1993) status bar, face, fonts and menu graphics straight from the WAD.
 * All coordinates are "virtual" Doom coordinates (320x200); {@link #setT} maps them to the screen.
 */
public final class Hud {
    final Gfx gfx;
    final Map<String, Bitmap> cache = new HashMap<>();
    final Paint p = new Paint();               // nearest-neighbour, no smoothing
    final RectF dr = new RectF();
    final String[] fontName = new String[96];
    float sx = 1, sy = 1, ox, oy;

    // ---- face state (st_stuff.c, simplified)
    final Random rnd = new Random();
    String face = "STFST01";
    int lastTic = -1, faceCount, idleCount, idleLook = 1, fireTics, hurtSeen = -1000, grinSeen = -1000;

    public Hud(Gfx g) {
        gfx = g;
        p.setFilterBitmap(false);
        for (int i = 33; i < 96; i++) fontName[i] = String.format("STCFN%03d", i);
    }

    public void setT(float sx, float sy, float ox, float oy) { this.sx = sx; this.sy = sy; this.ox = ox; this.oy = oy; }

    // ------------------------------------------------------------------ patches
    Bitmap bm(String name) {
        if (cache.containsKey(name)) return cache.get(name);
        Bitmap b = null;
        Gfx.Pic pc = gfx.patch(name);
        if (pc != null) {
            int[] px = new int[pc.w * pc.h];
            for (int x = 0; x < pc.w; x++)
                for (int y = 0; y < pc.h; y++)
                    if (pc.op[x * pc.h + y] != 0) px[y * pc.w + x] = gfx.lit[0][pc.pix[x * pc.h + y] & 255];
            b = Bitmap.createBitmap(px, pc.w, pc.h, Bitmap.Config.ARGB_8888);
        }
        cache.put(name, b);
        return b;
    }

    public boolean has(String name) { return gfx.patch(name) != null; }

    /** Draw a patch at virtual (x,y), honouring its offsets like V_DrawPatch, scaled by k. */
    public void patch(Canvas c, String name, float x, float y) { patchK(c, name, x, y, 1f); }

    public void patchK(Canvas c, String name, float x, float y, float k) {
        Gfx.Pic pc = gfx.patch(name);
        Bitmap b = bm(name);
        if (pc == null || b == null) return;
        float l = ox + (x - pc.lo * k) * sx, t = oy + (y - pc.to * k) * sy;
        dr.set(Math.round(l), Math.round(t), Math.round(l + pc.w * k * sx), Math.round(t + pc.h * k * sy));
        c.drawBitmap(b, null, dr, p);
    }

    // ------------------------------------------------------------------ font (STCFN)
    Gfx.Pic glyph(char ch) {
        if (ch >= 'a' && ch <= 'z') ch = (char) (ch - 32);
        if (ch < 33 || ch > 95) return null;
        return gfx.patch(fontName[ch]);
    }

    /** Width in virtual units at scale 1. */
    public float textW(String s) {
        float w = 0;
        for (int i = 0; i < s.length(); i++) { Gfx.Pic g = glyph(s.charAt(i)); w += g != null ? g.w : 4; }
        return w;
    }

    public void text(Canvas c, String s, float x, float y, float k) {
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            Gfx.Pic g = glyph(ch);
            if (g == null) { x += 4 * k; continue; }
            if (ch >= 'a' && ch <= 'z') ch = (char) (ch - 32);
            patchK(c, fontName[ch], x, y, k);
            x += g.w * k;
        }
    }

    // ------------------------------------------------------------------ numbers
    /** Right-aligned number made of prefix0..prefix9 patches, like STlib_drawNum. */
    public void num(Canvas c, String pre, int v, float right, float y) {
        Gfx.Pic z = gfx.patch(pre + "0");
        if (z == null) return;
        boolean neg = v < 0;
        String s = Integer.toString(Math.abs(v));
        float x = right;
        for (int i = s.length() - 1; i >= 0; i--) { x -= z.w; patch(c, pre + s.charAt(i), x, y); }
        if (neg) patch(c, "STTMINUS", x - 8, y);
    }

    // ------------------------------------------------------------------ face logic
    static int painLevel(int health) {
        int h = Math.max(0, Math.min(100, health));
        return ((100 - h) * 5) / 101;
    }

    /** Advance the face once per game tic. */
    public void update(Game g) {
        if (g.tic < lastTic) { hurtSeen = grinSeen = -1000; faceCount = 0; fireTics = 0; }
        if (g.tic == lastTic) return;
        lastTic = g.tic;
        int pain = painLevel(g.health);
        if (g.state == Game.ST_DEAD || g.health <= 0) { face = "STFDEAD0"; return; }
        if (g.invuln > 0) { face = "STFGOD0"; return; }
        if (g.hurtTic > hurtSeen) {
            hurtSeen = g.hurtTic;
            faceCount = 35;
            if (g.hurtAmount > 20) face = "STFOUCH" + pain;
            else if (g.hurtFrom) {
                if (Math.abs(g.hurtAngle) < Math.PI / 4) face = "STFKILL" + pain;
                else face = (g.hurtAngle > 0 ? "STFTL" : "STFTR") + pain + "0";
            } else face = "STFOUCH" + pain;
        } else if (g.grinTic > grinSeen) {
            grinSeen = g.grinTic;
            if (faceCount <= 0) { faceCount = 70; face = "STFEVL" + pain; }
        }
        if (g.inFire) fireTics++; else fireTics = 0;
        if (faceCount > 0) { faceCount--; return; }
        if (fireTics > 70) { face = "STFKILL" + pain; return; }
        if (--idleCount <= 0) { idleLook = rnd.nextInt(3); idleCount = 17; }
        face = "STFST" + pain + idleLook;
    }

    // ------------------------------------------------------------------ status bar
    /** Draws the 320x32 status bar; virtual y = 168 is the top of the bar. */
    public void drawBar(Canvas c, Game g) {
        update(g);
        patch(c, "STBAR", 0, 168);
        patch(c, "STARMS", 104, 168);
        int am = g.currentAmmo();
        if (am >= 0) num(c, "STTNUM", am, 44, 171);
        num(c, "STTNUM", g.health, 90, 171);
        patch(c, "STTPRCNT", 90, 171);
        num(c, "STTNUM", g.armor, 221, 171);
        patch(c, "STTPRCNT", 221, 171);
        boolean[] arms = {g.owned[Game.W_PISTOL], g.owned[Game.W_SHOTGUN] || g.owned[Game.W_SSG], g.owned[Game.W_CHAINGUN],
                g.owned[Game.W_ROCKET], g.owned[Game.W_PLASMA], g.owned[Game.W_BFG]};
        for (int i = 0; i < 6; i++)
            patch(c, (arms[i] ? "STYSNUM" : "STGNUM") + (i + 2), 111 + (i % 3) * 12, 172 + (i / 3) * 10);
        patch(c, face, 143, 168);
        for (int i = 0; i < 3; i++) if (g.keys[i]) patch(c, "STKEYS" + i, 239, 171 + 10 * i);
        for (int i = 0; i < 4; i++) {
            num(c, "STYSNUM", g.ammo[i], 288, 173 + 6 * i);
            num(c, "STYSNUM", g.maxAmmo[i], 314, 173 + 6 * i);
        }
    }

    /** Compact overlay for full-screen mode (no status bar): health, armor, ammo with the big Doom digits. */
    public void drawMinimal(Canvas c, Game g, float sw, float sh, float s, float margin) {
        update(g);
        float k = s, kY = s * 1.2f;
        float by = sh - margin - 16 * kY;
        setT(k, kY, margin, by);
        num(c, "STTNUM", g.health, 42, 0);
        patch(c, "STTPRCNT", 42, 0);
        setT(k, kY, margin + 62 * k, by);
        num(c, "STTNUM", g.armor, 42, 0);
        patch(c, "STTPRCNT", 42, 0);
        int am = g.currentAmmo();
        if (am >= 0) {
            setT(k, kY, sw - margin - 44 * k, by);
            num(c, "STTNUM", am, 44, 0);
        }
        setT(k, kY, margin, by - 14 * kY);
        for (int i = 0; i < 3; i++) if (g.keys[i]) patch(c, "STKEYS" + i, i * 9, 0);
    }
}
