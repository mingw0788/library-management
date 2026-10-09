package com.example.library.catalog;

import org.springframework.stereotype.Component;

@Component
public class NoBookArchiveGuard implements BookArchiveGuard {

    @Override
    public void assertCanArchive(Long bookId) {
        // T09 尚未创建副本、借阅和预约表；后续任务会提供实际检查实现。
    }
}
