package org.bukkit.craftbukkit;

import org.domi.furnace.compat.i18n.CompatLang;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.ServicesManager;
import org.bukkit.scheduler.BukkitScheduler;
import net.minecraft.server.dedicated.DedicatedServer;
import org.domi.furnace.api.Furnace;
import org.domi.furnace.compat.paper.Compat;
import org.domi.furnace.compat.paper.CompatBlockData;
import org.domi.furnace.compat.paper.CompatConsole;
import org.domi.furnace.compat.paper.CompatScheduler;
import org.domi.furnace.compat.paper.UnsupportedFeature;

/**
 * Furnace 서버를 Bukkit Server 로 보여 준다. 상태는 없고 부를 때마다 지금 떠 있는 Furnace 서버에 물어본다.
 * 클래스 이름이 Paper 1.20.5 이후와 같은 접미사 없는 {@code org.bukkit.craftbukkit.CraftServer} 라서, 서버 클래스·패키지 이름으로
 * 구현을 고르는 라이브러리가 Paper 로 본다. {@link #getServer()} 가 NMS 서버 shim 을 준다(H6).
 */
public final class CraftServer implements Server {

    public static final CraftServer INSTANCE = new CraftServer();

    private static final Logger LOG = Logger.getLogger("Minecraft");

    /**
     * CraftBukkit 의 월드 표(이름 소문자 → 월드, N2b-6). 서버가 들고 있는 월드 목록을 그대로 비추는 읽기 보기라 리플렉션으로 읽는 플러그인에게도
     * Paper 처럼 지금 월드가 보인다. 월드 만들기는 {@link #createWorld}, 내리기는 Furnace API {@code unloadWorld} 로 하므로 표에 직접 넣거나 지우는 것은 길이 없다
     * (이미 없는 이름을 지우는 것은 아무 일도 없다 — 월드를 내린 뒤 표를 정리하는 코드가 그렇게 부른다).
     */
    private final java.util.Map<String, org.bukkit.World> worlds = new WorldsView();

    private final class WorldsView extends java.util.AbstractMap<String, org.bukkit.World> {
        /** 지금 월드(서버 순서)를 이름 소문자로 묶은 새 표. */
        private java.util.Map<String, org.bukkit.World> snapshot() {
            java.util.Map<String, org.bukkit.World> out = new java.util.LinkedHashMap<>();
            for (World w : getWorlds()) out.put(w.getName().toLowerCase(Locale.ROOT), w);
            return out;
        }

        @Override
        public java.util.Set<Entry<String, org.bukkit.World>> entrySet() { return java.util.Collections.unmodifiableMap(snapshot()).entrySet(); }

        @Override
        public org.bukkit.World get(Object key) { return key instanceof String s ? snapshot().get(s) : null; }

        @Override
        public boolean containsKey(Object key) { return get(key) != null; }

        @Override
        public org.bukkit.World put(String key, org.bukkit.World value) {
            throw new org.domi.furnace.compat.nms.UnsupportedNmsCall("CraftServer#worlds.put", "worlds are created with Server#createWorld, not by putting them into the table");
        }

        @Override
        public org.bukkit.World remove(Object key) {
            if (get(key) == null) return null;
            throw new org.domi.furnace.compat.nms.UnsupportedNmsCall("CraftServer#worlds.remove", "worlds are unloaded with the Furnace API (Furnace#unloadWorld), not by removing them from the table");
        }

        @Override
        public void clear() {
            if (!snapshot().isEmpty()) throw new org.domi.furnace.compat.nms.UnsupportedNmsCall("CraftServer#worlds.clear", "worlds are unloaded with the Furnace API (Furnace#unloadWorld), not by clearing the table");
        }
    }

    private CraftServer() {}

    /** NMS 서버 shim(Paper·CraftBukkit 의 {@code getServer()} 처럼 DedicatedServer). 늘 같은 객체다. */
    public DedicatedServer getServer() { return DedicatedServer.INSTANCE; }

    private static Furnace f() { return Compat.furnace(); }

