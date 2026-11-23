package org.domi.furnace.compat.nms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.BeaconBeamBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.GameMasterBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.StainedGlassBlock;
import net.minecraft.world.level.block.StainedGlassPaneBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.CraftServer;
import org.domi.furnace.api.internal.BlockStateInternals;
import org.domi.furnace.compat.CompatTestPlugin;
import org.domi.furnace.test.FurnaceTestServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * T-231: 블록·아이템 객체가 바닐라와 같은 클래스인가. 정답은 바닐라 서버를 부트스트랩해 뽑은 종류 표({@code src/shimspec/kind-classes-1.21.1.txt}, vanilla-extract
 * {@code kind-classes})이고, 그 클래스의 상위 사슬은 서명표에서 푼 잠금 파일이 정한다 — 그래서 객체 클래스가 같으면 모든 {@code instanceof} 가 바닐라와 같다.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NmsKindClassesTest {

    FurnaceTestServer server;

    @BeforeAll
    void start() throws Exception {
        server = FurnaceTestServer.builder().failFast(false).exposeCompatToNative(true).plugin(CompatTestPlugin.class).start();
        Bukkit.setServer(CraftServer.INSTANCE);
    }

    @AfterAll
    void stop() { server.close(); }

    private static List<String[]> table(char kind) throws Exception {
        List<String[]> out = new ArrayList<>();
        for (String l : Files.readAllLines(Path.of("src/shimspec/kind-classes-1.21.1.txt"), StandardCharsets.UTF_8)) {
            if (l.isBlank() || l.startsWith("#")) continue;
            String[] w = l.split(" ");
            if (w[0].charAt(0) == kind) out.add(w);
        }
        return out;
    }

    @Test
    void everyBlockHasItsVanillaClass() throws Exception {
        List<String[]> t = table('B');
        assertEquals(BuiltInRegistries.BLOCK.size(), t.size(), "블록 수");
        int i = 0;
        for (Block b : BuiltInRegistries.BLOCK) {
            String[] w = t.get(i++);
            assertEquals(w[1], BuiltInRegistries.BLOCK.getKey(b).toString(), "레지스트리 순서");
            assertEquals(w[2], b.getClass().getName(), w[1]);
        }
    }

    @Test
    void everyItemHasItsVanillaClass() throws Exception {
        List<String[]> t = table('I');
        assertEquals(BuiltInRegistries.ITEM.size(), t.size(), "아이템 수");
        int i = 0;
        for (Item it : BuiltInRegistries.ITEM) {
            String[] w = t.get(i++);
            assertEquals(w[1], BuiltInRegistries.ITEM.getKey(it).toString(), "레지스트리 순서");
            assertEquals(w[2], it.getClass().getName(), w[1]);
            assertSame(it, Item.byKey(w[1]), "같은 이름은 같은 객체");
        }
        assertSame(Item.class, Item.byKey("furnace:not_vanilla").getClass(), "바닐라에 없는 이름은 Item");
    }

    /** 바닐라 EntityBlock 구현 클래스 = 서버의 "블록 엔티티 있음" 상태(지금까지 FurnaceEntityBlock 이 고르던 기준)와 같다. */
    @Test
    void entityBlocksAreTheBlocksWithBlockEntities() {
        short[] flags = NmsBlockStates.table().flags();
        int entityBlocks = 0;
        for (Block b : BuiltInRegistries.BLOCK) {
            boolean has = (flags[b.defaultBlockState().furnaceStateId()] & BlockStateInternals.HAS_BLOCK_ENTITY) != 0;
            assertEquals(has, b instanceof EntityBlock, b.toString());
            if (has) entityBlocks++;
        }
        assertTrue(entityBlocks > 100, "블록 엔티티 있는 블록 " + entityBlocks);
        ChestBlock chest = assertInstanceOf(ChestBlock.class, Blocks.CHEST);
        assertInstanceOf(BaseEntityBlock.class, chest);
        assertInstanceOf(ChestBlockEntity.class, chest.newBlockEntity(BlockPos.ZERO, Blocks.CHEST.defaultBlockState()));
        assertNull(((EntityBlock) Blocks.MOVING_PISTON).newBlockEntity(BlockPos.ZERO, Blocks.MOVING_PISTON.defaultBlockState()), "움직이는 피스톤은 바닐라도 null");
        assertNotNull(((EntityBlock) Blocks.RED_BED).newBlockEntity(BlockPos.ZERO, Blocks.RED_BED.defaultBlockState()));
    }

    /** Axiom 이 가르는 클래스(5d 남긴 것: 침대·문·두 칸 식물이면 setPlacedBy 를 건너뛴다) 등. */
    @Test
    void classesPluginsBranchOn() {
        assertInstanceOf(BedBlock.class, Blocks.RED_BED);
        assertInstanceOf(DoorBlock.class, Blocks.OAK_DOOR);
        assertInstanceOf(DoorBlock.class, Blocks.IRON_DOOR);
        assertInstanceOf(DoublePlantBlock.class, Blocks.TALL_GRASS);
        assertInstanceOf(DoublePlantBlock.class, Blocks.SUNFLOWER);
        assertInstanceOf(GameMasterBlock.class, Blocks.COMMAND_BLOCK);
        assertInstanceOf(GameMasterBlock.class, Blocks.STRUCTURE_BLOCK);
        assertInstanceOf(LiquidBlock.class, Blocks.WATER);
        assertInstanceOf(LiquidBlock.class, Blocks.LAVA);
        assertFalse(Blocks.STONE instanceof LiquidBlock);
        assertInstanceOf(BushBlock.class, Blocks.WHEAT);
        assertInstanceOf(CropBlock.class, Blocks.BEETROOTS);
        assertFalse(Blocks.OAK_DOOR instanceof EntityBlock);
    }

    @Test
    void beaconBeamColors() {
        assertEquals(DyeColor.LIGHT_BLUE, assertInstanceOf(StainedGlassBlock.class, Blocks.LIGHT_BLUE_STAINED_GLASS).getColor());
        assertEquals(DyeColor.BLACK, assertInstanceOf(StainedGlassPaneBlock.class, Blocks.BLACK_STAINED_GLASS_PANE).getColor());
        int beam = 0;
        for (Block b : BuiltInRegistries.BLOCK) {
            if (!(b instanceof BeaconBeamBlock bb)) continue;
            beam++;
            String path = BuiltInRegistries.BLOCK.getKey(b).getPath();
            if (path.equals("beacon")) assertEquals(DyeColor.WHITE, bb.getColor(), "바닐라 BeaconBlock 은 흰색");
            else assertTrue(path.startsWith(bb.getColor().getName() + "_stained_glass"), path);
        }
        assertEquals(33, beam, "색유리 16 + 색유리판 16 + 신호기");
    }

    /** BlockItem.getBlock = 바닐라 블록 아이템 표(vanilla-extract item-blocks, 서버 자원)의 블록. */
    @Test
    void blockItemsPlaceTheirVanillaBlock() throws Exception {
        int n = 0;
        try (var in = NmsKindClassesTest.class.getClassLoader().getResourceAsStream("items/itemblocks_1.21.1.txt")) {
            assertNotNull(in, "items/itemblocks_1.21.1.txt");
            var r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            for (String l; (l = r.readLine()) != null; ) {
                if (!l.startsWith("I ")) continue;
                String[] w = l.split(" ");
                BlockItem bi = assertInstanceOf(BlockItem.class, Item.byKey(w[1]), w[1]);
                assertEquals(w[3], BuiltInRegistries.BLOCK.getKey(bi.getBlock()).toString(), w[1]);
                n++;
            }
        }
        int blockItems = 0;
        for (Item it : BuiltInRegistries.ITEM) if (it instanceof BlockItem) blockItems++;
        assertEquals(n, blockItems, "블록 아이템 수가 표와 같다");
        assertFalse(Items.DIAMOND_SWORD instanceof BlockItem);
        assertSame(Blocks.WHEAT, ((BlockItem) Items.WHEAT_SEEDS).getBlock());
        assertSame(Blocks.TORCH, ((BlockItem) Items.TORCH).getBlock());
    }
}
