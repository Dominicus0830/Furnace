// 구조물 몹(T-221): 바닐라 구조물 조각·템플릿이 몹을 만들 때 부르는
//   EntityType.create(world.getLevel()) → (조각마다: 유지 표시·체력) → moveTo → finalizeSpawn(world, world.getCurrentDifficultyAt(pos), STRUCTURE, null)
//   → world.addFreshEntityWithPassengers(entity)
// 를 배치 길마다 같은 world 로 돌린다. world 는 세 가지다.
//   - 월드젠: 바닐라 WorldGenRegion — 난수는 그 장식의 레벨 난수(worldgen_region_random 을 원점에 판 것, 구조물·지형지물이 이어 쓴다),
//     난이도는 (서버 난이도, 하루 시각, 머문 시간 0, 달 밝기), 엔티티 찾기는 늘 빈 목록, 더하기는 그 순간 저장(Entity.save)해 청크 NBT 로.
//   - 실제 레벨(/place, 플러그인이 서버 레벨에 바로): 몹을 그 레벨에서 만들고 그 레벨의 값을 쓴다. 더하기는 사본을 반영할 때(LiveGrid.commit).
//   - 플러그인 레벨(통로): 난수·난이도·달 밝기·바이옴·엔티티 찾기·더하기를 그 레벨에 묻는다(Paper 의 populator 가 하는 대로).
// 몹 쪽 코드(finalizeSpawn)는 엔티티의 level 을 읽으므로, 월드젠·통로는 이 world 로 답하는 분리 레벨(DetachedMobLevel)에서 몹을 만든다.
package org.domi.furnace.server.world.gen.structure;

import java.util.List;
import java.util.function.Consumer;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.RandomSource;
import org.domi.furnace.server.entity.DifficultyInstance;
import org.domi.furnace.server.entity.Entities;
import org.domi.furnace.server.entity.Entity;
import org.domi.furnace.server.entity.Mob;
import org.domi.furnace.server.entity.spawn.SpawnReason;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.world.gen.noise.NoiseRandom;

public final class StructureMobs {

    private StructureMobs() {}

    /** finalizeSpawn 의 world 인자(바닐라 ServerLevelAccessor)와 몹이 놓일 곳. */
    public interface Env {

        /** 몹을 만들 레벨(엔티티의 {@code level()}). 월드젠·통로는 {@link DetachedMobLevel}, 실제 레벨 길은 그 레벨. */
        ChunkLevel entityLevel();

        /** 바닐라 {@code EntityType.create(world.getLevel())} 의 {@code getLevel()} 자리(통로는 그 레벨에 묻는다 — 던지면 그대로). */
        default void askLevel() {}

        /** {@code world.getRandom()}. */
        RandomSource random();

        /** {@code world.getCurrentDifficultyAt(pos)}. */
        DifficultyInstance difficultyAt(Pos pos);

        /** {@code world.addFreshEntity(e, reason)} — 한 엔티티(addFreshEntityWithPassengers 는 자신과 승객마다 부른다). */
        void addFresh(Entity e);
    }

    /** 바닐라 {@code RandomSource} 로 본 월드젠 난수(뽑기마다 같은 이름의 뽑기). */
    public static RandomSource randomOf(NoiseRandom r) {
        java.util.Objects.requireNonNull(r, "random");
        return new RandomSource() {
            @Override public int nextInt() { return r.nextInt(); }
            @Override public int nextInt(int bound) { return r.nextInt(bound); }
            @Override public long nextLong() { return r.nextLong(); }
            @Override public float nextFloat() { return r.nextFloat(); }
            @Override public double nextDouble() { return r.nextDouble(); }
            @Override public boolean nextBoolean() { return r.nextBoolean(); }

            @Override
            public double nextGaussian() {
                // 구조물 몹 finalizeSpawn 은 가우스 뽑기를 쓰지 않는다 — 쓰는 길이 생기면 그 난수의 이식부터
                throw new UnsupportedOperationException("nextGaussian on a worldgen random");
            }
        };
    }

    /**
     * 조각이 코드로 만드는 몹(늪 오두막 마녀·고양이, 바다 유적 익사체, 해저 유적 엘더 가디언, 저택 몹).
     *
     * @param before   moveTo 전에 할 일(바닐라 setPersistenceRequired·heal 등), 없으면 null
     * @param reason   더할 때의 플러그인 스폰 이유(CraftBukkit SpawnReason 이름 — 늪 오두막 CHUNK_GEN, 나머지 DEFAULT)
     */
    public static void spawn(Env env, String id, double x, double y, double z, Consumer<Mob> before, String reason) {
        spawnGroup(env, List.of(id), x, y, z, before, reason, null);
    }

