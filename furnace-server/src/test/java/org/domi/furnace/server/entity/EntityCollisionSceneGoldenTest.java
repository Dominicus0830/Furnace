// T-018(P-O05-07): 충돌 수집 재작성 전후로 엔티티 장면 해시가 같아야 한다. 금 파일은 재작성 전 커밋(7b2d6b77)에서 만들었다.
package org.domi.furnace.server.entity;

import org.domi.furnace.server.identity.SceneHash;
import org.junit.jupiter.api.Test;

class EntityCollisionSceneGoldenTest {

    @Test
    void collisionSceneMatchesGolden() {
        SceneHash.assertGoldenInFreshJvm("entity-collision-scene", EntityCollisionScene.class);
    }
}
