// 레드스톤 횃불 과열 기록(바닐라 RedstoneTorchBlock.RECENT_TOGGLES 의 한 레벨 몫).
// 바닐라는 레벨(차원) 하나에 목록 하나다. Furnace 는 region 레벨마다 하나를 두고 그 region 스레드만 쓴다 — region 이 합쳐지고 나뉠 때
// 칸 위치를 따라 옮기므로, 차원 전체로 보면 바닐라 목록을 청크별로 나눠 든 것과 같다.
package org.domi.furnace.server.block;

import java.util.ArrayList;
import java.util.List;
import java.util.function.LongPredicate;

public final class RedstoneTorchToggles {

    /** 꺼진 칸과 그때의 게임 시각(바닐라 RedstoneTorchBlock.Toggle). */
    private record Toggle(long pos, long when) {}

    /** 꺼진 순서(게임 시각이 줄지 않는 순서). 바닐라처럼 앞에서부터 오래된 것을 지운다. */
    private final List<Toggle> list = new ArrayList<>();

    /** 바닐라 tick 앞부분: 맨 앞이 timer 틱보다 오래됐으면 지운다(목록이 시각 순이라 오래된 것은 모두 빠진다). */
    public void expire(long now, int timer) {
        int n = 0;
        while (n < list.size() && now - list.get(n).when > timer) n++;
        if (n > 0) list.subList(0, n).clear();
    }

    public void add(Pos pos, long when) { list.add(new Toggle(pos.asLong(), when)); }

    /** 그 칸의 기록 수. */
    public int count(Pos pos) {
        long p = pos.asLong();
        int n = 0;
        for (Toggle t : list) if (t.pos == p) n++;
        return n;
    }

    public int size() { return list.size(); }

    /**
     * region 합치기: other 의 기록을 모두 가져온다(other 는 버려진다). 두 목록을 시각 순으로 섞어, 앞에서부터 지우는 바닐라 규칙이
     * 합친 뒤에도 "오래된 것 모두"를 지우게 한다. 같은 시각이면 이쪽 것이 앞(시각만 보는 규칙이라 결과는 같다).
     */
    public void absorb(RedstoneTorchToggles other) {
        if (other.list.isEmpty()) return;
        if (list.isEmpty() || list.get(list.size() - 1).when <= other.list.get(0).when) {
            list.addAll(other.list);
        } else {
            List<Toggle> merged = new ArrayList<>(list.size() + other.list.size());
            int i = 0, j = 0;
            while (i < list.size() && j < other.list.size()) {
                merged.add(other.list.get(j).when < list.get(i).when ? other.list.get(j++) : list.get(i++));
            }
            while (i < list.size()) merged.add(list.get(i++));
            while (j < other.list.size()) merged.add(other.list.get(j++));
            list.clear();
            list.addAll(merged);
        }
        other.list.clear();
    }

    /** region 나누기: 칸 조건에 맞는 기록을 target 으로 옮긴다(시각 순서 유지). target 은 새 레벨이라 비어 있다. */
    public void splitTo(RedstoneTorchToggles target, LongPredicate byPos) {
        if (list.isEmpty()) return;
        List<Toggle> keep = new ArrayList<>(list.size());
        List<Toggle> moved = new ArrayList<>();
        for (Toggle t : list) (byPos.test(t.pos) ? moved : keep).add(t);
        if (moved.isEmpty()) return;
        list.clear();
        list.addAll(keep);
        RedstoneTorchToggles tmp = new RedstoneTorchToggles();
        tmp.list.addAll(moved);
        target.absorb(tmp);
    }
}
