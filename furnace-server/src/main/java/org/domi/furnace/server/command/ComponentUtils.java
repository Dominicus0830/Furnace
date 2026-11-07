// 바닐라 ComponentUtils.updateForEntity — 명령이 보내기 직전에 텍스트 안의 선택자·점수·NBT 를 값으로 풀어 쓴다.
// 엔티티는 region 이 나눠 갖고 있어 선택자(@e)와 엔티티·블록 NBT 는 그 주인 스레드에서 읽어야 한다 — 그래서 결과가 future 다.
// 전부 플레이어·저장소만 건드리면 future 는 이미 끝나 있다(EntityOps.result 가 그 자리에서 값을 돌려준다).
package org.domi.furnace.server.command;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.domi.furnace.api.text.Content;
import org.domi.furnace.api.text.HoverEvent;
import org.domi.furnace.api.text.NamedTextColor;
import org.domi.furnace.api.text.Style;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.command.arguments.BlockPosArgument;
import org.domi.furnace.server.command.arguments.EntityArgument;
import org.domi.furnace.server.command.arguments.EntitySelector;
import org.domi.furnace.server.command.arguments.EntitySelectorParser;
import org.domi.furnace.server.command.arguments.NbtPathArgument;
import org.domi.furnace.server.command.brigadier.CommandSyntaxException;
import org.domi.furnace.server.command.brigadier.StringReader;
import org.domi.furnace.server.entity.Rider;
import org.domi.furnace.server.item.Snbt;
import org.domi.furnace.server.net.io.nbt.NbtTags;
import org.domi.furnace.server.scores.NumberFormat;
import org.domi.furnace.server.scores.ScoreHolder;

public final class ComponentUtils {

    private static final int MAX_DEPTH = 100;
    /** 바닐라 StyledFormat.NO_STYLE: 스타일 없는 수 그대로. */
    private static final NumberFormat NO_STYLE = new NumberFormat.Styled(Style.empty());

    private ComponentUtils() {}

    /** 바닐라 DEFAULT_SEPARATOR — 회색 ", ". */
    public static Text defaultSeparator() { return Text.text(", ").color(NamedTextColor.GRAY); }

    /** 바닐라 DEFAULT_NO_STYLE_SEPARATOR — 스타일 없는 ", ". */
    public static Text defaultNoStyleSeparator() { return Text.text(", "); }

    /**
     * 바닐라 ComponentUtils.updateForEntity. entity 는 {"score":{"name":"*"}} 가 가리키는 대상(없으면 null).
     * 선택자 문법 오류는 빈 텍스트, 점수·NBT 해석 실패는 빈 텍스트. 선택자가 가리키는 대상이 여럿이면 score 이름만 오류를 던진다(바닐라와 같다).
     */
    public static CompletableFuture<Text> resolve(CommandSource source, Text component, Rider entity) {
        return resolve(source, component, entity, 0);
    }

    static CompletableFuture<Text> resolve(CommandSource source, Text component, Rider entity, int depth) {
        if (source == null || depth > MAX_DEPTH) return CompletableFuture.completedFuture(component);
        int d = depth + 1;
        Content content = component.content();
        CompletableFuture<Text> head;
        try {
            if (content instanceof Content.Selector sel) {
                head = resolveSelector(source, sel, entity, d);
            } else if (content instanceof Content.Score score) {
                head = resolveScore(source, score, entity);
            } else if (content instanceof Content.Nbt nbt) {
                head = resolveNbt(source, nbt, entity, d);
            } else if (content instanceof Content.Translatable tr) {
                head = resolveTranslatable(source, tr, entity, d);
            } else {
                head = CompletableFuture.completedFuture(Text.of(content, Style.empty(), List.of()));
            }
        } catch (CommandSyntaxException e) {
            return CompletableFuture.failedFuture(e);
        }
        List<CompletableFuture<Text>> siblings = new ArrayList<>(component.children().size());
        for (Text child : component.children()) siblings.add(resolve(source, child, entity, d));
        CompletableFuture<Text> style = resolveStyle(source, component.style(), entity, d);
        List<CompletableFuture<?>> all = new ArrayList<>(siblings.size() + 2);
        all.add(head);
        all.addAll(siblings);
        all.add(style);
        return then(source, CompletableFuture.allOf(all.toArray(new CompletableFuture[0])), v -> {
            Text out = head.join();
            for (CompletableFuture<Text> s : siblings) out = out.append(s.join());
            Text hover = style.join();
            // 바닐라 withStyle: 이 글이 정한 스타일만 덮고, 풀어 쓴 글 자신의 스타일(개체 표시 이름의 호버·팀 색 등)은 남긴다
            Style own = hover == null ? component.style() : component.style().hoverEvent(HoverEvent.showText(hover));
            return out.style(own.applyTo(out.style()));
        });
    }

