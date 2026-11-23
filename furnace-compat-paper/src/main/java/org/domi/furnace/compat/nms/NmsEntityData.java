package org.domi.furnace.compat.nms;

import java.util.List;
import org.domi.furnace.api.entity.data.EntityDataType;
import org.domi.furnace.api.internal.ChunkInternals;
import org.domi.furnace.api.packet.clientbound.ClientboundSetEntityDataPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializer;
import net.minecraft.network.syncher.SyncedDataHolder;
import net.minecraft.network.syncher.SynchedEntityData;

/**
 * 아직 월드에 없는 NMS 엔티티의 칸(엔티티 데이터 설계 3.2). 칸 값은 서버가 빈 레벨에서 그 종류를 만들고 지금까지의 조작을 재생해 낸 것이다
 * ({@link ChunkInternals#detachedEntityData}) — 처음 값·setter·load 의 칸 효과를 shim 에 다시 적지 않는다. 값은 {@link EntityDataBridge} 로 바꾼다.
 * 아직 바꾸지 못하는 값 종류(입자·주민 정보·변형 등, 설계 D4)가 그 종류에 있으면 UnsupportedNmsCall.
 */
public final class NmsEntityData {

    private NmsEntityData() {}

    /** 처음 꺼낼 때: 칸마다 DataItem(처음 값 = 정의 값, 값·더러움 = 서버 재생 뒤). */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static SynchedEntityData build(SyncedDataHolder holder, String kind, List<ChunkInternals.EntityOp> ops) {
        List<ChunkInternals.EntityDataSlot> slots = NmsChunks.bridge().detachedEntityData(kind, List.copyOf(ops));
        SynchedEntityData.DataItem<?>[] items = new SynchedEntityData.DataItem<?>[slots.size()];
        for (ChunkInternals.EntityDataSlot s : slots) {
            String name = s.definedValue().dataType().name();
            EntityDataSerializer ser = EntityDataBridge.serializer(name);
            if (ser == null) throw new UnsupportedNmsCall("Entity#getEntityData(" + kind + ")", "the shim has no " + name + " metadata value kind");
            String at = "Entity#getEntityData(" + kind + ")[" + s.index() + "]";
            items[s.index()] = new SynchedEntityData.DataItem(new EntityDataAccessor(s.index(), ser), EntityDataBridge.toShim(name, s.definedValue().asObject(), at));
        }
        SynchedEntityData data = new SynchedEntityData(holder, items);
        assign(data, kind, slots);
        return data;
    }

    /** 조작이 붙은 뒤: 같은 객체의 값·더러움을 서버 재생 결과로 다시 채운다. */
    public static void refresh(SynchedEntityData data, String kind, List<ChunkInternals.EntityOp> ops) {
        assign(data, kind, NmsChunks.bridge().detachedEntityData(kind, List.copyOf(ops)));
    }

    private static void assign(SynchedEntityData data, String kind, List<ChunkInternals.EntityDataSlot> slots) {
        for (ChunkInternals.EntityDataSlot s : slots) {
            String at = "Entity#getEntityData(" + kind + ")[" + s.index() + "]";
            data.furnaceAssign(s.index(), EntityDataBridge.toShim(s.value().dataType().name(), s.value().asObject(), at), s.dirty());
        }
    }

    /** {@code SynchedEntityData#set} 하나를 넣을 때 재생할 조작으로(값은 API 값 종류로 바꾼다). */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static ChunkInternals.EntityOp setOp(EntityDataAccessor<?> accessor, Object value) {
        String name = EntityDataBridge.name(accessor.serializer());
        EntityDataType type = name == null ? null : EntityDataType.byName(name);
        if (type == null) throw new UnsupportedNmsCall("SynchedEntityData#set", "metadata value kind that Furnace does not have: " + accessor.serializer());
        Object v = EntityDataBridge.toApi(name, value, "SynchedEntityData#set[" + accessor.id() + "]");
        return new ChunkInternals.EntityOp.SetData(accessor.id(), ClientboundSetEntityDataPacket.Value.of(type, v), true);
    }

    private static final EntityDataAccessor<Byte> SHARED_FLAGS = new EntityDataAccessor<>(0, net.minecraft.network.syncher.EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<net.minecraft.world.entity.Pose> POSE = new EntityDataAccessor<>(6, net.minecraft.network.syncher.EntityDataSerializers.POSE);

    /** 공유 깃발 칸(Entity 0번). */
    public static byte sharedFlags(SynchedEntityData data) { return data.get(SHARED_FLAGS); }

    /** 자세 칸(Entity 6번)의 순번(furnaceLook 과 같은 번호). */
    public static int pose(SynchedEntityData data) { return data.get(POSE).ordinal(); }
}
