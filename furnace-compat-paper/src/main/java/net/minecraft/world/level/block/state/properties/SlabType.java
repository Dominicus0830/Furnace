package net.minecraft.world.level.block.state.properties;

import net.minecraft.util.StringRepresentable;

/** 블록 상태 속성 값(Mojang 이름 shim, N0). 상수 이름·순서·직렬화 이름은 바닐라 1.21.1 바이트코드의 정적 초기화와 같다. toString 도 바닐라처럼 직렬화 이름. */
public enum SlabType implements StringRepresentable {
    TOP("top"),
    BOTTOM("bottom"),
    DOUBLE("double");

    private final String name;

    SlabType(String name) { this.name = name; }

    @Override
    public String getSerializedName() { return name; }

    @Override
    public String toString() { return name; }
}
