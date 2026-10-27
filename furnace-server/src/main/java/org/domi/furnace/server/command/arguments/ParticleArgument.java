// 바닐라 ParticleArgument(minecraft:particle): "<입자 id>[ 옵션…]" → ParticleOptions.
// 옵션 문법: block 류 ' 블록상태', dust ' r g b scale', dust_color_transition ' r g b scale r g b', entity_effect ' r g b a'(가정),
// sculk_charge ' roll', shriek ' 지연', vibration ' x y z 도착틱', item ' 아이템id'(컴포넌트 없음 — 가정).
package org.domi.furnace.server.command.arguments;

import java.util.List;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.command.brigadier.ArgumentType;
import org.domi.furnace.server.command.brigadier.CommandContext;
import org.domi.furnace.server.command.brigadier.CommandSyntaxException;
import org.domi.furnace.server.command.brigadier.DynamicCommandExceptionType;
import org.domi.furnace.server.command.brigadier.StringReader;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.net.protocol.play.ParticleOptions;

public final class ParticleArgument implements ArgumentType<ParticleOptions> {

    private static final List<String> EXAMPLES = List.of("foo", "foo:bar", "particle{foo:bar}");
    public static final DynamicCommandExceptionType ERROR_UNKNOWN_PARTICLE = new DynamicCommandExceptionType(id -> Text.translatable("particle.notFound", id));
    private static final DynamicCommandExceptionType ERROR_UNKNOWN_ITEM = new DynamicCommandExceptionType(id -> Text.translatable("argument.item.id.invalid", id));

    public static ParticleArgument particle() { return new ParticleArgument(); }

    public static ParticleOptions getParticle(CommandContext<CommandSource> ctx, String name) { return ctx.getArgument(name, ParticleOptions.class); }

    @Override
    public ParticleOptions parse(StringReader reader) throws CommandSyntaxException {
        int start = reader.getCursor();
        String id = ResourceLocationArgument.id().parse(reader).toString();
        if (!ParticleOptions.Registry.has(id)) {
            reader.setCursor(start);
            throw ERROR_UNKNOWN_PARTICLE.createWithContext(reader, id);
        }
        switch (id) {
            case "minecraft:block", "minecraft:block_marker", "minecraft:falling_dust", "minecraft:dust_pillar" -> {
                reader.expect(' ');
                return new ParticleOptions.Block(id, BlockStateArgument.parse(reader, false, false).state());
            }
            case "minecraft:dust" -> {
                float[] c = color(reader);
                reader.expect(' ');
                return new ParticleOptions.Dust(c[0], c[1], c[2], reader.readFloat());
            }
            case "minecraft:dust_color_transition" -> {
                float[] a = color(reader);
                reader.expect(' ');
                float scale = reader.readFloat();
                float[] b = color(reader);
                return new ParticleOptions.DustTransition(a[0], a[1], a[2], b[0], b[1], b[2], scale);
            }
            case "minecraft:entity_effect" -> {
                float[] c = color(reader);
                reader.expect(' ');
                float a = reader.readFloat();
                return new ParticleOptions.Color(argb(a, c[0], c[1], c[2]));
            }
            case "minecraft:sculk_charge" -> {
                reader.expect(' ');
                return new ParticleOptions.SculkCharge(reader.readFloat());
            }
            case "minecraft:shriek" -> {
                reader.expect(' ');
                return new ParticleOptions.Shriek(reader.readInt());
            }
            case "minecraft:vibration" -> {
                reader.expect(' ');
                double x = reader.readDouble();
                reader.expect(' ');
                double y = reader.readDouble();
                reader.expect(' ');
                double z = reader.readDouble();
                reader.expect(' ');
                return ParticleOptions.Vibration.toBlock((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z), reader.readInt());
            }
            case "minecraft:item" -> {
                reader.expect(' ');
                int s = reader.getCursor();
                String item = ResourceLocationArgument.id().parse(reader).toString();
                Integer iid = ItemContext.installed() ? ItemContext.id("minecraft:item", item) : null;
                if (iid == null) {
                    reader.setCursor(s);
                    throw ERROR_UNKNOWN_ITEM.createWithContext(reader, item);
                }
                return new ParticleOptions.Item(new ItemStack(iid, 1));
            }
            default -> { return new ParticleOptions.Simple(id); }
        }
    }

    private static float[] color(StringReader reader) throws CommandSyntaxException {
        reader.expect(' ');
        float r = reader.readFloat();
        reader.expect(' ');
        float g = reader.readFloat();
        reader.expect(' ');
        return new float[] {r, g, reader.readFloat()};
    }

    /** ARGB.colorFromFloat. */
    static int argb(float a, float r, float g, float b) {
        return (Math.round(a * 255) & 255) << 24 | (Math.round(r * 255) & 255) << 16 | (Math.round(g * 255) & 255) << 8 | (Math.round(b * 255) & 255);
    }

    @Override
    public <S> java.util.concurrent.CompletableFuture<org.domi.furnace.server.command.brigadier.Suggestions> listSuggestions(
            CommandContext<S> context, org.domi.furnace.server.command.brigadier.SuggestionsBuilder builder) {
        return org.domi.furnace.server.command.SharedSuggestions.suggest(ParticleOptions.Registry.names(), builder);
    }

    @Override public List<String> getExamples() { return EXAMPLES; }

    @Override public String networkId() { return "minecraft:particle"; }
}
