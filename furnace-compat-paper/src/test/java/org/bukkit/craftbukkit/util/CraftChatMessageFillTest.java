package org.bukkit.craftbukkit.util;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import org.bukkit.ChatColor;
import org.junit.jupiter.api.Test;

/** CraftChatMessage 에 채운 변환·자르기·색 대응(Paper 1.21.1 몸통과 같은 규칙). */
class CraftChatMessageFillTest {

    @Test
    void nullStringGivesOneEmptyComponent() {
        Component[] parts = CraftChatMessage.fromString(null);
        assertEquals(1, parts.length);
        assertEquals("", parts[0].getString());
        assertTrue(CraftChatMessage.fromStringOrOptional(null).isEmpty());
        assertTrue(CraftChatMessage.fromStringOrOptional("").isEmpty());
        assertEquals("hi", CraftChatMessage.fromStringOrOptional("hi").orElseThrow().getString());
    }

    @Test
    void trimMessageCutsOnlyWhenTooLong() {
        String s = "abcdef";
        assertSame(s, CraftChatMessage.trimMessage(s, 6));
        assertEquals("abc", CraftChatMessage.trimMessage(s, 3));
        assertNull(CraftChatMessage.trimMessage(null, 3));
    }

    @Test
    void jsonOrStringHonoursNullableAndMaxLength() {
        assertNull(CraftChatMessage.fromJSONOrString("", true, false));
        assertEquals("", CraftChatMessage.fromJSONOrString("", false, false).getString());
        // 따옴표 없는 한 낱말도 관대 JSON 으로 읽혀 글이 되므로, 길이 검사를 켜야 잘린다(Paper 와 같다)
        assertEquals("abcdef", CraftChatMessage.fromJSONOrString("abcdef", false, false, 3, false).getString());
        assertEquals("abc", CraftChatMessage.fromJSONOrString("abcdef", false, false, 3, true).getString());
        assertEquals("abc", CraftChatMessage.fromJSONOrString("abc def", false, false, 3, false).getString(), "JSON 이 아니면 문자열 경로에서 자른다");
        Component json = CraftChatMessage.fromJSONOrString("{\"text\":\"abcdef\"}", false, false, 3, true);
        assertEquals("abc", json.getString(), "JSON 글도 내용 길이를 넘으면 자른 글(서식 제거)");
        Component whole = CraftChatMessage.fromJSONOrString("{\"text\":\"abc\"}", false, false, 10, true);
        assertEquals("abc", whole.getString());
        assertNull(CraftChatMessage.toJSONOrNull(null));
        assertNotNull(CraftChatMessage.toJSONOrNull(whole));
    }

    @Test
    void colorMapsBothWays() {
        assertEquals(ChatFormatting.RED, CraftChatMessage.getColor(ChatColor.RED));
        assertEquals(ChatColor.BOLD, CraftChatMessage.getColor(ChatFormatting.BOLD));
    }
}
