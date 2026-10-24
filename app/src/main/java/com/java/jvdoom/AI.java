package com.java.jvdoom;

/** Monster behaviour: look, chase, attack, pain, death. */
public final class AI {
    final Game g;
    static final int NODIR = 8;
    static final int[] DIAGS = {3, 1, 5, 7};

    AI(Game g) { this.g = g; }

    Monsters.Seq seq(Monsters.Def d, int s) {
        switch (s) {
            case Monsters.SEQ_STAND: return d.stand;
            case Monsters.SEQ_WALK: return d.walk;
            case Monsters.SEQ_ATTACK: return d.attack;
            case Monsters.SEQ_PAIN: return d.pain;
            default: return d.die;
        }
    }

    void setSeq(Mobj m, int s, int step) {
        Monsters.Seq q = seq(m.def, s);
        if (q == null) q = m.def.stand;
        m.seq = s; m.step = step;
        enterStep(m, q, false);
    }

    void enterStep(Mobj m, Monsters.Seq q, boolean fire) {
        if (m.step >= q.frames.length()) m.step = q.frames.length() - 1;
        int f = q.frames.charAt(m.step) - 'A';
        m.frame = f;
        m.tics = q.tics[m.step];
        m.fullbright = false;
        if (!fire) return;
        switch (m.seq) {
            case Monsters.SEQ_STAND: look(m); break;
            case Monsters.SEQ_WALK: chase(m); break;
            case Monsters.SEQ_ATTACK:
                if (m.step == 0) faceTarget(m);
                if (((q.events >> m.step) & 1) != 0) attack(m);
                break;
            default:
        }
    }

    void think(Mobj m) {
        // physics
        if (m.shootable || m.corpse) {
            g.refreshFloor(m);
            if (!m.noGravity) g.gravity(m);
            else if (m.corpse) { m.z -= 2; if (m.z < m.floorz) m.z = m.floorz; }
        }
        if (m.charging) { charge(m); }
        else g.applyMomentum(m);
        if (m.painTics > 0) m.painTics--;
        // animation
        if (m.tics < 0) return;
        if (--m.tics > 0) return;
        Monsters.Seq q = seq(m.def, m.seq);
        if (q == null) q = m.def.stand;
        m.step++;
        if (m.step >= q.frames.length()) {
            switch (m.seq) {
                case Monsters.SEQ_STAND: case Monsters.SEQ_WALK: m.step = 0; break;
                case Monsters.SEQ_ATTACK:
                    m.justAttacked = true;
                    m.seq = m.target != null ? Monsters.SEQ_WALK : Monsters.SEQ_STAND; m.step = 0;
                    q = seq(m.def, m.seq); break;
                case Monsters.SEQ_PAIN:
                    m.seq = m.target != null ? Monsters.SEQ_WALK : Monsters.SEQ_STAND; m.step = 0;
                    q = seq(m.def, m.seq); break;
                default: m.step = q.frames.length() - 1; m.tics = -1; return;
            }
        }
        enterStep(m, q, true);
    }

    // ------------------------------------------------------------------ look
    void look(Mobj m) {
        if (m.target != null) { wake(m); return; }
        Mobj p = g.pl;
        if (g.state != Game.ST_PLAY || !p.shootable) return;
        float dx = p.x - m.x, dy = p.y - m.y;
        float dist = (float) Math.hypot(dx, dy);
        if (dist > 2600f) return;
        if (dist > 64f) {
            float a = Phys.normAngle((float) Math.atan2(dy, dx) - m.angle);
            if (Math.abs(a) > Math.PI / 2) return;
        }
        if (!g.phys.canSee(m, p)) return;
        m.target = p;
        wake(m);
    }

    void wake(Mobj m) {
        g.snd(m.def.sight, m);
        m.reaction = 8;
        setSeq(m, Monsters.SEQ_WALK, 0);
    }

    public void alert(Mobj m, Mobj who) {
        if (m.target != null || !m.shootable || m.seq != Monsters.SEQ_STAND) return;
        if (m.ambush && !g.phys.canSee(m, who)) return;
        m.target = who; wake(m);
    }

    // ------------------------------------------------------------------ chase
    float approach(float cur, float want, float step) {
        float d = Phys.normAngle(want - cur);
        if (Math.abs(d) <= step) return want;
        return cur + (d > 0 ? step : -step);
    }

    void faceTarget(Mobj m) {
        if (m.target == null) return;
        float a = (float) Math.atan2(m.target.y - m.y, m.target.x - m.x);
        if (m.target.fuzz || (m.target == g.pl && g.invis > 0)) a += (g.rnd() - g.rnd()) * 0.003068f;
        m.angle = a;
    }

    boolean meleeRange(Mobj m) {
        if (m.target == null) return false;
        float d = (float) Math.hypot(m.target.x - m.x, m.target.y - m.y);
        if (d >= 44f + m.target.radius) return false;
        return g.phys.canSee(m, m.target);
    }