    /** 호버 텍스트를 푼 결과를 담은 임시 텍스트(스타일만 꺼내 쓴다). 호버가 없으면 null 로 끝나 스타일 그대로. */
    private static CompletableFuture<Text> resolveStyle(CommandSource source, Style style, Rider entity, int depth) {
        if (style.hoverEvent() instanceof HoverEvent.ShowText st) return resolve(source, st.text(), entity, depth);
        return CompletableFuture.completedFuture(null);
    }

    private static CompletableFuture<Text> resolveTranslatable(CommandSource source, Content.Translatable tr, Rider entity, int depth) {
        List<CompletableFuture<Object>> args = new ArrayList<>(tr.args().size());
        for (Object a : tr.args()) {
            if (a instanceof Text t) args.add(resolve(source, t, entity, depth).thenApply(x -> (Object) x));
            else args.add(CompletableFuture.completedFuture(a));
        }
        return then(source, CompletableFuture.allOf(args.toArray(new CompletableFuture[0])), v -> {
            List<Object> out = new ArrayList<>(args.size());
            for (CompletableFuture<Object> f : args) out.add(f.join());
            return Text.of(new Content.Translatable(tr.key(), tr.fallback(), out), Style.empty(), List.of());
        });
    }

    // ---------------------------------------------------------------- 선택자

    /** 바닐라 SelectorContents.resolve — 찾은 대상의 표시 이름을 구분자로 잇는다. 선택자 문법이 틀리면 빈 텍스트. */
    private static CompletableFuture<Text> resolveSelector(CommandSource source, Content.Selector sel, Rider entity, int depth)
            throws CommandSyntaxException {
        EntitySelector selector;
        try {
            selector = new EntitySelectorParser(new StringReader(sel.pattern()), true).parse();
        } catch (CommandSyntaxException e) {
            return CompletableFuture.completedFuture(Text.empty());
        }
        CompletableFuture<Text> sep = sel.separator() == null ? CompletableFuture.completedFuture(defaultSeparator())
                : resolve(source, sel.separator(), entity, depth);
        CompletableFuture<List<Rider>> found = selector.findRiders(source);
        return then(source, CompletableFuture.allOf(sep, found), v -> formatList(found.join(), sep.join()));
    }

    /** 바닐라 ComponentUtils.formatList: 비면 빈 텍스트, 하나면 그 이름, 여럿이면 구분자로 잇는다. */
    public static Text formatList(List<Rider> riders, Text separator) {
        if (riders.isEmpty()) return Text.empty();
        if (riders.size() == 1) return riders.get(0).displayName();
        Text out = Text.empty();
        for (int i = 0; i < riders.size(); i++) {
            if (i > 0) out = out.append(separator);
            out = out.append(riders.get(i).displayName());
        }
        return out;
    }

    // ---------------------------------------------------------------- 점수

    /**
     * 바닐라 ScoreContents.resolve. name 이 @선택자면 찾은 대상(둘 이상이면 오류, 없으면 이름 그대로), "*" 는 entity.
     * 목표가 없거나 점수가 없으면 빈 텍스트. 점수는 점수판 잠금 안에서 읽는다.
     */
    private static CompletableFuture<Text> resolveScore(CommandSource source, Content.Score score, Rider entity) throws CommandSyntaxException {
        EntitySelector selector = null;
        if (score.name().startsWith("@")) {
            try {
                selector = new EntitySelectorParser(new StringReader(score.name()), true).parse();
            } catch (CommandSyntaxException e) {
                // 바닐라: 선택자 문법이 틀리면 그냥 이름 글자로 본다
            }
        }
        if (selector == null) return CompletableFuture.completedFuture(scoreText(source, ScoreHolder.forNameOnly(score.name()), entity, score.objective()));
        return then(source, selector.findRiders(source), found -> {
            if (found.size() > 1) throw EntityArgument.ERROR_NOT_SINGLE_ENTITY.create();
            ScoreHolder holder = found.isEmpty() ? ScoreHolder.forNameOnly(score.name()) : ScoreHolder.of(found.get(0));
            return scoreText(source, holder, entity, score.objective());
        });
    }

