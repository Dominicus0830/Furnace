package net.minecraft.world.level.levelgen;

import java.util.BitSet;
import java.util.Optional;
import net.minecraft.world.level.chunk.status.ChunkStatus;

/**
 * 1.18 이전 청크의 아래쪽(y &lt; 0) 다시 만들기 표시(Mojang 이름 shim): 목표 단계와 빠진 바닥돌 칸. 모양은 Paper 와 같다(생성자는 비공개, 칸 표는 BitSet).
 * Furnace 의 청크는 이것을 만들지 않는다({@code ChunkAccess#getBelowZeroRetrogen} 은 늘 null). 시험용 만들기는 {@link #furnaceCreate}.
 */
class BelowZeroRetrogen {

    private static final BitSet EMPTY = new BitSet(0);

    private final ChunkStatus targetStatus;
    private final BitSet missingBedrock;

    private BelowZeroRetrogen(ChunkStatus targetStatus, Optional<BitSet> missingBedrock) {
        this.targetStatus = targetStatus;
        this.missingBedrock = missingBedrock.orElse(EMPTY);
    }

    /** Furnace 전용: 바닐라는 NBT 읽기로만 만든다 — 호환 계층 시험이 값 객체를 만들 때 쓴다. */
    public static BelowZeroRetrogen furnaceCreate(ChunkStatus targetStatus, Optional<BitSet> missingBedrock) {
        return new BelowZeroRetrogen(java.util.Objects.requireNonNull(targetStatus, "targetStatus"), java.util.Objects.requireNonNull(missingBedrock, "missingBedrock"));
    }

    public ChunkStatus targetStatus() { return targetStatus; }

    public boolean hasBedrockHoles() { return !missingBedrock.isEmpty(); }

    /** 바닐라 hasBedrockHole: 청크 안 (x, z) 칸의 바닥돌이 빠졌는가. */
    public boolean hasBedrockHole(int x, int z) { return missingBedrock.get((z & 15) * 16 + (x & 15)); }
}
