package com.java.jvdoom;

import java.util.HashMap;
import java.util.Map;

/** Compact data tables for monsters, projectiles and effects (sprite frames, timings, stats). */
public final class Monsters {
    public static final int SEQ_STAND = 0, SEQ_WALK = 1, SEQ_ATTACK = 2, SEQ_PAIN = 3, SEQ_DIE = 4;
    public static final int ATK_NONE = 0, ATK_HITSCAN = 1, ATK_MISSILE = 2, ATK_CHARGE = 3, ATK_MELEE = 4;

    /** Animation sequence: frame letters, ticks per frame (last frame tics<0 = hold), bitmask of attack frames. */
    public static final class Seq {
        public final String frames;
        public final int[] tics;
        public final int events;
        public Seq(String frames, int tics, int events) {
            this.frames = frames; this.events = events;
            this.tics = new int[frames.length()];
            java.util.Arrays.fill(this.tics, tics);
        }
        public Seq(String frames, int[] tics, int events) { this.frames = frames; this.tics = tics; this.events = events; }
    }

    public static final class Def {
        public int type;
        public String spr, name;
        public int hp, painChance = 200, mass = 100;
        public float radius = 20, height = 56, speed = 8;
        public boolean fly, fuzz;
        public Seq stand, walk, attack, pain, die;
        public int atk;
        public int pellets = 1, dmgMul = 3, dmgDie = 5;      // hitscan
        public int meleeMul, meleeDie;                        // melee (0 = none)
        public String missile; public int missDmgMul, missDmgDie;
        public String sight, act, painSnd, deathSnd, atkSnd, meleeSnd;
        public int drop;                                      // ednum dropped on death
        public boolean seeAll;
    }

    public static final class MissileDef {
        public String name, spr; public String fly, boom; public int flyTics = 4, boomTics = 6;
        public float speed, radius = 6, height = 8; public int dmgMul, dmgDie;
        public boolean splash; public String launchSnd, hitSnd;
        public boolean fullbright = true;
    }

    public static final Map<Integer, Def> DEFS = new HashMap<>();
    public static final Map<String, MissileDef> MISSILES = new HashMap<>();

    static Def mk(int type, String spr, String name, int hp, float r, float h, float speed, int pain) {
        Def d = new Def();
        d.type = type; d.spr = spr; d.name = name; d.hp = hp; d.radius = r; d.height = h; d.speed = speed; d.painChance = pain;
        DEFS.put(type, d);
        return d;
    }

    static MissileDef mm(String name, String spr, String fly, String boom, float speed, int mul, int die) {
        MissileDef m = new MissileDef();
        m.name = name; m.spr = spr; m.fly = fly; m.boom = boom; m.speed = speed; m.dmgMul = mul; m.dmgDie = die;
        MISSILES.put(name, m);
        return m;
    }

