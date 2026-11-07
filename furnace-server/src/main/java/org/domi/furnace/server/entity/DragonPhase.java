// 바닐라 엔더 드래곤 단계(EnderDragonPhase + 각 DragonPhaseInstance). 서버 쪽 동작만 — 입자·소리 같은 doClientTick 은 클라이언트 몫.
// 난수는 dragon.random() 하나를 바닐라와 같은 순서로 쓴다.
package org.domi.furnace.server.entity;

import java.util.List;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.effect.MobEffect;
import org.domi.furnace.server.effect.MobEffectInstance;
import org.domi.furnace.server.entity.ai.control.Mth;
import org.domi.furnace.server.entity.ai.navigation.Node;
import org.domi.furnace.server.entity.ai.navigation.Path;
import org.domi.furnace.server.entity.ai.targeting.TargetingConditions;

public abstract class DragonPhase {
    /** EnderDragonPhase id(DATA_PHASE·저장 값). */
    public static final int HOLDING_PATTERN = 0, STRAFE_PLAYER = 1, LANDING_APPROACH = 2, LANDING = 3, TAKEOFF = 4,
            SITTING_FLAMING = 5, SITTING_SCANNING = 6, SITTING_ATTACKING = 7, CHARGING_PLAYER = 8, DYING = 9, HOVERING = 10;
    public static final int COUNT = 11;

    protected final EnderDragon dragon;

    protected DragonPhase(EnderDragon dragon) { this.dragon = dragon; }

    public abstract int id();

    public boolean isSitting() { return false; }

    public void doServerTick() {}

    public void onCrystalDestroyed(EndCrystal crystal, Pos pos, Entity.Damage source, Player player) {}

    public void begin() {}

    public void end() {}

    public float getFlySpeed() { return 0.6F; }

    public Vec3 getFlyTargetLocation() { return null; }

    /** onHurt: 받는 피해량을 바꾼다(기본 그대로). direct 는 직접 맞힌 엔티티(화살 등). */
    public float onHurt(Entity.Damage source, Entity direct, float amount) { return amount; }

    public float getTurnSpeed() {
        float f = (float) Math.sqrt(dragon.vx() * dragon.vx() + dragon.vz() * dragon.vz()) + 1.0F;
        float f1 = Math.min(f, 40.0F);
        return 0.7F / f1 / f;
    }

    public static DragonPhase create(EnderDragon d, int id) {
        return switch (id) {
            case HOLDING_PATTERN -> new HoldingPattern(d);
            case STRAFE_PLAYER -> new StrafePlayer(d);
            case LANDING_APPROACH -> new LandingApproach(d);
            case LANDING -> new Landing(d);
            case TAKEOFF -> new Takeoff(d);
            case SITTING_FLAMING -> new SittingFlaming(d);
            case SITTING_SCANNING -> new SittingScanning(d);
            case SITTING_ATTACKING -> new SittingAttacking(d);
            case CHARGING_PLAYER -> new ChargingPlayer(d);
            case DYING -> new Dying(d);
            default -> new Hover(d);
        };
    }

    // ------------------------------------------------------------------ 공통 도우미

    /** EndPodiumFeature.getLocation(origin) 칸의 높이 지도 위치(MOTION_BLOCKING_NO_LEAVES). */
    static Pos podiumTop(EnderDragon d) {
        Pos o = d.getFightOrigin();
        return new Pos(o.x(), d.level().getMotionBlockingNoLeavesHeight(o.x(), o.z()), o.z());
    }

    static double distToCenterSqr(Pos p, double x, double y, double z) {
        double dx = p.x() + 0.5 - x, dy = p.y() + 0.5 - y, dz = p.z() + 0.5 - z;
        return dx * dx + dy * dy + dz * dz;
    }

    static int crystalsAlive(EnderDragon d) { return d.getDragonFight() == null ? 0 : d.getDragonFight().getCrystalsAlive(); }

