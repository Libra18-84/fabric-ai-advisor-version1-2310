package com.fabric.fabricaiadvisor.service;

import java.util.List;
import java.util.Map;

/** Evidence in catalog text, not independent verification of the fabric. */
public final class FabricEvidenceRules {
    public enum State { MATCH, CONFLICT, UNKNOWN }
    private FabricEvidenceRules() {}

    private static final Map<String, List<String>> POSITIVE = Map.of(
            "thoáng mát", List.of("thoang", "thoang khi", "thong thoang", "mat me"),
            "ít nhăn", List.of("it nhan", "chong nhan", "han che nhan"),
            "mềm", List.of("mem", "mem mai", "em ai"),
            "co giãn", List.of("co gian", "dan hoi"),
            "thấm hút", List.of("tham hut", "hut am"));
    private static final Map<String, List<String>> NEGATIVE = Map.of(
            "thoáng mát", List.of("khong thoang", "kem thoang", "it thoang", "bi nong", "bi ham", "ham bi"),
            "ít nhăn", List.of("de nhan", "hay nhan", "nhan nhieu", "rat nhan", "khong chong nhan", "khong it nhan"),
            "mềm", List.of("khong mem", "kem mem", "tho rap", "cung"),
            "co giãn", List.of("khong co gian", "it co gian", "kem co gian", "khong dan hoi"),
            "thấm hút", List.of("khong tham hut", "kem tham hut", "it tham hut", "khong hut am"));
    private static final Map<String, List<String>> ABSENCE = Map.of(
            "thoáng mát", List.of("khong thoang"),
            "ít nhăn", List.of("khong it nhan", "khong chong nhan"),
            "mềm", List.of("khong mem"),
            "co giãn", List.of("khong co gian", "khong dan hoi"),
            "thấm hút", List.of("khong tham hut", "khong hut am"));

    public static String canonical(String preference) {
        return new FabricPreferenceNormalizer().normalizePreference(preference);
    }

    public static String category(String preference) {
        String normalized = canonical(preference);
        String key = normalized.startsWith("không ") ? normalized.substring(6) : normalized;
        return POSITIVE.containsKey(key) ? key : null;
    }

    public static State evaluate(Object rawDescription, String preference) {
        String category = category(preference);
        if (category == null) return State.UNKNOWN;
        boolean wantsAbsence = canonical(preference).startsWith("không ");
        String text = FabricText.normalize(rawDescription == null ? "" : rawDescription.toString())
                .replaceAll("(?<![a-z0-9])khong\\s+de\\s+nhan(?![a-z0-9])", "it nhan");
        boolean match = false, conflict = false, uncertain = false;
        for (String clause : text.split("[,;.!?]|\\b(?:nhung|tuy nhien)\\b")) {
            boolean positive = has(clause, POSITIVE.get(category));
            boolean negative = has(clause, NEGATIVE.get(category));
            if (!positive && !negative) continue;
            if (clause.matches(".*\\b(?:chua|kho xac dinh)\\b.*")
                    || clause.matches(".*\\bkhong (?:co|du) (?:thong tin|bang chung|du lieu)\\b.*")
                    || clause.matches(".*\\b(?:chua|chua duoc|khong) (?:xac minh|xac nhan|kiem chung|kiem tra|ro|co thong tin|du bang chung).*" )
                    || clause.matches(".*\\b(?:can|phai) (?:kiem tra|xac minh|xac nhan|kiem chung).*" )
                    || clause.matches(".*\\b(?:co the|tuy|phu thuoc|chua du bang chung|chua du thong tin|khong du thong tin).*")) {
                uncertain = true;
            } else if (wantsAbsence) {
                if (has(clause, ABSENCE.get(category))) match = true;
                else if (negative) uncertain = true;
                else conflict = true;
            } else if (negative) {
                conflict = true;
            } else {
                match = true;
            }
        }
        if (uncertain || (match && conflict)) return State.UNKNOWN;
        return conflict ? State.CONFLICT : match ? State.MATCH : State.UNKNOWN;
    }

    private static boolean has(String text, List<String> phrases) {
        return phrases.stream().anyMatch(phrase -> FabricText.contains(text, phrase));
    }
}
