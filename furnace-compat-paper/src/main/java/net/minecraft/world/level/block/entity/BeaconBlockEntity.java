package net.minecraft.world.level.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** 신호기 블록 엔티티(Mojang 이름 shim, N2b-6) - FAWE 가 instanceof 로 구별한다. 신호기 동작(범위·효과)은 서버 안에 있어 두지 않았다. */
public class BeaconBlockEntity extends BlockEntity {

    public BeaconBlockEntity(BlockPos pos, BlockState state) { super(BlockEntityType.furnaceOf("minecraft:beacon"), pos, state); }
}
