// 바닐라 WitherBoss: 위더. 소환 직후 220틱 동안 무적으로 커지다 폭발(7)하고, 이후 머리 셋(가운데는 주 대상, 양옆은 근처의 다른 대상)에서 위더 해골을 쏜다.
// 체력이 절반 이하면 화살·해골에 면역이고 블록을 부수며 돌진한다. 죽으면 네더 스타를 남긴다. 보스 바는 보는 플레이어에게 보낸다.
// TODO: 소환 틀(WitherSkullBlock.checkSpawn), 블록을 못 부수는 머리 회전 애니메이션은 클라이언트가 계산한다.
package org.domi.furnace.server.entity;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.attribute.Attribute;
import org.domi.furnace.server.attribute.AttributeMap;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.effect.MobEffect;
import org.domi.furnace.server.effect.MobEffectInstance;
import org.domi.furnace.server.entity.ai.Goal;
import org.domi.furnace.server.entity.ai.control.FlyingMoveControl;
import org.domi.furnace.server.entity.ai.goal.*;
import org.domi.furnace.server.entity.ai.navigation.FlyingPathNavigation;
import org.domi.furnace.server.entity.ai.navigation.PathNavigation;
import org.domi.furnace.server.entity.ai.targeting.TargetingConditions;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.protocol.play.BossEvent;
import org.domi.furnace.server.net.protocol.play.SetEntityData;

public class WitherBoss extends Monster implements RangedAttackMob {
    public static final int DATA_TARGET_A = 16, DATA_TARGET_B = 17, DATA_TARGET_C = 18, DATA_ID_INV = 19;
    private static final int[] TARGET_IDS = {DATA_TARGET_A, DATA_TARGET_B, DATA_TARGET_C};
    private static final int INVULNERABLE_TICKS = 220;
    private static final java.util.function.Predicate<Targetable> LIVING_SELECTOR =
            t -> !(t instanceof Entity e && e.type().is("minecraft:wither_friends")) && t.canBeSeenAsEnemy();
    private static final TargetingConditions TARGETING_CONDITIONS = TargetingConditions.forCombat().range(20.0).selector(LIVING_SELECTOR);

    private final int[] nextHeadUpdate = new int[2];
    private final int[] idleHeadUpdates = new int[2];
    private int destroyBlocksTick;
    private final UUID bossId = UUID.randomUUID();
    private final List<Player> bossViewers = new ArrayList<>();

    public WitherBoss(ChunkLevel level, int id) {
        super(EntityType.WITHER, level, id);
        moveControl = new FlyingMoveControl(this, 10, false);
        setHealth(maxHealth());
        xpReward = 50;
    }

    @Override
    protected PathNavigation createNavigation(ChunkLevel level) {
        FlyingPathNavigation nav = new FlyingPathNavigation(this, level);
        nav.setCanOpenDoors(false);
        nav.setCanFloat(true);
        nav.setCanPassDoors(true);
        return nav;
    }

