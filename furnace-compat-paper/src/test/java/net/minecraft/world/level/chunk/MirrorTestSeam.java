package net.minecraft.world.level.chunk;

import java.util.function.Consumer;

/** 시험 전용: 거울 맞추기의 "계획 뒤·들여오기 전" 이음매를 다른 패키지의 시험이 걸게 한다(FurnaceChunkMirror 는 패키지 전용). */
public final class MirrorTestSeam {

    private MirrorTestSeam() {}

    /** 이음매를 건다(null 이면 뗀다). 주인 스레드에서 맞추기마다 불린다 — 대상 청크는 부르는 쪽이 가린다. */
    public static void afterPlan(Consumer<ChunkAccess> hook) { FurnaceChunkMirror.testAfterPlan = hook; }
}
