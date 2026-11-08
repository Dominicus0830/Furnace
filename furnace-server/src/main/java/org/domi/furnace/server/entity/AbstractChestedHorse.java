// 바닐라 AbstractChestedHorse(당나귀·노새): 상자를 달 수 있고(저장 'Items'), 달면 인벤토리가 5열(칸 16)로 늘어난다. 체력만 무작위(15~30), 속도 0.175.
package org.domi.furnace.server.entity;

import org.domi.furnace.server.attribute.Attribute;
import org.domi.furnace.server.attribute.AttributeMap;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.item.ItemTools;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtList;
import org.domi.furnace.server.net.protocol.play.SetEntityData;

public abstract class AbstractChestedHorse extends AbstractHorse {
    public static final int DATA_ID_CHEST = 18;

    protected AbstractChestedHorse(EntityType type, ChunkLevel level, int id) { super(type, level, id); }

    @Override
    protected void randomizeAttributes(org.domi.furnace.server.block.RandomSource r) {
        attributes().get(Attribute.MAX_HEALTH).setBaseValue(a(r::nextInt));
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_ID_CHEST, SetEntityData.SERIALIZER_BOOLEAN, false);
    }

    @Override
    protected void createAttributes(AttributeMap map) {
        super.createAttributes(map);
        map.add(Attribute.MOVEMENT_SPEED, 0.175F).add(Attribute.JUMP_STRENGTH, 0.5);
    }

    public boolean hasChest() { return (Boolean) entityData.get(DATA_ID_CHEST); }

    public void setChest(boolean v) { entityData.set(DATA_ID_CHEST, v); }

    @Override
    public int inventoryColumns() { return hasChest() ? 5 : 0; }

    /**
     * getSlot(499)(horse.chest)의 set: 빈 스택이면 상자를 떼고, 상자면 단다 — 바뀌면 인벤토리를 다시 만든다(줄어든 칸의 물건은 바닐라처럼 사라진다).
     * 그 밖의 아이템은 거절(false). 엔티티의 region 스레드에서.
     */
    public boolean setChestFromSlot(ItemStack s) {
        if (s.isEmpty()) {
            if (hasChest()) {
                setChest(false);
                createInventory();
            }
            return true;
        }
        if (!ItemTools.itemName(s).equals("minecraft:chest")) return false;
        if (!hasChest()) {
            setChest(true);
            createInventory();
        }
        return true;
    }

    @Override
    protected void dropEquipment() {
        super.dropEquipment();
        if (hasChest()) {
            spawnAtLocation(new ItemStack(ItemContext.id("minecraft:item", "chest"), 1));
            afterDeathDrop(() -> setChest(false));   // 사망 이벤트가 취소되면 상자는 그대로(Paper postDeathDropItems)
        }
    }

    @Override
    protected void addAdditionalSaveData(NbtCompound tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("ChestedHorse", hasChest());
        if (hasChest()) {
            NbtList items = new NbtList((byte) 10);
            for (int i = 1; i < inventory.length; i++) {
                ItemStack s = inventory[i];
                if (!s.isEmpty()) {
                    NbtCompound c = ItemStackNbt.write(s);
                    c.putByte("Slot", (byte) (i - 1));
                    items.add(c);
                }
            }
            tag.put("Items", items);
        }
    }

    @Override
    protected void readAdditionalSaveData(NbtCompound tag) {
        super.readAdditionalSaveData(tag);
        setChest(tag.getBoolean("ChestedHorse"));
        createInventory();
        if (hasChest() && tag.tags().get("Items") instanceof NbtList l) {
            for (int i = 0; i < l.size(); i++) {
                if (l.get(i) instanceof NbtCompound c) {
                    int slot = c.getByte("Slot") & 255;
                    if (slot < inventory.length - 1) inventory[slot + 1] = ItemStackNbt.read(c);
                }
            }
        }
        updateContainerEquipment();
    }

    @Override
    public boolean interact(Player p, int hand, ItemStack held, org.domi.furnace.server.world.Region region) {
        boolean sneakingOwner = !isBaby() && isTamed() && p.sneaking();
        if (!isVehicle() && !sneakingOwner && !held.isEmpty()) {
            if (isFood(held)) return fedFood(p, hand, held);
            if (!isTamed()) {
                makeMad();
                return true;
            }
            if (!hasChest() && ItemTools.itemName(held).equals("minecraft:chest")) {
                setChest(true);
                playChestEquipsSound();
                usePlayerItem(p, hand, held);
                createInventory();
                return true;
            }
        }
        return super.interact(p, hand, held, region);
    }

    protected void playChestEquipsSound() { playSound("entity.donkey.chest", 1.0F, (random.nextFloat() - random.nextFloat()) * 0.2F + 1.0F); }

    @Override
    protected void followMother() { super.followMother(); }

    /** AbstractChestedHorse.playGallopSound: 질주 소리에 10분의 1 확률로 숨소리(크기 ×0.6). */
    @Override
    protected void playGallopSound(org.domi.furnace.server.block.BlockSounds.SoundType type) {
        super.playGallopSound(type);
        if (random.nextInt(10) == 0) playSound("minecraft:entity.horse.breathe", type.volume() * 0.6F, type.pitch());
    }
}
