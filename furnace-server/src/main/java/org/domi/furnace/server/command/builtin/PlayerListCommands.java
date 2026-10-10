// 바닐라 OpCommand·DeOpCommands·WhitelistCommand·BanPlayerCommands·BanIpCommands·BanListCommands·PardonCommand·PardonIpCommand.
package org.domi.furnace.server.command.builtin;

import static org.domi.furnace.server.command.Commands.argument;
import static org.domi.furnace.server.command.Commands.literal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.command.NamedSuggestionProvider;
import org.domi.furnace.server.command.SharedSuggestions;
import org.domi.furnace.server.command.arguments.EntitySelector;
import org.domi.furnace.server.command.arguments.GameProfileArgument;
import org.domi.furnace.server.command.arguments.MessageArgument;
import org.domi.furnace.server.command.brigadier.Arguments;
import org.domi.furnace.server.command.brigadier.CommandContext;
import org.domi.furnace.server.command.brigadier.CommandDispatcher;
import org.domi.furnace.server.command.brigadier.CommandSyntaxException;
import org.domi.furnace.server.command.brigadier.SimpleCommandExceptionType;
import org.domi.furnace.server.command.brigadier.SuggestionProvider;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.net.protocol.Text;
import org.domi.furnace.server.net.protocol.play.Disconnect;
import org.domi.furnace.server.players.BanListEntry;
import org.domi.furnace.server.players.GameProfile;
import org.domi.furnace.server.players.IpBanListEntry;
import org.domi.furnace.server.players.PlayerLists;
import org.domi.furnace.server.players.UserBanListEntry;
import org.domi.furnace.server.players.UserWhiteListEntry;

public final class PlayerListCommands {

    private static final SimpleCommandExceptionType ERROR_ALREADY_OP = err("commands.op.failed");
    private static final SimpleCommandExceptionType ERROR_NOT_OP = err("commands.deop.failed");
    private static final SimpleCommandExceptionType ERROR_ALREADY_ENABLED = err("commands.whitelist.alreadyOn");
    private static final SimpleCommandExceptionType ERROR_ALREADY_DISABLED = err("commands.whitelist.alreadyOff");
    private static final SimpleCommandExceptionType ERROR_ALREADY_WHITELISTED = err("commands.whitelist.add.failed");
    private static final SimpleCommandExceptionType ERROR_NOT_WHITELISTED = err("commands.whitelist.remove.failed");
    private static final SimpleCommandExceptionType ERROR_ALREADY_BANNED = err("commands.ban.failed");
    private static final SimpleCommandExceptionType ERROR_IP_ALREADY_BANNED = err("commands.banip.failed");
    private static final SimpleCommandExceptionType ERROR_INVALID_IP_OR_PLAYER = err("commands.banip.invalid");
    private static final SimpleCommandExceptionType ERROR_NOT_BANNED = err("commands.pardon.failed");
    private static final SimpleCommandExceptionType ERROR_INVALID_IP = err("commands.pardonip.invalid");
    private static final SimpleCommandExceptionType ERROR_IP_NOT_BANNED = err("commands.pardonip.failed");
    /** 바닐라 BanIpCommands.IP_ADDRESS_PATTERN. */
    public static final Pattern IP_ADDRESS_PATTERN =
            Pattern.compile("^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$");

    private PlayerListCommands() {}

    private static SimpleCommandExceptionType err(String key) { return new SimpleCommandExceptionType(Text.translatable(key)); }

    private static SuggestionProvider<CommandSource> ask(SuggestionProvider<CommandSource> p) {
        return new NamedSuggestionProvider() {
            @Override
            public String id() { return ASK_SERVER; }

            @Override
            public java.util.concurrent.CompletableFuture<org.domi.furnace.server.command.brigadier.Suggestions> getSuggestions(
                    CommandContext<CommandSource> c, org.domi.furnace.server.command.brigadier.SuggestionsBuilder b) throws CommandSyntaxException {
                return p.getSuggestions(c, b);
            }
        };
    }

    private static Text name(GameProfile p) { return Text.literal(p.name()); }

    // ------------------------------------------------------------------ op / deop

    public static void registerOp(CommandDispatcher<CommandSource> d) {
        d.register(literal("op").requires(s -> s.hasPermission(3))
                .then(argument("targets", GameProfileArgument.gameProfile())
                        .suggests(ask((c, b) -> {
                            PlayerLists lists = c.getSource().getServer().lists();
                            List<String> names = new ArrayList<>();
                            for (Player p : c.getSource().getServer().players()) if (!lists.isOp(ServerCore.profileOf(p))) names.add(p.name());
                            return SharedSuggestions.suggest(names, b);
                        }))
                        .executes(c -> opPlayers(c.getSource(), GameProfileArgument.getGameProfiles(c, "targets")))));
    }

