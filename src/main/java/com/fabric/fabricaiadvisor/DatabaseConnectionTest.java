
package com.fabric.fabricaiadvisor;

import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class DatabaseConnectionTest implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    public DatabaseConnectionTest(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        System.out.println("===== KIEM TRA SQLITE =====");

        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM sqlite_master " +
                            "WHERE type = 'table' " +
                            "AND name IN ('fabric_database', " +
                            "'fabric_sources', 'training_examples')",
                    Integer.class
            );

            System.out.println("So bang tim thay: " + count + "/3");

            if (count != null && count == 3) {
                System.out.println("KET NOI SQLITE THANH CONG!");

                Integer totalFabrics = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM fabric_database",
                        Integer.class
                );

                System.out.println("===== KIEM TRA DU LIEU VAI =====");
                System.out.println("Tong so mau vai: " + totalFabrics);

            } else {
                System.out.println("CHUA TIM THAY DU 3 BANG!");
            }

        } catch (Exception e) {
            System.out.println("LOI KET NOI DATABASE!");
            e.printStackTrace();
        }
    }
}
