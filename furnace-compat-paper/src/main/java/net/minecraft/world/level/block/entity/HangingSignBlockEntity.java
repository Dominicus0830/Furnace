// 생성 파일 — tools/compat/gen-nms-block-entities.py 로 만든다(손으로 고치지 않는다).
package net.minecraft.world.level.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 바닐라 HangingSignBlockEntity(Mojang 이름 shim, N2b-6) — 블록 엔티티 종류 {@code minecraft:hanging_sign}. 종류별 필드·메서드는 두지 않았고
 * 플러그인이 종류별 클래스로 가르는 {@code instanceof} 와 형변환이 돌게 하는 계층 자리다(저장은 {@link BlockEntity} 가 서버의 NBT 로 한다).
 */
public class HangingSignBlockEntity extends SignBlockEntity {

    public HangingSignBlockEntity(BlockPos pos, BlockState state) { this(BlockEntityType.furnaceOf("minecraft:hanging_sign"), pos, state); }

    /** 하위 종류가 쓴다(바닐라는 HangingSignBlockEntity 이 종류를 받는 생성자를 둔다). */
    protected HangingSignBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) { super(type, pos, state); }
}