    private static int opPlayers(CommandSource source, Collection<GameProfile> targets) throws CommandSyntaxException {
        ServerCore server = source.getServer();
        int i = 0;
        for (GameProfile p : targets) {
            if (!server.lists().isOp(p)) {
                server.lists().op(p, server.operatorUserPermissionLevel());
                Player online = server.playerByUuid(p.id());
                if (online != null) server.sendPlayerPermissionLevel(online);
                i++;
                // 바닐라 그대로: 여럿이어도 첫 대상 이름으로 알린다.
                source.sendSuccess(() -> Text.translatable("commands.op.success", targets.iterator().next().name()), true);
            }
        }
        if (i == 0) throw ERROR_ALREADY_OP.create();
        return i;
    }

    public static void registerDeop(CommandDispatcher<CommandSource> d) {
        d.register(literal("deop").requires(s -> s.hasPermission(3))
                .then(argument("targets", GameProfileArgument.gameProfile())
                        .suggests(ask((c, b) -> SharedSuggestions.suggest(c.getSource().getServer().lists().getOps().getUserList(), b)))
                        .executes(c -> deopPlayers(c.getSource(), GameProfileArgument.getGameProfiles(c, "targets")))));
    }

    private static int deopPlayers(CommandSource source, Collection<GameProfile> targets) throws CommandSyntaxException {
        ServerCore server = source.getServer();
        int i = 0;
        for (GameProfile p : targets) {
            if (server.lists().isOp(p)) {
                server.lists().deop(p);
                Player online = server.playerByUuid(p.id());
                if (online != null) server.sendPlayerPermissionLevel(online);
                i++;
                source.sendSuccess(() -> Text.translatable("commands.deop.success", targets.iterator().next().name()), true);
            }
        }
        if (i == 0) throw ERROR_NOT_OP.create();
        kickUnlistedPlayers(server);
        return i;
    }

    /** 바닐라 kickUnlistedPlayers — enforce-whitelist 이고 화이트리스트가 켜져 있으면 목록에 없는(OP 아닌) 접속자를 내보낸다. */
    public static void kickUnlistedPlayers(ServerCore server) {
        if (!server.settings().get().enforceWhitelist || !server.lists().isUsingWhitelist()) return;
        for (Player p : server.players()) {
            if (!server.lists().isWhiteListed(ServerCore.profileOf(p))) {
                p.kick(new Disconnect(Text.translatable("multiplayer.disconnect.not_whitelisted")));
            }
        }
    }

    // ------------------------------------------------------------------ whitelist

    public static void registerWhitelist(CommandDispatcher<CommandSource> d) {
        d.register(literal("whitelist").requires(s -> s.hasPermission(3))
                .then(literal("on").executes(c -> enableWhitelist(c.getSource())))
                .then(literal("off").executes(c -> disableWhitelist(c.getSource())))
                .then(literal("list").executes(c -> showList(c.getSource())))
                .then(literal("add").then(argument("targets", GameProfileArgument.gameProfile())
                        .suggests(ask((c, b) -> {
                            PlayerLists lists = c.getSource().getServer().lists();
                            List<String> names = new ArrayList<>();
                            for (Player p : c.getSource().getServer().players()) {
                                if (!lists.getWhiteList().isListed(ServerCore.profileOf(p))) names.add(p.name());
                            }
                            return SharedSuggestions.suggest(names, b);
                        }))
                        .executes(c -> addPlayers(c.getSource(), GameProfileArgument.getGameProfiles(c, "targets")))))
                .then(literal("remove").then(argument("targets", GameProfileArgument.gameProfile())
                        .suggests(ask((c, b) -> SharedSuggestions.suggest(c.getSource().getServer().lists().getWhiteList().getUserList(), b)))
                        .executes(c -> removePlayers(c.getSource(), GameProfileArgument.getGameProfiles(c, "targets")))))
                .then(literal("reload").executes(c -> reload(c.getSource()))));
    }

    private static int reload(CommandSource source) {
        source.getServer().lists().reloadWhiteList();
        source.sendSuccess(() -> Text.translatable("commands.whitelist.reloaded"), true);
        kickUnlistedPlayers(source.getServer());
        return 1;
    }

