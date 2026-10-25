package com.java.jvdoom;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Line specials (doors, lifts, floors, ceilings, switches, teleports, exits), sector light effects and texture animation. */
public final class Specials {
    static final int TR_WALK = 0, TR_SWITCH = 1, TR_GUN = 2, TR_DOOR = 3;
    static final int A_DOOR_OWC = 1, A_DOOR_STAY = 2, A_DOOR_CLOSE = 3, A_LIFT = 4, A_FLOOR = 5, A_CEIL = 6, A_EXIT = 7,
            A_SECRET = 8, A_TELE = 9, A_LIGHT = 10, A_STAIRS = 11, A_SCROLL = 12, A_DOOR_CLOSE30 = 13;
    // floor params
    static final int F_RAISE_CEIL = 1, F_RAISE_NEXT = 2, F_LOWER_LOWEST = 3, F_LOWER_HIGHEST = 4, F_LOWER_8 = 5,
            F_RAISE_24 = 6, F_RAISE_TEX = 7, F_RAISE_32 = 8;
    // ceiling params
    static final int C_RAISE_HIGH = 1, C_LOWER_FLOOR = 2, C_LOWER_8 = 3;

    /** {trigger, repeat, action, param, key(0 none,1 blue,2 yellow,3 red), fast} */
    static final Map<Integer, int[]> TABLE = new HashMap<>();
    static void sp(int n, int trig, int rep, int act, int param, int key, int fast) {
        TABLE.put(n, new int[]{trig, rep, act, param, key, fast});
    }
    static {
        // manual doors
        sp(1, TR_DOOR, 1, A_DOOR_OWC, 0, 0, 0); sp(26, TR_DOOR, 1, A_DOOR_OWC, 0, 1, 0);
        sp(27, TR_DOOR, 1, A_DOOR_OWC, 0, 2, 0); sp(28, TR_DOOR, 1, A_DOOR_OWC, 0, 3, 0);
        sp(31, TR_DOOR, 0, A_DOOR_STAY, 0, 0, 0); sp(32, TR_DOOR, 0, A_DOOR_STAY, 0, 1, 0);
        sp(33, TR_DOOR, 0, A_DOOR_STAY, 0, 3, 0); sp(34, TR_DOOR, 0, A_DOOR_STAY, 0, 2, 0);
        sp(117, TR_DOOR, 1, A_DOOR_OWC, 0, 0, 1); sp(118, TR_DOOR, 0, A_DOOR_STAY, 0, 0, 1);
        // tagged doors
        sp(2, TR_WALK, 0, A_DOOR_STAY, 0, 0, 0); sp(3, TR_WALK, 0, A_DOOR_CLOSE, 0, 0, 0); sp(4, TR_WALK, 0, A_DOOR_OWC, 0, 0, 0);
        sp(16, TR_WALK, 0, A_DOOR_CLOSE30, 0, 0, 0); sp(108, TR_WALK, 0, A_DOOR_OWC, 0, 0, 1); sp(109, TR_WALK, 0, A_DOOR_STAY, 0, 0, 1);
        sp(110, TR_WALK, 0, A_DOOR_CLOSE, 0, 0, 1);
        sp(75, TR_WALK, 1, A_DOOR_CLOSE, 0, 0, 0); sp(76, TR_WALK, 1, A_DOOR_CLOSE30, 0, 0, 0); sp(86, TR_WALK, 1, A_DOOR_STAY, 0, 0, 0);
        sp(90, TR_WALK, 1, A_DOOR_OWC, 0, 0, 0); sp(105, TR_WALK, 1, A_DOOR_OWC, 0, 0, 1); sp(106, TR_WALK, 1, A_DOOR_STAY, 0, 0, 1);
        sp(107, TR_WALK, 1, A_DOOR_CLOSE, 0, 0, 1);
        sp(29, TR_SWITCH, 0, A_DOOR_OWC, 0, 0, 0); sp(50, TR_SWITCH, 0, A_DOOR_CLOSE, 0, 0, 0); sp(103, TR_SWITCH, 0, A_DOOR_STAY, 0, 0, 0);
        sp(111, TR_SWITCH, 0, A_DOOR_OWC, 0, 0, 1); sp(112, TR_SWITCH, 0, A_DOOR_STAY, 0, 0, 1); sp(113, TR_SWITCH, 0, A_DOOR_CLOSE, 0, 0, 1);
        sp(133, TR_SWITCH, 0, A_DOOR_STAY, 0, 1, 1); sp(135, TR_SWITCH, 0, A_DOOR_STAY, 0, 3, 1); sp(137, TR_SWITCH, 0, A_DOOR_STAY, 0, 2, 1);
        sp(42, TR_SWITCH, 1, A_DOOR_CLOSE, 0, 0, 0); sp(61, TR_SWITCH, 1, A_DOOR_STAY, 0, 0, 0); sp(63, TR_SWITCH, 1, A_DOOR_OWC, 0, 0, 0);
        sp(114, TR_SWITCH, 1, A_DOOR_OWC, 0, 0, 1); sp(115, TR_SWITCH, 1, A_DOOR_STAY, 0, 0, 1); sp(116, TR_SWITCH, 1, A_DOOR_CLOSE, 0, 0, 1);
        sp(99, TR_SWITCH, 1, A_DOOR_STAY, 0, 1, 1); sp(134, TR_SWITCH, 1, A_DOOR_STAY, 0, 3, 1); sp(136, TR_SWITCH, 1, A_DOOR_STAY, 0, 2, 1);
        sp(46, TR_GUN, 1, A_DOOR_STAY, 0, 0, 0);
        // lifts
        sp(10, TR_WALK, 0, A_LIFT, 0, 0, 0); sp(121, TR_WALK, 0, A_LIFT, 0, 0, 1); sp(88, TR_WALK, 1, A_LIFT, 0, 0, 0);
        sp(120, TR_WALK, 1, A_LIFT, 0, 0, 1); sp(21, TR_SWITCH, 0, A_LIFT, 0, 0, 0); sp(122, TR_SWITCH, 0, A_LIFT, 0, 0, 1);
        sp(62, TR_SWITCH, 1, A_LIFT, 0, 0, 0); sp(123, TR_SWITCH, 1, A_LIFT, 0, 0, 1);
        // floors
        sp(5, TR_WALK, 0, A_FLOOR, F_RAISE_CEIL, 0, 0); sp(19, TR_WALK, 0, A_FLOOR, F_LOWER_HIGHEST, 0, 0);
        sp(30, TR_WALK, 0, A_FLOOR, F_RAISE_TEX, 0, 0); sp(36, TR_WALK, 0, A_FLOOR, F_LOWER_8, 0, 1);
        sp(37, TR_WALK, 0, A_FLOOR, F_LOWER_LOWEST, 0, 0); sp(38, TR_WALK, 0, A_FLOOR, F_LOWER_LOWEST, 0, 0);
        sp(56, TR_WALK, 0, A_FLOOR, F_RAISE_CEIL, 0, 0); sp(58, TR_WALK, 0, A_FLOOR, F_RAISE_24, 0, 0);
        sp(59, TR_WALK, 0, A_FLOOR, F_RAISE_24, 0, 0); sp(119, TR_WALK, 0, A_FLOOR, F_RAISE_NEXT, 0, 0);
        sp(22, TR_WALK, 0, A_FLOOR, F_RAISE_NEXT, 0, 0); sp(47, TR_GUN, 0, A_FLOOR, F_RAISE_NEXT, 0, 0);
        sp(18, TR_SWITCH, 0, A_FLOOR, F_RAISE_NEXT, 0, 0); sp(20, TR_SWITCH, 0, A_FLOOR, F_RAISE_NEXT, 0, 0);
        sp(23, TR_SWITCH, 0, A_FLOOR, F_LOWER_LOWEST, 0, 0); sp(55, TR_SWITCH, 0, A_FLOOR, F_RAISE_CEIL, 0, 0);
        sp(71, TR_SWITCH, 0, A_FLOOR, F_LOWER_8, 0, 1); sp(101, TR_SWITCH, 0, A_FLOOR, F_RAISE_CEIL, 0, 0);
        sp(102, TR_SWITCH, 0, A_FLOOR, F_LOWER_HIGHEST, 0, 0); sp(14, TR_SWITCH, 0, A_FLOOR, F_RAISE_32, 0, 0);
        sp(15, TR_SWITCH, 0, A_FLOOR, F_RAISE_24, 0, 0);
        sp(64, TR_SWITCH, 1, A_FLOOR, F_RAISE_CEIL, 0, 0); sp(65, TR_SWITCH, 1, A_FLOOR, F_RAISE_CEIL, 0, 0);
        sp(66, TR_SWITCH, 1, A_FLOOR, F_RAISE_24, 0, 0); sp(67, TR_SWITCH, 1, A_FLOOR, F_RAISE_32, 0, 0);
        sp(68, TR_SWITCH, 1, A_FLOOR, F_RAISE_NEXT, 0, 0); sp(69, TR_SWITCH, 1, A_FLOOR, F_RAISE_NEXT, 0, 0);
        sp(70, TR_SWITCH, 1, A_FLOOR, F_LOWER_8, 0, 1); sp(45, TR_SWITCH, 1, A_FLOOR, F_LOWER_HIGHEST, 0, 0);
        sp(60, TR_SWITCH, 1, A_FLOOR, F_LOWER_LOWEST, 0, 0);
        sp(83, TR_WALK, 1, A_FLOOR, F_LOWER_HIGHEST, 0, 0); sp(84, TR_WALK, 1, A_FLOOR, F_LOWER_LOWEST, 0, 0);
        sp(82, TR_WALK, 1, A_FLOOR, F_LOWER_LOWEST, 0, 0); sp(91, TR_WALK, 1, A_FLOOR, F_RAISE_CEIL, 0, 0);
        sp(92, TR_WALK, 1, A_FLOOR, F_RAISE_24, 0, 0); sp(93, TR_WALK, 1, A_FLOOR, F_RAISE_24, 0, 0);
        sp(94, TR_WALK, 1, A_FLOOR, F_RAISE_CEIL, 0, 0); sp(95, TR_WALK, 1, A_FLOOR, F_RAISE_NEXT, 0, 0);
        sp(96, TR_WALK, 1, A_FLOOR, F_RAISE_TEX, 0, 0); sp(98, TR_WALK, 1, A_FLOOR, F_LOWER_8, 0, 1);
        sp(128, TR_WALK, 1, A_FLOOR, F_RAISE_NEXT, 0, 0); sp(129, TR_WALK, 1, A_FLOOR, F_RAISE_NEXT, 0, 1);
        sp(24, TR_GUN, 0, A_FLOOR, F_RAISE_CEIL, 0, 0);
        // ceilings
        sp(40, TR_WALK, 0, A_CEIL, C_RAISE_HIGH, 0, 0); sp(41, TR_SWITCH, 0, A_CEIL, C_LOWER_FLOOR, 0, 0);
        sp(43, TR_SWITCH, 1, A_CEIL, C_LOWER_FLOOR, 0, 0); sp(44, TR_WALK, 0, A_CEIL, C_LOWER_8, 0, 0);
        sp(72, TR_WALK, 1, A_CEIL, C_LOWER_8, 0, 0);
        // exits / teleports / stairs / lights / scroll
        sp(11, TR_SWITCH, 0, A_EXIT, 0, 0, 0); sp(52, TR_WALK, 0, A_EXIT, 0, 0, 0);
        sp(51, TR_SWITCH, 0, A_SECRET, 0, 0, 0); sp(124, TR_WALK, 0, A_SECRET, 0, 0, 0);
        sp(39, TR_WALK, 0, A_TELE, 0, 0, 0); sp(97, TR_WALK, 1, A_TELE, 0, 0, 0);
        sp(7, TR_SWITCH, 0, A_STAIRS, 0, 0, 0); sp(8, TR_WALK, 0, A_STAIRS, 0, 0, 0);
        sp(12, TR_WALK, 0, A_LIGHT, 0, 0, 0); sp(13, TR_WALK, 0, A_LIGHT, 1, 0, 0); sp(35, TR_WALK, 0, A_LIGHT, 2, 0, 0);
        sp(79, TR_WALK, 1, A_LIGHT, 2, 0, 0); sp(80, TR_WALK, 1, A_LIGHT, 0, 0, 0); sp(81, TR_WALK, 1, A_LIGHT, 1, 0, 0);
        sp(138, TR_SWITCH, 1, A_LIGHT, 1, 0, 0); sp(139, TR_SWITCH, 1, A_LIGHT, 2, 0, 0);
        sp(48, TR_WALK, 1, A_SCROLL, 0, 0, 0);
    }

