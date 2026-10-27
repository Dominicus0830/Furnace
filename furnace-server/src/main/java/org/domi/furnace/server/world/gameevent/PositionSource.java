// 바닐라 PositionSource: 리스너의 현재 위치(BlockPositionSource = 칸 가운데, EntityPositionSource = 엔티티 위치 + 높이 보정).
package org.domi.furnace.server.world.gameevent;

import java.util.Optional;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.Entity;
import org.domi.furnace.server.entity.Vec3;

public interface PositionSource {

    Optional<Vec3> getPosition(ChunkLevel level);

    /** VibrationParticleOption 용 위치 소스 표현(알 수 없는 소스면 null). */
    default org.domi.furnace.server.net.protocol.play.ParticleOptions.Vibration toVibration(int arrivalTicks) { return null; }

    record BlockSource(Pos pos) implements PositionSource {
        @Override public Optional<Vec3> getPosition(ChunkLevel level) { return Optional.of(new Vec3(pos.x() + 0.5, pos.y() + 0.5, pos.z() + 0.5)); }
        @Override public org.domi.furnace.server.net.protocol.play.ParticleOptions.Vibration toVibration(int t) {
            return org.domi.furnace.server.net.protocol.play.ParticleOptions.Vibration.toBlock(pos.x(), pos.y(), pos.z(), t);
        }
    }

    record EntitySource(Entity e, float yOffset) implements PositionSource {
        @Override public Optional<Vec3> getPosition(ChunkLevel level) {
            return e.isRemoved() ? Optional.empty() : Optional.of(new Vec3(e.x(), e.y() + yOffset, e.z()));
        }
        @Override public org.domi.furnace.server.net.protocol.play.ParticleOptions.Vibration toVibration(int t) {
            return org.domi.furnace.server.net.protocol.play.ParticleOptions.Vibration.toEntity(e.id(), yOffset, t);
        }
    }

    static PositionSource block(Pos pos) { return new BlockSource(pos); }

    /** EntityPositionSource(entity, yOffset): 지워진 엔티티면 비어 있다. */
    static PositionSource entity(Entity e, float yOffset) { return new EntitySource(e, yOffset); }
}
