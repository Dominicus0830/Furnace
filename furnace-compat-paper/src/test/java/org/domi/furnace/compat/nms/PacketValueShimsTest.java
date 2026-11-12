package org.domi.furnace.compat.nms;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
        // 줄바꿈 뒤에도 색이 이어진다(CraftBukkit 은 modifier 를 초기화하지 않는다)
        assertEquals("§cworld", CraftChatMessage.fromComponent(lines[1]));
        assertEquals("§c§lb", CraftChatMessage.fromComponent(CraftChatMessage.fromString("§c§la\nb")[1]));
        assertEquals("b", CraftChatMessage.fromComponent(CraftChatMessage.fromString("§ca§r\nb")[1]), "§r 로 비우면 이어지지 않는다");
        assertEquals(1, CraftChatMessage.fromString("a\nb", true).length);
        assertEquals(0, CraftChatMessage.fromString(null).length);
        assertNull(CraftChatMessage.fromStringOrNull(null));
        assertEquals("", CraftChatMessage.fromStringOrEmpty(null).getString());
        // CraftBukkit: 빈 문자열은 null, 여러 줄이면 첫 줄만
        assertNull(CraftChatMessage.fromStringOrNull(""));
        assertNull(CraftChatMessage.fromStringOrNull("", true));
        assertEquals("a", CraftChatMessage.fromStringOrNull("a\nb").getString());
        assertEquals("a", CraftChatMessage.fromStringOrEmpty("a\nb").getString());
        assertEquals("a\nb", CraftChatMessage.fromStringOrNull("a\nb", true).getString());
    }

    @Test
    void chatMessageTurnsAddressesIntoLinksUnlessPlain() {
        String linked = CraftChatMessage.toJSON(CraftChatMessage.fromString("visit example.com now")[0]);
        assertTrue(linked.contains("open_url") && linked.contains("http://example.com"), linked);
        String plain = CraftChatMessage.toJSON(CraftChatMessage.fromString("visit example.com now", false, true)[0]);
        assertFalse(plain.contains("open_url"), plain);
    }

    @Test
    void chatMessageJsonRoundTripsAndFallsBackToLegacy() {
        Component c = CraftChatMessage.fromJSON("{\"text\":\"a\",\"color\":\"red\"}");
        assertEquals("a", c.getString());
        assertEquals("{\"text\":\"a\",\"color\":\"red\"}", CraftChatMessage.toJSON(c));
        assertNull(CraftChatMessage.fromJSONOrNull("{not json"));
        assertEquals("plain", CraftChatMessage.fromJSONOrString("plain").getString());
        assertEquals("b", CraftChatMessage.fromJSONOrString("{\"text\":\"b\"}").getString());
        // 바닐라의 관대 읽기: 따옴표 없는 한 낱말은 글자 그대로, 낱말 둘 이상이면 JSON 이 아니다
        assertEquals("plain", CraftChatMessage.fromJSON("plain").getString());
        assertEquals("§chello", CraftChatMessage.fromJSONOrString("§chello").getString());
        assertThrows(RuntimeException.class, () -> CraftChatMessage.fromJSON("plain text"));
        assertEquals("plain text", CraftChatMessage.fromJSONOrString("plain text").getString());
    }

    @Test
    void entityTypeIsAKeyValueTiedToBukkitNames() {
        EntityType<?> zombie = EntityType.byString("zombie").orElseThrow();
        assertEquals(ResourceLocation.parse("minecraft:zombie"), EntityType.getKey(zombie));
        assertSame(zombie, EntityType.byString("minecraft:zombie").orElseThrow(), "같은 키는 같은 객체");
        // 키 없이 만들어진 객체(패킷 라이브러리가 필드를 빈 값으로 채울 때)는 바닐라 기본 레지스트리처럼 minecraft:pig
        var blank = assertDoesNotThrow(() -> {
            var ctor = EntityType.class.getDeclaredConstructor(ResourceLocation.class);
            ctor.setAccessible(true);
            return (EntityType<?>) ctor.newInstance((Object) null);
        });
        assertEquals(ResourceLocation.parse("minecraft:pig"), EntityType.getKey(blank));
        assertEquals("entity.minecraft.pig", blank.toString());
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
            assertSame(net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), CraftMagicNumbers.getBlock(Material.DIAMOND, (byte) 0), "블록이 아닌 재료는 Paper 처럼 공기");
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
    void interactPacketHasTheActionImplementationsAPacketLibraryLooksFor() throws Exception {
        Class<?> outer = Class.forName("net.minecraft.network.protocol.game.ServerboundInteractPacket");
        var attack = outer.getDeclaredField("ATTACK_ACTION");
        assertTrue(Modifier.isStatic(attack.getModifiers()));
        attack.setAccessible(true);
        Object a = attack.get(null);
        var getType = Class.forName("net.minecraft.network.protocol.game.ServerboundInteractPacket$Action").getDeclaredMethod("getType");
        getType.setAccessible(true);
        assertEquals("ATTACK", ((Enum<?>) getType.invoke(a)).name());
        boolean hand = false, handAt = false;
        for (Class<?> c : outer.getDeclaredClasses()) {
            for (var k : c.getDeclaredConstructors()) {
                var p = k.getParameterTypes();
                if (p.length == 1 && p[0] == net.minecraft.world.InteractionHand.class) hand = true;
                if (p.length == 2 && p[0] == net.minecraft.world.InteractionHand.class && p[1] == net.minecraft.world.phys.Vec3.class) handAt = true;
            }
        }
        assertTrue(hand && handAt);
    }

    @Test
    void interactActionDeclaresGetType() throws Exception {
        Class<?> action = Class.forName("net.minecraft.network.protocol.game.ServerboundInteractPacket$Action");
        Method getType = action.getDeclaredMethod("getType");
        assertEquals("net.minecraft.network.protocol.game.ServerboundInteractPacket$ActionType", getType.getReturnType().getName());
    }
}
