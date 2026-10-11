// 바닐라 LootTable — 풀들을 돌려 아이템을 만들고(표 함수 적용), 최대 스택으로 나누고, 컨테이너를 채운다(fill).
package org.domi.furnace.server.loot;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;
import org.domi.furnace.server.data.ResourceId;
import org.domi.furnace.server.world.gen.carver.LegacyRandom;
import org.domi.furnace.server.world.gen.noise.NoiseRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class LootTable {

    private static final Logger log = LoggerFactory.getLogger(LootTable.class);

    public static final LootTable EMPTY = new LootTable("minecraft:empty", null, List.of(), List.of());

    private final String paramSet;
    private final ResourceId randomSequence;
    private final List<LootPool> pools;
    private final LootFunction functions;

    LootTable(String paramSet, ResourceId randomSequence, List<LootPool> pools, List<LootFunction> functions) {
        this.paramSet = paramSet;
        this.randomSequence = randomSequence;
        this.pools = pools;
        this.functions = LootFunction.compose(functions);
    }

    static LootTable parse(JsonObject o, LootRegistries regs) {
        String type = o.has("type") ? LootJson.id(o.get("type").getAsString()) : "minecraft:generic";
        ResourceId seq = o.has("random_sequence") ? ResourceId.parse(o.get("random_sequence").getAsString()) : null;
        return new LootTable(type, seq, LootJson.list(o, "pools", x -> LootPool.parse(x.getAsJsonObject(), regs)),
                LootFunction.list(o, "functions", regs));
    }

    public String paramSet() { return paramSet; }

    public ResourceId randomSequence() { return randomSequence; }

    /** 바닐라 LootContext.Builder.create — 시드 0 이 아니면 Legacy(시드), 아니면 random_sequence, 아니면 월드 난수. */
    public LootContext context(LootData data, LootWorld world, LootParams params, long seed) {
        NoiseRandom r = seed != 0L ? new LegacyRandom(seed) : randomSequence != null ? world.randomSequence(randomSequence) : world.random();
        return new LootContext(data, world, params, r);
    }

    /** 바닐라 getRandomItemsRaw — 표 함수까지 적용, 스택 나누기 없음. */
    public void getRandomItemsRaw(LootContext ctx, Consumer<LootStack> out) {
        if (ctx.pushVisited(this)) {
            Consumer<LootStack> decorated = LootEntry.decorate(functions, out, ctx);
            for (LootPool pool : pools) pool.addRandomItems(decorated, ctx);
            ctx.popVisited(this);
        } else {
            log.warn("Detected infinite loop in loot tables");
        }
    }

    /** 바닐라 getRandomItems — 최대 스택 크기로 나눠 넘긴다. */
    public void getRandomItems(LootContext ctx, Consumer<LootStack> out) { getRandomItemsRaw(ctx, splitter(out)); }

    public List<LootStack> getRandomItems(LootContext ctx) {
        List<LootStack> list = new ArrayList<>();
        getRandomItems(ctx, list::add);
        return list;
    }

    /** 바닐라 createStackSplitter. */
    static Consumer<LootStack> splitter(Consumer<LootStack> out) {
        return s -> {
            if (s.count() < s.maxStackSize()) {
                out.accept(s);
            } else {
                int remaining = s.count();
                while (remaining > 0) {
                    LootStack part = s.copyWithCount(Math.min(s.maxStackSize(), remaining));
                    remaining -= part.count();
                    out.accept(part);
                }
            }
        };
    }

    /**
     * 바닐라 fill — 컨테이너 칸(빈 칸은 null 또는 빈 스택)을 채운다. 난수 순서: 전리품 → 빈 칸 섞기 → 나누기 → 아이템 섞기.
     */
    public void fill(LootStack[] container, LootData data, LootWorld world, LootParams params, long seed) {
        LootContext ctx = context(data, world, params, seed);
        List<LootStack> items = getRandomItems(ctx);
        NoiseRandom r = ctx.random();
        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < container.length; i++) if (container[i] == null || container[i].isEmpty()) slots.add(i);
        Mth.shuffle(slots, r);
        shuffleAndSplit(items, slots.size(), r);
        for (LootStack s : items) {
            if (slots.isEmpty()) {
                log.warn("Tried to over-fill a container");
                return;
            }
            container[slots.remove(slots.size() - 1)] = s.isEmpty() ? LootStack.empty() : s;
        }
    }

    private static void shuffleAndSplit(List<LootStack> items, int emptySlots, NoiseRandom r) {
        List<LootStack> splittable = new ArrayList<>();
        Iterator<LootStack> it = items.iterator();
        while (it.hasNext()) {
            LootStack s = it.next();
            if (s.isEmpty()) it.remove();
            else if (s.count() > 1) {
                splittable.add(s);
                it.remove();
            }
        }
        while (emptySlots - items.size() - splittable.size() > 0 && !splittable.isEmpty()) {
            LootStack stack = splittable.remove(Mth.nextInt(r, 0, splittable.size() - 1));
            int i = Mth.nextInt(r, 1, stack.count() / 2);
            LootStack split = stack.split(i);
            if (stack.count() > 1 && r.nextBoolean()) splittable.add(stack); else items.add(stack);
            if (split.count() > 1 && r.nextBoolean()) splittable.add(split); else items.add(split);
        }
        items.addAll(splittable);
        Mth.shuffle(items, r);
    }
}
