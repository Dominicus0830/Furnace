package org.domi.furnace.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipFile;
import org.domi.furnace.api.GameVersion;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.ItemRegistry;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * T-238 — 바닐라 데이터 생성기 보고서(`--reports`)와 "Furnace 코드가 실제로 쓰는 표"를 직접 맞댄다: 버전 상수·패킷 번호·블록 상태 번호·아이템·엔티티·입자
 * 레지스트리·명령 트리 정답 파일. 보고서는 바닐라 서버 jar 로 만든 것이라 저장소에 없다 — 개발자·CI 컴퓨터의 vanilla-extract 캐시에서 읽는다.
 *
 * <p>평소 {@code :furnace-server:test} 에는 들어가지 않는다(태그 {@code vanilla-reports} 제외). {@code ./gradlew :furnace-server:vanillaReportsTest} 가
 * 보고서를 만든(캐시가 있으면 재사용) 뒤 이 시험만 돌린다. 보고서가 없으면 건너뛰지 않고 실패한다 — "비교하지 않았는데 초록"을 막는다(AGENTS.md 규칙 5).
 * 차이가 나오면 시험을 고치지 말고 그 차이마다 FIX 과제로 만든다.
 */
@Tag("vanilla-reports")
class VanillaReportsParityTest {

    private static Path reports() {
        String p = System.getProperty("furnace.vanillaReports");
        if (p == null || p.isBlank()) fail("-Dfurnace.vanillaReports 가 없다 — ./gradlew :furnace-server:vanillaReportsTest 로 돌린다(보고서를 만든다)");
        Path dir = Path.of(p);
        assertTrue(Files.isDirectory(dir), "보고서 폴더가 없다: " + dir);
        return dir;
    }

    private static JsonObject json(String name) throws IOException {
        try (BufferedReader r = Files.newBufferedReader(reports().resolve(name), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(r).getAsJsonObject();
        }
    }

    private static List<String> resourceLines(String path) throws IOException {
        try (InputStream in = VanillaReportsParityTest.class.getClassLoader().getResourceAsStream(path)) {
            assertNotNull(in, path);
            List<String> out = new ArrayList<>();
            for (String l : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) if (!l.isBlank() && !l.startsWith("#")) out.add(l.strip());
            return out;
        }
    }

    /** 보고서는 `<캐시>/<판>/generated/reports` 에 있고 번들러 jar 는 `<캐시>/<판>/server.jar` — 그 안의 version.json 이 판 상수의 정답이다. */
    @Test
    void gameVersionConstantsMatchTheServerJarVersionJson() throws IOException {
        Path jar = reports().getParent().getParent().resolve("server.jar");
        assertTrue(Files.isRegularFile(jar), "번들러 jar 가 없다: " + jar);
        try (ZipFile z = new ZipFile(jar.toFile());
             var in = new InputStreamReader(z.getInputStream(z.getEntry("version.json")), StandardCharsets.UTF_8)) {
            JsonObject v = JsonParser.parseReader(in).getAsJsonObject();
            assertEquals(GameVersion.MINECRAFT, v.get("id").getAsString(), "판 이름");
            assertEquals(GameVersion.PROTOCOL, v.get("protocol_version").getAsInt(), "프로토콜 번호");
            assertEquals(GameVersion.DATA_VERSION, v.get("world_version").getAsInt(), "월드 데이터 버전");
            assertEquals(GameVersion.PACK_FORMAT_DATA, v.getAsJsonObject("pack_version").get("data").getAsInt(), "데이터 팩 형식");
        }
    }

    /** packets.json 의 상태·방향·이름·번호가 PacketIds 의 상수 이름·값과 양쪽 모두 같다(빠진 것도 남는 것도 없다). */
    @Test
    void packetIdsMatchPacketsJson() throws Exception {
        Map<String, Integer> expected = new TreeMap<>();
        for (var state : json("packets.json").entrySet()) {
            for (var dir : state.getValue().getAsJsonObject().entrySet()) {
                for (var pkt : dir.getValue().getAsJsonObject().entrySet()) {
                    String name = pkt.getKey().substring(pkt.getKey().indexOf(':') + 1);
                    String c = (state.getKey() + "_" + dir.getKey() + "_" + name).toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "_");
                    assertTrue(expected.put(c, pkt.getValue().getAsJsonObject().get("protocol_id").getAsInt()) == null, "상수 이름이 겹친다: " + c);
                }
            }
        }
        Map<String, Integer> ours = new TreeMap<>();
        for (Field f : Class.forName("org.domi.furnace.server.net.protocol.PacketIds").getDeclaredFields()) {
            if (Modifier.isPublic(f.getModifiers()) && Modifier.isStatic(f.getModifiers()) && f.getType() == int.class) ours.put(f.getName(), f.getInt(null));
        }
        assertEquals(expected, ours);
    }

