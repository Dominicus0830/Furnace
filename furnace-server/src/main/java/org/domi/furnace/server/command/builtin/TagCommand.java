// 바닐라 TagCommand: /tag <대상> add|remove <이름> | list. 태그는 엔티티(플레이어 포함)의 Tags NBT 로 저장된다.
// 차이: 일반 엔티티가 대상이면 반환값이 늘 1 이고 결과는 나중에 메시지로 온다(context-notes 의 "반환값 차이").
package org.domi.furnace.server.command.builtin;

import static org.domi.furnace.server.command.Commands.argument;
import static org.domi.furnace.server.command.Commands.literal;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.domi.furnace.api.text.NamedTextColor;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.command.EntityOps;
import org.domi.furnace.server.command.SharedSuggestions;
import org.domi.furnace.server.command.arguments.EntityArgument;
import org.domi.furnace.server.command.arguments.EntitySelector;
import org.domi.furnace.server.command.brigadier.Arguments;
import org.domi.furnace.server.command.brigadier.CommandDispatcher;
import org.domi.furnace.server.command.brigadier.CommandSyntaxException;
import org.domi.furnace.server.command.brigadier.SimpleCommandExceptionType;
import org.domi.furnace.server.entity.Rider;

public final class TagCommand {
    private TagCommand() { }

    private static final SimpleCommandExceptionType ERROR_ADD_FAILED = new SimpleCommandExceptionType(Text.translatable("commands.tag.add.failed"));
    private static final SimpleCommandExceptionType ERROR_REMOVE_FAILED = new SimpleCommandExceptionType(Text.translatable("commands.tag.remove.failed"));

    public static void register(CommandDispatcher<CommandSource> d) {
        d.register(literal("tag").requires(s -> s.hasPermission(2))
                .then(argument("targets", EntityArgument.entities())
                        .then(literal("add").then(argument("name", Arguments.word())
                                .executes(c -> EntityOps.result(c.getSource(), EntityArgument.withEntitiesAsync(c, "targets",
                                        t -> add(c.getSource(), t, c.getArgument("name", String.class)))))))
                        .then(literal("remove").then(argument("name", Arguments.word())
                                .suggests(TagCommand::suggestTags)
                                .executes(c -> EntityOps.result(c.getSource(), EntityArgument.withEntitiesAsync(c, "targets",
                                        t -> remove(c.getSource(), t, c.getArgument("name", String.class)))))))
                        .then(literal("list").executes(c -> EntityOps.result(c.getSource(), EntityArgument.withEntitiesAsync(c, "targets", t -> list(c.getSource(), t)))))));
    }

    /** 바닐라 SUGGEST: 고른 엔티티들이 가진 태그 전부. 일반 엔티티의 태그는 주인 region 에서 읽는다. */
    private static CompletableFuture<org.domi.furnace.server.command.brigadier.Suggestions> suggestTags(
            org.domi.furnace.server.command.brigadier.CommandContext<CommandSource> c, org.domi.furnace.server.command.brigadier.SuggestionsBuilder b) {
        CommandSource source = c.getSource();
        try {
            return c.getArgument("targets", EntitySelector.class).findRiders(source).thenCompose(riders -> {
                List<CompletableFuture<Set<String>>> fs = new java.util.ArrayList<>(riders.size());
                for (Rider r : riders) fs.add(EntityOps.onOwner(source, r, x -> (Set<String>) new HashSet<>(x.getTags())).exceptionally(t -> Set.of()));
                return CompletableFuture.allOf(fs.toArray(new CompletableFuture[0])).thenCompose(v -> {
                    Set<String> all = new HashSet<>();
                    for (var f : fs) all.addAll(f.join());
                    return SharedSuggestions.suggest(all, b);
                });
            });
        } catch (CommandSyntaxException e) {
            return org.domi.furnace.server.command.brigadier.Suggestions.empty();   // 해석 실패면 제안 없음
        }
    }

    private static java.util.concurrent.CompletableFuture<Integer> add(CommandSource source, List<Rider> targets, String tag) {
        return EntityOps.forEachAsync(source, targets, r -> r.addTag(tag), results -> {
            int n = 0;
            for (boolean b : results) if (b) n++;
            if (n == 0) throw ERROR_ADD_FAILED.create();
            if (targets.size() == 1) source.sendSuccess(() -> Text.translatable("commands.tag.add.success.single", tag, targets.get(0).displayName()), true);
            else source.sendSuccess(() -> Text.translatable("commands.tag.add.success.multiple", tag, targets.size()), true);
            return n;
        });
    }

    private static java.util.concurrent.CompletableFuture<Integer> remove(CommandSource source, List<Rider> targets, String tag) {
        return EntityOps.forEachAsync(source, targets, r -> r.removeTag(tag), results -> {
            int n = 0;
            for (boolean b : results) if (b) n++;
            if (n == 0) throw ERROR_REMOVE_FAILED.create();
            if (targets.size() == 1) source.sendSuccess(() -> Text.translatable("commands.tag.remove.success.single", tag, targets.get(0).displayName()), true);
            else source.sendSuccess(() -> Text.translatable("commands.tag.remove.success.multiple", tag, targets.size()), true);
            return n;
        });
    }

    private static java.util.concurrent.CompletableFuture<Integer> list(CommandSource source, List<Rider> targets) {
        return EntityOps.forEachAsync(source, targets, Rider::getTags, results -> {
            Set<String> all = new HashSet<>();
            for (Set<String> s : results) all.addAll(s);
            if (targets.size() == 1) {
                Rider r = targets.get(0);
                if (all.isEmpty()) source.sendSuccess(() -> Text.translatable("commands.tag.list.single.empty", r.displayName()), false);
                else source.sendSuccess(() -> Text.translatable("commands.tag.list.single.success", r.displayName(), all.size(), formatTags(all)), false);
            } else if (all.isEmpty()) {
                source.sendSuccess(() -> Text.translatable("commands.tag.list.multiple.empty", targets.size()), false);
            } else {
                source.sendSuccess(() -> Text.translatable("commands.tag.list.multiple.success", targets.size(), all.size(), formatTags(all)), false);
            }
            return all.size();
        });
    }

    /** 바닐라 ComponentUtils.formatList(문자열): 정렬해 초록 글자로, 회색 ", " 로 잇는다. */
    static Text formatTags(java.util.Collection<String> tags) {
        if (tags.isEmpty()) return Text.empty();
        List<String> sorted = new ArrayList<>(tags);
        java.util.Collections.sort(sorted);
        if (sorted.size() == 1) return Text.text(sorted.get(0)).color(NamedTextColor.GREEN);
        Text out = Text.empty();
        for (int i = 0; i < sorted.size(); i++) {
            if (i > 0) out = out.append(Text.text(", ").color(NamedTextColor.GRAY));
            out = out.append(Text.text(sorted.get(i)).color(NamedTextColor.GREEN));
        }
        return out;
    }
}
