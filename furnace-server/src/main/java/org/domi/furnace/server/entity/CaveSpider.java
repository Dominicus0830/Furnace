// 바닐라 CaveSpider: 작은 독거미(체력 12). 맞추면 난이도에 따라 독(보통 7초, 어려움 15초). 스폰 때 기수·효과 없음.
package org.domi.furnace.server.entity;

import org.domi.furnace.server.attribute.Attribute;
import org.domi.furnace.server.attribute.AttributeMap;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.effect.MobEffect;
import org.domi.furnace.server.effect.MobEffectInstance;

public class CaveSpider extends Spider {

    public CaveSpider(ChunkLevel level, int id) { super(EntityType.CAVE_SPIDER, level, id); }

    @Override
    protected void createAttributes(AttributeMap map) {
        super.createAttributes(map);
        map.add(Attribute.MAX_HEALTH, 12.0);
    }

    @Override
    public boolean doHurtTarget(Targetable t) {
        if (!super.doHurtTarget(t)) return false;
        int seconds = level.difficulty() == 2 ? 7 : level.difficulty() == 3 ? 15 : 0;
        if (seconds > 0) {
            if (t instanceof LivingEntity le) le.addEffect(new MobEffectInstance(MobEffect.POISON, seconds * 20, 0));
            else if (t instanceof Player p) p.vitals().addEffect(new MobEffectInstance(MobEffect.POISON, seconds * 20, 0));
        }
        return true;
    }

    @Override
    public void finalizeSpawn(org.domi.furnace.server.block.RandomSource r, DifficultyInstance difficulty) {
        // 바닐라 CaveSpider.finalizeSpawn 은 super 를 부르지 않는다(속성 무작위화·기수·효과 모두 없음)
    }

    @Override
    protected double riderAttachY() { return 0.21875; }
}
