// 바닐라 Projectile: 소유자(UUID)·쏜 뒤 소유자에게서 벗어났는지(leftOwner)·shoot 속도, 틱마다 움직임에 따른 맞음 판정(블록 충돌 + 엔티티·플레이어 훑기).
package org.domi.furnace.server.entity;

import java.util.UUID;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Clip;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.net.io.nbt.NbtCompound;

public abstract class Projectile extends Entity {
    private UUID ownerUUID;
    private Targetable owner;
    private boolean leftOwner;
    private boolean hasBeenShot;

    /** 맞은 대상: 블록이면 pos·face, 엔티티/플레이어면 target. 빗나감은 null. */
    public record Hit(Targetable target, Pos blockPos, int face, double x, double y, double z) {
        public boolean isEntity() { return target != null; }
    }

    protected Projectile(EntityType type, ChunkLevel level, int id) { super(type, level, id); }

    public void setOwner(Targetable o) {
        if (o != null) {
            ownerUUID = NeutralMob.uuidOf(o);
            owner = o;
        }
    }

    public Targetable getOwner() {
        if (owner != null && owner.isAlive()) return owner;
        if (ownerUUID != null) {
            owner = level.getTargetableByUuid(ownerUUID);
            return owner;
        }
        return null;
    }

    public int ownerId() { return owner == null ? 0 : owner.id(); }

    @Override
    public int spawnData() { return owner == null ? 0 : owner.id(); }

    @Override
    protected void addAdditionalSaveData(NbtCompound tag) {
        if (ownerUUID != null) tag.putIntArray("Owner", uuidToInts(ownerUUID));
        if (leftOwner) tag.putBoolean("LeftOwner", true);
        tag.putBoolean("HasBeenShot", hasBeenShot);
    }

    @Override
    protected void readAdditionalSaveData(NbtCompound tag) {
        if (tag.tags().get("Owner") instanceof int[] u && u.length == 4) {
            ownerUUID = new UUID((long) u[0] << 32 | u[1] & 0xFFFFFFFFL, (long) u[2] << 32 | u[3] & 0xFFFFFFFFL);
            owner = null;
        }
        leftOwner = tag.getBoolean("LeftOwner");
        hasBeenShot = tag.getBoolean("HasBeenShot");
    }

    protected boolean ownedBy(Targetable t) { return ownerUUID != null && ownerUUID.equals(NeutralMob.uuidOf(t)); }

    @Override
    public void tick() {
        if (!hasBeenShot) hasBeenShot = true;
        if (!leftOwner) leftOwner = checkLeftOwner();
        super.tick();
    }

    private boolean checkLeftOwner() {
        Targetable o = getOwner();
        if (o != null) {
            AABB box = boundingBox().expandTowards(vx(), vy(), vz()).inflate(1.0);
            for (Entity e : level.getEntities(this, box, x -> !(x instanceof Projectile) || true)) {
                if (e instanceof Targetable t && t.isPickable() && NeutralMob.uuidOf(t) != null && e == o) return false;
            }
            if (o instanceof Player p) {
                boolean[] inside = {false};
                level.forEachPlayerIn(box, q -> { if (q == p) inside[0] = true; });
                if (inside[0]) return false;
            }
        }
        return true;
    }

    /** Projectile.canHitEntity: 맞출 수 있고, 소유자는 한 번 벗어나기 전에는 안 맞는다. */
    protected boolean canHitTarget(Targetable t) {
        if (!t.isPickable()) return false;
        Targetable o = getOwner();
        return o == null || leftOwner || !(o == t);
    }

