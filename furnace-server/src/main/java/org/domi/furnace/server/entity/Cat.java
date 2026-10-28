// 바닐라 Cat: 길들이기(생선 1/3), 앉기, 목걸이 색, 고양이 변종(보름달엔 11종), 플레이어를 피함(길들이기 전).
// TODO: 침대에 눕기·블록에 앉기·주인 위에서 쉬기·아침 선물(잠자기 필요), 마녀 오두막/마을에서 검은 고양이 변종
package org.domi.furnace.server.entity;

import org.domi.furnace.server.attribute.Attribute;
import org.domi.furnace.server.attribute.AttributeMap;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.entity.ai.goal.*;
import org.domi.furnace.server.item.ItemTags;
import org.domi.furnace.server.item.ItemTools;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.protocol.play.SetEntityData;

public class Cat extends TamableAnimal {
    public static final int DATA_VARIANT = 19, DATA_IS_LYING = 20, DATA_RELAX_STATE_ONE = 21, DATA_COLLAR_COLOR = 22;
    private static final String[] VARIANTS = {"tabby", "black", "red", "siamese", "british_shorthair", "calico", "persian", "ragdoll", "white", "jellie", "all_black"};
    private AvoidEntityGoal avoidPlayersGoal;
    private TemptGoal temptGoal;

