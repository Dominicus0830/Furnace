// Bukkit 명령 지도(SimpleCommandMap·Bukkit.dispatchCommand·JavaPlugin.getCommand)에 해당 — LegacyCommand 를 서버 명령 트리에 붙인다.
//
// 스레드: 옛 플러그인은 명령이 주 스레드에서 돈다고 믿는다. 서버의 명령 큐는 이미 전역 스레드에서 BasicCommand.execute 를 부르므로
// 실행은 그대로 전역에서 돌고(PrimaryThread 표시), 자동완성은 연결 스레드에서 불리므로 전역으로 넘겨 결과를 기다린다(시간 제한, 넘으면 빈 목록).
// 이름: 서버 명령 등록소가 "이름"·별칭·"플러그인:이름"을 붙이고 겹치면 네임스페이스 형태만 남긴다(Bukkit·Paper 와 같다).
// 알려진 차이: 서버가 트리에서 권한 없는 사람에게 명령을 숨기므로 권한 없이 치면 Bukkit 의 거절 문장 대신 "알 수 없는 명령"이 나온다.
package org.domi.furnace.compat.command;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import org.domi.furnace.api.Furnace;
import org.domi.furnace.api.command.BasicCommand;
import org.domi.furnace.api.command.CommandSender;
import org.domi.furnace.api.plugin.FurnacePlugin;
import org.domi.furnace.api.plugin.PluginDescription;
import org.domi.furnace.compat.thread.PrimaryThread;

public final class LegacyCommands {

    private final Furnace server;
    private final long tabTimeoutMillis;
    /** 플러그인 → (이름·별칭 → 명령). */
    private final Map<FurnacePlugin, Map<String, LegacyCommand>> byPlugin = new ConcurrentHashMap<>();

    public LegacyCommands(Furnace server) { this(server, 1000); }

    /** @param tabTimeoutMillis 연결 스레드가 전역 스레드의 자동완성 결과를 기다리는 한도 */
    public LegacyCommands(Furnace server, long tabTimeoutMillis) {
        this.server = server;
        this.tabTimeoutMillis = tabTimeoutMillis;
    }

    /**
     * plugin.yml 의 commands: 에 선언된 명령을 모두 만들어 등록한다(JavaPlugin 이 PluginCommand 를 미리 만들어 getCommand 로 주는 것과 같다).
     * 실행기는 나중에 {@link #command(FurnacePlugin, String)} 로 받아 setExecutor 한다 — 그 전에 쳐지면 사용법만 나온다.
     */
    public List<LegacyCommand> declareFromDescription(FurnacePlugin plugin) {
        List<LegacyCommand> out = new ArrayList<>();
        for (Map.Entry<String, PluginDescription.CommandInfo> e : plugin.description().commands().entrySet()) {
            PluginDescription.CommandInfo info = e.getValue();
            LegacyCommand c = new LegacyCommand(plugin, e.getKey());
            if (info.description() != null) c.setDescription(info.description());
            if (info.usage() != null) c.setUsage(info.usage());
            if (info.permission() != null) c.setPermission(info.permission());
            c.setAliases(info.aliases() == null ? List.of() : info.aliases());
            register(plugin, c);
            out.add(c);
        }
        return out;
    }

    /** 그 플러그인의 명령을 이름이나 별칭으로(Bukkit getCommand). 없으면 null. */
    public LegacyCommand command(FurnacePlugin plugin, String nameOrAlias) {
        Map<String, LegacyCommand> m = byPlugin.get(plugin);
        return m == null ? null : m.get(nameOrAlias.toLowerCase(Locale.ROOT));
    }

    /** 이름·별칭·"플러그인:이름"으로 모든 플러그인에서 찾는다. */
    public LegacyCommand find(String label) {
        String l = label.toLowerCase(Locale.ROOT);
        int colon = l.indexOf(':');
        for (Map.Entry<FurnacePlugin, Map<String, LegacyCommand>> e : byPlugin.entrySet()) {
            if (colon >= 0) {
                String ns = e.getKey().name().toLowerCase(Locale.ROOT).replace(' ', '_');
                if (l.startsWith(ns + ":")) {
                    LegacyCommand c = e.getValue().get(l.substring(colon + 1));
                    if (c != null) return c;
                }
            }
        }
        if (colon >= 0) return null;
        for (Map<String, LegacyCommand> m : byPlugin.values()) {
            LegacyCommand c = m.get(l);
            if (c != null && isLive(c, l)) return c;
        }
        return null;
    }

    /** 서버 트리에 실제로 그 이름으로 올라간 명령인가(바닐라·다른 플러그인과 겹쳐 네임스페이스로만 올라갔으면 아님). */
    private boolean isLive(LegacyCommand c, String name) {
        Collection<String> reg = registered.get(c);
        return reg != null && reg.contains(name);
    }

    private final Map<LegacyCommand, Collection<String>> registered = new ConcurrentHashMap<>();

