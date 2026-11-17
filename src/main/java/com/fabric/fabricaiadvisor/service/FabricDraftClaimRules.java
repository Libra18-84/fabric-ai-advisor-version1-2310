package com.fabric.fabricaiadvisor.service;

import java.util.*;
import java.util.regex.Pattern;

/** Checks clear assertions in the five supported feature groups, using catalog evidence only. */
public final class FabricDraftClaimRules {
    private FabricDraftClaimRules() {}
    private static final List<String> FEATURES = List.of("thoáng mát", "ít nhăn", "mềm", "co giãn", "thấm hút");

    private static final Pattern NO_WRINKLES = Pattern.compile(
            "(?<![a-z0-9])khong\\s+(?:(?:bi|bao gio|he)\\s+)?nhan(?![a-z0-9])");

    public static List<String> check(List<Map<String, Object>> sources, FabricExplanationService.Draft draft) {
        List<String> issues = new ArrayList<>();
        List<Map<String, Object>> selected = sources.stream()
                .filter(row -> draft.reasonsById().containsKey(row.get("fabric_id").toString())).toList();
        for (var row : selected) {
            String id = row.get("fabric_id").toString();
            inspect(draft.reasonsById().get(id), List.of(row), id, issues);
        }
        inspect(draft.explanation(), selected, "phần tóm tắt", issues);
        return issues.stream().distinct().toList();
    }

    private static void inspect(String text, List<Map<String, Object>> sources, String subject, List<String> issues) {
        for (String clause : qualified(text).split("[,;.!?]|\\b(?:nhung|tuy nhien)\\b")) {
            // A mention of the user's preference is not itself a property assertion.
            if (clause.matches(".*\\b(?:so thich|uu tien|mong muon|yeu cau|can luu y ve|can can nhac)\\b.*")
                    && !clause.matches(".*\\b(?:khang dinh|chac chan|dam bao|co dac tinh)\\b.*")) continue;
            if (assertsNoWrinkles(clause) && sources.stream().noneMatch(row ->
                    assertsNoWrinkles(qualified(Objects.toString(row.get("properties_and_cautions"), ""))))) {
                issues.add("Khẳng định không nhăn trong " + subject
                        + " mạnh hơn bằng chứng của nguồn. Ít nhăn hoặc ít nhăn tùy tỷ lệ pha không chứng minh không nhăn.");
            }
            for (String feature : FEATURES) {
                var claim = FabricEvidenceRules.evaluate(clause, feature);
                if (claim == FabricEvidenceRules.State.UNKNOWN) continue;
                boolean supported = sources.stream().anyMatch(row ->
                        FabricEvidenceRules.evaluate(qualified(Objects.toString(row.get("properties_and_cautions"), "")), feature) == claim);
                if (!supported) issues.add("Khẳng định về ‘" + feature + "’ trong " + subject
                        + " chưa được mô tả và cảnh báo của nguồn hỗ trợ. Hãy bỏ khẳng định hoặc nêu rõ thiếu thông tin.");
            }
        }
    }

    private static boolean assertsNoWrinkles(String text) {
        // Relative wrinkle resistance does not establish the absolute absence of wrinkles.
        // Explicit uncertainty and statements denying a claim are not assertions of absence.
        boolean assertion = false;
        for (String clause : text.split("[,;.!?]|\\b(?:nhung|tuy nhien)\\b")) {
            if (clause.matches(".*\\b(?:khong|chua) (?:khang dinh|ket luan|dam bao|chung minh)\\b.*")) continue;
            var mentions = NO_WRINKLES.matcher(clause);
            while (mentions.find()) {
                if (!clause.substring(0, mentions.start()).matches(".*\\bkhong phai(?: la)?\\s+$")) assertion = true;
            }
        }
        return assertion && FabricEvidenceRules.evaluate(NO_WRINKLES.matcher(text).replaceAll("it nhan"), "ít nhăn")
                == FabricEvidenceRules.State.MATCH;
    }

    private static String qualified(String text) {
        return FabricText.normalize(text).replaceAll(",\\s*(?=(?:tuy|phu thuoc)\\b)", " ");
    }
}
