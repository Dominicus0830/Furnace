// 바닐라 Villager(+AbstractVillager): 거래(Merchant)·경험치와 레벨·마을 사람 데이터(종류·직업·레벨)·저장. AI·일과·평판·거래표 생성은 아직 없다 — 저장된 거래(Offers)만 쓴다.
package org.domi.furnace.server.entity;

import java.util.List;
import java.util.Optional;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.item.ItemNbt;
import org.domi.furnace.server.item.ItemTools;
import org.domi.furnace.server.menu.MenuPlayer;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtList;
import org.domi.furnace.server.net.protocol.play.SetEntityData;
import org.domi.furnace.server.trade.ItemCost;
import org.domi.furnace.server.trade.Merchant;
import org.domi.furnace.server.trade.MerchantOffer;
import org.domi.furnace.server.trade.MerchantOffers;

public final class Villager extends Mob implements Merchant {

    /** AgeableMob 16 아기 여부, AbstractVillager 17 불만 카운터, Villager 18 마을 사람 데이터. */
    public static final int DATA_BABY = 16, DATA_UNHAPPY = 17, DATA_VILLAGER_DATA = 18;
    public static final List<String> TYPES = List.of("desert", "jungle", "plains", "savanna", "snow", "swamp", "taiga");
    public static final List<String> PROFESSIONS = List.of("none", "armorer", "butcher", "cartographer", "cleric", "farmer", "fisherman", "fletcher",
            "leatherworker", "librarian", "mason", "nitwit", "shepherd", "toolsmith", "weaponsmith");
    private static final int[] XP_PER_LEVEL = {0, 0, 10, 70, 150, 250};

    private MenuPlayer tradingPlayer;
    private MerchantOffers offers = new MerchantOffers();
    private int villagerXp;
    private int age;
    private int foodLevel;

    public Villager(ChunkLevel level, int id) { super(EntityType.VILLAGER, level, id); }

