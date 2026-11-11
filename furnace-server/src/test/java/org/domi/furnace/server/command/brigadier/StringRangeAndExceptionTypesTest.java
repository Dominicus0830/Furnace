// 브리가디어 값 타입: StringRange 산술과 예외 종류 5개(메시지 인자·원인 줄 위치).
package org.domi.furnace.server.command.brigadier;

import static org.junit.jupiter.api.Assertions.*;

import org.domi.furnace.api.text.Text;
import org.junit.jupiter.api.Test;

class StringRangeAndExceptionTypesTest {

    @Test
    void rangeBasics() {
        StringRange r = StringRange.between(2, 5);
        assertEquals(3, r.getLength());
        assertEquals("cde", r.get("abcdefg"));
        assertFalse(r.isEmpty());
        assertTrue(StringRange.at(4).isEmpty());
        assertEquals(0, StringRange.at(4).getLength());
        assertEquals("", StringRange.at(4).get("abcdefg"));
        assertEquals("cde", r.get(new StringReader("abcdefg")));
    }

    @Test
    void encompassingTakesOuterBounds() {
        assertEquals(StringRange.between(1, 9), StringRange.encompassing(StringRange.between(3, 9), StringRange.between(1, 4)));
        assertEquals(StringRange.between(0, 3), StringRange.encompassing(StringRange.at(0), StringRange.between(1, 3)));
    }

    @Test
    void simpleTypeCreatesExceptionWithItsMessage() {
        var t = new SimpleCommandExceptionType(Text.translatable("my.key"));
        CommandSyntaxException e = t.create();
        assertEquals("my.key", e.getRawMessage().translationKey());
    }

    @Test
    void dynamicTypesPassArgumentsToTheirFunction() {
        var d1 = new DynamicCommandExceptionType(a -> Text.translatable("k1", a));
        assertEquals("k1", d1.create("x").getRawMessage().translationKey());
        var d2 = new Dynamic2CommandExceptionType((a, b) -> Text.translatable("k2", a, b));
        assertEquals("k2", d2.create(1, 2).getRawMessage().translationKey());
        var d3 = new Dynamic3CommandExceptionType((a, b, c) -> Text.translatable("k3", a, b, c));
        assertEquals("k3", d3.create(1, 2, 3).getRawMessage().translationKey());
        var d4 = new Dynamic4CommandExceptionType((a, b, c, d) -> Text.translatable("k4", a, b, c, d));
        assertEquals("k4", d4.create(1, 2, 3, 4).getRawMessage().translationKey());
    }

    @Test
    void exceptionsFromReaderRecordInputAndCursor() {
        StringReader reader = new StringReader("abc def");
        reader.setCursor(3);
        CommandSyntaxException e = new SimpleCommandExceptionType(Text.translatable("k")).createWithContext(reader);
        assertEquals(3, e.getCursor());
        assertEquals("abc def", e.getInput());
    }
}
