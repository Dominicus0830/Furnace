package net.minecraft.network.codec;

/**
 * 스트림 코덱 shim(Mojang 이름, N3 1단계). 이 단계에서는 인터페이스와 {@link #of} 만 있다 —
 * 합성 도우미(composite·map 등)는 플러그인이 쓰는 것이 확인되는 대로 더한다.
 */
public interface StreamCodec<B, V> extends StreamDecoder<B, V>, StreamEncoder<B, V> {

    static <B, V> StreamCodec<B, V> of(StreamEncoder<B, V> encoder, StreamDecoder<B, V> decoder) {
        return new StreamCodec<>() {
            @Override public V decode(B input) { return decoder.decode(input); }
            @Override public void encode(B output, V value) { encoder.encode(output, value); }
        };
    }

    static <B, V> StreamCodec<B, V> unit(V instance) {
        return new StreamCodec<>() {
            @Override public V decode(B input) { return instance; }
            @Override public void encode(B output, V value) {
                if (!instance.equals(value)) throw new IllegalStateException("Can't encode '" + value + "', expected '" + instance + "'");
            }
        };
    }
}
