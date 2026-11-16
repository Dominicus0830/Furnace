package org.domi.furnace.compat.nms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtBridge;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BeaconBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.domi.furnace.api.nbt.NbtCompound;
import org.domi.furnace.test.FurnaceTestServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** N2b-6: FAWE PaperweightBlockMaterial 의 EntityBlock·newBlockEntity·saveWithId 경로. */
class NmsEntityBlockTest {

    private static FurnaceTestServer server;

    @BeforeAll
    static void start() { server = FurnaceTestServer.builder().start(); }

    @AfterAll
    static void stop() { server.close(); }

    private static final BlockPos ZERO = BlockPos.ZERO;

    private static BlockEntity make(Block b) { return ((EntityBlock) b).newBlockEntity(ZERO, b.defaultBlockState()); }

    private static CompoundTag save(BlockEntity be) { return be.saveWithId(MinecraftServer.getServer().registryAccess()); }

    @Test
    void membershipMatchesServerHasBlockEntitySet() {
        var table = NmsBlockStates.table();
        int entityBlocks = 0;
        for (int id = 0; id < table.flags().length; id++) {
            BlockState st = Block.stateById(id);
            boolean has = (table.flags()[id] & org.domi.furnace.api.internal.BlockStateInternals.HAS_BLOCK_ENTITY) != 0;
            assertEquals(has, st.getBlock() instanceof EntityBlock, st.toString());
            if (has && st.getBlock().defaultBlockState() == st) entityBlocks++;
        }
        assertTrue(entityBlocks > 40, "블록 엔티티 있는 블록 수 " + entityBlocks);
        for (Block b : new Block[] {Blocks.CHEST, Blocks.FURNACE, Blocks.OAK_SIGN, Blocks.BEACON, Blocks.WHITE_BED, Blocks.SKELETON_SKULL,
                Blocks.WHITE_BANNER, Blocks.SPAWNER, Blocks.WHITE_SHULKER_BOX, Blocks.END_PORTAL, Blocks.MOVING_PISTON, Blocks.ENCHANTING_TABLE}) {
            assertTrue(b instanceof EntityBlock, b.toString());
        }
        for (Block b : new Block[] {Blocks.STONE, Blocks.AIR, Blocks.PISTON, Blocks.PISTON_HEAD, Blocks.CRAFTING_TABLE}) assertFalse(b instanceof EntityBlock, b.toString());
    }

    @Test
    void freshSaveMatchesServer() {
        var bridge = NmsBlockStates.bridge();
        for (Block b : new Block[] {Blocks.CHEST, Blocks.FURNACE, Blocks.OAK_SIGN, Blocks.BEACON, Blocks.SPAWNER, Blocks.TRAPPED_CHEST, Blocks.WHITE_BED}) {
            BlockEntity be = make(b);
            assertNotNull(be, b.toString());
            NbtCompound expected = bridge.defaultBlockEntityNbt(b.defaultBlockState().furnaceStateId());
            assertEquals(expected, NbtBridge.toFurnace(save(be)), b.toString());
            assertEquals(bridge.blockEntityType(b.defaultBlockState().furnaceStateId()), save(be).getString("id"), b.toString());
            assertFalse(be.hasLevel());
        }
        assertEquals("minecraft:chest", save(make(Blocks.CHEST)).getString("id"));
        assertNull(make(Blocks.MOVING_PISTON));   // 바닐라 MovingPistonBlock.newBlockEntity 는 null
        CompoundTag full = make(Blocks.BEACON).saveWithFullMetadata(MinecraftServer.getServer().registryAccess());
        assertEquals(0, full.getInt("x"));
        assertTrue(make(Blocks.BEACON).saveWithoutMetadata(MinecraftServer.getServer().registryAccess()).getAllKeys().stream().noneMatch(k -> k.equals("id") || k.equals("x")));
    }

    @Test
    void beaconAndRemoveField() throws Exception {
        BlockEntity be = make(Blocks.BEACON);
        assertTrue(be instanceof BeaconBlockEntity);
        assertFalse(make(Blocks.CHEST) instanceof BeaconBlockEntity);
        assertFalse(be.isRemoved());
        var f = BlockEntity.class.getDeclaredField("remove");
        f.setAccessible(true);
        f.setBoolean(be, true);
        assertTrue(be.isRemoved());
        be.clearRemoved();
        assertFalse(be.isRemoved());
        be.setRemoved();
        assertTrue(be.isRemoved());
        assertEquals(ZERO, be.getBlockPos());
        assertEquals(Blocks.BEACON.defaultBlockState(), be.getBlockState());
    }

