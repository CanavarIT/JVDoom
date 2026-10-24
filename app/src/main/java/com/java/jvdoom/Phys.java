package com.java.jvdoom;

/** Collision, line-of-sight and hitscan helpers working on Game state. */
public final class Phys {
    final Game g;
    public float tmFloor, tmCeil, tmDrop;
    public Level.Line tmBlockLine;
    public Mobj tmBlockMobj;
    Level.Line tmFloorLine, tmCeilLine;

    Phys(Game g) { this.g = g; }

    /** Overlap of an axis aligned box with a line (touching counts). */
    static boolean boxTouchesLine(float x0, float y0, float x1, float y1, Level.Line l) {
        if (l.maxx < x0 || l.minx > x1 || l.maxy < y0 || l.miny > y1) return false;
        float s0 = side(l, x0, y0), s1 = side(l, x1, y0), s2 = side(l, x1, y1), s3 = side(l, x0, y1);
        boolean allPos = s0 > 0 && s1 > 0 && s2 > 0 && s3 > 0;
        boolean allNeg = s0 < 0 && s1 < 0 && s2 < 0 && s3 < 0;
        return !(allPos || allNeg);
    }

    static float side(Level.Line l, float x, float y) { return l.dx * (y - l.y1) - l.dy * (x - l.x1); }

    /** Fills tm* fields for mobj m placed at (x,y). Returns false if blocked by a line or solid thing. */
    boolean checkPosition(Mobj m, float x, float y, boolean ignoreThings) {
        final Level L = g.L;
        float r = m.radius;
        Level.Sector s = L.sectorAt(x, y);
        tmFloor = s.floor; tmDrop = s.floor; tmCeil = s.ceil;
        tmBlockLine = null; tmBlockMobj = null; tmFloorLine = null; tmCeilLine = null;
        final boolean[] ok = {true};

        if (!ignoreThings && (m.solid || m.kind == Mobj.MISSILE)) {
            for (int i = 0, n = g.mobjs.size(); i < n; i++) {
                Mobj o = g.mobjs.get(i);
                if (o == m || o.removed || !o.solid) continue;
                if (m.kind == Mobj.MISSILE && !o.shootable) continue;
                float rr = r + o.radius;
                if (Math.abs(o.x - x) >= rr || Math.abs(o.y - y) >= rr) continue;
                if (m.z >= o.z + o.height || m.z + m.height <= o.z) continue;
                if (m.kind == Mobj.MISSILE && (o == m.owner)) continue;
                tmBlockMobj = o;
                ok[0] = false;
                break;
            }
        }
        final boolean monster = m.kind == Mobj.MONSTER;
        final boolean missile = m.kind == Mobj.MISSILE;
        L.forLinesInBox(x - r, y - r, x + r, y + r, new Level.LineVisitor() {
            public boolean visit(Level.Line l) {
                if (!boxTouchesLine(x - r, y - r, x + r, y + r, l)) return true;
                if (l.back == null || (l.flags & Level.ML_BLOCKING) != 0 || (monster && (l.flags & Level.ML_BLOCKMONSTERS) != 0)) {
                    tmBlockLine = l; ok[0] = false; return true;
                }
                float top = Math.min(l.fs.ceil, l.bs.ceil), bot = Math.max(l.fs.floor, l.bs.floor);
                float low = Math.min(l.fs.floor, l.bs.floor);
                if (top < tmCeil) { tmCeil = top; tmCeilLine = l; }
                if (bot > tmFloor) { tmFloor = bot; tmFloorLine = l; }
                if (low < tmDrop) tmDrop = low;
                return true;
            }
        });
        return ok[0];
    }

    boolean tryMove(Mobj m, float nx, float ny) {
        if (!checkPosition(m, nx, ny, false)) return false;
        boolean fly = m.noGravity;
        if (tmCeil - tmFloor < m.height) { tmBlockLine = tmCeilLine != null ? tmCeilLine : tmFloorLine; return false; }
        if (!fly) {
            if (tmCeil - m.z < m.height) { tmBlockLine = tmCeilLine; return false; }
            if (tmFloor - m.z > 24f) { tmBlockLine = tmFloorLine; return false; }
            if (m.kind == Mobj.MONSTER && tmFloor - tmDrop > 24f) return false;
        } else {
            if (tmCeil - m.z < m.height) { tmBlockLine = tmCeilLine; return false; }
        }
        m.x = nx; m.y = ny;
        m.floorz = tmFloor; m.ceilz = tmCeil;
        m.sector = g.L.sectorAt(nx, ny);
        return true;
    }

