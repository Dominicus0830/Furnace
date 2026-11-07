package org.bukkit.command;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.domi.furnace.compat.paper.UnsupportedFeature;

/**
 * 명령 바탕 클래스(Bukkit Command). 이름·별칭·설명·사용법·권한을 들고, 명령 지도({@link CommandMap})에 올라가야 쓸 수 있다.
 * 플러그인이 직접 상속해 {@link #execute} 를 구현하는 명령도 지도에 올리면 Furnace 명령 트리에 붙는다.
 */
public abstract class Command {

    private String name;
    private String nextLabel;
    private String label;
    private List<String> aliases;
    private List<String> activeAliases;
    private CommandMap commandMap = null;
    protected String description = "";
    protected String usageMessage;
    private String permission;
    private String permissionMessage;

    protected Command(String name) { this(name, "", "/" + name, new ArrayList<>()); }

    protected Command(String name, String description, String usageMessage, List<String> aliases) {
        this.name = name;
        this.nextLabel = name;
        this.label = name;
        this.description = description;
        this.usageMessage = usageMessage;
        this.aliases = aliases;
        this.activeAliases = new ArrayList<>(aliases);
    }

    /** 명령을 실행한다. 사용법을 보이려면 false. */
    public abstract boolean execute(CommandSender sender, String commandLabel, String[] args);

    /** 자동완성 후보. 기본은 접속 중인 플레이어 이름(보낸 사람이 볼 수 있는 것만, 대소문자 무시 정렬). */
    public List<String> tabComplete(CommandSender sender, String alias, String[] args) throws IllegalArgumentException {
        if (sender == null) throw new IllegalArgumentException("Sender cannot be null");
        if (args == null) throw new IllegalArgumentException("Arguments cannot be null");
        if (alias == null) throw new IllegalArgumentException("Alias cannot be null");
        if (args.length == 0) return Collections.emptyList();
        String lastWord = args[args.length - 1].toLowerCase(Locale.ROOT);
        List<String> matched = new ArrayList<>();
        for (Player player : sender.getServer().getOnlinePlayers()) {
            String n = player.getName();
            if (!n.toLowerCase(Locale.ROOT).startsWith(lastWord)) continue;
            if (sender instanceof Player viewer && !org.domi.furnace.compat.paper.CompatCommands.canSee(viewer, player)) continue;
            matched.add(n);
        }
        matched.sort(String.CASE_INSENSITIVE_ORDER);
        return matched;
    }

    /** 위치를 아는 자동완성. 기본은 위치 없이 같은 결과. */
    public List<String> tabComplete(CommandSender sender, String alias, String[] args, Location location) throws IllegalArgumentException {
        return tabComplete(sender, alias, args);
    }

    public String getName() { return name; }

    /** 등록되기 전에만 바꿀 수 있다. */
    public boolean setName(String name) {
        if (!isRegistered()) {
            this.name = name == null ? "" : name;
            return true;
        }
        return false;
    }

    public String getPermission() { return permission; }

    public void setPermission(String permission) { this.permission = permission; }

    public boolean testPermission(CommandSender target) {
        if (testPermissionSilent(target)) return true;
        if (permissionMessage == null) {
            target.sendMessage("§cI'm sorry, but you do not have permission to perform this command. Please contact the server administrators if you believe that this is in error.");
        } else if (!permissionMessage.isEmpty()) {
            for (String line : permissionMessage.replace("<permission>", permission).split("\n")) target.sendMessage(line);
        }
        return false;
    }

    /** 세미콜론으로 이은 권한 중 하나라도 있으면 true(권한이 비었으면 누구나). */
    public boolean testPermissionSilent(CommandSender target) {
        if (permission == null || permission.isEmpty()) return true;
        for (String p : permission.split(";")) if (target.hasPermission(p)) return true;
        return false;
    }

    public String getLabel() { return label; }

    /** 등록되기 전에만 바로 바뀐다. 등록돼 있으면 다시 등록할 때 반영된다. */
    public boolean setLabel(String name) {
        this.nextLabel = name;
        if (!isRegistered()) {
            this.label = name;
            return true;
        }
        return false;
    }

    public boolean register(CommandMap commandMap) {
        if (allowChangesFrom(commandMap)) {
            this.commandMap = commandMap;
            return true;
        }
        return false;
    }

    public boolean unregister(CommandMap commandMap) {
        if (allowChangesFrom(commandMap)) {
            this.commandMap = null;
            this.activeAliases = new ArrayList<>(this.aliases);
            this.label = this.nextLabel;
            return true;
        }
        return false;
    }

    private boolean allowChangesFrom(CommandMap commandMap) { return this.commandMap == null || this.commandMap == commandMap; }

    public boolean isRegistered() { return commandMap != null; }

    /** 지도에 실제로 올라간 별칭(등록 때 다른 명령에 막힌 별칭은 빠진다). */
    public List<String> getAliases() { return activeAliases; }

    public String getPermissionMessage() { return permissionMessage; }

    public String getDescription() { return description; }

    public String getUsage() { return usageMessage; }

    /** 등록되기 전에만 활성 별칭에 반영된다. */
    public Command setAliases(List<String> aliases) {
        this.aliases = aliases;
        if (!isRegistered()) this.activeAliases = new ArrayList<>(aliases);
        return this;
    }

    public Command setDescription(String description) {
        this.description = description == null ? "" : description;
        return this;
    }

    public Command setPermissionMessage(String permissionMessage) {
        this.permissionMessage = permissionMessage;
        return this;
    }

    public Command setUsage(String usage) {
        this.usageMessage = usage == null ? "" : usage;
        return this;
    }

    /**
     * 관리자에게 명령 사용을 알리는 줄. Bukkit 은 권한 구독자(bukkit.broadcast.admin)와 게임 규칙에 따라 보내는데
     * Furnace 호환 계층에는 권한 구독 목록이 없다.
     */
    public static void broadcastCommandMessage(CommandSender source, String message) { broadcastCommandMessage(source, message, true); }

    public static void broadcastCommandMessage(CommandSender source, String message, boolean sendToSource) {
        throw new UnsupportedFeature("Command.broadcastCommandMessage", "권한 구독자 목록(PluginManager.getPermissionSubscriptions)이 없다");
    }

    @Override
    public String toString() { return getClass().getName() + '(' + name + ')'; }
}
