// 바닐라 MerchantOffer: 거래 하나 — 값 A(+ 값 B) → 결과, 사용 횟수·최대·경험치·특가·가격 배율·수요.
package org.domi.furnace.server.trade;

import java.util.Optional;
import org.domi.furnace.server.entity.ItemStack;

public final class MerchantOffer {

    private final ItemCost baseCostA;
    private final Optional<ItemCost> costB;
    private final ItemStack result;
    private int uses;
    private final int maxUses;
    private final boolean rewardExp;
    private int specialPriceDiff;
    private int demand;
    private final float priceMultiplier;
    private final int xp;

    public MerchantOffer(ItemCost a, Optional<ItemCost> b, ItemStack result, int uses, int maxUses, boolean rewardExp, int specialPrice, int demand,
            float priceMultiplier, int xp) {
        this.baseCostA = a;
        this.costB = b;
        this.result = result;
        this.uses = uses;
        this.maxUses = maxUses;
        this.rewardExp = rewardExp;
        this.specialPriceDiff = specialPrice;
        this.demand = demand;
        this.priceMultiplier = priceMultiplier;
        this.xp = xp;
    }

    /** 기본 거래: 4번까지, 경험치 1, 배율 0. */
    public MerchantOffer(ItemCost a, ItemStack result, int maxUses, int xp, float priceMultiplier) {
        this(a, Optional.empty(), result, 0, maxUses, true, 0, 0, priceMultiplier, xp);
    }

    public MerchantOffer copy() {
        return new MerchantOffer(baseCostA, costB, result, uses, maxUses, rewardExp, specialPriceDiff, demand, priceMultiplier, xp);
    }

    public ItemCost baseCostA() { return baseCostA; }

    /** 수요·특가를 반영한 값 A 개수(바닐라 calculateCost): count + max(0, floor(count * demand * multiplier)) + special, 1..최대 스택. */
    public int costACount() {
        int c = baseCostA.count();
        int bump = Math.max(0, (int) Math.floor(c * demand * priceMultiplier));
        return Math.clamp((long) c + bump + specialPriceDiff, 1, baseCostA.maxStack());
    }

    /** getCostA: 값 A 스택. */
    public ItemStack costA() { return baseCostA.stack().withCount(costACount()); }

    public ItemStack baseCostAStack() { return baseCostA.stack(); }

    public Optional<ItemCost> costB() { return costB; }

    public ItemStack costBStack() { return costB.map(ItemCost::stack).orElse(ItemStack.EMPTY); }

    public ItemStack result() { return result; }

    public ItemStack assemble() { return result; }

    public int uses() { return uses; }

    public int maxUses() { return maxUses; }

    public int xp() { return xp; }

    public int demand() { return demand; }

    public int specialPriceDiff() { return specialPriceDiff; }

    public float priceMultiplier() { return priceMultiplier; }

    public boolean rewardExp() { return rewardExp; }

    public void updateDemand() { demand = demand + uses - (maxUses - uses); }

    public void increaseUses() { uses++; }

    public void resetUses() { uses = 0; }

    public void addToSpecialPriceDiff(int d) { specialPriceDiff += d; }

    public void resetSpecialPriceDiff() { specialPriceDiff = 0; }

    public void setSpecialPriceDiff(int d) { specialPriceDiff = d; }

    public void setToOutOfStock() { uses = maxUses; }

    public boolean isOutOfStock() { return uses >= maxUses; }

    public boolean shouldRewardExp() { return rewardExp; }

    /** satisfiedBy(a, b): 값 A 개수 이상을 내놓았고, 값 B 가 없으면 b 는 비어야 하고 있으면 B 를 만족해야 한다. */
    public boolean satisfiedBy(ItemStack a, ItemStack b) {
        if (!baseCostA.test(a) || a.count() < costACount()) return false;
        return costB.isEmpty() ? b.isEmpty() : costB.get().test(b) && b.count() >= costB.get().count();
    }

    /**
     * take(a, b): 맞으면 값만큼 줄인 새 스택 둘을 돌려주고, 아니면 null. (바닐라는 제자리에서 shrink)
     */
    public ItemStack[] take(ItemStack a, ItemStack b) {
        if (!satisfiedBy(a, b)) return null;
        ItemStack na = a.withCount(a.count() - costACount());
        ItemStack nb = b;
        if (costB.isPresent() && costB.get().count() > 0) nb = b.withCount(b.count() - costB.get().count());
        return new ItemStack[]{na, nb};
    }
}
