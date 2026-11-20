package org.domi.furnace.compat.nms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.destroystokyo.paper.antixray.ChunkPacketBlockController;
import java.util.EnumSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.EndTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.ShortTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.domi.furnace.api.packet.PacketTypes;
import org.domi.furnace.compat.paper.Compat;
import org.domi.furnace.test.FurnaceTestServer;
import org.domi.furnace.test.TestPlayer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * NMS shim 5e-3a: Axiom 이 쓰는 작은 조각 — NBT {@code sizeInBytes}, {@code BlockEntity.getPosFromTag}, {@code BlockEntityType.validBlocks},
 * {@code RelativeMovement} 풀기·묶기, Paper 엑스레이 막기 조절기, {@code Player.kick(Component)}. 기대값은 Paper 1.21.1 jar 바이트코드(javap).
 */
class NmsAxiomSmallBitsTest {

    private static FurnaceTestServer server;

    @BeforeAll
    static void start() {
        server = FurnaceTestServer.builder().start();
        Bukkit.setServer(CraftServer.INSTANCE);
    }

    @AfterAll
    static void stop() { server.close(); }

    @Test
    void tagSizesFollowVanillaFormulas() {
        assertEquals(9, ByteTag.valueOf((byte) 1).sizeInBytes());
        assertEquals(10, ShortTag.valueOf((short) 1).sizeInBytes());
        assertEquals(12, IntTag.valueOf(1).sizeInBytes());
        assertEquals(16, LongTag.valueOf(1).sizeInBytes());
        assertEquals(12, FloatTag.valueOf(1).sizeInBytes());
        assertEquals(16, DoubleTag.valueOf(1).sizeInBytes());
        assertEquals(8, EndTag.INSTANCE.sizeInBytes());
        assertEquals(24 + 3, new ByteArrayTag(new byte[3]).sizeInBytes());
        assertEquals(24 + 4 * 3, new IntArrayTag(new int[3]).sizeInBytes());
        assertEquals(24 + 8 * 3, new LongArrayTag(new long[3]).sizeInBytes());
        assertEquals(36 + 2 * 3, StringTag.valueOf("abc").sizeInBytes());
        ListTag list = new ListTag();
        list.add(IntTag.valueOf(1));
        list.add(IntTag.valueOf(2));
        assertEquals(37 + 4 * 2 + 12 * 2, list.sizeInBytes());
        CompoundTag c = new CompoundTag();
        c.putInt("a", 1);
        assertEquals(48 + 28 + 2 + 36 + 12, c.sizeInBytes());
        CompoundTag outer = new CompoundTag();
        outer.put("in", c);
        outer.put("l", list);
        assertEquals(48 + (28 + 4 + 36 + c.sizeInBytes()) + (28 + 2 + 36 + list.sizeInBytes()), outer.sizeInBytes());
    }

    @Test
    void blockEntityPosFromTagReadsXyz() {
        CompoundTag t = new CompoundTag();
        t.putInt("x", -5);
        t.putInt("y", 70);
        t.putInt("z", 123);
        assertEquals(new BlockPos(-5, 70, 123), BlockEntity.getPosFromTag(t));
        assertEquals(BlockPos.ZERO, BlockEntity.getPosFromTag(new CompoundTag()), "없는 키는 0");
    }

    @Test
    void validBlocksAreTheBlocksCarryingThatType() {
        var chest = BuiltInRegistries.BLOCK_ENTITY_TYPE.get(ResourceLocation.withDefaultNamespace("chest"));
        assertTrue(chest.validBlocks.contains(Blocks.CHEST));
        assertFalse(chest.validBlocks.contains(Blocks.TRAPPED_CHEST), "덫 상자는 따로 종류(trapped_chest)");
        assertFalse(chest.validBlocks.contains(Blocks.STONE));
        var sign = BuiltInRegistries.BLOCK_ENTITY_TYPE.get(ResourceLocation.withDefaultNamespace("sign"));
        assertTrue(sign.validBlocks.contains(Blocks.OAK_SIGN) && sign.validBlocks.contains(Blocks.OAK_WALL_SIGN));
        assertEquals(22, sign.validBlocks.size(), "1.21.1 서 있는·벽 표지판 11나무 × 2(매단 표지판은 hanging_sign)");
        assertThrows(UnsupportedOperationException.class, () -> chest.validBlocks.add(Blocks.STONE));
    }

    @Test
    void relativeMovementPacksLikeVanilla() {
        assertEquals(EnumSet.noneOf(RelativeMovement.class), RelativeMovement.unpack(0));
        assertEquals(EnumSet.of(RelativeMovement.X, RelativeMovement.Z), RelativeMovement.unpack(0b101));
        assertEquals(RelativeMovement.ALL, RelativeMovement.unpack(0xFF), "모르는 비트는 버린다");
        assertEquals(0b11000, RelativeMovement.pack(RelativeMovement.ROTATION));
        assertEquals(0b11111, RelativeMovement.pack(RelativeMovement.ALL));
        assertEquals(Set.of(RelativeMovement.X_ROT, RelativeMovement.Y_ROT), RelativeMovement.ROTATION);
        assertThrows(UnsupportedOperationException.class, () -> RelativeMovement.ALL.clear());
        RelativeMovement.unpack(1).add(RelativeMovement.Y);   // 새 EnumSet 이라 고칠 수 있다
    }

    @Test
    void antiXrayControllerIsPapersNoOperation() {
        ServerLevel level = ((CraftWorld) Bukkit.getWorlds().get(0)).getHandle();
        ChunkPacketBlockController c = level.chunkPacketBlockController;
        assertSame(ChunkPacketBlockController.NO_OPERATION_INSTANCE, c);
        assertFalse(c.shouldModify(null, null));
        assertNull(c.getPresetBlockStates(level, null, 0));
    }

    @Test
    void kickWithAdventureComponentSendsThatReason() {
        TestPlayer tp = server.join("Kicked");
        server.tick(2);
        var reason = net.kyori.adventure.text.serializer.gson.GsonComponentSerializer.gson().deserialize("{\"text\":\"bye\",\"color\":\"red\"}");
        Compat.player(tp.player()).kick(reason);
        var p = tp.awaitPacket(PacketTypes.Clientbound.DISCONNECT, x -> true);
        assertEquals("bye", p.reason().plainText());
        assertTrue(p.reason().toJson().contains("\"color\":\"red\""), p.reason().toJson());
    }
}
