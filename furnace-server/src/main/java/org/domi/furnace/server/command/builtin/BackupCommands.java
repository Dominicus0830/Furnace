// /furnace backup [status] — 지금 월드를 백업하거나(유지보수 스레드에서, 끝나면 알린다) 마지막 결과를 본다. 권한 4(save-all 과 같다).
// 바닐라에 없는 명령이라 BuiltinCommands 대조 대상 밖에서 따로 등록한다.
package org.domi.furnace.server.command.builtin;

import static org.domi.furnace.server.command.Commands.literal;

import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.command.brigadier.CommandDispatcher;

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
            source.sendFailure(Text.text("백업을 쓸 수 없다(서버가 아직 준비되지 않았다)"));
            return 0;
        }
        if (backup.running()) {
            source.sendFailure(Text.text("이미 백업 중이다"));
            return 0;
        }
        source.sendSuccess(() -> Text.text("백업을 시작한다 → " + backup.options().backupDirectory().toAbsolutePath()), true);
        backup.runAsync("명령").thenAccept(r -> {
            if (r.ok()) source.sendSuccess(() -> Text.text("백업 완료: " + r.message()), true);
            else source.sendFailure(Text.text("백업 실패: " + r.message()));
        });
        return 1;
    }

    private static int status(CommandSource source) {
        var backup = source.getServer().backup();
        if (backup == null) {
            source.sendFailure(Text.text("백업을 쓸 수 없다"));
            return 0;
        }
        var o = backup.options();
        String every = o.backupEnabled() ? o.backupIntervalMinutes() + "분마다" : "주기 백업 꺼짐(backup.interval-minutes=0)";
        source.sendSuccess(() -> Text.text("백업: " + every + ", 폴더 " + o.backupDirectory().toAbsolutePath()
                + ", 보관 " + (o.backupKeep() == 0 ? "전부" : o.backupKeep() + "개") + (backup.running() ? " — 지금 백업 중" : "")), false);
        var last = backup.last();
        source.sendSuccess(() -> Text.text(last == null ? "이번 기동 뒤 백업 없음" : "마지막: " + (last.ok() ? "성공 " : "실패 ") + last.message()), false);
        return 1;
    }
}
