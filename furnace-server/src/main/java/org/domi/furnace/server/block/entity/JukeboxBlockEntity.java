// 바닐라 JukeboxBlockEntity + JukeboxSongPlayer: 음반 한 장(RecordItem)과 재생 경과 틱(ticks_since_song_started).
// 재생 중 1초마다 음표 입자 위치용 난수 한 번(level.random.nextInt(4))을 쓴다 — 입자 패킷은 아직 없지만 난수는 바닐라와 같게 소비한다.
package org.domi.furnace.server.block.entity;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockActor;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.item.ItemNbt;
import org.domi.furnace.server.item.JukeboxSongs;
import org.domi.furnace.server.net.io.nbt.NbtCompound;

public class JukeboxBlockEntity extends BlockEntity {

    private static final Prop HAS_RECORD = B.prop("has_record");

    private ItemStack item = ItemStack.EMPTY;
    private JukeboxSongs.Song song;
    private long ticksSinceSongStarted;

    public JukeboxBlockEntity(Pos pos, int state) { super("minecraft:jukebox", pos, state); }

    @Override public boolean isValid(int state) { return B.is(state, "jukebox"); }

    @Override public boolean ticking() { return true; }

    public ItemStack theItem() { return item; }

    /** 부수 효과 없이 칸만 바꾼다(깔때기가 같은 스택의 수를 되돌릴 때). */
    void putRaw(ItemStack s) { item = s == null ? ItemStack.EMPTY : s; }

    /** JukeboxSongPlayer.isPlaying. */
    public boolean isPlaying() { return song != null; }

    public long ticksSinceSongStarted() { return ticksSinceSongStarted; }

    /** 바닐라 getComparatorOutput: 음반의 comparator_output(노래를 모르면 0). */
    public int comparatorOutput() {
        JukeboxSongs.Song s = JukeboxSongs.of(item);
        return s == null ? 0 : s.comparatorOutput();
    }

    /** 바닐라 setTheItem: 상태(has_record)를 바꾸고 노래가 있으면 재생 시작(levelEvent 1010), 아니면 정지(1011). */
    public void setTheItem(ItemStack stack, BlockLevel level) {
        item = stack;
        boolean has = !item.isEmpty();
        JukeboxSongs.Song s = JukeboxSongs.of(item);
        setHasRecordBlockState(level, has);
        if (has && s != null) startPlaying(level, s);
        else stopPlaying(level, level.getBlockState(pos));
        setChanged(level);
    }

    private void setHasRecordBlockState(BlockLevel level, boolean has) {
        int cur = level.getBlockState(pos);
        if (B.is(cur, "jukebox")) {   // 이미 다른 블록이 된 뒤(onRemove)에는 건드리지 않는다
            level.setBlock(pos, HAS_RECORD.with(cur, has), 2);
            level.gameEvent("minecraft:block_change", pos, null, cur);
        }
    }

    private void startPlaying(BlockLevel level, JukeboxSongs.Song s) {
        song = s;
        ticksSinceSongStarted = 0L;
        level.levelEvent(null, 1010, pos, s.id());
        setChanged(level);
    }

    private void stopPlaying(BlockLevel level, int state) {
        if (song != null) {
            song = null;
            ticksSinceSongStarted = 0L;
            level.gameEvent("minecraft:jukebox_stop_play", pos, null, state);
            level.levelEvent(null, 1011, pos, 0);
            setChanged(level);
        }
    }

    /** 바닐라 popOutTheItem: 음반을 비우고 칸 위(0.5, 1.01, 0.5 ± 0.35 난수 float 3번 x,y,z)에 아이템 엔티티로 내보낸다. */
    public void popOutTheItem(BlockLevel level) {
        if (item.isEmpty()) return;
        ItemStack out = item;
        setTheItem(ItemStack.EMPTY, level);
        var r = level.random();
        float f = 0.7F;
        double x = pos.x() + 0.5 + (r.nextFloat() - 0.5F) * f;
        double y = pos.y() + 1.01 + (r.nextFloat() - 0.5F) * f;
        double z = pos.z() + 0.5 + (r.nextFloat() - 0.5F) * f;
        level.spawnItemEntity(x, y, z, out);
    }

    /** JukeboxPlayable.tryInsertIntoJukebox 의 블록 엔티티 쪽: 한 장을 넣고 BLOCK_CHANGE. */
    public void insert(ItemStack one, BlockActor player, BlockLevel level) {
        setTheItem(one, level);
        level.gameEvent("minecraft:block_change", pos, player, level.getBlockState(pos));
    }

    @Override
    public void tick(BlockLevel level) {
        int state = level.getBlockState(pos);
        if (!HAS_RECORD.has(state) || !HAS_RECORD.is(state) || song == null) return;
        if (song.hasFinished(ticksSinceSongStarted)) {
            stopPlaying(level, state);
        } else {
            if (ticksSinceSongStarted % 20L == 0L) {
                level.gameEvent("minecraft:jukebox_play", pos, null, state);
                level.random().nextInt(4);   // spawnMusicParticles 의 음표 색 난수(입자 패킷은 아직 없다)
            }
            ticksSinceSongStarted++;
        }
    }

    @Override
    public void load(NbtCompound tag) {
        item = ItemStack.EMPTY;
        song = null;
        ticksSinceSongStarted = 0L;
        if (tag.tags().get("RecordItem") instanceof NbtCompound ie && ItemContext.installed()) {
            try {
                String name = ie.getString("id");
                Integer id = ItemContext.id("minecraft:item", name);
                int count = ie.contains("count") ? ie.getInt("count") : 1;
                var patch = id == null ? null : ItemNbt.read(ie, name, ItemNbt.LEGACY_KEY);
                if (id != null && id > 0 && count > 0 && patch != null) item = new ItemStack(id, count, patch);
            } catch (RuntimeException e) {
                // 이상한 음반은 없는 것으로
            }
        }
        if (tag.tags().get("ticks_since_song_started") instanceof Long t) {
            JukeboxSongs.Song s = JukeboxSongs.of(item);
            if (s != null && !s.hasFinished(t)) {   // setSongWithoutPlaying
                song = s;
                ticksSinceSongStarted = t;
            }
        }
    }

    @Override
    protected void saveAdditional(NbtCompound tag) {
        if (!item.isEmpty() && ItemContext.installed()) {
            NbtCompound e = new NbtCompound();
            e.putString("id", ItemContext.name("minecraft:item", item.itemId()));
            e.putInt("count", item.count());
            NbtCompound comps = ItemNbt.write(item.patch());
            if (comps != null) e.putCompound(ItemNbt.KEY, comps);
            tag.putCompound("RecordItem", e);
        }
        if (song != null) tag.putLong("ticks_since_song_started", ticksSinceSongStarted);
    }

    /** getUpdateTag(saveCustomOnly): 클라이언트가 음반·재생 경과를 알도록 그대로. */
    @Override
    public NbtCompound updateTag() { return saveWithoutMetadata(); }
}
