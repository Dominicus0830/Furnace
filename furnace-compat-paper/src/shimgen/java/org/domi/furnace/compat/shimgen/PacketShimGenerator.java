// 빌드 때 도는 생성기: 중립 패킷 스키마(furnace-api protocol-767.schema)에서 net.minecraft 패킷 shim 소스를 만든다.
// 결과물은 build/ 아래에만 생기고 커밋하지 않는다. 손으로 쓴 shim(src/main/java)이 있는 클래스는 만들지 않는다(손으로 쓴 것이 이긴다).
//
// 만드는 것
//  - 패킷 클래스: Mojang 이름·필드 이름·필드 타입·필드 순서(바닐라 선언 순서), record 는 record, class 는 private final 필드 +
//    모든 필드 생성자(바닐라에 같은 public 생성자가 있을 때만 public), get= 접근자, singleton 은 INSTANCE.
//  - 패킷에 딸린 내포·보조 타입(스키마 record/class/interface/enum/base 줄).
//  - 필드에 나오는 net.minecraft 타입 중 호환 계층에 없는 것: 빈 shim(enum 은 상수까지). 멤버를 부르면 기존 덫(UnsupportedNmsLink)이 잡는다.
//  - 패킷 처리자 표지 인터페이스(상태·방향별) 중 없는 것.
//  - 덧붙임 파일(packet-shim-extras.txt)의 손으로 쓴 멤버(바닐라 편의 생성자·변환 접근자·상수)를 해당 클래스 몸통에 넣는다.
// Mojang 라이브러리 타입(authlib·DFU·fastutil)은 호환 계층 컴파일 경로에 없어 Object 로 지운다(그 서술자를 쓰는 생성자는 바닐라와 달라진다).
package org.domi.furnace.compat.shimgen;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.domi.furnace.api.packet.PacketDirection;
import org.domi.furnace.api.packet.schema.FieldDef;
import org.domi.furnace.api.packet.schema.PacketDef;
import org.domi.furnace.api.packet.schema.PacketSchema;
import org.domi.furnace.api.packet.schema.StructDef;
import org.domi.furnace.api.packet.schema.TypeDecl;
import org.domi.furnace.api.packet.schema.TypeRef;

public final class PacketShimGenerator {

    static final String PROTO = "net.minecraft.network.protocol.";
    static final String SCHEMA_PACKET = "org.domi.furnace.compat.nms.SchemaPacket";

    /** 상태별 (clientbound, serverbound) 처리자 인터페이스. */
    static final Map<String, String[]> LISTENERS = Map.of(
            "game", new String[] {PROTO + "game.ClientGamePacketListener", PROTO + "game.ServerGamePacketListener"},
            "common", new String[] {PROTO + "common.ClientCommonPacketListener", PROTO + "common.ServerCommonPacketListener"},
            "configuration", new String[] {PROTO + "configuration.ClientConfigurationPacketListener", PROTO + "configuration.ServerConfigurationPacketListener"},
            "login", new String[] {PROTO + "login.ClientLoginPacketListener", PROTO + "login.ServerLoginPacketListener"},
            "status", new String[] {PROTO + "status.ClientStatusPacketListener", PROTO + "status.ServerStatusPacketListener"},
            "cookie", new String[] {PROTO + "cookie.ClientCookiePacketListener", PROTO + "cookie.ServerCookiePacketListener"},
            "ping", new String[] {PROTO + "ping.ClientPongPacketListener", PROTO + "ping.ServerPingPacketListener"},
            "handshake", new String[] {null, PROTO + "handshake.ServerHandshakePacketListener"});

    /** 처리자 인터페이스의 상위(1.21.1 계층). */
    static final Map<String, List<String>> LISTENER_PARENTS = new LinkedHashMap<>();

