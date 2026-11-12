// Paper 가 서버 jar 에 싣고 있어 Paper/Bukkit 플러그인이 libraries 선언 없이 기대는 라이브러리 목록(H4 묶음 B, 사용자 결정 4·5).
// plugins/bukkit-plugins/ 에 그 라이브러리를 쓰는 플러그인이 있을 때만 준비해 공유 로더(SharedLibraryLoader)에 붙인다.
//  - Guava(+failureaccess)·SnakeYAML 은 서버 jar 에 jar 그대로 싣는다(bundled, 사용자 결정 5 정정 2026-11-12). 내려받지 않고 서버 jar 에서 캐시로 꺼낸다.
//    패키지 이름은 바꾸지 않는다(재배치 없음 — 플러그인이 com.google.common·org.yaml.snakeyaml 을 그대로 쓴다). 서버 로더에는 풀지 않는다.
//    서버 jar 에 없으면(gradle 개발 실행·시험) 예전처럼 Maven Central 에서 받는다. 나머지(Adventure 포함)는 필요할 때 받는다.
//  - 버전·sha256 은 코드에 고정한다. 바닐라 1.21.1 서버 jar 의 META-INF/libraries.list 에 있는 것(Guava·fastutil·JOML·commons-*·log4j-api,
//    그리고 Mojang 저장소에만 있는 Brigadier·authlib·DataFixerUpper)은 Mojang 이 적은 sha256 을 그대로 쓰고, 나머지(Adventure·SnakeYAML·BungeeChat 등)는
//    Maven Central 의 jar 를 받아 잰 값이다(2026-11-08). 다른 바이트는 받지도 쓰지도 않는다.
//  - 저장소는 Maven Central, Mojang 라이브러리 3개만 libraries.minecraft.net(plugins.compat.libraries.mojang=false 면 빼고 경고).
//  - 묶음(Group)마다 "이 패키지 이름이 플러그인 클래스에 보이면 필요하다"는 표지가 있다. needed 모드는 그 표지가 보이는 묶음만 받는다.
package org.domi.furnace.server.plugin.library;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.domi.furnace.api.plugin.PluginDescription.Library;

public final class ServerLibraries {

    /** 바닐라·Paper 가 Mojang 라이브러리를 받는 저장소. */
    public static final URI MOJANG = URI.create("https://libraries.minecraft.net/");

    public enum Repo {
        CENTRAL("Maven Central"), MOJANG("Mojang 라이브러리 저장소(libraries.minecraft.net)");

        public final String label;

        Repo(String label) { this.label = label; }
    }

    /**
     * jar 하나: 좌표·고정 sha256·저장소. bundled 면 서버 jar 의 {@value #BUNDLE_DIR} 에 jar 그대로 실려 있어 내려받지 않고 꺼낸다
     * (서버 jar 에 없을 때만 repo 에서 받는다).
     */
    public record Artifact(Library library, Repo repo, boolean bundled) {
        public Artifact(String coordinate, String sha256, Repo repo) { this(new Library(coordinate, sha256), repo, false); }

        public Artifact(String coordinate, String sha256, Repo repo, boolean bundled) { this(new Library(coordinate, sha256), repo, bundled); }

        /** 서버 jar 안 자원 이름: {@code META-INF/furnace/libraries/<아티팩트>-<버전>.jar}. */
        public String bundleResource() { return BUNDLE_DIR + library.artifact() + "-" + library.version() + ".jar"; }

        public String coordinate() { return library.coordinate(); }

        public String sha256() { return library.sha256(); }
    }

    /**
     * 함께 받는 jar 묶음. markers 중 하나라도 플러그인 클래스 파일에 보이면(슬래시·점 표기 모두) 필요한 것으로 본다. requires 는 이 묶음이 기대는 다른 묶음.
     */
    public record Group(String id, String description, List<Artifact> artifacts, List<String> markers, List<String> requires) {
        public Group {
            artifacts = List.copyOf(artifacts);
            markers = List.copyOf(markers);
            requires = List.copyOf(requires);
        }

        public boolean mojang() { return artifacts.stream().anyMatch(a -> a.repo() == Repo.MOJANG && !a.bundled()); }

        /** 모든 jar 가 서버 jar 에 실려 있다. */
        public boolean bundled() { return artifacts.stream().allMatch(Artifact::bundled); }
    }

