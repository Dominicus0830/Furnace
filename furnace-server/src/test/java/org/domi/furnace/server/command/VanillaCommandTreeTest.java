// 우리 commands 패킷(플레이어가 받는 트리)을 해석해 바닐라 1.21.1 데이터 생성기의 commands.json 과 같은지 비교한다.
// 구현된 명령마다 노드 종류·실행 가능 여부·파서·파서 속성·자식 이름·리다이렉트가 같아야 한다.
package org.domi.furnace.server.command;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.zip.GZIPInputStream;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.config.ServerPropertiesFile;
import org.domi.furnace.server.net.ChunkWorkerPool;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.players.GameProfileCache;
import org.domi.furnace.server.players.PlayerLists;
import org.domi.furnace.server.players.ProfileLookup;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.Difficulty;
import org.domi.furnace.server.world.GameMode;
import org.domi.furnace.server.world.World;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.domi.furnace.server.world.storage.LevelData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VanillaCommandTreeTest {

    @TempDir Path tmp;

    record Node(int flags, int[] children, int redirect, String name, String parser, JsonObject props) {
        int type() { return flags & 3; }
        boolean executable() { return (flags & 4) != 0; }
    }

    static ServerCore core(Path tmp, FurnaceSchedulerImpl sched) { return org.domi.furnace.server.TestServers.core(tmp, sched); }

    private static List<Node> decode(CommandTreePacket p, int[] rootOut) {
        ByteBuf b = Unpooled.buffer();
        p.write(b);
        int n = Types.readVarInt(b);
        List<Node> nodes = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            int flags = b.readByte();
            int[] ch = new int[Types.readVarInt(b)];
            for (int j = 0; j < ch.length; j++) ch[j] = Types.readVarInt(b);
            int redirect = (flags & 8) != 0 ? Types.readVarInt(b) : -1;
            String name = null, parser = null;
            JsonObject props = null;
            if ((flags & 3) != 0) name = Types.readString(b, 32767);
            if ((flags & 3) == 2) {
                parser = ArgumentTypeIds.nameOf(Types.readVarInt(b));
                props = readProps(parser, b);
                if ((flags & 0x10) != 0) Types.readString(b, 32767);
            }
            nodes.add(new Node(flags, ch, redirect, name, parser, props));
        }
        rootOut[0] = Types.readVarInt(b);
        assertEquals(0, b.readableBytes());
        return nodes;
    }

    private static JsonObject readProps(String parser, ByteBuf b) {
        JsonObject o = new JsonObject();
        switch (parser) {
            case "brigadier:integer" -> { int f = b.readByte(); if ((f & 1) != 0) o.addProperty("min", b.readInt()); if ((f & 2) != 0) o.addProperty("max", b.readInt()); }
            case "brigadier:long" -> { int f = b.readByte(); if ((f & 1) != 0) o.addProperty("min", b.readLong()); if ((f & 2) != 0) o.addProperty("max", b.readLong()); }
            case "brigadier:float" -> { int f = b.readByte(); if ((f & 1) != 0) o.addProperty("min", b.readFloat()); if ((f & 2) != 0) o.addProperty("max", b.readFloat()); }
            case "brigadier:double" -> { int f = b.readByte(); if ((f & 1) != 0) o.addProperty("min", b.readDouble()); if ((f & 2) != 0) o.addProperty("max", b.readDouble()); }
            case "minecraft:time" -> o.addProperty("min", b.readInt());
            case "brigadier:string" -> o.addProperty("type", new String[]{"word", "phrase", "greedy"}[Types.readVarInt(b)]);
            case "minecraft:resource", "minecraft:resource_or_tag", "minecraft:resource_or_tag_key", "minecraft:resource_key" -> o.addProperty("registry", Types.readString(b, 32767));
            case "minecraft:entity" -> {
                int f = b.readByte();
                o.addProperty("type", (f & 2) != 0 ? "players" : "entities");
                o.addProperty("amount", (f & 1) != 0 ? "single" : "multiple");
            }
            case "minecraft:score_holder" -> o.addProperty("amount", (b.readByte() & 1) != 0 ? "multiple" : "single");
            default -> { }
        }
        return o.size() == 0 ? null : o;
    }

    /**
     * 아직 구현하지 않은 하위 명령(서버에 그 기능이 없다). 지금은 없다.
     */
    private static final java.util.Map<String, List<String>> NOT_YET = java.util.Map.of();

    /**
     * 바닐라 트리에는 있으나 이 서버가 등록하지 않는 루트 명령과 사유. 등록 안 된 루트가 이 목록과 정확히 같아야 한다(새 바닐라 루트가
     * 생기거나 구현되면 이 표를 고치게 한다).
     * <ul>
     *   <li>publish: 바닐라 Commands 가 includeIntegrated(통합 서버·ALL)일 때만 등록한다. 전용 서버(DEDICATED)의 commands 패킷에는 없다.
     *       (이 서버는 전용 서버라 바닐라 전용 서버와 같게 등록하지 않는다 — 가짜로 만들 대상이 아님.)</li>
     * </ul>
     */
    private static final java.util.Map<String, String> ABSENT_ROOTS = java.util.Map.of(
            "publish", "통합 서버 전용 — 전용 서버 트리에 없다");

    /** 이동·엔티티 명령 묶음 — 구현돼 있어야 하고(트리 대조를 빠져나가지 못하게) 바닐라 트리와 같아야 한다. */
    private static final List<String> PROFILING_COMMANDS = List.of("debug", "spectate", "jfr", "perf");

    private static final List<String> MOVEMENT_COMMANDS = List.of("teleport", "tp", "spreadplayers", "ride", "random", "tag", "forceload", "tick", "scoreboard", "team", "execute", "function", "return", "schedule", "place", "trigger", "teammsg", "tm");

    private static void compare(String path, JsonObject vanilla, List<Node> nodes, int idx, int root) {
        Node n = nodes.get(idx);
        String type = vanilla.get("type").getAsString();
        assertEquals(type, switch (n.type()) { case 0 -> "root"; case 1 -> "literal"; default -> "argument"; }, path + " 종류");
        assertEquals(vanilla.has("executable") && vanilla.get("executable").getAsBoolean(), n.executable(), path + " 실행 가능");
        if (type.equals("argument")) {
            assertEquals(vanilla.get("parser").getAsString(), n.parser(), path + " 파서");
            JsonElement vp = vanilla.get("properties");
            if (vp == null) assertNull(n.props(), path + " 속성");
            else assertEquals(normalize(vp.getAsJsonObject()), normalize(n.props()), path + " 속성");
        }
        // 리다이렉트 대상 경로까지 비교한다. 루트로 가는 것(execute run)은 바닐라 덤프(JSON)에 경로가 비어 키가 없다 — 그때만 키 없음으로 본다.
        boolean toRoot = n.redirect() == root;
        assertEquals(vanilla.has("redirect"), n.redirect() >= 0 && !toRoot, path + " 리다이렉트");
        if (n.redirect() >= 0 && !toRoot) {
            List<String> expectedPath = new ArrayList<>();
            for (JsonElement e : vanilla.getAsJsonArray("redirect")) expectedPath.add(e.getAsString());
            assertEquals(expectedPath, pathOf(nodes, root, n.redirect()), path + " 리다이렉트 대상");
        } else if (toRoot) {
            assertTrue(path.equals("/execute run") || path.equals("/return run"), "루트로 가는 리다이렉트는 execute run·return run 만 허용: " + path);
        }
        JsonObject vc = vanilla.has("children") ? vanilla.getAsJsonObject("children") : new JsonObject();
        TreeSet<String> ours = new TreeSet<>();
        for (int c : n.children()) ours.add(nodes.get(c).name());
        TreeSet<String> expected = new TreeSet<>(vc.keySet());
        for (String m : NOT_YET.getOrDefault(path, List.of())) expected.remove(m);
        assertEquals(expected, ours, path + " 자식");
        for (int c : n.children()) compare(path + " " + nodes.get(c).name(), vc.getAsJsonObject(nodes.get(c).name()), nodes, c, root);
    }

    /** 루트에서 target 노드까지 자식 이름 경로(리다이렉트는 따라가지 않는다). */
    private static List<String> pathOf(List<Node> nodes, int root, int target) {
        List<String> path = new ArrayList<>();
        return find(nodes, root, target, path) ? path : null;
    }

    private static boolean find(List<Node> nodes, int cur, int target, List<String> path) {
        if (cur == target) return true;
        for (int c : nodes.get(cur).children()) {
            path.add(nodes.get(c).name());
            if (find(nodes, c, target, path)) return true;
            path.remove(path.size() - 1);
        }
        return false;
    }

    private static String normalize(JsonObject o) {
        if (o == null) return null;
        TreeSet<String> parts = new TreeSet<>();
        for (var e : o.entrySet()) {
            JsonElement v = e.getValue();
            parts.add(e.getKey() + "=" + (v.getAsJsonPrimitive().isNumber() ? Double.toString(v.getAsDouble()) : v.getAsString()));
        }
        return parts.toString();
    }

    private static TreeSet<String> rootNames(ServerCore core, int permission, int[] root) {
        List<Node> nodes = decode(core.commands().treeFor(CommandSource.forProfile(core, "P" + permission, permission)), root);
        TreeSet<String> names = new TreeSet<>();
        for (int c : nodes.get(root[0]).children()) names.add(nodes.get(c).name());
        return names;
    }

    @Test
    void implementedCommandsMatchVanillaTree() throws Exception {
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        try {
            ServerCore core = core(tmp, sched);
            JsonObject vanilla;
            try (var in = new InputStreamReader(new GZIPInputStream(getClass().getResourceAsStream("/commands/vanilla-1.21.1-commands.json.gz")),
                    StandardCharsets.UTF_8)) {
                vanilla = JsonParser.parseReader(in).getAsJsonObject().getAsJsonObject("children");
            }
            int[] root = new int[1];
            // 권한 4(콘솔·OP 4)로 받은 트리: 구현된 모든 명령이 들어 있다
            List<Node> nodes = decode(core.commands().treeFor(CommandSource.forProfile(core, "Op", 4)), root);
            int checked = 0;
            for (int c : nodes.get(root[0]).children()) {
                String name = nodes.get(c).name();
                assertTrue(vanilla.has(name), "바닐라에 없는 명령: " + name);
                compare("/" + name, vanilla.getAsJsonObject(name), nodes, c, root[0]);
                checked++;
            }
            assertTrue(checked >= 18, "검사한 명령 수 " + checked);
            TreeSet<String> registered = new TreeSet<>();
            for (int c : nodes.get(root[0]).children()) registered.add(nodes.get(c).name());
            for (String name : MOVEMENT_COMMANDS) assertTrue(registered.contains(name), "등록돼 있어야 하는 명령: " + name);
            // jfr 은 JFR 을 쓸 수 있는 JVM 에서만 등록된다(바닐라 JvmProfiler.isAvailable)
            for (String name : PROFILING_COMMANDS) {
                if (name.equals("jfr") && !org.domi.furnace.server.profiling.JfrProfiler.isAvailable()) continue;
                assertTrue(registered.contains(name), "등록돼 있어야 하는 명령: " + name);
            }
            // 바닐라 루트 중 등록하지 않은 것은 사유가 적힌 ABSENT_ROOTS 뿐이다
            TreeSet<String> unregistered = new TreeSet<>(vanilla.keySet());
            unregistered.removeAll(registered);
            if (!org.domi.furnace.server.profiling.JfrProfiler.isAvailable()) unregistered.remove("jfr");
            assertEquals(new TreeSet<>(ABSENT_ROOTS.keySet()), unregistered, "등록하지 않은 바닐라 루트 명령");
            // 권한: 바닐라 requires 와 같다 — spectate 2, debug 3, jfr·perf 4
            TreeSet<String> at2 = rootNames(core, 2, root), at3 = rootNames(core, 3, root), at4 = rootNames(core, 4, root);
            assertTrue(at2.contains("spectate") && !at2.contains("debug") && !at2.contains("jfr") && !at2.contains("perf"));
            assertTrue(at3.contains("spectate") && at3.contains("debug") && !at3.contains("jfr") && !at3.contains("perf"));
            assertTrue(at4.contains("spectate") && at4.contains("debug") && at4.contains("perf"));
            assertEquals(org.domi.furnace.server.profiling.JfrProfiler.isAvailable(), at4.contains("jfr"));
            // 권한 0 이면 help·list·me·msg·random·teammsg·tell·tm·trigger·w 만(바닐라 random·teammsg·trigger 는 requires 가 없다)
            List<Node> guest = decode(core.commands().treeFor(CommandSource.forProfile(core, "Guest", 0)), root);
            TreeSet<String> names = new TreeSet<>();
            for (int c : guest.get(root[0]).children()) names.add(guest.get(c).name());
            assertEquals(new TreeSet<>(List.of("help", "list", "me", "msg", "random", "teammsg", "tell", "tm", "trigger", "w")), names);
            // 바닐라 권한 레벨 3 명령(KickCommand 등)은 레벨 2 트리에 없고 레벨 3 트리에 있다
            for (String name : List.of("kick", "ban", "op", "deop", "whitelist")) {
                assertFalse(names(core, 2).contains(name), "레벨 2 에 보이면 안 됨: " + name);
                assertTrue(names(core, 3).contains(name), "레벨 3 에 보여야 함: " + name);
            }
        } finally {
            sched.close();
        }
    }

    private static TreeSet<String> names(ServerCore core, int level) {
        int[] root = new int[1];
        List<Node> nodes = decode(core.commands().treeFor(CommandSource.forProfile(core, "L" + level, level)), root);
        TreeSet<String> out = new TreeSet<>();
        for (int c : nodes.get(root[0]).children()) out.add(nodes.get(c).name());
        return out;
    }
}