    /** 경로의 다음 칸으로: 높이는 그 칸 + [0, 20) (아래면 다시 뽑는다). */
    static Vec3 nextNodeTarget(EnderDragon d, Path path) {
        Pos p = path.getNextNodePos();
        path.advance();
        double y;
        do {
            y = (float) p.y() + d.random().nextFloat() * 20.0F;
        } while (y < p.y());
        return new Vec3(p.x(), y, p.z());
    }

    /** 앉은 단계 공통(AbstractDragonSittingPhase): 화살·돌풍구(WindCharge)는 불붙이고 무시. */
    abstract static class Sitting extends DragonPhase {
        Sitting(EnderDragon d) { super(d); }

        @Override public boolean isSitting() { return true; }

        @Override
        public float onHurt(Entity.Damage source, Entity direct, float amount) {
            if (direct instanceof AbstractArrow || direct instanceof WindCharge) {
                direct.igniteForSeconds(1.0F);
                return 0.0F;
            }
            return super.onHurt(source, direct, amount);
        }
    }

    // ------------------------------------------------------------------ 단계들

    /** DragonHoldingPatternPhase: 둘레 노드를 돌다 착륙 접근·플레이어 공격으로. */
    public static final class HoldingPattern extends DragonPhase {
        private static final TargetingConditions NEW_TARGET_TARGETING = TargetingConditions.forCombat().ignoreLineOfSight();
        private Path currentPath;
        private Vec3 targetLocation;
        private boolean clockwise;

        HoldingPattern(EnderDragon d) { super(d); }

        @Override public int id() { return HOLDING_PATTERN; }

        @Override
        public void doServerTick() {
            double d0 = targetLocation == null ? 0.0 : targetLocation.distanceToSqr(dragon.x(), dragon.y(), dragon.z());
            if (d0 < 100.0 || d0 > 22500.0 || dragon.horizontalCollision || dragon.verticalCollision) findNewTarget();
        }

        @Override
        public void begin() {
            currentPath = null;
            targetLocation = null;
        }

        @Override public Vec3 getFlyTargetLocation() { return targetLocation; }

        private void findNewTarget() {
            if (currentPath != null && currentPath.isDone()) {
                Pos top = podiumTop(dragon);
                int i = crystalsAlive(dragon);
                if (dragon.random().nextInt(i + 3) == 0) {
                    dragon.getPhaseManager().setPhase(LANDING_APPROACH);
                    return;
                }
                Player player = dragon.level().getNearestPlayer(NEW_TARGET_TARGETING, dragon, top.x(), top.y(), top.z());
                double d0 = player != null ? distToCenterSqr(top, player.x(), player.y(), player.z()) / 512.0 : 64.0;
                if (player != null && (dragon.random().nextInt((int) (d0 + 2.0)) == 0 || dragon.random().nextInt(i + 2) == 0)) {
                    strafePlayer(player);
                    return;
                }
            }
            if (currentPath == null || currentPath.isDone()) {
                int j = dragon.findClosestNode();
                int k = j;
                if (dragon.random().nextInt(8) == 0) {
                    clockwise = !clockwise;
                    k = j + 6;
                }
                if (clockwise) k++;
                else k--;
                // 바닐라 그대로: 전투가 있으면(>= 0) 언제나 바깥 고리
                if (dragon.getDragonFight() != null && dragon.getDragonFight().getCrystalsAlive() >= 0) {
                    k %= 12;
                    if (k < 0) k += 12;
                } else {
                    k -= 12;
                    k &= 7;
                    k += 12;
                }
                currentPath = dragon.findPath(j, k, null);
                if (currentPath != null) currentPath.advance();
            }
            navigateToNextPathNode();
        }

        private void strafePlayer(Player p) {
            dragon.getPhaseManager().setPhase(STRAFE_PLAYER);
            ((StrafePlayer) dragon.getPhaseManager().getPhase(STRAFE_PLAYER)).setTarget(p);
        }

        private void navigateToNextPathNode() {
            if (currentPath != null && !currentPath.isDone()) targetLocation = nextNodeTarget(dragon, currentPath);
        }

