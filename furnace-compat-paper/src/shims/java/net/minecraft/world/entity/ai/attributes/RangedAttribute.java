package net.minecraft.world.entity.ai.attributes;

/** 범위가 있는 속성(Mojang 이름 shim, NMS shim 3번 묶음). 바닐라 1.21.1 의 모든 속성이 이 종류다. */
class RangedAttribute {

    private final double minValue;
    public double maxValue;

    public RangedAttribute(String descriptionId, double defaultValue, double minValue, double maxValue) {
        super(descriptionId, defaultValue);
        this.minValue = minValue;
        this.maxValue = maxValue;
        if (minValue > maxValue) throw new IllegalArgumentException("Minimum value cannot be bigger than maximum value!");
        if (defaultValue < minValue) throw new IllegalArgumentException("Default value cannot be lower than minimum value!");
        if (defaultValue > maxValue) throw new IllegalArgumentException("Default value cannot be bigger than maximum value!");
    }

    public double getMinValue() { return minValue; }

    public double getMaxValue() { return maxValue; }

    /** 바닐라: NaN 이면 최솟값, 아니면 범위로 자른다. */
    @Override
    public double sanitizeValue(double value) { return Double.isNaN(value) ? minValue : Math.clamp(value, minValue, maxValue); }
}
