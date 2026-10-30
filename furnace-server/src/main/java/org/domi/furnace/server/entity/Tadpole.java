// 바닐라 Tadpole: 브레인으로 헤엄치는 올챙이. 24000틱 뒤 개구리로 자라고, 슬라임 볼을 주면 빨리 자란다.
package org.domi.furnace.server.entity;

import org.domi.furnace.server.attribute.Attribute;
import org.domi.furnace.server.attribute.AttributeMap;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.entity.ai.brain.Brain;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;
import org.domi.furnace.server.entity.ai.brain.SensorType;
import org.domi.furnace.server.entity.ai.control.SmoothSwimmingLookControl;
import org.domi.furnace.server.entity.ai.control.SmoothSwimmingMoveControl;
import org.domi.furnace.server.item.ItemTags;
import org.domi.furnace.server.net.io.nbt.NbtCompound;

public class Tadpole extends AbstractFish {
    public static int ticksToBeFrog = Math.abs(-24000);
    private int age;

    public Tadpole(ChunkLevel level, int id) {
        super(EntityType.TADPOLE, level, id);
        moveControl = new SmoothSwimmingMoveControl(this, 85, 10, 0.02F, 0.1F, true);
        lookControl = new SmoothSwimmingLookControl(this, 10);
        brain = makeBrain();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Brain<Tadpole> makeBrain() {
        Brain<Tadpole> b = new Brain<>(java.util.List.of(MemoryModuleType.LOOK_TARGET, MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES, MemoryModuleType.WALK_TARGET,
                MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE, MemoryModuleType.PATH, MemoryModuleType.NEAREST_VISIBLE_ADULT, MemoryModuleType.TEMPTATION_COOLDOWN_TICKS,
                MemoryModuleType.IS_TEMPTED, MemoryModuleType.TEMPTING_PLAYER, MemoryModuleType.BREED_TARGET, MemoryModuleType.IS_PANICKING),
                (java.util.List) java.util.List.of(SensorType.NEAREST_LIVING_ENTITIES, SensorType.NEAREST_PLAYERS, SensorType.HURT_BY, SensorType.FROG_TEMPTATIONS));
        return TadpoleAi.makeBrain(b);
    }

    @SuppressWarnings("unchecked")
    public Brain<Tadpole> tadpoleBrain() { return (Brain<Tadpole>) brain; }

    @Override
    protected void createAttributes(AttributeMap map) {
        super.createAttributes(map);
        map.add(Attribute.MOVEMENT_SPEED, 1.0).add(Attribute.MAX_HEALTH, 6.0);
    }

    @Override
    protected void customServerAiStep() {
        tadpoleBrain().tick(level, this);
        TadpoleAi.updateActivity(this);
        super.customServerAiStep();
    }

    @Override
    public void tick() {
        super.tick();
        setAge(age + 1);
    }

    @Override
    protected void addAdditionalSaveData(NbtCompound tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Age", age);
    }

    @Override
    protected void readAdditionalSaveData(NbtCompound tag) {
        super.readAdditionalSaveData(tag);
        setAge(tag.getInt("Age"));
    }

    @Override public String getAmbientSound() { return null; }

    @Override protected String getHurtSound(Damage source) { return "entity.tadpole.hurt"; }

    @Override protected String getDeathSound() { return "entity.tadpole.death"; }

    @Override protected String getFlopSound() { return "entity.tadpole.flop"; }

    @Override
    public boolean interact(Player p, int hand, ItemStack held, org.domi.furnace.server.world.Region region) {
        if (ItemTags.is(held, "minecraft:frog_food")) {
            usePlayerItem(p, hand, held);
            ageUp(getTicksLeftUntilAdult());
            return true;
        }
        return Bucketable.bucketMobPickup(p, hand, held, this, region);
    }

    @Override public ItemStack getBucketItemStack() { return stackOf("tadpole_bucket"); }

    @Override public String getPickupSound() { return "minecraft:item.bucket.fill_tadpole"; }

    /** Tadpole.saveToBucketTag: 공통 데이터 + Age. */
    @Override
    public ItemStack saveToBucketTag(ItemStack bucket) {
        ItemStack s = Bucketable.saveDefaultDataToBucketTag(this, bucket);
        NbtCompound tag = Bucketable.bucketTag(s);
        tag.putInt("Age", age);
        return Bucketable.withBucketTag(s, tag);
    }

    /** Tadpole.loadFromBucketTag: 공통 데이터 + Age 가 있으면. */
    @Override
    public void loadFromBucketTag(NbtCompound tag) {
        Bucketable.loadDefaultDataFromBucketTag(this, tag);
        if (tag.contains("Age")) setAge(tag.getInt("Age"));
    }

    private void ageUp(int ticks) { setAge(age + ticks * 20); }

    private int getTicksLeftUntilAdult() { return Math.max(0, ticksToBeFrog - age); }

    private void setAge(int a) {
        age = a;
        if (age >= ticksToBeFrog) ageUp();
    }

    private void ageUp() {
        Frog frog = new Frog(level, level.nextEntityId());
        frog.moveTo(x(), y(), z(), yRot(), xRot());
        frog.finalizeSpawn(level.random(), level.getCurrentDifficultyAt(frog.blockPosition()));
        frog.setNoAi(isNoAi());
        frog.persistenceRequired = true;
        playSound("entity.tadpole.grow_up", 0.15F, 1.0F);
        level.addFreshEntity(frog);
        discard();
    }

    @Override public boolean fromBucket() { return true; }

    /** 올챙이는 늘 양동이에서 온 것으로 친다(setFromBucket 은 아무 일도 안 한다). */
    @Override public void setFromBucket(boolean v) {}

    /** 바닐라 Tadpole: 경험치 없음. */
    @Override
    protected boolean shouldDropExperience() { return false; }
}
