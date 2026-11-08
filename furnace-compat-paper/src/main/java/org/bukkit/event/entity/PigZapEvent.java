package org.bukkit.event.entity;

import org.bukkit.entity.LightningStrike;
import org.bukkit.entity.Pig;
import org.bukkit.entity.PigZombie;
import org.bukkit.event.Cancellable;

/**
 * 번개 맞은 돼지가 좀비화 피글린으로 바뀌기 직전. 처리기 목록은 EntityZapEvent 의 것이다(Paper 와 같다).
 * Furnace 에는 맞는 이벤트가 아직 없어 불리지 않는다 — 취소로 돼지를 지킬 수 없다(문서의 다른 점).
 */
public class PigZapEvent extends com.destroystokyo.paper.event.entity.EntityZapEvent implements Cancellable {

    private boolean canceled;
    private final PigZombie pigzombie;
    private final LightningStrike bolt;

    public PigZapEvent(Pig pig, LightningStrike bolt, PigZombie pigzombie) {
        super(pig, bolt, pigzombie);
        this.bolt = bolt;
        this.pigzombie = pigzombie;
    }

    @Override
    public boolean isCancelled() { return canceled; }

    @Override
    public void setCancelled(boolean cancel) { this.canceled = cancel; }

    @Override
    public Pig getEntity() { return (Pig) entity; }

    public LightningStrike getLightning() { return bolt; }

    /** @deprecated getTransformedEntity 를 쓴다. */
    @Deprecated
    public PigZombie getPigZombie() { return pigzombie; }
}
