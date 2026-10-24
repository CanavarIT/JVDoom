package com.java.jvdoom;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Reads IWAD/PWAD files fully into memory; lump lookup is case-insensitive, last lump wins. */
public final class Wad {
    public static final class Lump {
        public final String name;
        public final int pos, size;
        Lump(String n, int p, int s) { name = n; pos = p; size = s; }
    }

    public final byte[] data;
    public final List<Lump> lumps = new ArrayList<>();
    private final Map<String, Integer> index = new HashMap<>();
    public final boolean isIwad;

    public Wad(byte[] d) throws IOException {
        data = d;
        if (d.length < 12) throw new IOException("File is too small to be a WAD");
        String id = new String(d, 0, 4, "US-ASCII");
        if (!id.equals("IWAD") && !id.equals("PWAD")) throw new IOException("Not a WAD file (header: " + id + ")");
        isIwad = id.equals("IWAD");
        int n = i32(d, 4), dir = i32(d, 8);
        if (n < 0 || dir < 0 || dir + (long) n * 16 > d.length) throw new IOException("Corrupt WAD (directory is outside the file)");
        for (int i = 0; i < n; i++) {
            int o = dir + i * 16;
            int p = i32(d, o), s = i32(d, o + 4);
            String nm = cstr(d, o + 8, 8).toUpperCase();
            if (p < 0 || s < 0 || (long) p + s > d.length) { p = 0; s = 0; }
            lumps.add(new Lump(nm, p, s));
            index.put(nm, i);
        }
    }

    public static Wad read(InputStream in) throws IOException {
        ByteArrayOutputStream bo = new ByteArrayOutputStream(1 << 22);
        byte[] b = new byte[1 << 16];
        int r;
        while ((r = in.read(b)) > 0) bo.write(b, 0, r);
        in.close();
        return new Wad(bo.toByteArray());
    }

    public static int i32(byte[] d, int o) {
        return (d[o] & 255) | (d[o + 1] & 255) << 8 | (d[o + 2] & 255) << 16 | (d[o + 3] & 255) << 24;
    }
    public static int u16(byte[] d, int o) { return (d[o] & 255) | (d[o + 1] & 255) << 8; }
    public static int s16(byte[] d, int o) { return (short) u16(d, o); }
    public static String cstr(byte[] d, int o, int max) {
        int e = 0;
        while (e < max && d[o + e] != 0) e++;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < e; i++) sb.append((char) (d[o + i] & 255));
        return sb.toString();
    }

    public int find(String name) {
        Integer i = index.get(name.toUpperCase());
        return i == null ? -1 : i;
    }
    public boolean has(String name) { return find(name) >= 0; }

    /** Find lump searching only after a given index (used for maps). */
    public int findAfter(String name, int after) {
        name = name.toUpperCase();
        for (int i = after + 1; i < lumps.size(); i++) if (lumps.get(i).name.equals(name)) return i;
        return -1;
    }

    public byte[] bytes(int i) {
        Lump l = lumps.get(i);
        byte[] r = new byte[l.size];
        System.arraycopy(data, l.pos, r, 0, l.size);
        return r;
    }
    public byte[] bytes(String name) {
        int i = find(name);
        return i < 0 ? null : bytes(i);
    }

    public String name(int i) { return lumps.get(i).name; }
    public int pos(int i) { return lumps.get(i).pos; }
    public int size(int i) { return lumps.get(i).size; }

    /** List map names present (E1M1.. or MAP01..) in order. */
    public List<String> maps() {
        List<String> r = new ArrayList<>();
        for (int i = 0; i + 1 < lumps.size(); i++) {
            String n = lumps.get(i).name;
            boolean m = n.matches("E\\dM\\d") || n.matches("MAP\\d\\d");
            if (m && lumps.get(i + 1).name.equals("THINGS")) r.add(n);
        }
        return r;
    }

    public boolean isDoom2() { return has("MAP01") && !has("E1M1"); }
}
