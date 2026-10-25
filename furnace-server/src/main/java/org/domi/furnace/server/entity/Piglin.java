// 바닐라 Piglin: 금을 좋아하는 네더 주민. 금 주괴로 물물교환하고, 금 갑옷이 없으면 공격하며, 호글린을 사냥한다. 오버월드에서는 좀비 피글린이 된다.
// TODO(탑승): 아기가 호글린을 타는 행동. TODO(머리): 충전된 크리퍼에 죽을 때 피글린 머리 드롭. TODO(인챈트): 방어구 교체 방지 인챈트.
package org.domi.furnace.server.entity;

import org.domi.furnace.server.attribute.Attribute;
import org.domi.furnace.server.attribute.AttributeMap;
import org.domi.furnace.server.attribute.AttributeModifier;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.RandomSource;
import org.domi.furnace.server.entity.ai.brain.Brain;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;
import org.domi.furnace.server.entity.ai.brain.SensorType;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.protocol.play.SetEntityData;

public class Piglin extends AbstractPiglin implements CrossbowAttackMob, InventoryCarrier {
    public static final int DATA_BABY = 17, DATA_IS_CHARGING_CROSSBOW = 18, DATA_IS_DANCING = 19;
    private static final String BABY_MODIFIER_ID = "minecraft:baby";
    private final MobInventory inventory = new MobInventory(8);
    private boolean cannotHunt;

    public Piglin(ChunkLevel level, int id) {
        super(EntityType.PIGLIN, level, id);
        xpReward = 5;
        brain = makeBrain();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Brain<Piglin> makeBrain() {
        Brain<Piglin> b = new Brain<>(java.util.List.of(MemoryModuleType.LOOK_TARGET, MemoryModuleType.DOORS_TO_CLOSE, MemoryModuleType.NEAREST_LIVING_ENTITIES,
                MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES, MemoryModuleType.NEAREST_VISIBLE_PLAYER, MemoryModuleType.NEAREST_VISIBLE_ATTACKABLE_PLAYER,
                MemoryModuleType.NEARBY_ADULT_PIGLINS, MemoryModuleType.NEAREST_VISIBLE_ADULT_PIGLINS, MemoryModuleType.NEAREST_VISIBLE_WANTED_ITEM, MemoryModuleType.ITEM_PICKUP_COOLDOWN_TICKS,
                MemoryModuleType.HURT_BY, MemoryModuleType.HURT_BY_ENTITY, MemoryModuleType.WALK_TARGET, MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE, MemoryModuleType.ATTACK_TARGET,
                MemoryModuleType.ATTACK_COOLING_DOWN, MemoryModuleType.INTERACTION_TARGET, MemoryModuleType.PATH, MemoryModuleType.ANGRY_AT, MemoryModuleType.UNIVERSAL_ANGER,
                MemoryModuleType.AVOID_TARGET, MemoryModuleType.ADMIRING_ITEM, MemoryModuleType.TIME_TRYING_TO_REACH_ADMIRE_ITEM, MemoryModuleType.ADMIRING_DISABLED,
                MemoryModuleType.DISABLE_WALK_TO_ADMIRE_ITEM, MemoryModuleType.CELEBRATE_LOCATION, MemoryModuleType.DANCING, MemoryModuleType.HUNTED_RECENTLY,
                MemoryModuleType.NEAREST_VISIBLE_BABY_HOGLIN, MemoryModuleType.NEAREST_VISIBLE_NEMESIS, MemoryModuleType.NEAREST_VISIBLE_ZOMBIFIED, MemoryModuleType.RIDE_TARGET,
                MemoryModuleType.VISIBLE_ADULT_PIGLIN_COUNT, MemoryModuleType.VISIBLE_ADULT_HOGLIN_COUNT, MemoryModuleType.NEAREST_VISIBLE_HUNTABLE_HOGLIN,
                MemoryModuleType.NEAREST_TARGETABLE_PLAYER_NOT_WEARING_GOLD, MemoryModuleType.NEAREST_PLAYER_HOLDING_WANTED_ITEM, MemoryModuleType.ATE_RECENTLY,
                MemoryModuleType.NEAREST_REPELLENT),
                (java.util.List) java.util.List.of(SensorType.NEAREST_LIVING_ENTITIES, SensorType.NEAREST_PLAYERS, SensorType.NEAREST_ITEMS, SensorType.HURT_BY, SensorType.PIGLIN_SPECIFIC));
        return PiglinAi.makeBrain(this, b);
    }

    @SuppressWarnings("unchecked")
    public Brain<Piglin> piglinBrain() { return (Brain<Piglin>) brain; }

    @Override
    protected void createAttributes(AttributeMap map) {
        super.createAttributes(map);
        map.add(Attribute.MAX_HEALTH, 16.0).add(Attribute.MOVEMENT_SPEED, 0.35F).add(Attribute.ATTACK_DAMAGE, 5.0);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_BABY, SetEntityData.SERIALIZER_BOOLEAN, false);
        entityData.define(DATA_IS_CHARGING_CROSSBOW, SetEntityData.SERIALIZER_BOOLEAN, false);
        entityData.define(DATA_IS_DANCING, SetEntityData.SERIALIZER_BOOLEAN, false);
    }

