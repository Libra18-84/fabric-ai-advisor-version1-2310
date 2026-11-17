# Fabric AI Advisor

Ứng dụng Java 21 / Spring Boot 4.1.1, SQLite và llama.cpp. Adapter Ollama vẫn được giữ để tương thích khi chọn provider=ollama.

## Trạng thái dự án

Bản demo đang phát triển, dùng catalog mẫu. Chưa phải bản production hoặc cam kết dữ liệu chính xác ngoài thực tế.

| Hạng mục | Kết quả đã ghi nhận |
|---|---|
| Kiểm thử mã | 360 test, 0 failure, 0 error, 0 skipped; model được giả lập khi cần |
| Tư vấn với model thật | Một lượt `advice_only` đạt warm-up và 12/12 ca API trước bản vá Java hẹp cho khẳng định không nhăn |
| Giao diện sau bản vá | Sơ mi không quá 200.000 VND/m trả FAB001/FAB005/FAB006; dưới 90.000 VND/m chỉ FAB005 |
| Kiểm chứng Qwen độc lập | Chưa đạt mục tiêu: một lượt prompt đang giữ khớp 8/11 nhãn đề xuất; phương án context thử khác chỉ khớp 11/24 và đã bị loại |
| Thời gian phản hồi | Bốn mẫu luồng tư vấn đầy đủ đã ghi nhận khoảng 9,7–14,5 giây phía HTTP client; chưa chứng minh p95 dưới 5 giây |

360 test mã không có nghĩa 360 câu hỏi được model thật trả lời đúng. Các bộ nhỏ và nhãn đề xuất không chứng minh độ chính xác thực tế trên 95% hay mức hài lòng trên 80%.

## Luồng tư vấn đang chạy

`POST /api/advise` và `POST /api/ai/ask` cùng gọi `FabricAdviceService`:

1. Model chính trích xuất điều kiện từ câu hỏi và ngữ cảnh tùy chọn.
2. Backend đối chiếu các phủ định nhận diện được với câu gốc. Điều kiện đối lập hoặc chưa hiểu được được yêu cầu làm rõ.
3. `FabricRepository` thực thi SQL có tham số để lấy ứng viên từ SQLite. Model không tự tạo hoặc thực thi SQL.
4. Backend đọc bằng chứng trong mô tả, lọc điều kiện bắt buộc và chấm điểm sở thích.
5. Model chính chọn một hoặc nhiều ID và viết lý do cho từng vải. Backend dựng phần tóm tắt chung theo catalog, thay cho phần tóm tắt tự do của model.
6. Backend kiểm tra ID, giá, trang phục, điều kiện bắt buộc, thành phần, URL, trạng thái xác minh và các khẳng định đặc tính nhận diện được; evaluator tiếp tục đối chiếu dự thảo với nguồn.
7. Nếu bị từ chối, model chính được sửa tối đa một lần. Chỉ trả `factCheck.validated=true` khi kiểm tra bắt buộc đã đạt.

Tên, khoảng giá, nguồn và trạng thái xác minh trong response do backend lấy từ SQL. Cập nhật catalog không đòi hỏi train lại model. Bảng `training_examples` hiện không được dùng để trả lời chat.

Form `/api/recommendations` cung cấp tư vấn bằng quy tắc, có cùng bộ tìm kiếm, chuẩn hóa và kiểm tra bằng chứng với luồng AI. Form này không gọi model.

## Chạy trên Windows

Cần **JDK 21**. Maven Wrapper (`mvnw`, `mvnw.cmd`, `.mvn/wrapper/maven-wrapper.properties`) đi kèm repository; lần chạy đầu cần Internet để tải Maven và các thư viện còn thiếu.

### 1. Khôi phục catalog mẫu và chạy kiểm thử

Sau khi clone, mở PowerShell tại thư mục chứa `pom.xml`, chạy:

```powershell
if (!(Test-Path -LiteralPath ".\fabric.db")) {
    Expand-Archive -LiteralPath ".\fabric_database_handover.zip" -DestinationPath ".\target\demo-catalog" -Force
    Copy-Item -LiteralPath ".\target\demo-catalog\fabric_for_integration.db" -Destination ".\fabric.db"
}
.\mvnw.cmd test
```

