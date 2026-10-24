package com.java.jvdoom;

import android.content.Context;
import android.content.SharedPreferences;
import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Build;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.util.ArrayList;
import java.util.List;

/** Render/game thread, touch controls, status bar, automap and the Doom menu. */
public final class GameView extends SurfaceView implements SurfaceHolder.Callback, Runnable {
    static final double TIC_NS = 1e9 / 35.0;
    static final int[] RES = {180, 240, 360};

    final Game game;
    final Gfx gfx;
    final Wad wad;
    final SoundSystem sound;
    final SharedPreferences prefs;
    final float d;                         // density
    final Object lock = new Object();

    Thread thread;
    volatile boolean running;
    volatile boolean paused;               // activity paused
    volatile boolean menuOpen, titleMode = true;
    boolean mapOpen;
    int resIndex = 1;
    boolean smooth = true;
    float sens = 1f;
    boolean showFps;
    // Doom options
    boolean messagesOn = true, lowDetail;
    int screenBlocks = 10;                 // 3..10 = framed view (10 = full width + status bar), 11 = full screen
    int sfxVol = 15;                       // 0..15
    // JVDoom setup
    int hudSize = 1, ctlSize = 1, ctlAlpha = 2;     // 0..2
    static final float[] HUD_FRAC = {0.12f, 0.15f, 0.18f}, CTL_SCALE = {0.8f, 1f, 1.25f}, CTL_ALPHA = {0.4f, 0.7f, 1f};
    // layout of the 3D view and the status bar
    int vX, vY, vW, vH, barTop;
    float barS, barX0;
    final Hud hud;
    final Menu menu;
    final Intermission wi;
    final Paint pBorder = new Paint();
    Bitmap flatBmp;

    int sw, sh;                            // surface size
    Bitmap bmp;
    Renderer rend;
    boolean rebuildRenderer = true;
    final Paint pBmp = new Paint(), pFill = new Paint(Paint.ANTI_ALIAS_FLAG), pText = new Paint(Paint.ANTI_ALIAS_FLAG),
            pStroke = new Paint(Paint.ANTI_ALIAS_FLAG), pLine = new Paint(Paint.ANTI_ALIAS_FLAG);
    final Rect src = new Rect(), dst = new Rect();
    final RectF rf = new RectF();
    long fpsTime, fpsCount; int fps;

    // touch state
    int stickPtr = -1, lookPtr = -1, firePtr = -1, usePtr = -1;
    float stickCx, stickCy, stickX, stickY, lastLookX, lastFireX;
    float bFireX, bFireY, bFireR, bUseX, bUseY, bUseR, bNextX, bNextY, bPrevX, bPrevY, bSmallR, bMapX, bMapY, bMenuX, bMenuY;

    public GameView(Context c, Game g, Gfx gx, Wad w, SoundSystem snd) {
        super(c);
        game = g; gfx = gx; wad = w; sound = snd;
        d = c.getResources().getDisplayMetrics().density;
        prefs = c.getSharedPreferences("jvdoom", Context.MODE_PRIVATE);
        resIndex = prefs.getInt("res", 1); smooth = prefs.getBoolean("smooth", true);
        sens = prefs.getFloat("sens", 1f); game.skill = prefs.getInt("skill", 3);
        messagesOn = prefs.getBoolean("msg", true); lowDetail = prefs.getBoolean("detail", false);
        screenBlocks = Math.max(3, Math.min(11, prefs.getInt("blocks", 10)));
        sfxVol = Math.max(0, Math.min(15, prefs.getInt("sfx", 15)));
        hudSize = Math.max(0, Math.min(2, prefs.getInt("hud", 1)));
        ctlSize = Math.max(0, Math.min(2, prefs.getInt("ctl", 1)));
        ctlAlpha = Math.max(0, Math.min(2, prefs.getInt("alpha", 2)));
        snd.volume = sfxVol / 15f;
        hud = new Hud(gx);
        menu = new Menu(this, hud);
        wi = new Intermission(this, hud);
        menu.build();
        pBorder.setFilterBitmap(false);
        game.aimAssist = prefs.getBoolean("assist", true);
        showFps = prefs.getBoolean("fps", false);
        getHolder().addCallback(this);
        setFocusable(true);
        pText.setTypeface(Typeface.DEFAULT_BOLD);
        pLine.setStrokeCap(Paint.Cap.ROUND);
    }

