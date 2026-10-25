// 바닐라 ItemFrame / GlowItemFrame: 블록 면에 걸리는 액자. 아이템 하나를 넣고(우클릭) 돌리며(8방향), 때리면 아이템이 먼저 빠지고 빈 액자는 부서져 떨어진다.
// Fixed(고정)면 건드릴 수 없고, Invisible 이면 투명하다. 폭발에는 아이템과 함께 통째로 부서진다.
// TODO: 지도 아이템(지도 데이터의 액자 연결·배너 확인), 아이템 액자의 비교기 출력 신호는 이웃 갱신만 한다.
package org.domi.furnace.server.entity;

import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.protocol.play.SetEntityData;

public class ItemFrame extends HangingEntity {
    public static final int DATA_ITEM = 8, DATA_ROTATION = 9;
    private float dropChance = 1.0F;
    private boolean fixed;
    private final boolean glow;

    public ItemFrame(ChunkLevel level, int id) { this(EntityType.ITEM_FRAME, level, id, false); }

    public ItemFrame(ChunkLevel level, int id, Pos pos, int direction, boolean glow) {
        this(glow ? EntityType.GLOW_ITEM_FRAME : EntityType.ITEM_FRAME, level, id, glow);
        this.blockPos = pos;
        setDirection(direction);
    }

    protected ItemFrame(EntityType type, ChunkLevel level, int id, boolean glow) {
        super(type, level, id);
        this.glow = glow;
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_ITEM, SetEntityData.SERIALIZER_ITEM, ItemStack.EMPTY);
        entityData.define(DATA_ROTATION, SetEntityData.SERIALIZER_INT, 0);
    }

    private String sound(String kind) { return "minecraft:entity." + (glow ? "glow_item_frame" : "item_frame") + "." + kind; }

    @Override
    protected AABB calculateBoundingBox(Pos pos, int dir) {
        double cx = pos.x() + 0.5 - Dir.DX[dir] * 0.46875, cy = pos.y() + 0.5 - Dir.DY[dir] * 0.46875, cz = pos.z() + 0.5 - Dir.DZ[dir] * 0.46875;
        int axis = Dir.axis(dir);
        double sx = axis == Dir.AXIS_X ? 0.0625 : 0.75, sy = axis == Dir.AXIS_Y ? 0.0625 : 0.75, sz = axis == Dir.AXIS_Z ? 0.0625 : 0.75;
        return new AABB(cx - sx / 2, cy - sy / 2, cz - sz / 2, cx + sx / 2, cy + sy / 2, cz + sz / 2);
    }

    @Override
    public boolean survives() {
        if (fixed) return true;
        if (!level.noCollision(boundingBox())) return false;
        int behind = level.getBlockState(blockPos.relative(Dir.opposite(direction)));
        boolean solid = org.domi.furnace.server.block.B.isSolid(behind)
                || Dir.axis(direction) != Dir.AXIS_Y && org.domi.furnace.server.block.types.DiodeBlock.isDiode(behind);
        return solid && level.getEntities(this, boundingBox(), e -> e instanceof HangingEntity).isEmpty();
    }

    @Override
    public boolean hurtBy(Damage source, float amount, Targetable attacker) {
        if (fixed) return source.bypassesInvulnerability() && super.hurtBy(source, amount, attacker);
        if (isInvulnerableTo(source)) return false;
        if (!source.explosion() && !getItem().isEmpty()) {
            removeItem(attacker, false);
            playSound(sound("remove_item"), 1.0F, 1.0F);
            return true;
        }
        return super.hurtBy(source, amount, attacker);
    }

    @Override
    public void dropItem(Targetable attacker) {
        playSound(sound("break"), 1.0F, 1.0F);
        removeItem(attacker, true);
    }

    @Override
    public void playPlacementSound() { playSound(sound("place"), 1.0F, 1.0F); }

    private void removeItem(Targetable attacker, boolean withFrame) {
        if (fixed) return;
        ItemStack item = getItem();
        setItem(ItemStack.EMPTY, false);
        if (!level.gameRule("doEntityDrops")) return;
        if (attacker instanceof Player p && p.gameMode() == org.domi.furnace.server.world.GameMode.CREATIVE) return;
        if (withFrame) spawnItem(new ItemStack(ItemContext.id("minecraft:item", glow ? "minecraft:glow_item_frame" : "minecraft:item_frame"), 1));
        if (!item.isEmpty() && random.nextFloat() < dropChance) spawnItem(item);
    }

    public ItemStack getItem() { return (ItemStack) entityData.get(DATA_ITEM); }

    public void setItem(ItemStack stack) { setItem(stack, true); }

    public void setItem(ItemStack stack, boolean updateNeighbours) {
        if (!stack.isEmpty()) stack = stack.withCount(1);
        entityData.set(DATA_ITEM, stack);
        if (!stack.isEmpty()) playSound(sound("add_item"), 1.0F, 1.0F);
        if (updateNeighbours && blockPos != null) level.updateNeighbourForOutputSignal(blockPos, org.domi.furnace.server.block.B.id("minecraft:air"));
    }

    public int rotation() { return (Integer) entityData.get(DATA_ROTATION); }

    public void setRotation(int r) { setRotation(r, true); }

    private void setRotation(int r, boolean updateNeighbours) {
        entityData.set(DATA_ROTATION, r % 8);
        if (updateNeighbours && blockPos != null) level.updateNeighbourForOutputSignal(blockPos, org.domi.furnace.server.block.B.id("minecraft:air"));
    }

    @Override
    public boolean interact(Player p, int hand) {
        if (fixed) return false;
        var inv = p.inventory();
        int ws = hand == 0 ? inv.heldWindowSlot() : 45;
        ItemStack held = inv.get(ws);
        boolean hasItem = !getItem().isEmpty();
        if (!hasItem) {
            if (!held.isEmpty() && !isRemoved()) {
                // TODO(지도): 지도 아이템이면 지도 데이터에 이 액자를 연결한다
                setItem(held);
                if (p.gameMode() != org.domi.furnace.server.world.GameMode.CREATIVE) inv.set(ws, held.withCount(held.count() - 1));
            }
        } else {
            playSound(sound("rotate_item"), 1.0F, 1.0F);
            setRotation(rotation() + 1);
        }
        return true;
    }

    @Override
    protected void addAdditionalSaveData(NbtCompound tag) {
        super.addAdditionalSaveData(tag);
        if (!getItem().isEmpty()) {
            tag.putCompound("Item", ItemStackNbt.write(getItem()));
            tag.putByte("ItemRotation", (byte) rotation());
            tag.putFloat("ItemDropChance", dropChance);
        }
        tag.putByte("Facing", (byte) direction);
        tag.putBoolean("Fixed", fixed);
    }

    @Override
    protected void readAdditionalSaveData(NbtCompound tag) {
        super.readAdditionalSaveData(tag);
        ItemStack item = tag.tags().get("Item") instanceof NbtCompound c ? ItemStackNbt.read(c) : ItemStack.EMPTY;
        entityData.set(DATA_ITEM, item.isEmpty() ? ItemStack.EMPTY : item.withCount(1));
        if (!item.isEmpty()) {
            setRotation(tag.getIntOr0("ItemRotation"), false);
            if (tag.containsNumber("ItemDropChance")) dropChance = tag.getFloat("ItemDropChance");
        }
        setDirection(Dir.NAME.length > tag.getIntOr0("Facing") ? tag.getIntOr0("Facing") : Dir.SOUTH);
        fixed = tag.getBoolean("Fixed");
    }
}
