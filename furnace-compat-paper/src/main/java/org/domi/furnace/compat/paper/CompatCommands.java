// Bukkit Command 를 Furnace 명령 트리에 붙이는 다리. H2 의 LegacyCommands(트리 등록·스레드 규칙) 위에 얹는다.
//
//  - 소유 플러그인: PluginIdentifiableCommand 면 그 플러그인, 아니면 명령 클래스를 올린 클래스로더의 플러그인. 둘 다 아니면 UnsupportedFeature
//    (Furnace 명령 트리의 모든 명령은 주인 플러그인이 있어야 꺼질 때 치울 수 있다).
//  - 실행·권한·사용법은 Bukkit Command 의 execute 가 한다(PluginCommand.execute 도 Bukkit 규칙 그대로) — 코어는 "통과 모드"로 부르기만 한다.
//    트리에 보일지는 Command#testPermissionSilent 가 정한다(권한이 없으면 트리에서 숨는다 — H2 문서의 알려진 차이 1).
package org.domi.furnace.compat.paper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import org.bukkit.command.Command;
import org.bukkit.command.CommandException;
import org.bukkit.command.PluginIdentifiableCommand;
import org.bukkit.entity.Player;
import org.domi.furnace.compat.command.LegacyCommand;
import org.domi.furnace.compat.command.LegacyCommandException;

public final class CompatCommands {

    private CompatCommands() {}

    /** 명령 하나가 Furnace 트리에 올라간 모습. */
    public static final class Attached {
        private final BukkitPluginAdapter owner;
        private final LegacyCommand core;
        private final Collection<String> names;

        Attached(BukkitPluginAdapter owner, LegacyCommand core, Collection<String> names) {
            this.owner = owner;
            this.core = core;
            this.names = names;
        }

        public BukkitPluginAdapter owner() { return owner; }

        /** 트리에 실제로 올라간 이름들(이름·별칭·"플러그인:이름"). */
        public Collection<String> names() { return names; }

        /** 이 플러그인의 이름 접두어("플러그인:이름" 의 앞). */
        public String namespace() { return owner.name().toLowerCase(Locale.ROOT).replace(' ', '_'); }
    }

    /** 명령을 트리에 올린다. 별칭 중 쓸 수 없는 것(빈 문자열·공백·':' 포함)은 활성 별칭에서 뺀다. */
    public static Attached attach(Command command, String label) {
        BukkitPluginAdapter owner = ownerOf(command);
        List<String> aliases = new ArrayList<>();
        for (java.util.Iterator<String> it = command.getAliases().iterator(); it.hasNext(); ) {
            String a = it.next();
            String n = a == null ? "" : a.toLowerCase(Locale.ROOT).trim();
            if (n.isEmpty() || n.indexOf(' ') >= 0 || n.indexOf(':') >= 0) {
                owner.logger().log(Level.WARNING, "명령 /" + label + " 의 별칭 '" + a + "' 은(는) Furnace 명령 트리에 올릴 수 없어 건너뛴다");
                it.remove();
            } else {
                aliases.add(n);
            }
        }
        LegacyCommand core = new LegacyCommand(owner, label);
        core.setAliases(aliases);
        core.setDescription(command.getDescription());
        core.setPassthrough(sender -> command.testPermissionSilent(Compat.sender(sender)));
        core.setExecutor((sender, c, l, args) -> {
            try {
                return command.execute(Compat.sender(sender), l, args);
            } catch (CommandException e) {
                throw new LegacyCommandException(e.getMessage(), e.getCause());
            }
        });
        core.setCompleter((sender, c, alias, args) -> {
            try {
                return command.tabComplete(Compat.sender(sender), alias, args);
            } catch (CommandException e) {
                throw new LegacyCommandException(e.getMessage(), e.getCause());
            }
        });
        Collection<String> names = Compat.commands().register(owner, core);
        return new Attached(owner, core, names);
    }

    /** 이 명령이 올라갈 때 붙는 이름 접두어(소유 플러그인 이름). */
    public static String namespaceOf(Command command) { return ownerOf(command).name().toLowerCase(Locale.ROOT).replace(' ', '_'); }

    /** 이름 하나만 트리에서 뺀다. */
    public static boolean detachName(Attached a, String name) { return Compat.commands().unregisterName(a.core, name); }

    /** 이 명령의 이름을 모두 트리에서 뺀다. */
    public static void detachAll(Attached a) { Compat.commands().unregister(a.core); }

    static BukkitPluginAdapter ownerOf(Command command) {
        if (command instanceof PluginIdentifiableCommand pic) return CompatPluginManager.adapter(pic.getPlugin());
        BukkitPluginAdapter byLoader = PluginLoaders.of(command.getClass().getClassLoader());
        if (byLoader != null) return byLoader;
        throw new UnsupportedFeature("CommandMap.register(" + command.getClass().getName() + ")",
                "Furnace 명령 트리의 명령은 주인 플러그인이 있어야 한다 — PluginIdentifiableCommand 로 만들거나 플러그인 jar 안의 클래스여야 한다");
    }

    /** 보낸 사람이 대상을 볼 수 있는가(숨은 플레이어는 이름을 새지 않는다). 이 서버가 보이기 규칙을 모르면 보인다고 본다. */
    public static boolean canSee(Player viewer, Player target) {
        if (viewer == target) return true;
        if (!(viewer instanceof org.bukkit.craftbukkit.entity.CraftPlayer v) || !(target instanceof org.bukkit.craftbukkit.entity.CraftPlayer t)) return true;
        try {
            return v.furnaceHandle().canSee(t.furnaceHandle());
        } catch (UnsupportedOperationException e) {
            return true;
        }
    }
}