    @Override
    public org.bukkit.inventory.ItemFactory getItemFactory() { return org.bukkit.craftbukkit.inventory.CraftItemFactory.instance(); }

    @Override
    public org.bukkit.block.data.BlockData createBlockData(org.bukkit.Material material) { return CompatBlockData.create(material); }

    @Override
    public org.bukkit.block.data.BlockData createBlockData(String data) { return CompatBlockData.create(data); }

    @Override
    public org.bukkit.profile.PlayerProfile createPlayerProfile(java.util.UUID uniqueId) { return new org.bukkit.craftbukkit.profile.CraftPlayerProfile(uniqueId, null); }

    @Override
    public org.bukkit.profile.PlayerProfile createPlayerProfile(String name) { return new org.bukkit.craftbukkit.profile.CraftPlayerProfile(null, name); }

    @Override
    public org.bukkit.profile.PlayerProfile createPlayerProfile(java.util.UUID uniqueId, String name) { return new org.bukkit.craftbukkit.profile.CraftPlayerProfile(uniqueId, name); }

    @Override
    public com.destroystokyo.paper.profile.PlayerProfile createProfile(java.util.UUID uuid) { return createProfile(uuid, null); }

    @Override
    public com.destroystokyo.paper.profile.PlayerProfile createProfile(String name) { return createProfile(null, name); }

    @Override
    public com.destroystokyo.paper.profile.PlayerProfile createProfile(java.util.UUID uuid, String name) {
        Player player = uuid != null ? getPlayer(uuid) : (name != null ? getPlayerExact(name) : null);
        if (player != null) return new com.destroystokyo.paper.profile.CraftPlayerProfile((org.bukkit.craftbukkit.entity.CraftPlayer) player);
        return new com.destroystokyo.paper.profile.CraftPlayerProfile(uuid, name);
    }

    @Override
    public com.destroystokyo.paper.profile.PlayerProfile createProfileExact(java.util.UUID uuid, String name) {
        Player player = uuid != null ? getPlayer(uuid) : (name != null ? getPlayerExact(name) : null);
        if (player == null) return new com.destroystokyo.paper.profile.CraftPlayerProfile(uuid, name);
        if (java.util.Objects.equals(uuid, player.getUniqueId()) && java.util.Objects.equals(name, player.getName())) {
            return new com.destroystokyo.paper.profile.CraftPlayerProfile((org.bukkit.craftbukkit.entity.CraftPlayer) player);
        }
        com.destroystokyo.paper.profile.PlayerProfile profile = new com.destroystokyo.paper.profile.CraftPlayerProfile(uuid, name);
        profile.setProperties(new com.destroystokyo.paper.profile.CraftPlayerProfile((org.bukkit.craftbukkit.entity.CraftPlayer) player).getProperties());
        return profile;
    }

    @Override
    public org.bukkit.block.data.BlockData createBlockData(org.bukkit.Material material, String data) { return CompatBlockData.create(material, data); }

    @Override
    public org.bukkit.inventory.Inventory createInventory(org.bukkit.inventory.InventoryHolder owner, int size) {
        return org.domi.furnace.compat.paper.CompatMenuInventory.chest(owner, size, null);
    }

    @Override
    public org.bukkit.inventory.Inventory createInventory(org.bukkit.inventory.InventoryHolder owner, int size, String title) {
        return org.domi.furnace.compat.paper.CompatMenuInventory.chest(owner, size, title);
    }

    @Override
    public org.bukkit.inventory.Inventory createInventory(org.bukkit.inventory.InventoryHolder owner, org.bukkit.event.inventory.InventoryType type) {
        return org.domi.furnace.compat.paper.CompatMenuInventory.ofType(owner, type, null);
    }

    @Override
    public org.bukkit.inventory.Inventory createInventory(org.bukkit.inventory.InventoryHolder owner, org.bukkit.event.inventory.InventoryType type, String title) {
        return org.domi.furnace.compat.paper.CompatMenuInventory.ofType(owner, type, title);
    }

