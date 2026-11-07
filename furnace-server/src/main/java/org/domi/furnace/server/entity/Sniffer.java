// 바닐라 Sniffer: 냄새를 맡고 땅을 파 씨앗을 찾는 동물(IDLING → SNIFFING → SEARCHING → DIGGING → RISING). 번식하면 알 아이템을 떨군다.
// 길찾기 앞뒤 훅(onPathfindingStart/Done)으로 불붙었거나 물속이면 물 비용을 0 으로 풀었다가 되돌린다. 파는 동안 몸 높이 -0.4(눈 0.81).
// 파는 입자·찾는 소리는 바닐라도 클라이언트(애니메이션 시간)에서만 나서 서버는 10틱마다 entity_action 게임 이벤트만 낸다.
package org.domi.furnace.server.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.domi.furnace.server.attribute.Attribute;
import org.domi.furnace.server.attribute.AttributeMap;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Clip;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.ai.brain.Brain;
import org.domi.furnace.server.entity.ai.brain.GlobalPos;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;
import org.domi.furnace.server.entity.ai.brain.SensorType;
import org.domi.furnace.server.entity.ai.navigation.PathType;
import org.domi.furnace.server.entity.ai.util.LandRandomPos;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.item.ItemTags;
import org.domi.furnace.server.net.protocol.play.SetEntityData;

public class Sniffer extends Animal {
    public static final int DATA_STATE = 17, DATA_DROP_SEED_AT_TICK = 18;
    public static final int EGG_DROP_COOLDOWN_TICKS = 48000;

    public enum State {
        IDLING(0), FEELING_HAPPY(1), SCENTING(2), SNIFFING(3), SEARCHING(4), DIGGING(5), RISING(6);

        private final int id;

        State(int id) { this.id = id; }

        public int id() { return id; }

        public static State byId(int id) {
            for (State s : values()) if (s.id == id) return s;
            return IDLING;
        }
    }

    public Sniffer(ChunkLevel level, int id) {
        super(EntityType.SNIFFER, level, id);
        getNavigation().setCanFloat(true);
        setPathfindingMalus(PathType.WATER, -1.0F);
        setPathfindingMalus(PathType.DANGER_POWDER_SNOW, -1.0F);
        setPathfindingMalus(PathType.DAMAGE_CAUTIOUS, -1.0F);
        brain = makeBrain();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Brain<Sniffer> makeBrain() {
        Brain<Sniffer> b = new Brain<>(java.util.List.of(MemoryModuleType.LOOK_TARGET, MemoryModuleType.WALK_TARGET, MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE, MemoryModuleType.PATH,
                MemoryModuleType.IS_PANICKING, MemoryModuleType.SNIFFER_SNIFFING_TARGET, MemoryModuleType.SNIFFER_DIGGING, MemoryModuleType.SNIFFER_HAPPY, MemoryModuleType.SNIFF_COOLDOWN,
                MemoryModuleType.SNIFFER_EXPLORED_POSITIONS, MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES, MemoryModuleType.BREED_TARGET, MemoryModuleType.TEMPTING_PLAYER,
                MemoryModuleType.TEMPTATION_COOLDOWN_TICKS, MemoryModuleType.IS_TEMPTED),
                (java.util.List) java.util.List.of(SensorType.NEAREST_LIVING_ENTITIES, SensorType.HURT_BY, SensorType.NEAREST_PLAYERS, SensorType.SNIFFER_TEMPTATIONS));
        return SnifferAi.makeBrain(b);
    }

    @SuppressWarnings("unchecked")
    public Brain<Sniffer> snifferBrain() { return (Brain<Sniffer>) brain; }

    @Override
    protected void createAttributes(AttributeMap map) {
        super.createAttributes(map);
        map.add(Attribute.MOVEMENT_SPEED, 0.1F).add(Attribute.MAX_HEALTH, 14.0);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_STATE, SetEntityData.SERIALIZER_SNIFFER_STATE, State.IDLING.id());
        entityData.define(DATA_DROP_SEED_AT_TICK, SetEntityData.SERIALIZER_INT, 0);
    }

    @Override
    public void onPathfindingStart() {
        super.onPathfindingStart();
        if (isOnFire() || isInWater()) setPathfindingMalus(PathType.WATER, 0.0F);
    }

    @Override
    public void onPathfindingDone() { setPathfindingMalus(PathType.WATER, -1.0F); }

