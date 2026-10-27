// /tp·/spreadplayers 가 공유하는 옮기기: 플레이어는 연결의 보정 텔레포트 경로(Player.teleportTo)로, 일반 엔티티는 그 엔티티를 가진
// region 스레드에서 직접 옮긴다. 같은 차원 안에서만 옮긴다(차원 사이 이동은 아직 없다).
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

    /** 바닐라에 없는 제약: 다른 차원으로 옮기는 길이 아직 없다. */
    static final SimpleCommandExceptionType ERROR_CROSS_DIMENSION =
            new SimpleCommandExceptionType(Text.text("Teleporting between dimensions is not supported yet"));

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

    /**
     * 대상을 (x, y, z) 로 옮기고 회전을 맞춘다(바닐라 TeleportCommand.performTeleport 의 같은 차원 경로). 일은 대상의 주인 스레드로 보낸다.
     * 플레이어: 포스트 텔레포트 티켓(5틱), 탈것에서 내리고, 자던 중이면 깨운 뒤 연결의 보정 텔레포트.
     * 그 밖: 위치·회전, 승객 자리, 수평 이동만 남기고(y 속도 0) 땅에 선 상태로, 길 찾기 중단.
     */
    static void teleport(CommandSource source, Rider target, double x, double y, double z, float yaw, float pitch) {
        World world = worldOf(source, target);
        if (target instanceof Player p) {
            world.addTicket((int) Math.floor(x) >> 4, (int) Math.floor(z) >> 4, 1, 5);
            world.runAt(p.x(), p.z(), region -> {
                if (p.vehicle() != null) p.stopRiding();
                if (p.isSleeping()) PlayerSleeping.stopSleepInBed(p, region.level(), true, true);
                p.teleportTo(x, y, z, yaw, pitch);
            });
        } else if (target instanceof Entity en) {
            org.domi.furnace.server.command.EntityOps.runOnOwner(source, en, e -> {
                if (e.isRemoved()) return;
                e.teleportSameLevel(x, y, z, yaw, pitch);
                if (e instanceof LivingEntity le) le.setYHeadRot(yaw);
                e.setDeltaMovement(e.vx(), 0.0, e.vz());
                e.setOnGround(true);
                if (e instanceof PathfinderMob m) m.getNavigation().stop();
            }, () -> { });
        }
    }
}
