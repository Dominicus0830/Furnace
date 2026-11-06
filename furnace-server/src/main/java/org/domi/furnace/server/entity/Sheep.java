// 바닐라 Sheep: 색깔(데이터 17 의 하위 4비트)·깎임(16 비트), 풀 먹고 양털 다시 자람, 가위로 깎기. 새끼 색은 염료 섞기 레시피(2칸) 결과.
package org.domi.furnace.server.entity;

import java.util.Map;
import org.domi.furnace.server.attribute.Attribute;
import org.domi.furnace.server.attribute.AttributeMap;
import org.domi.furnace.server.block.BlockActor;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.PlayerActor;
import org.domi.furnace.server.entity.ai.goal.*;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.item.ItemTags;
import org.domi.furnace.server.item.ItemTools;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.protocol.play.SetEntityData;

public class Sheep extends Animal {
    public static final int DATA_WOOL_ID = 17;
    private EatBlockGoal eatBlockGoal;

    /** 염료 섞기 레시피(순서 무관 2칸): 바닐라 crafting_shapeless 중 재료 2개짜리. */
    private static final Map<Long, DyeColor> MIX = Map.of(
            key(DyeColor.RED, DyeColor.YELLOW), DyeColor.ORANGE,
            key(DyeColor.RED, DyeColor.WHITE), DyeColor.PINK,
            key(DyeColor.BLACK, DyeColor.WHITE), DyeColor.GRAY,
            key(DyeColor.GRAY, DyeColor.WHITE), DyeColor.LIGHT_GRAY,
            key(DyeColor.GREEN, DyeColor.WHITE), DyeColor.LIME,
            key(DyeColor.BLUE, DyeColor.WHITE), DyeColor.LIGHT_BLUE,
            key(DyeColor.BLUE, DyeColor.GREEN), DyeColor.CYAN,
            key(DyeColor.BLUE, DyeColor.RED), DyeColor.PURPLE,
            key(DyeColor.PURPLE, DyeColor.PINK), DyeColor.MAGENTA);

    private static long key(DyeColor a, DyeColor b) { return Math.min(a.id(), b.id()) * 16L + Math.max(a.id(), b.id()); }

    public Sheep(ChunkLevel level, int id) { super(EntityType.SHEEP, level, id); }