    public boolean isTempted() { return getBrain().getMemory(MemoryModuleType.IS_TEMPTED).orElse(false); }

    public boolean canSniff() { return !isTempted() && !isPanicking() && !isInWater() && !isInLove() && onGround() && !isPassenger() && !isLeashed(); }

    public boolean isSearching() { return getState() == State.SEARCHING; }

    public boolean isTempting() { return false; }

    private Pos getHeadBlock() {
        double[] head = getHeadPosition();
        return new Pos((int) Math.floor(head[0]), (int) Math.floor(y() + 0.2F), (int) Math.floor(head[2]));
    }

    private double[] getHeadPosition() {
        double[] v = Clip.viewVector(xRot(), yRot());
        return new double[] {x() + v[0] * 2.25, y() + v[1] * 2.25, z() + v[2] * 2.25};
    }

    public State getState() { return State.byId((Integer) entityData.get(DATA_STATE)); }

    private Sniffer setState(State state) {
        entityData.set(DATA_STATE, state.id());
        refreshBabyDimensions();   // onSyncedDataUpdated(DATA_STATE) → refreshDimensions
        return this;
    }

    /** Sniffer.getDefaultDimensions: 파는 중이면 scalable(1.9, 1.75 - 0.4).withEyeHeight(0.81) × 나이 배율. */
    @Override
    protected void refreshBabyDimensions() {
        if (getState() == State.DIGGING) {
            float s = getAgeScale();
            setDimensions(EntityType.SNIFFER.width() * s, (EntityType.SNIFFER.height() - 0.4F) * s, 0.81F * s);
        } else {
            super.refreshBabyDimensions();
        }
    }

    @Override
    public void tick() {
        // emitDiggingParticles: 파는 동안 10틱마다 머리 칸에서 entity_action(입자·소리는 클라이언트의 애니메이션 시간이 있어야 해 서버에선 없음)
        if (getState() == State.DIGGING && tickCount % 10 == 0) level.gameEventFrom("minecraft:entity_action", getHeadBlock(), this, -1);
        super.tick();
        if (getState() == State.DIGGING) dropSeed();
    }

    /** 상태 전환: 소리와 파기 시작 처리까지 함께. */
    public Sniffer transitionTo(State state) {
        switch (state) {
            case IDLING -> setState(State.IDLING);
            case FEELING_HAPPY -> {
                playSound("entity.sniffer.happy", 1.0F, 1.0F);
                setState(State.FEELING_HAPPY);
            }
            case SCENTING -> {
                setState(State.SCENTING);
                onScenting();
            }
            case SNIFFING -> {
                playSound("entity.sniffer.sniffing", 1.0F, 1.0F);
                setState(State.SNIFFING);
            }
            case SEARCHING -> setState(State.SEARCHING);
            case DIGGING -> {
                setState(State.DIGGING);
                onDiggingStart();
            }
            case RISING -> {
                playSound("entity.sniffer.digging_stop", 1.0F, 1.0F);
                setState(State.RISING);
            }
        }
        return this;
    }

    private void onScenting() { playSound("entity.sniffer.scenting", 1.0F, isBaby() ? 1.3F : 1.0F); }

    private void onDiggingStart() {
        entityData.set(DATA_DROP_SEED_AT_TICK, tickCount + 120);
        broadcastEntityEvent((byte) 63);
    }

    public void onDiggingComplete(boolean success) {
        if (success) storeExploredPosition(getOnPos());
    }

    Optional<Pos> calculateDigPosition() {
        for (int i = 0; i < 5; i++) {
            double[] v = LandRandomPos.getPos(this, 10 + 2 * i, 3);
            if (v == null) continue;
            Pos pos = new Pos((int) Math.floor(v[0]), (int) Math.floor(v[1]), (int) Math.floor(v[2]));
            if (!level.withinBorder(pos)) continue;
            Pos below = pos.below();
            if (canDig(below)) return Optional.of(below);
        }
        return Optional.empty();
    }

    boolean canDig() {
        return !isPanicking() && !isTempted() && !isBaby() && !isInWater() && onGround() && canDig(getHeadBlock().below());
    }

    private boolean canDig(Pos pos) {
        return B.isTag(level.getBlockState(pos), "minecraft:sniffer_diggable_block") && getExploredPositions().noneMatch(g -> new GlobalPos(level.dimension(), pos).equals(g))
                && Optional.ofNullable(navigation.createPath(pos, 1)).map(p -> p.canReach()).orElse(false);
    }

