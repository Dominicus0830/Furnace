// 바닐라 Shulker: 블록 면에 붙어 사는 몹. 껍데기(peek)를 열면 대상에게 셜커 탄환을 쏘고, 닫으면 방어력 +20. 맞으면 순간이동한다.
// 속도·밀기 없음. TODO(플레이어 밀기): 껍데기가 열릴 때 플레이어 밀기(MOVER_SHULKER).
package org.domi.furnace.server.entity;

import java.util.EnumSet;
import java.util.List;
import org.domi.furnace.server.attribute.Attribute;
import org.domi.furnace.server.attribute.AttributeMap;
import org.domi.furnace.server.attribute.AttributeModifier;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.ai.Goal;
import org.domi.furnace.server.entity.ai.control.BodyRotationControl;
import org.domi.furnace.server.entity.ai.control.Mth;
import org.domi.furnace.server.entity.ai.control.ShulkerLookControl;
import org.domi.furnace.server.entity.ai.goal.*;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.protocol.play.SetEntityData;

public class Shulker extends AbstractGolem implements Enemy, HardCollider {

    @Override
    protected MovementEmission getMovementEmission() { return MovementEmission.NONE; }

    public static final int DATA_ATTACH_FACE_ID = 16, DATA_PEEK_ID = 17, DATA_COLOR_ID = 18;
    private static final String COVERED_ARMOR_MODIFIER_ID = "minecraft:covered";
    private static final AttributeModifier COVERED_ARMOR_MODIFIER = new AttributeModifier(COVERED_ARMOR_MODIFIER_ID, 20.0, AttributeModifier.ADD_VALUE);
    private float currentPeekAmount, currentPeekAmountO;
    private boolean ready;

    public Shulker(ChunkLevel level, int id) {
        super(EntityType.SHULKER, level, id);
        xpReward = 5;
        lookControl = new ShulkerLookControl(this);
        bodyRotationControl = new BodyRotationControl(this) {
            @Override public void clientTick() { }
        };
        ready = true;
        refreshBoundingBox();
    }

