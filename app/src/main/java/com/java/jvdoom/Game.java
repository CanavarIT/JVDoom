package com.java.jvdoom;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Game state and 35 Hz simulation: player, weapons, items, projectiles, level flow. */
public final class Game {
    public interface Sfx {
        void play(String lump, float x, float y);
        void listener(float x, float y, float angle);
    }

    public static final int ST_PLAY = 0, ST_DEAD = 1, ST_EXIT = 2;
    public static final int W_FIST = 0, W_PISTOL = 1, W_SHOTGUN = 2, W_CHAINGUN = 3, W_ROCKET = 4, W_PLASMA = 5,
            W_BFG = 6, W_SAW = 7, W_SSG = 8;
    static final String[] W_SPR = {"PUNG", "PISG", "SHTG", "CHGG", "MISG", "PLSG", "BFGG", "SAWG", "SHT2"};
    static final String[] W_FLASH = {null, "PISF", "SHTF", "CHGF", "MISF", "PLSF", null, null, "SHT2"};
    static final int[] W_AMMO = {-1, 0, 1, 0, 2, 3, 3, -1, 1};
    static final int[] W_USE = {0, 1, 1, 1, 1, 1, 40, 0, 2};
    public static final String[] W_NAME = {"Fist", "Pistol", "Shotgun", "Chaingun", "Rocket Launcher", "Plasma Rifle", "BFG 9000", "Chainsaw", "Super Shotgun"};
    // attack steps: {frame, tics, flashFrame, fire}
    static final int[][][] W_ATK = {
            {{1, 4, -1, 0}, {2, 4, -1, 1}, {3, 5, -1, 0}, {2, 4, -1, 0}, {1, 5, -1, 0}},
            {{0, 4, -1, 0}, {1, 6, 0, 1}, {2, 4, -1, 0}, {1, 5, -1, 0}},
            {{0, 3, -1, 0}, {0, 7, 0, 1}, {1, 5, -1, 0}, {2, 5, -1, 0}, {3, 4, -1, 0}, {2, 5, -1, 0}, {1, 5, -1, 0}, {0, 3, -1, 0}},
            {{0, 4, 0, 1}, {1, 4, 1, 1}},
            {{1, 8, 0, 0}, {1, 12, 1, 1}},
            {{0, 3, 0, 1}, {1, 20, -1, 0}},
            {{0, 20, -1, 0}},
            {{0, 4, -1, 1}, {1, 4, -1, 1}},
            {{0, 3, -1, 0}, {0, 7, 8, 1}, {1, 7, 9, 0}, {2, 7, -1, 0}, {3, 7, -1, 0}, {4, 7, -1, 0}, {5, 7, -1, 0}, {6, 6, -1, 0}, {7, 6, -1, 0}}
    };

    public final Wad wad;
    public final Gfx gfx;
    public Renderer rend;
    public Level L;
    public String mapName;
    public int skill = 3;
    public boolean aimAssist = true;
    public final List<Mobj> mobjs = new ArrayList<>();
    public final List<Mobj> spawnQueue = new ArrayList<>();
    public Mobj pl;
    public Sfx sfx;
    public final Random rng = new Random();
    public Phys phys = new Phys(this);
    public AI ai = new AI(this);
    public Specials specials = new Specials(this);
    public int tic, state;
    public float angle;
    private float turnAccum;

    // input
    public volatile float inMove, inStrafe;
    public volatile boolean inFire, inUse;
    boolean useHeld;

    // player stats
    public int health = 100, armor, armorType;
    public final int[] ammo = new int[4];
    public final int[] maxAmmo = {200, 50, 50, 300};
    public final boolean[] owned = new boolean[9];
    public final boolean[] keys = new boolean[3];
    public int weapon = W_PISTOL, pending = -1;
    public int invuln, invis, suit, light;
    public boolean berserk, hasMap, backpack;
    public int kills, items, secrets, totalKills, totalItems, totalSecrets, levelTics;
    public int damageFlash, bonusFlash;
    // face/HUD hooks: last damage taken (tic, amount, direction of the attacker relative to the view) and last weapon pickup
    public int hurtTic = -1000, hurtAmount, grinTic = -1000;
    public boolean hurtFrom;
    public float hurtAngle;
    public String message = "";
    public int msgTics;
    public String nextMap;
    public boolean secretExit;

    // weapon state
    int wState, wStep, wTics, refire;
    float psy = 128, psx = 1;
    int wFrameOverride = -1;
    float bob;
    float eyeZ, eyeZPrev;
    int viewHeightDead = 41;

    public final List<Level.Thing> teleDests = new ArrayList<>();

    public Game(Wad wad, Gfx gfx, Renderer rend) {
        this.wad = wad; this.gfx = gfx; this.rend = rend;
    }

    public int rnd() { return rng.nextInt(256); }

    public void setRenderer(Renderer r) {
        rend = r;
        if (L != null) rend.setLevel(L, gfx.texture(skyFor(mapName)));
    }

    public void msg(String s) { message = s; msgTics = 105; }

