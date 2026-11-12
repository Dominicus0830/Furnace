// canBeCollidedWith 를 덮어쓰는 엔티티 종류는 HardCollider 표지를 달아야 한다 — ChunkLevel.entityCollisions 가 그 구역 목록만 훑기 때문.
// 또 canCollideWith 가 상대의 canBeCollidedWith 없이 참이 될 수 있는 종류(배·수레)는 collidesOnlyWithHardColliders 를 거짓으로 덮어써야 한다.
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class HardColliderTest {

    private static List<Class<?>> entityClasses() throws Exception {
        var url = Entity.class.getResource("Entity.class");
        assertNotNull(url);
        File dir = new File(url.toURI()).getParentFile();
        List<Class<?>> out = new ArrayList<>();
        for (String name : dir.list()) {
            if (!name.endsWith(".class")) continue;
            Class<?> c = Class.forName("org.domi.furnace.server.entity." + name.substring(0, name.length() - 6), false, Entity.class.getClassLoader());
            if (Entity.class.isAssignableFrom(c)) out.add(c);
        }
        assertTrue(out.size() > 50, "엔티티 클래스를 찾았다: " + out.size());
        return out;
    }

    private static boolean declares(Class<?> c, String method, Class<?>... params) {
        try {
            c.getDeclaredMethod(method, params);
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    @Test
    void canBeCollidedWithOverridesCarryMarker() throws Exception {
        for (Class<?> c : entityClasses()) {
            if (c != Entity.class && declares(c, "canBeCollidedWith")) {
                assertTrue(HardCollider.class.isAssignableFrom(c), c.getName() + " 는 canBeCollidedWith 를 덮어쓰니 HardCollider 를 달아야 한다");
            }
        }
        assertTrue(HardCollider.class.isAssignableFrom(Boat.class));
        assertTrue(HardCollider.class.isAssignableFrom(Shulker.class));
    }

    @Test
    void vehicleCollidersScanEverything() throws Exception {
        for (Class<?> c : entityClasses()) {
            if (c != Entity.class && declares(c, "canCollideWithPlayer", Player.class)) {
                assertTrue(declares(c, "collidesOnlyWithHardColliders"), c.getName() + " 는 플레이어(단단하지 않음)와 부딪히니 전체를 훑어야 한다");
            }
        }
    }
}
