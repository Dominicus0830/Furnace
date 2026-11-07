package net.minecraft.server.dedicated;

import net.minecraft.server.MinecraftServer;

/** 전용 서버 shim(Mojang 이름). 서버마다가 아니라 지금 떠 있는 Furnace 서버를 늘 다시 묻는 상태 없는 객체라 하나뿐이다. */
public class DedicatedServer extends MinecraftServer {

    /** 호환 모듈 전용. */
    public static final DedicatedServer INSTANCE = new DedicatedServer();

    private DedicatedServer() {}

    public boolean isDedicatedServer() { return true; }
}
