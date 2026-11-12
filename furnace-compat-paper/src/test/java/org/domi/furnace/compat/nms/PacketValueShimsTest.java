package org.domi.furnace.compat.nms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.ChatVisiblity;
import org.bukkit.Material;
import org.bukkit.craftbukkit.util.CraftChatMessage;
import org.bukkit.craftbukkit.util.CraftMagicNumbers;
import org.domi.furnace.test.FurnaceTestServer;
import org.junit.jupiter.api.Test;

/**
 * 패킷 라이브러리(ProtocolLib 등)가 값 래퍼를 만들 때 리플렉션·호출로 쓰는 shim: CraftChatMessage·엔티티 종류·옛 블록 데이터·
 * 채팅 보이기 enum·사용 패킷의 Action 계약. 진짜 ProtocolLib 5.4.0 의 {@code WrappedChatComponent}·{@code BukkitConverters}·
 * {@code WrappedBlockData}·{@code EnumWrappers} 가 이것들을 찾는 모양을 따른다(검토 R4).
 */
class PacketValueShimsTest {

    @Test
    void chatMessageSplitsLinesAndKeepsLegacyFormatting() {
        Component[] lines = CraftChatMessage.fromString("§chello\nworld");
        assertEquals(2, lines.length);
        assertEquals("hello", lines[0].getString());
        assertEquals("world", lines[1].getString());
        assertEquals("§chello", CraftChatMessage.fromComponent(lines[0]));
        assertEquals(1, CraftChatMessage.fromString("a\nb", true).length);
        assertEquals(0, CraftChatMessage.fromString(null).length);
        assertNull(CraftChatMessage.fromStringOrNull(null));
        assertEquals("", CraftChatMessage.fromStringOrEmpty(null).getString());
    }

    @Test
    void chatMessageJsonRoundTripsAndFallsBackToLegacy() {
        Component c = CraftChatMessage.fromJSON("{\"text\":\"a\",\"color\":\"red\"}");
        assertEquals("a", c.getString());
        assertEquals("{\"text\":\"a\",\"color\":\"red\"}", CraftChatMessage.toJSON(c));
        assertNull(CraftChatMessage.fromJSONOrNull("{not json"));
        assertEquals("plain", CraftChatMessage.fromJSONOrString("plain").getString());
        assertEquals("b", CraftChatMessage.fromJSONOrString("{\"text\":\"b\"}").getString());
    }

    @Test
    void entityTypeIsAKeyValueTiedToBukkitNames() {
        EntityType<?> zombie = EntityType.byString("zombie").orElseThrow();
        assertEquals(ResourceLocation.parse("minecraft:zombie"), EntityType.getKey(zombie));
        assertSame(zombie, EntityType.byString("minecraft:zombie").orElseThrow(), "같은 키는 같은 객체");
        assertTrue(EntityType.byString("nope").isEmpty());
        assertTrue(EntityType.byString(null).isEmpty());
        assertEquals("zombie", org.bukkit.entity.EntityType.ZOMBIE.getName());
        assertSame(org.bukkit.entity.EntityType.ZOMBIE, org.bukkit.entity.EntityType.fromName("Zombie"));
        assertNull(org.bukkit.entity.EntityType.fromName("nope"));
    }

    @Test
    void legacyBlockDataHelpersLinkAndFlattenedValuesAreDefaults() throws Exception {
        try (FurnaceTestServer server = FurnaceTestServer.builder().start()) {
            var stone = CraftMagicNumbers.getBlock(Material.STONE, (byte) 3);
            assertNotNull(stone);
            assertSame(CraftMagicNumbers.getBlock(Material.STONE).defaultBlockState(), stone, "평탄화 이후 데이터는 무시");
            assertEquals(0, CraftMagicNumbers.toLegacyData(stone));
            assertNull(CraftMagicNumbers.getBlock(Material.DIAMOND, (byte) 0), "블록이 아닌 재료");
            // 패킷 라이브러리의 FuzzyReflection 이 찾는 모양: BlockState 의 0 인자 getBlock, Block 의 0 인자 defaultBlockState
            Method getBlock = stone.getClass().getMethod("getBlock");
            assertSame(CraftMagicNumbers.getBlock(Material.STONE), getBlock.invoke(stone));
            assertTrue(Modifier.isStatic(CraftMagicNumbers.class.getMethod("toLegacyData", net.minecraft.world.level.block.state.BlockState.class).getModifiers()));
        }
    }

    @Test
    void chatVisibilityFollowsVanillaNumbers() {
        assertEquals(3, ChatVisiblity.values().length);
        assertEquals(0, ChatVisiblity.FULL.getId());
        assertEquals(1, ChatVisiblity.SYSTEM.getId());
        assertEquals(2, ChatVisiblity.HIDDEN.getId());
        assertSame(ChatVisiblity.HIDDEN, ChatVisiblity.byId(2));
    }

    @Test
    void interactActionDeclaresGetType() throws Exception {
        Class<?> action = Class.forName("net.minecraft.network.protocol.game.ServerboundInteractPacket$Action");
        Method getType = action.getDeclaredMethod("getType");
        assertEquals("net.minecraft.network.protocol.game.ServerboundInteractPacket$ActionType", getType.getReturnType().getName());
    }
}
