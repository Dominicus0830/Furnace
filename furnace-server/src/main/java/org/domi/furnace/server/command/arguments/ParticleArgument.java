// 바닐라 ParticleArgument(minecraft:particle, 1.21.1): "<입자 id>[{SNBT 복합 태그}]" → ParticleOptions.
// id 뒤 다음 글자가 '{' 면 SNBT 복합 태그를 읽어 입자 종류의 MapCodec 으로 해석하고, 아니면 빈 태그를 쓴다.
// 필수 필드가 없거나 형식이 틀리면 particle.invalidOptions. 필드(바닐라 코덱 확인):
//   dust{color:[r,g,b],scale} / dust_color_transition{from_color,to_color,scale} (scale 0.01~4, 밖이면 오류)
//   block·block_marker·falling_dust·dust_pillar{block_state:"id" | {Name,Properties}} / item{item:"id" | {id,components}}
//   entity_effect{color:정수 ARGB | [r,g,b,a]} / sculk_charge{roll} / shriek{delay}
//   vibration{destination:{type:"block",pos:[x,y,z]}, arrival_in_ticks}
// 미지원: item 의 비어 있지 않은 components, vibration 의 entity 소스(엔티티 UUID 조회 필요) — 오류로 거절한다.
package org.domi.furnace.server.command.arguments;

import java.util.List;
import java.util.Map;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.block.Blocks;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.command.brigadier.ArgumentType;
import org.domi.furnace.server.command.brigadier.CommandContext;
import org.domi.furnace.server.command.brigadier.CommandSyntaxException;
import org.domi.furnace.server.command.brigadier.DynamicCommandExceptionType;
import org.domi.furnace.server.command.brigadier.StringReader;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtList;
import org.domi.furnace.server.net.protocol.play.ParticleOptions;

public final class ParticleArgument implements ArgumentType<ParticleOptions> {

    private static final List<String> EXAMPLES = List.of("foo", "foo:bar", "particle{foo:bar}");
    public static final DynamicCommandExceptionType ERROR_UNKNOWN_PARTICLE = new DynamicCommandExceptionType(id -> Text.translatable("particle.notFound", id));
    public static final DynamicCommandExceptionType ERROR_INVALID_OPTIONS = new DynamicCommandExceptionType(e -> Text.translatable("particle.invalidOptions", e));