    @Test
    void everyBlockEntityBlockMakesTheVanillaClassOfItsKind() {
        var ids = new java.util.HashSet<String>();
        for (int id = 0; id < NmsBlockStates.table().flags().length; id++) {
            BlockState st = Block.stateById(id);
            if (!(st.getBlock() instanceof EntityBlock eb) || st.getBlock().defaultBlockState() != st) continue;
            BlockEntity be = eb.newBlockEntity(ZERO, st);
            if (be == null) {
                assertEquals(Blocks.MOVING_PISTON, st.getBlock(), "바닐라에서 null 인 것은 움직이는 피스톤뿐: " + st);
                continue;
            }
            String type = NmsBlockStates.bridge().blockEntityType(st.getBlock().defaultBlockState().furnaceStateId());
            assertEquals(type, be.getType().furnaceId().toString(), st.toString());
            assertFalse(be.getClass().isAnonymousClass(), "표에 없는 종류라 일반 껍데기가 됐다: " + type + " " + be.getClass());
            ids.add(type);
        }
        assertTrue(ids.size() >= 40, "블록 엔티티 종류 " + ids.size());
    }

    @Test
    void blockEntityClassesFollowTheVanillaHierarchy() {
        assertTrue(make(Blocks.CHEST) instanceof net.minecraft.world.level.block.entity.ChestBlockEntity);
        assertFalse(make(Blocks.CHEST) instanceof net.minecraft.world.level.block.entity.TrappedChestBlockEntity);
        assertTrue(make(Blocks.TRAPPED_CHEST) instanceof net.minecraft.world.level.block.entity.ChestBlockEntity);
        assertTrue(make(Blocks.TRAPPED_CHEST) instanceof net.minecraft.world.level.block.entity.TrappedChestBlockEntity);
        assertTrue(make(Blocks.OAK_SIGN) instanceof net.minecraft.world.level.block.entity.SignBlockEntity);
        assertFalse(make(Blocks.OAK_SIGN) instanceof net.minecraft.world.level.block.entity.HangingSignBlockEntity);
        assertTrue(make(Blocks.OAK_HANGING_SIGN) instanceof net.minecraft.world.level.block.entity.SignBlockEntity);
        assertTrue(make(Blocks.OAK_HANGING_SIGN) instanceof net.minecraft.world.level.block.entity.HangingSignBlockEntity);
        for (Block f : new Block[] {Blocks.FURNACE, Blocks.SMOKER, Blocks.BLAST_FURNACE}) {
            assertTrue(make(f) instanceof net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity, f.toString());
            assertTrue(make(f) instanceof net.minecraft.world.level.block.entity.BaseContainerBlockEntity, f.toString());
        }
        assertTrue(make(Blocks.DROPPER) instanceof net.minecraft.world.level.block.entity.DispenserBlockEntity);
        for (Block c : new Block[] {Blocks.CHEST, Blocks.BARREL, Blocks.HOPPER, Blocks.DISPENSER, Blocks.WHITE_SHULKER_BOX, Blocks.CRAFTER}) {
            assertTrue(make(c) instanceof net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity, c.toString());
        }
        assertFalse(make(Blocks.ENDER_CHEST) instanceof net.minecraft.world.level.block.entity.BaseContainerBlockEntity);
        assertTrue(make(Blocks.END_GATEWAY) instanceof net.minecraft.world.level.block.entity.TheEndPortalBlockEntity);
        assertTrue(make(Blocks.CALIBRATED_SCULK_SENSOR) instanceof net.minecraft.world.level.block.entity.SculkSensorBlockEntity);
        assertTrue(make(Blocks.VAULT) instanceof net.minecraft.world.level.block.entity.vault.VaultBlockEntity);
        assertTrue(make(Blocks.SPAWNER) instanceof net.minecraft.world.level.block.entity.SpawnerBlockEntity);
        assertTrue(make(Blocks.SKELETON_SKULL) instanceof net.minecraft.world.level.block.entity.SkullBlockEntity);
        assertTrue(make(Blocks.WHITE_BANNER) instanceof net.minecraft.world.level.block.entity.BannerBlockEntity);
        assertTrue(make(Blocks.WHITE_BED) instanceof net.minecraft.world.level.block.entity.BedBlockEntity);
    }
}
