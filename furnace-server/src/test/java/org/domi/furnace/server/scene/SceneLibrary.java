// 공용 장면 라이브러리(T-003). 새 장면은 여기 한 줄을 더한다.
package org.domi.furnace.server.scene;

import java.util.List;

public final class SceneLibrary {
    private SceneLibrary() {}

    /** 모든 장면. 이름은 서로 달라야 한다(SceneLibraryTest 가 검사). */
    public static List<LibraryScene> all() {
        return List.of(new NoiseQuickScene(), new SharedPipelineScene(), new FluidScene(), new PistonScene(), new ExplosionScene(),
                new MobsScene(), new VillageScene(), new ChaseScene(), new FarmScene());
    }
}
