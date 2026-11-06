// 바닐라 EntityArgument(minecraft:entity) — 속성 바이트: 0x01 단일, 0x02 플레이어만.
package org.domi.furnace.server.command.arguments;

import io.netty.buffer.ByteBuf;
import java.util.Collection;
import java.util.List;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.command.brigadier.ArgumentType;
import org.domi.furnace.server.command.brigadier.CommandContext;
import org.domi.furnace.server.command.brigadier.CommandSyntaxException;
import org.domi.furnace.server.command.brigadier.SimpleCommandExceptionType;
import org.domi.furnace.server.command.brigadier.StringReader;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.entity.Rider;
import org.domi.furnace.api.text.Text;

public final class EntityArgument implements ArgumentType<EntitySelector> {

    public static final SimpleCommandExceptionType ERROR_NOT_SINGLE_ENTITY = new SimpleCommandExceptionType(Text.translatable("argument.entity.toomany"));
    public static final SimpleCommandExceptionType ERROR_NOT_SINGLE_PLAYER = new SimpleCommandExceptionType(Text.translatable("argument.player.toomany"));
    public static final SimpleCommandExceptionType ERROR_ONLY_PLAYERS_ALLOWED = new SimpleCommandExceptionType(Text.translatable("argument.player.entities"));
    private static final List<String> EXAMPLES = List.of("Player", "0123", "@e", "@e[type=foo]", "dd12be42-52a9-4a91-a8a1-11c01849e498");

    final boolean single;
    final boolean playersOnly;

    private EntityArgument(boolean single, boolean playersOnly) {
        this.single = single;
        this.playersOnly = playersOnly;
    }

    public static EntityArgument entity() { return new EntityArgument(true, false); }

    public static EntityArgument entities() { return new EntityArgument(false, false); }

    public static EntityArgument player() { return new EntityArgument(true, true); }

    public static EntityArgument players() { return new EntityArgument(false, true); }

    public static Player getPlayer(CommandContext<CommandSource> ctx, String name) throws CommandSyntaxException {
        return ctx.getArgument(name, EntitySelector.class).findSinglePlayer(ctx.getSource());
    }

    /** 바닐라 getEntities: 결과가 없으면 "No entity was found". 지금 엔티티는 플레이어뿐이다. */
    public static Collection<Player> getEntities(CommandContext<CommandSource> ctx, String name) throws CommandSyntaxException {
        List<Player> list = ctx.getArgument(name, EntitySelector.class).findEntities(ctx.getSource());
        if (list.isEmpty()) throw EntitySelector.NO_ENTITIES_FOUND.create();
        return list;
    }

    /** 바닐라 getPlayers: 결과가 없으면 "No player was found". */
    public static Collection<Player> getPlayers(CommandContext<CommandSource> ctx, String name) throws CommandSyntaxException {
        List<Player> list = ctx.getArgument(name, EntitySelector.class).findPlayers(ctx.getSource());
        if (list.isEmpty()) throw EntitySelector.NO_PLAYERS_FOUND.create();
        return list;
    }

    public static Collection<Player> getOptionalPlayers(CommandContext<CommandSource> ctx, String name) throws CommandSyntaxException {
        return ctx.getArgument(name, EntitySelector.class).findPlayers(ctx.getSource());
    }

    /** 해석된 엔티티(플레이어 포함)를 받아 동기로 결과를 내는 명령 본문. */
    @FunctionalInterface
    public interface RiderAction {
        int run(List<Rider> targets) throws CommandSyntaxException;
    }

    /** 해석된 엔티티를 받아 안쪽에서 또 기다리는(future 를 돌려주는) 명령 본문 — 중첩 헬퍼용. */
    @FunctionalInterface
    public interface AsyncRiderAction {
        java.util.concurrent.CompletableFuture<Integer> runAsync(List<Rider> targets) throws CommandSyntaxException;
    }

