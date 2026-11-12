// E4b 손 코덱: 스키마에서 custom(와이어가 필드 나열로 떨어지지 않는 것 — 비트 묶음·조건부·순서 다름)인 종류를 바닐라 1.21.1 와이어 그대로 읽고 쓴다.
// 필드 이름은 Mojang 필드 이름의 snake_case, 필드 순서는 와이어 순서다. 비트 묶음은 Boolean 필드 여럿, 조건부 묶음은 Optional<PacketStruct>.
// 값 모양 표는 docs/api/packets.md "손 코덱 종류 (E4b)", 바닐라 근거는 docs/feature-parity/context-notes/2026-11-12-easy-nms-e4b.md.
// 여기 없는 custom 종류는 아직 readable=false(GenericCodec767 이 까닭을 남긴다).
package org.domi.furnace.server.plugin.packet;

import static org.domi.furnace.server.plugin.packet.GenericCodec767.BLOCK_POS;
import static org.domi.furnace.server.plugin.packet.GenericCodec767.BOOL;
import static org.domi.furnace.server.plugin.packet.GenericCodec767.F32;
import static org.domi.furnace.server.plugin.packet.GenericCodec767.F64;
import static org.domi.furnace.server.plugin.packet.GenericCodec767.I64;
import static org.domi.furnace.server.plugin.packet.GenericCodec767.IDENTIFIER;
import static org.domi.furnace.server.plugin.packet.GenericCodec767.STRING;
import static org.domi.furnace.server.plugin.packet.GenericCodec767.TEXT;
import static org.domi.furnace.server.plugin.packet.GenericCodec767.VARLONG;
import static org.domi.furnace.server.plugin.packet.GenericCodec767.VEC3;

import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import org.domi.furnace.api.item.ItemStack;
import org.domi.furnace.api.packet.PacketDirection;
import org.domi.furnace.api.packet.PacketStruct;
import org.domi.furnace.api.packet.schema.PacketDef;
import org.domi.furnace.api.world.BlockData;
import org.domi.furnace.api.world.BlockPos;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.plugin.packet.GenericCodec767.Compiled;
import org.domi.furnace.server.plugin.packet.GenericCodec767.Hand;
import org.domi.furnace.server.plugin.packet.GenericCodec767.Node;

final class GenericHand767 {

    private GenericHand767() {}

    private static final Node VARINT = GenericCodec767.integer("varint", Integer.class);
    private static final Node I8 = GenericCodec767.integer("i8", Byte.class);
    private static final Node I16 = GenericCodec767.integer("i16", Short.class);
    private static final Node I8_INT = GenericCodec767.integer("i8", Integer.class);
    private static final Node I16_INT = GenericCodec767.integer("i16", Integer.class);

