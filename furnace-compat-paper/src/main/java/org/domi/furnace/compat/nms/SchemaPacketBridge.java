package org.domi.furnace.compat.nms;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.domi.furnace.api.Furnace;
import org.domi.furnace.api.packet.GenericPacket;
import org.domi.furnace.api.packet.PacketDirection;
import org.domi.furnace.api.packet.PacketStruct;
import org.domi.furnace.api.packet.PacketType;
import org.domi.furnace.api.packet.PacketTypes;
import org.domi.furnace.api.packet.schema.FieldDef;
import org.domi.furnace.api.packet.schema.PacketDef;
import org.domi.furnace.api.packet.schema.PacketSchema;
import org.domi.furnace.api.packet.schema.StructDef;
import org.domi.furnace.api.packet.schema.TypeDecl;
import org.domi.furnace.api.packet.schema.TypeRef;

/**
 * 스키마로 생성한 clientbound 패킷 shim → Furnace {@link GenericPacket}(easy-nms E4c). 보내기 다리가 쓴다. 플러그인용이 아니다.
 *
 * <p>필드 나열 종류(스키마 custom 이 아닌 것)는 스키마 필드를 차례로 바꾼다 — 이름은 snake_case, 값은 서버 범용 코덱(GenericCodec767)이
 * 같은 스키마 타입에 쓰는 값 종류와 같다(Component → Text, BlockPos → api BlockPos, enum → 상수 이름, Holder·ResourceKey·ResourceLocation → 이름,
 * 내포 record·class → PacketStruct, nullable → Optional). 바닐라 와이어 바이트는 서버 코덱이 만든다.
 * 손 코덱 종류(custom)와 타입 레코드 종류는 아직 바꾸지 않는다(E4c-2·E4c-3) — {@link #supports} 가 false.
 */
public final class SchemaPacketBridge {

    private SchemaPacketBridge() {}

    private static final PacketSchema SCHEMA = PacketSchema.protocol767();

    /** 서버 범용 코덱의 Holder 표와 같다(그 밖의 Holder 는 서버도 못 쓴다). */
    private static final Set<String> HOLDERS = Set.of("MobEffect", "DimensionType", "SoundEvent", "Attribute");

    /** 이 다리가 값을 바꿀 줄 아는 타입 와이어(나머지 — global_pos·number_format 등 — 는 shim 이 아직 빈 껍데기다). */
    private static final Set<String> WIRES = Set.of("component", "identifier", "block_pos", "item_stack", "block_state", "nbt", "i64", "vec3", "registry_id");

    private static final Map<String, Boolean> SUPPORTS = new ConcurrentHashMap<>();

    /** 스키마 키("game/clientbound/open_sign_editor")의 패킷 정의. */
    static Optional<PacketDef> def(String schemaKey) {
        String[] k = schemaKey.split("/", 3);
        if (k.length != 3) return Optional.empty();
        PacketDirection dir = k[1].equals("clientbound") ? PacketDirection.CLIENTBOUND : k[1].equals("serverbound") ? PacketDirection.SERVERBOUND : null;
        return dir == null ? Optional.empty() : SCHEMA.packet(k[0], dir, k[2]);
    }

    /** 그 패킷의 Furnace 종류(play 단계 clientbound 만, 없으면 null). */
    private static PacketType<?> type(PacketDef d) {
        if (d.direction() != PacketDirection.CLIENTBOUND || !(d.state().equals("game") || d.state().equals("common"))) return null;
        return PacketTypes.byName(PacketDirection.CLIENTBOUND, "minecraft:" + d.wireName());
    }

    /**
     * 이 패킷 정의를 보낼 수 있게 바꿀 수 있는가(스키마만 보고 정한다 — 서버 없이도 된다). play 단계 clientbound, 타입 레코드가 아닌 종류, 필드 나열(custom 아님),
     * 모든 필드 타입을 이 다리가 아는 것.
     */
    public static boolean supports(PacketDef d) {
        return SUPPORTS.computeIfAbsent(d.state() + "/" + d.direction() + "/" + d.wireName(), k -> {
            PacketType<?> t = type(d);
            if (t == null || t.typed() || d.custom() || d.superType() != null) return false;
            for (FieldDef f : d.fields()) if (!supported(f.type(), f.wire(), d.javaPackage(), 0)) return false;
            return true;
        });
    }

