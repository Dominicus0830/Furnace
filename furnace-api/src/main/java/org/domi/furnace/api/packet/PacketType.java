// 패킷 종류 — 방향과 이름(바닐라 패킷 이름, 예: minecraft:system_chat)으로 정해진다. 숫자 id 는 버전마다 달라서 드러내지 않는다.
// 상수는 PacketTypes 에 있다. 같은 종류는 언제나 같은 객체라 == 로 비교해도 된다.
package org.domi.furnace.api.packet;

import java.util.Objects;

public final class PacketType<P extends Packet> {

    private final PacketDirection direction;
    private final String name;
    private final Class<P> packetClass;

    PacketType(PacketDirection direction, String name, Class<P> packetClass) {
        this.direction = Objects.requireNonNull(direction, "direction");
        this.name = Objects.requireNonNull(name, "name");
        this.packetClass = Objects.requireNonNull(packetClass, "packetClass");
    }

    public PacketDirection direction() { return direction; }

    /** "minecraft:system_chat" 같은 이름. 방향마다 따로 매겨진다(custom_payload 는 양쪽에 있다). */
    public String name() { return name; }

    /** 이 종류의 값 클래스. 타입 레코드가 없으면 GenericPacket. */
    public Class<P> packetClass() { return packetClass; }

    /** 타입 레코드가 있는가(없으면 GenericPacket — 이름 붙은 필드로 다룬다). */
    public boolean typed() { return packetClass != GenericPacket.class; }

    @Override
    public String toString() { return direction.name().toLowerCase(java.util.Locale.ROOT) + "/" + name; }
}
