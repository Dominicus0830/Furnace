// 바닐라 ZombieVillager: 주민 데이터(종류·직업·레벨)와 거래·경험치를 갖고, 약화 효과 + 황금 사과로 치료(3600~6000틱)하면 주민으로 돌아온다.
// 소문(Gossips)은 NBT 목록으로 들고 있다가 치료되면 주민에게 넘기고, 치료한 플레이어에게 ZOMBIE_VILLAGER_CURED 평판.
package org.domi.furnace.server.entity;

import java.util.UUID;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.effect.MobEffect;
import org.domi.furnace.server.effect.MobEffectInstance;
import org.domi.furnace.server.item.ItemTools;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtList;
import org.domi.furnace.server.net.protocol.play.SetEntityData;
import org.domi.furnace.server.trade.MerchantOffer;
import org.domi.furnace.server.trade.MerchantOffers;

public class ZombieVillager extends Zombie {
    public static final int DATA_CONVERTING_ID = 19, DATA_VILLAGER_DATA = 20;
    private int villagerConversionTime;
    private UUID conversionStarter;
    private MerchantOffers tradeOffers;
    private int villagerXp;
    /** 바닐라 gossips(Tag): 주민 시절 소문 NBT 그대로. */
    private NbtList gossips;

    public ZombieVillager(ChunkLevel level, int id) {
        super(EntityType.ZOMBIE_VILLAGER, level, id);
        int[] d = villagerData();
        setVillagerData(Villager.TYPES.get(d[0]), Villager.PROFESSIONS.get(random.nextInt(Villager.PROFESSIONS.size())), d[2]);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_CONVERTING_ID, SetEntityData.SERIALIZER_BOOLEAN, false);
        entityData.define(DATA_VILLAGER_DATA, SetEntityData.SERIALIZER_VILLAGER_DATA, new int[] {2, 0, 1});
    }

    public int[] villagerData() { return (int[]) entityData.get(DATA_VILLAGER_DATA); }

    public void setVillagerData(String type, String profession, int lvl) {
        int t = Math.max(0, Villager.TYPES.indexOf(type)), p = Math.max(0, Villager.PROFESSIONS.indexOf(profession));
        int[] old = villagerData();
        if (old[1] != p && tradeOffers != null) tradeOffers = null;   // 직업이 바뀌면 거래 초기화
        entityData.set(DATA_VILLAGER_DATA, new int[] {t, p, lvl});
    }

    public void setOffers(MerchantOffers offers) { tradeOffers = offers; }

    public void setVillagerXp(int xp) { villagerXp = xp; }

    public void setGossips(NbtList g) { gossips = g; }

    public boolean isConverting() { return (Boolean) entityData.get(DATA_CONVERTING_ID); }

    @Override protected boolean convertsInWater() { return false; }

    @Override
    public boolean removeWhenFarAway(double distSqr) { return !isConverting() && villagerXp == 0; }

    @Override
    public void tick() {
        if (isAlive() && isConverting()) {
            int speed = getConversionProgress();
            villagerConversionTime -= speed;
            if (villagerConversionTime <= 0) finishConversion();
        }
        super.tick();
    }

    @Override
    public boolean interact(Player p, int hand, ItemStack held, org.domi.furnace.server.world.Region region) {
        if (!held.isEmpty() && ItemTools.itemName(held).equals("minecraft:golden_apple")) {
            if (hasEffect(MobEffect.WEAKNESS)) {
                if (!p.vitals().isCreative()) {
                    var inv = p.inventory();
                    inv.set(hand == 0 ? inv.heldWindowSlot() : 45, held.withCount(held.count() - 1));
                }
                startConverting(p.uuid(), random.nextInt(2401) + 3600);
                return true;
            }
            return false;
        }
        return super.interact(p, hand, held, region);
    }

    private void startConverting(UUID starter, int time) {
        conversionStarter = starter;
        villagerConversionTime = time;
        entityData.set(DATA_CONVERTING_ID, true);
        removeEffect(MobEffect.WEAKNESS);
        addEffect(new MobEffectInstance(MobEffect.STRENGTH, time, Math.min(level.difficulty() - 1, 0)));
        broadcastEntityEvent((byte) 16);
    }

    private void finishConversion() {
        Villager v = convertTo(new Villager(level, level.nextEntityId()), false);
        if (v == null) return;
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot == EquipmentSlot.MAINHAND || slot == EquipmentSlot.OFFHAND) continue;
            ItemStack item = getItemBySlot(slot);
            if (!item.isEmpty() && !hasBindingCurse(item)) {
                // 주민은 장비 칸이 없어 아이템을 인벤토리 대신 바닥에 둔다(바닐라: slotId 300+ 인벤토리 칸에 넣음)
                v.spawnAtLocation(item);
            }
        }
        int[] d = villagerData();
        v.setVillagerData(Villager.TYPES.get(d[0]), Villager.PROFESSIONS.get(d[1]), d[2]);
        if (gossips != null) v.getGossips().update(gossips);
        if (tradeOffers != null) v.setOffers(tradeOffers);
        v.overrideXp(villagerXp);
        if (conversionStarter != null) {
            UUID starter = conversionStarter;
            var zombieSnap = KillCredit.lootOf(this);
            var villagerSnap = KillCredit.lootOf(v);
            level.forEachPlayer(p -> {
                if (!p.uuid().equals(starter)) return;
                p.triggerAdvancement(a -> a.triggerCured(zombieSnap, villagerSnap));   // 바닐라: 평판 이벤트 직전에 cured_zombie_villager
                v.onReputationEvent(Villager.ReputationEvent.ZOMBIE_VILLAGER_CURED, p);
            });
        }
        v.addEffect(new MobEffectInstance(MobEffect.NAUSEA, 200, 0));
        if (!isSilent()) level.levelEvent(null, 1027, blockPosition(), 0);
    }

    private static boolean hasBindingCurse(ItemStack item) {
        return ItemTools.enchantmentLevel(item, "minecraft:binding_curse") > 0;
    }

    private int getConversionProgress() {
        int i = 1;
        if (random.nextFloat() < 0.01F) {
            int j = 0;
            for (int x = (int) x() - 4; x < (int) x() + 4 && j < 14; x++) {
                for (int y = (int) y() - 4; y < (int) y() + 4 && j < 14; y++) {
                    for (int z = (int) z() - 4; z < (int) z() + 4 && j < 14; z++) {
                        int st = level.getBlockState(new Pos(x, y, z));
                        if (B.is(st, "minecraft:iron_bars") || B.isTag(st, "minecraft:beds")) {
                            if (random.nextFloat() < 0.3F) i++;
                            j++;
                        }
                    }
                }
            }
        }
        return i;
    }

    @Override
    public float getVoicePitch() {
        return isBaby() ? (random.nextFloat() - random.nextFloat()) * 0.2F + 2.0F : (random.nextFloat() - random.nextFloat()) * 0.2F + 1.0F;
    }

    @Override public String getAmbientSound() { return "entity.zombie_villager.ambient"; }

    @Override protected String getHurtSound(Damage source) { return "entity.zombie_villager.hurt"; }

    @Override protected String getDeathSound() { return "entity.zombie_villager.death"; }

    @Override
    public void finalizeSpawn(org.domi.furnace.server.block.RandomSource r, DifficultyInstance difficulty) {
        applyBiomeType();
        super.finalizeSpawn(r, difficulty);
    }

    @Override
    public void finalizeSpawnForConversion(org.domi.furnace.server.block.RandomSource r, DifficultyInstance difficulty) {
        applyBiomeType();
        super.finalizeSpawnForConversion(r, difficulty);
    }

    private void applyBiomeType() {
        int[] d = villagerData();
        Pos p = blockPosition();
        setVillagerData(Villager.typeForBiome(level.biomeAt(p.x(), p.y(), p.z())), Villager.PROFESSIONS.get(d[1]), d[2]);
    }

    @Override
    protected void addAdditionalSaveData(NbtCompound tag) {
        super.addAdditionalSaveData(tag);
        int[] d = villagerData();
        NbtCompound vd = new NbtCompound();
        vd.putString("type", "minecraft:" + Villager.TYPES.get(d[0]));
        vd.putString("profession", "minecraft:" + Villager.PROFESSIONS.get(d[1]));
        vd.putInt("level", d[2]);
        tag.putCompound("VillagerData", vd);
        if (tradeOffers != null && !tradeOffers.isEmpty()) {
            NbtList recipes = new NbtList((byte) 10);
            for (MerchantOffer o : tradeOffers) recipes.add(Villager.writeOffer(o));
            NbtCompound wrapper = new NbtCompound();
            wrapper.putList("Recipes", recipes);
            tag.putCompound("Offers", wrapper);
        }
        if (gossips != null) tag.putList("Gossips", gossips);
        tag.putInt("ConversionTime", isConverting() ? villagerConversionTime : -1);
        if (conversionStarter != null) tag.putIntArray("ConversionPlayer", uuidToInts(conversionStarter));
        tag.putInt("Xp", villagerXp);
    }

    @Override
    protected void readAdditionalSaveData(NbtCompound tag) {
        super.readAdditionalSaveData(tag);
        if (tag.tags().get("VillagerData") instanceof NbtCompound vd) {
            String type = vd.tags().get("type") instanceof String s ? s.replace("minecraft:", "") : "plains";
            String prof = vd.tags().get("profession") instanceof String s ? s.replace("minecraft:", "") : "none";
            setVillagerData(type, prof, Math.max(1, vd.getIntOr0("level")));
        }
        if (tag.tags().get("Offers") instanceof NbtCompound w && w.tags().get("Recipes") instanceof NbtList list) {
            tradeOffers = new MerchantOffers();
            for (int i = 0; i < list.size(); i++) if (list.get(i) instanceof NbtCompound c) {
                MerchantOffer o = Villager.readOffer(c);
                if (o != null) tradeOffers.add(o);
            }
        }
        if (tag.tags().get("Gossips") instanceof NbtList g) gossips = g;
        if (tag.contains("ConversionTime") && tag.getInt("ConversionTime") > -1) {
            UUID starter = tag.tags().get("ConversionPlayer") instanceof int[] u && u.length == 4
                    ? new UUID((long) u[0] << 32 | u[1] & 0xFFFFFFFFL, (long) u[2] << 32 | u[3] & 0xFFFFFFFFL) : null;
            startConverting(starter, tag.getInt("ConversionTime"));
        }
        if (tag.contains("Xp")) villagerXp = tag.getIntOr0("Xp");
    }
}
