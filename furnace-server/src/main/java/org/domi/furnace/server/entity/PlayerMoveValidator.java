// 바닐라 ServerGamePacketListenerImpl.handleMovePlayer(1.21.1)의 서버 검증: 너무 빠른 이동(moved too quickly), 충돌로 어긋난 이동(moved wrongly),
// 이동 후 블록에 끼는 위치 되돌리기(isPlayerCollidingWithBlocks), 떠 있는 틱 세기와 비행 킥(tick 의 clientIsFloating).
// 플레이어를 가진 region 스레드에서만 부른다. 연결(IO) 스레드는 패킷을 Player 의 검증 대기열에 넣기만 한다.
// 바닐라와 다른 점은 docs/feature-parity/context-notes.md 2026-10-31 항목.
package org.domi.furnace.server.entity;

import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.block.BlockInfo;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.ai.control.Mth;
import org.domi.furnace.server.world.GameMode;

public final class PlayerMoveValidator {
    private PlayerMoveValidator() {}

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(PlayerMoveValidator.class);

    /** 이동 패킷 하나(move_player_pos/pos_rot/rot/status_only 를 하나로). 없는 필드는 서버가 아는 현재 값을 쓴다. */
    public record Check(boolean hasPos, boolean hasRot, double x, double y, double z, float yaw, float pitch) {}

    /** ServerGamePacketListenerImpl 의 이동 검증 필드. */
    public static final class State {
        double firstGoodX, firstGoodY, firstGoodZ, lastGoodX, lastGoodY, lastGoodZ;
        boolean started;
        int receivedMovePacketCount, knownMovePacketCount;
        boolean clientIsFloating;
        int aboveGroundTickCount;
        /** 지난 서버 쪽 move 의 verticalCollisionBelow(패킷 처리 첫머리에서 읽는다). */
        boolean verticalCollisionBelow;

        /** resetPosition: 지금 위치를 처음·마지막 좋은 위치로. */
        void reset(Player p) {
            firstGoodX = lastGoodX = p.x();
            firstGoodY = lastGoodY = p.y();
            firstGoodZ = lastGoodZ = p.z();
        }

        public boolean clientIsFloating() { return clientIsFloating; }

        public int aboveGroundTickCount() { return aboveGroundTickCount; }
    }

    /**
     * 검증에 쓰는 몸통 높이. 서버 자세는 틱마다 한 번 갱신되어 클라이언트보다 한 틱 늦을 수 있다 — 수영·기어가기 자세이거나
     * 질주 중 물에 닿아 곧 수영 자세가 될 수 있으면 낮은 상자(0.6)로 본다(좁은 틈을 지나는 정상 이동을 거절하지 않기 위해, 가정).
     */
    static double height(Player p) {
        if (p.isSwimPose() || p.sprinting() && p.wasTouchingWater) return Math.min(p.bbHeight(), 0.6);
        return p.bbHeight();
    }

    static AABB box(Player p) {
        double hw = p.bbWidth() / 2.0;
        return new AABB(p.x() - hw, p.y(), p.z() - hw, p.x() + hw, p.y() + height(p), p.z() + hw);
    }

    private static double clampHorizontal(double v) { return Math.clamp(v, -3.0E7, 3.0E7); }

    private static double clampVertical(double v) { return Math.clamp(v, -2.0E7, 2.0E7); }

    /** containsInvalidValues: 좌표 NaN, 각도 비유한 — 연결을 끊는다(multiplayer.disconnect.invalid_player_movement). */
    public static boolean invalid(Check c) {
        return Double.isNaN(c.x()) || Double.isNaN(c.y()) || Double.isNaN(c.z()) || !Float.isFinite(c.pitch()) || !Float.isFinite(c.yaw());
    }

    /** 서버 쪽 deltaMovement 의 제곱 길이 근사: 겉날개 비행 중엔 굴리는 속도, 아니면 마지막으로 받아들인 변위(가정). */
    private static double deltaMovementLengthSqr(Player p) {
        double[] v = p.serverMovement();
        return v[0] * v[0] + v[1] * v[1] + v[2] * v[2];
    }

