
package com.fabric.fabricaiadvisor.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Map;
@Repository
public class FabricRepository {

    private final JdbcTemplate jdbcTemplate;

    public FabricRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public int countFabrics() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM fabric_database",
                Integer.class
        );

        return count == null ? 0 : count;
    }

    public List<Map<String, Object>> findAllFabrics() {

        String sql = """
        SELECT
            fabric_id,
            fabric_name,
            fiber_composition,
            construction,
            gsm_range,
            typical_width,
            price_min_vnd_per_m,
            price_max_vnd_per_m,
            recommended_garments,
            properties_and_cautions,
            verification_status,
            source_url
        FROM fabric_database
        ORDER BY fabric_id
        """;

        return jdbcTemplate.queryForList(sql);
    }

    public List<Map<String, Object>> findFabricsByBudget(
            double maxBudget) {

        String sql = """
        SELECT
            fabric_id,
            fabric_name,
            fiber_composition,
            gsm_range,
            price_min_vnd_per_m,
            price_max_vnd_per_m,
            recommended_garments,
            verification_status,
            source_url
        FROM fabric_database
        WHERE price_min_vnd_per_m IS NOT NULL
          AND price_min_vnd_per_m <= ?
        ORDER BY price_min_vnd_per_m ASC
        """;

        return jdbcTemplate.queryForList(sql, maxBudget);
    }

    public List<Map<String, Object>> findFabricsByGarment(
            String garment) {

        String sql = """
        SELECT
            fabric_id,
            fabric_name,
            fiber_composition,
            gsm_range,
            price_min_vnd_per_m,
            price_max_vnd_per_m,
            recommended_garments,
            verification_status,
            source_url
        FROM fabric_database
        WHERE recommended_garments IS NOT NULL
          AND LOWER(recommended_garments) LIKE ?
        ORDER BY fabric_id
        """;

        String search = "%" + garment.toLowerCase() + "%";

        return jdbcTemplate.queryForList(sql, search);
    }

    public List<Map<String, Object>> findFabricsByGarmentAndBudget(
            String garment, double maxBudget) {

        String sql = """
        SELECT
            fabric_id,
            fabric_name,
            fiber_composition,
            construction,
            gsm_range,
            typical_width,
            price_min_vnd_per_m,
            price_max_vnd_per_m,
            recommended_garments,
            properties_and_cautions,
            verification_status,
            source_url
        FROM fabric_database
        WHERE recommended_garments IS NOT NULL
          AND LOWER(recommended_garments) LIKE ?
          AND price_min_vnd_per_m IS NOT NULL
          AND price_min_vnd_per_m <= ?
        ORDER BY price_min_vnd_per_m ASC
        """;

        String search = "%" + garment.toLowerCase() + "%";

        return jdbcTemplate.queryForList(
                sql, search, maxBudget
        );
    }

}