    /** 이 종류의 손 코덱(없으면 null). */
    static Compiled compile(GenericCodec767 c, PacketDef d) {
        boolean cb = d.direction() == PacketDirection.CLIENTBOUND;
        return switch ((cb ? "c:" : "s:") + d.wireName()) {
            // ---------------------------------------------------------- clientbound
            case "c:player_abilities" -> new Seq()   // 플래그 1 무적·2 나는 중·4 날 수 있음·8 즉시 건설, 그다음 f32 둘
                    .flags("invulnerable", "is_flying", "can_fly", "instabuild")
                    .f("flying_speed", F32).f("walking_speed", F32).build();
            case "c:set_experience" -> new Seq()     // 와이어 순서: 진행도, 레벨, 총 경험치
                    .f("experience_progress", F32).f("experience_level", VARINT).f("total_experience", VARINT).build();
            case "c:stop_sound" -> stopSound(c, d);
            case "c:player_look_at" -> new Seq()     // atEntity 가 참일 때만 대상 번호·대상 기준점
                    .f("from_anchor", c.fieldNode(d, "fromAnchor")).f("x", F64).f("y", F64).f("z", F64)
                    .f("at_entity", GenericCodec767.optional(GenericCodec767.structNode(
                            GenericCodec767.shape(List.of("entity", "to_anchor"), List.of(VARINT, c.fieldNode(d, "toAnchor")), null)), false))
                    .build();
            case "c:player_position" -> new Seq()
                    .f("x", F64).f("y", F64).f("z", F64).f("y_rot", F32).f("x_rot", F32)
                    .f("relative_arguments", flagSet(c.constants(d, "relativeArguments", true)))
                    .f("id", VARINT).build();
            case "c:move_entity_pos" -> new Seq()
                    .f("entity_id", VARINT).f("xa", I16).f("ya", I16).f("za", I16).f("on_ground", BOOL).build();
            case "c:move_entity_pos_rot" -> new Seq()
                    .f("entity_id", VARINT).f("xa", I16).f("ya", I16).f("za", I16).f("y_rot", I8).f("x_rot", I8).f("on_ground", BOOL).build();
            case "c:move_entity_rot" -> new Seq()
                    .f("entity_id", VARINT).f("y_rot", I8).f("x_rot", I8).f("on_ground", BOOL).build();
            case "c:set_objective" -> setObjective(c, d);
            case "c:set_equipment" -> setEquipment(c, d);
            case "c:section_blocks_update" -> sectionBlocks(c);
            case "c:damage_event" -> new Seq()       // 원인·직접 원인 번호는 id + 1(0 = 없음 = 필드 값 -1)
                    .f("entity_id", VARINT).f("source_type", GenericCodec767.registryId("minecraft:damage_type"))
                    .f("source_cause_id", PLUS_ONE).f("source_direct_id", PLUS_ONE)
                    .f("source_position", GenericCodec767.optional(VEC3, false)).build();
            // ---------------------------------------------------------- serverbound
            case "s:player_abilities" -> new Seq().flags(null, "is_flying").build();   // 비트 2 만 쓴다
            case "s:player_input" -> new Seq()
                    .f("xxa", F32).f("zza", F32).flags("is_jumping", "is_shift_key_down").build();
            case "s:seen_advancements" -> seenAdvancements(c, d);
            case "s:set_command_block" -> new Seq()
                    .f("pos", BLOCK_POS).f("command", STRING).f("mode", c.fieldNode(d, "mode"))
                    .flags("track_output", "conditional", "automatic").build();
            case "s:set_structure_block" -> new Seq()
                    .f("pos", BLOCK_POS).f("update_type", c.fieldNode(d, "updateType")).f("mode", c.fieldNode(d, "mode"))
                    .f("name", STRING).f("offset", BYTE_POS).f("size", BYTE_POS)
                    .f("mirror", c.fieldNode(d, "mirror")).f("rotation", c.fieldNode(d, "rotation")).f("data", STRING_128)
                    .f("integrity", F32).f("seed", VARLONG)
                    .flags("ignore_entities", "show_air", "show_bounding_box").build();
            case "s:container_click" -> new Seq()    // 바뀐 칸 목록이 들고 있는 아이템보다 먼저 나간다
                    .f("container_id", I8_INT).f("state_id", VARINT).f("slot_num", I16_INT).f("button_num", I8_INT)
                    .f("click_type", c.fieldNode(d, "clickType"))
                    .f("changed_slots", changedSlots(c)).f("carried_item", c.itemStack()).build();
            default -> null;
        };
    }

    // ------------------------------------------------------------------ 차례대로 읽고 쓰기

    /** 와이어를 차례로: 값 하나(노드)나 비트 묶음 바이트 하나. 필드 순서 = 와이어 순서. */
    private static final class Seq {
        private final List<String> names = new ArrayList<>();
        private final List<Node> kinds = new ArrayList<>();
        private final List<BiConsumer<ByteBuf, Map<String, Object>>> reads = new ArrayList<>();
        private final List<BiConsumer<ByteBuf, Map<String, Object>>> writes = new ArrayList<>();

