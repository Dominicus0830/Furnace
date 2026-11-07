package org.bukkit.command;

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.domi.furnace.api.Furnace;
import org.domi.furnace.compat.paper.CompatCommands;
import org.domi.furnace.compat.paper.UnsupportedFeature;
import org.domi.furnace.compat.thread.PrimaryThread;

/**
 * Bukkit 명령 지도(SimpleCommandMap)를 Furnace 명령 트리 위에 옮긴 것. 올린 명령은 트리에 이름·별칭·"플러그인:이름" 으로 붙고,
 * {@link #getKnownCommands()} 는 트리에 실제로 올라간 이름을 보여 준다(지우면 트리에서도 빠진다).
 *
 * <p>Bukkit 과 다른 점: ① 바닐라 명령과 Furnace 가 직접 올린 다른 플러그인 명령은 이 지도에 없다(getCommand 가 null),
 * ② "fallbackPrefix:이름" 의 접두어는 호출자가 준 값이 아니라 소유 플러그인 이름이다, ③ 이름이 이미 트리에 있으면
 * (바닐라·다른 플러그인) 접두어 형태로만 올라가고 register 가 false 를 돌려준다.
 */
public class SimpleCommandMap implements CommandMap {

    private final Server server;
    /** 트리에 올라간 이름 → 명령. 직접 만지면 안 되는 안쪽 저장소(밖에는 {@link #knownCommands} 뷰가 나간다). */
    private final Map<String, Command> names = new LinkedHashMap<>();
    private final Map<Command, CompatCommands.Attached> attached = new IdentityHashMap<>();
    private final Set<Object> hookedOwners = new java.util.HashSet<>();
    /** Bukkit 의 protected knownCommands — 지우면 트리에서도 빠지고 넣기는 {@link #register} 로만 된다. */
    protected final Map<String, Command> knownCommands = new KnownView();

    public SimpleCommandMap(Server server) { this.server = server; }

    @Override
    public void registerAll(String fallbackPrefix, List<Command> commands) {
        if (commands != null) for (Command c : commands) register(fallbackPrefix, c);
    }

    @Override
    public boolean register(String fallbackPrefix, Command command) { return register(command.getName(), fallbackPrefix, command); }

    @Override
    public boolean register(String label, String fallbackPrefix, Command command) {
        if (label == null || command == null) throw new IllegalArgumentException("label/command 가 null");
        String lower = label.toLowerCase(Locale.ROOT).trim();
        CompatCommands.Attached at;
        synchronized (this) {
            CompatCommands.Attached old = attached.remove(command);
            if (old != null) {   // 같은 명령을 다시 올린다 — 앞의 등록을 치운다
                forget(command, old);
                command.unregister(this);
            }
            at = CompatCommands.attach(command, lower);
            attached.put(command, at);
            for (String n : at.names()) names.put(n, command);
            if (hookedOwners.add(at.owner())) {
                at.owner().own(() -> dropOwner(at.owner()));
            }
        }
        boolean registered = at.names().contains(lower);
        // 별칭 중 다른 명령에 막힌 것은 활성 별칭에서 뺀다
        command.getAliases().removeIf(a -> a == null || !at.names().contains(a.toLowerCase(Locale.ROOT).trim()));
        command.setLabel(registered ? lower : at.namespace() + ":" + lower);
        command.register(this);
        return registered;
    }

    private void forget(Command command, CompatCommands.Attached at) {
        CompatCommands.detachAll(at);
        names.values().removeIf(c -> c == command);
    }

    /** 플러그인이 꺼졌다 — 그 플러그인이 올린 명령을 지도에서 치운다(트리 쪽은 LegacyCommands 가 이미 치운다). */
    private synchronized void dropOwner(Object owner) {
        hookedOwners.remove(owner);
        for (Iterator<Map.Entry<Command, CompatCommands.Attached>> it = attached.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Command, CompatCommands.Attached> e = it.next();
            if (e.getValue().owner() != owner) continue;
            names.values().removeIf(c -> c == e.getKey());
            e.getKey().unregister(this);
            it.remove();
        }
    }

    @Override
    public synchronized void clearCommands() {
        for (Map.Entry<Command, CompatCommands.Attached> e : new ArrayList<>(attached.entrySet())) {
            CompatCommands.detachAll(e.getValue());
            e.getKey().unregister(this);
        }
        attached.clear();
        names.clear();
    }

    @Override
    public synchronized Command getCommand(String name) { return name == null ? null : names.get(name.toLowerCase(Locale.ROOT)); }

    @Override
    public Map<String, Command> getKnownCommands() { return knownCommands; }

    @Override
    public boolean dispatch(CommandSender sender, String commandLine) throws CommandException {
        String[] args = commandLine.split(" ");   // Bukkit 의 PATTERN_ON_SPACE.split: 끝의 빈 칸은 버리고 가운데 빈 칸은 빈 인자로 둔다
        if (args.length == 0) return false;
        String sentCommandLabel = args[0].toLowerCase(Locale.ROOT);
        Command target = getCommand(sentCommandLabel);
        if (target == null) return false;
        String[] rest = Arrays.copyOfRange(args, 1, args.length);
        if (PrimaryThread.isPrimary()) {
            try (PrimaryThread.Scope ignored = PrimaryThread.enter()) {
                runTarget(target, sender, sentCommandLabel, rest, commandLine);
            }
        } else {
            // 주 스레드가 아니다(region 스레드·비동기). 옛 코드는 주 스레드에서 돈다고 믿으므로 전역으로 넘기고 접수한 것으로 본다(H2 문서 dispatchCommand)
            Furnace.server().scheduler().global().execute(() -> {
                try (PrimaryThread.Scope ignored = PrimaryThread.enter()) {
                    runTarget(target, sender, sentCommandLabel, rest, commandLine);
                } catch (RuntimeException e) {
                    java.util.logging.Logger.getLogger("Minecraft").log(Level.SEVERE, e.getMessage(), e.getCause() != null ? e.getCause() : e);
                    sender.sendMessage("§cAn internal error occurred while attempting to perform this command");
                }
            });
        }
        return true;
    }

