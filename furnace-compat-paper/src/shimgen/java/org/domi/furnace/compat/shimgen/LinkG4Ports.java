// T-263 S6: G4 서버 이식 짝(작업 목록, 연결 아님 — 설계 3.2 G4). 동작 없는 NMS 멤버마다 "Furnace 서버의 어느 메서드가 이미 같은 일을 하는가"를
// 바이트코드 특징으로 짐작해 영역별 표로 낸다. 영역 스레드가 그 서버 메서드를 부르는 다리(@LinkTarget)를 쓰면 G2·G3 가 잇는다.
// 바닐라 클래스 ↔ 서버 클래스 쌍은 서버 소스의 @VanillaPort(소스 전용 표지)에서. 짝은 점수 높은 하나만, 애매하면 비운다.
// 점수는 순서·참고일 뿐 증명이 아니다(다리를 쓰는 사람이 확인한다). 이름 든 표는 build/ 에만.
package org.domi.furnace.compat.shimgen;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

final class LinkG4Ports {
    /** 받아들일 가장 낮은 점수와, 둘째와의 가장 작은 차이(애매함). */
    static final double MIN_SCORE = 4.0, MIN_MARGIN = 1.5;
    static final Set<String> RANDOM = Set.of("nextInt", "nextFloat", "nextDouble", "nextBoolean", "nextGaussian", "nextLong", "triangle");
    // 서버 소스는 짧은 이름(@VanillaPort)과 전체 이름(@org.domi.furnace.server.VanillaPort) 둘 다 쓴다(엔티티 패키지는 대부분 전체 이름)
    static final Pattern PORT = Pattern.compile("@(?:org\\.domi\\.furnace\\.server\\.)?VanillaPort\\s*\\(\\s*(\\{[^}]*}|\"[^\"]*\")\\s*\\)");
    static final Pattern DECL = Pattern.compile("\\b(class|interface|enum|record)\\s+(\\w+)");
    static final Pattern STR = Pattern.compile("\"([^\"]+)\"");

    /** 메서드의 바이트코드 특징. */
    static final class Features {
        final String name, desc;
        final Set<Object> consts = new HashSet<>();
        final Set<String> calls = new HashSet<>();
        int random, insns;

        Features(String name, String desc) {
            this.name = name;
            this.desc = desc;
        }
    }

    record Pair(LinkInfer.Shell m, String server, Features target, double score, double second, String why) {}

    final LinkInfer inf;
    final ZipFile vanilla;
    final Path serverSrc, serverClasses;
    final List<String[]> areas;
    /** 바닐라 클래스(내부 이름) → 이식한 서버 클래스들(내부 이름). */
    final Map<String, List<String>> ports = new TreeMap<>();
    final List<Pair> pairs = new ArrayList<>();
    final Map<String, int[]> byArea = new TreeMap<>();   // 0 동작 없음(바닐라 몸통 있음), 1 이식 클래스 있음, 2 짝, 3 애매, 4 짝 없음
    int portLines, unresolvedPorts;

    LinkG4Ports(LinkInfer inf, ZipFile vanilla, Path serverSrc, Path serverClasses, List<String[]> areas) {
        this.inf = inf;
        this.vanilla = vanilla;
        this.serverSrc = serverSrc;
        this.serverClasses = serverClasses;
        this.areas = areas;
    }

