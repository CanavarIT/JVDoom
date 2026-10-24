package com.java.jvdoom;

import java.util.ArrayList;
import java.util.List;

/** Parsed map: geometry, BSP, sectors and a coarse line grid for collision queries. */
public final class Level {
    public static final int ML_BLOCKING = 1, ML_BLOCKMONSTERS = 2, ML_TWOSIDED = 4, ML_DONTPEGTOP = 8,
            ML_DONTPEGBOTTOM = 16, ML_SECRET = 32, ML_SOUNDBLOCK = 64;

    public static final class Sector {
        public float floor, ceil;
        public byte[] floorPic, ceilPic;
        public String floorName, ceilName;
        public boolean skyCeil;
        public int light, special, tag;
        public int idx;
        public final List<Line> lines = new ArrayList<>();
        public Mover mover;          // active door/lift/etc.
        public float origFloor, origCeil;
        public boolean secretFound;
        public float cx, cy;
    }

    public static final class Side {
        public float xoff, yoff;
        public Gfx.Pic upper, lower, mid;
        public String upperName, lowerName, midName;
        public Sector sector;
    }

    public static final class Line {
        public int idx, v1, v2, flags, special, tag;
        public float x1, y1, x2, y2, dx, dy, len;
        public Side front, back;
        public Sector fs, bs;
        public int validcount;
        public boolean used;       // for one-shot specials
        public boolean mapped;     // seen by the player (automap)
        public float minx, maxx, miny, maxy;
    }

    public static final class Seg {
        public float x1, y1, x2, y2, len, offset;
        public Line line;
        public Side side;
        public Sector front, back;
        public boolean dir;
    }

    public static final class Thing {
        public float x, y;
        public int angle, type, flags;
    }

    /** Active moving sector (door / lift / floor / ceiling). */
    public static final class Mover {
        public Sector sector;
        public int kind;             // 0 door, 1 lift, 2 floor, 3 ceiling
        public float target, speed;
        public int state;            // 0 moving to target, 1 waiting, 2 returning, 3 closing
        public int wait, waitMax;
        public float home;
        public boolean useCeil;
        public boolean once;
        public Line triggerLine;
    }

    public String name;
    public float[] vx, vy;
    public Line[] lines;
    public Side[] sides;
    public Sector[] sectors;
    public Seg[] segs;
    public int[] ssNum, ssFirst;
    public Sector[] ssSector;
    public float[] nx, ny, ndx, ndy;
    public float[][] nbb;            // [node][8]: right(top,bottom,left,right), left(...)
    public int[] nchild;             // [node*2+side]
    public Thing[] things;
    public float minX, minY, maxX, maxY;

    // line grid
    static final int CELL = 128;
    int gx0, gy0, gw, gh;
    int[][] grid;
    int validcount = 1;

