package org.domi.furnace.tools.extract;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LookupSwitchInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * 패킷 코덱 추출기(T-252). 바닐라 서버 jar(Mojang 이름)의 패킷 클래스 코덱 정의를 바이트코드로 따라가 **와이어 모양**(필드 순서·코덱 종류)을 뽑는다.
 *
 * <p>두 가지 정의 방식을 읽는다.
 * <ul>
 *   <li>{@code StreamCodec.composite(코덱, 게터, …, 생성자)} — 인자 목록이 곧 필드 순서·코덱</li>
 *   <li>{@code Packet.codec(쓰기 메서드, 읽기 생성자)} — 쓰기 메서드 몸통의 버퍼 호출 순서</li>
 * </ul>
 * 정적 초기화·쓰기 메서드는 작은 기호 실행기({@link Exec})로 훑는다: 값 스택에 식(트리)을 쌓고, 버퍼에 닿는 호출을 사건(와이어 토큰)으로 모은다.
 * 분기·모르는 호출·실행기가 못 따라가는 명령이 나오면 그 패킷은 {@code custom} 으로 표시하고 이유를 적는다(손으로 남는 것).
 *
 * <p>이 클래스는 몸통의 명령·상수를 어디에도 저장하지 않는다. 결과에 남는 것은 토큰(와이어 종류)과 패킷·게터 이름뿐이다.
 */
public final class PacketLayout {

    /** 와이어 사건 하나. field 는 값이 어느 필드(또는 게터)에서 왔는지(알 수 없으면 null). */
    public record Op(String token, String field) {
        @Override
        public String toString() { return field == null ? token : token + "@" + field; }
    }

    /**
     * 패킷 하나의 추출 결과.
     *
     * @param className  바이너리 이름('/' 구분, '$' 내포)
     * @param typeField  패킷 종류 상수 이름(예 CLIENTBOUND_SET_TIME), 못 찾으면 null
     * @param wireName   패킷 종류 상수에 적힌 와이어 이름(예 set_time), 못 찾으면 null
     * @param serverbound 방향(true 면 서버 쪽으로 오는 것), 모르면 false
     * @param state      상태 패키지 이름(game·common·…)
     * @param form       정의 방식: composite · codec · unit · none
     * @param ops        와이어 사건 순서
     * @param custom     손으로 남는 이유(없으면 null)
     */
    public record Packet(String className, String typeField, String wireName, boolean serverbound, String state, String form,
                         List<Op> ops, String custom) {
        public String layout() {
            StringBuilder sb = new StringBuilder();
            for (Op o : ops) {
                if (sb.length() > 0) sb.append(' ');
                sb.append(o.token());
            }
            return sb.toString();
        }
    }

    private static final String PROTOCOL_PREFIX = "net/minecraft/network/protocol/";
    /** 바닐라 값 코덱 모음(ByteBufCodecs 와 그 익명 클래스). 잎 코덱의 와이어를 이 안의 encode 몸통에서 읽는다. */
    private static final String CODEC_PREFIX = "net/minecraft/network/codec/";

    private final Map<String, ClassNode> classes = new HashMap<>();

    private PacketLayout() {}

    public static List<Packet> fromJar(Path jar) throws IOException {
        PacketLayout pl = new PacketLayout();
        try (ZipFile zf = new ZipFile(jar.toFile())) {
            Enumeration<? extends ZipEntry> en = zf.entries();
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                String n = e.getName();
                if (e.isDirectory() || !n.endsWith(".class") || !(n.startsWith(PROTOCOL_PREFIX) || n.startsWith(CODEC_PREFIX))) continue;
                try (InputStream in = zf.getInputStream(e)) {
                    ClassNode cn = new ClassNode();
                    new ClassReader(in.readAllBytes()).accept(cn, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                    pl.classes.put(cn.name, cn);
                }
            }
        }
        return pl.extract();
    }

    // ------------------------------------------------------------------ 패킷 종류 상수(와이어 이름)

    /** 종류 상수 소유자.이름 → (와이어 이름, 서버 쪽 여부). */
    private Map<String, String[]> typeConstants() {
        Map<String, String[]> out = new HashMap<>();
        for (ClassNode cn : classes.values()) {
            if (!cn.name.endsWith("PacketTypes")) continue;
            MethodNode clinit = method(cn, "<clinit>", null);
            if (clinit == null) continue;
            Exec x = new Exec(this, clinit, List.of(), "");
            x.run();
            for (Map.Entry<String, E> p : x.putStatics.entrySet()) {
                E v = p.getValue();
                if (v.args.isEmpty()) continue;
                String name = v.op;
                boolean sb = name.endsWith("createServerbound");
                boolean cb = name.endsWith("createClientbound");
                if (!sb && !cb) continue;
                E first = v.args.get(v.args.size() - 1);
                out.put(cn.name + "." + p.getKey(), new String[] {first.op.replace("\"", ""), sb ? "S" : "C"});
            }
        }
        return out;
    }

