// 바닐라 BeehiveBlockEntity: 벌집·벌통 안의 벌(최대 3마리). 벌은 NBT(entity_data)로 담겨 있다가 ticks_in_hive 가 min_ticks_in_hive 를 넘으면 앞쪽으로 나온다.
// 꿀을 가져온 벌이 나오면 honey_level 이 1(1/100 확률로 2) 오른다. 밤·비에는 비상(EMERGENCY)이 아니면 나오지 않는다.
// 저장: bees[{entity_data, ticks_in_hive, min_ticks_in_hive}], flower_pos(int[3]).
package org.domi.furnace.server.block.entity;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockBehaviors;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;
import org.domi.furnace.server.entity.Bee;
import org.domi.furnace.server.entity.Entities;
import org.domi.furnace.server.entity.Entity;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtIo;
import org.domi.furnace.server.net.io.nbt.NbtList;

public class BeehiveBlockEntity extends BlockEntity {

    private static final Prop HONEY_LEVEL = B.prop("honey_level"), FACING = B.prop("facing");
    /** 벌을 담을 때 지우는 엔티티 태그(바닐라 IGNORED_BEE_TAGS, 순서 그대로). */
    static final List<String> IGNORED_BEE_TAGS = List.of("Air", "ArmorDropChances", "ArmorItems", "Brain", "CanPickUpLoot", "DeathTime", "FallDistance",
            "FallFlying", "Fire", "HandDropChances", "HandItems", "HurtByTimestamp", "HurtTime", "LeftHanded", "Motion", "NoGravity", "OnGround",
            "PortalCooldown", "Pos", "Rotation", "SleepingX", "SleepingY", "SleepingZ", "CannotEnterHiveTicks", "TicksSincePollination",
            "CropsGrownSincePollination", "hive_pos", "Passengers", "leash", "UUID");
    public static final int MAX_OCCUPANTS = 3;
    private static final int MIN_TICKS_BEFORE_REENTERING_HIVE = 400;
    private static final int MIN_OCCUPATION_TICKS_NECTAR = 2400;
    public static final int MIN_OCCUPATION_TICKS_NECTARLESS = 600;

    public enum BeeReleaseStatus { HONEY_DELIVERED, BEE_RELEASED, EMERGENCY }

    /** Occupant(entity_data, ticks_in_hive, min_ticks_in_hive) — 저장·아이템 구성 요소 꼴. */
    public record Occupant(NbtCompound entityData, int ticksInHive, int minTicksInHive) {
        /** Occupant.of(entity): 저장하고 무시 태그를 지운다. 꿀이 있으면 2400 틱, 없으면 600 틱. */
        public static Occupant of(Entity e) {
            NbtCompound tag = e.saveAsPassenger();
            if (tag == null) tag = new NbtCompound();
            for (String k : IGNORED_BEE_TAGS) tag.tags().remove(k);
            boolean nectar = tag.getBoolean("HasNectar");
            return new Occupant(tag, 0, nectar ? MIN_OCCUPATION_TICKS_NECTAR : MIN_OCCUPATION_TICKS_NECTARLESS);
        }

        /** Occupant.create(ticksInHive): 월드젠 벌집의 새 벌({id:"minecraft:bee"}, 600). */
        public static Occupant create(int ticksInHive) {
            NbtCompound tag = new NbtCompound();
            tag.putString("id", "minecraft:bee");
            return new Occupant(tag, ticksInHive, MIN_OCCUPATION_TICKS_NECTARLESS);
        }

        /** createEntity: 태그를 복사해 무시 태그를 지우고 불러온다. beehive_inhabitors(벌)만 — 중력 끔, 벌이면 벌집 위치·나이 보정. */
        public Entity createEntity(ChunkLevel level, Pos pos) {
            NbtCompound tag = entityData.copy();
            for (String k : IGNORED_BEE_TAGS) tag.tags().remove(k);
            Entity e = Entities.load(level, tag);
            if (e == null || !(e instanceof Bee)) return null;   // #beehive_inhabitors = 벌뿐
            e.setNoGravity(true);
            if (e instanceof Bee bee) {
                bee.setHivePos(pos);
                setBeeReleaseData(ticksInHive, bee);
            }
            return e;
        }