    /** Projectile.shoot(x, y, z, speed, inaccuracy): 방향에 무작위 흩어짐(엔티티 난수 3번)을 더해 속도로. */
    public void shoot(double dx, double dy, double dz, float speed, float inaccuracy) {
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double nx = dx / len, ny = dy / len, nz = dz / len;
        double spread = 0.0172275 * inaccuracy;
        nx += random.triangle(0.0, spread);
        ny += random.triangle(0.0, spread);
        nz += random.triangle(0.0, spread);
        double sx = nx * speed, sy = ny * speed, sz = nz * speed;
        setDeltaMovement(sx, sy, sz);
        hasImpulse = true;
        double h = Math.sqrt(sx * sx + sz * sz);
        setYRot((float) (org.domi.furnace.server.entity.ai.control.Mth.atan2(sx, sz) * 180.0F / (float) Math.PI));
        setXRot((float) (org.domi.furnace.server.entity.ai.control.Mth.atan2(sy, h) * 180.0F / (float) Math.PI));
        yRotO = yRot();
        xRotO = xRot();
    }

    /** 이동 선분에서 맞은 것(블록 → 엔티티 순으로 가장 가까운 것). */
    protected Hit findHit(double fx, double fy, double fz, double tx, double ty, double tz) {
        Hit best = null;
        Clip.Result r = Clip.clipCollider(level, fx, fy, fz, tx, ty, tz);
        double limit = 1.0;
        if (!r.miss()) {
            best = new Hit(null, r.pos(), r.face(), r.x(), r.y(), r.z());
            tx = r.x();
            ty = r.y();
            tz = r.z();
        }
        Hit ent = findHitEntity(fx, fy, fz, tx, ty, tz);
        return ent != null ? ent : best;
    }

    protected Hit findHitEntity(double fx, double fy, double fz, double tx, double ty, double tz) {
        AABB box = boundingBox().expandTowards(vx(), vy(), vz()).inflate(1.0);
        double bestD = Double.MAX_VALUE;
        Targetable bestT = null;
        double[] bestP = null;
        for (Entity e : level.getEntities(this, box, x -> true)) {
            if (!(e instanceof Targetable t) || !canHitTarget(t) || !shouldHit(t)) continue;
            double[] p = clipTarget(t, fx, fy, fz, tx, ty, tz);
            if (p == null) continue;
            double d = (p[0] - fx) * (p[0] - fx) + (p[1] - fy) * (p[1] - fy) + (p[2] - fz) * (p[2] - fz);
            if (d < bestD) {
                bestD = d;
                bestT = t;
                bestP = p;
            }
        }
        java.util.List<Player> players = new java.util.ArrayList<>();
        level.forEachPlayerIn(box, players::add);
        for (Player pl : players) {
            if (!canHitTarget(pl) || !shouldHit(pl)) continue;
            double[] p = clipTarget(pl, fx, fy, fz, tx, ty, tz);
            if (p == null) continue;
            double d = (p[0] - fx) * (p[0] - fx) + (p[1] - fy) * (p[1] - fy) + (p[2] - fz) * (p[2] - fz);
            if (d < bestD) {
                bestD = d;
                bestT = pl;
                bestP = p;
            }
        }
        return bestT == null ? null : new Hit(bestT, null, 0, bestP[0], bestP[1], bestP[2]);
    }

    protected boolean shouldHit(Targetable t) { return true; }

    /** ProjectileUtil.getEntityHitResult 의 한 칸: 상자를 0.3 키워 선분과 교차(시작점이 안이면 시작점). */
    private static double[] clipTarget(Targetable t, double fx, double fy, double fz, double tx, double ty, double tz) {
        AABB bb = t.boundingBox().inflate(0.3F);
        if (bb.contains(fx, fy, fz)) return new double[] {fx, fy, fz};
        return bb.clip(fx, fy, fz, tx, ty, tz);
    }

    protected void onHit(Hit hit) {
        if (hit.isEntity()) onHitEntity(hit);
        else onHitBlock(hit);
    }

    protected void onHitEntity(Hit hit) {}

    protected void onHitBlock(Hit hit) {}

    /** Projectile.updateRotation: 이동 방향으로 0.2 보간. */
    protected static float lerpRotation(float old, float cur) {
        while (cur - old < -180.0F) old -= 360.0F;
        while (cur - old >= 180.0F) old += 360.0F;
        return old + (cur - old) * 0.2F;
    }
}