    static {
        String b = "net.minecraft.network.";
        // 1.21.1 바닐라 계층(바이트코드 대조: tools/compat/packet-schema-check 에 shim 클래스 폴더를 주면 본다).
        // game.ServerPacketListener 는 서버 쪽 처리자의 공통 부모(바닐라는 Logger·onPacketError 기본 메서드를 가진다, Mojang 이름은 가정).
        String sp = PROTO + "game.ServerPacketListener";
        LISTENER_PARENTS.put(b + "ClientboundPacketListener", List.of(b + "PacketListener"));
        LISTENER_PARENTS.put(b + "ServerboundPacketListener", List.of(b + "PacketListener"));
        LISTENER_PARENTS.put(sp, List.of(b + "ServerboundPacketListener"));
        LISTENER_PARENTS.put(PROTO + "cookie.ClientCookiePacketListener", List.of(b + "ClientboundPacketListener"));
        LISTENER_PARENTS.put(PROTO + "cookie.ServerCookiePacketListener", List.of(sp));
        LISTENER_PARENTS.put(PROTO + "ping.ClientPongPacketListener", List.of(b + "PacketListener"));
        LISTENER_PARENTS.put(PROTO + "ping.ServerPingPacketListener", List.of(b + "PacketListener"));
        LISTENER_PARENTS.put(PROTO + "common.ClientCommonPacketListener", List.of(PROTO + "cookie.ClientCookiePacketListener", b + "ClientboundPacketListener"));
        LISTENER_PARENTS.put(PROTO + "common.ServerCommonPacketListener", List.of(PROTO + "cookie.ServerCookiePacketListener", sp));
        LISTENER_PARENTS.put(PROTO + "game.ClientGamePacketListener", List.of(PROTO + "ping.ClientPongPacketListener", PROTO + "common.ClientCommonPacketListener"));
        LISTENER_PARENTS.put(PROTO + "game.ServerGamePacketListener", List.of(PROTO + "ping.ServerPingPacketListener", PROTO + "common.ServerCommonPacketListener"));
        LISTENER_PARENTS.put(PROTO + "configuration.ClientConfigurationPacketListener", List.of(PROTO + "common.ClientCommonPacketListener"));
        LISTENER_PARENTS.put(PROTO + "configuration.ServerConfigurationPacketListener", List.of(PROTO + "common.ServerCommonPacketListener"));
        LISTENER_PARENTS.put(PROTO + "login.ClientLoginPacketListener", List.of(PROTO + "cookie.ClientCookiePacketListener", b + "ClientboundPacketListener"));
        LISTENER_PARENTS.put(PROTO + "login.ServerLoginPacketListener", List.of(PROTO + "cookie.ServerCookiePacketListener", sp));
        LISTENER_PARENTS.put(PROTO + "status.ClientStatusPacketListener", List.of(PROTO + "ping.ClientPongPacketListener", b + "ClientboundPacketListener"));
        LISTENER_PARENTS.put(PROTO + "status.ServerStatusPacketListener", List.of(sp, PROTO + "ping.ServerPingPacketListener"));
        LISTENER_PARENTS.put(PROTO + "handshake.ServerHandshakePacketListener", List.of(sp));
    }

    private final PacketSchema schema;
    private final Path srcDir;
    private final Map<String, String> extras;
    private final Set<String> usedExtras = new LinkedHashSet<>();
    /** 맨 위 클래스 '$' 없는 전체 이름 → 그 파일의 내용 조각들. */
    private final Map<String, Unit> units = new TreeMap<>();
    /** 지운 타입(Object 로). */
    private final Set<String> erased = new LinkedHashSet<>();

    PacketShimGenerator(PacketSchema schema, Path srcDir, Map<String, String> extras) {
        this.schema = schema;
        this.srcDir = srcDir;
        this.extras = extras;
    }

    /** 인자: 출력 폴더, 손으로 쓴 소스 폴더(src/main/java), 덧붙임 파일. */
    public static void main(String[] args) throws IOException {
        Path out = Path.of(args[0]);
        Path src = Path.of(args[1]);
        Map<String, String> extras = readExtras(Files.readString(Path.of(args[2]), StandardCharsets.UTF_8));
        PacketShimGenerator g = new PacketShimGenerator(PacketSchema.protocol767(), src, extras);
        Map<String, String> files = g.generate();
        deleteTree(out);
        for (Map.Entry<String, String> e : files.entrySet()) {
            Path p = out.resolve(e.getKey().replace('.', '/') + ".java");
            Files.createDirectories(p.getParent());
            Files.writeString(p, e.getValue(), StandardCharsets.UTF_8);
        }
        System.out.println("[shimgen] 패킷 스키마 " + g.schema.packets().size() + "종 → 생성 파일 " + files.size() + "개, 지운 라이브러리 타입 " + g.erased);
    }

