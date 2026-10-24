package com.java.jvdoom;

import android.graphics.Canvas;

import java.util.Random;

/**
 * The original Doom end-of-level screen (wi_stuff.c): world map with animated background,
 * "LEVEL FINISHED" title, KILLS / ITEMS / SECRETS percentages counting up, TIME / PAR,
 * then the "ENTERING" screen with splats on finished levels and a blinking "you are here" skull.
 */
public final class Intermission {
    static final double TIC_NS = 1e9 / 35.0;
    static final int ST_STATS = 0, ST_NEXT = 1;

    // level positions on the episode maps (wi_stuff.c lnodes)
    static final int[][][] LNODES = {
        {{185, 164}, {148, 143}, {69, 122}, {209, 102}, {116, 89}, {166, 55}, {71, 56}, {135, 29}, {71, 24}},
        {{254, 25}, {97, 50}, {188, 64}, {128, 78}, {214, 92}, {133, 130}, {208, 136}, {148, 140}, {235, 158}},
        {{156, 168}, {48, 154}, {174, 95}, {265, 75}, {130, 48}, {279, 23}, {198, 48}, {140, 25}, {281, 136}}
    };
    // animated background of episode 1
    static final int[][] ANIM0 = {{224, 104}, {184, 160}, {112, 136}, {72, 112}, {88, 96}, {64, 48}, {192, 40}, {136, 16}, {80, 16}, {64, 24}};
    static final int[][] PAR1 = {{30, 75, 120, 90, 165, 180, 180, 30, 165}, {90, 90, 90, 120, 90, 360, 240, 30, 170}, {90, 45, 90, 150, 90, 90, 165, 30, 135}};
    static final int[] PAR2 = {30, 90, 120, 120, 90, 150, 120, 120, 270, 90, 210, 150, 150, 150, 210, 150, 420, 150, 210, 150, 240, 150, 180, 150, 150, 300, 330, 420, 300, 180, 120, 30};

    final GameView gv;
    final Hud hud;
    final Random rnd = new Random();

    volatile boolean active;
    long t0;
    int ticsDone, bcnt, state, spState, cntPause, cnt;
    int cntKills, cntItems, cntSecret, cntTime, cntPar;
    int pctKills, pctItems, pctSecret, secs, par;
    int epsd, last, next;            // 0-based episode / level indexes
    boolean doom2, pointerOn, didSecret, hasNext;
    String lastName, nextName;
    final int[] aCtr = new int[10], aNext = new int[10];
    float sx = 1, sy = 1, ox;

    public Intermission(GameView g, Hud h) { gv = g; hud = h; }

    // ------------------------------------------------------------------ lifecycle
    public void begin() {
        Game g = gv.game;
        doom2 = gv.wad.isDoom2();
        lastName = g.mapName; nextName = g.nextMap;
        hasNext = nextName != null;
        if (doom2) {
            epsd = 0;
            last = idx2(lastName); next = hasNext ? idx2(nextName) : last;
            par = last >= 0 && last < PAR2.length ? PAR2[last] : 0;
        } else {
            epsd = Math.max(0, Math.min(2, lastName.charAt(1) - '1'));
            last = lastName.charAt(3) - '1';
            next = hasNext ? nextName.charAt(3) - '1' : last;
            int[] p = PAR1[epsd];
            par = last >= 0 && last < 9 ? p[last] : 0;
            if (g.secretExit) didSecret = true;
            if (last == 7) didSecret = false;
        }
        pctKills = g.totalKills == 0 ? 100 : g.kills * 100 / g.totalKills;
        pctItems = g.totalItems == 0 ? 100 : g.items * 100 / g.totalItems;
        pctSecret = g.totalSecrets == 0 ? 100 : g.secrets * 100 / g.totalSecrets;
        secs = g.levelTics / 35;
        state = ST_STATS; spState = 1; cntPause = 35; bcnt = 0; ticsDone = 0;
        cntKills = cntItems = cntSecret = cntTime = cntPar = 0;
        for (int i = 0; i < 10; i++) { aCtr[i] = -1; aNext[i] = 1 + rnd.nextInt(11); }
        t0 = System.nanoTime();
        active = true;
    }

