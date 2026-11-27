package org.domi.furnace.compat.nms.port;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.domi.furnace.api.concurrent.ThreadOwnershipException;
import org.domi.furnace.compat.nms.UnsupportedNmsCall;
import org.domi.furnace.compat.nms.port.gen.BlockPorts;
import org.domi.furnace.compat.nms.port.gen.ItemInventoryPorts;
import org.domi.furnace.compat.paper.Compat;
import org.domi.furnace.server.block.BlockBehaviors;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.types.CropBlock;
import org.domi.furnace.test.FurnaceTestServer;
import org.domi.furnace.test.TestPlayer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * 이식 다리 P1 실제 줄(docs/roadmap/port-bridge-2026-11-27.md 15장): 호환 스텁(연결 몸통이 부르는 것)으로 부른 작물 블록 동작이
 * 같은 세계에서 서버 블록 동작을 직접 부른 값과 같은가 — 같은 씨앗 난수면 같은 성장과 같은 난수 소비. 주인 아닌 스레드의 규칙도 본다.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PortBridgeCropTest {

    FurnaceTestServer server;
    ServerLevel level;
    int baseX, baseZ;
    static final int Y = 150;

    @BeforeAll
    void start() {
        server = FurnaceTestServer.builder().failFast(false).start();
        Bukkit.setServer(CraftServer.INSTANCE);
        TestPlayer tp = server.join("Porter");
        server.tick(3);
        var loc = tp.player().location();
        baseX = Math.floorDiv((int) Math.floor(loc.x()), 16) * 16;
        baseZ = Math.floorDiv((int) Math.floor(loc.z()), 16) * 16;
        level = ((CraftWorld) Compat.player(tp.player()).getWorld()).getHandle();
        server.execute("forceload add " + baseX + " " + baseZ + " " + (baseX + 15) + " " + (baseZ + 15));
        server.execute("gamerule randomTickSpeed 0");   // 세계의 무작위 틱이 끼어들지 않게(비교는 시험이 부른 틱만)
        server.tick(5);
    }

    @AfterAll
    void stop() { server.close(); }

    <T> T on(BlockPos p, Supplier<T> task) { return server.onRegion(new org.domi.furnace.api.world.BlockPos(p.getX(), p.getY(), p.getZ()), task); }

    void run(BlockPos p, Runnable task) { on(p, () -> { task.run(); return null; }); }

    org.domi.furnace.server.world.RegionLevel serverLevel(BlockPos p) {
        return server.internals().world().regionizer().ownerOf(p.getX() >> 4, p.getZ() >> 4).level();
    }

    static BlockState named(String block, String... props) {
        var b = org.domi.furnace.server.block.Blocks.get();
        int s = b.defaultState(block);
        for (int i = 0; i < props.length; i += 2) s = b.prop(props[i]).with(s, props[i + 1]);
        return Block.stateById(s);
    }

    /** 젖은 농지 위 작물 한 칸(주변은 공기) — 두 자리가 같은 성장 속도·빛을 갖게. */
    void plant(BlockPos p, int age) {
        run(p, () -> {
            level.setBlock(p.below(), named("minecraft:farmland", "moisture", "7"), 2 | 16);
            level.setBlock(p, named("minecraft:wheat", "age", String.valueOf(age)), 2 | 16);
        });
    }

    BlockState at(BlockPos p) { return on(p, () -> level.getBlockState(p)); }

    static CropBlock crop(int state) { return (CropBlock) BlockBehaviors.of(state); }

    @Test
    void randomTickMatchesTheServerWithTheSameSeed() {
        BlockPos a = new BlockPos(baseX + 2, Y, baseZ + 2), b = new BlockPos(baseX + 8, Y, baseZ + 2);
        plant(a, 0);
        plant(b, 0);
        server.tick(2);
        RandomSource ra = RandomSource.create(20261127L);
        org.domi.furnace.server.block.RandomSource rb = org.domi.furnace.server.block.RandomSource.create(20261127L);
        for (int i = 0; i < 60; i++) {
            run(a, () -> BlockPorts.CropBlock$randomTick(Blocks.WHEAT, level.getBlockState(a), level, a, ra));
            run(b, () -> {
                var lv = serverLevel(b);
                int s = lv.getBlockState(new Pos(b.getX(), b.getY(), b.getZ()));
                crop(s).randomTick(s, lv, new Pos(b.getX(), b.getY(), b.getZ()), rb);
            });
            assertEquals(at(b).furnaceStateId(), at(a).furnaceStateId(), "step " + i);
        }
        assertEquals(rb.nextLong(), ra.nextLong(), "both sides drew the same number of values");
        int age = org.domi.furnace.server.block.Blocks.get().prop("age").intValue(at(a).furnaceStateId());
        assertTrue(age > 0, "the crop never grew - light or farmland is not set up, the comparison saw nothing");
    }

    @Test
    void bonemealTargetMatchesTheServerOnEveryAge() {
        BlockPos p = new BlockPos(baseX + 2, Y, baseZ + 6);
        for (int age = 0; age <= 7; age++) {
            plant(p, age);
            final int fAge = age;
            boolean viaPort = on(p, () -> BlockPorts.CropBlock$isValidBonemealTarget(Blocks.WHEAT, level, p, level.getBlockState(p)));
            boolean direct = on(p, () -> {
                int s = level.getBlockState(p).furnaceStateId();
                return crop(s).isValidBonemealTarget(serverLevel(p), new Pos(p.getX(), p.getY(), p.getZ()), s);
            });
            assertEquals(direct, viaPort, "age " + fAge);
            assertEquals(fAge < 7, viaPort, "age " + fAge);
        }
    }

    @Test
    void performBonemealGrowsInTheWorld() {
        BlockPos p = new BlockPos(baseX + 6, Y, baseZ + 6);
        plant(p, 0);
        run(p, () -> BlockPorts.CropBlock$performBonemeal(Blocks.WHEAT, level, RandomSource.create(1), p, level.getBlockState(p)));
        int age = org.domi.furnace.server.block.Blocks.get().prop("age").intValue(at(p).furnaceStateId());
        assertTrue(age >= 2 && age <= 5, "vanilla bonemeal adds 2..5 ages, got " + age);
    }

    /** 틱 스레드가 아닌 스레드(여기서는 시험 스레드)는 주인 region 에 넘겨 기다린다 — 값은 주인 스레드에서 부른 것과 같다. */
    @Test
    void offThreadCallWaitsForTheOwner() {
        BlockPos p = new BlockPos(baseX + 10, Y, baseZ + 10);
        plant(p, 3);
        BlockState s = named("minecraft:wheat", "age", "3");
        assertTrue(BlockPorts.CropBlock$isValidBonemealTarget(Blocks.WHEAT, level, p, s));
        assertEquals(false, BlockPorts.CropBlock$isValidBonemealTarget(Blocks.WHEAT, level, p, named("minecraft:wheat", "age", "7")));
    }

    /** 전역 스레드(틱 스레드)가 다른 region 의 칸을 부르면 교착을 피해 ThreadOwnershipException — NmsOwner 와 같은 규칙. */
    @Test
    void tickThreadOfAnotherRegionIsRefused() {
        BlockPos p = new BlockPos(baseX + 10, Y, baseZ + 12);
        plant(p, 1);
        BlockState s = named("minecraft:wheat", "age", "1");
        RuntimeException e = server.onGlobal(() -> {
            try {
                BlockPorts.CropBlock$isValidBonemealTarget(Blocks.WHEAT, level, p, s);
                return null;
            } catch (RuntimeException x) {
                return x;
            }
        });
        assertTrue(e instanceof ThreadOwnershipException, "expected ThreadOwnershipException, got " + e);
    }

    /** 짝이 그 서버 클래스가 아니면(돌 블록을 작물로) 조용히 다른 동작을 돌리지 않고 UnsupportedNmsCall. */
    @Test
    void wrongReceiverIsALoudError() {
        BlockPos p = new BlockPos(baseX + 12, Y, baseZ + 2);
        plant(p, 1);
        UnsupportedNmsCall e = assertThrows(UnsupportedNmsCall.class,
                () -> on(p, () -> BlockPorts.CropBlock$isValidBonemealTarget(Blocks.STONE, level, p, level.getBlockState(p))));
        assertTrue(e.getMessage().contains("CropBlock#isValidBonemealTarget") && e.getMessage().contains("minecraft:stone"), e.getMessage());
    }

    @Test
    void notAWorldLevelIsALoudError() {
        BlockPos p = new BlockPos(baseX + 12, Y, baseZ + 4);
        assertThrows(UnsupportedNmsCall.class, () -> BlockPorts.CropBlock$isValidBonemealTarget(Blocks.WHEAT, new Object(), p, named("minecraft:wheat")));
        assertThrows(NullPointerException.class, () -> BlockPorts.CropBlock$isValidBonemealTarget(Blocks.WHEAT, level, null, named("minecraft:wheat")));
    }

    @Test
    void pureStaticLineGivesTheVanillaValue() {
        for (int c : new int[] {0, 1, 7, 31, Integer.MAX_VALUE / 2 + 1, Integer.MAX_VALUE}) {
            assertEquals((int) Math.min(c * 2L + 1L, Integer.MAX_VALUE), ItemInventoryPorts.AnvilMenu$calculateIncreasedRepairCost(c), "cost " + c);
        }
    }
}