    void run() throws IOException {
        readPorts();
        Map<String, Map<String, Features>> vanillaFeatures = new HashMap<>(), serverFeatures = new HashMap<>();
        for (LinkInfer.Shell s : inf.shells) {
            int acc = s.access();
            if (s.name().startsWith("<") || !Links.visible(acc) || !inf.bodies.containsKey(s.key())) continue;
            int[] a = byArea.computeIfAbsent(LinkInfer.area(areas, s.owner()), k -> new int[5]);
            a[0]++;
            List<String> fs = ports.get(s.owner());
            if (fs == null) continue;
            a[1]++;
            Features v = vanillaFeatures.computeIfAbsent(s.owner(), o -> features(bytes(vanilla, o))).get(s.name() + s.desc());
            if (v == null) continue;
            Features best = null;
            String bestOwner = null;
            double top = -1, second = -1;
            for (String f : fs) {
                for (Features c : serverFeatures.computeIfAbsent(f, o -> features(serverBytes(o))).values()) {
                    double sc = score(v, c);
                    if (sc > top) {
                        second = top;
                        top = sc;
                        best = c;
                        bestOwner = f;
                    } else if (sc > second) second = sc;
                }
            }
            if (best == null || top < MIN_SCORE) {
                a[4]++;
                continue;
            }
            if (top - second < MIN_MARGIN) {
                a[3]++;
                continue;
            }
            a[2]++;
            pairs.add(new Pair(s, bestOwner, best, top, Math.max(second, 0), why(v, best)));
        }
    }

    /** 서버 소스의 @VanillaPort → 서버 클래스 내부 이름. 표지 바로 뒤 선언이 파일 이름과 다르면 안쪽 클래스로 본다. */
    void readPorts() throws IOException {
        List<Path> files;
        try (Stream<Path> s = Files.walk(serverSrc)) {
            files = s.filter(p -> p.toString().endsWith(".java")).sorted().toList();
        }
        for (Path f : files) {
            String text = Files.readString(f, StandardCharsets.UTF_8);
            if (!text.contains("VanillaPort")) continue;
            String rel = serverSrc.relativize(f).toString().replace('\\', '/');
            String pkgPath = rel.contains("/") ? rel.substring(0, rel.lastIndexOf('/') + 1) : "";
            String outer = f.getFileName().toString().replace(".java", "");
            Matcher m = PORT.matcher(text);
            while (m.find()) {
                Matcher d = DECL.matcher(text);
                if (!d.find(m.end())) continue;
                String name = d.group(2);
                String server = pkgPath + (name.equals(outer) ? outer : outer + "$" + name);
                if (!Files.isRegularFile(serverClasses.resolve(server + ".class"))) {
                    unresolvedPorts++;
                    continue;
                }
                Matcher s = STR.matcher(m.group(1));
                while (s.find()) {
                    ports.computeIfAbsent(s.group(1).replace('.', '/'), k -> new ArrayList<>()).add(server);
                    portLines++;
                }
            }
        }
    }

