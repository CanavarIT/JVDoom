package com.java.jvdoom;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * The classic Doom menu (New Game / Options / Read This / Quit, skull cursor, thermometer sliders)
 * drawn from the WAD graphics, plus a "JVDoom Setup" page with the app's own settings.
 */
public final class Menu {
    static final int S_MAIN = 0, S_EPI = 1, S_SKILL = 2, S_OPT = 3, S_SND = 4, S_JV = 5, S_HELP = 6, S_QUIT = 7, S_END = 8;
    static final String[] RES_LABEL = {"LOW 180P", "MEDIUM 240P", "HIGH 360P"};
    static final String[] SIZE_LABEL = {"SMALL", "MEDIUM", "LARGE"};
    static final String[] ALPHA_LABEL = {"LOW", "MEDIUM", "HIGH"};

    static final class It {
        final int id, slider;
        final String patch, text;
        It(int id, String patch, String text, int slider) { this.id = id; this.patch = patch; this.text = text; this.slider = slider; }
        int rows() { return slider > 0 ? 2 : 1; }
    }

    final GameView gv;
    final Hud hud;
    final List<It> items = new ArrayList<>();
    float[] itY = new float[16];
    int ax, ay;
    int scr = S_MAIN, prev = S_MAIN, helpPage;
    final int[] sel = new int[9];
    int episode = 1;
    float sx = 1, sy = 1, ox, oy;
    boolean drag;
    int downItem = -2;                       // -1 none, -3 back button
    final Paint pFill = new Paint(Paint.ANTI_ALIAS_FLAG), pStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    final RectF rf = new RectF();

    public Menu(GameView g, Hud h) { gv = g; hud = h; pStroke.setStyle(Paint.Style.STROKE); }

    // ------------------------------------------------------------------ navigation
    public void open() { scr = S_MAIN; build(); }

    void goTo(int s) { prev = scr; scr = s; build(); gv.beep("DSPSTART"); }

    public void back() {
        gv.beep("DSSWTCHN");
        switch (scr) {
            case S_MAIN: gv.closeMenu(); return;
            case S_SKILL: scr = episodeCount() > 1 && !gv.wad.isDoom2() ? S_EPI : S_MAIN; break;
            case S_SND: scr = S_OPT; break;
            case S_QUIT: case S_END: scr = prev; break;
            default: scr = S_MAIN;
        }
        build();
    }

    int episodeCount() {
        int n = 0;
        for (int e = 1; e <= 4; e++) if (gv.wad.has("E" + e + "M1")) n++;
        return n;
    }

    int firstEpisode() {
        for (int e = 1; e <= 4; e++) if (gv.wad.has("E" + e + "M1")) return e;
        return 1;
    }

    // ------------------------------------------------------------------ layout
    void add(int id, String patch, String text, int slider) { items.add(new It(id, patch, text, slider)); }

    public void build() {
        items.clear();
        switch (scr) {
            case S_MAIN:
                ax = 97; ay = 64;
                add(1, "M_NGAME", null, 0); add(2, "M_OPTION", null, 0); add(3, null, "JVDOOM SETUP", 0);
                add(4, "M_RDTHIS", null, 0); add(5, "M_QUITG", null, 0);
                break;
            case S_EPI:
                ax = 48; ay = 63;
                for (int e = 1; e <= 4; e++) if (gv.wad.has("E" + e + "M1")) add(10 + e, "M_EPI" + e, null, 0);
                break;
            case S_SKILL:
                ax = 48; ay = 63;
                add(20, "M_JKILL", null, 0); add(21, "M_ROUGH", null, 0); add(22, "M_HURT", null, 0);
                add(23, "M_ULTRA", null, 0); add(24, "M_NMARE", null, 0);
                break;
            case S_OPT:
                ax = 60; ay = 37;
                add(30, "M_ENDGAM", null, 0); add(31, "M_MESSG", null, 0); add(32, "M_DETAIL", null, 0);
                add(33, "M_SCRNSZ", null, 9); add(34, "M_MSENS", null, 10); add(35, "M_SVOL", null, 0);
                break;
            case S_SND:
                ax = 80; ay = 64;
                add(40, "M_SFXVOL", null, 16);
                break;
            case S_JV:
                ax = 44; ay = 34;
                add(50, null, "RESOLUTION: " + RES_LABEL[gv.resIndex], 0);
                add(51, null, "SMOOTHING: " + onOff(gv.smooth), 0);
                add(52, null, "AIM ASSIST: " + onOff(gv.game.aimAssist), 0);
                add(53, null, "FPS COUNTER: " + onOff(gv.showFps), 0);
                add(54, null, "BAR SIZE: " + SIZE_LABEL[gv.hudSize], 0);
                add(55, null, "BUTTON SIZE: " + SIZE_LABEL[gv.ctlSize], 0);
                add(56, null, "BUTTON OPACITY: " + ALPHA_LABEL[gv.ctlAlpha], 0);
                add(57, null, "MAP: " + gv.game.mapName + " >", 0);
                break;
            default:
        }
        float y = ay;
        if (itY.length < items.size() + 1) itY = new float[items.size() + 1];
        for (int i = 0; i < items.size(); i++) { itY[i] = y; y += 16 * items.get(i).rows(); }
        if (sel[scr] >= items.size()) sel[scr] = 0;
    }