    @Override
    protected void addAdditionalSaveData(NbtCompound tag) {
        super.addAdditionalSaveData(tag);
        if (isBaby()) tag.putBoolean("IsBaby", true);
        if (cannotHunt) tag.putBoolean("CannotHunt", true);
        writeInventoryToSave(tag);
    }

    @Override
    protected void readAdditionalSaveData(NbtCompound tag) {
        super.readAdditionalSaveData(tag);
        setBaby(tag.getBoolean("IsBaby"));
        cannotHunt = tag.getBoolean("CannotHunt");
        readInventoryFromSave(tag);
    }

    @Override
    protected void dropCustomDeathLoot(Damage source, boolean byPlayer) {
        super.dropCustomDeathLoot(source, byPlayer);
        for (ItemStack s : inventory.removeAllItems()) spawnAtLocation(s);
    }

    protected ItemStack addToInventory(ItemStack stack) { return inventory.addItem(stack); }

    protected boolean canAddToInventory(ItemStack stack) { return inventory.canAddItem(stack); }

    @Override public MobInventory getInventory() { return inventory; }

    @Override
    public void finalizeSpawn(RandomSource r, DifficultyInstance difficulty) {
        if (r.nextFloat() < 0.2F) setBaby(true);
        else if (isAdult()) setItemSlot(EquipmentSlot.MAINHAND, createSpawnWeapon());
        PiglinAi.initMemories(this, r);
        populateDefaultEquipmentSlots(r, difficulty);
        super.finalizeSpawn(r, difficulty);
    }

    @Override public boolean removeWhenFarAway(double distSqr) { return !persistenceRequired; }

    @Override
    protected void populateDefaultEquipmentSlots(RandomSource r, DifficultyInstance difficulty) {
        if (isAdult()) {
            maybeWearArmor(EquipmentSlot.FEET, stackOf("minecraft:golden_boots"), r);
            maybeWearArmor(EquipmentSlot.LEGS, stackOf("minecraft:golden_leggings"), r);
            maybeWearArmor(EquipmentSlot.CHEST, stackOf("minecraft:golden_chestplate"), r);
            maybeWearArmor(EquipmentSlot.HEAD, stackOf("minecraft:golden_helmet"), r);
        }
    }

    private void maybeWearArmor(EquipmentSlot slot, ItemStack stack, RandomSource r) {
        if (r.nextFloat() < 0.1F) setItemSlot(slot, stack);
    }

    private ItemStack createSpawnWeapon() { return random.nextFloat() < 0.5 ? stackOf("minecraft:crossbow") : stackOf("minecraft:golden_sword"); }

    @Override
    public boolean interact(Player p, int hand, ItemStack held, org.domi.furnace.server.world.Region region) {
        if (super.interact(p, hand, held, region)) return true;
        return PiglinAi.mobInteract(this, p, hand, held);
    }

    @Override
    public boolean hurtBy(Damage source, float amount, Targetable attacker) {
        boolean hurt = super.hurtBy(source, amount, attacker);
        if (hurt && attacker != null) PiglinAi.wasHurtBy(this, attacker);
        return hurt;
    }

    // ------------------------------------------------------------------ 아기

    @Override public boolean isBaby() { return (Boolean) entityData.get(DATA_BABY); }

