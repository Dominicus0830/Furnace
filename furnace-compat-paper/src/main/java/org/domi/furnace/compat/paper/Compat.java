// Furnace 객체 ↔ Bukkit 객체 변환 입구. 서버(Furnace 인스턴스)마다 따로 캐시를 둔다 — 시험처럼 서버를 여러 번 띄워도 낡은 핸들이 남지 않는다.
package org.domi.furnace.compat.paper;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.SimpleCommandMap;
import org.bukkit.plugin.SimpleServicesManager;
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
        final ConcurrentHashMap<String, CompatWorld> worlds = new ConcurrentHashMap<>();
        final ConcurrentHashMap<UUID, CompatPlayer> players = new ConcurrentHashMap<>();
        // 옛 스케줄러·명령·서비스는 쓰는 플러그인이 있을 때 처음 만든다(박동 하나를 전역에 걸기 때문)
        private volatile LegacyScheduler scheduler;
        private volatile LegacyCommands commands;
        private volatile SimpleCommandMap commandMap;
        private volatile SimpleServicesManager services;

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
            if (commandMap == null) commandMap = new SimpleCommandMap(CompatServer.INSTANCE);
            return commandMap;
        }

        synchronized SimpleServicesManager services() {
            if (services == null) services = new SimpleServicesManager();
            return services;
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

    static Furnace furnace() { return state().furnace; }

    /** 지금 서버의 옛 스케줄러(BukkitScheduler 의 바닥). */
    static LegacyScheduler scheduler() { return state().scheduler(); }

    /** 지금 서버의 옛 명령 지도(명령 트리에 붙이는 바닥). */
    static LegacyCommands commands() { return state().commands(); }

    /** 지금 서버의 Bukkit 명령 지도. */
    static SimpleCommandMap commandMap() { return state().commandMap(); }

    /** 지금 서버의 서비스 지도. */
    static SimpleServicesManager services() { return state().services(); }

    // ------------------------------------------------------------------ 월드

    /** 차원 키(minecraft:overworld)로. 없으면 null. */
    public static World world(String dimension) {
        if (dimension == null) return null;
        State s = state();
        CompatWorld w = s.worlds.get(dimension);
        org.domi.furnace.api.world.World fw = s.furnace.world(dimension);
        if (fw == null) {
            s.worlds.remove(dimension);
            return null;
        }
        if (w == null || w.handle() != fw) {
            w = new CompatWorld(fw);
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
        if (w instanceof CompatWorld c) return c.handle().dimension();
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
        CompatPlayer c = s.players.get(p.uniqueId());
        if (c == null || c.handle() != p) {
            if (s.players.size() > PRUNE_AT) s.players.values().removeIf(x -> !x.handle().isOnline());
            c = new CompatPlayer(p);
            s.players.put(p.uniqueId(), c);
        }
        return c;
    }

    public static org.bukkit.entity.Entity entity(org.domi.furnace.api.entity.Entity e) {
        if (e == null) return null;
        if (e instanceof org.domi.furnace.api.entity.Player p) return player(p);
        if (e instanceof org.domi.furnace.api.entity.LivingEntity l) return new CompatLivingEntity(l);
        return new CompatEntity(e);
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
        if (s instanceof CompatPlayer p) return p.handle();
        if (s instanceof CompatConsole) return state().furnace.console();
        if (s instanceof CompatSender c) return c.handle();
        if (s instanceof CompatEntity e && e.rawHandle() instanceof org.domi.furnace.api.command.CommandSender cs) return cs;
        throw new IllegalArgumentException("Furnace 가 모르는 CommandSender 구현이다: " + s.getClass().getName());
    }

    // ------------------------------------------------------------------ 글

    private static final LegacyTextSerializer LEGACY = LegacyTextSerializer.section();

    /** Text → § 서식 문자열(null 이면 null). */
    public static String legacy(Text t) { return t == null ? null : LEGACY.serialize(t); }

    /** § 서식 문자열 → Text(null 이면 null). */
    public static Text text(String legacy) { return legacy == null ? null : LEGACY.deserialize(legacy); }
}