    boolean missileRange(Mobj m) {
        if (!g.phys.canSee(m, m.target)) return false;
        if (m.justHit) { m.justHit = false; return true; }
        if (m.reaction > 0) return false;
        float dist = (float) Math.hypot(m.target.x - m.x, m.target.y - m.y) - 64f;
        if (m.def.meleeMul == 0) dist -= 128f;
        if (m.def.atk == Monsters.ATK_CHARGE) dist -= 100f;
        if (dist > 200f) dist = 200f;
        if (dist < 0) dist = 0;
        return g.rnd() >= dist;
    }

    void chase(Mobj m) {
        if (m.reaction > 0) m.reaction--;
        if (m.threshold > 0) { if (m.target == null || !m.target.shootable) m.threshold = 0; else m.threshold--; }
        if (m.moveDir < 8) m.angle = approach(m.angle, m.moveDir * (float) (Math.PI / 4), (float) (Math.PI / 4));
        if (m.target == null || !m.target.shootable) {
            m.target = null;
            if (g.state == Game.ST_PLAY && g.pl.shootable && g.phys.canSee(m, g.pl)) { m.target = g.pl; }
            else { setSeq(m, Monsters.SEQ_STAND, 0); return; }
        }
        if (m.justAttacked) { m.justAttacked = false; newChaseDir(m); return; }
        Monsters.Def d = m.def;
        if (d.meleeMul > 0 && d.atk != Monsters.ATK_CHARGE && meleeRange(m)) {
            g.snd(d.atkSnd != null && d.atk != Monsters.ATK_MELEE ? d.meleeSnd : d.meleeSnd, m);
            setSeq(m, Monsters.SEQ_ATTACK, 0);
            return;
        }
        if (d.atk != Monsters.ATK_NONE && d.atk != Monsters.ATK_MELEE && m.moveCount <= 0 && missileRange(m)) {
            setSeq(m, Monsters.SEQ_ATTACK, 0);
            g.snd(d.atkSnd, m);
            return;
        }
        if (--m.moveCount < 0 || !move(m)) newChaseDir(m);
        if (g.rnd() < 3) g.snd(d.act, m);
    }

    boolean move(Mobj m) {
        if (m.moveDir >= 8) return false;
        float a = m.moveDir * (float) (Math.PI / 4);
        float sp = m.def.speed;
        float nx = m.x + (float) Math.cos(a) * sp, ny = m.y + (float) Math.sin(a) * sp;
        if (!g.phys.tryMove(m, nx, ny)) {
            if (m.noGravity && g.phys.tmCeil - g.phys.tmFloor >= m.height) {
                // floaters: try rising/falling to pass
            }
            Level.Line bl = g.phys.tmBlockLine;
            if (bl != null && bl.special != 0) {
                if (g.specials.useLine(bl, m)) return true;
            }
            return false;
        }
        if (m.noGravity && m.target != null) {
            float dz = m.target.z - m.z;
            if (dz > 4) m.z += 4; else if (dz < -4) m.z -= 4;
            if (m.z < m.floorz) m.z = m.floorz;
            if (m.z + m.height > m.ceilz) m.z = m.ceilz - m.height;
        }
        return true;
    }

    boolean tryDir(Mobj m, int dir) {
        if (dir == NODIR) return false;
        int old = m.moveDir;
        m.moveDir = dir;
        if (!move(m)) { m.moveDir = old; return false; }
        m.moveCount = g.rnd() & 15;
        return true;
    }

    void newChaseDir(Mobj m) {
        if (m.target == null) return;
        int old = m.moveDir;
        int turn = old == NODIR ? NODIR : (old + 4) & 7;
        float dx = m.target.x - m.x, dy = m.target.y - m.y;
        int d1 = dx > 10 ? 0 : (dx < -10 ? 4 : NODIR);
        int d2 = dy < -10 ? 6 : (dy > 10 ? 2 : NODIR);
        if (d1 != NODIR && d2 != NODIR) {
            int dd = DIAGS[((dy < 0) ? 2 : 0) + (dx > 0 ? 1 : 0)];
            if (dd != turn && tryDir(m, dd)) return;
        }
        if (g.rnd() > 200 || Math.abs(dy) > Math.abs(dx)) { int t = d1; d1 = d2; d2 = t; }
        if (d1 == turn) d1 = NODIR;
        if (d2 == turn) d2 = NODIR;
        if (d1 != NODIR && tryDir(m, d1)) return;
        if (d2 != NODIR && tryDir(m, d2)) return;
        if (old != NODIR && tryDir(m, old)) return;
        if ((g.rnd() & 1) != 0) {
            for (int d = 0; d < 8; d++) if (d != turn && tryDir(m, d)) return;
        } else {
            for (int d = 7; d >= 0; d--) if (d != turn && tryDir(m, d)) return;
        }
        if (turn != NODIR && tryDir(m, turn)) return;
        m.moveDir = NODIR;
    }

