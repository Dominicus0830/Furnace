// 바닐라 MushroomCow: 빨강/갈색, 그릇으로 스튜, 가위로 소로 바뀌며 버섯 5개. 갈색은 작은 꽃을 먹여 수상한 스튜 효과를 저장한다.
package org.domi.furnace.server.entity;

import java.util.List;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.item.ItemTags;
import org.domi.furnace.server.item.ItemTools;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.protocol.play.SetEntityData;
import org.domi.furnace.server.recipe.StewEffects;

public class Mooshroom extends Cow {
    /** 마지막으로 바꾼 번개(같은 번개로 여러 번 바뀌지 않게). 저장하지 않는다(바닐라와 같음). */
    private java.util.UUID lastLightningBoltUUID;

    /** MushroomCow.thunderHit: 새 번개면 빨강↔갈색을 바꾸고 변환 소리. */
    @Override
    public void thunderHit(ChunkLevel level, LightningBolt bolt) {
        java.util.UUID id = bolt.uuid();
        if (!id.equals(lastLightningBoltUUID)) {
            setMushroomType(mushroomType().equals("red") ? "brown" : "red");
            lastLightningBoltUUID = id;
            playSound("entity.mooshroom.convert", 2.0F, 1.0F);
        }
    }

    public static final int DATA_TYPE = 17;
    private List<Object> stewEffects;