    void savePrefs() {
        prefs.edit().putInt("res", resIndex).putBoolean("smooth", smooth).putFloat("sens", sens)
                .putInt("skill", game.skill).putBoolean("assist", game.aimAssist).putBoolean("fps", showFps)
                .putBoolean("msg", messagesOn).putBoolean("detail", lowDetail).putInt("blocks", screenBlocks).putInt("sfx", sfxVol)
                .putInt("hud", hudSize).putInt("ctl", ctlSize).putInt("alpha", ctlAlpha).apply();
    }

    // ------------------------------------------------------------------ lifecycle
    @Override public void surfaceCreated(SurfaceHolder h) { }
    @Override public void surfaceChanged(SurfaceHolder h, int f, int w, int hh) {
        sw = w; sh = hh; layoutButtons(); relayout();
        startLoop();
    }
    @Override public void surfaceDestroyed(SurfaceHolder h) { stopLoop(); }

    public void onPauseActivity() { paused = true; game.inFire = false; game.inUse = false; game.inMove = 0; game.inStrafe = 0; sound.stop(); savePrefs(); }
    public void onResumeActivity() { paused = false; sound.start(); }

    void startLoop() {
        if (thread != null) return;
        running = true;
        thread = new Thread(this, "doom-loop");
        thread.start();
    }
    void stopLoop() {
        running = false;
        if (thread != null) { try { thread.join(1000); } catch (InterruptedException ignored) { } thread = null; }
    }

    void layoutButtons() {
        float k = CTL_SCALE[ctlSize];
        bFireR = 54 * d * k; bFireX = sw - 96 * d * k; bFireY = sh - 96 * d * k;
        bUseR = 34 * d * k; bUseX = sw - 200 * d * k; bUseY = sh - 62 * d * k;
        bSmallR = 24 * d * k;
        bNextX = sw - 52 * d * k; bNextY = sh - 210 * d * k;
        bPrevX = sw - 140 * d * k; bPrevY = sh - 190 * d * k;
        bMapX = sw - 44 * d; bMapY = 40 * d;
        bMenuX = sw - 104 * d; bMenuY = 40 * d;
    }

    /** Recompute the 3D view rectangle and the status bar size (Doom's "Screen Size" + our bar scale). */
    void relayout() {
        if (sw <= 0 || sh <= 0) return;
        float barH = sh * HUD_FRAC[hudSize];
        barS = Math.min(barH / 38.4f, sw / 320f);
        barH = 38.4f * barS;
        barX0 = (sw - 320 * barS) / 2f;
        barTop = Math.round(sh - barH);
        if (screenBlocks >= 11) { vX = 0; vY = 0; vW = sw; vH = sh; }
        else {
            vW = Math.round(sw * screenBlocks / 10f); vH = Math.round(barTop * screenBlocks / 10f);
            vX = (sw - vW) / 2; vY = (barTop - vH) / 2;
        }
        rebuildRenderer = true;
    }

    void beep(String lump) { if (sound != null) sound.play(lump, sound.lx, sound.ly); }

    void closeMenu() { menuOpen = false; }

    void openMenu() { releaseAll(); menu.open(); menuOpen = true; }

    void startNewGame(int episode, int skill) {
        synchronized (lock) {
            game.skill = skill + 1;
            String m = wad.isDoom2() ? "MAP01" : "E" + episode + "M1";
            if (wad.find(m) < 0) m = wad.maps().get(0);
            game.startMap(m, false);
        }
        savePrefs();
        mapOpen = false; titleMode = false; menuOpen = false;
    }

    void endGame() { titleMode = true; menuOpen = false; mapOpen = false; releaseAll(); }

    void quitApp() { if (getContext() instanceof Activity) ((Activity) getContext()).finish(); }

    void warpNext() {
        List<String> maps = wad.maps();
        if (maps.isEmpty()) return;
        int i = maps.indexOf(game.mapName);
        final String n = maps.get((i + 1) % maps.size());
        synchronized (lock) { game.startMap(n, false); }
        if (titleMode) titleMode = false;
        menu.build();
    }

