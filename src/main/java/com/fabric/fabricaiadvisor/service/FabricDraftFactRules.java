package com.fabric.fabricaiadvisor.service;

import com.fabric.fabricaiadvisor.dto.FabricCriteria;
import java.math.BigDecimal;
import java.util.*;
import java.util.regex.*;

/** Checks explicit catalog fact claims. This is a bounded recognizer, not a general language verifier. */
public final class FabricDraftFactRules {
    private FabricDraftFactRules() {}
    private static final String NUMBER = "\\d[\\d.,]*(?: \\d{3})*";
    private static final String SCALE = "nghin|ngan|trieu|tr|k";
    private static final String MONEY = "(?<currency>vnd|dong|d|usd|eur)(?![a-z])(?:\\s*/\\s*(?<unit>met(?:\\s+(?:vuong|khoi))?|m(?:\\^?[23]|[²³])?|kg)(?![a-z0-9]))?";
    private static final Pattern RANGE = Pattern.compile("(?<![a-z0-9.,])(?<low>" + NUMBER
            + ")\\s*(?<lowScale>" + SCALE + ")?\\s*(?:vnd|dong|d)?\\s*(?:-|den|toi)\\s*(?<high>"
            + NUMBER + ")\\s*(?<highScale>" + SCALE + ")?\\s*" + MONEY);
    private static final Pattern PRICE = Pattern.compile("(?<![a-z0-9.,])(?<value>" + NUMBER
            + ")\\s*(?<scale>" + SCALE + ")?\\s*" + MONEY);
    private static final String FIBER = "cotton|polyester|linen|viscose|rayon|spandex|elastane|nylon|polyamide|wool|silk|bong|lanh|len|lua";
    private static final Pattern PERCENT_FIRST = Pattern.compile("(?<![a-z0-9.,])(?<value>\\d+(?:[.,]\\d+)?)\\s*%\\s*(?<fiber>" + FIBER + ")(?![a-z])");
    private static final Pattern FIBER_FIRST = Pattern.compile("(?<![a-z])(?<fiber>" + FIBER + ")\\s*(?<value>\\d+(?:[.,]\\d+)?)\\s*%");
    private static final Pattern FIBER_WORD = Pattern.compile("(?<![a-z])(" + FIBER + ")(?![a-z])");
    private static final Pattern COMPOSITION = Pattern.compile("\\bthanh phan(?: theo (?:catalog|nguon|du lieu))?\\s*(?:la|gom|:)\\s+(.+?)(?=,(?!\\d)|$)");
    private static final Pattern VERIFIED = Pattern.compile("\\b(?:(?:da duoc|da|duoc) (?:xac minh|kiem chung|xac thuc)(?: doc lap)?|(?:is|has been) (?:independently )?verified|verified independently)\\b");
    private static final Pattern URL = Pattern.compile("https?://[^\\s\"<>]+", Pattern.CASE_INSENSITIVE);
    private record Portion(String fiber, BigDecimal value) {}
    private record Mention(int start, int end, Map<String, Object> row, boolean fullName) {}
    private record Target(List<Map<String, Object>> rows, boolean aggregate) {}

    public static List<String> check(FabricCriteria criteria, List<Map<String, Object>> sources,
                                     FabricExplanationService.Draft draft) {
        List<String> issues = new ArrayList<>();
        var selected = sources.stream().filter(row -> draft.reasonsById().containsKey(row.get("fabric_id").toString())).toList();
        for (var row : selected) inspect(criteria, draft.reasonsById().get(row.get("fabric_id").toString()),
                List.of(row), row.get("fabric_id").toString(), issues);
        inspect(criteria, draft.explanation(), selected, "phần tóm tắt", issues);
        return issues.stream().distinct().toList();
    }

    private static void inspect(FabricCriteria criteria, String text, List<Map<String, Object>> sources,
                                String subject, List<String> issues) {
        checkUrls(text, scope(normalized(text), sources), subject, issues);
        for (String raw : URL.matcher(text).replaceAll(" ").split("[;!?]\\s*|(?<=\\.)\\s+")) {
            String clause = normalized(raw);
            var scope = scope(clause, sources);
            checkVerification(clause, scope, subject, issues);
            checkComposition(clause, sources, subject, issues);
            checkPrices(criteria, clause, sources, subject, issues);
        }
    }

