// 바닐라 Wolf: 길들이기(뼈 1/3), 앉기, 목걸이 색, 분노(NeutralMob), 구걸. 길들이면 체력 40.
// TODO: 늑대 변종(바이옴), 늑대 갑옷, 거북·해골·라마·여우 관련 목표(해당 몹 추가 시), 젖어 털기 입자, 죽음 알림.
package org.domi.furnace.server.entity;

import java.util.UUID;
import org.domi.furnace.server.attribute.Attribute;
import org.domi.furnace.server.attribute.AttributeMap;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.entity.ai.goal.*;
import org.domi.furnace.server.item.ItemTags;
import org.domi.furnace.server.item.ItemTools;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.protocol.play.SetEntityData;

public class Wolf extends TamableAnimal implements NeutralMob {
    public static final int DATA_INTERESTED_ID = 19, DATA_COLLAR_COLOR = 20, DATA_REMAINING_ANGER_TIME = 21;
    private boolean isWet, isShaking;
    private float shakeAnim, shakeAnimO;
    private UUID persistentAngerTarget;

    public Wolf(ChunkLevel level, int id) {
        super(EntityType.WOLF, level, id);
        setTame(false, false);
        setPathfindingMalus(org.domi.furnace.server.entity.ai.navigation.PathType.POWDER_SNOW, -1.0F);
        setPathfindingMalus(org.domi.furnace.server.entity.ai.navigation.PathType.DANGER_POWDER_SNOW, -1.0F);
    }

