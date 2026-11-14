package org.domi.furnace.compat.nms;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.domi.furnace.api.entity.data.EntityDataType;
import org.domi.furnace.api.packet.clientbound.ClientboundSetEntityDataPacket;
import net.minecraft.network.syncher.EntityDataSerializer;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;

/**
 * 엔티티 메타데이터 패킷(set_entity_data) shim ↔ Furnace 타입 레코드(ProtocolLib 다리 2b-4 의 첫 종류, 2026-11-14 점검).
 *
 * <p>값 종류는 이름으로 잇는다 — shim {@link EntityDataSerializers} 의 상수 이름(소문자)이 Furnace {@link EntityDataType} 이름과 같다
 * (byte, optional_component, item_stack …). 두 방향 모두 값마다 바꾸고, 바꾸지 못하는 종류가 하나라도 있으면 패킷 전체를
 * {@link UnsupportedNmsCall} 로 거절한다(일부 칸만 버리면 클라이언트가 보는 엔티티 모양이 Paper 와 달라진다).
 *
 * <p>아직 못 하는 종류: particle·particles(입자 shim 이 빈 껍데기), villager_data·고양이/늑대/개구리/그림 변형(값 shim 이 빈 껍데기),
 * optional_global_pos, vector3·quaternion(shim 직렬화 상수가 없다 — JOML 이 이 로더에 없다).
 */
final class EntityDataBridge {

    private EntityDataBridge() {}

    /** shim 직렬화 상수 → 이름(소문자 상수 이름). */
    private static final Map<EntityDataSerializer<?>, String> NAMES = new IdentityHashMap<>();
    /** 이름 → shim 직렬화 상수. */
    private static final Map<String, EntityDataSerializer<?>> BY_NAME = new java.util.HashMap<>();

    static {
        for (Field f : EntityDataSerializers.class.getDeclaredFields()) {
            if (!Modifier.isStatic(f.getModifiers()) || !EntityDataSerializer.class.isAssignableFrom(f.getType())) continue;
            try {
                EntityDataSerializer<?> s = (EntityDataSerializer<?>) f.get(null);
                String n = f.getName().toLowerCase(Locale.ROOT);
                NAMES.put(s, n);
                BY_NAME.put(n, s);
            } catch (IllegalAccessException e) {
                throw new ExceptionInInitializerError(e);
            }
        }
    }

    // ------------------------------------------------------------------ shim → Furnace