    private List<Packet> extract() {
        Map<String, String[]> types = typeConstants();
        List<Packet> out = new ArrayList<>();
        List<ClassNode> sorted = new ArrayList<>(classes.values());
        sorted.sort((a, b) -> a.name.compareTo(b.name));
        for (ClassNode cn : sorted) {
            if ((cn.access & Opcodes.ACC_ABSTRACT) != 0 || (cn.access & Opcodes.ACC_INTERFACE) != 0) continue;
            int slash = cn.name.indexOf('/', PROTOCOL_PREFIX.length());
            if (slash < 0) continue;
            String state = cn.name.substring(PROTOCOL_PREFIX.length(), slash);
            String simple = cn.name.substring(cn.name.lastIndexOf('/') + 1);
            String typeKey = null;
            MethodNode typeM = method(cn, "type", "()Lnet/minecraft/network/protocol/PacketType;");
            if (typeM == null) continue;   // 패킷이 아닌 보조 클래스(내포 열거형·Entry·익명 클래스)
            {
                for (AbstractInsnNode i = typeM.instructions.getFirst(); i != null; i = i.getNext()) {
                    if (i instanceof FieldInsnNode f && f.getOpcode() == Opcodes.GETSTATIC) typeKey = f.owner + "." + f.name;
                }
            }
            String[] tc = typeKey == null ? null : types.get(typeKey);
            String typeField = typeKey == null ? null : typeKey.substring(typeKey.lastIndexOf('.') + 1);
            boolean serverbound = tc != null ? tc[1].equals("S") : !simple.startsWith("Clientbound");
            out.add(markUnresolved(analyze(cn, state, typeField, tc == null ? null : tc[0], serverbound)));
        }
        return out;
    }

    /** 못 푼 토큰(`?`)이 하나라도 든 패킷은 손으로 남는 것으로 본다(이유가 없으면 unresolved). */
    private static Packet markUnresolved(Packet p) {
        if (p.custom() != null) return p;
        for (Op o : p.ops()) {
            if (o.token().contains("?")) return new Packet(p.className(), p.typeField(), p.wireName(), p.serverbound(), p.state(), p.form(), p.ops(), "unresolved:" + o.token());
        }
        return p;
    }

    private Packet analyze(ClassNode cn, String state, String typeField, String wire, boolean serverbound) {
        MethodNode clinit = method(cn, "<clinit>", null);
        if (clinit == null) return new Packet(cn.name, typeField, wire, serverbound, state, "none", List.of(), "no-static-init");
        Exec x = new Exec(this, clinit, List.of(), cn.name);
        x.run();
        E codec = x.putStatics.get("STREAM_CODEC");
        if (codec == null && x.putStatics.size() > 0) {
            // 코덱 상수가 여러 개인 패킷(커스텀 페이로드: 설정·게임 두 코덱) — 손으로 남긴다
            List<String> names = new ArrayList<>();
            for (String k : x.putStatics.keySet()) if (k.endsWith("STREAM_CODEC")) names.add(k);
            if (!names.isEmpty()) return new Packet(cn.name, typeField, wire, serverbound, state, "multi", List.of(), "several-codecs:" + String.join("+", names));
        }
        if (codec == null) {
            // 정적 초기화가 있어도 코덱 필드가 다른 이름이거나(다른 클래스가 가짐) 없는 경우
            return new Packet(cn.name, typeField, wire, serverbound, state, "none", List.of(),
                    x.problem != null ? "static-init:" + x.problem : "no-STREAM_CODEC");
        }
        if (codec.op.equals("net/minecraft/network/protocol/Packet.codec") && codec.args.size() == 2) {
            E enc = codec.args.get(0);
            if (!enc.op.startsWith("fn#")) return new Packet(cn.name, typeField, wire, serverbound, state, "codec", List.of(), "encoder-not-a-method-ref");
            Fn fn = Fn.parse(enc.op);
            ClassNode owner = classes.get(fn.owner);
            MethodNode m = owner == null ? null : method(owner, fn.name, fn.desc);
            if (m == null) return new Packet(cn.name, typeField, wire, serverbound, state, "codec", List.of(), "encoder-not-found");
            List<Op> ops = new ArrayList<>();
            String custom = analyzeWrite(m, enc.args, ops, 0);
            return new Packet(cn.name, typeField, wire, serverbound, state, "codec", ops, custom);
        }
        // composite / unit / 기타 코덱 식
        List<Op> ops = new ArrayList<>();
        String custom = codecOps(codec, ops);
        String form = codec.op.endsWith("StreamCodec.composite") ? "composite" : codec.op.endsWith("StreamCodec.unit") ? "unit" : "expr";
        return new Packet(cn.name, typeField, wire, serverbound, state, form, ops, custom);
    }

    // ------------------------------------------------------------------ 코덱 식 → 토큰

    /** 패킷 코덱 식(최상위)을 사건 목록으로. 못 푸는 부분이 있으면 이유를 돌려준다. */
    private String codecOps(E codec, List<Op> ops) {
        String op = codec.op;
        if (op.endsWith("StreamCodec.unit")) return null;
        if (op.endsWith("StreamCodec.composite")) {
            List<E> a = codec.args;
            // (코덱, 게터) 쌍 반복 뒤 생성자
            String problem = null;
            for (int i = 0; i + 1 < a.size(); i += 2) {
                E c = a.get(i);
                E g = a.get(i + 1);
                String field = g.op.startsWith("fn#") ? Fn.parse(g.op).name : null;
                String t = codecToken(c);
                ops.add(new Op(t, field));
                if (t.startsWith("?") && problem == null) problem = "unknown-codec:" + t;
            }
            return problem;
        }
        // StreamCodec.map(...) 같은 껍질: 안쪽
        if (op.endsWith("StreamCodec.map") || op.endsWith("StreamCodec.mapStream")) {
            return codecOps(codec.args.get(0), ops);
        }
        // 패킷 코덱이 필드 하나짜리 껍질(map)로 다른 코덱을 가리키는 꼴 — 잎 토큰이 알려진 것이면 손이 필요 없다
        String t = codecToken(codec);
        ops.add(new Op(t, null));
        return t.startsWith("?") ? "expr:" + op : null;
    }

    private static final Map<String, String> LEAF = new HashMap<>();

