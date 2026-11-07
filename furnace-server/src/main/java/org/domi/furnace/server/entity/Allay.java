// 바닐라 Allay: 날아다니는 도우미 정령. 주운 것과 같은 아이템을 찾아 주워 와 좋아하는 플레이어에게 던져 준다. 춤추는 중 자수정 조각으로 복제한다.
// 진동 수신기(음표 블록 호감)와 주크박스 수신기(춤)를 가진다. TODO(탑승·애니메이션): 해당 규칙이 생기면.
package org.domi.furnace.server.entity;

import java.util.UUID;
import org.domi.furnace.server.attribute.Attribute;
import org.domi.furnace.server.attribute.AttributeMap;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.entity.ai.brain.Brain;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;
import org.domi.furnace.server.entity.ai.brain.SensorType;
import org.domi.furnace.server.entity.ai.control.FlyingMoveControl;
import org.domi.furnace.server.entity.ai.behavior.BehaviorUtils;
import org.domi.furnace.server.entity.ai.navigation.FlyingPathNavigation;
import org.domi.furnace.server.entity.ai.navigation.PathNavigation;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.item.ItemStackCodecs;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.protocol.play.SetEntityData;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.world.gameevent.DynamicGameEventListener;
import org.domi.furnace.server.world.gameevent.GameEvent;
import org.domi.furnace.server.world.gameevent.GameEventContext;
import org.domi.furnace.server.world.gameevent.GameEventListener;
import org.domi.furnace.server.world.gameevent.PositionSource;
import org.domi.furnace.server.world.gameevent.VibrationSystem;

public class Allay extends PathfinderMob implements InventoryCarrier, VibrationSystem {

    /** 바닐라는 낙하 처리를 비운다(날아다니는 몹은 착지 입자·낙하 거리·HIT_GROUND 가 없다). */
    @Override
    protected void checkFallDamage(double dy, boolean ground, int state, org.domi.furnace.server.block.Pos pos) {}
    public static final int DATA_DANCING = 16, DATA_CAN_DUPLICATE = 17;
    public static final float[] THROW_SOUND_PITCHES = {0.5625F, 0.625F, 0.75F, 0.9375F, 1.0F, 1.0F, 1.125F, 1.25F, 1.5F, 1.875F, 2.0F, 2.25F, 2.5F, 3.0F, 3.75F, 4.0F};
    public static final int DUPLICATION_COOLDOWN_TICKS = 6000;
    private final MobInventory inventory = new MobInventory(1);
    private long duplicationCooldown;
    /** 주크박스가 있는 칸(cq). 춤추는 동안만 안다(저장하지 않는다). */
    private Pos jukeboxPos;
    private VibrationSystem.Data vibrationData = new VibrationSystem.Data();
    private final VibrationSystem.User vibrationUser = new AllayVibrationUser();
    private final DynamicGameEventListener<VibrationSystem.Listener> vibrationListener = new DynamicGameEventListener<>(new VibrationSystem.Listener(this));
    private final DynamicGameEventListener<JukeboxListener> jukeboxListener = new DynamicGameEventListener<>(new JukeboxListener());

    public Allay(ChunkLevel level, int id) {
        super(EntityType.ALLAY, level, id);
        moveControl = new FlyingMoveControl(this, 20, true);
        brain = makeBrain();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Brain<Allay> makeBrain() {
        Brain<Allay> b = new Brain<>(java.util.List.of(MemoryModuleType.PATH, MemoryModuleType.LOOK_TARGET, MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES, MemoryModuleType.WALK_TARGET,
                MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE, MemoryModuleType.HURT_BY, MemoryModuleType.NEAREST_VISIBLE_WANTED_ITEM, MemoryModuleType.LIKED_PLAYER,
                MemoryModuleType.LIKED_NOTEBLOCK_POSITION, MemoryModuleType.LIKED_NOTEBLOCK_COOLDOWN_TICKS, MemoryModuleType.ITEM_PICKUP_COOLDOWN_TICKS, MemoryModuleType.IS_PANICKING),
                (java.util.List) java.util.List.of(SensorType.NEAREST_LIVING_ENTITIES, SensorType.NEAREST_PLAYERS, SensorType.HURT_BY, SensorType.NEAREST_ITEMS));
        return AllayAi.makeBrain(b);
    }

    @SuppressWarnings("unchecked")
    public Brain<Allay> allayBrain() { return (Brain<Allay>) brain; }

    @Override
    protected void createAttributes(AttributeMap map) {
        super.createAttributes(map);
        map.add(Attribute.MAX_HEALTH, 20.0).add(Attribute.FLYING_SPEED, 0.1F).add(Attribute.MOVEMENT_SPEED, 0.1F).add(Attribute.ATTACK_DAMAGE, 2.0).add(Attribute.FOLLOW_RANGE, 48.0);
    }

    @Override
    protected PathNavigation createNavigation(ChunkLevel level) {
        FlyingPathNavigation nav = new FlyingPathNavigation(this, level);
        nav.setCanOpenDoors(false);
        nav.setCanFloat(true);
        nav.setCanPassDoors(true);
        return nav;
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_DANCING, SetEntityData.SERIALIZER_BOOLEAN, false);
        entityData.define(DATA_CAN_DUPLICATE, SetEntityData.SERIALIZER_BOOLEAN, true);
    }

