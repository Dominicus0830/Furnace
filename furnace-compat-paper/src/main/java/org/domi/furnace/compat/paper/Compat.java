// Furnace 객체 ↔ Bukkit 객체 변환 입구. 서버(Furnace 인스턴스)마다 따로 캐시를 둔다 — 시험처럼 서버를 여러 번 띄워도 낡은 핸들이 남지 않는다.
package org.domi.furnace.compat.paper;

import java.lang.ref.WeakReference;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.SimpleCommandMap;
import org.bukkit.plugin.SimpleServicesManager;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.bukkit.craftbukkit.entity.CraftLivingEntity;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.domi.furnace.api.Furnace;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.api.text.serializer.LegacyTextSerializer;
import org.domi.furnace.api.world.BlockPos;
import org.domi.furnace.compat.command.LegacyCommands;
import org.domi.furnace.compat.sched.LegacyScheduler;

public final class Compat {

    private static final int PRUNE_AT = 1024;

    private static volatile State state;

    private Compat() {}

    private static final class State {
        final Furnace furnace;
        final ConcurrentHashMap<String, CraftWorld> worlds = new ConcurrentHashMap<>();
        final ConcurrentHashMap<UUID, CraftPlayer> players = new ConcurrentHashMap<>();
        /** 플레이어가 아닌 엔티티(H6: 같은 엔티티는 같은 CraftEntity → 같은 getHandle()). 아무도 안 들고 있으면 놓는다. */
        final ConcurrentHashMap<UUID, WeakReference<CraftEntity>> entities = new ConcurrentHashMap<>();
        /** Bukkit 메타데이터(메모리에만, 서버마다 따로 — Paper 처럼 엔티티는 UUID·플레이어는 이름·월드는 UID·블록은 칸으로 가른다). */
        final org.bukkit.craftbukkit.metadata.EntityMetadataStore entityMetadata = new org.bukkit.craftbukkit.metadata.EntityMetadataStore();
        final org.bukkit.craftbukkit.metadata.PlayerMetadataStore playerMetadata = new org.bukkit.craftbukkit.metadata.PlayerMetadataStore();
        final org.bukkit.craftbukkit.metadata.WorldMetadataStore worldMetadata = new org.bukkit.craftbukkit.metadata.WorldMetadataStore();
        final org.bukkit.craftbukkit.metadata.BlockMetadataStore blockMetadata = new org.bukkit.craftbukkit.metadata.BlockMetadataStore();
        // 옛 스케줄러·명령·서비스는 쓰는 플러그인이 있을 때 처음 만든다(박동 하나를 전역에 걸기 때문)
        private volatile LegacyScheduler scheduler;
        private volatile LegacyCommands commands;
        private volatile SimpleCommandMap commandMap;
        private volatile SimpleServicesManager services;
        private volatile CompatMessenger messenger;
        @SuppressWarnings("removal")
        private volatile org.bukkit.plugin.SimplePluginManager pluginManager;

        State(Furnace furnace) {
            this.furnace = furnace;
            installPermissionOverride(furnace, this);
        }

        synchronized LegacyScheduler scheduler() {
            if (scheduler == null) scheduler = new LegacyScheduler(furnace);
            return scheduler;
        }

        synchronized LegacyCommands commands() {
            if (commands == null) commands = new LegacyCommands(furnace);
            return commands;
        }

        synchronized SimpleCommandMap commandMap() {
            if (commandMap == null) commandMap = new SimpleCommandMap(CraftServer.INSTANCE);
            return commandMap;
        }

        synchronized SimpleServicesManager services() {
            if (services == null) services = new SimpleServicesManager();
            return services;
        }

        @SuppressWarnings("removal")
        synchronized org.bukkit.plugin.SimplePluginManager pluginManager() {
            if (pluginManager == null) pluginManager = new org.bukkit.plugin.SimplePluginManager(CraftServer.INSTANCE, commandMap());
            return pluginManager;
        }

        synchronized CompatMessenger messenger() {
            if (messenger == null) messenger = new CompatMessenger(furnace);
            return messenger;
        }

        void close() {
            LegacyScheduler sc = scheduler;
            if (sc != null) {
                try {
                    sc.close();
                } catch (RuntimeException ignored) {
                    // 이미 멈춘 서버의 박동이라 닫을 것이 없을 수 있다
                }
            }
        }
    }

