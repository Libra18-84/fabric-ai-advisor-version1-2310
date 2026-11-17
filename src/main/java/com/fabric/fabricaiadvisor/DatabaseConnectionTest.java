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
        System.out.println("===== KIỂM TRA SQLITE =====");

        try {
            Integer totalFabrics = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM fabric_database",
                    Integer.class
            );

            if (totalFabrics == null) {
                throw new IllegalStateException(
                        "Không nhận được kết quả đếm dữ liệu vải."
                );
            }

            System.out.println("ĐỌC DATABASE SQLITE THÀNH CÔNG!");
            System.out.println("Tổng số mẫu vải: " + totalFabrics);

            if (totalFabrics == 0) {
                System.out.println(
                        "Lưu ý: bảng fabric_database hiện chưa có dữ liệu."
                );
            }

        } catch (Exception e) {
            throw new IllegalStateException(
                    "Không đọc được bảng fabric_database. "
                            + "Kiểm tra DATABASE_URL và file database thực tế.",
                    e
            );
        }
    }
}