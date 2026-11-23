// 블록 상태(TileState)의 Bukkit PersistentDataContainer — 블록 엔티티 NBT 사본의 PublicBukkitValues 를 메모리로 들고 있다가 update 때 사본에 되쓴다
// (Paper 는 스냅숏 블록 엔티티의 persistentDataContainer 를 고치고 update 때 싣는다).
package org.domi.furnace.compat.paper;

import org.bukkit.persistence.PersistentDataContainer;
import org.domi.furnace.api.nbt.NbtCompound;

public final class TileStatePdc {

    private TileStatePdc() {}

    /** PublicBukkitValues 사본 → 고칠 수 있는 메모리 컨테이너. */
    public static PersistentDataContainer of(NbtCompound publicBukkitValues) { return PdcViews.craft(ItemPdc.fromNbt(publicBukkitValues)); }

    /** 컨테이너 내용 → NBT compound({@link #of} 로 만든 것만). */
    public static NbtCompound toNbt(PersistentDataContainer c) { return ((ItemPdc) PdcViews.body((org.bukkit.craftbukkit.persistence.CraftPersistentDataContainer) c)).toNbt(); }
}
