# Fabric AI Advisor

Ứng dụng Java 21 / Spring Boot 4.1.1, SQLite và llama.cpp. Adapter Ollama vẫn được giữ để tương thích khi chọn provider=ollama.

## Trạng thái dự án

Bản demo đang phát triển, dùng catalog mẫu. Kết quả dưới đây ghi nhận ngày **19/11/2026**. Chưa có cơ sở kết luận bộ kiểm chứng AI đáng tin cậy trong thực tế.

| Hạng mục | Kết quả đã ghi nhận |
|---|---|
| Kiểm thử mã | 370 test, 0 failure, 0 error, 0 skipped trên project chính; model được giả lập khi cần |
| Form lọc và xếp hạng | Đã kiểm tra giá, sở thích và điều kiện chưa đủ bằng chứng; ca hủy sở thích cho cả ba vải 0 điểm sau bản sửa |
| Tư vấn với model thật | Bộ live được lưu trong phiên có warm-up và 12/12 ca hợp đồng API đạt; không phải đánh giá độ chính xác chung của model |
| Đầu vào API không hợp lệ | 4/4 ca thực tế trả HTTP 400: câu hỏi rỗng, thiếu câu hỏi, sai kiểu dữ liệu, dài 1001 ký tự |
| Mất kết nối và khôi phục | Ngắt Qwen trả fallback chưa được kiểm chứng; bật lại Qwen, API tư vấn phục hồi. Ngắt Gemma trả hướng dẫn dùng form, không đề xuất vải. Đã bật lại và xác nhận cả hai model healthy |
| Kiểm chứng AI độc lập | Chưa đạt: Qwen có cả bỏ lọt dự thảo sai và từ chối dự thảo đúng. Phương án nêu lỗi trước kết luận chỉ khớp 2/4 ca; Gemma thử cùng vai trò cũng khớp 2/4, không được áp dụng |
| Thời gian phản hồi | Ca API phục hồi Qwen đo khoảng 16,8 giây ở lớp dịch vụ. Chưa chứng minh đáp ứng p95 dưới 5 giây |
| Tiếng Việt và dữ liệu HTTP | Phản hồi catalog UTF-8 đúng, 14 mẫu; 182 trường khớp database đã rà soát |

370 test mã không có nghĩa 370 câu hỏi được model thật trả lời đúng. Khớp nhãn trong bộ thử nhỏ không phải độ chính xác ngoài thực tế; lý do kiểm chứng vẫn phải được đối chiếu với nguồn. Không có bằng chứng cho tuyên bố độ chính xác trên 95% hoặc mức hài lòng trên 80%.

`factCheck.validated=true` thể hiện rằng các quy tắc đã qua và model kiểm chứng trả chấp nhận cho dự thảo đó. Nó không tự chứng minh tư vấn đúng ngoài thực tế hoặc model kiểm chứng đủ tin cậy. Dữ liệu catalog vẫn giữ `verification_status=unverified`.

Xem phạm vi, các ca đạt và các giới hạn còn tồn tại trong [báo cáo kiểm thử ngày 19/11/2026](TEST_REPORT_2026-11-19.md).

## Luồng tư vấn đang chạy

`POST /api/advise` và `POST /api/ai/ask` cùng gọi `FabricAdviceService`:

1. Model chính trích xuất điều kiện từ câu hỏi và ngữ cảnh tùy chọn.
2. Backend đối chiếu các phủ định nhận diện được với câu gốc. Điều kiện đối lập hoặc chưa hiểu được được yêu cầu làm rõ.
3. `FabricRepository` thực thi SQL có tham số để lấy ứng viên từ SQLite. Model không tự tạo hoặc thực thi SQL.
4. Backend đọc bằng chứng trong mô tả, lọc điều kiện bắt buộc và chấm điểm sở thích.
5. Model chính chọn một hoặc nhiều ID và viết lý do cho từng vải. Backend dựng phần tóm tắt chung theo catalog, thay cho phần tóm tắt tự do của model.
6. Backend kiểm tra ID, giá, trang phục, điều kiện bắt buộc, thành phần, URL, trạng thái xác minh và các khẳng định đặc tính nhận diện được; evaluator tiếp tục đối chiếu dự thảo với nguồn.
7. Nếu bị từ chối, model chính được sửa tối đa một lần. `factCheck.validated=true` chỉ được trả khi quy tắc đã qua và evaluator trả chấp nhận; độ tin cậy của evaluator còn là giới hạn đã ghi ở trên.

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