    static {
        String b = "net/minecraft/network/codec/ByteBufCodecs.";
        LEAF.put(b + "BOOL", "bool");
        LEAF.put(b + "BYTE", "i8");
        LEAF.put(b + "SHORT", "i16");
        LEAF.put(b + "UNSIGNED_SHORT", "u16");
        LEAF.put(b + "INT", "i32");
        LEAF.put(b + "VAR_INT", "varint");
        LEAF.put(b + "VAR_LONG", "varlong");
        LEAF.put(b + "LONG", "i64");
        LEAF.put(b + "FLOAT", "f32");
        LEAF.put(b + "DOUBLE", "f64");
        LEAF.put(b + "BYTE_ARRAY", "bytes");
        LEAF.put(b + "STRING_UTF8", "string");
        LEAF.put(b + "TAG", "nbt_any");
        LEAF.put(b + "COMPOUND_TAG", "nbt");
        LEAF.put(b + "OPTIONAL_COMPOUND_TAG", "nbt_opt");
        LEAF.put(b + "VECTOR3F", "vec3f");
        LEAF.put(b + "QUATERNIONF", "quaternionf");
        LEAF.put(b + "GAME_PROFILE", "game_profile");
        LEAF.put(b + "GAME_PROFILE_PROPERTIES", "game_profile_properties");
        LEAF.put(b + "PROPERTY_MAP", "property_map");
        LEAF.put(b + "PLAYER_NAME", "string");
        LEAF.put("net/minecraft/core/BlockPos.STREAM_CODEC", "block_pos");
        LEAF.put("net/minecraft/resources/ResourceLocation.STREAM_CODEC", "identifier");
        LEAF.put("net/minecraft/core/UUIDUtil.STREAM_CODEC", "uuid");
        // 잠금 파일에서 읽기 쉽게 이름을 붙인 값 코덱(스키마의 type 줄 wire= 와 같은 이름; 대조 시험이 스키마와 맞는지 본다)
        String cs = "net/minecraft/network/chat/ComponentSerialization.";
        for (String n : new String[] {"STREAM_CODEC", "TRUSTED_STREAM_CODEC", "CONTEXT_FREE_STREAM_CODEC", "TRUSTED_CONTEXT_FREE_STREAM_CODEC"}) LEAF.put(cs + n, "component");
        LEAF.put("net/minecraft/world/item/ItemStack.STREAM_CODEC", "item_stack");
        LEAF.put("net/minecraft/world/item/ItemStack.OPTIONAL_STREAM_CODEC", "item_stack");
    }

    /** 코덱 식 하나의 와이어 토큰. */
    String codecToken(E c) {
        String op = c.op;
        String leaf = LEAF.get(op);
        if (leaf != null && c.args.isEmpty()) return leaf;
        if (c.args.isEmpty()) {
            String derived = derivedLeaf(op);
            if (derived != null) return derived;
        }
        if (c.args.isEmpty() && !op.startsWith("fn#")) return "ref(" + shortRef(op) + ")";
        String bb = "net/minecraft/network/codec/ByteBufCodecs.";
        String sc = "net/minecraft/network/codec/StreamCodec.";
        if (op.equals(bb + "list") && !c.args.isEmpty() && c.args.size() == 1 && !c.args.get(0).op.endsWith("I")) {
            E a = c.args.get(0);
            if (isNumber(a)) return "list_op";
            return "list<" + codecToken(a) + ">";
        }
        if (op.equals(bb + "list")) return "list_op";
        if (op.equals(bb + "collection")) return "collection_op";
        if (op.equals(sc + "apply")) {
            E base = c.args.get(0);
            E opn = c.args.get(1);
            String inner = codecToken(base);
            // 연산은 ByteBufCodecs.list()·collection(..) 호출이거나 ByteBufCodecs::optional 메서드 참조
            String opName = opn.op.startsWith("fn#") ? Fn.parse(opn.op).owner + "." + Fn.parse(opn.op).name : opn.op;
            if (opName.equals(bb + "list") || opName.equals(bb + "collection")) return "list<" + inner + ">";
            if (opName.equals(bb + "optional")) return "optional<" + inner + ">";
            return "?apply(" + inner + "," + opName + ")";
        }
        if (op.equals(sc + "map") || op.equals(sc + "mapStream")) return codecToken(c.args.get(0));
        if (op.equals(bb + "optional")) return "optional<" + codecToken(c.args.get(0)) + ">";
        if (op.equals(bb + "stringUtf8")) return "string";
        if (op.equals(bb + "byteArray")) return "bytes";
        if (op.equals(bb + "idMapper")) return "varint";
        if (op.equals(bb + "registry")) return "registry_id";
        if (op.equals("net/minecraft/resources/ResourceKey.streamCodec")) return "identifier";   // ResourceLocation 코덱을 map 한 것
        if (op.equals(bb + "holderRegistry")) return "holder";
        if (op.equals(bb + "holder")) return "holder";
        if (op.equals(bb + "holderSet")) return "holder_set";
        if (op.equals(bb + "fromCodec") || op.equals(bb + "fromCodecTrusted") || op.equals(bb + "fromCodecWithRegistries")) return "codec_nbt";
        if (op.equals(bb + "lenientJson")) return "json";
        if (op.equals(bb + "map")) {
            // map(factory, keyCodec, valueCodec[, maxSize])
            List<E> a = c.args;
            return "map<" + codecToken(a.get(1)) + "," + codecToken(a.get(2)) + ">";
        }
        if (op.equals(bb + "either")) return "either<" + codecToken(c.args.get(0)) + "," + codecToken(c.args.get(1)) + ">";
        if (op.equals(bb + "lazyInitialized") || op.equals(bb + "recursive")) return "lazy";
        if (op.equals(sc + "composite")) {
            List<Op> ops = new ArrayList<>();
            codecOps(c, ops);
            StringBuilder sb = new StringBuilder("struct(");
            for (int i = 0; i < ops.size(); i++) {
                if (i > 0) sb.append(',');
                sb.append(ops.get(i).token());
            }
            return sb.append(')').toString();
        }
        if (op.equals(sc + "unit")) return "unit";
        // 값 검사만 얹는 껍질(ItemStack.validatedStreamCodec(코덱) 등): 안쪽 코덱이 와이어
        if (op.endsWith(".validatedStreamCodec") && c.args.size() == 1) return codecToken(c.args.get(0));
        if (op.equals(sc + "ofMember")) return "ref(member)";
        return "?" + shortRef(op);
    }

