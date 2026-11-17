package com.fabric.fabricaiadvisor.service;

import org.springframework.stereotype.Service;
import java.util.*;
import java.util.regex.*;

@Service
public class FabricRequirementService {
    public record Mention(String property, String category, boolean neutral, boolean required) {}
    private static final Map<String, List<String>> ALIASES = new LinkedHashMap<>();
    static {
        ALIASES.put("thoáng mát", List.of("thoang mat", "thoang khi", "thong thoang", "thoang", "mat me"));
        ALIASES.put("ít nhăn", List.of("it nhan", "chong nhan", "han che nhan", "khong de nhan"));
        ALIASES.put("mềm", List.of("mem mai", "em ai", "mem"));
        ALIASES.put("co giãn", List.of("co gian", "dan hoi"));
        ALIASES.put("thấm hút", List.of("tham hut", "hut am"));
    }

    public List<String> extractPreferences(String text) {
        return inspect(text).stream().filter(m -> !m.neutral())
                .map(Mention::property).distinct().toList();
    }

    public List<Mention> inspect(String input) {
        String text = FabricText.normalize(input);
        List<Mention> result = new ArrayList<>();
        for (var entry : ALIASES.entrySet()) {
            String choices = entry.getValue().stream()
                    .sorted(Comparator.comparingInt(String::length).reversed())
                    .map(Pattern::quote).reduce((a,b) -> a + "|" + b).orElseThrow();
            Matcher matcher = Pattern.compile("(?<![a-z0-9])(?:" + choices + ")(?![a-z0-9])").matcher(text);
            while (matcher.find()) {
                String[] clauses = text.substring(0, matcher.start())
                        .split("[,;.!?]|\\b(?:nhung|va|tuy nhien)\\b", -1);
                String prefix = clauses[clauses.length - 1].trim();
                String suffix = text.substring(matcher.end()).split("[,;.!?]|\\b(?:nhung|va)\\b", -1)[0].trim();
                boolean neutral = prefix.matches(".*\\bkhong (?:can|yeu cau|bat buoc|nhat thiet)(?: (?:phai|vai|chat lieu))*$");
                boolean negative = prefix.matches(".*\\b(?:khong|chang)(?: (?:muon|thich|duoc|phai|dung|chon|vai|chat lieu))*$")
                        || prefix.matches(".*\\btranh(?: (?:dung|chon|vai|chat lieu))*$");
                String[] scopes = text.substring(0, matcher.start())
                        .split("[,;.!?]|\\b(?:nhung|tuy nhien)\\b", -1);
                String scope = scopes[scopes.length - 1];
                boolean mandatoryScope = scope.matches(".*\\b(?:bat buoc|nhat dinh|tuyet doi|phai|chi chap nhan)\\b.*")
                        && !scope.matches(".*\\bkhong (?:can|yeu cau|bat buoc|nhat thiet)\\b.*");
                boolean required = !neutral && (negative || mandatoryScope
                        || prefix.matches(".*\\b(?:bat buoc|nhat dinh|tuyet doi|yeu cau|phai|chi chap nhan)\\b.*")
                        || suffix.matches("^(?:la )?(?:bat buoc|yeu cau bat buoc)\\b.*"));
                result.add(new Mention((negative ? "không " : "") + entry.getKey(), entry.getKey(), neutral, required));
            }
        }
        return result;
    }
}