        @Override
        public void onCrystalDestroyed(EndCrystal crystal, Pos pos, Entity.Damage source, Player player) {
            if (player != null && player.canBeSeenAsEnemy()) strafePlayer(player);
        }
    }

    /** DragonStrafePlayerPhase: 대상 쪽으로 날며 정면 10도 안이면 화염구를 쏘고 돌아간다. */
    public static final class StrafePlayer extends DragonPhase {
        private int fireballCharge;
        private Path currentPath;
        private Vec3 targetLocation;
        private Targetable attackTarget;
        private boolean holdingPatternClockwise;

        StrafePlayer(EnderDragon d) { super(d); }

        @Override public int id() { return STRAFE_PLAYER; }

        @Override
        public void doServerTick() {
            if (attackTarget == null) {
                dragon.getPhaseManager().setPhase(HOLDING_PATTERN);
                return;
            }
            if (currentPath != null && currentPath.isDone()) {
                double d0 = attackTarget.x(), d1 = attackTarget.z();
                double d2 = d0 - dragon.x(), d3 = d1 - dragon.z();
                double d4 = Math.sqrt(d2 * d2 + d3 * d3);
                double d5 = Math.min(0.4F + d4 / 80.0 - 1.0, 10.0);
                targetLocation = new Vec3(d0, attackTarget.y() + d5, d1);
            }
            double d12 = targetLocation == null ? 0.0 : targetLocation.distanceToSqr(dragon.x(), dragon.y(), dragon.z());
            if (d12 < 100.0 || d12 > 22500.0) findNewTarget();
            double dx = attackTarget.x() - dragon.x(), dy = attackTarget.y() - dragon.y(), dz = attackTarget.z() - dragon.z();
            if (dx * dx + dy * dy + dz * dz < 4096.0) {
                if (dragon.hasLineOfSight(attackTarget)) {
                    fireballCharge++;
                    Vec3 toTarget = new Vec3(attackTarget.x() - dragon.x(), 0.0, attackTarget.z() - dragon.z()).normalize();
                    Vec3 facing = new Vec3(Mth.sin(dragon.yRot() * (float) (Math.PI / 180.0)), 0.0, -Mth.cos(dragon.yRot() * (float) (Math.PI / 180.0))).normalize();
                    float f1 = (float) facing.dot(toTarget);
                    float f = (float) (Math.acos(f1) * 180.0F / (float) Math.PI);
                    f += 0.5F;
                    if (fireballCharge >= 5 && f >= 0.0F && f < 10.0F) {
                        Vec3 view = dragon.viewVector(dragon.xRot(), dragon.yRot());
                        EnderDragonPart head = dragon.head;
                        double d6 = head.x() - view.x();
                        double d7 = head.y(0.5) + 0.5;
                        double d8 = head.z() - view.z();
                        double d9 = attackTarget.x() - d6;
                        double d10 = attackTarget.y() + attackTarget.bbHeight() * 0.5 - d7;
                        double d11 = attackTarget.z() - d8;
                        if (!dragon.isSilent()) dragon.level().levelEvent(null, 1017, dragon.blockPosition(), 0);
                        Vec3 dir = new Vec3(d9, d10, d11).normalize();
                        DragonFireball fb = new DragonFireball(dragon.level(), dragon.level().nextEntityId());
                        fb.launchFrom(dragon, dir.x(), dir.y(), dir.z());
                        fb.moveTo(d6, d7, d8, 0.0F, 0.0F);
                        dragon.level().addFreshEntity(fb);
                        fireballCharge = 0;
                        if (currentPath != null) while (!currentPath.isDone()) currentPath.advance();
                        dragon.getPhaseManager().setPhase(HOLDING_PATTERN);
                    }
                } else if (fireballCharge > 0) {
                    fireballCharge--;
                }
            } else if (fireballCharge > 0) {
                fireballCharge--;
            }
        }

