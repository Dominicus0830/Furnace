// 타입 레코드가 없는 패킷 종류의 값 — 이름 붙은 필드로 읽고, 바꾸고, 보낸다(PacketTypes 의 GenericPacket 종류).
//
//   packets.listen(this, PacketTypes.Clientbound.GAME_EVENT, e -> {
//       if (e.packet().get(EVENT).equals(1)) e.setCancelled(true);              // 비 시작을 숨긴다
//   });
//   player.sendPacket(GenericPacket.of(PacketTypes.Clientbound.SET_CAMERA).with("camera_id", npcId));
//
// 필드 이름은 바닐라(Mojang) 필드 이름을 snake_case 로 바꾼 것이고, 순서는 바닐라 필드 순서다. 종류마다 필드는 PacketService.fields(type).
// 값 종류(버전 중립):
//   int·long·short·byte·float·double·boolean 필드 → Integer·Long·Short·Byte·Float·Double·Boolean(바닐라 필드에 담긴 단위 그대로 —
//     회전은 1/256 바퀴 byte, 속도는 1/8000 블록/틱 정수. 단위를 바꾼 편의 값은 타입 레코드가 준다)
//   String·UUID → String·UUID, Component → Text, ResourceLocation·ResourceKey → 이름 문자열("minecraft:overworld"),
//   BlockPos → BlockPos, GlobalPos → GlobalPos, BlockState → BlockData, ItemStack → ItemStack, CompoundTag → NbtCompound,
//   아이템·블록·엔티티 종류·메뉴 종류·효과 등 레지스트리 값 → 이름 문자열("minecraft:stone"),
//   enum → 바닐라 상수 이름 문자열("MAIN_HAND"), byte[]·int[]·long[] → 배열, List·Set → List, Optional·없을 수 있는 값 → Optional,
//   패킷 안 내포 구조 → PacketStruct, ChunkPos·SectionPos → 바닐라가 쓰는 묶은 long.
//
// readable() 이 false 면 서버가 이 버전에서 내용을 풀지 못하는 종류다(손으로 다뤄야 하는 와이어 등) — 관찰·취소만 된다.
// 어떤 종류가 읽히는지는 PacketService.isReadable(type).
package org.domi.furnace.api.packet;

import java.util.List;
import java.util.Objects;

public final class GenericPacket implements ClientboundPacket, ServerboundPacket {

    private final PacketType<GenericPacket> type;
    private final PacketStruct fields;   // null = 내용을 모른다

    private GenericPacket(PacketType<GenericPacket> type, PacketStruct fields) {
        this.type = Objects.requireNonNull(type, "type");
        if (type.packetClass() != GenericPacket.class) {
            throw new IllegalArgumentException(type + " 은 타입 레코드(" + type.packetClass().getSimpleName() + ")로 다룬다");
        }
        this.fields = fields;
    }

    /** 빈 패킷 — with 로 필드를 모두 채워 보낸다(빠진 필드가 있으면 보낼 때 IllegalArgumentException). */
    public static GenericPacket of(PacketType<GenericPacket> type) { return new GenericPacket(type, PacketStruct.of()); }

    /** 필드를 담은 패킷. */
    public static GenericPacket of(PacketType<GenericPacket> type, PacketStruct fields) {
        return new GenericPacket(type, Objects.requireNonNull(fields, "fields"));
    }

    /** 내용을 모르는 패킷(서버가 이 종류를 풀지 못할 때 리스너가 받는 값). 보내거나 바꿀 수 없다. */
    public static GenericPacket unreadable(PacketType<GenericPacket> type) { return new GenericPacket(type, null); }

    @Override
    public PacketType<GenericPacket> type() { return type; }

    /** 내용을 읽을 수 있는가. false 면 아래 읽기·바꾸기는 IllegalStateException. */
    public boolean readable() { return fields != null; }

    /** 모든 필드. */
    public PacketStruct fields() {
        if (fields == null) throw new IllegalStateException(type + " 은 이 서버 버전에서 내용을 읽을 수 없다(관찰·취소만)");
        return fields;
    }

    /** 필드 이름(바닐라 순서). */
    public List<String> names() { return fields().names(); }

    public boolean has(String name) { return fields().has(name); }

    /** 필드 값. 없는 이름이면 IllegalArgumentException. */
    public Object get(String name) { return fields().get(name); }

    public <T> T get(PacketField<T> field) { return fields().get(field); }

    /** 그 필드만 바꾼 새 패킷. 이미 있는 필드면 같은 종류의 값이어야 한다. */
    public GenericPacket with(String name, Object value) { return new GenericPacket(type, fields().with(name, value)); }

    public <T> GenericPacket with(PacketField<T> field, T value) { return with(field.name(), value); }

    @Override
    public boolean equals(Object o) {
        return o instanceof GenericPacket g && g.type == type && Objects.equals(g.fields, fields);
    }

    @Override
    public int hashCode() { return type.hashCode() * 31 + Objects.hashCode(fields); }

    @Override
    public String toString() { return "GenericPacket[" + type + (fields == null ? ", 읽을 수 없음" : ", " + fields) + "]"; }
}