        Seq f(String name, Node n) {
            names.add(name);
            kinds.add(n);
            reads.add((b, m) -> m.put(name, n.read(b)));
            writes.add((o, m) -> {
                try {
                    n.write(o, m.get(name));
                } catch (IllegalArgumentException | ClassCastException e) {
                    throw new IllegalArgumentException(name + ": " + e.getMessage(), e);
                }
            });
            return this;
        }

        /** 바이트 하나에 담긴 참/거짓 — i 번째 이름이 비트 1 &lt;&lt; i (null 은 쓰지 않는 비트). 바닐라처럼 모르는 비트는 읽을 때 버린다. */
        Seq flags(String... bits) {
            for (String n : bits) {
                if (n == null) continue;
                names.add(n);
                kinds.add(BOOL);
            }
            reads.add((b, m) -> {
                int v = b.readUnsignedByte();
                for (int i = 0; i < bits.length; i++) if (bits[i] != null) m.put(bits[i], (v & (1 << i)) != 0);
            });
            writes.add((o, m) -> {
                int v = 0;
                for (int i = 0; i < bits.length; i++) if (bits[i] != null && (Boolean) m.get(bits[i])) v |= 1 << i;
                o.writeByte(v);
            });
            return this;
        }

        Compiled build() {
            return GenericCodec767.shape(names, kinds, hand((b, m) -> reads.forEach(r -> r.accept(b, m)), (o, m) -> writes.forEach(w -> w.accept(o, m))));
        }
    }

    private static Hand hand(BiConsumer<ByteBuf, Map<String, Object>> r, BiConsumer<ByteBuf, Map<String, Object>> w) {
        return new Hand() {
            @Override
            public void read(ByteBuf b, Map<String, Object> out) { r.accept(b, out); }

            @Override
            public void write(ByteBuf o, Map<String, Object> in) { w.accept(o, in); }
        };
    }

    // ------------------------------------------------------------------ 값 노드

    /** 바닐라가 readUtf(128) 로 읽는 문자열(구조물 블록 data — 검토 R1). 129자 넘으면 바닐라 서버가 디코드에 실패한다. */
    private static final Node STRING_128 = node(String.class, b -> Types.readString(b, 128), (o, v) -> {
        String str = (String) v;
        if (str.length() > 128) throw new IllegalArgumentException("128자를 넘는다(" + str.length() + ")");
        Types.writeString(o, str);
    });

    /** id + 1 로 나가는 엔티티 번호(필드 값 -1 = 없음 = 와이어 0). */
    private static final Node PLUS_ONE = node(Integer.class, b -> Types.readVarInt(b) - 1, (o, v) -> {
        int i = (Integer) v;
        if (i < -1) throw new IllegalArgumentException("엔티티 번호는 -1(없음) 이상: " + i);
        Types.writeVarInt(o, i + 1);
    });

    /** 좌표마다 i8 한 바이트인 BlockPos(구조물 블록 offset·size). 바닐라 서버는 받은 값을 -48~48(크기 0~48)로 자른다. */
    private static final Node BYTE_POS = node(BlockPos.class, b -> new BlockPos(b.readByte(), b.readByte(), b.readByte()), (o, v) -> {
        BlockPos p = (BlockPos) v;
        for (int x : new int[]{p.x(), p.y(), p.z()}) {
            if (x < Byte.MIN_VALUE || x > Byte.MAX_VALUE) throw new IllegalArgumentException("좌표는 i8 범위(-128~127): " + p);
        }
        o.writeByte(p.x());
        o.writeByte(p.y());
        o.writeByte(p.z());
    });

