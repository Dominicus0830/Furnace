// 바닐라 CombatTracker — 최근 피해 기록으로 사망 메시지를 만든다(낙하 변형 death.fell.accident.* 포함).
// 차이: 전투 진입/종료(player_combat_enter/end)는 아직 없다. 손에 든 이름 붙은 아이템(.item 변형)은 아직 보지 않는다.
package org.domi.furnace.server.entity.damage;

import java.util.ArrayList;
import java.util.List;
import org.domi.furnace.api.text.Text;

public final class CombatTracker {

    public static final int RESET_DAMAGE_STATUS_TIME = 100;

    /** @param fallLocation 바닐라 FallLocation 이름(generic·ladder·vines·…·water), 없으면 null */
    /** attacker: 원인 엔티티(getEntity) 이름, direct: 직접 엔티티(getDirectEntity) 이름. 없으면 null. */
    public record Entry(DamageType source, float damage, String fallLocation, float fallDistance, Text attacker, Text direct) {
        public Entry(DamageType source, float damage, String fallLocation, float fallDistance, Text attacker) {
            this(source, damage, fallLocation, fallDistance, attacker, attacker);
        }
    }

    private final List<Entry> entries = new ArrayList<>();
    private int lastDamageTime;
    private boolean takingDamage;

    /** @param tickCount 주인의 tickCount, fallLocation 맞은 순간의 FallLocation(없으면 null) */
    public void recordDamage(DamageType source, float damage, String fallLocation, float fallDistance, int tickCount, boolean alive) {
        recordDamage(source, damage, fallLocation, fallDistance, tickCount, alive, null);
    }

    /** attacker: 원인 엔티티의 표시 이름(없으면 null). */
    public void recordDamage(DamageType source, float damage, String fallLocation, float fallDistance, int tickCount, boolean alive, Text attacker) {
        recordDamage(source, damage, fallLocation, fallDistance, tickCount, alive, attacker, attacker);
    }

    /** attacker: 원인 엔티티 이름, direct: 직접 엔티티 이름(없으면 null). */
    public void recordDamage(DamageType source, float damage, String fallLocation, float fallDistance, int tickCount, boolean alive, Text attacker, Text direct) {
        recheckStatus(tickCount, alive);
        entries.add(new Entry(source, damage, fallLocation, fallDistance, attacker, direct));
        lastDamageTime = tickCount;
        takingDamage = true;
    }

    public Text getDeathMessage(Text victim) { return getDeathMessage(victim, null); }

    /** killCredit: 주인의 getKillCredit 이름(없으면 null) — 원인 엔티티 없는 피해의 ".player" 변형에 쓴다. */
    public Text getDeathMessage(Text victim, Text killCredit) {
        if (entries.isEmpty()) return Text.translatable("death.attack.generic", victim);
        Entry last = entries.get(entries.size() - 1);
        Entry fall = mostSignificantFall();
        if (last.source().fallVariants() && fall != null) return fallMessage(fall, last.attacker(), victim);
        if (last.source().intentionalGameDesign()) {   // [의도된 게임 디자인] 링크(MCPE-28723)
            Text link = Text.translatable("chat.square_brackets", Text.translatable("death.attack.badRespawnPoint.link"))
                    .style(st -> st.clickEvent(org.domi.furnace.api.text.ClickEvent.openUrl("https://bugs.mojang.com/browse/MCPE-28723"))
                            .hoverEvent(org.domi.furnace.api.text.HoverEvent.showText(Text.text("MCPE-28723"))));
            return Text.translatable("death.attack." + last.source().msgId() + ".message", victim, link);
        }
        return localizedDeathMessage(last, victim, killCredit);
    }

    /**
     * 바닐라 DamageSource.getLocalizedDeathMessage: 원인·직접 엔티티가 모두 없으면 killCredit 이 있을 때 "death.attack.<msgId>.player",
     * 아니면 "death.attack.<msgId>". 있으면 원인(없으면 직접) 엔티티 이름을 붙인다. TODO(손에 든 아이템 이름): .item 변형
     */
    public static Text localizedDeathMessage(Entry e, Text victim, Text killCredit) {
        String key = "death.attack." + e.source().msgId();
        if (e.attacker() == null && e.direct() == null) {
            return killCredit != null ? Text.translatable(key + ".player", victim, killCredit) : Text.translatable(key, victim);
        }
        return Text.translatable(key, victim, e.attacker() != null ? e.attacker() : e.direct());
    }

    /** 바닐라 CombatTracker.getFallMessage: killer = 마지막 피해의 원인 엔티티. */
    private Text fallMessage(Entry fall, Text killer, Text victim) {
        DamageType s = fall.source();
        if (s.isFall() || s.alwaysMostSignificantFall()) {
            String loc = fall.fallLocation() != null ? fall.fallLocation() : "generic";
            return Text.translatable("death.fell.accident." + loc, victim);
        }
        Text fallKiller = fall.attacker();
        if (fallKiller != null && !fallKiller.equals(killer)) return Text.translatable("death.fell.assist", victim, fallKiller);
        if (killer != null) return Text.translatable("death.fell.finish", victim, killer);
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