    private static State state() {
        Furnace f = Furnace.server();
        State s = state;
        if (s == null || s.furnace != f) {
            synchronized (Compat.class) {
                s = state;
                if (s == null || s.furnace != f) {
                    if (s != null) s.close();   // 다른 서버로 바뀌었다(시험처럼 서버를 다시 띄움) — 옛 박동을 닫는다
                    state = s = new State(f);
                }
            }
        }
        return s;
    }

    /**
     * 서버 종료 중 플러그인이 모두 꺼진 뒤: 옛 Bukkit 비동기 작업이 끝나기를 최대 {@code maxMillis} 기다리고(Paper Wait-for-Async-Tasks-during-shutdown),
     * 비동기 풀을 닫는다. 옛 스케줄러를 쓴 적이 없으면(아직 안 만들었으면) 아무것도 안 한다.
     */
    public static void awaitAsyncTasksAtShutdown(Furnace server, long maxMillis) {
        State s = state;
        if (s == null || s.furnace != server) return;
        LegacyScheduler sc = s.scheduler;
        if (sc == null) return;
        try {
            sc.awaitAsyncWorkers(maxMillis);
        } finally {
            s.close();
        }
    }

    /**
     * 개발 모드 핫 리로드: 방금 끈 플러그인의 옛 Bukkit 비동기 작업이 끝나기를 최대 {@code maxMillis} 기다린다(남으면 Nag). 풀은 닫지 않는다.
     * 옛 스케줄러를 쓴 적이 없으면 아무것도 안 한다.
     */
    public static void awaitPluginAsyncTasks(Furnace server, java.util.Collection<org.domi.furnace.api.plugin.FurnacePlugin> plugins, long maxMillis) {
        State s = state;
        if (s == null || s.furnace != server) return;
        LegacyScheduler sc = s.scheduler;
        if (sc == null) return;
        sc.awaitAsyncWorkers(plugins, maxMillis);
    }

    /** 호환 모듈 전용 — 지금 떠 있는 Furnace 서버. */
    public static Furnace furnace() { return state().furnace; }

    /** 지금 서버의 옛 스케줄러(BukkitScheduler 의 바닥). */
    static LegacyScheduler scheduler() { return state().scheduler(); }

    /** 지금 서버의 옛 명령 지도(명령 트리에 붙이는 바닥). */
    static LegacyCommands commands() { return state().commands(); }

    /** 지금 서버의 Bukkit 명령 지도. */
    public static SimpleCommandMap commandMap() { return state().commandMap(); }

    /** 지금 서버의 서비스 지도. */
    public static SimpleServicesManager services() { return state().services(); }

    /** 지금 서버의 Bukkit 플러그인 관리자(Bukkit.getPluginManager()). */
    @SuppressWarnings("removal")
    public static org.bukkit.plugin.SimplePluginManager pluginManager() { return state().pluginManager(); }

    /** 지금 서버의 플러그인 메신저. */
    public static CompatMessenger messenger() { return state().messenger(); }

    /** 지금 서버의 엔티티 메타데이터(CraftServer#getEntityMetadata). */
    public static org.bukkit.craftbukkit.metadata.EntityMetadataStore entityMetadata() { return state().entityMetadata; }

    /** 지금 서버의 플레이어 메타데이터(CraftServer#getPlayerMetadata). */
    public static org.bukkit.craftbukkit.metadata.PlayerMetadataStore playerMetadata() { return state().playerMetadata; }

    /** 지금 서버의 월드 메타데이터(CraftServer#getWorldMetadata). */
    public static org.bukkit.craftbukkit.metadata.WorldMetadataStore worldMetadata() { return state().worldMetadata; }

    /** 지금 서버의 블록 메타데이터(CraftWorld#getBlockMetadata 를 월드 UID 로 합친 것). */
    public static org.bukkit.craftbukkit.metadata.BlockMetadataStore blockMetadata() { return state().blockMetadata; }

    // ------------------------------------------------------------------ 월드

    /** 차원 키(minecraft:overworld)로. 없으면 null. */
    public static World world(String dimension) {
        if (dimension == null) return null;
        State s = state();
        CraftWorld w = s.worlds.get(dimension);
        org.domi.furnace.api.world.World fw = s.furnace.world(dimension);
        if (fw == null) {
            s.worlds.remove(dimension);
            return null;
        }
        if (w == null || w.furnaceHandle() != fw) {
            w = new CraftWorld(fw);
            s.worlds.put(dimension, w);
        }
        return w;
    }