    @Override
    public org.bukkit.boss.BossBar createBossBar(String title, org.bukkit.boss.BarColor color, org.bukkit.boss.BarStyle style, org.bukkit.boss.BarFlag... flags) {
        return new org.bukkit.craftbukkit.boss.CraftBossBar(title, color, style, flags);
    }

    @Override
    public String getName() { return "Furnace"; }

    @Override
    public String getVersion() { return "git-Furnace-" + f().version() + " (MC: " + f().minecraftVersion() + ")"; }

    @Override
    public String getBukkitVersion() { return f().minecraftVersion() + "-R0.1-SNAPSHOT"; }

    @Override
    public String getMinecraftVersion() { return f().minecraftVersion(); }

    @Override
    @Deprecated
    public org.bukkit.UnsafeValues getUnsafe() { return org.bukkit.craftbukkit.util.CraftMagicNumbers.INSTANCE; }

    @Override
    public Logger getLogger() { return LOG; }

    @Override
    public PluginManager getPluginManager() { return Compat.pluginManager(); }

    /**
     * Paper 의 playerView 처럼 고칠 수 없는 살아 있는 보기(A15-08): 받아 둔 컬렉션도 지금 접속자를 보이고, add·remove·clear 는
     * UnsupportedOperationException. 순회는 그때의 접속자 목록을 하나씩 어댑터로 바꾼다(목록을 미리 복사하지 않는다).
     */
    /** Paper: Collections.unmodifiableList(Lists.transform(...)) — 살아 있는 고칠 수 없는 List. 고치는 메서드는 비어 있어도 언제나 던진다. */
    private final List<Player> onlineView = new OnlineView();

    private final class OnlineView extends java.util.AbstractList<Player> implements java.util.RandomAccess {
        /** 감싼 배열 캐시: 서버가 준 접속자 목록(동일성)과 그것을 Bukkit 객체로 감싼 배열. 서버가 같은 목록 객체를 주는 동안 다시 만들지 않는다. */
        private record Wrapped(Collection<?> source, Player[] players) {}

        private volatile Wrapped wrapped;

        /**
         * 지금 접속자(접속 순)를 감싼 배열. 읽기 전용 — 고치거나 밖으로 내보내지 않는다.
         * 접속자 집합이 그대로이면 서버가 같은 목록 객체를 주므로 바로 돌려준다(size·get 이 O(1)).
         */
        private Player[] array() {
            Collection<? extends org.domi.furnace.api.entity.Player> all = f().onlinePlayers();
            Wrapped w = wrapped;
            if (w != null && w.source == all) return w.players;
            Player[] out = new Player[all.size()];
            int n = 0;
            for (var p : all) {
                if (n == out.length) out = java.util.Arrays.copyOf(out, n + 1);   // 세는 사이 늘었다(서버가 같은 목록을 안 줄 때만)
                out[n++] = Compat.player(p);
            }
            if (n != out.length) out = java.util.Arrays.copyOf(out, n);
            wrapped = new Wrapped(all, out);
            return out;
        }

        @Override
        public Player get(int index) {
            Player[] a = array();
            java.util.Objects.checkIndex(index, a.length);
            return a[index];
        }

        /** 순회는 시작 때의 배열 하나를 끝까지 본다(도중에 접속·퇴장이 있어도 건너뛰거나 겹치지 않는다). */
        @Override
        public java.util.Iterator<Player> iterator() {
            Player[] a = array();
            return new java.util.Iterator<>() {
                private int i;

                @Override
                public boolean hasNext() { return i < a.length; }

                @Override
                public Player next() {
                    if (i >= a.length) throw new java.util.NoSuchElementException();
                    return a[i++];
                }
            };
        }

        @Override
        public java.util.Spliterator<Player> spliterator() {
            return java.util.Spliterators.spliterator(array(), java.util.Spliterator.ORDERED | java.util.Spliterator.NONNULL);
        }

        @Override
        public Object[] toArray() { return array().clone(); }

        @Override
        public int size() { return array().length; }

        private static UnsupportedOperationException ro() { return new UnsupportedOperationException(); }

