package net.minecraft.server.level;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.GameType;

/**
 * 플레이어의 게임 모드·블록 부수기({@code ServerPlayer#gameMode}, NMS shim 3번 묶음 — VeinMiner·squaremap). 서버 상태를 따로 들지 않는 보기다:
 * 게임 모드는 그 순간 플레이어의 것을 읽고, 블록 부수기는 서버의 같은 길로 보낸다. 부수는 중인 칸·진행도 같은 Paper 의 내부 필드는 두지 않는다.
 */
public class ServerPlayerGameMode {

    /** 바닐라: 이 게임 모드의 플레이어. */
    protected final ServerPlayer player;

    public ServerPlayerGameMode(ServerPlayer player) { this.player = java.util.Objects.requireNonNull(player, "player"); }

    /** 바닐라: 지금 게임 모드. */
    public GameType getGameModeForPlayer() { return GameType.valueOf(player.getBukkitEntity().getGameMode().name()); }

    /** 바닐라: 서바이벌·모험이면 참. */
    public boolean isSurvival() { return getGameModeForPlayer().isSurvival(); }

    /** 바닐라: 크리에이티브면 참. */
    public boolean isCreative() { return getGameModeForPlayer().isCreative(); }

    /**
     * 바닐라 {@code destroyBlock(pos)}(Paper 는 블록 부수기 이벤트를 더한다): 그 플레이어가 손으로 부순 것과 같다 — 이벤트를 취소하면 안 부순다, 드롭·경험치·
     * 도구 닳기·허기. 다른 region 의 칸은 그 region 으로 넘기고 true 를 준다(결정 D3-1, {@link org.domi.furnace.compat.nms.NmsBreaking}).
     * 로드되지 않은 칸은 IllegalStateException(Paper 는 그 자리에서 불러온다).
     */
    public boolean destroyBlock(BlockPos pos) { return org.domi.furnace.compat.nms.NmsBreaking.destroyBlock(player, pos); }
}
