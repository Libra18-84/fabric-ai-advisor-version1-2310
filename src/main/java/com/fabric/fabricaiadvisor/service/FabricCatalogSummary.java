package com.fabric.fabricaiadvisor.service;

import java.util.*;

/** Public overview built from the actual selection; model-written per-fabric reasons remain subject to validation. */
public final class FabricCatalogSummary {
    private FabricCatalogSummary() {}

    public static FabricExplanationService.Draft apply(List<Map<String, Object>> sources,
                                                       FabricExplanationService.Draft generated) {
        if (generated == null || generated.reasonsById() == null || generated.reasonsById().isEmpty())
            throw new IllegalStateException("Thiếu lựa chọn để tạo tóm tắt catalog.");
        var selected = sources.stream().filter(row -> generated.reasonsById().containsKey(row.get("fabric_id").toString())).toList();
        if (selected.size() != generated.reasonsById().size())
            throw new IllegalStateException("Lựa chọn trong dự thảo không thuộc catalog.");
        boolean unconfirmed = selected.stream().anyMatch(row -> !"independently_verified".equals(row.get("verification_status")));
        String summary = "Gợi ý tham khảo " + selected.size() + " loại vải theo bộ lọc đã nhận diện. "
                + "Giá, thành phần và nguồn của từng vải được giữ theo catalog. "
                + (unconfirmed ? "Có dữ liệu chưa được xác minh độc lập; cần kiểm tra theo mã vải trước khi mua."
                : "Trạng thái xác minh của từng vải được giữ theo catalog.");
        return new FabricExplanationService.Draft(summary,
                Collections.unmodifiableMap(new LinkedHashMap<>(generated.reasonsById())));
    }
}
