package org.domi.furnace.compat.nms;

import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.domi.furnace.api.Furnace;
import org.domi.furnace.api.internal.FeatureInternals;

/**
 * NMS 지형지물 배치(T-211): {@code ConfiguredFeature#place}·{@code PlacedFeature#place(WorldGenLevel, ChunkGenerator, RandomSource, BlockPos)} 를
 * 서버 지형 생성 코드(바닐라 1.21.1 이식)로 돌린다 — 설계 {@code docs/feature-parity/compat-nms-t211-world-surface-2026-11-17.md} 3.2.
 * <ul>
 *   <li>레벨이 서버 레벨 자신이고 쓰기 기록 중이 아니면 바닐라 런타임 배치(서버 월드에 바로, 블록 엔티티 포함).</li>
 *   <li>그 밖(BlockStateListPopulator·FAWE populator·기록 중인 서버 레벨)은 쓰기를 그 레벨의 {@code setBlock(pos, state, flags)} 로,
 *       블록 엔티티 있음은 그 레벨의 {@code getBlockEntity(pos)} 로 묻는다. 그래서 FAWE populator 는 Paper 처럼 서버 레벨 기록에, 평범한 populator 는 자기 표에 쌓인다.</li>
 *   <li>난수는 받은 RandomSource 에서 뽑을 때마다 그대로 뽑는다(Paper 와 같은 수열, 원천 상태도 같이 나아간다).</li>
 * </ul>
 * 같은 결과를 낼 수 없으면 {@link UnsupportedNmsCall}: 구현 안 된 지형지물, 플러그인 레벨의 블록 엔티티 내용을 바꿔야 하는 배치, 다른 월드의 생성기,
 * 서버 레벨을 모르는 레벨, ensureCanWrite 를 바꾼 레벨, 광석 줄기(바닐라는 청크 섹션에 바로 쓴다).
 * 쓰기 기록({@code captureTreeGeneration})은 전역 스레드에서만 적용한다({@link #captureAppliesHere}). 로드 안 된·다른 region·원점에서 2청크 넘는 청크에 닿으면 IllegalStateException(Paper 는 불러와 계속한다).
 */
public final class NmsFeatures {

    private NmsFeatures() {}

    /**
     * {@code Level} 의 쓰기 기록을 이 호출에 적용하는가: 전역 스레드(Bukkit 동기 작업 — Paper 의 주 스레드 자리)일 때만. 기록 표시는 월드마다 하나인 평범한
     * 필드라 켠 스레드를 알 수 없다 — 다른 region 스레드의 쓰기·배치가 전역 스레드가 켠 기록에 섞이지 않게 한 규칙으로 정한다(검토 #494).
     */
    public static boolean captureAppliesHere() {
        try {
            return org.domi.furnace.compat.thread.PrimaryThread.isGlobalThread(Furnace.server());
        } catch (IllegalStateException e) {
            return false;   // 서버 없음
        }
    }

    /** 플러그인 레벨이 던진 것을 서버 코드 너머로 그대로 나르는 껍데기(서버가 던진 것과 구별한다). */
    static final class LevelFailure extends RuntimeException {
        LevelFailure(RuntimeException cause) { super(null, cause, false, false); }
    }

    /**
     * @param placed  참이면 배치된 지형지물(PlacedFeature), 거짓이면 설정된 지형지물
     * @param id      레지스트리 이름
     * @param target  예외 메시지용 이름(예: {@code ConfiguredFeature#place})
     */
    public static boolean place(boolean placed, String id, String target, WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(random, "random");
        Objects.requireNonNull(origin, "origin");
        if (id == null) throw new UnsupportedNmsCall(target, "the feature is not one of the server's registered features");
        ServerLevel server;
        try {
            server = level.getMinecraftWorld();
        } catch (UnsupportedOperationException e) {
            throw new UnsupportedNmsCall(target, "the level (" + level.getClass().getName() + ") does not tell which server level it belongs to");
        }
        if (server == null) throw new UnsupportedNmsCall(target, "the level (" + level.getClass().getName() + ") does not belong to a server level");
        var world = server.getWorld().furnaceHandle();
        if (generator == null || !Objects.equals(generator.furnaceWorld(), world)) {
            throw new UnsupportedNmsCall(target, "the chunk generator must be the one of the level's world (getChunkSource().getGenerator())");
        }
        if (overridesEnsureCanWrite(level)) {
            throw new UnsupportedNmsCall(target, "the level overrides WorldGenLevel#ensureCanWrite, which the server's feature code does not ask for every feature");
        }
        boolean capturing = server.captureTreeGeneration && captureAppliesHere();
        boolean direct = level == server && !capturing;
        if (direct && server.captureBlockStates && captureAppliesHere()) {
            throw new UnsupportedNmsCall(target + "(captureBlockStates)",
                    "writing to the world without neighbour updates while recording the old states does not exist on the server yet");
        }
        FeatureInternals.LevelSink sink = direct ? null : levelSink(level);
        try {
            return Furnace.server().internals().features().placeNow(world, placed, id, origin.getX(), origin.getY(), origin.getZ(),
                    new RandomSourceAsJava(random), sink);
        } catch (LevelFailure f) {
            throw (RuntimeException) f.getCause();
        } catch (UnsupportedNmsCall e) {
            throw e;
        } catch (UnsupportedOperationException e) {
            throw new UnsupportedNmsCall(target + "(" + id + ")", e.getMessage());
        } catch (IllegalArgumentException e) {
            throw new UnsupportedNmsCall(target + "(" + id + ")", "the server's feature table does not have it: " + e.getMessage());
        }
    }

