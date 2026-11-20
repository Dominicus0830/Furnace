package org.domi.furnace.compat.nms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.papermc.paper.configuration.WorldConfiguration;
import io.papermc.paper.configuration.type.EngineMode;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.VarInt;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.domi.furnace.test.FurnaceTestServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * NMS shim 5e-2: voxy 가 쓰는 작은 서버 조각 — {@code VarInt}, {@code Biomes}, {@code BuiltInRegistries.ENTITY_TYPE}, {@code ServerLevel.paperConfig()}.
 * 기대값은 Paper 1.21.1 jar 의 바이트코드·기본 설정(javap)과 바닐라 VarInt 규칙(VarInt 는 Furnace 가 새로 쓴 구현).
 */
class NmsVoxyServerBitsTest {

    private static FurnaceTestServer server;

    @BeforeAll
    static void start() {
        server = FurnaceTestServer.builder().start();
        Bukkit.setServer(CraftServer.INSTANCE);
    }

    @AfterAll
    static void stop() { server.close(); }

    @Test
    void varIntSizesAndBytesLikeVanilla() {
        int[] values = {0, 1, 127, 128, 255, 300, 16383, 16384, 2097151, 2097152, Integer.MAX_VALUE, -1, Integer.MIN_VALUE};
        int[] sizes = {1, 1, 1, 2, 2, 2, 2, 3, 3, 4, 5, 5, 5};
        for (int i = 0; i < values.length; i++) {
            assertEquals(sizes[i], VarInt.getByteSize(values[i]), "크기 " + values[i]);
            assertEquals(sizes[i], VarInt.getByteSizeOld(values[i]), "옛 크기 " + values[i]);
            ByteBuf a = VarInt.write(Unpooled.buffer(), values[i]);
            ByteBuf b = VarInt.writeOld(Unpooled.buffer(), values[i]);
            assertEquals(sizes[i], a.readableBytes());
            assertEquals(a, b, "빠른 길과 옛 길이 같은 바이트");
            assertEquals(values[i], VarInt.read(a));
        }
        ByteBuf three = VarInt.write(Unpooled.buffer(), 300);
        assertEquals((byte) 0xAC, three.getByte(0));
        assertEquals((byte) 0x02, three.getByte(1));
        assertTrue(VarInt.hasContinuationBit((byte) 0x80));
        assertFalse(VarInt.hasContinuationBit((byte) 0x7F));
        // 다섯 바이트 뒤에도 이어지면 바닐라처럼 여섯째 바이트까지 읽고 거절한다
        ByteBuf tooBig = Unpooled.buffer().writeBytes(new byte[] {(byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, 1});
        RuntimeException e = assertThrows(RuntimeException.class, () -> VarInt.read(tooBig));
        assertEquals("VarInt too big", e.getMessage());
        assertEquals(6, tooBig.readerIndex());
        ByteBuf five = Unpooled.buffer().writeBytes(new byte[] {(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0x7F});
        assertEquals(-1, VarInt.read(five), "다섯째 바이트의 남는 윗비트는 버린다");
    }

    @Test
    void biomeKeysAreTheVanillaSixtyFourAndExistOnTheServer() throws Exception {
        List<ResourceKey<Biome>> keys = new ArrayList<>();
        for (Field f : Biomes.class.getFields()) {
            if (Modifier.isStatic(f.getModifiers()) && f.getType() == ResourceKey.class) {
                @SuppressWarnings("unchecked") ResourceKey<Biome> k = (ResourceKey<Biome>) f.get(null);
                keys.add(k);
            }
        }
        assertEquals(64, keys.size());
        assertEquals("minecraft:plains", Biomes.PLAINS.location().toString());
        assertEquals("minecraft:worldgen/biome", Biomes.PLAINS.registry().toString());
        var reg = RegistryAccess.SERVER.registryOrThrow(Registries.BIOME);
        for (ResourceKey<Biome> k : keys) assertTrue(reg.getHolder(k).isPresent(), "서버 바이옴에 있다: " + k);
        assertEquals(reg.keySet().size(), keys.size(), "1.21.1 바이옴 수와 같다");
    }

    @Test
    void entityTypeRegistryIsDefaultedToPig() {
        var reg = BuiltInRegistries.ENTITY_TYPE;
        assertEquals(ResourceLocation.withDefaultNamespace("pig"), reg.getDefaultKey());
        assertSame(reg.get(ResourceLocation.withDefaultNamespace("pig")), reg.get(ResourceLocation.withDefaultNamespace("nope")), "없는 이름은 기본값(돼지)");
        assertEquals(ResourceLocation.withDefaultNamespace("player"), reg.getKey(EntityType.PLAYER));
        assertSame(EntityType.PLAYER, reg.byId(reg.getId(EntityType.PLAYER)));
        assertSame(reg, RegistryAccess.SERVER.registryOrThrow(Registries.ENTITY_TYPE));
    }

    @Test
    void paperConfigReportsAntiXrayOffWithPaperDefaults() {
        ServerLevel level = ((CraftWorld) Bukkit.getWorlds().get(0)).getHandle();
        WorldConfiguration c = level.paperConfig();
        assertSame(c, level.paperConfig());
        var ax = c.anticheat.antiXray;
        assertFalse(ax.enabled, "Furnace 에는 엑스레이 막기가 없다");
        assertSame(EngineMode.HIDE, ax.engineMode);
        assertEquals(1, ax.engineMode.getId());
        assertEquals(64, ax.maxBlockHeight);
        assertEquals(2, ax.updateRadius);
        assertEquals(23, ax.hiddenBlocks.size());
        assertTrue(ax.hiddenBlocks.contains(Blocks.DIAMOND_ORE) && ax.hiddenBlocks.contains(Blocks.ENDER_CHEST));
        assertEquals(List.of(Blocks.STONE, Blocks.OAK_PLANKS, Blocks.DEEPSLATE), ax.replacementBlocks);
        assertEquals(31, c.version, "Paper 1.21.1 의 CURRENT_VERSION");
        assertSame(EngineMode.OBFUSCATE_LAYER, EngineMode.valueOf(3));
    }
}