        private static void setBeeReleaseData(int ticks, Bee bee) {
            int age = bee.getAge();
            if (age < 0) bee.setAge(Math.min(0, age + ticks));
            else if (age > 0) bee.setAge(Math.max(0, age - ticks));
            bee.setInLoveTime(Math.max(0, bee.inLoveTime() - ticks));
        }

        public NbtCompound save() {
            NbtCompound t = new NbtCompound();
            t.putCompound("entity_data", entityData.copy());
            t.putInt("ticks_in_hive", ticksInHive);
            t.putInt("min_ticks_in_hive", minTicksInHive);
            return t;
        }

        /** 코덱 읽기: ticks_in_hive·min_ticks_in_hive 는 필수(없으면 null — 바닐라 resultOrPartial 은 그 원소를 버린다). */
        public static Occupant load(NbtCompound t) {
            if (!t.containsNumber("ticks_in_hive") || !t.containsNumber("min_ticks_in_hive")) return null;
            NbtCompound data = t.tags().get("entity_data") instanceof NbtCompound c ? c.copy() : new NbtCompound();
            return new Occupant(data, t.getIntOr0("ticks_in_hive"), t.getIntOr0("min_ticks_in_hive"));
        }
    }

    /** BeeData: 담긴 벌 하나 — 들어온 뒤 지난 틱을 센다. */
    static final class BeeData {
        private final Occupant occupant;
        private int ticksInHive;

        BeeData(Occupant o) {
            this.occupant = o;
            this.ticksInHive = o.ticksInHive();
        }

        boolean tick() { return ticksInHive++ > occupant.minTicksInHive(); }

        Occupant toOccupant() { return new Occupant(occupant.entityData(), ticksInHive, occupant.minTicksInHive()); }

        boolean hasNectar() { return occupant.entityData().getBoolean("HasNectar"); }
    }

    private final List<BeeData> stored = new ArrayList<>();
    private Pos savedFlowerPos;

    public BeehiveBlockEntity(Pos pos, int state) { super("minecraft:beehive", pos, state); }

    @Override public boolean isValid(int state) { return B.is(state, "minecraft:beehive") || B.is(state, "minecraft:bee_nest"); }

    @Override public boolean ticking() { return true; }

    /** setChanged: 불이 가까우면 모두 비상으로 내보낸 뒤 저장 표시. */
    @Override
    public void setChanged(BlockLevel level) {
        if (isFireNearby(level)) emptyAllLivingFromHive(level, null, level.getBlockState(pos), BeeReleaseStatus.EMERGENCY);
        super.setChanged(level);
    }

    /** isFireNearby: 둘레 3x3x3 에 불(fire 블록)이 있는가. */
    public boolean isFireNearby(BlockLevel level) {
        if (level == null) return false;
        for (int x = -1; x <= 1; x++)
            for (int y = -1; y <= 1; y++)
                for (int z = -1; z <= 1; z++)
                    if (B.is(level.getBlockState(pos.offset(x, y, z)), "minecraft:fire")) return true;
        return false;
    }

    public boolean isEmpty() { return stored.isEmpty(); }

    public boolean isFull() { return stored.size() == MAX_OCCUPANTS; }

    public int getOccupantCount() { return stored.size(); }

    public Pos savedFlowerPos() { return savedFlowerPos; }

    /** emptyAllLivingFromHive: 모두 내보내고, 플레이어가 있으면 4칸 안의 벌이 그를 노린다(연기가 있으면 대신 400틱 못 들어온다). */
    public void emptyAllLivingFromHive(BlockLevel level, Player player, int state, BeeReleaseStatus status) {
        List<Entity> released = releaseAllOccupants(level, state, status);
        if (player != null) {
            for (Entity e : released) {
                if (e instanceof Bee bee) {
                    double dx = player.x() - bee.x(), dy = player.y() - bee.y(), dz = player.z() - bee.z();
                    if (dx * dx + dy * dy + dz * dz <= 16.0) {
                        if (!isSedated(level)) bee.setTarget(player);
                        else bee.setStayOutOfHiveCountdown(MIN_TICKS_BEFORE_REENTERING_HIVE);
                    }
                }
            }
        }
    }

