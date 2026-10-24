package com.java.jvdoom;

/** Any object in the world: player, monsters, items, projectiles, decorations, effects. */
public final class Mobj {
    // kinds
    public static final int DECOR = 0, MONSTER = 1, PICKUP = 2, MISSILE = 3, PLAYER = 4, EFFECT = 5, BARREL = 6;

    public int kind = DECOR;
    public int type;                // doom ednum
    public float x, y, z, mx, my, mz;
    public float ox, oy, oz;        // previous tick position (render interpolation)
    public float angle;             // radians
    public float radius = 16, height = 16;
    public float floorz, ceilz;
    public Level.Sector sector;

    // rendering
    public Gfx.SpriteSet spr;
    public int frame;
    public boolean fullbright, fuzz;

    // flags
    public boolean solid, shootable, noGravity, corpse, removed, countKill, countItem, floatMonster, ambush;
    public boolean onFloor = true;

    // stats / ai
    public int health;
    public Monsters.Def def;
    public int tics;                // ticks left in current frame
    public int seq;                 // animation sequence id (Monsters.SEQ_*)
    public int step;                // index within sequence
    public Mobj target, owner;
    public int reaction, moveCount, moveDir, threshold, painTics, attackTics;
    public boolean seesTarget;
    public int pickupKind;          // Items.* id
    public int damage;              // for missiles
    public String missileName;
    public Monsters.MissileDef miss;
    public boolean charging, justAttacked, justHit, exploding;
    public int chargeTics;
    public Things.Spec spec;        // decor / pickup spec
    public int animTics;           // generic animation timer for decor
    public int life;               // effect lifetime

    public Mobj(float x, float y, float z) { this.x = x; this.y = y; this.z = z; ox = x; oy = y; oz = z; }
}
