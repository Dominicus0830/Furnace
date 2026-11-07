// 바닐라 WanderingTrader(+AbstractVillager 의 거래 부분): 방랑 상인. 거래표는 WANDERING_TRADER_TRADES 에서 처음 열 때 5+1개,
// 사라지기까지 despawnDelay 틱, 밤엔 투명 물약·낮엔 우유, 위협에서 달아남, 사라질 위치(wander target)로 걷기.
// AbstractVillager 의 8칸 Inventory(NBT "Inventory", /item 의 villager.0~7). TODO(트리거): 거래 발전 과제(CriteriaTriggers.TRADE)는 생략.
package org.domi.furnace.server.entity;

import java.util.EnumSet;
import java.util.Map;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.effect.MobEffectInstance;
import org.domi.furnace.server.entity.ai.Goal;
import org.domi.furnace.server.entity.ai.goal.AvoidEntityGoal;
import org.domi.furnace.server.entity.ai.goal.FloatGoal;
import org.domi.furnace.server.entity.ai.goal.InteractGoal;
import org.domi.furnace.server.entity.ai.goal.LookAtMobGoal;
import org.domi.furnace.server.entity.ai.goal.LookAtTradingPlayerGoal;
import org.domi.furnace.server.entity.ai.goal.MoveTowardsRestrictionGoal;
import org.domi.furnace.server.entity.ai.goal.PanicGoal;
import org.domi.furnace.server.entity.ai.goal.TradeWithPlayerGoal;
import org.domi.furnace.server.entity.ai.goal.UseItemGoal;
import org.domi.furnace.server.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.item.ItemTools;
import org.domi.furnace.server.menu.MenuPlayer;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtList;
import org.domi.furnace.server.net.protocol.play.SetEntityData;
import org.domi.furnace.server.trade.Merchant;
import org.domi.furnace.server.trade.MerchantOffer;
import org.domi.furnace.server.trade.MerchantOffers;
import org.domi.furnace.server.trade.VillagerTrades;

public final class WanderingTrader extends PathfinderMob implements Merchant, InventoryCarrier {
    /** canBeLeashed(디컴파일 y): AbstractVillager(cmf)는 못 맨다. */
    @Override
    public boolean canBeLeashed() { return false; }



    /** AgeableMob 16 아기 여부, AbstractVillager 17 불만 카운터. */
    public static final int DATA_BABY = 16, DATA_UNHAPPY = 17;
    private static final int NUMBER_OF_TRADE_OFFERS = 5;

    private MenuPlayer tradingPlayer;
    /** null = 아직 안 만들었다(처음 필요할 때 만든다). */
    private MerchantOffers offers;
    private int despawnDelay;
    /** AbstractVillager.inventory: 8칸(방랑 상인은 줍지 않지만 저장·명령으로 채울 수 있다). */
    private final MobInventory inventory = new MobInventory(8);
    private Pos wanderTarget;

    public WanderingTrader(ChunkLevel level, int id) {
        super(EntityType.WANDERING_TRADER, level, id);
        setPathfindingMalus(org.domi.furnace.server.entity.ai.navigation.PathType.DANGER_FIRE, 16.0F);   // AbstractVillager 생성자
        setPathfindingMalus(org.domi.furnace.server.entity.ai.navigation.PathType.DAMAGE_FIRE, -1.0F);
    }

    /** AbstractVillager.die: 죽은 뒤 거래 중이던 플레이어를 놓는다(stopTrading). */
    @Override
    public void die(Damage source) {
        super.die(source);
        setTradingPlayer(null);
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
    }

