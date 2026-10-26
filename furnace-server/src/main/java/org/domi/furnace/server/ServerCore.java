// 명령·플러그인·연결이 함께 쓰는 서버 전역 핸들(바닐라 MinecraftServer/DedicatedServer 의 공개 부분).
package org.domi.furnace.server;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.domi.furnace.api.sched.FurnaceScheduler;
import org.domi.furnace.server.command.Commands;
import org.domi.furnace.server.config.ServerPropertiesFile;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.api.text.Text;
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
    private final org.domi.furnace.server.world.WorldBorder border = new org.domi.furnace.server.world.WorldBorder();
    private volatile Runnable halt = () -> {};
    private volatile Runnable saveAll = () -> {};

    public ServerCore(ServerPropertiesFile settings, PlayerLists lists, GameProfileCache profileCache, World world,
                      LevelData levelData, FurnaceScheduler scheduler) {
        this.settings = settings;
        this.lists = lists;
        this.profileCache = profileCache;
        this.world = world;
        org.domi.furnace.server.map.MapStore.install(world.worldDir());   // 지도 저장 데이터(world/data/map_N.dat) 읽기
        this.levelData = levelData;
        this.scheduler = scheduler;
        this.gameRules = org.domi.furnace.server.world.GameRules.fromStrings(levelData.gameRules);
        this.gameRules.onChange(this::onGameRuleChanged);
        this.clock = world.clock();
        this.randomSequences = new org.domi.furnace.server.loot.RandomSequences(levelData.seed);
        this.biomeZoomSeed = org.domi.furnace.server.net.protocol.play.LoginPlay.obfuscateSeed(levelData.seed);
        this.clock.restore(levelData.gameTime, levelData.dayTime, levelData.clearWeatherTime, levelData.rainTime,
                levelData.thunderTime, levelData.raining, levelData.thundering);
        // 바닐라 createLevels: 저장된 보더 설정을 적용한 뒤에 리스너를 붙인다(적용 중 변경은 접속자에게 알리지 않는다).
        this.border.applySettings(levelData.worldBorder);
        this.border.addListener(new BorderBroadcaster());
        this.commands = new Commands(this);
        world.levelServices(levelServices);
        this.pluginHost = new org.domi.furnace.server.plugin.PluginHost(this, java.nio.file.Path.of("plugins"));
    }

    private final org.domi.furnace.server.plugin.PluginHost pluginHost;

    private volatile org.domi.furnace.server.data.DataPackManager dataPacks;

    /** 데이터 팩(없으면 null — 바닐라 데이터를 못 구했거나 테스트). */
    public org.domi.furnace.server.data.DataPackManager dataPacks() { return dataPacks; }

    /** 데이터 팩을 붙인다 — /reload 가 끝나면 접속자에게 새 태그를 보낸다(바닐라 PlayerList.reloadResources). */
    public void dataPacks(org.domi.furnace.server.data.DataPackManager m) {
        this.dataPacks = m;
        m.onReload(dp -> {
            // 바닐라 reloadResources: 플레이어 발전 과제를 저장한 뒤 새 매니저로 다시 읽는다(보내는 것은 다음 틱 flushDirty)
            var advancements = dp.advancements();
            for (Player p : players()) {
                var adv = p.advancements();
                if (adv != null && advancements != null) {
                    adv.save();
                    adv.reload(advancements);
                }
            }
            // 바닐라 PlayerList.reloadResources: 태그 방송 → 플레이어마다 레시피 목록 + 레시피 북 INIT
            var packet = new org.domi.furnace.server.net.protocol.play.UpdateTagsPlay(dp.tags());
            for (Player p : players()) p.send(packet);
            var recipes = dp.recipes();
            if (recipes == null) return;
            var recipePacket = new org.domi.furnace.server.net.protocol.play.UpdateRecipes(recipes.networkBody());
            for (Player p : players()) {
                p.send(recipePacket);
                p.send(p.recipeBook().initPacket());
            }
        });
    }

    /** 전리품 평가가 보는 월드(난수 시퀀스는 월드 시드로 — 바닐라 RandomSequences). */
    private final org.domi.furnace.server.loot.RandomSequences randomSequences;
    private final org.domi.furnace.server.loot.LootWorld lootWorld = new org.domi.furnace.server.loot.LootWorld() {
        public org.domi.furnace.server.world.gen.noise.NoiseRandom random() {
            return new org.domi.furnace.server.world.gen.carver.LegacyRandom(java.util.concurrent.ThreadLocalRandom.current().nextLong());
        }

        public org.domi.furnace.server.world.gen.noise.NoiseRandom randomSequence(org.domi.furnace.server.data.ResourceId id) { return randomSequences.get(id); }

        public long dayTime() { return clock.dayTime(); }

        public boolean isRaining() { return clock.isRaining(); }

        public boolean isThundering() { return clock.thunderLevel() > 0.9f; }

        /** 바닐라 Level.getBiome(pos)(확대 지터 포함). 지금 스레드가 그 region 을 가진 경우에만 — 아니면 null. */
        public String biomeAt(int x, int y, int z) {
            var dp = dataPacks;
            if (dp == null || dp.loot() == null) return null;
            int[] q = org.domi.furnace.server.world.BiomeZoom.quart(biomeZoomSeed, x, y, z);
            int cx = q[0] >> 2, cz = q[2] >> 2;
            if (!world.ownsChunkOnCurrentThread(cx, cz)) return null;
            int id = world.regionizer().ownerOf(cx, cz).biomeIdAtQuart(q[0], q[1], q[2]);
            var names = dp.loot().registries().entries("minecraft:worldgen/biome");
            return id >= 0 && id < names.size() ? names.get(id) : null;
        }

        public String dimension() { return "minecraft:overworld"; }
    };
    private final long biomeZoomSeed;

    /** 전리품 조건이 보는 플레이어(바닐라 ADVANCEMENT_ENTITY 의 this_entity). */
    private static org.domi.furnace.server.loot.LootEntity lootEntity(Player p) {
        return new org.domi.furnace.server.loot.LootEntity() {
            public String type() { return "minecraft:player"; }
            public double[] position() { return new double[]{p.x(), p.y(), p.z()}; }
            public boolean isCrouching() { return p.sneaking(); }
            public boolean isSprinting() { return p.sprinting(); }
        };
    }

    private volatile org.domi.furnace.server.map.ExplorerMaps.Backend explorerBackend;

    /**
     * 탐험 지도 지원: 구조물 찾기(StructureLocator — 바닐라 findNearestMapStructure)와 지도 바탕용 바이옴(Level.getBiome — 확대 지터 포함, 생성기 바이옴).
     * 월드 생성기가 없으면(평지·시험) null.
     */
    private org.domi.furnace.server.map.ExplorerMaps.Backend explorerMapBackend() {
        var b = explorerBackend;
        if (b != null) return b;
        var gen = org.domi.furnace.server.world.gen.NoiseWorldSource.of(world.worldSource());
        if (gen == null) return null;
        return explorerBackend = new org.domi.furnace.server.map.ExplorerMaps.Backend() {
            private final java.util.Set<String> waterBiomes =
                    org.domi.furnace.server.world.gen.structure.StructureRegistry.biomeTag("minecraft:water_on_map_outlines");

            public int[] findNearestMapStructure(String tag, int x, int y, int z, int radius) {
                if (!settings.get().generateStructures) return null;
                var ids = org.domi.furnace.server.world.gen.structure.StructureRegistry.structureTag(tag);
                if (ids == null) return null;
                var f = gen.structureLocator().findNearest(ids, x, y, z, radius);
                return f == null ? null : new int[] {f.x(), f.z()};
            }

            public boolean waterOnMapOutlines(int bx, int bz) {
                int[] q = org.domi.furnace.server.world.BiomeZoom.quart(biomeZoomSeed, bx, 0, bz);
                return waterBiomes.contains(gen.biomeAt(q[0] << 2, q[1] << 2, q[2] << 2));
            }

            public String dimension() { return "minecraft:overworld"; }
        };
    }

    /** 접속한 플레이어의 발전 과제 진행을 만든다(바닐라 PlayerList.getPlayerAdvancements). 데이터 팩이 없으면 null. */
    public org.domi.furnace.server.advancement.PlayerAdvancements attachAdvancements(Player p) {
        var dp = dataPacks;
        if (dp == null || dp.advancements() == null) return null;
        var host = new org.domi.furnace.server.advancement.PlayerAdvancements.Host() {
            public void send(org.domi.furnace.server.net.protocol.Packet packet) { p.send(packet); }

            public void awardRecipes(java.util.List<org.domi.furnace.server.data.ResourceId> ids) {
                var rm = recipes();
                if (rm != null) p.awardRecipes(ids, rm);
            }

            public void giveExperience(int points) { p.vitals().giveExperiencePoints(points); }

            public void giveLoot(String table) {
                org.slf4j.LoggerFactory.getLogger("Furnace").warn("발전 과제 전리품 보상은 아직 인벤토리에 넣지 못한다: {}", table);
            }

            public void broadcast(Text message) { broadcastSystemMessage(message, false); }

            public boolean announceAdvancements() { return gameRule("announceAdvancements"); }

            public Text displayName() { return p.displayName(); }

            public org.domi.furnace.server.loot.LootContext playerContext() {
                var loot = dataPacks.loot();
                var params = new org.domi.furnace.server.loot.LootParams()
                        .with(org.domi.furnace.server.loot.LootParams.THIS_ENTITY, lootEntity(p))
                        .with(org.domi.furnace.server.loot.LootParams.ORIGIN, new double[]{p.x(), p.y(), p.z()});
                return new org.domi.furnace.server.loot.LootContext(loot, lootWorld, params, lootWorld.random());
            }

            public org.domi.furnace.server.loot.LootRegistries registries() { return dataPacks.loot().registries(); }
        };
        var adv = new org.domi.furnace.server.advancement.PlayerAdvancements(world.worldDir(), p.uuid(), dp.advancements(), host);
        p.advancements(adv);
        return adv;
    }

    /** 위치 문맥(바닐라 ADVANCEMENT_LOCATION: origin = 블록 중심, this_entity, block_state, tool) — placed_block 등. */
    public org.domi.furnace.server.loot.LootContext locationContext(Player p, int x, int y, int z, String blockState,
                                                                     org.domi.furnace.server.loot.LootStack tool) {
        var loot = dataPacks.loot();
        var params = new org.domi.furnace.server.loot.LootParams()
                .with(org.domi.furnace.server.loot.LootParams.ORIGIN, new double[]{x + 0.5, y + 0.5, z + 0.5})
                .with(org.domi.furnace.server.loot.LootParams.THIS_ENTITY, lootEntity(p))
                .with(org.domi.furnace.server.loot.LootParams.BLOCK_STATE, org.domi.furnace.server.loot.LootBlockState.parse(blockState))
                .with(org.domi.furnace.server.loot.LootParams.TOOL, tool);
        return new org.domi.furnace.server.loot.LootContext(loot, lootWorld, params, lootWorld.random());
    }

    /**
     * 바닐라 RandomizableContainer.unpackLootTable — 열지 않은 전리품 상자를 채운다(region 스레드에서).
     * 표 이름을 먼저 지우고, 위치는 상자 중심, 연 플레이어가 this_entity.
     */
    public void unpackLoot(org.domi.furnace.server.block.entity.ContainerBlockEntity chest, Player opener) {
        var dp = dataPacks;
        if (chest.lootTable == null || dp == null || dp.loot() == null) return;
        var loot = dp.loot();
        var table = loot.table(chest.lootTable);
        long seed = chest.lootSeed;
        chest.lootTable = null;
        var slots = new org.domi.furnace.server.loot.LootStack[chest.items.length];
        var occupied = new org.domi.furnace.server.loot.LootStack("minecraft:stone", 1);
        for (int i = 0; i < slots.length; i++) if (chest.items[i] != null && !chest.items[i].isEmpty()) slots[i] = occupied;
        var params = new org.domi.furnace.server.loot.LootParams()
                .with(org.domi.furnace.server.loot.LootParams.ORIGIN, new double[]{chest.pos().x() + 0.5, chest.pos().y() + 0.5, chest.pos().z() + 0.5});
        if (opener != null) params.with(org.domi.furnace.server.loot.LootParams.THIS_ENTITY, () -> "minecraft:player");
        table.fill(slots, loot, lootWorld, params, seed);
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] != null && slots[i] != occupied) chest.items[i] = dp.network().toItemStack(slots[i]);
        }
    }

    /** 지금 레시피 매니저(데이터 팩 없이 뜬 서버면 null). */
    public org.domi.furnace.server.recipe.RecipeManager recipes() {
        var dp = dataPacks;
        return dp == null ? null : dp.recipes();
    }

    /** 플러그인 호스트(Furnace API 파사드·이벤트 버스·플러그인 관리자). */
    public org.domi.furnace.server.plugin.PluginHost plugins() { return pluginHost; }

    /** 이벤트 버스(구독자가 없으면 이벤트 객체를 만들지 않도록 hasSubscribers 로 먼저 묻는다). */
    public org.domi.furnace.server.plugin.EventBusImpl events() { return pluginHost.eventsImpl(); }

    /** 콘솔 명령(ServerCommandEvent → 실행). */
    public void runConsoleCommand(String command) {
        var events = events();
        if (events.hasSubscribers(org.domi.furnace.api.event.server.ServerCommandEvent.class)) {
            var ev = events.call(new org.domi.furnace.api.event.server.ServerCommandEvent(pluginHost.console(), command));
            if (ev.isCancelled()) return;
            command = ev.command();
        }
        commands.performPrefixedCommand(org.domi.furnace.server.command.CommandSource.console(this), command);
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

    public org.domi.furnace.server.world.WorldBorder border() { return border; }

    /** 전역 틱(20TPS): 보더 보간 진행, 시간·날씨 진행과 주기적 시간 동기화. */
    public void tick() {
        border.tick();
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
        levelData.worldBorder = border.createSettings();
        levelData.gameRules.clear();
        levelData.gameRules.putAll(gameRules.toStrings());
    }

    /** 바닐라 PlayerList 의 보더 리스너: 변경을 모든 접속자에게 알린다(피해량·안전 거리 변경은 아무것도 보내지 않는다). */
    private final class BorderBroadcaster implements org.domi.furnace.server.world.WorldBorder.Listener {
        private void broadcast(org.domi.furnace.server.net.protocol.Packet packet) {
            for (Player p : players()) p.send(packet);
        }

        @Override
        public void onSizeSet(org.domi.furnace.server.world.WorldBorder b, double size) {
            broadcast(org.domi.furnace.server.net.protocol.play.SetBorderSize.of(b));
        }

        @Override
        public void onSizeLerping(org.domi.furnace.server.world.WorldBorder b, double from, double to, long timeMs) {
            broadcast(org.domi.furnace.server.net.protocol.play.SetBorderLerpSize.of(b));
        }

        @Override
        public void onCenterSet(org.domi.furnace.server.world.WorldBorder b, double x, double z) {
            broadcast(org.domi.furnace.server.net.protocol.play.SetBorderCenter.of(b));
        }

        @Override
        public void onWarningTimeSet(org.domi.furnace.server.world.WorldBorder b, int seconds) {
            broadcast(org.domi.furnace.server.net.protocol.play.SetBorderWarningDelay.of(b));
        }

        @Override
        public void onWarningBlocksSet(org.domi.furnace.server.world.WorldBorder b, int blocks) {
            broadcast(org.domi.furnace.server.net.protocol.play.SetBorderWarningDistance.of(b));
        }
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

    /**
     * 바닐라 DedicatedServer.isUnderSpawnProtection(오버월드): OP 가 한 명이라도 있고, 이 플레이어가 OP 가 아니며,
     * spawn-protection > 0 이고 월드 스폰과의 체비셰프 거리가 그 이하인가.
     */
    public boolean isUnderSpawnProtection(int x, int z, Player p) {
        if (lists.getOps().isEmpty()) return false;
        if (lists.isOp(profileOf(p))) return false;
        int radius = settings.get().spawnProtection;
        if (radius <= 0) return false;
        var spawn = world.defaultSpawn();
        return Math.max(Math.abs(x - spawn.x()), Math.abs(z - spawn.z())) <= radius;
    }

    /** 바닐라 Level.mayInteract(ServerLevel): 스폰 보호 밖이고 월드 보더 안. */
    public boolean mayInteract(Player p, int x, int z) {
        return !isUnderSpawnProtection(x, z, p) && border.isWithinBounds(x, z);
    }

    /** 바닐라 ServerLevel.setDefaultSpawnPos: 바뀌었으면 level.dat 에 남기고 모두에게 알린다. */
    public void setWorldSpawn(int x, int y, int z, float angle) {
        var old = world.defaultSpawn();
        if (old.x() == x && old.y() == y && old.z() == z && old.yaw() == angle) return;
        levelData.spawnX = x;
        levelData.spawnY = y;
        levelData.spawnZ = z;
        levelData.spawnAngle = angle;
        world.defaultSpawn(new org.domi.furnace.server.world.SpawnPoint(x, y, z, angle));
        var pkt = new org.domi.furnace.server.net.protocol.play.SetDefaultSpawnPosition(x, y, z, angle);
        for (Player p : players()) p.send(pkt);
    }

    /** 바닐라 MinecraftServer.getDefaultGameType: level.dat 의 GameType. */
    public org.domi.furnace.server.world.GameMode defaultGameMode() { return levelData.gameType; }

    /** 바닐라 DedicatedServer.getForcedGameType: force-gamemode 면 기본 게임 모드, 아니면 null. */
    public org.domi.furnace.server.world.GameMode forcedGameMode() {
        return settings.get().forceGameMode ? levelData.gameType : null;
    }

    /** 바닐라 MinecraftServer.setDefaultGameType(/defaultgamemode): level.dat 에 남는다. */
    public void setDefaultGameMode(org.domi.furnace.server.world.GameMode mode) { levelData.gameType = mode; }

    private final org.domi.furnace.server.entity.PlayerVitals.Env vitalsEnv = new org.domi.furnace.server.entity.PlayerVitals.Env() {
        public org.domi.furnace.server.world.Difficulty difficulty() { return levelData.difficulty; }
        public boolean gameRule(String name) { return gameRules.getBoolean(name); }
        public boolean pvp() { return settings.get().pvp; }
        public void broadcastSystemMessage(Text message) { ServerCore.this.broadcastSystemMessage(message, false); }
        public void broadcastAll(org.domi.furnace.server.net.protocol.Packet packet) { for (Player p : players()) p.send(packet); }
        public void broadcastToTrackers(int entityId, org.domi.furnace.server.net.protocol.Packet packet) { world.broadcastToTrackers(entityId, packet); }
        public org.domi.furnace.server.entity.PlayerVitals.DeathOutcome death(Player player, Text message, boolean keepInventory) {
            var events = events();
            if (!events.hasSubscribers(org.domi.furnace.api.event.player.PlayerDeathEvent.class)) {
                return new org.domi.furnace.server.entity.PlayerVitals.DeathOutcome(message, keepInventory, keepInventory);
            }
            var ev = events.call(new org.domi.furnace.api.event.player.PlayerDeathEvent(pluginHost.player(player), message, keepInventory));
            return new org.domi.furnace.server.entity.PlayerVitals.DeathOutcome(ev.deathMessage(), ev.keepInventory(), ev.keepLevel());
        }
        public boolean allowGameModeChange(Player player, org.domi.furnace.server.world.GameMode mode) {
            var events = events();
            if (!events.hasSubscribers(org.domi.furnace.api.event.player.PlayerGameModeChangeEvent.class)) return true;
            return !events.call(new org.domi.furnace.api.event.player.PlayerGameModeChangeEvent(pluginHost.player(player),
                    org.domi.furnace.api.entity.GameMode.byId(mode.id()))).isCancelled();
        }
        public float borderDamage(double x, double y, double z) {
            return border.computePlayerDamage(x - 0.3, z - 0.3, x + 0.3, z + 0.3, x, z);
        }
    };

    /** 블록 레벨이 보는 서버 쪽 정보(게임룰·난이도·보더·날씨·시간·접속자). */
    private final org.domi.furnace.server.block.LevelServices levelServices = new org.domi.furnace.server.block.LevelServices() {
        public boolean gameRule(String name) { return gameRules.getBoolean(name); }
        public int gameRuleInt(String name) { return gameRules.getInt(name); }
        public int difficulty() { return levelData.difficulty.id(); }
        public boolean withinBorder(int x, int z) { return border.isWithinBounds(x, z); }
        public boolean isRaining() { return clock.isRaining(); }
        public boolean isThundering() { return clock.thunderLevel() > 0.9f; }
        public float rainLevel() { return clock.rainLevel(); }
        public float thunderLevel() { return clock.thunderLevel(); }
        public long dayTime() { return clock.dayTime(); }
        public long gameTime() { return clock.gameTime(); }
        public String dimension() { return "minecraft:overworld"; }
        public boolean ultraWarm() { return false; }
        public int maxChainedNeighborUpdates() { return settings.get().maxChainedNeighborUpdates; }
        public void forEachPlayer(java.util.function.Consumer<Player> c) { for (Player p : world.onlinePlayers()) c.accept(p); }
        public boolean biomeIs(int x, int y, int z, String tag) {
            String b = lootWorld.biomeAt(x, y, z);
            var dp = dataPacks;
            return b != null && dp != null && dp.loot() != null && dp.loot().registries().tag("minecraft:worldgen/biome", tag).contains(b);
        }
        public java.util.List<ItemStack> entityDrops(String lootTable, org.domi.furnace.server.loot.LootEntity self, double x, double y, double z,
                String damageType, Player attacker, Player lastPlayer, long seed) {
            return ServerCore.this.entityDrops(lootTable, self, x, y, z, damageType, attacker, lastPlayer, seed);
        }

        public java.util.List<ItemStack> blockDrops(String lootTable, int state, double x, double y, double z, ItemStack tool, Object entity,
                                                    Float explosionRadius) {
            return ServerCore.this.blockDrops(lootTable, state, x, y, z, tool, entity, explosionRadius);
        }
        public java.util.List<ItemStack> fishingLoot(org.domi.furnace.server.loot.LootEntity hook, double x, double y, double z, ItemStack tool, float luck) {
            return ServerCore.this.fishingLoot(hook, x, y, z, tool, luck);
        }
        public String biomeAt(int x, int y, int z) { return lootWorld.biomeAt(x, y, z); }
        public org.domi.furnace.server.map.ExplorerMaps.Backend explorerMaps() { return explorerMapBackend(); }
        public long seed() { return levelData.seed; }
        public int[] sharedSpawnPos() { return new int[] {levelData.spawnX, levelData.spawnY, levelData.spawnZ}; }
        public boolean spawnMonsters() { return settings.get().spawnMonsters; }
        public boolean spawnAnimals() { return settings.get().spawnAnimals; }
        public int simulationDistance() { return settings.get().simulationDistance; }
        public int entityBroadcastRangePercentage() { return settings.get().entityBroadcastRangePercentage; }
        public Object recipes() { return ServerCore.this.recipes(); }
        public org.domi.furnace.server.loot.LootData loot() {
            var dp = dataPacks;
            return dp == null ? null : dp.loot();
        }
        public void unpackContainerLoot(Object container) {
            if (container instanceof org.domi.furnace.server.block.entity.ContainerBlockEntity c) unpackLoot(c, null);
        }
    };

    /** 바닐라 BlockBehaviour.getDrops: 표가 비었으면 빈 목록, 아니면 random_sequence 난수로 평가하고 최대 스택으로 나눈다. */
    java.util.List<ItemStack> blockDrops(String lootTable, int state, double x, double y, double z, ItemStack tool, Object entity, Float explosionRadius) {
        var dp = dataPacks;
        if (dp == null || dp.loot() == null || lootTable == null || lootTable.equals("minecraft:empty")) return java.util.List.of();
        var loot = dp.loot();
        var table = loot.table(lootTable);
        var params = new org.domi.furnace.server.loot.LootParams()
                .with(org.domi.furnace.server.loot.LootParams.ORIGIN, new double[]{x, y, z})
                .with(org.domi.furnace.server.loot.LootParams.TOOL, lootStack(tool))
                .with(org.domi.furnace.server.loot.LootParams.BLOCK_STATE, lootBlockState(state))
                .with(org.domi.furnace.server.loot.LootParams.EXPLOSION_RADIUS, explosionRadius);
        if (entity instanceof Player p) params.with(org.domi.furnace.server.loot.LootParams.THIS_ENTITY, lootEntity(p));
        var ctx = table.context(loot, lootWorld, params, 0L);
        java.util.List<ItemStack> out = new java.util.ArrayList<>();
        for (var s : table.getRandomItems(ctx)) {
            ItemStack is = dp.network().toItemStack(s);
            if (is != null && !is.isEmpty()) out.add(is);
        }
        return out;
    }

    /** 바닐라 LivingEntity.dropFromLootTable: 엔티티 표 평가(시드 0 이면 random_sequence/월드 난수). */
    java.util.List<ItemStack> entityDrops(String lootTable, org.domi.furnace.server.loot.LootEntity self, double x, double y, double z, String damageType,
            Player attacker, Player lastPlayer, long seed) {
        var dp = dataPacks;
        if (dp == null || dp.loot() == null || lootTable == null) return java.util.List.of();
        var loot = dp.loot();
        var table = loot.table(lootTable);
        var attackerEntity = attacker == null ? null : lootEntity(attacker);
        var params = new org.domi.furnace.server.loot.LootParams()
                .with(org.domi.furnace.server.loot.LootParams.THIS_ENTITY, self)
                .with(org.domi.furnace.server.loot.LootParams.ORIGIN, new double[]{x, y, z})
                .with(org.domi.furnace.server.loot.LootParams.DAMAGE_SOURCE, new org.domi.furnace.server.loot.LootDamageSource() {
                    public String type() { return damageType; }
                    // TODO(피해 종류 태그): is_fire·is_explosion 등 태그 조회
                    public boolean is(String tag) { return false; }
                    public org.domi.furnace.server.loot.LootEntity entity() { return attackerEntity; }
                    public org.domi.furnace.server.loot.LootEntity directEntity() { return attackerEntity; }
                })
                .with(org.domi.furnace.server.loot.LootParams.ATTACKING_ENTITY, attackerEntity)
                .with(org.domi.furnace.server.loot.LootParams.DIRECT_ATTACKING_ENTITY, attackerEntity);
        if (lastPlayer != null) params.with(org.domi.furnace.server.loot.LootParams.LAST_DAMAGE_PLAYER, lootEntity(lastPlayer));   // TODO(행운): 플레이어 luck 속성
        var ctx = table.context(loot, lootWorld, params, seed);
        java.util.List<ItemStack> out = new java.util.ArrayList<>();
        for (var st : table.getRandomItems(ctx)) {
            ItemStack is = dp.network().toItemStack(st);
            if (is != null && !is.isEmpty()) out.add(is);
        }
        return out;
    }

    /** 바닐라 FishingHook.retrieve: gameplay/fishing 을 FISHING 문맥으로(random_sequence 난수). */
    java.util.List<ItemStack> fishingLoot(org.domi.furnace.server.loot.LootEntity hook, double x, double y, double z, ItemStack tool, float luck) {
        var dp = dataPacks;
        if (dp == null || dp.loot() == null) return java.util.List.of();
        var loot = dp.loot();
        var table = loot.table("minecraft:gameplay/fishing");
        var params = org.domi.furnace.server.loot.LootParams.fishing(new double[]{x, y, z}, lootStack(tool), hook, luck);
        java.util.List<ItemStack> out = new java.util.ArrayList<>();
        for (var st : table.getRandomItems(table.context(loot, lootWorld, params, 0L))) {
            ItemStack is = dp.network().toItemStack(st);
            if (is != null && !is.isEmpty()) out.add(is);
        }
        return out;
    }

    /** 블록 상태 → 전리품 블록 상태(블록 이름과 전체 속성). */
    private static org.domi.furnace.server.loot.LootBlockState lootBlockState(int state) {
        var blocks = org.domi.furnace.server.block.Blocks.get();
        java.util.Map<String, String> props = new java.util.LinkedHashMap<>();
        for (var p : blocks.propsOf(blocks.blockOf(state))) props.put(p.name(), p.valueName(state));
        return new org.domi.furnace.server.loot.LootBlockState(blocks.name(blocks.blockOf(state)), props);
    }

    /** 서버 아이템 → 전리품 스택(컴포넌트 패치까지). */
    private static org.domi.furnace.server.loot.LootStack lootStack(ItemStack s) {
        if (s == null || s.isEmpty()) return org.domi.furnace.server.loot.LootStack.empty();
        String name = org.domi.furnace.server.item.ItemContext.name("minecraft:item", s.itemId());
        return name == null ? org.domi.furnace.server.loot.LootStack.empty()
                : org.domi.furnace.server.loot.LootStack.of(name, s.count(), s.patch().entries());
    }

    /** 플레이어 생존 상태가 쓰는 서버 쪽 정보·브로드캐스트. */
    public org.domi.furnace.server.entity.PlayerVitals.Env vitalsEnv() { return vitalsEnv; }

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
        log.info("{}", new org.domi.furnace.server.log.ConsoleText(message));
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
        Text line = tag != null ? Text.text("[" + tag + "] " + s) : Text.text(s);
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
