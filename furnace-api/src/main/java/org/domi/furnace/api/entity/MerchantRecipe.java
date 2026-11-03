// 상인 거래 하나(불변). ingredient2 가 없으면 ItemStack.EMPTY. uses/maxUses 는 사용 횟수, xp 는 주민이 얻는 경험치,
// priceMultiplier 는 수요·평판 가격 배율, rewardExp 는 거래한 플레이어가 경험치 구슬을 받는가.
package org.domi.furnace.api.entity;

import org.domi.furnace.api.item.ItemStack;

public record MerchantRecipe(ItemStack ingredient1, ItemStack ingredient2, ItemStack result,
                             int uses, int maxUses, int xp, float priceMultiplier, boolean rewardExp) {

    public MerchantRecipe {
        if (ingredient1 == null || ingredient1.isEmpty()) throw new IllegalArgumentException("첫 재료가 비었다");
        if (result == null || result.isEmpty()) throw new IllegalArgumentException("결과가 비었다");
        if (ingredient2 == null) ingredient2 = ItemStack.EMPTY;
        if (uses < 0 || maxUses < 1) throw new IllegalArgumentException("uses >= 0, maxUses >= 1: " + uses + "/" + maxUses);
    }

    /** 기본값: 사용 0, 최대 maxUses, 경험치 1, 배율 0.05, 경험치 구슬 줌. */
    public static MerchantRecipe of(ItemStack ingredient1, ItemStack ingredient2, ItemStack result, int maxUses) {
        return new MerchantRecipe(ingredient1, ingredient2, result, 0, maxUses, 1, 0.05f, true);
    }
}
