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

    /**
     * 게임 레지스트리 등록. 서버 시작 때 레지스트리를 고정하기 전에 한 번 불린다(데이터 팩을 읽기 전). 여기서 등록한 바이옴·차원 타입은
     * 클라이언트가 구성 단계에서 받는 레지스트리에 들어가고, 바이옴은 /fillbiome·/locate·태그·청크 저장에서 바닐라 바이옴처럼 쓰인다.
     * 몹 변종·그림·마법부여 등 데이터 주도 항목은 데이터 팩 파일과 함께 레지스트리에 들어간다.
     */
    public static final LifecycleEventType<GameRegistrar> GAME_REGISTRIES =
            new LifecycleEventType<>("furnace:game_registries", GameRegistrar.class);

    /**
     * 월드젠 단계 훅 등록(불만 표 40번). 서버 시작 때 게임 레지스트리 다음, 월드를 만들기 전에 한 번 불린다(핫 리로드로 다시 켜면 그 플러그인 것만 다시).
     * 여기서 등록한 키를 {@link org.domi.furnace.api.world.WorldCreator.Builder#generationHooks} 나 furnace.properties 의
     * {@code worldgen-hooks.overworld|the_nether|the_end} 가 고른다. 플러그인이 꺼지면 그 키는 비고, 그 월드의 새 청크는 바닐라로 생성된다
     * (Paper 에서 생성기 플러그인이 빠졌을 때와 같다).
     */
    public static final LifecycleEventType<WorldGenerationRegistrar> WORLD_GENERATION =
            new LifecycleEventType<>("furnace:world_generation", WorldGenerationRegistrar.class);
}
