package com.fabric.fabricaiadvisor.repository;

import com.fabric.fabricaiadvisor.dto.FabricCriteria;
import com.fabric.fabricaiadvisor.service.FabricText;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.util.*;

@Repository
public class FabricRepository {
    private final JdbcTemplate jdbcTemplate;
    private static final String SELECT = """
            SELECT fabric_id, fabric_name, currency, fiber_composition, construction,
                   gsm_range, typical_width, price_min_vnd_per_m, price_max_vnd_per_m,
                   recommended_garments, properties_and_cautions, verification_status, source_url
            FROM fabric_database
            """;

    public FabricRepository(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }

    public int countFabrics() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM fabric_database", Integer.class);
        return count == null ? 0 : count;
    }

    public List<Map<String, Object>> findAllFabrics() {
        return jdbcTemplate.queryForList(SELECT + " ORDER BY fabric_id");
    }

    public List<Map<String, Object>> findFabricsByBudget(double maxBudget) {
        return findCandidates(criteria(null, maxBudget));
    }

    public List<Map<String, Object>> findFabricsByGarment(String garment) {
        return findCandidates(criteria(garment, null));
    }

    public List<Map<String, Object>> findFabricsByGarmentAndBudget(String garment, double maxBudget) {
        return findCandidates(criteria(garment, maxBudget));
    }

    private FabricCriteria criteria(String garment, Double budget) {
        if (budget != null && (!Double.isFinite(budget) || budget < 0))
            throw new IllegalArgumentException("Ngân sách không hợp lệ.");
        return new FabricCriteria("recommend_fabric", garment, budget == null ? null : BigDecimal.valueOf(budget),
                budget == null ? null : true, List.of(), false, null);
    }

    public List<Map<String, Object>> findCandidates(FabricCriteria criteria) {
        if (criteria == null || !"recommend_fabric".equals(criteria.intent()) || criteria.needsClarification())
            throw new IllegalArgumentException("Yêu cầu chưa đủ rõ để tìm vải.");
        BigDecimal budget = criteria.maxPriceVndPerMeter();
        if (budget != null && (budget.signum() < 0 || criteria.budgetInclusive() == null))
            throw new IllegalArgumentException("Giới hạn ngân sách không hợp lệ.");
        StringBuilder sql = new StringBuilder(SELECT + " WHERE 1 = 1 ");
        List<Object> parameters = new ArrayList<>();
        if (budget != null) {
            sql.append("""
                    AND price_min_vnd_per_m IS NOT NULL AND price_min_vnd_per_m >= 0
                    AND price_max_vnd_per_m IS NOT NULL
                    AND price_max_vnd_per_m >= price_min_vnd_per_m
                    AND price_max_vnd_per_m
                    """);
            sql.append(criteria.budgetInclusive() ? " <= ? " : " < ? ");
            parameters.add(budget.toPlainString());
        }
        sql.append(" ORDER BY fabric_id");
        return jdbcTemplate.queryForList(sql.toString(), parameters.toArray()).stream()
                .filter(row -> FabricText.matchesGarment(Objects.toString(row.get("recommended_garments"), ""), criteria.garment()))
                .toList();
    }
}
