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

    private final net.minecraft.server.network.ServerConnectionListener connection = new net.minecraft.server.network.ServerConnectionListener();

    /**
     * 서버 접속 수신(N3 2단계, 패킷 라이브러리가 소켓 채널 목록을 읽는 자리). 지금은 채널 목록이 비어 있다.
     * 플러그인이 여기 들어오면 패킷 파이프라인에 끼어들려는 것이므로, 아직 가로채기·보내기가 이어지지 않았음을 한 번 알린다.
     */
    public net.minecraft.server.network.ServerConnectionListener getConnection() {
        warnPipelineNotBridged();
        return connection;
    }

    private static void warnPipelineNotBridged() {
        org.domi.furnace.compat.nms.NmsCaller.warnOnce("pipeline",
                "플러그인 {} 가 패킷 파이프라인(ServerConnectionListener)에 끼어들려 한다. Furnace 는 서버 소켓·로그인 전 단계 가로채기를 잇지 않았다 — "
                        + "그 단계(핸드셰이크·상태·로그인)를 보려는 기능은 동작하지 않는다. 접속한 플레이어에게 보내는 패킷은 일부 이어졌다"
                        + "(sendServerPacket·connection.send 와 나가는 가로채기), 서버가 스스로 보내는 패킷·들어오는 패킷 가로채기는 아직이다"
                        + "(docs/feature-parity/compat-protocollib-pipeline-2b-plan-2026-11-12.md). 대신 Furnace 패킷 API(docs/api/packets.md)를 쓴다.");
    }

    /** 바닐라 서버 버전 이름(1.21.1). */
    public String getServerVersion() { return CraftServer.INSTANCE.getMinecraftVersion(); }

    /** 서버 레지스트리 묶음(H7b). Component·데이터 컴포넌트 직렬화 인자로만 쓰인다 — 레지스트리 내용은 내보내지 않는다. */
    public net.minecraft.core.RegistryAccess.Frozen registryAccess() { return net.minecraft.core.RegistryAccess.SERVER; }

    public int getPlayerCount() { return CraftServer.INSTANCE.getOnlinePlayers().size(); }

    public int getMaxPlayers() { return CraftServer.INSTANCE.getMaxPlayers(); }

    public String getMotd() { return CraftServer.INSTANCE.getMotd(); }

    /** 그 차원 키의 월드(없으면 null). */
    public net.minecraft.server.level.ServerLevel getLevel(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> key) {
        for (ServerLevel l : getAllLevels()) if (l.dimension() == key) return l;
        return null;
    }

    /** 오버월드. */
    public net.minecraft.server.level.ServerLevel overworld() { return getLevel(net.minecraft.world.level.Level.OVERWORLD); }

    /** 불러온 월드의 차원 키. */
    public java.util.Set<net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>> levelKeys() {
        java.util.Set<net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>> out = new java.util.LinkedHashSet<>();
        for (ServerLevel l : getAllLevels()) out.add(l.dimension());
        return out;
    }

    /** 불러온 월드 전부의 shim. */
    public Iterable<ServerLevel> getAllLevels() {
        List<ServerLevel> out = new ArrayList<>();
        for (var w : CraftServer.INSTANCE.getWorlds()) out.add(((CraftWorld) w).getHandle());
        return out;
    }
}
