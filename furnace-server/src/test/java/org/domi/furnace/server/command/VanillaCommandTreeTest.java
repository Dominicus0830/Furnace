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
            case "minecraft:resource", "minecraft:resource_or_tag_key" -> o.addProperty("registry", Types.readString(b, 32767));
            case "minecraft:entity" -> {
                int f = b.readByte();
                o.addProperty("type", (f & 2) != 0 ? "players" : "entities");
                o.addProperty("amount", (f & 1) != 0 ? "single" : "multiple");
            }
            default -> { }
        }
        return o.size() == 0 ? null : o;
    }

    /** 아직 구현하지 않은 하위 명령(서버에 그 기능이 없다): /locate biome·poi. */
    private static final java.util.Map<String, List<String>> NOT_YET = java.util.Map.of("/locate", List.of("biome", "poi"));

    private static void compare(String path, JsonObject vanilla, List<Node> nodes, int idx) {
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
        assertEquals(vanilla.has("redirect"), n.redirect() >= 0, path + " 리다이렉트");
        JsonObject vc = vanilla.has("children") ? vanilla.getAsJsonObject("children") : new JsonObject();
        TreeSet<String> ours = new TreeSet<>();
        for (int c : n.children()) ours.add(nodes.get(c).name());
        TreeSet<String> expected = new TreeSet<>(vc.keySet());
        for (String m : NOT_YET.getOrDefault(path, List.of())) expected.remove(m);
        assertEquals(expected, ours, path + " 자식");
        for (int c : n.children()) compare(path + " " + nodes.get(c).name(), vc.getAsJsonObject(nodes.get(c).name()), nodes, c);
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
                compare("/" + name, vanilla.getAsJsonObject(name), nodes, c);
                checked++;
            }
            assertTrue(checked >= 18, "검사한 명령 수 " + checked);
            // 권한 0 이면 help·list·me·msg·random·tell·w 만(바닐라 random 은 requires 가 없다)
            List<Node> guest = decode(core.commands().treeFor(CommandSource.forProfile(core, "Guest", 0)), root);
            TreeSet<String> names = new TreeSet<>();
            for (int c : guest.get(root[0]).children()) names.add(guest.get(c).name());
            assertEquals(new TreeSet<>(List.of("help", "list", "me", "msg", "random", "tell", "w")), names);
        } finally {
            sched.close();
        }
    }
}
