package net.minecraft.server.level;

import org.bukkit.craftbukkit.entity.CraftPlayer;

/**
 * 접속한 플레이어 shim(Mojang 이름, H6 최소 표면): {@code ((CraftPlayer) p).getHandle()}.
 * 패킷 연결은 {@code connection.send(packet)}(H7c) — 이 모듈의 clientbound 패킷 shim 만 보낼 수 있다.
 * 옛 이름 {@code playerConnection.sendPacket}(Spigot 1.16 이하)은 없다 — 그 호출 위치에서 UnsupportedNmsCall 이 난다.
 */
public class ServerPlayer extends net.minecraft.world.entity.player.Player {

    /** 호환 모듈 전용(CraftPlayer 가 만든다). */
    public ServerPlayer(CraftPlayer bukkit) {
        super(bukkit);
        this.connection = new net.minecraft.server.network.ServerGamePacketListenerImpl(this);
    }

    /** 바닐라처럼 공개 필드(Paper 도 같다). */
    public net.minecraft.server.network.ServerGamePacketListenerImpl connection;

    @Override
    public CraftPlayer getBukkitEntity() { return (CraftPlayer) super.getBukkitEntity(); }

    /** 이 플레이어가 있는 월드. */
    public ServerLevel serverLevel() { return (ServerLevel) level(); }
}
