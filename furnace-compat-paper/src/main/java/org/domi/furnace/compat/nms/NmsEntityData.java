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

    /** 서버 빈 레벨에서 그 종류를 만들어 ops 를 재생한 상태 — 뒤의 조작은 {@link #refresh} 로 하나씩만 재생한다. */
    public static ChunkInternals.DetachedEntityData open(String kind, List<ChunkInternals.EntityOp> ops) {
        return NmsChunks.bridge().openDetachedEntityData(kind, List.copyOf(ops));
    }

    /** 처음 꺼낼 때: 칸마다 DataItem(처음 값 = 정의 값, 값·더러움 = 서버 재생 뒤). */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static SynchedEntityData build(SyncedDataHolder holder, String kind, ChunkInternals.DetachedEntityData session) {
        List<ChunkInternals.EntityDataSlot> slots = session.slots();
        SynchedEntityData data = new SynchedEntityData(holder, items(kind, slots));
        assign(data, kind, slots);
        return data;
    }

    /** 칸마다 DataItem(처음 값 = 정의 값). 값은 뒤의 assign 이 채운다. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static SynchedEntityData.DataItem<?>[] items(String kind, List<ChunkInternals.EntityDataSlot> slots) {
        SynchedEntityData.DataItem<?>[] items = new SynchedEntityData.DataItem<?>[slots.size()];
        for (ChunkInternals.EntityDataSlot s : slots) {
            String name = s.definedValue().dataType().name();
            EntityDataSerializer ser = EntityDataBridge.serializer(name);
            if (ser == null) throw new UnsupportedNmsCall("Entity#getEntityData(" + kind + ")", "the shim has no " + name + " metadata value kind");
            String at = "Entity#getEntityData(" + kind + ")[" + s.index() + "]";
            items[s.index()] = new SynchedEntityData.DataItem(new EntityDataAccessor(s.index(), ser), EntityDataBridge.toShim(name, s.definedValue().asObject(), at));
        }
        return items;
    }

    /** 조작이 하나 붙은 뒤: 그 조작만 서버에서 재생하고 같은 객체의 값·더러움을 다시 채운다. */
    public static void refresh(SynchedEntityData data, String kind, ChunkInternals.DetachedEntityData session, ChunkInternals.EntityOp op) {
        assign(data, kind, session.apply(op));
    }

    static void assign(SynchedEntityData data, String kind, List<ChunkInternals.EntityDataSlot> slots) {
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

    /**
     * 바닐라 칸 상수({@code Goat.DATA_HAS_LEFT_HORN} 등, 엔티티 데이터 설계 D2)의 값: 그 클래스·상수 이름의 지문으로 칸 번호표(#565
     * {@code entity_data/ids-<판>.txt} 의 A 줄)에서 칸 번호·값 종류를 찾는다. 명세(entity-data.spec)가 생성 필드 초기값으로 부른다.
     * 표에 없거나 shim 에 그 값 종류가 없으면 IllegalStateException(그 클래스 초기화가 실패한다 — 조용히 틀린 번호를 주지 않는다).
     */
    @SuppressWarnings("unchecked")
    public static <T> EntityDataAccessor<T> accessor(Class<?> owner, String name) {
        String[] row = ids().get(owner.getName() + " " + fingerprint(owner.getName(), name));
        if (row == null) throw new IllegalStateException("no data slot in the entity data id table for " + owner.getName() + "." + name);
        EntityDataSerializer<?> ser = EntityDataBridge.serializer(row[1].toLowerCase(java.util.Locale.ROOT));
        if (ser == null) throw new IllegalStateException("the shim has no " + row[1] + " serializer for " + owner.getName() + "." + name);
        return new EntityDataAccessor<>(Integer.parseInt(row[0]), (EntityDataSerializer<T>) ser);
    }

    private static volatile java.util.Map<String, String[]> ids;

    /** "클래스 지문" → {칸 번호, 직렬화 상수 이름}. */
    private static java.util.Map<String, String[]> ids() {
        var m = ids;
        if (m != null) return m;
        synchronized (NmsEntityData.class) {
            if (ids != null) return ids;
            String res = "entity_data/ids-" + org.domi.furnace.api.GameVersion.RESOURCE_TAG + ".txt";
            java.util.Map<String, String[]> out = new java.util.HashMap<>();
            try (var in = NmsEntityData.class.getClassLoader().getResourceAsStream(res)) {
                if (in == null) throw new IllegalStateException(res + " is missing");
                for (String l : new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).split("\n")) {
                    String[] p = l.trim().split(" ");
                    if (p.length == 5 && p[0].equals("A")) out.put(p[1] + " " + p[2], new String[] {p[3], p[4]});
                }
            } catch (java.io.IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
            return ids = out;
        }
    }

    /** 칸 번호표의 상수 지문: SHA-256(클래스 이진 이름 + "." + 상수 이름) 앞 5바이트(ExportEntityData 와 같다). */
    static String fingerprint(String cls, String constant) {
        try {
            byte[] h = java.security.MessageDigest.getInstance("SHA-256").digest((cls + "." + constant).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < 5; i++) b.append(String.format("%02x", h[i]));
            return b.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static final EntityDataAccessor<Byte> SHARED_FLAGS = new EntityDataAccessor<>(0, net.minecraft.network.syncher.EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<net.minecraft.world.entity.Pose> POSE = new EntityDataAccessor<>(6, net.minecraft.network.syncher.EntityDataSerializers.POSE);

    // ------------------------------------------------------------------ T-263 관용구(엔티티 데이터 설계 D3)
    // 바닐라에서 같은 모양으로 되풀이되는 칸 접근 몸통을 여기 한 번 구현한다. 연결 줄 {@code @idiom(entity-data-…, <칸 필드>[, <마스크>])} 이
    // 껍데기 멤버를 이리로 잇는다. 인자 순서: 받는 쪽(엔티티 shim), 줄의 값(칸 필드·마스크), 멤버 인자. 소유 검사는 연결 몸통이 먼저 부른다(NmsEntityOwner).
    // 칸 값은 getEntityData() 를 지난다 — 월드 밖 엔티티는 서버 재생 값, 월드 엔티티는 서버가 갱신하는 최근 사본({@link NmsWorldEntityData}, 설계 D5).

    /** {@code return this.entityData.get(<칸>)}. 반환은 칸의 값 그대로(연결 몸통이 멤버 반환형으로 바꾼다). */
    @org.domi.furnace.compat.nms.link.LinkIdiom(value = "entity-data-get", check = "read=owner", owner = NmsEntityOwner.class)
    public static Object get(Object self, EntityDataAccessor<?> slot) { return data(self).get(slot); }

    /** {@code this.entityData.set(<칸>, 값)}. 값 형식은 칸의 값 종류여야 한다(바닐라와 같이 형식 검사는 직렬화 쪽이 한다). */
    @SuppressWarnings("unchecked")
    @org.domi.furnace.compat.nms.link.LinkIdiom(value = "entity-data-set", check = "write", owner = NmsEntityOwner.class)
    public static void set(Object self, EntityDataAccessor<?> slot, Object value) { data(self).set((EntityDataAccessor<Object>) slot, value); }

    /** {@code return (this.entityData.get(<바이트 칸>) & <마스크>) != 0}. */
    @org.domi.furnace.compat.nms.link.LinkIdiom(value = "entity-data-flag-get", check = "read=owner", owner = NmsEntityOwner.class)
    public static boolean flag(Object self, EntityDataAccessor<Byte> slot, int mask) { return (data(self).get(slot) & mask) != 0; }

    /** 바이트 칸의 깃발 하나 켜고 끄기: 켜면 {@code (byte)(b | 마스크)}, 끄면 {@code (byte)(b & ~마스크)} 를 칸에 쓴다(나머지 비트는 그대로). */
    @org.domi.furnace.compat.nms.link.LinkIdiom(value = "entity-data-flag-set", check = "write", owner = NmsEntityOwner.class)
    public static void setFlag(Object self, EntityDataAccessor<Byte> slot, int mask, boolean value) {
        SynchedEntityData d = data(self);
        // 월드 엔티티: 주인 스레드의 한 번의 조작(읽고 비트 쓰기)이라 다른 스레드의 쓰기와 엇갈려도 비트를 잃지 않는다(D5)
        if (d instanceof NmsWorldEntityData w) {
            w.setFlag(slot, mask, value);
            return;
        }
        // 월드 밖 엔티티는 한 스레드(만든 쪽)라 읽고-고치고-쓰기 두 호출로 충분하다
        byte b = d.get(slot);
        d.set(slot, value ? (byte) (b | mask) : (byte) (b & ~mask));
    }

    private static SynchedEntityData data(Object self) {
        if (!(self instanceof net.minecraft.world.entity.Entity e)) throw new IllegalArgumentException("entity data idiom: not an entity shim: " + self);
        return e.getEntityData();
    }

    /** 공유 깃발 칸(Entity 0번). */
    public static byte sharedFlags(SynchedEntityData data) { return data.get(SHARED_FLAGS); }

    /** 자세 칸(Entity 6번)의 순번(furnaceLook 과 같은 번호). */
    public static int pose(SynchedEntityData data) { return data.get(POSE).ordinal(); }
}