    private static boolean supported(TypeRef t, String wire, String pkg, int depth) {
        if (depth > 8 || t.bound() != TypeRef.Bound.NONE) return false;
        if (t.arrayDimensions() > 0) return t.arrayDimensions() == 1 && wire == null && Set.of("byte", "long", "int").contains(t.name());
        switch (t.name()) {
            case "int", "short", "byte", "long", "float", "double", "boolean", "String", "UUID", "ResourceKey", "Instant": return true;
            case "List", "Set", "Collection", "Optional": return wire == null && supported(t.arguments().get(0), null, pkg, depth + 1);
            case "Holder": return HOLDERS.contains(t.arguments().get(0).name());
            case "Map": return wire == null && t.arguments().size() == 2 && t.arguments().get(0).name().equals("String") && t.arguments().get(1).name().equals("String");
            default: break;
        }
        String bin;
        try {
            bin = SCHEMA.resolve(t.name(), pkg);
        } catch (IllegalArgumentException e) {
            return false;
        }
        if (bin == null) return false;
        TypeDecl decl = SCHEMA.type(bin).orElse(null);
        StructDef struct = SCHEMA.struct(bin).orElse(null);
        if (isEnum(decl, struct)) return true;
        if (wire != null) return intField(struct) != null;   // 명시 와이어(예: game_event 의 Type u8) = 바닐라 번호
        if (decl != null) {
            if (!WIRES.contains(String.valueOf(decl.wire()))) return false;
            return !"registry_id".equals(decl.wire()) || REGISTRY_IDS.contains(bin);
        }
        if (struct == null || !(struct.kind() == StructDef.Kind.RECORD || struct.kind() == StructDef.Kind.CLASS)) return false;
        for (FieldDef f : struct.fields()) if (!supported(f.type(), f.wire(), struct.javaPackage(), depth + 1)) return false;
        return true;
    }

    private static final Set<String> REGISTRY_IDS = Set.of("net.minecraft.world.item.Item", "net.minecraft.world.level.block.Block",
            "net.minecraft.world.entity.EntityType");

    private static boolean isEnum(TypeDecl decl, StructDef struct) {
        return (decl != null && decl.kind() == TypeDecl.Kind.ENUM) || (struct != null && struct.kind() == StructDef.Kind.ENUM);
    }

    /** 명시 와이어 자리의 보조 클래스가 들고 있는 번호 필드 하나(game_event Type 의 id). 없으면 null. */
    private static String intField(StructDef struct) {
        if (struct == null || struct.fields().size() != 1) return null;
        FieldDef f = struct.fields().get(0);
        return f.type().name().equals("int") && f.type().arrayDimensions() == 0 ? f.name() : null;
    }

    /**
     * shim → GenericPacket. {@link #supports} 가 false 인 종류이거나 이 서버가 그 종류를 읽고 쓰지 못하면 {@link UnsupportedNmsCall},
     * 값이 바닐라 모양이 아니면(필수 필드가 null 등) IllegalArgumentException.
     */
    @SuppressWarnings("unchecked")
    public static GenericPacket toFurnace(SchemaPacket packet) {
        PacketDef d = def(packet.schemaKey()).orElseThrow(() -> new UnsupportedNmsCall("send(" + packet.getClass().getName() + ")", "스키마에 없는 패킷"));
        if (!supports(d)) {
            throw new UnsupportedNmsCall("ServerCommonPacketListenerImpl#send(" + d.binaryName() + ")",
                    d.custom() ? "이 패킷은 와이어가 필드 나열이 아니라(손 코덱) 아직 shim 에서 바꾸지 못한다 — Furnace 패킷 API(docs/api/packets.md)를 쓴다"
                            : "이 패킷의 필드 값 shim 을 아직 Furnace 값으로 바꾸지 못한다 — Furnace 패킷 API(docs/api/packets.md)를 쓴다");
        }
        PacketType<?> t = type(d);
        if (!Furnace.server().packets().isReadable(t)) {
            throw new UnsupportedNmsCall("ServerCommonPacketListenerImpl#send(" + d.binaryName() + ")", "이 서버 버전이 이 패킷을 쓰지 못한다");
        }
        Object[] values = packet.schemaValues();
        if (values.length != d.fields().size()) throw new IllegalStateException(d.binaryName() + " shim 값 수가 스키마와 다르다");
        return GenericPacket.of((PacketType<GenericPacket>) t, struct(d.fields(), values, d.javaPackage(), d.wireName()));
    }

