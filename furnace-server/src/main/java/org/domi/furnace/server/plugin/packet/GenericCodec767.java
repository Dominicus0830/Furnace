// 1.21.1 범용 패킷 코덱(E4): 중립 스키마(PacketSchema.protocol767)의 필드 목록으로 타입 레코드가 없는 종류를 GenericPacket 으로 읽고 쓴다.
// 스키마에서 와이어가 필드 나열로 떨어지는 종류(custom 이 아닌 것)만, 그리고 아래 와이어를 아는 것만 다룬다. 나머지는 readable=false
// (리스너는 내용 없는 GenericPacket 을 받는다). 읽을 때 본문을 끝까지 다 읽지 못하면 실패로 본다(모양이 어긋난 것).
// 값 종류는 api GenericPacket 머리말 표. 바닐라 와이어 근거는 docs/feature-parity/context-notes/2026-11-12-easy-nms-e4a.md.
package org.domi.furnace.server.plugin.packet;

import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.domi.furnace.api.item.ItemStack;
import org.domi.furnace.api.nbt.NbtCompound;
import org.domi.furnace.api.packet.GenericPacket;
import org.domi.furnace.api.packet.PacketDirection;
import org.domi.furnace.api.packet.PacketField;
import org.domi.furnace.api.packet.PacketStruct;
import org.domi.furnace.api.packet.PacketType;
import org.domi.furnace.api.packet.PacketTypes;
import org.domi.furnace.api.packet.schema.FieldDef;
import org.domi.furnace.api.packet.schema.PacketDef;
import org.domi.furnace.api.packet.schema.PacketSchema;
import org.domi.furnace.api.packet.schema.StructDef;
import org.domi.furnace.api.packet.schema.TypeDecl;
import org.domi.furnace.api.packet.schema.TypeRef;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.api.world.BlockData;
import org.domi.furnace.api.world.BlockPos;
import org.domi.furnace.api.world.GlobalPos;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.item.ItemStackCodecs;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.io.nbt.NbtWriter;
import org.domi.furnace.server.plugin.ApiConvert;
import org.domi.furnace.server.plugin.ApiNbt;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.ItemRegistry;

public final class GenericCodec767 {

    /** play 종류를 찾을 스키마 상태(앞쪽 우선). */
    private static final List<String> PLAY_STATES = List.of("game", "common", "cookie", "ping");
    private static final int STRING_MAX = 32767;

    /** 레지스트리 번호로 나가는 타입(type 줄 wire=registry_id) → 레지스트리. 없는 것(블록 엔티티 종류 등)은 아직 못 읽는다. */
    private static final Map<String, String> REGISTRY_IDS = Map.of(
            "net.minecraft.world.item.Item", "minecraft:item",
            "net.minecraft.world.level.block.Block", "minecraft:block",
            "net.minecraft.world.entity.EntityType", "minecraft:entity_type",
            "net.minecraft.world.inventory.MenuType", "minecraft:menu");

    /**
     * Holder&lt;X&gt; 의 와이어(바닐라 X.STREAM_CODEC, 바이트코드로 확인). PLAIN = holderRegistry(번호 그대로),
     * PLUS_ONE = holder(레지스트리, 직접 코덱)(등록된 것 번호 + 1, 0 = 직접 정의).
     */
    private enum HolderForm { PLAIN, PLUS_ONE }

    private record HolderInfo(String registry, HolderForm form) {}

    private static final Map<String, HolderInfo> HOLDERS = Map.of(
            "MobEffect", new HolderInfo("minecraft:mob_effect", HolderForm.PLAIN),
            "DimensionType", new HolderInfo("minecraft:dimension_type", HolderForm.PLAIN),
            "SoundEvent", new HolderInfo("minecraft:sound_event", HolderForm.PLUS_ONE));

    /** 아직 다루지 않는 모양 — 그 종류는 readable=false. */
    static final class Unsupported extends RuntimeException {
        Unsupported(String why) { super(why, null, false, false); }
    }

    /** 값 하나의 읽기·쓰기. kind 는 api 값 종류(PacketStruct.VALUE_KINDS 중 하나). */
    private interface Node {
        Object read(ByteBuf b);

