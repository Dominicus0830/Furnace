package org.domi.furnace.compat.nms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.domi.furnace.test.FurnaceTestServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * N0 3부 태그: TagKey·HolderSet·Registry.getTag·Holder.is(TagKey)·BlockState.is·ItemStack.is. 내용은 서버가 지금 건 바닐라 데이터 팩 태그.
 * 문구 꼴(TagKey[…/…]·NamedSet(…)[…]·DirectSet[…])은 바닐라 1.21.1 awu·jq$c·jq$a 의 문자열 상수에서 옮겼다.
 */
class NmsTagsTest {

    private static FurnaceTestServer server;

    @BeforeAll
    static void start() { server = FurnaceTestServer.builder().start(); }

    @AfterAll
    static void stop() { server.close(); }

    @Test
    void tagKeyIsInternedRecord() {
        assertEquals("TagKey[minecraft:block / minecraft:logs]", BlockTags.LOGS.toString());
        assertSame(BlockTags.LOGS, TagKey.create(Registries.BLOCK, ResourceLocation.withDefaultNamespace("logs")));
        assertTrue(BlockTags.LOGS.isFor(Registries.BLOCK));
        assertFalse(BlockTags.LOGS.isFor(Registries.ITEM));
        assertTrue(BlockTags.LOGS.cast(Registries.ITEM).isEmpty());
        assertEquals(ResourceLocation.withDefaultNamespace("mineable/pickaxe"), BlockTags.MINEABLE_WITH_PICKAXE.location());
        assertEquals(ResourceLocation.withDefaultNamespace("enchantable/sword"), ItemTags.SWORD_ENCHANTABLE.location());
        assertEquals(ResourceLocation.withDefaultNamespace("dripstone_replaceable_blocks"), BlockTags.DRIPSTONE_REPLACEABLE.location(), "규칙 밖 이름(R1)");
        assertEquals(ResourceLocation.withDefaultNamespace("noteblock_top_instruments"), ItemTags.NOTE_BLOCK_TOP_INSTRUMENTS.location());
    }

    @Test
    void blockStateAndHolderAskCurrentTags() {
        assertTrue(Blocks.OAK_LOG.defaultBlockState().is(BlockTags.LOGS), "logs 는 #oak_logs 를 품는다 — 태그 안 태그가 풀려 있어야 한다");
        assertFalse(Blocks.STONE.defaultBlockState().is(BlockTags.LOGS));
        assertTrue(Blocks.STONE.defaultBlockState().is(BlockTags.MINEABLE_WITH_PICKAXE));
        assertTrue(Blocks.OAK_LOG.defaultBlockState().is(BlockTags.LOGS, s -> s.getBlock() == Blocks.OAK_LOG));
        Holder<Block> oak = Blocks.OAK_LOG.builtInRegistryHolder();
        List<TagKey<Block>> tags = oak.tags().toList();
        assertTrue(tags.contains(BlockTags.LOGS) && tags.contains(BlockTags.LOGS_THAT_BURN) && tags.contains(BlockTags.OAK_LOGS), tags.toString());
        assertSame(BlockTags.LOGS, tags.get(tags.indexOf(BlockTags.LOGS)), "같은 TagKey 객체");
        assertFalse(Holder.direct(Blocks.OAK_LOG).is(BlockTags.LOGS), "바닐라: 직접 손잡이는 태그가 없다");
        @SuppressWarnings({"unchecked", "rawtypes"})
        TagKey<Block> itemTagAsBlock = (TagKey) ItemTags.LOGS;
        assertFalse(oak.is(itemTagAsBlock), "다른 레지스트리의 태그");

        assertTrue(new ItemStack(Items.DIAMOND_SWORD).is(ItemTags.SWORDS));
        assertFalse(new ItemStack(Items.STONE).is(ItemTags.SWORDS));
        assertTrue(new ItemStack(Items.OAK_LOG).getTags().anyMatch(t -> t == ItemTags.LOGS));
    }

