// 바닐라 RideCommand: /ride <대상> mount <탈것> | dismount. 탑승 목록은 탈것을 가진 region 스레드에서만 바꾸므로 일을 거기로 보낸다 —
// 그래서 검사 결과(실패 메시지)도 그 스레드에서 알린다. 대상과 탈것이 같은 region 에 없으면 실패한다(차이).
// 차이: 반환값이 늘 1 이고 결과는 나중에 메시지로 온다(context-notes 의 "반환값 차이").
package org.domi.furnace.server.command.builtin;

import static org.domi.furnace.server.command.Commands.argument;
import static org.domi.furnace.server.command.Commands.literal;

import java.util.ArrayList;
import java.util.List;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.command.arguments.EntityArgument;
import org.domi.furnace.server.command.brigadier.CommandDispatcher;
import org.domi.furnace.server.command.brigadier.CommandSyntaxException;
import org.domi.furnace.server.command.brigadier.Dynamic2CommandExceptionType;
import org.domi.furnace.server.command.brigadier.DynamicCommandExceptionType;
import org.domi.furnace.server.command.brigadier.SimpleCommandExceptionType;
import org.domi.furnace.server.entity.Entity;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.entity.Rider;
import org.domi.furnace.server.world.Region;

public final class RideCommand {
    private RideCommand() { }

    private static final DynamicCommandExceptionType ERROR_NOT_RIDING = new DynamicCommandExceptionType(n -> Text.translatable("commands.ride.not_riding", n));
    private static final Dynamic2CommandExceptionType ERROR_ALREADY_RIDING = new Dynamic2CommandExceptionType((a, b) -> Text.translatable("commands.ride.already_riding", a, b));
    private static final Dynamic2CommandExceptionType ERROR_MOUNT_FAILED = new Dynamic2CommandExceptionType((a, b) -> Text.translatable("commands.ride.mount.failure.generic", a, b));
    private static final SimpleCommandExceptionType ERROR_MOUNTING_PLAYER = new SimpleCommandExceptionType(Text.translatable("commands.ride.mount.failure.cant_ride_players"));
    private static final SimpleCommandExceptionType ERROR_MOUNTING_LOOP = new SimpleCommandExceptionType(Text.translatable("commands.ride.mount.failure.loop"));
    private static final SimpleCommandExceptionType ERROR_WRONG_DIMENSION = new SimpleCommandExceptionType(Text.translatable("commands.ride.mount.failure.wrong_dimension"));

    public static void register(CommandDispatcher<CommandSource> d) {
        d.register(literal("ride").requires(s -> s.hasPermission(2))
                .then(argument("target", EntityArgument.entity())
                        .then(literal("mount").then(argument("vehicle", EntityArgument.entity())
                                .executes(c -> EntityArgument.withEntity(c, "target",
                                        t -> EntityArgument.withEntity(c, "vehicle", v -> mount(c.getSource(), t.get(0), v.get(0)))))))
                        .then(literal("dismount")
                                .executes(c -> EntityArgument.withEntity(c, "target", t -> dismount(c.getSource(), t.get(0)))))));
    }

    /** 이 region 에서 r 이 있는가(탈것을 가진 region 이 두 쪽을 모두 만질 수 있어야 한다). */
    private static boolean ownedHere(Region region, Rider r) {
        if (r instanceof Player p) return region.players().containsKey(p.entityId());
        return r instanceof Entity e && region.level() != null && region.level().entities().contains(e);
    }

    static int mount(CommandSource source, Rider rider, Rider vehicle) throws CommandSyntaxException {
        // 바닐라 검사 순서대로 — 플레이어는 탈것이 될 수 없다는 것은 어디서든 같다
        if (rider.vehicle() != null) throw ERROR_ALREADY_RIDING.create(rider.displayName(), rider.vehicle().displayName());
        if (vehicle instanceof Player) throw ERROR_MOUNTING_PLAYER.create();
        Entity v = (Entity) vehicle;
        List<Rider> chain = new ArrayList<>();
        chain.add(rider);
        if (rider instanceof Entity re) re.collectIndirectPassengers(chain);
        for (Rider r : chain) if (r == v) throw ERROR_MOUNTING_LOOP.create();
        if (!rider.dimensionKey().equals(vehicle.dimensionKey())) throw ERROR_WRONG_DIMENSION.create();
        Teleports.worldOf(source, vehicle).runAt(v.x(), v.z(), region -> {
            try {
                if (!ownedHere(region, rider) || !ownedHere(region, v) || !rider.startRiding(v, true)) {
                    throw ERROR_MOUNT_FAILED.create(rider.displayName(), vehicle.displayName());
                }
                source.sendSuccess(() -> Text.translatable("commands.ride.mount.success", rider.displayName(), vehicle.displayName()), true);
            } catch (CommandSyntaxException e) {
                source.sendFailure(e.getRawMessage());
            }
        });
        return 1;
    }

    static int dismount(CommandSource source, Rider rider) throws CommandSyntaxException {
        Entity v = rider.vehicle();
        if (v == null) throw ERROR_NOT_RIDING.create(rider.displayName());
        Teleports.worldOf(source, v).runAt(v.x(), v.z(), region -> {
            if (rider.vehicle() != v) return;
            rider.stopRiding();
            source.sendSuccess(() -> Text.translatable("commands.ride.dismount.success", rider.displayName(), v.displayName()), true);
        });
        return 1;
    }
}