    private void dropSeed() {
        if ((Integer) entityData.get(DATA_DROP_SEED_AT_TICK) == tickCount) {
            double[] head = getHeadPosition();
            Pos headBlock = getHeadBlock();
            // 표 gameplay/sniffer_digging(origin=머리 위치, this_entity=나, 선물 매개변수 묶음)
            for (ItemStack s : level.giftLoot("minecraft:gameplay/sniffer_digging", lootEntity(), head[0], head[1], head[2])) {
                ItemEntity item = new ItemEntity(level, level.nextEntityId(), headBlock.x(), headBlock.y(), headBlock.z(), s);
                item.setDefaultPickUpDelay();
                level.addFreshEntity(item);
            }
            playSound("entity.sniffer.drop_seed", 1.0F, 1.0F);
        }
    }

    private Sniffer storeExploredPosition(Pos pos) {
        List<GlobalPos> list = getExploredPositions().limit(20L).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        list.add(0, new GlobalPos(level.dimension(), pos));
        getBrain().setMemory(MemoryModuleType.SNIFFER_EXPLORED_POSITIONS, list);
        return this;
    }

    private java.util.stream.Stream<GlobalPos> getExploredPositions() {
        return getBrain().getMemory(MemoryModuleType.SNIFFER_EXPLORED_POSITIONS).stream().flatMap(java.util.Collection::stream);
    }

    @Override
    public void jumpFromGround() {
        super.jumpFromGround();
        double speed = moveControl.getSpeedModifier();
        if (speed > 0.0) {
            double horizontalSq = vx() * vx() + vz() * vz();
            if (horizontalSq < 0.01) moveRelative(0.1F, 0.0F, 0.0F, 1.0F);
        }
    }

    @Override
    public void spawnChildFromBreeding(ChunkLevel level, Animal partner) {
        ItemStack egg = new ItemStack(ItemContext.id("minecraft:item", "sniffer_egg"), 1);
        ItemEntity item = new ItemEntity(level, level.nextEntityId(), x(), y(), z(), egg);
        item.setDefaultPickUpDelay();
        finalizeBreeding(level, partner, null);
        playSound("block.sniffer_egg.plop", 1.0F, (random.nextFloat() - random.nextFloat()) * 0.2F + 0.5F);
        level.addFreshEntity(item);
    }

    @Override
    public void die(Damage source) {
        transitionTo(State.IDLING);
        super.die(source);
    }

    @Override
    public void setBaby(boolean baby) { setAge(baby ? -EGG_DROP_COOLDOWN_TICKS : 0); }

    @Override public int getMaxHeadYRot() { return 50; }

    @Override
    public boolean canMate(Animal other) {
        if (!(other instanceof Sniffer s)) return false;
        java.util.Set<State> ok = java.util.Set.of(State.IDLING, State.SCENTING, State.FEELING_HAPPY);
        return ok.contains(getState()) && ok.contains(s.getState()) && super.canMate(other);
    }

    @Override
    public AgeableMob getBreedOffspring(ChunkLevel level, AgeableMob partner) { return new Sniffer(level, level.nextEntityId()); }

    @Override public boolean isFood(ItemStack s) { return ItemTags.is(s, "minecraft:sniffer_food"); }

    @Override
    public boolean interact(Player p, int hand, ItemStack held, org.domi.furnace.server.world.Region region) {
        boolean food = isFood(held);
        boolean ok = super.interact(p, hand, held, region);
        if (ok && food) level.playSound(null, this, "entity.sniffer.eat", "neutral", 1.0F, 0.8F + level.random().nextFloat() * 0.4F);
        return ok;
    }

    @Override
    protected void customServerAiStep() {
        snifferBrain().tick(level, this);
        SnifferAi.updateActivity(this);
        super.customServerAiStep();
    }

    @Override
    public String getAmbientSound() { return java.util.Set.of(State.DIGGING, State.SEARCHING).contains(getState()) ? null : "entity.sniffer.idle"; }

    @Override protected String getHurtSound(Damage source) { return "entity.sniffer.hurt"; }

    @Override protected String getDeathSound() { return "entity.sniffer.death"; }

    /** Sniffer.playStepSound: sniffer.step 크기 0.15(블록 소리를 쓰지 않는다). */
    @Override
    protected void playStepSound(org.domi.furnace.server.block.Pos pos, int state) { playSound("minecraft:entity.sniffer.step", 0.15F, 1.0F); }
}