### Chế độ trả lời từ catalog

Mặc định, ứng dụng chạy luồng đầy đủ với model kiểm chứng. Để thử chế độ catalog, mở PowerShell tại thư mục dự án sau khi các model local đã sẵn sàng, rồi chạy:

~~~powershell
$env:FABRIC_ADVICE_CATALOG_ONLY = 'true'
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\CHAY_APP_LLAMA.ps1
~~~

Gemma đọc yêu cầu tiếng Việt. Java lọc, xếp hạng và dựng kết quả từ SQLite. API dùng trạng thái fallback và evaluationStatus=not_run khi trả đề xuất từ catalog. Dữ liệu mẫu vẫn giữ trạng thái chưa xác minh độc lập. Đặt biến trên thành false và khởi động lại backend để chọn luồng mặc định.

Trên Windows, ba lượt đối chiếu cùng câu hỏi về sơ mi giá không quá 200.000 VND/m cho cùng FAB001, FAB005, FAB006. Thời gian xử lý trung vị giảm từ 10,30 xuống 2,00 giây. Đây là phép đo của một câu hỏi. Xem chi tiết trong báo cáo kiểm thử.

## Registry và đổi model khi đang chạy

`AiModelClient` là interface. `LlamaCppModelClient` là adapter mặc định, `OllamaService` là adapter tương thích; `ActiveMainModel` là bean được inject vào các service chính. `AiModelRegistry` đọc file ngoài JAR được chọn bằng `AI_MODELS_FILE`.

`ai-models.json` nằm cùng cấp `pom.xml`. `active.main` và `active.evaluation` chọn ID cấu hình trong `models`. Mẫu đi kèm dùng provider=llamacpp, Gemma 3 4B và Qwen 2.5 7B qua hai llama-server. Trường model là alias của server, không phải đường dẫn GGUF hoặc tag Ollama. Có thể chọn model/endpoint đã được phục vụ bằng cấu hình, không sửa code.

## Kiểm thử

Chạy kiểm thử mã thông thường sau khi chuẩn bị catalog theo hướng dẫn trên:

```powershell
.\mvnw.cmd test
```

Để chạy kiểm thử với model thật, khởi động cả hai llama-server, giữ cấu hình đã rà soát và dừng backend demo để tránh tranh tài nguyên. Bộ test tự khởi động một backend trên cổng ngẫu nhiên:

```powershell
.\mvnw.cmd "-Dtest=LiveAiEvaluationIT" "-Dlive.eval=true" test
```

Báo cáo được ghi vào `target/live-evaluation/<thư-mục-lượt-chạy>/report.json`. Bộ test gồm ca hợp đồng API và ca đánh giá độc lập của model kiểm chứng. Với cấu hình đã thử trong báo cáo hiện tại, phần kiểm chứng độc lập chưa đạt nên lệnh này có thể kết thúc bằng `BUILD FAILURE` dù các ca API thông thường đạt. Giữ nguyên nhãn và báo cáo thất bại để phân tích.

Không dùng kết quả từ các quy tắc Java thay cho kết quả model trong thước đo kiểm chứng độc lập. Một ca API `success` hoặc endpoint `/health` trả `ok` không xác nhận model đánh giá đúng các dự thảo sai.

## Đọc phản hồi UTF-8 trong Windows PowerShell

Trong môi trường Windows PowerShell đã thử, việc parse phản hồi trực tiếp bằng `Invoke-RestMethod` có thể làm sai dấu tiếng Việt khi phản hồi không được giải mã đúng. Khi kiểm tra API, lưu phản hồi HTTP gốc rồi đọc file với UTF-8:

```powershell
Invoke-WebRequest -UseBasicParsing -Uri "http://localhost:8080/api/fabrics" -OutFile ".\target\fabrics-http-raw.json"
$catalog = Get-Content -LiteralPath ".\target\fabrics-http-raw.json" -Raw -Encoding UTF8 | ConvertFrom-Json
$catalog.total
```

Cách này giữ nguyên dữ liệu HTTP để đối chiếu. Các file đã bị giải mã sai rồi ghi lại cần lấy lại từ phản hồi gốc; chỉ đổi encoding lúc đọc file cũ không tự khôi phục dữ liệu.
