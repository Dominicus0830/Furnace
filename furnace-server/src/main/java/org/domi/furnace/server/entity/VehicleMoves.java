// 바닐라 ServerGamePacketListenerImpl.handleMoveVehicle: 플레이어가 조종하는 뿌리 탈것의 위치를 클라이언트가 보낸다(클라이언트 권위).
// 서버는 너무 빠른 이동(틱 시작점에서 거리² − 속도² > 100)과 충돌로 어긋난 이동(move 뒤 0.25 넘게 차이 나고 원래 안 끼어 있었음)만 되돌린다.
// 탈것을 가진 region 스레드에서 부른다.
package org.domi.furnace.server.entity;

import org.domi.furnace.server.entity.ai.control.Mth;
import org.domi.furnace.server.net.protocol.play.MoveVehicle;

public final class VehicleMoves {

    private VehicleMoves() {}

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(VehicleMoves.class);

    /** 결과: 받아들임·무시(조종 중이 아님)·되돌림(MoveVehicle 을 보냈다). */
    public enum Result { ACCEPTED, IGNORED, REJECTED }

    /** 값이 NaN·무한이면 연결을 끊어야 한다(multiplayer.disconnect.invalid_vehicle_movement). */
    public static boolean invalid(double x, double y, double z, float yRot, float xRot) {
        return Double.isNaN(x) || Double.isNaN(y) || Double.isNaN(z) || !Float.isFinite(xRot) || !Float.isFinite(yRot);
    }

    public static Result handle(Player p, double px, double py, double pz, float pyRot, float pxRot) {
        Entity v = Entity.rootVehicleOf(p);
        if (v == null || v.getControllingPassenger() != p || v != p.lastVehicle) return Result.IGNORED;
        double ox = v.x(), oy = v.y(), oz = v.z();
        double tx = Math.clamp(px, -3.0E7, 3.0E7), ty = Math.clamp(py, -2.0E7, 2.0E7), tz = Math.clamp(pz, -3.0E7, 3.0E7);
        float yRot = Mth.wrapDegrees(pyRot), xRot = Mth.wrapDegrees(pxRot);
        double dx = tx - p.vehicleFirstGood[0], dy = ty - p.vehicleFirstGood[1], dz = tz - p.vehicleFirstGood[2];
        double speedSqr = v.vx() * v.vx() + v.vy() * v.vy() + v.vz() * v.vz();
        double distSqr = dx * dx + dy * dy + dz * dz;
        if (distSqr - speedSqr > 100.0) {
            log.warn("{} (vehicle of {}) moved too quickly! {},{},{}", v, p.name(), dx, dy, dz);
            p.send(MoveVehicle.of(v));
            return Result.REJECTED;
        }
        boolean wasFree = v.level().noCollision(v.boundingBox().deflate(0.0625));
        dx = tx - p.vehicleLastGood[0];
        dy = ty - p.vehicleLastGood[1] - 1.0E-6;
        dz = tz - p.vehicleLastGood[2];
        boolean wasOnGroundBelow = v.verticalCollisionBelow;
        if (v instanceof LivingEntity le && le.onClimbable()) le.resetFallDistance();
        v.move(Entity.MOVER_PLAYER, dx, dy, dz);
        dx = tx - v.x();
        dy = 0.0;   // 바닐라: if (dy > -0.5 || dy < 0.5) dy = 0 — 언제나 참
        dz = tz - v.z();
        distSqr = dx * dx + dy * dy + dz * dz;
        boolean wrong = false;
        if (distSqr > 0.0625) {
            wrong = true;
            log.warn("{} (vehicle of {}) moved wrongly! {}", v, p.name(), Math.sqrt(distSqr));
        }
        v.absMoveTo(tx, ty, tz, yRot, xRot);
        boolean nowFree = v.level().noCollision(v.boundingBox().deflate(0.0625));
        if (wasFree && (wrong || !nowFree)) {
            v.absMoveTo(ox, oy, oz, yRot, xRot);
            p.send(MoveVehicle.of(v));
            return Result.REJECTED;
        }
        // TODO(통계·비행 킥): checkMovementStatistics, clientVehicleIsFloating(비행 허용 아님·중력 있음·주변 블록 없음)
        p.clientVehicleIsFloating = false;
        p.vehicleLastGood[0] = v.x();
        p.vehicleLastGood[1] = v.y();
        p.vehicleLastGood[2] = v.z();
        return Result.ACCEPTED;
    }
}
