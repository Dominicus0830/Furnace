// 어댑터(호환 계층)와 핵심의 의존 방향을 CI 시험으로 지킨다(T-249). 방향은 어댑터 → 공개 API → (서버 구현은 api.internal 다리로만 노출).
// 규칙 ① furnace-compat-paper 주 코드는 서버 구현(org.domi.furnace.server..)을 쓰지 않는다 → furnace-compat-paper 의 AdapterDirectionStaticTest
// 규칙 ② furnace-api 는 호환 계층·어댑터 이름(org.bukkit, io.papermc, net.minecraft, …)을 쓰지 않는다 → 이 시험
// 규칙 ③ api.internal.* 는 허용 목록의 서버 클래스만 쓴다(어댑터 전용 핵심 코드가 더 늘지 않게) → 이 시험
// 허용 목록은 줄어들기만 한다: 줄일 수 있게 되면 그 줄을 지우고, 새 줄을 더하려면 이유를 적어 검토를 받는다(docs/ops/adapter-boundary.md).
package org.domi.furnace.server.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;
import org.domi.furnace.api.plugin.PluginFormat;
import org.junit.jupiter.api.Test;

class AdapterBoundaryStaticTest {

    /** ② 공개 API 가 이름으로 가리키면 안 되는 어댑터·호환 패키지(내부 이름 + 점 표기). */
    private static final List<String> API_FORBIDDEN = List.of("org/bukkit/", "io/papermc/", "com/destroystokyo/", "org/spigotmc/", "net/minecraft/",
            "net/md_5/", "org/domi/furnace/compat/", "org/domi/furnace/server/",
            "org.bukkit.", "io.papermc.", "com.destroystokyo.", "org.spigotmc.", "net.minecraft.", "net.md_5.", "org.domi.furnace.compat.", "org.domi.furnace.server.");

    /**
     * ③ api.internal 을 이름으로 가리켜도 되는 서버 클래스(클래스 파일 상대 경로의 접두 — 안쪽 클래스 $ 는 바깥 클래스를 따른다).
     * 이 클래스들이 호환 어댑터(furnace-compat-paper)에게 서버 내부를 보여 주는 다리의 서버 쪽이다. 더 늘리지 않는다.
     */
    private static final Map<String, String> INTERNAL_ALLOWED = new TreeMap<>(Map.ofEntries(
            Map.entry("org/domi/furnace/server/plugin/BlockStateInternalsImpl", "api.internal.BlockStateInternals 서버 쪽 구현(다리)"),
            Map.entry("org/domi/furnace/server/plugin/ChunkInternalsImpl", "api.internal.ChunkInternals 서버 쪽 구현(다리)"),
            Map.entry("org/domi/furnace/server/plugin/FeatureInternalsImpl", "api.internal.FeatureInternals 서버 쪽 구현(다리)"),
            Map.entry("org/domi/furnace/server/plugin/InternalsImpl", "api.internal.Internals 진입점 서버 쪽 구현(다리)"),
            Map.entry("org/domi/furnace/server/plugin/ScratchWorldsImpl", "api.internal.ScratchWorlds 서버 쪽 구현(다리)"),
            Map.entry("org/domi/furnace/server/plugin/PluginHost", "서버가 다리 진입점(InternalsImpl)을 하나 들고 호환 어댑터에게 internals() 로 내준다"),
            Map.entry("org/domi/furnace/server/plugin/PlacementBridge", "NMS 놓기 어댑터 전용 다리 — 서버 놓기 규칙을 api.internal 로 보인다"),
            // 이식 다리(docs/roadmap/port-bridge-2026-11-27.md 12장, 도미니코 결정 11-28 안 "가" — docs/user-todo/entries/2026-11-27-port-bridge-allowlist.md):
            // 손으로 쓰는 다리 클래스는 이름으로, 생성 구현은 아래 INTERNAL_ALLOWED_GENERATED 접두로
            Map.entry("org/domi/furnace/server/port/PortGuards", "이식 다리 소유 검사 — 생성 구현의 첫 호출(NmsOwner 규칙을 서버 쪽으로 옮김) + level kind(주인 region 레벨)"),
            Map.entry("org/domi/furnace/server/port/PortKinds", "이식 다리 kind 바꾸기(block·random) — 중립 형식 → 서버 형식"),
            // 아래 셋은 구현 클래스가 아니라 핵심 자료구조가 어댑터를 위한 "읽기 보기" 자료형을 복사 없이 내주려고 API 인터페이스를 직접 구현한다 — 감싸면 읽기가 6~14% 느려져(3b 측정) 그대로 둔다(docs/ops/adapter-boundary-step3-plan.md). 거울 동기화(Region·ChunkColumn)는 3단계에서 훅(ChunkMirrorOps) 뒤로 옮겨 빠졌다.
            Map.entry("org/domi/furnace/server/world/ChunkSection", "ChunkInternals 읽기 보기용 접근(블록 묶음 복사본) — 감싸개 비용 때문에 남김(3b)"),
            Map.entry("org/domi/furnace/server/world/ChunkReadView", "ChunkInternals 읽기 보기 자료형(ChunkView 직접 구현) — 감싸개 비용 때문에 남김(3b)"),
            Map.entry("org/domi/furnace/server/world/PaletteStorage", "ChunkInternals 읽기 보기용 팔레트 접근(PackedStates 직접 구현, 복사 없음) — 감싸개 비용 때문에 남김(3b)")));

