// 바닐라 PlaySoundCommand·StopSoundCommand.
// playsound 는 Holder.direct(가변 범위 소리)로 보낸다 — 패킷에는 레지스트리 id 가 아니라 이름이 직접 실린다.
package org.domi.furnace.server.command.builtin;

import static org.domi.furnace.server.command.Commands.argument;
import static org.domi.furnace.server.command.Commands.literal;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.command.arguments.EntityArgument;
import org.domi.furnace.server.command.arguments.ResourceLocationArgument;
import org.domi.furnace.server.command.arguments.Vec3Argument;
import org.domi.furnace.server.command.brigadier.Arguments;
import org.domi.furnace.server.command.brigadier.CommandContext;
import org.domi.furnace.server.command.brigadier.CommandDispatcher;
import org.domi.furnace.server.command.brigadier.CommandSyntaxException;
import org.domi.furnace.server.command.brigadier.LiteralArgumentBuilder;
import org.domi.furnace.server.command.brigadier.SimpleCommandExceptionType;
import org.domi.furnace.server.data.ResourceId;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.net.protocol.play.SoundPacket;
import org.domi.furnace.server.net.protocol.play.StopSound;
import org.domi.furnace.server.sound.SoundSource;

public final class SoundCommands {

    private SoundCommands() {}

    /** 바닐라 SuggestionProviders.AVAILABLE_SOUNDS — 클라이언트가 이름으로 아는 공급자(소리 이름은 클라이언트 레지스트리에서). 서버에 직접 물으면 소리 레지스트리 전부. */
    public static final org.domi.furnace.server.command.NamedSuggestionProvider AVAILABLE_SOUNDS = new org.domi.furnace.server.command.NamedSuggestionProvider() {
        @Override public String id() { return "minecraft:available_sounds"; }

        @Override
        public java.util.concurrent.CompletableFuture<org.domi.furnace.server.command.brigadier.Suggestions> getSuggestions(
                CommandContext<CommandSource> c, org.domi.furnace.server.command.brigadier.SuggestionsBuilder b) {
            return org.domi.furnace.server.command.SharedSuggestions.suggest(soundNames(), b);
        }
    };

    private static volatile java.util.List<String> soundNames;

    /** builtin 레지스트리 덤프의 minecraft:sound_event 엔트리(레지스트리 순서). */
    private static java.util.List<String> soundNames() {
        java.util.List<String> l = soundNames;
        if (l == null) {
            java.util.List<String> out = new java.util.ArrayList<>();
            try (var in = SoundCommands.class.getClassLoader().getResourceAsStream("registries/builtin-1.21.1.txt")) {
                if (in != null) {
                    var r = new java.io.BufferedReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
                    String line;
                    String prefix = "minecraft:sound_event ";
                    while ((line = r.readLine()) != null) if (line.startsWith(prefix)) out.add(line.substring(prefix.length()));
                }
            } catch (java.io.IOException e) {
                // 목록을 못 읽으면 빈 제안
            }
            soundNames = l = java.util.List.copyOf(out);
        }
        return l;
    }

    private static final SimpleCommandExceptionType ERROR_TOO_FAR = new SimpleCommandExceptionType(Text.translatable("commands.playsound.failed"));

    public static void registerPlaySound(CommandDispatcher<CommandSource> d) {
        var sound = argument("sound", ResourceLocationArgument.id()).suggests(AVAILABLE_SOUNDS)
                .executes(c -> play(c, SoundSource.MASTER, caller(c.getSource()), pos(c.getSource()), 1.0F, 1.0F, 0.0F));
        for (SoundSource src : SoundSource.values()) sound.then(sourceNode(src));
        d.register(literal("playsound").requires(s -> s.hasPermission(2)).then(sound));
    }

    private static LiteralArgumentBuilder<CommandSource> sourceNode(SoundSource src) {
        return literal(src.key())
                .executes(c -> play(c, src, caller(c.getSource()), pos(c.getSource()), 1.0F, 1.0F, 0.0F))
                .then(argument("targets", EntityArgument.players())
                        .executes(c -> play(c, src, EntityArgument.getPlayers(c, "targets"), pos(c.getSource()), 1.0F, 1.0F, 0.0F))
                        .then(argument("pos", Vec3Argument.vec3())
                                .executes(c -> play(c, src, EntityArgument.getPlayers(c, "targets"), Vec3Argument.getVec3(c, "pos"), 1.0F, 1.0F, 0.0F))
                                .then(argument("volume", Arguments.floatArg(0.0F, Float.MAX_VALUE))
                                        .executes(c -> play(c, src, EntityArgument.getPlayers(c, "targets"), Vec3Argument.getVec3(c, "pos"), f(c, "volume"), 1.0F, 0.0F))
                                        .then(argument("pitch", Arguments.floatArg(0.0F, 2.0F))
                                                .executes(c -> play(c, src, EntityArgument.getPlayers(c, "targets"), Vec3Argument.getVec3(c, "pos"), f(c, "volume"), f(c, "pitch"), 0.0F))
                                                .then(argument("minVolume", Arguments.floatArg(0.0F, 1.0F))
                                                        .executes(c -> play(c, src, EntityArgument.getPlayers(c, "targets"), Vec3Argument.getVec3(c, "pos"),
                                                                f(c, "volume"), f(c, "pitch"), f(c, "minVolume"))))))));
    }

