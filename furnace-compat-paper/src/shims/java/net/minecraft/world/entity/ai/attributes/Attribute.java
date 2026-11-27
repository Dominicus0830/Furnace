package net.minecraft.world.entity.ai.attributes;

/**
 * 엔티티 속성(Mojang 이름 shim, NMS shim 3번 묶음). 바닐라 1.21.1 과 같은 값(설명 키·기본값·클라이언트 동기 여부)이고, 값은
 * {@link Attributes} 의 등록된 손잡이로 얻는다. 코덱·감정(sentiment)·글자 색은 두지 않았다.
 */
class Attribute {

    private final double defaultValue;
    private boolean syncable;
    private final String descriptionId;

    protected Attribute(String descriptionId, double defaultValue) {
        this.defaultValue = defaultValue;
        this.descriptionId = descriptionId;
    }

    public double getDefaultValue() { return defaultValue; }

    public boolean isClientSyncable() { return syncable; }

    public Attribute setSyncable(boolean syncable) {
        this.syncable = syncable;
        return this;
    }

    /** 바닐라: 범위가 없는 속성은 그대로. */
    public double sanitizeValue(double value) { return value; }

    public String getDescriptionId() { return descriptionId; }
}
