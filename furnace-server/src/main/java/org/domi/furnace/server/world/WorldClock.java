// 전역 틱으로 진행하는 시간·날씨(바닐라 ServerLevel.tickTime·advanceWeatherCycle). 패킷은 sink 로 내보낸다.
package org.domi.furnace.server.world;

import java.util.Random;
import java.util.function.Consumer;
import org.domi.furnace.server.net.protocol.Packet;
import org.domi.furnace.server.net.protocol.play.GameEvent;
import org.domi.furnace.server.net.protocol.play.SetTime;

public final class WorldClock {

    // 바닐라 ServerLevel 의 날씨 구간(UniformInt, 양 끝 포함)
    public static final int[] RAIN_DELAY = {12000, 180000};
    public static final int[] RAIN_DURATION = {12000, 24000};
    public static final int[] THUNDER_DELAY = {12000, 180000};
    public static final int[] THUNDER_DURATION = {3600, 15600};

    private final Random random = new Random();
    private volatile long gameTime;
    private volatile long dayTime;
    private int clearWeatherTime, rainTime, thunderTime;
    private boolean raining, thundering;
    private float rainLevel, oRainLevel, thunderLevel, oThunderLevel;
    private long tickCount;

    public static int sample(Random r, int[] range) { return range[0] + r.nextInt(range[1] - range[0] + 1); }

    public long gameTime() { return gameTime; }

    public long dayTime() { return dayTime; }

    public synchronized void setDayTime(long t) { dayTime = t; }

    public synchronized void restore(long gameTime, long dayTime, int clearWeatherTime, int rainTime, int thunderTime,
                                     boolean raining, boolean thundering) {
        this.gameTime = gameTime;
        this.dayTime = dayTime;
        this.clearWeatherTime = clearWeatherTime;
        this.rainTime = rainTime;
        this.thunderTime = thunderTime;
        this.raining = raining;
        this.thundering = thundering;
        // 바닐라 ServerLevel 생성자: 저장된 상태가 비/뇌우면 레벨을 1 로 시작한다.
        this.rainLevel = this.oRainLevel = raining ? 1f : 0f;
        this.thunderLevel = this.oThunderLevel = thundering ? 1f : 0f;
    }

    public synchronized int clearWeatherTime() { return clearWeatherTime; }

    public synchronized int rainTime() { return rainTime; }

    public synchronized int thunderTime() { return thunderTime; }

    public synchronized boolean rainingFlag() { return raining; }

    public synchronized boolean thunderingFlag() { return thundering; }

    public synchronized float rainLevel() { return rainLevel; }

    public synchronized float thunderLevel() { return thunderLevel; }

    /** Level.isRaining: 비 강도 0.2 초과. */
    public synchronized boolean isRaining() { return rainLevel > 0.2f; }

    /** /weather (바닐라 setWeatherParameters). */
    public synchronized void setWeatherParameters(int clearTime, int weatherTime, boolean raining, boolean thundering) {
        this.clearWeatherTime = clearTime;
        this.rainTime = weatherTime;
        this.thunderTime = weatherTime;
        this.raining = raining;
        this.thundering = thundering;
    }

    public Random random() { return random; }

    /** 한 틱. broadcast 는 모든 플레이어에게 보낼 패킷 수신자. */
    public synchronized void tick(GameRules rules, Consumer<Packet> broadcast) { tick(rules, broadcast, broadcast); }

    /**
     * 차원별: 비·천둥 세기 변화는 하늘빛 있는 차원(오버월드)의 플레이어에게만(바닐라 broadcastAll(packet, dimension)),
     * 비 시작/끝과 그 뒤 세기 두 개는 모든 플레이어에게(바닐라 broadcastAll).
     */
    public synchronized void tick(GameRules rules, Consumer<Packet> broadcast, Consumer<Packet> skyBroadcast) {
        tickWeather(rules, broadcast, skyBroadcast);
        tickTime(rules, broadcast);
    }