    /**
     * 바닐라 getEntities: 플레이어가 아닌 엔티티도 고른다. 엔티티는 region 이 나눠 갖고 있어 해석이 나중에 끝날 수 있다 —
     * 이미 끝났으면 바로 action 의 결과를 돌려주고(오류는 그대로 던진다), 아니면 결과 future 를 실행 컨텍스트에 맡기고(defer) 1 을 돌려준다
     * (무시된다). action 은 전역 스레드에서 불린다. 이 껍데기는 명령 본문 맨 바깥에서만 쓴다 — 안쪽은 withEntitiesAsync.
     */
    public static int withEntities(CommandContext<CommandSource> ctx, String name, RiderAction action) throws CommandSyntaxException {
        return org.domi.furnace.server.command.EntityOps.result(ctx.getSource(), withEntitiesAsync(ctx, name, sync(action)));
    }

    /** 바닐라 getEntity: 정확히 하나가 아니면 "No entity was found". */
    public static int withEntity(CommandContext<CommandSource> ctx, String name, RiderAction action) throws CommandSyntaxException {
        return org.domi.furnace.server.command.EntityOps.result(ctx.getSource(), withEntityAsync(ctx, name, sync(action)));
    }

    public static java.util.concurrent.CompletableFuture<Integer> withEntitiesAsync(CommandContext<CommandSource> ctx, String name, AsyncRiderAction action)
            throws CommandSyntaxException {
        return awaitAsync(ctx.getSource(), ctx.getArgument(name, EntitySelector.class), false, action);
    }

    public static java.util.concurrent.CompletableFuture<Integer> withEntityAsync(CommandContext<CommandSource> ctx, String name, AsyncRiderAction action)
            throws CommandSyntaxException {
        return awaitAsync(ctx.getSource(), ctx.getArgument(name, EntitySelector.class), true, action);
    }

    private static AsyncRiderAction sync(RiderAction action) {
        return list -> java.util.concurrent.CompletableFuture.completedFuture(action.run(list));
    }

    private static java.util.concurrent.CompletableFuture<Integer> awaitAsync(CommandSource source, EntitySelector selector, boolean single,
                                                                             AsyncRiderAction action) throws CommandSyntaxException {
        return org.domi.furnace.server.command.EntityOps.awaitAsync(source, selector.findRiders(source), list -> {
            if (single ? list.size() != 1 : list.isEmpty()) throw EntitySelector.NO_ENTITIES_FOUND.create();
            return action.runAsync(list);
        });
    }

    /** 대상 하나 또는 여럿을 future 로(없으면 "No entity was found" 로 실패한 future). 다른 비동기 단계와 이어 붙일 때. */
    public static java.util.concurrent.CompletableFuture<List<Rider>> findRiders(CommandContext<CommandSource> ctx, String name, boolean single) throws CommandSyntaxException {
        return ctx.getArgument(name, EntitySelector.class).findRiders(ctx.getSource()).thenApply(list -> {
            if (single ? list.size() != 1 : list.isEmpty()) throw new java.util.concurrent.CompletionException(EntitySelector.NO_ENTITIES_FOUND.create());
            return list;
        });
    }

    @Override
    public EntitySelector parse(StringReader reader) throws CommandSyntaxException { return parse(reader, true); }

    @Override
    public <S> EntitySelector parse(StringReader reader, S source) throws CommandSyntaxException {
        boolean allow = !(source instanceof CommandSource cs) || cs.hasPermission(2);
        return parse(reader, allow);
    }

    private EntitySelector parse(StringReader reader, boolean allowSelectors) throws CommandSyntaxException {
        int start = reader.getCursor();
        EntitySelector selector = new EntitySelectorParser(reader, allowSelectors).parse();
        if (selector.getMaxResults() > 1 && single) {
            reader.setCursor(0);
            throw (playersOnly ? ERROR_NOT_SINGLE_PLAYER : ERROR_NOT_SINGLE_ENTITY).createWithContext(reader);
        }
        if (selector.includesEntities() && playersOnly && !selector.isSelfSelector()) {
            reader.setCursor(0);
            throw ERROR_ONLY_PLAYERS_ALLOWED.createWithContext(reader);
        }
        if (start < 0) throw new IllegalStateException();
        return selector;
    }

    @Override
    public Collection<String> getExamples() { return EXAMPLES; }

    @Override
    public String networkId() { return "minecraft:entity"; }

    @Override
    public void writeProperties(ByteBuf buf) { buf.writeByte((single ? 1 : 0) | (playersOnly ? 2 : 0)); }
}
