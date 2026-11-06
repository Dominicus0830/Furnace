// 서버가 내는 수명주기 훅 목록. 데이터 주도 등록(몹 변종·마법부여·바이옴·차원 타입)은 이 목록에 훅을 더하는 식으로 붙는다.
package org.domi.furnace.api.plugin.lifecycle;

public final class LifecycleEvents {

    private LifecycleEvents() {}

    /**
     * 데이터 팩 찾기. 서버 시작 때(레지스트리·데이터 팩을 읽기 전)와 /reload·/datapack 이 팩을 다시 찾을 때마다 불린다.
     * 여기서 알린 팩은 월드 datapacks/ 의 팩과 똑같이 다뤄진다: 처음 보이면 자동으로 켜지고, /datapack disable 로 끄면
     * level.dat 에 꺼진 것으로 남는다.
     */
    public static final LifecycleEventType<DatapackRegistrar> DATAPACK_DISCOVERY =
            new LifecycleEventType<>("furnace:datapack_discovery", DatapackRegistrar.class);
}