    private final Map<String, String> derivedLeaves = new HashMap<>();

    /**
     * {@code ByteBufCodecs.NAME} 처럼 익명 클래스로 만든 값 코덱의 와이어를, 그 클래스의 encode 몸통(버퍼 호출 하나)에서 읽는다.
     * 표에 없는 새 코덱이 판마다 생겨도 사람이 이름을 더하지 않게 한다. 한 호출로 안 떨어지면 null(불투명 참조로 둔다).
     */
    private String derivedLeaf(String op) {
        int dot = op.lastIndexOf('.');
        if (dot < 0) return null;
        String owner = op.substring(0, dot);
        String name = op.substring(dot + 1);
        if (!owner.equals("net/minecraft/network/codec/ByteBufCodecs")) return null;
        String key = op;
        if (derivedLeaves.containsKey(key)) return derivedLeaves.get(key);
        String result = null;
        ClassNode cn = classes.get(owner);
        MethodNode clinit = cn == null ? null : method(cn, "<clinit>", null);
        if (clinit != null) {
            Exec x = new Exec(this, clinit, List.of(), owner);
            x.run();
            E e = x.putStatics.get(name);
            if (e != null && e.op.startsWith("new:") && e.args.isEmpty()) {
                ClassNode anon = classes.get(e.op.substring(4));
                MethodNode enc = null;
                if (anon != null) {
                    for (MethodNode m : anon.methods) {
                        if (!m.name.equals("encode") || (m.access & Opcodes.ACC_BRIDGE) != 0) continue;
                        Type[] ps = Type.getArgumentTypes(m.desc);
                        if (ps.length == 2 && isBufType(ps[0].getDescriptor())) enc = m;
                    }
                }
                if (enc != null) {
                    List<Op> ops = new ArrayList<>();
                    String custom = analyzeWrite(enc, List.of(), ops, 1);
                    if (custom == null && ops.size() == 1 && !ops.get(0).token().startsWith("?") && !ops.get(0).token().startsWith("call(")) result = ops.get(0).token();
                }
            }
        }
        derivedLeaves.put(key, result);
        return result;
    }

    private static boolean isNumber(E e) {
        return e.args.isEmpty() && e.op.matches("-?\\d+");
    }

    private static String shortRef(String op) {
        return op.substring(op.lastIndexOf('/') + 1);
    }

    // ------------------------------------------------------------------ 쓰기 메서드

    private static final Map<String, String> BUF = new HashMap<>();

    static {
        BUF.put("writeBoolean", "bool");
        BUF.put("writeByte", "i8");
        BUF.put("writeShort", "i16");
        BUF.put("writeShortLE", "i16le");
        BUF.put("writeInt", "i32");
        BUF.put("writeLong", "i64");
        BUF.put("writeChar", "u16");
        BUF.put("writeFloat", "f32");
        BUF.put("writeDouble", "f64");
        BUF.put("writeVarInt", "varint");
        BUF.put("writeVarLong", "varlong");
        BUF.put("writeContainerId", "varint");   // 1.21.2 부터: VarInt.write(바이트코드로 확인). 1.21.1 에는 없다(그 판은 writeByte)
        BUF.put("writeUtf", "string");
        BUF.put("writeUUID", "uuid");
        BUF.put("writeByteArray", "bytes");
        BUF.put("writeLongArray", "longs");
        BUF.put("writeVarIntArray", "varints");
        BUF.put("writeEnum", "varint");
        BUF.put("writeResourceLocation", "identifier");
        BUF.put("writeResourceKey", "identifier");
        BUF.put("writeRegistryKey", "identifier");
        BUF.put("writeBlockPos", "block_pos");
        BUF.put("writeChunkPos", "chunk_pos");
        BUF.put("writeSectionPos", "section_pos");
        BUF.put("writeGlobalPos", "global_pos");
        BUF.put("writeNbt", "nbt_opt");
        BUF.put("writeBitSet", "bitset");
        BUF.put("writeFixedBitSet", "fixed_bitset");
        BUF.put("writeInstant", "instant");
        BUF.put("writeDate", "instant");
        BUF.put("writeEnumSet", "enum_set");
        BUF.put("writeBlockHitResult", "block_hit");
        BUF.put("writeJsonWithCodec", "json");
        BUF.put("writeWithCodec", "codec_nbt");
        BUF.put("writeGameProfile", "game_profile");
        BUF.put("writeGameProfileProperties", "game_profile_properties");
        BUF.put("writePropertyMap", "property_map");
        BUF.put("writeLpVec3", "lp_vec3");
        BUF.put("writeVector3f", "vec3f");
        BUF.put("writeQuaternion", "quaternionf");
        BUF.put("writeIntIdList", "int_id_list");
        BUF.put("writeBytes", "raw_bytes");
        BUF.put("writeMedium", "i24");
        BUF.put("writeLongLE", "i64le");
        BUF.put("writeIntLE", "i32le");
    }

