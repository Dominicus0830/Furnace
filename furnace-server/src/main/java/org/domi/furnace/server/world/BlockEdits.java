// 빠른 블록 쓰기(E7) 적용: 청크마다 그 청크를 가진 region 스레드에서, 칸마다 바닐라 Level.setBlock 길로 바꾼다.
// region 이 여럿이면 지금 주인별로 묶어 각 region 작업 큐에 하나씩 넣는다 — 묶음이 돌 때 주인이 바뀐(나뉜·합쳐진) 청크는 그때의 주인에게
// 다시 보낸다. 한 틱 한도(chunksPerTick)가 있으면 남은 청크는 다음 틱 지연 작업으로 이어 간다(합치기·나누기를 따라간다).
package org.domi.furnace.server.world;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Pos;

public final class BlockEdits {

    private BlockEdits() {}

    /**
     * 서버 쪽 옵션.
     * @param flags          칸마다 넘길 바닐라 setBlock 플래그(1 이웃, 2 클라이언트, 8 즉시, 16 모양 건너뛰기)
     * @param afterEdit      다 바꾼 뒤 바뀐 칸마다 blockUpdated(바닐라 /fill)
     * @param resendChunks   바꾼 청크를 보는 사람에게 청크째 다시 보낸다(그 청크의 칸 갱신은 버린다)
     * @param clear          다른 상태로 바꾸는 칸의 내용물을 먼저 비운다(Clearable.tryClear)
     * @param chunksPerTick  region 한 틱에 적용할 최대 청크 수(0 = 다)
     */
    public record Mode(int flags, boolean afterEdit, boolean resendChunks, boolean clear, int chunksPerTick) {}

    /** 결과 {바뀜, 그대로, 건너뜀}. */
    public record Result(int changed, int unchanged, int skipped) {}

    /** 묶음 하나(region 한 틱)를 적용한 결과를 모은다. */
    private static final class Counts {
        int changed, unchanged, skipped;
    }

    // ------------------------------------------------------------------ 비동기(아무 스레드)

    /** 모든 청크를 각자 주인 region 에서 적용한다. 버퍼는 이 뒤로 고치지 않는다. */
    public static CompletableFuture<Result> apply(World world, BlockEditBuffer buf, Mode mode) {
        Job job = new Job(world, buf, mode);
        if (buf.chunks().isEmpty()) {
            job.f.complete(new Result(0, 0, buf.outOfHeight()));
            return job.f;
        }
        job.skipped.add(buf.outOfHeight());
        job.dispatch(new ArrayList<>(buf.chunks()));
        return job.f;
    }

    private static final class Job {
        final World world;
        final Mode mode;
        final int minY;
        final CompletableFuture<Result> f = new CompletableFuture<>();
        final LongAdder changed = new LongAdder(), unchanged = new LongAdder(), skipped = new LongAdder();
        final AtomicInteger remaining;
        volatile boolean failed;

        Job(World world, BlockEditBuffer buf, Mode mode) {
            this.world = world;
            this.mode = mode;
            this.minY = buf.minY();
            this.remaining = new AtomicInteger(buf.chunks().size());
        }

        /** 청크들을 지금 주인별로 묶어 각 주인 작업 큐에 넣는다. 주인이 없는(로드 안 된) 청크는 건너뛴다. */
        void dispatch(List<BlockEditBuffer.ChunkEdit> list) {
            Map<Region, List<BlockEditBuffer.ChunkEdit>> groups = new IdentityHashMap<>();
            List<BlockEditBuffer.ChunkEdit> orphan = new ArrayList<>();
            for (var e : list) {
                Region r = world.regionizer().ownerOf(e.cx, e.cz);
                if (r == null) orphan.add(e);
                else groups.computeIfAbsent(r, k -> new ArrayList<>()).add(e);
            }
            for (var e : orphan) skip(e);
            for (var g : groups.values()) send(g);
        }

        private void send(List<BlockEditBuffer.ChunkEdit> group) {
            if (failed) return;
            var head = group.get(0);
            boolean sent;
            try {
                sent = !world.isUnloaded() && world.regionizer().enqueueIfOwned(Regionizer.chunkKey(head.cx, head.cz), r -> runGroup(r, group));
            } catch (RuntimeException ex) {   // 스케줄러가 닫히는 중
                fail(ex);
                return;
            }
            if (!sent) {   // 그 사이 주인이 없어졌다 — 머리는 건너뛰고 나머지는 다시 묶는다
                skip(head);
                if (group.size() > 1) dispatch(group.subList(1, group.size()));
            }
        }

