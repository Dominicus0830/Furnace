package net.minecraft.world;

/**
 * 바닐라 이름 붙은 난수 순서 저장소(Mojang 이름, T-203). Furnace 는 /random 의 순서를 서버 안에 두고 이 객체로 비추지 않는다 —
 * 레벨이 이것을 들고 다니는 모양(플러그인이 만든 레벨에 넘기기)만 있다.
 */
public final class RandomSequences {

    private final long worldSeed;

    public RandomSequences(long worldSeed) { this.worldSeed = worldSeed; }

    /** 만들 때 받은 월드 시드. */
    public long worldSeed() { return worldSeed; }
}
