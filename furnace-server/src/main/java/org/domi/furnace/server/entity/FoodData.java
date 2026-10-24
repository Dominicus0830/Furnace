// 바닐라 FoodData — 허기·포화·소모(exhaustion)·자연 회복/굶주림 타이머. NBT 키도 바닐라 그대로.
package org.domi.furnace.server.entity;

import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.world.Difficulty;

public final class FoodData {

    /** tick 이 주인(플레이어)에게 묻고 시키는 것. */
    public interface Owner {
        Difficulty difficulty();
        boolean naturalRegeneration();
        /** 바닐라 Player.isHurt: 체력 > 0 이고 최대치 미만. */
        boolean isHurt();
        float health();
        void heal(float amount);
        void starve();
    }

    private int foodLevel = 20;
    private float saturationLevel = 5.0f;
    private float exhaustionLevel;
    private int tickTimer;
    private int lastFoodLevel = 20;

    public void add(int food, float saturation) {
        foodLevel = Math.clamp(food + foodLevel, 0, 20);
        saturationLevel = Math.clamp(saturation + saturationLevel, 0.0f, (float) foodLevel);
    }

    /** 바닐라 eat(nutrition, saturationModifier): 포화 증가량 = nutrition * modifier * 2. */
    public void eat(int nutrition, float saturationModifier) {
        add(nutrition, nutrition * saturationModifier * 2.0f);
    }

    public void tick(Owner p) {
        Difficulty difficulty = p.difficulty();
        lastFoodLevel = foodLevel;
        if (exhaustionLevel > 4.0f) {
            exhaustionLevel -= 4.0f;
            if (saturationLevel > 0.0f) {
                saturationLevel = Math.max(saturationLevel - 1.0f, 0.0f);
            } else if (difficulty != Difficulty.PEACEFUL) {
                foodLevel = Math.max(foodLevel - 1, 0);
            }
        }
        boolean regen = p.naturalRegeneration();
        if (regen && saturationLevel > 0.0f && p.isHurt() && foodLevel >= 20) {
            if (++tickTimer >= 10) {
                float amount = Math.min(saturationLevel, 6.0f);
                p.heal(amount / 6.0f);
                addExhaustion(amount);
                tickTimer = 0;
            }
        } else if (regen && foodLevel >= 18 && p.isHurt()) {
            if (++tickTimer >= 80) {
                p.heal(1.0f);
                addExhaustion(6.0f);
                tickTimer = 0;
            }
        } else if (foodLevel <= 0) {
            if (++tickTimer >= 80) {
                if (p.health() > 10.0f || difficulty == Difficulty.HARD || p.health() > 1.0f && difficulty == Difficulty.NORMAL) {
                    p.starve();
                }
                tickTimer = 0;
            }
        } else {
            tickTimer = 0;
        }
    }

    public void load(NbtCompound tag) {
        if (tag.containsNumber("foodLevel")) {
            foodLevel = tag.getIntOr0("foodLevel");
            tickTimer = tag.getIntOr0("foodTickTimer");
            saturationLevel = tag.getFloat("foodSaturationLevel");
            exhaustionLevel = tag.getFloat("foodExhaustionLevel");
        }
    }

    public void save(NbtCompound tag) {
        tag.putInt("foodLevel", foodLevel);
        tag.putInt("foodTickTimer", tickTimer);
        tag.putFloat("foodSaturationLevel", saturationLevel);
        tag.putFloat("foodExhaustionLevel", exhaustionLevel);
    }

    public int foodLevel() { return foodLevel; }
    public int lastFoodLevel() { return lastFoodLevel; }
    public boolean needsFood() { return foodLevel < 20; }

    /** 바닐라 addExhaustion: 최대 40. */
    public void addExhaustion(float amount) { exhaustionLevel = Math.min(exhaustionLevel + amount, 40.0f); }

    public float exhaustionLevel() { return exhaustionLevel; }
    public float saturationLevel() { return saturationLevel; }
    public void foodLevel(int v) { foodLevel = v; }
    public void saturationLevel(float v) { saturationLevel = v; }
    public void exhaustionLevel(float v) { exhaustionLevel = v; }
}