    private List<Entity> releaseAllOccupants(BlockLevel level, int state, BeeReleaseStatus status) {
        List<Entity> list = new ArrayList<>();
        stored.removeIf(d -> releaseOccupant(level, pos, state, d.toOccupant(), list, status, savedFlowerPos));
        if (!list.isEmpty()) super.setChanged(level);
        return list;
    }

    /** isSedated: 아래에 연기 나는 모닥불이 있다. */
    public boolean isSedated(BlockLevel level) { return org.domi.furnace.server.block.types.CampfireBlock.isSmokeyPos(level, pos); }

    /** addOccupant(entity): 3마리 미만이면 태운 것을 내리고 담는다. 꽃 위치를 넘겨받고(벌집에 없거나 1/2), 소리·게임 이벤트, 엔티티는 없앤다. */
    public void addOccupant(BlockLevel level, Entity e) {
        if (stored.size() >= MAX_OCCUPANTS) return;
        e.stopRiding();
        e.ejectPassengers();
        storeBee(Occupant.of(e));
        if (level != null) {
            if (e instanceof Bee bee && bee.hasSavedFlowerPos() && (savedFlowerPos == null || level.random().nextBoolean())) {
                savedFlowerPos = bee.getSavedFlowerPos();
            }
            level.playSound(null, pos.x(), pos.y(), pos.z(), "minecraft:block.beehive.enter", "blocks", 1.0F, 1.0F);
            level.gameEventFrom("minecraft:block_change", pos, e, level.getBlockState(pos));
        }
        e.discard();
        super.setChanged(level);
    }

    public void storeBee(Occupant o) { stored.add(new BeeData(o)); }

    /** releaseOccupant: 밤·비거나 앞이 막혔으면 비상이 아닌 한 못 나온다. 나오면 앞면 바깥(막혔으면 가운데)에 놓는다. */
    private static boolean releaseOccupant(BlockLevel level, Pos pos, int state, Occupant occupant, List<Entity> out, BeeReleaseStatus status, Pos flowerPos) {
        if (!(level instanceof ChunkLevel cl)) return false;
        if ((cl.isNight() || cl.isRaining()) && status != BeeReleaseStatus.EMERGENCY) return false;
        int dir = Dir.byName(FACING.valueName(state));
        Pos front = pos.relative(dir);
        int fs = level.getBlockState(front);
        boolean blocked = !BlockBehaviors.of(fs).getCollisionShape(fs, level, front, null).isEmpty();
        if (blocked && status != BeeReleaseStatus.EMERGENCY) return false;
        Entity e = occupant.createEntity(cl, pos);
        if (e == null) return false;
        if (e instanceof Bee bee) {
            if (flowerPos != null && !bee.hasSavedFlowerPos() && level.random().nextFloat() < 0.9F) bee.setSavedFlowerPos(flowerPos);
            if (status == BeeReleaseStatus.HONEY_DELIVERED) {
                bee.dropOffNectar();
                if (B.isTag(state, "minecraft:beehives") && HONEY_LEVEL.has(state)) {
                    int honey = getHoneyLevel(state);
                    if (honey < 5) {
                        int inc = level.random().nextInt(100) == 0 ? 2 : 1;
                        if (honey + inc > 5) inc--;
                        level.setBlockAndUpdate(pos, HONEY_LEVEL.withInt(state, honey + inc));
                    }
                }
            }
            if (out != null) out.add(bee);
            float w = e.bbWidth();
            double off = blocked ? 0.0 : 0.55 + w / 2.0F;
            double x = pos.x() + 0.5 + off * Dir.DX[dir];
            double y = pos.y() + 0.5 - e.bbHeight() / 2.0F;
            double z = pos.z() + 0.5 + off * Dir.DZ[dir];
            e.moveTo(x, y, z, e.yRot(), e.xRot());
        }
        level.playSound(null, pos, "minecraft:block.beehive.exit", "blocks", 1.0F, 1.0F);
        level.gameEventFrom("minecraft:block_change", pos, e, level.getBlockState(pos));
        return cl.addFreshEntity(e);
    }