    /**
     * 명령을 서버 트리에 붙인다(Bukkit CommandMap.register). 별칭도 같이 붙고 "플러그인:이름" 형태도 생긴다.
     * @return 실제로 올라간 이름들(겹친 이름은 네임스페이스 형태만)
     */
    public Collection<String> register(FurnacePlugin plugin, LegacyCommand cmd) {
        if (cmd.plugin() != plugin) throw new IllegalArgumentException("다른 플러그인의 명령: " + cmd);
        Map<String, LegacyCommand> names = byPlugin.computeIfAbsent(plugin, p -> {
            plugin.own(() -> {   // 꺼지면 서버가 트리 쪽은 치우고, 이 지도도 비운다
                byPlugin.remove(plugin);
                registered.keySet().removeIf(c -> c.plugin() == plugin);
            });
            return new ConcurrentHashMap<>();
        });
        Collection<String> done = server.commands().register(plugin, cmd.name(), cmd.description(), cmd.aliases(), new Adapter(cmd));
        registered.put(cmd, done);
        names.put(cmd.name(), cmd);
        for (String a : cmd.aliases()) names.putIfAbsent(a, cmd);
        return done;
    }

    /** 명령을 트리에서 뺀다(이름·별칭·네임스페이스 형태 모두). */
    public boolean unregister(LegacyCommand cmd) {
        Collection<String> reg = registered.remove(cmd);
        Map<String, LegacyCommand> names = byPlugin.get(cmd.plugin());
        if (names != null) names.values().removeIf(c -> c == cmd);
        boolean any = false;
        if (reg != null) for (String n : reg) any |= server.commands().unregister(cmd.plugin(), n);
        return any;
    }

    /**
     * Bukkit.dispatchCommand: 옛 명령이면 지금 스레드가 주 스레드일 때 바로 실행해 성공 여부를 돌려준다(실행기가 false 면 false).
     * 주 스레드가 아니면(region 스레드·비동기) 전역으로 넘기고 접수한 것으로 true. 옛 명령이 아니면 서버 명령 큐에 넣는다(결과는 알 수 없어 true).
     */
    public boolean dispatch(CommandSender sender, String commandLine) {
        String line = commandLine.startsWith("/") ? commandLine.substring(1) : commandLine;
        int sp = line.indexOf(' ');
        String label = sp < 0 ? line : line.substring(0, sp);
        LegacyCommand cmd = find(label);
        if (cmd == null) return server.dispatchCommand(sender, line);
        String[] args = sp < 0 ? new String[0] : line.substring(sp + 1).split(" ", -1);
        if (!PrimaryThread.isPrimary()) {
            server.scheduler().global().execute(() -> runNow(cmd, sender, label, args));
            return true;
        }
        return runNow(cmd, sender, label, args);
    }

    private boolean runNow(LegacyCommand cmd, CommandSender sender, String label, String[] args) {
        try (PrimaryThread.Scope ignored = PrimaryThread.enter()) {
            return cmd.execute(sender, label, args);
        } catch (LegacyCommandException e) {
            cmd.reportFailure(sender, e);
            return false;
        }
    }

    /** 서버 명령 등록소에 맡기는 BasicCommand — 친 이름(label)까지 LegacyCommand 에 넘긴다. */
    private final class Adapter implements BasicCommand {
        private final LegacyCommand cmd;

        Adapter(LegacyCommand cmd) { this.cmd = cmd; }

        @Override
        public void execute(CommandSender sender, String[] args) { execute(sender, cmd.name(), args); }

        @Override
        public void execute(CommandSender sender, String label, String[] args) {
            if (PrimaryThread.isPrimary()) {
                runNow(cmd, sender, label, args);
            } else {
                server.scheduler().global().execute(() -> runNow(cmd, sender, label, args));
            }
        }

        @Override
        public List<String> suggest(CommandSender sender, String[] args) { return suggest(sender, cmd.name(), args); }

        @Override
        public List<String> suggest(CommandSender sender, String label, String[] args) {
            try {
                if (!cmd.testPermissionSilent(sender)) return List.of();
                if (PrimaryThread.isPrimary()) return complete(sender, label, args);
                return server.scheduler().global().submit(() -> complete(sender, label, args)).get(tabTimeoutMillis, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                cmd.plugin().logger().log(Level.WARNING, "자동완성이 " + tabTimeoutMillis + "ms 안에 끝나지 않아 건너뛴다: /" + label);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                cmd.plugin().logger().log(Level.WARNING, "자동완성 실패: /" + label, e);
            }
            return List.of();
        }

        private List<String> complete(CommandSender sender, String label, String[] args) {
            try (PrimaryThread.Scope ignored = PrimaryThread.enter()) {
                return cmd.tabComplete(sender, label, args);
            } catch (LegacyCommandException e) {
                cmd.plugin().logger().log(Level.SEVERE, e.getMessage(), e.getCause() != null ? e.getCause() : e);
                return List.of();
            }
        }

        /** 권한 없는 사람에게는 트리에서 숨긴다. */
        @Override
        public boolean canUse(CommandSender sender) { return cmd.testPermissionSilent(sender); }

        /**
         * 서버 등록소가 plugin.yml 의 권한을 덧씌우지 않도록 null 이 아닌 값을 준다(권한 검사는 canUse 가 한다 — 세미콜론 여러 권한·setPermission 변경을 따른다).
         */
        @Override
        public String permission() { return cmd.permission() == null ? "" : cmd.permission(); }
    }
}
