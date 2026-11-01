// 바닐라 VaultBlockEntity: 금고. config(전리품 표·범위·열쇠), shared_data(클라이언트와 공유), server_data(서버 전용)를 가진다.
// 서버 틱(Server.tick): 20틱마다 활성 금고의 표시 아이템을 전리품 표에서 새로 뽑고, 갱신 시각이 되면 상태를 한 칸 옮긴다.
//   inactive/active: 범위 안(보상 받은 사람 제외) 플레이어 유무로 active↔inactive(20틱 쉼). unlocking: 20틱 뒤 ejecting. ejecting: 20틱마다 아이템 하나를 위로 내놓고, 다 내놓으면 범위 확인.
// 열쇠(Server.tryInsertKey): 활성일 때만. 열쇠가 맞지 않으면 실패 소리(15틱에 한 번), 이미 보상 받은 플레이어면 거절 소리, 아니면 전리품 표(플레이어 문맥·행운)로 보상을 뽑아 unlocking.
// 클라이언트 몫(생략): 입자·주변 소리·표시 아이템 회전. 이벤트 3015~3017(활성화/비활성화/아이템 배출)과 서버 소리는 보낸다.
package org.domi.furnace.server.block.entity;

import java.util.List;
import java.util.UUID;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockActor;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.item.ItemTools;
import org.domi.furnace.server.loot.LootEntity;
import org.domi.furnace.server.net.io.nbt.NbtCompound;

public class VaultBlockEntity extends BlockEntity {

    static final Prop VAULT_STATE = B.prop("vault_state"), OMINOUS = B.prop("ominous");

    private final VaultServerData serverData = new VaultServerData();
    private final VaultSharedData sharedData = new VaultSharedData();
    private VaultConfig config;

    public VaultBlockEntity(Pos pos, int state) {
        super("minecraft:vault", pos, state);
        config = VaultConfig.defaultConfig();
    }

    @Override public boolean isValid(int state) { return B.is(state, "vault"); }

    public VaultServerData serverData() { return serverData; }

    public VaultSharedData sharedData() { return sharedData; }

    public VaultConfig config() { return config; }

    /** 시험용: 설정을 바꾼다. */
    public void setConfig(VaultConfig c) { config = c; }

    public VaultState getVaultState() { return VaultState.byName(VAULT_STATE.valueName(blockState)); }

    @Override
    public void load(NbtCompound tag) {
        if (tag.tags().get("server_data") instanceof NbtCompound c) serverData.load(c);
        if (tag.tags().get("config") instanceof NbtCompound c) {
            VaultConfig parsed = VaultConfig.load(c);
            if (parsed != null) config = parsed;
        }
        if (tag.tags().get("shared_data") instanceof NbtCompound c) sharedData.load(c);
    }

    @Override
    protected void saveAdditional(NbtCompound tag) {
        tag.putCompound("config", config.save());
        tag.putCompound("shared_data", sharedData.save());
        tag.putCompound("server_data", serverData.save());
    }

    /** getUpdateTag: shared_data 만. */
    @Override
    public NbtCompound updateTag() {
        NbtCompound t = new NbtCompound();
        t.putCompound("shared_data", sharedData.save());
        return t;
    }

    @Override public boolean hasUpdatePacket() { return true; }

    @Override public boolean ticking() { return true; }

    @Override
    public void tick(BlockLevel level) {
        if (level instanceof ChunkLevel cl) Server.tick(cl, this);
    }

    /** VaultBlockEntity.Server — 서버 쪽 동작. */
    public static final class Server {
        private static final int UPDATE_PERIOD_TICKS = 20;
        private static final int UNLOCKING_DELAY_TICKS = 14;
        private static final int DISPLAY_CYCLE_TICKS = 20;
        private static final int INSERT_FAIL_SOUND_BUFFER_TICKS = 15;

        private Server() {}

        public static void tick(ChunkLevel level, VaultBlockEntity be) {
            Pos pos = be.pos;
            int state = be.blockState;
            VaultServerData sd = be.serverData;
            VaultSharedData sh = be.sharedData;
            VaultState vs = VaultState.byName(VAULT_STATE.valueName(state));
            if (shouldCycleDisplayItem(level.gameTime(), vs)) cycleDisplayItemFromLootTable(level, vs, be.config, sh, pos);
            if (level.gameTime() >= sd.stateUpdatingResumesAt) {
                int newState = VAULT_STATE.with(state, tickAndGetNext(vs, level, pos, be.config, sd, sh).getSerializedName());
                if (newState != state) setVaultState(level, pos, state, newState, be.config, sh);
            }
            if (sd.isDirty || sh.isDirty) {
                be.setChanged(level);
                if (sh.isDirty) level.sendBlockUpdated(pos);
                sd.isDirty = false;
                sh.isDirty = false;
            }
        }

