package org.bukkit.metadata;

/** 메타데이터 값을 원하는 형으로 바꿀 수 없다. */
public class MetadataConversionException extends RuntimeException {

    MetadataConversionException(String message) { super(message); }
}
