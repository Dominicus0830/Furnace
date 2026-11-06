// 바닐라 SummonCommand: /summon <엔티티> [위치] [NBT].
package org.domi.furnace.server.command.builtin;

import static org.domi.furnace.server.command.Commands.argument;
import static org.domi.furnace.server.command.Commands.literal;

import org.domi.furnace.api.text.Text;
import java.util.concurrent.CompletableFuture;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.command.EntityOps;
import org.domi.furnace.server.command.NamedSuggestionProvider;
import org.domi.furnace.server.command.SharedSuggestions;
import org.domi.furnace.server.command.arguments.CompoundTagArgument;
import org.domi.furnace.server.command.arguments.EntityTypes;
import org.domi.furnace.server.command.arguments.ResourceArgument;
import org.domi.furnace.server.command.arguments.ResourceLocationArgument;
import org.domi.furnace.server.command.arguments.Vec3Argument;
import org.domi.furnace.server.command.brigadier.CommandContext;
import org.domi.furnace.server.command.brigadier.CommandDispatcher;
import org.domi.furnace.server.command.brigadier.CommandSyntaxException;
import org.domi.furnace.server.command.brigadier.SimpleCommandExceptionType;
import org.domi.furnace.server.entity.Entities;
import org.domi.furnace.server.entity.Entity;
import org.domi.furnace.server.entity.Mob;
import org.domi.furnace.server.net.io.nbt.NbtCompound;

public final class SummonCommand {
    private SummonCommand() { }

    private static final SimpleCommandExceptionType ERROR_FAILED = new SimpleCommandExceptionType(Text.translatable("commands.summon.failed"));
    private static final SimpleCommandExceptionType ERROR_DUPLICATE_UUID = new SimpleCommandExceptionType(Text.translatable("commands.summon.failed.uuid"));
    private static final SimpleCommandExceptionType INVALID_POSITION = new SimpleCommandExceptionType(Text.translatable("commands.summon.invalidPosition"));

    /** 바닐라 SuggestionProviders.SUMMONABLE_ENTITIES — 클라이언트가 이름으로 아는 공급자. */
    public static final NamedSuggestionProvider SUMMONABLE_ENTITIES = new NamedSuggestionProvider() {
        @Override public String id() { return "minecraft:summonable_entities"; }

        @Override
        public java.util.concurrent.CompletableFuture<org.domi.furnace.server.command.brigadier.Suggestions> getSuggestions(
                CommandContext<CommandSource> c, org.domi.furnace.server.command.brigadier.SuggestionsBuilder b) {
            return SharedSuggestions.suggest(EntityTypes.all().stream().filter(SummonCommand::summonable).sorted().toList(), b);
        }
    };

    /** EntityType.canSummon: 플레이어와 낚시찌는 소환할 수 없다. */
    private static boolean summonable(String id) { return !id.equals("minecraft:player") && !id.equals("minecraft:fishing_bobber"); }

    public static void register(CommandDispatcher<CommandSource> d) {
        d.register(literal("summon").requires(s -> s.hasPermission(2))
                .then(argument("entity", ResourceArgument.resource("minecraft:entity_type")).suggests(SUMMONABLE_ENTITIES)
                        .executes(c -> summon(c, new double[] {c.getSource().x(), c.getSource().y(), c.getSource().z()}, new NbtCompound(), true))
                        .then(argument("pos", Vec3Argument.vec3())
                                .executes(c -> summon(c, Vec3Argument.getVec3(c, "pos"), new NbtCompound(), true))
                                .then(argument("nbt", CompoundTagArgument.compoundTag())
                                        .executes(c -> summon(c, Vec3Argument.getVec3(c, "pos"), CompoundTagArgument.getCompoundTag(c, "nbt"), false))))));
    }

    private static int summon(CommandContext<CommandSource> c, double[] pos, NbtCompound nbt, boolean randomizeProperties) throws CommandSyntaxException {
        CommandSource source = c.getSource();
        CompletableFuture<Entity> spawned = summonChecked(source, ResourceLocationArgument.getId(c, "entity"), pos, nbt, randomizeProperties);
        return EntityOps.result(source, EntityOps.awaitAsync(source, spawned, e -> {   // 이 람다는 전역 스레드에서 돈다
            source.sendSuccess(() -> Text.translatable("commands.summon.success", e.displayName()), true);
            return CompletableFuture.completedFuture(1);
        }));
    }

    /** 종류·위치를 검사한 뒤 소환한다(바닐라 SummonCommand.createEntity 의 앞부분). /summon 과 execute summon 이 함께 쓴다. */
    public static CompletableFuture<Entity> summonChecked(CommandSource source, org.domi.furnace.server.data.ResourceId rl, double[] pos, NbtCompound nbt,
                                                          boolean randomizeProperties) throws CommandSyntaxException {
        String id = rl.namespace() + ":" + rl.path();
        if (!EntityTypes.isKnown(id) || !summonable(id)) throw new SimpleCommandExceptionType(Text.translatable("entity.not_summonable", id)).create();
        int bx = (int) Math.floor(pos[0]), by = (int) Math.floor(pos[1]), bz = (int) Math.floor(pos[2]);
        if (by < -20000000 || by >= 20000000 || bx < -30000000 || bz < -30000000 || bx >= 30000000 || bz >= 30000000) throw INVALID_POSITION.create();
        return summonAt(source, id, pos, nbt, randomizeProperties);
    }

    /**
     * 위치의 region 에서 엔티티를 만들어 세계에 넣고 돌려준다(execute summon 도 재사용). 실패는 CommandSyntaxException 으로 끝난다. 메시지는 보내지 않는다.
     */
    public static CompletableFuture<Entity> summonAt(CommandSource source, String id, double[] pos, NbtCompound nbt, boolean randomizeProperties) {
        return EntityOps.atRegion(source.level(), pos[0], pos[2], region -> {
            var level = region.level();
            NbtCompound tag = nbt.copy();
            tag.tags().put("id", id);
            Entity e = Entities.load(level, tag);
            if (e == null) throw ERROR_FAILED.create();
            e.moveTo(pos[0], pos[1], pos[2], e.yRot(), e.xRot());
            var riders = new java.util.ArrayList<org.domi.furnace.server.entity.Rider>();
            e.collectIndirectPassengers(riders);
            for (var r : riders) if (r instanceof Entity pe) pe.moveTo(pos[0], pos[1], pos[2], pe.yRot(), pe.xRot());
            if (randomizeProperties && e instanceof Mob mob) {
                if (mob instanceof org.domi.furnace.server.entity.Villager v) v.setFinalizeReason(org.domi.furnace.server.entity.spawn.SpawnReason.COMMAND);
                // 바닐라 finalizeSpawn(level, difficulty, COMMAND, null): 무리 데이터 경로라야 말 능력치·아기 판정까지 돈다
                mob.finalizeSpawnGroup(level.random(), level.getCurrentDifficultyAt(mob.blockPosition()), null);
            }
            if (!level.addFreshEntityWithPassengers(e.spawnReason("COMMAND"))) throw ERROR_DUPLICATE_UUID.create();
            return e;
        });
    }
}
