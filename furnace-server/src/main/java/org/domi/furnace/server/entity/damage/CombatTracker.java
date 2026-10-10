// 바닐라 CombatTracker — 최근 피해 기록으로 사망 메시지를 만든다(낙하 변형 death.fell.accident.* 포함).
// 공격자 엔티티가 아직 없으므로 전투 진입/종료(player_combat_enter/end)와 death.fell.assist/finish/killer 갈래는 쓰이지 않는다.
package org.domi.furnace.server.entity.damage;

import java.util.ArrayList;
import java.util.List;
import org.domi.furnace.server.net.protocol.Text;

public final class CombatTracker {

    public static final int RESET_DAMAGE_STATUS_TIME = 100;

    /** @param fallLocation 바닐라 FallLocation 이름(generic·ladder·vines·…·water), 없으면 null */
    public record Entry(DamageType source, float damage, String fallLocation, float fallDistance) {}

    private final List<Entry> entries = new ArrayList<>();
    private int lastDamageTime;
    private boolean takingDamage;

    /** @param tickCount 주인의 tickCount, fallLocation 맞은 순간의 FallLocation(없으면 null) */
    public void recordDamage(DamageType source, float damage, String fallLocation, float fallDistance, int tickCount, boolean alive) {
        recheckStatus(tickCount, alive);
        entries.add(new Entry(source, damage, fallLocation, fallDistance));
        lastDamageTime = tickCount;
        takingDamage = true;
    }

    public Text getDeathMessage(Text victim) {
        if (entries.isEmpty()) return Text.translatable("death.attack.generic", victim);
        Entry last = entries.get(entries.size() - 1);
        Entry fall = mostSignificantFall();
        if (last.source().fallVariants() && fall != null) return fallMessage(fall, victim);
        // 바닐라 DamageSource.getLocalizedDeathMessage: 원인 엔티티 없음 → killCredit 이 없으면 "death.attack.<msgId>"
        return Text.translatable("death.attack." + last.source().msgId(), victim);
    }

    private Text fallMessage(Entry fall, Text victim) {
        DamageType s = fall.source();
        if (s.isFall() || s.alwaysMostSignificantFall()) {
            String loc = fall.fallLocation() != null ? fall.fallLocation() : "generic";
            return Text.translatable("death.fell.accident." + loc, victim);
        }
        return Text.translatable("death.fell.killer", victim);
    }

    private Entry mostSignificantFall() {
        Entry bestFall = null, bestLocated = null;
        float bestFallDistance = 0, bestLocatedDistance = 0;
        for (int i = 0; i < entries.size(); i++) {
            Entry e = entries.get(i);
            Entry prev = i > 0 ? entries.get(i - 1) : null;
            boolean always = e.source().alwaysMostSignificantFall();
            float d = always ? Float.MAX_VALUE : e.fallDistance();
            if ((e.source().isFall() || always) && d > 0 && (bestFall == null || d > bestFallDistance)) {
                bestFall = i > 0 ? prev : e;
                bestFallDistance = d;
            }
            if (e.fallLocation() != null && (bestLocated == null || e.damage() > bestLocatedDistance)) {
                bestLocated = e;
                bestLocatedDistance = e.damage();
            }
        }
        if (bestFallDistance > 5.0f && bestFall != null) return bestFall;
        return bestLocatedDistance > 5.0f && bestLocated != null ? bestLocated : null;
    }

    /** 바닐라 recheckStatus: 마지막 피해 후 100틱(전투 중이면 300) 지났거나 죽었으면 기록을 비운다. */
    public void recheckStatus(int tickCount, boolean alive) {
        if (takingDamage && (!alive || tickCount - lastDamageTime > RESET_DAMAGE_STATUS_TIME)) {
            takingDamage = false;
            entries.clear();
        }
    }
}