    @Override
    protected void createAttributes(org.domi.furnace.server.attribute.AttributeMap map) {
        super.createAttributes(map);
        map.add(org.domi.furnace.server.attribute.Attribute.MOVEMENT_SPEED, 0.5);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_BABY, SetEntityData.SERIALIZER_BOOLEAN, false);
        entityData.define(DATA_UNHAPPY, SetEntityData.SERIALIZER_INT, 0);
        entityData.define(DATA_VILLAGER_DATA, SetEntityData.SERIALIZER_VILLAGER_DATA, new int[] {2, 0, 1});
    }

    @Override
    public boolean isBaby() { return (Boolean) entityData.get(DATA_BABY); }

    public int[] villagerData() { return (int[]) entityData.get(DATA_VILLAGER_DATA); }

    public void setVillagerData(String type, String profession, int lvl) {
        int t = Math.max(0, TYPES.indexOf(type)), p = Math.max(0, PROFESSIONS.indexOf(profession));
        entityData.set(DATA_VILLAGER_DATA, new int[] {t, p, lvl});
    }

    public int villagerLevel() { return villagerData()[2]; }

    public String profession() { return PROFESSIONS.get(villagerData()[1]); }

    // ------------------------------------------------------------------ 거래(Merchant)

    @Override public void setTradingPlayer(MenuPlayer player) { this.tradingPlayer = player; }

    @Override public MenuPlayer tradingPlayer() { return tradingPlayer; }

    @Override public MerchantOffers offers() { return offers; }

    public void setOffers(MerchantOffers o) { offers = o; }

    @Override
    public void notifyTrade(MerchantOffer offer) {
        offer.increaseUses();
        villagerXp += offer.xp();
        if (offer.shouldRewardExp()) {
            int xp = 3 + random.nextInt(4);
            ExperienceOrb.award(level, x(), y() + 0.5, z(), xp);
        }
        // TODO(주민 레벨업): 경험치가 다음 레벨 기준을 넘으면 40틱 뒤 레벨이 오르고 새 거래가 붙는다(거래표 생성 필요)
    }

    @Override
    public void notifyTradeUpdated(ItemStack stack) {
        playSound(stack.isEmpty() ? "entity.villager.no" : "entity.villager.yes", 1.0F, 1.0F);
    }

    @Override public int villagerXp() { return villagerXp; }

    @Override public void overrideXp(int xp) { villagerXp = xp; }

    @Override public boolean showProgressBar() { return true; }

    @Override public String notifyTradeSound() { return "entity.villager.yes"; }

    @Override public boolean canRestock() { return true; }

    /** 거래 화면을 열 수 있나(아기가 아니고, 직업이 있거나 무직이 아니며, 거래가 있고, 누가 거래 중이 아님). */
    public boolean canTrade() {
        return isAlive() && !isBaby() && tradingPlayer == null && !offers.isEmpty() && !profession().equals("none") && !profession().equals("nitwit");
    }

    public Text tradeTitle() {
        String prof = profession();
        Text name = prof.equals("none") ? Text.translatable("entity.minecraft.villager") : Text.translatable("entity.minecraft.villager." + prof);
        return Text.translatable("merchant.title", name, Text.translatable("merchant.level." + villagerLevel()));
    }

    // ------------------------------------------------------------------ 저장

    @Override
    protected void addAdditionalSaveData(NbtCompound tag) {
        super.addAdditionalSaveData(tag);
        int[] d = villagerData();
        NbtCompound vd = new NbtCompound();
        vd.putString("type", "minecraft:" + TYPES.get(d[0]));
        vd.putString("profession", "minecraft:" + PROFESSIONS.get(d[1]));
        vd.putInt("level", d[2]);
        tag.putCompound("VillagerData", vd);
        tag.putInt("Xp", villagerXp);
        tag.putInt("Age", isBaby() ? -24000 : 0);
        tag.putByte("FoodLevel", (byte) foodLevel);
        tag.putBoolean("Willing", false);
        if (!offers.isEmpty()) {
            NbtList recipes = new NbtList((byte) 10);
            for (MerchantOffer o : offers) recipes.add(writeOffer(o));
            NbtCompound wrapper = new NbtCompound();
            wrapper.putList("Recipes", recipes);
            tag.putCompound("Offers", wrapper);
        }
    }

    @Override
    protected void readAdditionalSaveData(NbtCompound tag) {
        super.readAdditionalSaveData(tag);
        if (tag.tags().get("VillagerData") instanceof NbtCompound vd) {
            String type = vd.tags().get("type") instanceof String s ? s.replace("minecraft:", "") : "plains";
            String prof = vd.tags().get("profession") instanceof String s ? s.replace("minecraft:", "") : "none";
            setVillagerData(type, prof, Math.max(1, vd.getIntOr0("level")));
        }
        villagerXp = tag.getIntOr0("Xp");
        entityData.set(DATA_BABY, tag.getIntOr0("Age") < 0);
        foodLevel = tag.tags().get("FoodLevel") instanceof Number n ? n.intValue() : 0;
        offers = new MerchantOffers();
        if (tag.tags().get("Offers") instanceof NbtCompound w && w.tags().get("Recipes") instanceof NbtList list) {
            for (int i = 0; i < list.size(); i++) if (list.get(i) instanceof NbtCompound c) {
                MerchantOffer o = readOffer(c);
                if (o != null) offers.add(o);
            }
        }
    }

    private static NbtCompound writeStack(ItemStack s) { return ItemStackNbt.write(s); }

    private static ItemStack readStack(NbtCompound it) { return ItemStackNbt.read(it); }

    private static NbtCompound writeCost(ItemCost c) {
        NbtCompound t = new NbtCompound();
        t.putString("id", c.item());
        t.putInt("count", c.count());
        return t;
    }

    private static ItemCost readCost(NbtCompound c) {
        return c.tags().get("id") instanceof String id ? new ItemCost(id, Math.max(1, c.tags().get("count") instanceof Number n ? n.intValue() : 1)) : null;
    }

    private static NbtCompound writeOffer(MerchantOffer o) {
        NbtCompound t = new NbtCompound();
        t.putCompound("buy", writeCost(o.baseCostA()));
        o.costB().ifPresent(b -> t.putCompound("buyB", writeCost(b)));
        t.putCompound("sell", writeStack(o.result()));
        t.putInt("uses", o.uses());
        t.putInt("maxUses", o.maxUses());
        t.putBoolean("rewardExp", o.rewardExp());
        t.putInt("xp", o.xp());
        t.putFloat("priceMultiplier", o.priceMultiplier());
        t.putInt("specialPrice", o.specialPriceDiff());
        t.putInt("demand", o.demand());
        return t;
    }

    private static MerchantOffer readOffer(NbtCompound t) {
        ItemCost a = t.tags().get("buy") instanceof NbtCompound b ? readCost(b) : null;
        ItemStack sell = t.tags().get("sell") instanceof NbtCompound s ? readStack(s) : ItemStack.EMPTY;
        if (a == null || sell.isEmpty()) return null;
        ItemCost b = t.tags().get("buyB") instanceof NbtCompound bb ? readCost(bb) : null;
        boolean reward = !t.contains("rewardExp") || t.getBoolean("rewardExp");
        return new MerchantOffer(a, Optional.ofNullable(b), sell, t.getIntOr0("uses"), t.contains("maxUses") ? t.getIntOr0("maxUses") : 4, reward,
                t.getIntOr0("specialPrice"), t.getIntOr0("demand"), t.contains("priceMultiplier") ? t.getFloat("priceMultiplier") : 0.0F,
                t.contains("xp") ? t.getIntOr0("xp") : 1);
    }

    @Override
    public void tick() {
        super.tick();
        age++;
    }
}
