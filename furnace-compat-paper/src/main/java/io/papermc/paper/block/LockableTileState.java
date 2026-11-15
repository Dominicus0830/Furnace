package io.papermc.paper.block;

/** Paper 1.21.1 LockableTileState: 잠글 수 있고 이름을 가진 블록 엔티티 상태(용기·신호기). 몸은 CraftBlockEntityState. */
public interface LockableTileState extends org.bukkit.block.TileState, org.bukkit.block.Lockable, org.bukkit.Nameable {
}
