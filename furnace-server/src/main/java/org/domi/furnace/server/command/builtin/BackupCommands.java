// /furnace backup [status] — 지금 월드를 백업하거나(유지보수 스레드에서, 끝나면 알린다) 마지막 결과를 본다. 권한 4(save-all 과 같다).
// 바닐라에 없는 명령이라 BuiltinCommands 대조 대상 밖에서 따로 등록한다.
package org.domi.furnace.server.command.builtin;

import static org.domi.furnace.server.command.Commands.literal;

import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.command.brigadier.CommandDispatcher;
import org.domi.furnace.server.i18n.Lang;

public final class BackupCommands {

    private BackupCommands() {}

    public static void register(CommandDispatcher<CommandSource> d) {
        d.register(literal("furnace")
                .then(literal("backup").requires(s -> s.hasPermission(4))
                        .executes(c -> start(c.getSource()))
                        .then(literal("status").executes(c -> status(c.getSource())))));
    }

    private static int start(CommandSource source) {
        var backup = source.getServer().backup();
        if (backup == null) {
            source.sendFailure(Text.text(Lang.t("cmd.backup.unavailable-starting")));
            return 0;
        }
        if (backup.running()) {
            source.sendFailure(Text.text(Lang.t("backup.already-running")));
            return 0;
        }
        source.sendSuccess(() -> Text.text(Lang.t("cmd.backup.starting", backup.options().backupDirectory().toAbsolutePath())), true);
        backup.runAsync("command").thenAccept(r -> {
            if (r.ok()) source.sendSuccess(() -> Text.text(Lang.t("cmd.backup.done", r.message())), true);
            else source.sendFailure(Text.text(Lang.t("cmd.backup.failed", r.message())));
        });
        return 1;
    }

    private static int status(CommandSource source) {
        var backup = source.getServer().backup();
        if (backup == null) {
            source.sendFailure(Text.text(Lang.t("cmd.backup.unavailable")));
            return 0;
        }
        var o = backup.options();
        String every = o.backupEnabled() ? Lang.t("cmd.backup.every", o.backupIntervalMinutes()) : Lang.t("cmd.backup.scheduled-off");
        source.sendSuccess(() -> Text.text(Lang.t("cmd.backup.status", every, o.backupDirectory().toAbsolutePath(),
                o.backupKeep() == 0 ? Lang.t("cmd.backup.keep-all") : Lang.t("cmd.backup.keep-n", o.backupKeep()), backup.running() ? Lang.t("cmd.backup.running-part") : "")), false);
        var last = backup.last();
        source.sendSuccess(() -> Text.text(last == null ? Lang.t("cmd.backup.none-yet") : Lang.t(last.ok() ? "cmd.backup.last-ok" : "cmd.backup.last-failed", last.message())), false);
        return 1;
    }
}
