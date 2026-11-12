Fabric AI Advisor

Fabric AI Advisor là ứng dụng web hỗ trợ tư vấn và lựa chọn chất liệu vải dựa trên nhu cầu người dùng.

Dự án được phát triển bằng Java 21, Spring Boot và SQLite, đồng thời tích hợp mô hình ngôn ngữ Gemma 3 4B chạy cục bộ thông qua Ollama.

1. Công nghệ sử dụng

Java 21

Spring Boot 4.1.1

Maven

SQLite và SQLite JDBC

HTML, CSS, JavaScript

Ollama

Gemma 3 4B

JUnit 5

2. Chức năng hiện tại

2.1. Tư vấn chất liệu dựa trên SQLite

Tìm kiếm chất liệu theo loại trang phục.

Lọc vải theo ngân sách.

Nhận diện một số sở thích về chất liệu.

Chuẩn hóa các sở thích trùng nghĩa.

Phân tích yêu cầu bổ sung dựa trên từ khóa.

Xử lý một số câu yêu cầu phủ định.

Tính điểm và xếp hạng chất liệu.

Giải thích lý do đề xuất.

Hiển thị giá tham khảo, nguồn và trạng thái xác minh dữ liệu.

Chức năng này hiện sử dụng các quy tắc và từ khóa để truy vấn, xếp hạng dữ liệu SQLite.

2.2. Hỏi đáp AI bằng Gemma 3 4B

Người dùng nhập câu hỏi tiếng Việt trên website.

Spring Boot tiếp nhận yêu cầu qua REST API.

FabricAiController chuyển yêu cầu cho OllamaService.

OllamaService gửi yêu cầu đến Ollama.

Gemma 3 4B tạo câu trả lời.

Website hiển thị phản hồi tiếng Việt.

Lưu ý: Chức năng hỏi AI và chức năng tư vấn SQLite hiện hoạt động độc lập.

Gemma chưa trực tiếp truy vấn SQLite để đưa ra các đề xuất chất liệu dựa trên dữ liệu thực tế.

3. Cấu trúc project

Các thành phần chính:

controller/FabricAiController.java: API hỏi AI.

controller/FabricRecommendationController.java: API đề xuất chất liệu.

service/OllamaService.java: Kết nối Ollama và Gemma.

service/FabricRecommendationService.java: Logic đề xuất và xếp hạng chất liệu.

service/FabricPreferenceNormalizer.java: Chuẩn hóa sở thích.

service/FabricRequirementService.java: Xử lý yêu cầu bổ sung.

repository/FabricRepository.java: Truy vấn dữ liệu SQLite.

resources/templates/index.html: Giao diện website.

src/test/java/: Các bài kiểm thử tự động.

4. Cơ sở dữ liệu SQLite

Ứng dụng sử dụng file SQLite có tên fabric.db.

Database hiện có 4 bảng:

fabric_database

fabric_import_14_rows

fabric_sources

training_examples

Bảng fabric_database hiện chứa 14 mẫu vải.

Các thông tin giá, đặc tính và nguồn dữ liệu hiện chưa được xác minh độc lập.

Cấu hình kết nối

Trong src/main/resources/application.properties:

spring.datasource.url=jdbc:sqlite:./fabric.db
spring.datasource.driver-class-name=org.sqlite.JDBC
server.port=8080

Đường dẫn ./fabric.db được tính từ thư mục làm việc khi khởi chạy ứng dụng.

Các file database .db được loại trừ khỏi Git thông qua .gitignore, vì vậy không được tải lên GitHub cùng mã nguồn.

Để chạy ứng dụng trên máy khác, cần cung cấp database riêng và đặt file fabric.db vào đúng thư mục làm việc.

Tài liệu bàn giao database: DATABASE_HANDOVER.md.

5. Hướng dẫn chạy trên Windows

Bước 1 — Chuẩn bị môi trường

Cần có:

Java 21

Mã nguồn Fabric AI Advisor

Database SQLite fabric.db

Ollama và mô hình gemma3:4b nếu muốn sử dụng chức năng hỏi AI

Bước 2 — Chuẩn bị database

Đặt fabric.db trong thư mục gốc project khi chạy ứng dụng từ thư mục này.

Nếu nhận file fabric_for_integration.db, hãy tạo một bản sao có tên fabric.db.

Bước 3 — Chuẩn bị mô hình AI

Cài đặt Ollama, sau đó tải mô hình:

ollama pull gemma3:4b

Đảm bảo Ollama đang chạy và API của Ollama có thể truy cập tại:

http://localhost:11434

Bước 4 — Chạy kiểm thử

Mở PowerShell tại thư mục gốc project:

.\mvnw.cmd test

Tại thời điểm kiểm thử gần nhất, project có:

61 tests

0 failures

0 errors

BUILD SUCCESS

Bước 5 — Chạy Spring Boot

.\mvnw.cmd spring-boot:run

Sau khi Spring Boot khởi động thành công, truy cập:

http://localhost:8080

6. REST API

API đề xuất chất liệu

GET /api/recommendations

API truy vấn và xếp hạng chất liệu từ SQLite dựa trên các tiêu chí được cung cấp.

API hỏi AI

POST /api/ai/ask

Ví dụ request:

{
"question": "Vải cotton và linen khác nhau thế nào?"
}

API chuyển câu hỏi đến mô hình Gemma thông qua Ollama và trả về kết quả cho giao diện.

7. Giới hạn hiện tại

Chức năng đề xuất chất liệu vẫn dựa trên quy tắc và từ khóa.

AI chưa trực tiếp sử dụng dữ liệu SQLite để đề xuất vải.

Chưa triển khai AI Agent có khả năng lựa chọn và sử dụng công cụ truy vấn database.

Chưa tích hợp mô hình đánh giá độc lập để kiểm chứng câu trả lời AI.

Dữ liệu vải chưa được xác minh độc lập.

Chức năng AI cần Ollama và Gemma được cài đặt trên môi trường chạy.

Kết quả tư vấn chỉ mang tính tham khảo.

8. Định hướng phát triển V2

Tích hợp AI để hiểu ngôn ngữ tự nhiên và trích xuất nhu cầu.

Kết nối mô hình AI với dữ liệu SQLite.

Đưa ra đề xuất dựa trên dữ liệu chất liệu thực tế.

Kiểm tra giá, đặc tính và mã vải bằng dữ liệu nguồn.

Xây dựng lớp kết nối AI có thể thay đổi mô hình hoặc inference backend.

Đánh giá llama.cpp như một phương án thay thế Ollama.

Cải thiện hiệu năng, kiểm thử và khả năng triển khai trên máy khác.

9. Trạng thái dự án

V1 — Đã hoàn thành các chức năng nền tảng và kiểm thử.

Các thành phần đã hoạt động:

Website Spring Boot

Database SQLite

Hệ thống tư vấn chất liệu dựa trên quy tắc

API hỏi AI bằng Gemma 3 4B

Giao diện hỏi đáp AI bằng tiếng Việt

V2 — Đang lên kế hoạch tích hợp AI với SQLite theo yêu cầu khách hàng.