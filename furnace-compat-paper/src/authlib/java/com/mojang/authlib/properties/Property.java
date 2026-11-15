package com.mojang.authlib.properties;

/** authlib Property 의 컴파일·시험용 대역(배포 jar 에 넣지 않는다) — 진짜 authlib 6 처럼 레코드(name, value, signature). */
public record Property(String name, String value, String signature) {

    public Property(String name, String value) { this(name, value, null); }

    public boolean hasSignature() { return signature != null; }
}