    /**
     * 틱 한 번의 머리(ServerGamePacketListenerImpl.tick): 위치 기준 재설정, 지난 틱까지 받은 패킷 수를 알려진 것으로 올리고,
     * 떠 있는 틱을 센다. 한 번 더 세어 최대 비행 틱을 넘기면 "multiplayer.disconnect.flying" 으로 킥한다.
     * 이 틱에서 위치가 확정된 뒤(드레인 뒤)에 부른다.
     */
    public static void tick(Player p) {
        State s = p.moveState;
        s.reset(p);
        s.started = true;
        s.knownMovePacketCount = s.receivedMovePacketCount;
        if (s.clientIsFloating && !p.isSleeping() && p.vehicle() == null && !p.vitals().isDeadOrDying()) {
            if (++s.aboveGroundTickCount > maximumFlyingTicks(p)) {
                log.warn("{} was kicked for floating too long!", p.name());
                p.kick(new org.domi.furnace.server.net.protocol.play.Disconnect(Text.translatable("multiplayer.disconnect.flying")));
            }
        } else {
            s.clientIsFloating = false;
            s.aboveGroundTickCount = 0;
        }
    }

    /** getMaximumFlyingTicks: 중력 속성이 0.08 보다 작을수록 길다. 0 에 가까우면 끝없이. */
    static int maximumFlyingTicks(Player p) {
        double g = PlayerAttributes.value(p, org.domi.furnace.server.attribute.Attribute.GRAVITY);
        if (g < 1.0E-5F) return Integer.MAX_VALUE;
        double e = 0.08 / g;
        double d = 80.0 * Math.max(e, 1.0);
        int i = (int) d;
        return d > i ? i + 1 : i;
    }

    /** 서버가 위치를 정했을 때(스폰·텔레포트·리스폰): 기준점을 그 자리로(연결 쪽 확인 응답이 lastGood 을 정하는 것과 같다). */
    public static void teleported(Player p, double x, double y, double z) {
        State s = p.moveState;
        s.firstGoodX = s.lastGoodX = x;
        s.firstGoodY = s.lastGoodY = y;
        s.firstGoodZ = s.lastGoodZ = z;
        s.started = true;
    }

    /**
     * handleMovePlayer(탄 것·자는 중이 아닌 경우). 받아들이면 플레이어 위치·회전·onGround 를 갱신하고 참, 되돌리면 서버 위치로 텔레포트시키고 거짓.
     */
    public static boolean handle(ChunkLevel level, Player p, Check c, boolean packetOnGround) {
        State s = p.moveState;
        if (!s.started) {   // tickCount == 0: 첫 패킷이 첫 틱보다 먼저 온 경우
            s.reset(p);
            s.started = true;
        }
        double d = clampHorizontal(c.hasPos() ? c.x() : p.x());
        double e = clampVertical(c.hasPos() ? c.y() : p.y());
        double f = clampHorizontal(c.hasPos() ? c.z() : p.z());
        float g = Mth.wrapDegrees(c.hasRot() ? c.yaw() : p.yaw());
        float h = Mth.wrapDegrees(c.hasRot() ? c.pitch() : p.pitch());
        double i = p.x(), j = p.y(), k = p.z();
        double l = d - s.firstGoodX, m = e - s.firstGoodY, n = f - s.firstGoodZ;
        double o = deltaMovementLengthSqr(p);
        double pp = l * l + m * m + n * n;
        boolean fallFlying = p.isFallFlying();
        if (level.tickRunsNormally()) {
            s.receivedMovePacketCount++;
            int q = s.receivedMovePacketCount - s.knownMovePacketCount;
            if (q > 5) {
                log.debug("{} is sending move packets too frequently ({} packets since last tick)", p.name(), q);
                q = 1;
            }
            if (!level.gameRule("disableElytraMovementCheck") || !fallFlying) {
                float r = fallFlying ? 300.0F : 100.0F;
                if (pp - o > (double) (r * (float) q)) {
                    log.warn("{} moved too quickly! {},{},{}", p.name(), l, m, n);
                    reject(p, i, j, k, p.yaw(), p.pitch());
                    return false;
                }
            }
        }
        AABB oldBox = box(p);
        l = d - s.lastGoodX;
        m = e - s.lastGoodY;
        n = f - s.lastGoodZ;
        boolean up = m > 0.0;
        boolean belowBefore = s.verticalCollisionBelow;
        GameMode mode = p.gameMode();
        boolean noPhysics = mode == GameMode.SPECTATOR;
        double[] mv = move(level, p, l, m, n, noPhysics);   // {x, y, z, verticalCollisionBelow(0/1)}
        s.verticalCollisionBelow = mv[3] != 0.0;
        l = d - mv[0];
        m = e - mv[1];
        m = 0.0;   // 바닐라: if (m > -0.5 || m < 0.5) m = 0 — 언제나 참이라 세로 차이는 보지 않는다
        n = f - mv[2];
        pp = l * l + m * m + n * n;
        boolean wrongly = false;
        if (pp > 0.0625 && mode != GameMode.CREATIVE && mode != GameMode.SPECTATOR) {
            wrongly = true;
            log.warn("{} moved wrongly!", p.name());
        }
        if (noPhysics || (!wrongly || !level.noCollision(oldBox)) && !isPlayerCollidingWithBlocks(level, p, oldBox, mv, d, e, f)) {
            p.applyMove(d, e, f, g, h, packetOnGround);
            boolean autoSpin = p.autoSpinAttackTicks > 0;
            // m 은 위에서 0 으로 만들었으므로 m >= -0.03125 는 언제나 참(바닐라 그대로)
            s.clientIsFloating = !belowBefore && mode != GameMode.SPECTATOR && !level.flightAllowed() && !p.vitals().mayfly()
                    && !p.vitals().hasEffect(org.domi.furnace.server.effect.MobEffect.LEVITATION) && !fallFlying && !autoSpin
                    && noBlocksAround(level, p);
            s.lastGoodX = p.x();
            s.lastGoodY = p.y();
            s.lastGoodZ = p.z();
            return true;
        }
        reject(p, i, j, k, g, h);
        return false;
    }

