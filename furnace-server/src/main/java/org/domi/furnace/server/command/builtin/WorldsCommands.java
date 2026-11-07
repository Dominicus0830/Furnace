// Furnace 전용 명령 /worlds — 추가 월드(다중 월드, W1) 보기·만들기·불러오기·내리기. 바닐라에 없는 명령이라 BuiltinCommands 와 따로 등록한다. 권한 3.
//   /worlds list
//   /worlds create <이름> [normal|nether|the_end] [default|large_biomes|amplified|flat|void] [시드]
//   /worlds load <이름>             저장된 설명서(level.dat 의 furnace:world)로 불러온다
//   /worlds unload <이름> [save]    save 기본 true. 플레이어가 있으면 거절
//   /worlds regen [반경]            실행 위치 청크(둘레 반경 0..8 청크)를 처음부터 다시 생성한다(W3, 엔티티는 그대로)
//   /worlds regen <청크x> <청크z> [반경]
// 만들기·내리기는 월드 스레드에서 돌고, 결과는 끝난 뒤 전역 스레드에서 알린다. 들어가기는 바닐라 /execute in <키> run tp … 로.
package org.domi.furnace.server.command.builtin;

import static org.domi.furnace.server.command.Commands.argument;
import static org.domi.furnace.server.command.Commands.literal;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.api.world.WorldCreator;
import org.domi.furnace.api.world.WorldEnvironment;
import org.domi.furnace.api.world.WorldGenerator;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.command.brigadier.Arguments;
import org.domi.furnace.server.command.brigadier.CommandContext;
import org.domi.furnace.server.command.brigadier.CommandDispatcher;
import org.domi.furnace.server.world.World;
import org.domi.furnace.server.world.WorldManager;

public final class WorldsCommands {

    private WorldsCommands() {}

    public static void register(CommandDispatcher<CommandSource> d) {
        d.register(literal("worlds").requires(s -> s.hasPermission(3))
                .then(literal("list").executes(c -> list(c.getSource())))
                .then(literal("create").then(argument("name", Arguments.word())
                        .executes(c -> create(c, null, null, null))
                        .then(argument("environment", Arguments.word())
                                .executes(c -> create(c, str(c, "environment"), null, null))
                                .then(argument("generator", Arguments.word())
                                        .executes(c -> create(c, str(c, "environment"), str(c, "generator"), null))
                                        .then(argument("seed", Arguments.longArg(Long.MIN_VALUE, Long.MAX_VALUE))
                                                .executes(c -> create(c, str(c, "environment"), str(c, "generator"), c.getArgument("seed", Long.class))))))))
                .then(literal("load").then(argument("name", Arguments.word()).executes(c -> load(c.getSource(), str(c, "name")))))
                .then(literal("regen")
                        .executes(c -> regen(c.getSource(), here(c.getSource())[0], here(c.getSource())[1], 0))
                        .then(argument("radius", Arguments.integer(0, MAX_REGEN_RADIUS))
                                .executes(c -> regen(c.getSource(), here(c.getSource())[0], here(c.getSource())[1], c.getArgument("radius", Integer.class))))
                        .then(argument("chunkX", Arguments.integer()).then(argument("chunkZ", Arguments.integer())
                                .executes(c -> regen(c.getSource(), c.getArgument("chunkX", Integer.class), c.getArgument("chunkZ", Integer.class), 0))
                                .then(argument("radius", Arguments.integer(0, MAX_REGEN_RADIUS))
                                        .executes(c -> regen(c.getSource(), c.getArgument("chunkX", Integer.class), c.getArgument("chunkZ", Integer.class),
                                                c.getArgument("radius", Integer.class)))))))
                .then(literal("unload").then(argument("name", Arguments.word())
                        .executes(c -> unload(c.getSource(), str(c, "name"), true))
                        .then(argument("save", Arguments.bool())
                                .executes(c -> unload(c.getSource(), str(c, "name"), c.getArgument("save", Boolean.class)))))));
    }

    /** /worlds regen 반경 상한 — 17×17 = 289 청크. */
    static final int MAX_REGEN_RADIUS = 8;

    private static int[] here(CommandSource s) {
        return new int[] {(int) Math.floor(s.x()) >> 4, (int) Math.floor(s.z()) >> 4};
    }

