// 바닐라 TrialSpawnerConfig: 시련 생성기 한 쪽(일반/불길한)의 설정. 모든 필드가 lenientOptional 이라 틀린 값은 기본값이 된다.
// 저장은 DFU optionalFieldOf(이름, 기본값) 규칙을 따른다 — 기본값과 같은 필드는 쓰지 않는다(가정: 바닐라 코덱 인코더 동작).
package org.domi.furnace.server.block.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.domi.furnace.server.block.RandomSource;
import org.domi.furnace.server.block.entity.BaseSpawner.SpawnData;
import org.domi.furnace.server.block.entity.BaseSpawner.Weighted;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtIo;
import org.domi.furnace.server.net.io.nbt.NbtList;

public record TrialSpawnerConfig(int spawnRange, float totalMobs, float simultaneousMobs, float totalMobsAddedPerPlayer,
        float simultaneousMobsAddedPerPlayer, int ticksBetweenSpawn, List<Weighted> spawnPotentials, List<WeightedId> lootTablesToEject,
        String itemsToDropWhenOminous) {

    /** 가중치 붙은 전리품 표 이름(SimpleWeightedRandomList&lt;ResourceKey&lt;LootTable&gt;&gt; 항목). */
    public record WeightedId(String id, int weight) {}

    /** 기본 구성(TrialSpawnerConfig.DEFAULT). */
    public static final TrialSpawnerConfig DEFAULT = new TrialSpawnerConfig(4, 6.0F, 2.0F, 2.0F, 1.0F, 40, List.of(),
            List.of(new WeightedId("minecraft:spawners/trial_chamber/consumables", 1), new WeightedId("minecraft:spawners/trial_chamber/key", 1)),
            "minecraft:spawners/trial_chamber/items_to_drop_when_ominous");

    /** ticksBetweenItemSpawners: 불길한 아이템 생성기 사이 간격(상수 160). */
    public long ticksBetweenItemSpawners() { return 160L; }

    /** calculateTargetTotalMobs(players) = floor(totalMobs + totalMobsAddedPerPlayer × players). */
    public int calculateTargetTotalMobs(int additionalPlayers) { return (int) Math.floor(totalMobs + totalMobsAddedPerPlayer * additionalPlayers); }

    /** calculateTargetSimultaneousMobs(players). */
    public int calculateTargetSimultaneousMobs(int additionalPlayers) {
        return (int) Math.floor(simultaneousMobs + simultaneousMobsAddedPerPlayer * additionalPlayers);
    }

    /** WeightedRandomList.getRandom: 합이 0 이면 없음, 아니면 nextInt(합) 으로 고른다(난수 한 번). */
    static <T> T pick(List<T> items, java.util.function.ToIntFunction<T> weight, RandomSource r) {
        long sum = 0;
        for (T t : items) sum += weight.applyAsInt(t);
        if (sum == 0) return null;
        int i = r.nextInt((int) sum);
        for (T t : items) {
            i -= weight.applyAsInt(t);
            if (i < 0) return t;
        }
        return null;
    }

    /** spawnPotentials 에서 하나(없으면 null). */
    public SpawnData pickSpawnData(RandomSource r) {
        Weighted w = pick(spawnPotentials, Weighted::weight, r);
        return w == null ? null : w.data();
    }

    /** lootTablesToEject 에서 하나(없으면 null). */
    public String pickLootTableToEject(RandomSource r) {
        WeightedId w = pick(lootTablesToEject, WeightedId::weight, r);
        return w == null ? null : w.id();
    }

    // ------------------------------------------------------------------ NBT

    private static boolean num(Object o) { return o instanceof Number; }

    private static int intIn(Object o, int min, int max, int def) {
        if (!num(o)) return def;
        int v = ((Number) o).intValue();
        return v < min || v > max ? def : v;
    }

    private static float floatMin0(Object o, float def) {
        if (!num(o)) return def;
        float v = ((Number) o).floatValue();
        return v < 0.0F || Float.isNaN(v) ? def : v;
    }

    /** 코덱 해석(lenient — 필드마다 실패하면 기본값). */
    public static TrialSpawnerConfig load(NbtCompound t) {
        TrialSpawnerConfig d = DEFAULT;
        Object pots = t.tags().get("spawn_potentials");
        Object eject = t.tags().get("loot_tables_to_eject");
        Object drop = t.tags().get("items_to_drop_when_ominous");
        return new TrialSpawnerConfig(
                intIn(t.tags().get("spawn_range"), 1, 128, d.spawnRange),
                floatMin0(t.tags().get("total_mobs"), d.totalMobs),
                floatMin0(t.tags().get("simultaneous_mobs"), d.simultaneousMobs),
                floatMin0(t.tags().get("total_mobs_added_per_player"), d.totalMobsAddedPerPlayer),
                floatMin0(t.tags().get("simultaneous_mobs_added_per_player"), d.simultaneousMobsAddedPerPlayer),
                intIn(t.tags().get("ticks_between_spawn"), 0, Integer.MAX_VALUE, d.ticksBetweenSpawn),
                pots == null ? List.of() : parsePotentials(pots),
                eject == null ? d.lootTablesToEject : parseEject(eject, d.lootTablesToEject),
                drop instanceof String s && BaseSpawner.normalizeId(s) != null ? BaseSpawner.normalizeId(s) : d.itemsToDropWhenOminous);
    }

    private static List<Weighted> parsePotentials(Object o) {
        if (!(o instanceof NbtList l) || l.size() > 0 && l.elementType() != NbtIo.TAG_COMPOUND) return List.of();
        List<Weighted> out = new ArrayList<>();
        for (Object e : l.items()) {
            if (!(e instanceof NbtCompound c) || !(c.tags().get("weight") instanceof Number w) || w.intValue() < 0) return List.of();
            SpawnData sd = SpawnData.parse(c.tags().get("data"));
            if (sd == null) return List.of();
            out.add(new Weighted(sd, w.intValue()));
        }
        return out;
    }

    private static List<WeightedId> parseEject(Object o, List<WeightedId> def) {
        if (!(o instanceof NbtList l) || l.size() > 0 && l.elementType() != NbtIo.TAG_COMPOUND) return def;
        List<WeightedId> out = new ArrayList<>();
        for (Object e : l.items()) {
            if (!(e instanceof NbtCompound c) || !(c.tags().get("weight") instanceof Number w) || w.intValue() < 0
                    || !(c.tags().get("data") instanceof String s) || BaseSpawner.normalizeId(s) == null) return def;
            out.add(new WeightedId(BaseSpawner.normalizeId(s), w.intValue()));
        }
        return out;
    }

    /** 기본값과 다른 필드만 쓴다. */
    public NbtCompound save() {
        NbtCompound t = new NbtCompound();
        TrialSpawnerConfig d = DEFAULT;
        if (spawnRange != d.spawnRange) t.putInt("spawn_range", spawnRange);
        if (Float.compare(totalMobs, d.totalMobs) != 0) t.putFloat("total_mobs", totalMobs);
        if (Float.compare(simultaneousMobs, d.simultaneousMobs) != 0) t.putFloat("simultaneous_mobs", simultaneousMobs);
        if (Float.compare(totalMobsAddedPerPlayer, d.totalMobsAddedPerPlayer) != 0) t.putFloat("total_mobs_added_per_player", totalMobsAddedPerPlayer);
        if (Float.compare(simultaneousMobsAddedPerPlayer, d.simultaneousMobsAddedPerPlayer) != 0)
            t.putFloat("simultaneous_mobs_added_per_player", simultaneousMobsAddedPerPlayer);
        if (ticksBetweenSpawn != d.ticksBetweenSpawn) t.putInt("ticks_between_spawn", ticksBetweenSpawn);
        if (!spawnPotentials.isEmpty()) {
            NbtList l = new NbtList(NbtIo.TAG_COMPOUND);
            for (Weighted w : spawnPotentials) {
                NbtCompound c = new NbtCompound();
                c.putCompound("data", w.data().save());
                c.putInt("weight", w.weight());
                l.add(c);
            }
            t.putList("spawn_potentials", l);
        }
        if (!lootTablesToEject.equals(d.lootTablesToEject)) {
            NbtList l = new NbtList(NbtIo.TAG_COMPOUND);
            for (WeightedId w : lootTablesToEject) {
                NbtCompound c = new NbtCompound();
                c.putString("data", w.id());
                c.putInt("weight", w.weight());
                l.add(c);
            }
            t.putList("loot_tables_to_eject", l);
        }
        if (!Objects.equals(itemsToDropWhenOminous, d.itemsToDropWhenOminous)) t.putString("items_to_drop_when_ominous", itemsToDropWhenOminous);
        return t;
    }

    /** equals — Weighted/SpawnData 는 배열을 품어 레코드 equals 가 맞지 않으므로, 기본값을 뺀 저장 꼴로 비교한다. */
    public boolean sameAs(TrialSpawnerConfig o) { return org.domi.furnace.server.net.io.nbt.NbtTags.equal(save(), o.save()); }
}
