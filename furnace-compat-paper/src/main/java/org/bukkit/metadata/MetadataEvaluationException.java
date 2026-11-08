package org.bukkit.metadata;

/** LazyMetadataValue 의 계산이 던졌다(원래 예외가 cause). */
public class MetadataEvaluationException extends RuntimeException {

    MetadataEvaluationException(Throwable cause) { super(cause); }
}
