// 바닐라 LocateCommand: /locate structure <구조물|#태그>. (biome·poi 는 아직 없다)
package org.domi.furnace.server.command.builtin;

import static org.domi.furnace.server.command.Commands.argument;
import static org.domi.furnace.server.command.Commands.literal;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.domi.furnace.api.text.ClickEvent;
import org.domi.furnace.api.text.NamedTextColor;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.command.arguments.ResourceOrTagKeyArgument;
import org.domi.furnace.server.command.brigadier.CommandContext;
import org.domi.furnace.server.command.brigadier.CommandDispatcher;
import org.domi.furnace.server.command.brigadier.CommandSyntaxException;
import org.domi.furnace.server.command.brigadier.DynamicCommandExceptionType;
import org.domi.furnace.server.world.gen.NoiseWorldSource;
import org.domi.furnace.server.world.gen.structure.StructureLocator;
import org.domi.furnace.server.world.gen.structure.StructureRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class LocateCommand {
    private LocateCommand() { }

    private static final Logger LOG = LoggerFactory.getLogger(LocateCommand.class);
    private static final DynamicCommandExceptionType ERROR_STRUCTURE_NOT_FOUND =
        new DynamicCommandExceptionType(a -> Text.translatable("commands.locate.structure.not_found", a));
    private static final DynamicCommandExceptionType ERROR_STRUCTURE_INVALID =
        new DynamicCommandExceptionType(a -> Text.translatable("commands.locate.structure.invalid", a));
    private static final int MAX_STRUCTURE_SEARCH_RADIUS = 100;

    // 찾기는 구조물 시작점을 여러 개 계산할 수 있어 틱 스레드 밖에서 한다(바닐라는 서버 스레드에서 막고 돈다).
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "furnace-locate");
        t.setDaemon(true);
        return t;
    });

    public static void register(CommandDispatcher<CommandSource> d) {
        d.register(literal("locate").requires(s -> s.hasPermission(2))
            .then(literal("structure").then(argument("structure", ResourceOrTagKeyArgument.resourceOrTagKey("minecraft:worldgen/structure"))
                .executes(LocateCommand::locateStructure))));
    }

    /** 이름 → 구조물 id 목록(태그면 태그 순서). 모르면 null. */
    static List<String> resolve(ResourceOrTagKeyArgument.Result r) {
        String id = r.id().toString();
        if (r.tag()) return StructureRegistry.structureTag(id);
        return StructureRegistry.structureJsons().containsKey(id) ? List.of(id) : null;
    }

    private static int locateStructure(CommandContext<CommandSource> c) throws CommandSyntaxException {
        CommandSource source = c.getSource();
        ResourceOrTagKeyArgument.Result arg = ResourceOrTagKeyArgument.get(c, "structure");
        List<String> ids = resolve(arg);
        if (ids == null) throw ERROR_STRUCTURE_INVALID.create(arg.printable());
        NoiseWorldSource gen = NoiseWorldSource.of(source.getServer().world().worldSource());
        if (gen == null) throw ERROR_STRUCTURE_NOT_FOUND.create(arg.printable());
        // BlockPos.containing(source.getPosition())
        int ox = (int) Math.floor(source.x()), oy = (int) Math.floor(source.y()), oz = (int) Math.floor(source.z());
        WORKER.execute(() -> {
            long t0 = System.nanoTime();
            StructureLocator.Found f;
            try {
                f = gen.structureLocator().findNearest(ids, ox, oy, oz, MAX_STRUCTURE_SEARCH_RADIUS);
            } catch (RuntimeException e) {
                LOG.warn("구조물 찾기 실패", e);
                f = null;
            }
            if (f == null) {
                source.sendFailure(ERROR_STRUCTURE_NOT_FOUND.create(arg.printable()).getRawMessage());
                return;
            }
            String name = arg.tag() ? arg.printable() + " (" + f.structureId() + ")" : arg.printable();
            showLocateResult(source, ox, oz, f.x(), f.z(), "commands.locate.structure.success", name, (System.nanoTime() - t0) / 1_000_000);
        });
        return 1;
    }

    /** LocateCommand.showLocateResult(absoluteY=false): 수평 거리, 좌표는 [x, ~, z] 녹색·클릭하면 /tp. */
    static int showLocateResult(CommandSource source, int ox, int oz, int x, int z, String key, String name, long ms) {
        int distance = dist(ox, oz, x, z);
        Text coords = Text.translatable("chat.square_brackets", Text.translatable("chat.coordinates", x, "~", z))
            .color(NamedTextColor.GREEN)
            .clickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/tp @s " + x + " ~ " + z))
            .hoverText(Text.translatable("chat.coordinates.tooltip"));
        source.sendSuccess(() -> Text.translatable(key, name, coords, distance), false);
        LOG.info("Locating element " + name + " took " + ms + " ms");
        return distance;
    }

    /** Mth.floor(Mth.sqrt(dx*dx + dz*dz)) — 바닐라처럼 int 곱, float 제곱근. */
    static int dist(int x0, int z0, int x1, int z1) {
        int dx = x1 - x0, dz = z1 - z0;
        return (int) Math.floor((float) Math.sqrt((float) (dx * dx + dz * dz)));
    }
}