    public void snd(String name, Mobj at) {
        if (sfx != null && name != null) sfx.play(name, at != null ? at.x : pl.x, at != null ? at.y : pl.y);
    }
    public void sndAt(String name, float x, float y) { if (sfx != null && name != null) sfx.play(name, x, y); }

    // ================================================================== level setup
    public void resetPlayer() {
        health = 100; armor = 0; armorType = 0;
        java.util.Arrays.fill(ammo, 0);
        java.util.Arrays.fill(owned, false);
        maxAmmo[0] = 200; maxAmmo[1] = 50; maxAmmo[2] = 50; maxAmmo[3] = 300; backpack = false;
        owned[W_FIST] = true; owned[W_PISTOL] = true; ammo[0] = 50;
        weapon = W_PISTOL; pending = -1;
        berserk = false;
    }

    public void startMap(String map, boolean keepInventory) {
        if (!keepInventory) resetPlayer();
        java.util.Arrays.fill(keys, false);
        invuln = invis = suit = light = 0; hasMap = false;
        mapName = map;
        L = Level.load(wad, gfx, map);
        mobjs.clear(); spawnQueue.clear(); teleDests.clear();
        kills = items = secrets = totalKills = totalItems = totalSecrets = levelTics = 0;
        tic = 0; state = ST_PLAY; wState = 0; psy = 128; wState = 2; // raise
        damageFlash = bonusFlash = 0; message = ""; msgTics = 0;
        hurtTic = -1000; hurtAmount = 0; grinTic = -1000; hurtFrom = false;
        int mask = skill <= 2 ? 1 : (skill == 3 ? 2 : 4);
        Level.Thing start = null;
        for (Level.Thing t : L.things) {
            if (t.type == 1) { start = t; continue; }
            if (t.type == 14) { teleDests.add(t); continue; }
            if ((t.flags & 16) != 0) continue;
            if ((t.flags & mask) == 0) continue;
            spawnThing(t);
        }
        if (start == null && L.things.length > 0) start = L.things[0];
        float sx = start != null ? start.x : 0, sy = start != null ? start.y : 0;
        pl = new Mobj(sx, sy, L.sectorAt(sx, sy).floor);
        pl.kind = Mobj.PLAYER; pl.radius = 16; pl.height = 56; pl.solid = true; pl.shootable = true; pl.health = health;
        pl.sector = L.sectorAt(sx, sy); pl.floorz = pl.sector.floor; pl.ceilz = pl.sector.ceil;
        angle = start != null ? (float) Math.toRadians(start.angle) : 0;
        pl.angle = angle;
        eyeZ = eyeZPrev = pl.z + 41;
        mobjs.add(pl);
        if (health <= 0) health = 100;
        if (!owned[weapon]) weapon = W_PISTOL;
        specials.init();
        String skyName = skyFor(map);
        rend.setLevel(L, gfx.texture(skyName));
        if (sfx != null) sfx.listener(pl.x, pl.y, angle);
    }

    String skyFor(String map) {
        if (map.startsWith("MAP")) {
            int n = Integer.parseInt(map.substring(3));
            return n < 12 ? "SKY1" : (n < 21 ? "SKY2" : "SKY3");
        }
        char e = map.charAt(1);
        String s = "SKY" + e;
        return gfx.textures.containsKey(s) ? s : "SKY1";
    }

    Mobj spawnThing(Level.Thing t) {
        Level.Sector sec = L.sectorAt(t.x, t.y);
        Monsters.Def def = Monsters.DEFS.get(t.type);
        Mobj m = new Mobj(t.x, t.y, sec.floor);
        m.type = t.type; m.sector = sec; m.angle = (float) Math.toRadians(t.angle);
        m.floorz = sec.floor; m.ceilz = sec.ceil;
        if (def != null) {
            Gfx.SpriteSet ss = gfx.sprites.get(def.spr);
            if (ss == null) return null;
            m.kind = Mobj.MONSTER; m.def = def; m.spr = ss;
            m.radius = def.radius; m.height = def.height; m.health = def.hp;
            m.solid = true; m.shootable = true; m.countKill = true; m.fuzz = def.fuzz;
            m.noGravity = def.fly; m.ambush = (t.flags & 8) != 0;
            totalKills++;
            ai.setSeq(m, Monsters.SEQ_STAND, 0);
            m.tics = 1 + rnd() % 10;
            mobjs.add(m);
            return m;
        }
        Things.Spec sp = Things.SPECS.get(t.type);
        if (sp == null) return null;
        Gfx.SpriteSet ss = gfx.sprites.get(sp.spr);
        if (ss == null) return null;
        m.spec = sp; m.spr = ss; m.radius = sp.radius; m.height = sp.height; m.fullbright = sp.fullbright;
        m.frame = sp.frames.charAt(0) - 'A'; m.tics = sp.tics;
        m.solid = sp.solid;
        if (sp.hang) m.z = sec.ceil - sp.height;
        if (sp.barrel) {
            m.kind = Mobj.BARREL; m.shootable = true; m.health = 20;
        } else if (sp.pickup != 0) {
            m.kind = Mobj.PICKUP; m.pickupKind = sp.pickup; m.solid = false;
            if (sp.pickup < Things.P_KEY_BLUE || sp.pickup > Things.P_KEY_RED) m.countItem = true;
            if (m.countItem) totalItems++;
        }
        mobjs.add(m);
        return m;
    }

