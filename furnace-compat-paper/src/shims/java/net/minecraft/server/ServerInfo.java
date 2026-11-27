package net.minecraft.server;

/** 서버 정보 질의(Mojang 이름 shim) — 바닐라 1.21.1 과 같다. */
interface ServerInfo {

    String getMotd();

    String getServerVersion();

    int getPlayerCount();

    int getMaxPlayers();
}
