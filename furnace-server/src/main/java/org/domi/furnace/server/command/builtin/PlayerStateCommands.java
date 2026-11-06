// 바닐라 GameModeCommand·DefaultGameModeCommands·KillCommand·ExperienceCommand·SetSpawnCommand·SetWorldSpawnCommand.
package org.domi.furnace.server.command.builtin;

import static org.domi.furnace.server.command.Commands.argument;
import static org.domi.furnace.server.command.Commands.literal;

import java.util.Collection;
import java.util.List;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.command.arguments.AngleArgument;
import org.domi.furnace.server.command.arguments.BlockPosArgument;
import org.domi.furnace.server.command.arguments.EntityArgument;
import org.domi.furnace.server.command.arguments.GameModeArgument;
import org.domi.furnace.server.command.brigadier.Arguments;
import org.domi.furnace.server.command.brigadier.CommandContext;
import org.domi.furnace.server.command.brigadier.CommandDispatcher;
import org.domi.furnace.server.command.brigadier.CommandSyntaxException;
import org.domi.furnace.server.command.brigadier.LiteralCommandNode;
import org.domi.furnace.server.command.brigadier.SimpleCommandExceptionType;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.world.GameMode;

public final class PlayerStateCommands {

    private PlayerStateCommands() {}

    private static Text modeName(GameMode m) { return Text.translatable("gameMode." + m.key()); }

    // ------------------------------------------------------------------ defaultgamemode

    public static void registerDefaultGameMode(CommandDispatcher<CommandSource> d) {
        d.register(literal("defaultgamemode").requires(s -> s.hasPermission(2))
                .then(argument("gamemode", GameModeArgument.gameMode())
                        .executes(c -> setDefaultMode(c.getSource(), GameModeArgument.getGameMode(c, "gamemode")))));
    }

    private static int setDefaultMode(CommandSource source, GameMode mode) {
        ServerCore server = source.getServer();
        server.setDefaultGameMode(mode);
        int changed = 0;
        GameMode forced = server.forcedGameMode();
        if (forced != null) {
            for (Player p : server.players()) if (p.vitals().setGameMode(forced)) changed++;
        }
        source.sendSuccess(() -> Text.translatable("commands.defaultgamemode.success", modeName(mode)), true);
        return changed;
    }

    // ------------------------------------------------------------------ gamemode

    public static void registerGameMode(CommandDispatcher<CommandSource> d) {
        d.register(literal("gamemode").requires(s -> s.hasPermission(2))
                .then(argument("gamemode", GameModeArgument.gameMode())
                        .executes(c -> setMode(c, List.of(c.getSource().getPlayerOrException()), GameModeArgument.getGameMode(c, "gamemode")))
                        .then(argument("target", EntityArgument.players())
                                .executes(c -> setMode(c, EntityArgument.getPlayers(c, "target"), GameModeArgument.getGameMode(c, "gamemode"))))));
    }

    private static int setMode(CommandContext<CommandSource> c, Collection<Player> targets, GameMode mode) {
        int changed = 0;
        for (Player p : targets) {
            if (p.vitals().setGameMode(mode)) {
                logGameModeChange(c.getSource(), p, mode);
                changed++;
            }
        }
        return changed;
    }

    private static void logGameModeChange(CommandSource source, Player p, GameMode mode) {
        Text name = modeName(mode);
        if (source.getPlayer() == p) {
            source.sendSuccess(() -> Text.translatable("commands.gamemode.success.self", name), true);
        } else {
            if (source.getServer().gameRule("sendCommandFeedback")) p.sendSystemMessage(Text.translatable("gameMode.changed", name), false);
            source.sendSuccess(() -> Text.translatable("commands.gamemode.success.other", p.displayName(), name), true);
        }
    }

    // ------------------------------------------------------------------ kill

    public static void registerKill(CommandDispatcher<CommandSource> d) {
        d.register(literal("kill").requires(s -> s.hasPermission(2))
                .executes(c -> org.domi.furnace.server.command.EntityOps.result(c.getSource(),
                        kill(c.getSource(), List.of(c.getSource().getEntityOrException()))))
                .then(argument("targets", EntityArgument.entities())
                        .executes(c -> org.domi.furnace.server.command.EntityOps.result(c.getSource(),
                                EntityArgument.withEntitiesAsync(c, "targets", t -> kill(c.getSource(), t))))));
    }