    @Override
    protected void travel(float inX, float inY, float inZ) {
        if (isEffectiveAi()) {
            if (isInWater()) {
                moveRelative(0.02F, inX, inY, inZ);
                move(MOVER_SELF, vx(), vy(), vz());
                setDeltaMovement(vx() * 0.8F, vy() * 0.8F, vz() * 0.8F);
            } else if (isInLava()) {
                moveRelative(0.02F, inX, inY, inZ);
                move(MOVER_SELF, vx(), vy(), vz());
                setDeltaMovement(vx() * 0.5, vy() * 0.5, vz() * 0.5);
            } else {
                moveRelative(getSpeed(), inX, inY, inZ);
                move(MOVER_SELF, vx(), vy(), vz());
                setDeltaMovement(vx() * 0.91F, vy() * 0.91F, vz() * 0.91F);
            }
        }
    }

    @Override
    public boolean hurtBy(Damage source, float amount, Targetable attacker) {
        if (attacker instanceof Player p) {
            var liked = getBrain().getMemory(MemoryModuleType.LIKED_PLAYER);
            if (liked.isPresent() && p.uuid().equals(liked.get())) return false;
        }
        return super.hurtBy(source, amount, attacker);
    }

    @Override public String getAmbientSound() { return hasItemInHand() ? "entity.allay.ambient_with_item" : "entity.allay.ambient_without_item"; }

    @Override protected String getHurtSound(Damage source) { return "entity.allay.hurt"; }

    @Override protected String getDeathSound() { return "entity.allay.death"; }

    @Override protected float getSoundVolume() { return 0.4F; }

    @Override
    protected void customServerAiStep() {
        allayBrain().tick(level, this);
        AllayAi.updateActivity(this);
        super.customServerAiStep();
    }

    @Override
    public void tick() {
        super.tick();
        if (isAlive() && tickCount % 10 == 0) heal(1.0F);
        if (isDancing() && shouldStopDancing() && tickCount % 20 == 0) {
            setDancing(false);
            jukeboxPos = null;
        }
        updateDuplicationCooldown();

    }

    @Override public boolean canPickUpLoot() { return !isOnPickupCooldown() && hasItemInHand(); }

    public boolean hasItemInHand() { return !getItemBySlot(EquipmentSlot.MAINHAND).isEmpty(); }

    private boolean isOnPickupCooldown() { return getBrain().hasMemoryValue(MemoryModuleType.ITEM_PICKUP_COOLDOWN_TICKS); }

    @Override
    public boolean interact(Player p, int hand, ItemStack held, org.domi.furnace.server.world.Region region) {
        ItemStack inHand = getItemBySlot(EquipmentSlot.MAINHAND);
        if (isDancing() && isDuplicationItem(held) && canDuplicate()) {
            duplicateAllay();
            broadcastEntityEvent((byte) 18);
            level.playSound(new org.domi.furnace.server.block.PlayerActor(p), this, "block.amethyst_block.chime", "neutral", 2.0F, 1.0F);
            usePlayerItem(p, hand, held);
            return true;
        } else if (inHand.isEmpty() && !held.isEmpty()) {
            setItemSlot(EquipmentSlot.MAINHAND, held.withCount(1));
            usePlayerItem(p, hand, held);
            level.playSound(new org.domi.furnace.server.block.PlayerActor(p), this, "entity.allay.item_given", "neutral", 2.0F, 1.0F);
            getBrain().setMemory(MemoryModuleType.LIKED_PLAYER, p.uuid());
            return true;
        } else if (!inHand.isEmpty() && hand == 0 && held.isEmpty()) {
            setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            level.playSound(new org.domi.furnace.server.block.PlayerActor(p), this, "entity.allay.item_taken", "neutral", 2.0F, 1.0F);
            swing(0);
            for (ItemStack s : inventory.removeAllItems()) BehaviorUtils.throwItem(this, s, new double[] {x(), y(), z()});
            getBrain().eraseMemory(MemoryModuleType.LIKED_PLAYER);
            ItemStack left = p.inventory().add(inHand, false, ItemStackCodecs.maxStackSize(inHand), false);
            if (!left.isEmpty()) region.playerDrop(p, left, false, true);
            return true;
        }
        return super.interact(p, hand, held, region);
    }

