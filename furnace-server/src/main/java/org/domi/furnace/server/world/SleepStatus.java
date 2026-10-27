// 바닐라 SleepStatus: 차원 하나의 (관전자 아닌) 접속자 수와 자는 사람 수. 밤 건너뛰기 판정과 "N/M 명 자는 중" 메시지에 쓴다.
// region 스레드(잠들기·깨기)와 전역 스레드(밤 건너뛰기)가 함께 보므로 동기화한다.
package org.domi.furnace.server.world;

import java.util.Collection;
import org.domi.furnace.server.entity.Player;

public final class SleepStatus {

    private int activePlayers;
    private int sleepingPlayers;

    /** areEnoughSleeping(percentage): 자는 사람 수가 필요 수 이상. */
    public synchronized boolean areEnoughSleeping(int percentage) { return sleepingPlayers >= sleepersNeeded(percentage); }

    /** areEnoughDeepSleeping(percentage, players): 100틱 넘게 잔(isSleepingLongEnough) 사람 수가 필요 수 이상. */
    public synchronized boolean areEnoughDeepSleeping(int percentage, Collection<Player> players) {
        int n = 0;
        for (Player p : players) if (p.isSleepingLongEnough()) n++;
        return n >= sleepersNeeded(percentage);
    }

    /** sleepersNeeded(percentage) = max(1, ceil(active × percentage / 100)). */
    public synchronized int sleepersNeeded(int percentage) { return neededFor(activePlayers, percentage); }

    /** sleepersNeeded 의 계산(float 나눗셈 그대로). */
    public static int neededFor(int active, int percentage) { return Math.max(1, ceil(active * percentage / 100.0F)); }

    /** Mth.ceil(float). */
    private static int ceil(float f) {
        int i = (int) f;
        return f > i ? i + 1 : i;
    }

    /** removeAllSleepers: 밤을 건너뛴 뒤. */
    public synchronized void removeAllSleepers() { sleepingPlayers = 0; }

    public synchronized int amountSleeping() { return sleepingPlayers; }

    public synchronized int activePlayers() { return activePlayers; }

    /** update(players): 다시 센다. 자는 사람이 있었거나 생겼고 수가 바뀌었으면 true(메시지를 알린다). */
    public synchronized boolean update(Collection<Player> players) {
        int oldActive = activePlayers, oldSleeping = sleepingPlayers;
        activePlayers = 0;
        sleepingPlayers = 0;
        for (Player p : players) {
            if (!p.isSpectator()) {
                activePlayers++;
                if (p.isSleeping()) sleepingPlayers++;
            }
        }
        return (oldSleeping > 0 || sleepingPlayers > 0) && (oldActive != activePlayers || oldSleeping != sleepingPlayers);
    }
}
