// 바닐라 ShulkerBullet: 셜커가 쏘는 유도 탄환. 벽을 통과하며 한 축씩 대상을 따라가고, 맞으면 피해 4 + 공중 부양(10초). 쏘면 사라진다.
package org.domi.furnace.server.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.effect.MobEffect;
import org.domi.furnace.server.effect.MobEffectInstance;
import org.domi.furnace.server.entity.ai.control.Mth;
import org.domi.furnace.server.net.io.nbt.NbtCompound;

public class ShulkerBullet extends Projectile {
    private static final double SPEED = 0.15;
    private Targetable finalTarget;
    private int currentMoveDirection = -1;
    private int flightSteps;
    private double targetDeltaX, targetDeltaY, targetDeltaZ;
    private UUID targetId;

    public ShulkerBullet(ChunkLevel level, int id) {
        super(EntityType.SHULKER_BULLET, level, id);
        noPhysics = true;
    }

    public ShulkerBullet(ChunkLevel level, int id, LivingEntity owner, Targetable target, int axis) {
        this(level, id);
        setOwner(owner);
        AABB b = owner.boundingBox();
        moveTo((b.minX() + b.maxX()) / 2.0, (b.minY() + b.maxY()) / 2.0, (b.minZ() + b.maxZ()) / 2.0, yRot(), xRot());
        finalTarget = target;
        targetId = NeutralMob.uuidOf(target);
        currentMoveDirection = Dir.UP;
        selectNextMoveDirection(axis);
    }

    @Override public String soundSource() { return "hostile"; }

    @Override protected double defaultGravity() { return 0.04; }

    @Override public boolean isPickable() { return true; }

    @Override
    protected void addAdditionalSaveData(NbtCompound tag) {
        super.addAdditionalSaveData(tag);
        if (targetId != null) {
            long m = targetId.getMostSignificantBits(), l = targetId.getLeastSignificantBits();
            tag.putIntArray("Target", new int[] {(int) (m >> 32), (int) m, (int) (l >> 32), (int) l});
        }
        if (currentMoveDirection >= 0) tag.putInt("Dir", currentMoveDirection);
        tag.putInt("Steps", flightSteps);
        tag.putDouble("TXD", targetDeltaX);
        tag.putDouble("TYD", targetDeltaY);
        tag.putDouble("TZD", targetDeltaZ);
    }

    @Override
    protected void readAdditionalSaveData(NbtCompound tag) {
        super.readAdditionalSaveData(tag);
        flightSteps = tag.getIntOr0("Steps");
        targetDeltaX = tag.getDouble("TXD");
        targetDeltaY = tag.getDouble("TYD");
        targetDeltaZ = tag.getDouble("TZD");
        if (tag.tags().get("Dir") instanceof Number n) currentMoveDirection = n.intValue();
        if (tag.tags().get("Target") instanceof int[] u && u.length == 4) {
            targetId = new UUID((long) u[0] << 32 | u[1] & 0xFFFFFFFFL, (long) u[2] << 32 | u[3] & 0xFFFFFFFFL);
            finalTarget = null;
        }
    }

