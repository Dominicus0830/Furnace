package net.minecraft.world.level.chunk;

import java.util.function.Predicate;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.domi.furnace.compat.nms.NmsChunks;

/**
 * 청크 섹션 shim(Mojang 이름). 두 가지가 있다.
 * <ul>
 * <li><b>떨어진 섹션</b>(N2b-1): 생성자로 만든 것. 바닐라 1.21.1 포팅 그대로 — 필드 {@code nonEmptyBlockCount}·{@code tickingBlockCount}·
 *     {@code tickingFluidCount}(short)·{@code states}·{@code biomes} 와 개수 규칙 두 가지를 특이점까지 따른다:
 *     {@link #setBlockState} 는 칸마다 ±1(유체는 <b>비어 있지 않으면 물도</b> tickingFluid), {@link #recalcBlockCounts}(생성자)는 유체가 있는 칸을
 *     nonEmpty 에 <b>두 번</b> 세고 tickingFluid 는 <b>무작위 틱 유체(용암)만</b> 센다. 그래서 다시 센 섹션에서 물을 지우면 tickingFluid 가 0 아래로 간다.
 *     서버 청크와 무관하다(청크 섹션 배열에 넣어도 아직 서버에 닿지 않는다 — 들여오기는 N2b-4).</li>
 * <li><b>보기</b>(N2a, 로드된 청크의 {@code getSections()}): 읽을 때마다 서버 청크의 지금 값을 본다(소유 region 스레드 전용). 쓰기(setBlockState·read)는
 *     UnsupportedNmsCall(살아 있는 섹션 쓰기는 N2b-2). 개수 필드는 0 으로 남는다(보기의 바닐라 개수 보관은 N2b-2·4).</li>
 * </ul>
 * 칸 좌표는 섹션 안(0~15). 좌표를 자르지 않는 것도 바닐라와 같다.
 */
public class LevelChunkSection {

    public static final int SECTION_WIDTH = 16;
    public static final int SECTION_HEIGHT = 16;
    public static final int SECTION_SIZE = 4096;
    public static final int BIOME_CONTAINER_BITS = 2;

    private short nonEmptyBlockCount;
    private short tickingBlockCount;
    private short tickingFluidCount;
    private final PalettedContainer<BlockState> states;
    private PalettedContainerRO<Holder<Biome>> biomes;
    /** Furnace: 보기면 그 청크(강한 참조 — 섹션을 쥐면 같은 섹션 배열이 유지된다), 떨어진 섹션이면 null. */
    private final ChunkAccess furnaceChunk;
    private final int furnaceIndex;

    /** 바닐라: 주어진 컨테이너로 만들고 개수를 다시 센다(recalcBlockCounts). 컨테이너를 복사하지 않는다. */
    public LevelChunkSection(PalettedContainer<BlockState> states, PalettedContainerRO<Holder<Biome>> biomes) {
        this.states = states;
        this.biomes = biomes;
        this.furnaceChunk = null;
        this.furnaceIndex = -1;
        recalcBlockCounts();
    }

    /** 바닐라: 공기로 채운 섹션, 바이옴은 그 레지스트리의 plains. 개수는 0. */
    public LevelChunkSection(Registry<Biome> biomeRegistry) {
        this.states = new PalettedContainer<>(Block.BLOCK_STATE_REGISTRY, Blocks.AIR.defaultBlockState(), PalettedContainer.Strategy.SECTION_STATES);
        this.biomes = new PalettedContainer<>(biomeRegistry.asHolderIdMap(),
                biomeRegistry.getHolderOrThrow(ResourceKey.create(Registries.BIOME, ResourceLocation.withDefaultNamespace("plains"))),
                PalettedContainer.Strategy.SECTION_BIOMES);
        this.furnaceChunk = null;
        this.furnaceIndex = -1;
    }

