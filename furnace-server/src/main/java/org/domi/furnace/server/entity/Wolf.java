// 바닐라 Wolf: 길들이기(뼈 1/3), 앉기, 목걸이 색, 분노(NeutralMob), 구걸. 길들이면 체력 40.
// TODO: 젖어 털기 입자, 죽음 알림.
package org.domi.furnace.server.entity;

import java.util.UUID;
import org.domi.furnace.server.attribute.Attribute;
import org.domi.furnace.server.attribute.AttributeMap;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.damage.DamageType;
import org.domi.furnace.server.entity.ai.goal.*;
import org.domi.furnace.server.item.ItemTags;
import org.domi.furnace.server.item.ItemTools;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.protocol.play.SetEntityData;

public class Wolf extends TamableAnimal implements NeutralMob {
    /** canBeLeashed(디컴파일 y): 늑대(cgh)는 분노 중이면 못 맨다. */
    @Override
    public boolean canBeLeashed() { return !isAngry(); }


    public static final int DATA_INTERESTED_ID = 19, DATA_COLLAR_COLOR = 20, DATA_REMAINING_ANGER_TIME = 21, DATA_VARIANT_ID = 22;

    /** 바닐라 wolf_variant 레지스트리 순서(알파벳 = 데이터 팩·플러그인 변종이 없을 때의 네트워크 번호). 기본값 pale. */
    public static final String[] VARIANTS = {"ashen", "black", "chestnut", "pale", "rusty", "snowy", "spotted", "striped", "woods"};
    public static final int VARIANT_PALE = 3;
    private static final String REGISTRY = "minecraft:wolf_variant";

    /** 지금 레지스트리에서 pale 의 번호(WolfVariants.DEFAULT). 데이터 팩·플러그인 변종이 앞에 끼면 3 이 아닐 수 있다. */
    public static int paleIndex() {
        int i = org.domi.furnace.server.net.registry.RegistrySet.current().indexOf(REGISTRY, "minecraft:pale");
        return i >= 0 ? i : VARIANT_PALE;
    }

    /** 번호 → 변종 id(범위 밖이면 pale). */
    public static String variantName(int index) {
        var l = org.domi.furnace.server.net.registry.RegistrySet.current().entries(REGISTRY);
        return index >= 0 && index < l.size() ? l.get(index) : "minecraft:pale";
    }

    /** 변종 id → 번호(없으면 -1). namespace 가 없으면 minecraft. */
    public static int variantIndex(String name) {
        return org.domi.furnace.server.net.registry.RegistrySet.current().indexOf(REGISTRY, name.indexOf(':') < 0 ? "minecraft:" + name : name);
    }
    private boolean isWet, isShaking;
    private float shakeAnim, shakeAnimO;
    private UUID persistentAngerTarget;

    public Wolf(ChunkLevel level, int id) {
        super(EntityType.WOLF, level, id);
        setTame(false, false);
        setPathfindingMalus(org.domi.furnace.server.entity.ai.navigation.PathType.POWDER_SNOW, -1.0F);
        setPathfindingMalus(org.domi.furnace.server.entity.ai.navigation.PathType.DANGER_POWDER_SNOW, -1.0F);
    }