        @Override public boolean add(Player p) { throw ro(); }
        @Override public void add(int i, Player p) { throw ro(); }
        @Override public boolean addAll(Collection<? extends Player> c) { throw ro(); }
        @Override public boolean addAll(int i, Collection<? extends Player> c) { throw ro(); }
        @Override public Player set(int i, Player p) { throw ro(); }
        @Override public Player remove(int i) { throw ro(); }
        @Override public boolean remove(Object o) { throw ro(); }
        @Override public boolean removeAll(Collection<?> c) { throw ro(); }
        @Override public boolean retainAll(Collection<?> c) { throw ro(); }
        @Override public boolean removeIf(java.util.function.Predicate<? super Player> f) { throw ro(); }
        @Override public void replaceAll(java.util.function.UnaryOperator<Player> op) { throw ro(); }
        @Override public void sort(java.util.Comparator<? super Player> c) { throw ro(); }
        @Override public void clear() { throw ro(); }
    }

    @Override
    public Collection<? extends Player> getOnlinePlayers() { return onlineView; }

    @Override
    public int getMaxPlayers() { return f().maxPlayers(); }

    @Override
    public Player getPlayerExact(String name) {
        if (name == null) return null;
        return Compat.player(f().player(name));
    }

    @Override
    public Player getPlayer(String name) {
        if (name == null) return null;
        Player exact = getPlayerExact(name);
        if (exact != null) return exact;
        String lower = name.toLowerCase(Locale.ROOT);
        Player found = null;
        int delta = Integer.MAX_VALUE;
        for (var p : f().onlinePlayers()) {
            String n = p.name();
            if (n.toLowerCase(Locale.ROOT).startsWith(lower)) {
                int d = n.length() - lower.length();
                if (d < delta) {
                    found = Compat.player(p);
                    delta = d;
                }
                if (d == 0) break;
            }
        }
        return found;
    }

    @Override
    public Player getPlayer(UUID id) { return id == null ? null : Compat.player(f().player(id)); }

    /** 월드 순서대로 찾는다(찾는 규칙은 Compat.entityByUuid — 틱 스레드에서는 그 엔티티를 가진 region 에서만 보인다). */
    @Override
    public org.bukkit.entity.Entity getEntity(UUID uuid) {
        if (uuid == null) throw new IllegalArgumentException("UUID id cannot be null");
        for (World w : getWorlds()) {
            var e = w instanceof org.bukkit.craftbukkit.CraftWorld cw ? Compat.entityByUuid(cw.furnaceHandle(), uuid) : null;
            if (e != null) return e;
        }
        return null;
    }

    @Override
    public org.bukkit.OfflinePlayer getOfflinePlayer(UUID id) {
        if (id == null) throw new IllegalArgumentException("UUID id cannot be null");
        Player online = getPlayer(id);
        return online != null ? online : new CraftOfflinePlayer(id, null);
    }

    @Override
    public org.bukkit.OfflinePlayer getOfflinePlayer(String name) {
        if (name == null) throw new IllegalArgumentException("name cannot be null");
        if (name.isBlank()) throw new IllegalArgumentException("name cannot be empty");
        Player online = getPlayerExact(name);
        if (online != null) return online;
        if (getOnlineMode()) {
            var known = f().offlinePlayerCached(name);
            if (known != null) return getOfflinePlayer(known.uniqueId(), known.name());
        }
        // 바닐라 UUIDUtil.createOfflinePlayerUUID
        UUID offline = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return getOfflinePlayer(offline, name);
    }

    private org.bukkit.OfflinePlayer getOfflinePlayer(UUID id, String name) {
        Player online = getPlayer(id);
        return online != null ? online : new CraftOfflinePlayer(id, name);
    }

    @Override
    public java.util.Set<org.bukkit.OfflinePlayer> getOperators() {
        java.util.Set<org.bukkit.OfflinePlayer> out = new java.util.HashSet<>();
        for (var op : f().access().operators()) out.add(getOfflinePlayer(op.uniqueId(), op.name()));
        return out;
    }

