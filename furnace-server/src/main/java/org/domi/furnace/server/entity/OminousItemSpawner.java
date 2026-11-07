// 바닐라 OminousItemSpawner: 불길한 시련 방이 아이템을 내보내기 전에 잠시 머무는 보이지 않는 엔티티. 60~120틱 뒤(36틱 전에 소리) 아이템 엔티티를 만들고 사라진다.
// 발사체 아이템(화살·물약·폭죽·화염구 …)은 아이템 대신 아래로 쏜 발사체로 나온다(ProjectileItem.asProjectile + shoot).
package org.domi.furnace.server.entity;

import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.protocol.play.SetEntityData;

public class OminousItemSpawner extends Entity {

    /** getPistonPushReaction = IGNORE. */
    @Override
    public boolean ignoresPistonPush() { return true; }

    public static final int DATA_ITEM = 8;
    private long spawnItemAfterTicks;

    public OminousItemSpawner(ChunkLevel level, int id) {
        super(EntityType.OMINOUS_ITEM_SPAWNER, level, id);
        noPhysics = true;
    }

    public static OminousItemSpawner create(ChunkLevel level, ItemStack stack) {
        OminousItemSpawner e = new OminousItemSpawner(level, level.nextEntityId());
        e.spawnItemAfterTicks = 60 + level.random().nextInt(61);
        e.setItem(stack);
        return e;
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_ITEM, SetEntityData.SERIALIZER_ITEM, ItemStack.EMPTY);
    }

    public ItemStack getItem() { return (ItemStack) entityData.get(DATA_ITEM); }

    private void setItem(ItemStack s) { entityData.set(DATA_ITEM, s); }

    @Override
    public void tick() {
        super.tick();
        if (tickCount == spawnItemAfterTicks - 36L) level.playSound(null, x(), y(), z(), "minecraft:block.trial_spawner.about_to_spawn_item", "hostile", 1.0F, 1.0F);
        if (tickCount >= spawnItemAfterTicks) {
            spawnItem();
            kill();
        }
    }

    private void spawnItem() {
        ItemStack stack = getItem();
        if (stack.isEmpty()) return;
        Entity out = org.domi.furnace.server.block.dispenser.DispenseBehaviors.projectileDown(level, x(), y(), z(), blockPosition(), stack);
        if (out instanceof AbstractArrow arrow) arrow.pickup = AbstractArrow.Pickup.DISALLOWED;   // AbstractArrow.setOwner: 주인이 OminousItemSpawner 면 줍기 불가
        if (out == null) out = new ItemEntity(level, level.nextEntityId(), x(), y(), z(), stack);
        level.addFreshEntity(out);
        level.levelEvent(null, 3021, blockPosition(), 1);
        level.gameEvent(org.domi.furnace.server.world.gameevent.GameEvent.ENTITY_PLACE, new Vec3(x(), y(), z()),
                org.domi.furnace.server.world.gameevent.GameEventContext.of(out));   // btu:w 나온 엔티티가 출처, 자리는 생성기
        setItem(ItemStack.EMPTY);
    }

    @Override public boolean isPickable() { return true; }

    @Override
    protected void addAdditionalSaveData(NbtCompound tag) {
        if (!getItem().isEmpty()) tag.putCompound("item", ItemStackNbt.write(getItem()));
        tag.putLong("spawn_item_after_ticks", spawnItemAfterTicks);
    }

    @Override
    protected void readAdditionalSaveData(NbtCompound tag) {
        setItem(tag.tags().get("item") instanceof NbtCompound c ? ItemStackNbt.read(c) : ItemStack.EMPTY);
        spawnItemAfterTicks = tag.tags().get("spawn_item_after_ticks") instanceof Number n ? n.longValue() : 0L;
    }
}
