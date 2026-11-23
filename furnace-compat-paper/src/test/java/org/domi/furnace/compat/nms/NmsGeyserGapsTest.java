package org.domi.furnace.compat.nms;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
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

/**
 * Geyser 가 부르는 컴포넌트·레지스트리 shim(DATA_COMPONENT_TYPE·BUNDLE_CONTENTS·CONTAINER·CONTAINER_LOOT·streamCodec·entrySet)과 한 줄짜리 둘
 * (ServerLevel.getPlayerByUUID·Block.getCloneItemStack). 기대 바이트는 바닐라 1.21.1 네트워크 꼴(VarInt·아이템 스택 = 개수, 아이템 번호, 빈 패치)이다.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NmsGeyserGapsTest {

    FurnaceTestServer server;
    ServerLevel level;
    TestPlayer tp;

    @BeforeAll
    void start() {
        server = FurnaceTestServer.builder().failFast(false).start();
        Bukkit.setServer(CraftServer.INSTANCE);
        tp = server.join("Geysery");
        server.tick(3);
        level = ((CraftWorld) Compat.player(tp.player()).getWorld()).getHandle();
    }

    @AfterAll
    void stop() { server.close(); }

    private static RegistryFriendlyByteBuf buf() { return new RegistryFriendlyByteBuf(Unpooled.buffer(), net.minecraft.core.RegistryAccess.SERVER); }

    private static byte[] bytes(ByteBuf b) {
        byte[] out = new byte[b.readableBytes()];
        b.getBytes(b.readerIndex(), out);
        return out;
    }

    @Test
    void registryFollowsVanillaNetworkIds() {
        var reg = BuiltInRegistries.DATA_COMPONENT_TYPE;
        assertEquals(57, reg.size());
        assertEquals(0, reg.getId(DataComponents.CUSTOM_DATA));
        assertEquals(1, reg.getId(DataComponents.MAX_STACK_SIZE));
        assertEquals(DataComponents.CONTAINER, reg.byId(reg.getId(DataComponents.CONTAINER)));
        assertEquals(56, reg.getId(DataComponents.CONTAINER_LOOT));
        assertSame(DataComponents.BUNDLE_CONTENTS, reg.get(net.minecraft.resources.ResourceLocation.parse("minecraft:bundle_contents")));
        assertEquals("minecraft:container", DataComponents.CONTAINER.furnaceKey());
    }

    @Test
    void streamCodecWritesVanillaNetworkBytes() {
        RegistryFriendlyByteBuf b = buf();
        DataComponents.MAX_STACK_SIZE.streamCodec().encode(b, 16);
        assertArrayEquals(new byte[] {16}, bytes(b));
        assertEquals(16, DataComponents.MAX_STACK_SIZE.streamCodec().decode(b));
        assertEquals(0, b.readableBytes());

        // 번들: 목록 길이 1, 스택(개수 2, 돌 번호 1, 더한 0·지운 0)
        RegistryFriendlyByteBuf c = buf();
        var codec = DataComponents.BUNDLE_CONTENTS.streamCodec();
        codec.encode(c, new RawComponentValue("[{id:\"minecraft:stone\",count:2}]"));
        assertArrayEquals(new byte[] {1, 2, 1, 0, 0}, bytes(c));
        RawComponentValue back = codec.decode(c);
        assertTrue(back.snbt().contains("minecraft:stone"));
        assertEquals(0, c.readableBytes());
    }

    @Test
    void patchEntrySetListsValuesAndRemovals() {
        DataComponentPatch patch = DataComponentPatch.furnaceOf(
                Map.of("minecraft:max_stack_size", "16", "minecraft:bundle_contents", "[{id:\"minecraft:stone\",count:2}]"),
                Set.of("minecraft:damage"));
        assertEquals(3, patch.entrySet().size());
        for (var e : patch.entrySet()) {
            DataComponentType<?> t = e.getKey();
            if (t == DataComponents.MAX_STACK_SIZE) assertEquals(Optional.of(16), e.getValue());
            else if (t == DataComponents.BUNDLE_CONTENTS) assertInstanceOf(RawComponentValue.class, e.getValue().orElseThrow());
            else {
                assertSame(DataComponents.DAMAGE, t);
                assertEquals(Optional.empty(), e.getValue());
            }
        }
    }

    @Test
    void playerByUuidFindsOnlineOnes() {
        UUID id = Compat.player(tp.player()).getUniqueId();
        assertEquals(id, level.getPlayerByUUID(id).getUUID());
        assertNull(level.getPlayerByUUID(UUID.randomUUID()));
    }

    @Test
    void cloneItemStackIsOneOfTheBlockItem() {
        var stack = Blocks.STONE.getCloneItemStack(level, BlockPos.ZERO, Blocks.STONE.defaultBlockState());
        assertEquals(net.minecraft.world.item.Items.STONE, stack.getItem());
        assertEquals(1, stack.getCount());
    }
}
