package org.domi.furnace.compat.realworld;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * 실제 플러그인·플러그인 라이브러리 jar 가 서버에 요구하는 API 를 호환 계층과 견준다(H4).
 *
 * <p>정적 분석이라 "그 코드가 실제로 실행되는가"는 보지 않는다 — 요구 상한이다. 어느 클래스·메서드가 가장 많은 jar 에서
 * 빠졌는지 순위를 내 다음 묶음의 우선순위로 쓴다.
 */
public final class Coverage {

    public enum Kind {
        BUKKIT("Bukkit API"), PAPER("Paper API"), SPIGOT("Spigot·BungeeChat"), CRAFTBUKKIT("CraftBukkit"), NMS("net.minecraft·authlib"),
        ADVENTURE("Adventure(Paper 제공)"), SERVER_LIB("서버 제공 라이브러리(Guava·Gson·SnakeYAML 등)");

        public final String label;

        Kind(String label) {
            this.label = label;
        }

        /** Bukkit 계열 API(우리가 직접 새로 쓴 표면). */
        public boolean isApi() {
            return this == BUKKIT || this == PAPER || this == SPIGOT;
        }
    }

    /** 컴파일 시간 전용(주석)이라 실행에 필요 없는 패키지. */
    private static final String[] IGNORED = {
        "java/", "javax/", "jdk/", "sun/", "org/jetbrains/annotations/", "org/intellij/", "org/checkerframework/", "com/google/errorprone/",
        "org/jspecify/", "edu/umd/cs/findbugs/",
    };

    public static Kind kindOf(String internal) {
        for (String p : IGNORED) if (internal.startsWith(p)) return null;
        if (internal.startsWith("org/bukkit/craftbukkit/")) return Kind.CRAFTBUKKIT;
        if (internal.startsWith("org/bukkit/")) return Kind.BUKKIT;
        if (internal.startsWith("io/papermc/paper/") || internal.startsWith("com/destroystokyo/")) return Kind.PAPER;
        if (internal.startsWith("org/spigotmc/") || internal.startsWith("net/md_5/")) return Kind.SPIGOT;
        if (internal.startsWith("net/minecraft/") || internal.startsWith("com/mojang/authlib/") || internal.startsWith("com/mojang/datafixers/")
                || internal.startsWith("com/mojang/serialization/")) return Kind.NMS;
        if (internal.startsWith("net/kyori/")) return Kind.ADVENTURE;
        if (internal.startsWith("com/google/gson/") || internal.startsWith("com/google/common/") || internal.startsWith("org/yaml/snakeyaml/")
                || internal.startsWith("org/apache/commons/lang3/") || internal.startsWith("org/apache/logging/") || internal.startsWith("org/slf4j/")
                || internal.startsWith("it/unimi/dsi/fastutil/") || internal.startsWith("com/mojang/brigadier/")) return Kind.SERVER_LIB;
        return null; // 서버가 주지 않는 것(자기 jar 에 넣어 와야 한다) — 판단 대상 아님
    }

    /** jar 하나의 결과. */
    public static final class JarResult {
        public final String jar;
        public final boolean plugin;
        public int classCount;
        /** 종류별 필요 클래스(요구) / 없는 클래스. */
        public final Map<Kind, Set<String>> neededClasses = new LinkedHashMap<>();
        public final Map<Kind, Set<String>> missingClasses = new LinkedHashMap<>();
        public final Map<Kind, Set<ClassRefs.Member>> neededMembers = new LinkedHashMap<>();
        public final Map<Kind, Set<ClassRefs.Member>> missingMembers = new LinkedHashMap<>();
        /** 소유 클래스는 있는데 멤버가 없는 것(없는 클래스의 멤버는 클래스 누락에 묶인다). */
        public final Set<ClassRefs.Member> missingOnPresentClass = new LinkedHashSet<>();
        /** 소유 클래스가 아예 없어 판정 못 한 멤버 수(종류별). */
        public final Map<Kind, Integer> blockedMembers = new LinkedHashMap<>();
        /** 없는 클래스별로 이 jar 가 부르는 멤버 수. */
        public final Map<String, Integer> blockedByClass = new TreeMap<>();

