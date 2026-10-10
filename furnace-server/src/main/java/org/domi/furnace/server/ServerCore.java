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
    private final org.domi.furnace.server.chat.ServicesKeys servicesKeys = new org.domi.furnace.server.chat.ServicesKeys();
    private final org.domi.furnace.server.world.GameRules gameRules;
    private final org.domi.furnace.server.world.WorldClock clock;
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
        this.gameRules = org.domi.furnace.server.world.GameRules.fromStrings(levelData.gameRules);
        this.gameRules.onChange(this::onGameRuleChanged);
        this.clock = world.clock();
        this.clock.restore(levelData.gameTime, levelData.dayTime, levelData.clearWeatherTime, levelData.rainTime,
                levelData.thunderTime, levelData.raining, levelData.thundering);
        this.commands = new Commands(this);
    }

    public ServerPropertiesFile settings() { return settings; }

    public PlayerLists lists() { return lists; }

    public GameProfileCache profileCache() { return profileCache; }

    public World world() { return world; }

    public LevelData levelData() { return levelData; }

    public FurnaceScheduler scheduler() { return scheduler; }

    public Commands commands() { return commands; }

    public org.domi.furnace.server.chat.ServicesKeys servicesKeys() { return servicesKeys; }

    public org.domi.furnace.server.world.GameRules gameRules() { return gameRules; }

    public org.domi.furnace.server.world.WorldClock clock() { return clock; }

    /** 전역 틱(20TPS): 시간·날씨 진행과 주기적 시간 동기화. */
    public void tick() {
        clock.tick(gameRules, packet -> { for (Player p : players()) p.send(packet); });
    }

    /** level.dat 저장 전에 런타임 상태를 LevelData 에 반영한다. */
    public void writeLevelData() {
        levelData.gameTime = clock.gameTime();
        levelData.dayTime = clock.dayTime();
        levelData.clearWeatherTime = clock.clearWeatherTime();
        levelData.rainTime = clock.rainTime();
        levelData.thunderTime = clock.thunderTime();
        levelData.raining = clock.rainingFlag();
        levelData.thundering = clock.thunderingFlag();
        levelData.gameRules.clear();
        levelData.gameRules.putAll(gameRules.toStrings());
    }

    /** 게임룰이 바뀌었을 때 바닐라가 즉시 알리는 것들. */
    private void onGameRuleChanged(org.domi.furnace.server.world.GameRules.Rule rule, int value) {
        switch (rule.name()) {
            case "reducedDebugInfo" -> {
                for (Player p : players()) p.send(new EntityEvent(p.entityId(), (byte) (value != 0 ? 22 : 23)));
            }
            case "doLimitedCrafting" -> {
                for (Player p : players()) p.send(new org.domi.furnace.server.net.protocol.play.GameEvent(
                        org.domi.furnace.server.net.protocol.play.GameEvent.LIMITED_CRAFTING, value != 0 ? 1f : 0f));
            }
            case "doImmediateRespawn" -> {
                for (Player p : players()) p.send(new org.domi.furnace.server.net.protocol.play.GameEvent(
                        org.domi.furnace.server.net.protocol.play.GameEvent.IMMEDIATE_RESPAWN, value != 0 ? 1f : 0f));
            }
            default -> { }
        }
    }

    /** 바닐라 MinecraftServer.setDifficulty: 하드코어면 HARD 고정, 바뀐 값을 모두에게 알린다. */
    public void setDifficulty(org.domi.furnace.server.world.Difficulty difficulty, boolean ignoreLock) {
        if (!ignoreLock && levelData.difficultyLocked) return;
        levelData.difficulty = levelData.hardcore ? org.domi.furnace.server.world.Difficulty.HARD : difficulty;
        var packet = new org.domi.furnace.server.net.protocol.play.ChangeDifficulty(levelData.difficulty.id(), levelData.difficultyLocked);
        for (Player p : players()) p.send(packet);
    }

    /** 바닐라 DedicatedServer.enforceSecureProfile: 설정이 켜져 있고 온라인 모드이며 Mojang 프로필 키를 검증할 수 있을 때. */
    public boolean enforceSecureProfile() {
        var p = settings.get();
        return p.enforceSecureProfile && p.onlineMode && servicesKeys.canValidateProfileKeys();
    }

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
        for (Player p : players()) p.sendSystemMessage(message, overlay);
    }

    /** 바닐라 MinecraftServer.logChatMessage — "[Not Secure] <이름> 내용" 처럼 장식한 문장을 로그에. */
    public void logChatMessage(Text content, org.domi.furnace.server.chat.ChatType.Bound bound, String tag) {
        String s = bound.decorate(content).getString();
        if (tag != null) log.info("[{}] {}", tag, s);
        else log.info("{}", s);
        Text line = tag != null ? Text.literal("[" + tag + "] " + s) : Text.literal(s);
        for (var l : consoleListeners) l.accept(line);
    }

    /** 바닐라 PlayerList.broadcastChatMessage — 서명 신뢰 여부로 로그 태그를 정하고 모든 플레이어에게 보낸다. */
    public void broadcastChatMessage(org.domi.furnace.server.chat.PlayerChatMessage message, Player sender,
                                     org.domi.furnace.server.chat.ChatType.Bound bound) {
        boolean trusted = message.hasSignature() && !message.hasExpiredServer(java.time.Instant.now());
        logChatMessage(message.decoratedContent(), bound, trusted ? null : "Not Secure");
        for (Player p : players()) sendChatMessage(p, message, false, bound);
    }

    /** 바닐라 ServerPlayer.sendChatMessage: 채팅을 모두 보는 설정일 때만. 시스템 출처면 disguised_chat. */
    public void sendChatMessage(Player receiver, org.domi.furnace.server.chat.PlayerChatMessage message, boolean filtered,
                                org.domi.furnace.server.chat.ChatType.Bound bound) {
        var chat = receiver.chat();
        if (!chat.acceptsChatMessages()) return;
        if (message.isSystem()) {
            receiver.send(new org.domi.furnace.server.net.protocol.play.DisguisedChatPacket(message.decoratedContent(), bound));
            return;
        }
        boolean ok = chat.packAndTrack(message, cache -> {
            receiver.send(new org.domi.furnace.server.net.protocol.play.PlayerChatPacket(message, cache, bound));
            return null;
        });
        if (!ok) receiver.kick(new org.domi.furnace.server.net.protocol.play.Disconnect(
                Text.translatable("multiplayer.disconnect.too_many_pending_chats")));
    }

    public int operatorUserPermissionLevel() { return settings.get().opPermissionLevel; }

    public int functionCompilationLevel() { return settings.get().functionPermissionLevel; }

    /** 바닐라 getProfilePermissions: OP 목록의 레벨, 아니면 0. */
    public int profilePermissions(GameProfile profile) { return lists.opLevel(profile); }

    public boolean gameRule(String name) { return gameRules.getBoolean(name); }

    /** OP 변경 후: 그 플레이어에게 권한 레벨과 명령 트리를 다시 보낸다(바닐라 sendPlayerPermissionLevel). */
    public void sendPlayerPermissionLevel(Player p) {
        p.send(EntityEvent.permissionLevel(p.entityId(), profilePermissions(profileOf(p))));
        commands.sendCommands(p);
    }
}
