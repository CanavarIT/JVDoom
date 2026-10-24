package com.java.jvdoom;

import java.util.HashMap;
import java.util.Map;

/** Table of static things: decorations, pickups, barrels. Key = DoomEd number. */
public final class Things {
    public static final int P_CLIP = 1, P_CLIPBOX = 2, P_SHELL = 3, P_SHELLBOX = 4, P_ROCKET = 5, P_ROCKETBOX = 6,
            P_CELL = 7, P_CELLPACK = 8, P_BACKPACK = 9, P_STIM = 10, P_MEDI = 11, P_HBONUS = 12, P_ABONUS = 13,
            P_ARMOR1 = 14, P_ARMOR2 = 15, P_SOUL = 16, P_MEGA = 17, P_INVULN = 18, P_BERSERK = 19, P_INVIS = 20,
            P_SUIT = 21, P_MAP = 22, P_LIGHT = 23, P_KEY_BLUE = 24, P_KEY_YELLOW = 25, P_KEY_RED = 26,
            P_W_SHOTGUN = 30, P_W_CHAINGUN = 31, P_W_ROCKET = 32, P_W_PLASMA = 33, P_W_BFG = 34, P_W_SAW = 35, P_W_SSG = 36;

    public static final class Spec {
        public String spr, frames;
        public int tics = 6;
        public boolean solid, fullbright, hang, barrel;
        public float radius = 16, height = 16;
        public int pickup;
    }

    public static final Map<Integer, Spec> SPECS = new HashMap<>();

    static Spec d(int ed, String spr, String frames, int tics, boolean solid, float r, float h, boolean fb) {
        Spec s = new Spec();
        s.spr = spr; s.frames = frames; s.tics = tics; s.solid = solid; s.radius = r; s.height = h; s.fullbright = fb;
        SPECS.put(ed, s);
        return s;
    }
    static Spec item(int ed, String spr, String frames, int pk, boolean fb) {
        Spec s = d(ed, spr, frames, 6, false, 20, 16, fb);
        s.pickup = pk;
        return s;
    }

