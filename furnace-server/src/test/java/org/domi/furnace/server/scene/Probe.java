// 장면 틱을 재는 쪽 끼움점. 장면은 틱 하나(장면 틱 = 월드 틱 한 번)마다 before → 틱 → after 를 부른다.
// 시험 모드는 NONE(아무것도 안 함), 벤치 모드는 SceneLibraryBench 가 nanoTime·할당 바이트를 잰다.
package org.domi.furnace.server.scene;

public interface Probe {
    Probe NONE = new Probe() {};

    default void before(int tick) {}

    default void after(int tick) {}
}
