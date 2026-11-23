package org.domi.furnace.compat.shimgen;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;

/**
 * T-264: 호환 계층에 있는 CraftBukkit 클래스(와 Craft 계층이 기대는 Paper 내부 형식)의 선언이 Paper 와 같은가 — 서명표 없이, 커밋된 잠금
 * ({@code src/shimspec/craft-hierarchy-<판>.lock})과 컴파일된 클래스를 맞댄다. 다른 줄은 기준선({@code craft-hierarchy-baseline.txt}, 줄마다 할 일)에
 * 있어야 하고, 기준선은 줄어들기만 한다(같아진 줄이 남아 있으면 지우라며 실패). 설계 docs/feature-parity/compat-craft-hierarchy-t264-design-2026-11-23.md 3.2.
 */
class CraftHierarchyTest {
    static final Path LOCK = Path.of("src/shimspec/craft-hierarchy-1.21.1.lock");
    static final Path BASELINE = Path.of("src/test/resources/craft-hierarchy-baseline.txt");
    /** 비교하는 클래스 플래그(접근 범위·final·abstract·종류). */
    static final int FLAGS = Opcodes.ACC_PUBLIC | Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED | Opcodes.ACC_FINAL | Opcodes.ACC_ABSTRACT
            | Opcodes.ACC_INTERFACE | Opcodes.ACC_ENUM | Opcodes.ACC_ANNOTATION;

    static final Pattern ANONYMOUS = Pattern.compile("\\$[0-9]+($|\\$)");

    record Decl(int access, String superName, List<String> interfaces) {}

