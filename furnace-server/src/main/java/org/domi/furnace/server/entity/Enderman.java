// 바닐라 EnderMan: 쳐다보면 화나고(조각된 호박 머리는 예외) 순간이동으로 쫓는다. 블록을 집어 들고 놓으며, 물·비에 피해를 입고, 피하면 텔레포트.
// TODO: 낮에 움직이지 않을 때의 텔레포트 외 입자, 장비 드롭 외 소지 블록 전리품 문맥 일부.
package org.domi.furnace.server.entity;

import java.util.EnumSet;
import java.util.UUID;
import org.domi.furnace.server.attribute.Attribute;
import org.domi.furnace.server.attribute.AttributeMap;
import org.domi.furnace.server.attribute.AttributeModifier;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockInfo;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Clip;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.ai.Goal;
import org.domi.furnace.server.entity.ai.goal.*;
import org.domi.furnace.server.entity.ai.targeting.TargetingConditions;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.protocol.play.SetEntityData;

public class Enderman extends Monster implements NeutralMob {
    public static final int DATA_CARRY_STATE = 16, DATA_CREEPY = 17, DATA_STARED_AT = 18;
    private static final String SPEED_MODIFIER_ATTACKING_ID = "minecraft:attacking";
    private static final AttributeModifier SPEED_MODIFIER_ATTACKING = new AttributeModifier(SPEED_MODIFIER_ATTACKING_ID, 0.15F, AttributeModifier.ADD_VALUE);
    private int lastStareSound = Integer.MIN_VALUE;
    private int targetChangeTime;
    private int remainingPersistentAngerTime;
    private UUID persistentAngerTarget;

    public Enderman(ChunkLevel level, int id) {
        super(EntityType.ENDERMAN, level, id);
        setPathfindingMalus(org.domi.furnace.server.entity.ai.navigation.PathType.WATER, -1.0F);
    }

