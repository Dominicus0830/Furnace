package org.domi.furnace.compat.nms;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
import org.domi.furnace.api.packet.schema.FieldDef;
import org.domi.furnace.api.packet.schema.PacketDef;
import org.domi.furnace.api.packet.schema.PacketSchema;
import org.domi.furnace.api.packet.schema.StructDef;
import org.domi.furnace.api.packet.schema.TypeDecl;
import org.domi.furnace.api.packet.schema.TypeRef;

/**
 * {@link SchemaPacketBridge} 의 반대 방향(ProtocolLib 다리 2b-2): Furnace {@link GenericPacket} → 스키마로 생성한 clientbound 패킷 shim.
 * Furnace 서버가 스스로 보내는 패킷을 패킷 라이브러리 리스너에 보여 줄 때 쓴다. 플러그인용이 아니다.
 *
 * <p>값 하나하나가 {@code SchemaPacketBridge.toFurnace} 의 값 변환의 역이다: {@code toFurnace(fromFurnace(g)).equals(g)}(왕복).
 * shim 은 스키마 필드 순서의 모든 필드 생성자(생성기가 늘 만든다 — 바닐라에 없으면 private)로 만든다. 부모 클래스가 있는 이동 패킷 셋은 바닐라 생성자로.
 * 종류마다 계획을 처음 한 번 만들어 둔다. 거꾸로 바꿀 수 없는 종류·값이면 까닭을 밝힌 {@link UnsupportedNmsCall}.
 */
final class SchemaPacketReverse {

    private SchemaPacketReverse() {}

    private static final PacketSchema SCHEMA = PacketSchema.protocol767();

    /** 서버 범용 코덱이 play 연결로 다루는 단계(SchemaPacketBridge 와 같다). */
    private static final List<String> PLAY_STATES = List.of("game", "common", "cookie", "ping");

    /** 값 하나의 역변환(at = 오류 글에 쓸 자리). */
    private interface Inv {
        Object apply(Object v, String at);
    }

    /** GenericPacket 필드 → shim 생성자 인자(스키마 필드 순서). */
    private interface Unpack {
        Object[] apply(PacketStruct m, Back b, String at);
    }

    /** shim 만들기. */
    private interface Maker {
        Object make(Object[] values) throws ReflectiveOperationException;
    }

    /** 종류 하나의 역계획: names·invs = 스키마 필드마다(snake 이름·역변환, 범용으로 못 바꾸면 null), unpack·maker, 못 하면 refusal. */
    private record Back(PacketDef def, List<String> names, List<Inv> invs, Unpack unpack, Maker maker, String refusal) {}

    private static final Map<PacketType<?>, Back> BACKS = new ConcurrentHashMap<>();

    private static final class NoBridge extends RuntimeException {
        NoBridge(String why) { super(why, null, false, false); }
    }

    /** 이 Furnace 종류의 play 연결 clientbound 스키마 정의. */
    static Optional<PacketDef> def(PacketType<?> t) {
        if (t.direction() != PacketDirection.CLIENTBOUND || !t.name().startsWith("minecraft:")) return Optional.empty();
        String wire = t.name().substring("minecraft:".length());
        for (String st : PLAY_STATES) {
            Optional<PacketDef> d = SCHEMA.packet(st, PacketDirection.CLIENTBOUND, wire);
            if (d.isPresent()) return d;
        }
        return Optional.empty();
    }

    private static Back back(PacketType<?> t) { return BACKS.computeIfAbsent(t, SchemaPacketReverse::compile); }

    /** 이 종류를 거꾸로 바꿀 수 있는가(스키마·shim 모양만 본다 — 값에 따라 실패할 수는 있다). */
    static boolean supports(PacketType<?> t) { return back(t).refusal() == null; }

    /** 거꾸로 못 바꾸는 까닭(되면 null). */
    static String refusal(PacketType<?> t) { return back(t).refusal(); }

    private static Back refuse(PacketDef d, String why) { return new Back(d, null, null, null, null, why); }

