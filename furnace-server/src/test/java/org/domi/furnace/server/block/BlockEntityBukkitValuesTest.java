// 블록 엔티티 영속 데이터(PublicBukkitValues): 클라이언트로 나가는 길(block_entity_data 방송·명령 블록 화면)에서 빠지는지, 아이템
// block_entity_data 로 놓을 때 읽히는지, 영속 데이터만 고치는 길이 비면 키를 떼는지(Paper Sanitize-sent-BlockEntity-NBT·CraftBukkit loadAdditional).
package org.domi.furnace.server.block;

import org.domi.furnace.api.GameVersion;
import static org.junit.jupiter.api.Assertions.*;

import org.domi.furnace.server.block.entity.BlockEntity;
import org.domi.furnace.server.block.entity.CommandBlockEntity;
import org.domi.furnace.server.data.TagRegistries;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.protocol.play.BlockEntityDataPacket;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class BlockEntityBukkitValuesTest {

    static final Pos POS = new Pos(1, -60, 1);

    @BeforeAll
    static void install() { ItemContext.install(new TagRegistries(RegistrySet.load("registries/" + GameVersion.RESOURCE_TAG + ".txt"))); }

    private static NbtCompound secret() {
        NbtCompound pdc = new NbtCompound();
        pdc.putString("demo:secret", "token");
        return pdc;
    }

    private static BlockEntity placed(BlockPlacementTest.TestLevel level, String block) {
        level.setBlock(POS, B.defaultState(block), 3);
        var be = (BlockEntity) level.getBlockEntity(POS);
        assertNotNull(be, block + " 블록 엔티티");
        return be;
    }

    /** ChunkHolder.broadcastBlockEntityIfNeeded 길: 갱신 태그(표지판 글)는 나가고 영속 데이터는 안 나간다. */
    @Test
    void blockEntityDataBroadcastDropsBukkitValues() {
        var level = new BlockPlacementTest.TestLevel();
        BlockEntity sign = placed(level, "minecraft:oak_sign");
        level.broadcastChanges();
        level.broadcasts.clear();
        sign.bukkitValues(true).putCompound("demo:nested", secret());
        assertTrue(sign.saveCustomOnly().contains(BlockEntity.BUKKIT_VALUES), "저장 꼴에는 있다");

        level.sendBlockUpdated(POS);
        level.broadcastChanges();
        var data = level.broadcasts.stream().filter(p -> p instanceof BlockEntityDataPacket).map(p -> (BlockEntityDataPacket) p).toList();
        assertEquals(1, data.size(), "표지판은 갱신 패킷을 낸다");
        assertTrue(data.get(0).tag().contains("front_text"), "갱신 태그 내용은 그대로");
        assertFalse(data.get(0).tag().contains(BlockEntity.BUKKIT_VALUES), "영속 데이터는 클라이언트로 안 간다");
        assertTrue(sign.saveCustomOnly().contains(BlockEntity.BUKKIT_VALUES), "보낸 뒤에도 블록 엔티티 값은 그대로(사본에서 뺀다)");
    }

    /** 바닐라 ServerPlayer.openCommandBlock = ClientboundBlockEntityDataPacket.create(be, saveCustomOnly) — Paper 는 거기서 sanitizeSentNbt. */
    @Test
    void commandBlockScreenTagDropsBukkitValues() {
        var level = new BlockPlacementTest.TestLevel();
        var be = (CommandBlockEntity) placed(level, "minecraft:command_block");
        NbtCompound tag = be.saveCustomOnly();
        tag.putString("Command", "say hi");
        tag.putCompound(BlockEntity.BUKKIT_VALUES, secret());
        be.loadWithBukkitValues(tag);
        assertTrue(be.saveCustomOnly().contains(BlockEntity.BUKKIT_VALUES), "저장 꼴에는 있다");
        NbtCompound screen = be.screenTag();
        assertEquals("say hi", screen.getString("Command"));
        assertFalse(screen.contains(BlockEntity.BUKKIT_VALUES), "명령 블록 화면으로 보내는 태그에는 없다");
    }

    /** BlockItem.updateCustomBlockEntityTag(아이템 block_entity_data) 도 CraftBukkit loadAdditional 처럼 영속 데이터를 읽는다. */
    @Test
    void itemBlockEntityDataCarriesBukkitValues() {
        var level = new BlockPlacementTest.TestLevel();
        BlockEntity barrel = placed(level, "minecraft:barrel");
        NbtCompound data = new NbtCompound();
        data.putCompound(BlockEntity.BUKKIT_VALUES, secret());
        assertTrue(barrel.loadCustomDataInto(level, data));
        assertEquals("token", barrel.saveCustomOnly().getCompound(BlockEntity.BUKKIT_VALUES).getString("demo:secret"));
    }

    /** 영속 데이터만 고치는 길(TileState getState(false)): 다 지우면 저장 꼴에서 키가 빠진다. */
    @Test
    void emptiedBukkitValuesAreDropped() {
        var level = new BlockPlacementTest.TestLevel();
        BlockEntity barrel = placed(level, "minecraft:barrel");
        barrel.bukkitValues(true).putString("demo:a", "x");
        barrel.settleBukkitValues(level, true);
        assertTrue(barrel.saveCustomOnly().contains(BlockEntity.BUKKIT_VALUES));
        barrel.bukkitValues(false).tags().remove("demo:a");
        barrel.settleBukkitValues(level, true);
        assertFalse(barrel.saveCustomOnly().contains(BlockEntity.BUKKIT_VALUES), "빈 영속 데이터는 쓰지 않는다(Paper isEmpty 검사)");
        assertNull(barrel.bukkitValues(false));
    }
}