    @Override
    protected void createAttributes(AttributeMap map) {
        super.createAttributes(map);
        map.add(Attribute.MAX_HEALTH, 40.0).add(Attribute.MOVEMENT_SPEED, 0.3F).add(Attribute.ATTACK_DAMAGE, 7.0).add(Attribute.FOLLOW_RANGE, 64.0)
                .add(Attribute.STEP_HEIGHT, 1.0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new EndermanFreezeWhenLookedAt(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, false));
        goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 1.0, 0.0F));
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, 8.0F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        goalSelector.addGoal(10, new EndermanLeaveBlockGoal(this));
        goalSelector.addGoal(11, new EndermanTakeBlockGoal(this));
        targetSelector.addGoal(1, new EndermanLookForPlayerGoal(this, this::isAngryAt));
        targetSelector.addGoal(2, new HurtByTargetGoal(this));
        targetSelector.addGoal(3, new NearestAttackableTargetGoal(this, Endermite.class, true, false));
        targetSelector.addGoal(4, new ResetUniversalAngerTargetGoal<>(this, false));
    }

    @Override
    public boolean setTarget(Targetable t) {
        if (!super.setTarget(t)) return false;
        var speed = attributes().get(Attribute.MOVEMENT_SPEED);
        if (t == null) {
            targetChangeTime = 0;
            entityData.set(DATA_CREEPY, false);
            entityData.set(DATA_STARED_AT, false);
            speed.removeModifier(SPEED_MODIFIER_ATTACKING_ID);
        } else {
            targetChangeTime = tickCount;
            entityData.set(DATA_CREEPY, true);
            if (!speed.hasModifier(SPEED_MODIFIER_ATTACKING_ID)) speed.addOrReplaceTransientModifier(SPEED_MODIFIER_ATTACKING);
        }
        return true;
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_CARRY_STATE, SetEntityData.SERIALIZER_OPTIONAL_BLOCK_STATE, null);
        entityData.define(DATA_CREEPY, SetEntityData.SERIALIZER_BOOLEAN, false);
        entityData.define(DATA_STARED_AT, SetEntityData.SERIALIZER_BOOLEAN, false);
    }

    @Override public void startPersistentAngerTimer() { setRemainingPersistentAngerTime(20 + random.nextInt(20)); }

    @Override public void setRemainingPersistentAngerTime(int t) { remainingPersistentAngerTime = t; }

    @Override public int getRemainingPersistentAngerTime() { return remainingPersistentAngerTime; }

    @Override public void setPersistentAngerTarget(UUID u) { persistentAngerTarget = u; }

    @Override public UUID getPersistentAngerTarget() { return persistentAngerTarget; }

    @Override public boolean canAttackTarget(Targetable t) { return canAttackType(t); }

    public void playStareSound() {
        if (tickCount >= lastStareSound + 400) {
            lastStareSound = tickCount;
            if (!isSilent()) level.playSound(null, x(), eyeY(), z(), "entity.enderman.stare", soundSource(), 2.5F, 1.0F);
        }
    }

    @Override
    protected void addAdditionalSaveData(NbtCompound tag) {
        super.addAdditionalSaveData(tag);
        Integer st = getCarriedBlock();
        if (st != null) {
            NbtCompound s = new NbtCompound();
            s.putString("Name", Blocks_name(st));
            tag.putCompound("carriedBlockState", s);
        }
        addPersistentAngerSaveData(tag);
    }

    private static String Blocks_name(int state) { return org.domi.furnace.server.block.Blocks.get().nameOfState(state); }

    @Override
    protected void readAdditionalSaveData(NbtCompound tag) {
        super.readAdditionalSaveData(tag);
        Integer st = null;
        if (tag.tags().get("carriedBlockState") instanceof NbtCompound c && c.tags().get("Name") instanceof String n) {
            int block = org.domi.furnace.server.block.Blocks.get().block(n);
            if (block >= 0) {
                int d = org.domi.furnace.server.block.Blocks.get().defaultState(block);
                if (!BlockInfo.get().isAir(d)) st = d;
            }
        }
        setCarriedBlock(st);
        readPersistentAngerSaveData(tag);
    }

    /** EnderMan.isLookingAtMe(player): 조각된 호박을 쓰지 않았고 시선이 나를 향하며 막히지 않았다. */
    boolean isLookingAtMe(Player p) {
        ItemStack head = p.inventory().get(5);
        if (!head.isEmpty() && org.domi.furnace.server.item.ItemTools.itemName(head).equals("minecraft:carved_pumpkin")) return false;
        double[] view = Clip.viewVector(p.pitch(), p.yaw());
        double dx = x() - p.x(), dy = eyeY() - p.eyeY(), dz = z() - p.z();
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        dx /= len;
        dy /= len;
        dz /= len;
        double dot = view[0] * dx + view[1] * dy + view[2] * dz;
        if (!(dot > 1.0 - 0.025 / len)) return false;
        return Clip.clipCollider(level, org.domi.furnace.server.block.CollisionContext.of(p), p.x(), p.eyeY(), p.z(), x(), eyeY(), z()).miss();   // player.hasLineOfSight(this): 플레이어 문맥
    }

    @Override
    protected void aiStep() {
        jumping = false;
        updatePersistentAnger(level.gameRule("universalAnger"));
        super.aiStep();
    }

    @Override public boolean isSensitiveToWater() { return true; }

    @Override
    protected void customServerAiStep() {
        if (level.isDay() && tickCount >= targetChangeTime + 600) {
            float f = getLightLevelDependentMagicValue();
            if (f > 0.5F && level.canSeeSky(blockPosition()) && random.nextFloat() * 30.0F < (f - 0.4F) * 2.0F) {
                setTarget(null);
                teleport();
            }
        }
        super.customServerAiStep();
    }

    protected boolean teleport() {
        if (isAlive()) {
            double nx = x() + (random.nextDouble() - 0.5) * 64.0;
            double ny = y() + (random.nextInt(64) - 32);
            double nz = z() + (random.nextDouble() - 0.5) * 64.0;
            return teleportTowards(nx, ny, nz);
        }
        return false;
    }

    boolean teleportTowardsEntity(Entity e) {
        double dx = x() - e.x(), dy = y() + bbHeight() * 0.5 - (e.y() + e.bbHeight() * 0.5), dz = z() - e.z();
        return teleportTowardsVec(dx, dy, dz);
    }

    boolean teleportTowardsPlayer(Player p) {
        double dx = x() - p.x(), dy = y() + bbHeight() * 0.5 - (p.y() + p.bbHeight() * 0.5), dz = z() - p.z();
        return teleportTowardsVec(dx, dy, dz);
    }

    private boolean teleportTowardsVec(double dx, double dy, double dz) {
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        dx /= len;
        dy /= len;
        dz /= len;
        double nx = x() + (random.nextDouble() - 0.5) * 8.0 - dx * 16.0;
        double ny = y() + (random.nextInt(16) - 8) - dy * 16.0;
        double nz = z() + (random.nextDouble() - 0.5) * 8.0 - dz * 16.0;
        return teleportTowards(nx, ny, nz);
    }

    private boolean teleportTowards(double nx, double ny, double nz) {
        Pos p = new Pos((int) Math.floor(nx), (int) Math.floor(ny), (int) Math.floor(nz));
        int py = p.y();
        while (py > level.minBuildHeight() && !BlockInfo.get().blocksMotion(level.getBlockState(new Pos(p.x(), py, p.z())))) py--;
        Pos at = new Pos(p.x(), py, p.z());
        int state = level.getBlockState(at);
        boolean solid = BlockInfo.get().blocksMotion(state);
        boolean water = !org.domi.furnace.server.block.Fluids.isEmpty(level.getFluidState(at)) && org.domi.furnace.server.block.Fluids.isWater(level.getFluidState(at));
        if (solid && !water) {
            double ox = x(), oy = y(), oz = z();
            boolean ok = randomTeleport(nx, ny, nz, true);
            if (ok) level.gameEvent(org.domi.furnace.server.world.gameevent.GameEvent.TELEPORT, new Vec3(ox, oy, oz), org.domi.furnace.server.world.gameevent.GameEventContext.of(this));   // cjt:226 원래 위치에서
            if (ok && !isSilent()) {
                level.playSound(null, ox, oy, oz, "entity.enderman.teleport", soundSource(), 1.0F, 1.0F);
                playSound("entity.enderman.teleport", 1.0F, 1.0F);
            }
            return ok;
        }
        return false;
    }

    @Override
    public String getAmbientSound() { return isCreepy() ? "entity.enderman.scream" : "entity.enderman.ambient"; }

    @Override protected String getHurtSound(Damage source) { return "entity.enderman.hurt"; }

    @Override protected String getDeathSound() { return "entity.enderman.death"; }

    @Override
    protected void dropCustomDeathLoot(Damage source, boolean byPlayer) {
        super.dropCustomDeathLoot(source, byPlayer);
        Integer st = getCarriedBlock();
        if (st != null) {
            for (ItemStack s : org.domi.furnace.server.block.BlockBehaviors.of(st).getDrops(st, level, blockPosition(), null, null, ItemStack.EMPTY)) spawnAtLocation(s);
        }
    }

    public void setCarriedBlock(Integer state) { entityData.set(DATA_CARRY_STATE, state); }

    public Integer getCarriedBlock() { return (Integer) entityData.get(DATA_CARRY_STATE); }

    @Override
    public boolean hurtBy(Damage source, float amount, Targetable attacker) {
        if (isInvulnerableTo(source)) return false;
        // 바닐라 hurt: is_projectile 태그 피해이거나 직접 원인이 물약이면 맞지 않고 최대 64번 텔레포트를 시도한다. 맑은 물 물약은 피해도 받는다.
        boolean potion = directCause() instanceof ThrownPotion;
        if (potion || org.domi.furnace.server.entity.damage.DamageType.keyHasTag(source.type(), "is_projectile")) {
            boolean hurt = potion && Consuming.isWaterPotion(((ThrownPotion) directCause()).getItem()) && super.hurtBy(source, amount, attacker);
            for (int i = 0; i < 64; i++) if (teleport()) return true;
            return hurt;
        }
        boolean r = super.hurtBy(source, amount, attacker);
        if (!(attacker instanceof LivingEntity || attacker instanceof Player) && random.nextInt(10) != 0) teleport();
        return r;
    }

    public boolean isCreepy() { return (Boolean) entityData.get(DATA_CREEPY); }

    public boolean hasBeenStaredAt() { return (Boolean) entityData.get(DATA_STARED_AT); }

    public void setBeingStaredAt() { entityData.set(DATA_STARED_AT, true); }

    // ------------------------------------------------------------------ 목표

    static final class EndermanFreezeWhenLookedAt extends Goal {
        private final Enderman enderman;
        private Targetable target;

        EndermanFreezeWhenLookedAt(Enderman e) {
            this.enderman = e;
            setFlags(EnumSet.of(Flag.JUMP, Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            target = enderman.getTarget();
            if (!(target instanceof Player p)) return false;
            double dx = enderman.x() - p.x(), dy = enderman.y() - p.y(), dz = enderman.z() - p.z();
            return dx * dx + dy * dy + dz * dz > 256.0 ? false : enderman.isLookingAtMe(p);
        }

        @Override public void start() { enderman.getNavigation().stop(); }

        @Override
        public void tick() { enderman.getLookControl().setLookAt(target.x(), target.eyeY(), target.z()); }
    }

    static final class EndermanLeaveBlockGoal extends Goal {
        private final Enderman enderman;

        EndermanLeaveBlockGoal(Enderman e) { this.enderman = e; }

        @Override
        public boolean canUse() {
            if (enderman.getCarriedBlock() == null) return false;
            return !enderman.level().gameRule("mobGriefing") ? false : enderman.random().nextInt(reducedTickDelay(2000)) == 0;
        }

        @Override
        public void tick() {
            var r = enderman.random();
            var level = enderman.level();
            int x = (int) Math.floor(enderman.x() - 1.0 + r.nextDouble() * 2.0);
            int y = (int) Math.floor(enderman.y() + r.nextDouble() * 2.0);
            int z = (int) Math.floor(enderman.z() - 1.0 + r.nextDouble() * 2.0);
            Pos pos = new Pos(x, y, z);
            int here = level.getBlockState(pos);
            Pos below = pos.below();
            int belowState = level.getBlockState(below);
            Integer carried = enderman.getCarriedBlock();
            if (carried != null) {
                int placed = carried;   // TODO(이웃 모양 갱신): Block.updateFromNeighbourShapes
                // CraftBukkit: 놓기 전에 EntityChangeBlockEvent — 막으면 들고 있는 채로 둔다
                if (canPlaceBlock(level, pos, placed, here, belowState, below) && B.entityChange(level, enderman, pos, placed)) {
                    level.setBlock(pos, placed, 3, 512);
                    level.gameEventFrom("block_place", pos, enderman, placed);
                    enderman.setCarriedBlock(null);
                }
            }
        }

        private boolean canPlaceBlock(ChunkLevel level, Pos pos, int carried, int here, int belowState, Pos below) {
            return BlockInfo.get().isAir(here) && !BlockInfo.get().isAir(belowState) && !B.is(belowState, "minecraft:bedrock")
                    && org.domi.furnace.server.block.BlockBehaviors.of(carried).canSurvive(carried, level, pos)
                    && level.getEntities(enderman, new AABB(pos.x(), pos.y(), pos.z(), pos.x() + 1.0, pos.y() + 1.0, pos.z() + 1.0), e -> true).isEmpty();
        }
    }

    static final class EndermanTakeBlockGoal extends Goal {
        private final Enderman enderman;

        EndermanTakeBlockGoal(Enderman e) { this.enderman = e; }

        @Override
        public boolean canUse() {
            if (enderman.getCarriedBlock() != null) return false;
            return !enderman.level().gameRule("mobGriefing") ? false : enderman.random().nextInt(reducedTickDelay(20)) == 0;
        }

        @Override
        public void tick() {
            var r = enderman.random();
            var level = enderman.level();
            int x = (int) Math.floor(enderman.x() - 2.0 + r.nextDouble() * 4.0);
            int y = (int) Math.floor(enderman.y() + r.nextDouble() * 3.0);
            int z = (int) Math.floor(enderman.z() - 2.0 + r.nextDouble() * 4.0);
            Pos pos = new Pos(x, y, z);
            int state = level.getBlockState(pos);
            double sx = Math.floor(enderman.x()) + 0.5, sz = Math.floor(enderman.z()) + 0.5;
            var hit = Clip.clip(level, sx, y + 0.5, sz, x + 0.5, y + 0.5, z + 0.5, Clip.FluidMode.NONE);
            boolean same = hit.pos().equals(pos) && !hit.miss() || (hit.miss() && hit.pos().equals(pos));
            // CraftBukkit(+Paper 새 상태 수정): 집기 전에 EntityChangeBlockEvent(새 상태 = 그 칸의 유체) — 막으면 집지 않는다
            if (B.isTag(state, "minecraft:enderman_holdable") && same && B.entityChange(level, enderman, pos, B.fluidLeftover(state))) {
                level.removeBlock(pos, false);
                level.gameEventFrom("block_destroy", pos, enderman, state);
                enderman.setCarriedBlock(org.domi.furnace.server.block.Blocks.get().defaultState(org.domi.furnace.server.block.Blocks.get().blockOf(state)));
            }
        }
    }

    static final class EndermanLookForPlayerGoal extends NearestAttackableTargetGoal {
        private final Enderman enderman;
        private Player pendingTarget;
        private int aggroTime;
        private int teleportTime;
        private final TargetingConditions startAggroTargetConditions;
        private final TargetingConditions continueAggroTargetConditions = TargetingConditions.forCombat().ignoreLineOfSight();
        private final java.util.function.Predicate<Targetable> isAngerInducing;

        EndermanLookForPlayerGoal(Enderman e, java.util.function.Predicate<Targetable> angryAt) {
            super(e, Player.class, 10, false, false, angryAt);
            this.enderman = e;
            this.isAngerInducing = t -> (t instanceof Player pl && e.isLookingAtMe(pl) || angryAt.test(t));
            this.startAggroTargetConditions = TargetingConditions.forCombat().range(getFollowDistance()).selector(isAngerInducing);
        }

        @Override
        public boolean canUse() {
            pendingTarget = enderman.level().getNearestPlayer(startAggroTargetConditions, enderman, enderman.x(), enderman.eyeY(), enderman.z());
            return pendingTarget != null;
        }

        @Override
        public void start() {
            aggroTime = adjustedTickDelay(5);
            teleportTime = 0;
            enderman.setBeingStaredAt();
        }

        @Override
        public void stop() {
            pendingTarget = null;
            super.stop();
        }

        @Override
        public boolean canContinueToUse() {
            if (pendingTarget != null) {
                if (!isAngerInducing.test(pendingTarget)) return false;
                enderman.getLookControl().setLookAtPlayer(pendingTarget, 10.0F, 10.0F);
                return true;
            }
            if (target != null) {
                if (continueAggroTargetConditions.test(enderman, target)) return true;
            }
            return super.canContinueToUse();
        }

        @Override
        public void tick() {
            if (enderman.getTarget() == null) super.setTarget(null);
            if (pendingTarget != null) {
                if (--aggroTime <= 0) {
                    target = pendingTarget;
                    pendingTarget = null;
                    super.start();
                }
            } else {
                if (target != null && !enderman.isPassenger()) {
                    if (target instanceof Player p && enderman.isLookingAtMe(p)) {
                        double dx = target.x() - enderman.x(), dy = target.y() - enderman.y(), dz = target.z() - enderman.z();
                        if (dx * dx + dy * dy + dz * dz < 16.0) enderman.teleport();
                        teleportTime = 0;
                    } else {
                        double dx = target.x() - enderman.x(), dy = target.y() - enderman.y(), dz = target.z() - enderman.z();
                        if (dx * dx + dy * dy + dz * dz > 256.0 && teleportTime++ >= adjustedTickDelay(30) && target instanceof Player p && enderman.teleportTowardsPlayer(p)) {
                            teleportTime = 0;
                        }
                    }
                }
                super.tick();
            }
        }
    }
}
