package net.minecraft.server;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.level.ServerLevel;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;

/**
 * 서버 shim(Mojang 이름, H6 최소 표면): {@code ((CraftServer) Bukkit.getServer()).getServer()}, {@code MinecraftServer.getServer()}.
 * 읽기 질의만 있다. "서버 스레드에서 돌려라"·틱 수 같은 단일 틱 가정 표면은 Furnace 의 region 멀티스레드 모델로 같은 의미를 줄 수 없어 두지 않는다
 * (설계 H5 2.1 — 대체: Furnace 스케줄러·Bukkit 스케줄러).
 */
public abstract class MinecraftServer {

    /** CraftBukkit 이 더한 필드: Bukkit 서버(실제 jar 9개가 읽는다 — compat-h4-coverage 4장). */
    public CraftServer server = CraftServer.INSTANCE;

    protected MinecraftServer() {}

    /** CraftBukkit 이 더한 정적 입구: 지금 서버. */
    public static MinecraftServer getServer() { return DedicatedServer.INSTANCE; }

    /** 바닐라 서버 버전 이름(1.21.1). */
    public String getServerVersion() { return CraftServer.INSTANCE.getMinecraftVersion(); }

    public int getPlayerCount() { return CraftServer.INSTANCE.getOnlinePlayers().size(); }

    public int getMaxPlayers() { return CraftServer.INSTANCE.getMaxPlayers(); }

    public String getMotd() { return CraftServer.INSTANCE.getMotd(); }

    /** 불러온 월드 전부의 shim. */
    public Iterable<ServerLevel> getAllLevels() {
        List<ServerLevel> out = new ArrayList<>();
        for (var w : CraftServer.INSTANCE.getWorlds()) out.add(((CraftWorld) w).getHandle());
        return out;
    }
}
