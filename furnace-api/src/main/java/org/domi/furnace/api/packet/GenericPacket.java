// 타입 레코드가 없는 패킷 종류의 값. 이름 붙은 필드로 읽고, 바꾸고, 보낸다(PacketTypes 의 GenericPacket 종류).
//
//   packets.listen(this, PacketTypes.Clientbound.GAME_EVENT, e -> {
//       if (e.packet().get(EVENT).equals(1)) e.setCancelled(true);              // 비 시작을 숨긴다
//   });
//   player.sendPacket(GenericPacket.of(PacketTypes.Clientbound.SET_CAMERA).with("camera_id", npcId));
//
// 필드 이름은 바닐라(Mojang) 필드 이름을 snake_case 로 바꾼 것이다. 순서는 바닐라 필드 순서다.
// 비트 묶음이나 조건부 와이어인 종류만 와이어 순서를 따른다. 비트 묶음은 Boolean 필드 여럿으로, 조건부 부분은 Optional<PacketStruct> 로 나온다.
// 종류마다 어떤 필드가 있는지는 PacketService.fields(type) 로 안다.
//
// 필드 값은 다음 종류로 읽고 쓴다(버전 중립).
//   - int·long·short·byte·float·double·boolean 은 Integer·Long·Short·Byte·Float·Double·Boolean 이다.
//     바닐라 필드에 담긴 단위 그대로다(회전은 1/256 바퀴 byte, 속도는 1/8000 블록/틱 정수). 단위를 바꾼 편의 값은 타입 레코드가 준다.
//   - String·UUID 는 그대로 String·UUID 다. Component 는 Text 다.
//   - ResourceLocation·ResourceKey 는 이름 문자열이다("minecraft:overworld").
//   - 아이템·블록·엔티티 종류·메뉴 종류·효과 같은 레지스트리 값도 이름 문자열이다("minecraft:stone").
//   - BlockPos·GlobalPos 는 그대로 BlockPos·GlobalPos 다. BlockState 는 BlockData, ItemStack 은 ItemStack, CompoundTag 는 NbtCompound 다.
//   - enum 은 바닐라 상수 이름 문자열이다("MAIN_HAND").
//   - byte[]·int[]·long[] 는 배열이고, List·Set 은 List 다. Optional 과 없을 수 있는 값은 Optional 이다.
//   - 패킷 안에 내포된 구조는 PacketStruct 다.
//   - ChunkPos·SectionPos 는 바닐라가 쓰는 묶은 long 이다.
//   - Vec3 은 PacketStruct{x, y, z}(Double) 다.
//   - NumberFormat 은 PacketStruct{type:"minecraft:blank"|"minecraft:styled"(+ style NbtCompound)|"minecraft:fixed"(+ value Text)} 다.
//
// 종류마다 모양이 다른 값(number_format, boss_event 의 operation)은 type 필드가 나머지 필드를 정한다.
//
// readable() 이 false 면 서버가 이 버전에서 내용을 풀지 못하는 종류다(손으로 다뤄야 하는 와이어 등). 관찰과 취소만 된다.
// 어떤 종류가 읽히는지는 PacketService.isReadable(type) 로 안다.
package org.domi.furnace.api.packet;

import java.util.List;
import java.util.Objects;

public final class GenericPacket implements ClientboundPacket, ServerboundPacket {

    private final PacketType<GenericPacket> type;
    private final PacketStruct fields;   // null = 내용을 모른다

    private GenericPacket(PacketType<GenericPacket> type, PacketStruct fields) {
        this.type = Objects.requireNonNull(type, "type");
        if (type.packetClass() != GenericPacket.class) {
            throw new IllegalArgumentException(type + " is handled as a typed record (" + type.packetClass().getSimpleName() + ")");
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
        if (fields == null) throw new IllegalStateException(type + " has content that cannot be read in this server version (observe and cancel only)");
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
    public String toString() { return "GenericPacket[" + type + (fields == null ? ", unreadable" : ", " + fields) + "]"; }
}