    static {
        // decorations (solid)
        d(2028, "COLU", "A", 6, true, 16, 16, true);
        d(30, "COL1", "A", 6, true, 16, 16, false);
        d(31, "COL2", "A", 6, true, 16, 16, false);
        d(32, "COL3", "A", 6, true, 16, 16, false);
        d(33, "COL4", "A", 6, true, 16, 16, false);
        d(35, "CBRA", "A", 6, true, 16, 16, true);
        d(36, "COL5", "AB", 14, true, 16, 16, false);
        d(37, "COL6", "A", 6, true, 16, 16, false);
        d(41, "CEYE", "ABCB", 6, true, 16, 16, true);
        d(42, "FSKU", "ABC", 6, true, 16, 16, true);
        d(43, "TRE1", "A", 6, true, 16, 16, false);
        d(44, "TBLU", "ABCD", 4, true, 16, 16, true);
        d(45, "TGRN", "ABCD", 4, true, 16, 16, true);
        d(46, "TRED", "ABCD", 4, true, 16, 16, true);
        d(47, "SMIT", "A", 6, true, 16, 16, false);
        d(48, "ELEC", "A", 6, true, 16, 16, false);
        d(54, "TRE2", "A", 6, true, 32, 16, false);
        d(55, "SMBT", "ABCD", 4, true, 16, 16, true);
        d(56, "SMGT", "ABCD", 4, true, 16, 16, true);
        d(57, "SMRT", "ABCD", 4, true, 16, 16, true);
        d(70, "FCAN", "ABC", 4, true, 10, 42, true);
        d(85, "TLMP", "ABCD", 4, true, 16, 16, true);
        d(86, "TLP2", "ABCD", 4, true, 16, 16, true);
        d(25, "POL1", "A", 6, true, 16, 16, false);
        d(26, "POL6", "AB", 6, true, 16, 16, false);
        d(27, "POL4", "A", 6, true, 16, 16, false);
        d(28, "POL2", "A", 6, true, 16, 16, false);
        d(29, "POL3", "AB", 6, true, 16, 16, true);
        // decorations (walk-through)
        d(34, "CAND", "A", 6, false, 16, 16, true);
        d(10, "PLAY", "W", 6, false, 16, 16, false);
        d(12, "PLAY", "W", 6, false, 16, 16, false);
        d(15, "PLAY", "N", 6, false, 16, 16, false);
        d(18, "POSS", "L", 6, false, 16, 16, false);
        d(19, "SPOS", "L", 6, false, 16, 16, false);
        d(20, "TROO", "M", 6, false, 16, 16, false);
        d(21, "SARG", "N", 6, false, 16, 16, false);
        d(22, "HEAD", "L", 6, false, 16, 16, false);
        d(23, "SKUL", "K", 6, false, 16, 16, false);
        d(24, "POL5", "A", 6, false, 16, 16, false);
        d(79, "POB1", "A", 6, false, 16, 16, false);
        d(80, "POB2", "A", 6, false, 16, 16, false);
        d(81, "BRS1", "A", 6, false, 16, 16, false);
        // hanging
        Spec s;
        s = d(49, "GOR1", "ABCB", 10, true, 16, 68, false); s.hang = true;
        s = d(50, "GOR2", "A", 6, true, 16, 84, false); s.hang = true;
        s = d(51, "GOR3", "A", 6, true, 16, 84, false); s.hang = true;
        s = d(52, "GOR4", "A", 6, true, 16, 68, false); s.hang = true;
        s = d(53, "GOR5", "A", 6, true, 16, 52, false); s.hang = true;
        s = d(59, "GOR2", "A", 6, false, 20, 84, false); s.hang = true;
        s = d(60, "GOR4", "A", 6, false, 20, 68, false); s.hang = true;
        s = d(61, "GOR3", "A", 6, false, 20, 52, false); s.hang = true;
        s = d(62, "GOR5", "A", 6, false, 20, 52, false); s.hang = true;
        s = d(63, "GOR1", "ABCB", 10, false, 20, 68, false); s.hang = true;
        s = d(73, "HDB1", "A", 6, true, 16, 88, false); s.hang = true;
        s = d(74, "HDB2", "A", 6, true, 16, 88, false); s.hang = true;
        s = d(75, "HDB3", "A", 6, true, 16, 64, false); s.hang = true;
        s = d(76, "HDB4", "A", 6, true, 16, 64, false); s.hang = true;
        s = d(77, "HDB5", "A", 6, true, 16, 64, false); s.hang = true;
        s = d(78, "HDB6", "A", 6, true, 16, 64, false); s.hang = true;
        // barrel
        s = d(2035, "BAR1", "AB", 6, true, 10, 42, false); s.barrel = true;

        // ammo
        item(2007, "CLIP", "A", P_CLIP, false);
        item(2048, "AMMO", "A", P_CLIPBOX, false);
        item(2008, "SHEL", "A", P_SHELL, false);
        item(2049, "SBOX", "A", P_SHELLBOX, false);
        item(2010, "ROCK", "A", P_ROCKET, false);
        item(2046, "BROK", "A", P_ROCKETBOX, false);
        item(2047, "CELL", "A", P_CELL, false);
        item(17, "CELP", "A", P_CELLPACK, false);
        item(8, "BPAK", "A", P_BACKPACK, false);
        // health / armor / powerups
        item(2011, "STIM", "A", P_STIM, false);
        item(2012, "MEDI", "A", P_MEDI, false);
        item(2014, "BON1", "ABCDCB", P_HBONUS, true);
        item(2015, "BON2", "ABCDCB", P_ABONUS, true);
        item(2018, "ARM1", "AB", P_ARMOR1, false);
        item(2019, "ARM2", "AB", P_ARMOR2, false);
        item(2013, "SOUL", "ABCDCB", P_SOUL, true);
        item(83, "MEGA", "ABCD", P_MEGA, true);
        item(2022, "PINV", "ABCD", P_INVULN, true);
        item(2023, "PSTR", "A", P_BERSERK, true);
        item(2024, "PINS", "ABCD", P_INVIS, true);
        item(2025, "SUIT", "A", P_SUIT, false);
        item(2026, "PMAP", "ABCDCB", P_MAP, true);
        item(2045, "PVIS", "AB", P_LIGHT, true);
        // keys
        item(5, "BKEY", "AB", P_KEY_BLUE, false);
        item(40, "BSKU", "AB", P_KEY_BLUE, false);
        item(6, "YKEY", "AB", P_KEY_YELLOW, false);
        item(39, "YSKU", "AB", P_KEY_YELLOW, false);
        item(13, "RKEY", "AB", P_KEY_RED, false);
        item(38, "RSKU", "AB", P_KEY_RED, false);
        // weapons
        item(2001, "SHOT", "A", P_W_SHOTGUN, false);
        item(2002, "MGUN", "A", P_W_CHAINGUN, false);
        item(2003, "LAUN", "A", P_W_ROCKET, false);
        item(2004, "PLAS", "A", P_W_PLASMA, false);
        item(2006, "BFUG", "A", P_W_BFG, false);
        item(2005, "CSAW", "A", P_W_SAW, false);
        item(82, "SGN2", "A", P_W_SSG, false);
    }
}
