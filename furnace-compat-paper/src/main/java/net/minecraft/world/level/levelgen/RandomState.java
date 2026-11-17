package net.minecraft.world.level.levelgen;

/**
 * 생성 난수 상태(Mojang 이름 shim, T-211 2단계) — {@code ServerChunkCache#randomState()}. 값은 그 월드 생성기의 노이즈·난수 상태를 가리키는 손잡이다:
 * 구조물 생성이 받은 상태가 그 월드 것인지 확인하는 데 쓴다. 노이즈 읽기(router·sampler)는 아직 없다.
 */
public final class RandomState {

    private final org.domi.furnace.api.world.World world;

    /** 호환 모듈 전용. */
    public RandomState(org.domi.furnace.api.world.World world) { this.world = world; }

    /** 이 상태를 가진 월드(호환 모듈 전용). */
    public org.domi.furnace.api.world.World furnaceWorld() { return world; }
}
