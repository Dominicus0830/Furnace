// 생성 조각 — tools/compat/gen-nms-block-entities.py 로 만든다(손으로 고치지 않는다).
package net.minecraft.world.level.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 바닐라 BaseContainerBlockEntity(Mojang 이름 shim, N2b-6). 플러그인이 종류별 클래스로 가르는 {@code instanceof} 와 형변환이 돌게 하는 계층 자리다 —
 * 종류별 필드·메서드(컨테이너·틱 등)는 두지 않았고 저장은 {@link BlockEntity} 가 서버의 NBT 로 한다.
 */
class BaseContainerBlockEntity {

    protected BaseContainerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) { super(type, pos, state); }

    /** 바닐라 clearContent(Clearable) — 월드의 블록 엔티티면 서버 블록 엔티티를 바닐라처럼 비운다(T-211 3단계). */
    @Override
    public void clearContent() { furnaceClearContent(); }
}