    public void setBaby(boolean baby) {
        entityData.set(DATA_BABY, baby);
        var speed = attributes().get(Attribute.MOVEMENT_SPEED);
        speed.removeModifier(BABY_MODIFIER_ID);
        if (baby) speed.addOrReplaceModifier(new AttributeModifier(BABY_MODIFIER_ID, 0.2F, AttributeModifier.ADD_MULTIPLIED_BASE));
        if (baby) setDimensions(0.3F, 0.975F, 0.97F);
        else setDimensions(0.6F, 1.95F, 1.79F);
    }

    @Override protected void setBabyForConversion(boolean baby) { setBaby(baby); }

    @Override
    protected int getExperienceReward() {
        if (isBaby()) xpReward = (int) (xpReward * 2.5);
        return super.getExperienceReward();
    }

    @Override protected boolean canHunt() { return !cannotHunt; }

    // ------------------------------------------------------------------ 장비·줍기

    @Override
    public boolean wantsToPickUp(ItemStack stack) { return level.gameRule("mobGriefing") && canPickUpLoot() && PiglinAi.wantsToPickup(this, stack); }

    @Override
    protected boolean canReplaceCurrentItem(ItemStack candidate, ItemStack existing) {
        boolean candLoved = PiglinAi.isLovedItem(candidate) || isCrossbow(candidate);
        boolean existLoved = PiglinAi.isLovedItem(existing) || isCrossbow(existing);
        if (candLoved && !existLoved) return true;
        if (!candLoved && existLoved) return false;
        return isAdult() && !isCrossbow(candidate) && isCrossbow(existing) ? false : super.canReplaceCurrentItem(candidate, existing);
    }

    private static boolean isCrossbow(ItemStack s) { return org.domi.furnace.server.item.ItemTools.itemName(s).equals("minecraft:crossbow"); }

    @Override
    protected void pickUpItem(ItemEntity ie) {
        onItemPickup(ie);
        PiglinAi.pickUpItem(this, ie);
    }

    public boolean wantsToReplace(ItemStack stack) {
        EquipmentSlot slot = equipmentSlotFor(stack);
        return canReplaceCurrentItem(stack, getItemBySlot(slot));
    }

    public void holdInMainHand(ItemStack stack) { setItemSlotAndDropWhenKilled(EquipmentSlot.MAINHAND, stack); }

    public void holdInOffHand(ItemStack stack) {
        if (org.domi.furnace.server.item.ItemTools.itemName(stack).equals("minecraft:gold_ingot")) {
            setItemSlot(EquipmentSlot.OFFHAND, stack);
            setDropChance(EquipmentSlot.OFFHAND, 2.0F);
        } else {
            setItemSlotAndDropWhenKilled(EquipmentSlot.OFFHAND, stack);
        }
    }

    @Override public boolean isHoldingUsableProjectileWeapon() { return isHolding("minecraft:crossbow"); }

    // ------------------------------------------------------------------ 석궁

    @Override public void setChargingCrossbow(boolean v) { entityData.set(DATA_IS_CHARGING_CROSSBOW, v); }

    public boolean isChargingCrossbow() { return (Boolean) entityData.get(DATA_IS_CHARGING_CROSSBOW); }

    @Override public void onCrossbowAttackPerformed() { noActionTime = 0; }

    @Override public void performRangedAttack(Targetable target, float power) { performCrossbowAttack(this, 1.6F); }

    public boolean isDancing() { return (Boolean) entityData.get(DATA_IS_DANCING); }

    public void setDancing(boolean v) { entityData.set(DATA_IS_DANCING, v); }

    // ------------------------------------------------------------------ 브레인 틱·소리

    @Override
    protected void customServerAiStep() {
        piglinBrain().tick(level, this);
        PiglinAi.updateActivity(this);
        super.customServerAiStep();
    }

    @Override
    protected void finishConversion() {
        PiglinAi.cancelAdmiring(this);
        for (ItemStack s : inventory.removeAllItems()) spawnAtLocation(s);
        super.finishConversion();
    }

    @Override public String getAmbientSound() { return PiglinAi.getSoundForCurrentActivity(this).orElse(null); }

    @Override protected String getHurtSound(Damage source) { return "entity.piglin.hurt"; }

    @Override protected String getDeathSound() { return "entity.piglin.death"; }

    @Override protected void playConvertedSound() { makeSound("entity.piglin.converted_to_zombified"); }
}
