// 서버 AABB ↔ API Aabb 바꾸기.
package org.domi.furnace.server.plugin;

import org.domi.furnace.api.world.Aabb;
import org.domi.furnace.server.entity.AABB;

final class ApiBoxes {

    private ApiBoxes() {}

    static Aabb of(AABB b) { return new Aabb(b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ()); }

    static AABB of(Aabb b) { return new AABB(b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ()); }
}