    // ------------------------------------------------------------------ main loop
    @Override public void run() {
        long last = System.nanoTime();
        double acc = 0;
        while (running) {
            if (paused) { try { Thread.sleep(50); } catch (InterruptedException e) { return; } last = System.nanoTime(); continue; }
            if (rebuildRenderer && sw > 0) buildRenderer();
            long now = System.nanoTime();
            acc += now - last; last = now;
            if (acc > 250e6) acc = 250e6;
            boolean freeze = menuOpen || titleMode || game.state == Game.ST_EXIT;
            synchronized (lock) {
                if (!freeze) {
                    while (acc >= TIC_NS) { game.tick(); acc -= TIC_NS; }
                } else acc = 0;
                if (!titleMode) game.render(freeze ? 1f : (float) (acc / TIC_NS));
            }
            draw();
            long used = System.nanoTime() - now;
            long target = 16_000_000L;
            if (used < target) { try { Thread.sleep((target - used) / 1_000_000L); } catch (InterruptedException e) { return; } }
            fpsCount++;
            if (now - fpsTime > 1_000_000_000L) { fps = (int) fpsCount; fpsCount = 0; fpsTime = now; }
        }
    }

    void buildRenderer() {
        rebuildRenderer = false;
        if (vW <= 0 || vH <= 0) { relayout(); rebuildRenderer = false; }
        int h = Math.max(64, Math.round(RES[resIndex] * (float) vH / sh));
        int w = Math.max(64, Math.round(h * (float) vW / vH));
        if (lowDetail) w = Math.max(32, w / 2);          // Doom's "Graphic Detail: LOW" = chunky pixels
        w &= ~1;
        synchronized (lock) {
            rend = new Renderer(gfx, w, h);
            game.setRenderer(rend);
            if (bmp != null) bmp.recycle();
            bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        }
    }

    // ------------------------------------------------------------------ drawing
    void draw() {
        Surface surf = getHolder().getSurface();
        Canvas c = null;
        try {
            c = Build.VERSION.SDK_INT >= 26 ? surf.lockHardwareCanvas() : surf.lockCanvas(null);
            if (c == null) return;
            if (titleMode) {
                c.drawColor(Color.BLACK);
                drawTitle(c);
                if (menuOpen) menu.draw(c, sw, sh);
                return;
            }
            Renderer r = rend;
            if (r == null || bmp == null) { c.drawColor(Color.BLACK); return; }
            if (screenBlocks < 11) drawBorder(c);
            bmp.setPixels(r.fb, 0, r.W, 0, 0, r.W, r.H);
            pBmp.setFilterBitmap(smooth);
            src.set(0, 0, r.W, r.H); dst.set(vX, vY, vX + vW, vY + vH);
            c.drawBitmap(bmp, src, dst, pBmp);
            drawOverlays(c);
        } catch (Throwable ignored) {
        } finally {
            if (c != null) { try { surf.unlockCanvasAndPost(c); } catch (Throwable ignored) { } }
        }
    }