    @Override
    public boolean wantsToPickUp(ItemStack stack) {
        ItemStack inHand = getItemBySlot(EquipmentSlot.MAINHAND);
        return !inHand.isEmpty() && level.gameRule("mobGriefing") && inventory.canAddItem(stack) && allayConsidersItemEqual(inHand, stack);
    }

    private boolean allayConsidersItemEqual(ItemStack a, ItemStack b) {
        return a.itemId() == b.itemId() && java.util.Objects.equals(a.patch().entries().get("minecraft:potion_contents"), b.patch().entries().get("minecraft:potion_contents"));
    }

    @Override
    protected void pickUpItem(ItemEntity ie) { InventoryCarrier.pickUpItem(this, this, ie); }

    @Override
    protected void dropEquipment() {
        super.dropEquipment();
        for (ItemStack s : inventory.removeAllItems()) spawnAtLocation(s);
        ItemStack inHand = getItemBySlot(EquipmentSlot.MAINHAND);
        if (!inHand.isEmpty()) {   // TODO(인챈트): 장비 떨굼 방지(귀속 저주)
            spawnAtLocation(inHand);
            setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        }
    }

    @Override public boolean removeWhenFarAway(double distSqr) { return false; }

    @Override public MobInventory getInventory() { return inventory; }

    public boolean isDancing() { return (Boolean) entityData.get(DATA_DANCING); }

    public void setDancing(boolean v) {
        if (db() && (!v || !isPanicking())) entityData.set(DATA_DANCING, v);
    }

    /** Entity.isEffectiveAi 와 같은 서버 쪽 판정(죽지 않고 AI 가 있다). */
    private boolean db() { return !isNoAi(); }

    private boolean canDuplicate() { return (Boolean) entityData.get(DATA_CAN_DUPLICATE); }

    private void updateDuplicationCooldown() {
        if (duplicationCooldown > 0L) duplicationCooldown--;
        if (duplicationCooldown == 0L && !canDuplicate()) entityData.set(DATA_CAN_DUPLICATE, true);
    }

    private boolean isDuplicationItem(ItemStack s) { return !s.isEmpty() && s.itemId() == ItemContext.id("minecraft:item", "amethyst_shard"); }

    private void duplicateAllay() {
        Allay copy = new Allay(level, level.nextEntityId());
        copy.moveTo(x(), y(), z(), 0.0F, 0.0F);
        copy.persistenceRequired = true;
        copy.resetDuplicationCooldown();
        resetDuplicationCooldown();
        level.addFreshEntity(copy);
    }

    private void resetDuplicationCooldown() {
        duplicationCooldown = DUPLICATION_COOLDOWN_TICKS;
        entityData.set(DATA_CAN_DUPLICATE, false);
    }

    @Override
    protected void addAdditionalSaveData(NbtCompound tag) {
        super.addAdditionalSaveData(tag);
        writeInventoryToSave(tag);
        tag.putLong("DuplicationCooldown", duplicationCooldown);
        tag.putBoolean("CanDuplicate", canDuplicate());
        tag.putCompound(VibrationSystem.Data.NBT_TAG_KEY, vibrationData.save());
    }

    @Override
    protected void readAdditionalSaveData(NbtCompound tag) {
        super.readAdditionalSaveData(tag);
        readInventoryFromSave(tag);
        duplicationCooldown = tag.getLong("DuplicationCooldown");
        entityData.set(DATA_CAN_DUPLICATE, tag.getBoolean("CanDuplicate"));
        if (tag.tags().get(VibrationSystem.Data.NBT_TAG_KEY) instanceof NbtCompound l) {
            VibrationSystem.Data d = VibrationSystem.Data.load(l);
            if (d != null) vibrationData = d;
        }
    }

    // ------------------------------------------------------------------ 진동·주크박스 수신(cgk.a, cgk.b)

    /** Allay.aiStep(cgk.l) 서버 쪽: 진동 틱 + 겁먹으면 춤을 멈춘다. */
    @Override
    protected void aiStep() {
        super.aiStep();
        VibrationSystem.Ticker.tick(level, vibrationData, vibrationUser);
        if (isPanicking()) setDancing(false);
    }