        private void findNewTarget() {
            if (currentPath == null || currentPath.isDone()) {
                int i = dragon.findClosestNode();
                int j = i;
                if (dragon.random().nextInt(8) == 0) {
                    holdingPatternClockwise = !holdingPatternClockwise;
                    j = i + 6;
                }
                if (holdingPatternClockwise) j++;
                else j--;
                if (dragon.getDragonFight() != null && dragon.getDragonFight().getCrystalsAlive() > 0) {
                    j %= 12;
                    if (j < 0) j += 12;
                } else {
                    j -= 12;
                    j &= 7;
                    j += 12;
                }
                currentPath = dragon.findPath(i, j, null);
                if (currentPath != null) currentPath.advance();
            }
            navigateToNextPathNode();
        }

        private void navigateToNextPathNode() {
            if (currentPath != null && !currentPath.isDone()) targetLocation = nextNodeTarget(dragon, currentPath);
        }

        @Override
        public void begin() {
            fireballCharge = 0;
            targetLocation = null;
            currentPath = null;
            attackTarget = null;
        }

        public void setTarget(Targetable t) {
            attackTarget = t;
            int i = dragon.findClosestNode();
            int j = dragon.findClosestNode(t.x(), t.y(), t.z());
            int k = t.blockPosition().x(), l = t.blockPosition().z();
            double d0 = k - dragon.x(), d1 = l - dragon.z();
            double d2 = Math.sqrt(d0 * d0 + d1 * d1);
            double d3 = Math.min(0.4F + d2 / 80.0 - 1.0, 10.0);
            int i1 = (int) Math.floor(t.y() + d3);
            Node node = new Node(k, i1, l);
            currentPath = dragon.findPath(i, j, node);
            if (currentPath != null) {
                currentPath.advance();
                navigateToNextPathNode();
            }
        }

        public Targetable target() { return attackTarget; }

        @Override public Vec3 getFlyTargetLocation() { return targetLocation; }
    }

    /** DragonLandingApproachPhase: 받침대 쪽 경로를 따라가다 끝나면 착륙. */
    public static final class LandingApproach extends DragonPhase {
        private static final TargetingConditions NEAR_EGG_TARGETING = TargetingConditions.forCombat().ignoreLineOfSight();
        private Path currentPath;
        private Vec3 targetLocation;

        LandingApproach(EnderDragon d) { super(d); }

        @Override public int id() { return LANDING_APPROACH; }

        @Override
        public void doServerTick() {
            double d0 = targetLocation == null ? 0.0 : targetLocation.distanceToSqr(dragon.x(), dragon.y(), dragon.z());
            if (d0 < 100.0 || d0 > 22500.0 || dragon.horizontalCollision || dragon.verticalCollision) findNewTarget();
        }

        @Override
        public void begin() {
            currentPath = null;
            targetLocation = null;
        }

        @Override public Vec3 getFlyTargetLocation() { return targetLocation; }

        private void findNewTarget() {
            if (currentPath == null || currentPath.isDone()) {
                int i = dragon.findClosestNode();
                Pos top = podiumTop(dragon);
                Player player = dragon.level().getNearestPlayer(NEAR_EGG_TARGETING, dragon, top.x(), top.y(), top.z());
                int j;
                if (player != null) {
                    Vec3 v = new Vec3(player.x(), 0.0, player.z()).normalize();
                    j = dragon.findClosestNode(-v.x() * 40.0, 105.0, -v.z() * 40.0);
                } else {
                    j = dragon.findClosestNode(40.0, top.y(), 0.0);
                }
                Node node = new Node(top.x(), top.y(), top.z());
                currentPath = dragon.findPath(i, j, node);
                if (currentPath != null) currentPath.advance();
            }
            if (currentPath != null && !currentPath.isDone()) targetLocation = nextNodeTarget(dragon, currentPath);
            if (currentPath != null && currentPath.isDone()) dragon.getPhaseManager().setPhase(LANDING);
        }
    }

    /** DragonLandingPhase: 받침대 바로 위로 내려와 닿으면 앉아 둘러보기. */
    public static final class Landing extends DragonPhase {
        private Vec3 targetLocation;