    /** Tiles Doom's FLOOR7_2 flat behind the framed view and around the status bar. */
    void drawBorder(Canvas c) {
        if (flatBmp == null) {
            byte[] f = gfx.flats.get("FLOOR7_2");
            if (f != null && f.length >= 4096) {
                int[] px = new int[4096];
                for (int i = 0; i < 4096; i++) px[i] = gfx.lit[0][f[i] & 255];
                flatBmp = Bitmap.createBitmap(px, 64, 64, Bitmap.Config.ARGB_8888);
                BitmapShader sh2 = new BitmapShader(flatBmp, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
                Matrix m = new Matrix(); m.setScale(Math.max(1f, barS), Math.max(1.2f, barS * 1.2f));
                sh2.setLocalMatrix(m);
                pBorder.setShader(sh2);
            }
        }
        if (flatBmp != null) c.drawRect(0, 0, sw, sh, pBorder);
        else c.drawColor(0xFF3A3A3A);
    }

    void drawTitle(Canvas c) {
        float sy = sh / 200f, sx = sh / 240f;
        hud.setT(sx, sy, (sw - 320 * sx) / 2f, 0);
        if (hud.has("TITLEPIC")) hud.patch(c, "TITLEPIC", 0, 0);
        if (!menuOpen) {
            if (((System.nanoTime() / 600_000_000L) & 1) == 0) {
                String s = "TAP TO START";
                hud.text(c, s, 160 - hud.textW(s) * 0.75f, 182, 1.5f);
            }
        } else { pFill.setColor(0xA0000000); c.drawRect(0, 0, sw, sh, pFill); }
    }

    void text(Canvas c, String s, float x, float y, float size, int color, Paint.Align al) {
        pText.setTextSize(size); pText.setTextAlign(al);
        pText.setColor(0xCC000000); c.drawText(s, x + size * 0.07f, y + size * 0.07f, pText);
        pText.setColor(color); c.drawText(s, x, y, pText);
    }

    void doomTextC(Canvas c, String s, float cx, float y, float k) {
        float t = sh / 200f * k / 2f;
        hud.setT(t, t * 1.2f, cx - hud.textW(s) * t / 2f, y);
        hud.text(c, s, 0, 0, 1f);
    }

    void drawOverlays(Canvas c) {
        Game g = game;
        // screen tints (only over the 3D view, like the palette flashes of the original)
        int tint = 0;
        if (g.damageFlash > 0) tint = Color.argb(Math.min(150, g.damageFlash * 5), 255, 0, 0);
        else if (g.bonusFlash > 0) tint = Color.argb(g.bonusFlash * 10, 255, 220, 80);
        else if (g.suit > 0 && (g.suit > 140 || (g.suit & 8) != 0)) tint = Color.argb(45, 0, 200, 0);
        if (g.state == Game.ST_DEAD) tint = Color.argb(120, 120, 0, 0);
        if (tint != 0) { c.save(); c.clipRect(vX, vY, vX + vW, vY + vH); c.drawColor(tint); c.restore(); }

        if (mapOpen) drawAutomap(c);
        drawHud(c);
        if (g.state == Game.ST_PLAY && !menuOpen) drawControls(c);

        if (g.state == Game.ST_DEAD && !menuOpen) {
            doomTextC(c, "YOU DIED", sw / 2f, sh * 0.34f, 6f);
            doomTextC(c, "TAP TO RESTART THE LEVEL", sw / 2f, sh * 0.34f + 56 * d, 2f);
        }
        if (g.state == Game.ST_EXIT) drawIntermission(c); else wi.active = false;
        if (menuOpen) {
            pFill.setColor(0x66000000); c.drawRect(0, 0, sw, sh, pFill);
            menu.draw(c, sw, sh);
        }
        if (showFps) text(c, fps + " fps  " + (rend != null ? rend.W + "x" + rend.H : ""), 8 * d, 20 * d, 13 * d, Color.YELLOW, Paint.Align.LEFT);
    }

    void drawHud(Canvas c) {
        Game g = game;
        if (screenBlocks <= 10) {
            // original status bar, centred; the sides continue with the border flat
            hud.setT(barS, barS * 1.2f, barX0, barTop - 168 * barS * 1.2f);
            hud.drawBar(c, g);
        } else {
            hud.drawMinimal(c, g, sw, sh, Math.max(1.5f, sh * 0.0045f), 10 * d);
        }
        if (messagesOn && g.msgTics > 0 && !g.message.isEmpty()) {
            float k = Math.max(2f, sh * 0.0042f);
            hud.setT(k, k * 1.2f, vX + 6 * d, vY + 6 * d);
            hud.text(c, g.message, 0, 0, 1f);
        }
    }

    int al(int argb) { return (Math.round((argb >>> 24) * CTL_ALPHA[ctlAlpha]) << 24) | (argb & 0xFFFFFF); }

    void circle(Canvas c, float x, float y, float r, boolean active, String label, float ts) {
        pFill.setColor(al(active ? 0x66FFFFFF : 0x33FFFFFF));
        c.drawCircle(x, y, r, pFill);
        pStroke.setStyle(Paint.Style.STROKE); pStroke.setStrokeWidth(2 * d); pStroke.setColor(al(0x88FFFFFF));
        c.drawCircle(x, y, r, pStroke);
        text(c, label, x, y + ts * 0.35f, ts, 0xFFFFFFFF, Paint.Align.CENTER);
    }

    void drawControls(Canvas c) {
        circle(c, bFireX, bFireY, bFireR, firePtr >= 0, "FIRE", 14 * d);
        circle(c, bUseX, bUseY, bUseR, usePtr >= 0, "USE", 13 * d);
        circle(c, bPrevX, bPrevY, bSmallR, false, "◀", 16 * d);
        circle(c, bNextX, bNextY, bSmallR, false, "▶", 16 * d);
        circle(c, bMapX, bMapY, bSmallR, mapOpen, "MAP", 10 * d);
        circle(c, bMenuX, bMenuY, bSmallR, false, "MENU", 9 * d);
        // stick
        float cx = stickPtr >= 0 ? stickCx : 120 * d * CTL_SCALE[ctlSize], cy = stickPtr >= 0 ? stickCy : sh - 110 * d * CTL_SCALE[ctlSize];
        float k = CTL_SCALE[ctlSize];
        pFill.setColor(al(0x22FFFFFF)); c.drawCircle(cx, cy, 64 * d * k, pFill);
        pStroke.setColor(al(0x66FFFFFF)); c.drawCircle(cx, cy, 64 * d * k, pStroke);
        pFill.setColor(al(0x77FFFFFF)); c.drawCircle(cx + (stickPtr >= 0 ? stickX : 0), cy + (stickPtr >= 0 ? stickY : 0), 26 * d * k, pFill);
    }

    void drawAutomap(Canvas c) {
        Level L = game.L;
        if (L == null) return;
        pFill.setColor(0xB0000000); c.drawRect(0, 0, sw, sh, pFill);
        float scale = Math.min(sw / (L.maxX - L.minX + 200f), sh / (L.maxY - L.minY + 200f));
        float ox = sw / 2f - (L.minX + L.maxX) / 2f * scale, oy = sh / 2f + (L.minY + L.maxY) / 2f * scale;
        pLine.setStrokeWidth(Math.max(1f, d));
        for (Level.Line l : L.lines) {
            if (!l.mapped && !game.hasMap) continue;
            if ((l.flags & Level.ML_SECRET) != 0 && !game.hasMap) pLine.setColor(0xFFC00000);
            else if (l.back == null) pLine.setColor(0xFFE02020);
            else if (l.fs.floor != l.bs.floor) pLine.setColor(0xFFC8A030);
            else if (l.fs.ceil != l.bs.ceil) pLine.setColor(0xFFA0A0A0);
            else { if (!game.hasMap) continue; pLine.setColor(0xFF555555); }
            c.drawLine(ox + l.x1 * scale, oy - l.y1 * scale, ox + l.x2 * scale, oy - l.y2 * scale, pLine);
        }
        // player arrow
        float px = ox + game.pl.x * scale, py = oy - game.pl.y * scale;
        float a = game.angle;
        pLine.setColor(0xFF40FF40); pLine.setStrokeWidth(2 * d);
        float len = 14 * d;
        float ex = px + (float) Math.cos(a) * len, ey = py - (float) Math.sin(a) * len;
        c.drawLine(px, py, ex, ey, pLine);
        c.drawLine(ex, ey, px + (float) Math.cos(a + 2.6) * len * 0.6f, py - (float) Math.sin(a + 2.6) * len * 0.6f, pLine);
        c.drawLine(ex, ey, px + (float) Math.cos(a - 2.6) * len * 0.6f, py - (float) Math.sin(a - 2.6) * len * 0.6f, pLine);
        text(c, game.mapName, 12 * d, sh - 12 * d, 16 * d, Color.WHITE, Paint.Align.LEFT);
        // monsters
        pFill.setColor(0xFFFF8000);
        for (Mobj m : game.mobjs) if (m.kind == Mobj.MONSTER && m.shootable && game.hasMap) c.drawCircle(ox + m.x * scale, oy - m.y * scale, 2.5f * d, pFill);
    }

    void drawIntermission(Canvas c) { wi.draw(c, sw, sh); }

    void restartLevel() { synchronized (lock) { game.startMap(game.mapName, false); } }

    // ------------------------------------------------------------------ touch
    boolean hit(float x, float y, float cx, float cy, float r) { float dx = x - cx, dy = y - cy; return dx * dx + dy * dy <= r * r; }

    @Override public boolean onTouchEvent(MotionEvent e) {
        int act = e.getActionMasked();
        int idx = e.getActionIndex();
        if (menuOpen) return menu.touch(e);
        if (titleMode) {
            if (act == MotionEvent.ACTION_UP) openMenu();
            return true;
        }
        Game g = game;
        if (g.state == Game.ST_EXIT) {
            if (act == MotionEvent.ACTION_UP) wi.press();
            return true;
        }
        if (g.state == Game.ST_DEAD) {
            if (act == MotionEvent.ACTION_UP) restartLevel();
            return true;
        }
        switch (act) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                down(e.getPointerId(idx), e.getX(idx), e.getY(idx));
                break;
            case MotionEvent.ACTION_MOVE:
                for (int i = 0; i < e.getPointerCount(); i++) move(e.getPointerId(i), e.getX(i), e.getY(i));
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
                up(e.getPointerId(idx));
                break;
            case MotionEvent.ACTION_CANCEL:
                for (int i = 0; i < e.getPointerCount(); i++) up(e.getPointerId(i));
                break;
            default:
        }
        return true;
    }

