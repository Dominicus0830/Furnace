// 바닐라 Fireball(AbstractHurtingProjectile + 아이템 모양): 클라이언트가 그릴 아이템(기본 불쏘시개)을 데이터(8)로 가진다. 맞을 수 없다(hurt 거짓).
package org.domi.furnace.server.entity;

import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.protocol.play.SetEntityData;

public abstract class Fireball extends AbstractHurtingProjectile {
    public static final int DATA_ITEM_STACK = 8;

    protected Fireball(EntityType type, ChunkLevel level, int id) { super(type, level, id); }

    private static ItemStack defaultItem() { return new ItemStack(org.domi.furnace.server.item.ItemContext.id("minecraft:item", "fire_charge"), 1); }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_ITEM_STACK, SetEntityData.SERIALIZER_ITEM, defaultItem());
    }

    public ItemStack getItem() { return (ItemStack) entityData.get(DATA_ITEM_STACK); }

    public void setItem(ItemStack stack) { entityData.set(DATA_ITEM_STACK, stack.isEmpty() ? defaultItem() : stack.withCount(1)); }

    @Override
    protected void addAdditionalSaveData(NbtCompound tag) {
        super.addAdditionalSaveData(tag);
        tag.putCompound("Item", ItemStackNbt.write(getItem()));
    }

    @Override
    protected void readAdditionalSaveData(NbtCompound tag) {
        super.readAdditionalSaveData(tag);
        if (tag.tags().get("Item") instanceof NbtCompound c) setItem(ItemStackNbt.read(c));
        else setItem(ItemStack.EMPTY);
    }
}
