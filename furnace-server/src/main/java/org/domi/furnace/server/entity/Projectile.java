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

    /** ProjectileDeflection: NONE 튕기지 않음, AIM 튕긴 쪽의 시선(단위), REVERSE 반대로 반 속도·방향 170~190도 틀기, BREEZE 브리즈 튕김 소리 + REVERSE. */
    public enum Deflection { NONE, AIM, REVERSE, BREEZE }

    /** tags/entity_type redirectable_projectile(fireball·wind_charge·breeze_wind_charge): 맞을 수 있고 튕겨 낼 수 있다(Projectile.isPickable). */
    public boolean isRedirectable() {
        String n = type().name();
        return n.equals("minecraft:fireball") || n.equals("minecraft:wind_charge") || n.equals("minecraft:breeze_wind_charge");
    }

    @Override public boolean isPickable() { return isRedirectable(); }

    private Targetable lastDeflectedBy;

    /**
     * Projectile.deflect(deflection, deflecter, owner, byAttack): 서버에서 방식대로 속도를 바꾸고 주인을 owner 로 바꾼 뒤 onDeflection.
     * 튕김을 받지 않으면(canDeflect 거짓) false. AIM 은 튕긴 쪽이 없으면(null) 속도를 건드리지 않는다.
     */
    public boolean deflect(Deflection d, Targetable deflecter, Targetable newOwner, boolean byAttack) {
        if (!canDeflect()) return false;
        switch (d) {
            case AIM -> {
                if (deflecter != null) {
                    double[] v = lookOf(deflecter);
                    double len = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
                    if (len < 1.0E-5) setDeltaMovement(0.0, 0.0, 0.0);
                    else setDeltaMovement(v[0] / len, v[1] / len, v[2] / len);
                    hasImpulse = true;
                }
            }
            case BREEZE -> {
                if (deflecter instanceof Entity e) level.playSound(null, e, "entity.breeze.deflect", "hostile", 1.0F, 1.0F);
                reverse();
            }
            case REVERSE -> reverse();
            case NONE -> { }
        }
        setOwner(newOwner);
        onDeflection(byAttack);
        return true;
    }

    /** ProjectileDeflection.REVERSE: 속도 -0.5배, 바라보는 방향 170 + 난수(0~20)도 틀기. */
    private void reverse() {
        float turn = 170.0F + random.nextFloat() * 20.0F;
        setDeltaMovement(vx() * -0.5, vy() * -0.5, vz() * -0.5);
        setYRot(yRot() + turn);
        yRotO += turn;
        hasImpulse = true;
    }

    private static double[] lookOf(Targetable t) {
        if (t instanceof Player p) return org.domi.furnace.server.block.Clip.viewVector(p.pitch(), p.yaw());
        if (t instanceof Entity e) return org.domi.furnace.server.block.Clip.viewVector(e.xRot(), e.yRot());
        return new double[] {0.0, 0.0, 0.0};
    }

    /** Projectile.deflect(AIM_DEFLECT, deflecter, owner, byAttack): 시선 방향 단위 벡터가 속도가 된다(플레이어 공격). */
    public boolean deflectAim(Player deflecter, Targetable newOwner, boolean byAttack) { return deflect(Deflection.AIM, deflecter, newOwner, byAttack); }

    /**
     * Projectile.hitTargetOrDeflectSelf: 맞은 엔티티가 튕기는 쪽(브리즈)이면 이 투사체가 튕겨 나가고 onHit 은 부르지 않는다. 튕겼으면 true.
     * 그 밖에는 redirectable_projectile 을 맞췄을 때 그쪽을 시선 방향으로 튕긴 뒤 onHit(엔티티 적중: 이 투사체는 Targetable 이 아닌 투사체를 아직 못 맞춘다).
     */
    protected boolean hitTargetOrDeflectSelf(Hit hit) {
        if (hit.isEntity() && !(hit.target() instanceof EnderDragonPart)) {
            Targetable t = hit.target();
            Deflection d = t.projectileDeflection(this);
            if (d != Deflection.NONE) {
                if (t != lastDeflectedBy && deflect(d, t, getOwner(), false)) lastDeflectedBy = t;
                return true;
            }
        }
        onHit(hit);
        return false;
    }

    /** 튕김을 받는가(바람 구슬은 처음 5틱 동안 거짓). */
    protected boolean canDeflect() { return true; }

    /** Projectile.onDeflection(entity, byAttack): 기본은 아무것도 안 한다. */
    protected void onDeflection(boolean byAttack) {}

    /** 마지막으로 찾은 주인(살아 있는지 보지 않는다 — 바닐라 getOwner 는 제거되지 않았으면 죽었어도 돌려준다). */
    protected Targetable lastOwner() { return owner; }

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
        if (!hasBeenShot) {   // cnp:83 첫 틱에 주인이 원인인 PROJECTILE_SHOOT
            gameEvent(org.domi.furnace.server.world.gameevent.GameEvent.PROJECTILE_SHOOT, getOwner());
            hasBeenShot = true;
        }
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

    /**
     * Projectile.shootFromRotation: 쏜 쪽의 시선(xRot, yRot)과 z 오프셋으로 방향을 정해 shoot 하고, 쏜 쪽의 알려진 이동량을 더한다
     * (땅에 서 있으면 y 는 더하지 않는다).
     */
    public void shootFromRotation(float xRot, float yRot, float zOffset, float power, float inaccuracy, double[] shooterMovement, boolean shooterOnGround) {
        float rad = (float) (Math.PI / 180.0);
        float f = -org.domi.furnace.server.entity.ai.control.Mth.sin(yRot * rad) * org.domi.furnace.server.entity.ai.control.Mth.cos(xRot * rad);
        float f1 = -org.domi.furnace.server.entity.ai.control.Mth.sin((xRot + zOffset) * rad);
        float f2 = org.domi.furnace.server.entity.ai.control.Mth.cos(yRot * rad) * org.domi.furnace.server.entity.ai.control.Mth.cos(xRot * rad);
        shoot(f, f1, f2, power, inaccuracy);
        setDeltaMovement(vx() + shooterMovement[0], vy() + (shooterOnGround ? 0.0 : shooterMovement[1]), vz() + shooterMovement[2]);
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
        // 바닐라 Level.getEntities: 드래곤이 잡히면 바로 뒤에 파트 8개를 상자 검사 없이 조건만으로 붙인다
        java.util.List<Targetable> cands = new java.util.ArrayList<>();
        for (Entity e : level.getEntities(this, box, x -> true)) {
            if (e instanceof Targetable t) cands.add(t);
            if (e instanceof EnderDragon d) java.util.Collections.addAll(cands, d.subEntities);
        }
        for (Targetable t : cands) {
            if (!canHitTarget(t) || !shouldHit(t)) continue;
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

    /** Projectile.calculateHorizontalHurtKnockbackDirection: 기본은 투사체 속도 (x, z). 맞은 쪽은 이 반대로 밀린다. */
    public double[] hurtKnockbackDirection(Targetable target) { return new double[] {vx(), vz()}; }

    /** ProjectileUtil.getEntityHitResult 의 한 칸: 상자를 0.3 키워 선분과 교차(시작점이 안이면 시작점). */
    private static double[] clipTarget(Targetable t, double fx, double fy, double fz, double tx, double ty, double tz) {
        AABB bb = t.boundingBox().inflate(0.3F);
        if (bb.contains(fx, fy, fz)) return new double[] {fx, fy, fz};
        return bb.clip(fx, fy, fz, tx, ty, tz);
    }

    protected void onHit(Hit hit) {
        if (hit.target() instanceof EnderDragonPart part) {
            // 파트는 Entity·LivingEntity 가 아니라 각 투사체가 hurtByMob 으로 부른다 — 주인(플레이어 포함)·투사체를 실어 둔다
            part.beginProjectileHit(getOwner(), this);
            try {
                onHitEntity(hit);
            } finally {
                part.endProjectileHit();
            }
            level.gameEvent(org.domi.furnace.server.world.gameevent.GameEvent.PROJECTILE_LAND, new Vec3(hit.x(), hit.y(), hit.z()), org.domi.furnace.server.world.gameevent.GameEventContext.of(this));
            return;
        }
        if (hit.isEntity()) {
            onHitEntity(hit);
            level.gameEvent(org.domi.furnace.server.world.gameevent.GameEvent.PROJECTILE_LAND, new Vec3(hit.x(), hit.y(), hit.z()), org.domi.furnace.server.world.gameevent.GameEventContext.of(this));   // cnp:172 맞은 위치
        } else {
            onHitBlock(hit);
            if (hit.blockPos() != null) level.gameEventFrom("projectile_land", hit.blockPos(), this, level.getBlockState(hit.blockPos()));   // cnp:177 칸 가운데 + 블록 상태
        }
    }

    protected void onHitEntity(Hit hit) {}

    /** Projectile.onHitBlock: 맞은 블록의 onProjectileHit(하위 클래스는 super 를 부른다). */
    protected void onHitBlock(Hit hit) {
        if (hit.blockPos() == null || !(level instanceof org.domi.furnace.server.block.BlockLevel bl)) return;
        int state = bl.getBlockState(hit.blockPos());
        org.domi.furnace.server.block.B.behavior(state).onProjectileHit(bl, state, hit, this);
    }

    /** Projectile.updateRotation: 이동 방향으로 0.2 보간. */
    protected static float lerpRotation(float old, float cur) {
        while (cur - old < -180.0F) old -= 360.0F;
        while (cur - old >= 180.0F) old += 360.0F;
        return old + (cur - old) * 0.2F;
    }
}
