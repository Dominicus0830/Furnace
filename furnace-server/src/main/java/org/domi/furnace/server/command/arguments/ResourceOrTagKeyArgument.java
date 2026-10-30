// 바닐라 ResourceOrTagKeyArgument(minecraft:resource_or_tag_key) — "#태그" 또는 id. 속성: 레지스트리 이름.
package org.domi.furnace.server.command.arguments;

import io.netty.buffer.ByteBuf;
import java.util.Collection;
import java.util.List;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.command.brigadier.ArgumentType;
import org.domi.furnace.server.command.brigadier.CommandContext;
import org.domi.furnace.server.command.brigadier.CommandSyntaxException;
import org.domi.furnace.server.command.brigadier.StringReader;
import org.domi.furnace.server.data.ResourceId;
import org.domi.furnace.server.net.io.Types;

public final class ResourceOrTagKeyArgument implements ArgumentType<ResourceOrTagKeyArgument.Result> {

    /** id 또는 태그(tag=true 면 id 가 태그 이름). */
    public record Result(ResourceId id, boolean tag) {
        /** 바닐라 asPrintable: 태그는 "#" 를 붙인다. */
        public String printable() { return tag ? "#" + id : id.toString(); }
    }

    private final String registry;
    private final String networkId;

    private ResourceOrTagKeyArgument(String registry, String networkId) { this.registry = registry; this.networkId = networkId; }

    /** 바닐라 ResourceOrTagArgument(minecraft:resource_or_tag): 파싱은 같고 네트워크 id 만 다르다. */
    public static ResourceOrTagKeyArgument resourceOrTag(String registry) { return new ResourceOrTagKeyArgument(registry, "minecraft:resource_or_tag"); }

    public static ResourceOrTagKeyArgument resourceOrTagKey(String registry) { return new ResourceOrTagKeyArgument(registry, "minecraft:resource_or_tag_key"); }

    public static Result get(CommandContext<CommandSource> ctx, String name) { return ctx.getArgument(name, Result.class); }

    @Override
    public Result parse(StringReader reader) throws CommandSyntaxException {
        if (reader.canRead() && reader.peek() == '#') {
            int cursor = reader.getCursor();
            try {
                reader.skip();
                return new Result(ResourceLocationArgument.id().parse(reader), true);
            } catch (CommandSyntaxException e) {
                reader.setCursor(cursor);
                throw e;
            }
        }
        return new Result(ResourceLocationArgument.id().parse(reader), false);
    }

    private static final org.domi.furnace.server.command.brigadier.Dynamic2CommandExceptionType ERROR_UNKNOWN_RESOURCE =
            new org.domi.furnace.server.command.brigadier.Dynamic2CommandExceptionType((id, reg) -> org.domi.furnace.api.text.Text.translatable("argument.resource.not_found", id, reg));
    private static final org.domi.furnace.server.command.brigadier.Dynamic2CommandExceptionType ERROR_UNKNOWN_TAG =
            new org.domi.furnace.server.command.brigadier.Dynamic2CommandExceptionType((id, reg) -> org.domi.furnace.api.text.Text.translatable("argument.resource_tag.not_found", id, reg));

    /**
     * 바닐라는 파싱 때 레지스트리에서 찾고 없으면 위치가 붙은 오류를 낸다. 데이터 팩(레지스트리 목록)이 있으면 같게 파싱 때 검증하고,
     * 없으면(시험·기동 전) 검증하지 않는다 — 각 명령이 실행 때 따로 거른다.
     */
    @Override
    public <S> Result parse(StringReader reader, S source) throws CommandSyntaxException {
        Result r = parse(reader);
        if (source instanceof CommandSource cs && cs.getServer().dataPacks() != null && cs.getServer().dataPacks().loot() != null) {
            var regs = cs.getServer().dataPacks().loot().registries();
            List<String> names = regs.entries(registry);
            if (!names.isEmpty()) {
                String name = r.id().namespace() + ":" + r.id().path();
                if (r.tag() ? !regs.hasTag(registry, name) : !names.contains(name)) {
                    throw (r.tag() ? ERROR_UNKNOWN_TAG : ERROR_UNKNOWN_RESOURCE).createWithContext(reader, name, registry);
                }
            }
        }
        return r;
    }

    /**
     * 바닐라 제안(ResourceOrTagArgument.listSuggestions, ResourceOrTagKeyArgument → CommandSourceStack.suggestRegistryElements(ALL)):
     * 태그를 "#" 접두로, 그다음 엔트리를 suggestResource 로. 레지스트리가 없으면 빈 제안. 클라이언트가 레지스트리를 모르면
     * (worldgen/structure 등) 서버에 묻는다 — 그 응답이 이것이다.
     */
    @Override
    public <S> java.util.concurrent.CompletableFuture<org.domi.furnace.server.command.brigadier.Suggestions> listSuggestions(
            CommandContext<S> context, org.domi.furnace.server.command.brigadier.SuggestionsBuilder builder) {
        if (!(context.getSource() instanceof CommandSource cs)) return builder.buildFuture();
        List<ResourceId> tags = new java.util.ArrayList<>(), elements = new java.util.ArrayList<>();
        if (registry.equals("minecraft:worldgen/structure")) {
            for (String t : org.domi.furnace.server.world.gen.structure.StructureRegistry.STRUCTURE_TAG_NAMES) tags.add(ResourceId.parse("minecraft:" + t));
            for (String e : org.domi.furnace.server.world.gen.structure.StructureRegistry.structureJsons().keySet()) elements.add(ResourceId.parse(e));
        } else {
            var dp = cs.getServer() == null ? null : cs.getServer().dataPacks();
            if (dp == null || dp.loot() == null) return org.domi.furnace.server.command.brigadier.Suggestions.empty();
            var regs = dp.loot().registries();
            List<String> names = regs.entries(registry);
            if (names.isEmpty()) return org.domi.furnace.server.command.brigadier.Suggestions.empty();
            for (String t : regs.tagNames(registry)) tags.add(ResourceId.parse(t));
            for (String e : names) elements.add(ResourceId.parse(e));
        }
        org.domi.furnace.server.command.SharedSuggestions.suggestResource(tags, builder, "#");
        return org.domi.furnace.server.command.SharedSuggestions.suggestResource(elements, builder);
    }

    @Override
    public Collection<String> getExamples() { return List.of("foo", "foo:bar", "012", "#skeletons", "#minecraft:skeletons"); }

    @Override
    public String networkId() { return networkId; }

    @Override
    public void writeProperties(ByteBuf buf) { Types.writeString(buf, registry); }
}