    // ------------------------------------------------------------------ 덧붙임

    /** "@@ 이름" 으로 나뉜 조각들. 이름은 '$' 이름(net.minecraft...Outer$Inner). */
    static Map<String, String> readExtras(String text) {
        Map<String, String> m = new LinkedHashMap<>();
        String key = null;
        StringBuilder sb = new StringBuilder();
        for (String line : text.split("\r?\n", -1)) {
            if (line.startsWith("@@")) {
                if (key != null) put(m, key, sb.toString());
                key = line.substring(2).trim();
                sb.setLength(0);
            } else if (key != null) {
                sb.append(line).append('\n');
            } else if (!line.isBlank() && !line.startsWith("//")) {
                throw new IllegalArgumentException("덧붙임 파일: @@ 앞에 내용이 있다: " + line);
            }
        }
        if (key != null) put(m, key, sb.toString());
        return m;
    }

    private static void put(Map<String, String> m, String key, String body) {
        if (m.put(key, body) != null) throw new IllegalArgumentException("덧붙임 파일: 같은 클래스가 두 번: " + key);
    }

    // ------------------------------------------------------------------ 있음 판정

    /** 손으로 쓴 src 에 이 '$' 이름의 클래스가 있는가. */
    boolean handWritten(String binaryName) {
        String top = topLevel(binaryName);
        Path p = srcDir.resolve(top.replace('.', '/') + ".java");
        if (!Files.exists(p)) return false;
        if (top.equals(binaryName)) return true;
        String inner = binaryName.substring(binaryName.lastIndexOf('$') + 1);
        try {
            return Pattern.compile("\\b(class|interface|enum|record)\\s+" + inner + "\\b").matcher(Files.readString(p)).find();
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    static String topLevel(String binaryName) {
        int d = binaryName.indexOf('$');
        return d < 0 ? binaryName : binaryName.substring(0, d);
    }

    // ------------------------------------------------------------------ 생성

    Map<String, String> generate() {
        // 1) 바깥 타입: 라이브러리는 지우고, 없는 net.minecraft 타입은 빈 shim
        for (TypeDecl d : schema.types().values()) {
            if (d.kind() == TypeDecl.Kind.LIB) { erased.add(d.binaryName()); continue; }
            if (!d.binaryName().startsWith("net.minecraft.")) throw new IllegalStateException("net.minecraft 밖 타입은 lib 로: " + d.binaryName());
            if (handWritten(d.binaryName())) continue;
            if (!topLevel(d.binaryName()).equals(d.binaryName()) && handWritten(topLevel(d.binaryName()))) {
                // 바깥이 손으로 쓴 shim 인데 안쪽이 없다 — 손으로 쓴 파일에 끼울 수 없어 지운다
                erased.add(d.binaryName());
                continue;
            }
            unit(d.binaryName()).decls.put(d.binaryName(), stub(d));
        }
        // 2) 처리자 인터페이스
        for (Map.Entry<String, List<String>> e : LISTENER_PARENTS.entrySet()) {
            if (handWritten(e.getKey())) continue;
            StringBuilder sb = new StringBuilder();
            sb.append("/** 패킷 처리자 표지(Mojang 이름). 패킷 제네릭 인자로만 쓰인다. */\n");
            sb.append("public interface ").append(simple(e.getKey())).append(" extends ");
            sb.append(String.join(", ", e.getValue().stream().map(PacketShimGenerator::dotted).toList())).append(" {}\n");
            unit(e.getKey()).decls.put(e.getKey(), sb.toString());
        }
        // 3) 보조·내포 타입과 패킷
        for (StructDef s : schema.structs()) {
            if (handWritten(s.binaryName())) {
                if (extras.containsKey(s.binaryName())) throw new IllegalStateException("손으로 쓴 클래스에 덧붙임이 있다: " + s.binaryName());
                continue;
            }
            unit(s.binaryName()).decls.put(s.binaryName(), struct(s));
        }
        for (PacketDef p : schema.packets()) {
            if (handWritten(p.binaryName())) {
                if (extras.containsKey(p.binaryName())) throw new IllegalStateException("손으로 쓴 클래스에 덧붙임이 있다: " + p.binaryName());
                continue;
            }
            unit(p.binaryName()).decls.put(p.binaryName(), packet(p));
        }
        for (String k : extras.keySet()) {
            if (!usedExtras.contains(k)) throw new IllegalStateException("덧붙임 파일의 클래스가 생성 대상이 아니다: " + k);
        }
        // 4) 파일로 조립: 바깥 클래스 선언 안에 '$' 내포 선언을 끼운다
        Map<String, String> files = new TreeMap<>();
        for (Map.Entry<String, Unit> e : units.entrySet()) files.put(e.getKey(), assemble(e.getKey(), e.getValue()));
        return files;
    }

    private static final class Unit {
        final Map<String, String> decls = new LinkedHashMap<>();   // '$' 이름 → 선언 글(닫는 } 앞에 내포를 끼울 자리 표시)
    }

    private Unit unit(String binaryName) { return units.computeIfAbsent(topLevel(binaryName), k -> new Unit()); }

    private static final String NESTED_MARK = "    //@@NESTED@@\n";

    private String assemble(String top, Unit u) {
        String topDecl = u.decls.get(top);
        if (topDecl == null) throw new IllegalStateException("내포 타입의 바깥 클래스가 생성 대상이 아니다: " + top + " ← " + u.decls.keySet());
        // 깊은 것부터 바깥에 끼운다
        List<String> nested = new ArrayList<>(u.decls.keySet());
        nested.remove(top);
        nested.sort(Comparator.comparingLong((String n) -> n.chars().filter(c -> c == '$').count()).reversed());
        Map<String, String> decl = new LinkedHashMap<>(u.decls);
        for (String n : nested) {
            String outer = n.substring(0, n.lastIndexOf('$'));
            String outerDecl = decl.get(outer);
            if (outerDecl == null) throw new IllegalStateException("바깥 선언이 없다: " + n);
            // 안쪽은 이미 자기 내포를 다 받았다 — 표시를 지우고 바깥 표시 앞에 끼운다
            decl.put(outer, outerDecl.replace(NESTED_MARK, "\n" + indent(decl.get(n).replace(NESTED_MARK, "")) + NESTED_MARK));
        }
        String body = decl.get(top).replace(NESTED_MARK, "");
        String pkg = top.substring(0, top.lastIndexOf('.'));
        return "// 생성됨: furnace-compat-paper shimgen ← furnace-api packet/schema/protocol-767.schema. 고치지 말 것(스키마·덧붙임 파일을 고친다).\n"
                + "package " + pkg + ";\n\n" + body;
    }

    private static String indent(String s) {
        StringBuilder sb = new StringBuilder();
        for (String line : s.split("\n", -1)) {
            if (line.isEmpty()) sb.append('\n');
            else sb.append("    ").append(line).append('\n');
        }
        return sb.toString().stripTrailing() + "\n";
    }

    // ------------------------------------------------------------------ 타입 글

    static String dotted(String binaryName) { return binaryName.replace('$', '.'); }

    static String simple(String binaryName) {
        String s = binaryName.substring(binaryName.lastIndexOf('.') + 1);
        return s.substring(s.lastIndexOf('$') + 1);
    }

    private boolean isNested(String binaryName) { return binaryName.indexOf('$') >= 0; }

    /** 스키마 타입 식 → Java 소스 타입. */
    String java(TypeRef t, String pkg) {
        switch (t.bound()) {
            case WILDCARD: return "?";
            case EXTENDS: return "? extends " + java(new TypeRef(t.name(), t.arguments(), t.arrayDimensions(), TypeRef.Bound.NONE), pkg);
            case SUPER: return "? super " + java(new TypeRef(t.name(), t.arguments(), t.arrayDimensions(), TypeRef.Bound.NONE), pkg);
            default: break;
        }
        String bin = schema.resolve(t.name(), pkg);
        StringBuilder sb = new StringBuilder();
        if (erased.contains(bin)) {
            sb.append("java.lang.Object");
        } else {
            sb.append(dotted(bin));
            if (!t.arguments().isEmpty()) {
                sb.append('<');
                for (int i = 0; i < t.arguments().size(); i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(java(t.arguments().get(i), pkg));
                }
                sb.append('>');
            }
        }
        sb.append("[]".repeat(t.arrayDimensions()));
        return sb.toString();
    }

    private String stub(TypeDecl d) {
        String name = simple(d.binaryName());
        String params = d.typeParameters().isEmpty() ? "" : "<" + String.join(", ", d.typeParameters()) + ">";
        String st = isNested(d.binaryName()) ? "static " : "";
        String doc = "/** 빈 shim(Mojang 이름) — 패킷 shim 의 필드 타입 자리. 멤버는 아직 없다(부르면 UnsupportedNmsLink). */\n";
        return switch (d.kind()) {
            case CLASS -> doc + "public " + st + "class " + name + params + " {\n\n    private " + name + "() {}\n" + NESTED_MARK + "}\n";
            case ABSTRACT -> doc + "public " + st + "abstract class " + name + params + " {\n\n    protected " + name + "() {}\n" + NESTED_MARK + "}\n";
            case INTERFACE -> doc + "public interface " + name + params + " {\n" + NESTED_MARK + "}\n";
            case RECORD -> doc + "public record " + name + params + "() {\n" + NESTED_MARK + "}\n";
            case ENUM -> doc + "public enum " + name + " {\n    " + String.join(", ", d.constants()) + ";\n" + NESTED_MARK + "}\n";
            case LIB -> throw new IllegalStateException();
        };
    }

    private String listener(PacketDef p) {
        String l = LISTENERS.get(p.state())[p.direction() == PacketDirection.CLIENTBOUND ? 0 : 1];
        if (l == null) throw new IllegalStateException("처리자 없음: " + p.binaryName());
        return dotted(l);
    }

    private String struct(StructDef s) {
        String name = simple(s.binaryName());
        String vis = s.privateAccess() ? "private " : "public ";
        String st = isNested(s.binaryName()) && s.kind() != StructDef.Kind.ENUM && s.kind() != StructDef.Kind.INTERFACE && s.kind() != StructDef.Kind.RECORD ? "static " : "";
        String extra = takeExtras(s.binaryName());
        StringBuilder sb = new StringBuilder();
        sb.append("/** 스키마 보조 타입(Mojang 이름). */\n");
        switch (s.kind()) {
            case ENUM -> sb.append(vis).append("enum ").append(name).append(" {\n    ").append(String.join(", ", s.constants())).append(";\n");
            case INTERFACE -> sb.append(vis).append("interface ").append(name).append(" {\n");
            case RECORD -> {
                sb.append(vis).append("record ").append(name).append('(').append(components(s.fields(), s.javaPackage())).append(") {\n");
            }
            case CLASS, ABSTRACT -> {
                boolean abs = s.kind() == StructDef.Kind.ABSTRACT;
                // 이 base 를 부모로 삼는 패킷이 있으면 그 처리자의 Packet 을 구현한다
                String impl = "";
                for (PacketDef p : schema.packets()) {
                    if (p.superType() != null && schema.resolve(p.superType().name(), p.javaPackage()).equals(s.binaryName())) {
                        impl = " implements net.minecraft.network.protocol.Packet<" + listener(p) + ">";
                        break;
                    }
                }
                sb.append(vis).append(st).append(abs ? "abstract " : "").append("class ").append(name).append(impl).append(" {\n");
                fields(sb, s.fields(), s.javaPackage(), abs ? "protected" : "private");
                if (!s.fields().isEmpty()) ctor(sb, name, s.fields(), s.javaPackage(), abs ? "protected" : (s.publicConstructor() ? "public" : "private"));
                getters(sb, s.fields(), s.javaPackage());
            }
        }
        if (!extra.isEmpty()) sb.append('\n').append(extra);
        sb.append(NESTED_MARK).append("}\n");
        return sb.toString();
    }

    private String packet(PacketDef p) {
        String name = simple(p.binaryName());
        String pkg = p.javaPackage();
        boolean nested = isNested(p.binaryName());
        String extra = takeExtras(p.binaryName());
        String key = p.state() + "/" + p.direction().name().toLowerCase(java.util.Locale.ROOT) + "/" + p.wireName();
        StringBuilder sb = new StringBuilder();
        sb.append("/** ").append(p.direction() == PacketDirection.CLIENTBOUND ? "clientbound" : "serverbound").append(' ')
                .append(p.state()).append('/').append(p.wireName()).append(" 패킷 shim(Mojang 이름, 스키마 생성). */\n");
        String packetItf = "net.minecraft.network.protocol.Packet<" + listener(p) + ">";
        List<FieldDef> values = p.fields();
        if (p.kind() == PacketDef.Kind.RECORD) {
            sb.append("public record ").append(name).append('(').append(components(p.fields(), pkg)).append(")\n        implements ")
                    .append(packetItf).append(", ").append(SCHEMA_PACKET).append(" {\n");
        } else {
            String sup = p.superType() == null ? "" : " extends " + java(p.superType(), pkg);
            boolean inheritsPacket = p.superType() != null && schema.struct(schema.resolve(p.superType().name(), pkg)).isPresent();
            sb.append("public ").append(nested ? "static " : "").append("class ").append(name).append(sup).append("\n        implements ")
                    .append(inheritsPacket ? "" : packetItf + ", ").append(SCHEMA_PACKET).append(" {\n");
            if (p.kind() == PacketDef.Kind.SINGLETON) {
                sb.append("\n    public static final ").append(name).append(" INSTANCE = new ").append(name).append("();\n");
                sb.append("\n    private ").append(name).append("() {}\n");
            } else {
                fields(sb, p.fields(), pkg, "private");
                if (!p.fields().isEmpty()) ctor(sb, name, p.fields(), pkg, p.publicConstructor() ? "public" : "private");
                getters(sb, p.fields(), pkg);
                if (inheritsPacket && p.fields().isEmpty()) {
                    values = schema.struct(schema.resolve(p.superType().name(), pkg)).orElseThrow().fields();
                }
            }
        }
        sb.append("\n    @Override\n    public String schemaKey() { return \"").append(key).append("\"; }\n");
        sb.append("\n    @Override\n    public Object[] schemaValues() { return new Object[] {");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(values.get(i).name());
        }
        sb.append("}; }\n");
        if (!extra.isEmpty()) sb.append('\n').append(extra);
        sb.append(NESTED_MARK).append("}\n");
        return sb.toString();
    }

    private String takeExtras(String binaryName) {
        String e = extras.get(binaryName);
        if (e == null) return "";
        usedExtras.add(binaryName);
        return indent(e.strip());
    }

    private String components(List<FieldDef> fields, String pkg) {
        List<String> c = new ArrayList<>();
        for (FieldDef f : fields) c.add(java(f.type(), pkg) + " " + f.name());
        return String.join(", ", c);
    }

    private void fields(StringBuilder sb, List<FieldDef> fields, String pkg, String vis) {
        if (fields.isEmpty()) return;
        sb.append('\n');
        for (FieldDef f : fields) sb.append("    ").append(vis).append(" final ").append(java(f.type(), pkg)).append(' ').append(f.name()).append(";\n");
    }

    private void ctor(StringBuilder sb, String name, List<FieldDef> fields, String pkg, String vis) {
        sb.append("\n    ").append(vis).append(' ').append(name).append('(').append(components(fields, pkg)).append(") {\n");
        for (FieldDef f : fields) sb.append("        this.").append(f.name()).append(" = ").append(f.name()).append(";\n");
        sb.append("    }\n");
    }

    private void getters(StringBuilder sb, List<FieldDef> fields, String pkg) {
        boolean first = true;
        for (FieldDef f : fields) {
            if (f.accessor() == null) continue;
            if (first) { sb.append('\n'); first = false; }
            sb.append("    public ").append(java(f.type(), pkg)).append(' ').append(f.accessor()).append("() { return ").append(f.name()).append("; }\n");
        }
    }

    private static void deleteTree(Path p) throws IOException {
        if (!Files.exists(p)) return;
        try (Stream<Path> s = Files.walk(p)) {
            for (Path q : s.sorted(Comparator.reverseOrder()).toList()) Files.delete(q);
        }
    }
}