    /**
     * ② 의 예외: 상수 풀에 어댑터 이름이 "자료"로 들어 있는 API 클래스(클래스 파일 상대 경로 접두). 어댑터 클래스를 부르지 않는다.
     * PacketSchema 는 중립 패킷 스키마의 바닐라 패킷 패키지 이름("net.minecraft.network.protocol.")을 글자로 가진다 — 호환 계층이 빌드 때 shim 소스를 만드는 입력이다.
     */
    private static final Map<String, String> API_NAME_AS_DATA = Map.of(
            "org/domi/furnace/api/packet/schema/PacketSchema", "패킷 스키마의 바닐라 패킷 패키지 이름(자료) — 클래스 참조가 아니다");

    /**
     * ③ 의 생성물 접두(안 "가"): 이식 생성기(portgen)가 만든 구현만 있는 패키지. 손 코드가 섞이지 않았는지
     * {@link #generatedPortPackageHoldsOnlyGeneratedClasses} 가 본다(생성기·명세는 따로 독립 검토).
     */
    private static final Map<String, String> INTERNAL_ALLOWED_GENERATED = Map.of(
            "org/domi/furnace/server/port/gen/", "이식 다리 생성 구현(.ports → <Area>PortImpl) — 생성기 출력만, 손 코드 금지");

    private static final String INTERNAL = "org/domi/furnace/api/internal/";

    @Test
    void apiNeverNamesAnAdapterOrCompatClass() throws Exception {
        List<String> offenders = new ArrayList<>();
        int scanned = scan(PluginFormat.class, (name, bytes) -> {
            if (API_NAME_AS_DATA.keySet().stream().anyMatch(a -> name.replace('\\', '/').startsWith(a))) return;
            String text = new String(bytes, StandardCharsets.ISO_8859_1);
            for (String f : API_FORBIDDEN) {
                if (text.contains(f)) {
                    offenders.add(name + " → " + f);
                    return;
                }
            }
        });
        assertTrue(scanned > 100, "스캔한 API 클래스가 너무 적다(" + scanned + ") — 위치를 못 찾았을 수 있다");
        assertEquals(List.of(), offenders, "furnace-api 가 어댑터·호환 이름을 가리킨다 — 어댑터 이름은 호환 계층에만 있어야 한다");
    }

