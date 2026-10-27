// 바닐라 RotationArgument(minecraft:rotation) — "yaw pitch" 두 각도(절대·'~' 상대: 실행 회전 기준). 속성 없음.
package org.domi.furnace.server.command.arguments;

import java.util.Collection;
import java.util.List;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.command.brigadier.ArgumentType;
import org.domi.furnace.server.command.brigadier.CommandContext;
import org.domi.furnace.server.command.brigadier.CommandSyntaxException;
import org.domi.furnace.server.command.brigadier.SimpleCommandExceptionType;
import org.domi.furnace.server.command.brigadier.StringReader;

public final class RotationArgument implements ArgumentType<RotationArgument.Rotation> {

    public static final SimpleCommandExceptionType ERROR_NOT_COMPLETE = new SimpleCommandExceptionType(Text.translatable("argument.rotation.incomplete"));
    private static final List<String> EXAMPLES = List.of("0 0", "~ ~", "~-5 ~5");

    /** 바닐라는 WorldCoordinates(x=pitch, y=yaw)로 담는다. 첫 토큰이 yaw, 둘째가 pitch. */
    public record Rotation(WorldCoordinate yaw, WorldCoordinate pitch) {
        /** 실행 회전을 기준으로 푼 Vec2(x=pitch, y=yaw). */
        public Vec2Argument.Vec2 get(CommandSource source) {
            return new Vec2Argument.Vec2((float) pitch.get(source.xRot()), (float) yaw.get(source.yRot()));
        }

        public boolean isYawRelative() { return yaw.relative(); }

        public boolean isPitchRelative() { return pitch.relative(); }
    }

    public static RotationArgument rotation() { return new RotationArgument(); }

    public static Rotation getRotation(CommandContext<CommandSource> ctx, String name) { return ctx.getArgument(name, Rotation.class); }

    @Override
    public Rotation parse(StringReader reader) throws CommandSyntaxException {
        int start = reader.getCursor();
        if (!reader.canRead()) throw ERROR_NOT_COMPLETE.createWithContext(reader);
        WorldCoordinate yaw = WorldCoordinate.parseDouble(reader, false);
        if (reader.canRead() && reader.peek() == ' ') {
            reader.skip();
            WorldCoordinate pitch = WorldCoordinate.parseDouble(reader, false);
            return new Rotation(yaw, pitch);
        }
        reader.setCursor(start);
        throw ERROR_NOT_COMPLETE.createWithContext(reader);
    }

    @Override
    public Collection<String> getExamples() { return EXAMPLES; }

    @Override
    public String networkId() { return "minecraft:rotation"; }
}
