// 바닐라 Horse: 털 색 7가지 × 무늬 5가지(변종 정수 하나), 체력·속도·점프가 무작위. 말·당나귀와 번식(말+말=말, 말+당나귀=노새).
// 몸통 갑옷(말 갑옷 4종)을 입는다 — 우클릭이나 인벤토리 창의 갑옷 칸. 방어 수치는 장비 칸 속성 수정자(detectEquipmentUpdates)가 단다.
package org.domi.furnace.server.entity;

import org.domi.furnace.server.attribute.Attribute;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.protocol.play.SetEntityData;

public class Horse extends AbstractHorse {
    public static final int DATA_ID_TYPE_VARIANT = 18;
    private static final int COLORS = 7, MARKINGS = 5;

    public Horse(ChunkLevel level, int id) { super(EntityType.HORSE, level, id); }

    @Override
    protected void randomizeAttributes(org.domi.furnace.server.block.RandomSource r) {
        attributes().get(Attribute.MAX_HEALTH).setBaseValue(a(r::nextInt));
        attributes().get(Attribute.MOVEMENT_SPEED).setBaseValue(c(r::nextDouble));
        attributes().get(Attribute.JUMP_STRENGTH).setBaseValue(b(r::nextDouble));
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_ID_TYPE_VARIANT, SetEntityData.SERIALIZER_INT, 0);
    }

    private int variant() { return (Integer) entityData.get(DATA_ID_TYPE_VARIANT); }

    private void setVariantRaw(int v) { entityData.set(DATA_ID_TYPE_VARIANT, v); }

    public int color() { return variant() & 0xFF; }

    public int markings() { return (variant() & 0xFF00) >> 8; }

    public void setVariantAndMarkings(int color, int marking) { setVariantRaw(color & 0xFF | marking << 8 & 0xFF00); }

    @Override
    protected void addAdditionalSaveData(NbtCompound tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Variant", variant());
    }

    @Override
    protected void readAdditionalSaveData(NbtCompound tag) {
        super.readAdditionalSaveData(tag);
        setVariantRaw(tag.getIntOr0("Variant"));
    }

    /** canUseSlot(BODY): 말은 늘 된다. */
    @Override public boolean canUseBodySlot() { return true; }

    /** isBodyArmorItem: AnimalArmorItem 중 말용(EQUESTRIAN). */
    @Override
    public boolean isBodyArmorItem(ItemStack s) {
        if (s.isEmpty()) return false;
        return switch (org.domi.furnace.server.item.ItemTools.itemName(s)) {
            case "minecraft:leather_horse_armor", "minecraft:iron_horse_armor", "minecraft:golden_horse_armor", "minecraft:diamond_horse_armor" -> true;
            default -> false;
        };
    }

    /** containerChanged: 몸통 갑옷이 (틱 20 뒤) 다른 말 갑옷으로 바뀌었으면 갑옷 소리. 1.21.1 에선 갑옷이 인벤토리 밖이라 사실상 울리지 않는다. */
    @Override
    protected void containerChanged() {
        ItemStack before = getItemBySlot(EquipmentSlot.BODY);
        super.containerChanged();
        ItemStack after = getItemBySlot(EquipmentSlot.BODY);
        if (tickCount > 20 && isBodyArmorItem(after) && before != after) playSound("entity.horse.armor", 0.5F, 1.0F);
    }

    @Override public String getAmbientSound() { return "entity.horse.ambient"; }

    @Override protected String getDeathSound() { return "entity.horse.death"; }

    @Override protected String getEatingSound() { return "entity.horse.eat"; }

    @Override protected String getHurtSound(Damage source) { return "entity.horse.hurt"; }

    @Override protected String getAngrySound() { return "entity.horse.angry"; }

    @Override
    public boolean interact(Player p, int hand, ItemStack held, org.domi.furnace.server.world.Region region) {
        boolean sneakingOwner = !isBaby() && isTamed() && p.sneaking();
        if (!isVehicle() && !sneakingOwner && !held.isEmpty()) {
            if (isFood(held)) return fedFood(p, hand, held);
            if (!isTamed()) {
                makeMad();
                return true;
            }
        }
        return super.interact(p, hand, held, region);
    }

    @Override
    public boolean canMate(Animal other) {
        if (other == this) return false;
        return (other instanceof Donkey || other instanceof Horse) && canParent() && ((AbstractHorse) other).canParent();
    }

    @Override
    public AgeableMob getBreedOffspring(ChunkLevel level, AgeableMob partner) {
        if (partner instanceof Donkey) {
            Mule mule = new Mule(level, level.nextEntityId());
            setOffspringAttributes(partner, mule);
            return mule;
        }
        Horse other = (Horse) partner;
        Horse child = new Horse(level, level.nextEntityId());
        int i = random.nextInt(9);
        int color = i < 4 ? color() : i < 8 ? other.color() : random.nextInt(COLORS);
        int j = random.nextInt(5);
        int marking = j < 2 ? markings() : j < 4 ? other.markings() : random.nextInt(MARKINGS);
        child.setVariantAndMarkings(color, marking);
        setOffspringAttributes(partner, child);
        return child;
    }

    public static final class HorseGroupData extends AgeableMob.GroupData {
        public final int variant;

        public HorseGroupData(int variant) {
            super(true);
            this.variant = variant;
        }
    }

    @Override
    public AgeableMob.GroupData finalizeSpawn(org.domi.furnace.server.block.RandomSource r, DifficultyInstance difficulty, AgeableMob.GroupData data) {
        int color;
        if (data instanceof HorseGroupData g) {
            color = g.variant;
        } else {
            color = r.nextInt(COLORS);
            data = new HorseGroupData(color);
        }
        setVariantAndMarkings(color, r.nextInt(MARKINGS));
        return super.finalizeSpawn(r, difficulty, data);
    }
}