    public static World world(org.domi.furnace.api.world.World w) { return w == null ? null : world(w.dimension()); }

    /** 월드 이름(world, world_nether)으로. 없으면 null. */
    public static World worldNamed(String name) {
        org.domi.furnace.api.world.World w = state().furnace.worldNamed(name);
        return world(w);
    }

    // ------------------------------------------------------------------ 위치·블록

    public static org.bukkit.Location location(org.domi.furnace.api.world.Location l) {
        if (l == null) return null;
        return new org.bukkit.Location(world(l.world()), l.x(), l.y(), l.z(), l.yaw(), l.pitch());
    }

    public static org.domi.furnace.api.world.Location location(org.bukkit.Location l) {
        if (l == null) return null;
        World w = l.getWorld();
        if (w == null) throw new IllegalArgumentException("월드가 없는 Location 은 옮길 수 없다: " + l);
        return new org.domi.furnace.api.world.Location(dimension(w), l.getX(), l.getY(), l.getZ(), l.getYaw(), l.getPitch());
    }

    /** Bukkit 월드의 차원 키. */
    public static String dimension(World w) {
        if (w instanceof CraftWorld c) return c.furnaceHandle().dimension();
        org.domi.furnace.api.world.World f = state().furnace.worldNamed(w.getName());
        if (f == null) throw new IllegalArgumentException("Furnace 에 없는 월드: " + w.getName());
        return f.dimension();
    }

    public static Block block(String dimension, BlockPos pos) { return new CompatBlock(dimension, pos); }

    /** minecraft:stone → STONE. 모르는 종류(플러그인 블록)는 AIR 가 아니라 null 이 아닌 값을 줄 수 없어 UnsupportedFeature. */
    static Material material(String blockType) {
        Material m = Material.matchMaterial(blockType);
        if (m == null) throw new UnsupportedFeature("Material " + blockType, "Bukkit Material 열거형에 없는 블록이다(데이터 팩·플러그인 블록)");
        return m;
    }

    // ------------------------------------------------------------------ 엔티티·플레이어

    public static org.bukkit.entity.Player player(org.domi.furnace.api.entity.Player p) {
        if (p == null) return null;
        State s = state();
        CraftPlayer c = s.players.get(p.uniqueId());
        if (c != null && c.furnaceHandle() == p) return c;
        if (s.players.size() > PRUNE_AT) s.players.values().removeIf(x -> {
            if (x.furnaceHandle().isOnline()) return false;
            x.retire();
            return true;
        });
        // 처음 감싸기·핸들 교체를 원자적으로 — 두 스레드가 동시에 와도 같은 CraftPlayer 를 받는다
        return s.players.compute(p.uniqueId(), (k, cur) -> {
            if (cur != null && cur.furnaceHandle() == p) return cur;
            if (cur != null) cur.retire();   // 옛 접속의 권한 구독·거울을 걷은 뒤 새 객체를 만든다
            return new CraftPlayer(p);
        });
    }

    /** 로그인이 거부된 접속의 CraftPlayer 를 표에서 꺼내 권한 구독·거울을 걷는다(그 접속 것일 때만 — 같은 UUID 의 다른 접속은 두고). */
    static void forgetDenied(org.domi.furnace.api.entity.Player p) {
        State s = state;
        if (s == null || p == null) return;
        s.players.computeIfPresent(p.uniqueId(), (k, cur) -> {
            if (cur.furnaceHandle() != p) return cur;
            cur.retire();
            return null;
        });
    }

    /**
     * 이미 감싼 CraftPlayer 만 준다(새로 만들지 않는다) — 권한 판정 경로에서 부르므로 CraftPlayer 생성(권한 계산)으로 되돌아가지 않게.
     * 접속이 바뀐(핸들이 다른) 옛 객체는 null.
     */
    static CraftPlayer existingPlayer(org.domi.furnace.api.entity.Player p) {
        State s = state;
        if (s == null) return null;
        CraftPlayer c = s.players.get(p.uniqueId());
        return c != null && c.furnaceHandle() == p ? c : null;
    }