    @Override
    public boolean getOnlineMode() { return f().onlineMode(); }

    @Override
    public List<World> getWorlds() {
        List<World> out = new ArrayList<>();
        for (var w : f().worlds()) if (Compat.worldVisible(w.dimension())) out.add(Compat.world(w));   // load: STARTUP 단계엔 아직 안 보인다
        return out;
    }

    @Override
    public World getWorld(String name) { return name == null ? null : Compat.worldNamed(name); }

    /**
     * Paper CraftServer#createWorld 를 Furnace {@code createWorldNow} 위에: 같은 이름이 있으면 그 월드, 환경은 NORMAL·NETHER·THE_END,
     * 생성기 종류(type)는 오버월드에만 쓰인다(Paper 도 네더·엔드는 바닐라 그대로). FLAT 의 generatorSettings 는 바닐라 평지 JSON 을 그대로 넘긴다
     * (Paper DedicatedServerProperties.WorldDimensionData — 비면 기본 프리셋). 바이옴 공급자(T-210)는 CraftBukkit CustomWorldChunkManager 처럼
     * 생성기의 바이옴 원천을 바꾸고, Bukkit 생성기(T-217)는 CustomChunkGenerator 처럼 서버 생성 훅 객체로 단다
     * Paper 처럼 생성기·공급자가 비면 이름별 설정(bukkit.yml worlds.<이름>.generator·biome-provider 자리 — furnace.properties
     * world-generator.name.<이름>·world-biome-provider.name.<이름>, T-222)을 {@link #getGenerator}·{@link #getBiomeProvider} 로 읽는다. 하드코어 깃발은 서버 값만 쓴다(다르면 경고 한 번).
     */
    @Override
    public World createWorld(org.bukkit.WorldCreator creator) {
        if (creator == null) throw new IllegalArgumentException("WorldCreator cannot be null");
        if (Compat.startupWorldsPending()) throw new IllegalStateException("Cannot create additional worlds on STARTUP");
        String name = creator.name();
        // Paper(Expand-world-key-API): 이름(대소문자 무시)이나 키가 이미 있으면, 둘이 같은 월드일 때만 그 월드
        World existing = getWorld(name);
        World byKey = Compat.world(f().world(creator.key().toString()));   // 감춘 단계(STARTUP)는 위에서 이미 막혀 보이기 검사가 필요 없다
        if (existing != null || byKey != null) {
            if (existing == byKey) return existing;
            throw new IllegalArgumentException("Cannot create a world with key " + creator.key() + " and name " + name
                    + " one (or both) already match a world that exists");
        }
        // Paper: 같은 이름·키 검사 뒤, 환경 해석(Illegal dimension)과 level.dat 읽기 앞에서 비어 있는 쪽만 설정으로 채운다(둘은 따로 — 생성기를 줘도 공급자는 설정에서)
        org.bukkit.generator.ChunkGenerator generator = creator.generator();
        org.bukkit.generator.BiomeProvider provider = creator.biomeProvider();
        if (generator == null) generator = getGenerator(name);
        if (provider == null) provider = getBiomeProvider(name);
        var env = switch (creator.environment()) {
            case NORMAL -> org.domi.furnace.api.world.WorldEnvironment.NORMAL;
            case NETHER -> org.domi.furnace.api.world.WorldEnvironment.NETHER;
            case THE_END -> org.domi.furnace.api.world.WorldEnvironment.THE_END;
            default -> throw new IllegalArgumentException("Illegal dimension (" + creator.environment() + ")");
        };
        // Paper: 폴더에 level.dat 이 있으면 저장된 생성기·구조물·시드를 쓴다(설명서의 type·generatorSettings·generateStructures·seed 는 새 월드만)
        var stored = f().storedWorldCreator(name);
        // 공급자·생성기의 WorldInfo: 서버가 생성기를 만들기 직전에 채우고(prepare — 시드는 level.dat 의 것), 월드가 생기면 그 CraftWorld 를 따른다
        var info = provider == null && generator == null ? null : new org.domi.furnace.compat.paper.BukkitBiomeProviders.LateWorldInfo(name,
                creator.environment(), stored != null && stored.seed().isPresent() ? stored.seed().getAsLong() : creator.seed(),
                env == org.domi.furnace.api.world.WorldEnvironment.NORMAL ? -64 : 0, env == org.domi.furnace.api.world.WorldEnvironment.NORMAL ? 320 : 256);
        if (provider == null && generator != null) provider = generator.getDefaultBiomeProvider(info);   // CraftServer: 생성기의 기본 공급자
        var biomes = provider == null ? null : org.domi.furnace.compat.paper.BukkitBiomeProviders.adapt(provider, info);
        var hooks = generator == null ? null : org.domi.furnace.compat.paper.BukkitChunkGenerators.adapt(generator, info);   // CustomChunkGenerator
        if (stored != null) {
            var b = stored.toBuilder().key(creator.key().toString()).environment(env).biomeProvider(biomes).generationHooksObject(hooks);
            if (stored.environment() != env) {
                b.generator(org.domi.furnace.api.world.WorldGenerator.DEFAULT).dimensionType(null).flatLayers(java.util.List.of());   // 그 환경의 바닐라 줄기
            }
            if (biomes == null && stored.needsBiomeProvider() && WARNED_CREATE.add(name + "|provider")) {
                // Paper 처럼 공급자 없이 연다(새 청크는 바닐라 바이옴) — 경계가 생길 수 있다는 것만 알린다
                LOG.warning(CompatLang.t("compat.create-world-provider-dropped", name, stored.biomeProviderName()));
            }
            if (hooks == null && stored.needsGenerationHooksObject() && WARNED_CREATE.add(name + "|generator")) {
                // Paper 처럼 생성기 없이 연다(새 청크는 바닐라 생성기) — 경계가 생길 수 있다는 것만 알린다
                LOG.warning(CompatLang.t("compat.create-world-generator-dropped", name, stored.generationHooksObjectName()));
            }
            warnHardcore(creator, name);
            return bound(Compat.world(f().createWorldNow(b.build())), info);
        }
        var b = org.domi.furnace.api.world.WorldCreator.named(name).key(creator.key().toString()).environment(env)
                .seed(creator.seed()).generateStructures(creator.generateStructures()).biomeProvider(biomes).generationHooksObject(hooks);
        if (env == org.domi.furnace.api.world.WorldEnvironment.NORMAL) {
            switch (creator.type()) {
                case FLAT -> flatSettings(b, name, creator.generatorSettings());
                case LARGE_BIOMES -> b.generator(org.domi.furnace.api.world.WorldGenerator.LARGE_BIOMES);
                case AMPLIFIED -> b.generator(org.domi.furnace.api.world.WorldGenerator.AMPLIFIED);
                default -> { }
            }
        }
        warnHardcore(creator, name);
        return bound(Compat.world(f().createWorldNow(b.build())), info);
    }