    private static float f(CommandContext<CommandSource> c, String name) { return c.getArgument(name, Float.class); }

    private static double[] pos(CommandSource s) { return new double[] {s.x(), s.y(), s.z()}; }

    private static Collection<Player> caller(CommandSource s) { return s.getPlayer() == null ? List.of() : List.of(s.getPlayer()); }

    private static int play(CommandContext<CommandSource> c, SoundSource src, Collection<Player> targets, double[] pos, float volume, float pitch,
                            float minVolume) throws CommandSyntaxException {
        return playSound(c.getSource(), targets, ResourceLocationArgument.getId(c, "sound"), src, pos, volume, pitch, minVolume, ThreadLocalRandom.current().nextLong());
    }

    /** 바닐라 playSound — 범위(볼륨>1 이면 16×볼륨, 아니면 16칸) 밖이면 minVolume 이 0 보다 클 때만 2칸 앞쪽으로 당겨 들려 준다. */
    public static int playSound(CommandSource source, Collection<Player> targets, ResourceId sound, SoundSource src, double[] pos, float volume,
                                float pitch, float minVolume, long seed) throws CommandSyntaxException {
        double range = volume > 1.0F ? 16.0 * volume : 16.0;
        double rangeSq = range * range;
        int n = 0;
        for (Player p : targets) {
            double dx = pos[0] - p.x(), dy = pos[1] - p.y(), dz = pos[2] - p.z();
            double dist = dx * dx + dy * dy + dz * dz;
            double x = pos[0], y = pos[1], z = pos[2];
            float vol = volume;
            if (dist > rangeSq) {
                if (minVolume <= 0.0F) continue;
                double r = Math.sqrt(dist);
                x = p.x() + dx / r * 2.0;
                y = p.y() + dy / r * 2.0;
                z = p.z() + dz / r * 2.0;
                vol = minVolume;
            }
            p.send(new SoundPacket(-1, sound.toString(), src.ordinal(), x, y, z, vol, pitch, seed));
            n++;
        }
        if (n == 0) throw ERROR_TOO_FAR.create();
        String name = sound.toString();
        if (targets.size() == 1) {
            Player only = targets.iterator().next();
            source.sendSuccess(() -> Text.translatable("commands.playsound.success.single", Text.text(name), only.displayName()), true);
        } else {
            int size = targets.size();
            source.sendSuccess(() -> Text.translatable("commands.playsound.success.multiple", Text.text(name), size), true);
        }
        return n;
    }

    // ------------------------------------------------------------------ stopsound

    public static void registerStopSound(CommandDispatcher<CommandSource> d) {
        var targets = argument("targets", EntityArgument.players())
                .executes(c -> stop(c.getSource(), EntityArgument.getPlayers(c, "targets"), null, null))
                .then(literal("*").then(argument("sound", ResourceLocationArgument.id()).suggests(AVAILABLE_SOUNDS)
                        .executes(c -> stop(c.getSource(), EntityArgument.getPlayers(c, "targets"), null, ResourceLocationArgument.getId(c, "sound")))));
        for (SoundSource src : SoundSource.values()) {
            targets.then(literal(src.key())
                    .executes(c -> stop(c.getSource(), EntityArgument.getPlayers(c, "targets"), src, null))
                    .then(argument("sound", ResourceLocationArgument.id()).suggests(AVAILABLE_SOUNDS)
                            .executes(c -> stop(c.getSource(), EntityArgument.getPlayers(c, "targets"), src, ResourceLocationArgument.getId(c, "sound")))));
        }
        d.register(literal("stopsound").requires(s -> s.hasPermission(2)).then(targets));
    }

    static int stop(CommandSource s, Collection<Player> targets, SoundSource src, ResourceId sound) {
        StopSound pkt = new StopSound(src == null ? -1 : src.ordinal(), sound == null ? null : sound.toString());
        for (Player p : targets) p.send(pkt);
        if (src != null) {
            if (sound != null) s.sendSuccess(() -> Text.translatable("commands.stopsound.success.source.sound", Text.text(sound.toString()), src.key()), true);
            else s.sendSuccess(() -> Text.translatable("commands.stopsound.success.source.any", src.key()), true);
        } else if (sound != null) {
            s.sendSuccess(() -> Text.translatable("commands.stopsound.success.sourceless.sound", Text.text(sound.toString())), true);
        } else {
            s.sendSuccess(() -> Text.translatable("commands.stopsound.success.sourceless.any"), true);
        }
        return targets.size();
    }
}