    void down(int id, float x, float y) {
        Game g = game;
        if (hit(x, y, bFireX, bFireY, bFireR * 1.2f)) { firePtr = id; lastFireX = x; g.inFire = true; return; }
        if (hit(x, y, bUseX, bUseY, bUseR * 1.3f)) { usePtr = id; g.inUse = true; return; }
        if (hit(x, y, bNextX, bNextY, bSmallR * 1.4f)) { g.cycleWeapon(1); return; }
        if (hit(x, y, bPrevX, bPrevY, bSmallR * 1.4f)) { g.cycleWeapon(-1); return; }
        if (hit(x, y, bMapX, bMapY, bSmallR * 1.4f)) { mapOpen = !mapOpen; return; }
        if (hit(x, y, bMenuX, bMenuY, bSmallR * 1.4f)) { openMenu(); return; }
        if (x < sw * 0.45f && stickPtr < 0) { stickPtr = id; stickCx = x; stickCy = y; stickX = stickY = 0; return; }
        if (lookPtr < 0) { lookPtr = id; lastLookX = x; }
    }

    void move(int id, float x, float y) {
        if (id == stickPtr) {
            float R = 60 * d * CTL_SCALE[ctlSize];
            float dx = x - stickCx, dy = y - stickCy;
            float len = (float) Math.sqrt(dx * dx + dy * dy);
            if (len > R) { dx = dx / len * R; dy = dy / len * R; len = R; }
            stickX = dx; stickY = dy;
            float mag = len / R;
            if (mag < 0.12f) { game.inMove = 0; game.inStrafe = 0; }
            else {
                float k = (mag - 0.12f) / 0.88f;
                float nx = dx / (len == 0 ? 1 : len), ny = dy / (len == 0 ? 1 : len);
                game.inMove = -ny * Math.min(1f, k * 1.25f);
                game.inStrafe = nx * Math.min(1f, k * 1.25f);
            }
        } else if (id == lookPtr) {
            game.addTurn(-(x - lastLookX) * 0.0042f * sens); lastLookX = x;
        } else if (id == firePtr) {
            game.addTurn(-(x - lastFireX) * 0.0042f * sens); lastFireX = x;
        }
    }

    void up(int id) {
        if (id == stickPtr) { stickPtr = -1; game.inMove = 0; game.inStrafe = 0; }
        if (id == lookPtr) lookPtr = -1;
        if (id == firePtr) { firePtr = -1; game.inFire = false; }
        if (id == usePtr) { usePtr = -1; game.inUse = false; }
    }

    void releaseAll() {
        stickPtr = lookPtr = firePtr = usePtr = -1;
        game.inMove = game.inStrafe = 0; game.inFire = false; game.inUse = false;
    }

    void advanceLevel() {
        synchronized (lock) {
            String n = game.nextMap;
            if (n == null) game.startMap(wad.maps().isEmpty() ? game.mapName : wad.maps().get(0), false);
            else if (wad.find(n) < 0) game.startMap(wad.maps().get(0), false);
            else game.startMap(n, true);
        }
    }

    /** Hardware back button. Returns false when the activity should handle it (title screen: leave the app). */
    public boolean onBack() {
        if (menuOpen) { menu.back(); return true; }
        if (titleMode) return false;
        if (mapOpen) { mapOpen = false; return true; }
        openMenu();
        return true;
    }
}
