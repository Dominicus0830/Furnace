// 바닐라 LootPool — 조건 → (rolls + floor(bonus_rolls·행운))번 가중치 뽑기.
package org.domi.furnace.server.loot;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public record LootPool(List<LootEntry> entries, List<LootCondition> conditions, LootFunction functions,
                       NumberProvider rolls, NumberProvider bonusRolls) {

    static LootPool parse(JsonObject o, LootRegistries regs) {
        return new LootPool(LootJson.list(o, "entries", x -> LootEntry.parse(x, regs)), LootCondition.list(o, "conditions", regs),
                LootFunction.compose(LootFunction.list(o, "functions", regs)), NumberProvider.parse(LootJson.req(o, "rolls")),
                o.has("bonus_rolls") ? NumberProvider.parse(o.get("bonus_rolls")) : new NumberProvider.Constant(0.0f));
    }

    void addRandomItems(Consumer<LootStack> out, LootContext ctx) {
        if (!LootCondition.allOf(conditions, ctx)) return;
        Consumer<LootStack> decorated = LootEntry.decorate(functions, out, ctx);
        int n = rolls.getInt(ctx) + Mth.floor(bonusRolls.getFloat(ctx) * ctx.luck());
        for (int i = 0; i < n; i++) addRandomItem(decorated, ctx);
    }

    private void addRandomItem(Consumer<LootStack> out, LootContext ctx) {
        List<LootEntry.Candidate> candidates = new ArrayList<>();
        int[] total = {0};
        for (LootEntry e : entries) {
            e.expand(ctx, c -> {
                int w = c.weight(ctx.luck());
                if (w > 0) {
                    candidates.add(c);
                    total[0] += w;
                }
            });
        }
        int size = candidates.size();
        if (total[0] == 0 || size == 0) return;
        if (size == 1) {
            candidates.get(0).create(out, ctx);
            return;
        }
        int r = ctx.random().nextInt(total[0]);
        for (LootEntry.Candidate c : candidates) {
            r -= c.weight(ctx.luck());
            if (r < 0) {
                c.create(out, ctx);
                return;
            }
        }
    }
}