    @Test
    void apiInternalIsUsedOnlyByAllowedServerClasses() throws Exception {
        List<String> offenders = new ArrayList<>();
        TreeSet<String> seen = new TreeSet<>();
        int scanned = scan(PluginBootstrapper.class, (name, bytes) -> {
            if (!new String(bytes, StandardCharsets.ISO_8859_1).contains(INTERNAL)) return;
            String outer = name.replace('\\', '/').replaceAll("\\.class$", "");
            int dollar = outer.indexOf('$');
            if (dollar >= 0) outer = outer.substring(0, dollar);
            String gen = INTERNAL_ALLOWED_GENERATED.keySet().stream().filter(outer::startsWith).findFirst().orElse(null);
            if (INTERNAL_ALLOWED.containsKey(outer)) seen.add(outer);
            else if (gen != null) seen.add(gen);
            else offenders.add(name);
        });
        assertTrue(scanned > 1000, "스캔한 서버 클래스가 너무 적다(" + scanned + ")");
        assertEquals(List.of(), offenders, "허용 목록 밖의 서버 클래스가 api.internal 을 쓴다 — 어댑터 전용 핵심 코드를 늘리지 않는다(docs/ops/adapter-boundary.md)");
        // 목록이 줄어들기만 하게: 더는 쓰지 않는 항목은 지워야 한다
        TreeSet<String> stale = new TreeSet<>(INTERNAL_ALLOWED.keySet());
        stale.addAll(INTERNAL_ALLOWED_GENERATED.keySet());
        stale.removeAll(seen);
        assertEquals(new TreeSet<String>(), stale, "허용 목록에 있지만 이제 api.internal 을 쓰지 않는 클래스 — 목록에서 지운다");
    }

    /**
     * 생성물 접두 패키지에는 생성기 출력만: (1) 서버 소스 어디에도 그 패키지를 선언한 손 소스가 없고, (2) 그 패키지의 바깥 클래스는 이식 명세 파일마다
     * 하나씩 생기는 {@code <Area>PortImpl} 과 정확히 같으며, (3) 모두 @PortGenerated 를 단다.
     */
    @Test
    void generatedPortPackageHoldsOnlyGeneratedClasses() throws Exception {
        Path root = repoRoot();
        for (String prefix : INTERNAL_ALLOWED_GENERATED.keySet()) {
            String pkg = "package " + prefix.substring(0, prefix.length() - 1).replace('/', '.') + ";";
            List<String> hand = new ArrayList<>();
            try (Stream<Path> s = Files.walk(root.resolve("furnace-server/src"))) {
                for (Path f : (Iterable<Path>) s.filter(x -> x.toString().endsWith(".java"))::iterator) {
                    if (Files.readString(f, StandardCharsets.UTF_8).contains(pkg)) hand.add(root.relativize(f).toString());
                }
            }
            assertEquals(List.of(), hand, "손 소스가 생성물 패키지를 선언한다 — 생성 구현만 둔다(docs/roadmap/port-bridge-2026-11-27.md 12장)");
        }
        // 명세 파일 이름 → 생성 클래스 이름(PortSpec.camel 과 같은 규칙: 번호 뒤 이름을 - 로 나눠 첫 글자 대문자)
        TreeSet<String> expected = new TreeSet<>();
        String mc = org.domi.furnace.api.GameVersion.MINECRAFT;
        Path specs = root.resolve("furnace-compat-paper/src/shimspec/ports-" + mc);
        if (Files.isDirectory(specs)) {
            try (Stream<Path> s = Files.list(specs)) {
                for (Path f : (Iterable<Path>) s.filter(x -> x.toString().endsWith(".ports"))::iterator) {
                    boolean hasMember = Files.readAllLines(f, StandardCharsets.UTF_8).stream().anyMatch(l -> l.startsWith("  ") && l.contains("->"));
                    if (!hasMember) continue;
                    String name = f.getFileName().toString().replaceAll("^\\d+-|\\.ports$", "");
                    StringBuilder camel = new StringBuilder();
                    for (String part : name.split("-")) if (!part.isEmpty()) camel.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
                    expected.add("org/domi/furnace/server/port/gen/" + camel + "PortImpl");
                }
            }
        }
        TreeSet<String> found = new TreeSet<>();
        List<String> unmarked = new ArrayList<>();
        scan(PluginBootstrapper.class, (name, bytes) -> {
            String n = name.replace('\\', '/');
            if (INTERNAL_ALLOWED_GENERATED.keySet().stream().noneMatch(n::startsWith)) return;
            if (n.contains("$")) return;   // 람다·안쪽 클래스는 바깥 클래스를 따른다
            found.add(n.replaceAll("\\.class$", ""));
            if (!new String(bytes, StandardCharsets.ISO_8859_1).contains("Lorg/domi/furnace/api/internal/port/PortGenerated;")) unmarked.add(n);
        });
        assertTrue(!found.isEmpty(), "생성 구현이 하나도 없다 — 이식 명세(furnace-compat-paper/src/shimspec/ports-<판>)가 비었으면 접두 줄을 지운다");
        assertEquals(expected, found, "생성물 패키지의 클래스가 이식 명세 파일마다 하나씩 생기는 <Area>PortImpl 과 다르다");
        assertEquals(List.of(), unmarked, "생성물 패키지에 @PortGenerated 없는 클래스");
    }