    /** Move with wall sliding. Returns true if any movement happened. */
    boolean slideMove(Mobj m, float dx, float dy) {
        if (tryMove(m, m.x + dx, m.y + dy)) return true;
        Level.Line bl = tmBlockLine;
        if (bl != null && bl.len > 0) {
            float ux = bl.dx / bl.len, uy = bl.dy / bl.len;
            float dot = dx * ux + dy * uy;
            if (tryMove(m, m.x + ux * dot, m.y + uy * dot)) {
                float md = m.mx * ux + m.my * uy;
                m.mx = ux * md; m.my = uy * md;
                return true;
            }
        }
        boolean moved = false;
        if (tryMove(m, m.x + dx, m.y)) { moved = true; m.my *= 0.3f; }
        else if (tryMove(m, m.x, m.y + dy)) { moved = true; m.mx *= 0.3f; }
        else { m.mx = 0; m.my = 0; }
        return moved;
    }

    // ------------------------------------------------------------------ line of sight
    /** Does a ray from (x1,y1,z1) to (x2,y2,z2) pass through all two-sided openings and no solid wall? */
    public boolean los(float x1, float y1, float z1, float x2, float y2, float z2) {
        final Level L = g.L;
        final float dx = x2 - x1, dy = y2 - y1;
        final float ax = x1, ay = y1, az = z1, dz = z2 - z1;
        final boolean[] blocked = {false};
        L.forLinesInBox(Math.min(x1, x2) - 1, Math.min(y1, y2) - 1, Math.max(x1, x2) + 1, Math.max(y1, y2) + 1,
                new Level.LineVisitor() {
                    public boolean visit(Level.Line l) {
                        float den = dx * l.dy - dy * l.dx;
                        if (Math.abs(den) < 1e-6f) return true;
                        float t = ((l.x1 - ax) * l.dy - (l.y1 - ay) * l.dx) / den;
                        float u = ((l.x1 - ax) * dy - (l.y1 - ay) * dx) / den;
                        if (t <= 0f || t >= 1f || u < 0f || u > 1f) return true;
                        if (l.back == null) { blocked[0] = true; return false; }
                        float top = Math.min(l.fs.ceil, l.bs.ceil), bot = Math.max(l.fs.floor, l.bs.floor);
                        if (top <= bot) { blocked[0] = true; return false; }
                        float z = az + dz * t;
                        if (z < bot || z > top) { blocked[0] = true; return false; }
                        return true;
                    }
                });
        return !blocked[0];
    }

    public boolean canSee(Mobj a, Mobj b) {
        return los(a.x, a.y, a.z + a.height * 0.75f, b.x, b.y, b.z + b.height * 0.75f);
    }

    // ------------------------------------------------------------------ hitscan
    public static final class Hit {
        public Mobj mobj;
        public float x, y, z, dist;
        public Level.Line line;
        public boolean hitSky;
    }

