package org.domi.furnace.compat.nms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.WaterFluid;
import org.domi.furnace.test.FurnaceTestServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * N0 4부 유체: Fluids·FluidState 상태 표, 레거시 level 변환, BlockState.getFluidState, FluidTags.
 * 상태 표 순서·기본 상태(표의 첫 상태 = falling=true)는 바닐라 1.21.1 epd 생성자(dtd.b() = 첫 상태)와 dtt 값 순서 [true, false] 에서 옮겼다.
 */
class NmsFluidsTest {

    private static FurnaceTestServer server;

    @BeforeAll
    static void start() { server = FurnaceTestServer.builder().start(); }

    @AfterAll
    static void stop() { server.close(); }

    @Test
    void registryOrderMatchesVanilla() {
        assertEquals(List.of(Fluids.EMPTY, Fluids.FLOWING_WATER, Fluids.WATER, Fluids.FLOWING_LAVA, Fluids.LAVA),
                List.of(BuiltInRegistries.FLUID.byId(0), BuiltInRegistries.FLUID.byId(1), BuiltInRegistries.FLUID.byId(2),
                        BuiltInRegistries.FLUID.byId(3), BuiltInRegistries.FLUID.byId(4)));
        assertEquals(ResourceLocation.withDefaultNamespace("flowing_lava"), BuiltInRegistries.FLUID.getKey(Fluids.FLOWING_LAVA));
        assertSame(Fluids.EMPTY, BuiltInRegistries.FLUID.get(ResourceLocation.withDefaultNamespace("honey")), "기본값은 empty");
        assertSame(Fluids.WATER, Fluids.WATER.builtInRegistryHolder().value());
    }

    @Test
    void stateTablesAndValues() {
        assertEquals(1, Fluids.EMPTY.getStateDefinition().getPossibleStates().size());
        assertEquals(2, Fluids.WATER.getStateDefinition().getPossibleStates().size());
        assertEquals(16, Fluids.FLOWING_WATER.getStateDefinition().getPossibleStates().size());
        FluidState def = Fluids.FLOWING_WATER.defaultFluidState();
        assertTrue(def.getValue(FlowingFluid.FALLING), "기본 상태는 표의 첫 상태(falling=true)");
        assertEquals(1, def.getValue(FlowingFluid.LEVEL));
        assertTrue(Fluids.WATER.defaultFluidState().getValue(FlowingFluid.FALLING));

        FluidState f = Fluids.WATER.getFlowing(3, false);
        assertSame(f, Fluids.FLOWING_WATER.getFlowing(3, false), "같은 표의 같은 객체");
        assertSame(Fluids.FLOWING_WATER, f.getType());
        assertEquals(3, f.getAmount());
        assertEquals(3 / 9.0F, f.getOwnHeight());
        assertFalse(f.isSource());
        FluidState src = Fluids.LAVA.getSource(false);
        assertTrue(src.isSource());
        assertTrue(src.isSourceOfType(Fluids.LAVA));
        assertFalse(src.isSourceOfType(Fluids.WATER));
        assertEquals(8, src.getAmount());
        assertEquals(8 / 9.0F, src.getOwnHeight());
        assertTrue(Fluids.EMPTY.defaultFluidState().isEmpty());
        assertEquals(0, Fluids.EMPTY.defaultFluidState().getAmount());
        assertTrue(src.isRandomlyTicking(), "용암만 무작위 틱");
        assertFalse(Fluids.WATER.getSource(false).isRandomlyTicking());
        assertEquals(100.0F, src.getExplosionResistance());
        assertEquals(0.0F, Fluids.EMPTY.defaultFluidState().getExplosionResistance());
        assertTrue(Fluids.WATER.isSame(Fluids.FLOWING_WATER));
        assertFalse(Fluids.WATER.isSame(Fluids.LAVA));
        assertTrue(Fluids.EMPTY.isSame(Fluids.EMPTY));
        assertEquals("minecraft:water_bucket", Fluids.WATER.getBucket().furnaceKey());
        assertEquals("minecraft:lava_bucket", Fluids.FLOWING_LAVA.getBucket().furnaceKey());
        assertEquals("minecraft:air", Fluids.EMPTY.getBucket().furnaceKey());
        assertTrue(Fluids.WATER instanceof WaterFluid.Source);
    }