    private LevelChunkSection(ChunkAccess chunk, int index) {
        this.furnaceChunk = chunk;
        this.furnaceIndex = index;
        var pos = chunk.getPos();
        int bx = pos.x << 4, bz = pos.z << 4;
        this.states = PalettedContainer.furnaceView(Block.BLOCK_STATE_REGISTRY, PalettedContainer.Strategy.SECTION_STATES,
                i -> chunk.furnaceStateIdAt(bx + (i & 15), baseY() + (i >> 8), bz + (i >> 4 & 15)),
                () -> chunk.furnaceSectionStates(index));
        this.biomes = PalettedContainer.furnaceView(NmsChunks.BIOME_IDS, PalettedContainer.Strategy.SECTION_BIOMES,
                i -> chunk.furnaceSectionBiomes(index)[i],
                () -> chunk.furnaceSectionBiomes(index));
    }

    /** 호환 모듈 전용: 청크의 index 번째(0 = 맨 아래) 섹션 보기. 청크 shim 이 한 번만 만든다. */
    public static LevelChunkSection furnaceView(ChunkAccess chunk, int index) { return new LevelChunkSection(chunk, index); }

    /** Furnace: 보기면 이 섹션의 번호(0 = 맨 아래), 떨어진 섹션이면 -1. */
    public int furnaceIndex() { return furnaceIndex; }

    /** Furnace: 서버 청크의 보기인가(아니면 떨어진 섹션). */
    public boolean furnaceIsView() { return furnaceChunk != null; }

    private int baseY() { return furnaceChunk.getMinBuildHeight() + (furnaceIndex << 4); }

    private RuntimeException viewWrite(String what) {
        return new org.domi.furnace.compat.nms.UnsupportedNmsCall("LevelChunkSection#" + what + " (청크 섹션 보기)",
                "로드된 청크 섹션을 직접 고치는 길은 아직 없다(N2b-2). 블록은 Level#setBlock·LevelChunk#setBlockState 로 바꾸고, 새로 만든 섹션은 고칠 수 있다");
    }

    public BlockState getBlockState(int x, int y, int z) { return states.get(x, y, z); }

    public FluidState getFluidState(int x, int y, int z) { return states.get(x, y, z).getFluidState(); }

    /** 바닐라: 블록 컨테이너의 디버그 잠금(아무것도 하지 않는다 — PalettedContainer#acquire). */
    public void acquire() { states.acquire(); }

    public void release() { states.release(); }

    public BlockState setBlockState(int x, int y, int z, BlockState state) { return setBlockState(x, y, z, state, true); }

    /** 바닐라: 칸 하나를 바꾸고 개수를 ±1(유체는 비어 있지 않으면 모두 — 물도). 그 밖 부수 효과는 없다. */
    public BlockState setBlockState(int x, int y, int z, BlockState state, boolean useLocks) {
        if (furnaceChunk != null) throw viewWrite("setBlockState");
        BlockState old = useLocks ? states.getAndSet(x, y, z, state) : states.getAndSetUnchecked(x, y, z, state);
        FluidState oldFluid = old.getFluidState();
        FluidState newFluid = state.getFluidState();
        if (!old.isAir()) {
            nonEmptyBlockCount--;
            if (old.isRandomlyTicking()) tickingBlockCount--;
        }
        if (!oldFluid.isEmpty()) tickingFluidCount--;
        if (!state.isAir()) {
            nonEmptyBlockCount++;
            if (state.isRandomlyTicking()) tickingBlockCount++;
        }
        if (!newFluid.isEmpty()) tickingFluidCount++;
        return old;
    }

    /** 공기 아닌 칸이 없는가. 떨어진 섹션은 바닐라 nonEmptyBlockCount == 0, 보기는 서버의 공기 아닌 칸 수. */
    public boolean hasOnlyAir() {
        if (furnaceChunk != null) return furnaceChunk.furnaceSectionNonAir(furnaceIndex) == 0;
        return nonEmptyBlockCount == 0;
    }

    /** 바닐라: 블록 또는 유체 쪽이 무작위 틱 대상인가. 보기는 서버 섹션의 판정. */
    public boolean isRandomlyTicking() {
        if (furnaceChunk != null) return furnaceChunk.furnaceSectionRandomlyTicking(furnaceIndex);
        return isRandomlyTickingBlocks() || isRandomlyTickingFluids();
    }

