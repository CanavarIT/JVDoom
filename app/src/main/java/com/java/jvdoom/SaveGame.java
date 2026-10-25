package com.java.jvdoom;

import android.content.Context;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Save / load of a game in progress.
 *
 * A save stores the whole state of the level on top of the map itself: every sector, side and line,
 * the moving doors / lifts / floors, the light effects, all things in the world (monsters, items,
 * missiles, effects) and the player. Loading builds the same map again and then puts that state back.
 * Plain values are written by reflection together with their names, so the format survives small
 * code changes.
 */
public final class SaveGame {
    public static final int SLOTS = 6;
    private static final int MAGIC = 0x4A565356, VERSION = 1;      // "JVSV"

    private static final Set<String> GAME_SKIP = new HashSet<>(Arrays.asList(
            "turnAccum", "inMove", "inStrafe", "inFire", "inUse", "useHeld",                 // live input
            "speedMul", "takenMul", "dealtMul", "god", "noclip", "infAmmo", "noTarget", "aimAssist"));   // setup-menu options
    private static final Set<String> NONE = new HashSet<>();

    private SaveGame() { }

    static File file(Context c, int slot) { return new File(c.getFilesDir(), "jvdoom_save" + slot + ".jvs"); }

    /** Text shown in the menu, or null when the slot is empty or unreadable. */
    public static String describe(Context c, int slot) {
        File f = file(c, slot);
        if (!f.exists()) return null;
        try (DataInputStream in = new DataInputStream(new GZIPInputStream(new BufferedInputStream(new FileInputStream(f))))) {
            if (in.readInt() != MAGIC || in.readInt() != VERSION) return null;
            return in.readUTF();
        } catch (Exception e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ save
    public static boolean save(GameView gv, int slot) {
        Game g = gv.game;
        File f = file(gv.getContext(), slot);
        File tmp = new File(f.getPath() + ".tmp");
        try {
            synchronized (gv.lock) {
                DataOutputStream o = new DataOutputStream(new GZIPOutputStream(new BufferedOutputStream(new FileOutputStream(tmp))));
                try {
                    write(o, g, gv);
                } finally {
                    o.close();
                }
            }
            if (f.exists() && !f.delete()) return false;
            return tmp.renameTo(f);
        } catch (Throwable t) {
            tmp.delete();
            return false;
        }
    }

    private static void write(DataOutputStream o, Game g, GameView gv) throws Exception {
        Level L = g.L;
        int sec = g.levelTics / 35;
        String desc = g.mapName + " " + (sec / 60) + ":" + (sec % 60 < 10 ? "0" : "") + (sec % 60) + " "
                + new SimpleDateFormat("dd.MM HH:mm").format(new Date());
        o.writeInt(MAGIC); o.writeInt(VERSION);
        o.writeUTF(desc); o.writeUTF(g.mapName); o.writeInt(g.skill);

        Map<Level.Sector, Integer> secIdx = new IdentityHashMap<>();
        for (int i = 0; i < L.sectors.length; i++) secIdx.put(L.sectors[i], i);
        Map<Level.Line, Integer> lineIdx = new IdentityHashMap<>();
        for (int i = 0; i < L.lines.length; i++) lineIdx.put(L.lines[i], i);

        o.writeInt(L.sectors.length);
        for (Level.Sector s : L.sectors) writeFields(o, s, NONE);
        o.writeInt(L.sides.length);
        for (Level.Side s : L.sides) writeFields(o, s, NONE);
        o.writeInt(L.lines.length);
        for (Level.Line l : L.lines) writeFields(o, l, NONE);

        Specials sp = g.specials;
        o.writeInt(sp.movers.size());
        for (Level.Mover m : sp.movers) {
            writeFields(o, m, NONE);
            o.writeInt(m.sector == null ? -1 : secIdx.get(m.sector));
            Integer tl = m.triggerLine == null ? null : lineIdx.get(m.triggerLine);
            o.writeInt(tl == null ? -1 : tl);
        }
        o.writeInt(sp.lights.size());
        for (Specials.LightFx f : sp.lights) {
            o.writeInt(secIdx.get(f.s));
            o.writeInt(f.type); o.writeInt(f.min); o.writeInt(f.max); o.writeInt(f.count);
            o.writeInt(f.dark); o.writeInt(f.bright); o.writeInt(f.dir);
        }
        o.writeInt(sp.switchBack.size());
        for (Object[] sb : sp.switchBack) {
            Integer li = lineIdx.get((Level.Line) sb[0]);
            o.writeInt(li == null ? -1 : li);
            o.writeInt(((Number) sb[1]).intValue());
        }

        // things
        List<Mobj> list = new ArrayList<>(g.mobjs);
        for (Mobj m : g.spawnQueue) if (!list.contains(m)) list.add(m);
        Map<Mobj, Integer> mi = new IdentityHashMap<>();
        for (int i = 0; i < list.size(); i++) mi.put(list.get(i), i);
        Map<Gfx.SpriteSet, String> sprName = new IdentityHashMap<>();
        for (Map.Entry<String, Gfx.SpriteSet> e : g.gfx.sprites.entrySet()) sprName.put(e.getValue(), e.getKey());
        Map<Things.Spec, Integer> specKey = new IdentityHashMap<>();
        for (Map.Entry<Integer, Things.Spec> e : Things.SPECS.entrySet()) specKey.put(e.getValue(), e.getKey());

        Integer pi = mi.get(g.pl);
        o.writeInt(pi == null ? 0 : pi);
        o.writeInt(list.size());
        for (Mobj m : list) {
            writeFields(o, m, NONE);
            o.writeInt(m.sector == null ? -1 : secIdx.get(m.sector));
            String sn = m.spr == null ? null : sprName.get(m.spr);
            o.writeBoolean(sn != null); if (sn != null) o.writeUTF(sn);
            o.writeInt(m.def == null ? -1 : m.def.type);
            Integer sk = m.spec == null ? null : specKey.get(m.spec);
            o.writeInt(sk == null ? -1 : sk);
            o.writeBoolean(m.miss != null); if (m.miss != null) o.writeUTF(m.miss.name);
            Integer ti = m.target == null ? null : mi.get(m.target);
            o.writeInt(ti == null ? -1 : ti);
            Integer oi = m.owner == null ? null : mi.get(m.owner);
            o.writeInt(oi == null ? -1 : oi);
        }
        writeFields(o, g, GAME_SKIP);
    }

    // ------------------------------------------------------------------ load
    public static boolean load(GameView gv, int slot) {
        Game g = gv.game;
        File f = file(gv.getContext(), slot);
        if (!f.exists()) return false;
        try (DataInputStream in = new DataInputStream(new GZIPInputStream(new BufferedInputStream(new FileInputStream(f))))) {
            if (in.readInt() != MAGIC || in.readInt() != VERSION) return false;
            in.readUTF();
            String map = in.readUTF();
            int skill = in.readInt();
            synchronized (gv.lock) {
                gv.titleMode = false;
                g.skill = skill;
                g.startMap(map, false);
                read(in, g);
            }
            gv.musKey = "";
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static void read(DataInputStream in, Game g) throws Exception {
        Level L = g.L;
        Gfx gfx = g.gfx;

        int n = in.readInt();
        for (int i = 0; i < n; i++) {
            Level.Sector s = i < L.sectors.length ? L.sectors[i] : new Level.Sector();
            readFields(in, s, NONE);
            s.floorPic = gfx.flats.get(s.floorName);
            s.ceilPic = gfx.flats.get(s.ceilName);
        }
        n = in.readInt();
        for (int i = 0; i < n; i++) {
            Level.Side s = i < L.sides.length ? L.sides[i] : new Level.Side();
            readFields(in, s, NONE);
            s.upper = gfx.texture(s.upperName);
            s.lower = gfx.texture(s.lowerName);
            s.mid = gfx.texture(s.midName);
        }
        n = in.readInt();
        for (int i = 0; i < n; i++) readFields(in, i < L.lines.length ? L.lines[i] : new Level.Line(), NONE);

        // rebuild animation lists / scrollers from the restored names, then put the live effects back
        Specials sp = g.specials;
        sp.init();
        n = in.readInt();
        for (int i = 0; i < n; i++) {
            Level.Mover m = new Level.Mover();
            readFields(in, m, NONE);
            int si = in.readInt(), li = in.readInt();
            if (si < 0 || si >= L.sectors.length) continue;
            m.sector = L.sectors[si];
            m.triggerLine = li >= 0 && li < L.lines.length ? L.lines[li] : null;
            m.sector.mover = m;
            sp.movers.add(m);
        }
        sp.lights.clear();
        n = in.readInt();
        for (int i = 0; i < n; i++) {
            Specials.LightFx f = new Specials.LightFx();
            int si = in.readInt();
            f.type = in.readInt(); f.min = in.readInt(); f.max = in.readInt(); f.count = in.readInt();
            f.dark = in.readInt(); f.bright = in.readInt(); f.dir = in.readInt();
            if (si >= 0 && si < L.sectors.length) { f.s = L.sectors[si]; sp.lights.add(f); }
        }
        sp.switchBack.clear();
        n = in.readInt();
        for (int i = 0; i < n; i++) {
            int li = in.readInt(), tics = in.readInt();
            if (li >= 0 && li < L.lines.length) sp.switchBack.add(new Object[]{L.lines[li], tics});
        }

        // things
        int plIndex = in.readInt();
        n = in.readInt();
        Mobj[] arr = new Mobj[n];
        int[] tgt = new int[n], own = new int[n];
        for (int i = 0; i < n; i++) {
            Mobj m = new Mobj(0, 0, 0);
            readFields(in, m, NONE);
            int si = in.readInt();
            m.sector = si >= 0 && si < L.sectors.length ? L.sectors[si] : L.sectorAt(m.x, m.y);
            if (in.readBoolean()) m.spr = gfx.sprites.get(in.readUTF());
            int dt = in.readInt();
            m.def = dt >= 0 ? Monsters.DEFS.get(dt) : null;
            int sk = in.readInt();
            m.spec = sk >= 0 ? Things.SPECS.get(sk) : null;
            if (in.readBoolean()) m.miss = Monsters.MISSILES.get(in.readUTF());
            tgt[i] = in.readInt(); own[i] = in.readInt();
            arr[i] = m;
        }
        g.mobjs.clear();
        g.spawnQueue.clear();
        for (int i = 0; i < n; i++) {
            arr[i].target = tgt[i] >= 0 && tgt[i] < n ? arr[tgt[i]] : null;
            arr[i].owner = own[i] >= 0 && own[i] < n ? arr[own[i]] : null;
            g.mobjs.add(arr[i]);
        }
        g.pl = plIndex >= 0 && plIndex < n ? arr[plIndex] : arr[0];
        readFields(in, g, GAME_SKIP);
        if (g.sfx != null) g.sfx.listener(g.pl.x, g.pl.y, g.angle);
    }

    // ------------------------------------------------------------------ field helpers
    private static boolean eligible(Field f, Set<String> skip) {
        int m = f.getModifiers();
        if (Modifier.isStatic(m) || Modifier.isTransient(m) || skip.contains(f.getName())) return false;
        Class<?> t = f.getType();
        if (t.isArray()) return t == int[].class || t == boolean[].class || t == float[].class;
        if (Modifier.isFinal(m)) return false;
        return t == boolean.class || t == int.class || t == float.class || t == long.class || t == String.class;
    }

    private static void writeFields(DataOutputStream o, Object obj, Set<String> skip) throws Exception {
        List<Field> fl = new ArrayList<>();
        for (Field f : obj.getClass().getDeclaredFields()) if (eligible(f, skip)) { f.setAccessible(true); fl.add(f); }
        o.writeInt(fl.size());
        for (Field f : fl) {
            Class<?> t = f.getType();
            o.writeUTF(f.getName());
            if (t == boolean.class) { o.writeByte(0); o.writeBoolean(f.getBoolean(obj)); }
            else if (t == int.class) { o.writeByte(1); o.writeInt(f.getInt(obj)); }
            else if (t == float.class) { o.writeByte(2); o.writeFloat(f.getFloat(obj)); }
            else if (t == String.class) {
                String s = (String) f.get(obj);
                o.writeByte(3); o.writeBoolean(s != null); if (s != null) o.writeUTF(s);
            }
            else if (t == int[].class) {
                int[] a = (int[]) f.get(obj);
                o.writeByte(4); o.writeInt(a.length); for (int v : a) o.writeInt(v);
            }
            else if (t == boolean[].class) {
                boolean[] a = (boolean[]) f.get(obj);
                o.writeByte(5); o.writeInt(a.length); for (boolean v : a) o.writeBoolean(v);
            }
            else if (t == float[].class) {
                float[] a = (float[]) f.get(obj);
                o.writeByte(6); o.writeInt(a.length); for (float v : a) o.writeFloat(v);
            }
            else { o.writeByte(7); o.writeLong(f.getLong(obj)); }
        }
    }

    private static void readFields(DataInputStream in, Object obj, Set<String> skip) throws Exception {
        Map<String, Field> map = new HashMap<>();
        for (Field f : obj.getClass().getDeclaredFields()) if (eligible(f, skip)) { f.setAccessible(true); map.put(f.getName(), f); }
        int n = in.readInt();
        for (int i = 0; i < n; i++) {
            String name = in.readUTF();
            int tc = in.readByte();
            Field f = map.get(name);
            Class<?> t = f == null ? null : f.getType();
            switch (tc) {
                case 0: { boolean v = in.readBoolean(); if (t == boolean.class) f.setBoolean(obj, v); break; }
                case 1: { int v = in.readInt(); if (t == int.class) f.setInt(obj, v); break; }
                case 2: { float v = in.readFloat(); if (t == float.class) f.setFloat(obj, v); break; }
                case 3: { String v = in.readBoolean() ? in.readUTF() : null; if (t == String.class) f.set(obj, v); break; }
                case 4: {
                    int[] a = new int[in.readInt()]; for (int k = 0; k < a.length; k++) a[k] = in.readInt();
                    if (t == int[].class) { int[] d = (int[]) f.get(obj); System.arraycopy(a, 0, d, 0, Math.min(a.length, d.length)); }
                    break;
                }
                case 5: {
                    boolean[] a = new boolean[in.readInt()]; for (int k = 0; k < a.length; k++) a[k] = in.readBoolean();
                    if (t == boolean[].class) { boolean[] d = (boolean[]) f.get(obj); System.arraycopy(a, 0, d, 0, Math.min(a.length, d.length)); }
                    break;
                }
                case 6: {
                    float[] a = new float[in.readInt()]; for (int k = 0; k < a.length; k++) a[k] = in.readFloat();
                    if (t == float[].class) { float[] d = (float[]) f.get(obj); System.arraycopy(a, 0, d, 0, Math.min(a.length, d.length)); }
                    break;
                }
                default: { long v = in.readLong(); if (t == long.class) f.setLong(obj, v); break; }
            }
        }
    }
}
