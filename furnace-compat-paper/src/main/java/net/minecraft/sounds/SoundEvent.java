package net.minecraft.sounds;

import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/**
 * 소리(Mojang 이름 shim, easy-nms E4c-1b). 바닐라 1.21.1 과 같다: 위치 + 범위, 범위가 고정이 아니면(createVariableRangeEvent) 들리는 거리는
 * 음량 1 이하 16, 그보다 크면 16 × 음량. equals 는 바닐라처럼 객체 비교다 — 등록된 소리는 {@code SoundEvents}·레지스트리의 같은 객체를 쓴다.
 * 코덱 필드(CODEC·STREAM_CODEC 등)는 DataFixerUpper·네트워크 코덱 shim 이 없어 두지 않았다.
 */
public class SoundEvent {

    private static final float DEFAULT_RANGE = 16.0F;

    private final ResourceLocation location;
    private final float range;
    private final boolean newSystem;

    private SoundEvent(ResourceLocation location, float range, boolean newSystem) {
        this.location = location;
        this.range = range;
        this.newSystem = newSystem;
    }

    public static SoundEvent createVariableRangeEvent(ResourceLocation location) { return new SoundEvent(location, DEFAULT_RANGE, false); }

    public static SoundEvent createFixedRangeEvent(ResourceLocation location, float range) { return new SoundEvent(location, range, true); }

    public ResourceLocation getLocation() { return location; }

    public float getRange(float volume) {
        if (newSystem) return range;
        return volume > 1.0F ? DEFAULT_RANGE * volume : DEFAULT_RANGE;
    }

    /** 바닐라처럼 private — 패킷 다리(SchemaPacketBridge)가 직접 정의 소리의 와이어(고정 범위 Optional)를 만들 때 반사로 읽는다. */
    private Optional<Float> fixedRange() { return newSystem ? Optional.of(range) : Optional.empty(); }
}
