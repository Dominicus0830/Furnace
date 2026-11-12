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
 * 종류마다 변환 계획을 처음 한 번 만들어 둔다(스키마 해석·필드 반사를 보낼 때마다 하지 않는다).
 * 손 코덱 종류(custom)는 종류마다 서버 손 코덱의 모양으로 바꾼다(E4c-2, {@link #compileCustom}). 타입 레코드 종류는 아직 바꾸지 않는다(E4c-3) —
 * {@link #supports} 가 false 이고 보내면 까닭을 밝혀 거절한다.
 */
public final class SchemaPacketBridge {

    private SchemaPacketBridge() {}

    private static final PacketSchema SCHEMA = PacketSchema.protocol767();

    /** 서버 범용 코덱이 play 연결로 다루는 단계(GenericCodec767.PLAY_STATES 와 같다). */
    private static final Set<String> PLAY_STATES = Set.of("game", "common", "cookie", "ping");

    /** 서버 범용 코덱의 Holder 표와 같다(그 밖의 Holder 는 서버도 못 쓴다). */
    private static final Set<String> HOLDERS = Set.of("MobEffect", "DimensionType", "SoundEvent", "Attribute");

    /** 이 다리가 값을 바꿀 줄 아는 타입 와이어(나머지 — global_pos 등 — 는 shim 이 아직 빈 껍데기다). */
    private static final Set<String> WIRES = Set.of("component", "identifier", "block_pos", "item_stack", "block_state", "nbt", "i64", "vec3", "registry_id",
            "number_format");

    private static final Set<String> REGISTRY_IDS = Set.of("net.minecraft.world.item.Item", "net.minecraft.world.level.block.Block",
            "net.minecraft.world.entity.EntityType");

    /** 값 하나의 변환(at = 오류 글에 쓸 자리). */
    private interface Conv {
        Object apply(Object v, String at);
    }

    /**
     * 종류 하나의 계획: 보낼 수 있으면 fields·convs(필드 나열 종류) 또는 custom(손 코덱 종류), 아니면 refusal(까닭).
     * 손 코덱 종류의 convs 는 스키마 필드마다 범용 변환이고 범용으로 못 바꾸는 필드는 null 이다(그 필드는 custom 이 직접 바꾼다).
     */
    private record Plan(PacketDef def, PacketType<?> type, List<String> names, List<Conv> convs, List<String> fields, Custom custom, String refusal) {}

    /** 손 코덱 종류의 값 바꾸기: shim 값(스키마 필드 순서, 부모 필드 포함) → 서버 손 코덱의 필드(와이어 순서). */
    private interface Custom {
        LinkedHashMap<String, Object> apply(Object[] v, Plan p, String at);
    }

    private static final Map<String, Plan> PLANS = new ConcurrentHashMap<>();

    /** 계획을 만들 수 없는 타입(그 까닭). */
    private static final class NoBridge extends RuntimeException {
        NoBridge(String why) { super(why, null, false, false); }
    }

    /** 스키마 키("game/clientbound/open_sign_editor")의 패킷 정의. */
    static Optional<PacketDef> def(String schemaKey) {
        String[] k = schemaKey.split("/", 3);
        if (k.length != 3) return Optional.empty();
        PacketDirection dir = k[1].equals("clientbound") ? PacketDirection.CLIENTBOUND : k[1].equals("serverbound") ? PacketDirection.SERVERBOUND : null;
        return dir == null ? Optional.empty() : SCHEMA.packet(k[0], dir, k[2]);
    }

    private static Plan plan(PacketDef d) {
        return PLANS.computeIfAbsent(d.state() + "/" + d.direction() + "/" + d.wireName(), k -> compile(d));
    }

    private static Plan refuse(PacketDef d, PacketType<?> t, String why) { return new Plan(d, t, null, null, null, null, why); }

    private static Plan compile(PacketDef d) {
        if (d.direction() != PacketDirection.CLIENTBOUND) {
            return refuse(d, null, "serverbound 패킷이다 — 서버가 클라이언트에 보낼 수 없다(바닐라도 play 연결에서 인코딩이 실패한다). 받은 것처럼 넣으려면 Furnace PacketService.receive 를 쓴다");
        }
        if (!PLAY_STATES.contains(d.state())) {
            return refuse(d, null, d.state() + " 단계 패킷이다 — play 연결로는 보낼 수 없다(바닐라도 인코딩이 실패한다)");
        }
        PacketType<?> t = PacketTypes.byName(PacketDirection.CLIENTBOUND, "minecraft:" + d.wireName());
        if (t == null) return refuse(d, null, "Furnace 패킷 종류 표에 없다");
        if (t.typed()) {
            return refuse(d, t, "Furnace 타입 레코드 종류라 shim 변환이 아직 없다 — Furnace 패킷 API 의 레코드(docs/api/packets.md)를 쓴다");
        }
        if (d.custom()) return compileCustom(d, t);
        if (d.superType() != null) return refuse(d, t, "부모 클래스 필드가 있는 종류는 아직 다루지 않는다");
        try {
            List<String> names = new ArrayList<>();
            List<Conv> convs = new ArrayList<>();
            List<String> fields = new ArrayList<>();
            for (FieldDef f : d.fields()) {
                names.add(snake(f.name()));
                convs.add(field(f, d.javaPackage(), 0));
                fields.add(f.name());
            }
            return new Plan(d, t, List.copyOf(names), List.copyOf(convs), List.copyOf(fields), null, null);
        } catch (NoBridge e) {
            return refuse(d, t, e.getMessage() + " — Furnace 패킷 API(docs/api/packets.md)의 GenericPacket 을 쓴다");
        }
    }

    // ------------------------------------------------------------------ 손 코덱 종류(E4c-2)

    /**
     * 손 코덱 종류(스키마 custom): 서버 손 코덱(GenericHand767)의 필드 이름·순서·모양으로 바꾼다. 범용 변환이 되는 필드는 그것을 쓰고,
     * 나머지(라이브러리 Pair·배열·Holder 등)는 종류마다 직접 바꾼다. 바닐라 write 가 보지 않는 필드(팀 method 1 의 parameters 등)는 바닐라처럼 버린다.
     * 대응표가 없는 종류는 까닭과 함께 거절한다(docs/feature-parity/compat-nms-packets-2026-11-12.md).
     */
    private static Plan compileCustom(PacketDef d, PacketType<?> t) {
        String no = CUSTOM_REFUSALS.get(d.wireName());
        if (no != null) return refuse(d, t, no + " — Furnace 패킷 API(docs/api/packets.md)의 GenericPacket 을 쓴다");
        Custom c = custom(d.wireName());
        if (c == null) {
            return refuse(d, t, "와이어가 필드 나열이 아니라(손 코덱) 아직 shim 에서 바꾸지 못한다 — Furnace 패킷 API(docs/api/packets.md)의 GenericPacket 을 쓴다");
        }
        List<FieldDef> all = new ArrayList<>();
        String pkg = d.javaPackage();
        if (d.superType() != null) {
            String base = SCHEMA.resolve(d.superType().name(), pkg);
            StructDef st = base == null ? null : SCHEMA.struct(base).orElse(null);
            if (st == null) return refuse(d, t, "부모 클래스 " + d.superType() + " 를 스키마에서 찾지 못했다");
            all.addAll(st.fields());
        }
        all.addAll(d.fields());
        List<String> names = new ArrayList<>();
        List<Conv> convs = new ArrayList<>();
        List<String> fields = new ArrayList<>();
        for (FieldDef f : all) {
            names.add(snake(f.name()));
            Conv cv;
            try {
                cv = field(f, pkg, 0);
            } catch (NoBridge e) {
                cv = null;   // 이 필드는 custom 이 직접 바꾼다
            }
            convs.add(cv);
            fields.add(f.name());
        }
        return new Plan(d, t, List.copyOf(names), java.util.Collections.unmodifiableList(convs), List.copyOf(fields), c, null);
    }

    /** 대응표를 두지 않은 손 코덱 종류(clientbound play)와 까닭. 타입 레코드 종류(add_entity 등)는 그 앞에서 따로 거절된다. */
    private static final Map<String, String> CUSTOM_REFUSALS = Map.of(
            "boss_event", "보스바 operation 은 바닐라에서도 private 인터페이스이고 BossEvent shim 이 아직 비어 있어 shim 값을 읽을 수 없다",
            "award_stats", "통계 맵(fastutil Object2IntMap<Stat<?>>)은 라이브러리 타입이 지워졌고 Stat shim 이 아직 비어 있다");

    /** 스키마 필드 i 를 범용 변환으로. */
    private static Object g(Object[] v, Plan p, int i, String at) {
        Conv c = p.convs().get(i);
        if (c == null) throw new IllegalStateException(p.def().wireName() + "." + p.fields().get(i) + " 은 범용 변환이 없다");
        return c.apply(v[i], at + "." + p.fields().get(i));
    }

    /** 범용 변환한 필드를 그대로 손 코덱 이름으로(이름 = snake_case, 순서는 order). */
    private static Custom reorder(String... order) {
        return (v, p, at) -> {
            LinkedHashMap<String, Object> m = new LinkedHashMap<>();
            for (String n : order) {
                int i = p.names().indexOf(n);
                if (i < 0) throw new IllegalStateException(p.def().wireName() + " 스키마에 " + n + " 이 없다");
                m.put(n, g(v, p, i, at));
            }
            return m;
        };
    }

    /** 이동 패킷 셋: 부모 ClientboundMoveEntityPacket 필드(entityId, xa, ya, za, yRot, xRot, onGround, hasRot, hasPos) 중 그 종류가 쓰는 것. */
    private static Custom move(boolean pos, boolean rot) {
        return (v, p, at) -> {
            LinkedHashMap<String, Object> m = new LinkedHashMap<>();
            m.put("entity_id", v[0]);
            if (pos) {
                m.put("xa", v[1]);
                m.put("ya", v[2]);
                m.put("za", v[3]);
            }
            if (rot) {
                m.put("y_rot", v[4]);
                m.put("x_rot", v[5]);
            }
            m.put("on_ground", v[6]);
            return m;
        };
    }

    private static Custom custom(String wire) {
        return switch (wire) {
            case "player_abilities" -> reorder("invulnerable", "is_flying", "can_fly", "instabuild", "flying_speed", "walking_speed");
            case "set_experience" -> reorder("experience_progress", "experience_level", "total_experience");
            case "stop_sound" -> reorder("source", "name");   // 둘 다 null 이면 바닐라처럼 플래그 0(모든 소리 멈춤)
            case "player_position" -> reorder("x", "y", "z", "y_rot", "x_rot", "relative_arguments", "id");
            case "move_entity_pos" -> move(true, false);
            case "move_entity_pos_rot" -> move(true, true);
            case "move_entity_rot" -> move(false, true);
            case "player_look_at" -> SchemaPacketBridge::lookAt;
            case "set_equipment" -> SchemaPacketBridge::equipment;
            case "section_blocks_update" -> SchemaPacketBridge::sectionBlocks;
            case "damage_event" -> SchemaPacketBridge::damageEvent;
            case "set_player_team" -> SchemaPacketBridge::playerTeam;
            case "set_objective" -> SchemaPacketBridge::objective;
            case "update_attributes" -> SchemaPacketBridge::attributes;
            case "map_item_data" -> SchemaPacketBridge::mapItemData;
            default -> null;
        };
    }

    /** x, y, z, entity, fromAnchor, toAnchor, atEntity → from_anchor x y z at_entity Optional<{entity, to_anchor}>(atEntity 일 때만, 바닐라 write 와 같다). */
    private static LinkedHashMap<String, Object> lookAt(Object[] v, Plan p, String at) {
        LinkedHashMap<String, Object> m = new LinkedHashMap<>();
        m.put("from_anchor", g(v, p, 4, at));
        m.put("x", v[0]);
        m.put("y", v[1]);
        m.put("z", v[2]);
        if ((Boolean) v[6]) {
            LinkedHashMap<String, Object> e = new LinkedHashMap<>();
            e.put("entity", v[3]);
            e.put("to_anchor", g(v, p, 5, at));
            m.put("at_entity", Optional.of(PacketStruct.of(e)));
        } else {
            m.put("at_entity", Optional.empty());
        }
        return m;
    }

    /** entity, slots(List&lt;Pair&lt;EquipmentSlot, ItemStack&gt;&gt; — Pair 는 DataFixerUpper 라 이름으로 getFirst·getSecond 를 부른다). */
    private static LinkedHashMap<String, Object> equipment(Object[] v, Plan p, String at) {
        LinkedHashMap<String, Object> m = new LinkedHashMap<>();
        m.put("entity", v[0]);
        List<Object> slots = new ArrayList<>();
        int i = 0;
        for (Object pair : (Collection<?>) need(v[1], at + ".slots")) {
            String here = at + ".slots[" + i++ + "]";
            Object slot = pairPart(need(pair, here), "getFirst", here), item = pairPart(pair, "getSecond", here);
            if (!(slot instanceof net.minecraft.world.entity.EquipmentSlot es)) throw new IllegalArgumentException(here + ".first 는 EquipmentSlot 이어야 한다: " + slot);
            if (!(item instanceof net.minecraft.world.item.ItemStack is)) throw new IllegalArgumentException(here + ".second 는 ItemStack 이어야 한다: " + item);
            LinkedHashMap<String, Object> e = new LinkedHashMap<>();
            e.put("slot", es.name());
            e.put("item", is.furnaceValue());
            slots.add(PacketStruct.of(e));
        }
        m.put("slots", slots);
        return m;
    }

    private static Object pairPart(Object pair, String method, String at) {
        try {
            java.lang.reflect.Method mt = pair.getClass().getMethod(method);
            return mt.invoke(pair);
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException(at + " 은 DataFixerUpper Pair(getFirst·getSecond)이어야 한다: " + pair.getClass().getName());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(at + ": " + e, e);
        }
    }

    /**
     * sectionPos, positions(short[]), states(BlockState[]) → section_pos positions states. 바닐라 write 는 positions 길이만큼 states[i] 를 읽는다:
     * states 가 짧으면 바닐라도 실패(배열 밖), 길면 남는 것은 쓰지 않는다 — 같게 자른다.
     */
    private static LinkedHashMap<String, Object> sectionBlocks(Object[] v, Plan p, String at) {
        short[] pos = (short[]) need(v[1], at + ".positions");
        net.minecraft.world.level.block.state.BlockState[] st = (net.minecraft.world.level.block.state.BlockState[]) need(v[2], at + ".states");
        if (st.length < pos.length) throw new IllegalArgumentException(at + ": states(" + st.length + ") 가 positions(" + pos.length + ") 보다 짧다(바닐라도 쓰다 실패한다)");
        List<Object> ps = new ArrayList<>(pos.length), ss = new ArrayList<>(pos.length);
        for (int i = 0; i < pos.length; i++) {
            ps.add(pos[i]);
            ss.add(blockData((net.minecraft.world.level.block.state.BlockState) need(st[i], at + ".states[" + i + "]")));
        }
        LinkedHashMap<String, Object> m = new LinkedHashMap<>();
        m.put("section_pos", g(v, p, 0, at));
        m.put("positions", ps);
        m.put("states", ss);
        return m;
    }

    /** entityId, sourceType(Holder&lt;DamageType&gt;), sourceCauseId, sourceDirectId, sourcePosition → 같은 순서. 피해 종류는 등록된 Reference 의 이름. */
    private static LinkedHashMap<String, Object> damageEvent(Object[] v, Plan p, String at) {
        LinkedHashMap<String, Object> m = new LinkedHashMap<>();
        m.put("entity_id", v[0]);
        m.put("source_type", referenceName(v[1], "DamageType", at + ".sourceType"));
        m.put("source_cause_id", v[2]);
        m.put("source_direct_id", v[3]);
        m.put("source_position", g(v, p, 4, at));
        return m;
    }

    /**
     * method, name, players, parameters → name method parameters players. 바닐라 write 는 method 0·2 일 때만 parameters(없으면 실패),
     * 0·3·4 일 때만 players 를 쓴다 — 나머지는 버린다.
     */
    private static LinkedHashMap<String, Object> playerTeam(Object[] v, Plan p, String at) {
        int method = (Integer) v[0];
        LinkedHashMap<String, Object> m = new LinkedHashMap<>();
        m.put("name", need(v[1], at + ".name"));
        m.put("method", method);
        if (method == 0 || method == 2) {
            Optional<?> o = (Optional<?>) need(v[3], at + ".parameters");
            if (o.isEmpty()) throw new IllegalArgumentException(at + ": method " + method + " 인데 parameters 가 없다(바닐라도 쓰다 실패한다)");
            PacketStruct ps = (PacketStruct) ((Optional<?>) g(v, p, 3, at)).orElseThrow();
            LinkedHashMap<String, Object> q = new LinkedHashMap<>();
            for (String n : List.of("display_name", "options", "nametag_visibility", "collision_rule", "color", "player_prefix", "player_suffix")) {
                q.put(n, ps.asMap().get(n));
            }
            m.put("parameters", Optional.of(PacketStruct.of(q)));
        } else {
            m.put("parameters", Optional.empty());
        }
        m.put("players", method == 0 || method == 3 || method == 4 ? Optional.of(g(v, p, 2, at)) : Optional.empty());
        return m;
    }

    /**
     * objectiveName, displayName, renderType, numberFormat, method → objective_name method parameters. 바닐라 write 는 method 0·2 일 때만
     * 이름·그리기 방식·숫자 형식을 쓴다.
     */
    private static LinkedHashMap<String, Object> objective(Object[] v, Plan p, String at) {
        int method = (Integer) v[4];
        LinkedHashMap<String, Object> m = new LinkedHashMap<>();
        m.put("objective_name", need(v[0], at + ".objectiveName"));
        m.put("method", method);
        if (method == 0 || method == 2) {
            LinkedHashMap<String, Object> q = new LinkedHashMap<>();
            q.put("display_name", g(v, p, 1, at));
            q.put("render_type", g(v, p, 2, at));
            q.put("number_format", g(v, p, 3, at));
            m.put("parameters", Optional.of(PacketStruct.of(q)));
        } else {
            m.put("parameters", Optional.empty());
        }
        return m;
    }

    private static final Field STYLED_STYLE = declared(net.minecraft.network.chat.numbers.StyledFormat.class, "style");
    private static final Field FIXED_VALUE = declared(net.minecraft.network.chat.numbers.FixedFormat.class, "value");

    private static Field declared(Class<?> c, String name) {
        try {
            Field f = c.getDeclaredField(name);
            f.setAccessible(true);
            return f;
        } catch (NoSuchFieldException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    /**
     * 바닐라 숫자 형식 셋 → 서버 값({type, …}). 바닐라도 등록된 형식 종류(blank·styled·fixed)만 쓸 수 있다.
     * styled 의 글자 모양은 NBT 로 옮긴다 — 색·꾸밈·insertion·font 만. 클릭·호버 사건은 아직 옮기지 못해 거절한다(가정: 점수 숫자 모양에 쓰는 일은 드물다).
     */
    private static PacketStruct numberFormat(Object f, String at) {
        LinkedHashMap<String, Object> m = new LinkedHashMap<>();
        if (f instanceof net.minecraft.network.chat.numbers.BlankFormat) {
            m.put("type", "minecraft:blank");
        } else if (f instanceof net.minecraft.network.chat.numbers.StyledFormat) {
            m.put("type", "minecraft:styled");
            m.put("style", styleNbt(((net.minecraft.network.chat.Style) need(get(STYLED_STYLE, f), at + ".style")).furnace(), at + ".style"));
        } else if (f instanceof net.minecraft.network.chat.numbers.FixedFormat) {
            m.put("type", "minecraft:fixed");
            m.put("value", net.minecraft.network.chat.Component.furnace((net.minecraft.network.chat.Component) need(get(FIXED_VALUE, f), at + ".value")));
        } else {
            throw new IllegalArgumentException(at + ": 바닐라 숫자 형식(BlankFormat·StyledFormat·FixedFormat)이 아니다(바닐라도 형식 종류가 없어 쓰다 실패한다): " + f);
        }
        return PacketStruct.of(m);
    }

    /** 바닐라 Style 직렬화(NBT)와 같은 키: color(이름·#RRGGBB), bold 등 꾸밈(바이트 0·1, 정해진 것만), insertion, font. */
    private static org.domi.furnace.api.nbt.NbtCompound styleNbt(org.domi.furnace.api.text.Style s, String at) {
        if (s.clickEvent() != null || s.hoverEvent() != null) {
            throw new IllegalArgumentException(at + ": 숫자 형식 글자 모양의 클릭·호버 사건은 아직 보낼 수 없다(Furnace 다리 한계) — 색·꾸밈·insertion·font 만 쓴다");
        }
        org.domi.furnace.api.nbt.NbtCompound n = new org.domi.furnace.api.nbt.NbtCompound();
        if (s.color() != null) n.putString("color", s.color().serialize());
        for (var d : List.of(org.domi.furnace.api.text.TextDecoration.BOLD, org.domi.furnace.api.text.TextDecoration.ITALIC,
                org.domi.furnace.api.text.TextDecoration.UNDERLINED, org.domi.furnace.api.text.TextDecoration.STRIKETHROUGH,
                org.domi.furnace.api.text.TextDecoration.OBFUSCATED)) {
            var st = s.decoration(d);
            if (st != org.domi.furnace.api.text.TriState.NOT_SET) n.putBoolean(d.name().toLowerCase(java.util.Locale.ROOT), st == org.domi.furnace.api.text.TriState.TRUE);
        }
        if (s.insertion() != null) n.putString("insertion", s.insertion());
        if (s.font() != null) n.putString("font", s.font());
        return n;
    }

    /** entityId, attributes(List&lt;AttributeSnapshot{attribute, base, modifiers}&gt;) → entity_id attributes List&lt;{attribute, base, modifiers List&lt;{id, amount, operation}&gt;}&gt;. */
    private static LinkedHashMap<String, Object> attributes(Object[] v, Plan p, String at) {
        LinkedHashMap<String, Object> m = new LinkedHashMap<>();
        m.put("entity_id", v[0]);
        List<Object> out = new ArrayList<>();
        int i = 0;
        for (Object o : (Collection<?>) need(v[1], at + ".attributes")) {
            String here = at + ".attributes[" + i++ + "]";
            var a = (net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket.AttributeSnapshot) need(o, here);
            List<Object> mods = new ArrayList<>();
            int j = 0;
            if (a.modifiers() == null) throw new IllegalArgumentException(here + ".modifiers 이 null 이다(바닐라도 이 패킷을 쓰다 실패한다)");
            for (var mod : a.modifiers()) {
                String mh = here + ".modifiers[" + j++ + "]";
                need(mod, mh);
                LinkedHashMap<String, Object> e = new LinkedHashMap<>();
                e.put("id", need(mod.id(), mh + ".id").toString());
                e.put("amount", mod.amount());
                e.put("operation", ((Enum<?>) need(mod.operation(), mh + ".operation")).name());
                mods.add(PacketStruct.of(e));
            }
            LinkedHashMap<String, Object> e = new LinkedHashMap<>();
            e.put("attribute", referenceName(a.attribute(), "Attribute", here + ".attribute"));
            e.put("base", a.base());
            e.put("modifiers", mods);
            out.add(PacketStruct.of(e));
        }
        m.put("attributes", out);
        return m;
    }

    /**
     * mapId, scale, locked, decorations, colorPatch → map_id scale locked decorations color_patch. 표시 종류는 등록된 것(Reference 나 레지스트리에 있는
     * 같은 값 — 바닐라 holderRegistry 와 같다). 색 조각의 네 수는 바닐라 writeByte 처럼 아래 8비트만 보낸다.
     */
    private static LinkedHashMap<String, Object> mapItemData(Object[] v, Plan p, String at) {
        LinkedHashMap<String, Object> m = new LinkedHashMap<>();
        m.put("map_id", ((net.minecraft.world.level.saveddata.maps.MapId) need(v[0], at + ".mapId")).id());
        m.put("scale", v[1]);
        m.put("locked", v[2]);
        m.put("decorations", ((Optional<?>) need(v[3], at + ".decorations")).map(l -> {
            List<Object> out = new ArrayList<>();
            int i = 0;
            for (Object o : (Collection<?>) l) {
                String here = at + ".decorations[" + i++ + "]";
                var d = (net.minecraft.world.level.saveddata.maps.MapDecoration) need(o, here);
                LinkedHashMap<String, Object> e = new LinkedHashMap<>();
                e.put("type", mapDecorationType(d.type(), here + ".type"));
                e.put("x", d.x());
                e.put("y", d.y());
                e.put("rot", d.rot());
                e.put("name", ((Optional<?>) need(d.name(), here + ".name")).map(c -> net.minecraft.network.chat.Component.furnace((net.minecraft.network.chat.Component) c)));
                out.add(PacketStruct.of(e));
            }
            return out;
        }));
        m.put("color_patch", ((Optional<?>) need(v[4], at + ".colorPatch")).map(o -> {
            var c = (net.minecraft.world.level.saveddata.maps.MapItemSavedData.MapPatch) o;
            LinkedHashMap<String, Object> e = new LinkedHashMap<>();
            e.put("width", c.width() & 0xFF);
            e.put("height", c.height() & 0xFF);
            e.put("start_x", c.startX() & 0xFF);
            e.put("start_y", c.startY() & 0xFF);
            e.put("map_colors", need(c.mapColors(), at + ".colorPatch.mapColors"));
            return PacketStruct.of(e);
        }));
        return m;
    }

    private static String mapDecorationType(Object h, String at) {
        if (h instanceof net.minecraft.core.Holder<?> holder && holder.unwrapKey().isEmpty()
                && holder.value() instanceof net.minecraft.world.level.saveddata.maps.MapDecorationType t) {
            var loc = net.minecraft.core.registries.BuiltInRegistries.MAP_DECORATION_TYPE.getKey(t);
            if (loc == null) throw new IllegalArgumentException(at + ": 지도 표시 종류 레지스트리에 없는 값이다(바닐라도 번호를 못 찾아 쓰다 실패한다): " + t);
            return loc.toString();
        }
        return referenceName(h, "MapDecorationType", at);
    }

    /** 레지스트리 shim 이 아직 없는 종류의 Holder: 등록된 Reference 의 이름만(직접 정의는 바닐라가 번호로 바꾸는 값인지 Furnace 가 알 수 없어 거절). */
    private static String referenceName(Object h, String registry, String at) {
        if (h instanceof net.minecraft.core.Holder<?> holder) {
            var key = holder.unwrapKey();
            if (key.isPresent()) return key.get().location().toString();
            throw new IllegalArgumentException(at + ": " + registry + " 직접 정의 — Furnace 는 아직 이 레지스트리 값을 찾지 못한다. Holder.Reference 로 보낸다: " + h);
        }
        throw new IllegalArgumentException(at + ": " + registry + " Holder 가 아니거나 null 이다: " + h);
    }

    /**
     * 이 패킷 정의를 보낼 수 있게 바꿀 수 있는가(스키마만 보고 정한다 — 서버 없이도 된다). play 연결 clientbound, 타입 레코드가 아닌 종류,
     * 필드 나열이면 모든 필드 타입을 이 다리가 아는 것, 손 코덱(custom)이면 대응표가 있는 것.
     */
    public static boolean supports(PacketDef d) { return plan(d).refusal() == null; }

    /** 보낼 수 없는 까닭(보낼 수 있으면 null). 시험용. */
    static String refusal(PacketDef d) { return plan(d).refusal(); }

    private static Conv field(FieldDef f, String pkg, int depth) {
        Conv c = conv(f.type(), f.wire(), pkg, depth);
        if (!f.nullable()) return c;
        return (v, at) -> v == null ? Optional.empty() : Optional.of(c.apply(v, at));
    }

    private static Conv conv(TypeRef t, String wire, String pkg, int depth) {
        if (depth > 8) throw new NoBridge("타입이 너무 깊다: " + t);
        if (t.bound() != TypeRef.Bound.NONE) throw new NoBridge("와일드카드 타입 " + t);
        if (t.arrayDimensions() > 0) {
            if (t.arrayDimensions() == 1 && wire == null && Set.of("byte", "long", "int").contains(t.name())) return SchemaPacketBridge::need;
            throw new NoBridge("배열 " + t);
        }
        switch (t.name()) {
            case "int":
                return (v, at) -> narrow((Integer) need(v, at), wire);
            case "short", "byte", "long", "float", "double", "boolean", "String", "UUID":
                return SchemaPacketBridge::need;
            case "List", "Set", "Collection": {
                if (wire != null) throw new NoBridge("목록 명시 와이어 " + wire);
                Conv e = conv(t.arguments().get(0), null, pkg, depth + 1);
                return (v, at) -> {
                    List<Object> out = new ArrayList<>();
                    int i = 0;
                    for (Object x : (Collection<?>) need(v, at)) out.add(e.apply(x, at + "[" + i++ + "]"));
                    return out;
                };
            }
            case "Optional": {
                if (wire != null) throw new NoBridge("Optional 명시 와이어 " + wire);
                Conv e = conv(t.arguments().get(0), null, pkg, depth + 1);
                return (v, at) -> ((Optional<?>) need(v, at)).map(x -> e.apply(x, at));
            }
            case "Holder": {
                String arg = t.arguments().get(0).name();
                if (!HOLDERS.contains(arg)) throw new NoBridge("Holder<" + arg + "> 는 서버 코덱도 아직 쓰지 못한다");
                return (v, at) -> holder(need(v, at), arg, at);
            }
            case "ResourceKey":
                return (v, at) -> ((net.minecraft.resources.ResourceKey<?>) need(v, at)).location().toString();
            case "Instant":
                return (v, at) -> ((java.time.Instant) need(v, at)).toEpochMilli();
            case "Map": {
                if (wire != null || t.arguments().size() != 2 || !t.arguments().get(0).name().equals("String") || !t.arguments().get(1).name().equals("String")) {
                    throw new NoBridge("Map " + t);
                }
                return (v, at) -> {
                    LinkedHashMap<String, Object> m = new LinkedHashMap<>();
                    for (Map.Entry<?, ?> e : ((Map<?, ?>) need(v, at)).entrySet()) m.put((String) e.getKey(), e.getValue());
                    return PacketStruct.of(m);
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
            if ("i8".equals(wire)) return (v, at) -> v == null ? Optional.empty() : Optional.of(((Enum<?>) v).name());   // GameType.byNullableId 처럼 -1 = 없음
            return (v, at) -> ((Enum<?>) need(v, at)).name();
        }
        if (wire != null) {   // 명시 와이어(예: game_event 의 Type u8) = 보조 클래스가 들고 있는 바닐라 번호
            String idField = intField(struct);
            if (idField == null) throw new NoBridge("명시 와이어 " + wire + " 자리의 " + simple(bin));
            Field fld = reflectField(bin, idField);
            return (v, at) -> narrow((Integer) get(fld, need(v, at)), wire);
        }
        if (decl != null) {
            String w = String.valueOf(decl.wire());
            if (!WIRES.contains(w) || ("registry_id".equals(w) && !REGISTRY_IDS.contains(bin))) {
                throw new NoBridge(simple(bin) + " 값 shim 이 아직 빈 껍데기다");
            }
            return switch (w) {
                case "component" -> (v, at) -> net.minecraft.network.chat.Component.furnace((net.minecraft.network.chat.Component) need(v, at));
                case "identifier" -> (v, at) -> need(v, at) instanceof net.minecraft.resources.ResourceKey<?> k ? k.location().toString() : v.toString();
                case "block_pos" -> (v, at) -> ((net.minecraft.core.BlockPos) need(v, at)).furnace();
                case "item_stack" -> (v, at) -> ((net.minecraft.world.item.ItemStack) need(v, at)).furnaceValue();
                case "block_state" -> (v, at) -> blockData((net.minecraft.world.level.block.state.BlockState) need(v, at));
                case "nbt" -> (v, at) -> ((net.minecraft.nbt.CompoundTag) need(v, at)).furnace();
                case "i64" -> (v, at) -> need(v, at) instanceof net.minecraft.core.SectionPos s ? s.asLong() : ((net.minecraft.world.level.ChunkPos) v).toLong();
                case "vec3" -> (v, at) -> {
                    var p = (net.minecraft.world.phys.Vec3) need(v, at);
                    LinkedHashMap<String, Object> m = new LinkedHashMap<>();
                    m.put("x", p.x);
                    m.put("y", p.y);
                    m.put("z", p.z);
                    return PacketStruct.of(m);
                };
                case "registry_id" -> (v, at) -> registryName(need(v, at));
                case "number_format" -> (v, at) -> numberFormat(need(v, at), at);
                default -> throw new NoBridge("와이어 " + w);
            };
        }
        if (struct == null || !(struct.kind() == StructDef.Kind.RECORD || struct.kind() == StructDef.Kind.CLASS)) throw new NoBridge("타입 " + simple(bin));
        List<String> names = new ArrayList<>();
        List<Conv> convs = new ArrayList<>();
        List<Field> refl = new ArrayList<>();
        for (FieldDef f : struct.fields()) {
            names.add(snake(f.name()));
            convs.add(field(f, struct.javaPackage(), depth + 1));
            refl.add(reflectField(bin, f.name()));
        }
        return (v, at) -> {
            Object owner = need(v, at);
            LinkedHashMap<String, Object> m = new LinkedHashMap<>();
            for (int i = 0; i < names.size(); i++) m.put(names.get(i), convs.get(i).apply(get(refl.get(i), owner), at + "." + struct.fields().get(i).name()));
            return PacketStruct.of(m);
        };
    }

    /** 명시 와이어 자리의 보조 클래스가 들고 있는 번호 필드 하나(game_event Type 의 id). 없으면 null. */
    private static String intField(StructDef struct) {
        if (struct == null || struct.fields().size() != 1) return null;
        FieldDef f = struct.fields().get(0);
        return f.type().name().equals("int") && f.type().arrayDimensions() == 0 ? f.name() : null;
    }

    /** 생성된 보조 shim(record·class)의 필드 — 필드 이름은 스키마 이름 그대로다. 계획을 만들 때 한 번 찾는다. */
    private static Field reflectField(String bin, String name) {
        Class<?> c;
        try {
            c = Class.forName(bin, false, SchemaPacketBridge.class.getClassLoader());
        } catch (ClassNotFoundException | LinkageError e) {
            throw new NoBridge(simple(bin) + " shim 클래스가 없다");
        }
        for (Class<?> k = c; k != null && k != Object.class; k = k.getSuperclass()) {
            try {
                Field f = k.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException e) {
                // 부모에서 찾는다
            }
        }
        throw new NoBridge(simple(bin) + " shim 에 스키마 필드 " + name + " 이 없다");
    }

    private static Object get(Field f, Object owner) {
        try {
            return f.get(owner);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String simple(String bin) { return bin.substring(bin.lastIndexOf('.') + 1); }

    /**
     * shim → GenericPacket. 보낼 수 없는 종류이거나 이 서버가 그 종류를 읽고 쓰지 못하면 까닭을 밝힌 {@link UnsupportedNmsCall},
     * 값이 바닐라 모양이 아니면(필수 필드가 null 등) IllegalArgumentException.
     */
    @SuppressWarnings("unchecked")
    public static GenericPacket toFurnace(SchemaPacket packet) {
        PacketDef d = def(packet.schemaKey()).orElseThrow(() -> new UnsupportedNmsCall("send(" + packet.getClass().getName() + ")", "스키마에 없는 패킷"));
        Plan p = plan(d);
        String call = "ServerCommonPacketListenerImpl#send(" + d.binaryName() + ")";
        if (p.refusal() != null) throw new UnsupportedNmsCall(call, p.refusal());
        if (!Furnace.server().packets().isReadable(p.type())) throw new UnsupportedNmsCall(call, "이 서버 버전이 이 패킷을 쓰지 못한다");
        Object[] values = packet.schemaValues();
        if (values.length != p.convs().size()) throw new IllegalStateException(d.binaryName() + " shim 값 수가 스키마와 다르다");
        LinkedHashMap<String, Object> m;
        if (p.custom() != null) {
            m = p.custom().apply(values, p, d.wireName());
        } else {
            m = new LinkedHashMap<>();
            for (int i = 0; i < values.length; i++) m.put(p.names().get(i), p.convs().get(i).apply(values[i], d.wireName() + "." + p.fields().get(i)));
        }
        return GenericPacket.of((PacketType<GenericPacket>) p.type(), PacketStruct.of(m));
    }

    /**
     * Holder → 서버 코덱 값. 등록된 것(Reference)은 키 이름 — 소리는 그 이름이 소리 레지스트리에 있어야 한다(바닐라 holder 코덱은 레지스트리에 없는
     * Reference 의 번호를 못 찾아 인코딩이 실패한다. 서버 코덱은 모르는 이름을 직접 정의로 써 주므로 여기서 막는다, 검토 R3).
     * 직접 정의(Direct) 소리는 PacketStruct{sound_id, range}로 넘겨 서버 코덱이 바닐라처럼 언제나 직접 정의(0 + 이름 + 고정 범위 Optional)로 쓴다.
     * 다른 레지스트리(holderRegistry — asHolderIdMap.getId(value()))는 바닐라가 직접 정의라도 값이 레지스트리에 있으면 그 번호로 보낸다:
     * 효과는 MOB_EFFECT 에서 같은 객체의 이름을 찾는다. 레지스트리 shim 이 아직 없는 Attribute·DimensionType 의 직접 정의는 거절한다(검토 PR #263 R3).
     */
    private static Object holder(Object v, String registry, String at) {
        if (v instanceof net.minecraft.core.Holder<?> h) {
            var key = h.unwrapKey();
            if (key.isPresent()) {
                var loc = key.get().location();
                if (registry.equals("SoundEvent") && !net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.containsKey(loc)) {
                    throw new IllegalArgumentException(at + ": 소리 레지스트리에 없는 Reference 다(바닐라도 번호를 못 찾아 인코딩이 실패한다) — "
                            + "리소스 팩 소리는 Holder.direct(SoundEvent.createVariableRangeEvent(…)) 로 보낸다: " + loc);
                }
                return loc.toString();
            }
            if (registry.equals("SoundEvent") && h.value() instanceof net.minecraft.sounds.SoundEvent s) {
                LinkedHashMap<String, Object> m = new LinkedHashMap<>();
                m.put("sound_id", s.getLocation().toString());
                m.put("range", fixedRange(s));
                return PacketStruct.of(m);
            }
            if (registry.equals("MobEffect") && h.value() instanceof net.minecraft.world.effect.MobEffect e) {
                var loc = net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.getKey(e);
                if (loc == null) throw new IllegalArgumentException(at + ": 효과 레지스트리에 없는 효과다(바닐라도 번호를 못 찾아 인코딩이 실패한다): " + e);
                return loc.toString();
            }
            if (h.value() != null) {
                throw new IllegalArgumentException(at + ": " + registry + " 직접 정의 — 바닐라는 값이 레지스트리에 있으면 그 번호로 보내지만 Furnace 는 아직 이 레지스트리 값을 찾지 못한다."
                        + " Holder.Reference 로 보낸다: " + v);
            }
        }
        throw new IllegalArgumentException(at + ": " + registry + " Holder 가 아니거나 값이 없다: " + v);
    }

    /** SoundEvent 의 private fixedRange()(바닐라와 같은 private 멤버라 반사로 읽는다). */
    private static final java.lang.reflect.Method FIXED_RANGE;

    static {
        try {
            FIXED_RANGE = net.minecraft.sounds.SoundEvent.class.getDeclaredMethod("fixedRange");
            FIXED_RANGE.setAccessible(true);
        } catch (NoSuchMethodException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @SuppressWarnings("unchecked")
    private static Optional<Float> fixedRange(net.minecraft.sounds.SoundEvent s) {
        try {
            return (Optional<Float>) FIXED_RANGE.invoke(s);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
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
