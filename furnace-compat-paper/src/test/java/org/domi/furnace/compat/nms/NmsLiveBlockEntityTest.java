package org.domi.furnace.compat.nms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BeaconBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.domi.furnace.compat.paper.Compat;
import org.domi.furnace.test.FurnaceTestServer;
import org.domi.furnace.test.TestPlayer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/** N2b-6: BlockEntity.furnaceInWorld — 월드에 놓인 블록 엔티티의 살아 있는 보기(소유 스레드·다른 스레드 모두). */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NmsLiveBlockEntityTest {

    FurnaceTestServer server;
    ServerLevel level;
    org.domi.furnace.api.world.BlockPos at;

    @BeforeAll
    void start() {
        server = FurnaceTestServer.builder().failFast(false).start();
        Bukkit.setServer(CraftServer.INSTANCE);
        TestPlayer tp = server.join("Beacony");
        server.tick(3);
        var loc = tp.player().location();
        at = new org.domi.furnace.api.world.BlockPos((int) Math.floor(loc.x()), (int) Math.floor(loc.y()) + 5, (int) Math.floor(loc.z()));
        level = ((CraftWorld) Compat.player(tp.player()).getWorld()).getHandle();
    }

    @AfterAll
    void stop() { server.close(); }

    @Test
    void liveViewOnOwnerAndOtherThread() throws Exception {
        BlockPos pos = new BlockPos(at.x(), at.y(), at.z());
        int id = Blocks.BEACON.defaultBlockState().furnaceStateId();
        String saved = server.onRegion(at, () -> {
            level.setBlock(pos, Blocks.BEACON.defaultBlockState(), 3);
            BlockEntity be = BlockEntity.furnaceInWorld(level, pos, id);
            assertTrue(be instanceof BeaconBlockEntity);
            assertTrue(be.hasLevel());
            var tag = be.saveWithId(MinecraftServer.getServer().registryAccess());
            assertEquals("minecraft:beacon", tag.getString("id"));
            return tag.toString();
        });
        // 다른 스레드(시험 스레드)에서: 서버 future 로 읽는다
        var thread = new java.util.concurrent.atomic.AtomicReference<String>();
        var t = new Thread(() -> {
            BlockEntity be = BlockEntity.furnaceInWorld(level, pos, id);
            thread.set(be.saveWithId(MinecraftServer.getServer().registryAccess()).toString());
        });
        t.start();
        for (int i = 0; i < 200 && t.isAlive(); i++) { server.tick(1); t.join(50); }
        assertEquals(saved, thread.get());
    }
}