    // Resolve each fact separately; a sentence may mention several different fabrics.
    private static Target target(String text, int start, int end, List<Map<String, Object>> sources) {
        if (sources.size() == 1) return new Target(sources, false);
        var mentions = mentions(text, sources).stream()
                .filter(m -> m.end() <= start || m.start() >= end).toList();
        int previous = -1;
        for (int i = 0; i < mentions.size(); i++) if (mentions.get(i).end() <= start) previous = i;
        if (previous >= 0) {
            int first = previous;
            while (first > 0 && text.substring(mentions.get(first-1).end(), mentions.get(first).start())
                    .matches("\\s*(?:(?:,|/|&|va|hoac|hay|cung|voi)\\s*)+")) first--;
            var rows = mentions.subList(first, previous+1).stream().map(Mention::row).distinct().toList();
            String predicate = text.substring(mentions.get(previous).end(), start);
            boolean identical = predicate.matches(".*\\b(?:cung(?: mot)? (?:khoang )?gia|giong nhau)\\b.*");
            return new Target(rows, rows.size() > 1 && !identical);
        }
        for (var mention : mentions) {
            if (mention.start() >= end && text.substring(end, mention.start()).matches("\\s*(?:cua|cho|la|voi vai)\\s*"))
                return new Target(List.of(mention.row()), false);
        }
        boolean aggregate = text.matches(".*\\b(?:cac (?:loai|vai|goi y)|nhom vai|toan bo|dao dong)\\b.*");
        return new Target(sources, aggregate);
    }

    private static List<Mention> mentions(String text, List<Map<String, Object>> sources) {
        List<Mention> candidates = new ArrayList<>();
        for (var row : sources) {
            addMentions(candidates, text, normalized(row.get("fabric_id")), row, false);
            String name = normalized(row.get("fabric_name"));
            if (name.isBlank()) continue;
            String first = name.split("[^a-z0-9]+", 2)[0];
            long sameFirst = sources.stream().filter(r -> normalized(r.get("fabric_name")).split("[^a-z0-9]+", 2)[0].equals(first)).count();
            long sameName = sources.stream().filter(r -> normalized(r.get("fabric_name")).replace(" ", "").equals(name.replace(" ", ""))).count();
            if (sameName == 1 && (!name.equals(first) || sameFirst == 1)) addMentions(candidates, text, name, row, true);
            if (!first.isBlank() && sameFirst == 1) addMentions(candidates, text, first, row, false);
        }
        candidates.sort(Comparator.comparingInt(Mention::start).thenComparing(Comparator.comparingInt(Mention::end).reversed()));
        List<Mention> result = new ArrayList<>();
        for (var candidate : candidates) {
            if (result.stream().noneMatch(m -> candidate.start() < m.end() && candidate.end() > m.start())) result.add(candidate);
        }
        return result;
    }

    private static void addMentions(List<Mention> result, String text, String name, Map<String, Object> row, boolean full) {
        if (name.isBlank()) return;
        String pattern = Arrays.stream(name.split("\\s+")).map(Pattern::quote).reduce((a,b) -> a + "\\s*" + b).orElse("");
        Matcher match = Pattern.compile("(?<![a-z0-9])" + pattern + "(?![a-z0-9])").matcher(text);
        while (match.find()) result.add(new Mention(match.start(), match.end(), row, full));
    }

    private static boolean quotedName(String clause, int start, int end, List<Map<String, Object>> sources) {
        if (clause.substring(0, start).matches(".*\\bthanh phan[^,;.!?]*$")) return false;
        return mentions(clause, sources).stream().anyMatch(m -> m.fullName() && m.start() <= start && m.end() >= end);
    }

    private static String ids(List<Map<String, Object>> rows) { return rows.stream().map(r -> Objects.toString(r.get("fabric_id"))).toList().toString(); }

    private static List<Map<String, Object>> scope(String clause, List<Map<String, Object>> sources) {
        var ids = sources.stream().filter(row -> FabricText.contains(clause, normalized(row.get("fabric_id")))).toList();
        if (!ids.isEmpty()) return ids;
        var names = sources.stream().filter(row -> {
            String full = normalized(row.get("fabric_name"));
            if (full.isBlank()) return false;
            String first = full.split("[^a-z0-9]+", 2)[0];
            return FabricText.contains(clause, full) || !first.isBlank() && FabricText.contains(clause, first);
        }).toList();
        return names.isEmpty() ? sources : names;
    }

    private static void checkUrls(String raw, List<Map<String, Object>> sources, String subject, List<String> issues) {
        Matcher urls = URL.matcher(raw);
        while (urls.find()) {
            String literal = urls.group();
            String trimmed = literal.replaceAll("[.,;:!?)\\]]+$", "");
            if (sources.stream().noneMatch(row -> Objects.toString(row.get("source_url"), "").equals(literal)
                    || Objects.toString(row.get("source_url"), "").equals(trimmed)))
                issues.add("URL trong " + subject + " không thuộc source_url của vải tương ứng. Chỉ dùng URL catalog hoặc bỏ URL để backend hiển thị.");
        }
    }