    /**
     * 권한 플러그인(LuckPerms 등)이 {@code CraftHumanEntity.perm} 을 자기 PermissibleBase 하위 클래스로 바꿔 끼웠으면, Furnace 의 권한 판정
     * (Furnace 플러그인·Furnace 명령이 묻는 것)도 그 객체에 맡긴다 — Bukkit 서버에서 모든 hasPermission 이 그 객체로 가는 것과 같게.
     * 바꿔 끼우지 않았으면(정확히 PermissibleBase) 맡지 않는다. 그때는 Bukkit PermissibleBase 가 거꾸로 Furnace 판정에 묻는다.
     */
    private static void installPermissionOverride(Furnace furnace, State owner) {
        org.domi.furnace.api.permission.PermissionManager pm;
        try {
            pm = furnace.permissions();
            pm.setOverride(new org.domi.furnace.api.permission.PermissionOverride() {
                /** 안전망: 끼운 객체가 어떤 길로든 다시 Furnace 판정에 묻으면 그 안쪽은 맡지 않는다(재귀 고리 끊기, PR #210 검토 R1). */
                private final ThreadLocal<Boolean> inside = new ThreadLocal<>();

                @Override
                public Boolean hasPermission(org.domi.furnace.api.permission.Permissible subject, String node) {
                    org.bukkit.permissions.PermissibleBase pb = injected(subject);
                    if (pb == null) return null;
                    inside.set(Boolean.TRUE);
                    try { return pb.hasPermission(node); } finally { inside.remove(); }
                }

                @Override
                public Boolean isPermissionSet(org.domi.furnace.api.permission.Permissible subject, String node) {
                    org.bukkit.permissions.PermissibleBase pb = injected(subject);
                    if (pb == null) return null;
                    inside.set(Boolean.TRUE);
                    try { return pb.isPermissionSet(node); } finally { inside.remove(); }
                }

                private org.bukkit.permissions.PermissibleBase injected(org.domi.furnace.api.permission.Permissible subject) {
                    if (inside.get() != null || state != owner || !(subject instanceof org.domi.furnace.api.entity.Player p)) return null;
                    CraftPlayer c = existingPlayer(p);
                    if (c == null) return null;
                    org.bukkit.permissions.PermissibleBase pb = c.permissible();
                    return pb == null || pb.getClass() == org.bukkit.permissions.PermissibleBase.class ? null : pb;
                }
            });
        } catch (UnsupportedOperationException e) {
            // 권한 판정을 넘겨받지 못하는 서버(시험용 가짜 등) — Bukkit 쪽 PermissibleBase 만 바뀐다
        }
    }

    public static org.bukkit.entity.Entity entity(org.domi.furnace.api.entity.Entity e) {
        if (e == null) return null;
        if (e instanceof org.domi.furnace.api.entity.Player p) return player(p);
        State s = state();
        Class<? extends CraftEntity> kind = craftClass(e);
        WeakReference<CraftEntity> ref = s.entities.get(e.uniqueId());
        CraftEntity c = ref == null ? null : ref.get();
        if (c != null && c.getClass() == kind) {
            c.furnaceHandle(e);   // 같은 엔티티의 새 스냅샷(옛 것이면 버린다)
            return c;
        }
        if (s.entities.size() > PRUNE_AT) s.entities.values().removeIf(r -> r.get() == null);
        // 없거나 지워졌을 때만 원자적으로 새로 만든다 — 동시에 처음 감싸도 CraftEntity(=getHandle 정체성)가 하나다
        CraftEntity[] out = new CraftEntity[1];
        s.entities.compute(e.uniqueId(), (k, r) -> {
            CraftEntity cur = r == null ? null : r.get();
            if (cur != null && cur.getClass() == kind) {
                cur.furnaceHandle(e);
                out[0] = cur;
                return r;
            }
            out[0] = newCraft(kind, e);
            return new WeakReference<>(out[0]);
        });
        return out[0];
    }

