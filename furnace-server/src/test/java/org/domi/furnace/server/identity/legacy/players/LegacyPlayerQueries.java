// LEGACY-ORACLE since c470effbd: ChunkLevel 플레이어 조회 5종(hasNearbyAlivePlayer, getNearestPlayer×3, forEachPlayerIn) — T-016 격자 색인 이전
// 옮길 때 바꾼 것은 클래스 밖에서 돌게 하는 부분뿐이다: services().forEachPlayer(…) → 넘겨받는 players(같은 순서로 훑는다),
// getNearestPlayer(Entity, …) 의 from → 좌표 셋. 거르기·거리 계산·엄격한 < 비교·훑는 순서는 그 커밋 그대로다.
package org.domi.furnace.server.identity.legacy.players;

import java.util.function.Consumer;
import org.domi.furnace.server.entity.AABB;
import org.domi.furnace.server.entity.Mob;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.entity.ai.targeting.TargetingConditions;
import org.domi.furnace.server.identity.LegacyOracle;
import org.domi.furnace.server.world.GameMode;

@LegacyOracle(since = "c470effbd",
        copyOf = "ChunkLevel#hasNearbyAlivePlayer, #getNearestPlayer(x,y,z,dist,ignoreCreative), #getNearestPlayer(Entity,dist), #forEachPlayerIn, #getNearestPlayer(conditions,source,x,y,z)")
public final class LegacyPlayerQueries {

    private LegacyPlayerQueries() {}

    public static boolean hasNearbyAlivePlayer(Iterable<Player> players, double x, double y, double z, double range) {
        boolean found = false;
        for (Player p : players) {
            if (found || p.isDisconnected() || p.isSpectator() || !p.isAlive()) continue;
            double dx = p.x() - x, dy = p.y() - y, dz = p.z() - z;
            if (dx * dx + dy * dy + dz * dz < range * range) found = true;
        }
        return found;
    }

    public static Player getNearestPlayer(Iterable<Player> players, double x, double y, double z, double dist, boolean ignoreCreative) {
        Player best = null;
        double bestD = dist < 0.0 ? Double.MAX_VALUE : dist * dist;
        for (Player p : players) {
            if (p.isDisconnected() || p.gameMode() == GameMode.SPECTATOR) continue;
            if (ignoreCreative && p.gameMode() == GameMode.CREATIVE) continue;
            double dx = p.x() - x, dy = p.y() - y, dz = p.z() - z;
            double d = dx * dx + dy * dy + dz * dz;
            if (d < bestD) {
                bestD = d;
                best = p;
            }
        }
        return best;
    }

    /** getNearestPlayer(Entity from, dist) — from 의 좌표. */
    public static Player getNearestPlayerFrom(Iterable<Player> players, double fx, double fy, double fz, double dist) {
        Player best = null;
        double bestD = dist < 0.0 ? Double.MAX_VALUE : dist * dist;
        for (Player p : players) {
            if (p.gameMode() == GameMode.SPECTATOR || p.isDisconnected()) continue;
            double dx = p.x() - fx, dy = p.y() - fy, dz = p.z() - fz;
            double d = dx * dx + dy * dy + dz * dz;
            if (d < bestD) {
                bestD = d;
                best = p;
            }
        }
        return best;
    }

    public static void forEachPlayerIn(Iterable<Player> players, AABB box, Consumer<Player> c) {
        for (Player p : players) if (!p.isDisconnected() && p.boundingBox().intersects(box)) c.accept(p);
    }

    public static Player getNearestPlayer(Iterable<Player> players, TargetingConditions conditions, Mob source, double x, double y, double z) {
        Player best = null;
        double bestD = -1.0;
        final double far = source == null ? Double.POSITIVE_INFINITY : conditions.farLimitSqr();
        for (Player p : players) {
            if (p.isDisconnected()) continue;
            if (far != Double.POSITIVE_INFINITY) {
                double fx = source.x() - p.x(), fy = source.y() - p.y(), fz = source.z() - p.z();
                if (fx * fx + fy * fy + fz * fz > far) continue;
            }
            if (!conditions.test(source, p)) continue;
            double dx = p.x() - x, dy = p.y() - y, dz = p.z() - z;
            double d = dx * dx + dy * dy + dz * dz;
            if (bestD == -1.0 || d < bestD) {
                bestD = d;
                best = p;
            }
        }
        return best;
    }
}
