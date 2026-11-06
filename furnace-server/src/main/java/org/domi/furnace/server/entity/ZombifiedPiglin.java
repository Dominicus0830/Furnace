// 바닐라 ZombifiedPiglin: 불에 면역인 중립 좀비. 맞으면 화나서(속도 +0.05) 같은 종류에게 알리고 함께 쫓는다. 황금 검을 든다.
package org.domi.furnace.server.entity;

import java.util.UUID;
import org.domi.furnace.server.attribute.Attribute;
import org.domi.furnace.server.attribute.AttributeMap;
import org.domi.furnace.server.attribute.AttributeModifier;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.entity.ai.goal.*;
import org.domi.furnace.server.net.io.nbt.NbtCompound;

public class ZombifiedPiglin extends Zombie implements NeutralMob {
    private static final String SPEED_MODIFIER_ATTACKING_ID = "minecraft:attacking";
    private static final AttributeModifier SPEED_MODIFIER_ATTACKING = new AttributeModifier(SPEED_MODIFIER_ATTACKING_ID, 0.05, AttributeModifier.ADD_VALUE);
    private int playFirstAngerSoundIn;
    private int remainingPersistentAngerTime;
    private UUID persistentAngerTarget;
    private int ticksUntilNextAlert;

    public ZombifiedPiglin(ChunkLevel level, int id) {
        super(EntityType.ZOMBIFIED_PIGLIN, level, id);
        setPathfindingMalus(org.domi.furnace.server.entity.ai.navigation.PathType.LAVA, 8.0F);
    }

    @Override
    protected void createAttributes(AttributeMap map) {
        super.createAttributes(map);
        map.add(Attribute.SPAWN_REINFORCEMENTS, 0.0).add(Attribute.MOVEMENT_SPEED, 0.23F).add(Attribute.ATTACK_DAMAGE, 5.0);
    }

    @Override
    protected boolean supportsBreakDoorGoal() { return false; }

    @Override
    protected void addBehaviourGoals() {
        goalSelector.addGoal(2, new ZombieAttackGoal(this, 1.0, false));
        goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 1.0));
        targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers());
        targetSelector.addGoal(2, new NearestAttackableTargetGoal(this, Player.class, 10, true, false, t -> isAngryAt(t)));
        targetSelector.addGoal(3, new ResetUniversalAngerTargetGoal<>(this, true));
    }

    @Override protected boolean convertsInWater() { return false; }

    @Override
    protected void customServerAiStep() {
        var speed = attributes().get(Attribute.MOVEMENT_SPEED);
        if (isAngry()) {
            if (!isBaby() && !speed.hasModifier(SPEED_MODIFIER_ATTACKING_ID)) speed.addOrReplaceModifier(SPEED_MODIFIER_ATTACKING);
            maybePlayFirstAngerSound();
        } else if (speed.hasModifier(SPEED_MODIFIER_ATTACKING_ID)) {
            speed.removeModifier(SPEED_MODIFIER_ATTACKING_ID);
        }
        updatePersistentAnger(level.gameRule("universalAnger"));
        if (getTarget() != null) maybeAlertOthers();
        if (isAngry()) lastHurtByPlayerTime = tickCount;
        super.customServerAiStep();
    }

    private void maybePlayFirstAngerSound() {
        if (playFirstAngerSoundIn > 0) {
            playFirstAngerSoundIn--;
            if (playFirstAngerSoundIn == 0) playAngerSound();
        }
    }

    private void maybeAlertOthers() {
        if (ticksUntilNextAlert > 0) {
            ticksUntilNextAlert--;
        } else {
            Targetable t = getTarget();
            if (hasLineOfSight(t)) alertOthers();
            ticksUntilNextAlert = 4 + random.nextInt(3);
        }
    }

    private void alertOthers() {
        double d = attributeValue(Attribute.FOLLOW_RANGE);
        var box = boundingBox().inflate(d, 10.0, d);
        for (var other : level.getEntitiesOfClass(ZombifiedPiglin.class, box, e -> e != this && e.getTarget() == null)) {
            if (!other.isAlliedTo(getTarget())) other.setTarget(getTarget());
        }
    }

    private boolean isAlliedTo(Targetable t) { return false; }

    private void playAngerSound() { playSound("entity.zombified_piglin.angry", getSoundVolume() * 2.0F, getVoicePitch() * 1.8F); }

    @Override
    public boolean setTarget(Targetable t) {
        if (getTarget() == null && t != null) {
            playFirstAngerSoundIn = 0 + random.nextInt(2);   // UniformInt.of(0, 1)
            ticksUntilNextAlert = 4 + random.nextInt(3);
        }
        if (t instanceof Player p) setLastHurtByPlayer(p);
        return super.setTarget(t);
    }

    @Override public void startPersistentAngerTimer() { setRemainingPersistentAngerTime(20 + random.nextInt(20)); }

    @Override public void setRemainingPersistentAngerTime(int t) { remainingPersistentAngerTime = t; }

    @Override public int getRemainingPersistentAngerTime() { return remainingPersistentAngerTime; }

    @Override public void setPersistentAngerTarget(UUID u) { persistentAngerTarget = u; }

    @Override public UUID getPersistentAngerTarget() { return persistentAngerTarget; }

    @Override public boolean canAttackTarget(Targetable t) { return canAttackType(t); }

    @Override
    protected void populateDefaultEquipmentSlots(org.domi.furnace.server.block.RandomSource r, DifficultyInstance difficulty) {
        setItemSlot(EquipmentSlot.MAINHAND, stackOf("minecraft:golden_sword"));
    }

    @Override
    protected void randomizeReinforcementsChance() { attributes().get(Attribute.SPAWN_REINFORCEMENTS).setBaseValue(0.0); }

    @Override public String getAmbientSound() { return isAngry() ? "entity.zombified_piglin.angry" : "entity.zombified_piglin.ambient"; }

    @Override protected String getHurtSound(Damage source) { return "entity.zombified_piglin.hurt"; }

    @Override protected String getDeathSound() { return "entity.zombified_piglin.death"; }

    @Override
    protected void addAdditionalSaveData(NbtCompound tag) {
        super.addAdditionalSaveData(tag);
        addPersistentAngerSaveData(tag);
    }

    @Override
    protected void readAdditionalSaveData(NbtCompound tag) {
        super.readAdditionalSaveData(tag);
        readPersistentAngerSaveData(tag);
    }

    /** ZombifiedPiglin.isPreventingPlayerRest: 그 플레이어에게 화났을 때만. */
    @Override
    public boolean isPreventingPlayerRest(Player player) { return isAngryAt(player); }
}
