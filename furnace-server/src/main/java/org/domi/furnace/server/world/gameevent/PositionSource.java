// 바닐라 PositionSource: 리스너의 현재 위치(BlockPositionSource = 칸 가운데, EntityPositionSource = 엔티티 위치 + 높이 보정).
package org.domi.furnace.server.world.gameevent;

import java.util.Optional;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.Entity;
import org.domi.furnace.server.entity.Vec3;

public interface PositionSource {

    Optional<Vec3> getPosition(ChunkLevel level);

    static PositionSource block(Pos pos) {
        Vec3 v = new Vec3(pos.x() + 0.5, pos.y() + 0.5, pos.z() + 0.5);
        return level -> Optional.of(v);
    }

    /** EntityPositionSource(entity, yOffset): 지워진 엔티티면 비어 있다. */
    static PositionSource entity(Entity e, float yOffset) {
        return level -> e.isRemoved() ? Optional.empty() : Optional.of(new Vec3(e.x(), e.y() + yOffset, e.z()));
    }
}