    static ClientboundSetEntityDataPacket toFurnace(Object[] v, String at) {
        int id = ((Number) v[0]).intValue();
        List<?> items = (List<?>) SchemaPacketReverse.need(v[1], at + ".packedItems");
        List<ClientboundSetEntityDataPacket.Entry> out = new ArrayList<>(items.size());
        for (Object o : items) {
            var dv = (SynchedEntityData.DataValue<?>) SchemaPacketReverse.need(o, at + ".packedItems[]");
            String name = dv.serializer() == null ? null : NAMES.get(dv.serializer());
            if (name == null) throw new IllegalArgumentException(at + ": 칸 " + dv.id() + " 의 값 종류가 바닐라 직렬화 표에 없다: " + dv.serializer());
            EntityDataType<?> type = EntityDataType.byName(name);
            if (type == null) throw new UnsupportedNmsCall("set_entity_data(" + name + ")", "Furnace 에 없는 메타데이터 값 종류");
            String vat = at + "[" + dv.id() + "]";
            out.add(new ClientboundSetEntityDataPacket.Entry(dv.id(), value(type, toApi(name, dv.value(), vat))));
        }
        return new ClientboundSetEntityDataPacket(id, out);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ClientboundSetEntityDataPacket.Value value(EntityDataType type, Object v) {
        return ClientboundSetEntityDataPacket.Value.of(type, v);
    }

    private static Object toApi(String name, Object v, String at) {
        SchemaPacketReverse.need(v, at);   // 바닐라 직렬화도 null 값은 쓰다 실패한다(Optional 종류는 Optional.empty())
        return switch (name) {
            case "byte", "int", "long", "float", "string", "boolean", "optional_uuid", "optional_unsigned_int" -> v;
            case "component" -> net.minecraft.network.chat.Component.furnace((net.minecraft.network.chat.Component) v);
            case "optional_component" -> ((Optional<?>) v).map(c -> net.minecraft.network.chat.Component.furnace((net.minecraft.network.chat.Component) c));
            case "item_stack" -> ((net.minecraft.world.item.ItemStack) v).furnaceValue();
            case "rotations" -> {
                var r = (net.minecraft.core.Rotations) v;
                yield new org.domi.furnace.api.math.Rotations(r.getX(), r.getY(), r.getZ());
            }
            case "block_pos" -> ((net.minecraft.core.BlockPos) v).furnace();
            case "optional_block_pos" -> ((Optional<?>) v).map(p -> ((net.minecraft.core.BlockPos) p).furnace());
            case "direction" -> org.domi.furnace.api.world.Direction.valueOf(((net.minecraft.core.Direction) v).name());
            case "block_state" -> blockData(v, at);
            case "optional_block_state" -> ((Optional<?>) v).map(s -> blockData(s, at));
            case "compound_tag" -> ((net.minecraft.nbt.CompoundTag) v).furnace();
            case "pose" -> org.domi.furnace.api.entity.Pose.byId(((net.minecraft.world.entity.Pose) v).id());
            case "sniffer_state" -> ((net.minecraft.world.entity.animal.sniffer.Sniffer.State) v).name().toLowerCase(Locale.ROOT);
            case "armadillo_state" -> ((net.minecraft.world.entity.animal.armadillo.Armadillo.ArmadilloState) v).name().toLowerCase(Locale.ROOT);
            default -> throw new UnsupportedNmsCall("set_entity_data(" + name + ")", "이 메타데이터 값 종류는 아직 shim 에서 Furnace 로 바꾸지 못한다");
        };
    }

    private static org.domi.furnace.api.world.BlockData blockData(Object s, String at) {
        int id = ((net.minecraft.world.level.block.state.BlockState) s).furnaceStateId();
        return org.domi.furnace.api.Furnace.server().blockTypes().byStateId(id)
                .orElseThrow(() -> new IllegalArgumentException(at + ": 모르는 블록 상태 번호 " + id));
    }

    // ------------------------------------------------------------------ Furnace → shim

    @SuppressWarnings({"unchecked", "rawtypes"})
    static net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket fromFurnace(ClientboundSetEntityDataPacket p) {
        List<SynchedEntityData.DataValue<?>> items = new ArrayList<>(p.entries().size());
        for (ClientboundSetEntityDataPacket.Entry e : p.entries()) {
            EntityDataType<?> type = e.value().dataType();
            if (type == null) throw new UnsupportedNmsCall("fromFurnace(set_entity_data)", "이 서버 버전이 읽지 못한 값이 있다(칸 " + e.index() + ")");
            EntityDataSerializer s = BY_NAME.get(type.name());
            if (s == null) throw new UnsupportedNmsCall("fromFurnace(set_entity_data." + type.name() + ")", "shim 에 이 메타데이터 값 종류가 없다");
            items.add(new SynchedEntityData.DataValue(e.index(), s, toShim(type.name(), e.value().asObject(), "set_entity_data[" + e.index() + "]")));
        }
        return new net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket(p.entityId(), items);
    }

    private static Object toShim(String name, Object v, String at) {
        return switch (name) {
            case "byte", "int", "long", "float", "string", "boolean", "optional_uuid", "optional_unsigned_int" -> v;
            case "component" -> net.minecraft.network.chat.Component.fromFurnace((org.domi.furnace.api.text.Text) v);
            case "optional_component" -> ((Optional<?>) v).map(t -> net.minecraft.network.chat.Component.fromFurnace((org.domi.furnace.api.text.Text) t));
            case "item_stack" -> SchemaPacketReverse.itemStack(v, at);
            case "rotations" -> {
                var r = (org.domi.furnace.api.math.Rotations) v;
                yield new net.minecraft.core.Rotations(r.x(), r.y(), r.z());
            }
            case "block_pos" -> net.minecraft.core.BlockPos.fromFurnace((org.domi.furnace.api.world.BlockPos) v);
            case "optional_block_pos" -> ((Optional<?>) v).map(p -> net.minecraft.core.BlockPos.fromFurnace((org.domi.furnace.api.world.BlockPos) p));
            case "direction" -> net.minecraft.core.Direction.valueOf(((org.domi.furnace.api.world.Direction) v).name());
            case "block_state" -> SchemaPacketReverse.blockState(v, at);
            case "optional_block_state" -> ((Optional<?>) v).map(b -> SchemaPacketReverse.blockState(b, at));
            case "compound_tag" -> net.minecraft.nbt.CompoundTag.fromFurnace((org.domi.furnace.api.nbt.NbtCompound) v);
            case "pose" -> net.minecraft.world.entity.Pose.byId(((org.domi.furnace.api.entity.Pose) v).id());
            case "sniffer_state" -> net.minecraft.world.entity.animal.sniffer.Sniffer.State.valueOf(((String) v).toUpperCase(Locale.ROOT));
            case "armadillo_state" -> net.minecraft.world.entity.animal.armadillo.Armadillo.ArmadilloState.valueOf(((String) v).toUpperCase(Locale.ROOT));
            default -> throw new UnsupportedNmsCall("fromFurnace(set_entity_data." + name + ")", "이 메타데이터 값 종류는 아직 shim 으로 되돌리지 못한다");
        };
    }

    /** 두 방향 모두 되는 값 종류 이름. */
    private static final java.util.Set<String> BRIDGED = java.util.Set.of("byte", "int", "long", "float", "string", "boolean", "optional_uuid", "optional_unsigned_int", "component", "optional_component",
                "item_stack", "rotations", "block_pos", "optional_block_pos", "direction", "block_state", "optional_block_state", "compound_tag", "pose",
                "sniffer_state", "armadillo_state");

    /** 시험용: 두 방향 모두 되는 값 종류 이름. */
    static List<String> bridgedTypes() { return List.copyOf(new java.util.TreeSet<>(BRIDGED)); }

    /** Furnace → shim 으로 못 바꾸는 첫 값 종류 이름(모두 되면 null, 서버가 읽지 못한 값은 "unknown"). 값은 건드리지 않는다. */
    static String unbridgedKind(ClientboundSetEntityDataPacket p) {
        for (ClientboundSetEntityDataPacket.Entry e : p.entries()) {
            EntityDataType<?> type = e.value().dataType();
            if (type == null) return "unknown";
            if (!BRIDGED.contains(type.name()) || !BY_NAME.containsKey(type.name())) return type.name();
        }
        return null;
    }
}
