// 바닐라 Pillager: 석궁을 든 일러저. 5칸 인벤토리를 가지고, 1/300 확률로 관통 석궁으로 태어난다.
// TODO(습격·줍기): 습격 보너스 석궁 마법, 습격 중 깃발 줍기는 습격 시스템이 생기면.
package org.domi.furnace.server.entity;

import java.util.Map;
import org.domi.furnace.server.attribute.Attribute;
import org.domi.furnace.server.attribute.AttributeMap;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.ai.goal.*;
import org.domi.furnace.server.item.ItemTools;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtList;
import org.domi.furnace.server.net.protocol.play.SetEntityData;

public class Pillager extends AbstractIllager implements CrossbowAttackMob {
    public static final int DATA_CHARGING_STATE = 17;
    private final ItemStack[] inventory = new ItemStack[5];

    public Pillager(ChunkLevel level, int id) {
        super(EntityType.PILLAGER, level, id);
        java.util.Arrays.fill(inventory, ItemStack.EMPTY);
    }

    @Override
    protected void createAttributes(AttributeMap map) {
        super.createAttributes(map);
        map.add(Attribute.MOVEMENT_SPEED, 0.35F).add(Attribute.MAX_HEALTH, 24.0).add(Attribute.ATTACK_DAMAGE, 5.0).add(Attribute.FOLLOW_RANGE, 32.0);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_CHARGING_STATE, SetEntityData.SERIALIZER_BOOLEAN, false);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new HoldGroundAttackGoal(this, 10.0F));
        goalSelector.addGoal(3, new RangedCrossbowAttackGoal<>(this, 1.0, 8.0F));
        goalSelector.addGoal(8, new RandomStrollGoal(this, 0.6));
        goalSelector.addGoal(9, new InteractGoal(this, 15.0F, 1.0F));
        goalSelector.addGoal(10, new LookAtMobGoal(this, Mob.class, 15.0F, 0.02F));
        targetSelector.addGoal(1, new HurtByTargetGoal(this, Raider.class).setAlertOthers());
        targetSelector.addGoal(2, new NearestAttackableTargetGoal(this, Player.class, true));
        targetSelector.addGoal(3, new NearestAttackableTargetGoal(this, Villager.class, false));
        targetSelector.addGoal(3, new NearestAttackableTargetGoal(this, IronGolem.class, true));
    }

    public boolean isChargingCrossbow() { return (Boolean) entityData.get(DATA_CHARGING_STATE); }

    @Override
    public void setChargingCrossbow(boolean v) { entityData.set(DATA_CHARGING_STATE, v); }

    @Override
    public void onCrossbowAttackPerformed() { noActionTime = 0; }

    @Override
    public IllagerArmPose getArmPose() {
        if (isChargingCrossbow()) return IllagerArmPose.CROSSBOW_CHARGE;
        if (isHolding("minecraft:crossbow")) return IllagerArmPose.CROSSBOW_HOLD;
        return isAggressive() ? IllagerArmPose.ATTACKING : IllagerArmPose.NEUTRAL;
    }

    @Override
    public float getWalkTargetValue(Pos pos, ChunkLevel level) { return 0.0F; }

    @Override
    protected void addAdditionalSaveData(NbtCompound tag) {
        super.addAdditionalSaveData(tag);
        NbtList list = new NbtList((byte) 10);
        for (ItemStack s : inventory) if (!s.isEmpty()) list.add(ItemStackNbt.write(s));
        tag.put("Inventory", list);
    }

    @Override
    protected void readAdditionalSaveData(NbtCompound tag) {
        super.readAdditionalSaveData(tag);
        java.util.Arrays.fill(inventory, ItemStack.EMPTY);
        if (tag.tags().get("Inventory") instanceof NbtList l) {
            int n = 0;
            for (int i = 0; i < l.size() && n < inventory.length; i++) {
                if (l.get(i) instanceof NbtCompound c) {
                    ItemStack s = ItemStackNbt.read(c);
                    if (!s.isEmpty()) inventory[n++] = s;
                }
            }
        }
        setCanPickUpLoot(true);
    }

    @Override
    protected void populateDefaultEquipmentSlots(org.domi.furnace.server.block.RandomSource r, DifficultyInstance difficulty) {
        setItemSlot(EquipmentSlot.MAINHAND, stackOf("minecraft:crossbow"));
    }

    @Override
    public void finalizeSpawn(org.domi.furnace.server.block.RandomSource r, DifficultyInstance difficulty) {
        super.finalizeSpawn(r, difficulty);
        populateDefaultEquipmentSlots(r, difficulty);
        populateDefaultEquipmentEnchantments(r, difficulty);
        if (r.nextInt(300) == 0) {
            ItemStack s = getItemBySlot(EquipmentSlot.MAINHAND);
            if (ItemTools.itemName(s).equals("minecraft:crossbow")) {
                setItemSlot(EquipmentSlot.MAINHAND, ItemTools.with(s, ItemTools.ENCHANTMENTS, Map.of("levels", Map.of("minecraft:piercing", 1))));
            }
        }
    }

    @Override
    public void performRangedAttack(Targetable target, float power) { performCrossbowAttack(this, 1.6F); }

    @Override public void applyRaidBuffs(int wave, boolean captain) { }

    @Override public String getAmbientSound() { return "entity.pillager.ambient"; }

    @Override protected String getDeathSound() { return "entity.pillager.death"; }

    @Override protected String getHurtSound(Damage source) { return "entity.pillager.hurt"; }
    @Override public int getMaxSpawnClusterSize() { return 1; }
}