    /**
     * Paper CraftServer#getGenerator(String) — 설정에 그 이름의 생성기 "플러그인[:id]"가 있으면 그 플러그인의 getDefaultWorldGenerator(이름, id).
     * 설정이 없거나 비면 null, 플러그인이 없거나 켜지지 않았거나 null 을 주거나 던지면 Paper 문구를 남기고 null(그 월드는 바닐라 생성기).
     */
    public org.bukkit.generator.ChunkGenerator getGenerator(String world) {
        String spec = f().configuredWorldGenerator(world).generator();
        return spec.isEmpty() ? null : org.domi.furnace.compat.paper.BukkitDefaultWorlds.generator(world, spec);
    }

    /** Paper CraftServer#getBiomeProvider(String) — {@link #getGenerator} 와 같은 규칙, 설정은 비 공급자 쪽. */
    public org.bukkit.generator.BiomeProvider getBiomeProvider(String world) {
        String spec = f().configuredWorldGenerator(world).biomeProvider();
        return spec.isEmpty() ? null : org.domi.furnace.compat.paper.BukkitDefaultWorlds.biomeProvider(world, spec);
    }

    /** 공급자의 WorldInfo 를 생긴 월드에 묶는다(CraftBukkit 은 CustomWorldChunkManager 에 그 CraftWorld 를 넘긴다). */
    private static World bound(World w, org.domi.furnace.compat.paper.BukkitBiomeProviders.LateWorldInfo info) {
        if (info != null && w != null) info.bind(w);
        return w;
    }