    /**
     * 여러 몹을 먼저 모두 만들고(만들 때마다 getLevel) 하나씩 마무리한다(바닐라 저택 표시: 목록을 만든 뒤 차례로). after 는 몹 하나를 더할 때마다(없으면 null).
     */
    public static void spawnGroup(Env env, List<String> ids, double x, double y, double z, Consumer<Mob> before, String reason, Runnable after) {
        List<Mob> mobs = new java.util.ArrayList<>(ids.size());
        for (String id : ids) {
            env.askLevel();
            Entity e = Entities.create(env.entityLevel(), id);
            if (!(e instanceof Mob mob)) throw new IllegalStateException("structure mob " + id + " is not a mob the server can make");
            mobs.add(mob);
        }
        for (Mob mob : mobs) {
            if (before != null) before.accept(mob);
            mob.moveTo(x, y, z, 0.0F, 0.0F);
            finalizeAndAdd(env, mob, reason);
            if (after != null) after.run();
        }
    }

    /**
     * 템플릿 엔티티(바닐라 StructureTemplate.placeEntities, finalizeEntities 일 때 몹): NBT 로 만들고 moveTo(위치, 돌린 방향) → 몹이면 finalizeSpawn →
     * addFreshEntityWithPassengers. 서버가 만들 수 없는 종류면 null(부르는 쪽이 NBT 를 그대로 둔다).
     */
    public static Entity spawnTemplate(Env env, NbtCompound nbt, double x, double y, double z, float yRot, float xRot, boolean finalizeMobs) {
        env.askLevel();   // createEntityIgnoreException: EntityType.create(nbt, world.getLevel())
        Entity e = Entities.load(env.entityLevel(), nbt);
        if (e == null) return null;
        e.moveTo(x, y, z, yRot, xRot);
        if (finalizeMobs && e instanceof Mob mob) {
            finalizeAndAdd(env, mob, "DEFAULT");
        } else {
            addWithPassengers(env, e, "DEFAULT");
        }
        return e;
    }

    private static void finalizeAndAdd(Env env, Mob mob, String reason) {
        Pos p = mob.blockPosition();
        DifficultyInstance difficulty = env.difficultyAt(p);
        finalizeStructure(mob, env.random(), difficulty);
        addWithPassengers(env, mob, reason);
    }

    /** {@code mob.finalizeSpawn(world, difficulty, MobSpawnType.STRUCTURE, null)}. */
    public static void finalizeStructure(Mob mob, RandomSource r, DifficultyInstance difficulty) {
        if (mob instanceof org.domi.furnace.server.entity.Villager v) v.setFinalizeReason(SpawnReason.STRUCTURE);
        if (mob instanceof org.domi.furnace.server.entity.Raider rd) rd.setFinalizeReason(SpawnReason.STRUCTURE);
        mob.finalizeSpawnGroup(r, difficulty, null, SpawnReason.STRUCTURE);
    }

    /** ServerLevelAccessor.addFreshEntityWithPassengers(e, reason): getSelfAndPassengers 차례로 addFreshEntity. */
    private static void addWithPassengers(Env env, Entity e, String reason) {
        e.spawnReason(reason);
        java.util.List<org.domi.furnace.server.entity.Rider> riders = new java.util.ArrayList<>();
        e.collectIndirectPassengers(riders);
        env.addFresh(e);
        for (var r : riders) if (r instanceof Entity pe) env.addFresh(pe.spawnReason(reason));
    }

    // ------------------------------------------------------------------ 월드젠(WorldGenRegion)

    /**
     * 월드젠 장식 한 번(원점 청크)의 world. 몹은 처음 필요할 때 만든 분리 레벨에서 만들고, 더할 때 저장한 NBT 를 view 청크에 남긴다.
     *
     * @param regionRandom 그 장식의 레벨 난수(구조물·지형지물이 이어 쓴다)
     * @param starts       그 청크가 참조하는 시작점(고양이의 #cats_spawn_as_black 검사 — 바닐라는 서버 레벨의 구조물 관리자가 그 청크 참조를 읽는다)
     * @param services     그 월드의 난이도·시간·레지스트리
     */
    public static Env generation(org.domi.furnace.server.world.gen.feature.FeatureWorldView view, NoiseRandom regionRandom, List<StructureStart> starts,
                                 org.domi.furnace.server.block.LevelServices services, StructureEngine engine) {
        return new Generation(view, regionRandom, starts, services, engine);
    }

