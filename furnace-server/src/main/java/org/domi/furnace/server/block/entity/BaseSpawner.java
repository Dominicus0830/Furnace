// 바닐라 BaseSpawner(서버 쪽): 다음 스폰 데이터(SpawnData)·후보 목록(SpawnPotentials, 가중치)·지연·횟수·범위.
// serverTick 의 난수 순서(레벨 난수): [다음 데이터가 없으면 후보 nextInt(합)] → 시도마다 x: nextDouble−nextDouble, y: nextInt(3), z: nextDouble−nextDouble
// (Pos 가 있으면 그 축은 건너뜀) → 몹 스폰 규칙 난수 → yRot nextFloat → finalizeSpawn 난수 → … → delay: nextInt(max−min)·후보 nextInt(합).
// 스폰 데이터 저장 꼴: {entity:{…}, custom_spawn_rules?:{block_light_limit, sky_light_limit}, equipment?}, 후보: [{data:…, weight:n}].
// TODO(입자): clientTick 의 연기·불꽃 입자는 클라이언트 몫. TODO(엔티티): 탈것 승객(loadEntityRecursive 의 Passengers), equipment 적용.
package org.domi.furnace.server.block.entity;

import java.util.ArrayList;
import java.util.List;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.RandomSource;
import org.domi.furnace.server.entity.AABB;
import org.domi.furnace.server.entity.Entity;
import org.domi.furnace.server.entity.Mob;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtIo;
import org.domi.furnace.server.net.io.nbt.NbtList;

public abstract class BaseSpawner {

    /** SpawnData: 엔티티 태그 + (선택) 빛 조건 + (선택) 장비 원본. */
    public record SpawnData(NbtCompound entity, int[] lightLimits, NbtCompound customRulesRaw, Object equipment) {
        public SpawnData() { this(new NbtCompound(), null, null, null); }

        /** 생성자의 id 정규화(ResourceLocation.tryParse): 이름공간이 없으면 minecraft:, 틀리면 지운다. */
        public SpawnData {
            if (entity.tags().get("id") instanceof String id) {
                String n = normalizeId(id);
                if (n != null) entity.putString("id", n);
                else entity.tags().remove("id");
            }
        }

        /** CustomSpawnRules.isValidPosition: 블록빛·하늘빛이 범위 안. */
        boolean validPosition(BlockLevel level, Pos pos) {
            int bl = level.getBlockLight(pos), sl = level.getSkyLight(pos);
            return bl >= lightLimits[0] && bl <= lightLimits[1] && sl >= lightLimits[2] && sl <= lightLimits[3];
        }

        NbtCompound save() {
            NbtCompound t = new NbtCompound();
            t.putCompound("entity", entity);
            if (customRulesRaw != null) t.putCompound("custom_spawn_rules", customRulesRaw);
            if (equipment != null) t.put("equipment", equipment);
            return t;
        }

        /** 코덱 해석(실패면 null). */
        static SpawnData parse(Object o) {
            if (!(o instanceof NbtCompound c) || !(c.tags().get("entity") instanceof NbtCompound e)) return null;
            int[] limits = null;
            NbtCompound raw = null;
            if (c.tags().get("custom_spawn_rules") instanceof NbtCompound r) {
                int[] b = range(r.tags().get("block_light_limit")), s = range(r.tags().get("sky_light_limit"));
                limits = new int[] {b[0], b[1], s[0], s[1]};
                raw = r;
            } else if (c.contains("custom_spawn_rules")) {
                return null;
            }
            return new SpawnData(e.copy(), limits, raw, c.tags().get("equipment"));
        }