        /** 주인 region 스레드에서: 이 region 것만 (한도까지) 적용하고, 남의 것은 다시 보내고, 남은 것은 다음 틱으로. */
        void runGroup(Region r, List<BlockEditBuffer.ChunkEdit> group) {
            if (failed) return;
            try {
                List<BlockEditBuffer.ChunkEdit> mine = new ArrayList<>(), others = new ArrayList<>();
                for (var e : group) (world.regionizer().owns(r, e.cx, e.cz) ? mine : others).add(e);
                if (!others.isEmpty()) dispatch(others);
                if (mine.isEmpty()) return;
                int limit = mode.chunksPerTick() <= 0 ? mine.size() : Math.min(mode.chunksPerTick(), mine.size());
                List<BlockEditBuffer.ChunkEdit> batch = mine.subList(0, limit);
                Counts c = applyBatch(r, batch, minY, mode);
                changed.add(c.changed);
                unchanged.add(c.unchanged);
                skipped.add(c.skipped);
                if (limit < mine.size()) {
                    List<BlockEditBuffer.ChunkEdit> rest = new ArrayList<>(mine.subList(limit, mine.size()));
                    var head = rest.get(0);
                    r.schedule(Regionizer.chunkKey(head.cx, head.cz), r2 -> runGroup(r2, rest), 1);
                }
                done(limit);
            } catch (Throwable t) {
                fail(t);
            }
        }

        private void skip(BlockEditBuffer.ChunkEdit e) {
            skipped.add(e.count);
            done(1);
        }

        private void done(int chunks) {
            if (remaining.addAndGet(-chunks) == 0 && !failed) {
                f.complete(new Result((int) changed.sum(), (int) unchanged.sum(), (int) skipped.sum()));
            }
        }

        private void fail(Throwable t) {
            failed = true;
            f.completeExceptionally(t);
        }
    }

    // ------------------------------------------------------------------ 동기(주인 region 스레드)

    /**
     * 이 스레드가 돌리는 region 에서 그 청크들을 한 번에 적용한다. 부르는 쪽이 청크마다 주인 스레드인지 먼저 확인했어야 한다
     * (청크 → 그 region). 로드 안 된 청크는 건너뛴다.
     */
    public static Result applyNow(Map<Region, List<BlockEditBuffer.ChunkEdit>> byRegion, BlockEditBuffer buf, Mode mode) {
        int changed = 0, unchanged = 0, skipped = buf.outOfHeight();
        for (var e : byRegion.entrySet()) {
            Counts c = applyBatch(e.getKey(), e.getValue(), buf.minY(), mode);
            changed += c.changed;
            unchanged += c.unchanged;
            skipped += c.skipped;
        }
        return new Result(changed, unchanged, skipped);
    }

    // ------------------------------------------------------------------ 한 region 의 한 묶음

    /** 주인 region 스레드에서: 청크들을 차례로 바꾸고, AFTER_EDIT 이웃 갱신·방송을 묶음 끝에 한다. */
    static Counts applyBatch(Region r, List<BlockEditBuffer.ChunkEdit> batch, int minY, Mode mode) {
        r.world().assertOwns(r);
        RegionLevel lv = r.level();
        Counts c = new Counts();
        List<Pos> after = mode.afterEdit() ? new ArrayList<>() : null;
        List<ChunkPos> touched = mode.resendChunks() ? new ArrayList<>() : null;
        for (var e : batch) {
            if (r.ownedChunk(e.cx, e.cz) == null) {   // 로드 안 됨 — 만들지 않는다
                c.skipped += e.count;
                continue;
            }
            int before = c.changed;
            applyChunk(lv, e, minY, mode, c, after);
            if (touched != null && c.changed > before) touched.add(new ChunkPos(e.cx, e.cz));
        }
        if (after != null) {
            // 바닐라 FillCommand: 다 놓은 뒤 놓은 칸마다 level.blockUpdated(pos, 지금 그 칸의 블록)
            for (Pos p : after) lv.blockUpdated(p, B.block(lv.getBlockState(p)));
        }
        if (touched != null && !touched.isEmpty()) {
            // 청크째 다시 보낼 때 빛까지 맞게 — 틱 끝에 할 빛 계산을 지금(재생성 교체와 같다). 그 청크의 칸 갱신은 청크가 대신한다.
            lv.runLightUpdates();
            for (ChunkPos cp : touched) {
                lv.discardChanges(cp.x(), cp.z());
                r.resendChunk(cp);
            }
        }
        if ((mode.flags() & BlockLevel.UPDATE_CLIENTS) != 0) lv.broadcastChanges();   // World.setBlock 처럼 바로(남은 이웃 청크 칸 갱신 포함)
        return c;
    }

    private static void applyChunk(RegionLevel lv, BlockEditBuffer.ChunkEdit e, int minY, Mode mode, Counts c, List<Pos> after) {
        int baseX = e.cx << 4, baseZ = e.cz << 4, flags = mode.flags();
        boolean clear = mode.clear();
        for (int s = 0; s < e.sections.length; s++) {
            char[] sec = e.sections[s];
            if (sec == null) continue;
            int baseY = minY + (s << 4);
            for (int idx = 0; idx < 4096; idx++) {
                int v = sec[idx];
                if (v == 0) continue;
                int state = v - 1;
                Pos p = new Pos(baseX | idx & 15, baseY + (idx >> 8), baseZ | idx >> 4 & 15);
                if (clear && lv.getBlockState(p) != state) org.domi.furnace.server.block.entity.Clearables.tryClear(lv.getBlockEntity(p));
                if (lv.setBlock(p, state, flags)) {
                    c.changed++;
                    if (after != null) after.add(p);
                } else {
                    c.unchanged++;
                }
            }
        }
    }
}
