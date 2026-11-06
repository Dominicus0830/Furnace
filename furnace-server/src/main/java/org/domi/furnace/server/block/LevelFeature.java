// 블록 동작이 월드에 놓는 지형 생성 기능(configured 또는 placed feature) 손잡이. 바닐라 Holder<ConfiguredFeature>/Holder<PlacedFeature> 대응.
package org.domi.furnace.server.block;

public interface LevelFeature {

    /** 하위 어디에도 미구현(Noop) 부분이 없어 바닐라와 같은 결과를 낼 수 있는가. 거짓이면 부르는 쪽은 아무것도 바꾸지 않는다. */
    boolean supported();
}