    private static final class Generation implements Env, DetachedMobLevel.Calls {
        private final org.domi.furnace.server.world.gen.feature.FeatureWorldView view;
        private final RandomSource random;
        private final List<StructureStart> starts;
        private final org.domi.furnace.server.block.LevelServices services;
        private final StructureEngine engine;
        private DetachedMobLevel level;

        Generation(org.domi.furnace.server.world.gen.feature.FeatureWorldView view, NoiseRandom regionRandom, List<StructureStart> starts,
                   org.domi.furnace.server.block.LevelServices services, StructureEngine engine) {
            this.view = view;
            this.random = randomOf(regionRandom);
            this.starts = starts == null ? List.of() : starts;
            this.services = services;
            this.engine = engine;
        }

        @Override
        public ChunkLevel entityLevel() {
            if (level == null) level = new DetachedMobLevel(this);
            return level;
        }

        @Override public RandomSource random() { return random; }

        /** WorldGenRegion.getCurrentDifficultyAt: new DifficultyInstance(level.getDifficulty(), level.getDayTime(), 0, level.getMoonBrightness()). */
        @Override
        public DifficultyInstance difficultyAt(Pos pos) {
            return new DifficultyInstance(services.difficulty(), services.dayTime(), 0L, moonBrightness(),
                    org.domi.furnace.server.world.DifficultyModifiers.local());
        }

        /** WorldGenRegion.addFreshEntity → ProtoChunk.addEntity: 그 순간 entity.save(tag) 가 참이면(탄 것이 아니면) 그 칸 청크에 NBT 로. */
        @Override
        public void addFresh(Entity e) {
            NbtCompound tag = e.save();
            if (tag != null) view.addEntityNbt(tag, e.x(), e.z());
        }

        // ---- 분리 레벨이 묻는 것(바닐라 world 인자 = WorldGenRegion, world.getLevel() = 서버 레벨)

        @Override public org.domi.furnace.server.block.LevelServices services() { return services; }

        /** LevelTimeAccess.getMoonBrightness — 레벨 데이터의 하루 시각. */
        @Override public float moonBrightness() { return DetachedMobLevel.moonBrightness(services.dayTime()); }

        /** WorldGenRegion.getBiome(pos) — 그 지역의 바이옴 확대(BiomeManager). */
        @Override public String biomeAt(int x, int y, int z) { return engine.biomeName(view.getBlendedBiomeId(x, y, z)); }

        /** WorldGenRegion.getEntities(…): 늘 빈 목록. */
        @Override public <T extends Entity> List<T> entities(Class<T> cls, org.domi.furnace.server.entity.AABB box, java.util.function.Predicate<? super T> filter) {
            return List.of();
        }

        @Override public boolean addFreshEntity(Entity e) { addFresh(e); return true; }

        /** 서버 레벨 StructureManager.getStructureWithPieceAt: 그 칸 청크의 참조 시작점 중 조각 하나가 그 칸을 담는 첫 것. */
        @Override
        public ChunkStructures.Ref structureWithPieceAt(Pos pos, String structureId) {
            for (StructureStart s : starts) {
                if (!s.structureId().equals(structureId)) continue;
                ChunkStructures.Ref r = ChunkStructures.refOf(s);
                if (r.hasPieceAt(pos.x(), pos.y(), pos.z())) return r;
            }
            return null;
        }

        @Override public DifficultyInstance difficultyAtForLevel(Pos pos) { return difficultyAt(pos); }

        @Override public RandomSource levelRandom() { return random; }
    }

    // ------------------------------------------------------------------ 실제 레벨(/place, 서버 레벨에 바로)