    final Game g;
    final List<Level.Mover> movers = new ArrayList<>();
    final List<Level.Line> scrollers = new ArrayList<>();
    final List<Object[]> switchBack = new ArrayList<>();   // {line, tics}

    static final class LightFx { Level.Sector s; int type, min, max, count, dark, bright, dir; }
    final List<LightFx> lights = new ArrayList<>();

    // animation
    static final String[][] FLAT_ANIMS = {
            {"NUKAGE1", "NUKAGE2", "NUKAGE3"}, {"FWATER1", "FWATER2", "FWATER3", "FWATER4"}, {"LAVA1", "LAVA2", "LAVA3", "LAVA4"},
            {"BLOOD1", "BLOOD2", "BLOOD3"}, {"RROCK05", "RROCK06", "RROCK07", "RROCK08"}, {"SLIME01", "SLIME02", "SLIME03", "SLIME04"},
            {"SLIME05", "SLIME06", "SLIME07", "SLIME08"}, {"SLIME09", "SLIME10", "SLIME11", "SLIME12"}};
    static final String[][] TEX_ANIMS = {
            {"BLODGR1", "BLODGR2", "BLODGR3", "BLODGR4"}, {"BLODRIP1", "BLODRIP2", "BLODRIP3", "BLODRIP4"}, {"FIREBLU1", "FIREBLU2"},
            {"FIRELAV3", "FIRELAVA"}, {"FIREMAG1", "FIREMAG2", "FIREMAG3"}, {"FIREWALA", "FIREWALB", "FIREWALL"},
            {"GSTFONT1", "GSTFONT2", "GSTFONT3"}, {"ROCKRED1", "ROCKRED2", "ROCKRED3"}, {"SLADRIP1", "SLADRIP2", "SLADRIP3"},
            {"BFALL1", "BFALL2", "BFALL3", "BFALL4"}, {"SFALL1", "SFALL2", "SFALL3", "SFALL4"}, {"WFALL1", "WFALL2", "WFALL3", "WFALL4"},
            {"DBRAIN1", "DBRAIN2", "DBRAIN3", "DBRAIN4"}};
    final List<Object[]> flatUsers = new ArrayList<>();   // {sector, boolean floor, String[] group, int idx}
    final List<Object[]> texUsers = new ArrayList<>();    // {side, int which, String[] group, int idx}

