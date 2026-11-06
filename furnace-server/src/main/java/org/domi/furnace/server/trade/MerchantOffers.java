// 바닐라 MerchantOffers: 거래 목록 + 재료로 거래 찾기.
package org.domi.furnace.server.trade;

import java.util.ArrayList;
import org.domi.furnace.server.entity.ItemStack;

public final class MerchantOffers extends ArrayList<MerchantOffer> {

    /** getRecipeFor(a, b, index): index 가 1..size-1 이면 그 거래가 맞을 때만, 아니면 처음으로 맞는 거래. */
    public MerchantOffer getRecipeFor(ItemStack a, ItemStack b, int index) {
        if (index > 0 && index < size()) {
            MerchantOffer o = get(index);
            return o.satisfiedBy(a, b) ? o : null;
        }
        for (MerchantOffer o : this) if (o.satisfiedBy(a, b)) return o;
        return null;
    }

    public MerchantOffers copy() {
        MerchantOffers c = new MerchantOffers();
        for (MerchantOffer o : this) c.add(o.copy());
        return c;
    }
}