        JarResult(String jar, boolean plugin) {
            this.jar = jar;
            this.plugin = plugin;
        }

        public int needed(Kind k) {
            return neededClasses.getOrDefault(k, Set.of()).size() + neededMembers.getOrDefault(k, Set.of()).size()
                    + blockedMembers.getOrDefault(k, 0);
        }

        public int missing(Kind k) {
            return missingClasses.getOrDefault(k, Set.of()).size() + missingMembers.getOrDefault(k, Set.of()).size()
                    + blockedMembers.getOrDefault(k, 0);
        }

        /** Bukkit 계열 API 의 충족 비율(0~1). 요구가 없으면 1. */
        public double apiCoverage() {
            int n = 0, m = 0;
            for (Kind k : Kind.values()) {
                if (!k.isApi()) continue;
                n += needed(k);
                m += missing(k);
            }
            return n == 0 ? 1.0 : (double) (n - m) / n;
        }

        public int apiNeeded() {
            int n = 0;
            for (Kind k : Kind.values()) if (k.isApi()) n += needed(k);
            return n;
        }

        public int apiMissing() {
            int m = 0;
            for (Kind k : Kind.values()) if (k.isApi()) m += missing(k);
            return m;
        }
    }

    private Coverage() {}

    /** jar 목록을 읽어 하나씩 판정한다. 서로의 클래스는 "자기 쪽"으로 보고 판정에서 뺀다(그림자 포함 라이브러리). */
    public static List<JarResult> scan(List<Path> jars, ApiIndex index) throws IOException {
        Set<String> own = new TreeSet<>();
        Map<Path, List<ClassRefs>> parsed = new LinkedHashMap<>();
        Map<Path, Boolean> plugin = new LinkedHashMap<>();
        for (Path jar : jars) {
            List<ClassRefs> refs = new ArrayList<>();
            boolean hasPluginYml = false;
            try (JarFile jf = new JarFile(jar.toFile())) {
                var en = jf.entries();
                while (en.hasMoreElements()) {
                    JarEntry e = en.nextElement();
                    String n = e.getName();
                    if (n.equals("plugin.yml") || n.equals("paper-plugin.yml")) hasPluginYml = true;
                    if (!n.endsWith(".class") || n.startsWith("META-INF/") || n.equals("module-info.class")) continue;
                    try (InputStream in = jf.getInputStream(e)) {
                        ClassRefs r = ClassRefs.parse(in.readAllBytes());
                        refs.add(r);
                        own.add(r.name);
                    }
                }
            }
            parsed.put(jar, refs);
            plugin.put(jar, hasPluginYml);
        }
        List<JarResult> out = new ArrayList<>();
        for (Path jar : jars) {
            JarResult res = new JarResult(jar.getFileName().toString(), plugin.get(jar));
            res.classCount = parsed.get(jar).size();
            Set<String> classSeen = new LinkedHashSet<>();
            Set<ClassRefs.Member> memberSeen = new LinkedHashSet<>();
            for (ClassRefs r : parsed.get(jar)) {
                classSeen.addAll(r.classes);
                memberSeen.addAll(r.members);
            }
            for (String c : classSeen) {
                if (own.contains(c)) continue;
                Kind k = kindOf(c);
                if (k == null) continue;
                res.neededClasses.computeIfAbsent(k, x -> new TreeSet<>()).add(c);
                if (index.find(c) == null) res.missingClasses.computeIfAbsent(k, x -> new TreeSet<>()).add(c);
            }
            for (ClassRefs.Member m : memberSeen) {
                if (own.contains(m.owner())) continue;
                Kind k = kindOf(m.owner());
                if (k == null) continue;
                if (index.find(m.owner()) == null) {
                    res.blockedMembers.merge(k, 1, Integer::sum);
                    res.blockedByClass.merge(m.owner(), 1, Integer::sum);
                    continue;
                }
                res.neededMembers.computeIfAbsent(k, x -> new LinkedHashSet<>()).add(m);
                if (!index.hasMember(m)) {
                    res.missingMembers.computeIfAbsent(k, x -> new LinkedHashSet<>()).add(m);
                    res.missingOnPresentClass.add(m);
                }
            }
            out.add(res);
        }
        return out;
    }

}
