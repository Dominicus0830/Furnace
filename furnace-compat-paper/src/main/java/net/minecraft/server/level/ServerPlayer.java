package net.minecraft.server.level;

import org.bukkit.craftbukkit.entity.CraftPlayer;

/**
 * 접속한 플레이어 shim(Mojang 이름, H6 최소 표면): {@code ((CraftPlayer) p).getHandle()}.
 * 패킷 연결({@code connection.send(...)}, 옛 {@code playerConnection.sendPacket})은 H7 패킷 묶음이 더한다 — 지금 그 필드에 닿으면
 * 호출 위치에서 NoSuchFieldError 가 난다(조용히 무시하지 않는다).
 */
public class ServerPlayer extends net.minecraft.world.entity.player.Player {

    /** 호환 모듈 전용(CraftPlayer 가 만든다). */
    public ServerPlayer(CraftPlayer bukkit) { super(bukkit); }

    @Override
    public CraftPlayer getBukkitEntity() { return (CraftPlayer) super.getBukkitEntity(); }

    /** 이 플레이어가 있는 월드. */
    public ServerLevel serverLevel() { return (ServerLevel) level(); }
}
