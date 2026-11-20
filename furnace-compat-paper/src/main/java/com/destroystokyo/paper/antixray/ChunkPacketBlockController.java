package com.destroystokyo.paper.antixray;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Paper 엑스레이 막기 조절기(NMS shim 5e-3 — Axiom 이 접속 때 {@code shouldModify} 로 엑스레이 막기가 켜졌는지 본다).
 * Furnace 에는 엑스레이 막기가 없어 모든 월드가 Paper 의 "아무것도 안 함" 조절기({@link #NO_OPERATION_INSTANCE})를 쓴다.
 * 동작은 Paper 1.21.1 의 이 클래스 그대로(javap): 미리 둔 값 없음, 바꿀 것 없음, 블록 변경·왼클릭은 무시.
 * 패킷 정보({@code getChunkPacketInfo}·{@code modifyBlocks})는 쓰는 플러그인이 없어 두지 않는다.
 */
public class ChunkPacketBlockController {

    public static final ChunkPacketBlockController NO_OPERATION_INSTANCE = new ChunkPacketBlockController();

    protected ChunkPacketBlockController() {}

    public BlockState[] getPresetBlockStates(Level level, ChunkPos chunkPos, int sectionY) { return null; }

    public boolean shouldModify(ServerPlayer player, LevelChunk chunk) { return false; }

    public void onBlockChange(Level level, BlockPos blockPos, BlockState newBlockState, BlockState oldBlockState, int flags, int maxUpdateDepth) {}

    public void onPlayerLeftClickBlock(ServerPlayerGameMode serverPlayerGameMode, BlockPos blockPos, ServerboundPlayerActionPacket.Action action,
            Direction direction, int worldHeight, int sequence) {}
}
