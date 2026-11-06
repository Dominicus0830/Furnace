// 바닐라 NodeEvaluator: 길찾기가 노드와 이웃을 만들고 칸의 종류를 판정하는 기반. 문 통과/열기, 물 위 뜨기, 울타리 넘기 같은 설정을 가진다.
package org.domi.furnace.server.entity.ai.navigation;

import java.util.HashMap;
import java.util.Map;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.Mob;

public abstract class NodeEvaluator {
    protected ChunkLevel level;
    protected Mob mob;
    protected final Map<Integer, Node> nodes = new HashMap<>();
    protected int entityWidth, entityHeight, entityDepth;
    protected boolean canPassDoors, canOpenDoors, canFloat, canWalkOverFences;

    public void prepare(ChunkLevel level, Mob mob) {
        this.level = level;
        this.mob = mob;
        nodes.clear();
        entityWidth = (int) Math.floor(mob.bbWidth() + 1.0F);
        entityHeight = (int) Math.floor(mob.bbHeight() + 1.0F);
        entityDepth = (int) Math.floor(mob.bbWidth() + 1.0F);
    }

    public void done() {
        level = null;
        mob = null;
    }

    protected Node getNode(Pos p) { return getNode(p.x(), p.y(), p.z()); }

    protected Node getNode(int x, int y, int z) {
        return nodes.computeIfAbsent(Node.createHash(x, y, z), k -> new Node(x, y, z));
    }

    public abstract Node getStart();

    public abstract Target getTarget(double x, double y, double z);

    protected Target getTargetNodeAt(double x, double y, double z) {
        return new Target(getNode((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z)));
    }

    public abstract int getNeighbors(Node[] out, Node node);

    public abstract PathType getPathTypeOfMob(PathfindingContext ctx, int x, int y, int z, Mob mob);

    /** 노드 칸 종류에 몹의 플러그인 훅을 씌운다(훅이 없으면 그대로). 칸별 기억(pathTypesByPosCache)에 넣기 전에 한 번. */
    protected final PathType hooked(int x, int y, int z, PathType vanilla) {
        PathTypeHook h = mob.pathTypeHook();
        if (h == null) return vanilla;
        PathType t = h.apply(x, y, z, vanilla);
        return t == null ? vanilla : t;
    }

    public abstract PathType getPathType(PathfindingContext ctx, int x, int y, int z);

    public PathType getPathType(Mob mob, Pos pos) {
        return getPathType(new PathfindingContext(mob.level(), mob), pos.x(), pos.y(), pos.z());
    }

    public void setCanPassDoors(boolean v) { canPassDoors = v; }

    public void setCanOpenDoors(boolean v) { canOpenDoors = v; }

    public void setCanFloat(boolean v) { canFloat = v; }

    public void setCanWalkOverFences(boolean v) { canWalkOverFences = v; }

    public boolean canPassDoors() { return canPassDoors; }

    public boolean canOpenDoors() { return canOpenDoors; }

    public boolean canFloat() { return canFloat; }

    public boolean canWalkOverFences() { return canWalkOverFences; }
}
