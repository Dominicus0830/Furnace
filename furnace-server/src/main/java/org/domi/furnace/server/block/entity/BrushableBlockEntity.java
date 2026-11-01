// 바닐라 BrushableBlockEntity(수상한 모래·자갈): 솔질 횟수(brushCount)·쿨다운·초기화 시각, 전리품 표(LootTable/LootTableSeed)를 풀어 얻은 보존 아이템,
// 솔질한 면(hit_direction). 10번 솔질하면 아이템을 솔질한 면 쪽으로 내놓고 블록을 모래/자갈로 바꾼다.
// 저장 꼴: 표가 남았으면 LootTable(+Seed), 아니면 비어 있지 않은 item. hit_direction 은 갱신 태그(클라이언트용)에만 싣는다.
package org.domi.furnace.server.block.entity;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockActor;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;
import org.domi.furnace.server.block.types.BrushableBlock;
import org.domi.furnace.server.entity.ItemEntity;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.entity.ItemStackNbt;
import org.domi.furnace.server.net.io.nbt.NbtCompound;

public class BrushableBlockEntity extends BlockEntity {

    /** 솔질 한 번의 간격(틱) · 마지막 솔질 뒤 되돌리기 시작까지(틱) · 되돌릴 때 감소 간격 · 완료까지 횟수. */
    public static final int BRUSH_COOLDOWN_TICKS = 10, BRUSH_RESET_TICKS = 40, REQUIRED_BRUSHES_TO_BREAK = 10, RESET_STEP_TICKS = 4;
    private static final Prop DUSTED = B.prop("dusted");

    private int brushCount;
    private long brushCountResetsAtTick, coolDownEndsAtTick;
    private ItemStack item = ItemStack.EMPTY;
    private int hitDirection = -1;
    private String lootTable;
    private long lootTableSeed;

    public BrushableBlockEntity(Pos pos, int state) { super("minecraft:brushable_block", pos, state); }

    @Override public boolean isValid(int state) { return B.behavior(state) instanceof BrushableBlock; }

    @Override public boolean hasUpdatePacket() { return true; }

    public int brushCount() { return brushCount; }

    public ItemStack item() { return item; }

    public int hitDirection() { return hitDirection; }

    public String lootTable() { return lootTable; }

    public long lootTableSeed() { return lootTableSeed; }

    /** setLootTable(table, seed). */
    public void setLootTable(String table, long seed) {
        this.lootTable = table;
        this.lootTableSeed = seed;
    }

    /** 표 없이 아이템을 바로 둔다(시험·명령용). */
    public void setItem(ItemStack s) { this.item = s == null ? ItemStack.EMPTY : s; }

    /**
     * brush(gameTime, player, hitDirection): 반환이 참이면 다 닦인 것(솔 내구도 1 소모). hitDirection 은 처음 한 번만 기억한다.
     * 쿨다운(10틱) 안이면 아무 일도 없다.
     */
    public boolean brush(BlockLevel level, long gameTime, BlockActor player, int direction) {
        if (hitDirection < 0) hitDirection = direction;
        brushCountResetsAtTick = gameTime + BRUSH_RESET_TICKS;
        if (gameTime >= coolDownEndsAtTick && level instanceof ChunkLevel) {
            coolDownEndsAtTick = gameTime + BRUSH_COOLDOWN_TICKS;
            unpackLootTable(level, player);
            int before = completionState();
            if (++brushCount >= REQUIRED_BRUSHES_TO_BREAK) {
                brushingCompleted(level, player);
                return true;
            }
            level.scheduleTick(pos, B.block(level.getBlockState(pos)), 2);
            int after = completionState();
            if (before != after) level.setBlock(pos, DUSTED.with(level.getBlockState(pos), after), 3);
            return false;
        }
        return false;
    }

    /** unpackLootTable(player): 표가 남았으면 CHEST 문맥으로 풀어 아이템 하나를 정한다(여럿이면 첫 번째). 표는 지운다. */
    public void unpackLootTable(BlockLevel level, BlockActor player) {
        if (lootTable == null || !(level instanceof ChunkLevel cl)) return;
        var list = cl.chestLoot(lootTable, lootTableSeed, pos.x() + 0.5, pos.y() + 0.5, pos.z() + 0.5,
                player == null ? null : player.handle(), player == null ? 0.0F : player.luck());
        item = list.isEmpty() ? ItemStack.EMPTY : list.get(0);   // 둘 이상이면 바닐라는 경고 후 첫 번째
        lootTable = null;
        setChanged(level);
    }

