// 바닐라 ResourceOrIdArgument(minecraft:loot_table · loot_predicate · loot_modifier) — 등록된 id 또는 인라인 SNBT 정의. 속성 없음.
// parseInlineOrId: SNBT 값을 읽어 인자 끝까지 먹었으면 그 태그, 아니면 처음으로 돌아가 id, 그래도 안 되면 argument.resource_or_id.invalid.
// 차이: 바닐라는 파싱 때 레지스트리에서 찾고 실패하면 argument.resource_or_id.failed_to_parse 를 낸다. 여기서는 데이터 팩을 실행 때 보므로
// 같은 오류를 실행 때 낸다(메시지 문구는 바닐라 RegistryFileCodec 꼴).
package org.domi.furnace.server.command.arguments;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.Collection;
import java.util.List;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.command.brigadier.ArgumentType;
import org.domi.furnace.server.command.brigadier.CommandContext;
import org.domi.furnace.server.command.brigadier.CommandSyntaxException;
import org.domi.furnace.server.command.brigadier.DynamicCommandExceptionType;
import org.domi.furnace.server.command.brigadier.StringReader;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtList;

public final class ResourceOrIdArgument implements ArgumentType<ResourceOrIdArgument.Ref> {

    public static final DynamicCommandExceptionType ERROR_FAILED_TO_PARSE =
            new DynamicCommandExceptionType(m -> Text.translatable("argument.resource_or_id.failed_to_parse", m));
    private static final List<String> EXAMPLES = List.of("foo", "foo:bar", "012", "{}", "true");

    /** id 또는 인라인 정의(JSON 꼴로 바꾼 것). 둘 중 하나만 있다. */
    public record Ref(String id, JsonElement inline) {}

    private final String networkId;
    private final String registry;

    private ResourceOrIdArgument(String networkId, String registry) {
        this.networkId = networkId;
        this.registry = registry;
    }

    public static ResourceOrIdArgument lootTable() { return new ResourceOrIdArgument("minecraft:loot_table", "minecraft:loot_table"); }

    public static ResourceOrIdArgument lootModifier() { return new ResourceOrIdArgument("minecraft:loot_modifier", "minecraft:item_modifier"); }

    public static ResourceOrIdArgument lootPredicate() { return new ResourceOrIdArgument("minecraft:loot_predicate", "minecraft:predicate"); }

    public static Ref get(CommandContext<CommandSource> ctx, String name) { return ctx.getArgument(name, Ref.class); }

    /** 등록된 원소를 찾지 못했을 때(실행 때). */
    public static CommandSyntaxException missing(String registry, String id) {
        return ERROR_FAILED_TO_PARSE.create("Failed to get element ResourceKey[" + registry + " / " + id + "]");
    }

    public String registry() { return registry; }

    public static final org.domi.furnace.server.command.brigadier.SimpleCommandExceptionType ERROR_INVALID =
            new org.domi.furnace.server.command.brigadier.SimpleCommandExceptionType(Text.translatable("argument.resource_or_id.invalid"));

    private static boolean atArgEnd(StringReader r) { return !r.canRead() || r.peek() == ' '; }

    @Override
    public Ref parse(StringReader reader) throws CommandSyntaxException {
        int start = reader.getCursor();
        Object tag = new TagParser(reader).readValue();
        if (!atArgEnd(reader)) {
            reader.setCursor(start);
            var id = ResourceLocationArgument.id().parse(reader);
            if (!atArgEnd(reader)) {
                reader.setCursor(start);
                throw ERROR_INVALID.createWithContext(reader);
            }
            return new Ref(id.toString(), null);
        }
        if (tag instanceof String s) {
            // 문자열 태그는 id(ResourceLocation 코덱) — 잘못된 id 면 파싱 실패
            if (!ResourceIdCheck.valid(s)) throw ERROR_FAILED_TO_PARSE.createWithContext(reader, "Not a valid resource location: " + s);
            return new Ref(s.indexOf(':') < 0 ? "minecraft:" + s : s, null);
        }
        return new Ref(null, toJson(tag));
    }

    /** NbtOps → JsonOps 변환(바닐라 Dynamic.convert 꼴). */
    static JsonElement toJson(Object tag) {
        return switch (tag) {
            case NbtCompound c -> {
                JsonObject o = new JsonObject();
                for (var e : c.tags().entrySet()) o.add(e.getKey(), toJson(e.getValue()));
                yield o;
            }
            case NbtList l -> {
                JsonArray a = new JsonArray();
                for (Object v : l.items()) a.add(toJson(v));
                yield a;
            }
            case byte[] b -> { JsonArray a = new JsonArray(); for (byte x : b) a.add(x); yield a; }
            case int[] b -> { JsonArray a = new JsonArray(); for (int x : b) a.add(x); yield a; }
            case long[] b -> { JsonArray a = new JsonArray(); for (long x : b) a.add(x); yield a; }
            case String s -> new JsonPrimitive(s);
            case Number n -> new JsonPrimitive(n);
            default -> throw new IllegalArgumentException("NBT 값이 아님: " + tag);
        };
    }

    /** ResourceLocation 글자 규칙. */
    static final class ResourceIdCheck {
        static boolean valid(String s) {
            int i = s.indexOf(':');
            String ns = i < 0 ? "minecraft" : s.substring(0, i), path = i < 0 ? s : s.substring(i + 1);
            for (char c : ns.toCharArray()) if (!(c == '_' || c == '-' || c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '.')) return false;
            for (char c : path.toCharArray()) if (!(c == '_' || c == '-' || c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '/' || c == '.')) return false;
            return true;
        }
    }

    @Override
    public Collection<String> getExamples() { return EXAMPLES; }

    @Override
    public String networkId() { return networkId; }
}
