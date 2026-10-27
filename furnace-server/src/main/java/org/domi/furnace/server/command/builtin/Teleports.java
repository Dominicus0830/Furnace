// /tp·/spreadplayers 가 공유하는 옮기기: 플레이어는 연결의 보정 텔레포트 경로(Player.teleportTo)로, 일반 엔티티는 그 엔티티를 가진
// region 스레드에서 직접 옮긴다. 목적 차원이 다르면 차원 이동 경로(플레이어는 연결의 차원 변경, 엔티티는 목적 region 에 새로 만들기)로.
package org.domi.furnace.server.command.builtin;

import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.command.brigadier.SimpleCommandExceptionType;
import org.domi.furnace.server.entity.Entity;
import org.domi.furnace.server.entity.LivingEntity;
import org.domi.furnace.server.entity.PathfinderMob;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.entity.PlayerSleeping;
import org.domi.furnace.server.entity.Rider;
import org.domi.furnace.server.world.World;

final class Teleports {
    private Teleports() { }

    /** Level.isInSpawnableBounds(BlockPos.containing(x, y, z)). */
    static boolean inSpawnableBounds(double x, double y, double z) {
        int bx = (int) Math.floor(x), by = (int) Math.floor(y), bz = (int) Math.floor(z);
        return by >= -20000000 && by < 20000000 && bx >= -30000000 && bz >= -30000000 && bx < 30000000 && bz < 30000000;
    }

    /** Mth.wrapDegrees(float). */
    static float wrapDegrees(float v) { return org.domi.furnace.server.entity.ai.control.Mth.wrapDegrees(v); }

    /**
     * Entity.lookAt(anchor=발, 목표): 목표 점을 바라보는 {yaw, pitch}. 바라보는 쪽의 위치는 옮긴 뒤의 (x, y, z) 다.
     * Mth.atan2 는 바닐라의 표 기반 근사식이다.
     */
    static float[] lookAt(double x, double y, double z, double[] target) {
        double dx = target[0] - x, dy = target[1] - y, dz = target[2] - z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float pitch = wrapDegrees((float) -(org.domi.furnace.server.entity.ai.control.Mth.atan2(dy, horizontal) * 180.0F / (float) Math.PI));
        float yaw = wrapDegrees((float) (org.domi.furnace.server.entity.ai.control.Mth.atan2(dz, dx) * 180.0F / (float) Math.PI) - 90.0F);
        return new float[] {yaw, pitch};
    }

    static World worldOf(CommandSource source, Rider r) {
        World w = source.getServer().world(r.dimensionKey());
        return w != null ? w : source.level();
    }

    /** 대상의 지금 차원 안에서 옮긴다(teleport(source, target, 대상 차원, …)). */
    static java.util.concurrent.CompletableFuture<Void> teleport(CommandSource source, Rider target, double x, double y, double z, float yaw, float pitch) {
        return teleport(source, target, target.dimensionKey(), x, y, z, yaw, pitch);
    }

    /**
     * 대상을 dimension 의 (x, y, z) 로 옮기고 회전을 맞춘다(바닐라 TeleportCommand.performTeleport → Entity.teleportTo(level, …)).
     * 일은 대상의 주인 스레드로 보낸다.
     * 플레이어: 목적 레벨에 포스트 텔레포트 티켓(5틱), 탈것에서 내리고, 자던 중이면 깨운 뒤 같은 차원이면 연결의 보정 텔레포트,
     * 다른 차원이면 차원 변경(속도 0, 도착 뒤 처리 없음).
     * 그 밖, 같은 차원: 위치·회전, 승객 자리, 수평 이동만 남기고(y 속도 0) 땅에 선 상태로, 길 찾기 중단.
     * 그 밖, 다른 차원: 승객을 내리고 목적 차원에 새로 만든다(속도 그대로 — 바닐라는 속도·땅·길 찾기 손질을 옛 엔티티에 한다).
     * 일이 끝나면(주인을 못 찾아 건너뛴 경우도) 완료되는 future 를 돌려준다.
     */
    static java.util.concurrent.CompletableFuture<Void> teleport(CommandSource source, Rider target, String dimension,
                                                                 double x, double y, double z, float yaw, float pitch) {
        var done = new java.util.concurrent.CompletableFuture<Void>();
        World world = worldOf(source, target);
        World dest = source.getServer().world(dimension);
        boolean across = dest != null && dest != world;
        if (target instanceof Player p) {
            (across ? dest : world).addTicket((int) Math.floor(x) >> 4, (int) Math.floor(z) >> 4, 1, 5);
            teleportPlayer(source, p, dimension, x, y, z, yaw, pitch, done);
        } else if (target instanceof Entity en) {
            org.domi.furnace.server.command.EntityOps.runOnOwner(source, en, e -> {
                try {
                    if (e.isRemoved()) {
                        done.complete(null);
                    } else if (across) {
                        if (!e.teleportToDimension(dimension, x, y, z, yaw, pitch, () -> done.complete(null))) done.complete(null);
                    } else {
                        e.teleportSameLevel(x, y, z, yaw, pitch);
                        if (e instanceof LivingEntity le) le.setYHeadRot(yaw);
                        e.setDeltaMovement(e.vx(), 0.0, e.vz());
                        e.setOnGround(true);
                        if (e instanceof PathfinderMob m) m.getNavigation().stop();
                        done.complete(null);
                    }
                } catch (Throwable t) {
                    done.completeExceptionally(t);
                }
            }, () -> done.complete(null));
        } else {
            done.complete(null);
        }
        return done;
    }

    /**
     * 플레이어를 가진 region 에서(소유를 먼저 확인한 뒤에만 상태를 바꾼다) 옮긴다. 지금 차원이 목적 차원과 다르면 그 region 에서 떼어 보낸다.
     * 주인을 끝내 못 찾으면(접속 끊김·차원 이동 중) 실패로 끝낸다(명령 오류).
     */
    private static void teleportPlayer(CommandSource source, Player p, String dimension, double x, double y, double z, float yaw, float pitch,
                                       java.util.concurrent.CompletableFuture<Void> done) {
        org.domi.furnace.server.command.EntityOps.runOnPlayerOwner(source, p, region -> {
            try {
                if (p.vehicle() != null) p.stopRiding();
                if (p.isSleeping()) PlayerSleeping.stopSleepInBed(p, region.level(), true, true);
                if (p.dimension().equals(dimension) || source.getServer().world(dimension) == null) {
                    p.teleportTo(x, y, z, yaw, pitch);
                    done.complete(null);
                } else if (!region.teleportAcross(p, dimension, x, y, z, yaw, pitch, () -> done.complete(null))) {
                    done.completeExceptionally(org.domi.furnace.server.command.EntityOps.ownerNotFound(p));   // 방금 확인했으니 일어나지 않는다
                }
            } catch (Throwable t) {
                done.completeExceptionally(t);
            }
        }, () -> done.completeExceptionally(org.domi.furnace.server.command.EntityOps.ownerNotFound(p)));
    }
}