    public static Level load(Wad w, Gfx gfx, String mapName) {
        int m = w.find(mapName);
        if (m < 0) throw new IllegalArgumentException("No such map: " + mapName);
        Level L = new Level();
        L.name = mapName;
        byte[] vt = lump(w, m, "VERTEXES"), ld = lump(w, m, "LINEDEFS"), sd = lump(w, m, "SIDEDEFS"),
                sc = lump(w, m, "SECTORS"), sg = lump(w, m, "SEGS"), ss = lump(w, m, "SSECTORS"),
                nd = lump(w, m, "NODES"), th = lump(w, m, "THINGS");
        if (vt == null || ld == null || sd == null || sc == null || sg == null || ss == null || nd == null)
            throw new IllegalStateException("Map " + mapName + " is damaged or has no BSP nodes (a WAD with compiled nodes is required)");

        int nv = vt.length / 4;
        L.vx = new float[nv]; L.vy = new float[nv];
        L.minX = L.minY = 1e9f; L.maxX = L.maxY = -1e9f;
        for (int i = 0; i < nv; i++) {
            L.vx[i] = Wad.s16(vt, i * 4); L.vy[i] = Wad.s16(vt, i * 4 + 2);
            L.minX = Math.min(L.minX, L.vx[i]); L.maxX = Math.max(L.maxX, L.vx[i]);
            L.minY = Math.min(L.minY, L.vy[i]); L.maxY = Math.max(L.maxY, L.vy[i]);
        }

        int ns = sc.length / 26;
        L.sectors = new Sector[ns];
        for (int i = 0; i < ns; i++) {
            int o = i * 26;
            Sector s = new Sector();
            s.idx = i;
            s.floor = s.origFloor = Wad.s16(sc, o);
            s.ceil = s.origCeil = Wad.s16(sc, o + 2);
            s.floorName = Wad.cstr(sc, o + 4, 8).toUpperCase();
            s.ceilName = Wad.cstr(sc, o + 12, 8).toUpperCase();
            s.floorPic = gfx.flats.get(s.floorName);
            s.ceilPic = gfx.flats.get(s.ceilName);
            s.skyCeil = s.ceilName.equals("F_SKY1");
            s.light = Wad.s16(sc, o + 20);
            s.special = Wad.s16(sc, o + 22);
            s.tag = Wad.s16(sc, o + 24);
            L.sectors[i] = s;
        }

        int nsd = sd.length / 30;
        L.sides = new Side[nsd];
        for (int i = 0; i < nsd; i++) {
            int o = i * 30;
            Side s = new Side();
            s.xoff = Wad.s16(sd, o); s.yoff = Wad.s16(sd, o + 2);
            s.upperName = Wad.cstr(sd, o + 4, 8).toUpperCase();
            s.lowerName = Wad.cstr(sd, o + 12, 8).toUpperCase();
            s.midName = Wad.cstr(sd, o + 20, 8).toUpperCase();
            s.upper = gfx.texture(s.upperName);
            s.lower = gfx.texture(s.lowerName);
            s.mid = gfx.texture(s.midName);
            int sec = Wad.u16(sd, o + 28);
            s.sector = sec < ns ? L.sectors[sec] : L.sectors[0];
            L.sides[i] = s;
        }

        int nl = ld.length / 14;
        L.lines = new Line[nl];
        for (int i = 0; i < nl; i++) {
            int o = i * 14;
            Line l = new Line();
            l.idx = i;
            l.v1 = Wad.u16(ld, o); l.v2 = Wad.u16(ld, o + 2);
            l.flags = Wad.u16(ld, o + 4); l.special = Wad.u16(ld, o + 6); l.tag = Wad.u16(ld, o + 8);
            int s0 = Wad.u16(ld, o + 10), s1 = Wad.u16(ld, o + 12);
            if (l.v1 >= nv || l.v2 >= nv) { l.v1 = 0; l.v2 = 0; }
            l.x1 = L.vx[l.v1]; l.y1 = L.vy[l.v1]; l.x2 = L.vx[l.v2]; l.y2 = L.vy[l.v2];
            l.dx = l.x2 - l.x1; l.dy = l.y2 - l.y1;
            l.len = (float) Math.sqrt(l.dx * l.dx + l.dy * l.dy);
            l.minx = Math.min(l.x1, l.x2); l.maxx = Math.max(l.x1, l.x2);
            l.miny = Math.min(l.y1, l.y2); l.maxy = Math.max(l.y1, l.y2);
            l.front = s0 != 0xFFFF && s0 < nsd ? L.sides[s0] : null;
            l.back = s1 != 0xFFFF && s1 < nsd ? L.sides[s1] : null;
            l.fs = l.front != null ? l.front.sector : null;
            l.bs = l.back != null ? l.back.sector : null;
            if (l.back == null) l.flags &= ~ML_TWOSIDED;
            if (l.fs != null) l.fs.lines.add(l);
            if (l.bs != null && l.bs != l.fs) l.bs.lines.add(l);
            L.lines[i] = l;
        }
        for (Sector s : L.sectors) {
            float sx = 0, sy = 0; int c = 0;
            for (Line l : s.lines) { sx += l.x1 + l.x2; sy += l.y1 + l.y2; c += 2; }
            if (c > 0) { s.cx = sx / c; s.cy = sy / c; }
        }

        int nsg = sg.length / 12;
        L.segs = new Seg[nsg];
        for (int i = 0; i < nsg; i++) {
            int o = i * 12;
            Seg s = new Seg();
            int a = Wad.u16(sg, o), b = Wad.u16(sg, o + 2), li = Wad.u16(sg, o + 6);
            s.dir = Wad.s16(sg, o + 8) != 0;
            s.offset = Wad.s16(sg, o + 10);
            if (a >= nv) a = 0;
            if (b >= nv) b = 0;
            if (li >= nl) li = 0;
            s.x1 = L.vx[a]; s.y1 = L.vy[a]; s.x2 = L.vx[b]; s.y2 = L.vy[b];
            s.len = (float) Math.hypot(s.x2 - s.x1, s.y2 - s.y1);
            s.line = L.lines[li];
            s.side = s.dir ? s.line.back : s.line.front;
            if (s.side == null) s.side = s.line.front != null ? s.line.front : s.line.back;
            s.front = s.dir ? s.line.bs : s.line.fs;
            s.back = (s.line.flags & ML_TWOSIDED) != 0 ? (s.dir ? s.line.fs : s.line.bs) : null;
            if (s.front == null) s.front = L.sectors[0];
            L.segs[i] = s;
        }

        int nss = ss.length / 4;
        L.ssNum = new int[nss]; L.ssFirst = new int[nss]; L.ssSector = new Sector[nss];
        for (int i = 0; i < nss; i++) {
            L.ssNum[i] = Wad.u16(ss, i * 4);
            L.ssFirst[i] = Wad.u16(ss, i * 4 + 2);
            int f = L.ssFirst[i];
            L.ssSector[i] = f < nsg ? L.segs[f].front : L.sectors[0];
        }

        int nn = nd.length / 28;
        L.nx = new float[nn]; L.ny = new float[nn]; L.ndx = new float[nn]; L.ndy = new float[nn];
        L.nbb = new float[nn][8]; L.nchild = new int[nn * 2];
        for (int i = 0; i < nn; i++) {
            int o = i * 28;
            L.nx[i] = Wad.s16(nd, o); L.ny[i] = Wad.s16(nd, o + 2);
            L.ndx[i] = Wad.s16(nd, o + 4); L.ndy[i] = Wad.s16(nd, o + 6);
            for (int k = 0; k < 8; k++) L.nbb[i][k] = Wad.s16(nd, o + 8 + k * 2);
            L.nchild[i * 2] = Wad.u16(nd, o + 24);
            L.nchild[i * 2 + 1] = Wad.u16(nd, o + 26);
        }

        int nt = th == null ? 0 : th.length / 10;
        L.things = new Thing[nt];
        for (int i = 0; i < nt; i++) {
            int o = i * 10;
            Thing t = new Thing();
            t.x = Wad.s16(th, o); t.y = Wad.s16(th, o + 2);
            t.angle = Wad.s16(th, o + 4); t.type = Wad.u16(th, o + 6); t.flags = Wad.u16(th, o + 8);
            L.things[i] = t;
        }
        L.buildGrid();
        return L;
    }

