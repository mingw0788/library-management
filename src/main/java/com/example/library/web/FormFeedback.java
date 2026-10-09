package com.example.library.web;

import org.springframework.ui.Model;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.LinkedHashMap;
import java.util.Map;

/** Shared server-side feedback for existing forms. Only explicitly supplied, non-sensitive values are retained. */
public final class FormFeedback {

    private static final int MAX_RETAINED_CODE_POINTS = 2_000;

    private FormFeedback() {}

    public static Map<String, String> safeValues(Object... keyValues) {
        if (keyValues == null || keyValues.length % 2 != 0) {
            throw new IllegalArgumentException("表单值必须按字段名和值成对提供");
        }
        Map<String, String> values = new LinkedHashMap<>();
        for (int index = 0; index < keyValues.length; index += 2) {
            String key = String.valueOf(keyValues[index]);
            Object rawValue = keyValues[index + 1];
            if (rawValue != null) values.put(key, limit(String.valueOf(rawValue)));
        }
        return Map.copyOf(values);
    }

    public static void failure(RedirectAttributes redirect, String formKey, String message,
                               Map<String, String> safeValues, String... candidateFields) {
        redirect.addFlashAttribute("error", message);
        if (formKey != null) redirect.addFlashAttribute("formKey", formKey);
        if (safeValues != null && !safeValues.isEmpty()) {
            redirect.addFlashAttribute("formValues", safeValues);
        }
        String field = fieldFor(message, candidateFields);
        if (field != null) redirect.addFlashAttribute("fieldErrors", Map.of(field, message));
    }

    public static void failure(Model model, String formKey, String message,
                               Map<String, String> safeValues, String... candidateFields) {
        model.addAttribute("error", message);
        if (formKey != null) model.addAttribute("formKey", formKey);
        if (safeValues != null && !safeValues.isEmpty()) model.addAttribute("formValues", safeValues);
        String field = fieldFor(message, candidateFields);
        if (field != null) model.addAttribute("fieldErrors", Map.of(field, message));
    }

    public static Map<String, String> fieldErrors(String field, String message) {
        return Map.of(field, message);
    }

    private static String fieldFor(String message, String... candidates) {
        if (message == null || candidates == null) return null;
        for (String field : candidates) {
            String[] labels = switch (field) {
                case "loginName" -> new String[]{"登录名"};
                case "initialPassword", "newPassword", "currentPassword", "confirmPassword" -> new String[]{"密码"};
                case "displayName" -> new String[]{"姓名"};
                case "role" -> new String[]{"角色"};
                case "readerNo" -> new String[]{"读者证号"};
                case "contact" -> new String[]{"联系方式"};
                case "shelfLocation" -> new String[]{"馆藏位置", "位置"};
                case "name" -> new String[]{"分类名称"};
                case "title" -> new String[]{"书名"};
                case "author" -> new String[]{"作者"};
                case "publisher" -> new String[]{"出版社"};
                case "publishedDate" -> new String[]{"出版日期"};
                case "isbn" -> new String[]{"ISBN"};
                case "categoryId" -> new String[]{"分类"};
                case "description" -> new String[]{"简介"};
                case "bookId" -> new String[]{"书目"};
                case "barcode" -> new String[]{"条码", "馆藏", "副本"};
                case "loanId" -> new String[]{"借阅编号"};
                default -> new String[0];
            };
            for (String label : labels) {
                if (message.contains(label)) return field;
            }
        }
        return null;
    }

    private static String limit(String value) {
        int points = value.codePointCount(0, value.length());
        if (points <= MAX_RETAINED_CODE_POINTS) return value;
        return value.substring(0, value.offsetByCodePoints(0, MAX_RETAINED_CODE_POINTS));
    }
}
