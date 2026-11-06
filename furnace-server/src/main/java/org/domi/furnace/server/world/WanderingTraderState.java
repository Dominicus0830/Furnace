// 바닐라 ServerLevelData 의 방랑 상인 필드(WanderingTraderSpawnDelay/Chance/Id)를 월드(오버월드) 하나가 공유하는 저장소.
// 바닐라 스포너는 차원당 하나라 타이머도 하나다. Furnace 는 region 마다 스포너를 두므로 같은 게임 틱에는 한 번만 타이머를
// 돌리도록 claimTick 으로 막고(가장 먼저 부른 region 이 이긴다), 값은 level.dat 에 저장·복원한다.
package org.domi.furnace.server.world;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.domi.furnace.server.net.io.nbt.NbtCompound;

public final class WanderingTraderState {
    private volatile int spawnDelay;
    private volatile int spawnChance;
    private volatile UUID traderId;
    /** 스포너의 1200틱 카운트다운(바닐라 tickDelay). 저장하지 않는다. */
    private volatile int tickDelay = 1200;
    private final AtomicLong lastClaimedTick = new AtomicLong(Long.MIN_VALUE);

    /** 스포너 본체(읽기→감소→쓰기)와 level.dat 저장이 같이 쓰는 자물쇠 — 지연·확률 쌍을 한 번에 읽고 쓴다. */
    public Object lock() { return this; }

    public int spawnDelay() { return spawnDelay; }
    public int spawnChance() { return spawnChance; }
    public UUID traderId() { return traderId; }
    public int tickDelay() { return tickDelay; }
    public void setSpawnDelay(int v) { spawnDelay = v; }
    public void setSpawnChance(int v) { spawnChance = v; }
    public void setTraderId(UUID id) { traderId = id; }
    public void setTickDelay(int v) { tickDelay = v; }

    /** 이 게임 틱에 타이머를 돌릴 권한을 얻는다(같은 틱에 한 번만 true). */
    public boolean claimTick(long gameTime) {
        long prev;
        do {
            prev = lastClaimedTick.get();
            if (gameTime <= prev) return false;
        } while (!lastClaimedTick.compareAndSet(prev, gameTime));
        return true;
    }

    /** WanderingTraderSpawner 생성자: 지연·확률이 모두 0 이면 24000 / 25 로 시작한다. */
    public synchronized void normalize() {
        if (spawnDelay == 0 && spawnChance == 0) {
            spawnDelay = 24000;
            spawnChance = 25;
        }
    }

    /** level.dat Data 에서 읽는다(없으면 0 → normalize 가 기본값). */
    public synchronized void read(NbtCompound data) {
        spawnDelay = data.getIntOr0("WanderingTraderSpawnDelay");
        spawnChance = data.getIntOr0("WanderingTraderSpawnChance");
        int[] id = data.getIntArray("WanderingTraderId");
        traderId = id.length == 4
                ? new UUID((long) id[0] << 32 | id[1] & 0xFFFFFFFFL, (long) id[2] << 32 | id[3] & 0xFFFFFFFFL) : null;
    }

    /** level.dat Data 에 쓴다(상인 번호는 있을 때만 — 바닐라 ServerLevelData). */
    public synchronized void write(NbtCompound data) {
        data.putInt("WanderingTraderSpawnDelay", spawnDelay);
        data.putInt("WanderingTraderSpawnChance", spawnChance);
        UUID id = traderId;
        if (id != null) {
            long m = id.getMostSignificantBits(), l = id.getLeastSignificantBits();
            data.putIntArray("WanderingTraderId", new int[] {(int) (m >> 32), (int) m, (int) (l >> 32), (int) l});
        }
    }
}
