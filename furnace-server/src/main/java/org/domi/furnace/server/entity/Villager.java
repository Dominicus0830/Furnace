// 바닐라 Villager(+AbstractVillager): 거래(Merchant)·거래표 생성(updateTrades)·경험치와 레벨업·재고 보충·마을 사람 데이터(종류·직업·레벨)·저장. AI·일과·평판은 아직 없다.
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
import org.domi.furnace.server.trade.VillagerTrades;

public final class Villager extends Mob implements Merchant {

    /** AgeableMob 16 아기 여부, AbstractVillager 17 불만 카운터, Villager 18 마을 사람 데이터. */
    public static final int DATA_BABY = 16, DATA_UNHAPPY = 17, DATA_VILLAGER_DATA = 18;
    public static final List<String> TYPES = List.of("desert", "jungle", "plains", "savanna", "snow", "swamp", "taiga");
    public static final List<String> PROFESSIONS = List.of("none", "armorer", "butcher", "cartographer", "cleric", "farmer", "fisherman", "fletcher",
            "leatherworker", "librarian", "mason", "nitwit", "shepherd", "toolsmith", "weaponsmith");
    /** VillagerType.byBiome: 바이옴 이름으로 종류를 정한다(기본 plains). */
    public static String typeForBiome(String biome) {
        if (biome == null) return "plains";
        return switch (biome.replace("minecraft:", "")) {
            case "badlands", "desert", "eroded_badlands", "wooded_badlands" -> "desert";
            case "bamboo_jungle", "jungle", "sparse_jungle" -> "jungle";
            case "savanna_plateau", "savanna", "windswept_savanna" -> "savanna";
            case "deep_frozen_ocean", "frozen_ocean", "frozen_river", "ice_spikes", "snowy_beach", "snowy_taiga", "snowy_plains", "grove", "snowy_slopes",
                    "frozen_peaks", "jagged_peaks" -> "snow";
            case "swamp", "mangrove_swamp" -> "swamp";
            case "old_growth_spruce_taiga", "old_growth_pine_taiga", "windswept_hills", "windswept_gravelly_hills", "windswept_forest", "taiga" -> "taiga";
            default -> "plains";
        };
    }

    private static final int[] XP_PER_LEVEL = {0, 10, 70, 150, 250};

    private MenuPlayer tradingPlayer;
    /** 바닐라 offers: 처음 필요할 때(offers()) 거래표로 채운다. null = 아직 안 만들었다. */
    private MerchantOffers offers;
    private int villagerXp;
    private int age;
    private int foodLevel;
    /** 레벨업 대기 틱(updateMerchantTimer)과 대기가 끝나면 레벨을 올릴지(increaseProfessionLevelOnUpdate). */
    private int updateMerchantTimer;
    private boolean increaseProfessionLevelOnUpdate;
    private long lastRestockGameTime;
    private int numberOfRestocksToday;

    public Villager(ChunkLevel level, int id) { super(EntityType.VILLAGER, level, id); }