    private static Back compile(PacketType<?> t) {
        PacketDef d = def(t).orElse(null);
        if (d == null) return refuse(null, "play 연결 clientbound 스키마에 없는 종류다");
        if (t.typed()) return refuse(d, "Furnace 타입 레코드 종류다(TypedPacketBridge 가 다룬다)");
        if (!SchemaPacketBridge.supports(d)) return refuse(d, "shim → Furnace 변환이 없는 종류다: " + SchemaPacketBridge.refusal(d));
        Class<?> cls;
        try {
            cls = Class.forName(d.binaryName(), false, SchemaPacketReverse.class.getClassLoader());
        } catch (ClassNotFoundException | LinkageError e) {
            return refuse(d, "shim 클래스가 없다");
        }
        try {
            List<FieldDef> all = new ArrayList<>();
            String pkg = d.javaPackage();
            if (d.superType() != null) {
                String base = SCHEMA.resolve(d.superType().name(), pkg);
                StructDef st = base == null ? null : SCHEMA.struct(base).orElse(null);
                if (st == null) return refuse(d, "부모 클래스 " + d.superType() + " 를 스키마에서 찾지 못했다");
                all.addAll(st.fields());
            }
            all.addAll(d.fields());
            List<String> names = new ArrayList<>();
            List<Inv> invs = new ArrayList<>();
            for (FieldDef f : all) {
                names.add(SchemaPacketBridge.snake(f.name()));
                Inv iv;
                try {
                    iv = field(f, pkg, 0);
                } catch (NoBridge e) {
                    if (!d.custom()) throw e;
                    iv = null;   // 손 코덱 종류: unpack 이 직접 바꾼다
                }
                invs.add(iv);
            }
            Unpack u = d.custom() ? customUnpack(d.wireName()) : SchemaPacketReverse::byName;
            if (u == null) return refuse(d, "손 코덱 종류의 역변환이 아직 없다");
            Maker mk = maker(d, cls, all);
            return new Back(d, List.copyOf(names), java.util.Collections.unmodifiableList(invs), u, mk, null);
        } catch (NoBridge e) {
            return refuse(d, e.getMessage());
        }
    }

    /** shim 만들기: 싱글턴은 INSTANCE, 이동 패킷 셋은 바닐라 생성자, 나머지는 스키마 필드 순서의 모든 필드 생성자. */
    private static Maker maker(PacketDef d, Class<?> cls, List<FieldDef> all) {
        if (d.kind() == PacketDef.Kind.SINGLETON) {
            Object inst = staticField(cls, "INSTANCE");
            return v -> inst;
        }
        switch (d.wireName()) {
            case "move_entity_pos": {
                Constructor<?> c = ctor(cls, int.class, short.class, short.class, short.class, boolean.class);
                return v -> c.newInstance(v[0], v[1], v[2], v[3], v[6]);
            }
            case "move_entity_pos_rot": {
                Constructor<?> c = ctor(cls, int.class, short.class, short.class, short.class, byte.class, byte.class, boolean.class);
                return v -> c.newInstance(v[0], v[1], v[2], v[3], v[4], v[5], v[6]);
            }
            case "move_entity_rot": {
                Constructor<?> c = ctor(cls, int.class, byte.class, byte.class, boolean.class);
                return v -> c.newInstance(v[0], v[4], v[5], v[6]);
            }
            default:
                break;
        }
        if (d.superType() != null) throw new NoBridge("부모 클래스가 있는 종류의 생성자를 모른다");
        Class<?>[] types = new Class<?>[all.size()];
        for (int i = 0; i < types.length; i++) types[i] = fieldType(cls, all.get(i).name());
        Constructor<?> c = ctor(cls, types);
        return c::newInstance;
    }

    private static Constructor<?> ctor(Class<?> cls, Class<?>... types) {
        try {
            Constructor<?> c = cls.getDeclaredConstructor(types);
            c.setAccessible(true);
            return c;
        } catch (NoSuchMethodException | RuntimeException e) {
            throw new NoBridge(cls.getSimpleName() + " shim 에 모든 필드 생성자가 없다");
        }
    }

