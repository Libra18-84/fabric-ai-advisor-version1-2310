package com.fabric.fabricaiadvisor.service;

import com.fabric.fabricaiadvisor.dto.FabricCriteria;
import java.util.*;
import java.util.regex.Pattern;

/** Cross-check explicit supported constraints against the original user text. */
public final class FabricConstraintService {
    private FabricConstraintService() {}

    public static FabricCriteria guard(String query, FabricCriteria criteria) {
        var normalizer = new FabricPreferenceNormalizer();
        Set<String> soft = new LinkedHashSet<>(normalizer.normalizePreferences(criteria.preferences()));
        Set<String> hard = new LinkedHashSet<>(normalizer.normalizePreferences(criteria.requiredProperties()));
        var mentions = new FabricRequirementService().inspect(query);
        String issue = null;
        Map<String, String> seen = new HashMap<>();
        for (var mention : mentions) {
            String category = mention.category();
            if (mention.neutral()) {
                soft.removeIf(p -> category.equals(FabricEvidenceRules.category(p)));
                hard.removeIf(p -> category.equals(FabricEvidenceRules.category(p)));
                continue;
            }
            String expected = mention.property();
            String previous = seen.putIfAbsent(category, expected);
            if (previous != null && !previous.equals(expected)) {
                issue = "Bạn đang nêu cả hai điều kiện đối lập về " + category + ". Bạn muốn điều kiện nào?";
                break;
            }
            for (String property : union(soft, hard)) {
                if (category.equals(FabricEvidenceRules.category(property)) && !expected.equals(property)) {
                    issue = "Hệ thống chưa xác nhận đúng điều kiện ‘" + expected
                            + "’ trong câu hỏi. Bạn vui lòng xác nhận lại điều kiện này.";
                }
            }
            if (mention.required()) {
                soft.remove(expected);
                hard.add(expected);
            } else {
                hard.remove(expected);
                soft.add(expected);
            }
        }
        String text = FabricText.normalize(query);
        // Literal checks also cover model-extracted properties outside the five scoring groups.
        for (String property : union(soft, hard)) {
            String base = FabricText.normalize(property).replaceFirst("^khong ", "");
            if (base.isEmpty()) continue;
            String tail = Pattern.quote(base) + "(?![a-z0-9])";
            if (Pattern.compile("\\bkhong (?:can|yeu cau|bat buoc|nhat thiet)(?: (?:phai|vai|chat lieu))*\\s+" + tail).matcher(text).find()) {
                soft.remove(property); hard.remove(property);
                continue;
            }
            if (Pattern.compile("\\b(?:khong|chang|tranh)(?: (?:muon|thich|duoc|phai|dung|chon|vai|chat lieu))*\\s+" + tail).matcher(text).find()) {
                if (!property.startsWith("không ") && issue == null) {
                    issue = "Hệ thống chưa xác nhận đúng phủ định về ‘" + property + "’. Bạn vui lòng làm rõ điều kiện này.";
                } else if (property.startsWith("không ")) {
                    soft.remove(property); hard.add(property);
                }
            }
        }
        for (String clause : text.split("[,;.!?]|\\b(?:nhung|va|tuy nhien)\\b")) {
            boolean negative = Pattern.compile("\\b(?:khong|chang|tranh) (?!(?:qua|can|yeu cau|bat buoc|nhat thiet|chi)\\b)").matcher(clause).find();
            boolean understood = new FabricRequirementService().inspect(clause).stream()
                    .anyMatch(m -> !m.neutral() && m.property().startsWith("không "));
            if (negative && !understood && hard.stream().noneMatch(p -> p.startsWith("không ")
                    && FabricText.contains(clause, FabricText.normalize(p).substring(6)))) {
                issue = "Có điều kiện phủ định chưa được nhận diện chắc chắn. Bạn vui lòng nói rõ đặc tính cần tránh.";
            }
        }
        String mandatoryText = text
                .replaceAll("\\bkhong (?:can|yeu cau|bat buoc|nhat thiet)[^,;.!?]*", "")
                .replaceAll("\\b(?:bat buoc|nhat dinh|tuyet doi|phai)\\s+(?:(?:gia|ngan sach)\\s+)?(?:duoi|khong qua|toi da)\\s+\\d[\\d., ]*", "");
        if (issue == null && text.matches(".*\\b(?:it|kem|hoi) (?:co gian|dan hoi)\\b.*")) {
            issue = "Bạn muốn mức co giãn như thế nào? ‘Ít co giãn’ khác với ‘không co giãn’; dữ liệu hiện tại cần điều kiện rõ hơn.";
        }
        if (issue == null && hard.isEmpty()
                && mandatoryText.matches(".*\\b(?:bat buoc|nhat dinh|tuyet doi|chi chap nhan|vai phai|chat lieu phai)\\b.*")) {
            issue = "Bạn có yêu cầu bắt buộc chưa được nhận diện đầy đủ. Vui lòng nói rõ đặc tính bắt buộc của vải.";
        }
        soft.removeAll(hard);
        return new FabricCriteria(criteria.intent(), criteria.garment(), criteria.maxPriceVndPerMeter(),
                criteria.budgetInclusive(), new ArrayList<>(soft), criteria.needsClarification() || issue != null,
                issue == null ? criteria.clarificationQuestion() : issue, new ArrayList<>(hard));
    }

    private static Set<String> union(Set<String> a, Set<String> b) {
        var result = new LinkedHashSet<>(a);
        result.addAll(b);
        return result;
    }
}