        void write(ByteBuf o, Object v);

        Class<?> kind();
    }

    private record Compiled(List<String> names, List<Node> nodes, List<PacketField<?>> fields) {}

    private final PacketSchema schema = PacketSchema.protocol767();
    private final Supplier<BlockRegistry> blocks;
    private final Supplier<ItemRegistry> items;
    private final Map<PacketType<?>, Compiled> packets = new IdentityHashMap<>();
    private final Map<PacketType<?>, String> unsupported = new IdentityHashMap<>();

    GenericCodec767(Supplier<BlockRegistry> blocks, Supplier<ItemRegistry> items) {
        this.blocks = blocks;
        this.items = items;
        for (PacketDirection dir : PacketDirection.values()) {
            for (PacketType<?> t : PacketTypes.all(dir)) {
                if (t.typed()) continue;
                PacketDef d = find(t);
                if (d == null) {
                    unsupported.put(t, "스키마에 없다");
                    continue;
                }
                try {
                    packets.put(t, compile(d));
                } catch (Unsupported e) {
                    unsupported.put(t, e.getMessage());
                }
            }
        }
    }

    private PacketDef find(PacketType<?> t) {
        String wire = t.name().substring(t.name().indexOf(':') + 1);
        for (String state : PLAY_STATES) {
            Optional<PacketDef> d = schema.packet(state, t.direction(), wire);
            if (d.isPresent()) return d.get();
        }
        return null;
    }

    /** 이 종류를 읽고 쓸 수 있는가. */
    public boolean readable(PacketType<?> type) { return packets.containsKey(type); }

    /** 읽을 수 없는 까닭(읽을 수 있거나 타입 레코드 종류면 null). */
    public String whyUnreadable(PacketType<?> type) { return unsupported.get(type); }

    /** 필드(이름·값 종류, 바닐라 순서). 읽을 수 없으면 빈 목록. */
    public List<PacketField<?>> fields(PacketType<?> type) {
        Compiled c = packets.get(type);
        return c == null ? List.of() : c.fields();
    }

    /** 본문을 끝까지 읽어 GenericPacket 으로. 모양이 어긋나면(남거나 모자라면) 예외. */
    @SuppressWarnings("unchecked")
    public GenericPacket decode(PacketType<?> type, ByteBuf b) {
        Compiled c = packets.get(type);
        if (c == null) throw new IllegalArgumentException("범용 코덱이 다루지 않는 종류: " + type);
        PacketStruct s = readStruct(c, b);
        if (b.isReadable()) throw new IllegalArgumentException(type + " 본문이 " + b.readableBytes() + "바이트 남았다(스키마와 와이어가 다르다)");
        return GenericPacket.of((PacketType<GenericPacket>) type, s);
    }

    /** 패킷 본문을 쓴다(id 는 부르는 쪽이). 필드가 빠졌거나 모르는 필드·종류가 다른 값이면 IllegalArgumentException. */
    public void encode(GenericPacket p, ByteBuf o) {
        Compiled c = packets.get(p.type());
        if (c == null) throw new IllegalArgumentException("이 서버 버전에서 내용을 쓸 수 없는 패킷: " + p.type() + " (" + unsupported.get(p.type()) + ")");
        if (!p.readable()) throw new IllegalArgumentException("내용 없는 패킷은 보낼 수 없다: " + p.type());
        writeStruct(c, p.fields(), o, p.type().toString());
    }

    // ------------------------------------------------------------------ 묶음

