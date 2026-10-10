// 바닐라 SignableCommand — 파싱 결과에서 서명 대상 인자(message 형식)의 이름과 입력 원문을 모은다(리다이렉트 사슬 포함, 루트 재진입 제외).
package org.domi.furnace.server.command;

import java.util.ArrayList;
import java.util.List;
import org.domi.furnace.server.command.arguments.MessageArgument;
import org.domi.furnace.server.command.brigadier.ArgumentCommandNode;
import org.domi.furnace.server.command.brigadier.CommandContextBuilder;
import org.domi.furnace.server.command.brigadier.ParseResults;
import org.domi.furnace.server.command.brigadier.ParsedArgument;
import org.domi.furnace.server.command.brigadier.ParsedCommandNode;

public record SignableCommand(List<Argument> arguments) {

    public record Argument(String name, String value) {}

    public static boolean hasSignableArguments(ParseResults<CommandSource> parse) { return !of(parse).arguments().isEmpty(); }

    public static SignableCommand of(ParseResults<CommandSource> parse) {
        String input = parse.getReader().getString();
        CommandContextBuilder<CommandSource> root = parse.getContext();
        List<Argument> out = collect(input, root);
        CommandContextBuilder<CommandSource> cur = root, child;
        while ((child = cur.getChild()) != null && child.getRootNode() != root.getRootNode()) {
            out.addAll(collect(input, child));
            cur = child;
        }
        return new SignableCommand(out);
    }

    private static List<Argument> collect(String input, CommandContextBuilder<CommandSource> ctx) {
        List<Argument> out = new ArrayList<>();
        for (ParsedCommandNode<CommandSource> n : ctx.getNodes()) {
            if (n.getNode() instanceof ArgumentCommandNode<CommandSource, ?> a && a.getType() instanceof MessageArgument) {
                ParsedArgument<CommandSource, ?> parsed = ctx.getArguments().get(a.getName());
                if (parsed != null) out.add(new Argument(a.getName(), parsed.getRange().get(input)));
            }
        }
        return out;
    }

    public Argument get(String name) {
        for (Argument a : arguments) if (a.name().equals(name)) return a;
        return null;
    }
}
