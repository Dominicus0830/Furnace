// 바닐라 PathNavigation: 경로를 만들고(createPath), 몹이 노드를 따라가게 이동 제어에 목표 점을 준다(tick). 막힘/시간 초과 감지와 다시 계산.
package org.domi.furnace.server.entity.ai.navigation;

import java.util.Set;
import org.domi.furnace.server.attribute.Attribute;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.Targetable;
import org.domi.furnace.server.entity.Mob;

public abstract class PathNavigation {
    private static final int MAX_TIME_RECOMPUTE = 20;
    private static final int STUCK_CHECK_INTERVAL = 100;
    private static final float STUCK_THRESHOLD_DISTANCE_FACTOR = 0.25F;

    protected final Mob mob;
    protected final ChunkLevel level;
    protected Path path;
    protected double speedModifier;
    protected int tick;
    protected int lastStuckCheck;
    protected double[] lastStuckCheckPos = {0, 0, 0};
    protected Pos timeoutCachedNode = Pos.ZERO;
    protected long timeoutTimer;
    protected long lastTimeoutCheck;
    protected double timeoutLimit;
    protected float maxDistanceToWaypoint = 0.5F;
    protected boolean hasDelayedRecomputation;
    protected long timeLastRecompute;
    protected NodeEvaluator nodeEvaluator;
    private Pos targetPos;
    private int reachRange;
    private float maxVisitedNodesMultiplier = 1.0F;
    private final PathFinder pathFinder;
    private boolean isStuck;

    public PathNavigation(Mob mob, ChunkLevel level) {
        this.mob = mob;
        this.level = level;
        int maxVisited = (int) Math.floor(mob.attributeValue(Attribute.FOLLOW_RANGE) * 16.0);
        this.pathFinder = createPathFinder(maxVisited);
    }

    public void resetMaxVisitedNodesMultiplier() { maxVisitedNodesMultiplier = 1.0F; }

    public void setMaxVisitedNodesMultiplier(float m) { maxVisitedNodesMultiplier = m; }

    public Pos getTargetPos() { return targetPos; }

    protected abstract PathFinder createPathFinder(int maxVisitedNodes);

    public void setSpeedModifier(double s) { speedModifier = s; }

    public void recomputePath() {
        if (level.gameTime() - timeLastRecompute > MAX_TIME_RECOMPUTE) {
            if (targetPos != null) {
                path = null;
                path = createPath(targetPos, reachRange);
                timeLastRecompute = level.gameTime();
                hasDelayedRecomputation = false;
            }
        } else {
            hasDelayedRecomputation = true;
        }
    }

    public final Path createPath(double x, double y, double z, int accuracy) {
        return createPath(new Pos((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z)), accuracy);
    }

    public Path createPath(Set<Pos> targets, int accuracy) { return createPath(targets, 8, false, accuracy); }

    public Path createPath(Pos target, int accuracy) { return createPath(Set.of(target), 8, false, accuracy); }

    public Path createPath(Pos target, int range, int accuracy) { return createPath(Set.of(target), range, false, accuracy); }

    public Path createPath(Targetable target, int accuracy) { return createPath(Set.of(target.blockPosition()), 16, true, accuracy); }

    protected Path createPath(Set<Pos> targets, int range, boolean above, int accuracy) {
        return createPath(targets, range, above, accuracy, (float) mob.attributeValue(Attribute.FOLLOW_RANGE));
    }

    protected Path createPath(Set<Pos> targets, int range, boolean above, int accuracy, float followRange) {
        if (targets.isEmpty()) return null;
        if (mob.y() < level.minBuildHeight()) return null;
        if (!canUpdatePath()) return null;
        if (path != null && !path.isDone() && targets.contains(targetPos)) return path;
        Path p = pathFinder.findPath(level, mob, targets, followRange, accuracy, maxVisitedNodesMultiplier);
        if (p != null && p.getTarget() != null) {
            targetPos = p.getTarget();
            reachRange = accuracy;
            resetStuckTimeout();
        }
        return p;
    }

    public boolean moveTo(double x, double y, double z, double speed) { return moveTo(createPath(x, y, z, 1), speed); }

    public boolean moveTo(double x, double y, double z, int accuracy, double speed) { return moveTo(createPath(x, y, z, accuracy), speed); }

