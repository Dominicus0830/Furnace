// 공용 장면 라이브러리(T-003)의 장면 하나. 한 장면의 같은 코드가 두 모드로 돈다(O15 4.3절):
//  - 증명 모드(PROOF): 평소 시험 SceneLibraryTest 가 돌려 금 파일(identity/<golden>.txt)과 비교한다.
//  - 벤치 모드(FULL): SceneLibraryBench(bench 작업)가 돌려 틱 시간·할당을 재고 해시를 JSON 에 남긴다.
// 구현은 public 이고 인자 없는 public 생성자가 있어야 한다(새 JVM 모드가 이름으로 만든다). 장면은 매번 새 상태에서 시작한다.
package org.domi.furnace.server.scene;

import org.domi.furnace.server.identity.SceneHash;

public interface LibraryScene extends SceneHash.Scene {

    /** 라이브러리 안의 이름(소문자·숫자·'-'). 벤치 JSON 파일 이름이기도 하다. */
    String name();

    /** 금 파일 이름(identity/<golden>.txt). */
    default String golden() { return "scene-" + name(); }

    /**
     * 엔티티가 든 장면은 true — 엔티티 id 카운터·정적 난수가 JVM 전역이라 같은 JVM 두 번째 실행이 달라진다(docs/testing/identitykit.md).
     * 새 JVM 에서 -Dfurnace.test.seeded=true 로 돈다. 블록만 다루는 장면은 false(같은 JVM 에서 돈다).
     */
    boolean freshJvm();

    /** 벤치 모드에서 Probe 가 재는 한 걸음의 단위("tick" — 월드 틱 한 번, "chunk" — 청크 하나 만들기). JSON 칸 이름에 쓰인다. */
    default String unit() { return "tick"; }

    /** 어떤 O-항목·최적화가 이 장면에 기대는지(문서·실패 메시지용). */
    String covers();

    /** 장면을 돌린다. rec 에 줄을 적고, 틱 장면이면 probe 로 틱마다 시간을 잴 수 있게 알린다. */
    void play(Size size, SceneHash.Recorder rec, Probe probe) throws Exception;

    /** SceneHash.Scene — 증명 크기, 측정 없음. */
    @Override
    default void run(SceneHash.Recorder rec) throws Exception { play(Size.PROOF, rec, Probe.NONE); }
}