    @Override
    public void thunderHit(org.domi.furnace.server.block.ChunkLevel level, LightningBolt bolt) {
        if (level.difficulty() != 0) {
            Witch witch = new Witch(level, level.nextEntityId());
            witch.moveTo(x(), y(), z(), yRot(), xRot());
            witch.finalizeSpawn(level.random(), level.getCurrentDifficultyAt(witch.blockPosition()));
            witch.setNoAi(isNoAi());
            witch.persistenceRequired = true;
            level.addFreshEntity(witch);
            discard();
        } else {
            super.thunderHit(level, bolt);
        }
    }

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
        if (p != villagerData()[1]) offers = null;   // 바닐라 setVillagerData: 직업이 바뀌면 거래표를 다시 만든다
        entityData.set(DATA_VILLAGER_DATA, new int[] {t, p, lvl});
    }

    public int villagerLevel() { return villagerData()[2]; }

    public String profession() { return PROFESSIONS.get(villagerData()[1]); }

    // ------------------------------------------------------------------ 거래(Merchant)

    @Override public void setTradingPlayer(MenuPlayer player) { this.tradingPlayer = player; }

    @Override public MenuPlayer tradingPlayer() { return tradingPlayer; }

    /** 바닐라 getOffers: 아직 없으면 직업·레벨의 거래표로 만든다. */
    @Override
    public MerchantOffers offers() {
        if (offers == null) {
            offers = new MerchantOffers();
            updateTrades();
        }
        return offers;
    }

    public void setOffers(MerchantOffers o) { offers = o; }

    /** 바닐라 updateTrades: 현재 직업·레벨의 후보에서 2개를 거래 목록에 더한다. 거래표가 없는 직업(무직·바보)은 아무것도 안 한다. */
    protected void updateTrades() { updateTrades(random); }

    /** updateTrades 를 넘긴 난수로 한다(시험·재현용). 거래 목록이 없으면 만든다. */
    public void updateTrades(org.domi.furnace.server.block.RandomSource r) {
        if (offers == null) offers = new MerchantOffers();
        var table = VillagerTrades.TRADES.get(profession());
        int lvl = villagerLevel();
        if (table == null || lvl < 1 || lvl > table.length) return;
        VillagerTrades.ItemListing[] listings = table[lvl - 1];
        if (listings != null) addOffersFromItemListings(offers, listings, 2, r);
    }

    /** 바닐라 AbstractVillager.addOffersFromItemListings: 후보에서 하나씩 무작위로 뽑아(nextInt(남은 수)) 거래가 만들어지면 더한다. 모두 count 개가 될 때까지. */
    static void addOffersFromItemListings(MerchantOffers target, VillagerTrades.ItemListing[] listings, int count,
                                          org.domi.furnace.server.block.RandomSource r, VillagerTrades.TradeContext ctx) {
        java.util.ArrayList<VillagerTrades.ItemListing> pool = new java.util.ArrayList<>(java.util.Arrays.asList(listings));
        int added = 0;
        while (added < count && !pool.isEmpty()) {
            MerchantOffer offer = pool.remove(r.nextInt(pool.size())).getOffer(ctx, r);
            if (offer != null) {
                target.add(offer);
                added++;
            }
        }
    }

    private void addOffersFromItemListings(MerchantOffers target, VillagerTrades.ItemListing[] listings, int count, org.domi.furnace.server.block.RandomSource r) {
        addOffersFromItemListings(target, listings, count, r, new VillagerTrades.TradeContext(TYPES.get(villagerData()[0]), level.lootData(),
                (int) Math.floor(x()), (int) Math.floor(y()), (int) Math.floor(z()), level.explorerMaps()));
    }

    /** 바닐라 increaseMerchantCareer: 레벨을 하나 올리고 그 레벨의 거래를 더한다. */
    private void increaseMerchantCareer() {
        int[] d = villagerData();
        entityData.set(DATA_VILLAGER_DATA, new int[] {d[0], d[1], d[2] + 1});
        updateTrades();
    }

    /** 바닐라 shouldIncreaseLevel: 경험치가 지금 레벨의 다음 단계 기준에 이르렀다(레벨 1~4 만). */
    private boolean shouldIncreaseLevel() {
        int lvl = villagerLevel();
        return lvl >= 1 && lvl < 5 && villagerXp >= XP_PER_LEVEL[lvl];
    }

    /** 바닐라 needsToRestock: 한 번이라도 쓴 거래가 있다. */
    public boolean needsToRestock() {
        for (MerchantOffer o : offers()) if (o.needsRestock()) return true;
        return false;
    }

    /**
     * 바닐라 restock: 수요를 갱신하고 사용 횟수를 0 으로 되돌린다.
     * TODO(평판): updateSpecialPrices(gossip)는 평판이 없어 생략. 침대·직업 블록·시간에 따른 호출 시점(shouldRestock)은 일과(brain) 범위.
     */
    public void restock() {
        for (MerchantOffer o : offers()) o.updateDemand();
        for (MerchantOffer o : offers()) o.resetUses();
        lastRestockGameTime = level.gameTime();
        numberOfRestocksToday++;
    }

    @Override
    public void notifyTrade(MerchantOffer offer) {
        offer.increaseUses();
        int xp = 3 + random.nextInt(4);
        villagerXp += offer.xp();
        if (shouldIncreaseLevel()) {
            updateMerchantTimer = 40;
            increaseProfessionLevelOnUpdate = true;
            xp += 5;
        }
        if (offer.shouldRewardExp()) ExperienceOrb.award(level, x(), y() + 0.5, z(), xp);
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
        return isAlive() && !isBaby() && tradingPlayer == null && !offers().isEmpty() && !profession().equals("none") && !profession().equals("nitwit");
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
        tag.putLong("LastRestock", lastRestockGameTime);
        tag.putInt("RestocksToday", numberOfRestocksToday);
        if (!offers().isEmpty()) {
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
        lastRestockGameTime = tag.tags().get("LastRestock") instanceof Number n ? n.longValue() : 0L;
        numberOfRestocksToday = tag.getIntOr0("RestocksToday");
        offers = null;
        if (tag.tags().get("Offers") instanceof NbtCompound w && w.tags().get("Recipes") instanceof NbtList list) {
            offers = new MerchantOffers();
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

    static NbtCompound writeOffer(MerchantOffer o) {
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

    static MerchantOffer readOffer(NbtCompound t) {
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
        // 바닐라 customServerAiStep: 거래 중이 아니면 레벨업 대기를 줄이고, 끝나면 레벨을 올린 뒤 재생 효과를 건다
        if (tradingPlayer == null && updateMerchantTimer > 0) {
            updateMerchantTimer--;
            if (updateMerchantTimer <= 0) {
                if (increaseProfessionLevelOnUpdate) {
                    increaseMerchantCareer();
                    increaseProfessionLevelOnUpdate = false;
                }
                addEffect(new org.domi.furnace.server.effect.MobEffectInstance(org.domi.furnace.server.effect.MobEffect.byName("regeneration"), 200, 0));
            }
        }
    }
}
