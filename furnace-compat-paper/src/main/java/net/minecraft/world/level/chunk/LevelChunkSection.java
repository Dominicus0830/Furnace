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
 *     로드된 청크의 섹션 배열에 넣으면 주인 region 이 1틱 안에(장벽 알림이면 다음 맞춤 지점에) 그 내용·개수 필드를 서버 섹션으로 들여오고 이 객체를 그 자리의
 *     보기로 붙인다(N2b-4 — 정체성 유지, 저장 표시·빛·하이트맵 재계산·패킷 없음은 바닐라와 같다).</li>
 * <li><b>보기</b>(N2a, 로드된 청크의 {@code getSections()}): 서버 섹션을 본다 — 주인 region 스레드는 살아 있는 값, 그 밖은 주인이 낸 공개 스냅샷 + 자기
 *     쓰기(N2b-3·4). {@link #setBlockState} 와 컨테이너 칸 쓰기는 바닐라 원시 의미 그대로 — 주인 스레드는 서버 섹션에 바로, 그 밖은 거울 칸 일지에 쌓아 주인이
 *     1틱 안에 그 칸만 재생한다(결정 D1). 개수는 서버가 바닐라 개수 셋을 들고 같은 규칙으로 고치고, 하이트맵(고정 하이트맵 모드로 낡은 값)·빛·저장 표시·
 *     클라이언트·블록 엔티티는 건드리지 않는다. 개수 <b>필드</b>는 서버 값의 거울이다(틱 끝마다 맞추고 다른 스레드의 칸 쓰기는 바닐라처럼 곧바로 ±1) —
 *     플러그인이 필드에 대입하면(FAWE clearCounts) 그 값이 서버 개수가 된다. {@link #read} 는 새 내용을 거울에 읽고 주인이 들여온다.
 *     배열에서 빠지면 그 순간 내용으로 굳어 떨어진 섹션이 된다(그 뒤 쓰기는 월드에 닿지 않는다 — 바닐라에서 교체된 섹션과 같다).</li>
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
    /**
     * Furnace: 붙은 보기면 그 청크(강한 참조 — 섹션을 쥐면 같은 섹션 배열이 유지된다), 떨어진 섹션이면 null. 들여오기가 플러그인 섹션을 붙이고 빠진 보기를
     * 떼어 낸다(N2b-4).
     */
    private volatile ChunkAccess furnaceChunk;
    private volatile int furnaceIndex;

    private static final java.lang.invoke.VarHandle NON_EMPTY, TICKING_BLOCK, TICKING_FLUID, BIOMES;
    static {
        try {
            var l = java.lang.invoke.MethodHandles.lookup();
            NON_EMPTY = l.findVarHandle(LevelChunkSection.class, "nonEmptyBlockCount", short.class);
            TICKING_BLOCK = l.findVarHandle(LevelChunkSection.class, "tickingBlockCount", short.class);
            TICKING_FLUID = l.findVarHandle(LevelChunkSection.class, "tickingFluidCount", short.class);
            BIOMES = l.findVarHandle(LevelChunkSection.class, "biomes", PalettedContainerRO.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    /** 바닐라: 주어진 컨테이너로 만들고 개수를 다시 센다(recalcBlockCounts). 컨테이너를 복사하지 않는다. */
    public LevelChunkSection(PalettedContainer<BlockState> states, PalettedContainerRO<Holder<Biome>> biomes) {
        this.states = states;
        this.biomes = biomes;
        this.furnaceChunk = null;
        this.furnaceIndex = -1;
        recalcBlockCounts();
    }

    /**
     * Paper 1.21.1 서술자({@code (PalettedContainer, PalettedContainer)}) — Paper 는 바이옴 인자·필드를 {@code PalettedContainer} 로 바꿨다.
     * Paper 로 컴파일한 플러그인(FAWE 등)이 이 꼴로 만든다(N2b-6). 뜻은 바닐라 생성자와 같다.
     */
    public LevelChunkSection(PalettedContainer<BlockState> states, PalettedContainer<Holder<Biome>> biomes) {
        this(states, (PalettedContainerRO<Holder<Biome>>) biomes);
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

    private LevelChunkSection(ChunkAccess chunk, int index, int[] counts) {
        this.furnaceChunk = chunk;
        this.furnaceIndex = index;
        this.states = PalettedContainer.furnaceView(Block.BLOCK_STATE_REGISTRY, PalettedContainer.Strategy.SECTION_STATES,
                new FurnaceSectionSource(chunk, index, false));   // 컨테이너 칸 쓰기: 바닐라처럼 개수 그대로
        this.biomes = PalettedContainer.furnaceView(NmsChunks.BIOME_IDS, PalettedContainer.Strategy.SECTION_BIOMES,
                new FurnaceSectionSource(chunk, index, true));
        if (counts != null) {   // 개수 필드 = 서버 바닐라 개수의 거울(N2b-4)
            nonEmptyBlockCount = (short) counts[0];
            tickingBlockCount = (short) counts[1];
            tickingFluidCount = (short) counts[2];
        }
    }

    /** 호환 모듈 전용: 청크의 index 번째(0 = 맨 아래) 섹션 보기. 청크 shim 이 한 번만 만든다. counts 는 개수 필드의 처음 값(없으면 null — 0). */
    static LevelChunkSection furnaceView(ChunkAccess chunk, int index, int[] counts) { return new LevelChunkSection(chunk, index, counts); }

    /** Furnace: 붙은 보기면 이 섹션의 번호(0 = 맨 아래), 떨어진 섹션이면 -1. */
    public int furnaceIndex() { return furnaceChunk == null ? -1 : furnaceIndex; }

    /** Furnace: 서버 청크의 보기인가(아니면 떨어진 섹션). */
    public boolean furnaceIsView() { return furnaceChunk != null; }

    // ------------------------------------------------------------------ 거울(N2b-4, 호환 모듈 전용)

    /** 붙은 청크(떨어졌으면 null). */
    ChunkAccess furnaceChunk() { return furnaceChunk; }

    /** biomes 필드(volatile 읽기 — 플러그인이 Field.set 으로 바꾼다). */
    Object furnaceBiomesField() { return BIOMES.getVolatile(this); }

    /** 개수 필드 셋을 out[off..off+2] 에. */
    void furnaceReadCountFields(int[] out, int off) {
        out[off] = (short) NON_EMPTY.getVolatile(this);
        out[off + 1] = (short) TICKING_BLOCK.getVolatile(this);
        out[off + 2] = (short) TICKING_FLUID.getVolatile(this);
    }

    /** 개수 필드가 기준과 다른가(플러그인 대입). */
    boolean furnaceCountFieldsDiffer(int[] expected, int off) {
        return (short) NON_EMPTY.getVolatile(this) != (short) expected[off] || (short) TICKING_BLOCK.getVolatile(this) != (short) expected[off + 1]
                || (short) TICKING_FLUID.getVolatile(this) != (short) expected[off + 2];
    }

    /** 개수 필드를 기준 → next 로(CAS — 그 사이 플러그인이 대입한 필드는 그대로 두고 false). 거울 잠금 아래. */
    boolean furnaceCasCountFields(int[] expected, int off, int[] next) {
        boolean a = NON_EMPTY.compareAndSet(this, (short) expected[off], (short) next[0]);
        boolean b = TICKING_BLOCK.compareAndSet(this, (short) expected[off + 1], (short) next[1]);
        boolean c = TICKING_FLUID.compareAndSet(this, (short) expected[off + 2], (short) next[2]);
        return a && b && c;
    }

    /** 다른 스레드의 칸 쓰기: 바닐라 setBlockState 의 개수 ±1 을 필드에(거울 잠금 아래). */
    void furnaceAddCountFields(int[] d) {
        NON_EMPTY.getAndAdd(this, (short) d[0]);
        TICKING_BLOCK.getAndAdd(this, (short) d[1]);
        TICKING_FLUID.getAndAdd(this, (short) d[2]);
    }

    /** 바닐라 LevelChunkSection.setBlockState 의 개수 변화(옛 상태 → 새 상태). */
    static int[] furnaceCountDelta(int oldId, int newId) {
        int[] d = new int[3];
        BlockState old = NmsChunks.state(oldId), now = NmsChunks.state(newId);
        if (old != null) {
            if (!old.isAir()) {
                d[0]--;
                if (old.isRandomlyTicking()) d[1]--;
            }
            if (!old.getFluidState().isEmpty()) d[2]--;
        }
        if (now != null) {
            if (!now.isAir()) {
                d[0]++;
                if (now.isRandomlyTicking()) d[1]++;
            }
            if (!now.getFluidState().isEmpty()) d[2]++;
        }
        return d;
    }

    /**
     * 들여온 섹션을 그 자리의 보기로 붙인다(주인, 거울 잠금 아래) — 컨테이너도(다른 곳에 붙은 것은 그대로). statesData·biomesData 는 들여온 내용을 읽은
     * data(그것이 기준이 된다), biomes 는 들여온 바이옴 컨테이너(그 사이 필드가 바뀌었으면 다음 맞추기가 들여온다). 개수 필드는 그대로.
     */
    void furnaceAttach(ChunkAccess chunk, int index, Object statesData, Object biomes, Object biomesData, long stamp) {
        if (!states.furnaceAttached()) states.furnaceAttach(new FurnaceSectionSource(chunk, index, false), statesData, stamp);
        if (biomes instanceof PalettedContainer<?> b && !b.furnaceAttached()) b.furnaceAttach(new FurnaceSectionSource(chunk, index, true), biomesData, stamp);
        furnaceIndex = index;
        furnaceChunk = chunk;
    }

    /** 들여온 바이옴 컨테이너(그 data 를 읽었다)를 이 자리의 보기로 붙인다(주인, 거울 잠금 아래). */
    void furnaceAttachBiomes(PalettedContainer<?> b, Object data, long stamp) {
        ChunkAccess c = furnaceChunk;
        if (c != null) b.furnaceAttach(new FurnaceSectionSource(c, furnaceIndex, true), data, stamp);
    }

    /** 그 컨테이너가 이 섹션 자리의 공급원을 쓰는가. */
    boolean furnaceOwnsBiomeSource(PalettedContainer<?> b) {
        return b.furnaceSourceObject() instanceof FurnaceSectionSource src && src.chunk == furnaceChunk && src.index == furnaceIndex && src.biome;
    }

    /** 배열에서 빠졌거나 청크가 내려갔다(주인, 거울 잠금 아래): 그 순간 내용으로 굳혀 떨어진 섹션이 된다(개수 필드는 그대로). */
    void furnaceDetach() {
        if (states.furnaceSourceObject() instanceof FurnaceSectionSource src && src.chunk == furnaceChunk && src.index == furnaceIndex) states.furnaceDetach();
        if (furnaceBiomesField() instanceof PalettedContainer<?> b && furnaceOwnsBiomeSource(b)) b.furnaceDetach();
        furnaceChunk = null;
        furnaceIndex = -1;
    }

    /** 이 칸 쓰기가 서버로 가는가(붙은 보기이고 플러그인이 data 를 바꾸지 않았다). */
    private ChunkAccess liveChunk() {
        ChunkAccess c = furnaceChunk;
        return c != null && states.furnaceLiveNow() ? c : null;
    }

    /** 붙은 보기이고 지금 스레드가 그 청크의 주인인가. */
    private ChunkAccess ownerChunk() {
        ChunkAccess c = furnaceChunk;
        return c != null && c.furnaceOwner() ? c : null;
    }

    public BlockState getBlockState(int x, int y, int z) { return states.get(x, y, z); }

    public FluidState getFluidState(int x, int y, int z) { return states.get(x, y, z).getFluidState(); }

    /** 바닐라: 블록 컨테이너의 디버그 잠금(아무것도 하지 않는다 — PalettedContainer#acquire). */
    public void acquire() { states.acquire(); }

    public void release() { states.release(); }

    public BlockState setBlockState(int x, int y, int z, BlockState state) { return setBlockState(x, y, z, state, true); }

    /**
     * 바닐라: 칸 하나를 바꾸고 개수를 ±1(유체는 비어 있지 않으면 모두 — 물도). 그 밖 부수 효과는 없다. 붙은 보기는 주인 스레드면 서버 섹션에 바로, 그 밖은 거울
     * 칸 일지에(개수 필드는 곧바로 ±1).
     */
    public BlockState setBlockState(int x, int y, int z, BlockState state, boolean useLocks) {
        ChunkAccess c = liveChunk();
        int idx = furnaceIndex;   // 한 번만 읽는다 — 떼어 내는 사이 -1 이 될 수 있다(PR #396 검토 R2)
        if (c != null && idx >= 0) {   // 바닐라 원시 의미 — 개수 ±1 은 서버가 같은 규칙으로
            int cell = PalettedContainer.Strategy.SECTION_STATES.getIndex(x, y, z);
            if (cell < 0 || cell >= SECTION_SIZE) {
                throw new IllegalArgumentException(String.format("The value %s is not in the specified inclusive range of %s to %s", (long) cell, 0L, 4095L));
            }
            int old = c.furnaceSectionSet(this, idx, cell, state.furnaceStateId());
            if (old != PalettedContainer.FURNACE_DETACHED) return NmsChunks.state(old);
            // 그 사이 배열에서 빠져 떼어 내졌다 — 아래 바닐라 길로 이 섹션에(월드에 닿지 않는다, Paper 에서 교체된 섹션과 같다)
        }
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
        noteMirror();
        return old;
    }

    /** 붙은 보기의 필드·data 를 바닐라처럼 바꿨다 — 거울에 알린다(주인이 들여온다). */
    private void noteMirror() {
        ChunkAccess c = furnaceChunk;
        if (c != null) c.furnaceMirror.noteWrite();
    }

    /**
     * 바닐라: nonEmptyBlockCount == 0. 보기는 주인 스레드면 서버 섹션이 가진 바닐라 개수, 그 밖은 그 거울인 필드(읽은 섹션에서 물을 지우면 공기뿐이어도
     * 거짓일 수 있다 — 바닐라 그대로).
     */
    public boolean hasOnlyAir() {
        ChunkAccess c = ownerChunk();
        if (c != null) return c.furnaceSectionCounts(furnaceIndex)[0] == 0;
        return nonEmptyBlockCount == 0;
    }

    /** 바닐라: 블록 또는 유체 쪽이 무작위 틱 대상인가. 보기는 주인 스레드면 서버 섹션의 판정. */
    public boolean isRandomlyTicking() {
        ChunkAccess c = ownerChunk();
        if (c != null) return c.furnaceSectionRandomlyTicking(furnaceIndex);
        return isRandomlyTickingBlocks() || isRandomlyTickingFluids();
    }

    /** 바닐라: tickingBlockCount > 0. 보기는 주인 스레드면 서버 섹션이 가진 바닐라 개수로 답한다. */
    public boolean isRandomlyTickingBlocks() {
        ChunkAccess c = ownerChunk();
        if (c != null) return (c.furnaceSectionRandomTickingParts(furnaceIndex) & 1) != 0;
        return tickingBlockCount > 0;
    }

    /**
     * 바닐라: tickingFluidCount > 0(음수도 "아니오"). 보기는 주인 스레드면 서버 섹션이 가진 바닐라 개수로 답한다 — 읽은 섹션은 용암만, 칸 쓰기로 놓은 물도 센다(PR #358).
     */
    public boolean isRandomlyTickingFluids() {
        ChunkAccess c = ownerChunk();
        if (c != null) return (c.furnaceSectionRandomTickingParts(furnaceIndex) & 2) != 0;
        return tickingFluidCount > 0;
    }

    /**
     * 바닐라 그대로 다시 센다: 공기 아닌 칸은 nonEmpty(무작위 틱 블록이면 tickingBlock 도), <b>유체가 있는 칸은 nonEmpty 를 한 번 더</b>,
     * 유체가 무작위 틱(용암)이면 tickingFluid. 보기는 주인 스레드면 서버 섹션의 바닐라 개수를 지금 칸에서 다시 센 값으로 바꾸고, 그 밖은 필드에(주인이 들여온다).
     */
    public void recalcBlockCounts() {
        int[] c = count(states);
        ChunkAccess oc = ownerChunk();
        if (oc != null && states.furnaceLiveNow()) {
            oc.furnaceSetSectionCounts(furnaceIndex, c[0], c[1], c[2]);
            return;
        }
        nonEmptyBlockCount = (short) c[0];
        tickingBlockCount = (short) c[1];
        tickingFluidCount = (short) c[2];
        noteMirror();
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

    /**
     * 바닐라: nonEmptyBlockCount(short) + 블록 컨테이너 + 바이옴 컨테이너(바이옴은 recreate 한 새 컨테이너에 읽어 갈아 끼운다). 붙은 보기는 거울에 읽고 주인이
     * 들여온다(N2b-4 — 블록은 새 data, 바이옴은 새 컨테이너, nonEmpty 는 필드 대입).
     */
    public void read(FriendlyByteBuf buf) {
        nonEmptyBlockCount = buf.readShort();
        states.read(buf);
        PalettedContainer<Holder<Biome>> b = biomes.recreate();
        b.read(buf);
        biomes = b;
        noteMirror();
    }

    public void readBiomes(FriendlyByteBuf buf) {
        PalettedContainer<Holder<Biome>> b = biomes.recreate();
        b.read(buf);
        biomes = b;
        noteMirror();
    }

    /** 바닐라 직렬화. 붙은 보기는 개수·블록·바이옴을 한 판에서(주인 스레드면 서버 섹션의 바닐라 nonEmptyBlockCount, 그 밖은 공개 스냅샷 하나와 필드). */
    public void write(FriendlyByteBuf buf) {
        ChunkAccess c = liveChunk();
        if (c != null && biomes instanceof PalettedContainer<?> pb && pb.furnaceLiveNow() && furnaceOwnsBiomeSource(pb)) {
            boolean owner = c.furnaceOwner();
            int[][] d = c.furnaceSectionData(furnaceIndex);
            buf.writeShort(owner ? d[0][0] : nonEmptyBlockCount);
            PalettedContainer.furnaceFromIds(Block.BLOCK_STATE_REGISTRY, PalettedContainer.Strategy.SECTION_STATES, d[1]).write(buf);
            PalettedContainer.furnaceFromIds(NmsChunks.BIOME_IDS, PalettedContainer.Strategy.SECTION_BIOMES, d[2]).write(buf);
            return;
        }
        buf.writeShort(nonEmptyBlockCount);
        states.write(buf);
        biomes.write(buf);
    }

    public int getSerializedSize() { return 2 + states.getSerializedSize() + biomes.getSerializedSize(); }

    public boolean maybeHas(Predicate<BlockState> predicate) { return states.maybeHas(predicate); }

    /** 섹션 안 바이옴 칸(0~3) 의 바이옴. */
    public Holder<Biome> getNoiseBiome(int x, int y, int z) { return biomes.get(x, y, z); }

    @Override
    public String toString() {
        ChunkAccess c = furnaceChunk;
        return c != null ? "LevelChunkSection[" + c.getPos() + ", " + furnaceIndex + "]" : super.toString();
    }
}
