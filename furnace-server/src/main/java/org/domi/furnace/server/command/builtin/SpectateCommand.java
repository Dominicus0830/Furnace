// 바닐라 SpectateCommand: /spectate [<대상> [<플레이어>]] — 관전 모드 플레이어의 시점을 엔티티로 옮기거나(대상 생략 시) 되돌린다.
// 시점은 관전자의 소유 region 스레드에서만 바꾼다(Region.setCamera). 대상의 자리는 대상의 주인 스레드에서 읽는다.
// 차이: 결과 메시지가 region 일이 끝난 뒤 전역 스레드에서 오고 반환값이 늘 1 이다(context-notes 의 "반환값 차이").
// 대상이 다른 차원이면 바닐라처럼 차원 이동으로 따라간다. 관전 모드가 아니게 되면 바닐라는 그 자리에서, 여기서는 다음 region 틱에 시점을 되돌린다.
package org.domi.furnace.server.command.builtin;

import static org.domi.furnace.server.command.Commands.argument;
import static org.domi.furnace.server.command.Commands.literal;

import java.util.concurrent.CompletableFuture;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.command.EntityOps;
import org.domi.furnace.server.command.PostMessages;
import org.domi.furnace.server.command.arguments.EntityArgument;
import org.domi.furnace.server.command.brigadier.CommandDispatcher;
import org.domi.furnace.server.command.brigadier.CommandSyntaxException;
import org.domi.furnace.server.command.brigadier.DynamicCommandExceptionType;
import org.domi.furnace.server.command.brigadier.SimpleCommandExceptionType;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.entity.Rider;
import org.domi.furnace.server.world.GameMode;

public final class SpectateCommand {
    private SpectateCommand() { }

    private static final SimpleCommandExceptionType ERROR_SELF = new SimpleCommandExceptionType(Text.translatable("commands.spectate.self"));
    private static final DynamicCommandExceptionType ERROR_NOT_SPECTATOR = new DynamicCommandExceptionType(n -> Text.translatable("commands.spectate.not_spectator", n));

    public static void register(CommandDispatcher<CommandSource> d) {
        d.register(literal("spectate").requires(s -> s.hasPermission(2))
                .executes(c -> EntityOps.result(c.getSource(), spectate(c.getSource(), null, c.getSource().getPlayerOrException())))
                .then(argument("target", EntityArgument.entity())
                        .executes(c -> EntityOps.result(c.getSource(), EntityArgument.withEntityAsync(c, "target",
                                t -> spectate(c.getSource(), t.get(0), c.getSource().getPlayerOrException()))))
                        .then(argument("player", EntityArgument.player())
                                .executes(c -> EntityOps.result(c.getSource(), EntityArgument.withEntityAsync(c, "target",
                                        t -> spectate(c.getSource(), t.get(0), EntityArgument.getPlayer(c, "player"))))))));
    }

    /** 대상의 자리 표본(대상의 주인 스레드에서 뜬다). */
    private record At(String dimension, double x, double y, double z, float yaw, float pitch) {
        static At of(Rider r) { return new At(r.dimensionKey(), r.x(), r.y(), r.z(), r.riderYRot(), r.riderXRot()); }
    }

    static CompletableFuture<Integer> spectate(CommandSource source, Rider target, Player spectator) throws CommandSyntaxException {
        if (spectator == target) throw ERROR_SELF.create();
        if (spectator.gameMode() != GameMode.SPECTATOR) throw ERROR_NOT_SPECTATOR.create(spectator.displayName());
        PostMessages post = new PostMessages();
        CompletableFuture<At> where = target == null ? CompletableFuture.completedFuture(null) : EntityOps.onOwner(source, target, At::of);
        return post.after(source, where.thenCompose(at -> {
            CompletableFuture<Integer> done = new CompletableFuture<>();
            EntityOps.runOnPlayerOwner(source, spectator, region -> {
                try {
                    // 바닐라 순서: 위치(보정 텔레포트) → 카메라 패킷 → 메시지
                    Runnable finished = () -> {
                        if (target != null) post.success(source, () -> Text.translatable("commands.spectate.success.started", target.displayName()), false);
                        else post.success(source, () -> Text.translatable("commands.spectate.success.stopped"), false);
                        done.complete(1);
                    };
                    if (at == null) region.setCamera(spectator, null, spectator.dimension(), spectator.x(), spectator.y(), spectator.z(),
                            spectator.riderYRot(), spectator.riderXRot(), finished);
                    else region.setCamera(spectator, target, at.dimension(), at.x(), at.y(), at.z(), at.yaw(), at.pitch(), finished);
                } catch (Throwable t) {
                    done.completeExceptionally(t);
                }
            }, () -> done.completeExceptionally(EntityOps.ownerNotFound(spectator)));
            return done;
        }));
    }
}
