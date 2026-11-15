package org.domi.furnace.compat.nms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.domi.furnace.test.FurnaceTestServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** N2b-6: FAWE PaperweightBlockMaterial 이 읽는 BlockState 술어가 모든 상태에서 던지지 않고 바닐라 1.21.1 값을 주는지. */
class NmsBlockMaterialTest {

    private static FurnaceTestServer server;

    @BeforeAll
    static void start() { server = FurnaceTestServer.builder().start(); }

    @AfterAll
    static void stop() { server.close(); }

    private static final EmptyBlockGetter G = EmptyBlockGetter.INSTANCE;
    private static final BlockPos Z = BlockPos.ZERO;

    private static BlockState s(Block b) { return b.defaultBlockState(); }

    @Test
    void everyStateComputes() {
        int n = 0, full = 0;
        for (int id = 0; Block.stateById(id) != null; id++) {
            BlockState st = Block.stateById(id);
            if (st.furnaceStateId() != id) break;
            int lb = st.getLightBlock(G, Z);
            assertTrue(lb >= 0 && lb <= 15, "lightBlock " + st);
            VoxelShape shape = st.getShape(G, Z);
            if (Block.isShapeFullBlock(shape)) full++;
            st.canOcclude(); st.isSignalSource(); st.isSolidRender(G, Z); st.getLightEmission(); st.getPistonPushReaction();
            st.blocksMotion(); st.canBeReplaced(); st.getFluidState().is(Fluids.EMPTY); st.getBlock().getFriction();
            st.getBlock().getExplosionResistance(); st.getBlock().defaultMapColor();
            // 바닐라 캐시: solidRender → canOcclude, 꽉 찬 가림이면 lightBlock 15
            if (st.isSolidRender(G, Z)) { assertTrue(st.canOcclude()); assertEquals(15, lb, st.toString()); }
            n++;
            if (n > 30000) break;
        }
        assertEquals(26684, n);
        assertTrue(full > 1000, "꽉 찬 모양 상태 수 " + full);
    }

    @Test
    void stone() {
        BlockState st = s(Blocks.STONE);
        assertEquals(15, st.getLightBlock(G, Z));
        assertTrue(st.isSolidRender(G, Z));
        assertTrue(st.canOcclude());
        assertTrue(Block.isShapeFullBlock(st.getShape(G, Z)));
        assertEquals(6.0f, Blocks.STONE.getExplosionResistance());
        assertEquals(0.6f, Blocks.STONE.getFriction());
        assertEquals(1.5f, st.destroySpeed);
        assertEquals(0x707070, Blocks.STONE.defaultMapColor().col);
        assertSame(MapColor.STONE, Blocks.STONE.defaultMapColor());
        assertEquals(11, MapColor.STONE.id);
        assertTrue(st.blocksMotion());
        assertFalse(st.canBeReplaced());
        assertFalse(st.isSignalSource());
    }

    @Test
    void transparentAndFluids() {
        BlockState glass = s(Blocks.GLASS);
        assertEquals(0, glass.getLightBlock(G, Z));
        assertFalse(glass.isSolidRender(G, Z));
        assertFalse(glass.canOcclude());
        assertTrue(Block.isShapeFullBlock(glass.getShape(G, Z)));
        BlockState water = s(Blocks.WATER);
        assertEquals(1, water.getLightBlock(G, Z));
        assertFalse(water.getFluidState().is(Fluids.EMPTY));
        assertTrue(water.canBeReplaced());
        assertFalse(water.blocksMotion());
        assertEquals(PushReaction.DESTROY, water.getPistonPushReaction());
        assertEquals(1, s(Blocks.OAK_LEAVES).getLightBlock(G, Z));
        BlockState air = s(Blocks.AIR);
        assertTrue(air.canBeReplaced());
        assertEquals(0, air.getLightBlock(G, Z));
        assertTrue(air.getFluidState().is(Fluids.EMPTY));
        assertTrue(air.getShape(G, Z).isEmpty());
    }

    @Test
    void blockProperties() {
        assertEquals(0.98f, Blocks.ICE.getFriction());
        assertEquals(0.8f, Blocks.SLIME_BLOCK.getFriction());
        assertEquals(PushReaction.NORMAL, s(Blocks.OBSIDIAN).getPistonPushReaction());   // 바닐라: 흑요석은 피스톤 쪽 하드코딩으로 못 민다(속성 반응은 NORMAL)
        assertEquals(1200.0f, Blocks.OBSIDIAN.getExplosionResistance());
        assertEquals(PushReaction.BLOCK, s(Blocks.PISTON_HEAD).getPistonPushReaction());
        assertEquals(PushReaction.DESTROY, s(Blocks.TORCH).getPistonPushReaction());
        assertEquals(15, s(Blocks.GLOWSTONE).getLightEmission());
        assertEquals(-1.0f, s(Blocks.BEDROCK).destroySpeed);
    }

    @Test
    void signalSources() {
        assertTrue(s(Blocks.REDSTONE_WIRE).isSignalSource());
        assertTrue(s(Blocks.REDSTONE_TORCH).isSignalSource());
        assertTrue(s(Blocks.LEVER).isSignalSource());
        assertTrue(s(Blocks.REPEATER).isSignalSource());
        assertTrue(s(Blocks.COMPARATOR).isSignalSource());
        assertTrue(s(Blocks.OBSERVER).isSignalSource());
        assertTrue(s(Blocks.REDSTONE_BLOCK).isSignalSource());
        assertTrue(s(Blocks.JUKEBOX).isSignalSource());
        assertTrue(s(Blocks.TRIPWIRE_HOOK).isSignalSource());
        assertTrue(s(Blocks.STONE_BUTTON).isSignalSource());
        assertTrue(s(Blocks.STONE_PRESSURE_PLATE).isSignalSource());
        assertFalse(s(Blocks.REDSTONE_LAMP).isSignalSource());
        assertFalse(s(Blocks.PISTON).isSignalSource());
    }
}