        /** VaultState.tickAndGetNext. */
        static VaultState tickAndGetNext(VaultState s, ChunkLevel level, Pos pos, VaultConfig config, VaultServerData sd, VaultSharedData sh) {
            switch (s) {
                case INACTIVE:
                    return updateStateForConnectedPlayers(level, pos, config, sd, sh, config.activationRange());
                case ACTIVE:
                    return updateStateForConnectedPlayers(level, pos, config, sd, sh, config.deactivationRange());
                case UNLOCKING:
                    sd.pauseStateUpdatingUntil(level.gameTime() + UPDATE_PERIOD_TICKS);
                    return VaultState.EJECTING;
                case EJECTING:
                    if (sd.itemsToEject.isEmpty()) {
                        sd.markEjectionFinished();
                        return updateStateForConnectedPlayers(level, pos, config, sd, sh, config.deactivationRange());
                    } else {
                        float progress = sd.ejectionProgress();
                        ejectResultItem(level, pos, sd.popNextItemToEject(), progress);
                        sh.setDisplayItem(sd.getNextItemToEject());
                        sd.pauseStateUpdatingUntil(level.gameTime() + UPDATE_PERIOD_TICKS);   // 마지막이든 아니든 20틱
                        return VaultState.EJECTING;
                    }
                default:
                    return s;
            }
        }

        private static VaultState updateStateForConnectedPlayers(ChunkLevel level, Pos pos, VaultConfig config, VaultServerData sd, VaultSharedData sh, double range) {
            sh.updateConnectedPlayersWithinRange(level, pos, sd, config, range);
            sd.pauseStateUpdatingUntil(level.gameTime() + UPDATE_PERIOD_TICKS);
            return sh.hasConnectedPlayers() ? VaultState.ACTIVE : VaultState.INACTIVE;
        }

        /** setVaultState: 블록 상태를 바꾸고(3) 이탈/진입 효과. */
        static void setVaultState(ChunkLevel level, Pos pos, int oldState, int newState, VaultConfig config, VaultSharedData sh) {
            VaultState oldVs = VaultState.byName(VAULT_STATE.valueName(oldState)), newVs = VaultState.byName(VAULT_STATE.valueName(newState));
            level.setBlock(pos, newState, 3);
            if (oldVs == VaultState.EJECTING) level.playSound(null, pos, "minecraft:block.vault.close_shutter", "blocks", 1.0F, 1.0F);
            boolean ominous = OMINOUS.is(newState);
            switch (newVs) {
                case INACTIVE:
                    sh.setDisplayItem(ItemStack.EMPTY);
                    level.levelEvent(null, 3016, pos, ominous ? 1 : 0);
                    break;
                case ACTIVE:
                    if (!sh.hasDisplayItem()) cycleDisplayItemFromLootTable(level, newVs, config, sh, pos);
                    level.levelEvent(null, 3015, pos, ominous ? 1 : 0);
                    break;
                case UNLOCKING:
                    level.playSound(null, pos, "minecraft:block.vault.insert_item", "blocks", 1.0F, 1.0F);
                    break;
                case EJECTING:
                    level.playSound(null, pos, "minecraft:block.vault.open_shutter", "blocks", 1.0F, 1.0F);
                    break;
            }
        }

        private static void ejectResultItem(ChunkLevel level, Pos pos, ItemStack stack, float progress) {
            org.domi.furnace.server.block.types.CrafterBlock.spawnItem(level, stack, 2, Dir.UP, pos.x() + 0.5, pos.y() + 1.2, pos.z() + 0.5);
            level.levelEvent(null, 3017, pos, 0);
            level.playSound(null, pos, "minecraft:block.vault.eject_item", "blocks", 1.0F, 0.8F + 0.4F * progress);
        }

        static boolean shouldCycleDisplayItem(long gameTime, VaultState state) { return gameTime % DISPLAY_CYCLE_TICKS == 0L && state == VaultState.ACTIVE; }

        /** canEjectReward: 전리품 표가 비어 있지 않고, 열쇠가 있고, 비활성이 아니다. */
        static boolean canEjectReward(VaultConfig config, VaultState state) {
            return !config.lootTable().equals(VaultConfig.EMPTY_LOOT_TABLE) && !config.keyItem().isEmpty() && state != VaultState.INACTIVE;
        }

        /** cycleDisplayItemFromLootTable: 보상을 줄 수 없으면 비우고, 아니면 (덮어쓴) 표에서 레벨 난수로 뽑은 항목 중 하나. */
        static void cycleDisplayItemFromLootTable(ChunkLevel level, VaultState state, VaultConfig config, VaultSharedData sh, Pos pos) {
            if (!canEjectReward(config, state)) {
                sh.setDisplayItem(ItemStack.EMPTY);
            } else {
                sh.setDisplayItem(getRandomDisplayItemFromLootTable(level, pos, config.overrideLootTableToDisplay().orElse(config.lootTable())));
            }
        }