    @Test
    void knownStringSplitHolesStillExistWithTheirReason() throws IOException {
        // 클래스 파일 검사는 상수 풀의 글자만 본다. 이름을 이어 붙여 피하는 곳은 검사에 안 잡히므로 여기에 이유와 함께 적고, 사라지면 줄을 지운다.
        Map<String, String> holes = Map.of(
                "furnace-server/src/main/java/org/domi/furnace/server/log/ConsolePrefixConverter.java",
                "로거 이름 접두(org.bukkit. 등)를 콘솔 접두 판단에만 쓴다 — 클래스를 부르지 않는다(이어 붙여 CompatIsolationStaticTest 를 피함)");
        Path root = repoRoot();
        for (var e : holes.entrySet()) {
            Path f = root.resolve(e.getKey());
            assertTrue(Files.exists(f), e.getKey() + " 가 없다 — 목록에서 지운다");
            assertTrue(Files.readString(f).contains("pkg(\"org\", \"bukkit\")"), e.getKey() + " 가 더는 이어 붙이지 않는다 — 목록에서 지운다");
        }
    }

    // ---- 어댑터가 꽂는 훅이 한 곳에 모여 있는가(권장 2단계) ----
    // 클래스 파일 상수 풀로는 "어느 메서드를 불렀나"를 파일 단위로만 볼 수 있어 원문을 훑는다. 줄은 줄어들기만 한다.

    /** 통로 모드(플러그인 레벨을 통한 배치) 분기를 직접 묻는 곳 — 훅(WriteSink·EdgeUpdater)을 받는 FeatureWorldView 밖에서는 템플릿 배치 엔진뿐. 그 밖은 FeatureWorldView 의 뜻 이름 메서드를 부른다. */
    private static final Map<String, String> SINK_MODE_CALLERS = Map.of(
            "world/gen/structure/template/TemplatePlacer.java", "템플릿 배치 엔진 — Paper 가 감싸개를 벗긴 레벨로 쓰는 차례(방벽·블록 엔티티·모양 갱신)",
            "world/block/ShapeUpdater.java", "템플릿 가장자리 모양 갱신 — 통로 모드면 가장자리 갱신기 훅으로");

    @Test
    void sinkModeStaysInPlacementEngine() throws IOException {
        assertEquals(new TreeSet<>(SINK_MODE_CALLERS.keySet()), serverSourcesContaining(".sinkMode()", null),
                "통로 모드 분기가 퍼졌다(또는 줄었다) — 월드젠 코드는 FeatureWorldView 의 pluginLevel* 메서드를 부른다(docs/ops/adapter-boundary.md)");
    }

    /** 연결의 패킷 가로채기 지점(PacketTap.KEY)을 만지는 곳 — 꽂는 쪽 PacketServiceImpl, 보내는 쪽 PacketTaps, 받는 쪽 ConnectionHandler 하나씩. */
    private static final Map<String, String> PACKET_TAP_READERS = Map.of(
            "plugin/packet/PacketServiceImpl.java", "플러그인 패킷 API 가 연결에 가로채기 지점을 꽂는 곳",
            "net/PacketTaps.java", "보내는 패킷이 가로채기 지점을 거치는 한 곳(리스너 안에서 보낸 패킷 순서 포함)",
            "net/ConnectionHandler.java", "받는 패킷이 가로채기 지점을 거치는 한 곳");