    /** 쓰기 메서드 몸통을 따라가 사건을 모은다. 손으로 남는 이유를 돌려준다(없으면 null). captured 는 메서드 참조에 묶인 값(앞쪽 매개 변수에 들어간다). */
    private String analyzeWrite(MethodNode m, List<E> captured, List<Op> ops, int depth) {
        if (depth > 4) return "too-deep";
        Exec x = new Exec(this, m, bindArgs(m, captured), m.name);
        x.run();
        ops.addAll(x.events);
        if (x.problem != null) return x.problem;
        if (x.branched) return "branch";
        return null;
    }

    /** 묶인 값을 앞쪽 매개 변수(인스턴스면 this 부터)에 넣고, 나머지는 버퍼면 "buf", 아니면 "p<번호>". */
    static List<E> bindArgs(MethodNode m, List<E> captured) {
        List<E> args = new ArrayList<>();
        int ci = 0;
        if ((m.access & Opcodes.ACC_STATIC) == 0) args.add(ci < captured.size() ? captured.get(ci++) : new E("this"));
        Type[] params = Type.getArgumentTypes(m.desc);
        for (int i = 0; i < params.length; i++) {
            if (ci < captured.size()) args.add(captured.get(ci++));
            else if (isBufType(params[i].getDescriptor())) args.add(new E("buf"));
            else args.add(new E("p" + i));
        }
        return args;
    }

    static boolean isBufType(String desc) {
        return desc.equals("Lnet/minecraft/network/FriendlyByteBuf;")
                || desc.equals("Lnet/minecraft/network/RegistryFriendlyByteBuf;")
                || desc.equals("Lio/netty/buffer/ByteBuf;");
    }

    private static MethodNode method(ClassNode cn, String name, String desc) {
        for (MethodNode m : cn.methods) if (m.name.equals(name) && (desc == null || m.desc.equals(desc))) return m;
        return null;
    }

    MethodNode findMethod(String owner, String name, String desc) {
        ClassNode cn = classes.get(owner);
        return cn == null ? null : method(cn, name, desc);
    }

    // ------------------------------------------------------------------ 식 트리

    /** 식. op 가 연산(호출은 "소유자.이름"), args 가 인자(호출이면 받는이 먼저). 잎은 args 가 비어 있다. */
    static final class E {
        final String op;
        final List<E> args;
        final boolean wide;

        E(String op) { this(op, List.of(), false); }

        E(String op, List<E> args, boolean wide) {
            this.op = op;
            this.args = args;
            this.wide = wide;
        }

        @Override
        public String toString() {
            if (args.isEmpty()) return op;
            StringBuilder sb = new StringBuilder(op).append('(');
            for (int i = 0; i < args.size(); i++) {
                if (i > 0) sb.append(',');
                sb.append(args.get(i));
            }
            return sb.append(')').toString();
        }
    }

    /** 메서드 참조/람다 식 "fn#소유자.이름.서술자" 의 분해. */
    record Fn(String owner, String name, String desc) {
        static Fn parse(String op) {
            String s = op.substring(3);
            int d = s.indexOf('(');
            String head = s.substring(0, d);
            int dot = head.lastIndexOf('.');
            return new Fn(head.substring(0, dot), head.substring(dot + 1), s.substring(d));
        }
    }

    // ------------------------------------------------------------------ 기호 실행기

    /** 직선 기호 실행기. 분기는 따라가지 않고 표시만 한다. */
    static final class Exec {
        final PacketLayout pl;
        final MethodNode m;
        final String self;
        final Map<Integer, E> locals = new HashMap<>();
        final Deque<E> stack = new ArrayDeque<>();
        final List<Op> events = new ArrayList<>();
        final Map<String, E> putStatics = new LinkedHashMap<>();
        boolean branched;
        String problem;

        Exec(PacketLayout pl, MethodNode m, List<E> args, String self) {
            this.pl = pl;
            this.m = m;
            this.self = self;
            int slot = 0;
            int ai = 0;
            boolean isStatic = (m.access & Opcodes.ACC_STATIC) != 0;
            if (!isStatic) {
                locals.put(slot++, ai < args.size() ? args.get(ai++) : new E("this"));
            }
            for (Type t : Type.getArgumentTypes(m.desc)) {
                locals.put(slot, ai < args.size() ? args.get(ai++) : new E("p" + slot));
                slot += t.getSize();
            }
        }

        void run() {
            try {
                for (AbstractInsnNode i = m.instructions.getFirst(); i != null; i = i.getNext()) step(i);
            } catch (RuntimeException e) {
                problem = "sim:" + e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ":" + e.getMessage());
            }
        }

        E pop() { return stack.pop(); }

        void push(E e) { stack.push(e); }

        void push(String s) { stack.push(new E(s)); }

        void pushWide(String s) { stack.push(new E(s, List.of(), true)); }