    /** enum 집합을 비트로(i 번째 상수 = 비트 1 &lt;&lt; i, u8). 값은 상수 이름 목록(읽을 때 비트 순서). */
    private static Node flagSet(List<String> constants) {
        return node(List.class, b -> {
            int v = b.readUnsignedByte();
            List<String> l = new ArrayList<>();
            for (int i = 0; i < constants.size(); i++) if ((v & (1 << i)) != 0) l.add(constants.get(i));
            return Collections.unmodifiableList(l);
        }, (o, v) -> {
            int bits = 0;
            for (Object x : (List<?>) v) {
                int i = x instanceof String s ? constants.indexOf(s) : -1;
                if (i < 0) throw new IllegalArgumentException("상수가 아니다: " + x + " " + constants);
                if ((bits & (1 << i)) != 0) throw new IllegalArgumentException("같은 상수가 두 번 있다: " + x);
                bits |= 1 << i;
            }
            o.writeByte(bits);
        });
    }

    private static Node node(Class<?> kind, java.util.function.Function<ByteBuf, Object> r, BiConsumer<ByteBuf, Object> w) {
        return new Node() {
            @Override
            public Object read(ByteBuf b) { return r.apply(b); }

            @Override
            public void write(ByteBuf o, Object v) { w.accept(o, v); }

            @Override
            public Class<?> kind() { return kind; }
        };
    }

    // ------------------------------------------------------------------ 종류별

    /** 플래그 바이트(1 = 소리 범주, 2 = 이름) 다음에 범주(varint 서수), 이름 순서. */
    private static Compiled stopSound(GenericCodec767 c, PacketDef d) {
        Node source = c.fieldNode(d, "source"), name = c.fieldNode(d, "name");
        Node optSource = GenericCodec767.optional(source, false), optName = GenericCodec767.optional(IDENTIFIER, false);
        if (name != IDENTIFIER) throw new GenericCodec767.Unsupported("stop_sound name 이 identifier 가 아니다");
        return GenericCodec767.shape(List.of("source", "name"), List.of(optSource, optName), hand((b, m) -> {
            int flags = b.readUnsignedByte();
            m.put("source", (flags & 1) != 0 ? Optional.of(source.read(b)) : Optional.empty());
            m.put("name", (flags & 2) != 0 ? Optional.of(name.read(b)) : Optional.empty());
        }, (o, m) -> {
            Optional<?> s = (Optional<?>) m.get("source"), n = (Optional<?>) m.get("name");
            o.writeByte((s.isPresent() ? 1 : 0) | (n.isPresent() ? 2 : 0));
            if (s.isPresent()) source.write(o, s.get());
            if (n.isPresent()) name.write(o, n.get());
        }));
    }

    /** 이름, 방식(i8: 0 추가·1 제거·2 바꿈), 방식이 0·2 일 때만 parameters{display_name, render_type, number_format}. */
    private static Compiled setObjective(GenericCodec767 c, PacketDef d) {
        Compiled params = GenericCodec767.shape(List.of("display_name", "render_type", "number_format"),
                List.of(TEXT, c.fieldNode(d, "renderType"), GenericCodec767.optional(GenericCodec767.NUMBER_FORMAT, false)), null);
        Node paramsNode = GenericCodec767.optional(GenericCodec767.structNode(params), false);
        return GenericCodec767.shape(List.of("objective_name", "method", "parameters"), List.of(STRING, I8_INT, paramsNode), hand((b, m) -> {
            m.put("objective_name", STRING.read(b));
            int method = b.readByte();
            m.put("method", method);
            m.put("parameters", method == 0 || method == 2 ? Optional.of(GenericCodec767.readStruct(params, b)) : Optional.empty());
        }, (o, m) -> {
            int method = (Integer) m.get("method");
            Optional<?> p = (Optional<?>) m.get("parameters");
            boolean wants = method == 0 || method == 2;
            if (p.isPresent() != wants) {
                throw new IllegalArgumentException("parameters 는 method 가 0(추가)·2(바꿈)일 때만 있어야 한다: method=" + method + ", parameters=" + p);
            }
            STRING.write(o, m.get("objective_name"));
            I8_INT.write(o, method);
            if (p.isPresent()) GenericCodec767.writeStruct(params, (PacketStruct) p.get(), o, "parameters");
        }));
    }