    /**
     * 바닐라 KillCommand: 대상마다 kill()(생명체는 generic_kill 피해 Float.MAX_VALUE, 그 밖은 KILLED 로 제거)하고 대상 수를 돌려준다.
     * 플레이어는 지금 스레드에서, 일반 엔티티는 그 엔티티를 가진 region 에서 죽인다. 메시지는 모두 끝난 뒤 전역에서.
     * 이름(단수 메시지)은 바닐라처럼 죽인 뒤에 읽는다 — 일반 엔티티는 주인 region 에서.
     */
    static java.util.concurrent.CompletableFuture<Integer> kill(CommandSource source, List<org.domi.furnace.server.entity.Rider> targets) {
        List<java.util.concurrent.CompletableFuture<Text>> fs = new java.util.ArrayList<>(targets.size());
        for (var r : targets) {
            var f = new java.util.concurrent.CompletableFuture<Text>();
            if (r instanceof Player p) {
                Text name = p.displayName();   // 접속 이름 기반 — 스레드와 무관한 값
                // 플레이어도 일반 엔티티와 같은 규칙: 그 플레이어를 가진 region 에서(소유 확인 뒤) 죽인다
                org.domi.furnace.server.command.EntityOps.runOnPlayerOwner(source, p, region -> {
                    try {
                        p.vitals().kill();
                        f.complete(p.displayName());
                    } catch (Throwable t) {
                        f.completeExceptionally(t);
                    }
                }, () -> f.complete(name));   // 끝내 못 찾음(접속 끊김) — 바닐라 /kill 은 고른 대상을 그대로 센다
            } else if (r instanceof org.domi.furnace.server.entity.Entity e) {
                // 주인을 못 찾으면(이미 사라진 엔티티) 죽일 것이 없다 — 바닐라도 대상 수를 그대로 센다. 이름은 이 스레드에서 읽지 않고 종류 이름으로
                String tn = e.type().name();
                Text fallback = Text.translatable("entity." + tn.replace(':', '.'));
                org.domi.furnace.server.command.EntityOps.runOnOwner(source, e, en -> {
                    try {
                        en.kill();
                        f.complete(en.displayName());
                    } catch (Throwable t) {
                        f.completeExceptionally(t);
                    }
                }, () -> f.complete(fallback));
            } else {
                f.complete(r.displayName());
            }
            fs.add(f);
        }
        return org.domi.furnace.server.command.EntityOps.awaitAllAsync(source, fs, names -> {
            if (names.size() == 1) {
                Text name = names.get(0);
                source.sendSuccess(() -> Text.translatable("commands.kill.success.single", name), true);
            } else {
                source.sendSuccess(() -> Text.translatable("commands.kill.success.multiple", names.size()), true);
            }
            return names.size();
        });
    }

    // ------------------------------------------------------------------ experience / xp

    private static final SimpleCommandExceptionType ERROR_SET_POINTS_INVALID =
            new SimpleCommandExceptionType(Text.translatable("commands.experience.set.points.invalid"));

    private enum XpType {
        POINTS("points"), LEVELS("levels");

        final String name;

        XpType(String name) { this.name = name; }

        void add(Player p, int amount) {
            if (this == POINTS) p.vitals().giveExperiencePoints(amount);
            else p.vitals().giveExperienceLevels(amount);
        }

        boolean set(Player p, int amount) {
            if (this == POINTS) return p.vitals().setExperiencePoints(amount);
            p.vitals().setExperienceLevels(amount);
            return true;
        }

        int query(Player p) {
            if (this == LEVELS) return p.vitals().xpLevel();
            return (int) Math.floor(p.vitals().xpProgress() * (float) p.vitals().xpNeededForNextLevel());
        }
    }

    public static void registerExperience(CommandDispatcher<CommandSource> d) {
        LiteralCommandNode<CommandSource> node = d.register(literal("experience").requires(s -> s.hasPermission(2))
                .then(literal("add").then(argument("targets", EntityArgument.players())
                        .then(argument("amount", Arguments.integer())
                                .executes(c -> addXp(c.getSource(), EntityArgument.getPlayers(c, "targets"), c.getArgument("amount", Integer.class), XpType.POINTS))
                                .then(literal("points").executes(c -> addXp(c.getSource(), EntityArgument.getPlayers(c, "targets"), c.getArgument("amount", Integer.class), XpType.POINTS)))
                                .then(literal("levels").executes(c -> addXp(c.getSource(), EntityArgument.getPlayers(c, "targets"), c.getArgument("amount", Integer.class), XpType.LEVELS))))))
                .then(literal("set").then(argument("targets", EntityArgument.players())
                        .then(argument("amount", Arguments.integer(0))
                                .executes(c -> setXp(c.getSource(), EntityArgument.getPlayers(c, "targets"), c.getArgument("amount", Integer.class), XpType.POINTS))
                                .then(literal("points").executes(c -> setXp(c.getSource(), EntityArgument.getPlayers(c, "targets"), c.getArgument("amount", Integer.class), XpType.POINTS)))
                                .then(literal("levels").executes(c -> setXp(c.getSource(), EntityArgument.getPlayers(c, "targets"), c.getArgument("amount", Integer.class), XpType.LEVELS))))))
                .then(literal("query").then(argument("targets", EntityArgument.player())
                        .then(literal("points").executes(c -> queryXp(c.getSource(), EntityArgument.getPlayer(c, "targets"), XpType.POINTS)))
                        .then(literal("levels").executes(c -> queryXp(c.getSource(), EntityArgument.getPlayer(c, "targets"), XpType.LEVELS))))));
        d.register(literal("xp").requires(s -> s.hasPermission(2)).redirect(node));
    }

