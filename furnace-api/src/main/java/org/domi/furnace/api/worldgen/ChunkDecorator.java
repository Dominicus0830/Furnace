// 월드 장식 단계 끝에 도는 일(T-217) — Bukkit BlockPopulator#populate(WorldInfo, Random, x, z, LimitedRegion) 와 같은 자리.
// World#addChunkDecorator 로 단다. 생성 스레드 여러 개에서 동시에 불린다 — 스레드 안전해야 하고 서버 API(월드·엔티티·스케줄러)를 부르면 안 된다.
package org.domi.furnace.api.worldgen;

@FunctionalInterface
public interface ChunkDecorator {

    /** 원점 청크의 장식이 끝난 뒤(바닐라 장식·월드젠 훅 decorate 뒤). 둘레 3×3 청크에 쓸 수 있다. 예외는 기록하고 다음 장식으로 넘어간다. */
    void decorate(DecorationRegion region);
}
