// 바닐라 PiglinBrute: 요새 수비 피글린. 금 도끼를 들고 금 갑옷과 상관없이 플레이어를 공격하며, 호글린은 사냥하지 않는다.
package org.domi.furnace.server.entity;

import org.domi.furnace.server.attribute.Attribute;
import org.domi.furnace.server.attribute.AttributeMap;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.RandomSource;
import org.domi.furnace.server.entity.ai.brain.Brain;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;
import org.domi.furnace.server.entity.ai.brain.SensorType;

public class PiglinBrute extends AbstractPiglin {
    public PiglinBrute(ChunkLevel level, int id) {
        super(EntityType.PIGLIN_BRUTE, level, id);
        xpReward = 20;
        brain = makeBrain();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Brain<PiglinBrute> makeBrain() {
        Brain<PiglinBrute> b = new Brain<>(java.util.List.of(MemoryModuleType.LOOK_TARGET, MemoryModuleType.DOORS_TO_CLOSE, MemoryModuleType.NEAREST_LIVING_ENTITIES,
                MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES, MemoryModuleType.NEAREST_VISIBLE_PLAYER, MemoryModuleType.NEAREST_VISIBLE_ATTACKABLE_PLAYER,
                MemoryModuleType.NEARBY_ADULT_PIGLINS, MemoryModuleType.NEAREST_VISIBLE_ADULT_PIGLINS, MemoryModuleType.HURT_BY, MemoryModuleType.HURT_BY_ENTITY,
                MemoryModuleType.WALK_TARGET, MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE, MemoryModuleType.ATTACK_TARGET, MemoryModuleType.ATTACK_COOLING_DOWN,
                MemoryModuleType.INTERACTION_TARGET, MemoryModuleType.PATH, MemoryModuleType.ANGRY_AT, MemoryModuleType.NEAREST_VISIBLE_NEMESIS, MemoryModuleType.HOME),
                (java.util.List) java.util.List.of(SensorType.NEAREST_LIVING_ENTITIES, SensorType.NEAREST_PLAYERS, SensorType.NEAREST_ITEMS, SensorType.HURT_BY, SensorType.PIGLIN_BRUTE_SPECIFIC));
        return PiglinBruteAi.makeBrain(this, b);
    }

    @SuppressWarnings("unchecked")
    public Brain<PiglinBrute> bruteBrain() { return (Brain<PiglinBrute>) brain; }

    @Override
    protected void createAttributes(AttributeMap map) {
        super.createAttributes(map);
        map.add(Attribute.MAX_HEALTH, 50.0).add(Attribute.MOVEMENT_SPEED, 0.35F).add(Attribute.ATTACK_DAMAGE, 7.0);
    }

    @Override
    public void finalizeSpawn(RandomSource r, DifficultyInstance difficulty) {
        PiglinBruteAi.initMemories(this);
        populateDefaultEquipmentSlots(r, difficulty);
        super.finalizeSpawn(r, difficulty);
    }

    @Override protected void populateDefaultEquipmentSlots(RandomSource r, DifficultyInstance difficulty) { setItemSlot(EquipmentSlot.MAINHAND, stackOf("minecraft:golden_axe")); }

    @Override protected boolean canHunt() { return false; }

    @Override
    public boolean wantsToPickUp(ItemStack stack) {
        return org.domi.furnace.server.item.ItemTools.itemName(stack).equals("minecraft:golden_axe") ? super.wantsToPickUp(stack) : false;
    }

    @Override
    protected void customServerAiStep() {
        bruteBrain().tick(level, this);
        PiglinBruteAi.updateActivity(this);
        PiglinBruteAi.maybePlayActivitySound(this);
        super.customServerAiStep();
    }

    @Override
    public boolean hurtBy(Damage source, float amount, Targetable attacker) {
        boolean hurt = super.hurtBy(source, amount, attacker);
        if (hurt && attacker != null) PiglinBruteAi.wasHurtBy(this, attacker);
        return hurt;
    }

    @Override public String getAmbientSound() { return "entity.piglin_brute.ambient"; }

    @Override protected String getHurtSound(Damage source) { return "entity.piglin_brute.hurt"; }

    @Override protected String getDeathSound() { return "entity.piglin_brute.death"; }

    protected void playAngrySound() { makeSound("entity.piglin_brute.angry"); }

    @Override protected void playConvertedSound() { makeSound("entity.piglin_brute.converted_to_zombified"); }
}