    /** 이 핸들을 감쌀 CraftEntity 종류 — Bukkit 이 따로 형을 두는 종류(갑옷 거치대·돼지)는 그 형, 나머지는 살아 있는지로. */
    private static Class<? extends CraftEntity> craftClass(org.domi.furnace.api.entity.Entity e) {
        if (!(e instanceof org.domi.furnace.api.entity.LivingEntity)) return CraftEntity.class;
        return switch (e.type()) {
            case "minecraft:armor_stand" -> org.bukkit.craftbukkit.entity.CraftArmorStand.class;
            case "minecraft:pig" -> e instanceof org.domi.furnace.api.entity.Mob ? org.bukkit.craftbukkit.entity.CraftPig.class : CraftLivingEntity.class;
            default -> CraftLivingEntity.class;
        };
    }

    private static CraftEntity newCraft(Class<? extends CraftEntity> kind, org.domi.furnace.api.entity.Entity e) {
        if (kind == CraftEntity.class) return new CraftEntity(e);
        var living = (org.domi.furnace.api.entity.LivingEntity) e;
        if (kind == org.bukkit.craftbukkit.entity.CraftArmorStand.class) return new org.bukkit.craftbukkit.entity.CraftArmorStand(living);
        if (kind == org.bukkit.craftbukkit.entity.CraftPig.class) return new org.bukkit.craftbukkit.entity.CraftPig((org.domi.furnace.api.entity.Mob) e);
        return new CraftLivingEntity(living);
    }

    public static org.bukkit.command.CommandSender sender(org.domi.furnace.api.command.CommandSender s) {
        if (s == null) return null;
        if (s instanceof org.domi.furnace.api.entity.Player p) return player(p);
        if (s == state().furnace.console()) return CompatConsole.INSTANCE;
        if (s instanceof org.domi.furnace.api.command.RemoteConsoleSender r) return new CompatRemoteSender(r);
        return new CompatSender(s);
    }

    /** Bukkit 발신자 → Furnace 발신자. */
    public static org.domi.furnace.api.command.CommandSender furnaceSender(org.bukkit.command.CommandSender s) {
        Objects.requireNonNull(s, "sender");
        if (s instanceof CraftPlayer p) return p.furnaceHandle();
        if (s instanceof CompatConsole) return state().furnace.console();
        if (s instanceof CompatSender c) return c.handle();
        if (s instanceof CraftEntity e && e.furnaceHandle() instanceof org.domi.furnace.api.command.CommandSender cs) return cs;
        throw new IllegalArgumentException("Furnace 가 모르는 CommandSender 구현이다: " + s.getClass().getName());
    }

    // ------------------------------------------------------------------ 글

    private static final LegacyTextSerializer LEGACY = LegacyTextSerializer.section();

    /** Text → § 서식 문자열(null 이면 null). */
    public static String legacy(Text t) { return t == null ? null : LEGACY.serialize(t); }

    /** § 서식 문자열 → Text(null 이면 null). */
    public static Text text(String legacy) { return legacy == null ? null : LEGACY.deserialize(legacy); }

    // Paper 1.21.1: CraftPlayer.sendMessage(String) 는 CraftChatMessage.fromString 을 써서 글 속 주소를 클릭하면 열리는 링크로 만든다.
    // CraftBukkit 고유 패턴: 대소문자 무시, 끝의 문장부호(. ? ! , ; :)는 링크에서 뺀다, 포트·경로·쿼리는 공백/줄바꿈/§ 전까지, 스킴이 없으면 http:// 를 붙인다.
    // (Paper 의 broadcastMessage 는 legacySection().deserialize 라 링크가 없다 — Spigot 만 링크. 그래서 broadcast 는 text() 를 쓴다.)
    private static final java.util.regex.Pattern CRAFT_URL = java.util.regex.Pattern.compile(
            "(?:(https?)://)?[-\\w_.]{2,}\\.[a-z]{2,4}.*?(?=[.?!,;:]?(?:[§ \\n]|$))", java.util.regex.Pattern.CASE_INSENSITIVE);
    private static final LegacyTextSerializer LEGACY_LINKS = LegacyTextSerializer.builder().character('§').hexColors().useUnusualXRepeatedCharacterHexFormat()
            .extractUrls(CRAFT_URL, org.domi.furnace.api.text.Style.empty()).urlScheme("http://").build();

    /** 플레이어에게 보이는 문자열 메시지(sendMessage) → Text: {@link #text} 와 같고 글 속 주소가 클릭 링크(OPEN_URL)가 된다(null 이면 null). */
    public static Text message(String legacy) { return legacy == null ? null : LEGACY_LINKS.deserialize(legacy); }
}
