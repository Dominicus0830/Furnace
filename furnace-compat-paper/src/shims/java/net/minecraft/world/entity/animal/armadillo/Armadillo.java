package net.minecraft.world.entity.animal.armadillo;

/** 아르마딜로(Mojang 이름 shim): 메타데이터 값 종류 자리인 상태 enum 만. */
class Armadillo {
    // 상수마다 몸통(바닐라처럼 $1~4 가 상수의 클래스 — T-261). shouldHideInShell 등은 아직 동작 없음(껍데기 멤버로 합쳐진다)
    // 직렬화 이름은 바닐라 1.21.1 정적 초기화(javap)와 같다. 그 밖의 상수 값(위협·애니메이션 길이·번호)은 T-262 몫
    public enum ArmadilloState implements net.minecraft.util.StringRepresentable {
        IDLE("idle") {}, ROLLING("rolling") {}, SCARED("scared") {}, UNROLLING("unrolling") {};

        private final String name;

        ArmadilloState(String name) { this.name = name; }

        @Override
        public String getSerializedName() { return name; }
    }
}