        /** InclusiveRange<Integer>(0..15 기본, lenientOptional — 틀리면 기본값). */
        private static int[] range(Object o) {
            int[] r = null;
            if (o instanceof Number n) r = new int[] {n.intValue(), n.intValue()};
            else if (o instanceof NbtCompound c && c.tags().get("min_inclusive") instanceof Number a && c.tags().get("max_inclusive") instanceof Number b)
                r = new int[] {a.intValue(), b.intValue()};
            else if (o instanceof NbtList l && l.size() == 2 && l.get(0) instanceof Number a && l.get(1) instanceof Number b)
                r = new int[] {a.intValue(), b.intValue()};
            else if (o instanceof int[] a && a.length == 2) r = a.clone();
            if (r == null || r[0] > r[1] || r[0] < 0 || r[1] > 15) return new int[] {0, 15};
            return r;
        }
    }

    public record Weighted(SpawnData data, int weight) {}

    static String normalizeId(String id) {
        String ns = "minecraft", path = id;
        int i = id.indexOf(':');
        if (i >= 0) {
            ns = id.substring(0, i);
            path = id.substring(i + 1);
            if (ns.isEmpty()) ns = "minecraft";
        }
        if (!ns.matches("[a-z0-9_.-]+") || !path.matches("[a-z0-9_./-]+")) return null;
        return ns + ":" + path;
    }

    int spawnDelay = 20;
    List<Weighted> spawnPotentials = new ArrayList<>();
    SpawnData nextSpawnData;
    int minSpawnDelay = 200;
    int maxSpawnDelay = 800;
    int spawnCount = 4;
    int maxNearbyEntities = 6;
    int requiredPlayerRange = 16;
    int spawnRange = 4;

    public int spawnDelay() { return spawnDelay; }

    public SpawnData nextSpawnData() { return nextSpawnData; }

    public List<Weighted> spawnPotentials() { return spawnPotentials; }

    /** setEntityId(type, level, random, pos): 다음 데이터의 엔티티 id 를 바꾼다. */
    public void setEntityId(String type, BlockLevel level, RandomSource random, Pos pos) {
        getOrCreateNextSpawnData(level, random, pos).entity().putString("id", type);
    }

    /** isNearPlayer: 칸 가운데에서 requiredPlayerRange 안에 살아 있는 관전자 아닌 플레이어. */
    private boolean isNearPlayer(ChunkLevel level, Pos pos) {
        return level.hasNearbyAlivePlayer(pos.x() + 0.5, pos.y() + 0.5, pos.z() + 0.5, requiredPlayerRange);
    }

