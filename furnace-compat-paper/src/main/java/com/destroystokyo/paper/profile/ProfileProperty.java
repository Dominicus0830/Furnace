package com.destroystokyo.paper.profile;

import java.util.Objects;

/** 프로필 속성 하나(이름·값·서명). Paper 와 같은 길이 한도·같음 규칙. */
public final class ProfileProperty {

    private final String name;
    private final String value;
    private final String signature;

    public ProfileProperty(String name, String value) { this(name, value, null); }

    public ProfileProperty(String name, String value, String signature) {
        this.name = Objects.requireNonNull(name, "ProfileProperty name can not be null");
        this.value = Objects.requireNonNull(value, "ProfileProperty value can not be null");
        this.signature = signature;
        if (name.length() > 64) throw new IllegalArgumentException("ProfileProperty name can not be longer than 64 characters");
        if (value.length() > Short.MAX_VALUE) throw new IllegalArgumentException("ProfileProperty value can not be longer than 32767 characters");
        if (signature != null && signature.length() > 1024) throw new IllegalArgumentException("ProfileProperty signature can not be longer than 1024 characters");
    }

    public String getName() { return name; }

    public String getValue() { return value; }

    public String getSignature() { return signature; }

    public boolean isSigned() { return signature != null; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProfileProperty that = (ProfileProperty) o;
        return Objects.equals(name, that.name) && Objects.equals(value, that.value) && Objects.equals(signature, that.signature);
    }

    @Override
    public int hashCode() { return Objects.hash(name); }
}