    public Cat(ChunkLevel level, int id) {
        super(EntityType.CAT, level, id);
        reassessTameGoals();
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_VARIANT, SetEntityData.SERIALIZER_CAT_VARIANT, 0);
        entityData.define(DATA_IS_LYING, SetEntityData.SERIALIZER_BOOLEAN, false);
        entityData.define(DATA_RELAX_STATE_ONE, SetEntityData.SERIALIZER_BOOLEAN, false);
        entityData.define(DATA_COLLAR_COLOR, SetEntityData.SERIALIZER_INT, DyeColor.RED.id());
    }

    @Override
    protected void registerGoals() {
        temptGoal = new CatTemptGoal(this, 0.6, s -> ItemTags.is(s, "minecraft:cat_food"), true);
        goalSelector.addGoal(1, new FloatGoal(this));
        goalSelector.addGoal(1, new TamablePanicGoal(this, 1.5));
        goalSelector.addGoal(2, new SitWhenOrderedToDoGoal(this));
        goalSelector.addGoal(4, temptGoal);
        goalSelector.addGoal(6, new FollowOwnerGoal(this, 1.0, 10.0F, 5.0F));
        goalSelector.addGoal(8, new LeapAtTargetGoal(this, 0.3F));
        goalSelector.addGoal(9, new OcelotAttackGoal(this));
        goalSelector.addGoal(10, new BreedGoal(this, 0.8));
        goalSelector.addGoal(11, new WaterAvoidingRandomStrollGoal(this, 0.8, 1.0000001E-5F));
        goalSelector.addGoal(12, new LookAtPlayerGoal(this, 10.0F));
        targetSelector.addGoal(1, new CatNonTameTarget(this, Rabbit.class));
        targetSelector.addGoal(1, new NonTameRandomTargetGoal(this, Turtle.class, false, Turtle.BABY_ON_LAND_SELECTOR));
    }

    public int variant() { return (Integer) entityData.get(DATA_VARIANT); }

    public void setVariant(int v) { entityData.set(DATA_VARIANT, v); }

    public DyeColor getCollarColor() { return DyeColor.byId((Integer) entityData.get(DATA_COLLAR_COLOR)); }

    private void setCollarColor(DyeColor c) { entityData.set(DATA_COLLAR_COLOR, c.id()); }

    public boolean isLying() { return (Boolean) entityData.get(DATA_IS_LYING); }

    public void setLying(boolean v) { entityData.set(DATA_IS_LYING, v); }

    public boolean isRelaxStateOne() { return (Boolean) entityData.get(DATA_RELAX_STATE_ONE); }

    @Override
    protected void createAttributes(AttributeMap map) {
        super.createAttributes(map);
        map.add(Attribute.MAX_HEALTH, 10.0).add(Attribute.MOVEMENT_SPEED, 0.3F).add(Attribute.ATTACK_DAMAGE, 3.0);
    }

    @Override
    protected void addAdditionalSaveData(NbtCompound tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("variant", "minecraft:" + VARIANTS[Math.min(variant(), VARIANTS.length - 1)]);
        tag.putByte("CollarColor", (byte) getCollarColor().id());
    }

    @Override
    protected void readAdditionalSaveData(NbtCompound tag) {
        super.readAdditionalSaveData(tag);
        if (tag.tags().get("variant") instanceof String v) {
            for (int i = 0; i < VARIANTS.length; i++) if (("minecraft:" + VARIANTS[i]).equals(v)) setVariant(i);
        }
        if (tag.contains("CollarColor")) setCollarColor(DyeColor.byId(tag.getByte("CollarColor")));
    }

    @Override
    protected void customServerAiStep() {
        if (moveControl.hasWanted()) {
            double s = moveControl.getSpeedModifier();
            if (s == 0.6) {
                setPose(POSE_CROUCHING);
                setSprinting(false);
            } else if (s == 1.33) {
                setPose(POSE_STANDING);
                setSprinting(true);
            } else {
                setPose(POSE_STANDING);
                setSprinting(false);
            }
        } else {
            setPose(POSE_STANDING);
            setSprinting(false);
        }
    }

    @Override
    public String getAmbientSound() {
        if (isTame()) {
            if (isInLove()) return "entity.cat.purr";
            return random.nextInt(4) == 0 ? "entity.cat.purreow" : "entity.cat.ambient";
        }
        return "entity.cat.stray_ambient";
    }

    @Override protected String getHurtSound(Damage source) { return "entity.cat.hurt"; }

    @Override protected String getDeathSound() { return "entity.cat.death"; }

    @Override
    protected void usePlayerItem(Player p, int hand, ItemStack held) {
        if (isFood(held)) playSound("entity.cat.eat", 1.0F, 1.0F);
        super.usePlayerItem(p, hand, held);
    }

    @Override
    public void tick() {
        super.tick();
        if (temptGoal != null && temptGoal.isRunning() && !isTame() && tickCount % 100 == 0) playSound("entity.cat.beg_for_food", 1.0F, 1.0F);
    }

    @Override
    public AgeableMob getBreedOffspring(ChunkLevel level, AgeableMob partner) {
        Cat child = new Cat(level, level.nextEntityId());
        if (partner instanceof Cat p) {
            child.setVariant(random.nextBoolean() ? variant() : p.variant());
            if (isTame()) {
                child.setOwnerUUID(getOwnerUUID());
                child.setTame(true, true);
                child.setCollarColor(random.nextBoolean() ? getCollarColor() : p.getCollarColor());
            }
        }
        return child;
    }

    @Override
    public boolean canMate(Animal o) {
        if (!isTame()) return false;
        return !(o instanceof Cat c) ? false : c.isTame() && super.canMate(o);
    }

    @Override
    public GroupData finalizeSpawn(org.domi.furnace.server.block.RandomSource r, DifficultyInstance difficulty, GroupData data) {
        data = super.finalizeSpawn(r, difficulty, data);
        boolean fullMoon = level.moonBrightness() > 0.9F;
        setVariant(level.random().nextInt(fullMoon ? 11 : 10));
        // #cats_spawn_as_black(swamp_hut) 조각 안이면 all_black + 유지. CatSpawner 는 moveTo 전에 불러 원점 기준이다(바닐라 그대로).
        if (level.structureWithPieceAt(blockPosition(), "minecraft:swamp_hut") != null) {
            setVariant(10);
            persistenceRequired = true;
        }
        return data;
    }

    @Override
    public boolean interact(Player p, int hand, ItemStack held, org.domi.furnace.server.world.Region region) {
        String name = held.isEmpty() ? "" : ItemTools.itemName(held);
        if (isTame()) {
            if (isOwnedBy(p)) {
                if (name.endsWith("_dye")) {
                    String c = name.substring("minecraft:".length(), name.length() - "_dye".length());
                    for (DyeColor d : DyeColor.values()) {
                        if (d.key().equals(c) && d != getCollarColor()) {
                            setCollarColor(d);
                            usePlayerItem(p, hand, held);
                            return true;
                        }
                    }
                } else if (isFood(held) && health() < (float) attributeValue(Attribute.MAX_HEALTH)) {
                    float nutrition = ItemTools.component(held, "minecraft:food") instanceof java.util.Map<?, ?> f && f.get("nutrition") instanceof Number n ? n.floatValue() : 1.0F;
                    usePlayerItem(p, hand, held);
                    heal(nutrition);
                    return true;
                }
                boolean handled = super.interact(p, hand, held, region);
                if (!handled) {
                    setOrderedToSit(!isOrderedToSit());
                    return true;
                }
                return handled;
            }
        } else if (isFood(held)) {
            usePlayerItem(p, hand, held);
            if (random.nextInt(3) == 0) {
                tame(p);
                setOrderedToSit(true);
                broadcastEntityEvent((byte) 7);
            } else {
                broadcastEntityEvent((byte) 6);
            }
            return true;
        }
        return super.interact(p, hand, held, region);
    }

    @Override public boolean isFood(ItemStack s) { return ItemTags.is(s, "minecraft:cat_food"); }

    @Override public boolean removeWhenFarAway(double distSqr) { return !isTame() && tickCount > 2400; }

    @Override
    protected void applyTamingSideEffects() {}

    @Override
    public void setTame(boolean tame, boolean side) {
        super.setTame(tame, side);
        reassessTameGoals();
    }

    protected void reassessTameGoals() {
        if (avoidPlayersGoal == null) avoidPlayersGoal = new CatAvoidEntityGoal(this, Player.class, 16.0F, 0.8, 1.33);
        goalSelector.removeGoal(avoidPlayersGoal);
        if (!isTame()) goalSelector.addGoal(3, avoidPlayersGoal);
    }

    private static final class CatAvoidEntityGoal extends AvoidEntityGoal {
        private final Cat cat;

        CatAvoidEntityGoal(Cat c, Class<? extends Targetable> cls, float d, double w, double s) {
            super(c, cls, d, w, s);
            this.cat = c;
        }

        @Override public boolean canUse() { return !cat.isTame() && super.canUse(); }

        @Override public boolean canContinueToUse() { return !cat.isTame() && super.canContinueToUse(); }
    }

    private static final class CatTemptGoal extends TemptGoal {
        private final Cat cat;
        private Player selectedPlayer;

        CatTemptGoal(Cat c, double speed, java.util.function.Predicate<ItemStack> items, boolean scare) {
            super(c, speed, items, scare);
            this.cat = c;
        }

        @Override
        public void tick() {
            super.tick();
            if (selectedPlayer == null && cat.random().nextInt(adjustedTickDelay(600)) == 0) selectedPlayer = player;
            else if (cat.random().nextInt(adjustedTickDelay(500)) == 0) selectedPlayer = null;
        }

        @Override protected boolean canScare() { return selectedPlayer != null && selectedPlayer == player ? false : super.canScare(); }

        @Override public boolean canUse() { return super.canUse() && !cat.isTame(); }
    }

    private static final class CatNonTameTarget extends NearestAttackableTargetGoal {
        private final Cat cat;

        CatNonTameTarget(Cat c, Class<?> type) {
            super(c, type, 10, false, false, null);
            this.cat = c;
        }

        @Override public boolean canUse() { return !cat.isTame() && super.canUse(); }
    }

    /** Cat.hiss: 하악질 소리(팬텀이 가까이 있을 때). */
    public void hiss() { playSound("entity.cat.hiss", getSoundVolume(), getVoicePitch()); }
}