    /** brushingCompleted: 아이템을 내놓고 완료 이벤트(3008)를 보낸 뒤 블록을 turns_into 로 바꾼다. */
    private void brushingCompleted(BlockLevel level, BlockActor player) {
        dropContent(level, player);
        int state = level.getBlockState(pos);
        level.levelEvent(null, 3008, pos, state);
        int next = B.behavior(state) instanceof BrushableBlock bb ? bb.turnsInto() : B.id("air");
        level.setBlock(pos, B.defaultState(next), 3);
    }

    /**
     * dropContent: 솔질한 면(없으면 위) 옆 칸 가운데 아래쪽에 아이템 엔티티를 속도 0 으로 낸다. 개수는 레벨 난수 nextInt(21)+10 (그 뒤
     * 아이템 엔티티 생성자가 속도용 난수 두 번).
     */
    private void dropContent(BlockLevel level, BlockActor player) {
        unpackLootTable(level, player);
        if (item.isEmpty() || !(level instanceof ChunkLevel cl)) return;
        double d1 = 1.0 - 0.25, d2 = 0.125;   // EntityType.ITEM 폭 0.25
        int dir = hitDirection < 0 ? Dir.UP : hitDirection;
        Pos p = pos.relative(dir);
        double x = p.x() + 0.5 * d1 + d2, y = p.y() + 0.5 + 0.25F / 2.0F, z = p.z() + 0.5 * d1 + d2;
        int n = cl.random().nextInt(21) + 10;
        ItemStack out = item.withCount(Math.min(n, item.count()));
        var ie = new ItemEntity(cl, cl.nextEntityId(), x, y, z, out);
        ie.setDeltaMovement(0.0, 0.0, 0.0);
        cl.addFreshEntity(ie);
        item = ItemStack.EMPTY;
    }

    /** checkReset: 마지막 솔질 40틱 뒤부터 4틱마다 2씩 줄여 간다. 0 이 되면 면·시각을 지운다. 블록 틱(BrushableBlock.tick)에서 부른다. */
    public void checkReset(BlockLevel level) {
        if (brushCount != 0 && level.gameTime() >= brushCountResetsAtTick) {
            int before = completionState();
            brushCount = Math.max(0, brushCount - 2);
            int after = completionState();
            if (before != after) level.setBlock(pos, DUSTED.with(level.getBlockState(pos), after), 3);
            brushCountResetsAtTick = level.gameTime() + RESET_STEP_TICKS;
        }
        if (brushCount == 0) {
            hitDirection = -1;
            brushCountResetsAtTick = 0L;
            coolDownEndsAtTick = 0L;
        } else {
            level.scheduleTick(pos, B.block(level.getBlockState(pos)), 2);
        }
    }

    /** getCompletionState: 0 / 1~2 → 1 / 3~5 → 2 / 6~ → 3. */
    public int completionState() {
        if (brushCount == 0) return 0;
        if (brushCount < 3) return 1;
        return brushCount < 6 ? 2 : 3;
    }

    @Override
    public NbtCompound updateTag() {
        NbtCompound tag = new NbtCompound();
        if (hitDirection >= 0) tag.putInt("hit_direction", hitDirection);
        if (!item.isEmpty()) tag.putCompound("item", ItemStackNbt.write(item));
        return tag;
    }

    @Override
    public void load(NbtCompound tag) {
        lootTable = null;
        lootTableSeed = 0L;
        item = ItemStack.EMPTY;
        if (tag.tags().get("LootTable") instanceof String t) {
            lootTable = t;
            lootTableSeed = tag.tags().get("LootTableSeed") instanceof Long seed ? seed : 0L;
        } else if (tag.tags().get("item") instanceof NbtCompound it && org.domi.furnace.server.item.ItemContext.installed()) {
            item = ItemStackNbt.read(it);
        }
        if (tag.contains("hit_direction")) {
            int d = tag.getInt("hit_direction");
            hitDirection = d >= 0 && d < 6 ? d : -1;
        }
    }

    @Override
    protected void saveAdditional(NbtCompound tag) {
        if (lootTable != null) {   // trySaveLootTable: 표가 있으면 아이템은 쓰지 않는다
            tag.putString("LootTable", lootTable);
            if (lootTableSeed != 0L) tag.putLong("LootTableSeed", lootTableSeed);
        } else if (!item.isEmpty() && org.domi.furnace.server.item.ItemContext.installed()) {
            tag.putCompound("item", ItemStackNbt.write(item));
        }
    }
}
