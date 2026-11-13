// 패킷 하나의 모양: 상태·방향·와이어 이름·Mojang 클래스 이름·필드(바닐라 선언 순서).
// className 에 '$' 가 있으면 내포 패킷(ClientboundMoveEntityPacket$Pos). superType 은 extends= 값(없으면 null).
package org.domi.furnace.api.packet.schema;

import java.util.List;
import java.util.Objects;
import org.domi.furnace.api.packet.PacketDirection;

public record PacketDef(String state, PacketDirection direction, String wireName, String javaPackage, String className,
                        Kind kind, boolean publicConstructor, boolean custom, TypeRef superType, List<FieldDef> fields) {

    /** RECORD·CLASS 는 바닐라 클래스 모양, SINGLETON 은 필드 없이 INSTANCE 하나만 있는 패킷. */
    public enum Kind { RECORD, CLASS, SINGLETON }

    public PacketDef {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(wireName, "wireName");
        Objects.requireNonNull(javaPackage, "javaPackage");
        Objects.requireNonNull(className, "className");
        Objects.requireNonNull(kind, "kind");
        fields = List.copyOf(fields);
    }

    /** 패키지를 붙인 '$' 이름(Class.forName 에 쓰는 이름). */
    public String binaryName() { return javaPackage + "." + className; }

    /** 내포 패킷이면 바깥 클래스 이름(패키지 뺀), 아니면 null. */
    public String outerName() {
        int i = className.lastIndexOf('$');
        return i < 0 ? null : className.substring(0, i);
    }
}