    public boolean moveTo(Targetable e, double speed) {
        Path p = createPath(e, 1);
        return p != null && moveTo(p, speed);
    }

    public boolean moveTo(Path newPath, double speed) {
        if (newPath == null) {
            path = null;
            return false;
        }
        if (!newPath.sameAs(path)) path = newPath;
        if (isDone()) return false;
        trimPath();
        if (path.getNodeCount() <= 0) return false;
        speedModifier = speed;
        double[] p = getTempMobPos();
        lastStuckCheck = tick;
        lastStuckCheckPos = p;
        return true;
    }

    public Path getPath() { return path; }

    public void tick() {
        tick++;
        if (hasDelayedRecomputation) recomputePath();
        if (!isDone()) {
            if (canUpdatePath()) {
                followThePath();
            } else if (path != null && !path.isDone()) {
                double[] p = getTempMobPos();
                double[] q = path.getNextEntityPos(mob);
                if (p[1] > q[1] && !mob.onGround() && Math.floor(p[0]) == Math.floor(q[0]) && Math.floor(p[2]) == Math.floor(q[2])) path.advance();
            }
            if (!isDone()) {
                double[] q = path.getNextEntityPos(mob);
                mob.getMoveControl().setWantedPosition(q[0], getGroundY(q), q[2], speedModifier);
            }
        }
    }

    protected double getGroundY(double[] v) {
        Pos pos = new Pos((int) Math.floor(v[0]), (int) Math.floor(v[1]), (int) Math.floor(v[2]));
        return B.isAir(level.getBlockState(pos.below())) ? v[1] : WalkNodeEvaluator.getFloorLevel(level, pos);
    }

    protected void followThePath() {
        double[] pos = getTempMobPos();
        maxDistanceToWaypoint = mob.bbWidth() > 0.75F ? mob.bbWidth() / 2.0F : 0.75F - mob.bbWidth() / 2.0F;
        Pos next = path.getNextNodePos();
        double dx = Math.abs(mob.x() - (next.x() + 0.5)), dy = Math.abs(mob.y() - next.y()), dz = Math.abs(mob.z() - (next.z() + 0.5));
        boolean close = dx < maxDistanceToWaypoint && dz < maxDistanceToWaypoint && dy < 1.0;
        if (close || canCutCorner(path.getNextNode().type) && shouldTargetNextNodeInDirection(pos)) path.advance();
        doStuckDetection(pos);
    }

    private boolean shouldTargetNextNodeInDirection(double[] mobPos) {
        if (path.getNextNodeIndex() + 1 >= path.getNodeCount()) return false;
        Pos next = path.getNextNodePos();
        double[] nextCenter = {next.x() + 0.5, next.y(), next.z() + 0.5};
        if (!closerThan(mobPos, nextCenter, 2.0)) return false;
        if (canMoveDirectly(mobPos, path.getNextEntityPos(mob))) return true;
        Pos after = path.getNodePos(path.getNextNodeIndex() + 1);
        double[] afterCenter = {after.x() + 0.5, after.y(), after.z() + 0.5};
        double[] toNext = {nextCenter[0] - mobPos[0], nextCenter[1] - mobPos[1], nextCenter[2] - mobPos[2]};
        double[] toAfter = {afterCenter[0] - mobPos[0], afterCenter[1] - mobPos[1], afterCenter[2] - mobPos[2]};
        double a = lengthSqr(toNext), b = lengthSqr(toAfter);
        boolean closerAfter = b < a, veryClose = a < 0.5;
        if (!closerAfter && !veryClose) return false;
        double[] n1 = normalize(toNext), n2 = normalize(toAfter);
        return n2[0] * n1[0] + n2[1] * n1[1] + n2[2] * n1[2] < 0.0;
    }

    private static boolean closerThan(double[] a, double[] b, double d) {
        double dx = a[0] - b[0], dy = a[1] - b[1], dz = a[2] - b[2];
        return dx * dx + dy * dy + dz * dz < d * d;
    }

    private static double lengthSqr(double[] v) { return v[0] * v[0] + v[1] * v[1] + v[2] * v[2]; }

    private static double[] normalize(double[] v) {
        double len = Math.sqrt(lengthSqr(v));
        return len < 1.0E-5F ? new double[] {0, 0, 0} : new double[] {v[0] / len, v[1] / len, v[2] / len};
    }

