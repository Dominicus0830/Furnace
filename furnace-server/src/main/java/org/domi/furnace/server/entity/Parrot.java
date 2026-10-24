// 바닐라 Parrot: 날아다니는 길들이기 새. 씨앗으로 길들이고(1/10), 쿠키는 독(900틱)이라 죽는다. 근처 몹 소리를 가끔 흉내 낸다. 중력 대신 낙하 감속.
// TODO(어깨): 주인 어깨에 앉기(LandOnOwnersShoulderGoal)·플레이어 어깨 엔티티 저장. TODO(주크박스 파티·날개짓 소리).
package org.domi.furnace.server.entity;

import java.util.List;
import org.domi.furnace.server.attribute.Attribute;
import org.domi.furnace.server.attribute.AttributeMap;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.effect.MobEffect;
import org.domi.furnace.server.effect.MobEffectInstance;
import org.domi.furnace.server.entity.ai.control.FlyingMoveControl;
import org.domi.furnace.server.entity.ai.goal.*;
import org.domi.furnace.server.entity.ai.navigation.FlyingPathNavigation;
import org.domi.furnace.server.entity.ai.navigation.PathNavigation;
import org.domi.furnace.server.entity.ai.navigation.PathType;
import org.domi.furnace.server.entity.ai.util.DefaultRandomPos;
import org.domi.furnace.server.item.ItemTags;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.protocol.play.SetEntityData;

public class Parrot extends TamableAnimal {
    public static final int DATA_VARIANT_ID = 19;
    /** 흉내 낼 수 있는 몹 → 소리(cave_spider 는 거미 소리). */
    private static final java.util.Map<String, String> IMITATIONS = new java.util.LinkedHashMap<>();

    static {
        for (String n : new String[] {"blaze", "bogged", "breeze", "creeper", "drowned", "elder_guardian", "ender_dragon", "endermite", "evoker", "ghast", "guardian",
                "hoglin", "husk", "illusioner", "magma_cube", "phantom", "piglin", "piglin_brute", "pillager", "ravager", "shulker", "silverfish", "skeleton", "slime",
                "spider", "stray", "vex", "vindicator", "warden", "witch", "wither", "wither_skeleton", "zoglin", "zombie", "zombie_villager"}) {
            IMITATIONS.put("minecraft:" + n, "entity.parrot.imitate." + n);
        }
        IMITATIONS.put("minecraft:cave_spider", "entity.parrot.imitate.spider");
    }

    public Parrot(ChunkLevel level, int id) {
        super(EntityType.PARROT, level, id);
        this.moveControl = new FlyingMoveControl(this, 10, false);
        setPathfindingMalus(PathType.DANGER_FIRE, -1.0F);
        setPathfindingMalus(PathType.DAMAGE_FIRE, -1.0F);
        setPathfindingMalus(PathType.COCOA, -1.0F);
    }

    @Override
    public AgeableMob.GroupData finalizeSpawn(org.domi.furnace.server.block.RandomSource r, DifficultyInstance difficulty, AgeableMob.GroupData data) {
        setVariant(r.nextInt(5));
        if (data == null) data = new AgeableMob.GroupData(false);
        return super.finalizeSpawn(r, difficulty, data);
    }

    @Override
    public boolean isBaby() { return false; }