    private void selectNextMoveDirection(int axis) {
        double d0 = 0.5;
        Pos blockpos;
        if (finalTarget == null) {
            blockpos = blockPosition().below();
        } else {
            d0 = finalTarget.bbHeight() * 0.5;
            blockpos = new Pos((int) Math.floor(finalTarget.x()), (int) Math.floor(finalTarget.y() + d0), (int) Math.floor(finalTarget.z()));
        }
        double d1 = blockpos.x() + 0.5, d2 = blockpos.y() + d0, d3 = blockpos.z() + 0.5;
        int dir = -1;
        double cx = blockpos.x() + 0.5 - x(), cy = blockpos.y() + 0.5 - y(), cz = blockpos.z() + 0.5 - z();
        if (!(cx * cx + cy * cy + cz * cz < 4.0)) {
            Pos bp1 = blockPosition();
            List<Integer> list = new ArrayList<>();
            if (axis != Dir.AXIS_X) {
                if (bp1.x() < blockpos.x() && level.isEmptyBlock(bp1.east())) list.add(Dir.EAST);
                else if (bp1.x() > blockpos.x() && level.isEmptyBlock(bp1.west())) list.add(Dir.WEST);
            }
            if (axis != Dir.AXIS_Y) {
                if (bp1.y() < blockpos.y() && level.isEmptyBlock(bp1.above())) list.add(Dir.UP);
                else if (bp1.y() > blockpos.y() && level.isEmptyBlock(bp1.below())) list.add(Dir.DOWN);
            }
            if (axis != Dir.AXIS_Z) {
                if (bp1.z() < blockpos.z() && level.isEmptyBlock(bp1.relative(Dir.SOUTH))) list.add(Dir.SOUTH);
                else if (bp1.z() > blockpos.z() && level.isEmptyBlock(bp1.relative(Dir.NORTH))) list.add(Dir.NORTH);
            }
            dir = Dir.getRandom(random);
            if (list.isEmpty()) {
                for (int i = 5; !level.isEmptyBlock(bp1.relative(dir)) && i > 0; i--) dir = Dir.getRandom(random);
            } else {
                dir = list.get(random.nextInt(list.size()));
            }
            d1 = x() + Dir.DX[dir];
            d2 = y() + Dir.DY[dir];
            d3 = z() + Dir.DZ[dir];
        }
        currentMoveDirection = dir;
        double dx = d1 - x(), dy = d2 - y(), dz = d3 - z();
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len == 0.0) {
            targetDeltaX = targetDeltaY = targetDeltaZ = 0.0;
        } else {
            targetDeltaX = dx / len * SPEED;
            targetDeltaY = dy / len * SPEED;
            targetDeltaZ = dz / len * SPEED;
        }
        hasImpulse = true;
        flightSteps = 10 + random.nextInt(5) * 10;
    }

    @Override
    public void tick() {
        super.tick();
        if (finalTarget == null && targetId != null) {
            finalTarget = level.getTargetableByUuid(targetId);
            if (finalTarget == null) targetId = null;
        }
        if (finalTarget == null || !finalTarget.isAlive() || finalTarget instanceof Player p && p.gameMode() == org.domi.furnace.server.world.GameMode.SPECTATOR) {
            applyGravity();
        } else {
            targetDeltaX = Math.clamp(targetDeltaX * 1.025, -1.0, 1.0);
            targetDeltaY = Math.clamp(targetDeltaY * 1.025, -1.0, 1.0);
            targetDeltaZ = Math.clamp(targetDeltaZ * 1.025, -1.0, 1.0);
            setDeltaMovement(vx() + (targetDeltaX - vx()) * 0.2, vy() + (targetDeltaY - vy()) * 0.2, vz() + (targetDeltaZ - vz()) * 0.2);
        }
        Hit hit = findHit(x(), y(), z(), x() + vx(), y() + vy(), z() + vz());
        if (hit != null) hitTargetOrDeflectSelf(hit);
        if (isRemoved()) return;
        checkInsideBlocks();
        double nx = x() + vx(), ny = y() + vy(), nz = z() + vz();
        setPos(nx, ny, nz);
        rotateTowardsMovement(0.5F);
        if (finalTarget != null && finalTarget.isAlive()) {
            if (flightSteps > 0) {
                flightSteps--;
                if (flightSteps == 0) selectNextMoveDirection(currentMoveDirection < 0 ? -1 : Dir.axis(currentMoveDirection));
            }
            if (currentMoveDirection >= 0) {
                Pos bp = blockPosition();
                int axis = Dir.axis(currentMoveDirection);
                Pos ahead = bp.relative(currentMoveDirection);
                if (level.hasChunkAt(ahead) && B.isFaceSturdy(level.getBlockState(ahead), level, ahead, Dir.UP)) {
                    selectNextMoveDirection(axis);
                } else {
                    Pos tp = finalTarget.blockPosition();
                    if (axis == Dir.AXIS_X && bp.x() == tp.x() || axis == Dir.AXIS_Z && bp.z() == tp.z() || axis == Dir.AXIS_Y && bp.y() == tp.y()) selectNextMoveDirection(axis);
                }
            }
        }
    }

    /** ProjectileUtil.rotateTowardsMovement(entity, factor) */
    private void rotateTowardsMovement(float factor) {
        double mx = vx(), my = vy(), mz = vz();
        if (mx * mx + my * my + mz * mz != 0.0) {
            double h = Math.sqrt(mx * mx + mz * mz);
            setYRot((float) (Mth.atan2(mz, mx) * 180.0F / (float) Math.PI) + 90.0F);
            setXRot((float) (Mth.atan2(h, my) * 180.0F / (float) Math.PI) - 90.0F);
            while (xRot() - xRotO < -180.0F) xRotO -= 360.0F;
            while (xRot() - xRotO >= 180.0F) xRotO += 360.0F;
            while (yRot() - yRotO < -180.0F) yRotO -= 360.0F;
            while (yRot() - yRotO >= 180.0F) yRotO += 360.0F;
            setXRot(xRotO + (xRot() - xRotO) * factor);
            setYRot(yRotO + (yRot() - yRotO) * factor);
        }
    }

    @Override
    protected boolean shouldHit(Targetable t) { return !(t instanceof Entity e && e.noPhysics); }

    @Override
    protected void onHitEntity(Hit hit) {
        Targetable target = hit.target();
        Targetable owner = getOwner();
        Damage dmg = new Damage("mob_projectile", false, false, false);
        boolean ok;
        if (target instanceof LivingEntity le) ok = le.hurtByVia(dmg, 4.0F, owner, this);
        else ok = target.hurtByMob(dmg, 4.0F, owner instanceof Mob m ? m : null);
        if (ok) {
            MobEffectInstance lev = new MobEffectInstance(MobEffect.LEVITATION, 200);
            if (target instanceof LivingEntity le) le.addEffect(lev);
            else if (target instanceof Player p) p.vitals().addEffect(lev);
        }
    }

    @Override
    protected void onHitBlock(Hit hit) {
        super.onHitBlock(hit);   // 바닐라는 먼저 맞은 블록의 onProjectileHit
        playSound("entity.shulker_bullet.hit", 1.0F, 1.0F);
    }

    @Override
    protected void onHit(Hit hit) {
        super.onHit(hit);
        discard();
    }

    @Override
    public boolean hurt(Damage source, float amount) {
        playSound("entity.shulker_bullet.hurt", 1.0F, 1.0F);
        discard();
        return true;
    }
}
