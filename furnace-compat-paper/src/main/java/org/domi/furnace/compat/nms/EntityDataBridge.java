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
 * <p>아직 못 하는 종류: particle·particles(입자 shim 이 빈 껍데기),
 * optional_global_pos. vector3·quaternion 은 JOML 값(서버 라이브러리 묶음)과 Furnace 불변 레코드 사이를 성분 그대로 옮긴다.
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
            if (name == null) throw new IllegalArgumentException(at + ": slot " + dv.id() + " has a value type missing from the vanilla serializer table: " + dv.serializer());
            EntityDataType<?> type = EntityDataType.byName(name);
            if (type == null) throw new UnsupportedNmsCall("set_entity_data(" + name + ")", "metadata value kind that Furnace does not have");
            String vat = at + "[" + dv.id() + "]";
            out.add(new ClientboundSetEntityDataPacket.Entry(dv.id(), value(type, toApi(name, dv.value(), vat))));
        }
        return new ClientboundSetEntityDataPacket(id, out);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ClientboundSetEntityDataPacket.Value value(EntityDataType type, Object v) {
        return ClientboundSetEntityDataPacket.Value.of(type, v);
    }

    static Object toApi(String name, Object v, String at) {
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
            case "particles" -> emptyParticles(v, "set_entity_data(particles)");   // 살아 있는 엔티티의 효과 입자 칸: 빈 목록만(입자 shim 은 설계 D4)
            case "villager_data" -> villagerData((net.minecraft.world.entity.npc.VillagerData) v);
            case "cat_variant" -> NmsVariants.name(NmsVariants.cats(), cast(v), at);
            case "frog_variant" -> NmsVariants.name(NmsVariants.frogs(), cast(v), at);
            case "wolf_variant" -> NmsVariants.name(NmsVariants.wolves(), cast(v), at);
            case "painting_variant" -> NmsVariants.name(NmsVariants.paintings(), cast(v), at);
            case "vector3" -> { org.joml.Vector3f f = (org.joml.Vector3f) v; yield new org.domi.furnace.api.math.Vector3f(f.x, f.y, f.z); }
            case "quaternion" -> { org.joml.Quaternionf q = (org.joml.Quaternionf) v; yield new org.domi.furnace.api.math.Quaternionf(q.x, q.y, q.z, q.w); }
            default -> throw new UnsupportedNmsCall("set_entity_data(" + name + ")", "this metadata value kind cannot be converted from the shim to Furnace yet");
        };
    }

    private static org.domi.furnace.api.world.BlockData blockData(Object s, String at) {
        int id = ((net.minecraft.world.level.block.state.BlockState) s).furnaceStateId();
        return org.domi.furnace.api.Furnace.server().blockTypes().byStateId(id)
                .orElseThrow(() -> new IllegalArgumentException(at + ": unknown block state id " + id));
    }

    // ------------------------------------------------------------------ Furnace → shim

    @SuppressWarnings({"unchecked", "rawtypes"})
    static net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket fromFurnace(ClientboundSetEntityDataPacket p) {
        List<SynchedEntityData.DataValue<?>> items = new ArrayList<>(p.entries().size());
        for (ClientboundSetEntityDataPacket.Entry e : p.entries()) {
            EntityDataType<?> type = e.value().dataType();
            if (type == null) throw new UnsupportedNmsCall("fromFurnace(set_entity_data)", "a value this server version could not read (index " + e.index() + ")");
            EntityDataSerializer s = BY_NAME.get(type.name());
            if (s == null) throw new UnsupportedNmsCall("fromFurnace(set_entity_data." + type.name() + ")", "the shim has no such metadata value kind");
            items.add(new SynchedEntityData.DataValue(e.index(), s, toShim(type.name(), e.value().asObject(), "set_entity_data[" + e.index() + "]")));
        }
        return new net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket(p.entityId(), items);
    }

    static Object toShim(String name, Object v, String at) {
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
            case "particles" -> emptyParticles(v, "fromFurnace(set_entity_data.particles)");
            case "villager_data" -> villagerData((org.domi.furnace.api.entity.data.VillagerData) v);
            case "cat_variant" -> NmsVariants.holder(NmsVariants.cats(), (String) v, at);
            case "frog_variant" -> NmsVariants.holder(NmsVariants.frogs(), (String) v, at);
            case "wolf_variant" -> NmsVariants.holder(NmsVariants.wolves(), (String) v, at);
            case "painting_variant" -> NmsVariants.holder(NmsVariants.paintings(), (String) v, at);
            case "vector3" -> { org.domi.furnace.api.math.Vector3f f = (org.domi.furnace.api.math.Vector3f) v; yield new org.joml.Vector3f(f.x(), f.y(), f.z()); }
            case "quaternion" -> { org.domi.furnace.api.math.Quaternionf q = (org.domi.furnace.api.math.Quaternionf) v; yield new org.joml.Quaternionf(q.x(), q.y(), q.z(), q.w()); }
            default -> throw new UnsupportedNmsCall("fromFurnace(set_entity_data." + name + ")", "this metadata value kind cannot be converted back to the shim yet");
        };
    }

    /**
     * 주민 데이터: 종류·직업은 레지스트리 이름으로 잇는다. 등록되지 않은 값(플러그인이 만든 직업 record)은 바닐라 DefaultedMappedRegistry 처럼
     * 기본값(plains·none)으로 — 바닐라 STREAM_CODEC 도 레지스트리 번호로 써서 같은 값이 클라이언트에 간다.
     */
    private static org.domi.furnace.api.entity.data.VillagerData villagerData(net.minecraft.world.entity.npc.VillagerData d) {
        return new org.domi.furnace.api.entity.data.VillagerData(
                net.minecraft.core.registries.BuiltInRegistries.VILLAGER_TYPE.getKey(d.getType()).toString(),
                net.minecraft.core.registries.BuiltInRegistries.VILLAGER_PROFESSION.getKey(d.getProfession()).toString(), d.getLevel());
    }

    private static net.minecraft.world.entity.npc.VillagerData villagerData(org.domi.furnace.api.entity.data.VillagerData d) {
        return new net.minecraft.world.entity.npc.VillagerData(
                net.minecraft.core.registries.BuiltInRegistries.VILLAGER_TYPE.get(net.minecraft.resources.ResourceLocation.parse(d.type())),
                net.minecraft.core.registries.BuiltInRegistries.VILLAGER_PROFESSION.get(net.minecraft.resources.ResourceLocation.parse(d.profession())), d.level());
    }

    @SuppressWarnings("unchecked")
    private static <T> net.minecraft.core.Holder<T> cast(Object v) { return (net.minecraft.core.Holder<T>) v; }

    /** 입자 목록은 빈 것만 바꾼다(빈 목록은 두 쪽 모두 같은 값). 입자가 든 목록은 입자 shim 이 생길 때까지 UnsupportedNmsCall(설계 D4). */
    private static List<?> emptyParticles(Object v, String what) {
        if (!((List<?>) v).isEmpty()) throw new UnsupportedNmsCall(what, "particle lists that are not empty cannot be converted yet");
        return List.of();
    }

    /** 이름(소문자 상수 이름) → shim 직렬화 상수(없으면 null). */
    static EntityDataSerializer<?> serializer(String name) { return BY_NAME.get(name); }

    /** shim 직렬화 상수 → 이름(없으면 null). */
    static String name(EntityDataSerializer<?> s) { return NAMES.get(s); }

    /** 두 방향 모두 되는 값 종류 이름. */
    private static final java.util.Set<String> BRIDGED = java.util.Set.of("byte", "int", "long", "float", "string", "boolean", "optional_uuid", "optional_unsigned_int", "component", "optional_component",
                "item_stack", "rotations", "block_pos", "optional_block_pos", "direction", "block_state", "optional_block_state", "compound_tag", "pose",
                "sniffer_state", "armadillo_state", "villager_data", "cat_variant", "frog_variant", "wolf_variant", "painting_variant",
                "vector3", "quaternion");

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
