// 바닐라 TeleportCommand: /teleport(=/tp) [대상] <좌표 [회전 | facing …] | 도착 엔티티>.
// 좌표는 절대·'~' 상대·'^' 국소. 엔티티와 플레이어를 가리지 않고 옮기며, 같은 차원 안에서만 옮긴다(다른 차원이면 실패).
package org.domi.furnace.server.command.builtin;

import static org.domi.furnace.server.command.Commands.argument;
import static org.domi.furnace.server.command.Commands.literal;

import java.util.List;
import java.util.Locale;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.command.arguments.EntityAnchorArgument;
import org.domi.furnace.server.command.arguments.EntityArgument;
import org.domi.furnace.server.command.arguments.RotationArgument;
import org.domi.furnace.server.command.arguments.Vec2Argument;
import org.domi.furnace.server.command.arguments.Vec3Argument;
import org.domi.furnace.server.command.brigadier.CommandContext;
import org.domi.furnace.server.command.brigadier.CommandDispatcher;
import org.domi.furnace.server.command.brigadier.CommandSyntaxException;
import org.domi.furnace.server.command.brigadier.SimpleCommandExceptionType;
import org.domi.furnace.server.entity.Rider;

public final class TeleportCommand {
    private TeleportCommand() { }

    static final SimpleCommandExceptionType INVALID_POSITION = new SimpleCommandExceptionType(Text.translatable("commands.teleport.invalidPosition"));

    /** 바라볼 점({x, y, z}) — facing 절이 없으면 null. */
    private record LookAt(double[] point) { }

    public static void register(CommandDispatcher<CommandSource> d) {
        var node = d.register(literal("teleport").requires(s -> s.hasPermission(2))
                .then(argument("location", Vec3Argument.vec3())
                        .executes(c -> toPosition(c, self(c.getSource()), Vec3Argument.getVec3(c, "location"), sourceRotation(c.getSource()), null)))
                .then(argument("destination", EntityArgument.entity())
                        .executes(c -> EntityArgument.withEntity(c, "destination", dest -> toEntity(c.getSource(), self(c.getSource()), dest.get(0)))))
                .then(argument("targets", EntityArgument.entities())
                        .then(argument("location", Vec3Argument.vec3())
                                .executes(c -> EntityArgument.withEntities(c, "targets",
                                        t -> toPosition(c, t, Vec3Argument.getVec3(c, "location"), null, null)))
                                .then(argument("rotation", RotationArgument.rotation())
                                        .executes(c -> EntityArgument.withEntities(c, "targets",
                                                t -> toPosition(c, t, Vec3Argument.getVec3(c, "location"),
                                                        RotationArgument.getRotation(c, "rotation").get(c.getSource()), null))))
                                .then(literal("facing")
                                        .then(literal("entity")
                                                .then(argument("facingEntity", EntityArgument.entity())
                                                        .executes(c -> facingEntity(c, EntityAnchorArgument.Anchor.FEET))
                                                        .then(argument("facingAnchor", EntityAnchorArgument.anchor())
                                                                .executes(c -> facingEntity(c, EntityAnchorArgument.getAnchor(c, "facingAnchor"))))))
                                        .then(argument("facingLocation", Vec3Argument.vec3())
                                                .executes(c -> EntityArgument.withEntities(c, "targets",
                                                        t -> toPosition(c, t, Vec3Argument.getVec3(c, "location"), null,
                                                                new LookAt(Vec3Argument.getVec3(c, "facingLocation"))))))))
                        .then(argument("destination", EntityArgument.entity())
                                .executes(c -> EntityArgument.withEntities(c, "targets",
                                        t -> EntityArgument.withEntity(c, "destination", dest -> toEntity(c.getSource(), t, dest.get(0))))))));
        d.register(literal("tp").requires(s -> s.hasPermission(2)).redirect(node));
    }

    private static int facingEntity(CommandContext<CommandSource> c, EntityAnchorArgument.Anchor anchor) throws CommandSyntaxException {
        return EntityArgument.withEntities(c, "targets", t -> EntityArgument.withEntity(c, "facingEntity",
                f -> toPosition(c, t, Vec3Argument.getVec3(c, "location"), null, new LookAt(anchor.apply(f.get(0))))));
    }

    private static List<Rider> self(CommandSource source) throws CommandSyntaxException {
        return List.of(source.getEntityOrException());
    }

    private static Vec2Argument.Vec2 sourceRotation(CommandSource source) { return new Vec2Argument.Vec2(source.xRot(), source.yRot()); }

    /** 좌표로 옮긴다. rotation(x=pitch, y=yaw)이 null 이면 각자 지금 회전을 유지한다. */
    private static int toPosition(CommandContext<CommandSource> c, List<Rider> targets, double[] pos, Vec2Argument.Vec2 rotation, LookAt look)
            throws CommandSyntaxException {
        return toPosition(c.getSource(), targets, pos, rotation, look);
    }

    static int toPosition(CommandSource source, List<Rider> targets, double[] pos, Vec2Argument.Vec2 rotation, LookAt look)
            throws CommandSyntaxException {
        if (!Teleports.inSpawnableBounds(pos[0], pos[1], pos[2])) throw INVALID_POSITION.create();
        String dimension = source.level().dimension();
        for (Rider t : targets) if (!t.dimensionKey().equals(dimension)) throw Teleports.ERROR_CROSS_DIMENSION.create();
        for (Rider t : targets) {
            float yaw = Teleports.wrapDegrees(rotation == null ? t.riderYRot() : rotation.y());
            float pitch = Teleports.wrapDegrees(rotation == null ? t.riderXRot() : rotation.x());
            if (look != null) {
                float[] r = Teleports.lookAt(pos[0], pos[1], pos[2], look.point());
                yaw = r[0];
                pitch = r[1];
            }
            Teleports.teleport(source, t, pos[0], pos[1], pos[2], yaw, pitch);
        }
        if (targets.size() == 1) {
            Rider t = targets.get(0);
            source.sendSuccess(() -> Text.translatable("commands.teleport.success.location.single", t.displayName(),
                    format(pos[0]), format(pos[1]), format(pos[2])), true);
        } else {
            source.sendSuccess(() -> Text.translatable("commands.teleport.success.location.multiple", targets.size(),
                    format(pos[0]), format(pos[1]), format(pos[2])), true);
        }
        return targets.size();
    }

    /** 도착 엔티티의 위치·회전으로 옮긴다. */
    static int toEntity(CommandSource source, List<Rider> targets, Rider destination) throws CommandSyntaxException {
        double x = destination.x(), y = destination.y(), z = destination.z();
        if (!Teleports.inSpawnableBounds(x, y, z)) throw INVALID_POSITION.create();
        String dimension = destination.dimensionKey();
        for (Rider t : targets) if (!t.dimensionKey().equals(dimension)) throw Teleports.ERROR_CROSS_DIMENSION.create();
        float yaw = Teleports.wrapDegrees(destination.riderYRot()), pitch = Teleports.wrapDegrees(destination.riderXRot());
        for (Rider t : targets) Teleports.teleport(source, t, x, y, z, yaw, pitch);
        if (targets.size() == 1) {
            source.sendSuccess(() -> Text.translatable("commands.teleport.success.entity.single", targets.get(0).displayName(), destination.displayName()), true);
        } else {
            source.sendSuccess(() -> Text.translatable("commands.teleport.success.entity.multiple", targets.size(), destination.displayName()), true);
        }
        return targets.size();
    }

    /** String.format(Locale.ROOT, "%f", v). */
    static String format(double v) { return String.format(Locale.ROOT, "%f", v); }
}