        private void step(AbstractInsnNode n) {
            int op = n.getOpcode();
            if (op < 0) return;
            if (n instanceof VarInsnNode v) {
                switch (op) {
                    case Opcodes.ILOAD, Opcodes.LLOAD, Opcodes.FLOAD, Opcodes.DLOAD, Opcodes.ALOAD -> {
                        E e = locals.get(v.var);
                        push(e != null ? e : new E("local" + v.var));
                    }
                    default -> locals.put(v.var, pop());
                }
                return;
            }
            if (n instanceof IincInsnNode) return;
            if (n instanceof LdcInsnNode l) {
                Object c = l.cst;
                if (c instanceof Long || c instanceof Double) pushWide(String.valueOf(c));
                else if (c instanceof String s) push("\"" + s + "\"");
                else if (c instanceof Type t) push("class:" + t.getInternalName());
                else push(String.valueOf(c));
                return;
            }
            if (n instanceof FieldInsnNode f) {
                switch (op) {
                    case Opcodes.GETSTATIC -> {
                        boolean wide = f.desc.equals("J") || f.desc.equals("D");
                        push(new E(f.owner + "." + f.name, List.of(), wide));
                    }
                    case Opcodes.GETFIELD -> {
                        E o = pop();
                        boolean wide = f.desc.equals("J") || f.desc.equals("D");
                        push(new E("field:" + f.name, List.of(o), wide));
                    }
                    case Opcodes.PUTSTATIC -> putStatics.put(f.name, pop());
                    default -> { pop(); pop(); }
                }
                return;
            }
            if (n instanceof MethodInsnNode mi) { invoke(mi); return; }
            if (n instanceof InvokeDynamicInsnNode d) { indy(d); return; }
            if (n instanceof JumpInsnNode j) {
                branched = true;
                switch (op) {
                    case Opcodes.GOTO -> { }
                    case Opcodes.IFEQ, Opcodes.IFNE, Opcodes.IFLT, Opcodes.IFGE, Opcodes.IFGT, Opcodes.IFLE, Opcodes.IFNULL, Opcodes.IFNONNULL -> pop();
                    default -> { pop(); pop(); }
                }
                return;
            }
            if (n instanceof TableSwitchInsnNode || n instanceof LookupSwitchInsnNode) { branched = true; pop(); return; }
            if (n instanceof TypeInsnNode t) {
                switch (op) {
                    case Opcodes.NEW -> push("new:" + t.desc);
                    case Opcodes.ANEWARRAY -> { pop(); push("newarray"); }
                    case Opcodes.CHECKCAST -> { }
                    case Opcodes.INSTANCEOF -> { pop(); push("instanceof"); }
                    default -> throw new IllegalStateException("type op " + op);
                }
                return;
            }
            switch (op) {
                case Opcodes.ACONST_NULL -> push("null");
                case Opcodes.ICONST_M1, Opcodes.ICONST_0, Opcodes.ICONST_1, Opcodes.ICONST_2, Opcodes.ICONST_3, Opcodes.ICONST_4, Opcodes.ICONST_5 ->
                        push(String.valueOf(op - Opcodes.ICONST_0));
                case Opcodes.LCONST_0, Opcodes.LCONST_1 -> pushWide(String.valueOf(op - Opcodes.LCONST_0));
                case Opcodes.FCONST_0, Opcodes.FCONST_1, Opcodes.FCONST_2 -> push(String.valueOf(op - Opcodes.FCONST_0));
                case Opcodes.DCONST_0, Opcodes.DCONST_1 -> pushWide(String.valueOf(op - Opcodes.DCONST_0));
                case Opcodes.BIPUSH, Opcodes.SIPUSH -> push(String.valueOf(((org.objectweb.asm.tree.IntInsnNode) n).operand));
                case Opcodes.NEWARRAY -> { pop(); push("newarray"); }
                case Opcodes.IALOAD, Opcodes.FALOAD, Opcodes.AALOAD, Opcodes.BALOAD, Opcodes.CALOAD, Opcodes.SALOAD -> { pop(); pop(); push("aload"); }
                case Opcodes.LALOAD, Opcodes.DALOAD -> { pop(); pop(); pushWide("aload"); }
                case Opcodes.IASTORE, Opcodes.LASTORE, Opcodes.FASTORE, Opcodes.DASTORE, Opcodes.AASTORE, Opcodes.BASTORE, Opcodes.CASTORE, Opcodes.SASTORE -> { pop(); pop(); pop(); }
                case Opcodes.POP -> pop();
                case Opcodes.POP2 -> { E a = pop(); if (!a.wide) pop(); }
                case Opcodes.DUP -> push(stack.peek());
                case Opcodes.DUP_X1 -> { E a = pop(); E b = pop(); push(a); push(b); push(a); }
                case Opcodes.DUP_X2 -> {
                    E a = pop(); E b = pop();
                    if (b.wide) { push(a); push(b); push(a); } else { E c = pop(); push(a); push(c); push(b); push(a); }
                }
                case Opcodes.DUP2 -> {
                    E a = pop();
                    if (a.wide) { push(a); push(a); } else { E b = pop(); push(b); push(a); push(b); push(a); }
                }
                case Opcodes.DUP2_X1 -> {
                    E a = pop();
                    if (a.wide) { E b = pop(); push(a); push(b); push(a); }
                    else { E b = pop(); E c = pop(); push(b); push(a); push(c); push(b); push(a); }
                }
                case Opcodes.DUP2_X2 -> throw new IllegalStateException("dup2_x2");
                case Opcodes.SWAP -> { E a = pop(); E b = pop(); push(a); push(b); }
                case Opcodes.IADD, Opcodes.ISUB, Opcodes.IMUL, Opcodes.IDIV, Opcodes.IREM, Opcodes.ISHL, Opcodes.ISHR, Opcodes.IUSHR,
                     Opcodes.IAND, Opcodes.IOR, Opcodes.IXOR, Opcodes.FADD, Opcodes.FSUB, Opcodes.FMUL, Opcodes.FDIV, Opcodes.FREM,
                     Opcodes.LCMP, Opcodes.FCMPL, Opcodes.FCMPG, Opcodes.DCMPL, Opcodes.DCMPG -> { pop(); pop(); push("arith"); }
                case Opcodes.LADD, Opcodes.LSUB, Opcodes.LMUL, Opcodes.LDIV, Opcodes.LREM, Opcodes.LAND, Opcodes.LOR, Opcodes.LXOR,
                     Opcodes.DADD, Opcodes.DSUB, Opcodes.DMUL, Opcodes.DDIV, Opcodes.DREM -> { pop(); pop(); pushWide("arith"); }
                case Opcodes.LSHL, Opcodes.LSHR, Opcodes.LUSHR -> { pop(); pop(); pushWide("arith"); }
                case Opcodes.INEG, Opcodes.FNEG -> { pop(); push("arith"); }
                case Opcodes.LNEG, Opcodes.DNEG -> { pop(); pushWide("arith"); }
                case Opcodes.I2L, Opcodes.I2D, Opcodes.F2L, Opcodes.F2D -> { pop(); pushWide("conv"); }
                case Opcodes.L2I, Opcodes.L2F, Opcodes.D2I, Opcodes.D2F, Opcodes.I2F, Opcodes.I2B, Opcodes.I2C, Opcodes.I2S -> { pop(); push("conv"); }
                case Opcodes.L2D, Opcodes.D2L -> { pop(); pushWide("conv"); }
                case Opcodes.ARRAYLENGTH -> { pop(); push("len"); }
                case Opcodes.RETURN, Opcodes.IRETURN, Opcodes.LRETURN, Opcodes.FRETURN, Opcodes.DRETURN, Opcodes.ARETURN, Opcodes.ATHROW,
                     Opcodes.MONITORENTER, Opcodes.MONITOREXIT -> { }
                default -> throw new IllegalStateException("opcode " + op);
            }
        }

