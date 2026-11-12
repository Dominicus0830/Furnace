// 로컬 전용 대조 도구: 패킷 중립 스키마(furnace-api protocol-767.schema)가 바닐라 1.21.1 서버 클래스 모양과 맞는지 본다.
// 바닐라 jar·클래스는 저장소에 없다 — 각자 main 브랜치 tools/vanilla-data/server-1.21.1 을 꺼내 경로로 준다(README.md). 결과는 화면에만 쓴다.
//
// 보는 것: 패킷 종류(와이어 이름 → 클래스), record/class/singleton, 부모 클래스, 인스턴스 필드 수·순서·서술자·제네릭 인자,
// 모든 필드 public 생성자 유무(new 플래그), enum 상수 이름·순서. 난독화 이름은 "Mojang 이름 ↔ 난독화 이름" 짝이
// 처음 본 대로 끝까지 한결같은지(일대일)로 맞춘다. 필드 이름·접근자 이름·와이어는 난독화돼 여기서 볼 수 없다.
// 두 번째 인자로 생성된 shim 클래스 폴더(furnace-compat-paper/build/classes/java/main)를 주면 더 본다:
// 처리자 인터페이스 계층(바로 위 인터페이스와 순서), shim 의 public 생성자·메서드 서술자가 바닐라에도 있는지(이름은 난독화돼 서술자만).
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.domi.furnace.api.packet.PacketDirection;
import org.domi.furnace.api.packet.schema.FieldDef;
import org.domi.furnace.api.packet.schema.PacketDef;
import org.domi.furnace.api.packet.schema.PacketSchema;
import org.domi.furnace.api.packet.schema.StructDef;
import org.domi.furnace.api.packet.schema.TypeDecl;
import org.domi.furnace.api.packet.schema.TypeRef;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

public class PacketSchemaCheck {

    /** 1.21.1 서버 jar 의 패킷 종류 표 클래스(난독화 이름) → 스키마 상태. 다른 버전이면 이 표만 바꾼다. */
    static final Map<String, String> TYPE_TABLES = Map.of(
            "zy", "common", "abq", "cookie", "ajk", "ping", "agg", "game", "abi", "configuration", "aiu", "login", "ajt", "status");
    /** 핸드셰이크는 종류 표가 패킷 하나라 직접 준다. */
    static final String HANDSHAKE_INTENTION = "aij";

    static Path root;
    static Path shimRoot;
    static final Map<String, ClassNode> shimCache = new HashMap<>();
    static final List<String> notes = new ArrayList<>();
    static PacketSchema schema = PacketSchema.protocol767();
    static final Map<String, ClassNode> cache = new HashMap<>();
    static final Map<String, String> m2o = new HashMap<>();   // Mojang 내부 이름 → 난독화
    static final Map<String, String> o2m = new HashMap<>();
    static final List<String> problems = new ArrayList<>();
    static final Deque<String[]> pending = new ArrayDeque<>();   // [Mojang 이름, 난독화 이름] 짝을 지은 뒤 구조 비교할 것

    public static void main(String[] args) throws Exception {
        root = Path.of(args[0]);
        shimRoot = args.length > 1 ? Path.of(args[1]) : null;
        Map<String, String> vanilla = packetTable();
        int checked = 0;
        for (PacketDef p : schema.packets()) {
            String key = p.state() + "/" + p.direction() + "/" + p.wireName();
            String obf = vanilla.remove(key);
            if (obf == null) { problems.add("바닐라에 없는 패킷: " + key); continue; }
            bind(internal(p.binaryName()), obf, key);
            checkPacket(p, obf);
            checked++;
        }
        for (String k : vanilla.keySet()) problems.add("스키마에 없는 바닐라 패킷: " + k);
        drain();
        if (shimRoot != null) {
            checkListeners();
            drain();
            checkPublicSignatures();
        }
        System.out.println("패킷 " + checked + "종 대조, 이름 짝 " + m2o.size() + "개" + (shimRoot != null ? " (shim 클래스 계층·공개 서술자 포함)" : ""));
        notes.forEach(s -> System.out.println("  · " + s));
        problems.forEach(s -> System.out.println("  ✗ " + s));
        System.out.println(problems.isEmpty() ? "일치" : "불일치 " + problems.size() + "건");
        if (!problems.isEmpty()) System.exit(1);
    }

