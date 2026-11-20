package org.domi.furnace.compat.nms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.IdMap;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerRO;
import org.domi.furnace.test.FurnaceTestServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * NMS shim 5e-2: voxy 가 디스크 청크 섹션을 푸는 코덱 — {@code BlockState.CODEC}, {@code PalettedContainer.codecRW}·{@code codecRO},
 * {@code Registry.holderByNameCodec}. 기대값은 바닐라(Paper) 1.21.1 의 {@code StateHolder.codec}·{@code PalettedContainer.codec/unpack/pack} 과
 * DataFixerUpper 8.0.16 조합기의 동작(문구·부분 결과)을 바이트코드에서 옮겼다. 시험 클래스패스의 DFU 는 대역이거나 진짜(-PdfuJar)다.
 */
class NmsCodecsTest {

    private static FurnaceTestServer server;

    @BeforeAll
    static void start() { server = FurnaceTestServer.builder().start(); }

    @AfterAll
    static void stop() { server.close(); }

    static String error(DataResult<?> r) {
        assertTrue(r.isError(), "오류여야 한다");
        return r.error().get().message();
    }

    static Tag encode(Codec<?> codec, Object value) {
        @SuppressWarnings("unchecked") Codec<Object> c = (Codec<Object>) codec;
        return c.encodeStart(NbtOps.INSTANCE, value).getOrThrow(IllegalStateException::new);
    }

    static StringTag s(String v) { return StringTag.valueOf(v); }

    // ------------------------------------------------------------------ BlockState.CODEC

    @Test
    void blockStateCodecWritesNameAndPropertiesAndReadsBack() {
        BlockState stairs = Blocks.OAK_STAIRS.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST);
        CompoundTag tag = (CompoundTag) encode(BlockState.CODEC, stairs);
        assertEquals("minecraft:oak_stairs", tag.getString("Name"));
        CompoundTag props = tag.getCompound("Properties");
        assertEquals("east", props.getString("facing"));
        assertEquals("bottom", props.getString("half"));
        assertEquals("straight", props.getString("shape"));
        assertEquals("false", props.getString("waterlogged"));
        assertEquals(4, props.size());
        assertSame(stairs, BlockState.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow(IllegalStateException::new), "같은 상태 객체");

