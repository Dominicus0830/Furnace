package net.minecraft.util;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;

/**
 * JSON 읽기 도우미(Mojang 이름 shim, T-212). FAWE 의 옛 도식 변환기(책·표지판 글)가 부르는 {@code fromJson}·{@code fromNullableJson} 만 둔다.
 * 동작은 바닐라 1.21.1 과 같다: lenient 를 리더에 걸고 Gson 의 그 타입 어댑터로 한 값을 읽는다. 읽기 IO 오류는 {@link JsonParseException} 으로
 * 감싸고, {@code fromJson} 은 결과가 null(빈 입력·{@code null} 리터럴)이면 "JSON data was null or empty" 로 던진다.
 */
public final class GsonHelper {

    private GsonHelper() {}

    public static <T> T fromNullableJson(Gson gson, Reader reader, Class<T> type, boolean lenient) {
        return read(gson, reader, lenient, gson.getAdapter(type));
    }

    public static <T> T fromNullableJson(Gson gson, Reader reader, TypeToken<T> type, boolean lenient) {
        return read(gson, reader, lenient, gson.getAdapter(type));
    }

    public static <T> T fromNullableJson(Gson gson, String content, Class<T> type, boolean lenient) {
        return fromNullableJson(gson, new StringReader(content), type, lenient);
    }

    public static <T> T fromJson(Gson gson, Reader reader, Class<T> type, boolean lenient) {
        return notNull(fromNullableJson(gson, reader, type, lenient));
    }

    public static <T> T fromJson(Gson gson, Reader reader, TypeToken<T> type, boolean lenient) {
        return notNull(fromNullableJson(gson, reader, type, lenient));
    }

    public static <T> T fromJson(Gson gson, String content, Class<T> type, boolean lenient) {
        return fromJson(gson, new StringReader(content), type, lenient);
    }

    public static <T> T fromJson(Gson gson, Reader reader, Class<T> type) { return fromJson(gson, reader, type, false); }

    public static <T> T fromJson(Gson gson, Reader reader, TypeToken<T> type) { return fromJson(gson, reader, type, false); }

    public static <T> T fromJson(Gson gson, String content, Class<T> type) { return fromJson(gson, content, type, false); }

    @SuppressWarnings("deprecation")   // 바닐라 1.21.1 은 setLenient(boolean) 를 부른다
    private static <T> T read(Gson gson, Reader reader, boolean lenient, com.google.gson.TypeAdapter<T> adapter) {
        try {
            JsonReader r = new JsonReader(reader);
            r.setLenient(lenient);
            return adapter.read(r);
        } catch (IOException e) {
            throw new JsonParseException(e);
        }
    }

    private static <T> T notNull(T v) {
        if (v == null) throw new JsonParseException("JSON data was null or empty");
        return v;
    }
}