    public static int getHoneyLevel(int state) { return HONEY_LEVEL.intValue(state); }

    /** tickOccupants: 다 머문 벌을 꿀 배달(꿀 있음) 또는 그냥 내보냄으로 내보낸다. */
    private void tickOccupants(BlockLevel level, int state) {
        boolean changed = false;
        Iterator<BeeData> it = stored.iterator();
        while (it.hasNext()) {
            BeeData d = it.next();
            if (d.tick()) {
                BeeReleaseStatus st = d.hasNectar() ? BeeReleaseStatus.HONEY_DELIVERED : BeeReleaseStatus.BEE_RELEASED;
                if (releaseOccupant(level, pos, state, d.toOccupant(), null, st, savedFlowerPos)) {
                    changed = true;
                    it.remove();
                }
            }
        }
        if (changed) staticSetChanged(level);
    }

    /** BlockEntity.setChanged(level, pos, state) 정적 판 — 불 검사 없이 저장 표시·비교기 알림. */
    private void staticSetChanged(BlockLevel level) { super.setChanged(level); }

    /** serverTick: 벌 틱 → 벌이 있으면 0.5% 확률로 일하는 소리. */
    @Override
    public void tick(BlockLevel level) {
        int state = level.getBlockState(pos);
        tickOccupants(level, state);
        if (!stored.isEmpty() && level.random().nextDouble() < 0.005) {
            level.playSound(null, pos.x() + 0.5, pos.y(), pos.z() + 0.5, "minecraft:block.beehive.work", "blocks", 1.0F, 1.0F);
        }
    }

    public List<Occupant> getBees() {
        List<Occupant> l = new ArrayList<>();
        for (BeeData d : stored) l.add(d.toOccupant());
        return l;
    }

    /** applyImplicitComponents: bees 를 그대로 담는다(있던 벌은 비운다). */
    @Override
    protected void applyImplicitComponents(ComponentInput in) {
        stored.clear();
        if (!(in.getOrDefault("minecraft:bees", List.of()) instanceof List<?> list)) return;
        for (Object o : list) {
            if (o instanceof java.util.Map<?, ?> m && m.get("ticks_in_hive") instanceof Number t && m.get("min_ticks_in_hive") instanceof Number mt) {
                NbtCompound data = m.get("entity_data") instanceof NbtCompound c ? c.copy() : new NbtCompound();
                storeBee(new Occupant(data, t.intValue(), mt.intValue()));
            }
        }
    }

    @Override
    protected void collectImplicitComponents(java.util.Map<String, Object> out) {
        List<Object> bees = new ArrayList<>();
        for (Occupant o : getBees()) {
            java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("entity_data", o.entityData().copy());
            m.put("ticks_in_hive", o.ticksInHive());
            m.put("min_ticks_in_hive", o.minTicksInHive());
            bees.add(m);
        }
        out.put("minecraft:bees", bees);
    }

    @Override
    public void load(NbtCompound tag) {
        stored.clear();
        if (tag.tags().get("bees") instanceof NbtList list) {
            for (Object o : list.items()) {
                if (o instanceof NbtCompound c) {
                    Occupant oc = Occupant.load(c);
                    if (oc != null) storeBee(oc);
                }
            }
        }
        savedFlowerPos = tag.tags().get("flower_pos") instanceof int[] a && a.length == 3 ? new Pos(a[0], a[1], a[2]) : null;
    }

    @Override
    protected void saveAdditional(NbtCompound tag) {
        NbtList list = new NbtList(NbtIo.TAG_COMPOUND);
        for (BeeData d : stored) list.add(d.toOccupant().save());
        tag.putList("bees", list);
        if (savedFlowerPos != null) tag.putIntArray("flower_pos", new int[] {savedFlowerPos.x(), savedFlowerPos.y(), savedFlowerPos.z()});
    }
}