    private static int regen(CommandSource s, int cx, int cz, int radius) {
        World w = s.level();
        int n = (2 * radius + 1) * (2 * radius + 1);
        java.util.List<CompletableFuture<Boolean>> all = new java.util.ArrayList<>(n);
        for (int dz = -radius; dz <= radius; dz++)
            for (int dx = -radius; dx <= radius; dx++) all.add(w.regenerateChunk(cx + dx, cz + dz, false, false));
        s.sendSuccess(() -> Text.text("청크 " + n + "개 다시 생성 중… (" + w.dimension() + " " + cx + "," + cz + ")"), true);
        CompletableFuture<Integer> done = CompletableFuture.allOf(all.toArray(new CompletableFuture[0])).thenApply(v -> {
            int ok = 0;
            for (var f : all) if (Boolean.TRUE.equals(f.join())) ok++;
            return ok;
        });
        report(s, done, ok -> "청크 " + ok + "/" + n + "개를 다시 생성했다");
        return n;
    }

    private static String str(CommandContext<CommandSource> c, String name) { return c.getArgument(name, String.class); }

    private static WorldManager manager(CommandSource s) {
        WorldManager m = s.getServer().worldManager();
        if (m == null) s.sendFailure(Text.text("이 서버는 다중 월드를 켜지 않았다"));
        return m;
    }

    private static int list(CommandSource s) {
        ServerCore core = s.getServer();
        int n = 0;
        for (World w : core.worlds()) {
            String name = w.levelName() != null ? w.levelName() : "(기본)";
            String line = String.format(Locale.ROOT, "%s %s — 타입 %s, 청크 %d, 플레이어 %d, 시드 %d", w.dimension(), name,
                    w.dimensionType().typeKey(), w.loadedChunkCount(), w.playersHere().size(), core.seedOf(w));
            s.sendSuccess(() -> Text.text(line), false);
            n++;
        }
        return n;
    }

    private static int create(CommandContext<CommandSource> c, String env, String gen, Long seed) {
        CommandSource s = c.getSource();
        WorldManager m = manager(s);
        if (m == null) return 0;
        WorldCreator spec;
        try {
            var b = WorldCreator.named(str(c, "name"));
            if (env != null) b.environment(environment(env));
            if (gen != null) b.generator(WorldGenerator.valueOf(gen.toUpperCase(Locale.ROOT)));
            if (seed != null) b.seed(seed);
            spec = b.build();
        } catch (IllegalArgumentException e) {
            s.sendFailure(Text.text("잘못된 설명서: " + e.getMessage()));
            return 0;
        }
        s.sendSuccess(() -> Text.text("월드 '" + spec.name() + "' 준비 중…"), true);
        report(s, m.create(spec), w -> "월드 '" + spec.name() + "' 올림 — /execute in " + w.dimension() + " run tp @s 0 100 0");
        return 1;
    }

    private static WorldEnvironment environment(String raw) {
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "normal", "overworld" -> WorldEnvironment.NORMAL;
            case "nether", "the_nether" -> WorldEnvironment.NETHER;
            case "end", "the_end" -> WorldEnvironment.THE_END;
            default -> throw new IllegalArgumentException("환경은 normal · nether · the_end: " + raw);
        };
    }

    private static int load(CommandSource s, String name) {
        WorldManager m = manager(s);
        if (m == null) return 0;
        report(s, m.load(name), w -> "월드 '" + name + "' 불러옴 (" + w.dimension() + ")");
        return 1;
    }

    private static int unload(CommandSource s, String name, boolean save) {
        WorldManager m = manager(s);
        if (m == null) return 0;
        World w = m.extraWorld(name);
        if (w == null) {
            s.sendFailure(Text.text("올라와 있는 추가 월드가 아니다: " + name));
            return 0;
        }
        report(s, m.unload(w, save), ok -> ok ? "월드 '" + name + "' 내림" : null);
        return 1;
    }

    /** 끝나면 전역 스레드에서 알린다. 성공 문구가 null 이면 거절(플레이어가 있거나 취소)로 알린다. */
    private static <T> void report(CommandSource s, CompletableFuture<T> f, java.util.function.Function<T, String> ok) {
        f.whenComplete((v, t) -> s.getServer().scheduler().global().execute(() -> {
            if (t != null) {
                Throwable cause = t instanceof java.util.concurrent.CompletionException && t.getCause() != null ? t.getCause() : t;
                s.sendFailure(Text.text("실패: " + cause.getMessage()));
                return;
            }
            String msg = ok.apply(v);
            if (msg == null) s.sendFailure(Text.text("내리지 않았다 — 월드 안에 플레이어가 있거나 플러그인이 취소했다"));
            else s.sendSuccess(() -> Text.text(msg), true);
        }));
    }
}