    private static byte[] lump(Wad w, int mapIdx, String name) {
        // map lumps follow the marker directly (within the next 12 entries)
        for (int i = mapIdx + 1; i < Math.min(w.lumps.size(), mapIdx + 13); i++)
            if (w.name(i).equals(name)) return w.bytes(i);
        return null;
    }

    // ------------------------------------------------------------------ BSP queries
    public boolean isSubsector(int c) { return (c & 0x8000) != 0; }

    public int pointOnSide(float x, float y, int node) {
        float dx = x - nx[node], dy = y - ny[node];
        return (ndx[node] * dy - ndy[node] * dx) < 0 ? 0 : 1;
    }

    public Sector sectorAt(float x, float y) {
        if (nx.length == 0) return ssSector.length > 0 ? ssSector[0] : sectors[0];
        int c = nx.length - 1;
        int guard = 0;
        while ((c & 0x8000) == 0 && guard++ < 4096) c = nchild[c * 2 + pointOnSide(x, y, c)];
        int s = c & 0x7FFF;
        return s < ssSector.length ? ssSector[s] : sectors[0];
    }

    // ------------------------------------------------------------------ line grid
    private void buildGrid() {
        gx0 = (int) Math.floor(minX / CELL) - 1; gy0 = (int) Math.floor(minY / CELL) - 1;
        gw = (int) Math.floor(maxX / CELL) - gx0 + 2; gh = (int) Math.floor(maxY / CELL) - gy0 + 2;
        List<List<Integer>> tmp = new ArrayList<>();
        for (int i = 0; i < gw * gh; i++) tmp.add(null);
        for (Line l : lines) {
            int c0 = (int) Math.floor(l.minx / CELL) - gx0, c1 = (int) Math.floor(l.maxx / CELL) - gx0;
            int r0 = (int) Math.floor(l.miny / CELL) - gy0, r1 = (int) Math.floor(l.maxy / CELL) - gy0;
            for (int r = r0; r <= r1; r++)
                for (int c = c0; c <= c1; c++) {
                    if (c < 0 || r < 0 || c >= gw || r >= gh) continue;
                    List<Integer> li = tmp.get(r * gw + c);
                    if (li == null) { li = new ArrayList<>(); tmp.set(r * gw + c, li); }
                    li.add(l.idx);
                }
        }
        grid = new int[gw * gh][];
        for (int i = 0; i < grid.length; i++) {
            List<Integer> li = tmp.get(i);
            grid[i] = new int[li == null ? 0 : li.size()];
            for (int k = 0; k < grid[i].length; k++) grid[i][k] = li.get(k);
        }
    }

    /** Calls visitor for every line whose cells overlap the box (each line once). */
    public interface LineVisitor { boolean visit(Line l); }

    public boolean forLinesInBox(float x0, float y0, float x1, float y1, LineVisitor v) {
        validcount++;
        int c0 = (int) Math.floor(x0 / CELL) - gx0, c1 = (int) Math.floor(x1 / CELL) - gx0;
        int r0 = (int) Math.floor(y0 / CELL) - gy0, r1 = (int) Math.floor(y1 / CELL) - gy0;
        c0 = Math.max(c0, 0); r0 = Math.max(r0, 0); c1 = Math.min(c1, gw - 1); r1 = Math.min(r1, gh - 1);
        for (int r = r0; r <= r1; r++)
            for (int c = c0; c <= c1; c++) {
                int[] cell = grid[r * gw + c];
                for (int id : cell) {
                    Line l = lines[id];
                    if (l.validcount == validcount) continue;
                    l.validcount = validcount;
                    if (!v.visit(l)) return false;
                }
            }
        return true;
    }
}
