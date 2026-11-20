package net.minecraft.server.dedicated;

import net.minecraft.server.MinecraftServer;

/** 전용 서버 shim(Mojang 이름). 서버마다가 아니라 지금 떠 있는 Furnace 서버를 늘 다시 묻는 상태 없는 객체라 하나뿐이다. */
public class DedicatedServer extends MinecraftServer {

    /** 호환 모듈 전용. */
    public static final DedicatedServer INSTANCE = new DedicatedServer();

    private DedicatedServer() {}

    public boolean isDedicatedServer() { return true; }

    /** 바닐라: server.properties sync-chunk-writes(지역 파일을 쓸 때마다 디스크에 내리는가). */
    public boolean forceSynchronousWrites() { return org.domi.furnace.api.Furnace.server().internals().chunks().syncChunkWrites(); }

    /** Paper 처럼 반환형이 전용 서버 목록이다(공변). */
    @Override
    public DedicatedPlayerList getPlayerList() { return DedicatedPlayerList.INSTANCE; }
}
