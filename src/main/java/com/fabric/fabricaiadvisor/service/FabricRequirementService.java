
package com.fabric.fabricaiadvisor.service;

import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class FabricRequirementService {

    private String normalize(String value) {
        if (value == null) {
            return "";
        }

        String text = Normalizer.normalize(
                value.toLowerCase(Locale.ROOT),
                Normalizer.Form.NFD
        );

        return text.replaceAll("\\p{M}+", "")
                .replace('đ', 'd')
                .replaceAll("\\s+", " ")
                .trim();
    }

    public List<String> extractPreferences(
            String requirements) {

        String text = normalize(requirements);

        text = text.replaceAll(
                "(?<![a-z0-9])khong\\s+can\\s+(?:vai\\s+)?co\\s+gian(?![a-z0-9])",
                " "
        );

        text = text.replaceAll(
                "(?<![a-z0-9])khong\\s+can\\s+(?:vai\\s+)?it\\s+nhan(?![a-z0-9])",
                " "
        );

        Set<String> preferences = new LinkedHashSet<>();

        if (text.isEmpty()) {
            return new ArrayList<>();
        }

        if (text.contains("thoang mat")
                || text.contains("thoang khi")
                || text.contains("thong thoang")) {
            preferences.add("thoáng mát");
        }

        if (text.contains("it nhan")
                || text.contains("chong nhan")) {
            preferences.add("ít nhăn");
        }

        if (text.contains("mem mai")
                || text.contains("mem")
                || text.contains("em ai")) {
            preferences.add("mềm");
        }

        if (text.contains("co gian")
                || text.contains("dan hoi")) {
            preferences.add("co giãn");
        }

        if (text.contains("tham hut")
                || text.contains("hut am")) {
            preferences.add("thấm hút");
        }

        return new ArrayList<>(preferences);
    }
}