        private void invoke(MethodInsnNode mi) {
            Type[] params = Type.getArgumentTypes(mi.desc);
            Type ret = Type.getReturnType(mi.desc);
            List<E> args = new ArrayList<>();
            for (int i = 0; i < params.length; i++) args.add(0, pop());
            E recv = null;
            boolean isStatic = mi.getOpcode() == Opcodes.INVOKESTATIC;
            if (!isStatic) recv = pop();
            boolean initCall = mi.name.equals("<init>");
            // FriendlyByteBuf.writeX(buf, 값…) 꼴 정적 도우미는 버퍼를 받는이로 본다
            if (isStatic && mi.owner.endsWith("/FriendlyByteBuf") && !args.isEmpty() && args.get(0).op.equals("buf")) {
                recv = args.get(0);
                args = new ArrayList<>(args.subList(1, args.size()));
            }
            boolean recvIsBuf = recv != null && recv.op.equals("buf");
            boolean argIsBuf = false;
            for (E a : args) if (a.op.equals("buf")) argIsBuf = true;
            // 버퍼 사건
            if (recvIsBuf || argIsBuf) {
                event(mi, recv, args, recvIsBuf);
            }
            if (initCall) {
                // new X; dup; args; invokespecial <init> → 남은 사본을 생성식으로
                if (recv != null && !stack.isEmpty() && stack.peek() == recv) {
                    stack.pop();
                    push(new E("new:" + mi.owner, args, false));
                }
                return;
            }
            if (ret.getSort() == Type.VOID) return;
            boolean wide = ret.getSize() == 2;
            if (recvIsBuf && isBufType(ret.getDescriptor())) { push(new E("buf")); return; }
            List<E> all = new ArrayList<>();
            if (recv != null) all.add(recv);
            all.addAll(args);
            push(new E(mi.owner + "." + mi.name, all, wide));
        }

        private void event(MethodInsnNode mi, E recv, List<E> args, boolean recvIsBuf) {
            String name = mi.name;
            if (recvIsBuf) {
                String tok = BUF.get(name);
                E first = args.isEmpty() ? null : args.get(0);
                switch (name) {
                    case "writeCollection" -> {
                        E fn = args.get(1);
                        events.add(new Op("list<" + fnToken(fn) + ">", fieldOf(first)));
                        return;
                    }
                    case "writeOptional", "writeNullable" -> {
                        E fn = args.get(1);
                        events.add(new Op("optional<" + fnToken(fn) + ">", fieldOf(first)));
                        return;
                    }
                    case "writeMap" -> {
                        events.add(new Op("map<" + fnToken(args.get(1)) + "," + fnToken(args.get(2)) + ">", fieldOf(first)));
                        return;
                    }
                    case "writeEither" -> {
                        events.add(new Op("either<" + fnToken(args.get(1)) + "," + fnToken(args.get(2)) + ">", fieldOf(first)));
                        return;
                    }
                    case "writeId" -> {
                        // writeId(IdMap, value) — 번호로 나가는 값
                        events.add(new Op("registry_id", fieldOf(args.size() > 1 ? args.get(1) : first)));
                        return;
                    }
                    case "writeById" -> {
                        events.add(new Op("varint", fieldOf(args.size() > 1 ? args.get(1) : first)));
                        return;
                    }
                    case "writeBytes" -> {
                        events.add(new Op("raw_bytes", fieldOf(first)));
                        return;
                    }
                    default -> { }
                }
                if (tok == null) {
                    if (name.equals("writerIndex") || name.equals("writeZero") || name.equals("ensureWritable")) return;
                    tok = "?" + name;
                }
                events.add(new Op(tok, fieldOf(first)));
                return;
            }
            // 값 쪽에서 버퍼를 받는 호출: X.STREAM_CODEC.encode(buf, v) · v.write(buf) · Codec 호출
            if (mi.name.equals("encode") && recv != null && args.size() == 2) {
                events.add(new Op(pl.codecToken(recv), fieldOf(args.get(1))));
                return;
            }
            String tok = "call(" + shortRef(mi.owner) + "." + mi.name + ")";
            E v = recv != null && !recv.op.equals("buf") ? recv : (args.size() > 1 ? args.get(0) : null);
            events.add(new Op(tok, fieldOf(v)));
        }

