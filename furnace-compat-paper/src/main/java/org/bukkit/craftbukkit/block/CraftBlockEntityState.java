package org.bukkit.craftbukkit.block;

import org.domi.furnace.api.nbt.NbtCompound;
import org.domi.furnace.api.world.BlockPos;
import org.domi.furnace.compat.paper.Compat;

/**
 * Paper CraftBlockEntityState: 블록 엔티티가 있는 블록의 상태. 만들 때 블록 엔티티 NBT 사본(스냅숏)을 떠 두고, 하위 클래스(표지판 등)는
 * 그 사본을 읽고 고친다. {@link #update} 가 블록을 쓴 뒤 같은 종류의 블록 엔티티가 그 칸에 있으면 사본을 통째로 싣는다(Paper applyTo·setChanged).
 * 놓이지 않은 상태와 블록 엔티티가 없는 칸은 id 만 든 사본(나머지는 바닐라 기본값으로 읽힌다)에서 시작한다 — Paper 는 새 블록 엔티티의
 * 전체 NBT 를 주므로 {@link #getSnapshotNBT} 에 기본값 칸이 빠지는 것만 다르다.
 */
public abstract class CraftBlockEntityState extends org.domi.furnace.compat.paper.CompatBlockState implements org.bukkit.block.TileState {

    private final NbtCompound snapshot;

    protected CraftBlockEntityState(String dimension, BlockPos pos, org.bukkit.block.data.BlockData data) {
        super(dimension, pos, data);
        NbtCompound n = dimension == null ? null : Compat.furnace().world(dimension).blockEntityNbtNow(pos).orElse(null);
        if (n == null) {
            n = new NbtCompound();
            String id = Compat.furnace().gameRegistries().blockEntityType(((org.domi.furnace.compat.paper.CompatBlockData) data).furnace().type());
            if (id != null) n.putString("id", id);   // Paper createTileEntity 자리: update 때 새로 생긴 같은 종류 블록 엔티티에 실린다
        }
        this.snapshot = n;
    }

    /** 블록 엔티티 NBT 사본(바꾸면 update 때 싣는다). */
    protected final NbtCompound snapshotNbt() { return snapshot; }

    /** 하위 클래스가 미뤄 둔 값(표지판 글 등)을 사본에 적는다(Paper applyTo 의 앞부분). */
    protected void applyToSnapshot() {}

    /** 지금 사본(미뤄 둔 값까지 적은 것)의 복사본 — Paper getSnapshotNBT. */
    public NbtCompound getSnapshotNBT() {
        applyToSnapshot();
        return snapshot.copy();
    }

    /**
     * TileStateInventoryHolder#getInventory 의 몸(용기 형만 그 인터페이스를 단다). Paper: 놓인 상태면 그 칸 블록 엔티티의 살아 있는 인벤토리,
     * 놓이지 않았으면 스냅숏 인벤토리. 스냅숏 인벤토리(NBT Items ↔ 아이템)는 아직 없어 UnsupportedFeature.
     */
    public org.bukkit.inventory.Inventory getInventory() {
        if (!isPlaced()) return getSnapshotInventory();
        org.bukkit.inventory.Inventory live = org.domi.furnace.compat.paper.BlockInventories.live(dimension(), position());
        if (live == null) {
            throw new org.domi.furnace.compat.paper.UnsupportedFeature(getClass().getSimpleName() + "#getInventory",
                    "Furnace 가 이 블록 엔티티를 아직 용기로 다루지 않는다(또는 블록 엔티티가 사라졌다)");
        }
        return live;
    }

    /** Paper: 이 상태의 사본 인벤토리(update 때 싣는다). NBT 아이템 변환이 아직 없어 UnsupportedFeature. */
    public org.bukkit.inventory.Inventory getSnapshotInventory() {
        throw new org.domi.furnace.compat.paper.UnsupportedFeature(getClass().getSimpleName() + "#getSnapshotInventory", "블록 상태의 스냅숏 인벤토리가 아직 없다");
    }

    @Override
    public boolean update(boolean force, boolean applyPhysics) {
        boolean result = super.update(force, applyPhysics);
        if (result && isPlaced()) {
            applyToSnapshot();
            var world = Compat.furnace().world(dimension());
            var live = world.blockEntityNbtNow(position());
            // Paper isApplicable: 그 칸의 블록 엔티티가 같은 종류일 때만
            if (live.isPresent() && live.get().getString("id").equals(snapshot.getString("id"))) {
                world.setBlockEntityNbtNow(position(), snapshot.copy());
            }
        }
        return result;
    }
}