    /** teleport(x,y,z,yRot,xRot): 서버 위치를 되돌리고 연결에 텔레포트 패킷을 시킨다. 확인이 올 때까지 대기열의 검증 패킷은 버린다. */
    private static void reject(Player p, double x, double y, double z, float yaw, float pitch) {
        p.applyMove(x, y, z, yaw, pitch, p.onGround());
        if (p.hasTeleporter()) {
            p.moveRejectHold = true;
            p.teleportTo(x, y, z, yaw, pitch);   // 연결(IO 스레드)이 대기열을 비우며 hold 를 풀고 SyncPlayerPosition 을 보낸다
        }
    }

    /** noBlocksAround: 몸통을 0.0625 넓히고 아래로 0.55 늘린 상자 안이 전부 공기. */
    static boolean noBlocksAround(ChunkLevel level, Player p) {
        AABB b = p.boundingBox().inflate(0.0625).expandTowards(0.0, -0.55, 0.0);
        int x0 = Entity.floor(b.minX()), y0 = Entity.floor(b.minY()), z0 = Entity.floor(b.minZ());
        int x1 = Entity.floor(b.maxX()), y1 = Entity.floor(b.maxY()), z1 = Entity.floor(b.maxZ());
        for (int z = z0; z <= z1; z++) {
            for (int y = y0; y <= y1; y++) {
                for (int x = x0; x <= x1; x++) {
                    if (!BlockInfo.get().isAir(level.getBlockState(new Pos(x, y, z)))) return false;
                }
            }
        }
        return true;
    }

    /**
     * isPlayerCollidingWithBlocks: 클라이언트가 말한 자리의 몸통(1e-5 줄임)과 겹치는 블록 충돌 모양 중에,
     * 이동 전 몸통(1e-5 줄임)과는 겹치지 않는 것이 하나라도 있으면 참(= 새로 벽 속으로 들어갔다).
     */
    static boolean isPlayerCollidingWithBlocks(ChunkLevel level, Player p, AABB oldBox, double[] moved, double d, double e, double f) {
        double hw = p.bbWidth() / 2.0;
        AABB box = new AABB(moved[0] - hw, moved[1], moved[2] - hw, moved[0] + hw, moved[1] + height(p), moved[2] + hw)
                .move(d - moved[0], e - moved[1], f - moved[2]);
        AABB old = oldBox.deflate(1.0E-5F);
        for (var shape : level.blockCollisions(box.deflate(1.0E-5F))) {
            if (!shape.overlaps(old, 0.0, 0.0, 0.0)) return true;
        }
        return false;
    }