    @Override
    protected void createAttributes(AttributeMap map) {
        super.createAttributes(map);
        map.add(Attribute.MOVEMENT_SPEED, 0.3F).add(Attribute.MAX_HEALTH, 8.0).add(Attribute.ATTACK_DAMAGE, 4.0);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_INTERESTED_ID, SetEntityData.SERIALIZER_BOOLEAN, false);
        entityData.define(DATA_COLLAR_COLOR, SetEntityData.SERIALIZER_INT, DyeColor.RED.id());
        entityData.define(DATA_REMAINING_ANGER_TIME, SetEntityData.SERIALIZER_INT, 0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new FloatGoal(this));
        goalSelector.addGoal(1, new TamablePanicGoal(this, 1.5, "minecraft:panic_environmental_causes"));
        goalSelector.addGoal(2, new SitWhenOrderedToDoGoal(this));
        goalSelector.addGoal(4, new LeapAtTargetGoal(this, 0.4F));
        goalSelector.addGoal(5, new MeleeAttackGoal(this, 1.0, true));
        goalSelector.addGoal(6, new FollowOwnerGoal(this, 1.0, 10.0F, 2.0F));
        goalSelector.addGoal(7, new BreedGoal(this, 1.0));
        goalSelector.addGoal(8, new WaterAvoidingRandomStrollGoal(this, 1.0));
        goalSelector.addGoal(9, new BegGoal(this, 8.0F));
        goalSelector.addGoal(10, new LookAtPlayerGoal(this, 8.0F));
        goalSelector.addGoal(10, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new OwnerHurtByTargetGoal(this));
        targetSelector.addGoal(2, new OwnerHurtTargetGoal(this));
        targetSelector.addGoal(3, new HurtByTargetGoal(this).setAlertOthers());
        targetSelector.addGoal(4, new NearestAttackableTargetGoal(this, Player.class, 10, true, false, t -> isAngryAt(t)));
        targetSelector.addGoal(5, new NonTameRandomTargetGoal(this, Animal.class, false, t -> t instanceof Entity e && PREY.contains(e.type().name())));
        // TODO(거북·해골·라마): 6 = 땅 위 아기 거북, 7 = 해골류, 3 = 라마에게서 도망(24칸)
        targetSelector.addGoal(8, new ResetUniversalAngerTargetGoal<>(this, true));
    }

    private static final java.util.Set<String> PREY = java.util.Set.of("minecraft:sheep", "minecraft:rabbit", "minecraft:fox");

    public DyeColor getCollarColor() { return DyeColor.byId((Integer) entityData.get(DATA_COLLAR_COLOR)); }

    private void setCollarColor(DyeColor c) { entityData.set(DATA_COLLAR_COLOR, c.id()); }

    public boolean isInterested() { return (Boolean) entityData.get(DATA_INTERESTED_ID); }

    public void setIsInterested(boolean v) { entityData.set(DATA_INTERESTED_ID, v); }

    @Override
    protected void applyTamingSideEffects() {
        if (isTame()) {
            attributes().get(Attribute.MAX_HEALTH).setBaseValue(40.0);
            setHealth(40.0F);
        } else {
            attributes().get(Attribute.MAX_HEALTH).setBaseValue(8.0);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (!isWet && isInWaterRainOrBubble() ) isWet = true;
        if (isWet && !isInWaterRainOrBubble() && !isShaking && !getNavigation().isInProgress() && onGround()) {
            isShaking = true;
            shakeAnim = 0.0F;
            shakeAnimO = 0.0F;
            broadcastEntityEvent((byte) 8);
        }
        if (isShaking) {
            shakeAnimO = shakeAnim;
            shakeAnim += 0.05F;
            if (shakeAnimO >= 2.0F) {
                isWet = false;
                isShaking = false;
                shakeAnimO = 0.0F;
                shakeAnim = 0.0F;
            }
            if (shakeAnim > 0.4F && shakeAnim - 0.05F <= 0.4F) playSound("entity.wolf.shake", 0.4F, (random.nextFloat() - random.nextFloat()) * 0.2F + 1.0F);
        }
        updatePersistentAnger(level.gameRule("universalAnger"));
    }

    @Override
    public boolean hurtBy(Damage source, float amount, Targetable attacker) {
        if (isInvulnerableTo(source)) return false;
        setOrderedToSit(false);
        return super.hurtBy(source, amount, attacker);
    }

    @Override
    public boolean doHurtTarget(Targetable t) {
        return super.doHurtTarget(t);
    }

    @Override
    public boolean interact(Player p, int hand, ItemStack held, org.domi.furnace.server.world.Region region) {
        String name = held.isEmpty() ? "" : ItemTools.itemName(held);
        if (isTame()) {
            if (isFood(held) && health() < attributeValueF(Attribute.MAX_HEALTH)) {
                usePlayerItem(p, hand, held);
                float nutrition = ItemTools.component(held, "minecraft:food") instanceof java.util.Map<?, ?> f && f.get("nutrition") instanceof Number n ? n.floatValue() : 1.0F;
                heal(2.0F * nutrition);
                return true;
            }
            if (name.endsWith("_dye") && isOwnedBy(p)) {
                String c = name.substring("minecraft:".length(), name.length() - "_dye".length());
                for (DyeColor d : DyeColor.values()) {
                    if (d.key().equals(c) && d != getCollarColor()) {
                        setCollarColor(d);
                        usePlayerItem(p, hand, held);
                        return true;
                    }
                }
            }
            boolean handled = super.interact(p, hand, held, region);
            if (!handled && isOwnedBy(p)) {
                setOrderedToSit(!isOrderedToSit());
                jumping = false;
                navigation.stop();
                setTarget(null);
                return true;
            }
            return handled;
        } else if (name.equals("minecraft:bone") && !isAngry()) {
            usePlayerItem(p, hand, held);
            tryToTame(p);
            return true;
        }
        return super.interact(p, hand, held, region);
    }

    private void tryToTame(Player p) {
        if (random.nextInt(3) == 0) {
            tame(p);
            navigation.stop();
            setTarget(null);
            setOrderedToSit(true);
            broadcastEntityEvent((byte) 7);
        } else {
            broadcastEntityEvent((byte) 6);
        }
    }

    private float attributeValueF(Attribute a) { return (float) attributeValue(a); }

    @Override public boolean isFood(ItemStack s) { return ItemTags.is(s, "minecraft:wolf_food"); }

    @Override
    public boolean canMate(Animal o) {
        if (o == this) return false;
        if (!isTame()) return false;
        if (!(o instanceof Wolf w)) return false;
        if (!w.isTame()) return false;
        return w.isInSittingPose() ? false : isInLove() && w.isInLove();
    }

    @Override
    public AgeableMob getBreedOffspring(ChunkLevel level, AgeableMob partner) {
        Wolf child = new Wolf(level, level.nextEntityId());
        if (partner instanceof Wolf) {
            random.nextBoolean();   // 변종 고르기(변종 미구현이라 난수만 소비)
            if (isTame()) {
                child.setOwnerUUID(getOwnerUUID());
                child.setTame(true, true);
                child.setCollarColor(random.nextBoolean() ? getCollarColor() : ((Wolf) partner).getCollarColor());
            }
        }
        return child;
    }

    @Override
    public boolean wantsToAttack(Targetable target, Targetable owner) {
        if (target instanceof Entity e) {
            String t = e.type().name();
            if (t.equals("minecraft:creeper") || t.equals("minecraft:ghast") || t.equals("minecraft:armor_stand")) return false;
            if (e instanceof Wolf w) return !w.isTame() || w.getOwner() != owner;
        }
        // TODO(PvP): 플레이어끼리 때릴 수 없으면(pvp 꺼짐·같은 팀) 주인의 적이 플레이어라도 공격하지 않는다
        return !(target instanceof TamableAnimal ta && ta.isTame());
    }

    @Override public boolean canAttackTarget(Targetable t) { return canAttackType(t); }

    @Override public int getRemainingPersistentAngerTime() { return (Integer) entityData.get(DATA_REMAINING_ANGER_TIME); }

    @Override public void setRemainingPersistentAngerTime(int t) { entityData.set(DATA_REMAINING_ANGER_TIME, t); }

    @Override public UUID getPersistentAngerTarget() { return persistentAngerTarget; }

    @Override public void setPersistentAngerTarget(UUID u) { persistentAngerTarget = u; }

    @Override public void startPersistentAngerTimer() { setRemainingPersistentAngerTime(20 + random.nextInt(20)); }

    @Override
    public String getAmbientSound() {
        if (isAngry()) return "entity.wolf.growl";
        if (random.nextInt(3) == 0) return isTame() && health() < 20.0F ? "entity.wolf.whine" : "entity.wolf.pant";
        return "entity.wolf.ambient";
    }

    @Override protected String getHurtSound(Damage source) { return "entity.wolf.hurt"; }

    @Override protected String getDeathSound() { return "entity.wolf.death"; }

    @Override
    protected void addAdditionalSaveData(NbtCompound tag) {
        super.addAdditionalSaveData(tag);
        tag.putByte("CollarColor", (byte) getCollarColor().id());
        addPersistentAngerSaveData(tag);
    }

    @Override
    protected void readAdditionalSaveData(NbtCompound tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("CollarColor")) setCollarColor(DyeColor.byId(tag.getByte("CollarColor")));
        readPersistentAngerSaveData(tag);
    }

    /** 무리 크기(Mob.getMaxSpawnClusterSize)는 자연 스폰 때 쓴다. */
    public int maxSpawnClusterSize() { return 8; }
}