    private static PacketStruct readStruct(Compiled c, ByteBuf b) {
        LinkedHashMap<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < c.nodes().size(); i++) m.put(c.names().get(i), c.nodes().get(i).read(b));
        return PacketStruct.of(m);
    }

    private static void writeStruct(Compiled c, PacketStruct s, ByteBuf o, String where) {
        Map<String, Object> m = s.asMap();
        if (!m.keySet().equals(Set.copyOf(c.names()))) {
            List<String> missing = new ArrayList<>(c.names());
            missing.removeAll(m.keySet());
            List<String> extra = new ArrayList<>(m.keySet());
            extra.removeAll(c.names());
            throw new IllegalArgumentException(where + ": " + (missing.isEmpty() ? "" : "빠진 필드 " + missing + " ")
                    + (extra.isEmpty() ? "" : "모르는 필드 " + extra + " ") + "(필드: " + c.fields() + ")");
        }
        for (int i = 0; i < c.nodes().size(); i++) {
            String n = c.names().get(i);
            Node node = c.nodes().get(i);
            Object v = m.get(n);
            if (!node.kind().isInstance(v)) {
                throw new IllegalArgumentException(where + "." + n + " 은 " + node.kind().getSimpleName() + " 값이어야 한다: " + v.getClass().getSimpleName());
            }
            try {
                node.write(o, v);
            } catch (IllegalArgumentException | ClassCastException e) {
                throw new IllegalArgumentException(where + "." + n + ": " + e.getMessage(), e);
            }
        }
    }

    // ------------------------------------------------------------------ 컴파일

    private Compiled compile(PacketDef d) {
        if (d.custom()) throw new Unsupported("손으로 다뤄야 하는 와이어(custom)");
        if (d.superType() != null) throw new Unsupported("부모 클래스 필드");
        return compileFields(d.fields(), d.javaPackage());
    }

    private Compiled compileFields(List<FieldDef> fields, String pkg) {
        List<String> names = new ArrayList<>();
        List<Node> nodes = new ArrayList<>();
        List<PacketField<?>> pf = new ArrayList<>();
        for (FieldDef f : fields) {
            String n = snake(f.name());
            if (names.contains(n)) throw new IllegalStateException("snake_case 이름이 겹친다: " + n);
            Node node = node(f.type(), f.wire(), pkg);
            if (f.nullable()) node = node == NBT ? NULLABLE_NBT : optional(node, true);
            names.add(n);
            nodes.add(node);
            pf.add(PacketField.of(n, node.kind()));
        }
        return new Compiled(List.copyOf(names), List.copyOf(nodes), List.copyOf(pf));
    }

    /** Mojang camelCase → snake_case(cameraId → camera_id, yHeadRot → y_head_rot). */
    static String snake(String camel) {
        StringBuilder b = new StringBuilder(camel.length() + 4);
        for (int i = 0; i < camel.length(); i++) {
            char c = camel.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0) b.append('_');
                b.append(Character.toLowerCase(c));
            } else {
                b.append(c);
            }
        }
        return b.toString();
    }

    private Node node(TypeRef t, String wire, String pkg) {
        if (t.bound() != TypeRef.Bound.NONE) throw new Unsupported("와일드카드 타입 " + t);
        if (t.arrayDimensions() > 0) {
            if (t.arrayDimensions() == 1 && wire == null) {
                switch (t.name()) {
                    case "byte": return BYTES;
                    case "long": return LONGS;
                    case "int": return VARINTS;
                    default: break;
                }
            }
            throw new Unsupported("배열 " + t);
        }
        switch (t.name()) {
            case "int": return integer(wire == null ? "varint" : wire, Integer.class);
            case "short": return integer(wire == null ? "i16" : wire, Short.class);
            case "byte": return integer(wire == null ? "i8" : wire, Byte.class);
            case "long":
                if (wire == null || wire.equals("i64")) return I64;
                if (wire.equals("varlong")) return VARLONG;
                throw new Unsupported("long 와이어 " + wire);
            case "float": return plain(wire, "f32", F32);
            case "double": return plain(wire, "f64", F64);
            case "boolean": return plain(wire, "bool", BOOL);
            case "String": return plain(wire, "string", STRING);
            case "UUID": return plain(wire, "uuid", UUID_NODE);
            case "List", "Set", "Collection":
                if (wire != null) throw new Unsupported("목록 명시 와이어 " + wire);
                return list(node(t.arguments().get(0), null, pkg));
            case "Optional":
                if (wire != null) throw new Unsupported("Optional 명시 와이어 " + wire);
                return optional(node(t.arguments().get(0), null, pkg), false);
            case "Holder": return holder(t.arguments().get(0));
            case "ResourceKey": return plain(wire, "identifier", IDENTIFIER);
            default: break;
        }
        String bin;
        try {
            bin = schema.resolve(t.name(), pkg);
        } catch (IllegalArgumentException e) {
            throw new Unsupported(e.getMessage());
        }
        if (bin == null) throw new Unsupported("타입 " + t);
        TypeDecl decl = schema.type(bin).orElse(null);
        StructDef struct = schema.struct(bin).orElse(null);
        boolean isEnum = (decl != null && decl.kind() == TypeDecl.Kind.ENUM) || (struct != null && struct.kind() == StructDef.Kind.ENUM);
        List<String> constants = decl != null ? decl.constants() : struct != null ? struct.constants() : List.of();
        if (wire != null) {   // 필드 줄 명시 와이어: 크기만 다른 정수
            if (isEnum) return enumNode(bin, constants, wire);
            if (struct != null || decl != null) return integer(wire, Integer.class);   // 예: game_event 의 Type(u8) — 바닐라 번호를 정수로
            throw new Unsupported("명시 와이어 " + wire + " on " + t);
        }
        if (decl != null) {
            if (decl.kind() == TypeDecl.Kind.ENUM) {
                if ("string".equals(decl.wire())) return enumString(bin, constants);
                return enumNode(bin, constants, decl.wire() == null ? "varint" : decl.wire());
            }
            String w = decl.wire();
            if (w == null) throw new Unsupported("와이어 없는 타입 " + bin);
            return switch (w) {
                case "component" -> TEXT;
                case "identifier" -> IDENTIFIER;
                case "block_pos" -> BLOCK_POS;
                case "global_pos" -> GLOBAL_POS;
                case "item_stack" -> itemStack();
                case "block_state" -> blockState();
                case "nbt" -> NBT;
                case "i64" -> I64;
                case "varint" -> integer("varint", Integer.class);
                case "registry_id" -> {
                    String reg = REGISTRY_IDS.get(bin);
                    if (reg == null) throw new Unsupported("레지스트리 번호 " + bin);
                    yield registryId(reg);
                }
                default -> throw new Unsupported("와이어 " + w + " (" + bin + ")");
            };
        }
        if (struct != null) {
            return switch (struct.kind()) {
                case ENUM -> enumNode(bin, constants, "varint");
                case RECORD, CLASS -> structNode(compileFields(struct.fields(), struct.javaPackage()));
                default -> throw new Unsupported("내포 " + struct.kind() + " " + bin);
            };
        }
        throw new Unsupported("타입 " + bin);
    }

    private static Node plain(String wire, String expected, Node n) {
        if (wire != null && !wire.equals(expected)) throw new Unsupported(expected + " 자리의 명시 와이어 " + wire);
        return n;
    }

    // ------------------------------------------------------------------ 값 종류

    private static Node node(Class<?> kind, java.util.function.Function<ByteBuf, Object> r, java.util.function.BiConsumer<ByteBuf, Object> w) {
        return new Node() {
            @Override
            public Object read(ByteBuf b) { return r.apply(b); }

            @Override
            public void write(ByteBuf o, Object v) { w.accept(o, v); }

            @Override
            public Class<?> kind() { return kind; }
        };
    }

    /** 정수 와이어 하나 — 값은 Java 필드 타입의 상자(Integer·Short·Byte). 쓸 때 와이어 범위를 넘으면 IllegalArgumentException. */
    private static Node integer(String wire, Class<?> box) {
        long min, max;
        switch (wire) {
            case "varint", "i32" -> { min = Integer.MIN_VALUE; max = Integer.MAX_VALUE; }
            case "i16" -> { min = Short.MIN_VALUE; max = Short.MAX_VALUE; }
            case "u16" -> { min = 0; max = 0xFFFF; }
            case "i8" -> { min = Byte.MIN_VALUE; max = Byte.MAX_VALUE; }
            case "u8" -> { min = 0; max = 0xFF; }
            default -> throw new Unsupported("정수 와이어 " + wire);
        }
        if (box == Byte.class && (min < Byte.MIN_VALUE || max > Byte.MAX_VALUE)) throw new Unsupported("byte 필드의 와이어 " + wire);
        if (box == Short.class && (min < Short.MIN_VALUE || max > Short.MAX_VALUE)) throw new Unsupported("short 필드의 와이어 " + wire);
        return node(box, b -> {
            int v = switch (wire) {
                case "varint" -> Types.readVarInt(b);
                case "i32" -> b.readInt();
                case "i16" -> b.readShort();
                case "u16" -> b.readUnsignedShort();
                case "i8" -> b.readByte();
                default -> b.readUnsignedByte();
            };
            return box == Integer.class ? (Object) v : box == Short.class ? (Object) (short) v : (Object) (byte) v;
        }, (o, x) -> {
            int v = ((Number) x).intValue();
            if (v < min || v > max) throw new IllegalArgumentException(wire + " 범위(" + min + "~" + max + ") 밖: " + v);
            switch (wire) {
                case "varint" -> Types.writeVarInt(o, v);
                case "i32" -> o.writeInt(v);
                case "i16", "u16" -> o.writeShort(v);
                default -> o.writeByte(v);
            }
        });
    }

    private static final Node I64 = node(Long.class, ByteBuf::readLong, (o, v) -> o.writeLong((Long) v));
    private static final Node VARLONG = node(Long.class, Types::readVarLong, (o, v) -> Types.writeVarLong(o, (Long) v));
    private static final Node F32 = node(Float.class, ByteBuf::readFloat, (o, v) -> o.writeFloat((Float) v));
    private static final Node F64 = node(Double.class, ByteBuf::readDouble, (o, v) -> o.writeDouble((Double) v));
    private static final Node BOOL = node(Boolean.class, ByteBuf::readBoolean, (o, v) -> o.writeBoolean((Boolean) v));
    private static final Node STRING = node(String.class, b -> Types.readString(b, STRING_MAX), (o, v) -> Types.writeString(o, (String) v));
    private static final Node UUID_NODE = node(UUID.class, Types::readUuid, (o, v) -> Types.writeUuid(o, (UUID) v));
    private static final Node IDENTIFIER = node(String.class, b -> Types.readString(b, STRING_MAX), (o, v) -> Types.writeString(o, identifier((String) v)));
    private static final Node TEXT = node(Text.class, NetworkText::read, (o, v) -> NetworkText.write(o, (Text) v));
    private static final Node BLOCK_POS = node(BlockPos.class, GenericCodec767::readPos, (o, v) -> writePos(o, (BlockPos) v));
    private static final Node GLOBAL_POS = node(GlobalPos.class, b -> new GlobalPos(Types.readString(b, STRING_MAX), readPos(b)), (o, v) -> {
        GlobalPos g = (GlobalPos) v;
        Types.writeString(o, identifier(g.dimension()));
        writePos(o, g.pos());
    });
    private static final Node NBT = node(NbtCompound.class, b -> ApiNbt.toApi(EntityData767.readCompound(b)),
            (o, v) -> new NbtWriter(o).writeCompound(ApiNbt.toServer((NbtCompound) v)));

    /** 없을 수 있는 CompoundTag — 바닐라 FriendlyByteBuf.writeNbt(null) 은 bool 머리가 아니라 END 태그(0) 한 바이트다(tag_query, 바이트코드 확인). */
    private static final Node NULLABLE_NBT = node(Optional.class, b -> {
        if (b.getByte(b.readerIndex()) == 0) {
            b.skipBytes(1);
            return Optional.empty();
        }
        return Optional.of(NBT.read(b));
    }, (o, v) -> {
        Optional<?> opt = (Optional<?>) v;
        if (opt.isEmpty()) o.writeByte(0);
        else if (opt.get() instanceof NbtCompound c) NBT.write(o, c);
        else throw new IllegalArgumentException("값은 NbtCompound 이어야 한다: " + opt.get());
    });

    private static final Node BYTES = node(byte[].class, b -> {
        int n = Types.readVarInt(b);
        if (n < 0 || n > b.readableBytes()) throw new IllegalArgumentException("바이트 길이 오류: " + n);
        byte[] a = new byte[n];
        b.readBytes(a);
        return a;
    }, (o, v) -> {
        byte[] a = (byte[]) v;
        Types.writeVarInt(o, a.length);
        o.writeBytes(a);
    });
    private static final Node LONGS = node(long[].class, b -> {
        int n = count(b, 8);
        long[] a = new long[n];
        for (int i = 0; i < n; i++) a[i] = b.readLong();
        return a;
    }, (o, v) -> {
        long[] a = (long[]) v;
        Types.writeVarInt(o, a.length);
        for (long x : a) o.writeLong(x);
    });
    private static final Node VARINTS = node(int[].class, b -> {
        int n = count(b, 1);
        int[] a = new int[n];
        for (int i = 0; i < n; i++) a[i] = Types.readVarInt(b);
        return a;
    }, (o, v) -> {
        int[] a = (int[]) v;
        Types.writeVarInt(o, a.length);
        for (int x : a) Types.writeVarInt(o, x);
    });

    /** 원소 수(원소 하나가 적어도 minBytes 라 남은 바이트로 상한을 건다). */
    private static int count(ByteBuf b, int minBytes) {
        int n = Types.readVarInt(b);
        if (n < 0 || (long) n * minBytes > b.readableBytes()) throw new IllegalArgumentException("원소 수 오류: " + n);
        return n;
    }

    private static Node list(Node elem) {
        return node(List.class, b -> {
            int n = count(b, 1);   // 원소는 모두 1바이트 이상
            List<Object> l = new ArrayList<>(Math.min(n, 4096));
            for (int i = 0; i < n; i++) l.add(elem.read(b));
            return Collections.unmodifiableList(l);
        }, (o, v) -> {
            List<?> l = (List<?>) v;
            Types.writeVarInt(o, l.size());
            for (int i = 0; i < l.size(); i++) {
                Object x = l.get(i);
                if (!elem.kind().isInstance(x)) throw new IllegalArgumentException("[" + i + "] 은 " + elem.kind().getSimpleName() + " 값이어야 한다: " + x);
                elem.write(o, x);
            }
        });
    }

    /** bool 머리 + 값(바닐라 optional·writeNullable 둘 다 같은 와이어). */
    private static Node optional(Node elem, boolean nullable) {
        return node(Optional.class, b -> b.readBoolean() ? Optional.of(elem.read(b)) : Optional.empty(), (o, v) -> {
            Optional<?> opt = (Optional<?>) v;
            o.writeBoolean(opt.isPresent());
            if (opt.isPresent()) {
                Object x = opt.get();
                if (!elem.kind().isInstance(x)) throw new IllegalArgumentException("값은 " + elem.kind().getSimpleName() + " 이어야 한다: " + x);
                elem.write(o, x);
            }
        });
    }

    private static Node structNode(Compiled c) {
        return node(PacketStruct.class, b -> readStruct(c, b), (o, v) -> writeStruct(c, (PacketStruct) v, o, "struct"));
    }

    /**
     * enum: 값은 바닐라 상수 이름. 번호는 서수(스키마 상수 순서 = 바닐라 선언 순서). i8 은 바닐라 GameType.byNullableId 처럼 -1 = 없음이라
     * Optional&lt;String&gt; 이다(previous_game_type).
     */
    private static Node enumNode(String bin, List<String> constants, String wire) {
        if (constants.isEmpty()) throw new Unsupported("상수 목록 없는 enum " + bin);
        if (wire.equals("i8")) {
            return node(Optional.class, b -> {
                int i = b.readByte();
                return i == -1 ? Optional.empty() : Optional.of(constantAt(constants, i, bin));
            }, (o, v) -> {
                Optional<?> opt = (Optional<?>) v;
                o.writeByte(opt.isEmpty() ? -1 : indexOf(constants, opt.get(), bin));
            });
        }
        Node n = integer(wire, Integer.class);
        return node(String.class, b -> constantAt(constants, (Integer) n.read(b), bin), (o, v) -> n.write(o, indexOf(constants, v, bin)));
    }

    /** 와이어가 문자열인 enum(바닐라 StringRepresentable 이름 = 상수 이름 소문자). */
    private static Node enumString(String bin, List<String> constants) {
        return node(String.class, b -> {
            String s = Types.readString(b, STRING_MAX).toUpperCase(Locale.ROOT);
            return constantAt(constants, constants.indexOf(s), bin);
        }, (o, v) -> Types.writeString(o, constants.get(indexOf(constants, v, bin)).toLowerCase(Locale.ROOT)));
    }

    private static String constantAt(List<String> constants, int i, String bin) {
        if (i < 0 || i >= constants.size()) throw new IllegalArgumentException(simple(bin) + " 범위 밖: " + i);
        return constants.get(i);
    }

    private static int indexOf(List<String> constants, Object v, String bin) {
        int i = v instanceof String s ? constants.indexOf(s) : -1;
        if (i < 0) throw new IllegalArgumentException(simple(bin) + " 상수가 아니다: " + v + " (" + constants + ")");
        return i;
    }

    private static String simple(String bin) { return bin.substring(bin.lastIndexOf('.') + 1); }

    private Node itemStack() {
        return node(ItemStack.class, b -> ApiConvert.item(ItemStackCodecs.readStack(b), items.get()),
                (o, v) -> ItemStackCodecs.writeStack(o, ApiConvert.item((ItemStack) v, items.get())));
    }

    private Node blockState() {
        return node(BlockData.class, b -> ApiConvert.block(Types.readVarInt(b), blocks.get()),
                (o, v) -> Types.writeVarInt(o, ApiConvert.stateId((BlockData) v, blocks.get())));
    }

    private static Node registryId(String registry) {
        return node(String.class, b -> registryName(registry, Types.readVarInt(b)), (o, v) -> Types.writeVarInt(o, registryNumber(registry, (String) v)));
    }

    private static Node holder(TypeRef arg) {
        HolderInfo h = HOLDERS.get(arg.name());
        if (h == null) throw new Unsupported("Holder<" + arg + ">");
        if (h.form() == HolderForm.PLAIN) return registryId(h.registry());
        // holder(레지스트리, 직접 코덱): 등록된 것 = 번호 + 1, 0 = 직접 정의. 소리의 직접 정의는 이름 + 고정 범위(선택) — 범위 없는 것만 다룬다.
        return node(String.class, b -> {
            int v = Types.readVarInt(b);
            if (v > 0) return registryName(h.registry(), v - 1);
            String name = Types.readString(b, STRING_MAX);
            if (b.readBoolean()) throw new IllegalArgumentException("고정 범위가 있는 직접 정의 소리는 아직 읽지 않는다: " + name);
            return name;
        }, (o, v) -> {
            String name = identifier((String) v);
            Integer id = ItemContext.installed() ? ItemContext.id(h.registry(), name) : null;
            if (id != null) {
                Types.writeVarInt(o, id + 1);
            } else {   // 등록되지 않은 이름(리소스 팩 소리) — 바닐라처럼 직접 정의로
                Types.writeVarInt(o, 0);
                Types.writeString(o, name);
                o.writeBoolean(false);
            }
        });
    }

    private static String registryName(String registry, int id) {
        String n = ItemContext.installed() ? ItemContext.name(registry, id) : null;
        if (n == null) throw new IllegalArgumentException(registry + " 에 없는 번호: " + id);
        return n;
    }

    private static int registryNumber(String registry, String name) {
        Integer id = ItemContext.installed() ? ItemContext.id(registry, identifier(name)) : null;
        if (id == null) throw new IllegalArgumentException(registry + " 에 없는 이름: " + name);
        return id;
    }

    /** 이름공간 없는 이름은 minecraft: 를 붙인다(바닐라 ResourceLocation 과 같은 뜻). */
    private static String identifier(String s) { return s.indexOf(':') < 0 ? "minecraft:" + s : s; }

    private static BlockPos readPos(ByteBuf b) {
        int[] p = Types.readBlockPos(b);
        return new BlockPos(p[0], p[1], p[2]);
    }

    private static void writePos(ByteBuf o, BlockPos p) { Types.writeBlockPos(o, p.x(), p.y(), p.z()); }
}