    public Mobj spawnEffect(String spr, String frames, int tics, float x, float y, float z, boolean bright) {
        Gfx.SpriteSet ss = gfx.sprites.get(spr);
        if (ss == null) return null;
        Mobj e = new Mobj(x, y, z);
        e.kind = Mobj.EFFECT; e.spr = ss; e.fullbright = bright; e.missileName = frames; e.step = 0;
        e.frame = frames.charAt(0) - 'A'; e.tics = tics; e.animTics = tics; e.noGravity = true;
        e.sector = L.sectorAt(x, y);
        spawnQueue.add(e);
        return e;
    }

    // ================================================================== input
    public synchronized void addTurn(float rad) { turnAccum += rad; }
    private synchronized float takeTurn() { float t = turnAccum; turnAccum = 0; return t; }

    /** Called every render frame to apply smooth turning. */
    public void applyTurn() {
        float t = takeTurn();
        if (t != 0 && state == ST_PLAY) { angle += t; pl.angle = angle; }
    }

    public void selectWeapon(int w) { if (w >= 0 && w < 9 && owned[w] && w != weapon) pending = w; }

    public void cycleWeapon(int dir) {
        int w = pending >= 0 ? pending : weapon;
        for (int i = 0; i < 9; i++) {
            w = (w + dir + 9) % 9;
            if (owned[w] && w != W_BFG && (W_AMMO[w] < 0 || ammo[W_AMMO[w]] >= W_USE[w])) { pending = w; return; }
        }
    }

    // ================================================================== tick
    public void tick() {
        if (state == ST_EXIT) return;
        tic++;
        for (int i = 0; i < mobjs.size(); i++) { Mobj m = mobjs.get(i); m.ox = m.x; m.oy = m.y; m.oz = m.z; }
        eyeZPrev = eyeZ;
        if (state == ST_PLAY) { levelTics++; playerThink(); } else deadThink();
        specials.tick();
        for (int i = 0; i < mobjs.size(); i++) {
            Mobj m = mobjs.get(i);
            if (m == pl || m.removed) continue;
            thinkMobj(m);
        }
        if (!spawnQueue.isEmpty()) { mobjs.addAll(spawnQueue); spawnQueue.clear(); }
        for (int i = mobjs.size() - 1; i >= 0; i--) if (mobjs.get(i).removed) mobjs.remove(i);
        if (damageFlash > 0) damageFlash--;
        if (bonusFlash > 0) bonusFlash--;
        if (msgTics > 0) msgTics--;
        if (sfx != null) sfx.listener(pl.x, pl.y, angle);
    }

    void thinkMobj(Mobj m) {
        switch (m.kind) {
            case Mobj.MONSTER: ai.think(m); break;
            case Mobj.MISSILE: missileThink(m); break;
            case Mobj.EFFECT: effectThink(m); break;
            case Mobj.BARREL: barrelThink(m); break;
            default: decorThink(m);
        }
        if (m.kind != Mobj.MONSTER) applyMomentum(m);
    }

    void decorThink(Mobj m) {
        if (m.spec == null || m.spec.frames.length() < 2) return;
        if (--m.tics <= 0) {
            m.animTics = (m.animTics + 1) % m.spec.frames.length();
            m.frame = m.spec.frames.charAt(m.animTics) - 'A';
            m.tics = m.spec.tics;
        }
    }

    void barrelThink(Mobj m) {
        if (m.exploding) {
            if (--m.tics <= 0) {
                m.step++;
                if (m.step >= 5) { m.removed = true; return; }
                m.frame = m.step;
                m.tics = 5;
                if (m.step == 2) { radiusDamage(m, m.owner, 128, 128); }
            }
            return;
        }
        decorThink(m);
    }

    void effectThink(Mobj m) {
        if (--m.tics <= 0) {
            m.step++;
            if (m.missileName == null || m.step >= m.missileName.length()) { m.removed = true; return; }
            m.frame = m.missileName.charAt(m.step) - 'A';
            m.tics = m.animTics;
        }
        m.z += m.mz;
    }

    void applyMomentum(Mobj m) {
        if (m.kind == Mobj.MISSILE || m.kind == Mobj.EFFECT) return;
        if (m.mx != 0 || m.my != 0) {
            float steps = Math.max(1, (float) Math.ceil(Math.max(Math.abs(m.mx), Math.abs(m.my)) / 12f));
            for (int i = 0; i < steps; i++) phys.slideMove(m, m.mx / steps, m.my / steps);
            m.mx *= 0.90625f; m.my *= 0.90625f;
            if (Math.abs(m.mx) < 0.06f && Math.abs(m.my) < 0.06f) { m.mx = 0; m.my = 0; }
        }
    }

    // ================================================================== player
    void refreshFloor(Mobj m) {
        phys.checkPosition(m, m.x, m.y, true);
        m.floorz = phys.tmFloor; m.ceilz = phys.tmCeil;
        m.sector = L.sectorAt(m.x, m.y);
    }