    @Override public DynamicGameEventListener<?> dynamicGameEventListener() { return vibrationListener; }

    @Override
    public java.util.List<DynamicGameEventListener<?>> dynamicGameEventListeners() { return java.util.List.of(vibrationListener, jukeboxListener); }

    @Override public Data getVibrationData() { return vibrationData; }

    @Override public User getVibrationUser() { return vibrationUser; }

    /** shouldStopDancing(gp): 주크박스를 모르거나, 10칸 안(칸 가운데 기준 제곱거리 < 100)이 아니거나, 그 칸이 주크박스가 아니다. */
    private boolean shouldStopDancing() {
        if (jukeboxPos == null) return true;
        double dx = x() - (jukeboxPos.x() + 0.5), dy = y() - (jukeboxPos.y() + 0.5), dz = z() - (jukeboxPos.z() + 0.5);
        double r = GameEvent.JUKEBOX_PLAY.notificationRadius;
        return !(dx * dx + dy * dy + dz * dz < r * r) || !org.domi.furnace.server.block.B.is(level.getBlockState(jukeboxPos), "minecraft:jukebox");
    }

    /** setJukeboxPlaying(b): 시작 때 아직 춤추지 않으면 그 칸을 기억하고 춤춘다. 멈춤은 기억한 칸(또는 모를 때)일 때만. */
    public void setJukeboxPlaying(Pos pos, boolean playing) {
        if (playing) {
            if (!isDancing()) {
                jukeboxPos = pos;
                setDancing(true);
            }
        } else if (pos.equals(jukeboxPos) || jukeboxPos == null) {
            jukeboxPos = null;
            setDancing(false);
        }
    }

    /** cgk.a: jukebox_play / jukebox_stop_play 를 바로 받는다(진동 이동 시간 없음). */
    private final class JukeboxListener implements GameEventListener {
        private final PositionSource source = PositionSource.entity(Allay.this, EntityType.ALLAY.eyeHeight());

        @Override public PositionSource getListenerSource() { return source; }

        @Override public int getListenerRadius() { return GameEvent.JUKEBOX_PLAY.notificationRadius; }

        @Override
        public boolean handleGameEvent(ChunkLevel level, GameEvent event, GameEventContext ctx, Vec3 pos) {
            if (event == GameEvent.JUKEBOX_PLAY) {
                setJukeboxPlaying(new Pos((int) Math.floor(pos.x()), (int) Math.floor(pos.y()), (int) Math.floor(pos.z())), true);
                return true;
            } else if (event == GameEvent.JUKEBOX_STOP_PLAY) {
                setJukeboxPlaying(new Pos((int) Math.floor(pos.x()), (int) Math.floor(pos.y()), (int) Math.floor(pos.z())), false);
                return true;
            }
            return false;
        }
    }

    /** cgk.b: 반경 16. 좋아하는 음표 블록이 있으면 그 칸 소리만, 없으면 어느 것이든 받는다. 음표 블록 소리면 그 칸을 기억한다. */
    private final class AllayVibrationUser implements VibrationSystem.User {
        private final PositionSource positionSource = PositionSource.entity(Allay.this, EntityType.ALLAY.eyeHeight());

        @Override public int getListenerRadius() { return 16; }

        @Override public PositionSource getPositionSource() { return positionSource; }

        /** cgk.b.c(): 알레이가 듣는 이벤트는 #allay_can_listen 만(걸음·블록 설치 진동이 음표 블록 소리를 밀어내지 않게). */
        @Override public String getListenableEvents() { return GameEvent.ALLAY_CAN_LISTEN; }

        @Override
        public boolean canReceiveVibration(ChunkLevel level, Pos pos, GameEvent event, GameEventContext ctx) {
            if (isNoAi()) return false;
            var liked = getBrain().getMemory(MemoryModuleType.LIKED_NOTEBLOCK_POSITION);
            if (liked.isEmpty()) return true;
            var g = liked.get();
            return g.dimension().equals(level.dimension()) && g.pos().equals(pos);
        }

        @Override
        public void onReceiveVibration(ChunkLevel level, Pos pos, GameEvent event, Object source, Object projectileOwner, float distance) {
            if (event == GameEvent.NOTE_BLOCK_PLAY) AllayAi.hearNoteblock(Allay.this, pos);
        }
    }

    /** Allay.playStepSound: 소리 없음(날거나 헤엄치는 몸). */
    @Override
    protected void playStepSound(org.domi.furnace.server.block.Pos pos, int state) {}
}