    public Mooshroom(ChunkLevel level, int id) { super(EntityType.MOOSHROOM, level, id); }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_TYPE, SetEntityData.SERIALIZER_STRING, "red");
    }

    public String mushroomType() { return (String) entityData.get(DATA_TYPE); }

    public void setMushroomType(String t) { entityData.set(DATA_TYPE, t); }

    @Override
    public float getWalkTargetValue(org.domi.furnace.server.block.Pos pos, ChunkLevel level) {
        if (org.domi.furnace.server.block.B.is(level.getBlockState(pos.below()), "minecraft:mycelium")) return 10.0F;
        float f = level.getMaxLocalRawBrightness(pos) / 15.0F;
        return f / (4.0F - 3.0F * f) - 0.5F;
    }

    @Override
    public boolean interact(Player p, int hand, ItemStack held, org.domi.furnace.server.world.Region region) {
        String name = held.isEmpty() ? "" : ItemTools.itemName(held);
        if (name.equals("minecraft:bowl") && !isBaby()) {
            boolean suspicious = false;
            ItemStack out;
            if (stewEffects != null) {
                suspicious = true;
                out = ItemTools.with(new ItemStack(ItemContext.id("minecraft:item", "suspicious_stew"), 1), "minecraft:suspicious_stew_effects", stewEffects);
                stewEffects = null;
            } else {
                out = new ItemStack(ItemContext.id("minecraft:item", "mushroom_stew"), 1);
            }
            swapHeld(p, hand, held, out, region);
            playSound(suspicious ? "entity.mooshroom.suspicious_milk" : "entity.mooshroom.milk", 1.0F, 1.0F);
            return true;
        }
        if (name.equals("minecraft:shears") && readyForShearing()) {
            shearToCow(p, hand, held);
            return true;
        }
        if (mushroomType().equals("brown") && ItemTags.is(held, "minecraft:small_flowers")) {
            if (stewEffects != null) return true;   // 이미 가지고 있으면 연기 입자만
            List<Object> fx = StewEffects.of(name);
            if (fx == null) return false;
            if (!p.vitals().isCreative()) {
                var inv = p.inventory();
                inv.set(hand == 0 ? inv.heldWindowSlot() : 45, held.withCount(held.count() - 1));
            }
            stewEffects = fx;
            playSound("entity.mooshroom.eat", 2.0F, 1.0F);
            return true;
        }
        return super.interact(p, hand, held, region);
    }

    private void swapHeld(Player p, int hand, ItemStack held, ItemStack out, org.domi.furnace.server.world.Region region) {
        if (p.vitals().isCreative()) return;
        var inv = p.inventory();
        int ws = hand == 0 ? inv.heldWindowSlot() : 45;
        ItemStack rest = held.withCount(held.count() - 1);
        if (rest.isEmpty()) {
            inv.set(ws, out);
        } else {
            inv.set(ws, rest);
            ItemStack left = inv.add(out, false, 64, false);
            if (!left.isEmpty()) region.playerDrop(p, left, false, true);
        }
    }

    public boolean readyForShearing() { return isAlive() && !isBaby(); }

    private void shearToCow(Player p, int hand, ItemStack held) {
        shear("players");
        gameEvent(org.domi.furnace.server.world.gameevent.GameEvent.SHEAR, p);   // 바닐라: shear 뒤 플레이어가 원인
        var inv = p.inventory();
        int ws = hand == 0 ? inv.heldWindowSlot() : 45;
        var actor = new org.domi.furnace.server.block.PlayerActor(p, hand == 0 ? held : ItemStack.EMPTY, hand == 1 ? held : ItemStack.EMPTY, 0);
        org.domi.furnace.server.block.BlockBreaking.hurtAndBreak(level, actor, hand, held, 1);
        if (actor.handChanged(hand)) inv.set(ws, actor.itemInHand(hand));
    }

    /** Shearable.shear(source): 소리 → 소로 바뀌고 버섯 5개. */
    public void shear(String source) {
        level.playSound(null, this, "entity.mooshroom.shear", source, 1.0F, 1.0F);
        Cow cow = new Cow(level, level.nextEntityId());
        cow.moveTo(x(), y(), z(), yRot(), xRot());
        cow.setHealth(health());
        cow.setYBodyRot(yBodyRot());
        cow.setYHeadRot(yHeadRot());
        cow.setAge(getAge());
        discard();
        level.addFreshEntity(cow.spawnReason("SHEARED"));
        String item = mushroomType().equals("red") ? "red_mushroom" : "brown_mushroom";
        for (int i = 0; i < 5; i++) {
            level.addFreshEntity(new ItemEntity(level, level.nextEntityId(), x(), y() + dimH, z(), new ItemStack(ItemContext.id("minecraft:item", item), 1)));
        }
    }

    @Override
    public AgeableMob getBreedOffspring(ChunkLevel level, AgeableMob partner) {
        Mooshroom child = new Mooshroom(level, level.nextEntityId());
        String a = mushroomType(), b = ((Mooshroom) partner).mushroomType();
        String t;
        if (a.equals(b) && random.nextInt(1024) == 0) t = a.equals("brown") ? "red" : "brown";
        else t = random.nextBoolean() ? a : b;
        child.setMushroomType(t);
        return child;
    }

    @Override
    protected void addAdditionalSaveData(NbtCompound tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("Type", mushroomType());
        if (stewEffects != null) {
            var list = new org.domi.furnace.server.net.io.nbt.NbtList(org.domi.furnace.server.net.io.nbt.NbtIo.TAG_COMPOUND);
            for (Object o : stewEffects) {
                var m = (java.util.Map<?, ?>) o;
                var c = new NbtCompound();
                c.putString("id", (String) m.get("id"));
                c.putInt("duration", ((Number) m.get("duration")).intValue());
                list.add(c);
            }
            tag.putList("stew_effects", list);
        }
    }

    @Override
    protected void readAdditionalSaveData(NbtCompound tag) {
        super.readAdditionalSaveData(tag);
        setMushroomType(tag.tags().get("Type") instanceof String s && s.equals("brown") ? "brown" : "red");
        if (tag.contains("stew_effects")) {
            stewEffects = new java.util.ArrayList<>();
            for (Object o : tag.getList("stew_effects").items()) {
                var c = (NbtCompound) o;
                var m = new java.util.LinkedHashMap<String, Object>();
                m.put("id", c.tags().get("id"));
                m.put("duration", c.getInt("duration"));
                stewEffects.add(m);
            }
        }
    }
}
