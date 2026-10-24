package com.java.jvdoom;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Software renderer: BSP front-to-back walls with per-column floor/ceiling spans, per-pixel depth buffer for
 * sprites and masked textures. Pure Java, renders into an int[] ARGB buffer.
 */
public final class Renderer {
    public final int W, H;
    public final int[] fb;
    final float[] depth;
    final float cx, cy, focal, focalY;
    static final float NEAR = 1f, VSTRETCH = 1.2f;

    final Gfx gfx;
    Level L;
    Gfx.Pic sky;

    // per-frame view
    float vx, vy, vz, ang, cosA, sinA;
    final int[] top, bot;
    final boolean[] closed;
    int closedCount;
    final float[] dirX, dirY, skyU;
    final float[] absDy, invDy;

    static final class Masked {
        Level.Seg seg; int x1, x2; float sx1, sx2, iz1, iz2, uz1, uz2, key; int lightnum; Level.Sector fs, bs;
    }
    static final class Drawable { float z; Mobj m; Masked s; }
    final List<Masked> masked = new ArrayList<>();
    final List<Drawable> drawables = new ArrayList<>();
    int frameCount;
    public float alpha = 1f;
    float ix(Mobj m) { return m.ox + (m.x - m.ox) * alpha; }
    float iy(Mobj m) { return m.oy + (m.y - m.oy) * alpha; }
    float izz(Mobj m) { return m.oz + (m.z - m.oz) * alpha; }

    public Renderer(Gfx g, int w, int h) {
        gfx = g; W = w; H = h;
        fb = new int[W * H];
        depth = new float[W * H];
        cx = W / 2f; cy = H / 2f;
        focal = 0.8f * H;
        focalY = focal * VSTRETCH;
        top = new int[W]; bot = new int[W]; closed = new boolean[W];
        dirX = new float[W]; dirY = new float[W]; skyU = new float[W];
        absDy = new float[H]; invDy = new float[H];
        for (int y = 0; y < H; y++) {
            float d = Math.abs(y + 0.5f - cy);
            if (d < 0.01f) d = 0.01f;
            absDy[y] = d; invDy[y] = 1f / d;
        }
    }

    public void setLevel(Level l, Gfx.Pic skyPic) { L = l; sky = skyPic; }

    static int shadeLevel(int lightnum, float z) {
        if (lightnum < 0) lightnum = 0; else if (lightnum > 15) lightnum = 15;
        int j = (int) (2560f / z);
        if (j > 47) j = 47;
        int lvl = (15 - lightnum) * 4 - (j >> 1);
        return lvl < 0 ? 0 : (lvl > 31 ? 31 : lvl);
    }

    // =================================================================== frame
    public void render(float px, float py, float eyeZ, float angle, List<Mobj> mobjs, Mobj self,
                       Gfx.SpriteSet wpn, int wpnFrame, Gfx.SpriteSet flash, int flashFrame, float wsx, float wsy,
                       int playerLight, int fixedMap) {
        vx = px; vy = py; vz = eyeZ; ang = angle;
        cosA = (float) Math.cos(angle); sinA = (float) Math.sin(angle);
        Arrays.fill(fb, 0xFF000000);
        Arrays.fill(depth, Float.MAX_VALUE);
        Arrays.fill(top, 0); Arrays.fill(bot, H - 1); Arrays.fill(closed, false);
        closedCount = 0;
        masked.clear(); drawables.clear();
        for (int x = 0; x < W; x++) {
            float xn = (x + 0.5f - cx) / focal;
            dirX[x] = cosA + sinA * xn;
            dirY[x] = sinA - cosA * xn;
            skyU[x] = (float) ((angle - Math.atan(xn)) / (Math.PI / 2));   // fraction of sky texture width (covers 90 deg)
        }
        fixedLevel = fixedMap;
        if (L != null && L.ssNum.length > 0) {
            if (L.nx.length == 0) subsector(0); else bsp(L.nx.length - 1);
        }
        drawSpritesAndMasked(mobjs, self);
        drawWeapon(wpn, wpnFrame, flash, flashFrame, wsx, wsy, playerLight);
        frameCount++;
    }

    int fixedLevel = -1;   // >=0 forces colormap row (invulnerability)