    @Test
    void legacyBlockLevels() {
        assertEquals(0, level(Fluids.WATER.getSource(false).createLegacyBlock()));
        assertEquals(5, level(Fluids.WATER.getFlowing(3, false).createLegacyBlock()), "8 - 3");
        assertEquals(8, level(Fluids.WATER.getFlowing(8, true).createLegacyBlock()), "8 - 8 + 8");
        assertEquals(14, level(Fluids.LAVA.getFlowing(2, true).createLegacyBlock()), "8 - 2 + 8");
        assertSame(Blocks.LAVA, Fluids.LAVA.getFlowing(2, true).createLegacyBlock().getBlock());
        assertSame(Blocks.AIR.defaultBlockState(), Fluids.EMPTY.defaultFluidState().createLegacyBlock());
    }

    @Test
    void blockStatesCarryFluids() {
        assertSame(Fluids.WATER.getSource(false), Blocks.WATER.defaultBlockState().getFluidState());
        assertSame(Fluids.WATER.getFlowing(5, false), withLevel(Blocks.WATER, 3).getFluidState());
        assertSame(Fluids.WATER.getFlowing(8, true), withLevel(Blocks.WATER, 8).getFluidState());
        assertSame(Fluids.WATER.getFlowing(8, true), withLevel(Blocks.WATER, 15).getFluidState(), "level 8..15 는 모두 떨어지는 8");
        assertSame(Fluids.LAVA.getSource(false), Blocks.LAVA.defaultBlockState().getFluidState());
        for (int lv = 0; lv <= 8; lv++) {
            BlockState s = withLevel(Blocks.WATER, lv);
            assertSame(s, s.getFluidState().createLegacyBlock(), "level " + lv + " 왕복");
        }
        assertSame(Fluids.EMPTY.defaultFluidState(), Blocks.STONE.defaultBlockState().getFluidState());
        assertSame(Fluids.WATER.getSource(false), Blocks.KELP.defaultBlockState().getFluidState());
        assertSame(Fluids.WATER.getSource(false), Blocks.BUBBLE_COLUMN.defaultBlockState().getFluidState());
        BlockState slab = Blocks.OAK_SLAB.defaultBlockState();
        @SuppressWarnings("unchecked")
        Property<Boolean> wl = (Property<Boolean>) Blocks.OAK_SLAB.getStateDefinition().getProperty("waterlogged");
        assertSame(Fluids.EMPTY.defaultFluidState(), slab.setValue(wl, false).getFluidState());
        assertSame(Fluids.WATER.getSource(false), slab.setValue(wl, true).getFluidState());
        BlockState grate = Blocks.COPPER_GRATE.defaultBlockState();
        @SuppressWarnings("unchecked")
        Property<Boolean> gwl = (Property<Boolean>) Blocks.COPPER_GRATE.getStateDefinition().getProperty("waterlogged");
        assertSame(Fluids.WATER.getSource(true), grate.setValue(gwl, true).getFluidState(), "구리 격자만 떨어지는 원천(바닐라 dpd 의 getSource(true))");
    }

    @Test
    void fluidTags() {
        assertTrue(Fluids.FLOWING_WATER.defaultFluidState().is(FluidTags.WATER));
        assertTrue(Fluids.WATER.getSource(false).is(FluidTags.WATER));
        assertFalse(Fluids.LAVA.getSource(false).is(FluidTags.WATER));
        assertTrue(Fluids.FLOWING_LAVA.defaultFluidState().is(FluidTags.LAVA));
        assertFalse(Fluids.EMPTY.defaultFluidState().is(FluidTags.WATER));
        assertTrue(Blocks.WATER.defaultBlockState().getFluidState().is(FluidTags.WATER));
        assertEquals(List.of(FluidTags.WATER), Fluids.WATER.getSource(true).getTags().toList());
        assertTrue(Fluids.LAVA.getSource(false).is(BuiltInRegistries.FLUID.getOrCreateTag(FluidTags.LAVA)));
        assertFalse(Fluids.WATER.getSource(false).is(BuiltInRegistries.FLUID.getOrCreateTag(FluidTags.LAVA)));
        assertEquals("TagKey[minecraft:fluid / minecraft:water]", FluidTags.WATER.toString());
    }

    @SuppressWarnings("unchecked")
    private static Property<Integer> levelOf(Block b) { return (Property<Integer>) b.getStateDefinition().getProperty("level"); }

    private static int level(BlockState s) { return s.getValue(levelOf(s.getBlock())); }

    private static BlockState withLevel(Block b, int lv) { return b.defaultBlockState().setValue(levelOf(b), lv); }
}
