// 바닐라 LocateCommand: /locate structure|biome|poi <id|#태그>.
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
    private static final DynamicCommandExceptionType ERROR_BIOME_NOT_FOUND =
        new DynamicCommandExceptionType(a -> Text.translatable("commands.locate.biome.not_found", a));
    private static final DynamicCommandExceptionType ERROR_POI_NOT_FOUND =
        new DynamicCommandExceptionType(a -> Text.translatable("commands.locate.poi.not_found", a));
    private static final int MAX_STRUCTURE_SEARCH_RADIUS = 100;
    private static final int MAX_BIOME_SEARCH_RADIUS = 6400;
    private static final int BIOME_SAMPLE_RESOLUTION_HORIZONTAL = 32;
    private static final int BIOME_SAMPLE_RESOLUTION_VERTICAL = 64;
    private static final int POI_SEARCH_RADIUS = 256;
    /** 벽시계 상한(바닐라에는 없다). 넘으면 취소하고 일반 명령 실패로 끝낸다. */
    static final long BIOME_TIMEOUT_SECONDS = 120, POI_TIMEOUT_SECONDS = 30;
    static final String BIOME_REGISTRY = "minecraft:worldgen/biome";
    static final String POI_REGISTRY = "minecraft:point_of_interest_type";

    // 찾기는 구조물 시작점을 여러 개 계산할 수 있어 틱 스레드 밖에서 한다(바닐라는 서버 스레드에서 막고 돈다).
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "furnace-locate");
        t.setDaemon(true);
        return t;
    });

    public static void register(CommandDispatcher<CommandSource> d) {
        d.register(literal("locate").requires(s -> s.hasPermission(2))
            .then(literal("structure").then(argument("structure", ResourceOrTagKeyArgument.resourceOrTagKey("minecraft:worldgen/structure"))
                .executes(LocateCommand::locateStructure)))
            .then(literal("biome").then(argument("biome", ResourceOrTagKeyArgument.resourceOrTag(BIOME_REGISTRY))
                .executes(LocateCommand::locateBiome)))
            .then(literal("poi").then(argument("poi", ResourceOrTagKeyArgument.resourceOrTag(POI_REGISTRY))
                .executes(LocateCommand::locatePoi))));
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
        NoiseWorldSource gen = NoiseWorldSource.of(source.level().worldSource());
        if (gen == null) throw ERROR_STRUCTURE_NOT_FOUND.create(arg.printable());
        // BlockPos.containing(source.getPosition())
        int ox = (int) Math.floor(source.x()), oy = (int) Math.floor(source.y()), oz = (int) Math.floor(source.z());
        long t0 = System.nanoTime();
        java.util.concurrent.CompletableFuture<StructureLocator.Found> found = java.util.concurrent.CompletableFuture.supplyAsync(() -> {
            try {
                return gen.structureLocator().findNearest(ids, ox, oy, oz, MAX_STRUCTURE_SEARCH_RADIUS);
            } catch (RuntimeException e) {
                LOG.warn("구조물 찾기 실패", e);
                return null;
            }
        }, WORKER);
        // 결과 메시지는 전역 스레드에서, 명령 결과는 바닐라처럼 거리
        return org.domi.furnace.server.command.EntityOps.result(source, org.domi.furnace.server.command.EntityOps.awaitAsync(source, found, f -> {
            if (f == null) throw ERROR_STRUCTURE_NOT_FOUND.create(arg.printable());
            String name = arg.tag() ? arg.printable() + " (" + f.structureId() + ")" : arg.printable();
            return java.util.concurrent.CompletableFuture.completedFuture(
                    showLocateResult(source, ox, oz, f.x(), f.z(), "commands.locate.structure.success", name, (System.nanoTime() - t0) / 1_000_000));
        }));
    }

    /** 레지스트리 태그 내용: 데이터 팩이 있으면 그것, 없으면(시험) 내장 태그 파일. */
    private static List<String> tagMembers(CommandSource source, String registry, String tag) {
        var dp = source.getServer() == null ? null : source.getServer().dataPacks();
        if (dp != null && dp.loot() != null) return dp.loot().registries().tag(registry, tag);
        return null;
    }

    /** 바이옴 이름 판정(ResourceOrTagArgument.Result.test). */
    static java.util.function.Predicate<String> biomePredicate(ResourceOrTagKeyArgument.Result arg, List<String> tagMembers) {
        String id = arg.id().toString();
        if (!arg.tag()) return id::equals;
        if (tagMembers != null) return tagMembers::contains;
        return b -> org.domi.furnace.server.world.gen.BiomeTags.is(b, id);
    }

    /** POI 종류 판정. 태그는 데이터 팩 내용, 없으면 PoiTypes 의 바닐라 태그. */
    static java.util.function.Predicate<org.domi.furnace.server.world.poi.PoiType> poiPredicate(ResourceOrTagKeyArgument.Result arg, List<String> tagMembers) {
        String id = arg.id().toString();
        if (!arg.tag()) return t -> t.name().equals(id);
        if (tagMembers != null) return t -> tagMembers.contains(t.name());
        return switch (id) {
            case "minecraft:acquirable_job_site" -> org.domi.furnace.server.world.poi.PoiTypes.ACQUIRABLE_JOB_SITE;
            case "minecraft:village" -> org.domi.furnace.server.world.poi.PoiTypes.VILLAGE;
            case "minecraft:bee_home" -> org.domi.furnace.server.world.poi.PoiTypes.BEE_HOME;
            default -> t -> false;
        };
    }

    /** 바닐라 결과 이름: id 는 그대로, 태그는 "#태그 (찾은 것)". */
    static String displayName(ResourceOrTagKeyArgument.Result arg, String foundId) {
        return arg.tag() ? arg.printable() + " (" + foundId + ")" : arg.printable();
    }

    private static int locateBiome(CommandContext<CommandSource> c) throws CommandSyntaxException {
        CommandSource source = c.getSource();
        ResourceOrTagKeyArgument.Result arg = ResourceOrTagKeyArgument.get(c, "biome");
        var pred = biomePredicate(arg, arg.tag() ? tagMembers(source, BIOME_REGISTRY, arg.id().toString()) : null);
        var world = source.level();
        int ox = (int) Math.floor(source.x()), oy = (int) Math.floor(source.y()), oz = (int) Math.floor(source.z());
        int minY = world.dimensionType().minY(), maxY = minY + world.dimensionType().height();
        var ws = world.worldSource();
        if (ws instanceof org.domi.furnace.server.world.gen.AnvilWorldSource a) ws = a.base();
        long t0 = System.nanoTime();
        var timedOut = new java.util.concurrent.atomic.AtomicBoolean();
        var cancel = canceller(source, timedOut);
        java.util.concurrent.CompletableFuture<String[]> found;
        if (ws instanceof NoiseWorldSource gen) {
            found = java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                var f = org.domi.furnace.server.world.gen.BiomeLocator.findClosestBiome3d(ox, oy, oz, MAX_BIOME_SEARCH_RADIUS,
                    BIOME_SAMPLE_RESOLUTION_HORIZONTAL, BIOME_SAMPLE_RESOLUTION_VERTICAL, gen.possibleBiomeIds(),
                    id -> pred.test(gen.biomeName(id)), gen::noiseBiomeIdAtQuart, minY, maxY, cancel);
                return f == null ? null : new String[] {gen.biomeName(f.biomeId()), "" + f.x(), "" + f.y(), "" + f.z()};
            }, WORKER);
        } else if (ws instanceof org.domi.furnace.server.world.gen.SuperflatWorldSource flat) {
            var dp = source.getServer() == null ? null : source.getServer().dataPacks();
            List<String> names = dp != null && dp.loot() != null ? dp.loot().registries().entries(BIOME_REGISTRY) : List.of();
            int bid = flat.biomeId();
            String bname = bid >= 0 && bid < names.size() ? names.get(bid) : "";
            found = java.util.concurrent.CompletableFuture.completedFuture(
                pred.test(bname) ? new String[] {bname, "" + ox, "" + oy, "" + oz} : null);
        } else {
            throw ERROR_BIOME_NOT_FOUND.create(arg.printable());
        }
        found = withTimeout(found, BIOME_TIMEOUT_SECONDS, timedOut);
        return org.domi.furnace.server.command.EntityOps.result(source, org.domi.furnace.server.command.EntityOps.awaitAsync(source, found, f -> {
            if (f == null) throw ERROR_BIOME_NOT_FOUND.create(arg.printable());
            return java.util.concurrent.CompletableFuture.completedFuture(showLocateResult(source, ox, oy, oz,
                Integer.parseInt(f[1]), Integer.parseInt(f[2]), Integer.parseInt(f[3]), true,
                "commands.locate.biome.success", displayName(arg, f[0]), (System.nanoTime() - t0) / 1_000_000));
        }));
    }

    private static int locatePoi(CommandContext<CommandSource> c) throws CommandSyntaxException {
        CommandSource source = c.getSource();
        ResourceOrTagKeyArgument.Result arg = ResourceOrTagKeyArgument.get(c, "poi");
        var pred = poiPredicate(arg, arg.tag() ? tagMembers(source, POI_REGISTRY, arg.id().toString()) : null);
        int ox = (int) Math.floor(source.x()), oy = (int) Math.floor(source.y()), oz = (int) Math.floor(source.z());
        long t0 = System.nanoTime();
        var timedOut = new java.util.concurrent.atomic.AtomicBoolean();
        var found = withTimeout(org.domi.furnace.server.world.poi.PoiLocator.findClosest(source.level(), pred,
            new org.domi.furnace.server.block.Pos(ox, oy, oz), POI_SEARCH_RADIUS, WORKER, canceller(source, timedOut)),
            POI_TIMEOUT_SECONDS, timedOut);
        return org.domi.furnace.server.command.EntityOps.result(source, org.domi.furnace.server.command.EntityOps.awaitAsync(source, found, f -> {
            if (f == null) throw ERROR_POI_NOT_FOUND.create(arg.printable());
            return java.util.concurrent.CompletableFuture.completedFuture(showLocateResult(source, ox, oy, oz,
                f.pos().x(), f.pos().y(), f.pos().z(), false,
                "commands.locate.poi.success", displayName(arg, f.type().name()), (System.nanoTime() - t0) / 1_000_000));
        }));
    }

    /** 취소: 상한을 넘었거나 실행한 플레이어가 나갔다. */
    static java.util.function.BooleanSupplier canceller(CommandSource source, java.util.concurrent.atomic.AtomicBoolean timedOut) {
        var e = source.entity();
        return () -> timedOut.get() || (e instanceof org.domi.furnace.server.entity.Player p && p.isDisconnected());
    }

    /** 상한을 넘으면 탐색에 취소를 알리고 TimeoutException 으로 끝낸다(기존 명령 실패 경로). */
    static <T> java.util.concurrent.CompletableFuture<T> withTimeout(java.util.concurrent.CompletableFuture<T> f, long seconds,
                                                                     java.util.concurrent.atomic.AtomicBoolean timedOut) {
        return f.orTimeout(seconds, java.util.concurrent.TimeUnit.SECONDS).whenComplete((v, t) -> {
            if (org.domi.furnace.server.command.EntityOps.unwrap(t == null ? new RuntimeException() : t) instanceof java.util.concurrent.TimeoutException) timedOut.set(true);
        });
    }

    static int showLocateResult(CommandSource source, int ox, int oz, int x, int z, String key, String name, long ms) {
        return showLocateResult(source, ox, 0, oz, x, 0, z, false, key, name, ms);
    }

    /** LocateCommand.showLocateResult: absoluteY 면 3차원 거리·[x, y, z], 아니면 수평 거리·[x, ~, z]. 녹색, 클릭하면 /tp 제안. */
    static int showLocateResult(CommandSource source, int ox, int oy, int oz, int x, int y, int z, boolean absoluteY, String key, String name, long ms) {
        int distance = absoluteY ? dist3(ox, oy, oz, x, y, z) : dist(ox, oz, x, z);
        String ys = absoluteY ? String.valueOf(y) : "~";
        Text coords = Text.translatable("chat.square_brackets", Text.translatable("chat.coordinates", x, ys, z))
            .color(NamedTextColor.GREEN)
            .clickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/tp @s " + x + " " + ys + " " + z))
            .hoverText(Text.translatable("chat.coordinates.tooltip"));
        source.sendSuccess(() -> Text.translatable(key, name, coords, distance), false);
        LOG.info("Locating element " + name + " took " + ms + " ms");
        return distance;
    }

    /** biome: Mth.floor(Mth.sqrt((float) pos.distSqr(found))) — distSqr 는 double. */
    static int dist3(int x0, int y0, int z0, int x1, int y1, int z1) {
        double dx = x0 - x1, dy = y0 - y1, dz = z0 - z1;
        return (int) Math.floor((float) Math.sqrt((float) (dx * dx + dy * dy + dz * dz)));
    }

    /** Mth.floor(Mth.sqrt(dx*dx + dz*dz)) — 바닐라처럼 int 곱, float 제곱근. */
    static int dist(int x0, int z0, int x1, int z1) {
        int dx = x1 - x0, dz = z1 - z0;
        return (int) Math.floor((float) Math.sqrt((float) (dx * dx + dz * dz)));
    }
}
