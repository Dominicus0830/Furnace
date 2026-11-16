package net.minecraft.core;

/**
 * 등록 정보(Mojang 이름 shim, N2b-6). 바닐라는 알려진 팩·수명 주기(DataFixerUpper Lifecycle)를 담지만 shim 은 표지뿐이다 —
 * {@link WritableRegistry#register} 가 받는 자리를 맞추려고 둔다(Furnace 레지스트리는 얼어 있어 등록은 늘 거절된다).
 */
public final class RegistrationInfo {

    public static final RegistrationInfo BUILT_IN = new RegistrationInfo();

    private RegistrationInfo() {}

    @Override
    public String toString() { return "RegistrationInfo[BUILT_IN]"; }
}
