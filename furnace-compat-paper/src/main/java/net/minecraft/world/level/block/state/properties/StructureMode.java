package net.minecraft.world.level.block.state.properties;

import net.minecraft.util.StringRepresentable;

/** 블록 상태 속성 값(Mojang 이름 shim, N0). 상수 이름·순서·직렬화 이름은 바닐라 1.21.1 바이트코드의 정적 초기화와 같다. toString 은 바닐라처럼 덮어쓰지 않는다(상수 이름). */
public enum StructureMode implements StringRepresentable {
    SAVE("save"),
    LOAD("load"),
    CORNER("corner"),
    DATA("data");

    private final String name;

    StructureMode(String name) { this.name = name; }

    @Override
    public String getSerializedName() { return name; }
}