    byte[] serverBytes(String internal) {
        try {
            Path p = serverClasses.resolve(internal + ".class");
            return Files.isRegularFile(p) ? Files.readAllBytes(p) : null;
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    static byte[] bytes(ZipFile jar, String internal) {
        try {
            ZipEntry e = jar.getEntry(internal + ".class");
            if (e == null) return null;
            try (InputStream in = jar.getInputStream(e)) {
                return in.readAllBytes();
            }
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    /** 클래스의 몸통 있는 메서드마다 특징(이름+서술자 → 특징). 생성자·정적 초기화·합성 메서드는 뺀다. */
    static Map<String, Features> features(byte[] b) {
        Map<String, Features> out = new HashMap<>();
        if (b == null) return out;
        new ClassReader(b).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] ex) {
                if (name.startsWith("<") || (access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_SYNTHETIC | Opcodes.ACC_BRIDGE)) != 0) return null;
                Features f = new Features(name, desc);
                out.put(name + desc, f);
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitInsn(int op) { f.insns++; }

                    @Override
                    public void visitIntInsn(int op, int operand) {
                        f.insns++;
                        if (op != Opcodes.NEWARRAY) f.consts.add(operand);
                    }

                    @Override
                    public void visitVarInsn(int op, int v) { f.insns++; }

                    @Override
                    public void visitTypeInsn(int op, String t) { f.insns++; }

                    @Override
                    public void visitFieldInsn(int op, String o, String n, String d) { f.insns++; }

                    @Override
                    public void visitMethodInsn(int op, String o, String n, String d, boolean itf) {
                        f.insns++;
                        if (RANDOM.contains(n)) f.random++;
                        if (!o.startsWith("java/") && !n.startsWith("<")) f.calls.add(n);
                    }

                    @Override
                    public void visitJumpInsn(int op, Label l) { f.insns++; }

                    @Override
                    public void visitLdcInsn(Object v) {
                        f.insns++;
                        if (v instanceof Number || v instanceof String) f.consts.add(v instanceof Float x ? (Object) x.doubleValue() : v);
                    }

                    @Override
                    public void visitIincInsn(int v, int inc) { f.insns++; }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return out;
    }

    /**
     * 짝 점수: 이름(get/is/has/set 뗀 것이 같음 5, 아니면 낱말 겹침 × 3) + 상수 겹침 × 4(둘 다 상수가 있을 때) + 난수 호출 수 같음 2(0 보다 클 때)
     * + 부르는 메서드 이름 겹침 × 2 + 인자 수 같음 1 + 반환 종류 같음 1 + 인자 종류 같음 1. 몸통 길이가 네 배 넘게 다르면 2 를 빼고,
     * 이름이 같은데 인자 수가 다르면 2 를 더 뺀다(근거에 ARITY DIFFERS).
     */
    static double score(Features v, Features s) {
        double sc = 0;
        if (LinkG2Census.norm(v.name).equals(LinkG2Census.norm(s.name))) sc += 5;
        else sc += 3 * jaccard(tokens(v.name), tokens(s.name));
        if (!v.consts.isEmpty() && !s.consts.isEmpty()) sc += 4 * jaccard(v.consts, s.consts);
        if (v.random > 0 && v.random == s.random) sc += 2;
        if (!v.calls.isEmpty() && !s.calls.isEmpty()) sc += 2 * jaccard(tokensOf(v.calls), tokensOf(s.calls));
        Type[] va = Type.getArgumentTypes(v.desc), sa = Type.getArgumentTypes(s.desc);
        if (va.length == sa.length) {
            sc += 1;
            boolean same = true;
            for (int i = 0; i < va.length; i++) same &= sort(va[i]) == sort(sa[i]);
            if (same) sc += 1;
        }
        if (sort(Type.getReturnType(v.desc)) == sort(Type.getReturnType(s.desc))) sc += 1;
        int lo = Math.max(1, Math.min(v.insns, s.insns)), hi = Math.max(v.insns, s.insns);
        if (hi > 4 * lo) sc -= 2;
        // 이름이 같아도 인자 수가 다르면 다른 겹치기(오버로드)일 수 있다 — 깎고 표에 적는다(검토 권고)
        if (va.length != sa.length && LinkG2Census.norm(v.name).equals(LinkG2Census.norm(s.name))) sc -= 2;
        return sc;
    }

    static int sort(Type t) { return t.getSort() == Type.ARRAY ? Type.OBJECT : t.getSort(); }

    static Set<String> tokens(String name) {
        Set<String> out = new HashSet<>();
        for (String p : name.split("(?<=[a-z0-9])(?=[A-Z])|_")) if (!p.isEmpty()) out.add(p.toLowerCase(Locale.ROOT));
        out.removeAll(Set.of("get", "is", "has", "set", "can", "do"));
        return out;
    }

    static Set<String> tokensOf(Set<String> names) {
        Set<String> out = new HashSet<>();
        for (String n : names) out.addAll(tokens(n));
        return out;
    }

    static <T> double jaccard(Set<T> a, Set<T> b) {
        if (a.isEmpty() || b.isEmpty()) return 0;
        int inter = 0;
        for (T x : a) if (b.contains(x)) inter++;
        return (double) inter / (a.size() + b.size() - inter);
    }

    static String why(Features v, Features s) {
        List<String> w = new ArrayList<>();
        if (LinkG2Census.norm(v.name).equals(LinkG2Census.norm(s.name))) w.add("name");
        else if (jaccard(tokens(v.name), tokens(s.name)) > 0) w.add("name words");
        Set<Object> c = new HashSet<>(v.consts);
        c.retainAll(s.consts);
        if (!c.isEmpty()) w.add("constants " + c.size() + "/" + v.consts.size());
        if (v.random > 0 && v.random == s.random) w.add("random calls " + v.random);
        if (jaccard(tokensOf(v.calls), tokensOf(s.calls)) > 0) w.add("callee words");
        if (Type.getArgumentTypes(v.desc).length != Type.getArgumentTypes(s.desc).length) w.add("ARITY DIFFERS");
        return String.join(", ", w);
    }

    /** 숫자 요약(이름 없음)과 영역별 작업 목록(이름 듦, build/ 만). */
    void write(Path out) throws IOException {
        Path dir = out.resolve("g4");
        Files.createDirectories(dir);
        Map<String, List<Pair>> per = new TreeMap<>();
        for (Pair p : pairs) per.computeIfAbsent(LinkInfer.area(areas, p.m().owner()), k -> new ArrayList<>()).add(p);
        for (Map.Entry<String, List<Pair>> e : per.entrySet()) {
            StringBuilder sb = new StringBuilder("# T-263 G4 server port pairs - area " + e.getKey() + "\n\n")
                    .append("For each throwing NMS member (public/protected, with a vanilla body): the method of the Furnace server class that ports its vanilla class (@VanillaPort) with the closest bytecode features.\n")
                    .append("**A guess** - before writing a bridge (@LinkTarget static method, adapter boundary docs/ops/adapter-boundary.md), read the server method and check it really does the same thing.\n")
                    .append("Write the bridge and rerun `inferLinks`; G2/G3 then link. Highest score first. Ambiguous pairs (small margin over the second best) are left out.\n")
                    .append("Same-name pairs first (mostly right); pairs with a different name follow - only constants/callees are alike, so wrong pairs are mixed in. ARITY DIFFERS: same name, different argument count (maybe an overload).\n");
            e.getValue().sort((a, b) -> {
                boolean an = a.why().startsWith("name,") || a.why().equals("name"), bn = b.why().startsWith("name,") || b.why().equals("name");
                return an != bn ? (an ? -1 : 1) : Double.compare(b.score(), a.score());
            });
            boolean header = false, weak = false;
            for (Pair p : e.getValue()) {
                boolean named = p.why().startsWith("name,") || p.why().equals("name");
                if (!header || (!named && !weak)) {
                    weak = !named;
                    header = true;
                    sb.append(weak ? "\n## Different name (weak - always check)\n\n" : "\n## Same name\n\n")
                            .append("| throwing NMS member | Furnace server method | score (second) | evidence |\n|---|---|---:|---|\n");
                }
                sb.append("| `").append(p.m().owner().replace('/', '.')).append('#').append(p.m().name()).append(p.m().desc()).append("` | `")
                        .append(p.server().replace('/', '.')).append('#').append(p.target().name).append(p.target().desc).append("` | ")
                        .append(String.format(Locale.ROOT, "%.1f (%.1f)", p.score(), p.second())).append(" | ").append(p.why()).append(" |\n");
            }
            Files.writeString(dir.resolve(e.getKey() + ".md"), sb.toString(), StandardCharsets.UTF_8);
        }
        StringBuilder md = new StringBuilder("# T-263 G4 server port pairs (numbers only - a work list, nothing linked)\n\n");
        md.append("@VanillaPort names: ").append(portLines).append(" (vanilla classes with a port: ").append(ports.size())
                .append(", annotations whose server class was not found: ").append(unresolvedPorts).append(")\n\n");
        md.append("| area | throwing (vanilla body) | owner has a port | paired | ambiguous | no pair |\n|---|---:|---:|---:|---:|---:|\n");
        int[] t = new int[5];
        byArea.forEach((k, v) -> {
            md.append("| ").append(k);
            for (int i = 0; i < 5; i++) {
                md.append(" | ").append(v[i]);
                t[i] += v[i];
            }
            md.append(" |\n");
        });
        md.append("| total");
        for (int x : t) md.append(" | ").append(x);
        md.append(" |\n");
        Files.writeString(out.resolve("g4-summary.md"), md.toString(), StandardCharsets.UTF_8);
        System.out.println("[link-infer] G4 port pairs: " + t[2] + " paired, " + t[3] + " ambiguous, of " + t[1] + " throwing members whose class has a server port -> " + dir);
    }
}