    @Test
    void packetTapIsCalledFromOnePlacePerDirection() throws IOException {
        assertEquals(new TreeSet<>(PACKET_TAP_READERS.keySet()), serverSourcesContaining("PacketTap.KEY", null),
                "패킷 가로채기 지점을 부르는 곳이 늘었다(또는 줄었다) — 보내기는 PacketTaps 를 거친다(docs/ops/adapter-boundary.md)");
    }

    @Test
    void publicApiHasNoAdapterOnlyMember() throws IOException {
        // 어댑터만 쓰는 통로는 api.internal(불안정 다리)에 둔다 — 공개 API(MIT 표면)에 "호환 어댑터 전용" 항목을 두지 않는다.
        // 구조 검사가 아니라 문구 검사다: 그 표시를 단 항목만 잡는다(다른 말로 적은 항목은 사람이 검토에서 본다 — docs/ops/adapter-boundary.md)
        Path api = repoRoot().resolve("furnace-api/src/main/java/org/domi/furnace/api");
        List<String> found = new ArrayList<>();
        try (Stream<Path> s = Files.walk(api)) {
            for (Path f : (Iterable<Path>) s.filter(x -> x.toString().endsWith(".java"))::iterator) {
                if (api.relativize(f).toString().replace('\\', '/').startsWith("internal/")) continue;
                if (Files.readString(f).contains("호환 어댑터 전용")) found.add(api.relativize(f).toString());
            }
        }
        assertEquals(List.of(), found, "공개 API 에 호환 어댑터 전용 항목이 있다 — api.internal 로 옮긴다");
    }

    /** 서버 주 소스(org/domi/furnace/server 아래 상대 경로) 가운데 needle 을 가진 파일. ignoreLine 을 가진 줄은 세지 않는다. */
    private static TreeSet<String> serverSourcesContaining(String needle, String ignoreLine) throws IOException {
        Path src = repoRoot().resolve("furnace-server/src/main/java/org/domi/furnace/server");
        TreeSet<String> out = new TreeSet<>();
        try (Stream<Path> s = Files.walk(src)) {
            for (Path f : (Iterable<Path>) s.filter(x -> x.toString().endsWith(".java"))::iterator) {
                for (String line : Files.readAllLines(f)) {
                    if (line.contains(needle) && (ignoreLine == null || !line.contains(ignoreLine))) {
                        out.add(src.relativize(f).toString().replace('\\', '/'));
                        break;
                    }
                }
            }
        }
        return out;
    }

    private static Path repoRoot() {
        Path p = Path.of("").toAbsolutePath();
        while (p != null && !Files.exists(p.resolve("settings.gradle.kts"))) p = p.getParent();
        assertTrue(p != null, "저장소 루트를 못 찾았다");
        return p;
    }

    private interface Visitor {
        void visit(String name, byte[] bytes);
    }

    private static int scan(Class<?> anchor, Visitor v) throws IOException, URISyntaxException {
        Path root = Path.of(anchor.getProtectionDomain().getCodeSource().getLocation().toURI());
        int[] n = {0};
        if (Files.isDirectory(root)) {
            try (Stream<Path> s = Files.walk(root)) {
                for (Path p : (Iterable<Path>) s.filter(f -> f.toString().endsWith(".class"))::iterator) {
                    v.visit(root.relativize(p).toString(), Files.readAllBytes(p));
                    n[0]++;
                }
            }
        } else {
            try (JarFile jar = new JarFile(root.toFile())) {
                for (JarEntry e : (Iterable<JarEntry>) jar.stream()::iterator) {
                    if (!e.getName().endsWith(".class")) continue;
                    try (InputStream in = jar.getInputStream(e)) {
                        v.visit(e.getName(), in.readAllBytes());
                    }
                    n[0]++;
                }
            }
        }
        return n[0];
    }
}