        /** getRandomDisplayItemFromLootTable: 표를 레벨 난수로 평가(origin = 칸 가운데)하고 nextInt(개수)로 하나. */
        static ItemStack getRandomDisplayItemFromLootTable(ChunkLevel level, Pos pos, String table) {
            List<ItemStack> items = level.lootItems(table, new double[] {pos.x() + 0.5, pos.y() + 0.5, pos.z() + 0.5}, null, 0.0F, 0L, level.random());
            return items.isEmpty() ? ItemStack.EMPTY : items.get(level.random().nextInt(items.size()));
        }

        private static List<ItemStack> resolveItemsToEject(ChunkLevel level, VaultConfig config, Pos pos, BlockActor player) {
            LootEntity self = new LootEntity() {
                @Override public String type() { return "minecraft:player"; }
                @Override public double[] position() { return new double[] {player.x(), player.y(), player.z()}; }
                @Override public float luck() { return player.luck(); }
            };
            return level.lootItems(config.lootTable(), new double[] {pos.x() + 0.5, pos.y() + 0.5, pos.z() + 0.5}, self, player.luck(), 0L, null);
        }

        /** isValidToInsert: 같은 아이템·같은 컴포넌트이고 개수가 열쇠 이상. */
        static boolean isValidToInsert(VaultConfig config, ItemStack stack) {
            return !stack.isEmpty() && stack.sameItemSameComponents(config.keyItem()) && stack.count() >= config.keyItem().count();
        }

        private static void playInsertFailSound(ChunkLevel level, VaultServerData sd, Pos pos, String sound) {
            if (level.gameTime() >= sd.lastInsertFailTimestamp + INSERT_FAIL_SOUND_BUFFER_TICKS) {
                level.playSound(null, pos, sound, "blocks", 1.0F, 1.0F);
                sd.setLastInsertFailTimestamp(level.gameTime());
            }
        }

        /**
         * tryInsertKey: 활성 금고에 손에 든 아이템을 넣는다. 성공하면 true(열쇠 소비 — 창작 모드는 소비하지 않음, 통계 used, unlocking, 보상 목록,
         * 보상 받은 플레이어 기록, 연결 플레이어 갱신). 실패(열쇠가 아님·이미 보상 받음)도 처리했으면 true 가 아니라 false 를 돌려준다.
         */
        public static boolean tryInsertKey(ChunkLevel level, Pos pos, int state, VaultBlockEntity be, BlockActor player, int hand, ItemStack stack) {
            VaultConfig config = be.config;
            VaultServerData sd = be.serverData;
            VaultSharedData sh = be.sharedData;
            VaultState vs = VaultState.byName(VAULT_STATE.valueName(state));
            if (!canEjectReward(config, vs)) return false;
            if (!isValidToInsert(config, stack)) {
                playInsertFailSound(level, sd, pos, "minecraft:block.vault.insert_item_fail");
            } else if (sd.hasRewardedPlayer(player.uuid())) {
                playInsertFailSound(level, sd, pos, "minecraft:block.vault.reject_rewarded_player");
            } else {
                List<ItemStack> rewards = resolveItemsToEject(level, config, pos, player);
                if (!rewards.isEmpty()) {
                    player.awardStat("minecraft:used:" + ItemTools.itemName(stack));
                    if (!player.instabuild()) player.setItemInHand(hand, stack.withCount(stack.count() - config.keyItem().count()));
                    unlock(level, state, pos, config, sd, sh, rewards);
                    UUID id = player.uuid();
                    if (id != null) sd.addToRewardedPlayers(id);
                    sh.updateConnectedPlayersWithinRange(level, pos, sd, config, config.deactivationRange());
                    be.setChanged(level);
                    return true;
                }
            }
            return false;
        }

        /** unlock: 보상 목록을 넣고 표시 아이템을 다음 보상으로, 14틱 쉰 뒤 unlocking 상태. */
        private static void unlock(ChunkLevel level, int state, Pos pos, VaultConfig config, VaultServerData sd, VaultSharedData sh, List<ItemStack> rewards) {
            sd.setItemsToEject(rewards);
            sh.setDisplayItem(sd.getNextItemToEject());
            sd.pauseStateUpdatingUntil(level.gameTime() + UNLOCKING_DELAY_TICKS);
            setVaultState(level, pos, state, VAULT_STATE.with(state, VaultState.UNLOCKING.getSerializedName()), config, sh);
        }
    }
}
