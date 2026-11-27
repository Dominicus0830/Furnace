// 이식 다리 비용(설계 docs/roadmap/port-bridge-2026-11-27.md 11장): 서버 직접 호출 / pure 줄 / pos 줄(주인 스레드). 수동 실행:
//   ./gradlew -PskipOracle=true :furnace-compat-paper:compatSceneBench --tests '*PortBridgeBench*' [-Dfurnace.bench.port.ops=2000000]
// 표준 출력 PORTBRIDGE 줄: 경우마다 ns/op(다섯 번 가운데 가장 빠른 것)와 bytes/op(그 스레드가 할당한 바이트 ÷ 호출 수).
// 절대 시간으로 CI 를 막지 않는다 — 같은 기계에서 직접 호출과 견준다.
package org.domi.furnace.compat.nms.port;

import java.lang.management.ManagementFactory;
import java.util.function.IntUnaryOperator;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.domi.furnace.compat.nms.port.gen.BlockPorts;
import org.domi.furnace.compat.nms.port.gen.ItemInventoryPorts;
import org.domi.furnace.compat.paper.Compat;
import org.domi.furnace.server.block.BlockBehaviors;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.types.CropBlock;
import org.domi.furnace.server.menu.AnvilMenu;
import org.domi.furnace.test.FurnaceTestServer;
import org.domi.furnace.test.TestPlayer;
import org.junit.jupiter.api.Test;

class PortBridgeBench {

    static volatile long sink;

    @Test
    void bench() {
        int ops = Integer.getInteger("furnace.bench.port.ops", 2_000_000);
        try (FurnaceTestServer server = FurnaceTestServer.builder().failFast(false).start()) {
            Bukkit.setServer(CraftServer.INSTANCE);
            TestPlayer tp = server.join("Bench");
            server.tick(3);
            var loc = tp.player().location();
            int x = (int) Math.floor(loc.x()), z = (int) Math.floor(loc.z()), y = 150;
            ServerLevel level = ((CraftWorld) Compat.player(tp.player()).getWorld()).getHandle();
            BlockPos p = new BlockPos(x, y, z);
            var b = org.domi.furnace.server.block.Blocks.get();
            BlockState s = Block.stateById(b.prop("age").with(b.defaultState("minecraft:wheat"), "3"));
            int sid = s.furnaceStateId();
            server.onRegion(new org.domi.furnace.api.world.BlockPos(x, y, z), () -> {
                var lv = server.internals().world().regionizer().ownerOf(x >> 4, z >> 4).level();
                CropBlock crop = (CropBlock) BlockBehaviors.of(sid);
                Pos sp = new Pos(x, y, z);
                report("direct-pure", ops, i -> AnvilMenu.calculateIncreasedRepairCost(i & 63));
                report("port-pure", ops, i -> ItemInventoryPorts.AnvilMenu$calculateIncreasedRepairCost(i & 63));
                report("direct-pos", ops, i -> crop.isValidBonemealTarget(lv, sp, sid) ? 1 : 0);
                report("direct-pos-newpos", ops, i -> crop.isValidBonemealTarget(lv, new Pos(x, y, z), sid) ? 1 : 0);
                report("port-pos", ops, i -> BlockPorts.CropBlock$isValidBonemealTarget(Blocks.WHEAT, level, p, s) ? 1 : 0);
                return null;
            });
        }
    }

    static void report(String name, int ops, IntUnaryOperator op) {
        var mx = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        long tid = Thread.currentThread().threadId();
        double best = Double.MAX_VALUE, bytes = 0;
        for (int round = 0; round < 6; round++) {
            long a0 = mx.getThreadAllocatedBytes(tid), t0 = System.nanoTime(), acc = 0;
            for (int i = 0; i < ops; i++) acc += op.applyAsInt(i);
            long t1 = System.nanoTime(), a1 = mx.getThreadAllocatedBytes(tid);
            sink += acc;
            if (round == 0) continue;   // 첫 판은 예열
            best = Math.min(best, (t1 - t0) / (double) ops);
            bytes = (a1 - a0) / (double) ops;
        }
        System.out.printf("PORTBRIDGE %-18s ns/op=%.2f bytes/op=%.2f%n", name, best, bytes);
    }
}