    // =================================================================== BSP
    void bsp(int node) {
        if (closedCount >= W) return;
        if ((node & 0x8000) != 0) { subsector(node & 0x7FFF); return; }
        int side = L.pointOnSide(vx, vy, node);
        bsp(L.nchild[node * 2 + side]);
        if (closedCount < W && checkBox(node, side ^ 1)) bsp(L.nchild[node * 2 + (side ^ 1)]);
    }

    boolean checkBox(int node, int side) {
        float[] b = L.nbb[node];
        int o = side * 4;
        float bt = b[o], bb = b[o + 1], bl = b[o + 2], br = b[o + 3];
        if (vx >= bl && vx <= br && vy >= bb && vy <= bt) return true;
        float minSx = 1e9f, maxSx = -1e9f;
        int behind = 0;
        for (int i = 0; i < 4; i++) {
            float x = (i & 1) == 0 ? bl : br, y = (i & 2) == 0 ? bb : bt;
            float dx = x - vx, dy = y - vy;
            float z = dx * cosA + dy * sinA;
            if (z < NEAR) { behind++; continue; }
            float xr = dx * sinA - dy * cosA;
            float sx = cx + xr * focal / z;
            if (sx < minSx) minSx = sx;
            if (sx > maxSx) maxSx = sx;
        }
        if (behind == 4) return false;
        if (behind > 0) return true;
        int x0 = Math.max(0, (int) Math.floor(minSx)), x1 = Math.min(W - 1, (int) Math.ceil(maxSx));
        if (x0 > x1) return false;
        for (int x = x0; x <= x1; x++) if (!closed[x]) return true;
        return false;
    }

    void subsector(int ss) {
        if (ss >= L.ssNum.length) return;
        int n = L.ssNum[ss], f = L.ssFirst[ss];
        for (int i = 0; i < n && f + i < L.segs.length; i++) addSeg(L.segs[f + i]);
    }

