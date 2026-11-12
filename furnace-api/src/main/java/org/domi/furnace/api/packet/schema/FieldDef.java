// 패킷·내포 타입의 필드 하나. 순서는 바닐라 클래스의 필드 선언 순서다.
// wire 는 스키마에 적힌 명시 와이어(없으면 null) — 실제 와이어는 PacketSchema.wireOf 로 얻는다.
// accessor 는 class 모양 타입의 접근자 이름(없으면 null). record 는 이름이 곧 접근자다.
package org.domi.furnace.api.packet.schema;

import java.util.Objects;

public record FieldDef(TypeRef type, String name, String wire, boolean nullable, String accessor) {

    public FieldDef {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(name, "name");
    }
}