    private void warnHardcore(org.bukkit.WorldCreator creator, String name) {
        if (creator.hardcore() && !f().worlds().get(0).isHardcore() && WARNED_CREATE.add(name + "|hardcore")) {
            LOG.warning(CompatLang.t("compat.create-world-hardcore", name));
        }
    }

    private static final java.util.Set<String> WARNED_CREATE = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /**
     * 바닐라 평지 설정 JSON(DedicatedServerProperties.WorldDimensionData → FlatLevelGeneratorSettings.CODEC)을 그대로 서버에 넘긴다 — 서버가
     * server.properties generator-settings 와 같은 규칙으로 읽는다: layers 가 없으면 기본 프리셋(기반암·흙 2·잔디, 평원, 요새·마을. Paper 는 오류 로그 뒤
     * 기본값), structure_overrides 가 없으면 모든 구조물 세트, features·lakes 그대로. 비면 Paper 처럼 "{}".
     */
    private static void flatSettings(org.domi.furnace.api.world.WorldCreator.Builder b, String name, String json) {
        b.generator(org.domi.furnace.api.world.WorldGenerator.FLAT);
        if (json == null || json.isBlank()) {
            b.flatGeneratorSettings("{}");
            return;
        }
        org.domi.furnace.api.config.ConfigSection c;
        try {
            c = org.domi.furnace.api.config.ConfigFormat.JSON.parse(json);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("createWorld('" + name + "'): generatorSettings is not a JSON object - " + e.getMessage(), e);
        }
        if (!c.isList("layers") && !c.isEmpty()) LOG.warning(CompatLang.t("compat.create-world-flat-no-layers", name));
        b.flatGeneratorSettings(json);
    }

    @Override
    public World getWorld(UUID id) {
        for (World w : getWorlds()) if (w.getUID().equals(id)) return w;
        return null;
    }

    @Override
    public int broadcastMessage(String message) {
        f().broadcast(Compat.text(message));   // Paper: legacySection().deserialize — 주소 링크 없음
        return f().onlinePlayers().size();
    }

    /**
     * CraftBukkit 처럼 그 권한을 구독한 대상(=값이 정해진 대상) 가운데 권한이 참인 발신자에게만 보낸다 — 그래서 등록되지 않은 권한이면
     * OP·콘솔도 받지 않는다(Bukkit 과 같다). Paper 의 BroadcastMessageEvent 는 아직 없다.
     */
    @Override
    public int broadcast(String message, String permission) {
        java.util.Set<CommandSender> recipients = new java.util.LinkedHashSet<>();
        for (org.bukkit.permissions.Permissible p : getPluginManager().getPermissionSubscriptions(permission)) {
            if (p instanceof CommandSender s && p.hasPermission(permission)) recipients.add(s);
        }
        for (CommandSender s : recipients) s.sendMessage(message);
        return recipients.size();
    }

    @Override
    public ConsoleCommandSender getConsoleSender() { return CompatConsole.INSTANCE; }

