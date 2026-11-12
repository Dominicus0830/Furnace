// 패킷 필드에 나오는 바깥 타입 선언(스키마의 type 줄).
// binaryName 은 내포 타입을 '$' 로 쓴 이름(net.minecraft.world.BossEvent$BossBarColor), typeParameters 는 선언된 타입 변수 이름.
package org.domi.furnace.api.packet.schema;

import java.util.List;
import java.util.Objects;

public record TypeDecl(Kind kind, String binaryName, List<String> typeParameters, String wire, List<String> constants) {

    /** 선언 종류. LIB = Minecraft 밖 Mojang 라이브러리 타입(authlib·DFU·fastutil 등). */
    public enum Kind { CLASS, ABSTRACT, INTERFACE, RECORD, ENUM, LIB }

    public TypeDecl {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(binaryName, "binaryName");
        typeParameters = List.copyOf(typeParameters);
        constants = List.copyOf(constants);
    }

    /** 스키마 필드에서 부르는 이름: 패키지를 뗀 이름, 내포는 '.' 로(BossEvent.BossBarColor). */
    public String simpleName() {
        String s = binaryName.substring(binaryName.lastIndexOf('.') + 1);
        return s.replace('$', '.');
    }

    /** 패키지 이름. */
    public String packageName() {
        int i = binaryName.lastIndexOf('.');
        return i < 0 ? "" : binaryName.substring(0, i);
    }
}
