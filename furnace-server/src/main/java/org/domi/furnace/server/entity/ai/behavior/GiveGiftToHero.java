// 바닐라 GiveGiftToHero(100): 보이는 가장 가까운 플레이어가 마을의 영웅이면 600틱(그다음은 600~6600틱, 레벨 난수) 뒤 다가가
// 5칸 안에서 20틱이 지나면 선물을 던진다. 아기는 양귀비, 직업이 있으면 gameplay/hero_of_the_village/<직업>_gift 표(GIFT), 나머지는 밀 씨앗.
package org.domi.furnace.server.entity.ai.behavior;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.effect.MobEffect;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.entity.Villager;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;
import org.domi.furnace.server.entity.ai.brain.MemoryStatus;

public class GiveGiftToHero extends Behavior<Villager> {
    private static final int THROW_GIFT_AT_DISTANCE = 5, MIN_TIME_BETWEEN_GIFTS = 600, MAX_TIME_BETWEEN_GIFTS = 6600, TIME_TO_DELAY_FOR_HEAD_TO_FINISH_TURNING = 20;
    private static final float SPEED_MODIFIER = 0.5F;
    /** 선물 표가 있는 직업(무직·바보 제외). */
    private static final List<String> GIFT_PROFESSIONS = List.of("armorer", "butcher", "cartographer", "cleric", "farmer", "fisherman", "fletcher",
            "leatherworker", "librarian", "mason", "shepherd", "toolsmith", "weaponsmith");

    private int timeUntilNextGift = MIN_TIME_BETWEEN_GIFTS;
    private boolean giftGivenDuringThisRun;
    private long timeSinceStart;

    public GiveGiftToHero(int duration) {
        super(Map.of(MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED, MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED,
                MemoryModuleType.INTERACTION_TARGET, MemoryStatus.REGISTERED, MemoryModuleType.NEAREST_VISIBLE_PLAYER, MemoryStatus.VALUE_PRESENT), duration);
    }

    @Override
    protected boolean checkExtraStartConditions(ChunkLevel level, Villager v) {
        if (!isHeroVisible(v)) return false;
        if (timeUntilNextGift > 0) {
            timeUntilNextGift--;
            return false;
        }
        return true;
    }

    @Override
    protected void start(ChunkLevel level, Villager v, long time) {
        giftGivenDuringThisRun = false;
        timeSinceStart = time;
        Player p = getNearestTargetableHero(v).get();
        v.getBrain().setMemory(MemoryModuleType.INTERACTION_TARGET, p);
        BehaviorUtils.lookAtEntity(v, p);
    }

    @Override
    protected boolean canStillUse(ChunkLevel level, Villager v, long time) { return isHeroVisible(v) && !giftGivenDuringThisRun; }

    @Override
    protected void tick(ChunkLevel level, Villager v, long time) {
        Player p = getNearestTargetableHero(v).get();
        BehaviorUtils.lookAtEntity(v, p);
        if (isWithinThrowingDistance(v, p)) {
            if (time - timeSinceStart > TIME_TO_DELAY_FOR_HEAD_TO_FINISH_TURNING) {
                throwGift(level, v, p);
                giftGivenDuringThisRun = true;
            }
        } else {
            BehaviorUtils.setWalkAndLookTargetMemories(v, p, SPEED_MODIFIER, THROW_GIFT_AT_DISTANCE);
        }
    }

    @Override
    protected void stop(ChunkLevel level, Villager v, long time) {
        timeUntilNextGift = calculateTimeUntilNextGift(level);
        v.getBrain().eraseMemory(MemoryModuleType.INTERACTION_TARGET);
        v.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        v.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
    }

    private void throwGift(ChunkLevel level, Villager v, Player p) {
        for (ItemStack s : getItemToThrow(level, v)) BehaviorUtils.throwItem(v, s, new double[] {p.x(), p.y(), p.z()});
    }

    private static List<ItemStack> getItemToThrow(ChunkLevel level, Villager v) {
        if (v.isBaby()) return List.of(B.stack("minecraft:poppy", 1));
        String prof = v.profession();
        if (GIFT_PROFESSIONS.contains(prof)) return level.giftLoot("minecraft:gameplay/hero_of_the_village/" + prof + "_gift", v.lootEntity(), v.x(), v.y(), v.z());
        return List.of(B.stack("minecraft:wheat_seeds", 1));
    }

    private static boolean isHeroVisible(Villager v) { return getNearestTargetableHero(v).isPresent(); }

    private static Optional<Player> getNearestTargetableHero(Villager v) {
        return v.getBrain().getMemory(MemoryModuleType.NEAREST_VISIBLE_PLAYER).filter(p -> p.vitals().hasEffect(MobEffect.HERO_OF_THE_VILLAGE));
    }

    /** 블록 위치끼리 거리 < 5. */
    private static boolean isWithinThrowingDistance(Villager v, Player p) {
        Pos a = p.blockPosition(), b = v.blockPosition();
        long dx = a.x() - b.x(), dy = a.y() - b.y(), dz = a.z() - b.z();
        return dx * dx + dy * dy + dz * dz < (double) THROW_GIFT_AT_DISTANCE * THROW_GIFT_AT_DISTANCE;
    }

    private static int calculateTimeUntilNextGift(ChunkLevel level) { return MIN_TIME_BETWEEN_GIFTS + level.random().nextInt(MAX_TIME_BETWEEN_GIFTS + 1 - MIN_TIME_BETWEEN_GIFTS); }
}