    /** 서버 jar 에 실린 라이브러리 jar 자리(fatJar 가 채운다). */
    public static final String BUNDLE_DIR = "META-INF/furnace/libraries/";
    /** 서버 jar 에 실려 있다(bundled). */
    private static final boolean B = true;

    private static final Repo C = Repo.CENTRAL;
    private static final Repo M = Repo.MOJANG;

    /** 기본 목록(1.21.1). 순서는 로그·문서 순서다. */
    public static final ServerLibraries DEFAULT = new ServerLibraries(List.of(
            new Group("guava", "Google Guava", List.of(
                    new Artifact("com.google.guava:guava:32.1.2-jre", "bc65dea7cfd9e4dacf8419d8af0e741655857d27885bb35d943d7187fc3a8fce", C, B),
                    new Artifact("com.google.guava:failureaccess:1.0.1", "a171ee4c734dd2da837e4b16be9df4661afab72a41adaf31eb84dfdaf936ca26", C, B)),
                    List.of("com/google/common/", "com/google/thirdparty/"), List.of()),
            new Group("adventure", "Kyori Adventure(API·MiniMessage·직렬화기)", List.of(
                    new Artifact("net.kyori:adventure-api:4.17.0", "15c8c2eb1a69d8b1bc914f554353da8ee7cf074c05c8074da9898aee5c70d0d8", C),
                    new Artifact("net.kyori:adventure-key:4.17.0", "8e5cf570612d6ccedf943ac1716a449de2dd5a90207b7de6f73c0236935b766e", C),
                    new Artifact("net.kyori:examination-api:1.3.0", "c9237ffecb05428f6eff86216246ac70ce0b47b04c08ea7ca35020fde57f8492", C),
                    new Artifact("net.kyori:examination-string:1.3.0", "7d01fc25a4bb3af0e1662685455f4541fbf4626216ea5846e455c1491e156b8c", C),
                    new Artifact("net.kyori:option:1.0.0", "2bde5a7a2d73fa1166a0f1663a22c3af7d2768ffc4fea331774c3c0f62225d11", C),
                    new Artifact("net.kyori:adventure-text-minimessage:4.17.0", "8372a9e9bc0c0ae21d60e6ce6e284a81c6c96d1ec398f808825015c6f60252f5", C),
                    new Artifact("net.kyori:adventure-text-serializer-json:4.17.0", "6c91d3e23b064d76ab7c154ebe940f552232ae178b95d21b50dfdcfec2925d96", C),
                    new Artifact("net.kyori:adventure-text-serializer-gson:4.17.0", "6227d6bfd2d6ae6d121d3dcda590d33ff8373cea6589485df3f7b75b09e7d210", C),
                    new Artifact("net.kyori:adventure-text-serializer-legacy:4.17.0", "9d4955542e40eb5b41dbed172b2826acf1cbe3405e002f3360eb2ea578d536ba", C),
                    new Artifact("net.kyori:adventure-text-serializer-plain:4.17.0", "226d4a3d2c42b7727df11a02a698c6c64475384705dd12bf02c7f69744799c2e", C),
                    new Artifact("net.kyori:adventure-text-serializer-ansi:4.17.0", "d0dcd946dc2dc858621a31914643f3c06817d4c38ad52c235ec147e565d8e24c", C),
                    new Artifact("net.kyori:ansi:1.0.3", "26239e8fd349d6a166d7c73623518b44dbac819b80530546724cb20ee5e38243", C),
                    new Artifact("net.kyori:adventure-text-logger-slf4j:4.17.0", "d3e39084845e030de1e14c1859919f8e7a29341282b364ee9b188a8f2f03992b", C)),
                    List.of("net/kyori/adventure/", "net/kyori/examination/"), List.of()),
            new Group("snakeyaml", "SnakeYAML", List.of(
                    new Artifact("org.yaml:snakeyaml:2.2", "1467931448a0817696ae2805b7b8b20bfb082652bf9c4efaed528930dc49389b", C, B)),
                    List.of("org/yaml/snakeyaml/"), List.of()),
            new Group("bungeecord-chat", "BungeeCord Chat(Spigot 채팅 컴포넌트)", List.of(
                    new Artifact("net.md-5:bungeecord-chat:1.20-R0.2", "5ea54c6a429331cc535f0bf912ce2aa1853b2f35e68e35d1ff3ef5e226e90596", C)),
                    // net.md_5.bungee.api.ChatColor(헥스 색) 만 쓰는 플러그인도 많다 — api/ 전체를 표지로. ChatColor·ComponentBuilder 등이 Guava Preconditions 를 쓴다
                    List.of("net/md_5/bungee/api/", "net/md_5/bungee/chat/"), List.of("guava")),
            new Group("json-simple", "json-simple", List.of(
                    new Artifact("com.googlecode.json-simple:json-simple:1.1.1", "4e69696892b88b41c55d49ab2fdcc21eead92bf54acc588c0050596c3b75199c", C)),
                    List.of("org/json/simple/"), List.of()),
            new Group("joml", "JOML", List.of(
                    new Artifact("org.joml:joml:1.10.5", "cac9f22f83a7aa33eebda73c16ff5261e3cb4911b6bafcf4c79ea486099d0c9a", C)),
                    List.of("org/joml/"), List.of()),
            new Group("fastutil", "fastutil", List.of(
                    new Artifact("it.unimi.dsi:fastutil:8.5.12", "b5543ee08d062d551cf0a5c9bc0fb70588b0382079029ba48941fa9c9be8a5d4", C)),
                    List.of("it/unimi/dsi/fastutil/"), List.of()),
            new Group("commons-lang3", "Apache Commons Lang 3", List.of(
                    new Artifact("org.apache.commons:commons-lang3:3.14.0", "7b96bf3ee68949abb5bc465559ac270e0551596fa34523fddf890ec418dde13c", C)),
                    List.of("org/apache/commons/lang3/"), List.of()),
            new Group("commons-io", "Apache Commons IO", List.of(
                    new Artifact("commons-io:commons-io:2.15.1", "a58af12ee1b68cfd2ebb0c27caef164f084381a00ec81a48cc275fd7ea54e154", C)),
                    List.of("org/apache/commons/io/"), List.of()),
            new Group("log4j", "Log4j API(서버 로그로 보내는 slf4j 다리 포함)", List.of(
                    new Artifact("org.apache.logging.log4j:log4j-api:2.22.1", "5d7beae7ff15d8516d6517121d7f12a79a6ac180df64b5fcec55d5be21056e53", C),
                    new Artifact("org.apache.logging.log4j:log4j-to-slf4j:2.22.1", "85a84842958e58dd8a89a3acf901a64b774704ce1511ece9f8eef70724884f92", C)),
                    List.of("org/apache/logging/log4j/"), List.of()),
            new Group("brigadier", "Mojang Brigadier", List.of(
                    new Artifact("com.mojang:brigadier:1.3.10", "c8ee4136e474ac7723ca2b432ec8d1a2bc88ef7d1ec57c314ba9e33cdc83dd75", M)),
                    List.of("com/mojang/brigadier/"), List.of()),
            new Group("authlib", "Mojang authlib(GameProfile·Property)", List.of(
                    new Artifact("com.mojang:authlib:6.0.54", "319ea7b53b5e52f62ad3e2b81e9db7f0751240edac548bd74f5f19e35dc21a3b", M)),
                    List.of("com/mojang/authlib/"), List.of("guava", "commons-io", "commons-lang3")),
            new Group("datafixerupper", "Mojang DataFixerUpper(Codec·DynamicOps)", List.of(
                    new Artifact("com.mojang:datafixerupper:8.0.16", "ffc138bc2596c291781b0d5e211ccac51f0f2345f27fc2742f335cedf7e2870d", M)),
                    List.of("com/mojang/serialization/", "com/mojang/datafixers/"), List.of("guava", "fastutil"))));