    private static void checkVerification(String clause, List<Map<String, Object>> sources, String subject, List<String> issues) {
        Matcher claims = VERIFIED.matcher(clause);
        while (claims.find()) {
            String prefix = clause.substring(0, claims.start());
            if (prefix.matches(".*\\b(?:chua|khong|can|phai|not)\\s*$")) continue;
            boolean independent = claims.group().contains("doc lap") || claims.group().contains("independently");
            boolean supported = sources.stream().allMatch(row -> {
                String status = Objects.toString(row.get("verification_status"), "");
                return independent ? "independently_verified".equals(status)
                        : Set.of("verified", "independently_verified").contains(status);
            });
            if (!supported) issues.add("Khẳng định đã xác minh trong " + subject + " không được verification_status hỗ trợ. Giữ đúng trạng thái chưa xác minh của nguồn.");
        }
        if (clause.contains("bao dam chinh xac ngoai thuc te") && !clause.contains("chua bao dam"))
            issues.add("Catalog không cung cấp căn cứ bảo đảm chính xác ngoài thực tế trong " + subject + ".");
    }

    private static void checkComposition(String clause, List<Map<String, Object>> sources, String subject, List<String> issues) {
        for (Pattern pattern : List.of(PERCENT_FIRST, FIBER_FIRST)) {
            Matcher match = pattern.matcher(clause);
            while (match.find()) {
                if (clause.substring(0, match.start()).matches(".*\\b(?:khong phai|chua xac nhan|khong the khang dinh)\\s*$")) continue;
                if (quotedName(clause, match.start(), match.end(), sources)) continue;
                var rows = target(clause, match.start(), match.end(), sources).rows();
                Portion claim = portion(match);
                if (rows.stream().anyMatch(row -> portions(normalized(row.get("fiber_composition"))).stream()
                        .noneMatch(actual -> actual.fiber().equals(claim.fiber()) && actual.value().compareTo(claim.value()) == 0)))
                    issues.add("Tỷ lệ ‘" + match.group() + "’ trong " + subject + " không khớp fiber_composition của " + ids(rows) + ". Không tự suy tỷ lệ từ tên vải; giữ điều kiện cần xác minh.");
            }
        }
        Matcher composition = COMPOSITION.matcher(clause);
        while (composition.find()) {
            var rows = target(clause, composition.start(), composition.end(), sources).rows();
            Matcher fibers = FIBER_WORD.matcher(composition.group(1));
            while (fibers.find()) {
                String wanted = fiber(fibers.group());
                if (rows.stream().anyMatch(row -> fiberWords(normalized(row.get("fiber_composition"))).stream().noneMatch(wanted::equals)))
                    issues.add("Tên sợi ‘" + fibers.group() + "’ trong " + subject + " không có trong fiber_composition của " + ids(rows) + ".");
            }
        }
    }

    private static List<Portion> portions(String text) {
        List<Portion> parts = new ArrayList<>();
        for (Pattern pattern : List.of(PERCENT_FIRST, FIBER_FIRST)) {
            Matcher match = pattern.matcher(text);
            while (match.find()) parts.add(portion(match));
        }
        return parts;
    }
    private static Portion portion(Matcher match) { return new Portion(fiber(match.group("fiber")), new BigDecimal(match.group("value").replace(',', '.'))); }
    private static Set<String> fiberWords(String text) {
        Set<String> result = new HashSet<>();
        Matcher match = FIBER_WORD.matcher(text);
        while (match.find()) result.add(fiber(match.group()));
        return result;
    }
    private static String fiber(String name) {
        return switch (name) { case "bong" -> "cotton"; case "lanh" -> "linen"; case "len" -> "wool"; case "lua" -> "silk"; default -> name; };
    }