Database làm việc `fabric.db` bị `.gitignore` loại khỏi Git. Gói `fabric_database_handover.zip` được chủ động giữ trong repository: chứa `fabric_for_integration.db` mẫu 14 bản ghi và tài liệu bàn giao. Đoạn lệnh trên chỉ tạo `fabric.db` khi chưa có; database đang tồn tại được giữ nguyên.

Các test Spring Boot dùng database mặc định cần bước chuẩn bị này. Nếu đang dùng catalog riêng hoặc `DATABASE_URL` khác, dùng một checkout thử với catalog mẫu để kiểm thử; không thay dữ liệu đang dùng chỉ để đạt test. Kiểm thử mã thông thường không cần tải GGUF hoặc khởi động model.

### 2. Cài và chạy model

Làm theo [README_LLAMA_CPP.md](README_LLAMA_CPP.md) để cài llama-server và hai GGUF. Mặc định model nằm trong `%USERPROFILE%\llama.cpp\models`, ngoài thư mục source. Trọng số và chương trình llama.cpp không nằm trong Git.

Sau khi đã cài, chạy tại thư mục project:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\BAT_MODEL_LLAMA.ps1
```

Chờ cả hai model báo `Ready`, rồi chạy:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\CHAY_APP_LLAMA.ps1
```

`ExecutionPolicy Bypass` ở đây chỉ áp dụng cho tiến trình PowerShell được gọi, không đổi chính sách toàn máy.

Gemma phục vụ tại `127.0.0.1:8081`, alias `gemma-main`; Qwen tại `127.0.0.1:8082`, alias `qwen-eval`. Mỗi server có một slot và context 8192. Mặc định Gemma dùng GPU (`MainGpuLayers=99`), Qwen dùng CPU (`EvaluationGpuLayers=0`). Nhánh mặc định cần bản llama.cpp CUDA và GPU NVIDIA phù hợp. Có hướng dẫn chạy hoàn toàn CPU trong README_LLAMA_CPP.

`CHAY_APP_LLAMA.ps1` chọn provider llama.cpp, nạp `ai-models.json` cùng thư mục script và chạy Spring Boot trên cổng 8080. Nếu đã đặt `DATABASE_URL`, script giữ nguyên giá trị đó; nếu chưa có, script dùng đường dẫn tuyệt đối tới `fabric.db` trong project.

Giữ cửa sổ Spring Boot mở và truy cập **http://localhost:8080**. Thử:

> Tôi cần vải may áo sơ mi, không quá 200.000 đồng/m.

Với catalog mẫu, ba ứng viên theo bộ lọc là FAB001, FAB005 và FAB006; model có thể chọn một tập con hợp lệ theo hợp đồng API. Câu “dưới 90.000 đồng/m” chỉ còn FAB005 ở bộ lọc.

Nhấn **Ctrl+C**, nhập **Y** nếu được hỏi `Terminate batch job`, để dừng ứng dụng. Hai model chạy nền; muốn dừng chúng, dùng:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\DUNG_MODEL_LLAMA.ps1
```

Khi dùng IntelliJ Run thay cho script, thiết lập biến môi trường trong đúng Run Configuration. Biến đặt ở một cửa sổ PowerShell không tự áp dụng cho tiến trình mở từ IntelliJ. Nếu console sai dấu tiếng Việt, kiểm tra UTF-8; lỗi hiển thị console không tự chứng minh dữ liệu HTTP/SQLite bị hỏng.

## Registry và đổi model khi đang chạy

`AiModelClient` là interface. `LlamaCppModelClient` là adapter mặc định, `OllamaService` là adapter tương thích; `ActiveMainModel` là bean được inject vào các service chính. `AiModelRegistry` đọc file ngoài JAR được chọn bằng `AI_MODELS_FILE`.

`ai-models.json` nằm cùng cấp `pom.xml`. `active.main` và `active.evaluation` chọn ID cấu hình trong `models`. Mẫu đi kèm dùng provider=llamacpp, Gemma 3 4B và Qwen 2.5 7B qua hai llama-server. Trường model là alias của server, không phải đường dẫn GGUF hoặc tag Ollama. Có thể chọn model/endpoint đã được phục vụ bằng cấu hình, không sửa code.