    @Override
    protected void createAttributes(AttributeMap map) {
        super.createAttributes(map);
        map.add(Attribute.MAX_HEALTH, 30.0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new LookAtPlayerGoal(this, 8.0F, 0.02F, true));
        goalSelector.addGoal(4, new ShulkerAttackGoal());
        goalSelector.addGoal(7, new ShulkerPeekGoal());
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this, getClass()).setAlertOthers());
        targetSelector.addGoal(2, new ShulkerNearestAttackableTargetGoal(this));
        targetSelector.addGoal(3, new ShulkerDefenseAttackGoal(this));
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_ATTACH_FACE_ID, SetEntityData.SERIALIZER_DIRECTION, Dir.DOWN);
        entityData.define(DATA_PEEK_ID, SetEntityData.SERIALIZER_BYTE, (byte) 0);
        entityData.define(DATA_COLOR_ID, SetEntityData.SERIALIZER_BYTE, (byte) 16);
    }

    @Override public String soundSource() { return "hostile"; }

    @Override public String getAmbientSound() { return "entity.shulker.ambient"; }

    @Override
    protected void playAmbientSound() {
        if (!isClosed()) super.playAmbientSound();
    }

    @Override protected String getDeathSound() { return "entity.shulker.death"; }

    @Override protected String getHurtSound(Damage source) { return isClosed() ? "entity.shulker.hurt_closed" : "entity.shulker.hurt"; }

    @Override public int getMaxHeadXRot() { return 180; }

    @Override public int getMaxHeadYRot() { return 180; }

    // ------------------------------------------------------------------ 저장

    @Override
    protected void readAdditionalSaveData(NbtCompound tag) {
        super.readAdditionalSaveData(tag);
        setAttachFace(tag.getByte("AttachFace"));
        entityData.set(DATA_PEEK_ID, tag.getByte("Peek"));
        if (tag.tags().get("Color") instanceof Number n) entityData.set(DATA_COLOR_ID, n.byteValue());
    }

    @Override
    protected void addAdditionalSaveData(NbtCompound tag) {
        super.addAdditionalSaveData(tag);
        tag.putByte("AttachFace", (byte) getAttachFace());
        tag.putByte("Peek", (Byte) entityData.get(DATA_PEEK_ID));
        tag.putByte("Color", (Byte) entityData.get(DATA_COLOR_ID));
    }

    // ------------------------------------------------------------------ 붙은 면·상자

    public int getAttachFace() { return (Integer) entityData.get(DATA_ATTACH_FACE_ID); }

    private void setAttachFace(int face) {
        entityData.set(DATA_ATTACH_FACE_ID, face);
        refreshBoundingBox();
    }

    private int getRawPeekAmount() { return (Byte) entityData.get(DATA_PEEK_ID); }

    public boolean isClosed() { return getRawPeekAmount() == 0; }

    private static float ease(float f) { return 0.5F - Mth.sin((0.5F + f) * (float) Math.PI) * 0.5F; }

    /** getProgressAabb(size, dir, from, to) */
    public static AABB progressAabb(float size, int dir, float from, float to) {
        double d0 = Math.max(from, to), d1 = Math.min(from, to);
        double minX = 0, minY = 0, minZ = 0, maxX = size, maxY = size, maxZ = size;
        double ex = Dir.DX[dir] * d0 * size, ey = Dir.DY[dir] * d0 * size, ez = Dir.DZ[dir] * d0 * size;   // expandTowards
        if (ex < 0) minX += ex; else maxX += ex;
        if (ey < 0) minY += ey; else maxY += ey;
        if (ez < 0) minZ += ez; else maxZ += ez;
        double cx = -Dir.DX[dir] * (1.0 + d1) * size, cy = -Dir.DY[dir] * (1.0 + d1) * size, cz = -Dir.DZ[dir] * (1.0 + d1) * size;   // contract
        if (cx < 0) minX -= cx; else if (cx > 0) maxX -= cx;   // AABB.contract: 음수면 min 을, 양수면 max 를 줄인다
        if (cy < 0) minY -= cy; else if (cy > 0) maxY -= cy;
        if (cz < 0) minZ -= cz; else if (cz > 0) maxZ -= cz;
        return AABB.of(minX, minY, minZ, maxX, maxY, maxZ);   // new AABB 는 min/max 를 정렬한다
    }

    @Override
    protected AABB makeBb(double px, double py, double pz) {
        if (!ready) return super.makeBb(px, py, pz);
        int opposite = Dir.opposite(getAttachFace());
        AABB a = progressAabb(1.0F, opposite, ease(currentPeekAmount));
        double half = bbWidth() / 2.0F;
        return a.move(px - half, py, pz - half);
    }

    private static AABB progressAabb(float size, int dir, float progress) { return progressAabb(size, dir, -1.0F, progress); }

    private boolean isPositionBlocked(Pos pos) {
        int state = level.getBlockState(pos);
        if (B.isAir(state)) return false;
        boolean movingPiston = B.is(state, "minecraft:moving_piston") && pos.equals(blockPosition());
        return !movingPiston;
    }

    boolean canStayAt(Pos pos, int face) {
        if (isPositionBlocked(pos)) return false;
        int opposite = Dir.opposite(face);
        Pos support = pos.relative(face);
        if (!level.hasChunkAt(support) || !B.isFaceSturdy(level.getBlockState(support), level, support, opposite)) return false;
        AABB a = progressAabb(1.0F, opposite, 1.0F).move(pos.x(), pos.y(), pos.z()).deflate(1.0E-6);
        return level.noCollision(this, a);
    }

    private int findAttachableSurface(Pos pos) {
        for (int d : Dir.ALL) if (canStayAt(pos, d)) return d;
        return -1;
    }

    // ------------------------------------------------------------------ 위치·속도

    @Override
    public void setPos(double nx, double ny, double nz) {
        Pos old = blockPosition();
        super.setPos(Math.floor(nx) + 0.5, Math.floor(ny + 0.5), Math.floor(nz) + 0.5);
        if (tickCount != 0 && !blockPosition().equals(old)) {
            entityData.set(DATA_PEEK_ID, (byte) 0);
            hasImpulse = true;
        }
    }

    @Override public double vx() { return 0.0; }

    @Override public double vy() { return 0.0; }

    @Override public double vz() { return 0.0; }

    @Override public void setDeltaMovement(double vx, double vy, double vz) { }

    @Override
    public void move(int mover, double dx, double dy, double dz) {
        if (mover == MOVER_SHULKER_BOX) teleportSomewhere();   // 바닐라 MoverType.SHULKER_BOX 일 때만(피스톤은 그냥 밀린다)
        else super.move(mover, dx, dy, dz);
    }

    @Override
    public void baseTick() {
        super.baseTick();
        yBodyRot = 0.0F;
        yBodyRotO = 0.0F;
    }

    @Override
    public void tick() {
        super.tick();
        if (!isPassenger() && !canStayAt(blockPosition(), getAttachFace())) findNewAttachment();
        if (updatePeekAmount()) onPeekAmountChange();
    }

    private void findNewAttachment() {
        int d = findAttachableSurface(blockPosition());
        if (d >= 0) setAttachFace(d);
        else teleportSomewhere();
    }

    private boolean updatePeekAmount() {
        currentPeekAmountO = currentPeekAmount;
        float raw = getRawPeekAmount() * 0.01F;
        if (currentPeekAmount == raw) return false;
        if (currentPeekAmount > raw) currentPeekAmount = Math.clamp(currentPeekAmount - 0.05F, raw, 1.0F);
        else currentPeekAmount = Math.clamp(currentPeekAmount + 0.05F, 0.0F, raw);
        return true;
    }

    private void onPeekAmountChange() {
        refreshBoundingBox();
        float f = ease(currentPeekAmount), f1 = ease(currentPeekAmountO);
        int opposite = Dir.opposite(getAttachFace());
        float d0 = (f - f1) * 1.0F;
        if (!(d0 <= 0.0F)) {
            AABB box = progressAabb(1.0F, opposite, f1, f).move(x() - 0.5, y(), z() - 0.5);
            for (Entity e : level.getEntities(this, box, x -> !x.isSpectator())) {
                if (!(e instanceof Shulker) && !e.noPhysics) e.move(MOVER_SHULKER, d0 * Dir.DX[opposite], d0 * Dir.DY[opposite], d0 * Dir.DZ[opposite]);
            }
        }
    }

    // ------------------------------------------------------------------ 순간이동

    protected boolean teleportSomewhere() {
        if (!isNoAi() && isAlive()) {
            Pos pos = blockPosition();
            for (int i = 0; i < 5; i++) {
                Pos p = pos.offset(random.nextInt(17) - 8, random.nextInt(17) - 8, random.nextInt(17) - 8);
                if (p.y() > level.minBuildHeight() && level.isEmptyBlock(p) && level.isInWorldBounds(p)
                        && level.noCollision(this, new AABB(p.x(), p.y(), p.z(), p.x() + 1.0, p.y() + 1.0, p.z() + 1.0).deflate(1.0E-6))) {
                    int d = findAttachableSurface(p);
                    if (d >= 0) {
                        setAttachFace(d);
                        playSound("entity.shulker.teleport", 1.0F, 1.0F);
                        setPos(p.x() + 0.5, p.y(), p.z() + 0.5);
                        level.gameEventFrom("teleport", pos, this, -1);   // ckj:321 옛 칸 가운데
                        entityData.set(DATA_PEEK_ID, (byte) 0);
                        setTarget(null);
                        return true;
                    }
                }
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ 피해

    @Override
    public boolean hurtBy(Damage source, float amount, Targetable attacker) {
        if (isClosed()) {
            Entity direct = directCause();
            if (direct instanceof AbstractArrow) return false;
        }
        if (!super.hurtBy(source, amount, attacker)) return false;
        if (health() < maxHealth() * 0.5 && random.nextInt(4) == 0) {
            teleportSomewhere();
        } else if (source.type().equals("mob_projectile") || source.type().equals("arrow") || source.type().equals("thrown")) {
            Entity direct = directCause();
            if (direct != null && direct.type() == EntityType.SHULKER_BULLET) hitByShulkerBullet();
        }
        return true;
    }

    private void hitByShulkerBullet() {
        double ox = x(), oy = y(), oz = z();
        AABB box = boundingBox();
        if (!isClosed() && teleportSomewhere()) {
            int n = level.getEntitiesOfClass(Shulker.class, box.inflate(8.0), Entity::isAlive).size();
            float f = (n - 1) / 5.0F;
            if (!(level.random().nextFloat() < f)) {
                Shulker s = new Shulker(level, level.nextEntityId());
                s.entityData.set(DATA_COLOR_ID, (Byte) entityData.get(DATA_COLOR_ID));
                s.moveTo(ox, oy, oz, 0.0F, 0.0F);
                level.addFreshEntity(s);
            }
        }
    }

    private void setRawPeekAmount(int v) {
        attributes().get(Attribute.ARMOR).removeModifier(COVERED_ARMOR_MODIFIER_ID);
        if (v == 0) {
            attributes().get(Attribute.ARMOR).addOrReplaceModifier(COVERED_ARMOR_MODIFIER);
            playSound("entity.shulker.close", 1.0F, 1.0F);
            gameEvent(org.domi.furnace.server.world.gameevent.GameEvent.CONTAINER_CLOSE);
        } else {
            playSound("entity.shulker.open", 1.0F, 1.0F);
            gameEvent(org.domi.furnace.server.world.gameevent.GameEvent.CONTAINER_OPEN);
        }
        entityData.set(DATA_PEEK_ID, (byte) v);
    }

    @Override
    public void finalizeSpawn(org.domi.furnace.server.block.RandomSource r, DifficultyInstance difficulty) {
        setYRot(0.0F);
        yBodyRot = yRot();
        setOldPosAndRot();
        super.finalizeSpawn(r, difficulty);
    }

    @Override public boolean isPickable() { return isAlive(); }

    /** 살아 있는 셜커는 단단해 다른 엔티티가 위에 서고 부딪힌다. */
    @Override public boolean canBeCollidedWith() { return isAlive(); }

    // ------------------------------------------------------------------ 목표

    class ShulkerAttackGoal extends Goal {
        private int attackTime;

        ShulkerAttackGoal() { setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK)); }

        @Override
        public boolean canUse() {
            Targetable t = getTarget();
            return t != null && t.isAlive() && level.difficulty() != 0;
        }

        @Override
        public void start() {
            attackTime = 20;
            setRawPeekAmount(100);
        }

        @Override public void stop() { setRawPeekAmount(0); }

        @Override public boolean requiresUpdateEveryTick() { return true; }

        @Override
        public void tick() {
            if (level.difficulty() != 0) {
                attackTime--;
                Targetable t = getTarget();
                if (t != null) {
                    getLookControl().setLookAt(t.x(), t.eyeY(), t.z(), 180.0F, 180.0F);
                    double dx = t.x() - x(), dy = t.y() - y(), dz = t.z() - z();
                    if (dx * dx + dy * dy + dz * dz < 400.0) {
                        if (attackTime <= 0) {
                            attackTime = 20 + random.nextInt(10) * 20 / 2;
                            level.addFreshEntity(new ShulkerBullet(level, level.nextEntityId(), Shulker.this, t, Dir.axis(getAttachFace())));
                            playSound("entity.shulker.shoot", 2.0F, (random.nextFloat() - random.nextFloat()) * 0.2F + 1.0F);
                        }
                    } else {
                        setTarget(null);
                    }
                }
            }
        }
    }

    class ShulkerPeekGoal extends Goal {
        private int peekTime;

        @Override
        public boolean canUse() { return getTarget() == null && random.nextInt(reducedTickDelay(40)) == 0 && canStayAt(blockPosition(), getAttachFace()); }

        @Override
        public boolean canContinueToUse() { return getTarget() == null && peekTime > 0; }

        @Override
        public void start() {
            peekTime = adjustedTickDelay(20 * (1 + random.nextInt(3)));
            setRawPeekAmount(30);
        }

        @Override
        public void stop() {
            if (getTarget() == null) setRawPeekAmount(0);
        }

        @Override public void tick() { peekTime--; }
    }

    /** ShulkerDefenseAttackGoal: 팀에 속해 있으면 보이는 적대 몹(Enemy)을 노린다(빛이 닿지 않아도 됨). 탐색 상자는 붙은 면 기준. */
    static class ShulkerDefenseAttackGoal extends NearestAttackableTargetGoal {
        private final Shulker shulker;

        ShulkerDefenseAttackGoal(Shulker shulker) {
            super(shulker, LivingEntity.class, 10, true, false, t -> t instanceof Enemy);
            this.shulker = shulker;
        }

        @Override
        public boolean canUse() { return shulker.getTeam() == null ? false : super.canUse(); }

        @Override
        protected AABB getTargetSearchArea(double range) {
            int axis = Dir.axis(shulker.getAttachFace());
            if (axis == Dir.AXIS_X) return mob.boundingBox().inflate(4.0, range, range);
            return axis == Dir.AXIS_Z ? mob.boundingBox().inflate(range, range, 4.0) : mob.boundingBox().inflate(range, 4.0, range);
        }
    }

    static class ShulkerNearestAttackableTargetGoal extends NearestAttackableTargetGoal {
        private final Shulker shulker;

        ShulkerNearestAttackableTargetGoal(Shulker shulker) {
            super(shulker, Player.class, true);
            this.shulker = shulker;
        }

        @Override
        public boolean canUse() { return shulker.level().difficulty() == 0 ? false : super.canUse(); }

        @Override
        protected AABB getTargetSearchArea(double range) {
            int axis = Dir.axis(shulker.getAttachFace());
            if (axis == Dir.AXIS_X) return mob.boundingBox().inflate(4.0, range, range);
            return axis == Dir.AXIS_Z ? mob.boundingBox().inflate(range, range, 4.0) : mob.boundingBox().inflate(range, 4.0, range);
        }
    }
}