    /** 코덱 오류(DataResult.error) 대신 던지는 내부 예외. */
    private static final class Bad extends Exception {
        Bad(String m) { super(m, null, false, false); }
    }

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
        NbtCompound tag;
        if (reader.canRead() && reader.peek() == '{') tag = CompoundTagArgument.compoundTag().parse(reader);
        else tag = new NbtCompound();
        try {
            return decode(id, tag);
        } catch (Bad e) {
            throw ERROR_INVALID_OPTIONS.createWithContext(reader, e.getMessage());
        }
    }

    /** 입자 종류별 MapCodec 해석. */
    static ParticleOptions decode(String id, NbtCompound t) throws Bad {
        switch (id) {
            case "minecraft:block", "minecraft:block_marker", "minecraft:falling_dust", "minecraft:dust_pillar" -> {
                return new ParticleOptions.Block(id, blockState(field(t, "block_state")));
            }
            case "minecraft:dust" -> {
                float[] c = floats(field(t, "color"), 3);
                return new ParticleOptions.Dust(c[0], c[1], c[2], scale(field(t, "scale")));
            }
            case "minecraft:dust_color_transition" -> {
                float[] a = floats(field(t, "from_color"), 3);
                float[] b = floats(field(t, "to_color"), 3);
                return new ParticleOptions.DustTransition(a[0], a[1], a[2], b[0], b[1], b[2], scale(field(t, "scale")));
            }
            case "minecraft:entity_effect" -> {
                Object c = field(t, "color");
                if (c instanceof Number n) return new ParticleOptions.Color(n.intValue());   // Codec.INT 가 먼저: 숫자 태그는 intValue
                float[] f = floats(c, 4);
                return new ParticleOptions.Color(argb(f[3], f[0], f[1], f[2]));
            }
            case "minecraft:sculk_charge" -> {
                return new ParticleOptions.SculkCharge(number(field(t, "roll")).floatValue());
            }
            case "minecraft:shriek" -> {
                return new ParticleOptions.Shriek(number(field(t, "delay")).intValue());
            }
            case "minecraft:vibration" -> {
                Object d = field(t, "destination");
                int ticks = number(field(t, "arrival_in_ticks")).intValue();
                if (!(d instanceof NbtCompound dc)) throw new Bad("Not a map: " + d);
                String type = dc.tags().get("type") instanceof String s ? s : null;
                if (type == null) throw new Bad("No key type in destination");
                if (type.indexOf(':') < 0) type = "minecraft:" + type;
                if (type.equals("minecraft:block")) {
                    int[] p = ints(field(dc, "pos"), 3);
                    return ParticleOptions.Vibration.toBlock(p[0], p[1], p[2], ticks);
                }
                if (type.equals("minecraft:entity")) throw new Bad("entity position source is not supported");
                throw new Bad("Unknown position source type: " + type);
            }
            case "minecraft:item" -> {
                return new ParticleOptions.Item(itemStack(field(t, "item")));
            }
            default -> { return new ParticleOptions.Simple(id); }
        }
    }

    private static Object field(NbtCompound t, String k) throws Bad {
        Object v = t.tags().get(k);
        if (v == null) throw new Bad("No key " + k + " in MapLike[" + t.tags().keySet() + "]");
        return v;
    }

    /** Codec.FLOAT/INT: 숫자 태그면 floatValue/intValue. */
    private static Number number(Object o) throws Bad {
        if (o instanceof Number n) return n;
        throw new Bad("Not a number: " + o);
    }

    /** ParticleOption 크기 코덱: 0.01~4 밖이면 오류. */
    private static float scale(Object o) throws Bad {
        float f = number(o).floatValue();
        if (!(f >= 0.01F && f <= 4.0F)) throw new Bad("Value must be within range [0.01;4.0]: " + f);
        return f;
    }

    /** 숫자 목록(NbtOps.getList: 리스트·정수/바이트/long 배열)을 정확히 n 개의 float 로. */
    private static float[] floats(Object o, int n) throws Bad {
        List<Object> items = listOf(o);
        if (items.size() != n) throw new Bad("Input does not have expected number of elements: " + n);
        float[] out = new float[n];
        for (int i = 0; i < n; i++) out[i] = number(items.get(i)).floatValue();
        return out;
    }

    private static int[] ints(Object o, int n) throws Bad {
        List<Object> items = listOf(o);
        if (items.size() != n) throw new Bad("Input does not have expected number of elements: " + n);
        int[] out = new int[n];
        for (int i = 0; i < n; i++) out[i] = number(items.get(i)).intValue();
        return out;
    }

    private static List<Object> listOf(Object o) throws Bad {
        if (o instanceof NbtList l) return l.items();
        if (o instanceof int[] a) return java.util.Arrays.stream(a).boxed().map(i -> (Object) i).toList();
        if (o instanceof long[] a) return java.util.Arrays.stream(a).boxed().map(i -> (Object) i).toList();
        if (o instanceof byte[] a) {
            var l = new java.util.ArrayList<Object>();
            for (byte b : a) l.add(b);
            return l;
        }
        throw new Bad("Not a list: " + o);
    }

    /** 블록 상태 코덱: 문자열 블록 id(기본 상태) 또는 {Name, Properties}. */
    private static int blockState(Object o) throws Bad {
        Blocks blocks = Blocks.get();
        if (o instanceof String s) return blockDefault(blocks, s);
        if (!(o instanceof NbtCompound c)) throw new Bad("Not a block state: " + o);
        if (!(c.tags().get("Name") instanceof String name)) throw new Bad("No key Name in MapLike[" + c.tags().keySet() + "]");
        String full = name.indexOf(':') < 0 ? "minecraft:" + name : name;
        int state = blockDefault(blocks, full);
        Object props = c.tags().get("Properties");
        if (props == null) return state;
        if (!(props instanceof NbtCompound pc)) throw new Bad("Not a map: " + props);
        for (Map.Entry<String, Object> e : pc.tags().entrySet()) {
            if (!(e.getValue() instanceof String v)) throw new Bad("Not a string: " + e.getValue());
            var prop = BlockStateArgument.propOf(e.getKey(), state);
            if (prop == null) throw new Bad("Unknown property '" + e.getKey() + "' for " + full);
            if (prop.indexOf(state, v) < 0) throw new Bad("Unable to read property: " + e.getKey() + " with value: " + v);
            state = prop.with(state, v);
        }
        return state;
    }

    private static int blockDefault(Blocks blocks, String id) throws Bad {
        String full = id.indexOf(':') < 0 ? "minecraft:" + id : id;
        int b = blocks.block(full);
        if (b < 0) throw new Bad("Unknown registry key in minecraft:block: " + full);
        return blocks.defaultState(b);
    }

    /** 아이템 코덱: 문자열 아이템 id 또는 {id, components}. 개수는 1. 빈 스택(air)은 바닐라처럼 허용하지 않는다. */
    private static ItemStack itemStack(Object o) throws Bad {
        String id;
        if (o instanceof String s) id = s;
        else if (o instanceof NbtCompound c) {
            if (!(c.tags().get("id") instanceof String s)) throw new Bad("No key id in MapLike[" + c.tags().keySet() + "]");
            id = s;
            if (c.tags().get("components") instanceof NbtCompound cc && !cc.tags().isEmpty()) throw new Bad("item components are not supported");
        } else throw new Bad("Not an item: " + o);
        String full = id.indexOf(':') < 0 ? "minecraft:" + id : id;
        Integer iid = ItemContext.installed() ? ItemContext.id("minecraft:item", full) : null;
        if (iid == null) throw new Bad("Unknown registry key in minecraft:item: " + full);
        if (full.equals("minecraft:air")) throw new Bad("Empty ItemStack not allowed");
        return new ItemStack(iid, 1);
    }

    /** ARGB.color(colorFromFloat(a), r, g, b): 채널은 floor(값×255), 마스크 없이 이어 붙인다(바닐라 그대로). */
    static int argb(float a, float r, float g, float b) {
        return fl(a) << 24 | fl(r) << 16 | fl(g) << 8 | fl(b);
    }

    private static int fl(float v) {
        float x = v * 255.0F;
        int i = (int) x;
        return x < i ? i - 1 : i;
    }

    @Override
    public <S> java.util.concurrent.CompletableFuture<org.domi.furnace.server.command.brigadier.Suggestions> listSuggestions(
            CommandContext<S> context, org.domi.furnace.server.command.brigadier.SuggestionsBuilder builder) {
        return org.domi.furnace.server.command.SharedSuggestions.suggest(ParticleOptions.Registry.names(), builder);
    }

    @Override public List<String> getExamples() { return EXAMPLES; }

    @Override public String networkId() { return "minecraft:particle"; }
}