        Landing(EnderDragon d) { super(d); }

        @Override public int id() { return LANDING; }

        @Override
        public void doServerTick() {
            if (targetLocation == null) {
                Pos top = podiumTop(dragon);
                targetLocation = new Vec3(top.x() + 0.5, top.y(), top.z() + 0.5);
            }
            if (targetLocation.distanceToSqr(dragon.x(), dragon.y(), dragon.z()) < 1.0) {
                ((SittingFlaming) dragon.getPhaseManager().getPhase(SITTING_FLAMING)).resetFlameCount();
                dragon.getPhaseManager().setPhase(SITTING_SCANNING);
            }
        }

        @Override public float getFlySpeed() { return 1.5F; }

        @Override
        public float getTurnSpeed() {
            float f = (float) Math.sqrt(dragon.vx() * dragon.vx() + dragon.vz() * dragon.vz()) + 1.0F;
            float f1 = Math.min(f, 40.0F);
            return f1 / f;
        }

        @Override public void begin() { targetLocation = null; }

        @Override public Vec3 getFlyTargetLocation() { return targetLocation; }
    }

    /** DragonTakeoffPhase: 머리가 보는 반대쪽 노드로 떠오르고 받침대에서 10칸 벗어나면 맴돌기. */
    public static final class Takeoff extends DragonPhase {
        private boolean firstTick;
        private Path currentPath;
        private Vec3 targetLocation;

        Takeoff(EnderDragon d) { super(d); }

        @Override public int id() { return TAKEOFF; }

        @Override
        public void doServerTick() {
            if (!firstTick && currentPath != null) {
                Pos top = podiumTop(dragon);
                if (!(distToCenterSqr(top, dragon.x(), dragon.y(), dragon.z()) < 100.0)) dragon.getPhaseManager().setPhase(HOLDING_PATTERN);
            } else {
                firstTick = false;
                findNewTarget();
            }
        }

        @Override
        public void begin() {
            firstTick = true;
            currentPath = null;
            targetLocation = null;
        }

        private void findNewTarget() {
            int i = dragon.findClosestNode();
            Vec3 v = dragon.getHeadLookVector();
            int j = dragon.findClosestNode(-v.x() * 40.0, 105.0, -v.z() * 40.0);
            if (dragon.getDragonFight() != null && dragon.getDragonFight().getCrystalsAlive() > 0) {
                j %= 12;
                if (j < 0) j += 12;
            } else {
                j -= 12;
                j &= 7;
                j += 12;
            }
            currentPath = dragon.findPath(i, j, null);
            if (currentPath != null) {
                currentPath.advance();
                if (!currentPath.isDone()) targetLocation = nextNodeTarget(dragon, currentPath);
            }
        }

        @Override public Vec3 getFlyTargetLocation() { return targetLocation; }
    }

    /** DragonSittingScanningPhase: 20칸 안(높이차 10 이하) 플레이어를 보면 돌아보다 25틱 넘으면 공격, 100틱 동안 없으면 이륙·돌진. */
    public static final class SittingScanning extends Sitting {
        private static final TargetingConditions CHARGE_TARGETING = TargetingConditions.forCombat().range(150.0);
        private final TargetingConditions scanTargeting;
        private int scanningTime;

        SittingScanning(EnderDragon d) {
            super(d);
            scanTargeting = TargetingConditions.forCombat().range(20.0).selector(t -> Math.abs(t.y() - d.y()) <= 10.0);
        }

        @Override public int id() { return SITTING_SCANNING; }

