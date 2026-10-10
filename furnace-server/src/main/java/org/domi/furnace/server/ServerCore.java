// 명령·플러그인·연결이 함께 쓰는 서버 전역 핸들(바닐라 MinecraftServer/DedicatedServer 의 공개 부분).
package org.domi.furnace.server;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.domi.furnace.api.sched.FurnaceScheduler;
import org.domi.furnace.server.command.Commands;
import org.domi.furnace.server.config.ServerPropertiesFile;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.net.protocol.Text;
import org.domi.furnace.server.net.protocol.play.EntityEvent;
import org.domi.furnace.server.net.protocol.play.SystemChat;
import org.domi.furnace.server.players.GameProfile;
import org.domi.furnace.server.players.GameProfileCache;
import org.domi.furnace.server.players.PlayerLists;
import org.domi.furnace.server.world.World;
import org.domi.furnace.server.world.storage.LevelData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ServerCore {

    private static final Logger log = LoggerFactory.getLogger("Server");

    private final ServerPropertiesFile settings;
    private final PlayerLists lists;
    private final GameProfileCache profileCache;
    private final World world;
    private final LevelData levelData;
    private final FurnaceScheduler scheduler;
    private final Commands commands;
    private volatile Runnable halt = () -> {};
    private volatile Runnable saveAll = () -> {};

    public ServerCore(ServerPropertiesFile settings, PlayerLists lists, GameProfileCache profileCache, World world,
                      LevelData levelData, FurnaceScheduler scheduler) {
        this.settings = settings;
        this.lists = lists;
        this.profileCache = profileCache;
        this.world = world;
        this.levelData = levelData;
        this.scheduler = scheduler;
        this.commands = new Commands(this);
    }

    public ServerPropertiesFile settings() { return settings; }

    public PlayerLists lists() { return lists; }

    public GameProfileCache profileCache() { return profileCache; }

    public World world() { return world; }

    public LevelData levelData() { return levelData; }

    public FurnaceScheduler scheduler() { return scheduler; }

    public Commands commands() { return commands; }

    public void onHalt(Runnable r) { this.halt = r; }

    public void onSaveAll(Runnable r) { this.saveAll = r; }

    /** /stop — 종료 훅을 돈다. */
    public void halt() { halt.run(); }

    public void saveAll() { saveAll.run(); }

    public boolean autosaveEnabled() { return world.autosaveEnabled(); }

    public void autosaveEnabled(boolean v) { world.autosaveEnabled(v); }

    /** 접속 중(끊기지 않은) 플레이어. */
    public List<Player> players() {
        List<Player> out = new ArrayList<>();
        for (Player p : world.onlinePlayers()) if (!p.isDisconnected()) out.add(p);
        return out;
    }

    /** 바닐라 PlayerList.getPlayerByName: 대소문자 무시. */
    public Player playerByName(String name) {
        for (Player p : players()) if (p.name().equalsIgnoreCase(name)) return p;
        return null;
    }

    public Player playerByUuid(UUID id) {
        for (Player p : players()) if (p.uuid().equals(id)) return p;
        return null;
    }

    public static GameProfile profileOf(Player p) { return new GameProfile(p.uuid(), p.name(), p.profileProperties()); }

    private final java.util.List<java.util.function.Consumer<Text>> consoleListeners = new java.util.concurrent.CopyOnWriteArrayList<>();

    /** 콘솔 메시지 구독(RCON·테스트·플러그인 콘솔). */
    public void addConsoleListener(java.util.function.Consumer<Text> l) { consoleListeners.add(l); }

    /** 콘솔(서버 로그)로 보내는 메시지 — 바닐라 MinecraftServer.sendSystemMessage. */
    public void sendSystemMessage(Text message) {
        log.info(message.getString());
        for (var l : consoleListeners) l.accept(message);
    }

    /** 모든 플레이어 + 콘솔(바닐라 PlayerList.broadcastSystemMessage). */
    public void broadcastSystemMessage(Text message, boolean overlay) {
        sendSystemMessage(message);
        SystemChat packet = new SystemChat(message, overlay);
        for (Player p : players()) p.send(packet);
    }

    public int operatorUserPermissionLevel() { return settings.get().opPermissionLevel; }

    public int functionCompilationLevel() { return settings.get().functionPermissionLevel; }

    /** 바닐라 getProfilePermissions: OP 목록의 레벨, 아니면 0. */
    public int profilePermissions(GameProfile profile) { return lists.opLevel(profile); }

    public boolean gameRule(String name) {
        return Boolean.parseBoolean(levelData.gameRules.getOrDefault(name, LevelData.DEFAULT_GAME_RULES.getOrDefault(name, "false")));
    }

    /** OP 변경 후: 그 플레이어에게 권한 레벨과 명령 트리를 다시 보낸다(바닐라 sendPlayerPermissionLevel). */
    public void sendPlayerPermissionLevel(Player p) {
        p.send(EntityEvent.permissionLevel(p.entityId(), profilePermissions(profileOf(p))));
        commands.sendCommands(p);
    }
}
