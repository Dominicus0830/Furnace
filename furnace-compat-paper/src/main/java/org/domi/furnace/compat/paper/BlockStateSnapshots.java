package org.domi.furnace.compat.paper;

import java.util.Objects;
import org.bukkit.block.data.BlockData;
import org.bukkit.craftbukkit.block.CraftBlockState;
import org.domi.furnace.api.world.BlockPos;

/** 블록 상태 스냅숏을 만드는 호환 모듈 쪽 입구(T-264 P2c 전 CompatBlockState 의 정적 메서드). 형은 Paper 와 같다(CompatBlockStates). */
public final class BlockStateSnapshots {
    private BlockStateSnapshots() {}

    /** 이 칸의 지금 블록을 스냅숏으로 — 소유 스레드에서. 블록 엔티티 없는 블록과 같은 일반 형(Paper CraftBlockState). */
    static CraftBlockState of(String dimension, BlockPos pos) {
        return new CraftBlockState(Objects.requireNonNull(dimension, "dimension"), pos, CompatBlockData.of(Compat.furnace().world(dimension).block(pos).data()));
    }

    /** Furnace 블록 데이터로 만든 스냅숏(이벤트의 "새 상태"). */
    static CraftBlockState of(String dimension, BlockPos pos, org.domi.furnace.api.world.BlockData data) {
        return new CraftBlockState(Objects.requireNonNull(dimension, "dimension"), pos, CompatBlockData.of(data));
    }

    /**
     * 호환 모듈 전용(T-211, Paper BlockStateListPopulator): 월드에 아직 없는 블록 상태 state 를 그 칸에 놓인 상태로 — 블록 엔티티가 있는 블록은
     * Paper 와 같은 형(CraftChest 등)이고 그 내용은 새 블록 엔티티(빈 내용)다. 월드를 읽지 않는다.
     */
    public static CraftBlockState generated(String dimension, BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        Objects.requireNonNull(dimension, "dimension");
        BlockData data = org.bukkit.craftbukkit.block.data.CraftBlockData.fromData(state);
        return org.bukkit.craftbukkit.block.CraftBlockEntityState.furnaceFresh(() -> CompatBlockStates.create(dimension, pos, data));
    }
}