        @Override
        public void doServerTick() {
            scanningTime++;
            Player le = dragon.level().getNearestPlayer(scanTargeting, dragon, dragon.x(), dragon.y(), dragon.z());
            if (le != null) {
                if (scanningTime > 25) {
                    dragon.getPhaseManager().setPhase(SITTING_ATTACKING);
                } else {
                    Vec3 v = new Vec3(le.x() - dragon.x(), 0.0, le.z() - dragon.z()).normalize();
                    Vec3 v1 = new Vec3(Mth.sin(dragon.yRot() * (float) (Math.PI / 180.0)), 0.0, -Mth.cos(dragon.yRot() * (float) (Math.PI / 180.0))).normalize();
                    float f = (float) v1.dot(v);
                    float f1 = (float) (Math.acos(f) * 180.0F / (float) Math.PI) + 0.5F;
                    if (f1 < 0.0F || f1 > 10.0F) {
                        double d0 = le.x() - dragon.head.x(), d1 = le.z() - dragon.head.z();
                        double d2 = Math.clamp(EnderDragon.wrapDegrees(180.0 - Mth.atan2(d0, d1) * 180.0F / (float) Math.PI - dragon.yRot()), -100.0, 100.0);
                        dragon.yRotA *= 0.8F;
                        float f2 = (float) Math.sqrt(d0 * d0 + d1 * d1) + 1.0F;
                        float f3 = f2;
                        if (f2 > 40.0F) f2 = 40.0F;
                        dragon.yRotA += (float) d2 * (0.7F / f2 / f3);
                        dragon.setYRot(dragon.yRot() + dragon.yRotA);
                    }
                }
            } else if (scanningTime >= 100) {
                Player target = dragon.level().getNearestPlayer(CHARGE_TARGETING, dragon, dragon.x(), dragon.y(), dragon.z());
                dragon.getPhaseManager().setPhase(TAKEOFF);
                if (target != null) {
                    dragon.getPhaseManager().setPhase(CHARGING_PLAYER);
                    ((ChargingPlayer) dragon.getPhaseManager().getPhase(CHARGING_PLAYER)).setTarget(new Vec3(target.x(), target.y(), target.z()));
                }
            }
        }

        @Override public void begin() { scanningTime = 0; }
    }

    /** DragonSittingAttackingPhase: 40틱 으르렁거린 뒤 숨결. */
    public static final class SittingAttacking extends Sitting {
        private int attackingTicks;

        SittingAttacking(EnderDragon d) { super(d); }

        @Override public int id() { return SITTING_ATTACKING; }

        @Override
        public void doServerTick() {
            if (attackingTicks++ >= 40) dragon.getPhaseManager().setPhase(SITTING_FLAMING);
        }

        @Override public void begin() { attackingTicks = 0; }
    }

    /** DragonSittingFlamingPhase: 10틱째 머리 앞 바닥에 숨결 구름(반경 5, 200틱, 즉시 피해). 200틱 뒤 4번째면 이륙, 아니면 다시 둘러보기. */
    public static final class SittingFlaming extends Sitting {
        private int flameTicks;
        private int flameCount;
        private AreaEffectCloud flame;

        SittingFlaming(EnderDragon d) { super(d); }

        @Override public int id() { return SITTING_FLAMING; }

        @Override
        public void doServerTick() {
            flameTicks++;
            if (flameTicks >= 200) {
                if (flameCount >= 4) dragon.getPhaseManager().setPhase(TAKEOFF);
                else dragon.getPhaseManager().setPhase(SITTING_SCANNING);
            } else if (flameTicks == 10) {
                Vec3 v = new Vec3(dragon.head.x() - dragon.x(), 0.0, dragon.head.z() - dragon.z()).normalize();
                double d0 = dragon.head.x() + v.x() * 5.0 / 2.0;
                double d1 = dragon.head.z() + v.z() * 5.0 / 2.0;
                double d2 = dragon.head.y(0.5);
                double d3 = d2;
                Pos p = new Pos((int) Math.floor(d0), (int) Math.floor(d2), (int) Math.floor(d1));
                while (dragon.level().isEmptyBlock(p)) {
                    if (--d3 < 0.0) {
                        d3 = d2;
                        break;
                    }
                    p = new Pos((int) Math.floor(d0), (int) Math.floor(d3), (int) Math.floor(d1));
                }
                d3 = Math.floor(d3) + 1;
                flame = new AreaEffectCloud(dragon.level(), dragon.level().nextEntityId());
                flame.moveTo(d0, d3, d1, 0.0F, 0.0F);
                flame.setOwner(dragon);
                flame.setRadius(5.0F);
                flame.setDuration(200);
                flame.setParticle(AreaEffectCloud.DRAGON_BREATH_PARTICLE);
                flame.setEffects(List.of(new MobEffectInstance(MobEffect.INSTANT_DAMAGE, 0, 0)));
                dragon.level().addFreshEntity(flame);
            }
        }