    Specials(Game g) { this.g = g; }

    // ================================================================== init
    void init() {
        movers.clear(); scrollers.clear(); lights.clear(); switchBack.clear(); flatUsers.clear(); texUsers.clear();
        Level L = g.L;
        for (Level.Sector s : L.sectors) {
            if (s.special == 9) g.totalSecrets++;
            int min = s.light;
            for (Level.Line l : s.lines) {
                Level.Sector o = l.fs == s ? l.bs : l.fs;
                if (o != null && o.light < min) min = o.light;
            }
            LightFx f = null;
            switch (s.special) {
                case 1: case 2: case 3: case 4: case 8: case 12: case 13: case 17:
                    f = new LightFx(); f.s = s; f.type = s.special; f.max = s.light; f.min = min;
                    if (s.special == 17) f.min = Math.min(s.light, min + 16);
                    f.count = 1 + g.rnd() % 8; f.dark = 1; f.bright = 1; f.dir = -1;
                    break;
                default:
            }
            if (f != null) lights.add(f);
            for (String[] grp : FLAT_ANIMS)
                for (int i = 0; i < grp.length; i++) {
                    if (s.floorName.equals(grp[i])) flatUsers.add(new Object[]{s, Boolean.TRUE, grp, i});
                    if (s.ceilName.equals(grp[i])) flatUsers.add(new Object[]{s, Boolean.FALSE, grp, i});
                }
        }
        for (Level.Side sd : L.sides) {
            for (String[] grp : TEX_ANIMS)
                for (int i = 0; i < grp.length; i++) {
                    if (sd.upperName.equals(grp[i])) texUsers.add(new Object[]{sd, 0, grp, i});
                    if (sd.lowerName.equals(grp[i])) texUsers.add(new Object[]{sd, 1, grp, i});
                    if (sd.midName.equals(grp[i])) texUsers.add(new Object[]{sd, 2, grp, i});
                }
        }
        for (Level.Line l : L.lines) if (l.special == 48 && l.front != null) scrollers.add(l);
    }