    private static int addPlayers(CommandSource source, Collection<GameProfile> targets) throws CommandSyntaxException {
        var wl = source.getServer().lists().getWhiteList();
        int i = 0;
        for (GameProfile p : targets) {
            if (!wl.isListed(p)) {
                wl.add(new UserWhiteListEntry(p));
                source.sendSuccess(() -> Text.translatable("commands.whitelist.add.success", name(p)), true);
                i++;
            }
        }
        if (i == 0) throw ERROR_ALREADY_WHITELISTED.create();
        return i;
    }

    private static int removePlayers(CommandSource source, Collection<GameProfile> targets) throws CommandSyntaxException {
        var wl = source.getServer().lists().getWhiteList();
        int i = 0;
        for (GameProfile p : targets) {
            if (wl.isListed(p)) {
                wl.remove(p);
                source.sendSuccess(() -> Text.translatable("commands.whitelist.remove.success", name(p)), true);
                i++;
            }
        }
        if (i == 0) throw ERROR_NOT_WHITELISTED.create();
        kickUnlistedPlayers(source.getServer());
        return i;
    }

    private static int enableWhitelist(CommandSource source) throws CommandSyntaxException {
        PlayerLists lists = source.getServer().lists();
        if (lists.isUsingWhitelist()) throw ERROR_ALREADY_ENABLED.create();
        lists.setUsingWhiteList(true);
        source.sendSuccess(() -> Text.translatable("commands.whitelist.enabled"), true);
        kickUnlistedPlayers(source.getServer());
        return 1;
    }

    private static int disableWhitelist(CommandSource source) throws CommandSyntaxException {
        PlayerLists lists = source.getServer().lists();
        if (!lists.isUsingWhitelist()) throw ERROR_ALREADY_DISABLED.create();
        lists.setUsingWhiteList(false);
        source.sendSuccess(() -> Text.translatable("commands.whitelist.disabled"), true);
        return 1;
    }

    private static int showList(CommandSource source) {
        String[] names = source.getServer().lists().getWhiteList().getUserList();
        if (names.length == 0) {
            source.sendSuccess(() -> Text.translatable("commands.whitelist.none"), false);
        } else {
            source.sendSuccess(() -> Text.translatable("commands.whitelist.list", names.length, String.join(", ", names)), false);
        }
        return names.length;
    }

    // ------------------------------------------------------------------ ban / ban-ip / banlist / pardon / pardon-ip

    public static void registerBan(CommandDispatcher<CommandSource> d) {
        d.register(literal("ban").requires(s -> s.hasPermission(3))
                .then(argument("targets", GameProfileArgument.gameProfile())
                        .executes(c -> banPlayers(c.getSource(), GameProfileArgument.getGameProfiles(c, "targets"), null))
                        .then(argument("reason", MessageArgument.message())
                                .executes(c -> banPlayers(c.getSource(), GameProfileArgument.getGameProfiles(c, "targets"),
                                        MessageArgument.getMessage(c, "reason"))))));
    }

    private static int banPlayers(CommandSource source, Collection<GameProfile> targets, Text reason) throws CommandSyntaxException {
        var bans = source.getServer().lists().getBans();
        int i = 0;
        for (GameProfile p : targets) {
            if (!bans.isListed(p)) {
                UserBanListEntry entry = new UserBanListEntry(p, null, source.getTextName(), null, reason == null ? null : reason.getString());
                bans.add(entry);
                i++;
                source.sendSuccess(() -> Text.translatable("commands.ban.success", name(p), Text.literal(entry.getReason())), true);
                Player online = source.getServer().playerByUuid(p.id());
                if (online != null) online.kick(new Disconnect(Text.translatable("multiplayer.disconnect.banned")));
            }
        }
        if (i == 0) throw ERROR_ALREADY_BANNED.create();
        return i;
    }

    public static void registerBanIp(CommandDispatcher<CommandSource> d) {
        d.register(literal("ban-ip").requires(s -> s.hasPermission(3))
                .then(argument("target", Arguments.word())
                        .executes(c -> banIpOrName(c.getSource(), c.getArgument("target", String.class), null))
                        .then(argument("reason", MessageArgument.message())
                                .executes(c -> banIpOrName(c.getSource(), c.getArgument("target", String.class),
                                        MessageArgument.getMessage(c, "reason"))))));
    }

    private static int banIpOrName(CommandSource source, String ipOrName, Text reason) throws CommandSyntaxException {
        if (IP_ADDRESS_PATTERN.matcher(ipOrName).matches()) return banIp(source, ipOrName, reason);
        Player p = source.getServer().playerByName(ipOrName);
        if (p != null) return banIp(source, p.ipAddress(), reason);
        throw ERROR_INVALID_IP_OR_PLAYER.create();
    }

