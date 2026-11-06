// 거래 목록(clientbound merchant_offers 0x2D): 창 id, 거래들, 주민 레벨, 경험치, 진행 막대 표시, 재입고 가능.
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.Packet;
import org.domi.furnace.server.trade.ItemCost;
import org.domi.furnace.server.trade.MerchantOffer;
import org.domi.furnace.server.trade.MerchantOffers;

public record MerchantOffersPacket(int containerId, MerchantOffers offers, int villagerLevel, int villagerXp, boolean showProgress, boolean canRestock)
        implements Packet {

    public static final int ID = 0x2D;

    @Override public int id() { return ID; }

    private static void writeCost(ByteBuf buf, ItemCost c) {
        Types.writeVarInt(buf, ItemContext.id("minecraft:item", c.item()));
        Types.writeVarInt(buf, c.count());
        Types.writeVarInt(buf, 0);   // DataComponentPredicate: 조건 없음 (TODO: 조건 컴포넌트가 있는 값)
    }

    @Override
    public void write(ByteBuf buf) {
        Types.writeVarInt(buf, containerId);
        Types.writeVarInt(buf, offers.size());
        for (MerchantOffer o : offers) {
            writeCost(buf, new ItemCost(o.baseCostA().item(), o.baseCostA().count(), o.baseCostA().components()));
            ContainerSetContent.writeSlot(buf, o.result());
            buf.writeBoolean(o.costB().isPresent());
            o.costB().ifPresent(c -> writeCost(buf, c));
            buf.writeBoolean(o.isOutOfStock());
            buf.writeInt(o.uses());
            buf.writeInt(o.maxUses());
            buf.writeInt(o.xp());
            buf.writeInt(o.specialPriceDiff());
            buf.writeFloat(o.priceMultiplier());
            buf.writeInt(o.demand());
        }
        Types.writeVarInt(buf, villagerLevel);
        Types.writeVarInt(buf, villagerXp);
        buf.writeBoolean(showProgress);
        buf.writeBoolean(canRestock);
    }
}
