package net.minecraft.world.level.levelgen;

import java.util.BitSet;
import java.util.Optional;
import net.minecraft.world.level.chunk.status.ChunkStatus;

/**
 * 1.18 이전 청크의 아래쪽(y &lt; 0) 다시 만들기 표시(Mojang 이름 shim): 목표 단계와 빠진 바닥돌 칸. 값 읽기만 있다.
 * Furnace 의 청크는 이것을 만들지 않는다({@code ChunkAccess#getBelowZeroRetrogen} 은 늘 null).
 */
public final class BelowZeroRetrogen {

    private final ChunkStatus targetStatus;
    private final Optional<BitSet> missingBedrock;

    public BelowZeroRetrogen(ChunkStatus targetStatus, Optional<BitSet> missingBedrock) {
        this.targetStatus = java.util.Objects.requireNonNull(targetStatus, "targetStatus");
        this.missingBedrock = java.util.Objects.requireNonNull(missingBedrock, "missingBedrock");
    }

    public ChunkStatus targetStatus() { return targetStatus; }

    public Optional<BitSet> missingBedrock() { return missingBedrock; }

    public boolean hasBedrockHoles() { return missingBedrock.isPresent(); }

    /** 바닐라 isBedrockHole: 청크 안 (x, z) 칸의 바닥돌이 빠졌는가. */
    public boolean isBedrockHole(int x, int z) { return missingBedrock.isPresent() && missingBedrock.get().get((z & 15) * 16 + (x & 15)); }
}