    static int idx2(String m) { try { return Integer.parseInt(m.substring(3)) - 1; } catch (Exception e) { return 0; } }

    void snd(String s) { gv.beep(s); }

    /** Player tapped the screen. */
    public void press() {
        if (!active) return;
        if (state == ST_STATS) {
            if (spState != 10) {
                cntKills = pctKills; cntItems = pctItems; cntSecret = pctSecret; cntTime = secs; cntPar = par;
                spState = 10; snd("DSSGCOCK");
            } else if (!doom2 && hasNext) {
                state = ST_NEXT; cnt = 4 * 35; snd("DSSGCOCK");
            } else finish();
        } else finish();
    }

    void finish() {
        active = false;
        gv.advanceLevel();
    }

    // ------------------------------------------------------------------ simulation (35 Hz like the original)
    void tick() {
        bcnt++;
        if (!doom2 && epsd == 0)
            for (int i = 0; i < 10; i++)
                if (bcnt == aNext[i]) { aCtr[i] = (aCtr[i] + 1) % 3; aNext[i] = bcnt + 11; }
        if (state == ST_NEXT) {
            if (--cnt <= 0) { finish(); return; }
            pointerOn = (cnt & 31) < 20;
            return;
        }
        if (spState == 2) {
            cntKills += 2; if ((bcnt & 3) == 0) snd("DSPISTOL");
            if (cntKills >= pctKills) { cntKills = pctKills; snd("DSBAREXP"); spState++; }
        } else if (spState == 4) {
            cntItems += 2; if ((bcnt & 3) == 0) snd("DSPISTOL");
            if (cntItems >= pctItems) { cntItems = pctItems; snd("DSBAREXP"); spState++; }
        } else if (spState == 6) {
            cntSecret += 2; if ((bcnt & 3) == 0) snd("DSPISTOL");
            if (cntSecret >= pctSecret) { cntSecret = pctSecret; snd("DSBAREXP"); spState++; }
        } else if (spState == 8) {
            if ((bcnt & 3) == 0) snd("DSPISTOL");
            cntTime += 3; cntPar += 3;
            if (cntPar >= par) {
                cntPar = par;
                if (cntTime >= secs) { cntTime = secs; snd("DSBAREXP"); spState++; }
            }
        } else if (spState != 10 && (spState & 1) != 0) {
            if (--cntPause <= 0) { spState++; cntPause = 35; }
        }
    }

    // ------------------------------------------------------------------ drawing
    float nw(String n) { Gfx.Pic p = gv.gfx.patch(n); return p == null ? 0 : p.w; }
    float nh(String n) { Gfx.Pic p = gv.gfx.patch(n); return p == null ? 0 : p.h; }

    float drawNum(Canvas c, float x, float y, int n, int digits) {
        float w = nw("WINUM0");
        if (digits < 0) {
            if (n == 0) digits = 1;
            else { digits = 0; for (int t = n; t > 0; t /= 10) digits++; }
        }
        while (digits-- > 0) {
            x -= w;
            hud.patch(c, "WINUM" + (n % 10), x, y);
            n /= 10;
        }
        return x;
    }

    void drawPercent(Canvas c, float x, float y, int p) {
        hud.patch(c, "WIPCNT", x, y);
        drawNum(c, x, y, p, -1);
    }

    void drawTime(Canvas c, float x, float y, int t) {
        if (t > 61 * 59) { hud.patch(c, "WISUCKS", x - nw("WISUCKS"), y); return; }
        int div = 1;
        do {
            int n = (t / div) % 60;
            x = drawNum(c, x, y, n, 2) - nw("WICOLON");
            div *= 60;
            if (div == 60 || t / div > 0) hud.patch(c, "WICOLON", x, y);
        } while (t / div > 0);
    }