    // ================================================================== tick
    void tick() {
        for (int i = movers.size() - 1; i >= 0; i--) {
            Level.Mover m = movers.get(i);
            if (moverTick(m)) { m.sector.mover = null; movers.remove(i); }
        }
        for (LightFx f : lights) lightTick(f);
        for (Level.Line l : scrollers) l.front.xoff += 1;
        for (int i = switchBack.size() - 1; i >= 0; i--) {
            Object[] o = switchBack.get(i);
            int t = (Integer) o[1] - 1;
            if (t <= 0) { toggleSwitch((Level.Line) o[0]); g.snd("DSSWTCHN", null); switchBack.remove(i); } else o[1] = t;
        }
        if ((g.tic & 7) == 0) animate(g.tic >> 3);
    }

    void animate(int phase) {
        for (Object[] u : flatUsers) {
            Level.Sector s = (Level.Sector) u[0];
            String[] grp = (String[]) u[2];
            byte[] pic = g.gfx.flats.get(grp[(((Integer) u[3]) + phase) % grp.length]);
            if (pic == null) continue;
            if ((Boolean) u[1]) s.floorPic = pic; else s.ceilPic = pic;
        }
        for (Object[] u : texUsers) {
            Level.Side sd = (Level.Side) u[0];
            String[] grp = (String[]) u[2];
            Gfx.Pic p = g.gfx.texture(grp[(((Integer) u[3]) + phase) % grp.length]);
            if (p == null) continue;
            switch ((Integer) u[1]) { case 0: sd.upper = p; break; case 1: sd.lower = p; break; default: sd.mid = p; }
        }
    }

    void lightTick(LightFx f) {
        Level.Sector s = f.s;
        switch (f.type) {
            case 17:
                if (--f.count <= 0) {
                    int amt = (g.rnd() & 3) * 16;
                    s.light = Math.max(f.max - amt, f.min);
                    f.count = 4;
                }
                break;
            case 1:
                if (--f.count <= 0) {
                    if (s.light == f.max) { s.light = f.min; f.count = (g.rnd() & 7) + 1; }
                    else { s.light = f.max; f.count = (g.rnd() & 63) + 1; }
                }
                break;
            case 2: case 4: case 13: strobe(f, 5, 15); break;
            case 3: case 12: strobe(f, 5, 35); break;
            case 8:
                s.light += f.dir * 8;
                if (s.light <= f.min) { s.light = f.min; f.dir = 1; }
                else if (s.light >= f.max) { s.light = f.max; f.dir = -1; }
                break;
            default:
        }
    }

    void strobe(LightFx f, int bright, int dark) {
        if (--f.count <= 0) {
            if (f.s.light == f.max) { f.s.light = f.min; f.count = dark; }
            else { f.s.light = f.max; f.count = bright; }
        }
    }

    // ================================================================== movers
    /** True when the mobj's body touches the sector (centre inside, or closer to one of its lines than its radius). */
    boolean overlaps(Mobj m, Level.Sector s) {
        if (m.sector == s) return true;
        float r = m.radius;
        for (Level.Line l : s.lines) {
            if (m.x < l.minx - r || m.x > l.maxx + r || m.y < l.miny - r || m.y > l.maxy + r) continue;
            float ex = l.x2 - l.x1, ey = l.y2 - l.y1;
            float len2 = ex * ex + ey * ey;
            float t = len2 <= 0 ? 0 : ((m.x - l.x1) * ex + (m.y - l.y1) * ey) / len2;
            if (t < 0) t = 0; else if (t > 1) t = 1;
            float px = l.x1 + ex * t - m.x, py = l.y1 + ey * t - m.y;
            if (px * px + py * py < r * r) return true;
        }
        return false;
    }

    boolean blockedBy(Level.Sector s) {
        float gap = s.ceil - s.floor;
        for (int i = 0, n = g.mobjs.size(); i < n; i++) {
            Mobj m = g.mobjs.get(i);
            if (m.removed || !m.solid || !m.shootable) continue;
            if (m.height <= gap) continue;
            if (overlaps(m, s)) return true;
        }
        return false;
    }

