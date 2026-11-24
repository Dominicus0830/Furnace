package net.minecraft.core.particles;

/**
 * 진동 입자(Mojang 이름 shim). 목적지 위치 원천(PositionSource) shim 이 아직 없어 생성자·목적지는 껍데기 합치기가 더한 껍데기다(부르면 실패) —
 * 그래서 이 값은 만들어지지 않고, Furnace 진동 입자를 shim 으로 바꾸는 쪽도 UnsupportedNmsCall 이다.
 */
public class VibrationParticleOption implements ParticleOptions {

    private int arrivalInTicks;

    private VibrationParticleOption() {}

    @Override
    public ParticleType<VibrationParticleOption> getType() { return ParticleTypes.VIBRATION; }

    public int getArrivalInTicks() { return arrivalInTicks; }
}
