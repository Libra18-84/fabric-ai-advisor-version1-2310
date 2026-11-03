
# Fabric AI Advisor

Fabric AI Advisor là ứng dụng web hỗ trợ tư vấn và lựa chọn chất liệu vải dựa trên nhu cầu của người dùng.

Dự án được phát triển bằng Java và Spring Boot, sử dụng cơ sở dữ liệu SQLite.

## 1. Công nghệ sử dụng

- Java 21
- Spring Boot
- Maven
- SQLite
- HTML, CSS, JavaScript
- JUnit 5
- IntelliJ IDEA

## 2. Chức năng hiện tại

- Tìm kiếm chất liệu vải theo loại trang phục.
- Lọc mẫu vải theo ngân sách.
- Nhận diện một số sở thích về chất liệu.
- Phân tích yêu cầu bổ sung bằng quy tắc từ khóa.
- Xử lý một số trường hợp phủ định.
- Chuẩn hóa và loại bỏ sở thích trùng nghĩa.
- Tính điểm và xếp hạng chất liệu vải.
- Giải thích lý do đề xuất cho từng mẫu vải.
- Hiển thị nguồn tham khảo và trạng thái xác minh dữ liệu.
- Cung cấp REST API phục vụ giao diện website.

## 3. Cấu trúc project

- `controller/`: Xử lý HTTP request và REST API.
- `service/`: Xử lý logic tư vấn, yêu cầu và chuẩn hóa sở thích.
- `repository/`: Truy xuất dữ liệu SQLite.
- `templates/index.html`: Giao diện website.
- `src/test/java/`: Các bài kiểm thử tự động.

## 4. Kiểm thử

Project sử dụng Maven và JUnit để kiểm thử các Service và API.

Trên Windows, chạy:

```powershell
.\mvnw.cmd test
```

## 5. Cơ sở dữ liệu

Ứng dụng sử dụng SQLite.

Các file database cục bộ không được đưa vào repository GitHub.

Để chạy dự án trên máy khác, cần chuẩn bị database SQLite phù hợp và cập nhật đường dẫn kết nối trong `application.properties`.

## 6. Giới hạn hiện tại

- Hệ thống tư vấn dựa trên quy tắc và từ khóa.
- Chưa tích hợp mô hình AI Gemma.
- Chỉ hỗ trợ một số nhóm sở thích.
- Dữ liệu chất liệu vải chưa được xác minh độc lập.
- Kết quả tư vấn chỉ mang tính tham khảo.

## 7. Định hướng phát triển

- Tích hợp Gemma thông qua Ollama.
- Nâng cao khả năng hiểu yêu cầu tiếng Việt.
- Cải thiện độ chính xác của hệ thống tư vấn.
- Bổ sung dữ liệu vải có nguồn xác minh.
- Hoàn thiện giao diện và trải nghiệm người dùng.

## 8. Trạng thái dự án

Đang phát triển.