    /** Returns true when mover finished. */
    boolean moverTick(Level.Mover m) {
        Level.Sector s = m.sector;
        switch (m.kind) {
            case 0: { // door
                if (m.state == 0) { // opening
                    s.ceil += m.speed;
                    if (s.ceil >= m.target) { s.ceil = m.target; if (m.once) return true; m.state = 1; m.wait = m.waitMax; }
                } else if (m.state == 1) {
                    if (--m.wait <= 0) {
                        if (m.reopenWait) {           // "close for 30 seconds, then open" finished waiting
                            m.reopenWait = false; m.state = 0; m.once = true;
                            m.target = lowestNeighborCeil(s) - 4;
                            g.sndAt(m.speed > 4 ? "DSBDOPN" : "DSDOROPN", s.cx, s.cy);
                        } else { m.state = 2; g.sndAt(m.speed > 4 ? "DSBDCLS" : "DSDORCLS", s.cx, s.cy); }
                    }
                } else {
                    float prev = s.ceil;
                    s.ceil -= m.speed;
                    if (blockedBy(s)) { s.ceil = prev; m.state = 0; m.reopen = false; g.sndAt(m.speed > 4 ? "DSBDOPN" : "DSDOROPN", s.cx, s.cy); break; }
                    if (s.ceil <= m.home) {
                        s.ceil = m.home;
                        if (m.reopen) { m.reopen = false; m.reopenWait = true; m.state = 1; m.wait = 30 * 35; return false; }
                        return true;
                    }
                }
                return false;
            }
            case 1: { // lift
                if (m.state == 0) {
                    float of = s.floor;
                    s.floor -= m.speed;
                    if (s.floor <= m.target) { s.floor = m.target; m.state = 1; m.wait = m.waitMax; g.sndAt("DSPSTOP", s.cx, s.cy); }
                    settleThings(s, of);
                } else if (m.state == 1) {
                    if (--m.wait <= 0) { m.state = 2; g.sndAt("DSPSTART", s.cx, s.cy); }
                } else {
                    float prev = s.floor;
                    s.floor += m.speed;
                    if (blockedBy(s)) { s.floor = prev; m.state = 0; break; }
                    if (s.floor >= m.home) s.floor = m.home;
                    settleThings(s, prev);
                    if (s.floor >= m.home) { g.sndAt("DSPSTOP", s.cx, s.cy); return true; }
                }
                return false;
            }
            case 2: { // floor
                float prev = s.floor;
                if (m.target > s.floor) { s.floor = Math.min(m.target, s.floor + m.speed); if (blockedBy(s)) { s.floor = prev; return false; } }
                else s.floor = Math.max(m.target, s.floor - m.speed);
                settleThings(s, prev);
                if (s.floor == m.target) { g.sndAt("DSPSTOP", s.cx, s.cy); return true; }
                return false;
            }
            default: { // ceiling
                float prev = s.ceil;
                if (m.target > s.ceil) s.ceil = Math.min(m.target, s.ceil + m.speed);
                else { s.ceil = Math.max(m.target, s.ceil - m.speed); if (blockedBy(s)) { s.ceil = prev; return false; } }
                if (s.ceil == m.target) return true;
                return false;
            }
        }
        return false;
    }

    /** Things standing on a sector's floor follow it up and down (P_ThingHeightClip). */
    void settleThings(Level.Sector s, float oldFloor) {
        float nf = s.floor;
        if (nf == oldFloor) return;
        for (int i = 0, n = g.mobjs.size(); i < n; i++) {
            Mobj m = g.mobjs.get(i);
            if (m.removed || m.noGravity || m == g.pl) continue;
            if (m.kind == Mobj.MISSILE || m.kind == Mobj.EFFECT || m.kind == Mobj.PLAYER) continue;
            boolean onIt = Math.abs(m.z - oldFloor) < 0.5f || m.z < nf;
            if (!onIt) continue;
            Level.Sector ms = m.sector != null ? m.sector : g.L.sectorAt(m.x, m.y);
            if (ms != s && g.L.sectorAt(m.x, m.y) != s) continue;
            m.z = nf; m.oz = nf; m.floorz = nf;
        }
    }

    Level.Mover newMover(Level.Sector s, int kind) {
        Level.Mover m = new Level.Mover();
        m.sector = s; m.kind = kind;
        s.mover = m; movers.add(m);
        return m;
    }

    float lowestNeighborCeil(Level.Sector s) {
        float v = 1e9f;
        for (Level.Line l : s.lines) { Level.Sector o = l.fs == s ? l.bs : l.fs; if (o != null && o != s && o.ceil < v) v = o.ceil; }
        return v == 1e9f ? s.ceil : v;
    }
    float highestNeighborCeil(Level.Sector s) {
        float v = -1e9f;
        for (Level.Line l : s.lines) { Level.Sector o = l.fs == s ? l.bs : l.fs; if (o != null && o != s && o.ceil > v) v = o.ceil; }
        return v == -1e9f ? s.ceil : v;
    }
    float lowestNeighborFloor(Level.Sector s) {
        float v = s.floor;
        for (Level.Line l : s.lines) { Level.Sector o = l.fs == s ? l.bs : l.fs; if (o != null && o != s && o.floor < v) v = o.floor; }
        return v;
    }
    float highestNeighborFloor(Level.Sector s) {
        float v = -1e9f;
        for (Level.Line l : s.lines) { Level.Sector o = l.fs == s ? l.bs : l.fs; if (o != null && o != s && o.floor > v) v = o.floor; }
        return v == -1e9f ? s.floor : v;
    }
    float nextHigherFloor(Level.Sector s) {
        float v = 1e9f;
        for (Level.Line l : s.lines) { Level.Sector o = l.fs == s ? l.bs : l.fs; if (o != null && o != s && o.floor > s.floor && o.floor < v) v = o.floor; }
        return v == 1e9f ? s.floor : v;
    }