    void gravity(Mobj m) {
        if (m.noGravity) return;
        if (m.z > m.floorz) { m.mz -= 1f; }
        m.z += m.mz;
        if (m.z <= m.floorz) {
            if (m.mz < -8 && m == pl) snd("DSOOF", pl);
            m.z = m.floorz; m.mz = 0;
        }
        if (m.z + m.height > m.ceilz) { m.z = m.ceilz - m.height; if (m.mz > 0) m.mz = 0; }
    }

    void playerThink() {
        refreshFloor(pl);
        float fwd = inMove * 50f, side = inStrafe * 40f;
        if (pl.z <= pl.floorz + 0.01f) {
            float c = (float) Math.cos(angle), s = (float) Math.sin(angle);
            pl.mx += (c * fwd + s * side) / 32f;
            pl.my += (s * fwd - c * side) / 32f;
        }
        float spd2 = pl.mx * pl.mx + pl.my * pl.my;
        bob = Math.min(16f, spd2 / 4f);
        float fr = 0.90625f;
        float steps = Math.max(1, (float) Math.ceil(Math.max(Math.abs(pl.mx), Math.abs(pl.my)) / 12f));
        for (int i = 0; i < steps; i++) phys.slideMove(pl, pl.mx / steps, pl.my / steps);
        if (pl.z <= pl.floorz + 0.01f) { pl.mx *= fr; pl.my *= fr; }
        if (Math.abs(pl.mx) < 0.06f && Math.abs(pl.my) < 0.06f && fwd == 0 && side == 0) { pl.mx = 0; pl.my = 0; }
        gravity(pl);
        pl.angle = angle;
        float target = pl.z + 41 + 0.5f * bob * (float) Math.sin(tic * 0.314f);
        if (target > eyeZ) eyeZ += Math.min(target - eyeZ, 6f); else eyeZ += (target - eyeZ) * 0.5f;

        // use
        if (inUse) { if (!useHeld) { useHeld = true; useLine(); } } else useHeld = false;

        // sector effects
        Level.Sector s = pl.sector;
        if (s.special == 9) { s.special = 0; secrets++; msg("A secret is revealed!"); }
        if (pl.z <= pl.floorz + 0.5f && (tic & 31) == 0) {
            switch (s.special) {
                case 5: if (suit <= 0) hurtPlayer(10); break;
                case 7: if (suit <= 0) hurtPlayer(5); break;
                case 16: case 4: if (suit <= 0 || rnd() < 5) hurtPlayer(s.special == 4 ? 10 : 20); break;
                case 11: hurtPlayer(20); break;
                default:
            }
        }
        if (s.special == 11 && health <= 10) exitLevel(false);
        L_crossCheck();
        touchPickups();
        if (invuln > 0) invuln--;
        if (invis > 0) invis--;
        if (suit > 0) suit--;
        if (light > 0) light--;
        weaponThink();
        if (health <= 0 && state == ST_PLAY) kill();
    }

    float lastPx, lastPy;
    void L_crossCheck() { specials.checkCross(pl, lastPx, lastPy); lastPx = pl.x; lastPy = pl.y; }

    void hurtPlayer(int n) { damage(pl, null, null, n); }

    void kill() {
        state = ST_DEAD; pl.shootable = false; health = 0; pl.health = 0;
        snd("DSPLDETH", pl);
        wState = 1;
    }

    void deadThink() {
        refreshFloor(pl); gravity(pl); applyMomentum(pl);
        viewHeightDead = Math.max(6, viewHeightDead - 1);
        eyeZ = pl.z + viewHeightDead;
        if (psy < 128) psy += 6;
    }

    void useLine() {
        float c = (float) Math.cos(angle), s = (float) Math.sin(angle);
        Level.Line best = specials.findUseLine(pl.x, pl.y, c, s, 64f);
        if (best != null) { if (!specials.useLine(best, pl)) snd("DSNOWAY", pl); }
        else snd("DSNOWAY", pl);
    }

    public void exitLevel(boolean secret) {
        if (state == ST_EXIT) return;
        secretExit = secret;
        nextMap = nextMapName(mapName, secret);
        state = ST_EXIT;
        snd("DSSWTCHX", pl);
    }

    String nextMapName(String m, boolean secret) {
        if (m.startsWith("MAP")) {
            int n = Integer.parseInt(m.substring(3));
            int nx = n + 1;
            if (secret) { if (n == 15) nx = 31; else if (n == 31) nx = 32; }
            else if (n == 31 || n == 32) nx = 16;
            else if (n == 30) return null;
            return String.format("MAP%02d", nx);
        }
        int e = m.charAt(1) - '0', mp = m.charAt(3) - '0';
        if (secret) return "E" + e + "M9";
        if (mp == 9) { int ret = e == 1 ? 4 : (e == 2 ? 6 : (e == 3 ? 7 : 3)); return "E" + e + "M" + ret; }
        if (mp == 8) return null;
        return "E" + e + "M" + (mp + 1);
    }

