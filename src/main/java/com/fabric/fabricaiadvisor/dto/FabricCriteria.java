package com.fabric.fabricaiadvisor.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

public record FabricCriteria(
        String intent,
        String garment,
        BigDecimal maxPriceVndPerMeter,
        Boolean budgetInclusive,
        List<String> preferences,
        boolean needsClarification,
        String clarificationQuestion,
        List<String> requiredProperties
) {
    public FabricCriteria {
        preferences = preferences == null ? List.of() : List.copyOf(preferences);
        requiredProperties = requiredProperties == null ? List.of() : List.copyOf(requiredProperties);
    }

    public FabricCriteria(String intent, String garment, BigDecimal price, Boolean inclusive,
                          List<String> preferences, boolean clarify, String question) {
        this(intent, garment, price, inclusive, preferences, clarify, question, List.of());
    }

    public List<String> allProperties() {
        var values = new LinkedHashSet<>(requiredProperties);
        values.addAll(preferences);
        return new ArrayList<>(values);
    }
}