    // =================================================================== walls
    void addSeg(Level.Seg s) {
        if (closedCount >= W) return;
        float cross = (s.x2 - s.x1) * (vy - s.y1) - (s.y2 - s.y1) * (vx - s.x1);
        if (cross >= 0) return;                       // back-facing
        s.line.mapped = true;

        float ax = s.x1 - vx, ay = s.y1 - vy, bx = s.x2 - vx, by = s.y2 - vy;
        float za = ax * cosA + ay * sinA, xa = ax * sinA - ay * cosA;
        float zb = bx * cosA + by * sinA, xb = bx * sinA - by * cosA;
        if (za < NEAR && zb < NEAR) return;
        float u1 = 0, u2 = s.len;
        if (za < NEAR) {
            float t = (NEAR - za) / (zb - za);
            xa += t * (xb - xa); u1 = t * s.len; za = NEAR;
        } else if (zb < NEAR) {
            float t = (NEAR - za) / (zb - za);
            xb = xa + t * (xb - xa); u2 = t * s.len; zb = NEAR;
        }
        float sx1 = cx + xa * focal / za, sx2 = cx + xb * focal / zb;
        if (sx1 >= sx2) return;
        int x1 = Math.max(0, (int) Math.ceil(sx1 - 0.5f));
        int x2 = Math.min(W - 1, (int) Math.ceil(sx2 - 0.5f) - 1);
        if (x1 > x2) return;
        // quick reject if all columns closed
        boolean any = false;
        for (int x = x1; x <= x2; x++) if (!closed[x]) { any = true; break; }
        if (!any) return;

        Level.Sector fs = s.front, bs = s.back;
        Level.Side side = s.side;
        float uOff = s.offset + side.xoff;
        float iz1 = 1f / za, iz2 = 1f / zb;
        float uz1 = (u1 + uOff) * iz1, uz2 = (u2 + uOff) * iz2;

        int lightnum = fs.light >> 4;
        if (s.y1 == s.y2) lightnum--; else if (s.x1 == s.x2) lightnum++;

        float worldTop = fs.ceil, worldBot = fs.floor;
        boolean solid = bs == null;
        float backTop = 0, backBot = 0;
        boolean drawUpper = false, drawLower = false, isClosed = false;
        Gfx.Pic midTex = null, upTex = null, loTex = null;
        float vtopMid = 0, vtopUp = 0, vtopLo = 0;
        boolean skyBoth = false;
        float openTop, openBot;
        if (solid) {
            midTex = side.mid;
            if ((s.line.flags & Level.ML_DONTPEGBOTTOM) != 0)
                vtopMid = fs.floor + (midTex != null ? midTex.h : 0);
            else vtopMid = fs.ceil;
            openTop = worldTop; openBot = worldBot;
        } else {
            backTop = bs.ceil; backBot = bs.floor;
            if (fs.skyCeil && bs.skyCeil) { worldTop = backTop; skyBoth = true; }
            isClosed = bs.ceil <= fs.floor || bs.floor >= fs.ceil;
            drawUpper = backTop < worldTop && !(skyBoth);
            drawLower = backBot > worldBot;
            if (drawUpper) {
                upTex = side.upper;
                vtopUp = (s.line.flags & Level.ML_DONTPEGTOP) != 0 ? worldTop : backTop + (upTex != null ? upTex.h : 0);
            }
            if (drawLower) {
                loTex = side.lower;
                vtopLo = (s.line.flags & Level.ML_DONTPEGBOTTOM) != 0 ? worldTop : backBot;
            }
            openTop = Math.min(fs.ceil, bs.ceil);
            openBot = Math.max(fs.floor, bs.floor);
            if (side.mid != null) {
                Masked m = new Masked();
                m.seg = s; m.x1 = x1; m.x2 = x2; m.sx1 = sx1; m.sx2 = sx2; m.iz1 = iz1; m.iz2 = iz2;
                m.uz1 = uz1; m.uz2 = uz2; m.lightnum = lightnum; m.fs = fs; m.bs = bs;
                m.key = 2f / (iz1 + iz2);
                masked.add(m);
            }
        }

        boolean drawCeil = fs.ceil > vz || fs.skyCeil;
        boolean drawFloor = fs.floor < vz;
        float ceilHd = fs.ceil - vz, floorHd = vz - fs.floor;
        float iscale = 1f / (sx2 - sx1);
        boolean ceilSky = fs.skyCeil;
        byte[] ceilPic = fs.ceilPic, floorPic = fs.floorPic;
        int planeLight = fs.light >> 4;

        for (int x = x1; x <= x2; x++) {
            if (closed[x]) continue;
            float xc = x + 0.5f;
            float t = (xc - sx1) * iscale;
            float iz = iz1 + (iz2 - iz1) * t;
            float z = 1f / iz;
            float scaleY = focalY * iz;
            float ytF = cy - (worldTop - vz) * scaleY;
            float ybF = cy - (worldBot - vz) * scaleY;
            int yt = (int) Math.ceil(ytF - 0.5f);                 // first wall row
            int yb = (int) Math.ceil(ybF - 0.5f) - 1;             // last wall row
            int tp = top[x], bt = bot[x];

            // ceiling
            if (drawCeil) {
                int ce = Math.min(yt - 1, bt);
                if (ce >= tp) {
                    if (ceilSky) drawSkyColumn(x, tp, ce);
                    else if (ceilPic != null && ceilHd > 0.01f) drawFlatColumn(x, tp, ce, ceilPic, ceilHd, planeLight, false);
                }
            }
            // floor
            if (drawFloor) {
                int fst = Math.max(yb + 1, tp);
                if (fst <= bt && floorPic != null && floorHd > 0.01f) drawFlatColumn(x, fst, bt, floorPic, floorHd, planeLight, true);
            }

            int lvl = fixedLevel >= 0 ? fixedLevel : shadeLevel(lightnum, z);
            int[] lt = gfx.lit[lvl];
            float uCol = (uz1 + (uz2 - uz1) * t) * z;

            if (solid) {
                int y0 = Math.max(yt, tp), y1 = Math.min(yb, bt);
                if (y0 <= y1 && midTex != null)
                    drawWallColumn(x, y0, y1, midTex, uCol, vtopMid - vz + side.yoff, z, lt, yt);
                else if (y0 <= y1) fillColumn(x, y0, y1, z, 0xFF000000);
                top[x] = H; bot[x] = -1;
                closed[x] = true; closedCount++;
            } else {
                int newTop = tp, newBot = bt;
                if (drawUpper) {
                    float ytU = cy - (backTop - vz) * scaleY;
                    int mid = (int) Math.ceil(ytU - 0.5f);        // first row below upper wall
                    int y0 = Math.max(yt, tp), y1 = Math.min(mid - 1, bt);
                    if (y0 <= y1) {
                        if (upTex != null) drawWallColumn(x, y0, y1, upTex, uCol, vtopUp - vz + side.yoff, z, lt, yt);
                        else fillColumn(x, y0, y1, z, 0xFF000000);
                    }
                    newTop = Math.max(tp, Math.max(mid, yt));
                } else {
                    newTop = Math.max(tp, yt);
                }
                if (drawLower) {
                    float ybL = cy - (backBot - vz) * scaleY;
                    int mid = (int) Math.ceil(ybL - 0.5f);        // first row of lower wall
                    int y0 = Math.max(mid, tp), y1 = Math.min(yb, bt);
                    if (y0 <= y1) {
                        if (loTex != null) drawWallColumn(x, y0, y1, loTex, uCol, vtopLo - vz + side.yoff, z, lt, mid);
                        else fillColumn(x, y0, y1, z, 0xFF000000);
                    }
                    newBot = Math.min(bt, Math.min(mid - 1, yb));
                } else {
                    newBot = Math.min(bt, yb);
                }
                if (isClosed) {
                    top[x] = H; bot[x] = -1; closed[x] = true; closedCount++;
                } else {
                    top[x] = newTop; bot[x] = newBot;
                    if (newTop > newBot) { closed[x] = true; closedCount++; }
                }
            }
        }
    }