    static String onOff(boolean b) { return b ? "ON" : "OFF"; }

    // ------------------------------------------------------------------ actions
    void activate(int id) {
        gv.beep("DSPISTOL");
        switch (id) {
            case 1:
                if (gv.wad.isDoom2() || episodeCount() <= 1) { episode = firstEpisode(); sel[S_SKILL] = 2; goTo(S_SKILL); }
                else goTo(S_EPI);
                break;
            case 2: goTo(S_OPT); break;
            case 3: goTo(S_JV); break;
            case 4: helpPage = 0; if (!gv.hud.has("HELP1") && !gv.hud.has("CREDIT")) break; goTo(S_HELP); break;
            case 5: goTo(S_QUIT); break;
            case 11: case 12: case 13: case 14: episode = id - 10; sel[S_SKILL] = 2; goTo(S_SKILL); break;
            case 20: case 21: case 22: case 23: case 24: gv.startNewGame(episode, id - 20); break;
            case 30: if (!gv.titleMode) goTo(S_END); break;
            case 31: gv.messagesOn = !gv.messagesOn; gv.savePrefs(); break;
            case 32: gv.lowDetail = !gv.lowDetail; gv.rebuildRenderer = true; gv.savePrefs(); break;
            case 35: goTo(S_SND); break;
            case 50: gv.resIndex = (gv.resIndex + 1) % 3; gv.rebuildRenderer = true; gv.savePrefs(); build(); break;
            case 51: gv.smooth = !gv.smooth; gv.savePrefs(); build(); break;
            case 52: gv.game.aimAssist = !gv.game.aimAssist; gv.savePrefs(); build(); break;
            case 53: gv.showFps = !gv.showFps; gv.savePrefs(); build(); break;
            case 54: gv.hudSize = (gv.hudSize + 1) % 3; gv.relayout(); gv.savePrefs(); build(); break;
            case 55: gv.ctlSize = (gv.ctlSize + 1) % 3; gv.layoutButtons(); gv.savePrefs(); build(); break;
            case 56: gv.ctlAlpha = (gv.ctlAlpha + 1) % 3; gv.savePrefs(); build(); break;
            case 57: gv.warpNext(); break;
            default:
        }
    }

    int sliderValue(int id) {
        switch (id) {
            case 33: return gv.screenBlocks - 3;
            case 34: return Math.max(0, Math.min(9, Math.round((gv.sens - 0.4f) / 0.2f)));
            case 40: return gv.sfxVol;
            default: return 0;
        }
    }

    void setSlider(int id, int dot) {
        switch (id) {
            case 33: gv.screenBlocks = dot + 3; gv.relayout(); break;
            case 34: gv.sens = 0.4f + 0.2f * dot; break;
            case 40: gv.sfxVol = dot; gv.sound.volume = dot / 15f; break;
            default: return;
        }
        gv.savePrefs();
    }

    void sliderFromTouch(float vx) {
        if (sel[scr] >= items.size()) return;
        It it = items.get(sel[scr]);
        if (it.slider <= 0) return;
        int dot = (int) Math.floor((vx - (ax + 8)) / 8f);
        dot = Math.max(0, Math.min(it.slider - 1, dot));
        if (dot != sliderValue(it.id)) { setSlider(it.id, dot); gv.beep("DSSTNMOV"); }
    }

    // ------------------------------------------------------------------ drawing
    void transform(int sw, int sh) {
        sy = sh / 200f; sx = sh / 240f;               // 320x200 shown at 4:3 like the original
        ox = (sw - 320 * sx) / 2f; oy = 0;
        hud.setT(sx, sy, ox, oy);
    }

