// 주민 문 행동: 코어 InteractWithDoor 가 닫힌 나무문을 열고 지나가 다시 닫는다. 철문은 열지 않고 길도 막힌다.
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.types.DoorBlock;
import org.domi.furnace.server.entity.ai.behavior.InteractWithDoor;
import org.domi.furnace.server.entity.ai.brain.GlobalPos;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;
import org.domi.furnace.server.entity.ai.brain.WalkTarget;
import org.domi.furnace.server.world.BlockRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class VillagerDoorTest {
    private static final BlockRegistry REG = BlockRegistry.load("blocks/1.21.1.txt");
    private static final Pos DOOR = new Pos(10, -60, 10);

    @BeforeAll
    static void install() {
        org.domi.furnace.server.item.ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
    }

    private static VillagerBrainTest.Clocked wall(String door) {
        var level = new VillagerBrainTest.Clocked();
        for (int x = 0; x < 24; x++) for (int z = 0; z < 24; z++) level.setBlock(new Pos(x, -61, z), B.defaultState("minecraft:grass_block"), 3);
        for (int z = -30; z <= 40; z++) for (int y = -60; y <= -58; y++) level.setBlock(new Pos(10, y, z), B.defaultState("minecraft:stone"), 3);
        for (String half : new String[] {"lower", "upper"}) {
            level.setBlock(new Pos(10, half.equals("lower") ? -60 : -59, 10), REG.stateIdOrAir(door,
                    Map.of("facing", "west", "half", half, "hinge", "left", "open", "false", "powered", "false")), 3);
        }
        return level;
    }

    private static boolean open(VillagerBrainTest.Clocked level) {
        int s = level.getBlockState(DOOR);
        return ((DoorBlock) B.behavior(s)).isOpen(s);
    }

    private static Villager villager(VillagerBrainTest.Clocked level) {
        var v = new Villager(level, level.nextEntityId());
        v.moveTo(8.5, -60, 10.5, 0, 0);
        level.addFreshEntity(v);
        v.setOnGround(true);
        walk(v);
        return v;
    }

    /** 한가 행동(산책 등)이 목표를 바꾸지 못하게 매 틱 같은 목표를 다시 건다(난수 의존 제거). */
    private static void walk(Villager v) {
        v.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(new Pos(16, -60, 10), 0.5F, 1));
    }

    @Test
    void villagerOpensWoodenDoorWalksThroughAndClosesIt() {
        var level = wall("minecraft:oak_door");
        var v = villager(level);
        boolean opened = false;
        for (int i = 0; i < 200; i++) {
            walk(v);
            level.step(v);
            opened |= open(level);
        }
        assertTrue(opened, "지나가는 동안 문이 열렸다");
        assertTrue(v.x() > 12.0, "문을 지나갔다 x=" + v.x());
        assertTrue(v.getBrain().getMemory(MemoryModuleType.DOORS_TO_CLOSE).orElseThrow().isEmpty(), "멀어지면 닫을 문 기억에서 빠진다");
    }

    private static java.util.Set<GlobalPos> doorSet(VillagerBrainTest.Clocked level) {
        var set = new java.util.HashSet<GlobalPos>();
        set.add(new GlobalPos(level.dimension(), DOOR));
        return set;
    }

    private static void openDoor(VillagerBrainTest.Clocked level) {
        int s = level.getBlockState(DOOR);
        ((DoorBlock) B.behavior(s)).setOpen(null, level, s, DOOR, true);
        assertTrue(open(level));
    }

    /** 바닐라 closeDoorsThatIHaveOpenedOrPassedThrough: 문 중심 3칸 안이고 곁에 같은 종류가 안 지나가면 닫고 기억에서 뺀다. */
    @Test
    void closesPassedDoorWithinThreeBlocks() {
        var level = wall("minecraft:oak_door");
        var v = villager(level);
        v.moveTo(12.0, -60, 10.5, 0, 0);   // 문 중심(10.5, -59.5, 10.5) 과 거리 sqrt(2.25+0.25)=1.58 < 3
        openDoor(level);
        var set = doorSet(level);
        InteractWithDoor.closeDoorsThatIHaveOpenedOrPassedThrough(level, v, null, null, set, java.util.Optional.of(java.util.List.of()));
        assertFalse(open(level), "닫혔다");
        assertTrue(set.isEmpty());
    }

    @Test
    void farDoorIsForgottenButLeftOpen() {
        var level = wall("minecraft:oak_door");
        var v = villager(level);
        v.moveTo(14.0, -60, 10.5, 0, 0);   // 거리 sqrt(12.25+0.25)=3.54 > 3
        openDoor(level);
        var set = doorSet(level);
        InteractWithDoor.closeDoorsThatIHaveOpenedOrPassedThrough(level, v, null, null, set, java.util.Optional.of(java.util.List.of()));
        assertTrue(open(level), "너무 멀면 닫지 않는다");
        assertTrue(set.isEmpty());
    }

    @Test
    void doorStaysOpenForAnotherVillagerComingThrough() {
        var level = wall("minecraft:oak_door");
        var v = villager(level);
        v.moveTo(12.0, -60, 10.5, 0, 0);
        var other = new Villager(level, level.nextEntityId());
        other.moveTo(9.5, -60, 10.5, 0, 0);   // 문 중심과 거리 sqrt(1+0.25)=1.12 < 2
        level.addFreshEntity(other);
        var nodes = new java.util.ArrayList<org.domi.furnace.server.entity.ai.navigation.Node>();
        nodes.add(new org.domi.furnace.server.entity.ai.navigation.Node(9, -60, 10));
        nodes.add(new org.domi.furnace.server.entity.ai.navigation.Node(10, -60, 10));
        nodes.add(new org.domi.furnace.server.entity.ai.navigation.Node(11, -60, 10));
        var path = new org.domi.furnace.server.entity.ai.navigation.Path(nodes, new Pos(11, -60, 10), true);
        path.setNextNodeIndex(2);   // prev = 문 칸
        other.getBrain().setMemory(MemoryModuleType.PATH, path);
        openDoor(level);
        var set = doorSet(level);
        InteractWithDoor.closeDoorsThatIHaveOpenedOrPassedThrough(level, v, null, null, set, java.util.Optional.of(java.util.List.of(other)));
        assertTrue(open(level), "다른 주민이 지나는 중이면 열어 둔다");
        assertTrue(set.isEmpty());
    }

    @Test
    void villagerDoesNotOpenIronDoor() {
        var level = wall("minecraft:iron_door");
        var v = villager(level);
        for (int i = 0; i < 200; i++) {
            walk(v);
            level.step(v);
        }
        assertFalse(open(level), "철문은 열지 않는다");
        assertTrue(v.x() < 10.0, "철문 너머로 못 간다 x=" + v.x());
    }
}
