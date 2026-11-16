// 패킷 클래스에 딸린 보조 타입. 다음 두 가지가 있다.
//   패킷 안에 내포된 타입(record·class·interface·enum). 이름에 '$' 가 들어간다.
//   같은 패키지의 부모 클래스나 보조 클래스(base 줄). 예: ClientboundMoveEntityPacket, CommonPlayerSpawnInfo
package org.domi.furnace.api.packet.schema;

import java.util.List;
import java.util.Objects;

// publicConstructor 는 class 보조 타입의 바닐라에 모든 필드를 받는 public 생성자가 있는지(new 플래그)를 나타낸다. 없으면 private 이고, abstract 는 protected 다.
public record StructDef(String state, String javaPackage, String name, Kind kind, boolean privateAccess,
                        List<FieldDef> fields, List<String> constants, boolean publicConstructor) {

    public enum Kind { CLASS, ABSTRACT, INTERFACE, RECORD, ENUM }

    public StructDef {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(javaPackage, "javaPackage");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(kind, "kind");
        fields = List.copyOf(fields);
        constants = List.copyOf(constants);
    }

    /** 패키지를 붙인 '$' 이름. */
    public String binaryName() { return javaPackage + "." + name; }

    /** 내포 타입이면 패키지를 뺀 바깥 클래스 이름, 아니면 null. */
    public String outerName() {
        int i = name.lastIndexOf('$');
        return i < 0 ? null : name.substring(0, i);
    }
}
