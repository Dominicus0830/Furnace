package org.bukkit.persistence;

import java.util.List;

/** 같은 종류 값의 목록 종류(Paper {@code ListPersistentDataType}). 목록의 목록은 지원하지 않는다. */
public interface ListPersistentDataType<P, C> extends PersistentDataType<List<P>, List<C>> {

    /** 원소의 종류. */
    PersistentDataType<P, C> elementType();
}
