package net.minecraft.world.level.block.state.properties;

/** 블록 상태 속성 값(Mojang 이름 shim, N0). 상수 이름·순서·직렬화 이름은 바닐라 1.21.1 바이트코드의 정적 초기화와 같다. toString 은 바닐라처럼 덮어쓰지 않는다(상수 이름). */
enum AttachFace {

    /** 직렬화 이름(바닐라 1.21.1 정적 초기화의 상수 인자, 서수 순서). */
    private static final String[] NAMES = {
        "floor",
        "wall",
        "ceiling"
    };

    @Override
    public String getSerializedName() { return NAMES[ordinal()]; }
}
