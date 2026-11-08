package net.minecraft.server.network;

import net.minecraft.server.level.ServerPlayer;

/** 서버 쪽 게임 처리자 shim(Mojang 이름, H7c). {@code ServerPlayer#connection} 이 이것이다. */
public class ServerGamePacketListenerImpl extends ServerCommonPacketListenerImpl {

    /** 바닐라처럼 공개 필드. */
    public final ServerPlayer player;

    /** 호환 모듈 전용(ServerPlayer 가 만든다). */
    public ServerGamePacketListenerImpl(ServerPlayer player) {
        super(player);
        this.player = player;
    }

    public ServerPlayer getPlayer() { return player; }
}
