package com.fabric.fabricaiadvisor.service;

import com.fabric.fabricaiadvisor.dto.FabricCriteria;
import java.math.BigDecimal;
import java.util.*;
import java.util.regex.*;

/** Confirms the scalar VND/m ceiling from text before using model-extracted prices. */
public final class FabricBudgetRules {
    private FabricBudgetRules() {}
    private static final Pattern PRICE = Pattern.compile(
            "(?<![a-z0-9.,])(?<amount>\\d[\\d.,]*(?: +\\d{3})*)\\s*"
            + "(?<scale>nghin|ngan|trieu|tr|k)?\\s*"
            + "(?<currency>[a-z$€]+)?\\s*(?:/|\\b(?:moi|tren|mot) +)"
            + "(?<unit>met|m)(?![\\p{L}\\p{N}])");
    private static final Pattern BOUND = Pattern.compile(
            "\\b(khong duoi|khong thap hon|khong it hon|toi thieu|it nhat|"
            + "khong qua|khong vuot qua|khong hon|toi da|duoi|it hon|nho hon|thap hon|tren|lon hon|cao hon)"
            + "\\s*(?:(?:la|o muc)\\s*)?$");
    private static final Set<String> INCLUSIVE = Set.of("khong qua", "khong vuot qua", "khong hon", "toi da");
    private static final Set<String> EXCLUSIVE = Set.of("duoi", "it hon", "nho hon", "thap hon");
    private static final String QUESTION = "Vui lòng cho biết giới hạn giá tối đa bằng đồng/mét; "
            + "tổng ngân sách hoặc đơn vị giá khác chưa đủ để lọc theo giá mỗi mét.";
    private record Limit(BigDecimal value, Boolean inclusive) {}

    public static FabricCriteria guard(String query, Map<String, Object> preferences, FabricCriteria criteria) {
        if (!"recommend_fabric".equals(criteria.intent())) return criteria;
        String text = normalize(query);
        if (!hasMoney(text) && !PRICE.matcher(text).find() && preferences != null
                && preferences.get("context") instanceof String context) text = normalize(context);
        Limit limit = read(text);
        return new FabricCriteria(criteria.intent(), criteria.garment(),
                limit == null ? null : limit.value(), limit == null ? null : limit.inclusive(),
                criteria.preferences(), criteria.needsClarification() || limit == null,
                limit == null ? QUESTION : criteria.clarificationQuestion(), criteria.requiredProperties());
    }

    private static Limit read(String text) {
        if (text.matches(".*\\b(?:tong (?:ngan sach|chi phi|tien|so tien)|tong cong)\\b.*")) return null;
        List<Limit> limits = new ArrayList<>();
        Matcher match = PRICE.matcher(text);
        while (match.find()) {
            String prefix = text.substring(0, match.start());
            String after = text.substring(match.end());
            String currency = match.group("currency"), scale = match.group("scale");
            if (currency != null && !Set.of("d", "dong", "vnd").contains(currency)) return null;
            if (currency == null && scale == null) return null;
            if (prefix.matches(".*(?:[-$€]|\\d\\s+)$") || prefix.matches(".*\\bam\\s*$")
                    || after.matches("^\\s*(?:vuong|khoi)\\b.*")) return null;
            if (prefix.matches(".*\\b(?:tu|khoang|tam)\\s+\\d[\\d., ]*\\s+(?:den|toi|-)?\\s*$")) return null;
            Matcher bound = BOUND.matcher(prefix);
            Boolean inclusive;
            if (bound.find()) {
                String kind = bound.group(1);
                if (prefix.substring(0, bound.start()).matches(".*\\bkhong (?:can|yeu cau)\\s*$")) return null;
                if (INCLUSIVE.contains(kind)) inclusive = true;
                else if (EXCLUSIVE.contains(kind)) inclusive = false;
                else return null;
            } else if (prefix.matches(".*\\b(?:ngan sach|gioi han gia)\\s*(?:la\\s*)?$")) inclusive = true;
            else return null;
            BigDecimal value = amount(match.group("amount"), scale);
            if (value == null) return null;
            limits.add(new Limit(value, inclusive));
        }
        if (limits.isEmpty()) return null;
        Limit first = limits.getFirst();
        if (limits.stream().anyMatch(l -> l.value().compareTo(first.value()) != 0
                || !l.inclusive().equals(first.inclusive()))) return null;
        return first;
    }

    private static BigDecimal amount(String raw, String scale) {
        String text = raw.replace(" ", "");
        if (raw.contains(" ") && !raw.matches("\\d{1,3}(?: +\\d{3})+")) return null;
        if (text.matches("\\d{1,3}(?:\\.\\d{3})+") || text.matches("\\d{1,3}(?:,\\d{3})+")) {
            text = text.replace(".", "").replace(",", "");
        } else if (text.matches("\\d+")) {
            // Whole dong, including 0 and 1, must never be expanded by model guesses.
        } else if (scale != null && text.matches("\\d+[.,]\\d{1,2}")) text = text.replace(',', '.');
        else return null;
        try {
            BigDecimal value = new BigDecimal(text);
            return scale == null ? value : value.multiply(new BigDecimal(
                    Set.of("trieu", "tr").contains(scale) ? "1000000" : "1000"));
        } catch (NumberFormatException e) { return null; }
    }

    private static boolean hasMoney(String text) {
        return text.matches(".*\\b(?:tong (?:ngan sach|chi phi|tien|so tien)|tong cong)\\b.*")
                || Pattern.compile("\\d[\\d., ]*\\s*(?:dong|vnd|usd|eur|nghin|ngan|trieu|k|tr)\\b").matcher(text).find();
    }

    private static String normalize(String text) {
        return FabricText.normalize(text == null ? "" : text.replace('\u00a0', ' ').replace('\u202f', ' ')
                .replace('\u2212', '-').replace('\u2013', '-'));
    }
}