    private static Class<?> fieldType(Class<?> cls, String name) {
        for (Class<?> k = cls; k != null && k != Object.class; k = k.getSuperclass()) {
            try {
                return k.getDeclaredField(name).getType();
            } catch (NoSuchFieldException e) {
                // 부모에서 찾는다
            }
        }
        throw new NoBridge(cls.getSimpleName() + " shim 에 스키마 필드 " + name + " 이 없다");
    }

    private static Object staticField(Class<?> cls, String name) {
        try {
            Field f = cls.getDeclaredField(name);
            f.setAccessible(true);
            return f.get(null);
        } catch (ReflectiveOperationException | RuntimeException e) {
            throw new NoBridge(cls.getSimpleName() + "." + name + " 이 없다");
        }
    }

    /**
     * GenericPacket → shim. 거꾸로 못 바꾸는 종류면 {@link UnsupportedNmsCall}, 값이 이 다리가 모르는 모양이면(직접 정의 Holder 등)
     * IllegalArgumentException 이나 UnsupportedNmsCall.
     */
    static net.minecraft.network.protocol.Packet<?> fromFurnace(GenericPacket g) {
        Back b = back(g.type());
        String call = "fromFurnace(" + g.type() + ")";
        if (b.refusal() != null) throw new UnsupportedNmsCall(call, b.refusal());
        if (!g.readable()) throw new UnsupportedNmsCall(call, "내용을 읽지 못한 패킷이다");
        Object[] v = b.unpack().apply(g.fields(), b, b.def().wireName());
        try {
            return (net.minecraft.network.protocol.Packet<?>) b.maker().make(v);
        } catch (java.lang.reflect.InvocationTargetException e) {
            Throwable c = e.getCause();
            if (c instanceof RuntimeException r) throw r;
            throw new IllegalStateException(c);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    // ------------------------------------------------------------------ 필드 → 생성자 인자

    /** 필드 나열 종류: 스키마 필드마다 이름으로 찾아 역변환. */
    private static Object[] byName(PacketStruct m, Back b, String at) {
        Object[] out = new Object[b.names().size()];
        for (int i = 0; i < out.length; i++) out[i] = inv(b, i, m.get(b.names().get(i)), at);
        return out;
    }

    private static Object inv(Back b, int i, Object v, String at) {
        Inv iv = b.invs().get(i);
        if (iv == null) throw new IllegalStateException(b.def().wireName() + "." + b.names().get(i) + " 은 범용 역변환이 없다");
        return iv.apply(v, at + "." + b.names().get(i));
    }

    private static int idx(Back b, String name) {
        int i = b.names().indexOf(name);
        if (i < 0) throw new IllegalStateException(b.def().wireName() + " 스키마에 " + name + " 이 없다");
        return i;
    }

    /** 손 코덱 종류의 역(SchemaPacketBridge.custom 의 반대). 대응이 없으면 null. */
    private static Unpack customUnpack(String wire) {
        return switch (wire) {
            // 범용으로 바꾼 필드를 이름만 다시 맞춘 것 — 이름으로 찾으면 된다
            case "player_abilities", "set_experience", "stop_sound", "player_position" -> SchemaPacketReverse::byName;
            case "move_entity_pos" -> move(true, false);
            case "move_entity_pos_rot" -> move(true, true);
            case "move_entity_rot" -> move(false, true);
            case "player_look_at" -> SchemaPacketReverse::lookAt;
            case "set_equipment" -> SchemaPacketReverse::equipment;
            case "section_blocks_update" -> SchemaPacketReverse::sectionBlocks;
            case "damage_event" -> SchemaPacketReverse::damageEvent;
            default -> null;   // set_player_team·set_objective·update_attributes·map_item_data 는 2b-4
        };
    }

    /** 부모 필드(entityId, xa, ya, za, yRot, xRot, onGround, hasRot, hasPos) 순서로 — 안 쓰는 칸은 0(바닐라 생성자와 같다). */
    private static Unpack move(boolean pos, boolean rot) {
        return (m, b, at) -> {
            Object[] v = new Object[9];
            v[0] = num(m.get("entity_id"), at + ".entity_id").intValue();
            v[1] = pos ? num(m.get("xa"), at + ".xa").shortValue() : (short) 0;
            v[2] = pos ? num(m.get("ya"), at + ".ya").shortValue() : (short) 0;
            v[3] = pos ? num(m.get("za"), at + ".za").shortValue() : (short) 0;
            v[4] = rot ? num(m.get("y_rot"), at + ".y_rot").byteValue() : (byte) 0;
            v[5] = rot ? num(m.get("x_rot"), at + ".x_rot").byteValue() : (byte) 0;
            v[6] = need(m.get("on_ground"), at + ".on_ground");
            v[7] = rot;
            v[8] = pos;
            return v;
        };
    }

    /** from_anchor x y z at_entity Optional&lt;{entity, to_anchor}&gt; → x, y, z, entity, fromAnchor, toAnchor, atEntity(바닐라 생성자처럼 없으면 0·FEET·false). */
    private static Object[] lookAt(PacketStruct m, Back b, String at) {
        Object[] v = new Object[7];
        v[0] = num(m.get("x"), at + ".x").doubleValue();
        v[1] = num(m.get("y"), at + ".y").doubleValue();
        v[2] = num(m.get("z"), at + ".z").doubleValue();
        v[4] = inv(b, idx(b, "from_anchor"), m.get("from_anchor"), at);
        Optional<?> e = (Optional<?>) need(m.get("at_entity"), at + ".at_entity");
        if (e.isPresent()) {
            PacketStruct s = (PacketStruct) e.get();
            v[3] = num(s.get("entity"), at + ".at_entity.entity").intValue();
            v[5] = inv(b, idx(b, "to_anchor"), s.get("to_anchor"), at + ".at_entity");
            v[6] = true;
        } else {
            v[3] = 0;
            v[5] = null;
            v[6] = false;
        }
        return v;
    }

    /** entity slots List&lt;{slot, item}&gt; → entity, List&lt;Pair&lt;EquipmentSlot, ItemStack&gt;&gt;(DataFixerUpper Pair 가 있을 때만). */
    private static Object[] equipment(PacketStruct m, Back b, String at) {
        java.lang.reflect.Method of = pairOf();
        List<Object> slots = new ArrayList<>();
        int i = 0;
        for (Object o : (Collection<?>) need(m.get("slots"), at + ".slots")) {
            String here = at + ".slots[" + i++ + "]";
            PacketStruct s = (PacketStruct) need(o, here);
            Object slot = net.minecraft.world.entity.EquipmentSlot.valueOf((String) need(s.get("slot"), here + ".slot"));
            Object item = itemStack(s.get("item"), here + ".item");
            try {
                slots.add(of.invoke(null, slot, item));
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(here + ": " + e, e);
            }
        }
        return new Object[] {num(m.get("entity"), at + ".entity").intValue(), slots};
    }

    /** DataFixerUpper {@code Pair.of} — 서버 라이브러리로 올라와 있을 때만(장비 패킷 shim 의 원소 타입이다). 시험은 바꿔 끼운다. */
    static volatile java.lang.reflect.Method pairOfOverride;

    private static java.lang.reflect.Method pairOf() {
        java.lang.reflect.Method o = pairOfOverride;
        if (o != null) return o;
        try {
            Class<?> c = Class.forName("com.mojang.datafixers.util.Pair", false, SchemaPacketReverse.class.getClassLoader());
            return c.getMethod("of", Object.class, Object.class);
        } catch (ClassNotFoundException | NoSuchMethodException | LinkageError e) {
            throw new UnsupportedNmsCall("fromFurnace(set_equipment)", "DataFixerUpper Pair 가 서버 라이브러리로 올라와 있지 않다(장비 패킷 shim 의 원소 타입)");
        }
    }

    /** section_pos positions states → sectionPos, short[], BlockState[]. */
    private static Object[] sectionBlocks(PacketStruct m, Back b, String at) {
        List<?> ps = (List<?>) need(m.get("positions"), at + ".positions");
        List<?> ss = (List<?>) need(m.get("states"), at + ".states");
        short[] pos = new short[ps.size()];
        net.minecraft.world.level.block.state.BlockState[] st = new net.minecraft.world.level.block.state.BlockState[ss.size()];
        for (int i = 0; i < pos.length; i++) pos[i] = num(ps.get(i), at + ".positions[" + i + "]").shortValue();
        for (int i = 0; i < st.length; i++) st[i] = blockState(ss.get(i), at + ".states[" + i + "]");
        return new Object[] {inv(b, idx(b, "section_pos"), m.get("section_pos"), at), pos, st};
    }

    /** entity_id source_type(이름) source_cause_id source_direct_id source_position → 같은 순서, 피해 종류는 등록된 Reference. */
    private static Object[] damageEvent(PacketStruct m, Back b, String at) {
        return new Object[] {num(m.get("entity_id"), at + ".entity_id").intValue(),
                reference(net.minecraft.core.registries.Registries.DAMAGE_TYPE, (String) need(m.get("source_type"), at + ".source_type"), null),
                num(m.get("source_cause_id"), at + ".source_cause_id").intValue(), num(m.get("source_direct_id"), at + ".source_direct_id").intValue(),
                inv(b, idx(b, "source_position"), m.get("source_position"), at)};
    }

    // ------------------------------------------------------------------ 값 하나의 역변환

    private static Inv field(FieldDef f, String pkg, int depth) {
        Inv c = inv(f.type(), f.wire(), pkg, depth);
        if (!f.nullable()) return c;
        return (v, at) -> ((Optional<?>) need(v, at)).map(x -> c.apply(x, at)).orElse(null);
    }

    /** ResourceKey&lt;T&gt; 의 레지스트리(서버 코덱도 이름만 쓴다 — 패킷에 나오는 것만). */
    private static final Map<String, net.minecraft.resources.ResourceKey<?>> RESOURCE_KEY_REGISTRIES = Map.of(
            "Level", net.minecraft.core.registries.Registries.DIMENSION);

    private static Inv inv(TypeRef t, String wire, String pkg, int depth) {
        if (depth > 8) throw new NoBridge("타입이 너무 깊다: " + t);
        if (t.bound() != TypeRef.Bound.NONE) throw new NoBridge("와일드카드 타입 " + t);
        if (t.arrayDimensions() > 0) {
            if (t.arrayDimensions() == 1 && wire == null && Set.of("byte", "long", "int").contains(t.name())) return SchemaPacketReverse::need;
            throw new NoBridge("배열 " + t);
        }
        switch (t.name()) {
            case "int":
                return (v, at) -> num(v, at).intValue();
            case "short":
                return (v, at) -> num(v, at).shortValue();
            case "byte":
                return (v, at) -> num(v, at).byteValue();
            case "long":
                return (v, at) -> num(v, at).longValue();
            case "float":
                return (v, at) -> num(v, at).floatValue();
            case "double":
                return (v, at) -> num(v, at).doubleValue();
            case "boolean", "String", "UUID":
                return SchemaPacketReverse::need;
            case "List", "Set", "Collection": {
                if (wire != null) throw new NoBridge("목록 명시 와이어 " + wire);
                Inv e = inv(t.arguments().get(0), null, pkg, depth + 1);
                boolean set = t.name().equals("Set");
                return (v, at) -> {
                    Collection<Object> out = set ? new LinkedHashSet<>() : new ArrayList<>();
                    int i = 0;
                    for (Object x : (Collection<?>) need(v, at)) out.add(e.apply(x, at + "[" + i++ + "]"));
                    return out;
                };
            }
            case "Optional": {
                if (wire != null) throw new NoBridge("Optional 명시 와이어 " + wire);
                Inv e = inv(t.arguments().get(0), null, pkg, depth + 1);
                return (v, at) -> ((Optional<?>) need(v, at)).map(x -> e.apply(x, at));
            }
            case "Holder": {
                String arg = t.arguments().get(0).name();
                return (v, at) -> holder(need(v, at), arg, at);
            }
            case "ResourceKey": {
                net.minecraft.resources.ResourceKey<?> reg = t.arguments().isEmpty() ? null : RESOURCE_KEY_REGISTRIES.get(t.arguments().get(0).name());
                if (reg == null) throw new NoBridge("ResourceKey 의 레지스트리를 모른다: " + t);
                return (v, at) -> resourceKey(reg, (String) need(v, at));
            }
            case "Instant":
                return (v, at) -> java.time.Instant.ofEpochMilli(num(v, at).longValue());
            case "Map": {
                if (wire != null || t.arguments().size() != 2 || !t.arguments().get(0).name().equals("String") || !t.arguments().get(1).name().equals("String")) {
                    throw new NoBridge("Map " + t);
                }
                return (v, at) -> {
                    LinkedHashMap<String, String> m = new LinkedHashMap<>();
                    for (Map.Entry<String, Object> e : ((PacketStruct) need(v, at)).asMap().entrySet()) m.put(e.getKey(), (String) e.getValue());
                    return m;
                };
            }
            default:
                break;
        }
        String bin;
        try {
            bin = SCHEMA.resolve(t.name(), pkg);
        } catch (IllegalArgumentException e) {
            throw new NoBridge(e.getMessage());
        }
        if (bin == null) throw new NoBridge("타입 " + t);
        TypeDecl decl = SCHEMA.type(bin).orElse(null);
        StructDef struct = SCHEMA.struct(bin).orElse(null);
        if ((decl != null && decl.kind() == TypeDecl.Kind.ENUM) || (struct != null && struct.kind() == StructDef.Kind.ENUM)) {
            Class<?> ec = shimClass(bin);
            if ("i8".equals(wire)) {   // 없으면 null(GameType.byNullableId 의 -1)
                return (v, at) -> v instanceof Optional<?> o ? o.map(n -> constant(ec, (String) n, at)).orElse(null) : v == null ? null : constant(ec, (String) v, at);
            }
            return (v, at) -> constant(ec, (String) need(v, at), at);
        }
        if (wire != null) {   // 명시 와이어 자리의 보조 클래스(game_event Type): 바닐라 상수 중 같은 번호, 없으면 새로
            if (struct == null || struct.fields().size() != 1 || !struct.fields().get(0).type().name().equals("int")) {
                throw new NoBridge("명시 와이어 " + wire + " 자리의 " + bin);
            }
            Class<?> sc = shimClass(bin);
            Field idf = declaredField(sc, struct.fields().get(0).name());
            Constructor<?> c = ctor(sc, int.class);
            Map<Integer, Object> constants = new ConcurrentHashMap<>();
            for (Field f : sc.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers()) && f.getType() == sc) {
                    try {
                        f.setAccessible(true);
                        Object k = f.get(null);
                        if (k != null) constants.putIfAbsent((Integer) idf.get(k), k);
                    } catch (ReflectiveOperationException | RuntimeException e) {
                        // 읽지 못한 상수는 건너뛴다
                    }
                }
            }
            return (v, at) -> {
                int id = num(v, at).intValue();
                Object k = constants.get(id);
                if (k != null) return k;
                try {
                    return c.newInstance(id);
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException(e);
                }
            };
        }
        if (decl != null) {
            String w = String.valueOf(decl.wire());
            return switch (w) {
                case "component" -> (v, at) -> net.minecraft.network.chat.Component.fromFurnace((org.domi.furnace.api.text.Text) need(v, at));
                case "identifier" -> (v, at) -> net.minecraft.resources.ResourceLocation.parse((String) need(v, at));
                case "block_pos" -> (v, at) -> net.minecraft.core.BlockPos.fromFurnace((org.domi.furnace.api.world.BlockPos) need(v, at));
                case "item_stack" -> SchemaPacketReverse::itemStack;
                case "block_state" -> SchemaPacketReverse::blockState;
                case "nbt" -> (v, at) -> net.minecraft.nbt.CompoundTag.fromFurnace((org.domi.furnace.api.nbt.NbtCompound) need(v, at));
                case "i64" -> {
                    if (bin.equals("net.minecraft.core.SectionPos")) yield (v, at) -> net.minecraft.core.SectionPos.of(num(v, at).longValue());
                    if (bin.equals("net.minecraft.world.level.ChunkPos")) yield (v, at) -> new net.minecraft.world.level.ChunkPos(num(v, at).longValue());
                    throw new NoBridge("i64 자리의 " + bin);
                }
                case "vec3" -> (v, at) -> {
                    PacketStruct s = (PacketStruct) need(v, at);
                    return new net.minecraft.world.phys.Vec3(num(s.get("x"), at + ".x").doubleValue(), num(s.get("y"), at + ".y").doubleValue(),
                            num(s.get("z"), at + ".z").doubleValue());
                };
                case "registry_id" -> registryValue(bin);
                case "number_format" -> SchemaPacketReverse::numberFormat;
                default -> throw new NoBridge(bin.substring(bin.lastIndexOf('.') + 1) + " 값 shim 이 아직 빈 껍데기다");
            };
        }
        if (struct == null || !(struct.kind() == StructDef.Kind.RECORD || struct.kind() == StructDef.Kind.CLASS)) throw new NoBridge("타입 " + bin);
        Class<?> sc = shimClass(bin);
        List<String> names = new ArrayList<>();
        List<Inv> invs = new ArrayList<>();
        Class<?>[] types = new Class<?>[struct.fields().size()];
        for (int i = 0; i < types.length; i++) {
            FieldDef f = struct.fields().get(i);
            names.add(SchemaPacketBridge.snake(f.name()));
            invs.add(field(f, struct.javaPackage(), depth + 1));
            types[i] = fieldType(sc, f.name());
        }
        Constructor<?> c = ctor(sc, types);
        return (v, at) -> {
            PacketStruct s = (PacketStruct) need(v, at);
            Object[] args = new Object[names.size()];
            for (int i = 0; i < args.length; i++) args[i] = invs.get(i).apply(s.get(names.get(i)), at + "." + names.get(i));
            try {
                return c.newInstance(args);
            } catch (java.lang.reflect.InvocationTargetException e) {
                if (e.getCause() instanceof RuntimeException r) throw r;
                throw new IllegalStateException(e.getCause());
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        };
    }

    private static Class<?> shimClass(String bin) {
        try {
            return Class.forName(bin, false, SchemaPacketReverse.class.getClassLoader());
        } catch (ClassNotFoundException | LinkageError e) {
            throw new NoBridge(bin.substring(bin.lastIndexOf('.') + 1) + " shim 클래스가 없다");
        }
    }

    private static Field declaredField(Class<?> c, String name) {
        try {
            Field f = c.getDeclaredField(name);
            f.setAccessible(true);
            return f;
        } catch (NoSuchFieldException e) {
            throw new NoBridge(c.getSimpleName() + " shim 에 " + name + " 이 없다");
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object constant(Class<?> enumClass, String name, String at) {
        try {
            return Enum.valueOf((Class) enumClass, name);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(at + ": " + enumClass.getSimpleName() + " 에 " + name + " 상수가 없다");
        }
    }

    private static Inv registryValue(String bin) {
        return switch (bin) {
            case "net.minecraft.world.item.Item" -> (v, at) -> net.minecraft.world.item.Item.byKey((String) need(v, at));
            case "net.minecraft.world.level.block.Block" ->
                    (v, at) -> net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(net.minecraft.resources.ResourceLocation.parse((String) need(v, at)));
            case "net.minecraft.world.entity.EntityType" -> (v, at) -> net.minecraft.world.entity.EntityType.byString((String) need(v, at))
                    .orElseThrow(() -> new IllegalArgumentException(at + ": 모르는 엔티티 종류 " + v));
            default -> throw new NoBridge("레지스트리 번호 " + bin);
        };
    }

    static Object itemStack(Object v, String at) {
        var s = (org.domi.furnace.api.item.ItemStack) need(v, at);
        if (s.isEmpty()) return net.minecraft.world.item.ItemStack.EMPTY;
        return net.minecraft.world.item.ItemStack.fromFurnace(s, s.amount());
    }

    static net.minecraft.world.level.block.state.BlockState blockState(Object v, String at) {
        var data = (org.domi.furnace.api.world.BlockData) need(v, at);
        return net.minecraft.world.level.block.Block.stateById(Furnace.server().blockTypes().stateId(data));
    }

    /** 서버 숫자 형식 값({type, …}) → 바닐라 숫자 형식 셋. styled 는 NBT 스타일을 다시 풀 길이 아직 없어 거절(2b-4). */
    private static Object numberFormat(Object v, String at) {
        PacketStruct s = (PacketStruct) need(v, at);
        String type = (String) need(s.get("type"), at + ".type");
        return switch (type) {
            case "minecraft:blank" -> net.minecraft.network.chat.numbers.BlankFormat.INSTANCE;
            case "minecraft:fixed" -> new net.minecraft.network.chat.numbers.FixedFormat(
                    net.minecraft.network.chat.Component.fromFurnace((org.domi.furnace.api.text.Text) need(s.get("value"), at + ".value")));
            case "minecraft:styled" -> throw new UnsupportedNmsCall("fromFurnace(number_format)", "styled 숫자 형식의 NBT 스타일을 shim Style 로 되돌리는 길이 아직 없다");
            default -> throw new IllegalArgumentException(at + ": 모르는 숫자 형식 " + type);
        };
    }

    /**
     * 서버 코덱 Holder 값 → shim Holder(SchemaPacketBridge.holder 의 반대). 이름은 Reference(소리·효과는 레지스트리의 것, 나머지는 키만 든 Reference),
     * 소리 직접 정의 {sound_id, range} 는 Holder.direct(SoundEvent).
     */
    private static Object holder(Object v, String registry, String at) {
        if (v instanceof PacketStruct s && registry.equals("SoundEvent")) {
            var loc = net.minecraft.resources.ResourceLocation.parse((String) need(s.get("sound_id"), at + ".sound_id"));
            Optional<?> range = (Optional<?>) need(s.get("range"), at + ".range");
            var ev = range.isPresent() ? net.minecraft.sounds.SoundEvent.createFixedRangeEvent(loc, num(range.get(), at + ".range").floatValue())
                    : net.minecraft.sounds.SoundEvent.createVariableRangeEvent(loc);
            return net.minecraft.core.Holder.direct(ev);
        }
        if (!(v instanceof String name)) throw new IllegalArgumentException(at + ": " + registry + " 값은 이름이어야 한다: " + v);
        var loc = net.minecraft.resources.ResourceLocation.parse(name);
        return switch (registry) {
            case "SoundEvent" -> net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.getHolder(loc)
                    .orElseThrow(() -> new IllegalArgumentException(at + ": 소리 레지스트리에 없는 이름 " + name));
            case "MobEffect" -> net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.getHolder(loc)
                    .orElseThrow(() -> new IllegalArgumentException(at + ": 효과 레지스트리에 없는 이름 " + name));
            case "Attribute" -> reference(net.minecraft.core.registries.Registries.ATTRIBUTE, name, null);
            case "DimensionType" -> reference(net.minecraft.core.registries.Registries.DIMENSION_TYPE, name, null);
            default -> throw new IllegalArgumentException(at + ": Holder<" + registry + "> 는 아직 다루지 않는다");
        };
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object reference(net.minecraft.resources.ResourceKey registry, String name, Object value) {
        return new net.minecraft.core.Holder.Reference(net.minecraft.resources.ResourceKey.create(registry, net.minecraft.resources.ResourceLocation.parse(name)), value);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object resourceKey(net.minecraft.resources.ResourceKey registry, String name) {
        return net.minecraft.resources.ResourceKey.create(registry, net.minecraft.resources.ResourceLocation.parse(name));
    }

    static Number num(Object v, String at) {
        if (v instanceof Number n) return n;
        throw new IllegalArgumentException(at + " 은 수여야 한다: " + v);
    }

    static Object need(Object v, String at) {
        if (v == null) throw new IllegalArgumentException(at + " 이 null 이다");
        return v;
    }
}