    static {
        Def d;
        // ---- Zombieman
        d = mk(3004, "POSS", "Zombieman", 20, 20, 56, 8, 200);
        d.stand = new Seq("AB", 10, 0); d.walk = new Seq("AABBCCDD", 4, 0);
        d.attack = new Seq("EFE", new int[]{10, 8, 8}, 2);
        d.pain = new Seq("GG", 3, 0); d.die = new Seq("HIJKL", new int[]{5, 5, 5, 5, -1}, 0);
        d.atk = ATK_HITSCAN; d.pellets = 1; d.atkSnd = "DSPISTOL"; d.sight = "DSPOSIT1"; d.act = "DSPOSACT";
        d.painSnd = "DSPOPAIN"; d.deathSnd = "DSPODTH1"; d.drop = 2007;
        // ---- Shotgun guy
        d = mk(9, "SPOS", "Sergeant", 30, 20, 56, 8, 170);
        d.stand = new Seq("AB", 10, 0); d.walk = new Seq("AABBCCDD", 3, 0);
        d.attack = new Seq("EFE", new int[]{10, 10, 10}, 2);
        d.pain = new Seq("GG", 3, 0); d.die = new Seq("HIJKL", new int[]{5, 5, 5, 5, -1}, 0);
        d.atk = ATK_HITSCAN; d.pellets = 3; d.atkSnd = "DSSHOTGN"; d.sight = "DSPOSIT2"; d.act = "DSPOSACT";
        d.painSnd = "DSPOPAIN"; d.deathSnd = "DSPODTH2"; d.drop = 2001;
        // ---- Chaingunner
        d = mk(65, "CPOS", "Chaingunner", 70, 20, 56, 8, 170);
        d.stand = new Seq("AB", 10, 0); d.walk = new Seq("AABBCCDD", 3, 0);
        d.attack = new Seq("EFEFEF", new int[]{10, 4, 4, 4, 4, 4}, 2 | 8 | 32);
        d.pain = new Seq("GG", 3, 0); d.die = new Seq("HIJKLMN", new int[]{5, 5, 5, 5, 5, 5, -1}, 0);
        d.atk = ATK_HITSCAN; d.pellets = 1; d.atkSnd = "DSSHOTGN"; d.sight = "DSPOSIT3"; d.act = "DSPOSACT";
        d.painSnd = "DSPOPAIN"; d.deathSnd = "DSPODTH3"; d.drop = 2002;
        // ---- Imp
        d = mk(3001, "TROO", "Imp", 60, 20, 56, 8, 200);
        d.stand = new Seq("AB", 10, 0); d.walk = new Seq("AABBCCDD", 3, 0);
        d.attack = new Seq("EFG", new int[]{8, 8, 6}, 4);
        d.pain = new Seq("HH", 2, 0); d.die = new Seq("IJKLM", new int[]{8, 8, 6, 6, -1}, 0);
        d.atk = ATK_MISSILE; d.missile = "IMPBALL"; d.meleeMul = 3; d.meleeDie = 8; d.meleeSnd = "DSCLAW";
        d.sight = "DSBGSIT1"; d.act = "DSBGACT"; d.painSnd = "DSPOPAIN"; d.deathSnd = "DSBGDTH1"; d.atkSnd = "DSFIRSHT";
        // ---- Demon & Spectre
        d = mk(3002, "SARG", "Demon", 150, 30, 56, 10, 180);
        d.stand = new Seq("AB", 10, 0); d.walk = new Seq("AABBCCDD", 2, 0);
        d.attack = new Seq("EFG", 8, 4);
        d.pain = new Seq("HH", 2, 0); d.die = new Seq("IJKLMN", new int[]{8, 8, 4, 4, 4, -1}, 0);
        d.atk = ATK_MELEE; d.meleeMul = 4; d.meleeDie = 10; d.meleeSnd = "DSSGTATK";
        d.sight = "DSSGTSIT"; d.act = "DSDMACT"; d.painSnd = "DSDMPAIN"; d.deathSnd = "DSSGTDTH";
        Def sp = mk(58, "SARG", "Spectre", 150, 30, 56, 10, 180);
        sp.stand = d.stand; sp.walk = d.walk; sp.attack = d.attack; sp.pain = d.pain; sp.die = d.die;
        sp.atk = d.atk; sp.meleeMul = 4; sp.meleeDie = 10; sp.meleeSnd = d.meleeSnd; sp.fuzz = true;
        sp.sight = d.sight; sp.act = d.act; sp.painSnd = d.painSnd; sp.deathSnd = d.deathSnd;
        // ---- Lost soul
        d = mk(3006, "SKUL", "Lost Soul", 100, 16, 56, 8, 256);
        d.stand = new Seq("AB", 10, 0); d.walk = new Seq("AB", 6, 0);
        d.attack = new Seq("CD", new int[]{10, 4}, 2);
        d.pain = new Seq("EF", 3, 0); d.die = new Seq("GHIJK", new int[]{6, 6, 6, 6, -1}, 0);
        d.fly = true; d.atk = ATK_CHARGE; d.meleeMul = 3; d.meleeDie = 8; d.mass = 50; d.fuzz = false;
        d.sight = "DSSKLATK"; d.act = "DSDMACT"; d.painSnd = "DSDMPAIN"; d.deathSnd = "DSFIRXPL"; d.atkSnd = "DSSKLATK";
        // ---- Cacodemon
        d = mk(3005, "HEAD", "Cacodemon", 400, 31, 56, 8, 128);
        d.stand = new Seq("A", 10, 0); d.walk = new Seq("A", 3, 0);
        d.attack = new Seq("BCD", 5, 4);
        d.pain = new Seq("EF", 3, 0); d.die = new Seq("GHIJKL", new int[]{8, 8, 8, 8, 8, -1}, 0);
        d.fly = true; d.mass = 400; d.atk = ATK_MISSILE; d.missile = "CACOBALL"; d.meleeMul = 10; d.meleeDie = 6; d.meleeSnd = "DSCLAW";
        d.sight = "DSCACSIT"; d.act = "DSDMACT"; d.painSnd = "DSDMPAIN"; d.deathSnd = "DSCACDTH"; d.atkSnd = "DSFIRSHT";
        // ---- Baron / Hell knight
        d = mk(3003, "BOSS", "Baron of Hell", 1000, 24, 64, 8, 50);
        d.stand = new Seq("AB", 10, 0); d.walk = new Seq("AABBCCDD", 3, 0);
        d.attack = new Seq("EFG", 8, 4);
        d.pain = new Seq("HH", 2, 0); d.die = new Seq("IJKLMNO", new int[]{8, 8, 8, 8, 8, 8, -1}, 0);
        d.mass = 1000; d.atk = ATK_MISSILE; d.missile = "BARONBALL"; d.meleeMul = 10; d.meleeDie = 8; d.meleeSnd = "DSCLAW";
        d.sight = "DSBRSSIT"; d.act = "DSDMACT"; d.painSnd = "DSDMPAIN"; d.deathSnd = "DSBRSDTH"; d.atkSnd = "DSFIRSHT";
        Def hk = mk(69, "BOS2", "Hell Knight", 500, 24, 64, 8, 50);
        hk.stand = d.stand; hk.walk = d.walk; hk.attack = d.attack; hk.pain = d.pain; hk.die = d.die;
        hk.mass = 1000; hk.atk = ATK_MISSILE; hk.missile = "BARONBALL"; hk.meleeMul = 10; hk.meleeDie = 8; hk.meleeSnd = "DSCLAW";
        hk.sight = "DSKNTSIT"; hk.act = "DSDMACT"; hk.painSnd = "DSDMPAIN"; hk.deathSnd = "DSKNTDTH"; hk.atkSnd = "DSFIRSHT";

        // ---- Missiles
        MissileDef m;
        m = mm("IMPBALL", "BAL1", "AB", "CDE", 10, 3, 8); m.launchSnd = "DSFIRSHT"; m.hitSnd = "DSFIRXPL";
        m = mm("CACOBALL", "BAL2", "AB", "CDE", 10, 5, 8); m.launchSnd = "DSFIRSHT"; m.hitSnd = "DSFIRXPL";
        m = mm("BARONBALL", "BAL7", "AB", "CDE", 15, 8, 8); m.launchSnd = "DSFIRSHT"; m.hitSnd = "DSFIRXPL";
        m = mm("ROCKET", "MISL", "A", "BCD", 20, 20, 8); m.splash = true; m.launchSnd = "DSRLAUNC"; m.hitSnd = "DSBAREXP";
        m.boomTics = 6; m.radius = 11; m.height = 8;
        m = mm("PLASMA", "PLSS", "AB", "PLSE", 25, 5, 8); m.launchSnd = "DSPLASMA"; m.hitSnd = "DSFIRXPL"; m.boomTics = 4;
        m.boom = "ABCDE"; m.flyTics = 6;
    }
}
