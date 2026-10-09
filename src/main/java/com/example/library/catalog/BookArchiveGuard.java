package com.example.library.catalog;

/**
 * 归档前的业务记录保护点。T10/T13 会让实现检查副本、未还借阅和活动预约。
 */
@FunctionalInterface
public interface BookArchiveGuard {
    void assertCanArchive(Long bookId);
}
