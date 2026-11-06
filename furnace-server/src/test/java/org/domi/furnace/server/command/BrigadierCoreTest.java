// Brigadier 호환 코어: 파싱·실행·리다이렉트·오류 위치·자동완성·사용법이 Brigadier 규칙대로인지.
package org.domi.furnace.server.command;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.domi.furnace.server.command.brigadier.Arguments;
import org.domi.furnace.server.command.brigadier.CommandDispatcher;
import org.domi.furnace.server.command.brigadier.CommandSyntaxException;
import org.domi.furnace.server.command.brigadier.LiteralArgumentBuilder;
import org.domi.furnace.server.command.brigadier.LiteralCommandNode;
import org.domi.furnace.server.command.brigadier.RequiredArgumentBuilder;
import org.domi.furnace.server.command.brigadier.StringReader;
import org.domi.furnace.server.command.brigadier.Suggestions;
import org.junit.jupiter.api.Test;

class BrigadierCoreTest {

    private static LiteralArgumentBuilder<Object> lit(String n) { return LiteralArgumentBuilder.literal(n); }

    @Test
    void parsesAndExecutesWithArguments() throws Exception {
        CommandDispatcher<Object> d = new CommandDispatcher<>();
        List<Integer> seen = new ArrayList<>();
        d.register(lit("add").then(RequiredArgumentBuilder.<Object, Integer>argument("a", Arguments.integer(0, 10))
                .then(RequiredArgumentBuilder.<Object, Integer>argument("b", Arguments.integer())
                        .executes(c -> { seen.add(c.getArgument("a", Integer.class) + c.getArgument("b", Integer.class)); return 7; }))));
        assertEquals(7, d.execute("add 3 4", new Object()));
        assertEquals(List.of(7), seen);
    }

    @Test
    void errorsCarryVanillaKeysAndCursor() {
        CommandDispatcher<Object> d = new CommandDispatcher<>();
        d.register(lit("add").then(RequiredArgumentBuilder.<Object, Integer>argument("a", Arguments.integer(0, 10)).executes(c -> 1)));
        CommandSyntaxException e = assertThrows(CommandSyntaxException.class, () -> d.execute("add 11", new Object()));
        assertEquals("Integer must not be more than 10, found 11", e.getRawMessage().getString());
        assertEquals(4, e.getCursor());
        e = assertThrows(CommandSyntaxException.class, () -> d.execute("nope", new Object()));
        assertEquals("Unknown or incomplete command, see below for error", e.getRawMessage().getString());
        assertEquals(0, e.getCursor(), "알 수 없는 명령은 처음 위치(화면에는 나머지가 빨간 밑줄로 보인다)");
        e = assertThrows(CommandSyntaxException.class, () -> d.execute("add", new Object()));
        assertEquals("Unknown or incomplete command, see below for error", e.getRawMessage().getString());
        e = assertThrows(CommandSyntaxException.class, () -> d.execute("add 1 2", new Object()));
        assertEquals("Incorrect argument for command", e.getRawMessage().getString());
    }

    @Test
    void redirectAndFork() throws Exception {
        CommandDispatcher<Object> d = new CommandDispatcher<>();
        LiteralCommandNode<Object> target = d.register(lit("teleport").then(lit("x").executes(c -> 5)));
        d.register(lit("tp").redirect(target));
        assertEquals(5, d.execute("tp x", new Object()));
        d.register(lit("twice").fork(d.getRoot(), c -> List.of(c.getSource(), c.getSource())));
        assertEquals(2, d.execute("twice teleport x", new Object()), "포크된 실행은 성공 수를 센다");
    }

    @Test
    void quotedStringsAndEscapes() throws Exception {
        StringReader r = new StringReader("\"a \\\"b\\\" c\" rest");
        assertEquals("a \"b\" c", r.readString());
        CommandSyntaxException e = assertThrows(CommandSyntaxException.class, () -> new StringReader("\"abc").readQuotedString());
        assertEquals("Unclosed quoted string", e.getRawMessage().getString());
    }

    @Test
    void suggestionsAndUsage() throws Exception {
        CommandDispatcher<Object> d = new CommandDispatcher<>();
        d.register(lit("whitelist").then(lit("add")).then(lit("on").executes(c -> 1)).then(lit("off").executes(c -> 1)));
        Suggestions s = d.getCompletionSuggestions(d.parse("whitelist o", new Object())).get();
        assertEquals(List.of("off", "on"), s.getList().stream().map(x -> x.getText()).toList());
        assertEquals(10, s.getRange().getStart());
        var usage = d.getSmartUsage(d.getRoot(), new Object());
        assertEquals("whitelist (add|on|off)", usage.values().iterator().next());
    }
}
