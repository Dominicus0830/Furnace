// /furnace pregen start <반경> [가운데] | start border | stop | resume | cancel | status — 미리 생성(T-175, world/Pregen 머리말).
// 명령을 친 차원(source.level(), 다른 차원은 /execute in <차원> run furnace pregen ...)에서 돈다. 가운데를 안 주면 월드 경계 가운데(기본 0, 0).
// 반경은 블록 — 정사각형 [가운데 − 반경, 가운데 + 반경) 에 걸치는 청크를 가운데에서 바깥으로 만든다. 권한 4(save-all·backup 과 같다).
// 바닐라에 없는 명령이라 BuiltinCommands 대조 대상 밖에서 따로 등록한다.
package org.domi.furnace.server.command.builtin;

import static org.domi.furnace.server.command.Commands.argument;
import static org.domi.furnace.server.command.Commands.literal;

import java.util.function.Consumer;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.command.arguments.ColumnPosArgument;
import org.domi.furnace.server.command.brigadier.Arguments;
import org.domi.furnace.server.command.brigadier.CommandDispatcher;
import org.domi.furnace.server.world.Pregen;
import org.domi.furnace.server.world.World;

public final class PregenCommands {

    private PregenCommands() {}

    /** 반경 상한(블록) — 바닐라 좌표 끝(±29,999,984)과 같게. */
    public static final int MAX_RADIUS = 29_999_984;
    /** 이 크기 이상인 월드 경계는 "경계 없음"(바닐라 기본 59,999,968)으로 보고 border 를 거절한다. */
    public static final double UNBOUNDED_BORDER = 59_999_968.0;

    public static void register(CommandDispatcher<CommandSource> d) {
        d.register(literal("furnace")
                .then(literal("pregen").requires(s -> s.hasPermission(4))
                        .executes(c -> status(c.getSource()))
                        .then(literal("start")
                                .then(literal("border").executes(c -> startBorder(c.getSource())))
                                .then(argument("radius", Arguments.integer(1, MAX_RADIUS))
                                        .executes(c -> startRadius(c.getSource(), c.getArgument("radius", Integer.class), null))
                                        .then(argument("center", ColumnPosArgument.columnPos())
                                                .executes(c -> startRadius(c.getSource(), c.getArgument("radius", Integer.class),
                                                        ColumnPosArgument.getColumnPos(c, "center"))))))
                        .then(literal("stop").executes(c -> stop(c.getSource())))
                        .then(literal("resume").executes(c -> resume(c.getSource())))
                        .then(literal("cancel").executes(c -> cancel(c.getSource())))
                        .then(literal("status").executes(c -> status(c.getSource())))));
    }

    /** 진행 보고를 받을 곳: 플레이어면 그 사람에게도(콘솔은 로그로 이미 본다). */
    private static Consumer<String> out(CommandSource s) {
        return s.isPlayer() ? line -> s.sendSuccess(() -> Text.text(line), false) : null;
    }

    private static int startRadius(CommandSource s, int radius, ColumnPosArgument.ColumnPos center) {
        World w = s.level();
        double cx = center != null ? center.x() : w.border().getCenterX();   // 블록 경계 — 반경 160 이면 −160..159(20×20 청크)
        double cz = center != null ? center.z() : w.border().getCenterZ();
        String label = "반경 " + radius + " (가운데 " + (int) Math.floor(cx) + ", " + (int) Math.floor(cz) + ")";
        return start(s, w, Pregen.Area.ofRadius(cx, cz, radius, label));
    }

    private static int startBorder(CommandSource s) {
        World w = s.level();
        var b = w.border();
        if (b.getSize() >= UNBOUNDED_BORDER) {
            s.sendFailure(Text.text("월드 경계가 기본값(사실상 없음)이다 — '/worldborder set <크기>' 로 경계를 정하거나 반경을 준다"));
            return 0;
        }
        double half = b.getSize() / 2.0;
        String label = "월드 경계 (가운데 " + (int) Math.floor(b.getCenterX()) + ", " + (int) Math.floor(b.getCenterZ()) + ", 크기 " + (long) b.getSize() + ")";
        return start(s, w, Pregen.Area.ofRadius(b.getCenterX(), b.getCenterZ(), half, label));
    }

    private static int start(CommandSource s, World w, Pregen.Area area) {
        if (s.getServer().pregen().job(w) == null && Pregen.loadSaved(w) != null) {   // 멈춰 둔 진행을 모르고 덮지 않게
            s.sendFailure(Text.text(w.dimension() + " 에 멈춰 둔 미리 생성이 있다 — '/furnace pregen resume' 으로 이어 하거나 '/furnace pregen cancel' 뒤 다시 시작한다"));
            return 0;
        }
        var r = s.getServer().pregen().start(w, area, out(s));
        if (r.job() == null) {
            s.sendFailure(Text.text(r.refused()));
            return 0;
        }
        s.sendSuccess(() -> Text.text("미리 생성을 시작했다: " + w.dimension() + " " + area.label() + ", " + area.total() + " 청크"
                ), true);
        return (int) Math.min(Integer.MAX_VALUE, area.total());
    }

    private static int stop(CommandSource s) {
        World w = s.level();
        if (!s.getServer().pregen().stop(w)) {
            s.sendFailure(Text.text(w.dimension() + " 에서 돌고 있는 미리 생성이 없다"));
            return 0;
        }
        s.sendSuccess(() -> Text.text("미리 생성을 멈췄다(" + w.dimension() + ") — '/furnace pregen resume' 으로 이어 한다"), true);
        return 1;
    }

    private static int resume(CommandSource s) {
        World w = s.level();
        var r = s.getServer().pregen().resume(w, out(s));
        if (r.job() == null) {
            s.sendFailure(Text.text(r.refused()));
            return 0;
        }
        var p = r.job().progress();
        s.sendSuccess(() -> Text.text("미리 생성을 이어 한다: " + w.dimension() + " " + p.area().label() + ", " + p.processed() + "/" + p.total() + " 청크부터"), true);
        return 1;
    }

    private static int cancel(CommandSource s) {
        World w = s.level();
        if (!s.getServer().pregen().cancel(w)) {
            s.sendFailure(Text.text(w.dimension() + " 에 멈추거나 지울 미리 생성이 없다"));
            return 0;
        }
        s.sendSuccess(() -> Text.text("미리 생성을 취소하고 진행 위치를 지웠다(" + w.dimension() + ") — 이미 만든 청크는 그대로 둔다"), true);
        return 1;
    }

    private static int status(CommandSource s) {
        World w = s.level();
        var j = s.getServer().pregen().job(w);
        if (j != null) {
            String line = j.progress().line();
            s.sendSuccess(() -> Text.text(line), false);
            return 1;
        }
        Pregen.Saved saved = Pregen.loadSaved(w);
        if (saved != null) {
            String line = String.format(java.util.Locale.ROOT, "[미리 생성] %s %s: 멈춤, %d/%d 청크 (%.2f%%) — '/furnace pregen resume' 으로 이어 한다",
                    w.dimension(), saved.area().label(), saved.next(), saved.area().total(), saved.next() * 100.0 / saved.area().total());
            s.sendSuccess(() -> Text.text(line), false);
            return 1;
        }
        s.sendSuccess(() -> Text.text(w.dimension() + ": 미리 생성 없음. '/furnace pregen start <반경 블록> [가운데 x z]' 또는 '/furnace pregen start border'"), false);
        return 0;
    }
}