    private final Map<String, Group> groups = new LinkedHashMap<>();

    public ServerLibraries(List<Group> groups) {
        for (Group g : groups) {
            if (this.groups.putIfAbsent(g.id(), g) != null) throw new IllegalArgumentException("묶음 이름이 겹친다: " + g.id());
        }
        for (Group g : groups) {
            for (String r : g.requires()) if (!this.groups.containsKey(r)) throw new IllegalArgumentException(g.id() + " 가 없는 묶음 " + r + " 를 요구한다");
        }
    }

    public Collection<Group> groups() { return groups.values(); }

    public Group group(String id) { return groups.get(id); }

    /** ids 와 그것들이 요구하는 묶음까지(목록 순서). 모르는 이름은 무시한다. */
    public List<Group> closure(Collection<String> ids) {
        Set<String> want = new LinkedHashSet<>();
        ArrayList<String> stack = new ArrayList<>(ids);
        while (!stack.isEmpty()) {
            String id = stack.remove(stack.size() - 1);
            Group g = groups.get(id);
            if (g == null || !want.add(id)) continue;
            stack.addAll(g.requires());
        }
        List<Group> out = new ArrayList<>();
        for (Group g : groups.values()) if (want.contains(g.id())) out.add(g);
        return out;
    }

    /**
     * 플러그인 jar 의 클래스 파일에 표지가 보이는 묶음 이름(목록 순서). 상수 풀의 클래스 이름(슬래시)과 리플렉션 문자열(점) 모두 센다.
     * skip 에 든 묶음(부트스트래퍼가 이미 준비를 시도한 것)은 다시 보지 않는다. jar 안에 든 jar(LuckPerms 의 {@code luckperms-bukkit.jarinjar}
     * 처럼 플러그인이 자기 로더로 여는 것, 이름이 .jar·.jarinjar)도 {@value #MAX_NESTED_DEPTH}단계까지 같이 훑는다 — 바깥 로더 클래스에는
     * 표지가 없고 안쪽 클래스가 Paper 가 싣는 Guava 를 바로 쓴다.
     */
    static final int MAX_NESTED_DEPTH = 2;