    String lvName(int i) {
        return doom2 ? String.format("CWILV%02d", i) : "WILV" + epsd + i;
    }

    void drawLevelTitle(Canvas c, int i, String fallback, float y, String sub) {
        String n = lvName(i);
        float h;
        if (hud.has(n)) { hud.patch(c, n, (320 - nw(n)) / 2f, y); h = nh(n); }
        else { hud.text(c, fallback, 160 - hud.textW(fallback) * 0.75f, y + 2, 1.5f); h = 16; }
        y += 5 * h / 4f;
        if (hud.has(sub)) hud.patch(c, sub, (320 - nw(sub)) / 2f, y);
    }

    void background(Canvas c) {
        String bg = doom2 ? "INTERPIC" : "WIMAP" + epsd;
        if (hud.has(bg)) hud.patch(c, bg, 0, 0);
        if (!doom2 && epsd == 0)
            for (int i = 0; i < 10; i++) {
                if (aCtr[i] < 0) continue;
                String n = String.format("WIA0%d%02d%02d", epsd, i, aCtr[i]);
                if (hud.has(n)) hud.patch(c, n, ANIM0[i][0], ANIM0[i][1]);
            }
    }

    public void draw(Canvas c, int sw, int sh) {
        if (!active) begin();
        int target = (int) ((System.nanoTime() - t0) / TIC_NS);
        int steps = 0;
        while (ticsDone < target && steps < 8 && active) { ticsDone++; steps++; tick(); }
        if (!active) return;
        if (ticsDone < target) ticsDone = target;

        sy = sh / 200f; sx = sh / 240f;
        ox = (sw - 320 * sx) / 2f;
        hud.setT(sx, sy, ox, 0);
        c.drawColor(0xFF000000);

        background(c);
        if (state == ST_STATS) drawStats(c);
        else drawNext(c);

        hud.setT(1, 1, 0, 0);
    }

    void drawStats(Canvas c) {
        drawLevelTitle(c, last, lastName + " FINISHED", 2, "WIF");
        float lh = 3 * nh("WINUM0") / 2f;
        hud.patch(c, "WIOSTK", 50, 50);
        hud.patch(c, "WIOSTI", 50, 50 + lh);
        hud.patch(c, "WIOSTS", 50, 50 + 2 * lh);
        if (spState >= 2) drawPercent(c, 270, 50, cntKills);
        if (spState >= 4) drawPercent(c, 270, 50 + lh, cntItems);
        if (spState >= 6) drawPercent(c, 270, 50 + 2 * lh, cntSecret);
        hud.patch(c, "WITIME", 16, 168);
        if (spState >= 8) drawTime(c, 144, 168, cntTime);
        if (doom2 || epsd < 3) {
            hud.patch(c, "WIPAR", 176, 168);
            if (spState >= 8) drawTime(c, 304, 168, cntPar);
        }
    }

    void drawNext(Canvas c) {
        if (!doom2) {
            int[][] ln = LNODES[epsd];
            int lastSplat = last == 8 ? next - 1 : last;
            for (int i = 0; i <= lastSplat && i < 9; i++) hud.patch(c, "WISPLAT", ln[i][0], ln[i][1]);
            if (didSecret) hud.patch(c, "WISPLAT", ln[8][0], ln[8][1]);
            if (pointerOn && next >= 0 && next < 9) hud.patch(c, "WIURH0", ln[next][0], ln[next][1]);
        }
        if (hud.has("WIENTER")) hud.patch(c, "WIENTER", (320 - nw("WIENTER")) / 2f, 2);
        String n = lvName(next);
        float y = 2 + 5 * nh(lvName(next)) / 4f;
        if (hud.has(n)) hud.patch(c, n, (320 - nw(n)) / 2f, y);
        else hud.text(c, nextName, 160 - hud.textW(nextName) * 0.75f, y, 1.5f);
    }
}
