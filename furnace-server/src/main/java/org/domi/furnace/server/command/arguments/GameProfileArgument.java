// 바닐라 GameProfileArgument(minecraft:game_profile) — 선택자(플레이어만) 또는 이름. 이름은 프로필 캐시(온라인이면 Mojang 조회)로 해석.
package org.domi.furnace.server.command.arguments;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.command.brigadier.ArgumentType;
import org.domi.furnace.server.command.brigadier.CommandContext;
import org.domi.furnace.server.command.brigadier.CommandSyntaxException;
import org.domi.furnace.server.command.brigadier.SimpleCommandExceptionType;
import org.domi.furnace.server.command.brigadier.StringReader;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.net.protocol.Text;
import org.domi.furnace.server.players.GameProfile;

public final class GameProfileArgument implements ArgumentType<GameProfileArgument.Result> {

    public static final SimpleCommandExceptionType ERROR_UNKNOWN_PLAYER = new SimpleCommandExceptionType(Text.translatable("argument.player.unknown"));
    private static final List<String> EXAMPLES = List.of("Player", "0123", "dd12be42-52a9-4a91-a8a1-11c01849e498", "@e");

    @FunctionalInterface
    public interface Result {
        Collection<GameProfile> getNames(CommandSource source) throws CommandSyntaxException;
    }

    public static GameProfileArgument gameProfile() { return new GameProfileArgument(); }

    public static Collection<GameProfile> getGameProfiles(CommandContext<CommandSource> ctx, String name) throws CommandSyntaxException {
        return ctx.getArgument(name, Result.class).getNames(ctx.getSource());
    }

    @Override
    public Result parse(StringReader reader) throws CommandSyntaxException { return parse(reader, true); }

    @Override
    public <S> Result parse(StringReader reader, S source) throws CommandSyntaxException {
        return parse(reader, !(source instanceof CommandSource cs) || cs.hasPermission(2));
    }

    private Result parse(StringReader reader, boolean allowSelectors) throws CommandSyntaxException {
        if (reader.canRead() && reader.peek() == '@') {
            EntitySelector selector = new EntitySelectorParser(reader, allowSelectors).parse();
            if (selector.includesEntities()) throw EntityArgument.ERROR_ONLY_PLAYERS_ALLOWED.createWithContext(reader);
            return source -> {
                List<Player> players = selector.findPlayers(source);
                if (players.isEmpty()) throw EntitySelector.NO_PLAYERS_FOUND.create();
                List<GameProfile> out = new ArrayList<>();
                for (Player p : players) out.add(ServerCore.profileOf(p));
                return out;
            };
        }
        int start = reader.getCursor();
        while (reader.canRead() && reader.peek() != ' ') reader.skip();
        String name = reader.getString().substring(start, reader.getCursor());
        return source -> {
            Optional<GameProfile> p = source.getServer().profileCache().get(name);
            return List.of(p.orElseThrow(ERROR_UNKNOWN_PLAYER::create));
        };
    }

    @Override
    public Collection<String> getExamples() { return EXAMPLES; }

    @Override
    public String networkId() { return "minecraft:game_profile"; }
}
