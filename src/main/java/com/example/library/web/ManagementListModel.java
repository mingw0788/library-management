package com.example.library.web;

import org.springframework.data.domain.Page;
import org.springframework.ui.Model;
import java.util.LinkedHashMap;
import java.util.Set;

public final class ManagementListModel {
    private ManagementListModel() {}

    public static void apply(Model model, ManagementSearch search, Page<?> page, String path, Set<String> states) {
        model.addAttribute("query", search.query());
        model.addAttribute("listStatus", search.status());
        model.addAttribute("resultPage", page);
        model.addAttribute("listPath", path);
        var labels = new LinkedHashMap<String, String>();
        labels.put("ALL", "全部状态");
        String[][] all = {{"ACTIVE", "有效 / 启用"}, {"DISABLED", "已禁用"}, {"ARCHIVED", "已归档"},
                {"AVAILABLE", "可借"}, {"ON_LOAN", "借出中"}, {"HELD", "预约保留"}, {"WITHDRAWN", "已注销"}};
        for (String[] pair : all) if (states.contains(pair[0])) labels.put(pair[0], pair[1]);
        model.addAttribute("listStates", labels);
    }
}