    void thermo(Canvas c, float x, float y, int width, int dot) {
        float xx = x;
        hud.patch(c, "M_THERML", xx, y);
        xx += 8;
        for (int i = 0; i < width; i++) { hud.patch(c, "M_THERMM", xx, y); xx += 8; }
        hud.patch(c, "M_THERMR", xx, y);
        hud.patch(c, "M_THERMO", x + 8 + dot * 8, y);
    }

    public void draw(Canvas c, int sw, int sh) {
        transform(sw, sh);
        if (scr == S_HELP) { drawHelp(c); return; }
        if (scr == S_QUIT || scr == S_END) { drawConfirm(c); return; }
        switch (scr) {
            case S_MAIN: hud.patch(c, "M_DOOM", 94, 2); break;
            case S_EPI: hud.patch(c, "M_NEWG", 96, 14); hud.patch(c, "M_EPISOD", 54, 38); break;
            case S_SKILL: hud.patch(c, "M_NEWG", 96, 14); hud.patch(c, "M_SKILL", 54, 38); break;
            case S_OPT: hud.patch(c, "M_OPTTTL", 108, 15); break;
            case S_SND: hud.patch(c, "M_SVOL", 60, 38); break;
            case S_JV: hud.text(c, "JVDOOM SETUP", 160 - hud.textW("JVDOOM SETUP"), 8, 2f); break;
            default:
        }
        for (int i = 0; i < items.size(); i++) {
            It it = items.get(i);
            float y = itY[i];
            if (it.patch != null) hud.patch(c, it.patch, ax, y);
            else hud.text(c, it.text, ax, y + (scr == S_JV ? 3 : 0), scr == S_JV ? 1.5f : 2f);
            if (it.id == 31) hud.patch(c, gv.messagesOn ? "M_MSGON" : "M_MSGOFF", ax + 120, y);
            if (it.id == 32) hud.patch(c, gv.lowDetail ? "M_GDLOW" : "M_GDHIGH", ax + 175, y);
            if (it.slider > 0) thermo(c, ax, y + 16, it.slider, sliderValue(it.id));
        }
        if (!items.isEmpty()) {
            boolean anim = ((System.nanoTime() / 228_000_000L) & 1) == 0;
            int s = Math.min(sel[scr], items.size() - 1);
            hud.patch(c, anim ? "M_SKULL1" : "M_SKULL2", ax - 32, itY[s] - 5);
        }
        // soft back button
        String label = scr == S_MAIN ? (gv.titleMode ? "< CLOSE" : "< RESUME") : "< BACK";
        hud.text(c, label, 6, 184, 1.6f);
    }

    boolean backHit(float vx, float vy) { return vx >= 0 && vx <= 80 && vy >= 178; }

    void drawHelp(Canvas c) {
        String n = helpPage == 0 ? "HELP1" : "HELP2";
        if (!hud.has(n)) n = hud.has("CREDIT") ? "CREDIT" : "HELP1";
        hud.patch(c, n, 0, 0);
    }

    String[] confirmLines() {
        return scr == S_QUIT ? new String[]{"ARE YOU SURE YOU WANT TO QUIT", "THIS GREAT GAME?"}
                : new String[]{"ARE YOU SURE YOU WANT TO END", "THE GAME?"};
    }

    static final float[] YES = {50, 112, 150, 138}, NO = {170, 112, 270, 138};

    void drawConfirm(Canvas c) {
        pFill.setColor(0xE0000000);
        rf.set(ox + 10 * sx, 50 * sy, ox + 310 * sx, 150 * sy);
        c.drawRoundRect(rf, 6 * sx, 6 * sx, pFill);
        pStroke.setColor(0xFFB02020); pStroke.setStrokeWidth(2 * sx);
        c.drawRoundRect(rf, 6 * sx, 6 * sx, pStroke);
        String[] ls = confirmLines();
        for (int i = 0; i < ls.length; i++) hud.text(c, ls[i], 160 - hud.textW(ls[i]) * 0.65f, 62 + i * 14, 1.3f);
        button(c, YES, "YES"); button(c, NO, "NO");
    }

    void button(Canvas c, float[] r, String s) {
        pFill.setColor(0xFF2A2A2A);
        rf.set(ox + r[0] * sx, r[1] * sy, ox + r[2] * sx, r[3] * sy);
        c.drawRoundRect(rf, 5 * sx, 5 * sx, pFill);
        pStroke.setColor(0xFF999999); pStroke.setStrokeWidth(sx);
        c.drawRoundRect(rf, 5 * sx, 5 * sx, pStroke);
        hud.text(c, s, (r[0] + r[2]) / 2f - hud.textW(s), r[1] + 6, 2f);
    }

    static boolean in(float[] r, float x, float y) { return x >= r[0] && x <= r[2] && y >= r[1] && y <= r[3]; }