    /** blocks.json 의 모든 블록·상태 번호·기본 상태가 BlockRegistry 와 같다(상태 키는 속성 이름·값을 포함한다). */
    @Test
    void blockStatesMatchBlocksJson() throws IOException {
        BlockRegistry reg = BlockRegistry.load("blocks/" + GameVersion.RESOURCE_TAG + ".txt");
        JsonObject blocks = json("blocks.json");
        int states = 0;
        for (var b : blocks.entrySet()) {
            String name = b.getKey();
            for (JsonElement se : b.getValue().getAsJsonObject().getAsJsonArray("states")) {
                JsonObject s = se.getAsJsonObject();
                int id = s.get("id").getAsInt();
                Map<String, String> props = new TreeMap<>();
                if (s.has("properties")) for (var p : s.getAsJsonObject("properties").entrySet()) props.put(p.getKey(), p.getValue().getAsString());
                String key = org.domi.furnace.server.world.BlockStateKey.of(name, props);
                assertEquals(id, reg.exactStateId(key), "상태 번호 " + key);
                assertEquals(key, reg.canonicalKeyOf(id), "번호 " + id);
                if (s.has("default") && s.get("default").getAsBoolean()) assertEquals(id, reg.defaultStateId(name), "기본 상태 " + name);
                states++;
            }
        }
        assertEquals(states, reg.totalStateCount(), "상태 총수");
        assertEquals(blocks.keySet(), reg.blockNames(), "블록 이름 집합");
    }

    /** registries.json 의 item 번호표가 ItemRegistry 와, entity_type·particle_type 순서가 Furnace 의 이름표 파일과 같다. */
    @Test
    void registriesMatchRegistriesJson() throws IOException {
        JsonObject regs = json("registries.json");
        ItemRegistry items = ItemRegistry.load("items/" + GameVersion.RESOURCE_TAG + ".txt");
        JsonObject itemEntries = regs.getAsJsonObject("minecraft:item").getAsJsonObject("entries");
        for (var e : itemEntries.entrySet()) {
            assertEquals(e.getValue().getAsJsonObject().get("protocol_id").getAsInt(), items.idOf(e.getKey()), "아이템 " + e.getKey());
        }
        int known = 0;
        for (int i = 0; i < itemEntries.size() + 8; i++) if (items.isValid(i)) known++;
        assertEquals(itemEntries.size() - (itemEntries.has("minecraft:air") ? 1 : 0), known, "아이템 개수(공기 제외)");
        for (String reg : List.of("minecraft:entity_type", "minecraft:particle_type")) {
            JsonObject entries = regs.getAsJsonObject(reg).getAsJsonObject("entries");
            String[] byId = new String[entries.size()];
            for (var e : entries.entrySet()) byId[e.getValue().getAsJsonObject().get("protocol_id").getAsInt()] = e.getKey();
            String file = reg.equals("minecraft:entity_type") ? "entity_types" : "particle_types";
            List<String> ours = new ArrayList<>(resourceLines("registries/" + file + "-" + GameVersion.RESOURCE_TAG + ".txt"));
            if (reg.equals("minecraft:entity_type")) ours.sort(null);   // 엔티티 이름표는 번호 순서가 아니라 이름순 집합이다(EntityTypes)
            List<String> expected = new ArrayList<>(List.of(byId));
            if (reg.equals("minecraft:entity_type")) expected.sort(null);
            assertEquals(expected, ours, reg);
        }
    }

    /** 명령 트리 정답 파일(VanillaCommandTreeTest 가 Furnace 트리와 맞대는 것)이 지금 바닐라 jar 의 commands.json 과 같다 — 낡은 정답 파일을 잡는다. */
    @Test
    void commandTreeFixtureEqualsCommandsJson() throws IOException {
        JsonObject fixture;
        try (InputStream in = VanillaReportsParityTest.class.getResourceAsStream("/commands/" + GameVersion.RESOURCE_TAG + "/vanilla-commands.json.gz");
             var r = new InputStreamReader(new GZIPInputStream(in), StandardCharsets.UTF_8)) {
            fixture = JsonParser.parseReader(r).getAsJsonObject();
        }
        assertEquals(json("commands.json"), fixture);
    }
}