    @Test
    void registryTagsAreNamedHolderSets() {
        var reg = BuiltInRegistries.BLOCK;
        HolderSet.Named<Block> logs = reg.getTag(BlockTags.LOGS).orElseThrow();
        assertSame(logs, reg.getTag(BlockTags.LOGS).orElseThrow(), "같은 태그면 같은 묶음");
        assertSame(BlockTags.LOGS, logs.key());
        assertEquals(BlockTags.LOGS, logs.unwrapKey().orElseThrow());
        List<String> want = server.api().gameRegistries().tag("minecraft:block", "minecraft:logs").orElseThrow();
        assertEquals(want, logs.stream().map(h -> h.value().furnaceKey().toString()).toList(), "서버 태그 내용 그대로, 같은 순서");
        assertEquals(want.size(), logs.size());
        assertTrue(logs.contains(Blocks.OAK_LOG.builtInRegistryHolder()));
        assertFalse(logs.contains(Blocks.STONE.builtInRegistryHolder()));
        assertSame(logs.get(0), reg.getHolder(ResourceLocation.parse(want.get(0))).orElseThrow());
        assertTrue(logs.toString().startsWith("NamedSet(TagKey[minecraft:block / minecraft:logs])[[Reference{ResourceKey[minecraft:block / "), logs.toString());

        TagKey<Block> none = TagKey.create(Registries.BLOCK, ResourceLocation.withDefaultNamespace("no_such_tag"));
        assertTrue(reg.getTag(none).isEmpty());
        assertFalse(reg.getTagOrEmpty(none).iterator().hasNext());
        HolderSet.Named<Block> created = reg.getOrCreateTag(none);
        assertEquals(0, created.size(), "바닐라처럼 없는 태그는 빈 묶음으로 만든다");
        assertSame(created, reg.getTag(none).orElseThrow(), "바닐라: 만든 묶음은 태그 지도에 들어가 getTag 가 준다(PR #223 검토 R3)");
        @SuppressWarnings({"unchecked", "rawtypes"})
        TagKey<Block> itemTag = (TagKey) ItemTags.SWORDS;
        assertTrue(reg.getTag(itemTag).isEmpty(), "다른 레지스트리의 태그");
        assertTrue(reg.getTagNames().anyMatch(t -> t == BlockTags.LOGS));
        assertEquals(185, reg.getTagNames().count(), "바닐라 블록 태그 184 + 위에서 만든 빈 묶음");
        assertTrue(reg.getTagNames().anyMatch(t -> t == none));
        assertSame(want, server.api().gameRegistries().tag("minecraft:block", "minecraft:logs").orElseThrow(), "같은 태그 표면 같은 목록 객체(shim 캐시의 바탕)");
        assertTrue(BuiltInRegistries.ITEM.getTag(ItemTags.SWORDS).orElseThrow().contains(Items.DIAMOND_SWORD.builtInRegistryHolder()));

        HolderSet<Block> direct = HolderSet.direct(Blocks.STONE.builtInRegistryHolder());
        assertTrue(direct.contains(Blocks.STONE.builtInRegistryHolder()));
        assertTrue(direct.unwrapKey().isEmpty());
        assertEquals("DirectSet[[Reference{ResourceKey[minecraft:block / minecraft:stone]=Block{minecraft:stone}}]]", direct.toString());
        assertEquals(0, HolderSet.empty().size());
        assertSame(HolderSet.empty(), HolderSet.empty(), "바닐라: 상수 하나");
        assertEquals(direct, HolderSet.direct(Blocks.STONE.builtInRegistryHolder()), "바닐라 jq$a: 내용 목록으로 비교(R2)");
        assertEquals(direct.hashCode(), HolderSet.direct(Blocks.STONE.builtInRegistryHolder()).hashCode());
        assertFalse(direct.equals(HolderSet.direct(Blocks.DIRT.builtInRegistryHolder())));
    }
}