    @Override
    protected void createAttributes(AttributeMap map) {
        super.createAttributes(map);
        map.add(Attribute.MAX_HEALTH, 6.0).add(Attribute.FLYING_SPEED, 0.4F).add(Attribute.MOVEMENT_SPEED, 0.2F).add(Attribute.ATTACK_DAMAGE, 3.0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new TamablePanicGoal(this, 1.25));
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new LookAtPlayerGoal(this, 8.0F));
        goalSelector.addGoal(2, new SitWhenOrderedToDoGoal(this));
        goalSelector.addGoal(2, new FollowOwnerGoal(this, 1.0, 5.0F, 1.0F));
        goalSelector.addGoal(2, new ParrotWanderGoal(this, 1.0));
        goalSelector.addGoal(3, new FollowMobGoal(this, 1.0, 3.0F, 7.0F));
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
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_VARIANT_ID, SetEntityData.SERIALIZER_INT, 0);
    }

    public int getVariant() { return Math.floorMod((Integer) entityData.get(DATA_VARIANT_ID), 5); }

    public void setVariant(int v) { entityData.set(DATA_VARIANT_ID, v); }

    @Override
    protected void addAdditionalSaveData(NbtCompound tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Variant", getVariant());
    }

    @Override
    protected void readAdditionalSaveData(NbtCompound tag) {
        super.readAdditionalSaveData(tag);
        setVariant(Math.floorMod(tag.getIntOr0("Variant"), 5));
    }

    @Override
    protected void aiStep() {
        if (level.random().nextInt(400) == 0) imitateNearbyMobs();
        super.aiStep();
        if (!onGround() && vy() < 0.0) setDeltaMovement(vx(), vy() * 0.6, vz());
    }

    private void imitateNearbyMobs() {
        if (isAlive() && !isSilent() && level.random().nextInt(2) == 0) {
            List<Mob> list = level.getEntitiesOfClass(Mob.class, boundingBox().inflate(20.0), m -> IMITATIONS.containsKey(m.type().name()));
            if (!list.isEmpty()) {
                Mob m = list.get(level.random().nextInt(list.size()));
                if (!m.isSilent()) {
                    level.playSound(null, x(), y(), z(), IMITATIONS.get(m.type().name()), "neutral", 0.7F, getPitch(level.random()));
                }
            }
        }
    }

    private static float getPitch(org.domi.furnace.server.block.RandomSource r) { return (r.nextFloat() - r.nextFloat()) * 0.2F + 1.0F; }

    @Override
    public boolean interact(Player p, int hand, ItemStack held, org.domi.furnace.server.world.Region region) {
        if (!isTame() && ItemTags.is(held, "minecraft:parrot_food")) {
            usePlayerItem(p, hand, held);
            playSound("entity.parrot.eat", 1.0F, 1.0F + (random.nextFloat() - random.nextFloat()) * 0.2F);
            if (random.nextInt(10) == 0) {
                tame(p);
                broadcastEntityEvent((byte) 7);
            } else {
                broadcastEntityEvent((byte) 6);
            }
            return true;
        }
        if (ItemTags.is(held, "minecraft:parrot_poisonous_food")) {
            usePlayerItem(p, hand, held);
            addEffect(new MobEffectInstance(MobEffect.POISON, 900));
            if (p.vitals().isCreative() || !isInvulnerable()) hurtBy(new Damage("player_attack", false, false, false), Float.MAX_VALUE, p);
            return true;
        }
        if (isTame() && !(!onGround()) && isOwnedBy(p)) {
            setOrderedToSit(!isOrderedToSit());
            setInSittingPose(isOrderedToSit());
            return true;
        }
        return super.interact(p, hand, held, region);
    }

    @Override
    public boolean isFood(ItemStack s) { return false; }

    @Override
    public boolean canMate(Animal other) { return false; }

    @Override
    public AgeableMob getBreedOffspring(ChunkLevel level, AgeableMob partner) { return null; }

    @Override
    public boolean causeFallDamage(float distance, float multiplier) { return false; }

    @Override
    public boolean hurtBy(Damage source, float amount, Targetable attacker) {
        if (isInvulnerableTo(source)) return false;
        setOrderedToSit(false);
        setInSittingPose(false);
        return super.hurtBy(source, amount, attacker);
    }

    @Override
    public float getVoicePitch() { return getPitch(random); }

    @Override
    public String getAmbientSound() {
        if (level.difficulty() != 0 && random.nextInt(1000) == 0) {
            java.util.List<String> sounds = new java.util.ArrayList<>(IMITATIONS.values());
            return sounds.get(random.nextInt(sounds.size()));
        }
        return "entity.parrot.ambient";
    }

    @Override protected String getHurtSound(Damage source) { return "entity.parrot.hurt"; }

    @Override protected String getDeathSound() { return "entity.parrot.death"; }

    /** ParrotWanderGoal: 가끔 근처 나무(잎/통나무 위 빈 칸)로, 물에서는 육지로, 아니면 무작위로 난다. */
    private static final class ParrotWanderGoal extends WaterAvoidingRandomFlyingGoal {
        ParrotWanderGoal(Parrot parrot, double speed) { super(parrot, speed); }

        @Override
        protected double[] getPosition() {
            double[] v = null;
            if (mob.isInWater()) v = DefaultRandomPos.getPos(mob, 15, 15);
            if (mob.random().nextFloat() >= probability) v = treePos();
            return v == null ? super.getPosition() : v;
        }

        private double[] treePos() {
            Pos here = mob.blockPosition();
            int x0 = (int) Math.floor(mob.x() - 3.0), x1 = (int) Math.floor(mob.x() + 3.0);
            int y0 = (int) Math.floor(mob.y() - 6.0), y1 = (int) Math.floor(mob.y() + 6.0);
            int z0 = (int) Math.floor(mob.z() - 3.0), z1 = (int) Math.floor(mob.z() + 3.0);
            var level = mob.level();
            for (int z = z0; z <= z1; z++) {
                for (int y = y0; y <= y1; y++) {
                    for (int x = x0; x <= x1; x++) {
                        Pos p = new Pos(x, y, z);
                        if (here.equals(p)) continue;
                        int below = level.getBlockState(p.below());
                        boolean tree = B.isTag(below, "minecraft:leaves") || B.isTag(below, "minecraft:logs");
                        if (tree && level.isEmptyBlock(p) && level.isEmptyBlock(p.above())) return new double[] {p.x() + 0.5, p.y(), p.z() + 0.5};
                    }
                }
            }
            return null;
        }
    }
}