    private static Text scoreText(CommandSource source, ScoreHolder holder, Rider entity, String objectiveName) {
        ScoreHolder resolved = entity != null && holder.scoreboardName().equals(ScoreHolder.WILDCARD_NAME) ? ScoreHolder.of(entity) : holder;
        var board = source.getServer().scoreboard();
        if (board == null) return Text.empty();
        var objective = board.getObjective(objectiveName);
        if (objective == null) return Text.empty();
        var info = board.getPlayerScoreInfo(resolved, objective);
        if (info == null) return Text.empty();
        return info.formatValue(objective.numberFormatOrDefault(NO_STYLE));
    }

    // ---------------------------------------------------------------- NBT

    /**
     * 바닐라 NbtContents.resolve. 출처(엔티티·블록·저장소)의 NBT 에서 경로로 태그를 모아, interpret 면 텍스트로 읽어 풀고
     * 아니면 getAsString 글자 그대로 보인다. 구분자 기본은 interpret 이면 스타일 없는 ", ", 아니면 "," 로 이은 글 하나(바닐라 NbtContents).
     */
    private static CompletableFuture<Text> resolveNbt(CommandSource source, Content.Nbt nbt, Rider entity, int depth) throws CommandSyntaxException {
        NbtPathArgument.NbtPath path;
        try {
            path = NbtPathArgument.of(nbt.path());
        } catch (CommandSyntaxException e) {
            return CompletableFuture.completedFuture(Text.empty());   // 바닐라: 경로가 틀리면 빈 텍스트
        }
        CompletableFuture<Text> sep = nbt.separator() == null ? CompletableFuture.completedFuture(defaultNoStyleSeparator())
                : resolve(source, nbt.separator(), entity, depth);
        CompletableFuture<List<org.domi.furnace.server.net.io.nbt.NbtCompound>> roots =
                org.domi.furnace.server.command.builtin.DataCommands.textSources(source, nbt.source());
        CompletableFuture<List<Object>> tags = roots.thenApply(list -> {
            List<Object> out = new ArrayList<>();
            for (var root : list) {
                try {
                    out.addAll(path.get(root));
                } catch (CommandSyntaxException ignored) {
                    // 찾는 게 없으면 건너뛴다
                }
            }
            return out;
        });
        if (!nbt.interpret()) {
            // 바닐라: 태그마다 getAsString 을 글자 그대로(색 없이) 보인다. 구분자를 안 주면 ","(공백 없음)로 이은 글 하나
            if (nbt.separator() == null) {
                return then(source, tags, list -> Text.text(String.join(",", list.stream().map(ComponentUtils::tagAsString).toList())));
            }
            return then(source, CompletableFuture.allOf(sep, tags),
                    v -> join(tags.join().stream().map(t -> Text.text(tagAsString(t))).toList(), sep.join()));
        }
        // interpret: 각 태그를 텍스트로 읽어 다시 풀어 쓴다(읽을 수 없으면 건너뛴다)
        return then(source, CompletableFuture.allOf(sep, tags), v -> {
            List<CompletableFuture<Text>> parts = new ArrayList<>();
            for (Object tag : tags.join()) {
                Text t = tag instanceof String str ? parseJsonText(str) : org.domi.furnace.server.scores.TextTags.tagToText(tag);
                if (t != null) parts.add(resolve(source, t, entity, depth));
            }
            return then(source, CompletableFuture.allOf(parts.toArray(new CompletableFuture[0])), w -> {
                List<Text> texts = new ArrayList<>(parts.size());
                for (var p : parts) texts.add(p.join());
                return join(texts, sep.join());
            });
        }).thenCompose(f -> f);
    }

    /** 바닐라 Tag.getAsString: 문자열 태그는 그 값, 나머지는 SNBT. */
    private static String tagAsString(Object tag) {
        return tag instanceof String s ? s : Snbt.write(NbtTags.freeze(NbtTags.copy(tag)));
    }

    /** interpret: 문자열 태그의 내용을 JSON 글로 읽는다(바닐라 Component.Serializer.fromJson). 읽을 수 없으면 null. */
    private static Text parseJsonText(String json) {
        try {
            return org.domi.furnace.api.text.serializer.JsonTextSerializer.json().deserialize(json);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static Text join(List<Text> parts, Text separator) {
        Text out = null;
        for (Text p : parts) out = out == null ? p : out.append(separator).append(p);
        return out == null ? Text.empty() : out;
    }

    // ---------------------------------------------------------------- 보조

    private static <T, R> CompletableFuture<R> then(CommandSource source, CompletableFuture<T> f, EntityOps.ThrowingFunction<T, R> fn) {
        return EntityOps.thenOnGlobal(source, f, fn);
    }
}
