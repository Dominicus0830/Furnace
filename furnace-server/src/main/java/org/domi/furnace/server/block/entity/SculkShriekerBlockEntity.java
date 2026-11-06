// 바닐라 SculkShriekerBlockEntity: 조각 감지기 딸깍(#shrieker_can_listen)을 듣거나 밟히면 원인 플레이어로 비명(tryShriek).
// 비명이 끝나면(예약 틱) 소환할 수 있는 비명체는 경고 단계에 따라 워든 근처 소리를 내거나(1..3) 워든을 소환한다(4), 그리고 어둠을 건다.
package org.domi.furnace.server.block.entity;

import java.util.OptionalInt;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.Entity;
import org.domi.furnace.server.entity.ItemEntity;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.entity.Projectile;
import org.domi.furnace.server.entity.Vec3;
import org.domi.furnace.server.entity.Warden;
import org.domi.furnace.server.entity.WardenSpawnTracker;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.world.gameevent.GameEvent;
import org.domi.furnace.server.world.gameevent.GameEventContext;
import org.domi.furnace.server.world.gameevent.GameEventListener;
import org.domi.furnace.server.world.gameevent.PositionSource;
import org.domi.furnace.server.world.gameevent.VibrationSystem;

public class SculkShriekerBlockEntity extends BlockEntity implements VibrationSystem, GameEventListener.Provider {

    private static final int SHRIEKER = B.id("sculk_shrieker");
    private static final org.domi.furnace.server.block.Prop SHRIEKING = B.prop("shrieking"), CAN_SUMMON = B.prop("can_summon");
    private static final int DARKNESS_RADIUS = 40, SHRIEKING_TICKS = 90;
    /** SOUND_BY_LEVEL. */
    private static final String[] SOUND_BY_LEVEL = {null, "minecraft:entity.warden.nearby_close", "minecraft:entity.warden.nearby_closer",
            "minecraft:entity.warden.nearby_closest", "minecraft:entity.warden.listening_angry"};

    private int warningLevel;
    private VibrationSystem.Data vibrationData = new VibrationSystem.Data();
    private final VibrationSystem.Listener vibrationListener = new VibrationSystem.Listener(this);
    private final VibrationSystem.User vibrationUser = new VibrationUser();
    private BlockLevel level;

    public SculkShriekerBlockEntity(Pos pos, int state) { super("minecraft:sculk_shrieker", pos, state); }

    @Override
    public Data getVibrationData() { return vibrationData; }

    @Override
    public User getVibrationUser() { return vibrationUser; }

    @Override
    public GameEventListener getListener() { return vibrationListener; }

    public int warningLevel() { return warningLevel; }

    @Override
    public void load(NbtCompound tag) {
        if (tag.containsNumber("warning_level")) warningLevel = tag.getIntOr0("warning_level");
        if (tag.tags().get(Data.NBT_TAG_KEY) instanceof NbtCompound l) {
            Data d = Data.load(l);
            if (d != null) vibrationData = d;
        }
    }

    @Override
    protected void saveAdditional(NbtCompound tag) {
        tag.putInt("warning_level", warningLevel);
        tag.putCompound(Data.NBT_TAG_KEY, vibrationData.save());
    }

    @Override
    public boolean ticking() { return true; }

    @Override
    public void tick(BlockLevel level) {
        this.level = level;
        if (level instanceof ChunkLevel cl) VibrationSystem.Ticker.tick(cl, vibrationData, vibrationUser);
    }

    @Override
    public boolean isValid(int state) { return B.block(state) == SHRIEKER; }

    /** tryGetPlayer: 플레이어, 조종하는 플레이어, 투사체·아이템을 던진 플레이어. */
    public static Player tryGetPlayer(Object e) {
        if (e instanceof Player p) return p;
        if (e instanceof Entity en && en.getControllingPassenger() instanceof Player p) return p;
        if (e instanceof Projectile pr && pr.getOwner() instanceof Player p) return p;
        if (e instanceof ItemEntity it && it.getOwnerEntity() instanceof Player p) return p;
        return null;
    }

    public void tryShriek(ChunkLevel level, Player player) {
        if (player == null) return;
        int state = blockState();
        if (SHRIEKING.is(state)) return;
        warningLevel = 0;
        if (!canRespond(level) || tryToWarn(level, player)) shriek(level, player);
    }

    private boolean tryToWarn(ChunkLevel level, Player player) {
        OptionalInt w = WardenSpawnTracker.tryWarn(level, pos, player);
        w.ifPresent(v -> warningLevel = v);
        return w.isPresent();
    }

    private void shriek(ChunkLevel level, Object source) {
        int state = blockState();
        level.setBlock(pos, SHRIEKING.with(state, true), 2);
        level.scheduleTick(pos, B.block(state), SHRIEKING_TICKS);
        level.levelEvent(null, 3007, pos, 0);
        level.gameEventFrom(GameEvent.SHRIEK.name, pos, source, -1);
    }

    private boolean canRespond(ChunkLevel level) {
        return CAN_SUMMON.is(blockState()) && level.difficulty() != 0 && level.gameRule("doWardenSpawning");
    }

    /** tryRespond: 비명이 끝났을 때(예약 틱)·부서질 때. */
    public void tryRespond(ChunkLevel level) {
        if (canRespond(level) && warningLevel > 0) {
            if (!trySummonWarden(level)) playWardenReplySound(level);
            Warden.applyDarknessAround(level, new Vec3(pos.x() + 0.5, pos.y() + 0.5, pos.z() + 0.5), null, DARKNESS_RADIUS);
        }
    }

    private void playWardenReplySound(ChunkLevel level) {
        String sound = warningLevel >= 1 && warningLevel <= 4 ? SOUND_BY_LEVEL[warningLevel] : null;
        if (sound != null) {
            var r = level.random();
            int x = pos.x() + (r.nextInt(21) - 10);   // Mth.randomBetweenInclusive(random, -10, 10)
            int y = pos.y() + (r.nextInt(21) - 10);
            int z = pos.z() + (r.nextInt(21) - 10);
            level.playSound(null, (double) x, (double) y, (double) z, sound, "hostile", 5.0F, 1.0F);
        }
    }

    private boolean trySummonWarden(ChunkLevel level) {
        return warningLevel >= 4 && Warden.trySpawnTriggered(level, pos);
    }

    private final class VibrationUser implements VibrationSystem.User {
        private final PositionSource positionSource = PositionSource.block(pos);

        @Override
        public int getListenerRadius() { return 8; }

        @Override
        public PositionSource getPositionSource() { return positionSource; }

        @Override
        public String getListenableEvents() { return GameEvent.SHRIEKER_CAN_LISTEN; }

        @Override
        public boolean canReceiveVibration(ChunkLevel level, Pos p, GameEvent event, GameEventContext ctx) {
            return !SHRIEKING.is(blockState()) && tryGetPlayer(ctx.sourceEntity()) != null;
        }

        @Override
        public void onReceiveVibration(ChunkLevel level, Pos p, GameEvent event, Object sourceEntity, Object projectileOwner, float distance) {
            tryShriek(level, tryGetPlayer(projectileOwner != null ? projectileOwner : sourceEntity));
        }

        @Override
        public void onDataChanged() {
            if (level != null) setChanged(level);
        }

        @Override
        public boolean requiresAdjacentChunksToBeTicking() { return true; }
    }
}
