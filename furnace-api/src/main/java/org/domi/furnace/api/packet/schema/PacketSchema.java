// 버전별 패킷 모양 표(중립 스키마). 손으로 쓴 텍스트 파일(protocol-767.schema)을 읽어 패킷마다 Mojang 클래스 이름, 필드 이름, 필드 타입, 와이어 종류를 준다.
// 바닐라 코드나 생성 결과물이 아니라 이름과 타입 목록뿐이다.
//
// 쓰는 곳은 두 군데다.
//   호환 계층이 빌드 때 net.minecraft 패킷 shim 을 만들 때
//   서버 범용 코덱이 와이어를 읽고 쓸 때
//
// 문법은 스키마 파일 머리말에 있다.
package org.domi.furnace.api.packet.schema;

import org.domi.furnace.api.GameVersion;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.domi.furnace.api.packet.PacketDirection;

public final class PacketSchema {

    /** 상태 이름에 대응하는 Mojang 패킷 패키지. */
    public static final Map<String, String> STATE_PACKAGES;

    static {
        Map<String, String> m = new LinkedHashMap<>();
        for (String s : List.of("handshake", "status", "ping", "cookie", "login", "configuration", "common", "game")) {
            m.put(s, "net.minecraft.network.protocol." + s);
        }
        STATE_PACKAGES = Collections.unmodifiableMap(m);
    }

    /** 필드 타입에 쓰는 JDK 이름. 단순 이름을 전체 이름에 대응시킨다. */
    public static final Map<String, String> BUILTIN_TYPES = Map.ofEntries(
            Map.entry("String", "java.lang.String"), Map.entry("Object", "java.lang.Object"),
            Map.entry("UUID", "java.util.UUID"), Map.entry("List", "java.util.List"), Map.entry("Set", "java.util.Set"),
            Map.entry("Map", "java.util.Map"), Map.entry("Optional", "java.util.Optional"),
            Map.entry("Collection", "java.util.Collection"), Map.entry("EnumSet", "java.util.EnumSet"),
            Map.entry("BitSet", "java.util.BitSet"), Map.entry("Instant", "java.time.Instant"));

    /** 필드 줄에 직접 적을 수 있는 와이어 이름. 크기나 부호만 다른 경우에 쓴다. */
    public static final Set<String> EXPLICIT_WIRES = Set.of(
            "varint", "varlong", "i8", "u8", "i16", "u16", "i32", "i64", "f32", "f64", "bool", "string", "uuid", "bytes");

    private static final Set<String> PRIMITIVES = Set.of("int", "long", "short", "byte", "float", "double", "boolean", "char");

    private static volatile PacketSchema current;

    private final int protocol;
    private final List<PacketDef> packets;
    private final Map<String, TypeDecl> types;          // binaryName → 선언
    private final Map<String, TypeDecl> typesBySimple;  // BossEvent.BossBarColor → 선언
    private final List<StructDef> structs;
    private final Map<String, StructDef> structsByBinary;
    private final Map<String, PacketDef> packetsByBinary;
    private final Map<String, PacketDef> packetsByKey;   // state/direction/wire

    private PacketSchema(int protocol, List<PacketDef> packets, List<TypeDecl> types, List<StructDef> structs) {
        this.protocol = protocol;
        this.packets = List.copyOf(packets);
        this.structs = List.copyOf(structs);
        Map<String, TypeDecl> t = new LinkedHashMap<>();
        Map<String, TypeDecl> ts = new HashMap<>();
        for (TypeDecl d : types) { t.put(d.binaryName(), d); ts.put(d.simpleName(), d); }
        this.types = Collections.unmodifiableMap(t);
        this.typesBySimple = ts;
        Map<String, StructDef> sb = new LinkedHashMap<>();
        for (StructDef s : structs) sb.put(s.binaryName(), s);
        this.structsByBinary = Collections.unmodifiableMap(sb);
        Map<String, PacketDef> pb = new LinkedHashMap<>();
        Map<String, PacketDef> pk = new HashMap<>();
        for (PacketDef p : packets) { pb.put(p.binaryName(), p); pk.put(key(p.state(), p.direction(), p.wireName()), p); }
        this.packetsByBinary = Collections.unmodifiableMap(pb);
        this.packetsByKey = pk;
    }

