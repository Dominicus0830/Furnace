// 바닐라 BlockPosArgument(minecraft:block_pos) — "x y z" 정수 좌표(절대·'~' 상대) 또는 '^' 로컬 좌표.
package org.domi.furnace.server.command.arguments;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.command.SharedSuggestions;
import org.domi.furnace.server.command.brigadier.ArgumentType;
import org.domi.furnace.server.command.brigadier.CommandContext;
import org.domi.furnace.server.command.brigadier.CommandSyntaxException;
import org.domi.furnace.server.command.brigadier.SimpleCommandExceptionType;
import org.domi.furnace.server.command.brigadier.StringReader;
import org.domi.furnace.server.command.brigadier.Suggestions;
import org.domi.furnace.server.command.brigadier.SuggestionsBuilder;
import org.domi.furnace.api.text.Text;

public final class BlockPosArgument implements ArgumentType<BlockPosArgument.Coordinates> {

    private static final List<String> EXAMPLES = List.of("0 0 0", "~ ~ ~", "^ ^ ^", "^1 ^ ^-5", "~0.5 ~1 ~-5");
    public static final SimpleCommandExceptionType ERROR_NOT_LOADED = new SimpleCommandExceptionType(Text.translatable("argument.pos.unloaded"));
    public static final SimpleCommandExceptionType ERROR_OUT_OF_WORLD = new SimpleCommandExceptionType(Text.translatable("argument.pos.outofworld"));
    public static final SimpleCommandExceptionType ERROR_OUT_OF_BOUNDS = new SimpleCommandExceptionType(Text.translatable("build.tooHigh"));
    /** Vec3Argument.ERROR_NOT_COMPLETE / ERROR_MIXED_TYPE. */
    public static final SimpleCommandExceptionType ERROR_NOT_COMPLETE = new SimpleCommandExceptionType(Text.translatable("argument.pos3d.incomplete"));

    /** 바닐라 Coordinates — 실행 위치·회전 기준으로 푼다. */
    public interface Coordinates {
        double[] getPosition(CommandSource source);
    }

    /** 바닐라 LocalCoordinates: 왼쪽·위·앞 오프셋(실행자 시선 기준). */
    public record LocalCoordinates(double left, double up, double forwards) implements Coordinates {
        @Override
        public double[] getPosition(CommandSource source) {
            float yRot = source.yRot(), xRot = source.xRot();
            float f = MthTrig.cos((yRot + 90.0f) * (float) (Math.PI / 180.0));
            float f1 = MthTrig.sin((yRot + 90.0f) * (float) (Math.PI / 180.0));
            float f2 = MthTrig.cos(-xRot * (float) (Math.PI / 180.0));
            float f3 = MthTrig.sin(-xRot * (float) (Math.PI / 180.0));
            float f4 = MthTrig.cos((-xRot + 90.0f) * (float) (Math.PI / 180.0));
            float f5 = MthTrig.sin((-xRot + 90.0f) * (float) (Math.PI / 180.0));
            double[] v1 = {f * f2, f3, f1 * f2};
            double[] v2 = {f * f4, f5, f1 * f4};
            // v3 = v1 × v2 * -1
            double[] v3 = {-(v1[1] * v2[2] - v1[2] * v2[1]), -(v1[2] * v2[0] - v1[0] * v2[2]), -(v1[0] * v2[1] - v1[1] * v2[0])};
            double dx = v1[0] * forwards + v2[0] * up + v3[0] * left;
            double dy = v1[1] * forwards + v2[1] * up + v3[1] * left;
            double dz = v1[2] * forwards + v2[2] * up + v3[2] * left;
            return new double[]{source.x() + dx, source.y() + dy, source.z() + dz};
        }

        static LocalCoordinates parse(StringReader reader) throws CommandSyntaxException {
            int start = reader.getCursor();
            double left = readDouble(reader, start);
            if (reader.canRead() && reader.peek() == ' ') {
                reader.skip();
                double up = readDouble(reader, start);
                if (reader.canRead() && reader.peek() == ' ') {
                    reader.skip();
                    double forwards = readDouble(reader, start);
                    return new LocalCoordinates(left, up, forwards);
                }
            }
            reader.setCursor(start);
            throw ERROR_NOT_COMPLETE.createWithContext(reader);
        }

        private static double readDouble(StringReader reader, int start) throws CommandSyntaxException {
            if (!reader.canRead()) throw WorldCoordinate.ERROR_EXPECTED_DOUBLE.createWithContext(reader);
            if (reader.peek() != '^') {
                reader.setCursor(start);
                throw WorldCoordinate.ERROR_MIXED_TYPE.createWithContext(reader);
            }
            reader.skip();
            return reader.canRead() && reader.peek() != ' ' ? reader.readDouble() : 0.0;
        }
    }

    record World(WorldCoordinates c) implements Coordinates {
        @Override
        public double[] getPosition(CommandSource source) { return c.getPosition(source); }
    }

    public static BlockPosArgument blockPos() { return new BlockPosArgument(); }

    /** 바닐라 getBlockPos: BlockPos.containing(위치). */
    public static int[] getBlockPos(CommandContext<CommandSource> ctx, String name) {
        double[] p = ctx.getArgument(name, Coordinates.class).getPosition(ctx.getSource());
        return new int[]{(int) Math.floor(p[0]), (int) Math.floor(p[1]), (int) Math.floor(p[2])};
    }

    /** 바닐라 getSpawnablePos: Level.isInSpawnableBounds 밖이면 "argument.pos.outofworld". */
    public static int[] getSpawnablePos(CommandContext<CommandSource> ctx, String name) throws CommandSyntaxException {
        int[] p = getBlockPos(ctx, name);
        boolean y = p[1] >= -20000000 && p[1] < 20000000;
        boolean xz = p[0] >= -30000000 && p[2] >= -30000000 && p[0] < 30000000 && p[2] < 30000000;
        if (!y || !xz) throw ERROR_OUT_OF_WORLD.create();
        return p;
    }

    @Override
    public Coordinates parse(StringReader reader) throws CommandSyntaxException {
        if (reader.canRead() && reader.peek() == '^') return LocalCoordinates.parse(reader);
        int start = reader.getCursor();
        WorldCoordinate x = WorldCoordinate.parseInt(reader);
        if (reader.canRead() && reader.peek() == ' ') {
            reader.skip();
            WorldCoordinate y = WorldCoordinate.parseInt(reader);
            if (reader.canRead() && reader.peek() == ' ') {
                reader.skip();
                WorldCoordinate z = WorldCoordinate.parseInt(reader);
                return new World(new WorldCoordinates(x, y, z));
            }
        }
        reader.setCursor(start);
        throw ERROR_NOT_COMPLETE.createWithContext(reader);
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        if (!(context.getSource() instanceof CommandSource)) return Suggestions.empty();
        String remaining = builder.getRemaining();
        Collection<SharedSuggestions.TextCoordinates> candidates = !remaining.isEmpty() && remaining.charAt(0) == '^'
                ? List.of(SharedSuggestions.TextCoordinates.DEFAULT_LOCAL) : List.of(SharedSuggestions.TextCoordinates.DEFAULT_GLOBAL);
        return SharedSuggestions.suggestCoordinates(remaining, candidates, builder, s -> {
            try {
                parse(new StringReader(s));
                return true;
            } catch (CommandSyntaxException e) {
                return false;
            }
        });
    }

    @Override
    public Collection<String> getExamples() { return EXAMPLES; }

    @Override
    public String networkId() { return "minecraft:block_pos"; }
}
