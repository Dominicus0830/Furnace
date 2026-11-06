// 바닐라 CelebrateVillagersSurvivedRaid(600, 600): 이긴 레이드 안에서 하늘이 보이면 축하한다.
// 틱마다 엔티티 난수 1/100 로 축하 소리, 1/200 로(하늘이 보이면) 무작위 염료 색 BURST 폭죽(비행 0~2)을 눈높이에서 쏜다.
// 폭죽 소유자는 쏜 주민(바닐라 new FireworkRocketEntity(level, owner, x, y, z, stack)) — 소유자는 처음엔 안 맞아 몸통에서 터지지 않는다.
package org.domi.furnace.server.entity.ai.behavior;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.entity.DyeColor;
import org.domi.furnace.server.entity.FireworkRocketEntity;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.entity.Villager;
import org.domi.furnace.server.item.ItemTools;
import org.domi.furnace.server.recipe.SpecialRecipes;
import org.domi.furnace.server.world.raid.Raid;

public class CelebrateVillagersSurvivedRaid extends Behavior<Villager> {
    private Raid currentRaid;

    public CelebrateVillagersSurvivedRaid(int minDuration, int maxDuration) { super(Map.of(), minDuration, maxDuration); }

    @Override
    protected boolean checkExtraStartConditions(ChunkLevel level, Villager v) {
        currentRaid = VillagerRaidBehaviors.raidAt(level, v);
        return currentRaid != null && currentRaid.isVictory() && VillagerRaidBehaviors.hasNoBlocksAbove(level, v, v.blockPosition());
    }

    @Override
    protected boolean canStillUse(ChunkLevel level, Villager v, long time) { return currentRaid != null && !currentRaid.isStopped(); }

    @Override
    protected void stop(ChunkLevel level, Villager v, long time) {
        currentRaid = null;
        v.getBrain().updateActivityFromSchedule(level.dayTime(), level.gameTime());
    }

    @Override
    protected void tick(ChunkLevel level, Villager v, long time) {
        var r = v.random();
        if (r.nextInt(100) == 0) v.playCelebrateSound();
        if (r.nextInt(200) == 0 && VillagerRaidBehaviors.hasNoBlocksAbove(level, v, v.blockPosition())) {
            DyeColor color = DyeColor.values()[r.nextInt(DyeColor.values().length)];
            int flight = r.nextInt(3);
            ItemStack rocket = getFirework(color, flight);
            level.addFreshEntity(new FireworkRocketEntity(level, level.nextEntityId(), v, v.x(), v.eyeY(), v.z(), rocket));
        }
    }

    /** getFirework: 폭죽 로켓 하나(비행 시간, BURST 폭발 하나, 색 하나, 흐려짐·꼬리·반짝임 없음). */
    public static ItemStack getFirework(DyeColor color, int flight) {
        Map<String, Object> explosion = new LinkedHashMap<>();
        explosion.put("shape", "burst");
        explosion.put("colors", List.of(SpecialRecipes.FIREWORK[color.id()]));
        explosion.put("fade_colors", List.of());
        explosion.put("has_trail", false);
        explosion.put("has_twinkle", false);
        Map<String, Object> fw = new LinkedHashMap<>();
        fw.put("flight_duration", flight);
        fw.put("explosions", List.of(explosion));
        return ItemTools.with(B.stack("minecraft:firework_rocket", 1), "minecraft:fireworks", fw);
    }
}