    /** 엔티티 번호, 그다음 칸마다 (칸 서수 | 뒤에 더 있으면 0x80) 바이트 + 아이템. 바닐라 읽기는 적어도 하나를 읽으므로 빈 목록은 못 쓴다. */
    private static Compiled setEquipment(GenericCodec767 c, PacketDef d) {
        List<String> slots = c.enumConstants("net.minecraft.world.entity.EquipmentSlot");
        Node slot = GenericCodec767.enumNode("EquipmentSlot", slots, "varint");
        Node item = c.itemStack();
        Compiled entry = GenericCodec767.shape(List.of("slot", "item"), List.of(slot, item), null);
        Node list = node(List.class, null, null);
        return GenericCodec767.shape(List.of("entity", "slots"), List.of(VARINT, list), hand((b, m) -> {
            m.put("entity", VARINT.read(b));
            List<Object> l = new ArrayList<>();
            int head;
            do {
                head = b.readByte();
                LinkedHashMap<String, Object> e = new LinkedHashMap<>();
                e.put("slot", constantAt(slots, head & 0x7F));
                e.put("item", item.read(b));
                l.add(PacketStruct.of(e));
            } while ((head & 0x80) != 0);
            m.put("slots", Collections.unmodifiableList(l));
        }, (o, m) -> {
            List<?> l = (List<?>) m.get("slots");
            if (l.isEmpty()) throw new IllegalArgumentException("slots 가 비었다(바닐라는 적어도 한 칸을 읽는다)");
            VARINT.write(o, m.get("entity"));
            for (int i = 0; i < l.size(); i++) {
                if (!(l.get(i) instanceof PacketStruct s)) throw new IllegalArgumentException("slots[" + i + "] 은 {slot, item} 이어야 한다: " + l.get(i));
                Map<String, Object> e = GenericCodec767.checked(entry, s, "slots[" + i + "]");
                int ord = slots.indexOf(e.get("slot"));
                if (ord < 0) throw new IllegalArgumentException("slots[" + i + "].slot 은 EquipmentSlot 상수여야 한다: " + e.get("slot") + " " + slots);
                o.writeByte(ord | (i < l.size() - 1 ? 0x80 : 0));
                item.write(o, (ItemStack) e.get("item"));
            }
        }));
    }

    /** 구역 위치(i64), 바뀐 칸 수, 칸마다 varlong(상태 번호 &lt;&lt; 12 | 구역 안 위치 x&lt;&lt;8|z&lt;&lt;4|y). */
    private static Compiled sectionBlocks(GenericCodec767 c) {
        Node state = c.blockState();
        return GenericCodec767.shape(List.of("section_pos", "positions", "states"), List.of(I64, node(List.class, null, null), node(List.class, null, null)),
                hand((b, m) -> {
                    m.put("section_pos", b.readLong());
                    int n = Types.readVarInt(b);
                    if (n < 0 || n > b.readableBytes()) throw new IllegalArgumentException("칸 수 오류: " + n);
                    List<Object> pos = new ArrayList<>(n), states = new ArrayList<>(n);
                    for (int i = 0; i < n; i++) {
                        long v = Types.readVarLong(b);
                        pos.add((short) (v & 0xFFF));
                        states.add(c.blockData((int) (v >>> 12)));
                    }
                    m.put("positions", Collections.unmodifiableList(pos));
                    m.put("states", Collections.unmodifiableList(states));
                }, (o, m) -> {
                    List<?> pos = (List<?>) m.get("positions"), states = (List<?>) m.get("states");
                    if (pos.size() != states.size()) throw new IllegalArgumentException("positions(" + pos.size() + ") 와 states(" + states.size() + ") 길이가 다르다");
                    o.writeLong((Long) m.get("section_pos"));
                    Types.writeVarInt(o, pos.size());
                    for (int i = 0; i < pos.size(); i++) {
                        if (!(pos.get(i) instanceof Short p) || p < 0 || p > 0xFFF) {
                            throw new IllegalArgumentException("positions[" + i + "] 은 0~4095 Short 이어야 한다: " + pos.get(i));
                        }
                        if (!(states.get(i) instanceof BlockData s)) throw new IllegalArgumentException("states[" + i + "] 은 BlockData 이어야 한다: " + states.get(i));
                        Types.writeVarLong(o, (long) c.stateId(s) << 12 | p);
                    }
                }));
    }