    protected boolean canMoveDirectly(double[] a, double[] b) { return false; }

    protected void doStuckDetection(double[] pos) {
        if (tick - lastStuckCheck > STUCK_CHECK_INTERVAL) {
            float speed = mob.getSpeed() >= 1.0F ? mob.getSpeed() : mob.getSpeed() * mob.getSpeed();
            float dist = speed * 100.0F * STUCK_THRESHOLD_DISTANCE_FACTOR;
            double dx = pos[0] - lastStuckCheckPos[0], dy = pos[1] - lastStuckCheckPos[1], dz = pos[2] - lastStuckCheckPos[2];
            if (dx * dx + dy * dy + dz * dz < (double) (dist * dist)) {
                isStuck = true;
                stop();
            } else {
                isStuck = false;
            }
            lastStuckCheck = tick;
            lastStuckCheckPos = pos;
        }
        if (path != null && !path.isDone()) {
            Pos next = path.getNextNodePos();
            long now = level.gameTime();
            if (next.equals(timeoutCachedNode)) {
                timeoutTimer += now - lastTimeoutCheck;
            } else {
                timeoutCachedNode = next;
                double dx = pos[0] - (next.x() + 0.5), dy = pos[1] - next.y(), dz = pos[2] - (next.z() + 0.5);
                double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
                timeoutLimit = mob.getSpeed() > 0.0F ? d / mob.getSpeed() * 20.0 : 0.0;
            }
            if (timeoutLimit > 0.0 && timeoutTimer > timeoutLimit * 3.0) timeoutPath();
            lastTimeoutCheck = now;
        }
    }

    private void timeoutPath() {
        resetStuckTimeout();
        stop();
    }

    private void resetStuckTimeout() {
        timeoutCachedNode = Pos.ZERO;
        timeoutTimer = 0L;
        timeoutLimit = 0.0;
        isStuck = false;
    }

    public boolean isDone() { return path == null || path.isDone(); }

    public boolean isInProgress() { return !isDone(); }

    public void stop() { path = null; }

    protected abstract double[] getTempMobPos();

    protected abstract boolean canUpdatePath();

    /** 경로 노드 손질(솥은 한 칸 위로). */
    protected void trimPath() {
        if (path == null) return;
        for (int i = 0; i < path.getNodeCount(); i++) {
            Node n = path.getNode(i);
            Node next = i + 1 < path.getNodeCount() ? path.getNode(i + 1) : null;
            int s = level.getBlockState(new Pos(n.x, n.y, n.z));
            if (B.isTag(s, "minecraft:cauldrons")) {
                path.replaceNode(i, n.cloneAndMove(n.x, n.y + 1, n.z));
                if (next != null && n.y >= next.y) path.replaceNode(i + 1, next.cloneAndMove(next.x, n.y + 1, next.z));
            }
        }
    }

    protected boolean canCutCorner(PathType t) { return t != PathType.DANGER_FIRE && t != PathType.DANGER_OTHER && t != PathType.WALKABLE_DOOR; }

    public boolean isStableDestination(Pos pos) {
        Pos below = pos.below();
        return B.isFullOcclusion(level.getBlockState(below));
    }

    public NodeEvaluator getNodeEvaluator() { return nodeEvaluator; }

    public void setCanFloat(boolean v) { nodeEvaluator.setCanFloat(v); }

    public boolean canFloat() { return nodeEvaluator.canFloat(); }

    public boolean shouldRecomputePath(Pos pos) {
        if (hasDelayedRecomputation) return false;
        if (path != null && !path.isDone() && path.getNodeCount() != 0) {
            Node end = path.getEndNode();
            double mx = (end.x + mob.x()) / 2.0, my = (end.y + mob.y()) / 2.0, mz = (end.z + mob.z()) / 2.0;
            double dx = pos.x() + 0.5 - mx, dy = pos.y() + 0.5 - my, dz = pos.z() + 0.5 - mz;
            double r = path.getNodeCount() - path.getNextNodeIndex();
            return dx * dx + dy * dy + dz * dz < r * r;   // closerToCenterThan(mid, size)
        }
        return false;
    }

    public float getMaxDistanceToWaypoint() { return maxDistanceToWaypoint; }

    public boolean isStuck() { return isStuck; }
}
