package net.minecraft.util;

/** 문자열 도우미(Mojang 이름 shim, T-212). FAWE 옛 도식 변환기가 부르는 것만. */
public final class StringUtil {

    private StringUtil() {}

    /** 바닐라: null 이거나 길이 0(공백만 있는 문자열은 비지 않았다). */
    public static boolean isNullOrEmpty(String s) { return s == null || s.isEmpty(); }
}