    boolean startDoor(Level.Sector s, int action, boolean fast) {
        if (s.mover != null) return false;
        Level.Mover m = newMover(s, 0);
        m.speed = fast ? 8 : 2; m.waitMax = 150; m.home = s.floor;
        m.target = lowestNeighborCeil(s) - 4;
        if (action == A_DOOR_CLOSE30) { m.state = 2; m.reopen = true; g.sndAt(fast ? "DSBDCLS" : "DSDORCLS", s.cx, s.cy); }
        else if (action == A_DOOR_CLOSE) { m.state = 2; g.sndAt(fast ? "DSBDCLS" : "DSDORCLS", s.cx, s.cy); }
        else {
            m.state = 0; m.once = action == A_DOOR_STAY;
            g.sndAt(fast ? "DSBDOPN" : "DSDOROPN", s.cx, s.cy);
        }
        return true;
    }

    boolean startLift(Level.Sector s, boolean fast) {
        if (s.mover != null) return false;
        Level.Mover m = newMover(s, 1);
        m.speed = fast ? 8 : 4; m.waitMax = 105; m.home = s.floor; m.state = 0;
        m.target = lowestNeighborFloor(s);
        g.sndAt("DSPSTART", s.cx, s.cy);
        return true;
    }

    /** Line specials whose floor move also changes the sector's flat / type (damage). 1 = flat only, 2 = flat + type copied, 3 = flat copied, type cleared ("no more damage"). */
    static final Map<Integer, Integer> FLOOR_CHANGE = new HashMap<>();
    static {
        for (int n : new int[]{14, 15, 66, 67}) FLOOR_CHANGE.put(n, 1);
        for (int n : new int[]{59, 93}) FLOOR_CHANGE.put(n, 2);
        for (int n : new int[]{20, 22, 47, 68, 95}) FLOOR_CHANGE.put(n, 3);
    }
    Level.Line curLine;

    void applyFloorChange(Level.Sector s) {
        if (curLine == null || curLine.fs == null) return;
        Integer mode = FLOOR_CHANGE.get(curLine.special);
        if (mode == null) return;
        Level.Sector m = curLine.fs;
        s.floorPic = m.floorPic; s.floorName = m.floorName;
        if (mode == 2) s.special = m.special;
        else if (mode == 3) s.special = 0;
        // stop the flat animation from restoring the old flat
        for (java.util.Iterator<Object[]> it = flatUsers.iterator(); it.hasNext(); ) {
            Object[] u = it.next();
            if (u[0] == s && (Boolean) u[1]) it.remove();
        }
    }

    boolean startFloor(Level.Sector s, int type, boolean fast) {
        if (s.mover != null) return false;
        float t = s.floor;
        switch (type) {
            case F_RAISE_CEIL: t = Math.min(lowestNeighborCeil(s), s.ceil); break;
            case F_RAISE_NEXT: t = nextHigherFloor(s); break;
            case F_LOWER_LOWEST: t = lowestNeighborFloor(s); break;
            case F_LOWER_HIGHEST: t = highestNeighborFloor(s); break;
            case F_LOWER_8: t = highestNeighborFloor(s) + 8; if (t > s.floor) t = s.floor; break;
            case F_RAISE_24: t = s.floor + 24; break;
            case F_RAISE_32: t = s.floor + 32; break;
            case F_RAISE_TEX: {
                float h = 1e9f;
                for (Level.Line l : s.lines) for (Level.Side sd : new Level.Side[]{l.front, l.back})
                    if (sd != null && sd.lower != null && sd.lower.h < h) h = sd.lower.h;
                t = s.floor + (h == 1e9f ? 64 : h);
                break;
            }
            default:
        }
        if (t == s.floor) return false;
        Level.Mover m = newMover(s, 2);
        m.target = t; m.speed = fast ? 4 : 1;
        applyFloorChange(s);
        g.sndAt("DSSTNMOV", s.cx, s.cy);
        return true;
    }

    boolean startCeil(Level.Sector s, int type) {
        if (s.mover != null) return false;
        float t = s.ceil;
        switch (type) {
            case C_RAISE_HIGH: t = highestNeighborCeil(s); break;
            case C_LOWER_FLOOR: t = s.floor; break;
            case C_LOWER_8: t = s.floor + 8; break;
            default:
        }
        if (t == s.ceil) return false;
        Level.Mover m = newMover(s, 3);
        m.target = t; m.speed = 1;
        g.sndAt("DSSTNMOV", s.cx, s.cy);
        return true;
    }

