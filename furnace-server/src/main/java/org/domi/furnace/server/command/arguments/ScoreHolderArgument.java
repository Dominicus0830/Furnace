// 바닐라 ScoreHolderArgument(minecraft:score_holder): 선택자(@…), "*"(이미 점수가 있는 모든 주인), UUID(엔티티), 이름.
// 엔티티 선택자·UUID 는 region 이 엔티티를 나눠 가져 해석이 나중에 끝날 수 있다 — 결과는 CompletableFuture 로 돌려준다.
package org.domi.furnace.server.command.arguments;

import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.command.EntityOps;
import org.domi.furnace.server.command.SharedSuggestions;
import org.domi.furnace.server.command.brigadier.ArgumentType;
import org.domi.furnace.server.command.brigadier.CommandContext;
import org.domi.furnace.server.command.brigadier.CommandSyntaxException;
import org.domi.furnace.server.command.brigadier.SimpleCommandExceptionType;
import org.domi.furnace.server.command.brigadier.StringReader;
import org.domi.furnace.server.command.brigadier.SuggestionProvider;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.entity.Rider;
import org.domi.furnace.server.scores.ScoreHolder;

public final class ScoreHolderArgument implements ArgumentType<ScoreHolderArgument.Result> {

    public static final SimpleCommandExceptionType ERROR_NO_RESULTS = new SimpleCommandExceptionType(Text.translatable("argument.scoreHolder.empty"));

    /** 바닐라 SUGGEST_SCORE_HOLDERS 의 이름 부분: 접속한 플레이어 이름(선택자 제안은 클라이언트가 한다). */
    public static final SuggestionProvider<CommandSource> SUGGEST_SCORE_HOLDERS = (c, b) -> {
        List<String> names = new ArrayList<>();
        for (Player p : c.getSource().getServer().players()) names.add(p.name());
        return SharedSuggestions.suggest(names, b);
    };

    @FunctionalInterface
    public interface Result {
        CompletableFuture<Collection<ScoreHolder>> getNames(CommandSource source, Supplier<Collection<ScoreHolder>> wildcard) throws CommandSyntaxException;
    }

    private final boolean multiple;

    private ScoreHolderArgument(boolean multiple) { this.multiple = multiple; }

    public static ScoreHolderArgument scoreHolder() { return new ScoreHolderArgument(false); }

    public static ScoreHolderArgument scoreHolders() { return new ScoreHolderArgument(true); }

    /** 바닐라 getName(단일): "*" 는 빈 목록이라 argument.scoreHolder.empty. */
    public static CompletableFuture<Collection<ScoreHolder>> getName(CommandContext<CommandSource> c, String name) throws CommandSyntaxException {
        return getNames(c, name, List::of);
    }

    /** 바닐라 getNamesWithDefaultWildcard: "*" 는 점수판이 아는 모든 주인. */
    public static CompletableFuture<Collection<ScoreHolder>> getNamesWithDefaultWildcard(CommandContext<CommandSource> c, String name) throws CommandSyntaxException {
        var board = c.getSource().getServer().scoreboard();
        return getNames(c, name, board::getTrackedPlayers);
    }

    private static CompletableFuture<Collection<ScoreHolder>> getNames(CommandContext<CommandSource> c, String name, Supplier<Collection<ScoreHolder>> wildcard)
            throws CommandSyntaxException {
        return c.getArgument(name, Result.class).getNames(c.getSource(), wildcard).thenApply(list -> {
            if (list.isEmpty()) throw new java.util.concurrent.CompletionException(EntitySelector.NO_ENTITIES_FOUND.create());
            return list;
        });
    }

    @Override
    public Result parse(StringReader r) throws CommandSyntaxException { return parse(r, true); }

    @Override
    public <S> Result parse(StringReader r, S source) throws CommandSyntaxException {
        return parse(r, !(source instanceof CommandSource cs) || cs.hasPermission(2));
    }

    private Result parse(StringReader r, boolean allowSelectors) throws CommandSyntaxException {
        if (r.canRead() && r.peek() == '@') {
            EntitySelector selector = new EntitySelectorParser(r, allowSelectors).parse();
            if (!multiple && selector.getMaxResults() > 1) throw EntityArgument.ERROR_NOT_SINGLE_ENTITY.createWithContext(r);
            return (source, wc) -> selectorNames(source, selector);
        }
        int start = r.getCursor();
        while (r.canRead() && r.peek() != ' ') r.skip();
        String s = r.getString().substring(start, r.getCursor());
        if (s.equals("*")) {
            return (source, wc) -> {
                Collection<ScoreHolder> all = wc.get();
                if (all.isEmpty()) throw ERROR_NO_RESULTS.create();
                return CompletableFuture.completedFuture(all);
            };
        }
        List<ScoreHolder> plain = List.of(ScoreHolder.forNameOnly(s));
        if (s.startsWith("#")) return (source, wc) -> CompletableFuture.completedFuture(plain);
        UUID uuid;
        try {
            uuid = UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            uuid = null;
        }
        if (uuid != null) {
            // 바닐라: 모든 레벨에서 그 UUID 의 엔티티(여럿이면 모두), 없으면 이름 그대로
            EntitySelector bySelector = new EntitySelectorParser(new StringReader(s), true).parse();
            return (source, wc) -> selectorNamesOrEmpty(source, bySelector).thenApply(l -> l.isEmpty() ? plain : l);
        }
        return (source, wc) -> {
            Player p = source.getServer().playerByName(s);
            return CompletableFuture.completedFuture(p != null ? List.of(ScoreHolder.of(p)) : plain);
        };
    }

    private static CompletableFuture<Collection<ScoreHolder>> selectorNamesOrEmpty(CommandSource source, EntitySelector selector) throws CommandSyntaxException {
        return selector.findRiders(source).thenApply(list -> {
            List<ScoreHolder> out = new ArrayList<>(list.size());
            for (Rider rd : list) out.add(ScoreHolder.of(rd));
            return out;
        });
    }

    private static CompletableFuture<Collection<ScoreHolder>> selectorNames(CommandSource source, EntitySelector selector) throws CommandSyntaxException {
        return selectorNamesOrEmpty(source, selector).thenApply(l -> {
            if (l.isEmpty()) throw new java.util.concurrent.CompletionException(EntitySelector.NO_ENTITIES_FOUND.create());
            return l;
        });
    }

    /** 결과 대기 도우미(껍데기): 이미 끝났으면 그 값을, 아니면 결과를 실행 컨텍스트에 맡기고(defer) 1(무시됨). action 은 전역 스레드에서. */
    @FunctionalInterface
    public interface Action<T> {
        int run(T value) throws CommandSyntaxException;
    }

    public static <T> int await(CommandSource source, CompletableFuture<T> future, Action<T> action) throws CommandSyntaxException {
        return EntityOps.result(source, awaitAsync(source, future, v -> CompletableFuture.completedFuture(action.run(v))));
    }

    /** await 의 future 판(중첩 헬퍼 안쪽에서). */
    public static <T> CompletableFuture<Integer> awaitAsync(CommandSource source, CompletableFuture<T> future, EntityOps.AsyncAction<T> action) {
        return EntityOps.awaitAsync(source, future, action);
    }

    @Override
    public Collection<String> getExamples() { return List.of("Player", "0123", "*", "@e"); }

    @Override
    public String networkId() { return "minecraft:score_holder"; }

    @Override
    public void writeProperties(ByteBuf buf) { buf.writeByte(multiple ? 1 : 0); }
}