        /** 사건 인자로 쓰인 함수 값(메서드 참조·람다)의 와이어 토큰. */
        private String fnToken(E fn) {
            if (!fn.op.startsWith("fn#")) return pl.codecToken(fn);   // 인코더 자리에 코덱을 그대로 넘긴 경우
            Fn f = Fn.parse(fn.op);
            // FriendlyByteBuf::writeXxx 메서드 참조
            if (f.owner.endsWith("FriendlyByteBuf") || f.owner.endsWith("ByteBuf")) {
                String t = BUF.get(f.name);
                return t != null ? t : "?" + f.name;
            }
            if (f.name.equals("encode") && !fn.args.isEmpty()) return pl.codecToken(fn.args.get(0));
            MethodNode lm = pl.findMethod(f.owner, f.name, f.desc);
            // 컴파일러가 만든 람다만 몸통을 따라간다. 이름 있는 도우미(다른 값 타입의 write)는 안쪽 값 타입이라 불투명 호출로 둔다.
            if (lm == null || !f.name.startsWith("lambda$")) return "call(" + shortRef(f.owner) + "." + f.name + ")";
            // 합성 람다 또는 정적 도우미 — 몸통을 따라간다
            List<E> a = bindArgs(lm, fn.args);
            Exec x = new Exec(pl, lm, a, f.name);
            x.run();
            if (x.problem != null) return "?lambda:" + x.problem;
            if (x.branched) return "?lambda-branch";
            if (x.events.size() == 1) return x.events.get(0).token();
            StringBuilder sb = new StringBuilder("struct(");
            for (int i = 0; i < x.events.size(); i++) {
                if (i > 0) sb.append(',');
                sb.append(x.events.get(i).token());
            }
            return sb.append(')').toString();
        }

        private static String fieldOf(E e) {
            if (e == null) return null;
            if (e.op.startsWith("field:")) {
                // this.<필드> 만
                if (!e.args.isEmpty() && e.args.get(0).op.equals("this")) return e.op.substring(6);
                // 중첩 접근: 마지막 이름
                return e.op.substring(6);
            }
            if (e.args.size() == 1 && e.args.get(0).op.startsWith("field:")) return fieldOf(e.args.get(0));
            if (e.op.endsWith(".name") || e.op.endsWith(".ordinal")) return e.args.isEmpty() ? null : fieldOf(e.args.get(0));
            // 게터 호출 this.getX()
            if (!e.args.isEmpty() && e.args.get(0).op.equals("this") && e.op.contains(".")) return e.op.substring(e.op.lastIndexOf('.') + 1) + "()";
            return null;
        }

        private void indy(InvokeDynamicInsnNode d) {
            Type[] params = Type.getArgumentTypes(d.desc);
            List<E> caps = new ArrayList<>();
            for (int i = 0; i < params.length; i++) caps.add(0, pop());
            boolean lambda = d.bsm.getOwner().equals("java/lang/invoke/LambdaMetafactory");
            if (lambda && d.bsmArgs.length >= 2 && d.bsmArgs[1] instanceof Handle h) {
                push(new E("fn#" + h.getOwner() + "." + h.getName() + h.getDesc(), caps, false));
            } else {
                push("indy");
            }
        }
    }

    /** 전체 표(Mojang 이름이 든다 — 캐시에만 둔다). 열: 상태 방향 와이어이름 클래스 방식 손으로남는이유 와이어토큰(필드). */
    public static List<String> fullLines(List<Packet> packets) {
        List<String> out = new ArrayList<>();
        for (Packet p : packets) {
            out.add(p.state() + "\t" + (p.serverbound() ? "S" : "C") + "\t" + p.wireName() + "\t" + p.className() + "\t" + p.form() + "\t"
                    + (p.custom() == null ? "-" : p.custom()) + "\t" + (p.ops().isEmpty() ? "-" : joinOps(p.ops(), false)));
        }
        Collections.sort(out);
        return out;
    }

    private static final java.util.regex.Pattern OPAQUE = java.util.regex.Pattern.compile("(ref|call)\\(([^()]*)\\)");

    /**
     * 커밋하는 중립 잠금 줄. Mojang 이름이 든 불투명 참조(`ref(…)`·`call(…)`)는 이름의 SHA-256 앞 8자리로 바꾼다(`ref#a1b2c3d4`).
     * 손으로 남는 이유는 종류만(콜론 앞)이다. 열: 상태 방향 와이어이름 방식 이유종류 와이어토큰(필드).
     */
    public static List<String> lockLines(List<Packet> packets) {
        List<String> out = new ArrayList<>();
        for (Packet p : packets) {
            String custom = p.custom() == null ? "-" : p.custom().replaceAll(":.*", "");
            String layout = p.ops().isEmpty() ? "-" : joinOps(p.ops(), true);
            out.add(p.state() + "\t" + (p.serverbound() ? "S" : "C") + "\t" + p.wireName() + "\t" + p.form() + "\t" + custom + "\t" + layout);
        }
        Collections.sort(out);
        return out;
    }

    private static String joinOps(List<Op> ops, boolean neutral) {
        StringBuilder sb = new StringBuilder();
        for (Op o : ops) {
            if (sb.length() > 0) sb.append(' ');
            String t = neutral ? neutralToken(o.token()) : o.token();
            sb.append(t);
            if (o.field() != null) sb.append('@').append(o.field());
        }
        return sb.toString();
    }

    static String neutralToken(String tok) {
        java.util.regex.Matcher m = OPAQUE.matcher(tok);
        StringBuilder sb = new StringBuilder();
        while (m.find()) m.appendReplacement(sb, "ref#" + fingerprint(m.group(2)));
        m.appendTail(sb);
        String r = sb.toString();
        return r.replaceAll("\\?[A-Za-z0-9_.$#-]*(\\([^()]*\\))?", "?");   // 못 푼 토큰은 이름 없이 ?
    }

    static String fingerprint(String s) {
        try {
            byte[] d = java.security.MessageDigest.getInstance("SHA-256").digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(d, 0, 4);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