    @Override
    protected void createAttributes(AttributeMap map) {
        super.createAttributes(map);
        map.add(Attribute.MOVEMENT_SPEED, 0.3F).add(Attribute.MAX_HEALTH, 8.0).add(Attribute.ATTACK_DAMAGE, 4.0);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_INTERESTED_ID, SetEntityData.SERIALIZER_BOOLEAN, false);
        entityData.define(DATA_COLLAR_COLOR, SetEntityData.SERIALIZER_INT, DyeColor.RED.id());
        entityData.define(DATA_REMAINING_ANGER_TIME, SetEntityData.SERIALIZER_INT, 0);
        entityData.define(DATA_VARIANT_ID, SetEntityData.SERIALIZER_WOLF_VARIANT, paleIndex());
    }

    public int getVariant() { return (Integer) entityData.get(DATA_VARIANT_ID); }

    public void setVariant(int v) { entityData.set(DATA_VARIANT_ID, v); }

    /**
     * WolfVariants.getSpawnVariant(cgj.a): 레지스트리 순서로 변종 데이터(wolf_variant/*.json)의 biomes 가 이 바이옴을 담는 첫 것,
     * 없으면 pale. 난수는 쓰지 않는다. 데이터 팩·플러그인 변종도 같은 규칙으로 고른다.
     */
    public static int variantForBiome(String biome) {
        var dyn = org.domi.furnace.server.data.DynamicRegistries.current();
        var defs = dyn == null ? java.util.Map.<String, com.google.gson.JsonObject>of() : dyn.definitions(REGISTRY);
        if (defs.isEmpty()) return vanillaVariantForBiome(biome);
        var order = dyn.registries().entries(REGISTRY);
        String vanilla = "minecraft:" + VARIANTS[vanillaVariantForBiome(biome)];
        for (int i = 0; i < order.size(); i++) {
            String id = order.get(i);
            var def = defs.get(id);
            // 정의가 없는 항목은 바닐라 데이터 없이 돈 내장 변종 — 내장 표로 본다
            if (def != null ? biomesContain(def.get("biomes"), biome) : id.equals(vanilla) && (!id.equals("minecraft:pale") || biome.equals("minecraft:taiga"))) return i;
        }
        return paleIndex();
    }

    /** HolderSet<Biome> 꼴(태그 "#id", id 하나, id 목록)이 바이옴을 담는가. 태그는 지금 데이터 팩 태그로 본다. */
    static boolean biomesContain(com.google.gson.JsonElement set, String biome) {
        if (set == null || biome == null) return false;
        if (set.isJsonArray()) {
            for (var e : set.getAsJsonArray()) if (e.isJsonPrimitive() && normalize(e.getAsString()).equals(biome)) return true;
            return false;
        }
        if (!set.isJsonPrimitive()) return false;
        String s = set.getAsString();
        if (!s.startsWith("#")) return normalize(s).equals(biome);
        String tag = normalize(s.substring(1));
        if (org.domi.furnace.server.item.ItemContext.hasTag("minecraft:worldgen/biome", tag)) {
            Integer id = org.domi.furnace.server.item.ItemContext.id("minecraft:worldgen/biome", biome);
            return id != null && org.domi.furnace.server.item.ItemContext.isInTag("minecraft:worldgen/biome", tag, id);
        }
        try {
            return org.domi.furnace.server.world.gen.BiomeTags.is(biome, tag);
        } catch (RuntimeException e) {
            return false;   // 모르는 태그
        }
    }

    private static String normalize(String id) { return id.indexOf(':') < 0 ? "minecraft:" + id : id; }

    /** 바닐라 변종만 있을 때의 결과(데이터 팩을 읽지 않고 돌 때). 바닐라 변종들의 바이옴은 겹치지 않아 순서와 무관하다. */
    static int vanillaVariantForBiome(String biome) {
        return switch (biome) {
            case "minecraft:taiga" -> 3;
            case "minecraft:grove" -> 5;
            case "minecraft:old_growth_pine_taiga" -> 1;
            case "minecraft:snowy_taiga" -> 0;
            case "minecraft:forest" -> 8;
            case "minecraft:old_growth_spruce_taiga" -> 2;
            default -> org.domi.furnace.server.world.gen.BiomeTags.is(biome, "minecraft:is_savanna") ? 6
                    : org.domi.furnace.server.world.gen.BiomeTags.is(biome, "minecraft:is_jungle") ? 4
                    : org.domi.furnace.server.world.gen.BiomeTags.is(biome, "minecraft:is_badlands") ? 7 : VARIANT_PALE;
        };
    }

    /** 같은 무리가 변종을 나눠 쓰는 그룹 데이터(cgh.b). */
    public static final class WolfGroupData extends AgeableMob.GroupData {
        public final int variant;
        public WolfGroupData(int variant) { super(false); this.variant = variant; }
    }

    /** Wolf.finalizeSpawn: 무리 데이터가 있으면 그 변종, 없으면 이 칸 바이옴의 변종을 고르고 무리 데이터를 만든다. 그 뒤 super. */
    @Override
    public GroupData finalizeSpawn(org.domi.furnace.server.block.RandomSource r, DifficultyInstance difficulty, GroupData data) {
        int v;
        if (data instanceof WolfGroupData w) {
            v = w.variant;
        } else {
            Pos p = blockPosition();
            v = variantForBiome(level.biomeAt(p.x(), p.y(), p.z()));
            data = new WolfGroupData(v);
        }
        setVariant(v);
        return super.finalizeSpawn(r, difficulty, data);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new FloatGoal(this));
        goalSelector.addGoal(1, new TamablePanicGoal(this, 1.5, "minecraft:panic_environmental_causes"));
        goalSelector.addGoal(2, new SitWhenOrderedToDoGoal(this));
        goalSelector.addGoal(3, new WolfAvoidEntityGoal(this, Llama.class, 24.0F, 1.5, 1.5));
        goalSelector.addGoal(4, new LeapAtTargetGoal(this, 0.4F));
        goalSelector.addGoal(5, new MeleeAttackGoal(this, 1.0, true));
        goalSelector.addGoal(6, new FollowOwnerGoal(this, 1.0, 10.0F, 2.0F));
        goalSelector.addGoal(7, new BreedGoal(this, 1.0));
        goalSelector.addGoal(8, new WaterAvoidingRandomStrollGoal(this, 1.0));
        goalSelector.addGoal(9, new BegGoal(this, 8.0F));
        goalSelector.addGoal(10, new LookAtPlayerGoal(this, 8.0F));
        goalSelector.addGoal(10, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new OwnerHurtByTargetGoal(this));
        targetSelector.addGoal(2, new OwnerHurtTargetGoal(this));
        targetSelector.addGoal(3, new HurtByTargetGoal(this).setAlertOthers());
        targetSelector.addGoal(4, new NearestAttackableTargetGoal(this, Player.class, 10, true, false, t -> isAngryAt(t)));
        targetSelector.addGoal(5, new NonTameRandomTargetGoal(this, Animal.class, false, t -> t instanceof Entity e && PREY.contains(e.type().name())));
        targetSelector.addGoal(6, new NonTameRandomTargetGoal(this, Turtle.class, false, Turtle.BABY_ON_LAND_SELECTOR));
        targetSelector.addGoal(7, new NearestAttackableTargetGoal(this, AbstractSkeleton.class, false));
        targetSelector.addGoal(8, new ResetUniversalAngerTargetGoal<>(this, true));
    }

    private static final java.util.Set<String> PREY = java.util.Set.of("minecraft:sheep", "minecraft:rabbit", "minecraft:fox");

    public DyeColor getCollarColor() { return DyeColor.byId((Integer) entityData.get(DATA_COLLAR_COLOR)); }

    private void setCollarColor(DyeColor c) { entityData.set(DATA_COLLAR_COLOR, c.id()); }

    public boolean isInterested() { return (Boolean) entityData.get(DATA_INTERESTED_ID); }

    public void setIsInterested(boolean v) { entityData.set(DATA_INTERESTED_ID, v); }

    @Override
    protected void applyTamingSideEffects() {
        if (isTame()) {
            attributes().get(Attribute.MAX_HEALTH).setBaseValue(40.0);
            setHealth(40.0F);
        } else {
            attributes().get(Attribute.MAX_HEALTH).setBaseValue(8.0);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (!isWet && isInWaterRainOrBubble() ) isWet = true;
        if (isWet && !isInWaterRainOrBubble() && !isShaking && !getNavigation().isInProgress() && onGround()) {
            isShaking = true;
            shakeAnim = 0.0F;
            shakeAnimO = 0.0F;
            broadcastEntityEvent((byte) 8);
        }
        if (isShaking) {
            if (shakeAnim == 0.0F) {   // cgh:191 털기 시작 틱에 소리와 ENTITY_ACTION(소리 높이는 난수 두 번)
                playSound("entity.wolf.shake", 0.4F, (random.nextFloat() - random.nextFloat()) * 0.2F + 1.0F);
                gameEvent(org.domi.furnace.server.world.gameevent.GameEvent.ENTITY_ACTION);
            }
            shakeAnimO = shakeAnim;
            shakeAnim += 0.05F;
            if (shakeAnimO >= 2.0F) {
                isWet = false;
                isShaking = false;
                shakeAnimO = 0.0F;
                shakeAnim = 0.0F;
            }
        }
        updatePersistentAnger(level.gameRule("universalAnger"));
    }

    @Override
    public boolean hurtBy(Damage source, float amount, Targetable attacker) {
        if (isInvulnerableTo(source)) return false;
        setOrderedToSit(false);
        return super.hurtBy(source, amount, attacker);
    }

    @Override
    public boolean doHurtTarget(Targetable t) {
        return super.doHurtTarget(t);
    }

    @Override
    public boolean interact(Player p, int hand, ItemStack held, org.domi.furnace.server.world.Region region) {
        String name = held.isEmpty() ? "" : ItemTools.itemName(held);
        if (isTame()) {
            if (isFood(held) && health() < attributeValueF(Attribute.MAX_HEALTH)) {
                usePlayerItem(p, hand, held);
                float nutrition = ItemTools.component(held, "minecraft:food") instanceof java.util.Map<?, ?> f && f.get("nutrition") instanceof Number n ? n.floatValue() : 1.0F;
                heal(2.0F * nutrition);
                return true;
            }
            boolean dye = name.endsWith("_dye") && isOwnedBy(p);
            if (!dye) {
                // 몸통 칸 늑대 갑옷(cgh.b): 장착 -> 가위로 벗기기 -> 아르마딜로 비늘 수리 -> 기본 상호작용/앉기
                if (name.equals("minecraft:wolf_armor") && isOwnedBy(p) && getItemBySlot(EquipmentSlot.BODY).isEmpty() && !isBaby()) {
                    setBodyArmorItem(held.withCount(1));
                    var inv = p.inventory();
                    inv.set(hand == 0 ? inv.heldWindowSlot() : 45, held.withCount(held.count() - 1));   // split(1): 크리에이티브도 줄어든다
                    return true;
                }
                if (name.equals("minecraft:shears") && isOwnedBy(p) && hasArmor()
                        && (ItemTools.enchantmentLevel(getItemBySlot(EquipmentSlot.BODY), "minecraft:binding_curse") == 0 || p.hasInfiniteMaterials())) {
                    hurtHeldItem(p, hand, held, 1);
                    playSound("item.armor.unequip_wolf", 1.0F, 1.0F);
                    ItemStack body = getItemBySlot(EquipmentSlot.BODY);
                    setBodyArmorItem(ItemStack.EMPTY);
                    spawnAtLocation(body);
                    return true;
                }
                if (name.equals("minecraft:armadillo_scute") && isInSittingPose() && hasArmor() && isOwnedBy(p)
                        && ItemTools.isDamageable(getItemBySlot(EquipmentSlot.BODY)) && ItemTools.damage(getItemBySlot(EquipmentSlot.BODY)) > 0) {
                    held = held.withCount(held.count() - 1);   // stack.shrink(1): 크리에이티브도 줄인다
                    var inv = p.inventory();
                    inv.set(hand == 0 ? inv.heldWindowSlot() : 45, held);
                    playSound("item.wolf_armor.repair", 1.0F, 1.0F);
                    ItemStack body = getItemBySlot(EquipmentSlot.BODY);
                    int repair = (int) (ItemTools.maxDamage(body) * 0.125F);
                    updateItemSlot(EquipmentSlot.BODY, ItemTools.withDamage(body, Math.max(0, ItemTools.damage(body) - repair)));
                    return true;
                }
                boolean handled = super.interact(p, hand, held, region);
                if (!handled && isOwnedBy(p)) {
                    setOrderedToSit(!isOrderedToSit());
                    jumping = false;
                    navigation.stop();
                    setTarget(null);
                    return true;
                }
                return handled;
            }
            String c = name.substring("minecraft:".length(), name.length() - "_dye".length());
            for (DyeColor d : DyeColor.values()) {
                if (d.key().equals(c) && d != getCollarColor()) {
                    setCollarColor(d);
                    usePlayerItem(p, hand, held);
                    return true;
                }
            }
            return super.interact(p, hand, held, region);
        } else if (name.equals("minecraft:bone") && !isAngry()) {
            usePlayerItem(p, hand, held);
            tryToTame(p);
            return true;
        }
        return super.interact(p, hand, held, region);
    }

    /** hasArmor(cgh.gA): 몸통 칸이 늑대 갑옷이다. */
    public boolean hasArmor() { return ItemTools.itemName(getItemBySlot(EquipmentSlot.BODY)).equals("minecraft:wolf_armor"); }

    /** Mob.setBodyArmorItem: 몸통 칸을 넣는다(떨굼 확률 2) — 장착 소리·equip/unequip 은 setItemSlot 의 onEquipItem 이. */
    public void setBodyArmorItem(ItemStack s) { setItemSlotAndDropWhenKilled(EquipmentSlot.BODY, s); }

    private boolean armorAbsorbs(Damage source) { return hasArmor() && !DamageType.keyHasTag(source.type(), "bypasses_wolf_armor"); }

    @Override protected String getHurtSound(Damage source) { return armorAbsorbs(source) ? "item.wolf_armor.damage" : "entity.wolf.hurt"; }

    /** Crackiness.WOLF_ARMOR.byDamage: 남은 비율(float) < 0.32 매우 금, < 0.69 금, < 0.95 약간 금, 아니면 없음. 닳는 아이템이 아니면 0. */
    static int crackiness(ItemStack s) {
        if (!ItemTools.isDamageable(s)) return 0;
        int max = ItemTools.maxDamage(s);
        float f = (float) (max - ItemTools.damage(s)) / max;
        return f < 0.32F ? 3 : f < 0.69F ? 2 : f < 0.95F ? 1 : 0;
    }

    /**
     * Wolf.actuallyHurt(cgh.f): 갑옷을 입고 bypasses_wolf_armor 가 아니면 늑대는 피해를 받지 않고 갑옷이 ceil(피해) 만큼 닳는다.
     * 금 단계가 바뀌면 깨지는 소리와 비늘 입자(20개).
     */
    @Override
    protected void actuallyHurt(Damage source, float amount) {
        if (!armorAbsorbs(source)) {
            super.actuallyHurt(source, amount);
            return;
        }
        ItemStack body = getItemBySlot(EquipmentSlot.BODY);
        int before = crackiness(body);
        hurtEquipped(EquipmentSlot.BODY, (int) Math.ceil(amount));
        if (before != crackiness(getItemBySlot(EquipmentSlot.BODY))) {
            playSound("item.wolf_armor.crack", 1.0F, 1.0F);
            level.sendParticles(new org.domi.furnace.server.net.protocol.play.ParticleOptions.Item(
                    new ItemStack(org.domi.furnace.server.item.ItemContext.id("minecraft:item", "armadillo_scute"), 1)),
                    x(), y() + 1.0, z(), 20, 0.2, 0.1, 0.2, 0.1);
        }
    }

    private void tryToTame(Player p) {
        if (random.nextInt(3) == 0 && tameAllowed(p)) {
            tame(p);
            navigation.stop();
            setTarget(null);
            setOrderedToSit(true);
            broadcastEntityEvent((byte) 7);
        } else {
            broadcastEntityEvent((byte) 6);
        }
    }

    private float attributeValueF(Attribute a) { return (float) attributeValue(a); }

    @Override public boolean isFood(ItemStack s) { return ItemTags.is(s, "minecraft:wolf_food"); }

    @Override
    public boolean canMate(Animal o) {
        if (o == this) return false;
        if (!isTame()) return false;
        if (!(o instanceof Wolf w)) return false;
        if (!w.isTame()) return false;
        return w.isInSittingPose() ? false : isInLove() && w.isInLove();
    }

    @Override
    public AgeableMob getBreedOffspring(ChunkLevel level, AgeableMob partner) {
        Wolf child = new Wolf(level, level.nextEntityId());
        if (partner instanceof Wolf) {
            child.setVariant(random.nextBoolean() ? getVariant() : ((Wolf) partner).getVariant());   // 변종 고르기(난수 1회)
            if (isTame()) {
                child.setOwnerUUID(getOwnerUUID());
                child.setTame(true, true);
                child.setCollarColor(random.nextBoolean() ? getCollarColor() : ((Wolf) partner).getCollarColor());
            }
        }
        return child;
    }

    @Override
    public boolean wantsToAttack(Targetable target, Targetable owner) {
        if (target instanceof Entity e) {
            String t = e.type().name();
            if (t.equals("minecraft:creeper") || t.equals("minecraft:ghast") || t.equals("minecraft:armor_stand")) return false;
            if (e instanceof Wolf w) return !w.isTame() || w.getOwner() != owner;
        }
        // TODO(PvP): 플레이어끼리 때릴 수 없으면(pvp 꺼짐·같은 팀) 주인의 적이 플레이어라도 공격하지 않는다
        return !(target instanceof TamableAnimal ta && ta.isTame());
    }

    @Override public boolean canAttackTarget(Targetable t) { return canAttackType(t); }

    @Override public int getRemainingPersistentAngerTime() { return (Integer) entityData.get(DATA_REMAINING_ANGER_TIME); }

    @Override public void setRemainingPersistentAngerTime(int t) { entityData.set(DATA_REMAINING_ANGER_TIME, t); }

    @Override public UUID getPersistentAngerTarget() { return persistentAngerTarget; }

    @Override public void setPersistentAngerTarget(UUID u) { persistentAngerTarget = u; }

    @Override public void startPersistentAngerTimer() { setRemainingPersistentAngerTime(20 + random.nextInt(20)); }

    @Override
    public String getAmbientSound() {
        if (isAngry()) return "entity.wolf.growl";
        if (random.nextInt(3) == 0) return isTame() && health() < 20.0F ? "entity.wolf.whine" : "entity.wolf.pant";
        return "entity.wolf.ambient";
    }


    @Override protected String getDeathSound() { return "entity.wolf.death"; }

    @Override
    protected void addAdditionalSaveData(NbtCompound tag) {
        super.addAdditionalSaveData(tag);
        tag.putByte("CollarColor", (byte) getCollarColor().id());
        tag.putString("variant", variantName(getVariant()));
        addPersistentAngerSaveData(tag);
    }

    @Override
    protected void readAdditionalSaveData(NbtCompound tag) {
        super.readAdditionalSaveData(tag);
        if (tag.tags().get("variant") instanceof String vs) {
            int i = variantIndex(vs);   // 없는 변종(지운 데이터 팩)이면 바닐라처럼 그대로 둔다(기본 pale)
            if (i >= 0) setVariant(i);
        }
        if (tag.contains("CollarColor")) setCollarColor(DyeColor.byId(tag.getByte("CollarColor")));
        readPersistentAngerSaveData(tag);
    }

    /** 무리 크기(Mob.getMaxSpawnClusterSize)는 자연 스폰 때 쓴다. */
    public int maxSpawnClusterSize() { return 8; }
    @Override public int getMaxSpawnClusterSize() { return 8; }

    /** 바닐라 Wolf.WolfAvoidEntityGoal: 길들지 않은 늑대는 힘(1~5)이 난수(0~4) 이상인 라마를 피하고, 피하는 동안 대상을 버린다. */
    final class WolfAvoidEntityGoal extends AvoidEntityGoal {
        WolfAvoidEntityGoal(Wolf wolf, Class<? extends Targetable> cls, float dist, double walk, double sprint) { super(wolf, cls, dist, walk, sprint); }

        @Override
        public boolean canUse() {
            return super.canUse() && toAvoid instanceof Llama llama ? !isTame() && avoidLlama(llama) : false;
        }

        private boolean avoidLlama(Llama llama) { return llama.getStrength() >= random.nextInt(5); }

        @Override
        public void start() {
            setTarget(null);
            super.start();
        }

        @Override
        public void tick() {
            setTarget(null);
            super.tick();
        }
    }
}
