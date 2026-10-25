// 바닐라 SensorType: 센서를 만드는 공장.
package org.domi.furnace.server.entity.ai.brain;

import java.util.function.Supplier;
import org.domi.furnace.server.entity.Mob;
import org.domi.furnace.server.entity.ai.sensing.*;

public final class SensorType<U extends Sensor<?>> {
    private final String name;
    private final Supplier<U> factory;

    private SensorType(String name, Supplier<U> factory) {
        this.name = name;
        this.factory = factory;
    }

    public U create() { return factory.get(); }

    public String name() { return name; }

    private static <U extends Sensor<?>> SensorType<U> of(String name, Supplier<U> f) { return new SensorType<>(name, f); }

    public static final SensorType<DummySensor> DUMMY = of("dummy", DummySensor::new);
    public static final SensorType<NearestItemSensor> NEAREST_ITEMS = of("nearest_items", NearestItemSensor::new);
    public static final SensorType<NearestLivingEntitySensor<Mob>> NEAREST_LIVING_ENTITIES = of("nearest_living_entities", NearestLivingEntitySensor<Mob>::new);
    public static final SensorType<PlayerSensor> NEAREST_PLAYERS = of("nearest_players", PlayerSensor::new);
    public static final SensorType<HurtBySensor> HURT_BY = of("hurt_by", HurtBySensor::new);
    public static final SensorType<NearestAdultSensor> NEAREST_ADULT = of("nearest_adult", NearestAdultSensor::new);
    public static final SensorType<IsInWaterSensor> IS_IN_WATER = of("is_in_water", IsInWaterSensor::new);

    public static final SensorType<TemptingSensor> FROG_TEMPTATIONS = temptations("frog_temptations", s -> org.domi.furnace.server.item.ItemTags.is(s, "minecraft:frog_food"));
    public static final SensorType<FrogAttackablesSensor> FROG_ATTACKABLES = of("frog_attackables", FrogAttackablesSensor::new);
    public static final SensorType<TemptingSensor> GOAT_TEMPTATIONS = temptations("goat_temptations", s -> org.domi.furnace.server.item.ItemTags.is(s, "minecraft:goat_food"));
    public static final SensorType<TemptingSensor> CAMEL_TEMPTATIONS = temptations("camel_temptations", s -> org.domi.furnace.server.item.ItemTags.is(s, "minecraft:camel_food"));
    public static final SensorType<TemptingSensor> ARMADILLO_TEMPTATIONS = temptations("armadillo_temptations", s -> org.domi.furnace.server.item.ItemTags.is(s, "minecraft:armadillo_food"));
    public static final SensorType<org.domi.furnace.server.entity.ai.sensing.ArmadilloScareDetectedSensor> ARMADILLO_SCARE_DETECTED = of("armadillo_scare_detected", org.domi.furnace.server.entity.ai.sensing.ArmadilloScareDetectedSensor::new);
    public static final SensorType<TemptingSensor> SNIFFER_TEMPTATIONS = temptations("sniffer_temptations", s -> org.domi.furnace.server.item.ItemTags.is(s, "minecraft:sniffer_food"));
    public static final SensorType<org.domi.furnace.server.entity.ai.sensing.HoglinSpecificSensor> HOGLIN_SPECIFIC = of("hoglin_specific_sensor", org.domi.furnace.server.entity.ai.sensing.HoglinSpecificSensor::new);
    public static final SensorType<org.domi.furnace.server.entity.ai.sensing.PiglinSpecificSensor> PIGLIN_SPECIFIC = of("piglin_specific_sensor", org.domi.furnace.server.entity.ai.sensing.PiglinSpecificSensor::new);
    public static final SensorType<org.domi.furnace.server.entity.ai.sensing.PiglinBruteSpecificSensor> PIGLIN_BRUTE_SPECIFIC = of("piglin_brute_specific_sensor", org.domi.furnace.server.entity.ai.sensing.PiglinBruteSpecificSensor::new);
    public static final SensorType<TemptingSensor> AXOLOTL_TEMPTATIONS = temptations("axolotl_temptations", s -> org.domi.furnace.server.item.ItemTags.is(s, "minecraft:axolotl_food"));
    public static final SensorType<org.domi.furnace.server.entity.ai.sensing.AxolotlAttackablesSensor> AXOLOTL_ATTACKABLES = of("axolotl_attackables", org.domi.furnace.server.entity.ai.sensing.AxolotlAttackablesSensor::new);

    /** 아이템 온도 센서(이름만 다른 여러 마리 공용): 몹마다 달라 팩토리가 외부에서 주어진다. */
    public static SensorType<TemptingSensor> temptations(String name, java.util.function.Predicate<org.domi.furnace.server.entity.ItemStack> temptItems) {
        return of(name, () -> new TemptingSensor(temptItems));
    }
}