    // ================================================================== pickups
    void touchPickups() {
        for (int i = 0, n = mobjs.size(); i < n; i++) {
            Mobj o = mobjs.get(i);
            if (o.kind != Mobj.PICKUP || o.removed) continue;
            if (Math.abs(o.x - pl.x) > pl.radius + o.radius || Math.abs(o.y - pl.y) > pl.radius + o.radius) continue;
            if (pl.z > o.z + o.height || pl.z + pl.height < o.z) continue;
            if (pickup(o.pickupKind)) {
                o.removed = true;
                if (o.countItem) items++;
                bonusFlash = 6;
            }
        }
    }

    boolean giveAmmo(int t, int n) {
        if (ammo[t] >= maxAmmo[t]) return false;
        ammo[t] = Math.min(maxAmmo[t], ammo[t] + n);
        return true;
    }

    boolean giveWeapon(int w, int ammoAdd) {
        boolean had = owned[w];
        boolean gotAmmo = W_AMMO[w] < 0 ? false : giveAmmo(W_AMMO[w], ammoAdd);
        if (had && !gotAmmo) return false;
        owned[w] = true;
        if (!had) { pending = w; grinTic = tic; }
        snd("DSWPNUP", pl);
        return true;
    }

    boolean giveArmor(int type, int pts) {
        if (armor >= pts && type > 0 && armor >= pts) return false;
        armorType = type; armor = pts; return true;
    }

    boolean pickup(int k) {
        String sound = "DSITEMUP";
        switch (k) {
            case Things.P_CLIP: if (!giveAmmo(0, 10)) return false; msg("Picked up a clip."); break;
            case Things.P_CLIPBOX: if (!giveAmmo(0, 50)) return false; msg("Picked up a box of bullets."); break;
            case Things.P_SHELL: if (!giveAmmo(1, 4)) return false; msg("Picked up 4 shotgun shells."); break;
            case Things.P_SHELLBOX: if (!giveAmmo(1, 20)) return false; msg("Picked up a box of shotgun shells."); break;
            case Things.P_ROCKET: if (!giveAmmo(2, 1)) return false; msg("Picked up a rocket."); break;
            case Things.P_ROCKETBOX: if (!giveAmmo(2, 5)) return false; msg("Picked up a box of rockets."); break;
            case Things.P_CELL: if (!giveAmmo(3, 20)) return false; msg("Picked up an energy cell."); break;
            case Things.P_CELLPACK: if (!giveAmmo(3, 100)) return false; msg("Picked up an energy cell pack."); break;
            case Things.P_BACKPACK:
                if (!backpack) { backpack = true; for (int i = 0; i < 4; i++) maxAmmo[i] *= 2; }
                for (int i = 0; i < 4; i++) giveAmmo(i, i == 0 ? 10 : (i == 1 ? 4 : (i == 2 ? 1 : 20)));
                msg("Picked up a backpack full of ammo!"); break;
            case Things.P_STIM: if (health >= 100) return false; heal(10); msg("Picked up a stimpack."); break;
            case Things.P_MEDI: if (health >= 100) return false; heal(25); msg("Picked up a medikit."); break;
            case Things.P_HBONUS: health = Math.min(200, health + 1); pl.health = health; msg("Picked up a health bonus."); break;
            case Things.P_ABONUS: armor = Math.min(200, armor + 1); if (armorType == 0) armorType = 1; msg("Picked up an armor bonus."); break;
            case Things.P_ARMOR1: if (!giveArmor(1, 100)) return false; msg("Picked up the armor."); break;
            case Things.P_ARMOR2: if (!giveArmor(2, 200)) return false; msg("Picked up the MegaArmor!"); break;
            case Things.P_SOUL: health = Math.min(200, health + 100); pl.health = health; msg("Supercharge!"); sound = "DSGETPOW"; break;
            case Things.P_MEGA: health = 200; pl.health = 200; armor = 200; armorType = 2; msg("MegaSphere!"); sound = "DSGETPOW"; break;
            case Things.P_INVULN: invuln = 30 * 35; msg("Invulnerability!"); sound = "DSGETPOW"; break;
            case Things.P_BERSERK: berserk = true; heal(100); if (weapon != W_FIST) pending = W_FIST; msg("Berserk!"); sound = "DSGETPOW"; break;
            case Things.P_INVIS: invis = 60 * 35; msg("Partial Invisibility"); sound = "DSGETPOW"; break;
            case Things.P_SUIT: suit = 60 * 35; msg("Radiation Shielding Suit"); sound = "DSGETPOW"; break;
            case Things.P_MAP: hasMap = true; msg("Computer Area Map"); sound = "DSGETPOW"; break;
            case Things.P_LIGHT: light = 120 * 35; msg("Light Amplification Visor"); sound = "DSGETPOW"; break;
            case Things.P_KEY_BLUE: if (keys[0]) return false; keys[0] = true; msg("Picked up a blue keycard."); break;
            case Things.P_KEY_YELLOW: if (keys[1]) return false; keys[1] = true; msg("Picked up a yellow keycard."); break;
            case Things.P_KEY_RED: if (keys[2]) return false; keys[2] = true; msg("Picked up a red keycard."); break;
            case Things.P_W_SHOTGUN: if (!giveWeapon(W_SHOTGUN, 8)) return false; msg("You got the shotgun!"); sound = null; break;
            case Things.P_W_CHAINGUN: if (!giveWeapon(W_CHAINGUN, 20)) return false; msg("You got the chaingun!"); sound = null; break;
            case Things.P_W_ROCKET: if (!giveWeapon(W_ROCKET, 2)) return false; msg("You got the rocket launcher!"); sound = null; break;
            case Things.P_W_PLASMA: if (!giveWeapon(W_PLASMA, 40)) return false; msg("You got the plasma gun!"); sound = null; break;
            case Things.P_W_SAW: if (owned[W_SAW]) return false; owned[W_SAW] = true; pending = W_SAW; grinTic = tic; msg("A chainsaw! Find some meat!"); sound = "DSWPNUP"; break;
            case Things.P_W_SSG: if (!giveWeapon(W_SSG, 8)) return false; msg("You got the super shotgun!"); sound = null; break;
            default: return false;
        }
        if (sound != null) snd(sound, pl);
        return true;
    }

