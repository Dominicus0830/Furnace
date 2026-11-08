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
        // 옛 스케줄러·명령·서비스는 쓰는 플러그인이 있을 때 처음 만든다(박동 하나를 전역에 걸기 때문)
        private volatile LegacyScheduler scheduler;
        private volatile LegacyCommands commands;
        private volatile SimpleCommandMap commandMap;
        private volatile SimpleServicesManager services;
        private volatile CompatMessenger messenger;

        State(Furnace furnace) { this.furnace = furnace; }

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

    /** 지금 서버의 플러그인 메신저. */
    public static CompatMessenger messenger() { return state().messenger(); }

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
        if (s.players.size() > PRUNE_AT) s.players.values().removeIf(x -> !x.furnaceHandle().isOnline());
        // 처음 감싸기·핸들 교체를 원자적으로 — 두 스레드가 동시에 와도 같은 CraftPlayer 를 받는다
        return s.players.compute(p.uniqueId(), (k, cur) -> cur != null && cur.furnaceHandle() == p ? cur : new CraftPlayer(p));
    }

    public static org.bukkit.entity.Entity entity(org.domi.furnace.api.entity.Entity e) {
        if (e == null) return null;
        if (e instanceof org.domi.furnace.api.entity.Player p) return player(p);
        State s = state();
        boolean living = e instanceof org.domi.furnace.api.entity.LivingEntity;
        WeakReference<CraftEntity> ref = s.entities.get(e.uniqueId());
        CraftEntity c = ref == null ? null : ref.get();
        if (c != null && (c instanceof CraftLivingEntity) == living) {
            c.furnaceHandle(e);   // 같은 엔티티의 새 스냅샷(옛 것이면 버린다)
            return c;
        }
        if (s.entities.size() > PRUNE_AT) s.entities.values().removeIf(r -> r.get() == null);
        // 없거나 지워졌을 때만 원자적으로 새로 만든다 — 동시에 처음 감싸도 CraftEntity(=getHandle 정체성)가 하나다
        CraftEntity[] out = new CraftEntity[1];
        s.entities.compute(e.uniqueId(), (k, r) -> {
            CraftEntity cur = r == null ? null : r.get();
            if (cur != null && (cur instanceof CraftLivingEntity) == living) {
                cur.furnaceHandle(e);
                out[0] = cur;
                return r;
            }
            out[0] = living ? new CraftLivingEntity((org.domi.furnace.api.entity.LivingEntity) e) : new CraftEntity(e);
            return new WeakReference<>(out[0]);
        });
        return out[0];
    }

    public static org.bukkit.command.CommandSender sender(org.domi.furnace.api.command.CommandSender s) {
        if (s == null) return null;
        if (s instanceof org.domi.furnace.api.entity.Player p) return player(p);
        if (s == state().furnace.console()) return CompatConsole.INSTANCE;
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