    /**
     * Entity.move(MoverType.PLAYER, delta) 중 위치·충돌에 관한 부분(플레이어가 서 있는 현재 위치 기준, 플레이어 상태는 바꾸지 않는다):
     * 관전자는 충돌 없이, 아니면 Player.maybeBackOffFromEdge(웅크리고 땅 위면 낭떠러지 쪽 이동을 0.05 씩 줄임) → 충돌(걸어 오르기 포함).
     * 거미줄 등의 stuckSpeedMultiplier 는 플레이어에게 아직 없다(가정: 그 안에서는 더 관대하다).
     * 결과 {x, y, z, verticalCollisionBelow}.
     */
    static double[] move(ChunkLevel level, Player p, double dx, double dy, double dz, boolean noPhysics) {
        double px = p.x(), py = p.y(), pz = p.z();
        if (noPhysics) return new double[] {px + dx, py + dy, pz + dz, 0.0};
        AABB box = box(p);
        float step = 0.6F;   // step_height 속성 기본값(플레이어에게 수정자 없음)
        boolean onGround = p.onGround();
        double[] back = maybeBackOffFromEdge(level, p, box, onGround, step, dx, dy, dz);
        dx = back[0];
        dz = back[2];
        double[] r = Entity.collideMove(level, box, onGround, step, dx, dy, dz);
        double lenSqr = r[0] * r[0] + r[1] * r[1] + r[2] * r[2];
        if (lenSqr > 1.0E-7) {
            px += r[0];
            py += r[1];
            pz += r[2];
        }
        boolean below = dy != r[1] && dy < 0.0;
        return new double[] {px, py, pz, below ? 1.0 : 0.0};
    }

    /** Player.maybeBackOffFromEdge. */
    static double[] maybeBackOffFromEdge(ChunkLevel level, Player p, AABB box, boolean onGround, float step, double mx, double my, double mz) {
        if (!p.vitals().isFlying() && !(my > 0.0) && p.sneaking() && isAboveGround(level, p, box, onGround, step)) {
            double d = mx, e = mz;
            double h = Math.signum(d) * 0.05;
            double i = Math.signum(e) * 0.05;
            while (d != 0.0 && canFallAtLeast(level, box, d, 0.0, step)) {
                if (Math.abs(d) <= 0.05) {
                    d = 0.0;
                    break;
                }
                d -= h;
            }
            while (e != 0.0 && canFallAtLeast(level, box, 0.0, e, step)) {
                if (Math.abs(e) <= 0.05) {
                    e = 0.0;
                    break;
                }
                e -= i;
            }
            while (d != 0.0 && e != 0.0 && canFallAtLeast(level, box, d, e, step)) {
                d = Math.abs(d) <= 0.05 ? 0.0 : d - h;
                e = Math.abs(e) <= 0.05 ? 0.0 : e - i;
            }
            return new double[] {d, my, e};
        }
        return new double[] {mx, my, mz};
    }

    /** isAboveGround(maxDownStep): 땅 위이거나, 떨어진 거리가 걸어 오르기 높이보다 짧고 그 아래(높이 − 떨어진 거리)에 받침이 있다. */
    private static boolean isAboveGround(ChunkLevel level, Player p, AABB box, boolean onGround, float step) {
        float fall = p.vitals().fallDistance();
        return onGround || fall < step && !canFallAtLeast(level, box, 0.0, 0.0, step - fall);
    }

    /** canFallAtLeast(dx, dz, distance): 발밑 얇은 상자(아래로 distance + 1e-5)에 충돌이 없다. */
    private static boolean canFallAtLeast(ChunkLevel level, AABB b, double x, double z, double distance) {
        return level.noCollision(new AABB(b.minX() + x, b.minY() - distance - 1.0E-5F, b.minZ() + z, b.maxX() + x, b.minY(), b.maxZ() + z));
    }
}
