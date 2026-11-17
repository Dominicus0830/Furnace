// GenericPacket 과 PacketStruct 필드의 타입 있는 키. 이름과 값 종류를 함께 가진다.
// 같은 필드를 여러 곳에서 읽는다면 상수로 두고 쓴다.
//
//   static final PacketField<Integer> CAMERA_ID = PacketField.of("camera_id", Integer.class);
//
// 종류마다 어떤 필드가 있는지는 PacketService.fields(type) 로 알 수 있다.
package org.domi.furnace.api.packet;

import java.util.Objects;

public record PacketField<T>(String name, Class<T> valueClass) {

    public PacketField {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(valueClass, "valueClass");
        if (!PacketStruct.VALUE_KINDS.contains(valueClass)) {
            throw new IllegalArgumentException("not a field value kind: " + valueClass.getName() + " (allowed kinds are listed in the GenericPacket class documentation)");
        }
    }

    public static <T> PacketField<T> of(String name, Class<T> valueClass) { return new PacketField<>(name, valueClass); }

    @Override
    public String toString() { return name + ": " + valueClass.getSimpleName(); }
}
