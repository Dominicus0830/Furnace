// 생성 파일 — tools/compat/gen-nms-block-entities.py 로 만든다(손으로 고치지 않는다).
package net.minecraft.world.level.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 바닐라 BeaconBlockEntity(Mojang 이름 shim, N2b-6) — 블록 엔티티 종류 {@code minecraft:beacon}. 종류별 필드·메서드는 두지 않았고
 * 플러그인이 종류별 클래스로 가르는 {@code instanceof} 와 형변환이 돌게 하는 계층 자리다(저장은 {@link BlockEntity} 가 서버의 NBT 로 한다).
 */
public class BeaconBlockEntity extends BlockEntity {

    public BeaconBlockEntity(BlockPos pos, BlockState state) { this(BlockEntityType.furnaceOf("minecraft:beacon"), pos, state); }

    /** 하위 종류가 쓴다(바닐라는 BeaconBlockEntity 이 종류를 받는 생성자를 둔다). */
    protected BeaconBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) { super(type, pos, state); }

    /**
     * 바닐라 BeaconBlockEntity.playSound(level, pos, sound): 그 칸 가운데에서 블록 소리 분류로 크기·높이 1(level.playSound(null, pos, …)) — 그 칸
     * 근처 플레이어가 듣는다(T-198 — FAWE 가 신호기를 지운 뒤 끄는 소리를 낸다). 그 칸의 주인 스레드(Bukkit 동기 작업 포함)에서 부른다.
     */
    public static void playSound(net.minecraft.world.level.Level level, BlockPos pos, net.minecraft.sounds.SoundEvent sound) {
        level.playSound(null, pos, sound, net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.0F);
    }
}