    static void drain() throws Exception {
        while (!pending.isEmpty()) {
            String[] b = pending.poll();
            checkBound(b[0], b[1]);
        }
    }

    // ------------------------------------------------------------------ 처리자 계층·공개 서술자(shim 클래스 폴더가 있을 때)

    /** 패킷의 Packet&lt;X&gt; 인자(자기나 부모 클래스의 제네릭 서명에서). */
    static String listenerOf(ClassNode c, boolean shim) throws Exception {
        for (ClassNode k = c; k != null; k = k.superName == null ? null : (shim ? loadShim(k.superName) : load(k.superName))) {
            String g = k.signature;
            if (g != null && g.contains("<L")) {
                int i = g.lastIndexOf("<L");
                return g.substring(i + 2, g.indexOf(';', i));
            }
        }
        return null;
    }

    static void checkListeners() throws Exception {
        Deque<String[]> q = new ArrayDeque<>();
        for (PacketDef p : schema.packets()) {
            String m = internal(p.binaryName());
            ClassNode v = load(m2o.get(m)), g = loadShim(m);
            if (v == null || g == null) continue;
            String vl = listenerOf(v, false), gl = listenerOf(g, true);
            if (vl == null || gl == null) { problems.add(p.className() + ": 처리자 인자를 못 찾음"); continue; }
            bind(gl, vl, p.className() + " 처리자");
            q.add(new String[] {gl, vl});
        }
        Set<String> seen = new TreeSet<>();
        while (!q.isEmpty()) {
            String[] b = q.poll();
            if (!seen.add(b[0])) continue;
            ClassNode v = load(b[1]), g = loadShim(b[0]);
            if (v == null || g == null) { problems.add(b[0] + ": 처리자 클래스가 없다(바닐라 " + (v != null) + ", shim " + (g != null) + ")"); continue; }
            if (v.interfaces.size() != g.interfaces.size()) {
                problems.add(b[0] + ": 바로 위 인터페이스 " + g.interfaces + " / 바닐라 " + v.interfaces.stream().map(i -> o2m.getOrDefault(i, "?" + i)).toList());
                continue;
            }
            for (int i = 0; i < v.interfaces.size(); i++) {
                bind(g.interfaces.get(i), v.interfaces.get(i), b[0] + " 위 " + i);
                q.add(new String[] {g.interfaces.get(i), v.interfaces.get(i)});
            }
        }
        notes.add("처리자 인터페이스 " + seen.size() + "개 계층 대조");
    }

    static void checkPublicSignatures() throws Exception {
        List<String> names = new ArrayList<>();
        schema.packets().forEach(p -> names.add(internal(p.binaryName())));
        schema.structs().forEach(s -> names.add(internal(s.binaryName())));
        int erasedOnly = 0, classes = 0;
        for (String m : names) {
            ClassNode v = load(m2o.get(m)), g = loadShim(m);
            if (v == null || g == null) continue;
            classes++;
            List<String> vs = new ArrayList<>();
            for (MethodNode mn : v.methods) if (visible(mn)) vs.add(sigKey(mn, true));
            for (MethodNode mn : g.methods) {
                // schemaKey·schemaValues(SchemaPacket)·toFurnace(NmsPacket)는 호환 계층 다리라 바닐라에 없다
                if (!visible(mn) || mn.name.equals("schemaKey") || mn.name.equals("schemaValues") || mn.name.equals("toFurnace")) continue;
                String k = sigKey(mn, false);
                if (vs.stream().anyMatch(x -> sameShape(x, k))) continue;
                if (k.contains("Ljava/lang/Object;")) { erasedOnly++; continue; }   // 지운 라이브러리 타입(authlib·fastutil 등) — 예상된 차이
                problems.add(m.substring(m.lastIndexOf('/') + 1) + ": 바닐라에 없는 public " + k);
            }
        }
        notes.add("공개 서술자 대조 " + classes + "개 클래스, 지운 라이브러리 타입 때문에 다른 것 " + erasedOnly + "개(예상)");
    }