    void heal(int n) { health = Math.min(100, health + n); pl.health = health; }

    // ================================================================== weapons
    Gfx.SpriteSet wSprite() { return gfx.sprites.get(W_SPR[weapon]); }

    boolean hasAmmo(int w) { return W_AMMO[w] < 0 || ammo[W_AMMO[w]] >= W_USE[w]; }

    void bestWeapon() {
        int[] pref = {W_PLASMA, W_SSG, W_CHAINGUN, W_SHOTGUN, W_PISTOL, W_ROCKET, W_SAW, W_FIST};
        for (int w : pref) if (owned[w] && hasAmmo(w) && gfx.sprites.containsKey(W_SPR[w])) { pending = w; return; }
        pending = W_FIST;
    }

    void weaponThink() {
        if (state != ST_PLAY) return;
        switch (wState) {
            case 0: // ready
                if (pending == weapon) pending = -1;
                if (pending >= 0 && pending != weapon) { wState = 1; break; }
                if (inFire) {
                    if (hasAmmo(weapon) && weapon != W_BFG) { wState = 3; wStep = 0; wTics = W_ATK[weapon][0][1]; refire = 0; if (W_ATK[weapon][0][3] != 0) fireWeapon(); }
                    else if (weapon != W_BFG) bestWeapon();
                }
                if (weapon == W_SAW) wFrameOverride = 2 + ((tic >> 2) & 1); else wFrameOverride = -1;
                psx = 1 + bob * (float) Math.cos(tic * Math.PI / 32);
                psy = 32 + bob * Math.abs((float) Math.sin(tic * Math.PI / 32));
                break;
            case 1: // lowering
                psy += 6;
                if (psy >= 128) {
                    psy = 128;
                    if (pending >= 0) { weapon = pending; pending = -1; }
                    if (!owned[weapon]) weapon = W_PISTOL;
                    wState = 2;
                }
                break;
            case 2: // raising
                psy -= 6;
                if (pending >= 0 && pending != weapon) { wState = 1; break; }
                if (psy <= 32) { psy = 32; wState = 0; }
                break;
            case 3: // attacking
                if (--wTics <= 0) {
                    int[][] st = W_ATK[weapon];
                    wStep++;
                    if (wStep >= st.length) {
                        if (inFire && hasAmmo(weapon)) { wStep = 0; refire++; }
                        else { wState = 0; refire = 0; wFrameOverride = -1; if (!hasAmmo(weapon)) bestWeapon(); break; }
                    }
                    wTics = st[wStep][1];
                    if (st[wStep][3] != 0 && hasAmmo(weapon)) fireWeapon();
                    else if (st[wStep][3] != 0) { wStep = st.length - 1; }
                }
                psx = 1; psy = 32;
                break;
        }
    }

    void bullet(float spreadMul, float dmgMulUnused, boolean accurate, int pellets, float spread) {
        float baseAng = angle;
        phys.aim(pl, angle, 2048f, aimAssist);
        float slope = phys.aimSlope; baseAng = phys.aimAngle;
        for (int i = 0; i < pellets; i++) {
            float a = baseAng;
            if (!accurate) a += (rnd() - rnd()) * spread;
            float sl = slope + (pellets > 1 ? (rnd() - rnd()) * 0.0005f : 0f);
            int dmg = 5 * (rnd() % 3 + 1);
            shoot(pl, a, 2048f, sl, dmg);
        }
    }

    void shoot(Mobj src, float a, float range, float slope, int dmg) {
        float z0 = src.z + (src.kind == Mobj.PLAYER ? 41 : src.height * 0.75f) ;
        Phys.Hit h = phys.trace(src, src.x, src.y, z0, a, range, slope);
        if (h.mobj != null) {
            Mobj t = h.mobj;
            if (t.kind == Mobj.BARREL || t == pl) { spawnEffect("PUFF", "ABCD", 4, h.x, h.y, h.z, true); }
            else spawnEffect("BLUD", "CBA", 8, h.x, h.y, h.z, false);
            damage(t, src, src, dmg);
        } else if (h.line != null) {
            spawnEffect("PUFF", "ABCD", 4, h.x, h.y, h.z, true);
            specials.shootLine(h.line, src);
        }
    }