    /** Draw wall texture rows y0..y1; texMidRel = world offset such that texRow = texMidRel + (y+.5-cy)*z/focalY. */
    void drawWallColumn(int x, int y0, int y1, Gfx.Pic tex, float u, float texMidRel, float z, int[] lt, int wallTopRow) {
        int w = tex.w, h = tex.h;
        int c = (int) Math.floor(u);
        c %= w; if (c < 0) c += w;
        int base = c * h;
        float stepY = z / focalY;
        float rf = texMidRel + (y0 + 0.5f - cy) * stepY;
        rf %= h; if (rf < 0) rf += h;
        int r16 = (int) (rf * 65536f);
        int st = (int) (stepY * 65536f);
        byte[] pix = tex.pix;
        int idx = y0 * W + x;
        for (int y = y0; y <= y1; y++) {
            int r = r16 >> 16;
            if (r >= h) r %= h;
            fb[idx] = lt[pix[base + r] & 255];
            depth[idx] = z;
            idx += W;
            r16 += st;
        }
    }

    void fillColumn(int x, int y0, int y1, float z, int color) {
        int idx = y0 * W + x;
        for (int y = y0; y <= y1; y++) { fb[idx] = color; depth[idx] = z; idx += W; }
    }

    void drawFlatColumn(int x, int y0, int y1, byte[] flat, float hd, int lightnum, boolean floor) {
        float hdF = hd * focalY;
        float k = 2560f / hdF;
        float dx = dirX[x], dy = dirY[x];
        int idx = y0 * W + x;
        int start = (15 - Math.max(0, Math.min(15, lightnum))) * 4;
        for (int y = y0; y <= y1; y++) {
            float z = hdF * invDy[y];
            float wx = vx + z * dx, wy = vy + z * dy;
            int tx = ((int) (wx + 65536f)) & 63;
            int ty = ((int) (65536f - wy)) & 63;
            int lvl;
            if (fixedLevel >= 0) lvl = fixedLevel;
            else {
                int j = (int) (k * absDy[y]);
                if (j > 47) j = 47;
                lvl = start - (j >> 1);
                if (lvl < 0) lvl = 0; else if (lvl > 31) lvl = 31;
            }
            fb[idx] = gfx.lit[lvl][flat[(ty << 6) | tx] & 255];
            depth[idx] = z;
            idx += W;
        }
    }

    void drawSkyColumn(int x, int y0, int y1) {
        if (sky == null) {
            int idx = y0 * W + x;
            for (int y = y0; y <= y1; y++) { fb[idx] = 0xFF2040A0; idx += W; }
            return;
        }
        int w = sky.w, h = sky.h;
        float uf = skyU[x] * w;
        int c = (int) Math.floor(uf);
        c %= w; if (c < 0) c += w;
        int base = c * h;
        float rowPerPx = (w / (float) (Math.PI / 2)) / focal / VSTRETCH;
        int[] lt = gfx.lit[fixedLevel >= 0 ? fixedLevel : 0];
        int idx = y0 * W + x;
        for (int y = y0; y <= y1; y++) {
            int r = (int) (100f + (y + 0.5f - cy) * rowPerPx);
            if (r < 0) r = 0; else if (r >= h) r = h - 1;
            fb[idx] = lt[sky.pix[base + r] & 255];
            idx += W;
        }
    }

