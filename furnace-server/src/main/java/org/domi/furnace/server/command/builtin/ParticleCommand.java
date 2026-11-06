// 바닐라 ParticleCommand: /particle <입자> [위치 [퍼짐 <속도> <개수> [force|normal] [보는 사람]]].
package org.domi.furnace.server.command.builtin;

import static org.domi.furnace.server.command.Commands.argument;
import static org.domi.furnace.server.command.Commands.literal;

import java.util.Collection;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.command.arguments.EntityArgument;
import org.domi.furnace.server.command.arguments.ParticleArgument;
import org.domi.furnace.server.command.arguments.Vec3Argument;
import org.domi.furnace.server.command.brigadier.Arguments;
import org.domi.furnace.server.command.brigadier.CommandContext;
import org.domi.furnace.server.command.brigadier.CommandDispatcher;
import org.domi.furnace.server.command.brigadier.CommandSyntaxException;
import org.domi.furnace.server.command.brigadier.SimpleCommandExceptionType;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.net.protocol.play.LevelParticles;
import org.domi.furnace.server.net.protocol.play.ParticleOptions;

public final class ParticleCommand {
    private ParticleCommand() { }

    private static final SimpleCommandExceptionType ERROR_FAILED = new SimpleCommandExceptionType(Text.translatable("commands.particle.failed"));

    public static void register(CommandDispatcher<CommandSource> d) {
        d.register(literal("particle").requires(s -> s.hasPermission(2))
                .then(argument("name", ParticleArgument.particle())
                        .executes(c -> send(c.getSource(), ParticleArgument.getParticle(c, "name"), pos(c.getSource()), new double[] {0, 0, 0}, 0.0F, 0, false, null))
                        .then(argument("pos", Vec3Argument.vec3())
                                .executes(c -> send(c.getSource(), ParticleArgument.getParticle(c, "name"), Vec3Argument.getVec3(c, "pos"), new double[] {0, 0, 0}, 0.0F, 0, false, null))
                                .then(argument("delta", Vec3Argument.vec3(false))
                                        .then(argument("speed", Arguments.floatArg(0.0F, Float.MAX_VALUE))
                                                .then(argument("count", Arguments.integer(0))
                                                        .executes(c -> run(c, false, null))
                                                        .then(literal("force")
                                                                .executes(c -> run(c, true, null))
                                                                .then(argument("viewers", EntityArgument.players())
                                                                        .executes(c -> run(c, true, EntityArgument.getPlayers(c, "viewers")))))
                                                        .then(literal("normal")
                                                                .executes(c -> run(c, false, null))
                                                                .then(argument("viewers", EntityArgument.players())
                                                                        .executes(c -> run(c, false, EntityArgument.getPlayers(c, "viewers")))))))))));
    }

    private static double[] pos(CommandSource s) { return new double[] {s.x(), s.y(), s.z()}; }

    private static int run(CommandContext<CommandSource> c, boolean force, Collection<Player> viewers) throws CommandSyntaxException {
        return send(c.getSource(), ParticleArgument.getParticle(c, "name"), Vec3Argument.getVec3(c, "pos"), Vec3Argument.getVec3(c, "delta"),
                c.getArgument("speed", Float.class), c.getArgument("count", Integer.class), force, viewers);
    }

    /** 바닐라 sendParticles(player, force, x,y,z, packet): 이 명령 레벨의 대상 중 칸 가운데가 (force ? 512 : 32) 미만인 플레이어에게만. */
    private static int send(CommandSource source, ParticleOptions p, double[] pos, double[] delta, float speed, int count, boolean force,
            Collection<Player> viewers) throws CommandSyntaxException {
        var pkt = new LevelParticles(force, pos[0], pos[1], pos[2], (float) delta[0], (float) delta[1], (float) delta[2], speed, count, p);
        var level = source.level();
        Collection<Player> targets = viewers != null ? viewers : source.getServer().players();
        int n = 0;
        for (Player t : targets) {
            if (source.getServer().world(t.dimension()) != level) continue;
            if (ChunkLevel.particleInRange(t, force, pos[0], pos[1], pos[2])) {
                t.send(pkt);
                n++;
            }
        }
        if (n == 0) throw ERROR_FAILED.create();
        String name = p.type();
        source.sendSuccess(() -> Text.translatable("commands.particle.success", name), true);
        return n;
    }
}