    /**
     * 플러그인 레벨로 가는 쓰기 통로(지형지물·구조물 공용). 쓰기는 그 레벨의 {@code setBlock}(인자 3개·4개 그대로), 블록 엔티티 있음은 {@code getBlockEntity},
     * 시드는 {@code getSeed()}·{@code getLevel().getSeed()}, 엔티티를 만들 때의 {@code getLevel()}, 템플릿 뒤 {@code blockUpdated} 를 그 레벨에 묻는다.
     * 엔티티 더하기는 그 레벨이 바닐라 기본({@code LevelWriter#addFreshEntity} — 거짓, Paper 의 populator 들)이면 버리고, 다른 레벨이면 {@link UnsupportedNmsCall}
     * (서버는 플러그인 레벨에 넘길 NMS 엔티티 객체를 만들지 않는다). {@code blockUpdated} 도 그 레벨이 바닐라 기본(아무것도 안 함)이 아니면(서버 레벨의 이웃 갱신 등)
     * {@link UnsupportedNmsCall} — 기록 중인 배치가 실제 월드를 바꾸는 이웃 갱신을 같은 순서로 낼 수 없다. 레벨이 던진 것은 {@link LevelFailure} 로 싸서 나른다.
     */
    static FeatureInternals.LevelSink levelSink(WorldGenLevel level) {
        return new FeatureInternals.LevelSink() {
            @Override
            public boolean setBlock(int x, int y, int z, int stateId, int flags) {
                try {
                    return level.setBlock(new BlockPos(x, y, z), NmsChunks.state(stateId), flags);
                } catch (RuntimeException e) {
                    throw new LevelFailure(e);
                }
            }

            @Override
            public boolean setBlock(int x, int y, int z, int stateId, int flags, int maxUpdateDepth) {
                try {
                    return level.setBlock(new BlockPos(x, y, z), NmsChunks.state(stateId), flags, maxUpdateDepth);
                } catch (RuntimeException e) {
                    throw new LevelFailure(e);
                }
            }

            @Override
            public String blockEntityType(int x, int y, int z) {
                try {
                    var be = level.getBlockEntity(new BlockPos(x, y, z));
                    return be == null ? null : be.getType().furnaceId().toString();
                } catch (RuntimeException e) {
                    throw new LevelFailure(e);
                }
            }

            @Override
            public long levelSeed(boolean throughGetLevel) {
                try {
                    return throughGetLevel ? level.getLevel().getSeed() : level.getSeed();
                } catch (RuntimeException e) {
                    throw new LevelFailure(e);
                }
            }

            @Override
            public void entityLevel() {
                try {
                    level.getLevel();
                } catch (RuntimeException e) {
                    throw new LevelFailure(e);
                }
            }

            @Override
            public void addEntity(String entityType, double x, double y, double z) {
                if (level instanceof net.minecraft.world.level.Level || declares(level, "addFreshEntity", net.minecraft.world.entity.Entity.class)) {
                    throw new LevelFailure(new UnsupportedNmsCall("addFreshEntity(" + entityType + ")",
                            "the level (" + level.getClass().getName() + ") takes entities, and the server does not hand NMS entity objects to plugin levels"));
                }
                // 바닐라 LevelWriter#addFreshEntity 기본: 거짓(엔티티는 사라진다)
            }

            @Override
            public void blockUpdated(int x, int y, int z, int stateId) {
                java.lang.reflect.Method m;
                try {
                    m = level.getClass().getMethod("blockUpdated", BlockPos.class, net.minecraft.world.level.block.Block.class);
                } catch (NoSuchMethodException e) {
                    throw new IllegalStateException(e);
                }
                if (m.getDeclaringClass() != net.minecraft.world.level.LevelAccessor.class) {
                    throw new LevelFailure(new UnsupportedNmsCall("blockUpdated",
                            "the level (" + level.getClass().getName() + ") updates neighbours on blockUpdated, which a placement through it cannot replay in the same order"));
                }
                // 바닐라 LevelAccessor#blockUpdated 기본: 아무것도 하지 않는다
            }
        };
    }

    /** 그 레벨 클래스가 이 공개 메서드를 가졌는가(바닐라 기본이 없는 메서드 — 있으면 레벨이 직접 정의했다). */
    private static boolean declares(Object level, String name, Class<?>... params) {
        try {
            level.getClass().getMethod(name, params);
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    private static boolean overridesEnsureCanWrite(WorldGenLevel level) {
        try {
            return level.getClass().getMethod("ensureCanWrite", BlockPos.class).getDeclaringClass() != WorldGenLevel.class;
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * 받은 RandomSource 를 서버 지형 생성 난수(java.util.Random 을 받는 쪽)에 넘기는 어댑터: 서버가 쓰는 뽑기마다 원천의 같은 이름 뽑기를 부른다.
     * 그 밖의 java.util.Random 메서드(비트 단위 next 등)는 RandomSource 에 대응이 없어 지원하지 않는다.
     */
    static final class RandomSourceAsJava extends java.util.Random {

        private final RandomSource source;

        RandomSourceAsJava(RandomSource source) {
            super(0L);
            this.source = source;
        }

        @Override
        public synchronized void setSeed(long seed) {
            if (source != null) source.setSeed(seed);   // 상위 생성자의 setSeed 는 건너뛴다
        }

        @Override
        protected int next(int bits) { throw new UnsupportedOperationException("java.util.Random#next over a RandomSource"); }

        @Override
        public int nextInt() { return source.nextInt(); }

        @Override
        public int nextInt(int bound) { return source.nextInt(bound); }

        @Override
        public long nextLong() { return source.nextLong(); }

        @Override
        public boolean nextBoolean() { return source.nextBoolean(); }

        @Override
        public float nextFloat() { return source.nextFloat(); }

        @Override
        public double nextDouble() { return source.nextDouble(); }

        @Override
        public synchronized double nextGaussian() { return source.nextGaussian(); }
    }
}