    /** ServerLevel.tick 의 날씨 단계(advanceWeatherCycle). 밤 건너뛰기 판정은 이 뒤, 시간 진행 전에 온다. */
    public synchronized void tickWeather(GameRules rules, Consumer<Packet> broadcast, Consumer<Packet> skyBroadcast) {
        advanceWeatherCycle(rules, broadcast, skyBroadcast);
    }

    /** ServerLevel.tickTime + MinecraftServer 의 20틱마다 시간 동기화. */
    public synchronized void tickTime(GameRules rules, Consumer<Packet> broadcast) {
        gameTime++;
        if (rules.getBoolean("doDaylightCycle")) dayTime++;
        if (++tickCount % 20 == 0) broadcast.accept(timePacket(rules));
    }

    /** 밤을 건너뛴 시각: (dayTime + 24000) 에서 24000 의 나머지를 뺀다(다음 날 0). */
    public static long nextMorning(long dayTime) {
        long t = dayTime + 24000L;
        return t - t % 24000L;
    }

    /**
     * ServerLevel.tick 의 밤 건너뛰기(충분히 잤을 때): doDaylightCycle 이면 다음 날 아침으로, doWeatherCycle 이고 비가 오면
     * resetWeatherCycle(비·천둥 시간 0, 비·천둥 끔). 깨우기(wakeUpAllPlayers)는 부르는 쪽이 한다.
     */
    public synchronized void skipNight(GameRules rules, Runnable wakeUpAllPlayers) {
        if (rules.getBoolean("doDaylightCycle")) dayTime = nextMorning(dayTime);
        wakeUpAllPlayers.run();
        if (rules.getBoolean("doWeatherCycle") && isRaining()) resetWeatherCycle();
    }

    /** ServerLevel.resetWeatherCycle. */
    public synchronized void resetWeatherCycle() {
        rainTime = 0;
        raining = false;
        thunderTime = 0;
        thundering = false;
    }

    public synchronized SetTime timePacket(GameRules rules) {
        return SetTime.of(gameTime, dayTime, rules.getBoolean("doDaylightCycle"));
    }

    private void advanceWeatherCycle(GameRules rules, Consumer<Packet> broadcast, Consumer<Packet> skyBroadcast) {
        boolean wasRaining = isRaining();
        if (rules.getBoolean("doWeatherCycle")) {
            int i = clearWeatherTime, j = thunderTime, k = rainTime;
            boolean th = thundering, ra = raining;
            if (i > 0) {
                --i;
                j = th ? 0 : 1;
                k = ra ? 0 : 1;
                th = false;
                ra = false;
            } else {
                if (j > 0) {
                    --j;
                    if (j == 0) th = !th;
                } else if (th) {
                    j = sample(random, THUNDER_DURATION);
                } else {
                    j = sample(random, THUNDER_DELAY);
                }
                if (k > 0) {
                    --k;
                    if (k == 0) ra = !ra;
                } else if (ra) {
                    k = sample(random, RAIN_DURATION);
                } else {
                    k = sample(random, RAIN_DELAY);
                }
            }
            thunderTime = j;
            rainTime = k;
            clearWeatherTime = i;
            thundering = th;
            raining = ra;
        }
        oThunderLevel = thunderLevel;
        thunderLevel = Math.clamp(thunderLevel + (thundering ? 0.01f : -0.01f), 0f, 1f);
        oRainLevel = rainLevel;
        rainLevel = Math.clamp(rainLevel + (raining ? 0.01f : -0.01f), 0f, 1f);
        if (oRainLevel != rainLevel) skyBroadcast.accept(new GameEvent(GameEvent.RAIN_LEVEL_CHANGE, rainLevel));
        if (oThunderLevel != thunderLevel) skyBroadcast.accept(new GameEvent(GameEvent.THUNDER_LEVEL_CHANGE, thunderLevel));
        if (wasRaining != isRaining()) {
            broadcast.accept(new GameEvent(wasRaining ? GameEvent.STOP_RAINING : GameEvent.START_RAINING, 0f));
            broadcast.accept(new GameEvent(GameEvent.RAIN_LEVEL_CHANGE, rainLevel));
            broadcast.accept(new GameEvent(GameEvent.THUNDER_LEVEL_CHANGE, thunderLevel));
        }
    }
}