    boolean buildStairs(Level.Sector s) {
        boolean any = false;
        float h = s.floor + 8;
        Level.Sector cur = s;
        for (int guard = 0; guard < 64 && cur != null; guard++) {
            if (cur.mover == null) {
                Level.Mover m = newMover(cur, 2);
                m.target = h; m.speed = 0.5f; any = true;
            }
            Level.Sector next = null;
            for (Level.Line l : cur.lines) {
                if (l.fs != cur || l.bs == null || l.bs.mover != null) continue;
                if (!l.bs.floorName.equals(cur.floorName)) continue;
                next = l.bs; break;
            }
            cur = next; h += 8;
        }
        return any;
    }

    void setLight(Level.Sector s, int mode) {
        int v;
        if (mode == 1) v = 255;
        else if (mode == 2) v = 35;
        else {
            v = 0;
            for (Level.Line l : s.lines) { Level.Sector o = l.fs == s ? l.bs : l.fs; if (o != null && o.light > v) v = o.light; }
        }
        s.light = v;
        for (LightFx f : lights) if (f.s == s) { f.max = v; }
    }

    // ================================================================== triggering
    boolean activate(Level.Line l, int[] sp, Mobj who) {
        boolean ok = false;
        int act = sp[2], prm = sp[3];
        boolean fast = sp[5] != 0;
        curLine = l;
        switch (act) {
            case A_EXIT: if (who == g.pl) { g.exitLevel(false); ok = true; } break;
            case A_SECRET: if (who == g.pl) { g.exitLevel(true); ok = true; } break;
            case A_SCROLL: break;
            case A_TELE: ok = teleport(l, who); break;
            default:
                if (l.tag == 0 && sp[0] != TR_DOOR) return false;
                for (Level.Sector s : g.L.sectors) {
                    if (sp[0] == TR_DOOR) continue;
                    if (s.tag != l.tag) continue;
                    switch (act) {
                        case A_DOOR_OWC: case A_DOOR_STAY: case A_DOOR_CLOSE: case A_DOOR_CLOSE30: ok |= startDoor(s, act, fast); break;
                        case A_LIFT: ok |= startLift(s, fast); break;
                        case A_FLOOR: ok |= startFloor(s, prm, fast); break;
                        case A_CEIL: ok |= startCeil(s, prm); break;
                        case A_STAIRS: ok |= buildStairs(s); break;
                        case A_LIGHT: setLight(s, prm); ok = true; break;
                        default:
                    }
                }
        }
        return ok;
    }

    boolean teleport(Level.Line l, Mobj who) {
        if (who != g.pl) return false;
        for (Level.Thing t : g.teleDests) {
            Level.Sector s = g.L.sectorAt(t.x, t.y);
            if (s.tag != l.tag) continue;
            g.sndAt("DSTELEPT", who.x, who.y);
            who.x = who.ox = t.x; who.y = who.oy = t.y;
            who.z = who.oz = s.floor; who.mx = who.my = 0;
            who.sector = s; who.floorz = s.floor; who.ceilz = s.ceil;
            g.angle = (float) Math.toRadians(t.angle); who.angle = g.angle;
            g.eyeZ = g.eyeZPrev = who.z + 41;
            g.lastPx = who.x; g.lastPy = who.y;
            g.sndAt("DSTELEPT", t.x, t.y);
            return true;
        }
        return false;
    }

    static boolean isNameSwitch(String n) { return n.startsWith("SW1") || n.startsWith("SW2"); }

    void toggleSwitch(Level.Line l) {
        if (l.front == null) return;
        Level.Side s = l.front;
        s.upperName = flip(s.upperName); s.lowerName = flip(s.lowerName); s.midName = flip(s.midName);
        s.upper = g.gfx.texture(s.upperName); s.lower = g.gfx.texture(s.lowerName); s.mid = g.gfx.texture(s.midName);
    }
    String flip(String n) {
        if (n.startsWith("SW1")) return "SW2" + n.substring(3);
        if (n.startsWith("SW2")) return "SW1" + n.substring(3);
        return n;
    }
    boolean hasSwitchTex(Level.Line l) {
        return l.front != null && (isNameSwitch(l.front.upperName) || isNameSwitch(l.front.lowerName) || isNameSwitch(l.front.midName));
    }

    static final String[] KEYNAME = {"blue", "yellow", "red"};

