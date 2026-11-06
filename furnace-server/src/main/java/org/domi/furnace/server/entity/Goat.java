// 바닐라 Goat: 브레인으로 움직이는 염소. 길게 뛰고, 대상에게 돌진하며(뿔이 부러질 수 있다), 소리 지르는 변종과 염소 뿔 드롭이 있다.
// TODO(양동이): 양동이로 우유 짜기는 Cow 와 같은 방식. TODO(애니메이션): 돌진 머리 숙임(엔티티 이벤트 58/59)은 클라이언트 연출이라 이벤트만 보낸다.
package org.domi.furnace.server.entity;

import org.domi.furnace.server.attribute.Attribute;
import org.domi.furnace.server.attribute.AttributeMap;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.RandomSource;
import org.domi.furnace.server.entity.ai.brain.Brain;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;
import org.domi.furnace.server.entity.ai.brain.SensorType;
import org.domi.furnace.server.entity.ai.control.Mth;
import org.domi.furnace.server.entity.ai.navigation.PathType;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.item.ItemTags;
import org.domi.furnace.server.item.ComponentPatch;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.protocol.play.SetEntityData;

public class Goat extends Animal {
    public static final int DATA_IS_SCREAMING_GOAT = 17, DATA_HAS_LEFT_HORN = 18, DATA_HAS_RIGHT_HORN = 19;
    public static final int ADULT_ATTACK_DAMAGE = 2;
    public static final int BABY_ATTACK_DAMAGE = 1;

    public Goat(ChunkLevel level, int id) {
        super(EntityType.GOAT, level, id);
        getNavigation().setCanFloat(true);
        setPathfindingMalus(PathType.POWDER_SNOW, -1.0F);
        setPathfindingMalus(PathType.DANGER_POWDER_SNOW, -1.0F);
        brain = makeBrain();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Brain<Goat> makeBrain() {
        Brain<Goat> b = new Brain<>(java.util.List.of(MemoryModuleType.LOOK_TARGET, MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES, MemoryModuleType.WALK_TARGET,
                MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE, MemoryModuleType.PATH, MemoryModuleType.ATE_RECENTLY, MemoryModuleType.BREED_TARGET, MemoryModuleType.LONG_JUMP_COOLDOWN_TICKS,
                MemoryModuleType.LONG_JUMP_MID_JUMP, MemoryModuleType.TEMPTING_PLAYER, MemoryModuleType.NEAREST_VISIBLE_ADULT, MemoryModuleType.TEMPTATION_COOLDOWN_TICKS,
                MemoryModuleType.IS_TEMPTED, MemoryModuleType.RAM_COOLDOWN_TICKS, MemoryModuleType.RAM_TARGET, MemoryModuleType.IS_PANICKING),
                (java.util.List) java.util.List.of(SensorType.NEAREST_LIVING_ENTITIES, SensorType.NEAREST_PLAYERS, SensorType.NEAREST_ITEMS, SensorType.NEAREST_ADULT, SensorType.HURT_BY,
                        SensorType.GOAT_TEMPTATIONS));
        return GoatAi.makeBrain(b);
    }

    @SuppressWarnings("unchecked")
    public Brain<Goat> goatBrain() { return (Brain<Goat>) brain; }

    @Override
    protected void createAttributes(AttributeMap map) {
        super.createAttributes(map);
        map.add(Attribute.MAX_HEALTH, 10.0).add(Attribute.MOVEMENT_SPEED, 0.2F).add(Attribute.ATTACK_DAMAGE, 2.0);
    }

    @Override
    protected void ageBoundaryReached() {
        if (isBaby()) {
            setBaseAttack(1.0);
            removeHorns();
        } else {
            setBaseAttack(2.0);
            addHorns();
        }
    }

    private void setBaseAttack(double v) { attributes().get(Attribute.ATTACK_DAMAGE).setBaseValue(v); }

    @Override
    protected int calculateFallDamage(float distance, float multiplier) { return super.calculateFallDamage(distance, multiplier) - 10; }

    @Override public String getAmbientSound() { return isScreamingGoat() ? "entity.goat.screaming.ambient" : "entity.goat.ambient"; }

    @Override protected String getHurtSound(Damage source) { return isScreamingGoat() ? "entity.goat.screaming.hurt" : "entity.goat.hurt"; }

    @Override protected String getDeathSound() { return isScreamingGoat() ? "entity.goat.screaming.death" : "entity.goat.death"; }

    protected String getMilkSound() { return isScreamingGoat() ? "entity.goat.screaming.milk" : "entity.goat.milk"; }

    protected String getEatSound() { return isScreamingGoat() ? "entity.goat.screaming.eat" : "entity.goat.eat"; }

    @Override
    public AgeableMob getBreedOffspring(ChunkLevel level, AgeableMob partner) {
        Goat child = new Goat(level, level.nextEntityId());
        GoatAi.initMemories(child, level.random());
        AgeableMob parent = level.random().nextBoolean() ? this : partner;
        boolean screaming = parent instanceof Goat g && g.isScreamingGoat() || level.random().nextDouble() < 0.02;
        child.setScreamingGoat(screaming);
        return child;
    }

    @Override
    protected void customServerAiStep() {
        goatBrain().tick(level, this);
        GoatAi.updateActivity(this);
        super.customServerAiStep();
    }

    @Override public int getMaxHeadYRot() { return 15; }

    @Override
    public void setYHeadRot(float v) {
        int max = getMaxHeadYRot();
        float diff = Mth.degreesDifference(yBodyRot(), v);
        super.setYHeadRot(yBodyRot() + Math.clamp(diff, (float) -max, (float) max));
    }

    @Override public boolean isFood(ItemStack s) { return ItemTags.is(s, "minecraft:goat_food"); }

    @Override
    public boolean interact(Player p, int hand, ItemStack held, org.domi.furnace.server.world.Region region) {
        if (!held.isEmpty() && org.domi.furnace.server.item.ItemTools.itemName(held).equals("minecraft:bucket") && !isBaby()) {
            level.playSound(null, p.x(), p.y(), p.z(), getMilkSound(), "players", 1.0F, 1.0F);
            fillResult(p, hand, held, "minecraft:milk_bucket", region);
            return true;
        }
        boolean ok = super.interact(p, hand, held, region);
        if (ok && isFood(held)) level.playSound(null, this, getEatSound(), "neutral", 1.0F, 0.8F + level.random().nextFloat() * 0.4F);
        return ok;
    }

    @Override
    public GroupData finalizeSpawn(RandomSource r, DifficultyInstance difficulty, GroupData data) {
        GoatAi.initMemories(this, r);
        setScreamingGoat(r.nextDouble() < 0.02);
        ageBoundaryReached();
        if (!isBaby() && r.nextFloat() < 0.1F) {
            int id = r.nextBoolean() ? DATA_HAS_LEFT_HORN : DATA_HAS_RIGHT_HORN;
            entityData.set(id, false);
        }
        return super.finalizeSpawn(r, difficulty, data);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_IS_SCREAMING_GOAT, SetEntityData.SERIALIZER_BOOLEAN, false);
        entityData.define(DATA_HAS_LEFT_HORN, SetEntityData.SERIALIZER_BOOLEAN, true);
        entityData.define(DATA_HAS_RIGHT_HORN, SetEntityData.SERIALIZER_BOOLEAN, true);
    }