    private static boolean isNestedJar(String name) { return name.endsWith(".jar") || name.endsWith(".jarinjar"); }

    private static void scanNested(InputStream raw, List<Group> todo, List<String[]> needles, Set<String> found, int depth) throws IOException {
        java.util.zip.ZipInputStream zin = new java.util.zip.ZipInputStream(raw);   // 바깥 스트림은 부른 쪽이 닫는다
        java.util.zip.ZipEntry e;
        while (found.size() < todo.size() && (e = zin.getNextEntry()) != null) {
            if (e.isDirectory()) continue;
            if (e.getName().endsWith(".class")) match(zin.readAllBytes(), todo, needles, found);
            else if (depth < MAX_NESTED_DEPTH && isNestedJar(e.getName())) scanNested(new java.io.FilterInputStream(zin) {
                @Override public void close() {}   // 안쪽 jar 를 다 읽어도 바깥 zip 스트림은 열어 둔다
            }, todo, needles, found, depth + 1);
        }
    }

    private static void match(byte[] classBytes, List<Group> todo, List<String[]> needles, Set<String> found) {
        String text = new String(classBytes, StandardCharsets.ISO_8859_1);   // 바이트 그대로 — 이름은 ASCII 다
        for (int i = 0; i < todo.size(); i++) {
            Group g = todo.get(i);
            if (found.contains(g.id())) continue;
            for (String n : needles.get(i)) {
                if (text.contains(n)) {
                    found.add(g.id());
                    break;
                }
            }
        }
    }

    public Set<String> scan(Path jar, Set<String> skip) throws IOException {
        List<Group> todo = new ArrayList<>();
        for (Group g : groups.values()) if (!skip.contains(g.id())) todo.add(g);
        Set<String> found = new LinkedHashSet<>();
        if (todo.isEmpty()) return found;
        List<String[]> needles = new ArrayList<>();
        for (Group g : todo) {
            String[] n = new String[g.markers().size() * 2];
            for (int i = 0; i < g.markers().size(); i++) {
                String m = g.markers().get(i);
                n[2 * i] = m;
                n[2 * i + 1] = m.replace('/', '.');
            }
            needles.add(n);
        }
        try (JarFile jf = new JarFile(jar.toFile())) {
            var en = jf.entries();
            while (en.hasMoreElements() && found.size() < todo.size()) {
                JarEntry e = en.nextElement();
                if (e.isDirectory()) continue;
                String name = e.getName();
                if (name.endsWith(".class")) {
                    try (InputStream in = jf.getInputStream(e)) {
                        match(in.readAllBytes(), todo, needles, found);
                    }
                } else if (isNestedJar(name)) {
                    try (InputStream in = jf.getInputStream(e)) {
                        scanNested(in, todo, needles, found, 1);
                    }
                }
            }
        }
        // 목록 순서로
        Set<String> ordered = new LinkedHashSet<>();
        for (Group g : groups.values()) if (found.contains(g.id())) ordered.add(g.id());
        return ordered;
    }
}