    /** Player/monster presses a line. Returns true if the line did something (or is a special). */
    boolean useLine(Level.Line l, Mobj who) {
        int[] sp = TABLE.get(l.special);
        if (sp == null) return false;
        if (sp[0] != TR_DOOR && sp[0] != TR_SWITCH) return false;
        boolean player = who == g.pl;
        if (!player && !(sp[0] == TR_DOOR && sp[4] == 0 && sp[2] == A_DOOR_OWC)) return false;
        if (player && Phys.side(l, who.x, who.y) >= 0) return false;      // must face the front side
        if (sp[4] != 0 && player) {
            if (!g.keys[sp[4] - 1]) { g.msg(sp[0] == TR_DOOR ? "You need a " + KEYNAME[sp[4] - 1] + " card to open this door" : "You need a " + KEYNAME[sp[4] - 1] + " key to activate this object"); g.snd("DSOOF", null); return true; }
        }
        if (sp[0] == TR_DOOR) {
            if (l.bs == null) return false;
            Level.Sector s = l.bs;
            if (s.mover != null && s.mover.kind == 0) {
                Level.Mover m = s.mover;
                if (sp[2] == A_DOOR_OWC) {
                    if (m.state == 2) { m.state = 0; g.sndAt(m.speed > 4 ? "DSBDOPN" : "DSDOROPN", s.cx, s.cy); }
                    else if (player) { m.state = 2; m.wait = 0; g.sndAt(m.speed > 4 ? "DSBDCLS" : "DSDORCLS", s.cx, s.cy); }
                }
                return true;
            }
            if (!startDoor(s, sp[2], sp[5] != 0)) return true;
            if (sp[1] == 0) l.special = 0;
            return true;
        }
        // switch
        boolean ok = activate(l, sp, who);
        if (ok || sp[2] == A_EXIT || sp[2] == A_SECRET) {
            g.snd("DSSWTCHN", who);
            if (hasSwitchTex(l)) {
                toggleSwitch(l);
                if (sp[1] != 0) switchBack.add(new Object[]{l, 35});
            }
            if (sp[1] == 0) l.special = 0;
        }
        return true;
    }

    void shootLine(Level.Line l, Mobj who) {
        int[] sp = TABLE.get(l.special);
        if (sp == null || sp[0] != TR_GUN || who != g.pl) return;
        if (activate(l, sp, who) && sp[1] == 0) l.special = 0;
    }

    void checkCross(final Mobj m, float ox, float oy) {
        final float nx = m.x, ny = m.y;
        if (ox == nx && oy == ny) return;
        final float fx = ox, fy = oy;
        g.L.forLinesInBox(Math.min(ox, nx) - 1, Math.min(oy, ny) - 1, Math.max(ox, nx) + 1, Math.max(oy, ny) + 1, new Level.LineVisitor() {
            public boolean visit(Level.Line l) {
                if (l.special == 0) return true;
                int[] sp = TABLE.get(l.special);
                if (sp == null || sp[0] != TR_WALK) return true;
                float s0 = Phys.side(l, fx, fy), s1 = Phys.side(l, nx, ny);
                if ((s0 < 0) == (s1 < 0)) return true;
                if (sp[2] == A_TELE && !(s0 < 0 && s1 >= 0)) return true;
                // intersection within the line extents
                float dx = nx - fx, dy = ny - fy;
                float den = dx * l.dy - dy * l.dx;
                if (Math.abs(den) < 1e-6f) return true;
                float u = ((l.x1 - fx) * dy - (l.y1 - fy) * dx) / den;
                if (u < 0f || u > 1f) return true;
                if (activate(l, sp, m) && sp[1] == 0) l.special = 0;
                return true;
            }
        });
    }

    /** Use-ray: first special line in front of the player, or null if a wall blocks first. */
    Level.Line findUseLine(float x, float y, float c, float s, float range) {
        final float ex = x + c * range, ey = y + s * range;
        final float dx = ex - x, dy = ey - y;
        final Level.Line[] best = {null};
        final float[] bt = {2f};
        final boolean[] wall = {false};
        final float[] wt = {2f};
        final float fx = x, fy = y;
        g.L.forLinesInBox(Math.min(x, ex) - 1, Math.min(y, ey) - 1, Math.max(x, ex) + 1, Math.max(y, ey) + 1, new Level.LineVisitor() {
            public boolean visit(Level.Line l) {
                float den = dx * l.dy - dy * l.dx;
                if (Math.abs(den) < 1e-6f) return true;
                float t = ((l.x1 - fx) * l.dy - (l.y1 - fy) * l.dx) / den;
                float u = ((l.x1 - fx) * dy - (l.y1 - fy) * dx) / den;
                if (t < 0f || t > 1f || u < 0f || u > 1f) return true;
                if (l.special != 0 && TABLE.containsKey(l.special)) { if (t < bt[0]) { bt[0] = t; best[0] = l; } }
                else if (l.back == null || Math.min(l.fs.ceil, l.bs.ceil) <= Math.max(l.fs.floor, l.bs.floor)) { if (t < wt[0]) wt[0] = t; }
                return true;
            }
        });
        if (best[0] != null && bt[0] <= wt[0]) return best[0];
        return null;
    }

    // ================================================================== sound
    void soundAlert(Mobj who) {
        int n = g.L.sectors.length;
        int[] best = new int[n];
        java.util.Arrays.fill(best, 99);
        if (who.sector != null) flood(who.sector, 0, best);
        for (int i = 0, k = g.mobjs.size(); i < k; i++) {
            Mobj m = g.mobjs.get(i);
            if (m.kind != Mobj.MONSTER || m.sector == null || m.removed) continue;
            if (best[m.sector.idx] < 99) g.ai.alert(m, who);
        }
    }

    void flood(Level.Sector s, int block, int[] best) {
        if (best[s.idx] <= block) return;
        best[s.idx] = block;
        for (Level.Line l : s.lines) {
            if (l.back == null) continue;
            if (Math.min(l.fs.ceil, l.bs.ceil) <= Math.max(l.fs.floor, l.bs.floor)) continue;
            Level.Sector o = l.fs == s ? l.bs : l.fs;
            if ((l.flags & Level.ML_SOUNDBLOCK) != 0) { if (block == 0) flood(o, 1, best); }
            else flood(o, block, best);
        }
    }
}
