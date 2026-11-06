// 바닐라 WallClimberNavigation: 거미처럼 벽을 오르는 몹의 길찾기 — 경로가 끝났어도 목표 칸으로 직접 다가간다.
package org.domi.furnace.server.entity.ai.navigation;

import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.Mob;
import org.domi.furnace.server.entity.Targetable;

public class WallClimberNavigation extends GroundPathNavigation {
    private Pos pathToPosition;

    public WallClimberNavigation(Mob mob, ChunkLevel level) { super(mob, level); }

    @Override
    public Path createPath(Pos target, int accuracy) {
        pathToPosition = target;
        return super.createPath(target, accuracy);
    }

    @Override
    public Path createPath(Targetable target, int accuracy) {
        pathToPosition = target.blockPosition();
        return super.createPath(target, accuracy);
    }

    @Override
    public boolean moveTo(Targetable target, double speed) {
        Path p = createPath(target, 0);
        if (p != null) return moveTo(p, speed);
        pathToPosition = target.blockPosition();
        speedModifier = speed;
        return true;
    }

    private boolean closerToCenterThan(Pos p, double ex, double ey, double ez, double dist) {
        double dx = p.x() + 0.5 - ex, dy = p.y() + 0.5 - ey, dz = p.z() + 0.5 - ez;
        return dx * dx + dy * dy + dz * dz < dist * dist;
    }

    @Override
    public void tick() {
        if (!isDone()) {
            super.tick();
        } else if (pathToPosition != null) {
            double w = mob.bbWidth();
            if (!closerToCenterThan(pathToPosition, mob.x(), mob.y(), mob.z(), w)
                    && (!(mob.y() > pathToPosition.y()) || !closerToCenterThan(new Pos(pathToPosition.x(), (int) Math.floor(mob.y()), pathToPosition.z()), mob.x(), mob.y(), mob.z(), w))) {
                mob.getMoveControl().setWantedPosition(pathToPosition.x(), pathToPosition.y(), pathToPosition.z(), speedModifier);
            } else {
                pathToPosition = null;
            }
        }
    }
}