    private static void runTarget(Command target, CommandSender sender, String label, String[] args, String commandLine) {
        try {
            target.execute(sender, label, args);
        } catch (CommandException ex) {
            throw ex;
        } catch (Throwable ex) {
            throw new CommandException("Unhandled exception executing '" + commandLine + "' in " + target, ex);
        }
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String cmdLine) throws IllegalArgumentException {
        return tabComplete(sender, cmdLine, null);
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String cmdLine, Location location) throws IllegalArgumentException {
        if (sender == null) throw new IllegalArgumentException("Sender cannot be null");
        if (cmdLine == null) throw new IllegalArgumentException("Command line cannot null");
        String[] args = cmdLine.split(" ", -1);
        if (args.length == 0) return null;
        String sentCommandLabel = args[0];
        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            String prefix = sender instanceof Player ? "/" : "";
            String lower = cmdLine.toLowerCase(Locale.ROOT);
            List<Map.Entry<String, Command>> snapshot;
            synchronized (this) {
                snapshot = new ArrayList<>(names.entrySet());
            }
            for (Map.Entry<String, Command> entry : snapshot) {
                if (!entry.getValue().testPermissionSilent(sender)) continue;
                if (entry.getKey().toLowerCase(Locale.ROOT).startsWith(lower)) completions.add(prefix + entry.getKey());
            }
            completions.sort(String.CASE_INSENSITIVE_ORDER);
            return completions;
        }
        Command target = getCommand(sentCommandLabel);
        if (target == null) return null;
        String[] rest = Arrays.copyOfRange(args, 1, args.length);
        try {
            return location == null ? target.tabComplete(sender, sentCommandLabel, rest) : target.tabComplete(sender, sentCommandLabel, rest, location);
        } catch (CommandException ex) {
            throw ex;
        } catch (Throwable ex) {
            throw new CommandException("Unhandled exception executing tab-completer for '" + cmdLine + "' in " + target, ex);
        }
    }

    /** 이름 하나를 트리에서 뺐다(knownCommands 에서 지웠다). */
    private synchronized void removeName(String name) {
        Command c = names.remove(name);
        if (c == null) return;
        CompatCommands.Attached at = attached.get(c);
        if (at == null) return;
        CompatCommands.detachName(at, name);
        if (!names.containsValue(c)) attached.remove(c);   // 마지막 이름이었다 — 이제 어디에도 안 올라 있다
    }

    /** knownCommands 의 바깥 모습: 읽기는 안쪽 저장소를, 지우기는 트리까지 반영한다. */
    private final class KnownView extends AbstractMap<String, Command> {
        @Override
        public Command get(Object key) {
            synchronized (SimpleCommandMap.this) {
                return names.get(key);
            }
        }

        @Override
        public boolean containsKey(Object key) {
            synchronized (SimpleCommandMap.this) {
                return names.containsKey(key);
            }
        }

        @Override
        public int size() {
            synchronized (SimpleCommandMap.this) {
                return names.size();
            }
        }

        @Override
        public Command put(String key, Command value) {
            Command current = get(key);
            if (current == value) return current;
            throw new UnsupportedFeature("SimpleCommandMap#knownCommands.put", "명령은 CommandMap.register 로 올려야 Furnace 명령 트리에 붙는다");
        }

        @Override
        public Command remove(Object key) {
            Command c;
            synchronized (SimpleCommandMap.this) {
                c = names.get(key);
                if (c == null) return null;
                removeName((String) key);
            }
            return c;
        }

        @Override
        public Set<Entry<String, Command>> entrySet() {
            return new AbstractSet<>() {
                @Override
                public int size() { return KnownView.this.size(); }

                @Override
                public Iterator<Entry<String, Command>> iterator() {
                    List<Entry<String, Command>> snapshot;
                    synchronized (SimpleCommandMap.this) {
                        snapshot = new ArrayList<>(names.entrySet().size());
                        for (Map.Entry<String, Command> e : names.entrySet()) snapshot.add(new SimpleImmutableEntry<>(e));
                    }
                    Iterator<Entry<String, Command>> it = snapshot.iterator();
                    return new Iterator<>() {
                        private Entry<String, Command> last;

                        @Override
                        public boolean hasNext() { return it.hasNext(); }

                        @Override
                        public Entry<String, Command> next() {
                            last = it.next();
                            return last;
                        }

                        @Override
                        public void remove() {
                            if (last == null) throw new IllegalStateException();
                            KnownView.this.remove(last.getKey());
                            last = null;
                        }
                    };
                }

                @Override
                public void clear() {
                    for (String k : new ArrayList<>(KnownView.this.keySet())) KnownView.this.remove(k);
                }
            };
        }
    }
}
