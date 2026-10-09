package com.example.library.catalog;

public final class IsbnNormalizer {

    private IsbnNormalizer() {}

    public static String normalize(String value) {
        if (value == null || value.codePoints().allMatch(codePoint ->
                Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint))) return null;
        StringBuilder compact = new StringBuilder();
        value.codePoints().forEach(codePoint -> {
            if (codePoint == '-' || Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint)) return;
            compact.appendCodePoint(codePoint);
        });
        if (compact.isEmpty()) throw new IllegalArgumentException("ISBN 格式或校验位不正确，请输入有效的 ISBN-10 或 ISBN-13");
        String isbn = compact.toString().toUpperCase(java.util.Locale.ROOT);
        if (isbn.length() == 10 && isValidIsbn10(isbn)) return isbn;
        if (isbn.length() == 13 && isValidIsbn13(isbn)) return isbn;
        throw new IllegalArgumentException("ISBN 格式或校验位不正确，请输入有效的 ISBN-10 或 ISBN-13");
    }

    private static boolean isValidIsbn10(String isbn) {
        int weightedSum = 0;
        for (int index = 0; index < 9; index++) {
            char value = isbn.charAt(index);
            if (value < '0' || value > '9') return false;
            weightedSum += (value - '0') * (10 - index);
        }
        char check = isbn.charAt(9);
        int checkValue;
        if (check >= '0' && check <= '9') checkValue = check - '0';
        else if (check == 'X') checkValue = 10;
        else return false;
        return (weightedSum + checkValue) % 11 == 0;
    }

    private static boolean isValidIsbn13(String isbn) {
        if (!isbn.startsWith("978") && !isbn.startsWith("979")) return false;
        int weightedSum = 0;
        for (int index = 0; index < 12; index++) {
            char value = isbn.charAt(index);
            if (value < '0' || value > '9') return false;
            weightedSum += (value - '0') * (index % 2 == 0 ? 1 : 3);
        }
        char check = isbn.charAt(12);
        if (check < '0' || check > '9') return false;
        return (10 - weightedSum % 10) % 10 == check - '0';
    }
}