    // ------------------------------------------------------------------ touch
    /** Row whose vertical band contains vy; above/below the list gives the first/last row (the skull just follows the finger). */
    int rowAt(float vy) {
        if (items.isEmpty()) return -1;
        for (int i = 0; i < items.size(); i++)
            if (vy < itY[i] + 16 * items.get(i).rows()) return i;
        return items.size() - 1;
    }

    /** Right edge (virtual units) of everything drawn for this item, so only the real text/picture is "clickable". */
    float textRight(It it) {
        float r = ax;
        if (it.patch != null) {
            Gfx.Pic p = gv.gfx.patch(it.patch);
            if (p != null) r = ax - p.lo + p.w;
        } else if (it.text != null) {
            r = ax + hud.textW(it.text) * (scr == S_JV ? 1.5f : 2f);
        }
        if (it.id == 31) { Gfx.Pic p = gv.gfx.patch(gv.messagesOn ? "M_MSGON" : "M_MSGOFF"); if (p != null) r = Math.max(r, ax + 120 - p.lo + p.w); }
        if (it.id == 32) { Gfx.Pic p = gv.gfx.patch(gv.lowDetail ? "M_GDLOW" : "M_GDHIGH"); if (p != null) r = Math.max(r, ax + 175 - p.lo + p.w); }
        if (it.slider > 0) r = Math.max(r, ax + 8 * (it.slider + 2));
        return r;
    }

    /** True when (vx,vy) is on the text / picture of item i (not on the empty space beside it). */
    boolean onText(int i, float vx, float vy) {
        if (i < 0 || i >= items.size()) return false;
        It it = items.get(i);
        if (vy < itY[i] || vy >= itY[i] + 16 * it.rows()) return false;
        return vx >= ax - 6 && vx <= textRight(it) + 6;
    }

    /** Slider is grabbed only on its thermometer row. */
    boolean onThermo(int i, float vx, float vy) {
        if (i < 0 || i >= items.size()) return false;
        It it = items.get(i);
        return it.slider > 0 && vy >= itY[i] + 16 && vy < itY[i] + 32 && vx >= ax - 6 && vx <= ax + 8 * (it.slider + 2) + 6;
    }

    float downVx, downVy;

    public boolean touch(MotionEvent e) {
        float vx = (e.getX() - ox) / sx, vy = (e.getY() - oy) / sy;
        int a = e.getActionMasked();
        if (scr == S_HELP) {
            if (a == MotionEvent.ACTION_UP) {
                if (helpPage == 0 && hud.has("HELP2")) helpPage = 1; else { scr = S_MAIN; build(); }
            }
            return true;
        }
        if (scr == S_QUIT || scr == S_END) {
            if (a == MotionEvent.ACTION_UP) {
                if (in(YES, vx, vy)) { if (scr == S_QUIT) gv.quitApp(); else gv.endGame(); }
                else if (in(NO, vx, vy)) back();
            }
            return true;
        }
        if (a == MotionEvent.ACTION_DOWN) {
            downVx = vx; downVy = vy; drag = false;
            if (backHit(vx, vy)) { downItem = -3; return true; }
            downItem = -1;
            int r = rowAt(vy);
            if (r < 0) return true;
            if (onThermo(r, vx, vy)) {                        // grab slider
                moveSel(r); drag = true; sliderFromTouch(vx);
            } else if (onText(r, vx, vy)) {                   // on the text: will open / toggle on release
                moveSel(r); downItem = r;
            } else {                                          // empty space: only move the skull
                moveSel(r);
            }
        } else if (a == MotionEvent.ACTION_MOVE) {
            if (drag) { sliderFromTouch(vx); return true; }
            if (downItem == -3) return true;
            // finger moved: it is a swipe / skull move, never a press
            if (Math.abs(vy - downVy) > 4 || Math.abs(vx - downVx) > 8) downItem = -1;
            if (downItem == -1) { int r = rowAt(vy); if (r >= 0) moveSel(r); }
        } else if (a == MotionEvent.ACTION_UP) {
            if (downItem == -3 && backHit(vx, vy)) back();
            else if (downItem >= 0 && onText(downItem, vx, vy) && items.get(downItem).slider == 0) activate(items.get(downItem).id);
            drag = false; downItem = -2;
        } else if (a == MotionEvent.ACTION_CANCEL) { drag = false; downItem = -2; }
        return true;
    }

    void moveSel(int r) {
        if (r != sel[scr]) { sel[scr] = r; gv.beep("DSPSTOP"); }
    }
}
