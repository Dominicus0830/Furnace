// 이식 다리 생성기 — 같은 .ports 명세에서 세 모듈의 생성물을 만든다(설계 docs/roadmap/port-bridge-2026-11-27.md 2·5장).
//   api    → org.domi.furnace.api.internal.port.<Area>Port        중립 형식만 쓰는 인터페이스
//   server → org.domi.furnace.server.port.gen.<Area>PortImpl      첫 호출 PortGuards → kind 바꾸기 → 서버 메서드 + META-INF/services 줄
//   compat → org.domi.furnace.compat.nms.port.gen.<Area>Ports     @LinkTarget(check="port") 정적 스텁 + <영역>.ports.links(FullShells 가 읽는 연결 줄)
// 생성기는 서버 서술자를 모른다(설계 [검토 8]): 서버 메서드를 이름으로 부르기만 하고 겹치기 고르기·서술자 검사는 javac 가 한다.
package org.domi.furnace.portgen;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.domi.furnace.portgen.PortSpec.Cls;
import org.domi.furnace.portgen.PortSpec.Kind;
import org.domi.furnace.portgen.PortSpec.Member;

public final class PortGen {

    static final String API_PKG = "org.domi.furnace.api.internal.port";
    static final String SERVER_PKG = "org.domi.furnace.server.port.gen";
    static final String COMPAT_PKG = "org.domi.furnace.compat.nms.port.gen";
    static final String WORLD = "org.domi.furnace.api.world.World";
    static final String RANDOM = API_PKG + ".PortRandom";
    static final String LEVEL_TYPE = "org.domi.furnace.server.block.BlockLevel";

    private PortGen() {}

    /** 인자: <api|server|compat> <명세 폴더> <소스 출력> [server: 리소스 출력 | compat: 연결 줄 출력]. */
    public static void main(String[] args) throws IOException {
        if (args.length < 3) throw new IllegalArgumentException("usage: PortGen <api|server|compat> <spec dir> <source out> [resource/links out]");
        List<PortSpec> specs = PortSpec.readDir(Path.of(args[1]));
        Map<String, String> files = generate(args[0], specs);
        Path src = Path.of(args[2]);
        Path extra = args.length > 3 ? Path.of(args[3]) : null;
        clean(src);
        if (extra != null) clean(extra);
        for (Map.Entry<String, String> e : files.entrySet()) {
            String k = e.getKey();
            Path out = k.startsWith("res:") || k.startsWith("links:") ? extra.resolve(k.substring(k.indexOf(':') + 1)) : src.resolve(k);
            Files.createDirectories(out.getParent());
            Files.writeString(out, e.getValue(), StandardCharsets.UTF_8);
        }
        System.out.println("[portgen] " + args[0] + ": " + specs.size() + " area file(s), " + count(specs) + " line(s)");
    }

    static int count(List<PortSpec> specs) {
        int n = 0;
        for (PortSpec s : specs) for (Cls c : s.classes) n += c.members().size();
        return n;
    }