    /** 바닐라: tickingBlockCount > 0. 보기는 서버 섹션이 가진 바닐라 개수로 답한다. */
    public boolean isRandomlyTickingBlocks() {
        if (furnaceChunk != null) return (furnaceChunk.furnaceSectionRandomTickingParts(furnaceIndex) & 1) != 0;
        return tickingBlockCount > 0;
    }

    /**
     * 바닐라: tickingFluidCount > 0(음수도 "아니오"). 보기는 서버 섹션이 가진 바닐라 개수로 답한다 — 읽은 섹션은 용암만, 칸 쓰기로 놓은 물도 센다(PR #358).
     */
    public boolean isRandomlyTickingFluids() {
        if (furnaceChunk != null) return (furnaceChunk.furnaceSectionRandomTickingParts(furnaceIndex) & 2) != 0;
        return tickingFluidCount > 0;
    }

    /**
     * 바닐라 그대로 다시 센다: 공기 아닌 칸은 nonEmpty(무작위 틱 블록이면 tickingBlock 도), <b>유체가 있는 칸은 nonEmpty 를 한 번 더</b>,
     * 유체가 무작위 틱(용암)이면 tickingFluid. 보기는 서버가 늘 정확한 수를 가져 할 일이 없다.
     */
    public void recalcBlockCounts() {
        if (furnaceChunk != null) return;
        int[] c = count(states);
        nonEmptyBlockCount = (short) c[0];
        tickingBlockCount = (short) c[1];
        tickingFluidCount = (short) c[2];
    }

    private static int[] count(PalettedContainer<BlockState> states) {
        int[] c = new int[3];
        states.count((state, n) -> {
            FluidState fluid = state.getFluidState();
            if (!state.isAir()) {
                c[0] += n;
                if (state.isRandomlyTicking()) c[1] += n;
            }
            if (!fluid.isEmpty()) {
                c[0] += n;
                if (fluid.isRandomlyTicking()) c[2] += n;
            }
        });
        return c;
    }

    /** 블록 상태 컨테이너(떨어진 섹션은 진짜, 보기는 읽기 전용 보기 — 늘 같은 객체). */
    public PalettedContainer<BlockState> getStates() { return states; }

    /** 바이옴 컨테이너(4×4×4). */
    public PalettedContainerRO<Holder<Biome>> getBiomes() { return biomes; }

    /** 바닐라: nonEmptyBlockCount(short) + 블록 컨테이너 + 바이옴 컨테이너(바이옴은 recreate 한 새 컨테이너에 읽어 갈아 끼운다). */
    public void read(FriendlyByteBuf buf) {
        if (furnaceChunk != null) throw viewWrite("read");
        nonEmptyBlockCount = buf.readShort();
        states.read(buf);
        PalettedContainer<Holder<Biome>> b = biomes.recreate();
        b.read(buf);
        biomes = b;
    }

    public void readBiomes(FriendlyByteBuf buf) {
        if (furnaceChunk != null) throw viewWrite("readBiomes");
        PalettedContainer<Holder<Biome>> b = biomes.recreate();
        b.read(buf);
        biomes = b;
    }

    /** 바닐라 직렬화. 보기는 nonEmpty 에 지금 칸을 다시 센 값(디스크에서 읽은 섹션과 같은 값)을 쓴다. */
    public void write(FriendlyByteBuf buf) {
        buf.writeShort(furnaceChunk != null ? count(states)[0] : nonEmptyBlockCount);
        states.write(buf);
        biomes.write(buf);
    }

    public int getSerializedSize() { return 2 + states.getSerializedSize() + biomes.getSerializedSize(); }

    public boolean maybeHas(Predicate<BlockState> predicate) { return states.maybeHas(predicate); }

    /** 섹션 안 바이옴 칸(0~3) 의 바이옴. */
    public Holder<Biome> getNoiseBiome(int x, int y, int z) { return biomes.get(x, y, z); }

    @Override
    public String toString() {
        return furnaceChunk != null ? "LevelChunkSection[" + furnaceChunk.getPos() + ", " + furnaceIndex + "]" : super.toString();
    }
}
