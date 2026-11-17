# Bàn giao SQLite — Fabric AI Advisor

## Dữ liệu bàn giao

| File | Số vải |
|---|---:|
| fabric.db | 14 |
| fabric_for_integration.db | 14 |
| fabric_backup.db.db | 0 |

Hai file đầu có cùng nội dung trong bản bàn giao; `PRAGMA integrity_check` trả `ok`. Số liệu có thể thay đổi khi cập nhật catalog. File backup trống không phải bản có 14 mẫu.

Các bảng gồm `fabric_database`, `fabric_import_14_rows`, `fabric_sources`, `training_examples`. Hiện `fabric_sources` và `training_examples` trống; 14 bản ghi vải có `verification_status=unverified`. Có URL tham khảo nhưng chưa có liên kết nguồn chi tiết qua `source_id`.

Thông số số kỹ thuật như GSM, khổ, co rút, độ giãn hiện chưa có dữ liệu số đầy đủ; ứng dụng chủ yếu sử dụng mô tả và khoảng giá. Không tự điền thông số hay đổi trạng thái xác minh để làm kết quả trông đầy đủ.

## Ứng dụng dùng database như thế nào

Cả `POST /api/advise` và `POST /api/ai/ask` dùng `FabricAdviceService`: trích xuất điều kiện → SQL → lọc điều kiện bắt buộc/chấm điểm → giải thích các ID được chọn → kiểm tra bằng code và evaluator.

`FabricRepository` thực thi SQL có tham số. Bản ghi SQL được đưa vào prompt và backend gắn dữ liệu sản phẩm vào response. Model không tự mở file SQLite hoặc tự chạy SQL. Sửa dữ liệu catalog không cần train lại model; `training_examples` hiện không phục vụ chat.

Form quy tắc và luồng AI dùng chung chuẩn hóa tìm kiếm. “Chưa xác minh độ mềm” là thiếu bằng chứng, không phải bằng chứng “mềm”; “ít co giãn” không chứng minh “không co giãn”. Vải chưa xác nhận điều kiện bắt buộc chỉ xuất hiện trong danh sách ứng viên chưa xác nhận.

## Kết nối trên máy nhận

SQLite là file trên máy chạy backend. Client khác truy cập qua HTTP API. Không có giao diện liên kết database bất kỳ; file nhận bàn giao phải có schema tương thích.

Đặt database ở vị trí tài khoản Java có quyền truy cập. Trong PowerShell tại thư mục project:

```powershell
$fabricDbPath = (Resolve-Path ".\fabric_for_integration.db").Path.Replace('\', '/')
$env:DATABASE_URL = "jdbc:sqlite:$fabricDbPath"
$env:AI_MODELS_FILE = (Resolve-Path ".\ai-models.json").Path
.\CHAY_APP_LLAMA.ps1
```

Hai llama-server phải được cài/chạy theo README_LLAMA_CPP.md. Không cần dịch vụ Ollama khi dùng provider=llamacpp. Nếu chạy bằng IntelliJ Run, thiết lập biến môi trường trong đúng Run Configuration. `.gitignore` bỏ qua `*.db`, nên bản nhận qua Git chưa chắc có database.

## Kiểm tra sau bàn giao

- Ứng dụng đọc `fabric_database` khi khởi động và in số bản ghi. Thiếu bảng hoặc không đọc được thì dừng; bảng trống thì cảnh báo.
- `GET /api/fabrics`: bản bàn giao này dự kiến `total=14`.
- `GET /api/models/status`: kiểm tra `revision` và hai vai trò model. `environment` nghĩa là đang dùng cấu hình mặc định, không chứng minh đã nạp file JSON.
- Thử `POST /api/advise` theo README. Truy vấn SQLite thành công chưa chứng minh model tạo/kiểm chứng câu trả lời thành công.

Có thể đọc bằng công cụ SQLite:

```sql
PRAGMA integrity_check;
SELECT COUNT(*) FROM fabric_database;
SELECT fabric_id, fabric_name, verification_status FROM fabric_database ORDER BY fabric_id;
```

Khi DB lỗi sau khởi động, các API đọc DB trả HTTP 503 cùng thông báo `DATABASE_UNAVAILABLE`. Nếu model kiểm chứng không khả dụng, `validated` không được true. Đối chiếu database không xác minh dữ liệu ngoài thực tế.

## Đồng bộ gói bàn giao

`fabric_database_handover.zip` phải chứa chính `fabric_for_integration.db` và tài liệu này. Khi sửa tài liệu hoặc dữ liệu bàn giao, tạo lại gói bằng PowerShell tại thư mục project:

```powershell
Compress-Archive -Path .\fabric_for_integration.db, .\DATABASE_HANDOVER.md -DestinationPath .\fabric_database_handover.zip -Force
```

Các phần còn chưa hoàn thành của kế hoạch, hợp đồng API và giới hạn kiểm thử được ghi trong README.