    public void serverTick(ChunkLevel level, Pos pos) {
        if (!isNearPlayer(level, pos)) return;
        if (spawnDelay == -1) delay(level, pos);
        if (spawnDelay > 0) {
            spawnDelay--;
            return;
        }
        boolean spawned = false;
        RandomSource random = level.random();
        SpawnData sd = getOrCreateNextSpawnData(level, random, pos);
        for (int i = 0; i < spawnCount; i++) {
            NbtCompound tag = sd.entity();
            String id = tag.tags().get("id") instanceof String s ? s : null;
            org.domi.furnace.server.entity.EntityType type = id == null ? null : org.domi.furnace.server.entity.EntityType.byName(id);
            if (type == null) {
                delay(level, pos);
                return;
            }
            NbtList posList = tag.tags().get("Pos") instanceof NbtList l && l.elementType() == NbtIo.TAG_DOUBLE ? l : null;
            int n = posList == null ? 0 : posList.size();
            double x = n >= 1 ? ((Number) posList.get(0)).doubleValue() : pos.x() + (random.nextDouble() - random.nextDouble()) * spawnRange + 0.5;
            double y = n >= 2 ? ((Number) posList.get(1)).doubleValue() : pos.y() + random.nextInt(3) - 1;
            double z = n >= 3 ? ((Number) posList.get(2)).doubleValue() : pos.z() + (random.nextDouble() - random.nextDouble()) * spawnRange + 0.5;
            if (!level.noCollision(type.makeBoundingBox(x, y, z))) continue;
            Pos at = new Pos((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
            if (sd.lightLimits() != null) {
                if (!org.domi.furnace.server.entity.spawn.EntityMeta.category(id).isFriendly() && level.difficulty() == 0) continue;
                if (!sd.validPosition(level, at)) continue;
            } else if (!org.domi.furnace.server.entity.spawn.SpawnPlacements.checkSpawnRules(id, level,
                    org.domi.furnace.server.entity.spawn.SpawnReason.SPAWNER, at, level.random())) {
                continue;
            }
            Entity e = loadEntity(level, tag, id, x, y, z);
            if (e == null) {
                delay(level, pos);
                return;
            }
            AABB box = new AABB(pos.x(), pos.y(), pos.z(), pos.x() + 1, pos.y() + 1, pos.z() + 1).inflate(spawnRange);
            Class<?> cls = e.getClass();
            int nearby = level.getEntities(null, box, o -> cls.isInstance(o) && !o.isSpectator()).size();
            if (nearby >= maxNearbyEntities) {
                delay(level, pos);
                return;
            }
            e.moveTo(e.x(), e.y(), e.z(), random.nextFloat() * 360.0F, 0.0F);
            if (e instanceof Mob mob) {
                if (sd.lightLimits() == null && !mob.checkSpawnRules(level, org.domi.furnace.server.entity.spawn.SpawnReason.SPAWNER)
                        || !mob.checkSpawnObstruction(level)) {
                    continue;
                }
                boolean onlyId = tag.tags().size() == 1 && tag.tags().get("id") instanceof String;
                if (onlyId) {
                    // 바닐라 finalizeSpawn(…, SPAWNER, …): 주민 종류(바이옴)·습격자 마녀 참여가 스폰 이유로 갈린다
                    if (mob instanceof org.domi.furnace.server.entity.Villager v) v.setFinalizeReason(org.domi.furnace.server.entity.spawn.SpawnReason.SPAWNER);
                    if (mob instanceof org.domi.furnace.server.entity.Raider r) r.setFinalizeReason(org.domi.furnace.server.entity.spawn.SpawnReason.SPAWNER);
                }
                if (onlyId) mob.finalizeSpawnGroup(level.random(), level.getCurrentDifficultyAt(mob.blockPosition()), null);
                // TODO(엔티티): sd.equipment() 가 있으면 mob.equip(장비 전리품 표)
            }
            if (!level.addFreshEntityWithPassengers(e.spawnReason("SPAWNER"))) {
                delay(level, pos);
                return;
            }
            level.levelEvent(null, 2004, pos, 0);
            level.gameEventFrom("minecraft:entity_place", at, e, level.getBlockState(at));
            if (e instanceof Mob mob) mob.spawnAnim();
            spawned = true;
        }
        if (spawned) delay(level, pos);
    }

    /** EntityType.loadEntityRecursive(tag, level, e → moveTo(x, y, z, 자기 회전)). 태그가 id 뿐이면 load 는 건너뛴다(같은 결과). */
    static Entity loadEntity(ChunkLevel level, NbtCompound tag, String id, double x, double y, double z) {
        Entity e;
        try {
            e = org.domi.furnace.server.entity.Entities.create(level, id);
            if (e == null) return null;
            if (tag.tags().size() > 1) e.load(tag);
        } catch (RuntimeException ex) {
            return null;
        }
        e.moveTo(x, y, z, e.yRot(), e.xRot());
        return e;
    }

    /** delay: 지연을 [min, max) 에서 고르고, 후보에서 다음 데이터를 고른 뒤 블록 이벤트 1. */
    private void delay(BlockLevel level, Pos pos) {
        RandomSource r = level.random();
        if (maxSpawnDelay <= minSpawnDelay) spawnDelay = minSpawnDelay;
        else spawnDelay = minSpawnDelay + r.nextInt(maxSpawnDelay - minSpawnDelay);
        SpawnData pick = pickPotential(r);
        if (pick != null) setNextSpawnData(level, pos, pick);
        broadcastEvent(level, pos, 1);
    }

    /** WeightedRandomList.getRandom: 합이 0 이면 없음, 아니면 nextInt(합) 으로 고른다. */
    private SpawnData pickPotential(RandomSource r) {
        int total = 0;
        for (Weighted w : spawnPotentials) total += w.weight();
        if (total == 0) return null;
        int i = r.nextInt(total);
        for (Weighted w : spawnPotentials) {
            i -= w.weight();
            if (i < 0) return w.data();
        }
        return null;
    }

    private SpawnData getOrCreateNextSpawnData(BlockLevel level, RandomSource random, Pos pos) {
        if (nextSpawnData != null) return nextSpawnData;
        SpawnData pick = pickPotential(random);
        setNextSpawnData(level, pos, pick != null ? pick : new SpawnData());
        return nextSpawnData;
    }

    protected void setNextSpawnData(BlockLevel level, Pos pos, SpawnData d) { nextSpawnData = d; }

    public abstract void broadcastEvent(BlockLevel level, Pos pos, int type);

    /** onEventTriggered: 1 이면 참(서버에서는 아무것도 바꾸지 않는다). */
    public boolean onEventTriggered(int type) { return type == 1; }

    public void load(NbtCompound tag) {
        spawnDelay = tag.getShort("Delay");
        if (tag.tags().get("SpawnData") instanceof NbtCompound c) {
            SpawnData d = SpawnData.parse(c);
            nextSpawnData = d != null ? d : new SpawnData();
        }
        if (tag.tags().get("SpawnPotentials") instanceof NbtList l) {
            List<Weighted> list = new ArrayList<>();
            boolean ok = l.size() == 0 || l.elementType() == NbtIo.TAG_COMPOUND;
            for (Object o : l.items()) {
                if (!ok) break;
                if (!(o instanceof NbtCompound c) || !(c.tags().get("weight") instanceof Number w) || w.intValue() < 0) { ok = false; break; }
                SpawnData d = SpawnData.parse(c.tags().get("data"));
                if (d == null) { ok = false; break; }
                list.add(new Weighted(d, w.intValue()));
            }
            spawnPotentials = ok ? list : new ArrayList<>();
        } else {
            spawnPotentials = new ArrayList<>(List.of(new Weighted(nextSpawnData != null ? nextSpawnData : new SpawnData(), 1)));
        }
        if (tag.containsNumber("MinSpawnDelay")) {
            minSpawnDelay = tag.getShort("MinSpawnDelay");
            maxSpawnDelay = tag.getShort("MaxSpawnDelay");
            spawnCount = tag.getShort("SpawnCount");
        }
        if (tag.containsNumber("MaxNearbyEntities")) {
            maxNearbyEntities = tag.getShort("MaxNearbyEntities");
            requiredPlayerRange = tag.getShort("RequiredPlayerRange");
        }
        if (tag.containsNumber("SpawnRange")) spawnRange = tag.getShort("SpawnRange");
    }

    public NbtCompound save(NbtCompound tag) {
        tag.putShort("Delay", (short) spawnDelay);
        tag.putShort("MinSpawnDelay", (short) minSpawnDelay);
        tag.putShort("MaxSpawnDelay", (short) maxSpawnDelay);
        tag.putShort("SpawnCount", (short) spawnCount);
        tag.putShort("MaxNearbyEntities", (short) maxNearbyEntities);
        tag.putShort("RequiredPlayerRange", (short) requiredPlayerRange);
        tag.putShort("SpawnRange", (short) spawnRange);
        if (nextSpawnData != null) tag.putCompound("SpawnData", nextSpawnData.save());
        NbtList l = new NbtList(NbtIo.TAG_COMPOUND);
        for (Weighted w : spawnPotentials) {
            NbtCompound c = new NbtCompound();
            c.putCompound("data", w.data().save());
            c.putInt("weight", w.weight());
            l.add(c);
        }
        tag.putList("SpawnPotentials", l);
        return tag;
    }
}