    private static int queryXp(CommandSource source, Player p, XpType type) {
        int v = type.query(p);
        source.sendSuccess(() -> Text.translatable("commands.experience.query." + type.name, p.displayName(), v), false);
        return v;
    }

    private static int addXp(CommandSource source, Collection<Player> targets, int amount, XpType type) {
        for (Player p : targets) type.add(p, amount);
        if (targets.size() == 1) {
            Player p = targets.iterator().next();
            source.sendSuccess(() -> Text.translatable("commands.experience.add." + type.name + ".success.single", amount, p.displayName()), true);
        } else {
            source.sendSuccess(() -> Text.translatable("commands.experience.add." + type.name + ".success.multiple", amount, targets.size()), true);
        }
        return targets.size();
    }

    private static int setXp(CommandSource source, Collection<Player> targets, int amount, XpType type) throws CommandSyntaxException {
        int ok = 0;
        for (Player p : targets) if (type.set(p, amount)) ok++;
        if (ok == 0) throw ERROR_SET_POINTS_INVALID.create();
        if (targets.size() == 1) {
            Player p = targets.iterator().next();
            source.sendSuccess(() -> Text.translatable("commands.experience.set." + type.name + ".success.single", amount, p.displayName()), true);
        } else {
            source.sendSuccess(() -> Text.translatable("commands.experience.set." + type.name + ".success.multiple", amount, targets.size()), true);
        }
        return targets.size();
    }

    // ------------------------------------------------------------------ spawnpoint / setworldspawn

    /** 실행 위치 블록(BlockPos.containing(source.getPosition())). */
    private static int[] sourceBlockPos(CommandSource s) {
        return new int[]{(int) Math.floor(s.x()), (int) Math.floor(s.y()), (int) Math.floor(s.z())};
    }

    public static void registerSpawnPoint(CommandDispatcher<CommandSource> d) {
        d.register(literal("spawnpoint").requires(s -> s.hasPermission(2))
                .executes(c -> setSpawn(c.getSource(), List.of(c.getSource().getPlayerOrException()), sourceBlockPos(c.getSource()), 0.0f))
                .then(argument("targets", EntityArgument.players())
                        .executes(c -> setSpawn(c.getSource(), EntityArgument.getPlayers(c, "targets"), sourceBlockPos(c.getSource()), 0.0f))
                        .then(argument("pos", BlockPosArgument.blockPos())
                                .executes(c -> setSpawn(c.getSource(), EntityArgument.getPlayers(c, "targets"), BlockPosArgument.getSpawnablePos(c, "pos"), 0.0f))
                                .then(argument("angle", AngleArgument.angle())
                                        .executes(c -> setSpawn(c.getSource(), EntityArgument.getPlayers(c, "targets"), BlockPosArgument.getSpawnablePos(c, "pos"),
                                                AngleArgument.getAngle(c, "angle")))))));
    }

    private static int setSpawn(CommandSource source, Collection<Player> targets, int[] pos, float angle) {
        String dimension = "minecraft:overworld";
        for (Player p : targets) p.vitals().setRespawnPosition(dimension, pos, angle, true);
        if (targets.size() == 1) {
            Player p = targets.iterator().next();
            source.sendSuccess(() -> Text.translatable("commands.spawnpoint.success.single", pos[0], pos[1], pos[2], angle, dimension, p.displayName()), true);
        } else {
            source.sendSuccess(() -> Text.translatable("commands.spawnpoint.success.multiple", pos[0], pos[1], pos[2], angle, dimension, targets.size()), true);
        }
        return targets.size();
    }

    public static void registerSetWorldSpawn(CommandDispatcher<CommandSource> d) {
        d.register(literal("setworldspawn").requires(s -> s.hasPermission(2))
                .executes(c -> setWorldSpawn(c.getSource(), sourceBlockPos(c.getSource()), 0.0f))
                .then(argument("pos", BlockPosArgument.blockPos())
                        .executes(c -> setWorldSpawn(c.getSource(), BlockPosArgument.getSpawnablePos(c, "pos"), 0.0f))
                        .then(argument("angle", AngleArgument.angle())
                                .executes(c -> setWorldSpawn(c.getSource(), BlockPosArgument.getSpawnablePos(c, "pos"), AngleArgument.getAngle(c, "angle"))))));
    }

    private static int setWorldSpawn(CommandSource source, int[] pos, float angle) {
        source.getServer().setWorldSpawn(pos[0], pos[1], pos[2], angle);
        source.sendSuccess(() -> Text.translatable("commands.setworldspawn.success", pos[0], pos[1], pos[2], angle), true);
        return 1;
    }
}