    @Override public boolean isBaby() { return false; }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(0, new UseItemGoal<>(this, ItemTools.with(new ItemStack(ItemContext.id("minecraft:item", "potion"), 1),
                "minecraft:potion_contents", Map.of("potion", "minecraft:invisibility")), "entity.wandering_trader.disappeared",
                t -> t.level().isNight() && !t.isInvisible()));
        goalSelector.addGoal(0, new UseItemGoal<>(this, new ItemStack(ItemContext.id("minecraft:item", "milk_bucket"), 1),
                "entity.wandering_trader.reappeared", t -> t.level().isDay() && t.isInvisible()));
        goalSelector.addGoal(1, new TradeWithPlayerGoal<>(this));
        goalSelector.addGoal(1, new AvoidEntityGoal(this, Zombie.class, 8.0F, 0.5, 0.5));
        goalSelector.addGoal(1, new AvoidEntityGoal(this, Evoker.class, 12.0F, 0.5, 0.5));
        goalSelector.addGoal(1, new AvoidEntityGoal(this, Vindicator.class, 8.0F, 0.5, 0.5));
        goalSelector.addGoal(1, new AvoidEntityGoal(this, Vex.class, 8.0F, 0.5, 0.5));
        goalSelector.addGoal(1, new AvoidEntityGoal(this, Pillager.class, 15.0F, 0.5, 0.5));
        goalSelector.addGoal(1, new AvoidEntityGoal(this, Illusioner.class, 12.0F, 0.5, 0.5));
        goalSelector.addGoal(1, new AvoidEntityGoal(this, Zoglin.class, 10.0F, 0.5, 0.5));
        goalSelector.addGoal(1, new PanicGoal(this, 0.5));
        goalSelector.addGoal(1, new LookAtTradingPlayerGoal<>(this));
        goalSelector.addGoal(2, new WanderToPositionGoal(this, 2.0, 0.35));
        goalSelector.addGoal(4, new MoveTowardsRestrictionGoal(this, 0.35));
        goalSelector.addGoal(8, new WaterAvoidingRandomStrollGoal(this, 0.35));
        goalSelector.addGoal(9, new InteractGoal(this, 3.0F, 1.0F));
        goalSelector.addGoal(10, new LookAtMobGoal(this, Mob.class, 8.0F, 0.02F));
    }

    // ------------------------------------------------------------------ 거래(Merchant)

    @Override public void setTradingPlayer(MenuPlayer player) { this.tradingPlayer = player; }

    @Override public MenuPlayer tradingPlayer() { return tradingPlayer; }

    public boolean isTrading() { return tradingPlayer != null; }

    /** 바닐라 getOffers: 아직 없으면 거래표로 만든다. */
    @Override
    public MerchantOffers offers() {
        if (offers == null) {
            offers = new MerchantOffers();
            updateTrades(random);
        }
        return offers;
    }

    public void setOffers(MerchantOffers o) { offers = o; }

    /**
     * 바닐라 updateTrades(trade_rebalance 끔): 첫 묶음에서 5개(addOffersFromItemListings), 둘째 묶음에서 nextInt(길이)로 1개.
     * 지도 거래는 locate 가 없어 표에서 null 을 돌려주므로 더해지지 않는다. 난수는 넘긴 것을 쓴다(시험·재현용).
     */
    public void updateTrades(org.domi.furnace.server.block.RandomSource r) {
        if (offers == null) offers = new MerchantOffers();
        VillagerTrades.ItemListing[] first = VillagerTrades.WANDERING_TRADER_TRADES[0], second = VillagerTrades.WANDERING_TRADER_TRADES[1];
        var ctx = new VillagerTrades.TradeContext("plains", level.lootData());
        Villager.addOffersFromItemListings(offers, first, NUMBER_OF_TRADE_OFFERS, r, ctx);
        MerchantOffer offer = second[r.nextInt(second.length)].getOffer(ctx, r);
        if (offer != null) offers.add(offer);
    }

    /** 바닐라 notifyTrade + rewardTradeXp(WanderingTrader): 경험치를 줄 거래면 3+nextInt(4) 짜리 구슬. */
    @Override
    public void notifyTrade(MerchantOffer offer) {
        offer.increaseUses();
        if (offer.shouldRewardExp()) {
            int xp = 3 + random.nextInt(4);
            ExperienceOrb.award(level, x(), y() + 0.5, z(), xp);
        }
    }

    @Override
    public void notifyTradeUpdated(ItemStack stack) {
        playSound(stack.isEmpty() ? "entity.wandering_trader.no" : "entity.wandering_trader.yes", 1.0F, 1.0F);
    }

    @Override public int villagerXp() { return 0; }

    @Override public void overrideXp(int xp) { }

    @Override public boolean showProgressBar() { return false; }

    @Override public String notifyTradeSound() { return "entity.wandering_trader.yes"; }

    /** mobInteract 의 조건: 살아 있고 거래 중이 아니며 거래가 있다(손에 스폰 알이면 부르는 쪽이 거른다). */
    public boolean canTrade() { return isAlive() && !isTrading() && !offers().isEmpty(); }

    /**
     * 바닐라 WanderingTrader.mobInteract: 주민 스폰 알을 들었거나 죽었거나 거래 중이거나 아기면 넘긴다. 아니면 주 손일 때 대화 통계를 올리고,
     * 거래가 없으면 소비만, 있으면 거래 화면을 연다(OPEN_TRADE: 부르는 쪽이 연다).
     */
    public Villager.TradeInteract mobInteract(Player p, boolean mainHand, boolean villagerSpawnEgg) {
        if (villagerSpawnEgg || !isAlive() || isTrading() || isBaby()) return Villager.TradeInteract.PASS;
        if (mainHand) p.awardStat("minecraft:talked_to_villager", 1);
        return offers().isEmpty() ? Villager.TradeInteract.CONSUME : Villager.TradeInteract.OPEN_TRADE;
    }

    public Text tradeTitle() { return hasCustomName() ? getCustomName() : Text.translatable("entity.minecraft.wandering_trader"); }

    // ------------------------------------------------------------------ 소리

    @Override public String getAmbientSound() { return isTrading() ? "entity.wandering_trader.trade" : "entity.wandering_trader.ambient"; }

    @Override protected String getHurtSound(Damage source) { return "entity.wandering_trader.hurt"; }

    @Override protected String getDeathSound() { return "entity.wandering_trader.death"; }

    // ------------------------------------------------------------------ 사라짐·마시기

    @Override public MobInventory getInventory() { return inventory; }

    public void setDespawnDelay(int v) { despawnDelay = v; }

    public int despawnDelay() { return despawnDelay; }

    public void setWanderTarget(Pos p) { wanderTarget = p; }

    public Pos wanderTarget() { return wanderTarget; }

    @Override public boolean removeWhenFarAway(double distSqr) { return false; }

    @Override
    public void tick() {
        super.tick();
        // 바닐라 maybeDespawn: 거래 중이 아니면 줄고, 0 이 되면 사라진다
        if (despawnDelay > 0 && !isTrading() && --despawnDelay == 0) discard();
    }

    @Override
    protected int getUseDuration(ItemStack s) {
        String n = ItemTools.itemName(s);
        return n.equals("minecraft:potion") || n.equals("minecraft:milk_bucket") ? 32 : super.getUseDuration(s);
    }

    /** 마시기 끝(바닐라 completeUsingItem): 물약은 효과를 받고 빈 병, 우유는 효과를 모두 지우고 양동이가 손에 남는다. */
    @Override
    protected void completeUsingItem() {
        EquipmentSlot slot = getUsedItemHand() == 0 ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
        ItemStack held = getItemBySlot(slot);
        String leftover = null;
        switch (ItemTools.itemName(held)) {
            case "minecraft:potion" -> {
                for (MobEffectInstance e : Consuming.potionEffects(held)) addEffect(e);
                leftover = "glass_bottle";
                gameEvent(org.domi.furnace.server.world.gameevent.GameEvent.DRINK);   // PotionItem.finishUsingItem
            }
            case "minecraft:milk_bucket" -> {
                removeAllEffects();
                leftover = "bucket";
            }
            default -> { }
        }
        if (leftover != null) setItemSlot(slot, new ItemStack(ItemContext.id("minecraft:item", leftover), 1));
        stopUsingItem();
    }

    // ------------------------------------------------------------------ 저장

    @Override
    protected void addAdditionalSaveData(NbtCompound tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Age", 0);
        tag.putInt("ForcedAge", 0);
        if (!offers().isEmpty()) {
            NbtList recipes = new NbtList((byte) 10);
            for (MerchantOffer o : offers) recipes.add(Villager.writeOffer(o));
            NbtCompound wrapper = new NbtCompound();
            wrapper.putList("Recipes", recipes);
            tag.putCompound("Offers", wrapper);
        }
        writeInventoryToSave(tag);   // AbstractVillager.addAdditionalSaveData: Offers 다음 Inventory
        tag.putInt("DespawnDelay", despawnDelay);
        if (wanderTarget != null) tag.putIntArray("wander_target", new int[] {wanderTarget.x(), wanderTarget.y(), wanderTarget.z()});
    }

    @Override
    protected void readAdditionalSaveData(NbtCompound tag) {
        super.readAdditionalSaveData(tag);
        if (tag.tags().get("Offers") instanceof NbtCompound w && w.tags().get("Recipes") instanceof NbtList list) {
            offers = new MerchantOffers();
            for (int i = 0; i < list.size(); i++) if (list.get(i) instanceof NbtCompound c) {
                MerchantOffer o = Villager.readOffer(c);
                if (o != null) offers.add(o);
            }
        }
        readInventoryFromSave(tag);
        if (tag.tags().get("DespawnDelay") instanceof Number n) despawnDelay = n.intValue();
        if (tag.tags().get("wander_target") instanceof int[] a && a.length == 3) wanderTarget = new Pos(a[0], a[1], a[2]);
    }

    // ------------------------------------------------------------------ 목표

    /** 바닐라 WanderingTrader.WanderToPositionGoal: 사라질 위치가 stopDistance 보다 멀면 그쪽으로 걷는다(멀면 10칸씩 끊어서). */
    static final class WanderToPositionGoal extends Goal {
        final WanderingTrader trader;
        final double stopDistance, speedModifier;

        WanderToPositionGoal(WanderingTrader trader, double stopDistance, double speedModifier) {
            this.trader = trader;
            this.stopDistance = stopDistance;
            this.speedModifier = speedModifier;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public void stop() {
            trader.setWanderTarget(null);
            trader.getNavigation().stop();
        }

        @Override
        public boolean canUse() {
            Pos t = trader.wanderTarget();
            return t != null && wantsToMove(t, stopDistance);
        }

        @Override
        public void tick() {
            Pos t = trader.wanderTarget();
            if (t != null && trader.getNavigation().isDone()) {
                if (wantsToMove(t, 10.0)) {
                    double dx = t.x() - trader.x(), dy = t.y() - trader.y(), dz = t.z() - trader.z();
                    double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    if (len < 1.0E-5) { dx = 0.0; dy = 0.0; dz = 0.0; } else { dx /= len; dy /= len; dz /= len; }
                    trader.getNavigation().moveTo(dx * 10.0 + trader.x(), dy * 10.0 + trader.y(), dz * 10.0 + trader.z(), speedModifier);
                } else {
                    trader.getNavigation().moveTo(t.x(), t.y(), t.z(), speedModifier);
                }
            }
        }

        /** !target.closerToCenterThan(trader.position, dist). */
        private boolean wantsToMove(Pos t, double dist) {
            double dx = t.x() + 0.5 - trader.x(), dy = t.y() + 0.5 - trader.y(), dz = t.z() + 0.5 - trader.z();
            return !(dx * dx + dy * dy + dz * dz < dist * dist);
        }
    }
}
