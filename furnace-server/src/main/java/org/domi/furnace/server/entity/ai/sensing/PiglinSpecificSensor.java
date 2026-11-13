// 바닐라 PiglinSpecificSensor: 피글린이 주목할 것들 — 기피 블록, 호글린, 어른·아기 피글린, 금을 안 두른/금을 든 플레이어, 숙적(위더), 좀비화된 것들.
package org.domi.furnace.server.entity.ai.sensing;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.AbstractPiglin;
import org.domi.furnace.server.entity.Entity;
import org.domi.furnace.server.entity.EntityType;
import org.domi.furnace.server.entity.Hoglin;
import org.domi.furnace.server.entity.Mob;
import org.domi.furnace.server.entity.Piglin;
import org.domi.furnace.server.entity.PiglinAi;
import org.domi.furnace.server.entity.PiglinBrute;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.entity.Targetable;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;
import org.domi.furnace.server.entity.ai.brain.NearestVisibleLivingEntities;
import org.domi.furnace.server.entity.ai.brain.Sensor;
import org.domi.furnace.server.world.GameMode;
import org.domi.furnace.server.world.gen.feature.BlockPosIter;

public class PiglinSpecificSensor extends Sensor<Mob> {
    @Override
    public Set<MemoryModuleType<?>> requires() {
        return Set.of(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES, MemoryModuleType.NEAREST_LIVING_ENTITIES, MemoryModuleType.NEAREST_VISIBLE_NEMESIS,
                MemoryModuleType.NEAREST_TARGETABLE_PLAYER_NOT_WEARING_GOLD, MemoryModuleType.NEAREST_PLAYER_HOLDING_WANTED_ITEM, MemoryModuleType.NEAREST_VISIBLE_HUNTABLE_HOGLIN,
                MemoryModuleType.NEAREST_VISIBLE_BABY_HOGLIN, MemoryModuleType.NEARBY_ADULT_PIGLINS, MemoryModuleType.NEAREST_VISIBLE_ADULT_PIGLINS, MemoryModuleType.VISIBLE_ADULT_PIGLIN_COUNT,
                MemoryModuleType.VISIBLE_ADULT_HOGLIN_COUNT, MemoryModuleType.NEAREST_REPELLENT);
    }

    @Override
    protected void doTick(ChunkLevel level, Mob owner) {
        var brain = owner.getBrain();
        brain.setMemory(MemoryModuleType.NEAREST_REPELLENT, findNearestRepellent(level, owner));
        Optional<Mob> nemesis = Optional.empty();
        Optional<Mob> huntable = Optional.empty();
        Optional<Mob> babyHoglin = Optional.empty();
        Optional<Targetable> zombified = Optional.empty();
        Optional<Player> notWearingGold = Optional.empty();
        Optional<Player> holdingWanted = Optional.empty();
        int hoglinCount = 0;
        List<Mob> visibleAdults = new ArrayList<>();
        List<Mob> nearbyAdults = new ArrayList<>();
        NearestVisibleLivingEntities visible = brain.getMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES).orElse(NearestVisibleLivingEntities.empty());
        for (Targetable t : visible.findAll(x -> true)) {
            if (t instanceof Hoglin h) {
                if (h.isBaby() && babyHoglin.isEmpty()) {
                    babyHoglin = Optional.of(h);
                } else if (h.isAdult()) {
                    hoglinCount++;
                    if (huntable.isEmpty() && h.canBeHunted()) huntable = Optional.of(h);
                }
            } else if (t instanceof PiglinBrute b) {
                visibleAdults.add(b);
            } else if (t instanceof Piglin p) {
                if (!p.isBaby() && p.isAdult()) visibleAdults.add(p);
            } else if (t instanceof Player pl) {
                if (notWearingGold.isEmpty() && !PiglinAi.wearsGoldArmor(pl) && pl.canBeSeenAsEnemy() && owner.canAttackType(pl)) notWearingGold = Optional.of(pl);
                if (holdingWanted.isEmpty() && pl.gameMode() != GameMode.SPECTATOR && PiglinAi.isPlayerHoldingLovedItem(pl)) holdingWanted = Optional.of(pl);
            } else if (t instanceof Entity e) {
                if (nemesis.isEmpty() && (e.type() == EntityType.WITHER_SKELETON || e.type() == EntityType.WITHER) && e instanceof Mob m) nemesis = Optional.of(m);
                else if (zombified.isEmpty() && PiglinAi.isZombified(e.type())) zombified = Optional.of(t);
            }
        }
        for (Targetable t : brain.getMemory(MemoryModuleType.NEAREST_LIVING_ENTITIES).orElse(List.of())) {
            if (t instanceof AbstractPiglin ap && ap.isAdult()) nearbyAdults.add(ap);
        }
        brain.setMemory(MemoryModuleType.NEAREST_VISIBLE_NEMESIS, nemesis);
        brain.setMemory(MemoryModuleType.NEAREST_VISIBLE_HUNTABLE_HOGLIN, huntable);
        brain.setMemory(MemoryModuleType.NEAREST_VISIBLE_BABY_HOGLIN, babyHoglin);
        brain.setMemory(MemoryModuleType.NEAREST_VISIBLE_ZOMBIFIED, zombified);
        brain.setMemory(MemoryModuleType.NEAREST_TARGETABLE_PLAYER_NOT_WEARING_GOLD, notWearingGold);
        brain.setMemory(MemoryModuleType.NEAREST_PLAYER_HOLDING_WANTED_ITEM, holdingWanted);
        brain.setMemory(MemoryModuleType.NEARBY_ADULT_PIGLINS, nearbyAdults);
        brain.setMemory(MemoryModuleType.NEAREST_VISIBLE_ADULT_PIGLINS, visibleAdults);
        brain.setMemory(MemoryModuleType.VISIBLE_ADULT_PIGLIN_COUNT, visibleAdults.size());
        brain.setMemory(MemoryModuleType.VISIBLE_ADULT_HOGLIN_COUNT, hoglinCount);
    }

    private static Optional<Pos> findNearestRepellent(ChunkLevel level, Mob owner) {
        Pos here = owner.blockPosition();
        Pos[] found = new Pos[1];
        BlockPosIter.withinManhattan(here.x(), here.y(), here.z(), 8, 4, 8, (x, y, z) -> {
            Pos p = new Pos(x, y, z);
            int state = level.getBlockState(p);
            boolean repellent = B.isTag(state, "minecraft:piglin_repellents");
            if (repellent && B.is(state, "minecraft:soul_campfire")) repellent = org.domi.furnace.server.block.types.CampfireBlock.isLitCampfire(state);
            if (repellent) {
                found[0] = p;
                return false;
            }
            return true;
        });
        return Optional.ofNullable(found[0]);
    }
}