    @Override
    protected void addAdditionalSaveData(NbtCompound tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("IsScreamingGoat", isScreamingGoat());
        tag.putBoolean("HasLeftHorn", hasLeftHorn());
        tag.putBoolean("HasRightHorn", hasRightHorn());
    }

    @Override
    protected void readAdditionalSaveData(NbtCompound tag) {
        super.readAdditionalSaveData(tag);
        setScreamingGoat(tag.getBoolean("IsScreamingGoat"));
        entityData.set(DATA_HAS_LEFT_HORN, tag.getBoolean("HasLeftHorn"));
        entityData.set(DATA_HAS_RIGHT_HORN, tag.getBoolean("HasRightHorn"));
    }

    public boolean hasLeftHorn() { return (Boolean) entityData.get(DATA_HAS_LEFT_HORN); }

    public boolean hasRightHorn() { return (Boolean) entityData.get(DATA_HAS_RIGHT_HORN); }

    /** 돌진하다 뿔이 부러진다: 남은 뿔 중 하나(둘 다 있으면 무작위)를 잃고 염소 뿔 아이템이 튀어 나온다. */
    public boolean dropHorn() {
        boolean left = hasLeftHorn(), right = hasRightHorn();
        if (!left && !right) return false;
        int id;
        if (!left) id = DATA_HAS_RIGHT_HORN;
        else if (!right) id = DATA_HAS_LEFT_HORN;
        else id = random.nextBoolean() ? DATA_HAS_LEFT_HORN : DATA_HAS_RIGHT_HORN;
        entityData.set(id, false);
        ItemStack horn = createHorn();
        double dx = random.nextFloat() * 0.4F - 0.2F, dy = 0.3F + random.nextFloat() * 0.4F, dz = random.nextFloat() * 0.4F - 0.2F;
        level.addFreshEntity(new ItemEntity(level, level.nextEntityId(), x(), y(), z(), horn, dx, dy, dz));
        return true;
    }

    /** createHorn: 엔티티 UUID 해시로 시드한 난수로 (소리 지르는 염소면 screaming) 뿔 종류를 고른다. */
    public ItemStack createHorn() {
        RandomSource r = RandomSource.create((long) uuid().hashCode());
        int[] ids = ItemContext.tagEntries("minecraft:instrument", isScreamingGoat() ? "minecraft:screaming_goat_horns" : "minecraft:regular_goat_horns");
        int item = ItemContext.id("minecraft:item", "goat_horn");
        if (ids.length == 0) return new ItemStack(item, 1);
        String instrument = ItemContext.name("minecraft:instrument", ids[r.nextInt(ids.length)]);
        return new ItemStack(item, 1, ComponentPatch.ofNormalized(new java.util.LinkedHashMap<>(java.util.Map.of("minecraft:instrument", instrument))));
    }

    public void addHorns() {
        entityData.set(DATA_HAS_LEFT_HORN, true);
        entityData.set(DATA_HAS_RIGHT_HORN, true);
    }

    public void removeHorns() {
        entityData.set(DATA_HAS_LEFT_HORN, false);
        entityData.set(DATA_HAS_RIGHT_HORN, false);
    }

    public boolean isScreamingGoat() { return (Boolean) entityData.get(DATA_IS_SCREAMING_GOAT); }

    public void setScreamingGoat(boolean v) { entityData.set(DATA_IS_SCREAMING_GOAT, v); }
}