    void fireWeapon() {
        int w = weapon;
        if (W_AMMO[w] >= 0) ammo[W_AMMO[w]] -= W_USE[w];
        noiseAlert();
        switch (w) {
            case W_FIST: case W_SAW: {
                float range = w == W_FIST ? 64f : 65f;
                float a = angle + (rnd() - rnd()) * 0.000383f;
                phys.aim(pl, a, range, aimAssist);
                int dmg = (rnd() % 10 + 1) * 2;
                if (w == W_FIST && berserk) dmg *= 10;
                Phys.Hit h = phys.trace(pl, pl.x, pl.y, pl.z + 41, phys.aimAngle, range, phys.aimSlope);
                if (h.mobj != null) {
                    snd(w == W_FIST ? "DSPUNCH" : "DSSAWHIT", pl);
                    spawnEffect("BLUD", "CBA", 8, h.x, h.y, h.z, false);
                    damage(h.mobj, pl, pl, dmg);
                    float da = (float) Math.atan2(h.mobj.y - pl.y, h.mobj.x - pl.x);
                    float diff = Phys.normAngle(da - angle);
                    angle += diff * 0.4f; pl.angle = angle;
                } else snd(w == W_SAW ? "DSSAWFUL" : null, pl);
                break;
            }
            case W_PISTOL: snd("DSPISTOL", pl); bullet(1, 1, refire == 0, 1, 0.000383f); break;
            case W_CHAINGUN: snd("DSPISTOL", pl); bullet(1, 1, refire == 0, 1, 0.000383f); break;
            case W_SHOTGUN: snd("DSSHOTGN", pl); bullet(1, 1, false, 7, 0.000383f); break;
            case W_SSG: snd("DSDSHTGN", pl); bullet(1, 1, false, 20, 0.000767f); break;
            case W_ROCKET: {
                phys.aim(pl, angle, 2048f, aimAssist);
                spawnMissile(pl, Monsters.MISSILES.get("ROCKET"), phys.aimAngle, phys.aimSlope, 32f);
                break;
            }
            case W_PLASMA: {
                phys.aim(pl, angle, 2048f, aimAssist);
                spawnMissile(pl, Monsters.MISSILES.get("PLASMA"), phys.aimAngle, phys.aimSlope, 32f);
                break;
            }
            default:
        }
    }

    public Mobj spawnMissile(Mobj src, Monsters.MissileDef md, float a, float slope, float zoff) {
        Gfx.SpriteSet ss = gfx.sprites.get(md.spr);
        if (ss == null) return null;
        float c = (float) Math.cos(a), s = (float) Math.sin(a);
        float off = src.kind == Mobj.PLAYER ? 4f : src.radius * 0.5f;
        Mobj m = new Mobj(src.x + c * off, src.y + s * off, src.z + zoff);
        m.kind = Mobj.MISSILE; m.miss = md; m.spr = ss; m.fullbright = md.fullbright; m.owner = src;
        m.radius = md.radius; m.height = md.height; m.noGravity = true; m.angle = a;
        m.mx = c * md.speed; m.my = s * md.speed;
        float h2 = (float) Math.sqrt(1 + slope * slope);
        m.mx /= h2; m.my /= h2; m.mz = slope * md.speed / h2;
        m.frame = md.fly.charAt(0) - 'A'; m.tics = md.flyTics; m.step = 0;
        m.sector = L.sectorAt(m.x, m.y);
        m.floorz = m.sector.floor; m.ceilz = m.sector.ceil;
        spawnQueue.add(m);
        snd(md.launchSnd, src);
        return m;
    }

    void missileThink(Mobj m) {
        if (m.exploding) {
            if (--m.tics <= 0) {
                m.step++;
                if (m.step >= m.miss.boom.length()) { m.removed = true; return; }
                m.frame = m.miss.boom.charAt(m.step) - 'A'; m.tics = m.miss.boomTics;
            }
            return;
        }
        if (--m.tics <= 0) {
            m.step = (m.step + 1) % m.miss.fly.length();
            m.frame = m.miss.fly.charAt(m.step) - 'A'; m.tics = m.miss.flyTics;
        }
        int parts = (int) Math.ceil(m.miss.speed / 8f);
        for (int i = 0; i < parts; i++) {
            float nx = m.x + m.mx / parts, ny = m.y + m.my / parts, nz = m.z + m.mz / parts;
            m.z = nz;
            if (!phys.checkPosition(m, nx, ny, false)) {
                if (phys.tmBlockMobj != null && phys.tmBlockMobj != m.owner) {
                    Mobj t = phys.tmBlockMobj;
                    int dmg = m.miss.dmgMul * (rnd() % m.miss.dmgDie + 1);
                    if (t.shootable) damage(t, m, m.owner, dmg);
                    explode(m);
                } else if (phys.tmBlockLine != null) {
                    Level.Line l = phys.tmBlockLine;
                    if (l.back == null && l.fs != null && l.fs.skyCeil && m.z + m.height > l.fs.ceil - 1) { m.removed = true; return; }
                    explode(m);
                } else explode(m);
                return;
            }
            if (m.z + m.height >= phys.tmCeil) {
                Level.Sector s = L.sectorAt(nx, ny);
                if (s.skyCeil) { m.removed = true; return; }
                explode(m); return;
            }
            if (m.z <= phys.tmFloor) { explode(m); return; }
            m.x = nx; m.y = ny; m.sector = L.sectorAt(nx, ny);
        }
    }

