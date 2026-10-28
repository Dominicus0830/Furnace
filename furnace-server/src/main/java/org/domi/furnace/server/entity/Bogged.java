// 바닐라 Bogged: 늪 해골. 체력 16, 화살에 독(100틱), 가위로 깎으면 버섯(떨어짐). TODO: 깎기 전리품 표 `shearing/bogged`.
package org.domi.furnace.server.entity;

import org.domi.furnace.server.attribute.Attribute;
import org.domi.furnace.server.attribute.AttributeMap;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.effect.MobEffect;
import org.domi.furnace.server.effect.MobEffectInstance;
import org.domi.furnace.server.item.ItemTools;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.protocol.play.SetEntityData;

public class Bogged extends AbstractSkeleton {
    public static final int DATA_SHEARED = 16;

    public Bogged(ChunkLevel level, int id) { super(EntityType.BOGGED, level, id); }

    @Override
    protected void createAttributes(AttributeMap map) {
        super.createAttributes(map);
        map.add(Attribute.MAX_HEALTH, 16.0);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_SHEARED, SetEntityData.SERIALIZER_BOOLEAN, false);
    }

    public boolean isSheared() { return (Boolean) entityData.get(DATA_SHEARED); }

    public void setSheared(boolean v) { entityData.set(DATA_SHEARED, v); }

    public boolean readyForShearing() { return !isSheared() && isAlive(); }

    @Override
    public boolean interact(Player p, int hand, ItemStack held, org.domi.furnace.server.world.Region region) {
        if (!held.isEmpty() && ItemTools.itemName(held).equals("minecraft:shears") && readyForShearing()) {
            shear("players");
            var inv = p.inventory();
            int ws = hand == 0 ? inv.heldWindowSlot() : 45;
            var actor = new org.domi.furnace.server.block.PlayerActor(p, hand == 0 ? held : ItemStack.EMPTY, hand == 1 ? held : ItemStack.EMPTY, 0);
            org.domi.furnace.server.block.BlockBreaking.hurtAndBreak(level, actor, hand, held, 1);
            if (actor.handChanged(hand)) inv.set(ws, actor.itemInHand(hand));
            return true;
        }
        return super.interact(p, hand, held, region);
    }

    /** Shearable.shear(source): 소리 → 깎임. TODO: 깎기 전리품(shearing/bogged 버섯). */
    public void shear(String source) {
        level.playSound(null, x(), y(), z(), "entity.bogged.shear", source, 1.0F, 1.0F);
        setSheared(true);
    }

    @Override protected void prepareArrow(Arrow arrow) { arrow.addEffect(new MobEffectInstance(MobEffect.POISON, 100)); }

    @Override protected int getHardAttackInterval() { return 50; }

    @Override protected int getAttackInterval() { return 70; }

    @Override public String getAmbientSound() { return "entity.bogged.ambient"; }

    @Override protected String getHurtSound(Damage source) { return "entity.bogged.hurt"; }

    @Override protected String getDeathSound() { return "entity.bogged.death"; }

    @Override String getStepSound() { return "entity.bogged.step"; }

    @Override
    protected void addAdditionalSaveData(NbtCompound tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("sheared", isSheared());
    }

    @Override
    protected void readAdditionalSaveData(NbtCompound tag) {
        super.readAdditionalSaveData(tag);
        setSheared(tag.getBoolean("sheared"));
    }
}