    /** 컴파일된 클래스의 선언(중첩이면 InnerClasses 의 접근). 없으면 null. */
    static Decl compiled(String internalName) throws IOException {
        try (InputStream in = CraftHierarchyTest.class.getClassLoader().getResourceAsStream(internalName + ".class")) {
            if (in == null) return null;
            ClassReader r = new ClassReader(in);
            int[] access = {r.getAccess()};
            r.accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public void visitInnerClass(String name, String outer, String inner, int acc) {
                    if (name.equals(internalName)) access[0] = acc;
                }
            }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            List<String> itf = new ArrayList<>();
            for (String i : r.getInterfaces()) itf.add(CraftHierarchyLock.publicName(i));
            return new Decl(access[0], CraftHierarchyLock.publicName(r.getSuperName()), itf);
        }
    }

    /**
     * 지금 다른 줄 전부(정렬). 줄 꼴: {@code <종류> <클래스> paper=<…> furnace=<…>}. 호환 계층에 없는 클래스(껍데기 몫)는 세지 않는다.
     * {@code extra <클래스>}: craftbukkit 꾸러미에 Paper 에 없는 클래스(설계 3.1 [검토 3] — 익명 클래스는 접는다).
     */
    static Set<String> differences() throws IOException {
        Set<String> out = new TreeSet<>();
        Map<String, CraftHierarchyLock.Entry> lock = CraftHierarchyLock.read(LOCK);
        for (String n : compiledCraftClasses()) {
            if (!lock.containsKey(n) && !ANONYMOUS.matcher(n).find()) out.add("extra " + n);
        }
        for (CraftHierarchyLock.Entry e : lock.values()) {
            if (ANONYMOUS.matcher(e.name()).find()) continue;   // 익명 클래스 번호는 컴파일러가 정한다 — 이름을 맞추지 않는다(설계 4.4)
            Decl d = compiled(e.name());
            if (d == null) continue;
            if (!e.superName().equals(d.superName())) out.add("super " + e.name() + " paper=" + e.superName() + " furnace=" + d.superName());
            Set<String> want = new TreeSet<>(e.interfaces());
            Set<String> have = new TreeSet<>(d.interfaces());
            if (!want.equals(have)) {
                Set<String> missing = new TreeSet<>(want);
                missing.removeAll(have);
                Set<String> extra = new TreeSet<>(have);
                extra.removeAll(want);
                out.add("interfaces " + e.name() + " paper-only=" + String.join(",", missing) + " furnace-only=" + String.join(",", extra));
            }
            if ((e.access() & FLAGS) != (d.access() & FLAGS)) {
                out.add("access " + e.name() + " paper=" + Integer.toHexString(e.access() & FLAGS) + " furnace=" + Integer.toHexString(d.access() & FLAGS));
            }
        }
        return out;
    }

    /** 호환 계층 컴파일 결과의 org/bukkit/craftbukkit/** 클래스(내부 이름). */
    static Set<String> compiledCraftClasses() throws IOException {
        java.net.URL anchor = CraftHierarchyTest.class.getClassLoader().getResource("org/bukkit/craftbukkit/CraftServer.class");
        if (anchor == null || !anchor.getProtocol().equals("file")) throw new IllegalStateException("compiled CraftBukkit classes not found as files: " + anchor);
        Path root;
        try {
            root = Path.of(anchor.toURI()).getParent().getParent().getParent().getParent();
        } catch (java.net.URISyntaxException e) {
            throw new IOException(e);
        }
        Set<String> out = new TreeSet<>();
        try (java.util.stream.Stream<Path> files = Files.walk(root.resolve(CraftHierarchyLock.PREFIX))) {
            files.filter(p -> p.toString().endsWith(".class")).forEach(p -> {
                String rel = root.relativize(p).toString().replace('\\', '/');
                out.add(rel.substring(0, rel.length() - ".class".length()));
            });
        }
        return out;
    }

    static Set<String> baseline() throws IOException {
        Set<String> out = new TreeSet<>();
        for (String raw : Files.readAllLines(BASELINE, StandardCharsets.UTF_8)) {
            String l = raw.strip();
            if (!l.isEmpty() && !l.startsWith("#")) out.add(l);
        }
        return out;
    }

    @Test
    void craftDeclarationsMatchPaperOrAreListed() throws IOException {
        Set<String> now = differences();
        Set<String> base = baseline();
        Set<String> fresh = new TreeSet<>(now);
        fresh.removeAll(base);
        Set<String> fixed = new TreeSet<>(base);
        fixed.removeAll(now);
        if (Boolean.getBoolean("furnace.craftHierarchy.write")) {
            StringBuilder sb = new StringBuilder("# T-264 CraftBukkit 계층 기준선 - Paper 와 아직 다른 선언. 줄어들기만 한다(같아지면 줄을 지운다).\n"
                    + "# 다시 쓰기: ./gradlew :furnace-compat-paper:test --tests '*CraftHierarchyTest' -Dfurnace.craftHierarchy.write=true (새 차이를 더하는 데 쓰지 않는다)\n");
            for (String l : now) sb.append(l).append('\n');
            Files.writeString(BASELINE, sb.toString(), StandardCharsets.UTF_8);
            return;
        }
        StringBuilder msg = new StringBuilder();
        if (!fresh.isEmpty()) msg.append("new differences from Paper (fix the class, do not add them to the baseline):\n  ").append(String.join("\n  ", fresh)).append('\n');
        if (!fixed.isEmpty()) msg.append("baseline lines no longer match (now equal to Paper, or the difference changed) - delete them from ").append(BASELINE).append(":\n  ").append(String.join("\n  ", fixed)).append('\n');
        if (!msg.isEmpty()) fail(msg.toString());
    }

    /** 검토 1: 잠금·기준선·관측표에 Mojang 이름이 글자로 들어가지 않는다(지문만). */
    @Test
    void noMojangNamesInCraftFiles() throws IOException {
        Pattern mojang = Pattern.compile("net[/.]minecraft|com[/.]mojang");
        List<Path> files = new ArrayList<>(List.of(LOCK, BASELINE, CraftLiveMethodsTest.TABLE));
        Path identity = Path.of("src/test/resources/craft-identity-paper-1.21.1.tsv");
        if (Files.exists(identity)) files.add(identity);
        for (Path f : files) {
            List<String> lines = Files.readAllLines(f, StandardCharsets.UTF_8);
            for (int i = 0; i < lines.size(); i++) {
                assertTrue(!mojang.matcher(lines.get(i)).find(), f + ":" + (i + 1) + " has a Mojang name - write it as nms:<sha256/16>");
            }
        }
    }

    @Test
    void lockCoversEveryRole() throws IOException {
        Map<String, CraftHierarchyLock.Entry> lock = CraftHierarchyLock.read(LOCK);
        Set<String> roles = new TreeSet<>();
        for (CraftHierarchyLock.Entry e : lock.values()) roles.add(e.role());
        assertTrue(roles.equals(Set.of("impl", "static", "internal", "paper-impl", "paper-internal")), "roles " + roles);
    }
}
