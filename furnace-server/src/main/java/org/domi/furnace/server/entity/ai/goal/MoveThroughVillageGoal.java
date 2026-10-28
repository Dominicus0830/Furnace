// 바닐라 MoveThroughVillageGoal: 마을 근처(6섹션)면 아직 안 간 점유된 마을 POI 쪽으로 길을 내 걷는다(밤에만 옵션).
// 길 위에 나무 문이 있으면 그 문까지만 간다. 문 처리 여부는 canDealWithDoors 가 정한다.
package org.domi.furnace.server.entity.ai.goal;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.types.DoorBlock;
import org.domi.furnace.server.entity.PathfinderMob;
import org.domi.furnace.server.entity.ai.Goal;
import org.domi.furnace.server.entity.ai.navigation.GroundPathNavigation;
import org.domi.furnace.server.entity.ai.navigation.Node;
import org.domi.furnace.server.entity.ai.navigation.Path;
import org.domi.furnace.server.entity.ai.util.DefaultRandomPos;
import org.domi.furnace.server.entity.ai.util.GoalUtils;
import org.domi.furnace.server.entity.ai.util.LandRandomPos;
import org.domi.furnace.server.world.poi.PoiManager;
import org.domi.furnace.server.world.poi.PoiTypes;

public class MoveThroughVillageGoal extends Goal {
    protected final PathfinderMob mob;
    private final double speedModifier;
    private Path path;
    private Pos poiPos;
    private final boolean onlyAtNight;
    private final List<Pos> visited = new ArrayList<>();
    private final int distanceToPoi;
    private final BooleanSupplier canDealWithDoors;

    public MoveThroughVillageGoal(PathfinderMob mob, double speed, boolean onlyAtNight, int distanceToPoi, BooleanSupplier canDealWithDoors) {
        this.mob = mob;
        this.speedModifier = speed;
        this.onlyAtNight = onlyAtNight;
        this.distanceToPoi = distanceToPoi;
        this.canDealWithDoors = canDealWithDoors;
        setFlags(EnumSet.of(Flag.MOVE));
        if (!GoalUtils.hasGroundPathNavigation(mob)) throw new IllegalArgumentException("Unsupported mob for MoveThroughVillageGoal");
    }

    @Override
    public boolean canUse() {
        if (!GoalUtils.hasGroundPathNavigation(mob)) return false;
        updateVisited();
        if (onlyAtNight && mob.level().isDay()) return false;
        ChunkLevel level = mob.level();
        Pos pos = mob.blockPosition();
        if (!level.isCloseToVillage(pos, 6)) return false;
        PoiManager poi = level.poiManager();
        double[] vec = LandRandomPos.getPos(mob, 15, 7, p -> {
            if (!level.isVillage(p)) return Double.NEGATIVE_INFINITY;
            Pos found = poi.findClosest(PoiTypes.VILLAGE, this::hasNotVisited, p, 10, PoiManager.Occupancy.IS_OCCUPIED);
            return found == null ? Double.NEGATIVE_INFINITY : -distSqr(found, pos);
        });
        if (vec == null) return false;
        Pos found = poi.findClosest(PoiTypes.VILLAGE, this::hasNotVisited, containing(vec), 10, PoiManager.Occupancy.IS_OCCUPIED);
        if (found == null) return false;
        poiPos = found;
        GroundPathNavigation nav = (GroundPathNavigation) mob.getNavigation();
        boolean old = nav.canOpenDoors();
        nav.setCanOpenDoors(canDealWithDoors.getAsBoolean());
        path = nav.createPath(poiPos, 0);
        nav.setCanOpenDoors(old);
        if (path == null) {
            double[] towards = DefaultRandomPos.getPosTowards(mob, 10, 7, new double[] {poiPos.x() + 0.5, poiPos.y(), poiPos.z() + 0.5}, (float) (Math.PI / 2));
            if (towards == null) return false;
            nav.setCanOpenDoors(canDealWithDoors.getAsBoolean());
            path = mob.getNavigation().createPath(towards[0], towards[1], towards[2], 0);
            nav.setCanOpenDoors(old);
            if (path == null) return false;
        }
        for (int i = 0; i < path.getNodeCount(); i++) {
            Node n = path.getNode(i);
            Pos door = new Pos(n.x, n.y + 1, n.z);
            if (DoorBlock.isWoodenDoor(mob.level(), door)) {
                path = mob.getNavigation().createPath(n.x, n.y, n.z, 0);
                break;
            }
        }
        return path != null;
    }

    @Override
    public boolean canContinueToUse() {
        return mob.getNavigation().isDone() ? false : !closerToCenterThan(poiPos, mob.bbWidth() + distanceToPoi);
    }

    @Override
    public void start() { mob.getNavigation().moveTo(path, speedModifier); }

    @Override
    public void stop() {
        if (mob.getNavigation().isDone() || closerToCenterThan(poiPos, distanceToPoi)) visited.add(poiPos);
    }

    private boolean hasNotVisited(Pos pos) {
        for (Pos p : visited) if (Objects.equals(pos, p)) return false;
        return true;
    }

    private void updateVisited() {
        if (visited.size() > 15) visited.remove(0);
    }

    /** Vec3i.closerToCenterThan(mob.position(), d). */
    private boolean closerToCenterThan(Pos p, double d) {
        double dx = p.x() + 0.5 - mob.x(), dy = p.y() + 0.5 - mob.y(), dz = p.z() + 0.5 - mob.z();
        return dx * dx + dy * dy + dz * dz < d * d;
    }

    private static double distSqr(Pos a, Pos b) {
        double dx = a.x() - b.x(), dy = a.y() - b.y(), dz = a.z() - b.z();
        return dx * dx + dy * dy + dz * dz;
    }

    private static Pos containing(double[] v) { return new Pos((int) Math.floor(v[0]), (int) Math.floor(v[1]), (int) Math.floor(v[2])); }
}