    /** Casts a ray, returning nearest thing or wall hit. */
    public Hit trace(Mobj shooter, float x0, float y0, float z0, float angle, float range, float slope) {
        final Level L = g.L;
        final float c = (float) Math.cos(angle), s = (float) Math.sin(angle);
        final float ex = x0 + c * range, ey = y0 + s * range;
        final float dx = ex - x0, dy = ey - y0;
        final float[] bestWall = {range + 1};
        final Level.Line[] wallLine = {null};
        final float fx = x0, fy = y0, fz = z0, sl = slope, rng = range;
        L.forLinesInBox(Math.min(x0, ex) - 1, Math.min(y0, ey) - 1, Math.max(x0, ex) + 1, Math.max(y0, ey) + 1,
                new Level.LineVisitor() {
                    public boolean visit(Level.Line l) {
                        float den = dx * l.dy - dy * l.dx;
                        if (Math.abs(den) < 1e-6f) return true;
                        float t = ((l.x1 - fx) * l.dy - (l.y1 - fy) * l.dx) / den;
                        float u = ((l.x1 - fx) * dy - (l.y1 - fy) * dx) / den;
                        if (t <= 0f || t >= 1f || u < 0f || u > 1f) return true;
                        float dist = t * rng;
                        if (dist >= bestWall[0]) return true;
                        if (l.back != null) {
                            float top = Math.min(l.fs.ceil, l.bs.ceil), bot = Math.max(l.fs.floor, l.bs.floor);
                            float z = fz + sl * dist;
                            if (top > bot && z >= bot && z <= top) return true;
                        }
                        bestWall[0] = dist; wallLine[0] = l;
                        return true;
                    }
                });
        Hit h = new Hit();
        float best = bestWall[0];
        Mobj hm = null;
        for (int i = 0, n = g.mobjs.size(); i < n; i++) {
            Mobj o = g.mobjs.get(i);
            if (o == shooter || o.removed || !o.shootable) continue;
            float ox = o.x - x0, oy = o.y - y0;
            float along = ox * c + oy * s;
            if (along <= 0 || along >= best) continue;
            float perp = Math.abs(-ox * s + oy * c);
            if (perp > o.radius) continue;
            float z = z0 + slope * along;
            if (z < o.z || z > o.z + o.height) continue;
            best = along; hm = o;
        }
        if (hm != null) {
            h.mobj = hm; h.dist = best;
        } else if (wallLine[0] != null) {
            h.line = wallLine[0]; h.dist = bestWall[0];
            Level.Line l = wallLine[0];
            if (l.back != null && l.bs != null && l.fs != null && (l.fs.skyCeil && l.bs.skyCeil)) h.hitSky = true;
        } else {
            h.dist = range; h.hitSky = true;
            h.x = x0 + c * range; h.y = y0 + s * range; h.z = z0 + slope * range;
            return h;
        }
        float back = hm != null ? 0f : 4f;
        h.x = x0 + c * (h.dist - back); h.y = y0 + s * (h.dist - back); h.z = z0 + slope * h.dist;
        return h;
    }

    /** Vertical autoaim. Returns target (or null) and sets aimSlope. */
    public float aimSlope;
    public Mobj aim(Mobj shooter, float angle, float range, boolean assist) {
        float eye = shooter.z + (shooter.kind == Mobj.PLAYER ? 41f : shooter.height * 0.75f);
        float[] offs = {0f, 0.098f, -0.098f};
        Mobj best = null; float bestD = 1e9f; float bestAng = angle;
        float c = (float) Math.cos(angle), s = (float) Math.sin(angle);
        for (int k = 0; k < 3; k++) {
            float a = angle + offs[k];
            float ca = (float) Math.cos(a), sa = (float) Math.sin(a);
            for (int i = 0, n = g.mobjs.size(); i < n; i++) {
                Mobj o = g.mobjs.get(i);
                if (o == shooter || o.removed || !o.shootable) continue;
                float ox = o.x - shooter.x, oy = o.y - shooter.y;
                float along = ox * ca + oy * sa;
                if (along <= 0 || along > range) continue;
                float perp = Math.abs(-ox * sa + oy * ca);
                if (perp > o.radius) continue;
                if (along < bestD && los(shooter.x, shooter.y, eye, o.x, o.y, o.z + o.height * 0.5f)) {
                    bestD = along; best = o; bestAng = a;
                }
            }
            if (best != null) break;
        }
        if (best != null) {
            float d = (float) Math.hypot(best.x - shooter.x, best.y - shooter.y);
            aimSlope = d > 1f ? (best.z + best.height * 0.5f - eye) / d : 0f;
            aimAngle = assist ? (float) Math.atan2(best.y - shooter.y, best.x - shooter.x) : angle;
            float diff = Math.abs(normAngle(aimAngle - angle));
            if (assist && diff > 0.11f) aimAngle = angle;
            if (!assist) aimAngle = angle;
        } else { aimSlope = 0f; aimAngle = angle; }
        return best;
    }
    public float aimAngle;

    public static float normAngle(float a) {
        double t = Math.PI * 2;
        a = (float) (a % t);
        if (a > Math.PI) a -= t;
        if (a < -Math.PI) a += t;
        return a;
    }
}
