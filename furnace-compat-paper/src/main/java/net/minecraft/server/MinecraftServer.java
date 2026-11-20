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
 * (설계 H5 2.1 — 대체: Furnace 스케줄러·Bukkit 스케줄러). 예외: {@link #isSameThread()} 는 Paper 의 뜻("틱 스레드인가")이 Furnace 에도 그대로 있다(2번 묶음).
 */
public abstract class MinecraftServer {

    /** CraftBukkit 이 더한 필드: Bukkit 서버(실제 jar 9개가 읽는다 — compat-h4-coverage 4장). */
    public CraftServer server = CraftServer.INSTANCE;

    protected MinecraftServer() {}

    /**
     * Paper·CraftBukkit 의 틱 수(정적 필드, N2b-6). 이 클래스를 처음 쓸 때부터 전역 스레드가 틱마다 서버 틱 수로 고쳐 쓴다
     * ({@code Bukkit.getCurrentTick()} 과 같은 값 — 그 전 틱 동안은 한 틱 늦을 수 있다).
     */
    public static int currentTick = furnaceStartTickMirror();

    private static int furnaceStartTickMirror() {
        try {
            var server = org.domi.furnace.api.Furnace.server();
            server.scheduler().global().runAtFixedRate(() -> currentTick = (int) server.currentTick(), 0, 1);
            return (int) server.currentTick();
        } catch (RuntimeException | LinkageError notRunning) {
            return 0;   // 서버 없이 올린 시험
        }
    }

    /** 바닐라: 주 스레드에 맡기는 실행기 — Furnace 는 전역 스레드(N2b-6). */
    public final java.util.concurrent.Executor executor = task -> org.domi.furnace.api.Furnace.server().scheduler().global().execute(task);

    /** 바닐라 BlockableEventLoop#execute: 주 스레드에 맡긴다 — Furnace 는 전역 스레드에서 돈다(Bukkit 주 스레드 작업과 같은 곳, N2b-6). */
    public void execute(Runnable task) { executor.execute(task); }

    /** 접속자 목록(2번 묶음). */
    public net.minecraft.server.players.PlayerList getPlayerList() { return DedicatedServer.INSTANCE.getPlayerList(); }

    /**
     * Paper 1.21.1: {@code TickThread.isTickThread()} — 서버 틱을 도는 스레드면 참. Furnace 에서는 전역·region 스레드({@code FurnaceScheduler#isTickThread})와
     * 호환 계층이 Bukkit 주 스레드로 표시한 구간(동기 작업·명령·이벤트)이다. Bukkit {@code isPrimaryThread} 보다 넓다(region 스레드도 참 — Paper·Folia 와 같다).
     * 2026-11-20 결정 D2-1(docs/user-todo/entries/2026-11-20-nms-shim-batch2.md).
     */
    public boolean isSameThread() {
        if (org.domi.furnace.compat.thread.PrimaryThread.isPrimary()) return true;
        try {
            return org.domi.furnace.api.Furnace.server().scheduler().isTickThread();
        } catch (IllegalStateException notRunning) {
            return false;
        }
    }

    /** 바닐라 CommandSource: 콘솔에 한 줄(서버 콘솔 보내는 곳으로 — 색·번역은 콘솔과 같다). */
    public void sendSystemMessage(net.minecraft.network.chat.Component message) {
        org.domi.furnace.api.Furnace.server().console().sendMessage(net.minecraft.network.chat.Component.furnace(message));
    }

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
        org.domi.furnace.compat.nms.NmsCaller.warnOnce("pipeline", "compat.pipeline-not-bridged");
    }

    /** 바닐라 서버 버전 이름(1.21.1). */
    public String getServerVersion() { return CraftServer.INSTANCE.getMinecraftVersion(); }

    /** 서버 레지스트리 묶음(H7b). Component·데이터 컴포넌트 직렬화 인자로만 쓰인다 — 레지스트리 내용은 내보내지 않는다. */
    public net.minecraft.core.RegistryAccess.Frozen registryAccess() { return net.minecraft.core.RegistryAccess.SERVER; }

    /** 바닐라 getStructureManager: 서버의 구조물 템플릿 관리자(T-211 2단계). */
    public net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager getStructureManager() {
        return net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager.of();
    }

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
