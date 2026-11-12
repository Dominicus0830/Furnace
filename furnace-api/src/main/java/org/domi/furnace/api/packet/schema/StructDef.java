// 패킷 클래스에 딸린 보조 타입: 패킷 안에 내포된 타입(record·class·interface·enum, 이름에 '$')이나
// 같은 패키지의 부모·보조 클래스(base 줄, 예: ClientboundMoveEntityPacket·CommonPlayerSpawnInfo).
package org.domi.furnace.api.packet.schema;

import java.util.List;
import java.util.Objects;

public record StructDef(String state, String javaPackage, String name, Kind kind, boolean privateAccess,
                        List<FieldDef> fields, List<String> constants) {

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

    /** 내포 타입이면 바깥 클래스 이름(패키지 뺀), 아니면 null. */
    public String outerName() {
        int i = name.lastIndexOf('$');
        return i < 0 ? null : name.substring(0, i);
    }
}
