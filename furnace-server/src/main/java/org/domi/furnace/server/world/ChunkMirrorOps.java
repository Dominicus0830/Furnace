// 청크에 붙은 어댑터 거울(N2b-4 — 플러그인이 고친 NMS 청크 shim)을 서버가 부르는 훅. 서버 핵심(ChunkColumn·Region)은 거울을 형 없이(Object) 들고
// 맞춤 지점마다 이 훅만 부른다. 구현은 다리(ChunkInternalsImpl)에 있고, 거울을 붙이는 것도 다리라 거울이 붙기 전에 꽂힌다.
package org.domi.furnace.server.world;

public interface ChunkMirrorOps {

    /** 맞추기: 거울의 바뀐 부분을 서버 청크로 들여온다. 맞추는 동안 들어온 쓰기가 남았으면 true. 주인 스레드. */
    boolean sync(Object mirror);

    /** 틱 끝 공개 뒤 알림(view 는 방금 낸 스냅샷). 주인 스레드. */
    void published(Object mirror, ChunkReadView view);

    /** 청크가 내려가기 직전(맞춘 뒤). 주인 스레드. */
    void unloaded(Object mirror);
}
