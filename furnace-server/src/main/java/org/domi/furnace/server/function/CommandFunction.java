// 바닐라 CommandFunction(ig) + PlainTextFunction(ik) — .mcfunction 한 개. 줄은 로드 시점에 파싱해 둔 UnboundEntryAction 목록이다.
// 매크로 줄($)은 S7 에서 MacroFunction 으로 이식한다 — 지금은 그런 함수를 로드 실패로 빼 둔다(근사 실행을 하지 않는다).
package org.domi.furnace.server.function;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.command.Commands;
import org.domi.furnace.server.command.brigadier.CommandDispatcher;
import org.domi.furnace.server.command.brigadier.CommandSyntaxException;
import org.domi.furnace.server.command.brigadier.ContextChain;
import org.domi.furnace.server.command.brigadier.ParseResults;
import org.domi.furnace.server.command.brigadier.StringReader;
import org.domi.furnace.server.command.exec.BuildContexts;
import org.domi.furnace.server.command.exec.UnboundEntryAction;
import org.domi.furnace.server.data.ResourceId;

public interface CommandFunction {

    /** 바닐라 ig.a(CharSequence) 의 상한. */
    int MAX_COMMAND_LENGTH = 2_000_000;

    ResourceId id();

    /** 실행할 줄들(바닐라 InstantiatedFunction.entries). 매크로가 없으므로 인스턴스화가 곧 자기 자신이다. */
    List<UnboundEntryAction> entries();

    record PlainTextFunction(ResourceId id, List<UnboundEntryAction> entries) implements CommandFunction {}

    private static boolean shouldConcatenateNextLine(CharSequence line) {
        int n = line.length();
        return n > 0 && line.charAt(n - 1) == '\\';
    }

    /** 바닐라 ig.a(akr, CommandDispatcher, T, List): 줄 이어 붙이기·주석·'/' 오류·파싱. 실패는 IllegalArgumentException/IllegalStateException. */
    static CommandFunction fromLines(ResourceId id, CommandDispatcher<CommandSource> dispatcher, CommandSource compilationContext, List<String> lines) {
        List<UnboundEntryAction> entries = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            int lineNumber = i + 1;
            String inputLine = lines.get(i).trim();
            String line;
            if (shouldConcatenateNextLine(inputLine)) {
                StringBuilder sb = new StringBuilder(inputLine);
                do {
                    if (++i == lines.size()) throw new IllegalArgumentException("Line continuation at end of file");
                    sb.deleteCharAt(sb.length() - 1);
                    sb.append(lines.get(i).trim());
                    checkCommandLineLength(sb);
                } while (shouldConcatenateNextLine(sb));
                line = sb.toString();
            } else {
                line = inputLine;
            }
            checkCommandLineLength(line);
            StringReader reader = new StringReader(line);
            if (!reader.canRead() || reader.peek() == '#') continue;
            if (reader.peek() == '/') {
                reader.skip();
                if (reader.peek() == '/') {
                    throw new IllegalArgumentException("Unknown or invalid command '" + line + "' on line " + lineNumber
                            + " (if you intended to make a comment, use '#' not '//')");
                }
                String name = reader.readUnquotedString();
                throw new IllegalArgumentException("Unknown or invalid command '" + line + "' on line " + lineNumber + " (did you mean '" + name
                        + "'? Do not use a preceding forwards slash.)");
            }
            if (reader.peek() == '$') {
                // 가정: 매크로는 S7. 바닐라는 MacroFunction 으로 로드하지만 지금은 그 함수를 빼 둔다
                throw new IllegalArgumentException("Macro line on line " + lineNumber + " is not supported yet (매크로는 S7)");
            }
            try {
                entries.add(parseCommand(dispatcher, compilationContext, reader));
            } catch (CommandSyntaxException e) {
                throw new IllegalArgumentException("Whilst parsing command on line " + lineNumber + ": " + e.getMessage());
            }
        }
        return new PlainTextFunction(id, List.copyOf(entries));
    }

    static void checkCommandLineLength(CharSequence s) {
        if (s.length() > MAX_COMMAND_LENGTH) {
            CharSequence head = s.subSequence(0, Math.min(512, MAX_COMMAND_LENGTH));
            throw new IllegalStateException("Command too long: " + s.length() + " characters, contents: " + head + "...");
        }
    }

    /** 바닐라 ig.a(dispatcher, source, reader): parse → validate → tryFlatten → Unbound. */
    static UnboundEntryAction parseCommand(CommandDispatcher<CommandSource> dispatcher, CommandSource source, StringReader reader)
            throws CommandSyntaxException {
        ParseResults<CommandSource> parse = dispatcher.parse(reader, source);
        Commands.validateParseResults(parse);
        Optional<ContextChain<CommandSource>> chain = ContextChain.tryFlatten(parse.getContext().build(reader.getString()));
        if (chain.isEmpty()) throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownCommand().createWithContext(parse.getReader());
        return new BuildContexts.Unbound(reader.getString(), chain.get());
    }
}