    // =================================================================== sprites + masked textures
    void drawSpritesAndMasked(List<Mobj> mobjs, Mobj self) {
        for (int i = 0; i < mobjs.size(); i++) {
            Mobj m = mobjs.get(i);
            if (m == self || m.removed || m.spr == null) continue;
            float dx = ix(m) - vx, dy = iy(m) - vy;
            float z = dx * cosA + dy * sinA;
            if (z < 4f || z > 8000f) continue;
            float xr = dx * sinA - dy * cosA;
            if (Math.abs(xr) > z * (W / focal) + 128f) continue;
            Drawable d = new Drawable();
            d.z = z; d.m = m;
            drawables.add(d);
        }
        for (Masked m : masked) { Drawable d = new Drawable(); d.z = m.key; d.s = m; drawables.add(d); }
        drawables.sort(new Comparator<Drawable>() {
            public int compare(Drawable a, Drawable b) { return Float.compare(b.z, a.z); }
        });
        for (Drawable d : drawables) {
            if (d.m != null) drawSprite(d.m, d.z); else drawMasked(d.s);
        }
    }

    void drawSprite(Mobj m, float z) {
        Gfx.SpriteSet ss = m.spr;
        int f = m.frame;
        if (f < 0 || f >= 29 || !ss.present[f]) return;
        float dx = ix(m) - vx, dy = iy(m) - vy;
        float xr = dx * sinA - dy * cosA;
        int rot = 0;
        if (ss.rotates[f]) {
            double a = Math.atan2(dy, dx);
            rot = ((int) Math.floor((a - m.angle) / (Math.PI / 4) + 4.5)) & 7;
        }
        Gfx.Pic p = ss.pic[f][rot];
        if (p == null) return;
        boolean flip = ss.flip[f][rot];
        float sc = focal / z, scY = focalY / z;
        float sx = cx + xr * sc;
        float left = sx - (flip ? (p.w - p.lo) : p.lo) * sc;
        float right = left + p.w * sc;
        int x0 = Math.max(0, (int) Math.ceil(left - 0.5f)), x1 = Math.min(W - 1, (int) Math.ceil(right - 0.5f) - 1);
        if (x0 > x1) return;
        float topS = cy - (izz(m) + p.to - vz) * scY;
        float botS = topS + p.h * scY;
        int y0 = Math.max(0, (int) Math.ceil(topS - 0.5f)), y1 = Math.min(H - 1, (int) Math.ceil(botS - 0.5f) - 1);
        if (y0 > y1) return;
        int lvl;
        if (fixedLevel >= 0) lvl = fixedLevel;
        else if (m.fullbright) lvl = 0;
        else {
            int light = m.sector != null ? m.sector.light : 160;
            lvl = shadeLevel(light >> 4, z);
        }
        int[] lt = gfx.lit[lvl];
        float invSc = 1f / sc, invScY = 1f / scY;
        float zt = z - 0.5f;
        boolean fuzz = m.fuzz;
        int ph = p.h;
        for (int x = x0; x <= x1; x++) {
            int tc = (int) ((x + 0.5f - left) * invSc);
            if (tc < 0) tc = 0; else if (tc >= p.w) tc = p.w - 1;
            if (flip) tc = p.w - 1 - tc;
            int base = tc * ph;
            float v = (y0 + 0.5f - topS) * invScY;
            int idx = y0 * W + x;
            for (int y = y0; y <= y1; y++) {
                int r = (int) v;
                if (r >= ph) r = ph - 1;
                v += invScY;
                if (p.op[base + r] != 0 && zt < depth[idx]) {
                    if (fuzz) {
                        int sidx = idx + (((x + y + frameCount) & 1) == 0 ? W : -W);
                        if (sidx < 0 || sidx >= fb.length) sidx = idx;
                        int c = fb[sidx];
                        fb[idx] = 0xFF000000 | (((c >> 1) & 0x7F7F7F) + ((c >> 3) & 0x1F1F1F));
                    } else fb[idx] = lt[p.pix[base + r] & 255];
                }
                idx += W;
            }
        }
    }

