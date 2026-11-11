package net.minecraft.util;

/** 직렬화 이름을 가진 값(Mojang 이름 shim, N0). 바닐라의 코덱 도우미(fromEnum 등)는 DataFixerUpper 가 서버에 없어 두지 않았다. */
public interface StringRepresentable {

    String getSerializedName();
}
