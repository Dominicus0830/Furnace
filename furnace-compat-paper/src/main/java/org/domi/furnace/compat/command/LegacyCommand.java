// Bukkit Command/PluginCommand 의 설정과 실행 규칙(권한 검사·사용법·예외 처리·자동완성 기본값).
// 서버 명령 트리에 붙이는 일은 LegacyCommands 가 한다.
package org.domi.furnace.compat.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import org.domi.furnace.api.Furnace;
import org.domi.furnace.api.command.CommandSender;
import org.domi.furnace.api.entity.Player;
import org.domi.furnace.api.plugin.FurnacePlugin;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.api.text.serializer.LegacyTextSerializer;

public final class LegacyCommand {

    /** Bukkit Command.testPermission 의 기본 거절 문장. */
    public static final String DEFAULT_PERMISSION_MESSAGE =
            "§cI'm sorry, but you do not have permission to perform this command. Please contact the server administrators if you believe that this is in error.";
    /** 실행 중 예외를 만났을 때 보낸 사람에게 보이는 문장(Bukkit·Paper 와 같다). */
    public static final String INTERNAL_ERROR_MESSAGE = "§cAn internal error occurred while attempting to perform this command";

    private final FurnacePlugin plugin;
    private final String name;
    private volatile List<String> aliases = List.of();
    private volatile String description = "";
    private volatile String usage;
    private volatile String permission;
    private volatile String permissionMessage;   // null = 기본 문장, "" = 아무 말도 안 함
    private volatile LegacyExecutor executor;
    private volatile LegacyTabCompleter completer;

    public LegacyCommand(FurnacePlugin plugin, String name) {
        if (plugin == null) throw new IllegalArgumentException("plugin");
        String n = name == null ? "" : name.toLowerCase(Locale.ROOT).trim();
        if (n.isEmpty() || n.indexOf(' ') >= 0 || n.indexOf(':') >= 0) throw new IllegalArgumentException("명령 이름이 잘못됐다: '" + name + "'");
        this.plugin = plugin;
        this.name = n;
        this.usage = "/" + n;
    }

    public FurnacePlugin plugin() { return plugin; }

    public String name() { return name; }

    public List<String> aliases() { return aliases; }

    public LegacyCommand setAliases(List<String> aliases) {
        List<String> a = new ArrayList<>();
        for (String s : aliases) a.add(s.toLowerCase(Locale.ROOT).trim());
        this.aliases = List.copyOf(a);
        return this;
    }

    public String description() { return description; }

    public LegacyCommand setDescription(String description) {
        this.description = description == null ? "" : description;
        return this;
    }

    /** 사용법(여러 줄은 \n). 기본은 "/이름". 비우면 실행이 false 여도 아무것도 안 보낸다. "&lt;command&gt;" 는 친 이름으로 바뀐다. */
    public String usage() { return usage; }

    public LegacyCommand setUsage(String usage) {
        this.usage = usage == null ? "" : usage;
        return this;
    }

    /** 필요한 권한. 세미콜론으로 여러 개를 주면 하나라도 가지면 된다(Bukkit 과 같다). null·빈 문자열은 제한 없음. */
    public String permission() { return permission; }

    public LegacyCommand setPermission(String permission) {
        this.permission = permission;
        return this;
    }

    public String permissionMessage() { return permissionMessage; }

    public LegacyCommand setPermissionMessage(String message) {
        this.permissionMessage = message;
        return this;
    }

    public LegacyExecutor executor() { return executor; }

    public LegacyCommand setExecutor(LegacyExecutor executor) {
        this.executor = executor;
        return this;
    }

    public LegacyTabCompleter completer() { return completer; }

    public LegacyCommand setCompleter(LegacyTabCompleter completer) {
        this.completer = completer;
        return this;
    }

    /** 보낸 사람이 이 명령을 쓸 권한이 있는가(말 없이). */
    public boolean testPermissionSilent(CommandSender sender) {
        String p = permission;
        if (p == null || p.isEmpty()) return true;
        for (String each : p.split(";")) if (sender.hasPermission(each)) return true;
        return false;
    }

    /** 권한이 없으면 거절 문장을 보내고 false. */
    public boolean testPermission(CommandSender sender) {
        if (testPermissionSilent(sender)) return true;
        String msg = permissionMessage;
        if (msg == null) {
            sender.sendMessage(text(DEFAULT_PERMISSION_MESSAGE));
        } else if (!msg.isEmpty()) {
            for (String line : msg.replace("<permission>", permission).split("\n")) sender.sendMessage(text(line));
        }
        return false;
    }

    /**
     * 실행(Bukkit PluginCommand.execute). 플러그인이 꺼져 있으면 예외, 권한이 없으면 거절 문장을 보내고 true,
     * 실행기가 false 를 돌려주면 사용법을 보내고 false. 실행기가 던진 예외는 {@link LegacyCommandException} 으로 싼다.
     * 호출하는 스레드가 "주 스레드"여야 한다 — LegacyCommands 가 전역 스레드로 맞춘다.
     */
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (!plugin.isEnabled()) {
            throw new LegacyCommandException("Cannot execute command '" + label + "' in plugin " + plugin.description().fullName() + " - plugin is disabled.", null);
        }
        if (!testPermission(sender)) return true;
        LegacyExecutor ex = executor;
        boolean success;
        try {
            success = ex != null && ex.onCommand(sender, this, label, args);
        } catch (Throwable t) {
            throw new LegacyCommandException("Unhandled exception executing command '" + label + "' in plugin " + plugin.description().fullName(), t);
        }
        if (!success && !usage.isEmpty()) {
            for (String line : usage.replace("<command>", label).split("\n")) sender.sendMessage(text(line));
        }
        return success;
    }

    /**
     * 자동완성(Bukkit PluginCommand.tabComplete): completer → (실행기가 LegacyTabCompleter 면) 실행기 → 접속자 이름 기본 완성.
     * 완성기가 null 을 돌려주면 다음으로 넘어간다.
     */
    public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
        List<String> out = null;
        try {
            LegacyTabCompleter c = completer;
            if (c != null) out = c.onTabComplete(sender, this, alias, args);
            if (out == null && executor instanceof LegacyTabCompleter tc) out = tc.onTabComplete(sender, this, alias, args);
        } catch (Throwable t) {
            throw new LegacyCommandException("Unhandled exception during tab completion for command '/" + alias + "' in plugin " + plugin.description().fullName(), t);
        }
        return out != null ? out : defaultComplete(args);
    }

    private static List<String> defaultComplete(String[] args) {
        if (args.length == 0) return List.of();
        String last = args[args.length - 1].toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (Player p : Furnace.server().onlinePlayers()) {
            if (p.name().toLowerCase(Locale.ROOT).startsWith(last)) out.add(p.name());
        }
        out.sort(String.CASE_INSENSITIVE_ORDER);
        return out;
    }

    /** 실행 중 예외를 로그·보낸 사람 메시지로 처리한다(Bukkit VanillaCommandWrapper 와 같다). */
    void reportFailure(CommandSender sender, LegacyCommandException e) {
        plugin.logger().log(Level.SEVERE, e.getMessage(), e.getCause() != null ? e.getCause() : e);
        sender.sendMessage(text(INTERNAL_ERROR_MESSAGE));
    }

    static Text text(String legacy) { return LegacyTextSerializer.section().deserialize(legacy); }

    @Override
    public String toString() { return "LegacyCommand(" + name + ", " + plugin.name() + ")"; }
}