    private static void checkPrices(FabricCriteria criteria, String clause, List<Map<String, Object>> sources,
                                     String subject, List<String> issues) {
        List<int[]> ranges = new ArrayList<>();
        Matcher range = RANGE.matcher(clause);
        while (range.find()) {
            ranges.add(new int[]{range.start(), range.end()});
            String scale = range.group("lowScale") == null ? range.group("highScale") : range.group("lowScale");
            BigDecimal low = amount(range.group("low"), scale), high = amount(range.group("high"), range.group("highScale"));
            Target target = target(clause, range.start(), range.end(), sources);
            boolean supported = target.aggregate()
                    ? same(low, endpoint(target.rows(), "price_min_vnd_per_m", true)) && same(high, endpoint(target.rows(), "price_max_vnd_per_m", false))
                    : target.rows().stream().allMatch(row -> same(low, row.get("price_min_vnd_per_m")) && same(high, row.get("price_max_vnd_per_m")));
            if (negativePrefix(clause, range.start()) || !vndPerMeter(range, clause, target.rows()) || low == null || high == null || !supported)
                issues.add("Khoảng giá ‘" + range.group() + "’ trong " + subject + " không khớp giá VND/m của " + ids(target.rows()) + ". Để backend hiển thị giá từ catalog.");
        }
        Matcher price = PRICE.matcher(clause);
        while (price.find()) {
            int start = price.start();
            if (ranges.stream().anyMatch(span -> start >= span[0] && start < span[1])) continue;
            BigDecimal value = amount(price.group("value"), price.group("scale"));
            String prefix = clause.substring(0, price.start());
            boolean budget = prefix.matches(".*\\b(?:ngan sach|gioi han gia|yeu cau ve gia|yeu cau gia|yeu cau toi da)\\b.*");
            var priceTarget = target(clause, price.start(), price.end(), sources);
            boolean supported = !negativePrefix(clause, price.start()) && vndPerMeter(price, clause, priceTarget.rows()) && value != null;
            if (supported && budget) supported = criteria.maxPriceVndPerMeter() != null
                    && value.compareTo(criteria.maxPriceVndPerMeter()) == 0;
            else if (supported) {
                boolean from = prefix.matches(".*\\btu\\s*$");
                boolean upTo = prefix.matches(".*\\b(?:toi da|den|toi)\\s*$");
                Target target = target(clause, price.start(), price.end(), sources);
                if (target.aggregate() && (from || upTo)) supported = same(value, endpoint(target.rows(), from ? "price_min_vnd_per_m" : "price_max_vnd_per_m", from));
                else supported = target.rows().stream().allMatch(row -> (upTo || same(value, row.get("price_min_vnd_per_m")))
                        && (from || same(value, row.get("price_max_vnd_per_m"))));
            }
            if (!supported) issues.add("Giá nêu trong " + subject + " không có căn cứ khớp catalog hoặc ngân sách. Không biến khoảng giá thành một giá cố định.");
        }
    }

    private static BigDecimal endpoint(List<Map<String, Object>> rows, String field, boolean minimum) {
        List<BigDecimal> values = new ArrayList<>();
        for (var row : rows) {
            try { values.add(new BigDecimal(Objects.toString(row.get(field)))); }
            catch (NumberFormatException e) { return null; }
        }
        return values.stream().min(minimum ? Comparator.naturalOrder() : Comparator.reverseOrder()).orElse(null);
    }

    private static boolean vndPerMeter(Matcher match, String clause, List<Map<String, Object>> rows) {
        if (!Set.of("vnd", "dong", "d").contains(match.group("currency"))) return false;
        if (match.group("unit") != null) return Set.of("m", "met").contains(match.group("unit"));
        // In a reason for one typed SQL row, bare VND can omit the repeated /m.
        // An explicit different unit or a total cost cannot use this contextual shorthand.
        if (rows.size() != 1 || !"VND".equals(rows.getFirst().get("currency"))
                || clause.substring(match.end()).stripLeading().startsWith("/")
                || clause.matches(".*\\b(?:kg|kilogram|yard|vuong|khoi|tong (?:chi phi|ngan sach|tien|gia))\\b.*")) return false;
        return endpoint(rows, "price_min_vnd_per_m", true) != null && endpoint(rows, "price_max_vnd_per_m", false) != null;
    }
    private static boolean negativePrefix(String text, int start) { return text.substring(0, start).matches(".*(?:[-−]\\s*|\\bam\\s*)$"); }
    private static boolean same(BigDecimal value, Object actual) {
        try { return value != null && actual != null && value.compareTo(new BigDecimal(actual.toString())) == 0; }
        catch (NumberFormatException e) { return false; }
    }
    private static BigDecimal amount(String raw, String scale) {
        String number = raw.replace(" ", "");
        if (number.matches("\\d{1,3}(?:\\.\\d{3})+") || number.matches("\\d{1,3}(?:,\\d{3})+")) number = number.replace(".", "").replace(",", "");
        else if (!number.matches("\\d+(?:[.,]\\d+)?")) return null;
        else number = number.replace(',', '.');
        try {
            BigDecimal value = new BigDecimal(number);
            return scale == null ? value : value.multiply(new BigDecimal(Set.of("trieu", "tr").contains(scale) ? "1000000" : "1000"));
        } catch (NumberFormatException e) { return null; }
    }
    private static String normalized(Object text) {
        return FabricText.normalize(Objects.toString(text, "").replace('\u00a0', ' ').replace('\u202f', ' ')
                .replace('\u2013', '-').replace('\u2014', '-'));
    }
}