    /**
     * 옛 방식 명령이면 Bukkit 처럼 지금 스레드가 주 스레드일 때 바로 실행해 알리고(예외는 CommandException), 아니면 Furnace 명령 큐에 넣는다.
     * 바닐라·Furnace 명령은 Paper 1.21.1 의 VanillaCommandWrapper 처럼 "이름 + 빈 칸 하나로 이은 인자" 로 다시 만들어 보낸다(빈 칸 규칙 통일, 이름 소문자).
     * 트리에 있는 이름이면 true, 없는 이름이면 false 이고 보낸 사람에게는 바닐라 오류 문장이 간다.
     * 앞의 '/' 는 떼지 않는다(Paper 와 같다): "/say hi" 는 없는 이름이라 false, 플러그인이 "/wand" 로 등록한 명령(WorldEdit 의 //wand)은 돈다.
     */
    @Override
    public boolean dispatchCommand(CommandSender sender, String commandLine) {
        if (Compat.commandMap().dispatch(sender, commandLine)) return true;
        String[] tokens = org.domi.furnace.compat.command.LegacyCommands.splitLine(commandLine);
        if (tokens.length == 0) return false;
        var commands = f().commands();
        String name = commands.hasCommand(tokens[0]) ? tokens[0] : tokens[0].toLowerCase(java.util.Locale.ROOT);
        if (!commands.hasCommand(name)) {
            // 없는 이름: 바닐라 오류 문장만 나오게 보낸다. 서버 쪽은 앞 '/' 를 하나 떼므로 하나 더 붙여 원래 줄이 그대로 파싱되게 한다
            f().dispatchCommand(Compat.furnaceSender(sender), "/" + commandLine.stripLeading());
            return false;
        }
        String line = tokens.length == 1 ? name : name + " " + String.join(" ", java.util.Arrays.copyOfRange(tokens, 1, tokens.length));
        f().dispatchCommand(Compat.furnaceSender(sender), line);
        return true;
    }

    @Override
    public BukkitScheduler getScheduler() { return CompatScheduler.INSTANCE; }

    @Override
    public io.papermc.paper.threadedregions.scheduler.AsyncScheduler getAsyncScheduler() { return org.domi.furnace.compat.paper.CompatPaperSchedulers.ASYNC; }

    @Override
    public io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler getGlobalRegionScheduler() { return org.domi.furnace.compat.paper.CompatPaperSchedulers.GLOBAL; }

    @Override
    public io.papermc.paper.threadedregions.scheduler.RegionScheduler getRegionScheduler() { return org.domi.furnace.compat.paper.CompatPaperSchedulers.REGION; }

    @Override
    public ServicesManager getServicesManager() { return Compat.services(); }

    @Override
    public org.bukkit.plugin.messaging.Messenger getMessenger() { return Compat.messenger(); }

    private final org.domi.furnace.compat.paper.CompatHelpMap helpMap = new org.domi.furnace.compat.paper.CompatHelpMap();

    @Override
    public org.bukkit.help.HelpMap getHelpMap() { return helpMap; }

    /** 모든 접속자에게(각자 그 채널을 알린 클라이언트에만 간다 — CraftServer 와 같다). */
    @Override
    public void sendPluginMessage(org.bukkit.plugin.Plugin source, String channel, byte[] message) {
        org.bukkit.plugin.messaging.StandardMessenger.validatePluginMessage(getMessenger(), source, channel, message);
        for (Player player : getOnlinePlayers()) player.sendPluginMessage(source, channel, message);
    }

    /** 접속자들이 알린 채널의 합. */
    @Override
    public java.util.Set<String> getListeningPluginChannels() {
        java.util.Set<String> result = new java.util.HashSet<>();
        for (Player player : getOnlinePlayers()) result.addAll(player.getListeningPluginChannels());
        return result;
    }

    @Override
    public boolean isPrimaryThread() { return org.domi.furnace.compat.thread.PrimaryThread.isPrimary(); }

    @Override
    public CommandMap getCommandMap() { return Compat.commandMap(); }

    @Override
    public org.bukkit.command.PluginCommand getPluginCommand(String name) {
        return name != null && Compat.commandMap().getCommand(name) instanceof org.bukkit.command.PluginCommand pc ? pc : null;
    }

    @Override
    public String getMotd() { return Compat.legacy(f().motd()); }

    @Override
    public int getViewDistance() { return f().viewDistance(); }

    @Override
    public int getSimulationDistance() { return f().simulationDistance(); }

    @Override
    public void shutdown() { Compat.furnace().shutdown(); }

    @Override
    public int getCurrentTick() { return (int) Compat.furnace().currentTick(); }
}