    static boolean visible(MethodNode mn) {
        return (mn.access & Opcodes.ACC_PUBLIC) != 0 && (mn.access & Opcodes.ACC_SYNTHETIC) == 0 && !mn.name.equals("<clinit>");
    }

    /** 이름은 난독화돼 비교 못 하므로 생성자/메서드 구분과 서술자만. 바닐라 쪽은 짝이 있는 이름을 Mojang 이름으로, 없는 것은 '?' 로. */
    static String sigKey(MethodNode mn, boolean vanilla) {
        String d = mn.desc;
        if (vanilla) {
            java.util.regex.Matcher mt = java.util.regex.Pattern.compile("L([^;]+);").matcher(d);
            StringBuilder sb = new StringBuilder();
            while (mt.find()) {
                String n = mt.group(1);
                String r = n.contains("/") ? n : o2m.getOrDefault(n, "?");
                mt.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement("L" + r + ";"));
            }
            mt.appendTail(sb);
            d = sb.toString();
        }
        return ((mn.access & Opcodes.ACC_STATIC) != 0 ? "static " : "") + (mn.name.equals("<init>") ? "<init>" : "m") + d;
    }

    /** 바닐라 서술자의 짝 없는 net.minecraft 타입('?')은 shim 의 net.minecraft 타입 아무것과 맞는다고 본다. */
    static boolean sameShape(String vanilla, String shim) {
        if (vanilla.equals(shim)) return true;
        if (!vanilla.contains("L?;")) return false;
        StringBuilder re = new StringBuilder();
        String[] parts = vanilla.split("L\\?;", -1);
        for (int k = 0; k < parts.length; k++) {
            if (k > 0) re.append("Lnet/minecraft/[^;]+;");
            re.append(java.util.regex.Pattern.quote(parts[k]));
        }
        return shim.matches(re.toString());
    }

    static ClassNode loadShim(String internalName) throws Exception {
        if (shimCache.containsKey(internalName)) return shimCache.get(internalName);
        Path p = shimRoot.resolve(internalName + ".class");
        ClassNode cn = null;
        if (Files.exists(p)) {
            cn = new ClassNode();
            new ClassReader(Files.readAllBytes(p)).accept(cn, ClassReader.SKIP_CODE);
        }
        shimCache.put(internalName, cn);
        return cn;
    }

    // ------------------------------------------------------------------ 바닐라 종류 표

    static Map<String, String> packetTable() throws Exception {
        Map<String, String> out = new HashMap<>();
        for (Map.Entry<String, String> t : TYPE_TABLES.entrySet()) {
            ClassNode cn = load(t.getKey());
            Map<String, String> sig = new HashMap<>();
            for (FieldNode f : cn.fields) if ((f.access & Opcodes.ACC_STATIC) != 0 && f.signature != null) sig.put(f.name, f.signature);
            MethodNode clinit = cn.methods.stream().filter(m -> m.name.equals("<clinit>")).findFirst().orElseThrow();
            String str = null, call = null;
            for (AbstractInsnNode in : clinit.instructions) {
                if (in instanceof LdcInsnNode l && l.cst instanceof String s) str = s;
                if (in instanceof MethodInsnNode m && m.owner.equals(cn.name)) call = m.name;
                if (in instanceof FieldInsnNode f && f.getOpcode() == Opcodes.PUTSTATIC && sig.containsKey(f.name)) {
                    String s = sig.get(f.name);
                    String cls = s.substring(s.indexOf("<L") + 2, s.lastIndexOf(";>"));
                    // 종류 표의 만드는 메서드: a = clientbound, b = serverbound
                    String dir = call.equals("a") ? "CLIENTBOUND" : "SERVERBOUND";
                    out.put(t.getValue() + "/" + dir + "/" + str, cls);
                }
            }
        }
        out.put("handshake/SERVERBOUND/intention", HANDSHAKE_INTENTION);
        return out;
    }

    // ------------------------------------------------------------------ 비교

    static void checkPacket(PacketDef p, String obf) throws Exception {
        ClassNode cn = load(obf);
        String where = p.className();
        boolean record = "java/lang/Record".equals(cn.superName);
        List<FieldNode> fields = instanceFields(cn);
        switch (p.kind()) {
            case RECORD -> { if (!record) problems.add(where + ": 바닐라는 record 가 아니다"); }
            case SINGLETON -> {
                if (!fields.isEmpty()) problems.add(where + ": singleton 인데 바닐라에 인스턴스 필드 " + fields.size() + "개");
                boolean inst = cn.fields.stream().anyMatch(f -> (f.access & Opcodes.ACC_STATIC) != 0 && f.desc.equals("L" + obf + ";"));
                if (!inst) problems.add(where + ": 바닐라에 INSTANCE(자기 타입 static 필드)가 없다");
            }
            case CLASS -> { if (record) problems.add(where + ": 바닐라는 record 다"); }
        }
        if (p.superType() != null) {
            String sup = schema.resolve(p.superType().name(), p.javaPackage());
            if (schema.struct(sup).isPresent()) bind(internal(sup), cn.superName, where + " 부모");
            else bind(internal(sup), cn.superName, where + " 부모");
        }
        if (p.kind() != PacketDef.Kind.SINGLETON) compareFields(where, p.fields(), p.javaPackage(), fields);
        if (p.kind() == PacketDef.Kind.CLASS && !p.fields().isEmpty()) {
            String all = "(" + String.join("", fields.stream().map(f -> f.desc).toList()) + ")V";
            boolean has = cn.methods.stream().anyMatch(m -> m.name.equals("<init>") && (m.access & Opcodes.ACC_PUBLIC) != 0 && m.desc.equals(all));
            if (has != p.publicConstructor()) problems.add(where + ": 모든 필드 public 생성자 " + (has ? "있음 → new 플래그 필요" : "없음 → new 플래그 빼야"));
        }
    }

    /** 짝이 지어진 스키마 보조 타입·선언 타입의 구조 비교(필드·enum 상수). */
    static void checkBound(String mojang, String obf) throws Exception {
        String bin = mojang.replace('/', '.');
        ClassNode cn = load(obf);
        if (cn == null) return;   // 라이브러리·JDK
        var st = schema.struct(bin);
        if (st.isPresent()) {
            StructDef s = st.get();
            if (s.kind() == StructDef.Kind.ENUM) enumConsts(s.name(), s.constants(), cn);
            else if (s.kind() == StructDef.Kind.RECORD || s.kind() == StructDef.Kind.CLASS || s.kind() == StructDef.Kind.ABSTRACT) {
                compareFields(s.name(), s.fields(), s.javaPackage(), instanceFields(cn));
                if (s.kind() == StructDef.Kind.CLASS && !s.privateAccess() && !s.fields().isEmpty()) {
                    String all = "(" + String.join("", instanceFields(cn).stream().map(f -> f.desc).toList()) + ")V";
                    boolean has = cn.methods.stream().anyMatch(m -> m.name.equals("<init>") && (m.access & Opcodes.ACC_PUBLIC) != 0 && m.desc.equals(all));
                    if (has != s.publicConstructor()) problems.add(s.name() + ": 모든 필드 public 생성자 " + (has ? "있음 → new 플래그 필요" : "없음 → new 플래그 빼야"));
                }
                boolean record = "java/lang/Record".equals(cn.superName);
                if ((s.kind() == StructDef.Kind.RECORD) != record) problems.add(s.name() + ": record 여부가 다르다");
            }
            boolean itf = (cn.access & Opcodes.ACC_INTERFACE) != 0;
            if ((s.kind() == StructDef.Kind.INTERFACE) != itf) problems.add(s.name() + ": interface 여부가 다르다");
            return;
        }
        var td = schema.type(bin);
        if (td.isPresent()) {
            TypeDecl d = td.get();
            boolean isEnum = (cn.access & Opcodes.ACC_ENUM) != 0;
            boolean itf = (cn.access & Opcodes.ACC_INTERFACE) != 0;
            boolean record = "java/lang/Record".equals(cn.superName);
            if ((d.kind() == TypeDecl.Kind.ENUM) != isEnum) problems.add(d.binaryName() + ": enum 여부가 다르다");
            if ((d.kind() == TypeDecl.Kind.INTERFACE) != itf) problems.add(d.binaryName() + ": interface 여부가 다르다");
            if ((d.kind() == TypeDecl.Kind.RECORD) != record) problems.add(d.binaryName() + ": record 여부가 다르다");
            if (isEnum && !d.constants().isEmpty()) enumConsts(d.binaryName(), d.constants(), cn);
        }
    }

    static void enumConsts(String where, List<String> want, ClassNode cn) {
        List<String> names = new ArrayList<>();
        MethodNode clinit = cn.methods.stream().filter(m -> m.name.equals("<clinit>")).findFirst().orElseThrow();
        int count = (int) cn.fields.stream().filter(f -> (f.access & Opcodes.ACC_ENUM) != 0).count();
        // enum 상수 이름은 clinit 에서 생성자에 넘기는 첫 문자열이다(난독화되지 않는다)
        for (AbstractInsnNode in = clinit.instructions.getFirst(); in != null && names.size() < count; in = in.getNext()) {
            if (in.getOpcode() == Opcodes.NEW && ((org.objectweb.asm.tree.TypeInsnNode) in).desc.equals(cn.name)) {
                for (AbstractInsnNode j = in.getNext(); j != null; j = j.getNext()) {
                    if (j instanceof LdcInsnNode l && l.cst instanceof String s) { names.add(s); break; }
                }
            }
        }
        if (!names.equals(want)) problems.add(where + ": enum 상수가 다르다 바닐라=" + names + " 스키마=" + want);
    }

    static void compareFields(String where, List<FieldDef> want, String pkg, List<FieldNode> have) {
        if (want.size() != have.size()) {
            problems.add(where + ": 필드 수 스키마 " + want.size() + " / 바닐라 " + have.size());
            return;
        }
        for (int i = 0; i < want.size(); i++) {
            FieldDef f = want.get(i);
            FieldNode v = have.get(i);
            String at = where + "." + f.name();
            matchDesc(at, f.type(), pkg, Type.getType(v.desc));
            if (v.signature != null && !f.type().arguments().isEmpty()) matchSig(at, f.type(), pkg, v.signature);
        }
    }

    static void matchDesc(String at, TypeRef t, String pkg, Type v) {
        int dims = t.arrayDimensions();
        if (dims != (v.getSort() == Type.ARRAY ? v.getDimensions() : 0)) { problems.add(at + ": 배열 차원 " + t + " / " + v); return; }
        Type e = dims > 0 ? v.getElementType() : v;
        String bin = schema.resolve(t.name(), pkg);
        if (t.isPrimitive()) {
            String want = switch (t.name()) { case "int" -> "I"; case "long" -> "J"; case "short" -> "S"; case "byte" -> "B";
                case "float" -> "F"; case "double" -> "D"; case "boolean" -> "Z"; default -> "C"; };
            if (!want.equals(e.getDescriptor())) problems.add(at + ": " + t + " / 바닐라 " + v.getDescriptor());
            return;
        }
        if (e.getSort() != Type.OBJECT) { problems.add(at + ": " + t + " / 바닐라 " + v.getDescriptor()); return; }
        bind(internal(bin), e.getInternalName(), at);
    }

    /** 필드 제네릭 서명의 타입 인자를 스키마 인자와 짝짓는다(첫 겹만 — List<Entry>·Optional<X>·Map<K,V> 등). */
    static void matchSig(String at, TypeRef t, String pkg, String sig) {
        List<String> args = topArgs(sig);
        if (args.size() != t.arguments().size()) { problems.add(at + ": 타입 인자 수 " + t + " / " + sig); return; }
        for (int i = 0; i < args.size(); i++) {
            TypeRef a = t.arguments().get(i);
            String s = args.get(i);
            if (a.bound() == TypeRef.Bound.WILDCARD) { if (!s.equals("*")) problems.add(at + ": ? / " + s); continue; }
            if (s.startsWith("+") || s.startsWith("-")) s = s.substring(1);
            if (s.equals("*")) { problems.add(at + ": 인자 " + a + " / *"); continue; }
            String desc = s.contains("<") ? s.substring(0, s.indexOf('<')) + ";" : s;
            if (desc.startsWith("[")) { matchDesc(at + "<" + i + ">", a, pkg, Type.getType(desc)); continue; }
            matchDesc(at + "<" + i + ">", a, pkg, Type.getType(desc));
            if (!a.arguments().isEmpty() && s.contains("<")) matchSig(at + "<" + i + ">", a, pkg, s);
        }
    }

    /** "Ljava/util/Map<Lakq<+Ljz<*>;>;Lawx$a;>;" → ["Lakq<+Ljz<*>;>;", "Lawx$a;"] */
    static List<String> topArgs(String sig) {
        List<String> out = new ArrayList<>();
        int lt = sig.indexOf('<');
        if (lt < 0) return out;
        int depth = 0, start = lt + 1;
        for (int i = lt + 1; i < sig.length(); i++) {
            char c = sig.charAt(i);
            if (c == '<') depth++;
            else if (c == '>') {
                if (depth == 0) { if (start < i) out.add(sig.substring(start, i)); break; }
                depth--;
            } else if (depth == 0 && (c == ';' || c == '*')) { out.add(sig.substring(start, i + 1)); start = i + 1; }
        }
        return out;
    }

    static void bind(String mojang, String obf, String at) {
        boolean javaOrLib = !mojang.startsWith("net/minecraft/");
        if (javaOrLib) {
            if (!mojang.equals(obf)) problems.add(at + ": " + mojang + " / 바닐라 " + obf);
            return;
        }
        String o = m2o.get(mojang), m = o2m.get(obf);
        if (o == null && m == null) {
            m2o.put(mojang, obf);
            o2m.put(obf, mojang);
            pending.add(new String[] {mojang, obf});
        } else if (!obf.equals(o) || !mojang.equals(m)) {
            problems.add(at + ": 이름 짝이 어긋난다 " + mojang + "↔" + obf + " (앞서 " + mojang + "↔" + o + ", " + obf + "↔" + m + ")");
        }
    }

    static List<FieldNode> instanceFields(ClassNode cn) {
        return cn.fields.stream().filter(f -> (f.access & Opcodes.ACC_STATIC) == 0).toList();
    }

    static String internal(String binaryName) { return binaryName.replace('.', '/'); }

    static ClassNode load(String internalName) throws Exception {
        if (cache.containsKey(internalName)) return cache.get(internalName);
        Path p = root.resolve(internalName + ".class");
        ClassNode cn = null;
        if (Files.exists(p)) {
            cn = new ClassNode();
            new ClassReader(Files.readAllBytes(p)).accept(cn, ClassReader.SKIP_CODE & 0 | ClassReader.SKIP_FRAMES);
        }
        cache.put(internalName, cn);
        return cn;
    }
}