    private static PacketStruct struct(List<FieldDef> fields, Object[] values, String pkg, String where) {
        LinkedHashMap<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < fields.size(); i++) {
            FieldDef f = fields.get(i);
            Object v = values[i];
            String at = where + "." + f.name();
            m.put(snake(f.name()), f.nullable() ? (v == null ? Optional.empty() : Optional.of(value(v, f.type(), f.wire(), pkg, at))) : value(v, f.type(), f.wire(), pkg, at));
        }
        return PacketStruct.of(m);
    }

    private static Object value(Object v, TypeRef t, String wire, String pkg, String at) {
        if (t.arrayDimensions() > 0) return need(v, at);   // byte[]·long[]·int[] 그대로
        switch (t.name()) {
            case "int": return narrow((Integer) need(v, at), wire);
            case "short", "byte", "long", "float", "double", "boolean", "String", "UUID": return need(v, at);
            case "List", "Set", "Collection": {
                List<Object> out = new ArrayList<>();
                int i = 0;
                for (Object x : (Collection<?>) need(v, at)) out.add(value(x, t.arguments().get(0), null, pkg, at + "[" + i++ + "]"));
                return out;
            }
            case "Optional": {
                Optional<?> o = (Optional<?>) need(v, at);
                return o.map(x -> value(x, t.arguments().get(0), null, pkg, at));
            }
            case "Holder": {
                if (need(v, at) instanceof net.minecraft.core.Holder<?> h && h.unwrapKey().isPresent()) return h.unwrapKey().get().location().toString();
                throw new IllegalArgumentException(at + ": 등록된 것(Holder.Reference)만 보낼 수 있다 — 직접 정의는 아직 안 된다: " + v);
            }
            case "ResourceKey": return ((net.minecraft.resources.ResourceKey<?>) need(v, at)).location().toString();
            case "Instant": return ((java.time.Instant) need(v, at)).toEpochMilli();
            case "Map": {
                LinkedHashMap<String, Object> m = new LinkedHashMap<>();
                for (Map.Entry<?, ?> e : ((Map<?, ?>) need(v, at)).entrySet()) m.put((String) e.getKey(), e.getValue());
                return PacketStruct.of(m);
            }
            default: break;
        }
        String bin = SCHEMA.resolve(t.name(), pkg);
        TypeDecl decl = SCHEMA.type(bin).orElse(null);
        StructDef struct = SCHEMA.struct(bin).orElse(null);
        if (isEnum(decl, struct)) {
            if ("i8".equals(wire)) return v == null ? Optional.empty() : Optional.of(((Enum<?>) v).name());   // GameType.byNullableId 처럼 -1 = 없음
            return ((Enum<?>) need(v, at)).name();
        }
        if (wire != null) return narrow((Integer) read(need(v, at), intField(struct)), wire);
        if (decl != null) {
            Object x = need(v, at);
            return switch (decl.wire()) {
                case "component" -> net.minecraft.network.chat.Component.furnace((net.minecraft.network.chat.Component) x);
                case "identifier" -> x instanceof net.minecraft.resources.ResourceKey<?> k ? k.location().toString() : x.toString();
                case "block_pos" -> ((net.minecraft.core.BlockPos) x).furnace();
                case "item_stack" -> ((net.minecraft.world.item.ItemStack) x).furnaceValue();
                case "block_state" -> blockData((net.minecraft.world.level.block.state.BlockState) x);
                case "nbt" -> ((net.minecraft.nbt.CompoundTag) x).furnace();
                case "i64" -> x instanceof net.minecraft.core.SectionPos s ? s.asLong() : ((net.minecraft.world.level.ChunkPos) x).toLong();
                case "vec3" -> {
                    var p = (net.minecraft.world.phys.Vec3) x;
                    LinkedHashMap<String, Object> m = new LinkedHashMap<>();
                    m.put("x", p.x);
                    m.put("y", p.y);
                    m.put("z", p.z);
                    yield PacketStruct.of(m);
                }
                case "registry_id" -> registryName(x);
                default -> throw new IllegalStateException("supports 와 어긋난 와이어: " + decl.wire());
            };
        }
        Object x = need(v, at);
        Object[] values = new Object[struct.fields().size()];
        for (int i = 0; i < values.length; i++) values[i] = read(x, struct.fields().get(i).name());
        return struct(struct.fields(), values, struct.javaPackage(), at);
    }

    /**
     * int 필드의 명시 와이어(u8·i8·u16·i16): 바닐라는 writeByte·writeShort 로 아래 비트만 보낸다(블록 부수기 진행도 -1 = 지우기,
     * 컨테이너 번호 등). 같은 바이트가 나가게 그 크기로 자른다 — 서버 코덱은 범위 밖 정수를 거절하기 때문이다.
     */
    private static Integer narrow(Integer v, String wire) {
        if (wire == null) return v;
        return switch (wire) {
            case "u8" -> v & 0xFF;
            case "i8" -> (int) (byte) (int) v;
            case "u16" -> v & 0xFFFF;
            case "i16" -> (int) (short) (int) v;
            default -> v;
        };
    }

    private static Object need(Object v, String at) {
        if (v == null) throw new IllegalArgumentException(at + " 이 null 이다(바닐라도 이 패킷을 쓰다 실패한다)");
        return v;
    }

    private static String registryName(Object x) {
        if (x instanceof net.minecraft.world.item.Item i) return i.furnaceKey();
        if (x instanceof net.minecraft.world.level.block.Block b) return net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(b).toString();
        if (x instanceof net.minecraft.world.entity.EntityType<?> e) return e.furnaceKey().toString();
        throw new IllegalStateException("레지스트리 번호 타입: " + x.getClass().getName());
    }

    private static org.domi.furnace.api.world.BlockData blockData(net.minecraft.world.level.block.state.BlockState s) {
        return Furnace.server().blockTypes().byStateId(s.furnaceStateId())
                .orElseThrow(() -> new IllegalArgumentException("모르는 블록 상태 번호: " + s.furnaceStateId()));
    }

    /** 생성된 보조 shim(record·class)의 필드 값 — 필드 이름은 스키마 이름 그대로다. */
    private static Object read(Object owner, String field) {
        for (Class<?> c = owner.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField(field);
                f.setAccessible(true);
                return f.get(owner);
            } catch (NoSuchFieldException e) {
                // 부모에서 찾는다
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }
        throw new IllegalStateException(owner.getClass().getName() + " 에 스키마 필드 " + field + " 이 없다");
    }

    /** Mojang camelCase → snake_case(서버 GenericCodec767.snake 와 같은 규칙). */
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
}