    void explode(Mobj m) {
        m.exploding = true; m.mx = m.my = m.mz = 0; m.step = 0;
        m.frame = m.miss.boom.charAt(0) - 'A'; m.tics = m.miss.boomTics;
        snd(m.miss.hitSnd, m);
        if (m.miss.splash) radiusDamage(m, m.owner, 128, 128);
    }

    public void radiusDamage(Mobj center, Mobj source, int dmg, float radius) {
        for (int i = 0, n = mobjs.size(); i < n; i++) {
            Mobj o = mobjs.get(i);
            if (o.removed || !o.shootable) continue;
            float d = Math.max(Math.abs(o.x - center.x), Math.abs(o.y - center.y)) - o.radius;
            if (d < 0) d = 0;
            if (d >= radius) continue;
            if (!phys.los(center.x, center.y, center.z + 8, o.x, o.y, o.z + o.height * 0.5f)) continue;
            damage(o, center, source, (int) (dmg - d));
        }
    }

    public void noiseAlert() { specials.soundAlert(pl); }

    // ================================================================== damage
    public void damage(Mobj t, Mobj inflictor, Mobj source, int amount) {
        if (!t.shootable || amount <= 0) return;
        if (inflictor != null && inflictor != t && (t.kind == Mobj.MONSTER || t.kind == Mobj.PLAYER)) {
            float a = (float) Math.atan2(t.y - inflictor.y, t.x - inflictor.x);
            int mass = t.def != null ? t.def.mass : 100;
            float th = amount * 12.5f / mass;
            if (th > 30) th = 30;
            t.mx += (float) Math.cos(a) * th; t.my += (float) Math.sin(a) * th;
        }
        if (t == pl) {
            if (invuln > 0) return;
            if (armorType > 0) {
                int saved = armorType == 1 ? amount / 3 : amount / 2;
                if (armor <= saved) saved = armor;
                armor -= saved; amount -= saved;
                if (armor <= 0) armorType = 0;
            }
            health -= amount; pl.health = health;
            hurtTic = tic; hurtAmount = amount;
            hurtFrom = source != null && source != pl;
            if (hurtFrom) {
                double da = Math.atan2(source.y - pl.y, source.x - pl.x) - angle;
                while (da > Math.PI) da -= 2 * Math.PI;
                while (da < -Math.PI) da += 2 * Math.PI;
                hurtAngle = (float) da;
            }
            damageFlash = Math.min(40, damageFlash + amount / 2 + 3);
            if (health > 0) snd(amount > 20 ? "DSPLPAIN" : "DSPLPAIN", pl);
            if (health <= 0) health = 0;
            if (source != null && source.kind == Mobj.MONSTER) { /* nothing */ }
            return;
        }
        t.health -= amount;
        if (t.kind == Mobj.BARREL) {
            if (t.health <= 0 && !t.exploding) {
                Gfx.SpriteSet ss = gfx.sprites.get("BEXP");
                t.exploding = true; t.owner = source; t.shootable = false; t.solid = false;
                t.fullbright = true;
                if (ss != null) { t.spr = ss; t.step = 0; t.frame = 0; t.tics = 5; }
                else { radiusDamage(t, source, 128, 128); t.removed = true; }
                snd("DSBAREXP", t);
            }
            return;
        }
        ai.hurt(t, inflictor, source, amount);
    }

    // ================================================================== render
    public void render(float alpha) {
        if (pl == null) return;
        applyTurn();
        float px = pl.ox + (pl.x - pl.ox) * alpha, py = pl.oy + (pl.y - pl.oy) * alpha;
        float ez = eyeZPrev + (eyeZ - eyeZPrev) * alpha;
        rend.alpha = alpha;
        Gfx.SpriteSet ws = state == ST_PLAY || state == ST_DEAD ? wSprite() : null;
        int wf = 0; int ff = -1; Gfx.SpriteSet fs = null;
        if (ws != null) {
            wf = wFrameOverride >= 0 ? wFrameOverride : 0;
            if (wState == 3) {
                int[] st = W_ATK[weapon][Math.min(wStep, W_ATK[weapon].length - 1)];
                wf = st[0];
                if (st[2] >= 0 && W_FLASH[weapon] != null) { fs = gfx.sprites.get(W_FLASH[weapon]); ff = st[2]; }
            }
        }
        int fixed = invuln > 0 ? 32 : (light > 0 ? 1 : -1);
        rend.render(px, py, ez, angle, mobjs, pl, ws, wf, fs, ff, psx, psy, pl.sector != null ? pl.sector.light : 160, fixed);
    }

    // ================================================================== HUD helpers
    public int currentAmmo() { return W_AMMO[weapon] >= 0 ? ammo[W_AMMO[weapon]] : -1; }
}