        CompoundTag stone = (CompoundTag) encode(BlockState.CODEC, Blocks.STONE.defaultBlockState());
        assertEquals("minecraft:stone", stone.getString("Name"));
        assertFalse(stone.contains("Properties"), "속성 없는 블록은 Properties 를 쓰지 않는다");
    }

    @Test
    void blockStateCodecIsLenientAboutProperties() {
        BlockState def = Blocks.OAK_STAIRS.defaultBlockState();
        CompoundTag noProps = new CompoundTag();
        noProps.putString("Name", "minecraft:oak_stairs");
        assertSame(def, BlockState.CODEC.parse(NbtOps.INSTANCE, noProps).getOrThrow(IllegalStateException::new));

        CompoundTag notMap = noProps.copy();
        notMap.putString("Properties", "facing=east");
        assertSame(def, BlockState.CODEC.parse(NbtOps.INSTANCE, notMap).getOrThrow(IllegalStateException::new), "맵이 아니면 없는 것");

        CompoundTag bad = noProps.copy();
        CompoundTag p = new CompoundTag();
        p.putString("facing", "up");          // 계단 방향에 없는 값 → 그 속성만 기본값
        p.putString("half", "top");
        bad.put("Properties", p);
        assertSame(def.setValue(BlockStateProperties.HALF, net.minecraft.world.level.block.state.properties.Half.TOP),
                BlockState.CODEC.parse(NbtOps.INSTANCE, bad).getOrThrow(IllegalStateException::new));

        CompoundTag extra = new CompoundTag();
        extra.putString("Name", "minecraft:stone");
        extra.put("Properties", p.copy());
        assertSame(Blocks.STONE.defaultBlockState(), BlockState.CODEC.parse(NbtOps.INSTANCE, extra).getOrThrow(IllegalStateException::new), "속성 없는 블록은 무시");
    }

    @Test
    void blockStateCodecErrorsLikeVanilla() {
        CompoundTag none = new CompoundTag();
        none.putString("name", "minecraft:stone");
        assertTrue(error(BlockState.CODEC.parse(NbtOps.INSTANCE, none)).startsWith("Input does not contain a key [Name]: "));

        CompoundTag unknown = new CompoundTag();
        unknown.putString("Name", "minecraft:nope");
        assertEquals("Unknown registry key in ResourceKey[minecraft:root / minecraft:block]: minecraft:nope",
                error(BlockState.CODEC.parse(NbtOps.INSTANCE, unknown)));

        CompoundTag invalid = new CompoundTag();
        invalid.putString("Name", "Bad Name");
        assertEquals("Not a valid resource location: Bad Name Non [a-z0-9/._-] character in path of location: minecraft:Bad Name",
                error(BlockState.CODEC.parse(NbtOps.INSTANCE, invalid)));

        assertTrue(error(BlockState.CODEC.parse(NbtOps.INSTANCE, s("minecraft:stone"))).startsWith("Not a map: "));
        assertFalse(BlockState.CODEC.parse(NbtOps.INSTANCE, unknown).resultOrPartial().isPresent(), "이름 오류는 부분 결과가 없다");
    }

    // ------------------------------------------------------------------ PalettedContainer.codecRW

    static Codec<PalettedContainer<BlockState>> states() {
        return PalettedContainer.codecRW(Block.BLOCK_STATE_REGISTRY, BlockState.CODEC, PalettedContainer.Strategy.SECTION_STATES, Blocks.AIR.defaultBlockState());
    }

    @Test
    void sectionStatesRoundTripLikeTheDiskFormat() {
        PalettedContainer<BlockState> c = new PalettedContainer<>(Block.BLOCK_STATE_REGISTRY, Blocks.AIR.defaultBlockState(), PalettedContainer.Strategy.SECTION_STATES);
        BlockState stairs = Blocks.OAK_STAIRS.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST);
        c.set(1, 0, 0, Blocks.STONE.defaultBlockState());
        c.set(5, 9, 3, stairs);
        CompoundTag tag = (CompoundTag) encode(states(), c);
        ListTag palette = tag.getList("palette", Tag.TAG_COMPOUND);
        assertEquals(List.of("minecraft:air", "minecraft:stone", "minecraft:oak_stairs"),
                palette.stream().map(t -> ((CompoundTag) t).getString("Name")).toList(), "처음 나온 칸 순서");
        // 3 항목 → ceillog2 2 → 배열 팔레트 설정이 4 비트 → 4096 칸 / 16 = 256 long
        assertEquals(256, ((LongArrayTag) tag.get("data")).getAsLongArray().length);

        PalettedContainer<BlockState> back = states().parse(NbtOps.INSTANCE, tag).getOrThrow(IllegalStateException::new);
        for (int i = 0; i < 4096; i++) assertSame(c.get(i), back.get(i), "칸 " + i);

        PalettedContainer<BlockState> air = new PalettedContainer<>(Block.BLOCK_STATE_REGISTRY, Blocks.AIR.defaultBlockState(), PalettedContainer.Strategy.SECTION_STATES);
        CompoundTag single = (CompoundTag) encode(states(), air);
        assertEquals(1, single.getList("palette", Tag.TAG_COMPOUND).size());
        assertFalse(single.contains("data"), "0 비트는 data 를 쓰지 않는다");
        assertSame(Blocks.AIR.defaultBlockState(), states().parse(NbtOps.INSTANCE, single).getOrThrow(IllegalStateException::new).get(7, 7, 7));
    }

    @Test
    void unknownPaletteEntryBecomesTheDefaultAsAPartialResult() {
        PalettedContainer<BlockState> c = new PalettedContainer<>(Block.BLOCK_STATE_REGISTRY, Blocks.STONE.defaultBlockState(), PalettedContainer.Strategy.SECTION_STATES);
        c.set(0, 0, 0, Blocks.DIRT.defaultBlockState());
        CompoundTag tag = (CompoundTag) encode(states(), c);
        ((CompoundTag) tag.getList("palette", Tag.TAG_COMPOUND).get(1)).putString("Name", "minecraft:gone");
        var r = states().parse(NbtOps.INSTANCE, tag);
        assertEquals("(Unknown registry key in ResourceKey[minecraft:root / minecraft:block]: minecraft:gone -> using default)", error(r));
        PalettedContainer<BlockState> partial = r.resultOrPartial().orElseThrow();
        assertSame(Blocks.DIRT.defaultBlockState(), partial.get(0, 0, 0));
        assertSame(Blocks.AIR.defaultBlockState(), partial.get(1, 0, 0), "못 읽은 항목 자리는 기본값");
    }

    /** 기대 문구는 바닐라 1.21.1 jar 의 코덱 짜임을 진짜 DFU 8.0.16 조합기로 지어 돌린 결과(PR 541 검토 재현)다: 뒤 항목부터, 뒤 단계 오류는 끝에. */
    @Test
    void severalBadPaletteEntriesJoinFromTheLastLikeVanilla() {
        PalettedContainer<BlockState> c = new PalettedContainer<>(Block.BLOCK_STATE_REGISTRY, Blocks.STONE.defaultBlockState(), PalettedContainer.Strategy.SECTION_STATES);
        c.set(0, 0, 0, Blocks.DIRT.defaultBlockState());
        CompoundTag tag = (CompoundTag) encode(states(), c);
        ListTag palette = tag.getList("palette", Tag.TAG_COMPOUND);
        ((CompoundTag) palette.get(0)).putString("Name", "minecraft:gone");
        ((CompoundTag) palette.get(1)).putString("Name", "minecraft:gone2");
        var r = states().parse(NbtOps.INSTANCE, tag);
        String key = "Unknown registry key in ResourceKey[minecraft:root / minecraft:block]: ";
        assertEquals("(" + key + "minecraft:gone2 -> using default); (" + key + "minecraft:gone -> using default)", error(r));
        assertSame(Blocks.AIR.defaultBlockState(), r.resultOrPartial().orElseThrow().get(0, 0, 0));

        tag.remove("data");   // 뒤 단계(풀기)도 틀리면 그 문구가 끝에 붙고 부분 결과는 없다
        var r2 = states().parse(NbtOps.INSTANCE, tag);
        assertEquals("(" + key + "minecraft:gone2 -> using default); (" + key + "minecraft:gone -> using default); Missing values for non-zero storage", error(r2));
        assertTrue(r2.resultOrPartial().isEmpty());
    }

    @Test
    void storageErrorsLikeVanilla() {
        CompoundTag tag = new CompoundTag();
        ListTag palette = new ListTag();
        for (String n : List.of("minecraft:stone", "minecraft:dirt")) {
            CompoundTag e = new CompoundTag();
            e.putString("Name", n);
            palette.add(e);
        }
        tag.put("palette", palette);
        assertEquals("Missing values for non-zero storage", error(states().parse(NbtOps.INSTANCE, tag)));
        tag.put("data", new LongArrayTag(new long[3]));
        assertEquals("Failed to read PalettedContainer: Invalid length given for storage, got: 3 but expected: 256", error(states().parse(NbtOps.INSTANCE, tag)));
        tag.putString("data", "x");   // 못 읽는 data 는 없는 것(lenient)
        assertEquals("Missing values for non-zero storage", error(states().parse(NbtOps.INSTANCE, tag)));
        assertTrue(error(states().parse(NbtOps.INSTANCE, new CompoundTag())).startsWith("No key palette in "));
    }

    // ------------------------------------------------------------------ 바이옴(codecRO + holderByNameCodec)

    @Test
    void biomesRoundTripIncludingTheGlobalPalette() {
        Registry<Biome> reg = RegistryAccess.SERVER.registryOrThrow(Registries.BIOME);
        IdMap<Holder<Biome>> ids = reg.asHolderIdMap();
        Holder<Biome> plains = reg.getHolderOrThrow(Biomes.PLAINS);
        Codec<PalettedContainerRO<Holder<Biome>>> codec = PalettedContainer.codecRO(ids, reg.holderByNameCodec(), PalettedContainer.Strategy.SECTION_BIOMES, plains);

        PalettedContainer<Holder<Biome>> c = new PalettedContainer<>(ids, plains, PalettedContainer.Strategy.SECTION_BIOMES);
        c.set(0, 0, 0, reg.getHolderOrThrow(Biomes.DESERT));
        CompoundTag tag = (CompoundTag) encode(codec, c);
        assertEquals(List.of("minecraft:desert", "minecraft:plains"), tag.getList("palette", Tag.TAG_STRING).stream().map(Tag::getAsString).toList());
        assertEquals(1, ((LongArrayTag) tag.get("data")).getAsLongArray().length, "64 칸 × 1 비트, 팔레트는 0번 칸부터 처음 나온 순서");
        PalettedContainerRO<Holder<Biome>> back = codec.parse(NbtOps.INSTANCE, tag).getOrThrow(IllegalStateException::new);
        assertSame(reg.getHolderOrThrow(Biomes.DESERT), back.get(0, 0, 0));
        assertSame(plains, back.get(3, 3, 3));

        // 9개 이상(4 비트 이상)이면 전역 팔레트 — 저장 꼴은 팔레트 번호, 읽으면 레지스트리 번호로 바꿔 담는다
        List<Holder.Reference<Biome>> many = reg.holders().limit(12).toList();
        PalettedContainer<Holder<Biome>> g = new PalettedContainer<>(ids, plains, PalettedContainer.Strategy.SECTION_BIOMES);
        for (int i = 0; i < many.size(); i++) g.set(i & 3, i >> 2 & 3, i >> 4, many.get(i));
        CompoundTag gt = (CompoundTag) encode(codec, g);
        PalettedContainerRO<Holder<Biome>> gb = codec.parse(NbtOps.INSTANCE, gt).getOrThrow(IllegalStateException::new);
        for (int x = 0; x < 4; x++) for (int y = 0; y < 4; y++) for (int z = 0; z < 4; z++) assertSame(g.get(x, y, z), gb.get(x, y, z));

        ListTag broken = tag.getList("palette", Tag.TAG_STRING).copy();
        broken.set(0, s("minecraft:nowhere"));   // 0번 칸의 사막 자리
        tag.put("palette", broken);
        var r = codec.parse(NbtOps.INSTANCE, tag);
        assertEquals("(Unknown registry key in ResourceKey[minecraft:root / minecraft:worldgen/biome]: minecraft:nowhere -> using default)", error(r));
        assertSame(plains, r.resultOrPartial().orElseThrow().get(0, 0, 0), "못 읽은 바이옴은 기본값(평원)");
    }

    @Test
    void holderByNameCodecRefusesAnUnregisteredHolder() {
        Registry<Biome> reg = RegistryAccess.SERVER.registryOrThrow(Registries.BIOME);
        Holder<Biome> direct = Holder.direct(reg.getHolderOrThrow(Biomes.PLAINS).value());
        String m = error(reg.holderByNameCodec().encodeStart(NbtOps.INSTANCE, direct));
        assertTrue(m.startsWith("Unregistered holder in ResourceKey[minecraft:root / minecraft:worldgen/biome]: "), m);
        assertNotNull(reg.byNameCodec());
        assertEquals("minecraft:plains", reg.holderByNameCodec().encodeStart(NbtOps.INSTANCE, reg.getHolderOrThrow(Biomes.PLAINS))
                .getOrThrow(IllegalStateException::new).getAsString());
    }
}
