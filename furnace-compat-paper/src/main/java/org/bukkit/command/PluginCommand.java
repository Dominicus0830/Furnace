package org.bukkit.command;

import java.util.List;
import org.bukkit.plugin.Plugin;

/**
 * 플러그인 명령(Bukkit PluginCommand). 실행기를 정하지 않으면 플러그인 본체({@code JavaPlugin#onCommand})가 실행기다.
 * 플러그인.yml 의 {@code commands:} 는 켜질 때 이 형으로 만들어 지도에 올라가며 {@code JavaPlugin#getCommand} 로 받는다.
 */
public final class PluginCommand extends Command implements PluginIdentifiableCommand {

    private final Plugin owningPlugin;
    private CommandExecutor executor;
    private TabCompleter completer;

    protected PluginCommand(String name, Plugin owner) {
        super(name);
        this.executor = owner;
        this.owningPlugin = owner;
        this.usageMessage = "";
    }

    @Override
    public boolean execute(CommandSender sender, String commandLabel, String[] args) {
        boolean success = false;
        if (!owningPlugin.isEnabled()) {
            throw new CommandException("Cannot execute command '" + commandLabel + "' in plugin " + owningPlugin.getDescription().getFullName() + " - plugin is disabled.");
        }
        if (!testPermission(sender)) return true;
        try {
            success = executor.onCommand(sender, this, commandLabel, args);
        } catch (Throwable ex) {
            throw new CommandException("Unhandled exception executing command '" + commandLabel + "' in plugin " + owningPlugin.getDescription().getFullName(), ex);
        }
        if (!success && !usageMessage.isEmpty()) {
            for (String line : usageMessage.replace("<command>", commandLabel).split("\n")) sender.sendMessage(line);
        }
        return success;
    }

    /** null 을 주면 플러그인 본체로 돌아간다. */
    public void setExecutor(CommandExecutor executor) { this.executor = executor == null ? owningPlugin : executor; }

    public CommandExecutor getExecutor() { return executor; }

    public void setTabCompleter(TabCompleter completer) { this.completer = completer; }

    public TabCompleter getTabCompleter() { return completer; }

    @Override
    public Plugin getPlugin() { return owningPlugin; }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args) throws CommandException, IllegalArgumentException {
        if (sender == null) throw new IllegalArgumentException("Sender cannot be null");
        if (args == null) throw new IllegalArgumentException("Arguments cannot be null");
        if (alias == null) throw new IllegalArgumentException("Alias cannot be null");
        List<String> completions = null;
        try {
            if (completer != null) completions = completer.onTabComplete(sender, this, alias, args);
            if (completions == null && executor instanceof TabCompleter tc) completions = tc.onTabComplete(sender, this, alias, args);
        } catch (Throwable ex) {
            StringBuilder message = new StringBuilder();
            message.append("Unhandled exception during tab completion for command '/").append(alias);
            for (String arg : args) message.append(' ').append(arg);
            message.append("' in plugin ").append(owningPlugin.getDescription().getFullName());
            throw new CommandException(message.toString(), ex);
        }
        if (completions == null) return super.tabComplete(sender, alias, args);
        return completions;
    }

    @Override
    public String toString() {
        StringBuilder stringBuilder = new StringBuilder(super.toString());
        stringBuilder.deleteCharAt(stringBuilder.length() - 1);
        stringBuilder.append(", ").append(owningPlugin.getDescription().getFullName()).append(')');
        return stringBuilder.toString();
    }
}
