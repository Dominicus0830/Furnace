package net.minecraft.world.level.block.entity.vault;

/** 블록 상태 속성 값(Mojang 이름 shim, N0). 상수 이름·순서·직렬화 이름은 바닐라 1.21.1 바이트코드의 정적 초기화와 같다. toString 은 바닐라처럼 덮어쓰지 않는다(상수 이름).
 * 상수마다 몸통이 있다(바닐라처럼 VaultState$1~4 가 상수의 클래스 — T-261). 상태별 동작은 아직 없다(껍데기 멤버로 합쳐진다). */
enum VaultState {
    ;

    private final String name;

    VaultState(String name) { this.name = name; }

    @Override
    public String getSerializedName() { return name; }
}