        @Override
        public void begin() {
            flameTicks = 0;
            flameCount++;
        }

        @Override
        public void end() {
            if (flame != null) {
                flame.discard();
                flame = null;
            }
        }

        public void resetFlameCount() { flameCount = 0; }

        public int flameCount() { return flameCount; }
    }

    /** DragonChargePlayerPhase: 정한 지점으로 빠르게(3.0) 돌진, 닿거나 벗어나고 10틱 지나면 맴돌기. */
    public static final class ChargingPlayer extends DragonPhase {
        private Vec3 targetLocation;
        private int timeSinceCharge;

        ChargingPlayer(EnderDragon d) { super(d); }

        @Override public int id() { return CHARGING_PLAYER; }

        @Override
        public void doServerTick() {
            if (targetLocation == null) {
                dragon.getPhaseManager().setPhase(HOLDING_PATTERN);
            } else if (timeSinceCharge > 0 && timeSinceCharge++ >= 10) {
                dragon.getPhaseManager().setPhase(HOLDING_PATTERN);
            } else {
                double d0 = targetLocation.distanceToSqr(dragon.x(), dragon.y(), dragon.z());
                if (d0 < 100.0 || d0 > 22500.0 || dragon.horizontalCollision || dragon.verticalCollision) timeSinceCharge++;
            }
        }

        @Override
        public void begin() {
            targetLocation = null;
            timeSinceCharge = 0;
        }

        public void setTarget(Vec3 v) { targetLocation = v; }

        @Override public float getFlySpeed() { return 3.0F; }

        @Override public Vec3 getFlyTargetLocation() { return targetLocation; }
    }

    /** DragonDeathPhase: 받침대 위로 날아가며 체력 1 을 지키다, 가까워지거나(10칸) 너무 멀면(150칸) 0 으로 — 죽음 애니메이션 시작. */
    public static final class Dying extends DragonPhase {
        private Vec3 targetLocation;
        private int time;

        Dying(EnderDragon d) { super(d); }

        @Override public int id() { return DYING; }

        @Override
        public void doServerTick() {
            time++;
            if (targetLocation == null) {
                Pos o = dragon.getFightOrigin();
                int y = dragon.level().getMotionBlockingHeight(o.x(), o.z());
                targetLocation = new Vec3(o.x() + 0.5, y, o.z() + 0.5);
            }
            double d0 = targetLocation.distanceToSqr(dragon.x(), dragon.y(), dragon.z());
            if (!(d0 < 100.0) && !(d0 > 22500.0) && !dragon.horizontalCollision && !dragon.verticalCollision) dragon.setHealth(1.0F);
            else dragon.setHealth(0.0F);
        }

        @Override
        public void begin() {
            targetLocation = null;
            time = 0;
        }

        @Override public float getFlySpeed() { return 3.0F; }

        @Override public Vec3 getFlyTargetLocation() { return targetLocation; }
    }

    /** DragonHoverPhase: 제자리(처음 위치)에 떠 있다 — 소환 직후 기본. */
    public static final class Hover extends DragonPhase {
        private Vec3 targetLocation;

        Hover(EnderDragon d) { super(d); }

        @Override public int id() { return HOVERING; }

        @Override
        public void doServerTick() {
            if (targetLocation == null) targetLocation = new Vec3(dragon.x(), dragon.y(), dragon.z());
        }

        @Override public boolean isSitting() { return true; }

        @Override public void begin() { targetLocation = null; }

        @Override public float getFlySpeed() { return 1.0F; }

        @Override public Vec3 getFlyTargetLocation() { return targetLocation; }
    }
}
