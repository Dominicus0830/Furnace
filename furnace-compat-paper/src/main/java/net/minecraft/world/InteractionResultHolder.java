package net.minecraft.world;

/** 결과와 값 한 쌍(Mojang 이름 shim, T-212 — {@code Item#use} 가 돌려준다). 바닐라 1.21.1 과 같은 모양이다. */
public class InteractionResultHolder<T> {

    private final InteractionResult result;
    private final T object;

    public InteractionResultHolder(InteractionResult result, T object) {
        this.result = result;
        this.object = object;
    }

    public InteractionResult getResult() { return result; }

    public T getObject() { return object; }

    public static <T> InteractionResultHolder<T> success(T object) { return new InteractionResultHolder<>(InteractionResult.SUCCESS, object); }

    public static <T> InteractionResultHolder<T> consume(T object) { return new InteractionResultHolder<>(InteractionResult.CONSUME, object); }

    public static <T> InteractionResultHolder<T> pass(T object) { return new InteractionResultHolder<>(InteractionResult.PASS, object); }

    public static <T> InteractionResultHolder<T> fail(T object) { return new InteractionResultHolder<>(InteractionResult.FAIL, object); }

    public static <T> InteractionResultHolder<T> sidedSuccess(T object, boolean clientSide) { return clientSide ? success(object) : consume(object); }
}
