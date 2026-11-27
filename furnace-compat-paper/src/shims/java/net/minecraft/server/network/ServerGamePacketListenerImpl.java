package net.minecraft.server.network;

import net.minecraft.server.level.ServerPlayer;

/** 서버 쪽 게임 처리자 shim(Mojang 이름, H7c). {@code ServerPlayer#connection} 이 이것이다. */
class ServerGamePacketListenerImpl {

    /** 바닐라처럼 공개 필드. */
    public final ServerPlayer player;

    /** 호환 모듈 전용(ServerPlayer 가 만든다). */
    public ServerGamePacketListenerImpl(ServerPlayer player) {
        super(player);
        this.player = player;
    }

    public ServerPlayer getPlayer() { return player; }

    /**
     * 바닐라: seq 번 블록 요청까지 처리했다고 알린다(NMS shim 5d — Axiom 블록 놓기). 알림은 그 플레이어가 선 칸의 region 틱 끝, 그 틱의 블록 변경 방송 뒤에
     * 나간다(바닐라: 다음 연결 틱 첫머리 — 같은 차례). 아무 스레드. 음수면 바닐라처럼 IllegalArgumentException.
     */
    public void ackBlockChangesUpTo(int sequence) {
        org.domi.furnace.compat.nms.NmsChunks.bridge().ackBlockChanges(player.getBukkitEntity().furnaceHandle(), sequence);
    }
}
