package net.minecraft.world.level.block;

/** 바닐라 Rotation(블록·구조물·걸이 엔티티 돌림). 상수(바닐라 순서)는 명세가 정하고 생성기가 머리 뒤에 쓴다. 전에는 패킷 생성기가 빈 열거형으로 채웠다. */
enum Rotation {

    /** 바닐라 getSerializedName: 데이터 팩·명령이 쓰는 이름. */
    public String getSerializedName() {
        return switch (this) {
            case NONE -> "none";
            case CLOCKWISE_90 -> "clockwise_90";
            case CLOCKWISE_180 -> "180";
            case COUNTERCLOCKWISE_90 -> "counterclockwise_90";
        };
    }
}