    // ------------------------------------------------------------------ attacks
    void attack(Mobj m) {
        Monsters.Def d = m.def;
        if (m.target == null) return;
        faceTarget(m);
        switch (d.atk) {
            case Monsters.ATK_HITSCAN: {
                for (int i = 0; i < d.pellets; i++) {
                    float a = m.angle + (g.rnd() - g.rnd()) * 0.001534f;
                    float dist = (float) Math.hypot(m.target.x - m.x, m.target.y - m.y);
                    float z0 = m.z + m.height * 0.75f;
                    float slope = (m.target.z + m.target.height * 0.5f - z0) / Math.max(1f, dist);
                    g.shoot(m, a, 2048f, slope, ((g.rnd() % 5) + 1) * 3);
                }
                g.snd(d.atkSnd, m);
                break;
            }
            case Monsters.ATK_MISSILE:
                if (d.meleeMul > 0 && meleeRange(m)) {
                    g.snd(d.meleeSnd, m);
                    g.damage(m.target, m, m, d.meleeMul * (g.rnd() % d.meleeDie + 1));
                } else {
                    Monsters.MissileDef md = Monsters.MISSILES.get(d.missile);
                    float dist = (float) Math.hypot(m.target.x - m.x, m.target.y - m.y);
                    float z0 = m.z + 32f;
                    float slope = (m.target.z + m.target.height * 0.5f - z0) / Math.max(1f, dist);
                    g.spawnMissile(m, md, m.angle, slope, 32f);
                }
                break;
            case Monsters.ATK_MELEE:
                if (meleeRange(m)) {
                    g.snd(d.meleeSnd, m);
                    g.damage(m.target, m, m, d.meleeMul * (g.rnd() % d.meleeDie + 1));
                }
                break;
            case Monsters.ATK_CHARGE: {
                g.snd(d.atkSnd, m);
                float a = (float) Math.atan2(m.target.y - m.y, m.target.x - m.x);
                m.angle = a;
                m.mx = (float) Math.cos(a) * 20f; m.my = (float) Math.sin(a) * 20f;
                float dist = (float) Math.hypot(m.target.x - m.x, m.target.y - m.y);
                float dt = Math.max(1f, dist / 20f);
                m.mz = (m.target.z + m.target.height * 0.5f - (m.z + m.height * 0.5f)) / dt;
                m.charging = true; m.chargeTics = 40; m.fullbright = true;
                break;
            }
            default:
        }
    }

    void charge(Mobj m) {
        m.fullbright = true;
        float nx = m.x + m.mx, ny = m.y + m.my;
        if (--m.chargeTics <= 0) { stopCharge(m); return; }
        if (!g.phys.tryMove(m, nx, ny)) {
            Mobj t = g.phys.tmBlockMobj;
            if (t != null && t.shootable && t != m) g.damage(t, m, m, m.def.meleeMul * (g.rnd() % m.def.meleeDie + 1));
            stopCharge(m);
            return;
        }
        m.z += m.mz;
        if (m.z < m.floorz) m.z = m.floorz;
        if (m.z + m.height > m.ceilz) m.z = m.ceilz - m.height;
    }

    void stopCharge(Mobj m) {
        m.charging = false; m.mx = m.my = m.mz = 0; m.fullbright = false;
        setSeq(m, Monsters.SEQ_WALK, 0);
    }

    // ------------------------------------------------------------------ damage reaction
    void hurt(Mobj t, Mobj inflictor, Mobj source, int amount) {
        if (t.def == null) { if (t.health <= 0) t.removed = true; return; }
        if (t.health <= 0) { die(t, source); return; }
        if (source != null && source != t && source.def != t.def && t.threshold <= 0 && source.shootable) {
            t.target = source; t.threshold = 100;
            if (t.seq == Monsters.SEQ_STAND) wake(t);
        } else if (t.target == null && source != null && source != t) {
            t.target = source; wake(t);
        }
        if (g.rnd() < t.def.painChance && !t.charging && t.def.pain != null) {
            t.justHit = true;
            g.snd(t.def.painSnd, t);
            setSeq(t, Monsters.SEQ_PAIN, 0);
        }
    }

    void die(Mobj t, Mobj source) {
        if (t.corpse) return;
        t.corpse = true; t.shootable = false; t.solid = false; t.charging = false; t.fullbright = false;
        t.fuzz = false;
        if (t.def.fly) t.noGravity = false;
        if (t.countKill) g.kills++;
        g.snd(t.def.deathSnd, t);
        setSeq(t, Monsters.SEQ_DIE, 0);
        if (t.def.drop != 0) {
            Level.Thing th = new Level.Thing();
            th.x = t.x; th.y = t.y; th.type = t.def.drop; th.flags = 7;
            Mobj item = g.spawnThing(th);
            if (item != null) { g.mobjs.remove(item); if (item.countItem) g.totalItems--; item.countItem = false; g.spawnQueue.add(item); item.z = t.z; }
        }
    }
}