    private static void clean(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) return;
        try (var w = Files.walk(dir)) {
            for (Path p : w.sorted(java.util.Comparator.reverseOrder()).toList()) if (!p.equals(dir)) Files.delete(p);
        }
    }

    /**
     * 모드의 생성물: 상대 경로 → 내용. 리소스는 "res:" 앞붙이, 연결 줄은 "links:" 앞붙이.
     * 명세에 문제가 있으면 전부 모아 IllegalStateException(빌드 멈춤).
     */
    public static Map<String, String> generate(String mode, List<PortSpec> specs) {
        List<String> problems = new ArrayList<>();
        for (PortSpec s : specs) problems.addAll(s.problems);
        if (!problems.isEmpty()) throw new IllegalStateException("port spec problems:\n  " + String.join("\n  ", problems));
        Map<String, String> out = new LinkedHashMap<>();
        for (PortSpec s : specs) {
            if (count(List.of(s)) == 0) continue;
            switch (mode) {
                case "api" -> out.put(API_PKG.replace('.', '/') + "/" + s.camel() + "Port.java", api(s));
                case "server" -> {
                    out.put(SERVER_PKG.replace('.', '/') + "/" + s.camel() + "PortImpl.java", server(s));
                    out.put("res:META-INF/services/" + API_PKG + "." + s.camel() + "Port", SERVER_PKG + "." + s.camel() + "PortImpl\n");
                }
                case "compat" -> {
                    out.put(COMPAT_PKG.replace('.', '/') + "/" + s.camel() + "Ports.java", compat(s));
                    out.put("links:" + s.area + "-" + s.areaName + ".ports.links", links(s));
                }
                default -> throw new IllegalArgumentException("mode must be api, server or compat: " + mode);
            }
        }
        return out;
    }

    // ───────────────────────── 중립 인자 ─────────────────────────

    /** 중립 인자 하나(이름·형식). pos 는 세 칸으로 펼친다. */
    record Arg(String type, String name) {}

    static List<Arg> neutralArgs(Member m) {
        List<Arg> a = new ArrayList<>();
        if (!m.isStatic()) a.add(new Arg("int", "self"));
        for (int i = 0; i < m.params().size(); i++) {
            String t = m.params().get(i);
            switch (PortSpec.kindOf(t)) {
                case PRIM -> a.add(new Arg(PortSpec.primName(t), "a" + i));
                case STRING -> a.add(new Arg("String", "a" + i));
                case STATE -> a.add(new Arg("int", "a" + i));
                case POS -> {
                    a.add(new Arg("int", "a" + i + "x"));
                    a.add(new Arg("int", "a" + i + "y"));
                    a.add(new Arg("int", "a" + i + "z"));
                }
                case LEVEL -> a.add(new Arg(WORLD, "a" + i));
                case RANDOM -> a.add(new Arg(RANDOM, "a" + i));
                default -> throw new IllegalStateException(m.where() + ": kind not handled " + t);
            }
        }
        return a;
    }

    static String retType(Member m) {
        return switch (PortSpec.kindOf(m.ret())) {
            case VOID, PRIM -> PortSpec.primName(m.ret());
            case STRING -> "String";
            default -> throw new IllegalStateException(m.where() + ": return kind not handled " + m.ret());
        };
    }

    static String params(List<Arg> args) {
        StringBuilder b = new StringBuilder();
        for (Arg a : args) b.append(b.isEmpty() ? "" : ", ").append(a.type()).append(' ').append(a.name());
        return b.toString();
    }

    static String names(List<Arg> args) {
        StringBuilder b = new StringBuilder();
        for (Arg a : args) b.append(b.isEmpty() ? "" : ", ").append(a.name());
        return b.toString();
    }

    /** 오류 문구에 쓰는 이름: NMS 클래스 단순 이름#멤버. */
    static String what(Cls c, Member m) { return c.nmsSimple() + "#" + m.name(); }

    static String header(PortSpec s) {
        return "// 자동 생성 — " + s.file + " (portgen, docs/roadmap/port-bridge-2026-11-27.md). 손으로 고치지 않는다.\n";
    }

    // ───────────────────────── api ─────────────────────────

    static String api(PortSpec s) {
        StringBuilder b = new StringBuilder(header(s));
        b.append("package ").append(API_PKG).append(";\n\n");
        b.append("/** 이식 줄 영역 ").append(s.area).append("(").append(s.areaName).append(")의 다리. 서버 구현은 {@link PortBridges#get} 으로 찾는다. */\n");
        b.append("public interface ").append(s.camel()).append("Port {\n");
        for (Cls c : s.classes) {
            for (Member m : c.members()) {
                b.append("\n    /** ").append(c.server()).append('#').append(m.serverName()).append(" — check=").append(m.check().text).append(" */\n");
                b.append("    ").append(retType(m)).append(' ').append(PortSpec.bridgeName(c, m)).append('(').append(params(neutralArgs(m))).append(");\n");
            }
        }
        return b.append("}\n").toString();
    }

    // ───────────────────────── server ─────────────────────────

    static String server(PortSpec s) {
        StringBuilder b = new StringBuilder(header(s));
        b.append("package ").append(SERVER_PKG).append(";\n\n");
        b.append("import org.domi.furnace.server.port.PortGuards;\n");
        b.append("import org.domi.furnace.server.port.PortKinds;\n\n");
        b.append("/** ").append(s.file).append(" 의 서버 쪽 구현 — 모든 공개 메서드의 첫 호출은 PortGuards(PortImplOwnerFirstTest 가 바이트코드로 본다). */\n");
        b.append("@").append(API_PKG).append(".PortGenerated(\"").append(s.file).append("\")\n");
        b.append("public final class ").append(s.camel()).append("PortImpl implements ").append(API_PKG).append('.').append(s.camel()).append("Port {\n");
        int n = 0;
        StringBuilder consts = new StringBuilder();
        StringBuilder body = new StringBuilder();
        for (Cls c : s.classes) {
            for (Member m : c.members()) {
                String w = "W" + n++;
                consts.append("    private static final String ").append(w).append(" = \"").append(what(c, m)).append("\";\n");
                serverMember(body, c, m, w);
            }
        }
        b.append(consts).append(body);
        return b.append("}\n").toString();
    }

    private static void serverMember(StringBuilder b, Cls c, Member m, String w) {
        List<Arg> args = neutralArgs(m);
        String bn = PortSpec.bridgeName(c, m);
        String ret = retType(m);
        boolean isVoid = ret.equals("void");
        boolean pos = m.check() == PortSpec.Check.POS;
        String run = "run$" + bn;
        b.append("\n    @Override\n    public ").append(ret).append(' ').append(bn).append('(').append(params(args)).append(") {\n");
        switch (m.check()) {
            case PURE -> {
                b.append("        PortGuards.pure();\n");
                b.append("        ").append(isVoid ? "" : "return ").append(run).append('(').append(names(args)).append(");\n");
            }
            case POS -> {
                int lv = indexOf(m, Kind.LEVEL), ps = indexOf(m, Kind.POS);
                String level = "a" + lv, x = "a" + ps + "x", y = "a" + ps + "y", z = "a" + ps + "z";
                // 주인 스레드면 region 을 한 번 찾아 그 레벨로 바로(할당 없음), 아니면 PortGuards.atPos 가 넘겨 기다리거나 거절한다 — 넘긴 쪽에서 레벨을 다시 찾는다
                b.append("        " + LEVEL_TYPE + " lv = PortGuards.ownedLevel(").append(level).append(", ").append(x).append(", ").append(z).append(");\n");
                String call = run + "(lv, " + names(args) + ")";
                b.append("        if (lv != null) ").append(isVoid ? "{\n            " + call + ";\n            return;\n        }\n" : "return " + call + ";\n");
                String late = run + "(l, " + names(args) + ")";
                b.append("        ").append(isVoid ? "" : "return ").append("PortGuards.atPos(").append(level).append(", ").append(x).append(", ").append(y).append(", ").append(z)
                        .append(", ").append(w).append(", l -> ").append(isVoid ? "{ " + late + "; return null; }" : late).append(");\n");
            }
            default -> throw new IllegalStateException(m.where() + ": check not handled " + m.check());
        }
        b.append("    }\n");
        // 몸통: kind 바꾸기 → 서버 메서드
        b.append("\n    private static ").append(ret).append(' ').append(run).append('(').append(pos ? LEVEL_TYPE + " lv, " : "").append(params(args)).append(") {\n        ");
        if (!isVoid) b.append("return ");
        if (m.isStatic()) b.append(c.serverFqcn());
        else b.append("PortKinds.behavior(self, ").append(c.serverFqcn()).append(".class, ").append(w).append(')');
        b.append('.').append(m.serverName()).append('(');
        for (int i = 0; i < m.params().size(); i++) {
            if (i > 0) b.append(", ");
            String a = "a" + i;
            b.append(switch (PortSpec.kindOf(m.params().get(i))) {
                case PRIM, STRING, STATE -> a;
                case POS -> "new org.domi.furnace.server.block.Pos(" + a + "x, " + a + "y, " + a + "z)";
                case LEVEL -> "lv";   // check=pos 가 찾은 첫 자리 칸의 주인 region 레벨
                case RANDOM -> "PortKinds.random(" + a + ")";
                default -> throw new IllegalStateException(m.where());
            });
        }
        b.append(");\n    }\n");
    }

    static int indexOf(Member m, Kind k) {
        for (int i = 0; i < m.params().size(); i++) if (PortSpec.kindOf(m.params().get(i)) == k) return i;
        return -1;
    }

    // ───────────────────────── compat ─────────────────────────

    /** 호환 스텁 이름: NMS 단순 이름 + $ + 멤버(지문이면 f_<지문>). 같은 클래스의 겹친 이름은 $2, $3 … */
    static Map<Member, String> stubNames(Cls c) {
        Map<Member, String> out = new LinkedHashMap<>();
        Map<String, Integer> seen = new LinkedHashMap<>();
        for (Member m : c.members()) {
            String base = c.nmsSimple() + "$" + (m.name().startsWith("#") ? "f_" + m.name().substring(1) : m.name());
            int k = seen.merge(base, 1, Integer::sum);
            out.put(m, k == 1 ? base : base + "$" + k);
        }
        return out;
    }

    static String compat(PortSpec s) {
        String port = API_PKG + "." + s.camel() + "Port";
        StringBuilder b = new StringBuilder(header(s));
        b.append("package ").append(COMPAT_PKG).append(";\n\n");
        b.append("import org.domi.furnace.api.internal.port.PortRefused;\n");
        b.append("import org.domi.furnace.compat.nms.link.LinkTarget;\n");
        b.append("import org.domi.furnace.compat.nms.port.Peers;\n\n");
        b.append("/** ").append(s.file).append(" 의 호환 쪽 스텁 — 연결 줄(").append(s.area).append('-').append(s.areaName)
                .append(".ports.links)이 껍데기 멤버를 여기로 잇는다. 짝 찾기 → 다리 → 반환. */\n");
        b.append("public final class ").append(s.camel()).append("Ports {\n");
        b.append("    private ").append(s.camel()).append("Ports() {}\n\n");
        // 다리는 처음 쓸 때 찾는다(서버 없이 올린 호환 계층이면 부를 때마다 IllegalStateException — 클래스 초기화 오류로 굳지 않게).
        // 서버 구현은 상태 없는 싱글턴이라 두 스레드가 함께 채워도 같은 객체다.
        b.append("    private static ").append(port).append(" bridge;\n\n");
        b.append("    private static ").append(port).append(" b() {\n");
        b.append("        ").append(port).append(" x = bridge;\n");
        b.append("        if (x == null) bridge = x = org.domi.furnace.api.internal.port.PortBridges.get(").append(port).append(".class);\n");
        b.append("        return x;\n    }\n");
        int n = 0;
        StringBuilder consts = new StringBuilder();
        StringBuilder body = new StringBuilder();
        for (Cls c : s.classes) {
            Map<Member, String> names = stubNames(c);
            for (Member m : c.members()) {
                String w = "W" + n++;
                consts.append("    private static final String ").append(w).append(" = \"").append(what(c, m)).append("\";\n");
                compatMember(body, c, m, names.get(m), w);
            }
        }
        b.append('\n').append(consts).append(body);
        return b.append("}\n").toString();
    }

    private static void compatMember(StringBuilder b, Cls c, Member m, String stub, String w) {
        List<String> ps = new ArrayList<>();
        if (!m.isStatic()) ps.add("Object self");
        for (int i = 0; i < m.params().size(); i++) {
            String t = m.params().get(i);
            ps.add(switch (PortSpec.kindOf(t)) {
                case PRIM -> PortSpec.primName(t) + " a" + i;
                case STRING -> "String a" + i;
                default -> "Object a" + i;   // 받는 쪽·NMS 형식은 Object — 껍데기에만 있는 형식을 이름으로 쓰지 않는다(Peers 가 바꾼다)
            });
        }
        String ret = retType(m);
        b.append("\n    @LinkTarget(check = \"port\")\n    public static ").append(ret).append(' ').append(stub).append('(').append(String.join(", ", ps)).append(") {\n");
        b.append("        try {\n");
        List<String> call = new ArrayList<>();
        if (!m.isStatic()) call.add("Peers.block(self, " + w + ")");
        for (int i = 0; i < m.params().size(); i++) {
            String a = "a" + i;
            switch (PortSpec.kindOf(m.params().get(i))) {
                case PRIM, STRING -> call.add(a);
                case STATE -> call.add("Peers.state(" + a + ", " + w + ")");
                case POS -> {
                    // 그 순간 값(가변 자리라도) — 바닐라와 같은 결과
                    b.append("            net.minecraft.core.BlockPos p").append(i).append(" = Peers.pos(").append(a).append(", ").append(w).append(");\n");
                    call.add("p" + i + ".getX()");
                    call.add("p" + i + ".getY()");
                    call.add("p" + i + ".getZ()");
                }
                case LEVEL -> call.add("Peers.world(" + a + ", " + w + ")");
                case RANDOM -> call.add("Peers.random(" + a + ", " + w + ")");
                default -> throw new IllegalStateException(m.where());
            }
        }
        b.append("            ").append(ret.equals("void") ? "" : "return ").append("b().").append(PortSpec.bridgeName(c, m)).append('(').append(String.join(", ", call)).append(");\n");
        b.append("        } catch (PortRefused e) {\n            throw Peers.refused(e);\n        }\n    }\n");
    }

    // ───────────────────────── 연결 줄 ─────────────────────────

    static String links(PortSpec s) {
        StringBuilder b = new StringBuilder("#furnace-links 1 " + s.version + " area=" + s.area + "\n");
        b.append("# 자동 생성 — ").append(s.file).append(" (portgen). 손으로 고치지 않는다. 이식 줄은 대상이 서버 쪽에서 스스로 소유 검사를 한다(check=port).\n");
        for (Cls c : s.classes) {
            if (c.members().isEmpty()) continue;
            b.append("class ").append(c.nms()).append('\n');
            Map<Member, String> names = stubNames(c);
            for (Member m : c.members()) {
                b.append("  ").append(m.name()).append(m.desc()).append(" -> ").append(COMPAT_PKG).append('.').append(s.camel()).append("Ports.")
                        .append(names.get(m)).append(" port\n");
            }
        }
        return b.toString();
    }
}