    @Override
    protected void createAttributes(AttributeMap map) {
        super.createAttributes(map);
        map.add(Attribute.MAX_HEALTH, 8.0).add(Attribute.MOVEMENT_SPEED, 0.23F);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_WOOL_ID, SetEntityData.SERIALIZER_BYTE, (byte) 0);
    }

    @Override
    protected void registerGoals() {
        eatBlockGoal = new EatBlockGoal(this, this::ate);
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new PanicGoal(this, 1.25));
        goalSelector.addGoal(2, new BreedGoal(this, 1.0));
        goalSelector.addGoal(3, new TemptGoal(this, 1.1, s -> ItemTags.is(s, "minecraft:sheep_food"), false));
        goalSelector.addGoal(4, new FollowParentGoal(this, 1.1));
        goalSelector.addGoal(5, eatBlockGoal);
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 1.0));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, 6.0F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
    }

    @Override
    public boolean isFood(ItemStack s) { return ItemTags.is(s, "minecraft:sheep_food"); }

    public DyeColor getColor() { return DyeColor.byId((Byte) entityData.get(DATA_WOOL_ID) & 15); }

    public void setColor(DyeColor c) {
        byte b = (Byte) entityData.get(DATA_WOOL_ID);
        entityData.set(DATA_WOOL_ID, (byte) (b & 240 | c.id() & 15));
    }

    public boolean isSheared() { return ((Byte) entityData.get(DATA_WOOL_ID) & 16) != 0; }

    public void setSheared(boolean v) {
        byte b = (Byte) entityData.get(DATA_WOOL_ID);
        entityData.set(DATA_WOOL_ID, (byte) (v ? b | 16 : b & -17));
    }

    public boolean readyForShearing() { return isAlive() && !isSheared() && !isBaby(); }

    /** Sheep.getRandomSheepColor. */
    public static DyeColor getRandomSheepColor(org.domi.furnace.server.block.RandomSource r) {
        int i = r.nextInt(100);
        if (i < 5) return DyeColor.BLACK;
        if (i < 10) return DyeColor.GRAY;
        if (i < 15) return DyeColor.LIGHT_GRAY;
        if (i < 18) return DyeColor.BROWN;
        return r.nextInt(500) == 0 ? DyeColor.PINK : DyeColor.WHITE;
    }

    @Override
    public void finalizeSpawn(org.domi.furnace.server.block.RandomSource r, DifficultyInstance difficulty) {
        setColor(getRandomSheepColor(level.random()));
        super.finalizeSpawn(r, difficulty);
    }

    /** Sheep.ate: Mob.ate(eat 게임 이벤트) 뒤 양털이 다시 자란다(아기는 나이 -60 으로 앞당긴다). */
    private void ate() {
        gameEvent(org.domi.furnace.server.world.gameevent.GameEvent.EAT);
        setSheared(false);
        if (isBaby()) ageUp(60);
    }

    @Override
    public boolean interact(Player p, int hand, ItemStack held, org.domi.furnace.server.world.Region region) {
        if (!held.isEmpty() && ItemTools.itemName(held).equals("minecraft:shears")) {
            if (readyForShearing()) {
                shear("players");
                gameEvent(org.domi.furnace.server.world.gameevent.GameEvent.SHEAR, p);   // 바닐라: shear 뒤 플레이어가 원인
                var inv = p.inventory();
                int ws = hand == 0 ? inv.heldWindowSlot() : 45;
                PlayerActor actor = new PlayerActor(p, hand == 0 ? held : ItemStack.EMPTY, hand == 1 ? held : ItemStack.EMPTY, 0);
                org.domi.furnace.server.block.BlockBreaking.hurtAndBreak(level, actor, hand, held, 1);
                if (actor.handChanged(hand)) inv.set(ws, actor.itemInHand(hand));
                return true;
            }
            return false;
        }
        return super.interact(p, hand, held, region);
    }

    public void shear(String source) {
        level.playSound(null, this, "entity.sheep.shear", source, 1.0F, 1.0F);
        setSheared(true);
        int n = 1 + random.nextInt(3);
        for (int i = 0; i < n; i++) {
            ItemEntity ie = spawnAtLocation(new ItemStack(ItemContext.id("minecraft:item", getColor().key() + "_wool"), 1));
            if (ie != null) {
                ie.setDeltaMovement(ie.vx() + (random.nextFloat() - random.nextFloat()) * 0.1F, ie.vy() + random.nextFloat() * 0.05F,
                        ie.vz() + (random.nextFloat() - random.nextFloat()) * 0.1F);
            }
        }
    }

    /** Sheep.getDefaultLootTable: 깎였으면 종류 기본 표(entities/sheep, 양고기), 아니면 색별 표. */
    @Override
    public String lootTable() { return isSheared() ? super.lootTable() : "minecraft:entities/sheep/" + getColor().key(); }

    @Override
    public AgeableMob getBreedOffspring(ChunkLevel level, AgeableMob partner) {
        Sheep child = new Sheep(level, level.nextEntityId());
        child.setColor(getOffspringColor(this, (Sheep) partner));
        return child;
    }

    private DyeColor getOffspringColor(Sheep a, Sheep b) {
        DyeColor ca = a.getColor(), cb = b.getColor();
        DyeColor mixed = MIX.get(key(ca, cb));
        if (mixed != null) return mixed;
        return level.random().nextBoolean() ? ca : cb;
    }

    @Override public String getAmbientSound() { return "entity.sheep.ambient"; }

    @Override protected String getHurtSound(Damage source) { return "entity.sheep.hurt"; }

    @Override protected String getDeathSound() { return "entity.sheep.death"; }

    @Override
    protected void addAdditionalSaveData(NbtCompound tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Sheared", isSheared());
        tag.putByte("Color", (byte) getColor().id());
    }

    @Override
    protected void readAdditionalSaveData(NbtCompound tag) {
        super.readAdditionalSaveData(tag);
        setSheared(tag.getBoolean("Sheared"));
        setColor(DyeColor.byId(tag.getByte("Color")));
    }
}
