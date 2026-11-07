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
        this.lootWorld = lootWorldFor(world);
        this.randomSequences = new org.domi.furnace.server.loot.RandomSequences(levelData.seed);
        this.biomeZoomSeed = org.domi.furnace.server.net.protocol.play.LoginPlay.obfuscateSeed(levelData.seed);
        this.clock.restore(levelData.gameTime, levelData.dayTime, levelData.clearWeatherTime, levelData.rainTime,
                levelData.thunderTime, levelData.raining, levelData.thundering);
        // 바닐라 createLevels: 저장된 보더 설정을 적용한 뒤에 리스너를 붙인다(적용 중 변경은 접속자에게 알리지 않는다).
        this.border.setAbsoluteMaxSize(settings.get().maxWorldSize);   // server.properties max-world-size (ServerLevel: setAbsoluteMaxSize)
        this.border.applySettings(levelData.worldBorder);
        this.border.addListener(new BorderBroadcaster());
        this.customBossEvents = new org.domi.furnace.server.bossbar.CustomBossEvents(this::playerByUuid);
        this.customBossEvents.load(levelData.customBossEventsData());
        this.scheduledEvents.load(levelData.scheduledEventsData());
        this.scoreboard = org.domi.furnace.server.scores.ServerScoreboard.open(this::players, world.worldDir());
        this.commands = new Commands(this);
        this.functions = new org.domi.furnace.server.function.FunctionManager(this,
                (org.domi.furnace.api.concurrent.ThreadOwner) scheduler.global(), org.domi.furnace.server.function.FunctionLibrary.EMPTY);
        world.levelServices(levelServices);
        this.pluginHost = new org.domi.furnace.server.plugin.PluginHost(this, java.nio.file.Path.of("plugins"));
    }

    private final org.domi.furnace.server.plugin.PluginHost pluginHost;
    private final org.domi.furnace.server.function.FunctionManager functions;
    /** /schedule 이 쓰는 지연 실행 큐(바닐라는 오버월드 레벨 데이터의 TimerQueue). 전역 스레드 전용. */
    private final org.domi.furnace.server.function.ScheduledEvents scheduledEvents = new org.domi.furnace.server.function.ScheduledEvents();

    public org.domi.furnace.server.function.ScheduledEvents scheduledEvents() { return scheduledEvents; }

    /** 데이터 팩 함수(바닐라 ServerFunctionManager). */
    public org.domi.furnace.server.function.FunctionManager functions() { return functions; }
    private final org.domi.furnace.server.scores.ServerScoreboard scoreboard;

    /** 서버 점수판(모든 차원이 함께 쓴다). */
    public org.domi.furnace.server.scores.ServerScoreboard scoreboard() { return scoreboard; }
    private final org.domi.furnace.server.bossbar.CustomBossEvents customBossEvents;

    /** /bossbar 로 만든 커스텀 보스바. */
    public org.domi.furnace.server.bossbar.CustomBossEvents customBossEvents() { return customBossEvents; }

    private volatile org.domi.furnace.server.net.registry.RegistrySet registries;

    /** 클라이언트와 동기하는 레지스트리(플러그인 바이옴·차원 타입 포함). 없으면 null — 접속 처리가 바닐라 목록을 쓴다. */
    public org.domi.furnace.server.net.registry.RegistrySet registries() { return registries; }

    public void registries(org.domi.furnace.server.net.registry.RegistrySet set) { this.registries = set; }

    private volatile org.domi.furnace.server.data.DataPackManager dataPacks;

    /** 데이터 팩(없으면 null — 바닐라 데이터를 못 구했거나 테스트). */
    public org.domi.furnace.server.data.DataPackManager dataPacks() { return dataPacks; }

    /** 데이터 팩을 붙인다 — /reload 가 끝나면 접속자에게 새 태그를 보낸다(바닐라 PlayerList.reloadResources). */
    public void dataPacks(org.domi.furnace.server.data.DataPackManager m) {
        this.dataPacks = m;
        m.onReload(dp -> {
            installFunctions(dp.functions());   // 리로드 스레드(background)에서 오므로 전역으로 넘긴다(설계 §4.4 I4)
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
            if (pluginHost != null) pluginHost.recipes().reapply(recipes);   // 플러그인 레시피·제거 기록을 새 매니저에 입힌다(보내기 전에)
            var recipePacket = new org.domi.furnace.server.net.protocol.play.UpdateRecipes(recipes.networkBody());
            for (Player p : players()) {
                p.send(recipePacket);
                p.send(p.recipeBook().initPacket());
            }
        });
    }

    /**
     * 함수 로더를 붙이고 지금 리소스로 함수를 읽는다. 함수 줄은 디스패처로 파싱되므로 플러그인 명령까지 등록된 뒤(FurnaceMain: 플러그인 loadAll 뒤)
     * 불러야 한다. 이후 /reload 는 DataPackManager.loadFrom 이 레시피 뒤·발전 과제 앞에서 읽는다.
     */
    public void loadFunctions() {
        var m = dataPacks;
        if (m == null) return;
        // 바닐라 alf 의 컴파일 소스: 위치 (0,0,0)·회전 0. 레벨은 Furnace 소스가 null 을 못 가져 오버월드(파싱은 레벨을 읽지 않는다)
        var compile = org.domi.furnace.server.command.CommandSource.forProfile(this, "", functionCompilationLevel()).withPosition(0, 0, 0);
        m.functionLoader(rm -> org.domi.furnace.server.function.FunctionLibrary.load(rm, commands.getDispatcher(), compile));
        installFunctions(m.functions());
    }

    /** 새 함수 라이브러리를 전역 스레드에서 끼운다(tick 스냅샷·postReload 는 전역 틱이 읽는다). */
    private void installFunctions(org.domi.furnace.server.function.FunctionLibrary lib) {
        if (lib == null) return;
        try {
            scheduler.global().execute(() -> functions.replaceLibrary(lib));
        } catch (java.util.concurrent.RejectedExecutionException ignored) {
            // 종료 중
        }
    }

    /** 전리품 평가가 보는 월드(난수 시퀀스는 월드 시드로 — 바닐라 RandomSequences). */
    private final org.domi.furnace.server.loot.RandomSequences randomSequences;

    /** 난수 시퀀스 저장소(전리품 표와 /random 이 함께 쓴다). */
    public org.domi.furnace.server.loot.RandomSequences randomSequences() { return randomSequences; }
    private final org.domi.furnace.server.loot.LootWorld lootWorld;

    /** 그 차원 월드를 보는 전리품 월드(바이옴·차원 조건). */
    private org.domi.furnace.server.loot.LootWorld lootWorldFor(World world) {
        return new org.domi.furnace.server.loot.LootWorld() {
        public org.domi.furnace.server.world.gen.noise.NoiseRandom random() {
            return new org.domi.furnace.server.world.gen.carver.LegacyRandom(java.util.concurrent.ThreadLocalRandom.current().nextLong());
        }

        public org.domi.furnace.server.world.gen.noise.NoiseRandom randomSequence(org.domi.furnace.server.data.ResourceId id) { return randomSequences.get(id); }

        public long dayTime() { return clock.dayTime(); }

        // 하늘빛 없는 차원(네더·엔드)은 비 세기가 늘 0 — 바닐라 advanceWeatherCycle 이 hasSkyLight 일 때만 바꾼다
        public boolean isRaining() { return world.dimensionType().hasSkyLight() && clock.isRaining(); }

        public boolean isThundering() { return world.dimensionType().hasSkyLight() && clock.thunderLevel() > 0.9f; }

        /** 바닐라 Level.getBiome(pos)(확대 지터 포함). 지금 스레드가 그 region 을 가진 경우에만 — 아니면 null. */
        public String biomeAt(int x, int y, int z) {
            var dp = dataPacks;
            if (dp == null || dp.loot() == null) return null;
            int[] q = org.domi.furnace.server.world.BiomeZoom.quart(biomeZoomSeed(world), x, y, z);
            int cx = q[0] >> 2, cz = q[2] >> 2;
            if (!world.ownsChunkOnCurrentThread(cx, cz)) return null;
            int id = world.regionizer().ownerOf(cx, cz).biomeIdAtQuart(q[0], q[1], q[2]);
            var names = dp.loot().registries().entries("minecraft:worldgen/biome");
            return id >= 0 && id < names.size() ? names.get(id) : null;
        }

        /** NaturalSpawner.getRoughBiome: 확대 지터 없이 ChunkAccess.getNoiseBiome(x>>2, y>>2, z>>2) — 지금 스레드가 그 region 을 가진 경우에만. */
        public String roughBiomeAt(int x, int y, int z) {
            var dp = dataPacks;
            if (dp == null || dp.loot() == null) return null;
            int cx = x >> 4, cz = z >> 4;
            if (!world.ownsChunkOnCurrentThread(cx, cz)) return null;
            int id = world.regionizer().ownerOf(cx, cz).noiseBiomeIdAtBlock(x, y, z);
            var names = dp.loot().registries().entries("minecraft:worldgen/biome");
            return id >= 0 && id < names.size() ? names.get(id) : null;
        }

        /** 그 칸 청크를 지금 스레드가 가진 region 에서만 읽는다(아니면 null — 위치 조건은 거짓). */
        public org.domi.furnace.server.loot.LootBlockState blockAt(int x, int y, int z) {
            int cx = x >> 4, cz = z >> 4;
            if (!world.ownsChunkOnCurrentThread(cx, cz)) return null;
            return lootBlockState(world.regionizer().ownerOf(cx, cz).getBlock(x, y, z));
        }

        public String dimension() { return world.dimension(); }
        };
    }
    private final long biomeZoomSeed;

    /** 명령이 전리품 표를 평가한다(/loot): random_sequence 또는 월드 난수, 최대 스택으로 나눈 결과. 데이터 팩이 없으면 빈 목록. */
    public java.util.List<ItemStack> commandLoot(org.domi.furnace.server.loot.LootTable table, org.domi.furnace.server.loot.LootParams params) {
        var dp = dataPacks;
        if (dp == null || dp.loot() == null) return java.util.List.of();
        java.util.List<ItemStack> out = new java.util.ArrayList<>();
        for (var st : table.getRandomItems(table.context(dp.loot(), lootWorld, params, 0L))) {
            ItemStack is = dp.network().toItemStack(st);
            if (is != null && !is.isEmpty()) out.add(is);
        }
        return out;
    }

    /** 바이옴 확대 지터 시드(Level.getBiome — 명령의 biome 조건용). */
    public long biomeZoomSeed() { return biomeZoomSeed; }

    /** 그 월드의 바이옴 확대 지터 시드 = 클라이언트가 LoginPlay/Respawn 으로 받는 hashed seed(차원별 시드를 정하지 않았으면 서버 시드 — 바닐라). */
    public long biomeZoomSeed(World w) {
        return w.hasSeed() ? org.domi.furnace.server.net.protocol.play.LoginPlay.obfuscateSeed(w.seed()) : biomeZoomSeed;
    }

    /** 그 월드의 생성 시드(정하지 않았으면 서버 시드). */
    public long seedOf(World w) { return w.hasSeed() ? w.seed() : levelData.seed; }

    /**
     * /execute if predicate: 명령 문맥(origin, 있으면 this_entity)에서 조건 하나를 평가한다. 난수는 월드 난수(바닐라 level.getRandom).
     * 데이터 팩이 없으면 false.
     */
    public boolean commandCondition(World w, org.domi.furnace.server.loot.LootCondition cond, org.domi.furnace.server.loot.LootParams params) {
        var dp = dataPacks;
        if (dp == null || dp.loot() == null) return false;
        var lw = lootWorldFor(w);
        return dp.loot().test(cond, new org.domi.furnace.server.loot.LootContext(dp.loot(), lw, params, lw.random()));
    }

    /** 명령이 아이템 수정자를 적용한다(/item modify, 바닐라 ItemCommands.applyModifier — 월드 난수). */
    public ItemStack applyModifier(org.domi.furnace.server.loot.LootFunction modifier, ItemStack stack, org.domi.furnace.server.loot.LootParams params) {
        var dp = dataPacks;
        if (dp == null || dp.loot() == null) return stack;
        var ctx = new org.domi.furnace.server.loot.LootContext(dp.loot(), lootWorld, params, lootWorld.random());
        ItemStack out = dp.network().toItemStack(modifier.apply(lootStack(stack), ctx));
        return out == null ? ItemStack.EMPTY : out;
    }

    /** 서버 아이템 → 전리품 스택(명령용 공개판). */
    public static org.domi.furnace.server.loot.LootStack toLootStack(ItemStack s) { return lootStack(s); }

    /** 플레이어의 전리품 엔티티 보기(명령용 공개판). */
    public static org.domi.furnace.server.loot.LootEntity playerLootEntity(Player p) { return lootEntity(p); }

    /** 전리품 조건이 보는 플레이어(바닐라 ADVANCEMENT_ENTITY 의 this_entity). */
    private static org.domi.furnace.server.loot.LootEntity lootEntity(Player p) { return org.domi.furnace.server.entity.LootEntities.of(p); }

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
                if (!levelData.generateFeatures) return null;   // ServerLevel.findNearestMapStructure: level.dat 의 generate_features
                var ids = org.domi.furnace.server.world.gen.structure.StructureRegistry.structureTag(tag);
                if (ids == null) return null;
                var f = gen.structureLocator().findNearest(ids, x, y, z, radius);
                return f == null ? null : new int[] {f.x(), f.z()};
            }

            public int[] findNearestStructurePos(String tag, int x, int y, int z, int radius) {
                if (!levelData.generateFeatures) return null;
                var ids = org.domi.furnace.server.world.gen.structure.StructureRegistry.structureTag(tag);
                if (ids == null) return null;
                var f = gen.structureLocator().findNearest(ids, x, y, z, radius);
                return f == null ? null : new int[] {f.x(), f.y(), f.z()};
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

            public void giveExperience(int points) { p.runOnOwner(() -> p.vitals().giveExperiencePoints(points)); }

            /**
             * 바닐라 AdvancementRewards.grant 의 전리품: 표를 ADVANCEMENT_REWARD 문맥(this_entity = 플레이어, origin = 플레이어 위치)으로 굴려
             * 하나씩 인벤토리에 넣고(들어가면 픽업 소리 0.2, 플레이어 난수), 못 넣은 분은 던지되 줍기 지연 없이 그 플레이어만 줍게 한다.
             * 하나라도 들어갔으면 열린 창을 동기화한다. giveLoot 가 주인 스레드(runOnOwner)로 보낸 뒤 여기서 돈다.
             */
            public void giveLoot(String tableName) {
                // /advancement grant 는 전역 스레드에서 award 를 부른다 — 인벤토리·소리·창은 region 소유 상태라 주인 스레드로 보낸다
                p.runOnOwner(() -> giveLootOnOwner(tableName));
            }

            private void giveLootOnOwner(String tableName) {
                var level = p.currentLevel;
                boolean any = false;
                for (ItemStack st : giftLoot(lootWorld, tableName, lootEntity(p), p.x(), p.y(), p.z())) {
                    ItemStack left = p.inventory().add(st, false, org.domi.furnace.server.item.ItemStackCodecs.maxStackSize(st),
                            org.domi.furnace.server.item.ItemTools.damage(st) > 0);
                    if (left.isEmpty()) {
                        if (level != null) {
                            var r = p.random();
                            level.playSound(null, p.x(), p.y(), p.z(), "minecraft:entity.item.pickup", "players", 0.2F,
                                    ((r.nextFloat() - r.nextFloat()) * 0.7F + 1.0F) * 2.0F);
                        }
                        any = true;
                    } else {
                        final ItemStack rest = left;
                        world.runAt(p.x(), p.z(), region -> {
                            var ie = region.playerDrop(p, rest, false, false);
                            if (ie != null) {
                                ie.setNoPickUpDelay();
                                ie.setTarget(p.uuid());
                            }
                        });
                    }
                }
                if (any) {
                    synchronized (p.menuLock()) { p.containerMenu().broadcastChanges(); }
                }
            }

            public void broadcast(Text message) { broadcastSystemMessage(message, false); }

            public Text advancementDone(String advancement, Text message) { return gameEvents().playerAdvancementDone(p, advancement, message); }

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

            // 바닐라는 award 안에서 바로 실행한다. 함수 관리자는 전역 스레드 전용이라 region 스레드에서는 전역으로 넘긴다(다음 전역 틱 차이).
            public void runFunction(String function) {
                // 실행 주체(위치·회전)는 플레이어 소유 스레드인 여기서 만든다
                var source = org.domi.furnace.server.command.CommandSource.player(ServerCore.this, p).withSilent().withPermission(2);
                var id = org.domi.furnace.server.data.ResourceId.parse(function);
                Runnable run = () -> {
                    var fm = functions();
                    if (fm != null) fm.library().function(id).ifPresent(f -> fm.execute(f, source, org.domi.furnace.api.event.server.FunctionExecuteEvent.Trigger.ADVANCEMENT));
                };
                // 전역 스레드에서 부여했으면(/advancement grant 등) 바닐라처럼 award 안에서 바로, region 스레드면 다음 전역 틱에
                if (((org.domi.furnace.api.concurrent.ThreadOwner) scheduler().global()).isOwnerThread()) run.run();
                else scheduler().global().execute(run);
            }
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
        if (chest.lootTable == null || dataPacks == null || dataPacks.loot() == null) return;
        String table = chest.lootTable;
        chest.lootTable = null;
        generatedLoot(opener, table);
        fillLoot(chest.items, table, chest.lootSeed, new double[]{chest.pos().x() + 0.5, chest.pos().y() + 0.5, chest.pos().z() + 0.5}, opener);
    }

    /** 바닐라 ContainerEntity.unpackChestVehicleLootTable — 엔티티 용기의 전리품 표를 푼다(그 엔티티의 region 스레드에서). 위치는 엔티티 위치. */
    public void unpackLoot(org.domi.furnace.server.entity.ContainerEntity entity, Player opener) {
        String table = entity.lootTable();
        if (table == null || dataPacks == null || dataPacks.loot() == null) return;
        entity.setLootTable(null);
        generatedLoot(opener, table);
        var e = entity.asEntity();
        fillLoot(entity.chestVehicleItems(), table, entity.lootTableSeed(), new double[]{e.x(), e.y(), e.z()}, opener);
    }

    /** 바닐라 unpackLootTable: 플레이어가 열어 전리품이 풀리면 전리품을 만들기 전에 player_generates_container_loot. */
    private static void generatedLoot(Player opener, String table) {
        if (opener == null) return;
        String id = table.indexOf(':') < 0 ? "minecraft:" + table : table;
        opener.triggerAdvancement(a -> a.triggerContainerLoot(id));
    }

    /** LootTable.fill(container, params, seed): 빈 칸에 전리품을 흩어 넣는다. 데이터 팩이 없으면 아무것도. */
    private void fillLoot(ItemStack[] items, String tableName, long seed, double[] origin, Player opener) {
        var dp = dataPacks;
        if (dp == null || dp.loot() == null) return;
        var loot = dp.loot();
        var table = loot.table(tableName);
        var slots = new org.domi.furnace.server.loot.LootStack[items.length];
        var occupied = new org.domi.furnace.server.loot.LootStack("minecraft:stone", 1);
        for (int i = 0; i < slots.length; i++) if (items[i] != null && !items[i].isEmpty()) slots[i] = occupied;
        var params = new org.domi.furnace.server.loot.LootParams().with(org.domi.furnace.server.loot.LootParams.ORIGIN, origin);
        if (opener != null) params.with(org.domi.furnace.server.loot.LootParams.THIS_ENTITY, () -> "minecraft:player");
        table.fill(slots, loot, lootWorld, params, seed);
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] != null && slots[i] != occupied) items[i] = dp.network().toItemStack(slots[i]);
        }
    }

    /** 지금 레시피 매니저(데이터 팩 없이 뜬 서버면 null). */
    public org.domi.furnace.server.recipe.RecipeManager recipes() {
        var dp = dataPacks;
        return dp == null ? null : dp.recipes();
    }

    /** 플러그인 호스트(Furnace API 파사드·이벤트 버스·플러그인 관리자). */
    public org.domi.furnace.server.plugin.PluginHost plugins() { return pluginHost; }

    /** 서버 게임 코드가 플러그인 이벤트를 부르는 입구(ConnectionHandler 등). */
    public org.domi.furnace.server.plugin.GameEvents gameEvents() { return pluginHost.gameEvents(); }

    /** 이벤트 버스(구독자가 없으면 이벤트 객체를 만들지 않도록 hasSubscribers 로 먼저 묻는다). */
    public org.domi.furnace.server.plugin.EventBusImpl events() { return pluginHost.eventsImpl(); }

    /** 워치독용 전역 틱 박동(마지막 scheduledTick 시작 시각)과 그 스레드. */
    /** 0 = 첫 전역 틱 전(기동 중). 워치독은 첫 틱 전에는 멈춘 것으로 보지 않는다. */
    private volatile long globalBeat;
    private volatile Thread globalTickThread;

    /** 전역 틱(scheduledTick) 소요 시간 분포 — /furnace 관측용. */
    private final org.domi.furnace.server.sched.TickTimeRing globalTickRing = new org.domi.furnace.server.sched.TickTimeRing(512);

    public org.domi.furnace.server.sched.TickTimeRing.Stats globalTickTimes() { return globalTickRing.stats(); }

    /** 전역 틱 시작 시각(/tps — 15분 × 20회를 덮는 2^15 칸)과 시간 창별 소요 시간(/mspt). 전역 스레드만 쓴다. */
    private final org.domi.furnace.server.sched.TickStartRing globalTickStarts = new org.domi.furnace.server.sched.TickStartRing(1 << 15);
    private final org.domi.furnace.server.sched.TickWindows globalTickWindows = new org.domi.furnace.server.sched.TickWindows();

    public org.domi.furnace.server.sched.TickStartRing globalTickStarts() { return globalTickStarts; }

    public org.domi.furnace.server.sched.TickWindows globalTickWindows() { return globalTickWindows; }

    /** 활성 region(플레이어가 있는 region) 평균 TPS 의 1초 표본 기록 — getTPS 의 1분·5분·15분 값. 전역 스레드가 쓴다. */
    private final org.domi.furnace.server.sched.ActiveTpsHistory activeTpsHistory = new org.domi.furnace.server.sched.ActiveTpsHistory();

    /** 표본 창(1초)과 /tps·getTPS 의 5초 창, 잴 수 있으려면 필요한 기록 길이. */
    static final long TPS_SAMPLE_WINDOW = 1_000_000_000L, TPS_LIVE_WINDOW = 5_000_000_000L, TPS_MIN_HISTORY = 1_000_000_000L;

    private org.domi.furnace.server.world.Regionizer.ActiveSummary activeSummary(long now, long window) {
        var sum = org.domi.furnace.server.world.Regionizer.ActiveSummary.NONE;
        for (World w : worlds()) {
            var rz = w.regionizer();
            if (rz != null) sum = sum.plus(rz.activeSummary(now, window, TPS_MIN_HISTORY));
        }
        return sum;
    }

    /** 1초마다(전역 스레드): 활성 region 평균(없으면 전역 박동)을 한 표본으로 적는다. */
    private void sampleActiveTps(long now) {
        if (!activeTpsHistory.due(now)) return;
        double v = activeSummary(now, TPS_SAMPLE_WINDOW).weightedAverage();
        if (Double.isNaN(v)) v = globalTickStarts.rate(now, TPS_SAMPLE_WINDOW, TPS_MIN_HISTORY);
        activeTpsHistory.record(now, v);
    }

    /** 서버 TPS(플러그인 API Furnace.tps() 와 /tps 의 바탕). 아무 스레드. 정의는 docs/api/tps.md. */
    public org.domi.furnace.api.world.ServerTps serverTps() {
        long now = System.nanoTime();
        var live = activeSummary(now, TPS_LIVE_WINDOW);
        double g5 = globalTickStarts.rate(now, 5L * SEC_NANOS, TPS_MIN_HISTORY);
        double a5 = live.weightedAverage();
        if (Double.isNaN(a5)) a5 = g5;
        return new org.domi.furnace.api.world.ServerTps(a5,
                activeAvgOrGlobal(now, 60L * SEC_NANOS), activeAvgOrGlobal(now, 300L * SEC_NANOS), activeAvgOrGlobal(now, 900L * SEC_NANOS),
                live.activeRegions(), live.activePlayers(), live.slowestActive(), live.slowestAny(),
                g5, globalTickStarts.rate(now, 60L * SEC_NANOS, TPS_MIN_HISTORY), globalTickStarts.rate(now, 300L * SEC_NANOS, TPS_MIN_HISTORY),
                globalTickStarts.rate(now, 900L * SEC_NANOS, TPS_MIN_HISTORY));
    }

    private static final long SEC_NANOS = 1_000_000_000L;

    private double activeAvgOrGlobal(long now, long window) {
        double v = activeTpsHistory.average(now, window);
        return Double.isNaN(v) ? globalTickStarts.rate(now, window, TPS_MIN_HISTORY) : v;
    }

    /** 전역 스레드 이름(관측용). */
    public String globalThreadName() {
        return scheduler.global() instanceof org.domi.furnace.server.sched.GlobalThread g ? g.ownerName() : "global";
    }

    /** 전역 스레드의 대기 작업 수(관측용). */
    public int globalQueueLength() {
        return scheduler.global() instanceof org.domi.furnace.server.sched.GlobalThread g ? g.queueLength() : 0;
    }

    /** 워치독이 볼 곳들: 전역 틱 + 차원마다 region 틱. 잠금 없이 휘발성 값만 읽는다. */
    public List<ServerWatchdog.Probe> watchdogProbes() {
        List<ServerWatchdog.Probe> out = new ArrayList<>();
        out.add(new ServerWatchdog.Probe() {
            @Override public String name() { return "global tick"; }
            @Override public long stalledNanos(long now) { long b = globalBeat; return b == 0 ? 0 : now - b; }
            @Override public Thread thread() { return globalTickThread; }
        });
        for (World w : worlds()) {
            var rz = w.regionizer();
            if (rz == null) continue;
            out.add(new ServerWatchdog.Probe() {
                @Override public String name() { return "region tick " + rz.dimension(); }
                @Override public long stalledNanos(long now) { return rz.stalledNanos(now); }
                @Override public Thread thread() { return rz.longestRunningThread(System.nanoTime()); }
            });
        }
        return out;
    }

    /**
     * RCON 명령: IO 스레드가 부른다. 전역 스레드에서 실행하고(performPrefixedCommandAsync) 컨텍스트가 끝날 때까지 기다려 출력을 돌려준다.
     * 전역/region 스레드에서 부르면 데드락이므로 막는다(CommandFuture 가드).
     */
    public String runRconCommand(String command) throws Exception {
        var spawn = world.defaultSpawn();
        var sink = new org.domi.furnace.server.remote.RconConsole(() -> settings.get().broadcastRconToOps, spawn.x(), spawn.y(), spawn.z());
        commands.performPrefixedCommandAsync(org.domi.furnace.server.command.CommandSource.rcon(this, sink), command)
                .get(5, java.util.concurrent.TimeUnit.MINUTES);
        return sink.takeOutput();
    }

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

    /** MinecraftServer.isCommandBlockEnabled: server.properties enable-command-block. */
    public boolean commandBlockEnabled() { return settings.get().enableCommandBlock; }

    /**
     * 명령 블록·명령 블록 수레 명령 실행(BaseCommandBlock.performCommand 의 서버 쪽): 권한 2 소스로 전역 스레드의 명령 큐에서 돌리고,
     * 컨텍스트가 모두 끝나면(지연 결과 포함) 성공 콜백 횟수로 done 을 부른다. done 은 전역 스레드(또는 종료 중이면 호출 스레드)에서 불린다.
     */
    public void runBlockCommand(World w, org.domi.furnace.server.block.BlockCommandSink sink, String command, java.util.function.IntConsumer done) {
        java.util.concurrent.atomic.AtomicInteger success = new java.util.concurrent.atomic.AtomicInteger();
        var source = org.domi.furnace.server.command.CommandSource.block(this, w, sink).withCallback(
                org.domi.furnace.server.command.exec.ResultCallback.sync((ok, r) -> { if (ok) success.incrementAndGet(); }));
        commands().performPrefixedCommandAsync(source, command).whenComplete((r, ex) -> {
            if (ex != null) log.error("[CMDBLOCK] 명령 블록 명령 실행 실패: {}", command, ex);
            done.accept(success.get());
        });
    }

    public org.domi.furnace.server.chat.ServicesKeys servicesKeys() { return servicesKeys; }

    public org.domi.furnace.server.world.GameRules gameRules() { return gameRules; }

    public org.domi.furnace.server.world.WorldClock clock() { return clock; }

    public org.domi.furnace.server.world.WorldBorder border() { return border; }

    /** /tick 이 바꾸는 서버 틱 상태(모든 차원이 같은 객체를 본다). */
    public org.domi.furnace.server.world.TickState tickState() { return world.tickState(); }

    // ------------------------------------------------------------------ /debug start·stop 의 틱 프로파일(바닐라 MinecraftServer.tickCount·profiler 상태)

    /** 바닐라 tickCount 에 해당하는 서버 틱 번호. 전역 스레드(전역 틱 반복)만 늘린다. */
    private volatile long serverTickCount;
    /** /debug start 가 요청했고 다음 틱 머리에서 시작을 기다리는 중. */
    private volatile boolean tickProfileRequested;
    /** 진행 중인 틱 프로파일 {시작 nanoTime, 시작 틱 번호}. 없으면 null. */
    private volatile long[] tickProfile;

    /** 바닐라 isRecordingMetrics 의 "요청됨 또는 진행 중". */
    public boolean isTickProfiling() { return tickProfileRequested || tickProfile != null; }

    /** /debug start: 다음 틱 머리에서 시작하게 요청한다(바닐라 startTickMetric). */
    public void requestTickProfile() { tickProfileRequested = true; }

    /**
     * /debug stop: 진행 중이던 프로파일을 끝내고 {걸린 시간 nanos, 센 틱 수}를 돌려준다. 시작 틱 전에 멈추면 {0, 0}이고 요청은 그대로 남는다
     * (바닐라 stopRecordingMetrics 가 아직 시작 안 한 요청을 지우지 않는 것과 같다).
     */
    public long[] stopTickProfile() {
        long[] p = tickProfile;
        if (p == null) return new long[] {0L, 0L};
        tickProfile = null;
        return new long[] {System.nanoTime() - p[0], serverTickCount - p[1]};
    }

    /** 틱 하나의 머리: 요청이 있으면 프로파일을 시작하고(틱 번호를 올리기 전) 서버 틱 번호를 올린다. */
    private void beginServerTick() {
        if (tickProfileRequested) {
            tickProfileRequested = false;
            tickProfile = new long[] {System.nanoTime(), serverTickCount};
        }
        serverTickCount++;
    }

    private float globalTickCredit;

    /**
     * 전역 틱 반복(50ms 마다)이 부르는 진입점. /tick rate 는 한 번에 rate/20 틱씩 낸다(20 미만이면 몇 번 걸러서), /tick freeze 중에는
     * 시간·날씨·보더를 멈추고 /tick step 으로 요청한 만큼만 낸다.
     */
    public void scheduledTick() {
        long t0 = System.nanoTime();
        globalBeat = t0;   // 워치독이 보는 전역 틱 박동
        globalTickThread = Thread.currentThread();
        globalTickStarts.record(t0);
        try {
            sampleActiveTps(t0);
            scheduledTick0();
        } finally {
            long took = System.nanoTime() - t0;
            globalTickRing.record(took);
            globalTickWindows.record(t0, took);
        }
    }

    // ------------------------------------------------------------------ 전역 틱 붙잡기(백업 기준점)·빈 서버 일시정지

    private final java.util.concurrent.atomic.AtomicInteger globalHolds = new java.util.concurrent.atomic.AtomicInteger();

    /** 전역 게임 틱(시간·날씨·함수)을 붙잡는다 / 놓는다. 붙잡힌 동안 전역 스레드는 작업만 돌린다. 횟수로 센다. */
    public void holdGlobalTicks(boolean on) {
        if (on) globalHolds.incrementAndGet();
        else if (globalHolds.decrementAndGet() < 0) globalHolds.set(0);
    }

    public boolean globalTicksHeld() { return globalHolds.get() > 0; }

    /** 전역 스레드가 지금 도는 틱을 끝내면 완료(붙잡은 뒤 부른다 — 그 뒤 전역 틱은 건너뛴다). */
    public java.util.concurrent.CompletableFuture<Void> quiesceGlobal() {
        return java.util.concurrent.CompletableFuture.runAsync(() -> { }, r -> scheduler.global().execute(r));
    }

    private volatile org.domi.furnace.server.maintenance.WorldBackup backup;

    /** 월드 백업(/furnace backup). 기동 전·시험은 null. */
    public org.domi.furnace.server.maintenance.WorldBackup backup() { return backup; }

    public void backup(org.domi.furnace.server.maintenance.WorldBackup b) { this.backup = b; }

    private volatile org.domi.furnace.server.maintenance.IdlePause idlePause;

    /** 빈 서버 일시정지(furnace.properties idle-pause.*). null 이면 끔(기본 — 1.21.1 바닐라에는 없다). */
    public void idlePause(org.domi.furnace.server.maintenance.IdlePause p) { this.idlePause = p; }

    public org.domi.furnace.server.maintenance.IdlePause idlePause() { return idlePause; }

    /** 지금 빈 서버 일시정지 중인가. */
    public boolean pausedWhileEmpty() {
        var p = idlePause;
        return p != null && p.paused();
    }

    private void scheduledTick0() {
        commands.queue().tick();   // 명령 컨텍스트 경과 틱(정지 중에도 센다 — head-of-line 관측)
        var ts = tickState();
        // 바닐라 1.21.2+ MinecraftServer.tickServer 의 pause-when-empty: 비어 있는 동안 틱을 세다가 넘으면 틱을 멈춘다(연결 처리만)
        var pause = idlePause;
        if (pause != null && pause.onGlobalTick(world.onlinePlayers().isEmpty(), ts.sprinting())) return;
        if (globalTicksHeld()) return;
        if (ts.sprinting()) {
            tickSprint(ts);
            return;
        }
        if (ts.frozen()) {
            if (ts.takeGlobalStep()) {
                tick();
                if (ts.globalStepsLeft() == 0) broadcastTickStep(0);
            } else {
                beginServerTick();   // 바닐라는 정지 중에도 서버 틱 번호가 간다
            }
            return;
        }
        globalTickCredit += ts.rate() / org.domi.furnace.server.world.TickState.DEFAULT_RATE;
        int n = Math.min((int) globalTickCredit, 1000);
        globalTickCredit -= (int) globalTickCredit;
        for (int i = 0; i < n; i++) tick();
    }

    /** sprint 중 전역 틱 한 번 호출에 쓰는 최대 시간: 넘으면 돌려주고 다음 호출에서 잇는다(그 사이 전역 인박스·명령이 돈다). */
    private static final long SPRINT_BUDGET_NANOS = 40_000_000L;

    /**
     * /tick sprint 중 전역 틱(설계 docs/feature-parity/tick-sprint-design-2026-10-28.md): 남은 전역 sprint 틱을 기다림 없이 돌리고,
     * 전역 틱을 다 썼고 모든 차원의 모든 region 이 sprint 틱을 다 돌았으면 끝내고 보고한다.
     */
    private void tickSprint(org.domi.furnace.server.world.TickState ts) {
        long until = System.nanoTime() + SPRINT_BUDGET_NANOS;
        while (ts.sprinting() && ts.sprintGlobalLeft() > 0 && System.nanoTime() < until) {
            // lockstep: 모든 참여 region 이 마친 틱 수보다 앞서 시작하지 않는다(전역 진행 ≤ region 최솟값 + 1)
            int g = ts.sprintGeneration();
            long min = Long.MAX_VALUE;
            for (World w : worlds()) min = Math.min(min, w.regionizer().sprintMinDone(g));
            if (ts.sprintGlobalDone() > min) {
                java.util.concurrent.locks.LockSupport.parkNanos(50_000L);
                continue;
            }
            // 바닐라 checkShouldSprintThisTick: 남은 수를 틱 전에 줄인다(틱 안의 함수가 sprint 를 다시 걸면 N 이 그대로 남는다)
            ts.takeSprintGlobalTick();
            tick();
        }
        if (!ts.sprinting() || ts.sprintGlobalLeft() > 0) return;
        int gen = ts.sprintGeneration();
        for (World w : worlds()) if (!w.regionizer().sprintDone(gen)) return;
        finishSprint();
    }

    /**
     * 바닐라 ServerTickRateManager.finishTickSprint: 보고(commands.tick.sprint.report — 초당 틱, 틱당 밀리초)를 서버 명령 소스로 알리고
     * 정지 상태를 되돌린다. sprint 중이 아니었으면 거짓. 시간은 바닐라의 틱 작업 시간 합 대신 시작부터 끝까지의 벽시계 시간이다.
     */
    public boolean finishSprint() {
        var ts = tickState();
        var r = ts.endSprint(System.nanoTime());
        if (r == null) return false;
        long ticks = r.ticks();
        double ms = Math.max(1.0, (double) r.elapsedNanos()) / 1_000_000.0;
        int tps = (int) (1000L * ticks / ms);
        String mspt = String.format(java.util.Locale.ROOT, "%.2f", ticks == 0L ? (double) ((float) ts.nanosPerTick() / 1_000_000.0F) : ms / ticks);
        org.domi.furnace.server.command.CommandSource.console(this)
                .sendSuccess(() -> org.domi.furnace.api.text.Text.translatable("commands.tick.sprint.report", tps, mspt), true);
        broadcastTickState();
        return true;
    }

    /** 바닐라 requestGameToSprint: 정지를 풀고 ticks 만큼 sprint. 이미 sprint 중이었으면 참. */
    public boolean startSprint(int ticks) {
        boolean was = tickState().startSprint(ticks, System.nanoTime());
        broadcastTickState();
        return was;
    }

    /** 모든 접속자에게 틱 속도·정지 상태(ClientboundTickingStatePacket)를 알린다. */
    public void broadcastTickState() {
        var ts = tickState();
        var pkt = new org.domi.furnace.server.net.protocol.play.TickingState(ts.rate(), ts.frozen());
        for (Player p : players()) p.send(pkt);
    }

    /** 정지 중 남은 진행 틱 수(ClientboundTickingStepPacket). */
    public void broadcastTickStep(int steps) {
        var pkt = new org.domi.furnace.server.net.protocol.play.TickingStep(steps);
        for (Player p : players()) p.send(pkt);
    }

    /** 전역 틱(20TPS): 보더 보간 진행, 시간·날씨 진행과 주기적 시간 동기화. */
    public void tick() {
        beginServerTick();
        functions.tick();   // 바닐라 tickChildren: 함수(#tick, 리로드 뒤 #load)가 레벨 틱보다 먼저
        border.tick();
        java.util.function.Consumer<org.domi.furnace.server.net.protocol.Packet> all = packet -> { for (Player p : players()) p.send(packet); };
        clock.tickWeather(gameRules, all, packet -> { for (Player p : players()) if (world.dimension().equals(p.dimension())) p.send(packet); });
        tickSleep();
        // 바닐라 ServerLevel.tickTime: gameTime+1 → 예약된 함수 → dayTime+1. 예약 함수는 clock 락 밖에서 돈다.
        // 한계(가정): 명령 큐가 idle 이면 함수 본문이 여기서 동기로 끝나 바닐라 순서와 같다. 앞선 컨텍스트(#tick 함수, 다른 명령)가
        // region 왕복으로 기다리는 중이면 FIFO 뒤로 밀려 dayTime 증가 뒤나 다음 틱 이후에 돈다(context-notes 의도한 차이).
        long gameTimeNow = clock.advanceGameTime();
        scheduledEvents.tick(gameTimeNow, () -> !commands.queue().isClosed(), functions::runScheduled);
        clock.advanceDayTime(gameRules, all);
        // 바닐라 ServerLevel.tick: 시간 진행 뒤 청크 틱의 특수 스포너 — 차원에 하나뿐인 포위·상인은 여기서 게임 틱마다 한 번
        var spawners = world.dimensionSpawners();
        if (spawners != null) {
            try {
                spawners.tick();
            } catch (RuntimeException e) {
                log.error("[SPAWN] 포위·상인 스포너 실패", e);
            }
        }
    }

    /**
     * ServerLevel.tick 의 밤 건너뛰기(오버월드 — 다른 차원은 잘 수 없다): 자는 사람이 playersSleepingPercentage 만큼이고 그만큼이
     * 100틱 넘게 잤으면 아침으로·깨우기·날씨 초기화. 깨우기는 각 플레이어의 region 스레드로 넘긴다(다음 region 틱).
     */
    private void tickSleep() {
        int pct = gameRules.getInt("playersSleepingPercentage");
        var status = world.sleepStatus();
        if (!status.areEnoughSleeping(pct)) return;
        var here = world.playersHere();
        if (!status.areEnoughDeepSleeping(pct, here)) return;
        clock.skipNight(gameRules, () -> {
            status.removeAllSleepers();   // wakeUpAllPlayers
            for (Player p : here) {
                if (!p.isSleeping()) continue;
                world.runAt(p.x(), p.z(), r -> {
                    if (p.isSleeping()) org.domi.furnace.server.entity.PlayerSleeping.stopSleepInBed(p, r.level(), false, false);
                });
            }
        });
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
        levelData.customBossEvents = customBossEvents.save();
        levelData.scheduledEvents = scheduledEvents.save();
        scoreboard.saveIfDirty();   // 바닐라는 overworld 데이터 저장소(data/scoreboard.dat)와 함께 저장
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

    /** 바닐라 MinecraftServer.setDifficulty: 하드코어면 HARD 고정, 바뀐 값을 모두에게 알린다. 플러그인 난이도 프로필은 내린다. */
    public void setDifficulty(org.domi.furnace.server.world.Difficulty difficulty, boolean ignoreLock) {
        if (!ignoreLock && levelData.difficultyLocked) return;
        synchronized (difficultyLock) {
            clearProfileState();
            levelData.difficulty = levelData.hardcore ? org.domi.furnace.server.world.Difficulty.HARD : difficulty;
        }
        var packet = new org.domi.furnace.server.net.protocol.play.ChangeDifficulty(levelData.difficulty.id(), levelData.difficultyLocked);
        for (Player p : players()) p.send(packet);
    }

    // ------------------------------------------------------------------ 난이도 프로필(불만 표 37번, 플러그인 전용·저장 안 함)

    private final Object difficultyLock = new Object();
    private volatile org.domi.furnace.api.world.DifficultyProfile difficultyProfile;   // null = 바닐라
    private volatile String difficultyProfileOwner;

    /** 지금 플러그인 프로필(없으면 null). */
    public org.domi.furnace.api.world.DifficultyProfile difficultyProfile() { return difficultyProfile; }

    public String difficultyProfileOwner() { return difficultyProfileOwner; }

    /** 프로필을 건다: 난이도는 base 로(클라에 알린다), 배율은 DifficultyModifiers 로. 하드코어에서 base 가 HARD 가 아니면 IllegalStateException. */
    public void setDifficultyProfile(String owner, org.domi.furnace.api.world.DifficultyProfile profile) {
        var base = org.domi.furnace.server.world.Difficulty.valueOf(profile.base().name());
        if (levelData.hardcore && base != org.domi.furnace.server.world.Difficulty.HARD) {
            throw new IllegalStateException("하드코어 월드의 난이도는 HARD 뿐이다");
        }
        synchronized (difficultyLock) {
            levelData.difficulty = base;
            difficultyProfile = profile;
            difficultyProfileOwner = owner;
            org.domi.furnace.server.world.DifficultyModifiers.set(profile.playerDamageMultiplier(), profile.localDifficultyMultiplier());
        }
        var packet = new org.domi.furnace.server.net.protocol.play.ChangeDifficulty(levelData.difficulty.id(), levelData.difficultyLocked);
        for (Player p : players()) p.send(packet);
    }

    /** 프로필을 내린다(owner 가 null 이면 누구 것이든, 아니면 그 주인 것일 때만). 난이도(base)는 그대로. 내렸으면 true. */
    public boolean clearDifficultyProfile(String owner) {
        synchronized (difficultyLock) {
            if (difficultyProfile == null || (owner != null && !owner.equals(difficultyProfileOwner))) return false;
            clearProfileState();
            return true;
        }
    }

    private void clearProfileState() {
        difficultyProfile = null;
        difficultyProfileOwner = null;
        org.domi.furnace.server.world.DifficultyModifiers.reset();
    }

    /** 바닐라 MinecraftServer.setDifficultyLocked: 잠금 상태를 바꾸고 모두에게 알린다. */
    public void setDifficultyLocked(boolean locked) {
        levelData.difficultyLocked = locked;
        var packet = new org.domi.furnace.server.net.protocol.play.ChangeDifficulty(levelData.difficulty.id(), levelData.difficultyLocked);
        for (Player p : players()) p.send(packet);
    }

    /**
     * 바닐라 DedicatedServer.isUnderSpawnProtection(오버월드): OP 가 한 명이라도 있고, 이 플레이어가 OP 가 아니며,
     * spawn-protection > 0 이고 월드 스폰과의 체비셰프 거리가 그 이하인가.
     */
    public boolean isUnderSpawnProtection(int x, int z, Player p) {
        if (!"minecraft:overworld".equals(p.dimension())) return false;   // 바닐라 DedicatedServer.isUnderSpawnProtection: 오버월드만
        if (lists.getOps().isEmpty()) return false;
        if (lists.isOp(profileOf(p))) return false;
        int radius = settings.get().spawnProtection;
        if (radius <= 0) return false;
        var spawn = world.defaultSpawn();
        return Math.max(Math.abs(x - spawn.x()), Math.abs(z - spawn.z())) <= radius;
    }

    /** 바닐라 Level.mayInteract(ServerLevel): 스폰 보호 밖이고 월드 보더 안. */
    public boolean mayInteract(Player p, int x, int z) {
        org.domi.furnace.server.world.WorldBorder own = p == null ? null : p.viewLayers().enforcedBorder();   // 플러그인이 enforce 한 개인 보더(PlayerViews)
        return !isUnderSpawnProtection(x, z, p) && (own != null ? own : border).isWithinBounds(x, z);
    }

    /** 바닐라 ServerLevel.setDefaultSpawnPos: 바뀌었으면 level.dat 에 남기고 모두에게 알린다. */
    public void setWorldSpawn(int x, int y, int z, float angle) {
        var old = world.defaultSpawn();
        if (old.x() == x && old.y() == y && old.z() == z && old.yaw() == angle) return;
        levelData.spawnX = x;
        levelData.spawnY = y;
        levelData.spawnZ = z;
        levelData.spawnAngle = angle;
        var spawn = new org.domi.furnace.server.world.SpawnPoint(x, y, z, angle);
        world.defaultSpawn(spawn);
        // 네더·엔드는 공유 스폰(DerivedLevelData — 오버월드 것)이라 따라간다. 따로 level.dat 을 가진 추가 월드는 자기 스폰을 유지한다.
        for (World w : worlds()) if (w != world && !w.isExtraWorld()) w.defaultSpawn(spawn);
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
        public org.domi.furnace.server.plugin.GameEvents gameEvents() { return pluginHost.gameEvents(); }
        public org.domi.furnace.server.world.Difficulty difficulty() { return levelData.difficulty; }
        public boolean gameRule(String name) { return gameRules.getBoolean(name); }
        public boolean pvp() { return settings.get().pvp; }
        public void broadcastSystemMessage(Text message) { ServerCore.this.broadcastSystemMessage(message, false); }
        public void broadcastDeathMessage(Player player, Text message) {
            // 바닐라: 팀 없음·always → 전원(콘솔 포함), hideForOtherTeams → 같은 팀(자신 제외), hideForOwnTeam → 다른 팀(팀 없으면 전원), never → 없음
            var team = scoreboard.getPlayersTeam(player.name());
            var v = team == null ? org.domi.furnace.server.scores.PlayerTeam.Visibility.ALWAYS : team.deathMessageVisibility();
            switch (v) {
                case ALWAYS -> ServerCore.this.broadcastSystemMessage(message, false);
                case HIDE_FOR_OTHER_TEAMS -> {
                    for (String m : team.playersCopy()) {
                        Player q = playerByName(m);
                        if (q != null && q != player) q.sendSystemMessage(message, false);
                    }
                }
                case HIDE_FOR_OWN_TEAM -> {
                    for (Player q : players()) if (scoreboard.getPlayersTeam(q.name()) != team) q.sendSystemMessage(message, false);
                }
                case NEVER -> { }
            }
        }
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
        public float borderDamage(Player player, double x, double y, double z) {
            org.domi.furnace.server.world.WorldBorder own = player.viewLayers().enforcedBorder();
            return (own != null ? own : border).computePlayerDamage(x - 0.3, z - 0.3, x + 0.3, z + 0.3, x, z);
        }
    };

    /** 블록 레벨이 보는 서버 쪽 정보(게임룰·난이도·보더·날씨·시간·접속자). */
    private final org.domi.furnace.server.block.LevelServices levelServices = new org.domi.furnace.server.block.LevelServices() {
        public boolean gameRule(String name) { return gameRules.getBoolean(name); }
        public int gameRuleInt(String name) { return gameRules.getInt(name); }
        public org.domi.furnace.server.plugin.GameEvents gameEvents() { return pluginHost == null ? org.domi.furnace.server.plugin.GameEvents.NONE : pluginHost.gameEvents(); }
        public int difficulty() { return levelData.difficulty.id(); }
        public boolean pvp() { return settings.get().pvp; }
        public org.domi.furnace.server.scores.Scoreboard scoreboard() { return scoreboard; }
        public boolean worldAdventure() { return levelData.gameType == org.domi.furnace.server.world.GameMode.ADVENTURE; }
        public void runSignCommand(Player player, int x, int y, int z, String command) {
            scheduler().global().execute(() -> commands().performPrefixedCommand(
                    org.domi.furnace.server.command.CommandSource.sign(ServerCore.this, player, x, y, z), command));
        }
        public boolean commandBlockEnabled() { return ServerCore.this.commandBlockEnabled(); }
        public org.domi.furnace.api.text.Text resolveSignText(int x, int y, int z, org.domi.furnace.api.text.Text text) {
            return ServerCore.this.resolveSignText(world, x, y, z, text);
        }
        public void runBlockCommand(org.domi.furnace.server.block.BlockCommandSink sink, String command, java.util.function.IntConsumer done) {
            ServerCore.this.runBlockCommand(world, sink, command, done);
        }
        public boolean withinBorder(int x, int z) { return border.isWithinBounds(x, z); }
        public double[] borderBounds() { return new double[] {border.getMinX(), border.getMinZ(), border.getMaxX(), border.getMaxZ()}; }
        public boolean isRaining() { return clock.isRaining(); }
        public boolean isThundering() { return clock.thunderLevel() > 0.9f; }
        public float rainLevel() { return clock.rainLevel(); }
        public float thunderLevel() { return clock.thunderLevel(); }
        public long dayTime() { return clock.dayTime(); }
        public long gameTime() { return clock.gameTime(); }
        public String dimension() { return "minecraft:overworld"; }
        public org.domi.furnace.server.block.LevelServices.DimensionTravel dimensionTravel() { return world.travelService(); }
        public boolean ultraWarm() { return false; }
        public int maxChainedNeighborUpdates() { return settings.get().maxChainedNeighborUpdates; }
        public void forEachPlayer(java.util.function.Consumer<Player> c) { world.forEachPlayerHere(c); }
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
                                                    Float explosionRadius, java.util.Map<String, Object> blockEntity) {
            return ServerCore.this.blockDrops(lootTable, state, x, y, z, tool, entity, explosionRadius, blockEntity);
        }
        public java.util.List<ItemStack> fishingLoot(org.domi.furnace.server.loot.LootEntity hook, double x, double y, double z, ItemStack tool, float luck) {
            return ServerCore.this.fishingLoot(hook, x, y, z, tool, luck);
        }
        public java.util.List<ItemStack> giftLoot(String lootTable, org.domi.furnace.server.loot.LootEntity self, double x, double y, double z) {
            return ServerCore.this.giftLoot(lootWorld, lootTable, self, x, y, z);
        }
        public java.util.List<ItemStack> lootItems(String lootTable, double[] origin, org.domi.furnace.server.loot.LootEntity thisEntity, float luck,
                long seed, org.domi.furnace.server.block.RandomSource random) {
            return ServerCore.this.lootItems(lootWorld, lootTable, origin, thisEntity, luck, seed, random);
        }

        public java.util.List<ItemStack> chestLoot(String lootTable, long seed, double x, double y, double z, Object player, float luck) {
            return ServerCore.this.chestLoot(lootWorld, lootTable, seed, x, y, z, player, luck);
        }
        public String biomeAt(int x, int y, int z) { return lootWorld.biomeAt(x, y, z); }
        public org.domi.furnace.server.map.ExplorerMaps.Backend explorerMaps() { return explorerMapBackend(); }
        public long seed() { return levelData.seed; }
        public int[] sharedSpawnPos() { return new int[] {levelData.spawnX, levelData.spawnY, levelData.spawnZ}; }
        public boolean spawnMonsters() { return settings.get().spawnMonsters; }
        public boolean spawnAnimals() { return settings.get().spawnAnimals; }
        public boolean spawnNpcs() { return settings.get().spawnNpcs; }
        public int simulationDistance() { return ServerCore.this.simulationDistance(); }
        public int playerIdleTimeoutMinutes() { return settings.get().playerIdleTimeout.get(); }
        public int entityBroadcastRangePercentage() { return settings.get().entityBroadcastRangePercentage; }
        public boolean flightAllowed() { return settings.get().allowFlight; }
        public boolean tickRunsNormally() { return !tickState().frozen(); }
        public Object recipes() { return ServerCore.this.recipes(); }
        public org.domi.furnace.server.loot.LootData loot() {
            var dp = dataPacks;
            return dp == null ? null : dp.loot();
        }
        public void unpackContainerLoot(Object container) {
            if (container instanceof org.domi.furnace.server.block.entity.ContainerBlockEntity c) unpackLoot(c, null);
            else if (container instanceof org.domi.furnace.server.entity.ContainerEntity e) unpackLoot(e, null);
        }
    };

    private volatile org.domi.furnace.server.world.Dimensions dimensions;

    /** 서버가 띄운 차원들(오버월드만 띄웠거나 테스트면 null). */
    public org.domi.furnace.server.world.Dimensions dimensions() { return dimensions; }

    /** 차원 월드를 붙인다: 오버월드가 아닌 월드에는 그 차원의 블록 레벨 서비스를 단다(게임룰·난이도·보더는 서버 공용). */
    public void dimensions(org.domi.furnace.server.world.Dimensions d) {
        this.dimensions = d;
        for (World w : d.all()) attachWorld(w);
    }

    /** 오버월드가 아닌 월드에 그 차원의 레벨 서비스와 공용 /tick 상태를 단다(시작 때 기본 차원, 실행 중 추가 월드). */
    public void attachWorld(World w) {
        if (w == world) return;
        w.levelServices(new DimensionServices(w));
        w.tickState(world.tickState());   // /tick 상태는 차원 공용
    }

    private volatile org.domi.furnace.server.world.WorldManager worldManager;

    /** 다중 월드 관리자(추가 월드 만들기·내리기). 서버 본체 없이(시험)는 null. */
    public org.domi.furnace.server.world.WorldManager worldManager() { return worldManager; }

    /** 관리자를 붙이고 서버 쪽 고리(레벨 서비스·플러그인 이벤트·W2 차원 타입)를 건다. */
    public void worldManager(org.domi.furnace.server.world.WorldManager m) {
        this.worldManager = m;
        m.hooks(new org.domi.furnace.server.world.WorldManager.Hooks() {
            @Override public void attach(World w) { attachWorld(w); }
            @Override public void loaded(World w) { pluginHost.worldLoaded(w); }
            @Override public boolean beforeUnload(World w, boolean save) { return pluginHost.worldUnloading(w, save); }
            @Override public void detached(World w) { pluginHost.worldDetached(w); }
            @Override public org.domi.furnace.api.worldgen.DimensionTypeDefinition pluginDimensionType(String key) {
                return pluginHost.pluginDimensionType(key);
            }
            @Override public org.domi.furnace.api.worldgen.ChunkGenerationHooks generationHooks(String key) {
                return pluginHost.generationHooks(key);
            }
        });
    }

    /** 그 차원의 월드(없으면 null). 오버월드는 world(). */
    public World world(String dimension) {
        if (world.dimension().equals(dimension)) return world;
        var d = dimensions;
        return d == null ? null : d.get(dimension);
    }

    /** 모든 차원 월드(오버월드 먼저). */
    /** 실행 중 바꾼 시뮬레이션 거리(-1 = server.properties 값). */
    private volatile int simulationDistanceOverride = -1;

    /** 지금 시뮬레이션 거리(바닐라 PlayerList.getSimulationDistance). */
    public int simulationDistance() {
        int o = simulationDistanceOverride;
        return o >= 0 ? o : settings.get().simulationDistance;
    }

    /** 지금 시야 거리(오버월드 기준 — 모든 차원이 같은 값을 쓴다). */
    public int viewDistance() { return worlds().get(0).viewDistance(); }

    /**
     * 바닐라 PlayerList.setViewDistance: 모든 플레이어에게 set_chunk_cache_radius 를 보내고 모든 차원의 시야 거리를 바꾼다.
     * 플레이어별 전송 거리는 min(새 값, 클라이언트 렌더 거리)로 다시 잡는다(클라이언트가 설정을 바꿨을 때와 같은 길 — 다음 틱부터 청크를 더 보내거나 내린다).
     * 값은 2~32 로 자른다(ChunkMap.setServerViewDistance). 어느 스레드에서 불러도 된다(호출끼리는 직렬화).
     */
    public synchronized void setViewDistance(int vd) {   // 동시 호출이 섞이면 월드 값과 클라이언트에 보낸 값이 어긋난다 — 한 번에 하나
        int v = Math.clamp(vd, org.domi.furnace.server.config.Config.MIN_VIEW_DISTANCE, org.domi.furnace.server.config.Config.MAX_VIEW_DISTANCE);
        for (World w : worlds()) w.viewDistance(v);
        var pkt = new org.domi.furnace.server.net.protocol.play.SetChunkCacheRadius(v);
        for (Player p : players()) {
            p.viewDistance(Math.min(v, p.clientViewDistance()));
            p.send(pkt);
        }
    }

    /** 바닐라 PlayerList.setSimulationDistance: 모든 플레이어에게 set_simulation_distance 를 보낸다. region 은 다음 틱부터 새 값을 쓴다. 2~32 로 자른다. */
    public synchronized void setSimulationDistance(int sd) {
        int v = Math.clamp(sd, org.domi.furnace.server.config.Config.MIN_VIEW_DISTANCE, org.domi.furnace.server.config.Config.MAX_VIEW_DISTANCE);
        simulationDistanceOverride = v;
        var pkt = new org.domi.furnace.server.net.protocol.play.SetSimulationDistance(v);
        for (Player p : players()) p.send(pkt);
    }

    public List<World> worlds() {
        var d = dimensions;
        return d == null ? List.of(world) : d.all();
    }

    /**
     * 오버월드가 아닌 차원의 블록 레벨 서비스: 차원 타입(높이·하늘빛·초고온), 그 차원의 접속자, 날씨 없음(하늘빛이 없으면 비 세기가
     * 늘 0 — 바닐라 advanceWeatherCycle), 그 차원의 바이옴·전리품 월드. 나머지(게임룰·난이도·보더·시간·시드·스폰)는 오버월드와 같다
     * (바닐라 DerivedLevelData).
     */
    private final class DimensionServices extends org.domi.furnace.server.block.LevelServices.Delegating {
        private final World w;
        private final org.domi.furnace.server.loot.LootWorld lw;

        DimensionServices(World w) {
            super(levelServices);
            this.w = w;
            this.lw = lootWorldFor(w);
        }

        private boolean sky() { return w.dimensionType().hasSkyLight(); }

        @Override public String dimension() { return w.dimension(); }
        @Override public org.domi.furnace.api.text.Text resolveSignText(int x, int y, int z, org.domi.furnace.api.text.Text text) {
            return ServerCore.this.resolveSignText(w, x, y, z, text);
        }
        @Override public void runBlockCommand(org.domi.furnace.server.block.BlockCommandSink sink, String command, java.util.function.IntConsumer done) {
            ServerCore.this.runBlockCommand(w, sink, command, done);
        }
        @Override public boolean ultraWarm() { return w.dimensionType().ultraWarm(); }
        @Override public boolean hasSkyLight() { return sky(); }
        @Override public org.domi.furnace.server.world.DimensionType dimensionType() { return w.dimensionType(); }
        @Override public boolean isRaining() { return sky() && super.isRaining(); }
        @Override public boolean isThundering() { return sky() && super.isThundering(); }
        @Override public float rainLevel() { return sky() ? super.rainLevel() : 0f; }
        @Override public float thunderLevel() { return sky() ? super.thunderLevel() : 0f; }
        @Override public void forEachPlayer(java.util.function.Consumer<Player> c) { w.forEachPlayerHere(c); }
        @Override public org.domi.furnace.server.block.LevelServices.DimensionTravel dimensionTravel() { return w.travelService(); }
        @Override public String biomeAt(int x, int y, int z) { return lw.biomeAt(x, y, z); }
        @Override public boolean biomeIs(int x, int y, int z, String tag) {
            String b = lw.biomeAt(x, y, z);
            var dp = dataPacks;
            return b != null && dp != null && dp.loot() != null && dp.loot().registries().tag("minecraft:worldgen/biome", tag).contains(b);
        }
        @Override public java.util.List<ItemStack> blockDrops(String lootTable, int state, double x, double y, double z, ItemStack tool, Object entity,
                                                              Float explosionRadius, java.util.Map<String, Object> blockEntity) {
            return ServerCore.this.blockDrops(lw, lootTable, state, x, y, z, tool, entity, explosionRadius, blockEntity);
        }
        @Override public java.util.List<ItemStack> entityDrops(String lootTable, org.domi.furnace.server.loot.LootEntity self, double x, double y, double z,
                String damageType, Player attacker, Player lastPlayer, long seed) {
            return ServerCore.this.entityDrops(lw, lootTable, self, x, y, z, damageType, attacker, lastPlayer, seed);
        }
        @Override public java.util.List<ItemStack> fishingLoot(org.domi.furnace.server.loot.LootEntity hook, double x, double y, double z, ItemStack tool,
                                                               float luck) {
            return ServerCore.this.fishingLoot(lw, hook, x, y, z, tool, luck);
        }
        @Override public java.util.List<ItemStack> giftLoot(String lootTable, org.domi.furnace.server.loot.LootEntity self, double x, double y, double z) {
            return ServerCore.this.giftLoot(lw, lootTable, self, x, y, z);
        }
        @Override public java.util.List<ItemStack> lootItems(String lootTable, double[] origin, org.domi.furnace.server.loot.LootEntity thisEntity,
                float luck, long seed, org.domi.furnace.server.block.RandomSource random) {
            return ServerCore.this.lootItems(lw, lootTable, origin, thisEntity, luck, seed, random);
        }

        @Override public java.util.List<ItemStack> chestLoot(String lootTable, long seed, double x, double y, double z, Object player, float luck) {
            return ServerCore.this.chestLoot(lw, lootTable, seed, x, y, z, player, luck);
        }
    }

    /**
     * SignBlockEntity.loadLine(레벨이 서버 레벨일 때): 표지판 소스(플레이어 없음·이름 "Sign"·권한 2·칸 가운데·그 월드)로 updateForEntity(…, null, 0).
     * 명령 오류(CommandSyntaxException)면 원래 줄을 그대로 쓴다. 표지판의 region 스레드에서 불린다(점수판·접속자 목록은 스레드 안전).
     */
    org.domi.furnace.api.text.Text resolveSignText(World w, int x, int y, int z, org.domi.furnace.api.text.Text text) {
        try {
            return org.domi.furnace.server.command.ComponentUtils.updateForEntity(
                    org.domi.furnace.server.command.CommandSource.sign(this, w, x, y, z), text, null, 0);
        } catch (org.domi.furnace.server.command.brigadier.CommandSyntaxException e) {
            return text;
        }
    }

    /** 바닐라 BlockBehaviour.getDrops: 표가 비었으면 빈 목록, 아니면 random_sequence 난수로 평가하고 최대 스택으로 나눈다. */
    java.util.List<ItemStack> blockDrops(String lootTable, int state, double x, double y, double z, ItemStack tool, Object entity, Float explosionRadius,
                                         java.util.Map<String, Object> blockEntity) {
        return blockDrops(lootWorld, lootTable, state, x, y, z, tool, entity, explosionRadius, blockEntity);
    }

    java.util.List<ItemStack> blockDrops(org.domi.furnace.server.loot.LootWorld lootWorld, String lootTable, int state, double x, double y, double z,
                                         ItemStack tool, Object entity, Float explosionRadius, java.util.Map<String, Object> blockEntity) {
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
        if (blockEntity != null) params.with(org.domi.furnace.server.loot.LootParams.BLOCK_ENTITY, blockEntity);   // collectComponents(region 스레드에서 모은 것)
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
        return entityDrops(lootWorld, lootTable, self, x, y, z, damageType, attacker, lastPlayer, seed);
    }

    java.util.List<ItemStack> entityDrops(org.domi.furnace.server.loot.LootWorld lootWorld, String lootTable, org.domi.furnace.server.loot.LootEntity self,
            double x, double y, double z, String damageType, Player attacker, Player lastPlayer, long seed) {
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
                    public boolean is(String tag) { return damageType != null && org.domi.furnace.server.entity.damage.DamageType.keyHasTag(damageType, tag); }   // damage_type 태그(is_fire·is_explosion …)
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
        return fishingLoot(lootWorld, hook, x, y, z, tool, luck);
    }

    java.util.List<ItemStack> fishingLoot(org.domi.furnace.server.loot.LootWorld lootWorld, org.domi.furnace.server.loot.LootEntity hook,
            double x, double y, double z, ItemStack tool, float luck) {
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

    /** 바닐라 BrushableBlockEntity.unpackLootTable: 표를 CHEST 문맥(origin, this_entity, luck)과 시드로 평가(최대 스택으로 나눈다). */
    java.util.List<ItemStack> chestLoot(org.domi.furnace.server.loot.LootWorld lootWorld, String lootTable, long seed, double x, double y, double z,
            Object player, float luck) {
        var dp = dataPacks;
        if (dp == null || dp.loot() == null) return java.util.List.of();
        var loot = dp.loot();
        var table = loot.table(lootTable);
        var params = new org.domi.furnace.server.loot.LootParams()
                .with(org.domi.furnace.server.loot.LootParams.ORIGIN, new double[]{x, y, z}).luck(luck);
        if (player instanceof Player p) params.with(org.domi.furnace.server.loot.LootParams.THIS_ENTITY, lootEntity(p));
        java.util.List<ItemStack> out = new java.util.ArrayList<>();
        for (var st : table.getRandomItems(table.context(loot, lootWorld, params, seed))) {
            ItemStack is = dp.network().toItemStack(st);
            out.add(is == null ? ItemStack.EMPTY : is);
        }
        return out;
    }

    /** 바닐라 GiveGiftToHero.getItemToThrow: 표를 GIFT 문맥(origin, this_entity)으로 평가. */
    java.util.List<ItemStack> giftLoot(org.domi.furnace.server.loot.LootWorld lootWorld, String lootTable, org.domi.furnace.server.loot.LootEntity self,
            double x, double y, double z) {
        var dp = dataPacks;
        if (dp == null || dp.loot() == null) return java.util.List.of();
        var loot = dp.loot();
        var table = loot.table(lootTable);
        var params = new org.domi.furnace.server.loot.LootParams()
                .with(org.domi.furnace.server.loot.LootParams.ORIGIN, new double[]{x, y, z})
                .with(org.domi.furnace.server.loot.LootParams.THIS_ENTITY, self);
        java.util.List<ItemStack> out = new java.util.ArrayList<>();
        for (var st : table.getRandomItems(table.context(loot, lootWorld, params, 0L))) {
            ItemStack is = dp.network().toItemStack(st);
            if (is != null && !is.isEmpty()) out.add(is);
        }
        return out;
    }

    /**
     * 일반 전리품 평가: origin·this_entity·luck. 난수는 seed(0 아님) → 넘겨 준 레벨 난수 → 표의 random_sequence/월드 난수 순.
     * 바닐라 LootTable.getRandomItems(params[, seed | random]) — 최대 스택으로 나눈다.
     */
    java.util.List<ItemStack> lootItems(org.domi.furnace.server.loot.LootWorld lootWorld, String lootTable, double[] origin,
            org.domi.furnace.server.loot.LootEntity thisEntity, float luck, long seed, org.domi.furnace.server.block.RandomSource random) {
        var dp = dataPacks;
        if (dp == null || dp.loot() == null) return java.util.List.of();
        var loot = dp.loot();
        var table = loot.table(lootTable);
        var params = new org.domi.furnace.server.loot.LootParams().luck(luck);
        if (origin != null) params.with(org.domi.furnace.server.loot.LootParams.ORIGIN, origin);
        if (thisEntity != null) params.with(org.domi.furnace.server.loot.LootParams.THIS_ENTITY, thisEntity);
        org.domi.furnace.server.loot.LootContext ctx = seed == 0L && random != null
                ? new org.domi.furnace.server.loot.LootContext(loot, lootWorld, params, new org.domi.furnace.server.loot.LegacyNoiseRandom(random))
                : table.context(loot, lootWorld, params, seed);
        java.util.List<ItemStack> out = new java.util.ArrayList<>();
        for (var st : table.getRandomItems(ctx)) {
            ItemStack is = dp.network().toItemStack(st);
            if (is != null && !is.isEmpty()) out.add(is);
        }
        return out;
    }

    /** 블록 상태 → 전리품 블록 상태(블록 이름과 전체 속성). */
    public static org.domi.furnace.server.loot.LootBlockState lootBlockState(int state) {
        var blocks = org.domi.furnace.server.block.Blocks.get();
        java.util.Map<String, String> props = new java.util.LinkedHashMap<>();
        for (var p : blocks.propsOf(blocks.blockOf(state))) props.put(p.name(), p.valueName(state));
        return new org.domi.furnace.server.loot.LootBlockState(blocks.name(blocks.blockOf(state)), props);
    }

    /** 서버 아이템 → 전리품 스택(공개판 — 발전 과제 사건용). */
    public static org.domi.furnace.server.loot.LootStack lootStackOf(ItemStack s) { return lootStack(s); }

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

    public void autosaveEnabled(boolean v) { for (World w : worlds()) w.autosaveEnabled(v); }

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

    /**
     * chat.unsigned-as-system: 서명 없는 시스템 메시지로 보낸다 — 클라이언트가 신고 대상으로 잡지 않는다. 채팅을 보는 설정인 플레이어에게만.
     */
    public void broadcastChatAsSystem(org.domi.furnace.server.chat.PlayerChatMessage message,
                                      org.domi.furnace.server.chat.ChatType.Bound bound) {
        logChatMessage(message.decoratedContent(), bound, null);
        Text line = bound.decorate(message.decoratedContent());
        for (Player p : players()) if (p.chat().acceptsChatMessages()) p.sendSystemMessage(line, false);
    }

    /**
     * 플러그인 렌더러(AsyncChatEvent.renderer)로 받는 사람마다 줄을 만들어 서명 없는 시스템 메시지로 보낸다 — 채팅을 보는 설정인 플레이어에게만.
     * 콘솔 로그에는 콘솔을 받는 사람으로 한 번 렌더한다. 렌더러가 예외를 던지면 그 사람에게는 바닐라 서식으로 보낸다.
     */
    public void broadcastRenderedChat(Player sender, Text senderName, Text content, org.domi.furnace.api.event.player.ChatRenderer renderer) {
        var host = plugins();
        var source = host.player(sender);
        Text consoleLine;
        try {
            consoleLine = renderer.render(source, senderName, content, host.console());
        } catch (RuntimeException e) {
            log.warn("채팅 렌더러 오류(콘솔)", e);
            consoleLine = org.domi.furnace.api.event.player.ChatRenderer.vanilla().render(source, senderName, content, host.console());
        }
        log.info("{}", consoleLine.getString());
        for (var l : consoleListeners) l.accept(consoleLine);
        for (Player p : players()) {
            if (!p.chat().acceptsChatMessages()) continue;
            Text line;
            try {
                line = renderer.render(source, senderName, content, host.player(p));
            } catch (RuntimeException e) {
                log.warn("채팅 렌더러 오류({})", p.name(), e);
                line = org.domi.furnace.api.event.player.ChatRenderer.vanilla().render(source, senderName, content, host.player(p));
            }
            p.sendSystemMessage(line, false);
        }
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