    /**
     * 실제 레벨에 바로 놓는 배치의 world(바닐라 /place structure — world = ServerLevel). 몹은 그 레벨에서 만들고 그 레벨의 난이도를 쓰며, 난수는 배치가 넘긴 것
     * (바닐라 /place 는 레벨 난수 하나로 배치와 몹을 함께 뽑는다). 더하기는 pending 에 모아 사본을 반영할 때 그 레벨에 넣는다.
     */
    public static Env live(ChunkLevel level, NoiseRandom random, List<Entity> pending) {
        java.util.Objects.requireNonNull(level, "level");
        RandomSource r = randomOf(random);
        return new Env() {
            @Override public ChunkLevel entityLevel() { return level; }
            @Override public RandomSource random() { return r; }
            @Override public DifficultyInstance difficultyAt(Pos pos) { return level.getCurrentDifficultyAt(pos); }
            @Override public void addFresh(Entity e) { pending.add(e); }
        };
    }

    // ------------------------------------------------------------------ 플러그인 레벨(통로)

    /**
     * 플러그인 레벨을 통한 배치의 world(Paper 의 populator 가 world 인자다): 난수·지역 난이도·달 밝기·바이옴·엔티티 찾기·엔티티 더하기는 그 레벨에 묻고,
     * world.getLevel() 쪽(고양이의 구조물 검사·낙타의 게임 시간·난이도·레지스트리)은 이 서버 레벨로 답한다. 몹은 분리 레벨에서 만들어 그 레벨에 넘긴다
     * (바닐라 기본 populator 는 버린다).
     */
    public static Env plugin(ChunkLevel level, org.domi.furnace.server.world.gen.feature.FeatureWorldView.WriteSink sink, NoiseRandom levelRandom) {
        return new Plugin(level, sink, levelRandom);
    }

    private static final class Plugin implements Env, DetachedMobLevel.Calls {
        private final ChunkLevel level;
        private final org.domi.furnace.server.world.gen.feature.FeatureWorldView.WriteSink sink;
        private final RandomSource random;
        private DetachedMobLevel detached;

        Plugin(ChunkLevel level, org.domi.furnace.server.world.gen.feature.FeatureWorldView.WriteSink sink, NoiseRandom levelRandom) {
            this.level = java.util.Objects.requireNonNull(level, "level");
            this.sink = java.util.Objects.requireNonNull(sink, "sink");
            this.random = randomOf(levelRandom);
        }

        @Override
        public ChunkLevel entityLevel() {
            if (detached == null) detached = new DetachedMobLevel(this);
            return detached;
        }

        @Override public void askLevel() { sink.entityLevel(); }

        @Override public RandomSource random() { return random; }

        @Override
        public DifficultyInstance difficultyAt(Pos pos) {
            float[] d = sink.currentDifficultyAt(pos.x(), pos.y(), pos.z());
            return DifficultyInstance.ofEffective((int) d[0], d[1]);
        }

        /** 그 레벨의 addFreshEntity(자신과 승객마다). */
        @Override public void addFresh(Entity e) { sink.addEntity(e.type().name(), e.x(), e.y(), e.z()); }

        @Override
        public org.domi.furnace.server.block.LevelServices services() {
            if (level instanceof org.domi.furnace.server.world.RegionLevel rl) return rl.world().levelServices();
            throw new IllegalStateException("structure mobs through a plugin level need a region level");
        }

        @Override public float moonBrightness() { return sink.moonBrightness(); }

        @Override public String biomeAt(int x, int y, int z) { return sink.biomeAt(x, y, z); }

        @Override
        public <T extends Entity> List<T> entities(Class<T> cls, org.domi.furnace.server.entity.AABB box, java.util.function.Predicate<? super T> filter) {
            if (cls != org.domi.furnace.server.entity.Chicken.class) {
                throw new UnsupportedOperationException("getEntitiesOfClass(" + cls.getSimpleName() + ") on a plugin level");
            }
            if (sink.chickensToRide(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
                throw new UnsupportedOperationException("the plugin level has a chicken for a baby zombie to ride - riding a plugin level's entity is not supported");
            }
            return List.of();
        }

        @Override public boolean addFreshEntity(Entity e) { addFresh(e); return true; }

        /** world.getLevel().structureManager().getStructureWithPieceAt(pos, …): getLevel() 을 묻고 이 서버 레벨의 구조물로 답한다. */
        @Override
        public ChunkStructures.Ref structureWithPieceAt(Pos pos, String structureId) {
            sink.entityLevel();
            return level.structureWithPieceAt(pos, structureId);
        }

        @Override public DifficultyInstance difficultyAtForLevel(Pos pos) { return difficultyAt(pos); }

        @Override public RandomSource levelRandom() { return random; }
    }
}
