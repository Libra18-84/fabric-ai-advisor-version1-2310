
package com.fabric.fabricaiadvisor.service;

import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class FabricPreferenceNormalizer {

    private String normalizeText(String value) {
        if (value == null) {
            return "";
        }

        String text = Normalizer.normalize(
                value.toLowerCase(Locale.ROOT),
                Normalizer.Form.NFD
        );

        return text
                .replaceAll("\\p{M}+", "")
                .replace('đ', 'd')
                .replaceAll("\\s+", " ")
                .trim();
    }

    public String normalizePreference(String preference) {
        String text = normalizeText(preference);

        // "Không cần" không có nghĩa là muốn đặc tính ngược lại.
        if (text.matches(
                "^khong (?:can|yeu cau|bat buoc|nhat thiet)\\b.*")) {
            return "";
        }

        if (text.equals("khong de nhan")) return "ít nhăn";

        boolean negative = text.startsWith("khong ");

        if (negative) {
            text = text.substring("khong ".length()).trim();
        }

        String normalized = switch (text) {
            case "mem", "mem mai", "em ai" -> "mềm";

            case "thoang", "thoang mat", "thoang khi",
                 "thong thoang", "mat me" -> "thoáng mát";

            case "it nhan", "chong nhan", "han che nhan" -> "ít nhăn";

            case "co gian", "dan hoi" -> "co giãn";

            case "tham hut", "hut am" -> "thấm hút";

            default -> text;
        };

        return negative ? "không " + normalized : normalized;
    }

    public List<String> normalizePreferences(
            List<String> preferences) {

        Set<String> uniquePreferences =
                new LinkedHashSet<>();

        if (preferences == null) {
            return new ArrayList<>();
        }

        for (String preference : preferences) {
            String normalized =
                    normalizePreference(preference);

            if (!normalized.isBlank()) {
                uniquePreferences.add(normalized);
            }
        }

        return new ArrayList<>(uniquePreferences);
    }
}