    /** 동작(varint 서수), OPENED_TAB 일 때만 탭 이름. */
    private static Compiled seenAdvancements(GenericCodec767 c, PacketDef d) {
        Node action = c.fieldNode(d, "action");
        return GenericCodec767.shape(List.of("action", "tab"), List.of(action, GenericCodec767.optional(IDENTIFIER, false)), hand((b, m) -> {
            Object a = action.read(b);
            m.put("action", a);
            m.put("tab", "OPENED_TAB".equals(a) ? Optional.of(IDENTIFIER.read(b)) : Optional.empty());
        }, (o, m) -> {
            Object a = m.get("action");
            Optional<?> tab = (Optional<?>) m.get("tab");
            if (tab.isPresent() != "OPENED_TAB".equals(a)) throw new IllegalArgumentException("tab 은 action 이 OPENED_TAB 일 때만 있어야 한다: " + a + ", " + tab);
            action.write(o, a);
            if (tab.isPresent()) IDENTIFIER.write(o, tab.get());
        }));
    }

    /** 바뀐 칸: varint 개수(바닐라 상한 128), 칸마다 i16 칸 번호 + 아이템(빈 칸 가능). 바닐라는 Int2ObjectMap 이라 같은 칸 번호는 하나다. */
    private static Node changedSlots(GenericCodec767 c) {
        Node item = c.itemStack();
        Compiled entry = GenericCodec767.shape(List.of("slot", "item"), List.of(I16_INT, item), null);
        return node(List.class, b -> {
            int n = Types.readVarInt(b);
            if (n < 0 || n > 128) throw new IllegalArgumentException("바뀐 칸이 너무 많다: " + n + " > 128");
            // 바닐라·서버는 Int2ObjectMap 이라 같은 칸 번호가 다시 오면 마지막 값만 남는다 — 읽기도 그렇게 합친다(검토 R3)
            LinkedHashMap<Object, Object> bySlot = new LinkedHashMap<>();
            for (int i = 0; i < n; i++) {
                PacketStruct e = GenericCodec767.readStruct(entry, b);
                bySlot.put(e.asMap().get("slot"), e);
            }
            return List.copyOf(bySlot.values());
        }, (o, v) -> {
            List<?> l = (List<?>) v;
            if (l.size() > 128) throw new IllegalArgumentException("바뀐 칸이 너무 많다: " + l.size() + " > 128");
            HashSet<Object> seen = new HashSet<>();
            for (int i = 0; i < l.size(); i++) {
                if (!(l.get(i) instanceof PacketStruct s)) throw new IllegalArgumentException("[" + i + "] 은 {slot, item} 이어야 한다: " + l.get(i));
                if (!seen.add(GenericCodec767.checked(entry, s, "[" + i + "]").get("slot"))) throw new IllegalArgumentException("같은 칸 번호가 두 번 있다: " + s);
            }
            Types.writeVarInt(o, l.size());
            for (int i = 0; i < l.size(); i++) GenericCodec767.writeStruct(entry, (PacketStruct) l.get(i), o, "[" + i + "]");
        });
    }

    private static String constantAt(List<String> constants, int i) {
        if (i < 0 || i >= constants.size()) throw new IllegalArgumentException("범위 밖 서수: " + i);
        return constants.get(i);
    }
}