    @Override
    protected void createAttributes(AttributeMap map) {
        super.createAttributes(map);
        map.add(Attribute.MAX_HEALTH, 300.0).add(Attribute.MOVEMENT_SPEED, 0.6F).add(Attribute.FLYING_SPEED, 0.6F)
                .add(Attribute.FOLLOW_RANGE, 40.0).add(Attribute.ARMOR, 4.0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new WitherDoNothingGoal());
        goalSelector.addGoal(2, new RangedAttackGoal(this, 1.0, 40, 20.0F));
        goalSelector.addGoal(5, new WaterAvoidingRandomFlyingGoal(this, 1.0));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, 8.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal(this, LivingEntity.class, 0, false, false, LIVING_SELECTOR));
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        for (int id : TARGET_IDS) entityData.define(id, SetEntityData.SERIALIZER_INT, 0);
        entityData.define(DATA_ID_INV, SetEntityData.SERIALIZER_INT, 0);
    }

    @Override
    protected void addAdditionalSaveData(NbtCompound tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Invul", getInvulnerableTicks());
    }

    @Override
    protected void readAdditionalSaveData(NbtCompound tag) {
        super.readAdditionalSaveData(tag);
        setInvulnerableTicks(tag.getIntOr0("Invul"));
    }

    @Override public String getAmbientSound() { return "entity.wither.ambient"; }

    @Override protected String getHurtSound(Damage source) { return "entity.wither.hurt"; }

    @Override protected String getDeathSound() { return "entity.wither.death"; }

    public int getInvulnerableTicks() { return (Integer) entityData.get(DATA_ID_INV); }

    public void setInvulnerableTicks(int t) { entityData.set(DATA_ID_INV, t); }

    public int getAlternativeTarget(int head) { return (Integer) entityData.get(TARGET_IDS[head]); }

    public void setAlternativeTarget(int head, int id) { entityData.set(TARGET_IDS[head], id); }

    /** 체력이 절반 이하인가(isPowered). */
    public boolean isPowered() { return health() <= maxHealth() / 2.0F; }

    /** makeInvulnerable: 소환 직후 상태. */
    public void makeInvulnerable() {
        setInvulnerableTicks(INVULNERABLE_TICKS);
        broadcastBossProgress(0.0F);
        setHealth(maxHealth() / 3.0F);
    }

    // ------------------------------------------------------------------ 보스 바

    private Text bossName() { return Text.translatable("entity.minecraft.wither"); }

    @Override
    public void onStartSeenBy(Player p) {
        bossViewers.add(p);
        p.send(BossEvent.add(bossId, bossName(), health() / maxHealth(), 5, 0, 1));   // 보라, 화면 어둡게
    }

    @Override
    public void onStopSeenBy(Player p) {
        bossViewers.remove(p);
        p.send(BossEvent.remove(bossId));
    }

    private float lastBossProgress = -1.0F;

    private void broadcastBossProgress(float progress) {
        if (progress == lastBossProgress) return;
        lastBossProgress = progress;
        for (Player p : bossViewers) if (!p.isDisconnected()) p.send(BossEvent.progress(bossId, progress));
    }

    // ------------------------------------------------------------------ 움직임·AI

    private double headX(int i) {
        if (i <= 0) return x();
        float a = (yBodyRot + 180 * (i - 1)) * (float) (Math.PI / 180.0);
        return x() + org.domi.furnace.server.entity.ai.control.Mth.cos(a) * 1.3 * 1.0;
    }

    private double headY(int i) { return y() + (i <= 0 ? 3.0F : 2.2F); }

    private double headZ(int i) {
        if (i <= 0) return z();
        float a = (yBodyRot + 180 * (i - 1)) * (float) (Math.PI / 180.0);
        return z() + org.domi.furnace.server.entity.ai.control.Mth.sin(a) * 1.3 * 1.0;
    }

    @Override
    protected void aiStep() {
        double mx = vx(), my = vy() * 0.6, mz = vz();
        if (getAlternativeTarget(0) > 0) {
            Targetable t = byId(getAlternativeTarget(0));
            if (t != null) {
                if (y() < t.y() || !isPowered() && y() < t.y() + 5.0) {
                    my = Math.max(0.0, my);
                    my += 0.3 - my * 0.6F;
                }
                double dx = t.x() - x(), dz = t.z() - z();
                if (dx * dx + dz * dz > 9.0) {
                    double len = Math.sqrt(dx * dx + dz * dz);
                    mx += dx / len * 0.3 - mx * 0.6;
                    mz += dz / len * 0.3 - mz * 0.6;
                }
            }
        }
        setDeltaMovement(mx, my, mz);
        if (mx * mx + my * my + mz * mz > 0.05) setYRot((float) (org.domi.furnace.server.entity.ai.control.Mth.atan2(mz, mx) * (180.0F / (float) Math.PI)) - 90.0F);
        super.aiStep();
        // 머리 위치 입자는 클라이언트 몫이지만 엔티티 난수는 같은 수만큼 쓴다(가우시안 3 + 조건부)
        boolean powered = isPowered();
        for (int i = 0; i < 3; i++) {
            random.nextGaussian();
            random.nextGaussian();
            random.nextGaussian();
            if (powered && level.random().nextInt(4) == 0) {
                random.nextGaussian();
                random.nextGaussian();
                random.nextGaussian();
            }
        }
        if (getInvulnerableTicks() > 0) {
            for (int i = 0; i < 3; i++) {
                random.nextGaussian();
                random.nextFloat();
                random.nextGaussian();
            }
        }
    }

    @Override
    protected void customServerAiStep() {
        if (getInvulnerableTicks() > 0) {
            int i = getInvulnerableTicks() - 1;
            broadcastBossProgress(1.0F - i / 220.0F);
            if (i <= 0) {
                level.explode(this, x(), y() + bbHeight() * 0.85F, z(), 7.0F, false, level.destroyType("MOB"));
                if (!isSilent()) level.globalLevelEvent(1023, blockPosition(), 0);
            }
            setInvulnerableTicks(i);
            if (tickCount % 10 == 0) heal(10.0F);
        } else {
            super.customServerAiStep();
            for (int i = 1; i < 3; i++) {
                if (tickCount >= nextHeadUpdate[i - 1]) {
                    nextHeadUpdate[i - 1] = tickCount + 10 + random.nextInt(10);
                    int diff = level.difficulty();
                    if ((diff == 2 || diff == 3) && idleHeadUpdates[i - 1]++ > 15) {
                        double tx = x() - 10.0 + random.nextDouble() * 20.0, ty = y() - 5.0 + random.nextDouble() * 10.0, tz = z() - 10.0 + random.nextDouble() * 20.0;
                        performRangedAttack(i + 1, tx, ty, tz, true);
                        idleHeadUpdates[i - 1] = 0;
                    }
                    int id = getAlternativeTarget(i);
                    if (id > 0) {
                        Targetable t = byId(id);
                        if (t != null && canAttackType(t) && t.isAlive() && distSqr(t) <= 900.0 && hasLineOfSight(t)) {
                            performRangedAttack(i + 1, t);
                            nextHeadUpdate[i - 1] = tickCount + 40 + random.nextInt(20);
                            idleHeadUpdates[i - 1] = 0;
                        } else {
                            setAlternativeTarget(i, 0);
                        }
                    } else {
                        List<Targetable> list = new ArrayList<>();
                        AABB box = boundingBox().inflate(20.0, 8.0, 20.0);
                        for (Entity e : level.getEntities(this, box, en -> en instanceof LivingEntity)) {
                            if (TARGETING_CONDITIONS.test(this, (LivingEntity) e)) list.add((LivingEntity) e);
                        }
                        level.forEachPlayerIn(box, p -> { if (TARGETING_CONDITIONS.test(this, p)) list.add(p); });
                        if (!list.isEmpty()) setAlternativeTarget(i, list.get(random.nextInt(list.size())).id());
                    }
                }
            }
            Targetable target = getTarget();
            setAlternativeTarget(0, target != null ? target.id() : 0);
            if (destroyBlocksTick > 0) {
                destroyBlocksTick--;
                if (destroyBlocksTick == 0 && level.gameRule("mobGriefing")) {
                    boolean any = false;
                    int j = (int) Math.floor(bbWidth() / 2.0F + 1.0F), k = (int) Math.floor(bbHeight());
                    int bx = (int) Math.floor(x()), by = (int) Math.floor(y()), bz = (int) Math.floor(z());
                    for (int px = bx - j; px <= bx + j; px++) {
                        for (int py = by; py <= by + k; py++) {
                            for (int pz = bz - j; pz <= bz + j; pz++) {
                                Pos pos = new Pos(px, py, pz);
                                int s = level.getBlockState(pos);
                                if (canDestroy(s)) any = level.destroyBlock(pos, true) || any;
                            }
                        }
                    }
                    if (any) level.levelEvent(null, 1022, blockPosition(), 0);
                }
            }
            if (tickCount % 20 == 0) heal(1.0F);
            broadcastBossProgress(health() / maxHealth());
        }
    }

    public static boolean canDestroy(int state) { return !B.isAir(state) && !B.isTag(state, "minecraft:wither_immune"); }

    private double distSqr(Targetable t) {
        double dx = t.x() - x(), dy = t.y() - y(), dz = t.z() - z();
        return dx * dx + dy * dy + dz * dz;
    }

    private Targetable byId(int id) {
        Entity e = level.entities().get(id);
        if (e instanceof Targetable t) return t;
        Targetable[] found = {null};
        level.forEachPlayer(p -> { if (p.id() == id) found[0] = p; });
        return found[0];
    }

    // ------------------------------------------------------------------ 공격

    @Override
    public void performRangedAttack(Targetable target, float power) { performRangedAttack(0, target); }

    private void performRangedAttack(int head, Targetable target) {
        performRangedAttack(head, target.x(), target.y() + target.bbHeight() * 0.5, target.z(), head == 0 && random.nextFloat() < 0.001F);
    }

    private void performRangedAttack(int head, double tx, double ty, double tz, boolean dangerous) {
        if (!isSilent()) level.levelEvent(null, 1024, blockPosition(), 0);
        double hx = headX(head), hy = headY(head), hz = headZ(head);
        WitherSkull skull = new WitherSkull(level, level.nextEntityId());
        skull.launchFrom(this, tx - hx, ty - hy, tz - hz);
        skull.setOwner(this);
        if (dangerous) skull.setDangerous(true);
        skull.setPos(hx, hy, hz);
        level.addFreshEntity(skull);
    }

    // ------------------------------------------------------------------ 피해·죽음

    @Override
    public boolean hurtBy(Damage source, float amount, Targetable attacker) {
        if (isInvulnerableTo(source)) return false;
        if (source.type().equals("drown") || attacker instanceof WitherBoss) return false;
        if (getInvulnerableTicks() > 0 && !source.bypassesInvulnerability()) return false;
        if (isPowered()) {
            Entity direct = directCause();
            if (direct != null && (direct.type().is("minecraft:arrows") || direct instanceof WitherSkull)) return false;
        }
        if (attacker instanceof Entity e && e.type().is("minecraft:wither_friends")) return false;
        if (destroyBlocksTick <= 0) destroyBlocksTick = 20;
        for (int i = 0; i < idleHeadUpdates.length; i++) idleHeadUpdates[i] += 3;
        return super.hurtBy(source, amount, attacker);
    }

    @Override
    protected void dropCustomDeathLoot(Damage source, boolean byPlayer) {
        super.dropCustomDeathLoot(source, byPlayer);
        ItemEntity star = spawnAtLocation(new ItemStack(org.domi.furnace.server.item.ItemContext.id("minecraft:item", "minecraft:nether_star"), 1));
        if (star != null) star.setExtendedLifetime();
    }

    @Override
    public void tick() {
        if (level.difficulty() == 0 && isAlive()) {   // checkDespawn: 평화로움이면 사라진다
            discard();
            return;
        }
        noActionTime = 0;
        super.tick();
    }

    @Override public boolean removeWhenFarAway(double distSqr) { return false; }

    @Override
    public boolean canBeAffected(MobEffectInstance i) { return i.effect() != MobEffect.WITHER && super.canBeAffected(i); }

    @Override public boolean fireImmune() { return true; }

    private final class WitherDoNothingGoal extends Goal {
        WitherDoNothingGoal() { setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP, Flag.LOOK)); }

        @Override public boolean canUse() { return getInvulnerableTicks() > 0; }
    }
}
