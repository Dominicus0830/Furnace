// 바닐라 AbstractSchoolingFish: 떼 짓는 물고기(대구·연어·열대어) — 우두머리를 따라 모이고, 무리 크기에 상한(기본 8)이 있다.
package org.domi.furnace.server.entity;

import java.util.List;
import java.util.stream.Stream;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.entity.ai.goal.FollowFlockLeaderGoal;

public abstract class AbstractSchoolingFish extends AbstractFish {
    private AbstractSchoolingFish leader;
    private int schoolSize = 1;

    protected AbstractSchoolingFish(EntityType type, ChunkLevel level, int id) { super(type, level, id); }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        goalSelector.addGoal(5, new FollowFlockLeaderGoal(this));
    }

    public int getMaxSpawnClusterSize() { return getMaxSchoolSize(); }

    public int getMaxSchoolSize() { return 8; }

    @Override
    protected boolean canRandomSwim() { return !isFollower(); }

    public boolean isFollower() { return leader != null && leader.isAlive(); }

    public AbstractSchoolingFish startFollowing(AbstractSchoolingFish leader) {
        this.leader = leader;
        leader.addFollower();
        return leader;
    }

    public void stopFollowing() {
        leader.removeFollower();
        leader = null;
    }

    private void addFollower() { schoolSize++; }

    private void removeFollower() { schoolSize--; }

    public boolean canBeFollowed() { return hasFollowers() && schoolSize < getMaxSchoolSize(); }

    @SuppressWarnings("unchecked")
    @Override
    public void tick() {
        super.tick();
        if (hasFollowers() && level.random().nextInt(200) == 1) {
            List<AbstractSchoolingFish> list = level.getEntitiesOfClass((Class<AbstractSchoolingFish>) (Class<?>) getClass(), boundingBox().inflate(8.0, 8.0, 8.0), f -> true);
            if (list.size() <= 1) schoolSize = 1;
        }
    }

    public boolean hasFollowers() { return schoolSize > 1; }

    public boolean inRangeOfLeader() {
        double dx = leader.x() - x(), dy = leader.y() - y(), dz = leader.z() - z();
        return dx * dx + dy * dy + dz * dz <= 121.0;
    }

    public void pathToLeader() {
        if (isFollower()) getNavigation().moveTo(leader, 1.0);
    }

    public void addFollowers(Stream<? extends AbstractSchoolingFish> followers) {
        followers.limit(getMaxSchoolSize() - schoolSize).filter(f -> f != this).forEach(f -> f.startFollowing(this));
    }

    /** 무리 그룹 데이터: 처음 스폰된 우두머리. */
    public static class SchoolSpawnGroupData {
        public final AbstractSchoolingFish leader;

        public SchoolSpawnGroupData(AbstractSchoolingFish leader) { this.leader = leader; }
    }

    public SchoolSpawnGroupData finalizeSpawn(org.domi.furnace.server.block.RandomSource r, DifficultyInstance difficulty, SchoolSpawnGroupData data) {
        finalizeSpawn(r, difficulty);
        if (data == null) data = new SchoolSpawnGroupData(this);
        else startFollowing(data.leader);
        return data;
    }
}