    void drawMasked(Masked m) {
        Level.Seg s = m.seg;
        Gfx.Pic tex = s.side.mid;
        if (tex == null) return;
        float openTop = Math.min(m.fs.ceil, m.bs.ceil), openBot = Math.max(m.fs.floor, m.bs.floor);
        float vtop = (s.line.flags & Level.ML_DONTPEGBOTTOM) != 0 ? openBot + tex.h : openTop;
        float texTopZ = vtop + s.side.yoff;
        float texBotZ = texTopZ - tex.h;
        float zTop = Math.min(texTopZ, openTop), zBot = Math.max(texBotZ, openBot);
        if (zTop <= zBot) return;
        float iscale = 1f / (m.sx2 - m.sx1);
        for (int x = m.x1; x <= m.x2; x++) {
            float xc = x + 0.5f;
            float t = (xc - m.sx1) * iscale;
            float iz = m.iz1 + (m.iz2 - m.iz1) * t;
            float z = 1f / iz;
            float u = (m.uz1 + (m.uz2 - m.uz1) * t) * z;
            int c = (int) Math.floor(u);
            c %= tex.w; if (c < 0) c += tex.w;
            float scY = focalY * iz;
            int y0 = Math.max(0, (int) Math.ceil(cy - (zTop - vz) * scY - 0.5f));
            int y1 = Math.min(H - 1, (int) Math.ceil(cy - (zBot - vz) * scY - 0.5f) - 1);
            if (y0 > y1) continue;
            int lvl = fixedLevel >= 0 ? fixedLevel : shadeLevel(m.lightnum, z);
            int[] lt = gfx.lit[lvl];
            float stepY = z / focalY;
            float rowF = (texTopZ - (vz - (y0 + 0.5f - cy) * stepY));
            int base = c * tex.h;
            int idx = y0 * W + x;
            float zt = z - 0.5f;
            for (int y = y0; y <= y1; y++) {
                int r = (int) Math.floor(rowF);
                rowF += stepY;
                if (r >= 0 && r < tex.h && (tex.op == null || tex.op[base + r] != 0) && zt < depth[idx])
                    fb[idx] = lt[tex.pix[base + r] & 255];
                idx += W;
            }
        }
    }

    // =================================================================== weapon (psprite)
    void drawWeapon(Gfx.SpriteSet wpn, int wf, Gfx.SpriteSet flash, int ff, float psx, float psy, int light) {
        float s = H / 240f;
        if (wpn != null) drawPsprite(wpn, wf, psx, psy, s, false, light);
        if (flash != null) drawPsprite(flash, ff, psx, psy, s, true, light);
    }

    void drawPsprite(Gfx.SpriteSet ss, int f, float psx, float psy, float s, boolean bright, int light) {
        if (f < 0 || f >= 29 || !ss.present[f]) return;
        Gfx.Pic p = ss.pic[f][0];
        if (p == null) return;
        float sh = s, sv = s * VSTRETCH;
        float left = cx + (psx - 160f - p.lo) * sh;
        float topS = cy - (100.5f - (psy - p.to)) * sv;
        float right = left + p.w * sh, botS = topS + p.h * sv;
        int x0 = Math.max(0, (int) Math.ceil(left - 0.5f)), x1 = Math.min(W - 1, (int) Math.ceil(right - 0.5f) - 1);
        int y0 = Math.max(0, (int) Math.ceil(topS - 0.5f)), y1 = Math.min(H - 1, (int) Math.ceil(botS - 0.5f) - 1);
        if (x0 > x1 || y0 > y1) return;
        int lvl = fixedLevel >= 0 ? fixedLevel : (bright ? 0 : shadeLevel(light >> 4, 1000f));
        int[] lt = gfx.lit[lvl];
        for (int x = x0; x <= x1; x++) {
            int tc = (int) ((x + 0.5f - left) / sh);
            if (tc < 0) tc = 0; else if (tc >= p.w) tc = p.w - 1;
            int base = tc * p.h;
            int idx = y0 * W + x;
            for (int y = y0; y <= y1; y++) {
                int r = (int) ((y + 0.5f - topS) / sv);
                if (r >= p.h) r = p.h - 1;
                if (p.op[base + r] != 0) fb[idx] = lt[p.pix[base + r] & 255];
                idx += W;
            }
        }
    }
}
