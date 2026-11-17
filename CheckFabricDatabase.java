import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

public class CheckFabricDatabase {

    public static void main(String[] args) throws Exception {

        String url =
                "jdbc:sqlite:file:fabric_for_integration.db?mode=ro";

        try (Connection connection =
                     DriverManager.getConnection(url);
             Statement statement =
                     connection.createStatement()) {

            System.out.println("=== KIEM TRA DATABASE ===");

            try (ResultSet result =
                         statement.executeQuery("PRAGMA integrity_check")) {

                if (result.next()) {
                    System.out.println(
                            "Integrity: " + result.getString(1)
                    );
                }
            }

            System.out.println("\n=== DANH SACH BANG ===");

            try (ResultSet result = statement.executeQuery(
                    "SELECT name FROM sqlite_master " +
                    "WHERE type = 'table' ORDER BY name")) {

                while (result.next()) {
                    System.out.println(result.getString("name"));
                }
            }

            System.out.println("\n=== SO LUONG MAU VAI ===");

            try (ResultSet result = statement.executeQuery(
                    "SELECT COUNT(*) FROM fabric_database")) {

                if (result.next()) {
                    System.out.println(
                            "Fabric count: " + result.getInt(1)
                    );
                }
            }
        }
    }
}
