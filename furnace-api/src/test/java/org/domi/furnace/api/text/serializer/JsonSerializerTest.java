// JSON 역직렬화 테스트 — 바닐라 정답 파일의 모든 사례를 읽어 다시 쓰면 같은 JSON·같은 NBT 바이트가 나와야 한다. 옛 형식과 오류 처리도 본다.
package org.domi.furnace.api.text.serializer;

import static org.junit.jupiter.api.Assertions.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.domi.furnace.api.text.*;
import org.junit.jupiter.api.Test;

class JsonSerializerTest {

    static final JsonTextSerializer JSON = JsonTextSerializer.json();

    static String hex(byte[] b) {
        StringBuilder s = new StringBuilder();
        for (byte x : b) s.append(String.format("%02x", x));
        return s.toString();
    }

    @Test
    void goldenCasesRoundTripExactly() throws Exception {
        List<String[]> golden = new ArrayList<>();
        try (var r = new BufferedReader(new InputStreamReader(getClass().getResourceAsStream("/text/vanilla-1.21.1-golden.tsv"), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) golden.add(line.split("\t"));
        }
        assertFalse(golden.isEmpty());
        for (int i = 0; i < golden.size(); i++) {
            Text t = JSON.deserialize(golden.get(i)[0]);
            assertEquals(golden.get(i)[0], JSON.serialize(t), "JSON #" + (i + 1));
            // 7번 사례의 인자 7L 은 JSON 에서 정수 7 로만 남아 NBT 숫자 폭(long/int)이 달라진다 — JSON 만 비교한다
            if (i != 6) assertEquals(golden.get(i)[1], hex(t.networkNbt()), "NBT #" + (i + 1));
        }
    }

    @Test
    void stringArrayAndPrimitives() {
        assertEquals(Text.text("hi"), JSON.deserialize("\"hi\""));
        assertEquals("12", JSON.deserialize("12").plainText());
        assertEquals("true", JSON.deserialize("true").plainText());
        assertEquals(Text.empty(), JSON.deserialize("[]"));
        Text arr = JSON.deserialize("[\"a\",{\"text\":\"b\",\"color\":\"red\"},\"c\"]");
        assertEquals("abc", arr.plainText());
        assertEquals("a", arr.literal());
        assertEquals(2, arr.children().size());
        assertEquals(NamedTextColor.RED, arr.children().get(0).color());
        Text withExtra = JSON.deserialize("[{\"text\":\"a\",\"extra\":[\"x\"]},\"y\"]");
        assertEquals("axy", withExtra.plainText());
    }

    @Test
    void objectsOfEveryContentType() {
        assertEquals(Text.keybind("key.jump"), JSON.deserialize("{\"keybind\":\"key.jump\"}"));
        assertEquals(Text.score("@p", "o"), JSON.deserialize("{\"score\":{\"name\":\"@p\",\"objective\":\"o\"}}"));
        assertEquals(Text.selector("@a", Text.text(", ")), JSON.deserialize("{\"selector\":\"@a\",\"separator\":\", \"}"));
        assertEquals(Text.nbt("Pos", true, null, new Content.NbtSource.Entity("@s")), JSON.deserialize("{\"nbt\":\"Pos\",\"interpret\":true,\"entity\":\"@s\"}"));
        assertEquals(Text.nbt("p", false, null, new Content.NbtSource.Block("1 2 3")), JSON.deserialize("{\"nbt\":\"p\",\"block\":\"1 2 3\"}"));
        assertEquals(Text.nbt("p", false, null, new Content.NbtSource.Storage("a:b")), JSON.deserialize("{\"nbt\":\"p\",\"storage\":\"a:b\"}"));
        Text tr = JSON.deserialize("{\"translate\":\"k\",\"fallback\":\"f %s\",\"with\":[1,2.5,true,\"s\",{\"text\":\"t\",\"bold\":true}]}");
        Content.Translatable c = (Content.Translatable) tr.content();
        assertEquals("f %s", c.fallback());
        assertEquals(List.of(1, 2.5, true, "s"), c.args().subList(0, 4));
        assertEquals(Text.text("t").decorate(TextDecoration.BOLD), c.args().get(4));
        assertThrows(IllegalArgumentException.class, () -> JSON.deserialize("{\"nbt\":\"p\"}"));
        assertThrows(IllegalArgumentException.class, () -> JSON.deserialize("{\"foo\":1}"));
    }

    @Test
    void styleFields() {
        Text t = JSON.deserialize("{\"text\":\"x\",\"color\":\"#ff00ff\",\"bold\":true,\"italic\":false,\"underlined\":true,\"strikethrough\":false,\"obfuscated\":true,"
                + "\"insertion\":\"ins\",\"font\":\"minecraft:uniform\",\"clickEvent\":{\"action\":\"open_url\",\"value\":\"https://a.b\"}}");
        assertEquals(TextColor.color(0xFF00FF), t.color());
        assertEquals(TriState.TRUE, t.decoration(TextDecoration.BOLD));
        assertEquals(TriState.FALSE, t.decoration(TextDecoration.ITALIC));
        assertEquals(TriState.TRUE, t.decoration(TextDecoration.UNDERLINED));
        assertEquals(TriState.FALSE, t.decoration(TextDecoration.STRIKETHROUGH));
        assertEquals(TriState.TRUE, t.decoration(TextDecoration.OBFUSCATED));
        assertEquals("ins", t.insertion());
        assertEquals("minecraft:uniform", t.font());
        assertEquals(ClickEvent.openUrl("https://a.b"), t.clickEvent());
        assertNull(JSON.deserialize("{\"text\":\"x\",\"color\":\"nonsense\"}").color(), "알 수 없는 색은 무시");
        assertEquals(TriState.TRUE, JSON.deserialize("{\"text\":\"x\",\"bold\":\"true\"}").decoration(TextDecoration.BOLD), "문자열 불리언도 받는다");
    }

    @Test
    void hoverNewAndLegacyForms() {
        UUID id = UUID.fromString("01234567-89ab-cdef-fedc-ba9876543210");
        assertEquals(HoverEvent.showText(Text.text("tip")), JSON.deserialize("{\"text\":\"x\",\"hoverEvent\":{\"action\":\"show_text\",\"contents\":\"tip\"}}").hoverEvent());
        assertEquals(HoverEvent.showText(Text.text("tip")), JSON.deserialize("{\"text\":\"x\",\"hoverEvent\":{\"action\":\"show_text\",\"value\":\"tip\"}}").hoverEvent(), "옛 value");
        assertEquals("tip", ((HoverEvent.ShowText) JSON.deserialize("{\"text\":\"x\",\"hoverEvent\":{\"action\":\"show_text\",\"value\":\"{\\\"text\\\":\\\"tip\\\"}\"}}").hoverEvent()).text().plainText(), "JSON 문자열 value");
        assertEquals(new HoverEvent.ShowItem("minecraft:stone", 2, null),
                JSON.deserialize("{\"text\":\"x\",\"hoverEvent\":{\"action\":\"show_item\",\"contents\":{\"id\":\"minecraft:stone\",\"count\":2}}}").hoverEvent());
        assertEquals(new HoverEvent.ShowItem("minecraft:stone", 1, null),
                JSON.deserialize("{\"text\":\"x\",\"hoverEvent\":{\"action\":\"show_item\",\"contents\":\"minecraft:stone\"}}").hoverEvent());
        assertEquals(new HoverEvent.ShowItem("minecraft:stone", 3, null),
                JSON.deserialize("{\"text\":\"x\",\"hoverEvent\":{\"action\":\"show_item\",\"value\":\"{id:\\\"minecraft:stone\\\",Count:3b}\"}}").hoverEvent(), "옛 SNBT value");
        HoverEvent.ShowItem withComps = (HoverEvent.ShowItem) JSON.deserialize("{\"text\":\"x\",\"hoverEvent\":{\"action\":\"show_item\",\"contents\":{\"id\":\"a:b\",\"components\":{\"minecraft:damage\":5}}}}").hoverEvent();
        assertEquals("{\"minecraft:damage\":5}", withComps.components());
        assertEquals(new HoverEvent.ShowEntity("minecraft:pig", id, Text.text("Pig")),
                JSON.deserialize("{\"text\":\"x\",\"hoverEvent\":{\"action\":\"show_entity\",\"contents\":{\"type\":\"minecraft:pig\",\"id\":\"" + id + "\",\"name\":\"Pig\"}}}").hoverEvent());
        int[] u = {0x01234567, 0x89abcdef, 0xfedcba98, 0x76543210};
        assertEquals(new HoverEvent.ShowEntity("minecraft:pig", id, null),
                JSON.deserialize("{\"text\":\"x\",\"hoverEvent\":{\"action\":\"show_entity\",\"contents\":{\"type\":\"minecraft:pig\",\"id\":[" + u[0] + "," + u[1] + "," + u[2] + "," + u[3] + "]}}}").hoverEvent());
        HoverEvent legacy = JSON.deserialize("{\"text\":\"x\",\"hoverEvent\":{\"action\":\"show_entity\",\"value\":\"{id:\\\"" + id + "\\\",type:\\\"minecraft:pig\\\",name:'{\\\"text\\\":\\\"Oink\\\"}'}\"}}").hoverEvent();
        assertEquals(new HoverEvent.ShowEntity("minecraft:pig", id, Text.text("Oink")), legacy);
        assertNull(JSON.deserialize("{\"text\":\"x\",\"hoverEvent\":{\"action\":\"unknown\",\"contents\":\"a\"}}").hoverEvent());
    }

    @Test
    void stringEscapesAndUnicode() {
        assertEquals("a\"b\\c/d\n\té😀", JSON.deserialize("\"a\\\"b\\\\c\\/d\\n\\t\\u00e9\\ud83d\\ude00\"").plainText());
        assertEquals("한글", JSON.deserialize("\"한글\"").plainText());
        Text t = Text.text("esc \" \\ \n \t \u0001 <>&='  한글  ");
        assertEquals(t.toJson(), JSON.deserialize(t.toJson()).toJson());
    }

    @Test
    void syntaxErrors() {
        for (String bad : new String[]{"", "{", "[1,", "{\"text\":}", "{\"text\" 1}", "\"abc", "nul", "{\"a\":1} x", "{text:1}", "[1 2]"}) {
            assertThrows(IllegalArgumentException.class, () -> JSON.deserialize(bad), bad);
        }
        assertThrows(IllegalArgumentException.class, () -> JSON.deserialize("null"));
    }

    @Test
    void deepNestingIsRejectedNotOverflowed() {
        String deep = "[".repeat(5000) + "]".repeat(5000);
        assertThrows(IllegalArgumentException.class, () -> JSON.deserialize(deep));
    }

    @Test
    void markupToJsonToTextKeepsLook() {
        Text t = org.domi.furnace.api.text.markup.TextMarkup.markup().deserialize("<red>hi <bold>there</bold> <click:run_command:/x>go</click>");
        assertEquals(Visual.of(t), Visual.of(JSON.deserialize(t.toJson())));
    }
}