    private static int banIp(CommandSource source, String ip, Text reason) throws CommandSyntaxException {
        var ipBans = source.getServer().lists().getIpBans();
        if (ipBans.isBanned(ip)) throw ERROR_IP_ALREADY_BANNED.create();
        List<Player> affected = new ArrayList<>();
        for (Player p : source.getServer().players()) if (p.ipAddress().equals(ip)) affected.add(p);
        IpBanListEntry entry = new IpBanListEntry(ip, null, source.getTextName(), null, reason == null ? null : reason.getString());
        ipBans.add(entry);
        source.sendSuccess(() -> Text.translatable("commands.banip.success", ip, entry.getReason()), true);
        if (!affected.isEmpty()) {
            source.sendSuccess(() -> Text.translatable("commands.banip.info", affected.size(), EntitySelector.joinNames(affected)), true);
        }
        for (Player p : affected) p.kick(new Disconnect(Text.translatable("multiplayer.disconnect.ip_banned")));
        return affected.size();
    }

    public static void registerBanList(CommandDispatcher<CommandSource> d) {
        d.register(literal("banlist").requires(s -> s.hasPermission(3))
                .executes(c -> {
                    PlayerLists lists = c.getSource().getServer().lists();
                    List<BanListEntry<?>> all = new ArrayList<>(lists.getBans().getEntries());
                    all.addAll(lists.getIpBans().getEntries());
                    return showBans(c.getSource(), all);
                })
                .then(literal("ips").executes(c -> showBans(c.getSource(), new ArrayList<>(c.getSource().getServer().lists().getIpBans().getEntries()))))
                .then(literal("players").executes(c -> showBans(c.getSource(), new ArrayList<>(c.getSource().getServer().lists().getBans().getEntries())))));
    }

    private static int showBans(CommandSource source, List<BanListEntry<?>> bans) {
        if (bans.isEmpty()) {
            source.sendSuccess(() -> Text.translatable("commands.banlist.none"), false);
        } else {
            source.sendSuccess(() -> Text.translatable("commands.banlist.list", bans.size()), false);
            for (BanListEntry<?> e : bans) {
                Text who = e.getDisplayName() != null ? Text.literal(e.getDisplayName()) : Text.translatable("commands.banlist.entry.unknown");
                source.sendSuccess(() -> Text.translatable("commands.banlist.entry", who, e.getSource(), e.getReason()), false);
            }
        }
        return bans.size();
    }

    public static void registerPardon(CommandDispatcher<CommandSource> d) {
        d.register(literal("pardon").requires(s -> s.hasPermission(3))
                .then(argument("targets", GameProfileArgument.gameProfile())
                        .suggests(ask((c, b) -> SharedSuggestions.suggest(c.getSource().getServer().lists().getBans().getUserList(), b)))
                        .executes(c -> pardonPlayers(c.getSource(), GameProfileArgument.getGameProfiles(c, "targets")))));
    }

    private static int pardonPlayers(CommandSource source, Collection<GameProfile> targets) throws CommandSyntaxException {
        var bans = source.getServer().lists().getBans();
        int i = 0;
        for (GameProfile p : targets) {
            if (bans.isListed(p)) {
                bans.remove(p);
                i++;
                source.sendSuccess(() -> Text.translatable("commands.pardon.success", name(p)), true);
            }
        }
        if (i == 0) throw ERROR_NOT_BANNED.create();
        return i;
    }

    public static void registerPardonIp(CommandDispatcher<CommandSource> d) {
        d.register(literal("pardon-ip").requires(s -> s.hasPermission(3))
                .then(argument("target", Arguments.word())
                        .suggests(ask((c, b) -> SharedSuggestions.suggest(c.getSource().getServer().lists().getIpBans().getUserList(), b)))
                        .executes(c -> unban(c.getSource(), c.getArgument("target", String.class)))));
    }

    private static int unban(CommandSource source, String ip) throws CommandSyntaxException {
        if (!IP_ADDRESS_PATTERN.matcher(ip).matches()) throw ERROR_INVALID_IP.create();
        var ipBans = source.getServer().lists().getIpBans();
        if (!ipBans.isBanned(ip)) throw ERROR_IP_NOT_BANNED.create();
        ipBans.remove(ip);
        source.sendSuccess(() -> Text.translatable("commands.pardonip.success", ip), true);
        return 1;
    }
}