    /** 1.21.1(프로토콜 767) 스키마. 처음 부를 때 리소스를 읽는다. */
    public static PacketSchema current() {
        PacketSchema s = current;
        if (s == null) {
            synchronized (PacketSchema.class) {
                s = current;
                if (s == null) current = s = load(GameVersion.PROTOCOL, "protocol-" + GameVersion.PROTOCOL + ".schema");
            }
        }
        return s;
    }

    private static PacketSchema load(int protocol, String resource) {
        try (InputStream in = PacketSchema.class.getResourceAsStream(resource)) {
            if (in == null) throw new IllegalStateException("schema resource is missing: " + resource);
            return parse(protocol, new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public int protocol() { return protocol; }

    /** 모든 패킷. 파일 순서를 따른다. */
    public List<PacketDef> packets() { return packets; }

    /** 한 상태와 방향의 패킷. 파일 순서를 따른다. */
    public List<PacketDef> packets(String state, PacketDirection direction) {
        return packets.stream().filter(p -> p.state().equals(state) && p.direction() == direction).toList();
    }

    public Optional<PacketDef> packet(String state, PacketDirection direction, String wireName) {
        return Optional.ofNullable(packetsByKey.get(key(state, direction, wireName)));
    }

    /** '$' 이름으로 패킷을 찾는다(net.minecraft.network.protocol.game.ClientboundMoveEntityPacket$Pos). */
    public Optional<PacketDef> packetByClass(String binaryName) { return Optional.ofNullable(packetsByBinary.get(binaryName)); }

    /** 바깥 타입 선언. binaryName 에 대응하는 선언이며 파일 순서를 따른다. */
    public Map<String, TypeDecl> types() { return types; }

    /** 패킷에 딸린 내포 타입과 보조 타입. 파일 순서를 따른다. */
    public List<StructDef> structs() { return structs; }

    public Optional<StructDef> struct(String binaryName) { return Optional.ofNullable(structsByBinary.get(binaryName)); }

    /**
     * 필드 타입 식의 이름 하나를 '$' 전체 이름으로 푼다. 기본형은 그대로 돌려주고, 와일드카드 '?' 는 null 을 준다.
     * 풀 수 없으면 IllegalArgumentException 을 던진다.
     */
    public String resolve(String name, String javaPackage) {
        if (name.equals("?")) return null;
        if (PRIMITIVES.contains(name)) return name;
        String b = BUILTIN_TYPES.get(name);
        if (b != null) return b;
        String local = javaPackage + "." + name.replace('.', '$');
        if (structsByBinary.containsKey(local) || packetsByBinary.containsKey(local)) return local;
        TypeDecl d = typesBySimple.get(name);
        if (d != null) return d.binaryName();
        throw new IllegalArgumentException("unknown type name: " + name + " (package " + javaPackage + ")");
    }

    /** '$' 이름으로 바깥 타입 선언을 찾는다. */
    public Optional<TypeDecl> type(String binaryName) { return Optional.ofNullable(types.get(binaryName)); }

    /**
     * 필드의 와이어 종류. 명시 와이어가 있으면 그것을 쓰고, 없으면 Java 타입에서 정한다.
     *
     * <ul>
     * <li>기본형은 int=varint 처럼 정한다.
     * <li>String=string, UUID=uuid, byte[]=bytes, long[]=longs, int[]=varints, BitSet=bitset, Instant=instant
     * <li>List·Set·Collection=list&lt;원소&gt;, Optional=optional&lt;값&gt;, Map=map&lt;키,값&gt;
     * <li>Holder=holder, ResourceKey=identifier
     * <li>선언 타입은 그 wire= 값을 쓴다. 없으면 enum 은 varint, 나머지는 custom 이다.
     * <li>스키마 안의 record·class 는 struct:&lt;이름&gt; 이다.
     * </ul>
     *
     * <p>nullable 은 여기에 반영하지 않는다(FieldDef.nullable).
     */
    public String wireOf(FieldDef field, String javaPackage) {
        if (field.wire() != null) return field.wire();
        return wireOf(field.type(), javaPackage);
    }

    private String wireOf(TypeRef t, String pkg) {
        if (t.bound() == TypeRef.Bound.WILDCARD) return "custom";
        if (t.arrayDimensions() > 0) {
            if (t.arrayDimensions() == 1 && t.bound() == TypeRef.Bound.NONE) {
                switch (t.name()) {
                    case "byte": return "bytes";
                    case "long": return "longs";
                    case "int": return "varints";
                    default: break;
                }
            }
            return "array<" + wireOf(t.component(), pkg) + ">";
        }
        switch (t.name()) {
            case "int": return "varint";
            case "long": return "i64";
            case "short": return "i16";
            case "byte": return "i8";
            case "float": return "f32";
            case "double": return "f64";
            case "boolean": return "bool";
            case "String": return "string";
            case "UUID": return "uuid";
            case "BitSet": return "bitset";
            case "Instant": return "instant";
            case "List", "Set", "Collection", "EnumSet": return "list<" + arg(t, 0, pkg) + ">";
            case "Optional": return "optional<" + arg(t, 0, pkg) + ">";
            case "Map": return "map<" + arg(t, 0, pkg) + "," + arg(t, 1, pkg) + ">";
            case "Holder": return "holder";
            case "ResourceKey": return "identifier";
            default: break;
        }
        String bin = resolve(t.name(), pkg);
        TypeDecl d = types.get(bin);
        if (d != null) {
            if (d.wire() != null) return d.wire();
            return d.kind() == TypeDecl.Kind.ENUM ? "varint" : "custom";
        }
        StructDef s = structsByBinary.get(bin);
        if (s != null) return s.kind() == StructDef.Kind.ENUM ? "varint" : "struct:" + s.name();
        return "custom";
    }

    private String arg(TypeRef t, int i, String pkg) {
        if (t.arguments().size() <= i) return "custom";
        return wireOf(t.arguments().get(i), pkg);
    }

    private static String key(String state, PacketDirection d, String wire) { return state + '/' + d + '/' + wire; }

    // ------------------------------------------------------------------ 읽기

    /** 스키마 글을 읽는다. 문법 오류, 모르는 타입 이름, 겹치는 패킷이면 줄 번호를 붙여 IllegalArgumentException 을 던진다. */
    public static PacketSchema parse(int protocol, String text) {
        return new Reader(text).read(protocol);
    }

    private static final class Reader {
        private final String[] lines;
        private final List<PacketDef> packets = new ArrayList<>();
        private final List<TypeDecl> types = new ArrayList<>();
        private final List<StructDef> structs = new ArrayList<>();
        private final Map<Object, Integer> lineOf = new HashMap<>();

        private String state;
        private int lineNo;
        // 지금 필드를 받는 블록
        private Block open;

        Reader(String text) { this.lines = text.split("\r?\n", -1); }

        private final class Block {
            final List<FieldDef> fields = new ArrayList<>();
            final java.util.function.Function<List<FieldDef>, Object> finish;
            final boolean isClassLike;   // get= 를 허용하는가

            Block(java.util.function.Function<List<FieldDef>, Object> finish, boolean isClassLike) {
                this.finish = finish;
                this.isClassLike = isClassLike;
            }
        }

        PacketSchema read(int protocol) {
            for (int i = 0; i < lines.length; i++) {
                lineNo = i + 1;
                String raw = lines[i];
                int hash = raw.indexOf('#');
                String line = (hash >= 0 ? raw.substring(0, hash) : raw).stripTrailing();
                if (line.isBlank()) continue;
                try {
                    if (Character.isWhitespace(line.charAt(0))) field(line.strip());
                    else { close(); directive(line); }
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("schema line " + lineNo + ": " + e.getMessage(), e);
                }
            }
            close();
            PacketSchema schema = new PacketSchema(protocol, packets, types, structs);
            validate(schema);
            return schema;
        }

        private void close() {
            if (open == null) return;
            Object made = open.finish.apply(open.fields);
            open = null;
            if (made instanceof PacketDef p) packets.add(p);
            else if (made instanceof StructDef s) structs.add(s);
        }

        private void directive(String line) {
            String[] colon = splitConstants(line);
            List<String> w = List.of(colon[0].trim().split("\\s+"));
            List<String> consts = colon[1] == null ? List.of() : List.of(colon[1].trim().split("\\s+"));
            switch (w.get(0)) {
                case "state" -> {
                    need(w, 2);
                    if (!STATE_PACKAGES.containsKey(w.get(1))) throw new IllegalArgumentException("unknown state: " + w.get(1));
                    state = w.get(1);
                }
                case "type" -> typeDecl(w, consts);
                case "packet" -> packet(w);
                case "base" -> base(w);
                case "record", "class", "interface", "enum" -> nested(w, consts);
                default -> throw new IllegalArgumentException("unknown directive: " + w.get(0));
            }
        }

        private static String[] splitConstants(String line) {
            int c = line.indexOf(" : ");
            if (c < 0) return new String[] {line, null};
            return new String[] {line.substring(0, c), line.substring(c + 3)};
        }

        private void typeDecl(List<String> w, List<String> consts) {
            need(w, 3);
            TypeDecl.Kind kind = switch (w.get(1)) {
                case "class" -> TypeDecl.Kind.CLASS;
                case "abstract" -> TypeDecl.Kind.ABSTRACT;
                case "interface" -> TypeDecl.Kind.INTERFACE;
                case "record" -> TypeDecl.Kind.RECORD;
                case "enum" -> TypeDecl.Kind.ENUM;
                case "lib" -> TypeDecl.Kind.LIB;
                default -> throw new IllegalArgumentException("unknown type kind: " + w.get(1));
            };
            String name = w.get(2);
            List<String> params = List.of();
            int lt = name.indexOf('<');
            if (lt >= 0) {
                if (!name.endsWith(">")) throw new IllegalArgumentException("malformed type variable brackets: " + name);
                params = List.of(name.substring(lt + 1, name.length() - 1).split(","));
                name = name.substring(0, lt);
            }
            String wire = null;
            for (int i = 3; i < w.size(); i++) {
                if (w.get(i).startsWith("wire=")) wire = w.get(i).substring(5);
                else throw new IllegalArgumentException("unknown type option: " + w.get(i));
            }
            if (!consts.isEmpty() && kind != TypeDecl.Kind.ENUM) throw new IllegalArgumentException("constants are only allowed in an enum: " + name);
            types.add(new TypeDecl(kind, name, params, wire, consts));
        }

        private void packet(List<String> w) {
            need(w, 5);
            if (state == null) throw new IllegalArgumentException("no state before packet");
            PacketDirection dir = switch (w.get(1)) {
                case "clientbound" -> PacketDirection.CLIENTBOUND;
                case "serverbound" -> PacketDirection.SERVERBOUND;
                default -> throw new IllegalArgumentException("the direction must be clientbound|serverbound: " + w.get(1));
            };
            String wire = w.get(2);
            String cls = w.get(3);
            PacketDef.Kind kind = switch (w.get(4)) {
                case "record" -> PacketDef.Kind.RECORD;
                case "class" -> PacketDef.Kind.CLASS;
                case "singleton" -> PacketDef.Kind.SINGLETON;
                default -> throw new IllegalArgumentException("the packet kind must be record|class|singleton: " + w.get(4));
            };
            boolean ctor = kind == PacketDef.Kind.RECORD;
            boolean custom = false;
            TypeRef sup = null;
            for (int i = 5; i < w.size(); i++) {
                String f = w.get(i);
                if (f.equals("new")) ctor = true;
                else if (f.equals("custom")) custom = true;
                else if (f.startsWith("extends=")) sup = TypeRef.parse(f.substring(8));
                else throw new IllegalArgumentException("unknown packet flag: " + f);
            }
            String st = state;
            String pkg = STATE_PACKAGES.get(st);
            boolean fCtor = ctor, fCustom = custom;
            TypeRef fSup = sup;
            int at = lineNo;
            open = new Block(fields -> {
                if (kind == PacketDef.Kind.SINGLETON && !fields.isEmpty()) {
                    throw new IllegalArgumentException("schema line " + at + ": a singleton packet cannot have fields: " + cls);
                }
                PacketDef p = new PacketDef(st, dir, wire, pkg, cls, kind, fCtor, fCustom, fSup, fields);
                lineOf.put(p, at);
                return p;
            }, kind == PacketDef.Kind.CLASS);
        }

        private void base(List<String> w) {
            need(w, 2);
            if (state == null) throw new IllegalArgumentException("no state before base");
            StructDef.Kind kind = StructDef.Kind.CLASS;
            boolean pub = false;
            for (int i = 2; i < w.size(); i++) {
                switch (w.get(i)) {
                    case "abstract" -> kind = StructDef.Kind.ABSTRACT;
                    case "record" -> kind = StructDef.Kind.RECORD;
                    case "new" -> pub = true;
                    default -> throw new IllegalArgumentException("a base option must be abstract|record|new: " + w.get(i));
                }
            }
            if (pub && kind != StructDef.Kind.CLASS) throw new IllegalArgumentException("new is only allowed on a class: " + w.get(1));
            if (w.get(1).contains("$")) throw new IllegalArgumentException("base only takes an outer class: " + w.get(1));
            openStruct(w.get(1), kind, false, pub, List.of());
        }

        private void nested(List<String> w, List<String> consts) {
            need(w, 2);
            if (state == null) throw new IllegalArgumentException("no state before " + w.get(0));
            StructDef.Kind kind = switch (w.get(0)) {
                case "record" -> StructDef.Kind.RECORD;
                case "class" -> StructDef.Kind.CLASS;
                case "interface" -> StructDef.Kind.INTERFACE;
                default -> StructDef.Kind.ENUM;
            };
            boolean priv = false, pub = false;
            for (int i = 2; i < w.size(); i++) {
                if (w.get(i).equals("private")) priv = true;
                else if (w.get(i).equals("new")) pub = true;
                else throw new IllegalArgumentException("unknown option: " + w.get(i));
            }
            if (pub && (kind != StructDef.Kind.CLASS || priv)) throw new IllegalArgumentException("new is only allowed on a public class: " + w.get(1));
            if (!w.get(1).contains("$")) throw new IllegalArgumentException("a nested type must be written as Outer$Inner: " + w.get(1));
            if (kind == StructDef.Kind.ENUM && consts.isEmpty()) throw new IllegalArgumentException("the enum has no constants: " + w.get(1));
            if (kind != StructDef.Kind.ENUM && !consts.isEmpty()) throw new IllegalArgumentException("constants are only allowed in an enum: " + w.get(1));
            openStruct(w.get(1), kind, priv, pub, consts);
        }

        private void openStruct(String name, StructDef.Kind kind, boolean priv, boolean pub, List<String> consts) {
            String st = state;
            String pkg = STATE_PACKAGES.get(st);
            int at = lineNo;
            open = new Block(fields -> {
                if ((kind == StructDef.Kind.ENUM || kind == StructDef.Kind.INTERFACE) && !fields.isEmpty()) {
                    throw new IllegalArgumentException("schema line " + at + ": " + kind + " cannot have fields: " + name);
                }
                StructDef s = new StructDef(st, pkg, name, kind, priv, fields, consts, pub);
                lineOf.put(s, at);
                return s;
            }, kind == StructDef.Kind.CLASS || kind == StructDef.Kind.ABSTRACT);
        }

        private void field(String line) {
            if (open == null) throw new IllegalArgumentException("a field must be inside a packet or type block");
            // 타입 식은 꺾쇠 안의 공백(? extends)을 품을 수 있다 — 깊이 0 의 공백까지가 타입
            int depth = 0, i = 0;
            for (; i < line.length(); i++) {
                char c = line.charAt(i);
                if (c == '<') depth++;
                else if (c == '>') depth--;
                else if (Character.isWhitespace(c) && depth == 0) break;
            }
            if (depth != 0) throw new IllegalArgumentException("unbalanced angle brackets: " + line);
            TypeRef type = TypeRef.parse(line.substring(0, i));
            List<String> rest = List.of(line.substring(i).trim().split("\\s+"));
            if (rest.isEmpty() || rest.get(0).isEmpty()) throw new IllegalArgumentException("missing field name: " + line);
            String name = rest.get(0);
            if (!isIdentifier(name)) throw new IllegalArgumentException("the field name is not an identifier: " + name);
            String wire = null, accessor = null;
            boolean nullable = false;
            for (int k = 1; k < rest.size(); k++) {
                String o = rest.get(k);
                if (o.equals("nullable")) nullable = true;
                else if (o.startsWith("get=")) {
                    if (!open.isClassLike) throw new IllegalArgumentException("get= is only allowed on a class shape (a record uses the name as the accessor): " + name);
                    accessor = o.substring(4);
                    if (!isIdentifier(accessor)) throw new IllegalArgumentException("invalid accessor name: " + accessor);
                } else if (EXPLICIT_WIRES.contains(o)) {
                    if (wire != null) throw new IllegalArgumentException("wire given twice: " + name);
                    wire = o;
                } else throw new IllegalArgumentException("unknown field option: " + o);
            }
            for (FieldDef f : open.fields) {
                if (f.name().equals(name)) throw new IllegalArgumentException("duplicate field name: " + name);
            }
            open.fields.add(new FieldDef(type, name, wire, nullable, accessor));
        }

        private static boolean isIdentifier(String s) {
            if (s.isEmpty() || !Character.isJavaIdentifierStart(s.charAt(0))) return false;
            for (int i = 1; i < s.length(); i++) if (!Character.isJavaIdentifierPart(s.charAt(i))) return false;
            return true;
        }

        private static void need(List<String> w, int n) {
            if (w.size() < n) throw new IllegalArgumentException("not enough words (" + n + " needed): " + String.join(" ", w));
        }

        private void validate(PacketSchema s) {
            Map<String, Object> seen = new HashMap<>();
            for (PacketDef p : packets) {
                if (s.packetsByKey.size() != packets.size()) {
                    throw new IllegalArgumentException("packets with the same state, direction and wire name overlap");
                }
                if (seen.put(p.binaryName(), p) != null) {
                    throw new IllegalArgumentException("schema line " + lineOf.get(p) + ": duplicate packet class: " + p.binaryName());
                }
                if (p.outerName() != null) {
                    String outer = p.javaPackage() + "." + p.outerName();
                    if (!s.structsByBinary.containsKey(outer) && !s.packetsByBinary.containsKey(outer)) {
                        throw new IllegalArgumentException("schema line " + lineOf.get(p) + ": the outer class is not in the schema: " + outer);
                    }
                }
                if (p.superType() != null) checkType(s, p.superType(), p.javaPackage(), lineOf.get(p));
                for (FieldDef f : p.fields()) checkType(s, f.type(), p.javaPackage(), lineOf.get(p));
            }
            for (StructDef st : structs) {
                if (seen.put(st.binaryName(), st) != null) {
                    throw new IllegalArgumentException("schema line " + lineOf.get(st) + ": duplicate type name: " + st.binaryName());
                }
                if (st.outerName() != null) {
                    String outer = st.javaPackage() + "." + st.outerName();
                    if (!s.structsByBinary.containsKey(outer) && !s.packetsByBinary.containsKey(outer)) {
                        throw new IllegalArgumentException("schema line " + lineOf.get(st) + ": the outer class is not in the schema: " + outer);
                    }
                }
                for (FieldDef f : st.fields()) checkType(s, f.type(), st.javaPackage(), lineOf.get(st));
            }
            Map<String, TypeDecl> simple = new HashMap<>();
            for (TypeDecl d : types) {
                if (simple.put(d.simpleName(), d) != null) {
                    throw new IllegalArgumentException("duplicate short type name: " + d.simpleName());
                }
                int dollar = d.binaryName().lastIndexOf('$');
                if (dollar > 0 && !s.types.containsKey(d.binaryName().substring(0, dollar))) {
                    throw new IllegalArgumentException("the outer type declaration of a nested type is missing: " + d.binaryName());
                }
            }
        }

        private static void checkType(PacketSchema s, TypeRef t, String pkg, Integer line) {
            try {
                s.resolve(t.name(), pkg);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("schema line " + line + ": " + e.getMessage(), e);
            }
            for (TypeRef a : t.arguments()) checkType(s, a, pkg, line);
        }
    }
}
