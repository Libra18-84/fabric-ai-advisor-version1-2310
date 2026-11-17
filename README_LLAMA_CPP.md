# Fabric AI Advisor: llama.cpp trên Windows

Cần **JDK 21** để build/chạy backend. Cấu hình đã thử: Windows, RAM 16 GB, NVIDIA RTX 3050 6 GB. Mặc định Gemma dùng GPU (`MainGpuLayers=99`), Qwen dùng CPU (`EvaluationGpuLayers=0`). Hiệu năng tùy phần cứng và tác vụ đang chạy; bản demo chưa đạt mục tiêu p95 dưới 5 giây.

## 1. Chuẩn bị source và catalog mẫu

Clone repository, mở PowerShell tại thư mục có `pom.xml`. Maven Wrapper đi kèm; lần chạy đầu cần Internet để tải Maven/thư viện còn thiếu.

Database làm việc bị loại khỏi Git. Khôi phục bản demo **trước khi test hoặc chạy ứng dụng**:

```powershell
if (!(Test-Path -LiteralPath ".\fabric.db")) {
    Expand-Archive -LiteralPath ".\fabric_database_handover.zip" -DestinationPath ".\target\demo-catalog" -Force
    Copy-Item -LiteralPath ".\target\demo-catalog\fabric_for_integration.db" -Destination ".\fabric.db"
}
.\mvnw.cmd test
```

`fabric_database_handover.zip` đi kèm repository chứa `fabric_for_integration.db` mẫu 14 bản ghi chưa xác minh và tài liệu bàn giao. Đoạn lệnh giữ nguyên `fabric.db` đã tồn tại. Nếu dùng database riêng, xem [DATABASE_HANDOVER.md](DATABASE_HANDOVER.md) và đảm bảo schema tương thích.

Kết quả kiểm thử mã hiện tại dự kiến **360 test, 0 failure, 0 error, 0 skipped**. Bộ này dùng model giả lập khi cần; chưa cần cài model để chạy bước test mã.

## 2. Cài llama.cpp và GGUF

Mặc định các script dùng thư mục `llama.cpp` bên trong thư mục tài khoản Windows. Tạo hai thư mục bằng PowerShell:

```powershell
New-Item -ItemType Directory -Force -Path "$env:USERPROFILE\llama.cpp\bin", "$env:USERPROFILE\llama.cpp\models" | Out-Null
```

Với máy NVIDIA/CUDA, tải và giải nén **nội dung cả hai ZIP** vào cùng `%USERPROFILE%\llama.cpp\bin`:

- https://github.com/ggml-org/llama.cpp/releases/download/b12444/llama-b12444-bin-win-cuda-12.4-x64.zip
- https://github.com/ggml-org/llama.cpp/releases/download/b12444/cudart-llama-bin-win-cuda-12.4-x64.zip

`llama-server.exe` và các DLL cần nằm ngay trong `bin`, gồm ggml-cuda, cublas, cublasLt và cudart. Dừng ứng dụng và các model trước khi thay chương trình/DLL.

Tải hai GGUF vào `%USERPROFILE%\llama.cpp\models`, giữ nguyên tên file:

- Gemma 3 4B: https://huggingface.co/ggml-org/gemma-3-4b-it-GGUF/resolve/main/gemma-3-4b-it-Q4_K_M.gguf?download=true
- Qwen 2.5 7B, revision đã đối chiếu: https://huggingface.co/bartowski/Qwen2.5-7B-Instruct-GGUF/resolve/8911e8a47f92bac19d6f5c64a2e2095bd2f7d031/Qwen2.5-7B-Instruct-Q4_K_M.gguf?download=true

SHA-256 Qwen đã kiểm tra: `65b8fcd92af6b4fefa935c625d1ac27ea29dcb6ee14589c55a8f115ceaaa1423`.

Trọng số không nằm trong Git. Script tự tạo thư mục log. Có thể dùng `-LlamaDirectory` khi chạy các script model nếu muốn đặt llama.cpp ở vị trí khác.

Kiểm tra GPU:

```powershell
nvidia-smi
& "$env:USERPROFILE\llama.cpp\bin\llama-server.exe" --list-devices
```

Cần thấy thiết bị NVIDIA khi chọn nhánh CUDA. Nếu hiện `(none)`, kiểm tra driver và DLL. Nếu thiếu VCRUNTIME/MSVCP, cài Microsoft Visual C++ Redistributable x64: https://aka.ms/vs/17/release/vc_redist.x64.exe.

## 3. Chạy ứng dụng

Tại PowerShell trong thư mục project, bật hai model:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\BAT_MODEL_LLAMA.ps1
```

Chờ cả hai báo `Ready` rồi chạy backend:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\CHAY_APP_LLAMA.ps1
```

Các lệnh đặt execution policy cho tiến trình được gọi, không thay chính sách toàn máy.

Sau dòng `Tomcat started on port 8080`, mở **http://localhost:8080**. Giữ cửa sổ backend mở. Không khởi động thêm cùng ứng dụng bằng IntelliJ trên cổng 8080 khi tiến trình này còn chạy.

| Vai trò | Địa chỉ | Alias |
|---|---|---|
| Model chính Gemma | http://127.0.0.1:8081 | gemma-main |
| Model kiểm chứng Qwen | http://127.0.0.1:8082 | qwen-eval |

Mỗi server có một slot, context 8192. `CHAY_APP_LLAMA.ps1` dùng `ai-models.json` cùng thư mục script. Nếu `DATABASE_URL` chưa được đặt, script dùng `fabric.db` trong project; nếu đã đặt thì giữ nguyên.

Có thể kiểm tra `GET /api/models/status`: cấu hình mẫu có revision `llamacpp-v1`, mainModel `gemma-main`, evaluationModel `qwen-eval`. Alias/cấu hình không tự chứng minh chất lượng model hoặc danh tính trọng số bất biến.

## 4. Đọc log và dừng

Mở PowerShell khác để đọc log:

```powershell
Get-Content "$env:USERPROFILE\llama.cpp\logs\gemma-main.err.log" -Tail 25
Get-Content "$env:USERPROFILE\llama.cpp\logs\qwen-eval.err.log" -Tail 25
```

`err.log` có cả thông tin, không chỉ lỗi. Mỗi `total time` của server là một lần gọi model, chưa phải toàn bộ thời gian câu hỏi qua HTTP.

Để dừng backend, nhấn **Ctrl+C** trong cửa sổ đang chạy app; nhập **Y** nếu được hỏi `Terminate batch job`. Khi dòng `PS ...>` trở lại, có thể dừng các model nền bằng:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\DUNG_MODEL_LLAMA.ps1
```

Chỉ đóng cửa sổ PowerShell chưa chắc đã dừng model nền. Đổi GGUF/GPU/context cần dừng model rồi bật lại: script sẽ dùng lại server đang `Ready`. Sửa Java hoặc prompt cần khởi động lại backend.

## 5. Chạy hoàn toàn bằng CPU

Với nhánh CPU, dùng gói https://github.com/ggml-org/llama.cpp/releases/download/b12444/llama-b12444-bin-win-cpu-x64.zip. Đặt EXE/DLL vào `bin`, giữ GGUF trong `models`. Dừng server cũ trước khi đổi gói hoặc tham số, rồi chạy:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\BAT_MODEL_LLAMA.ps1 -MainGpuLayers 0 -EvaluationGpuLayers 0
```

Sau `Ready`, chạy `CHAY_APP_LLAMA.ps1` như trên. Tham số 0 ghi đè mặc định GPU 99. Chạy CPU có thể chậm hơn; không coi cấu hình này là bảo đảm thời gian phản hồi.

## Giới hạn đã biết

360 test xác nhận kiểm thử mã, không phải 360 câu đúng của model thật. Một lượt tư vấn thật đã đạt warm-up và 12/12 ca trước bản vá Java hẹp gần nhất; giao diện đã kiểm tra hai ca cơ bản và ngưỡng giá sau bản vá.

Qwen kiểm chứng độc lập vẫn có lỗi: một lượt prompt đang giữ khớp 8/11 nhãn đề xuất; thử nghiệm context khác chỉ khớp 11/24 nên bị loại. Không coi câu trả lời đã đối chiếu catalog là dữ liệu đã xác minh ngoài thực tế.

Bốn mẫu luồng tư vấn thành công đã đo khoảng 9,7–14,5 giây phía HTTP client trong một lượt nhỏ, không gồm render trình duyệt. Chưa chứng minh p95 dưới 5 giây hoặc độ chính xác thực tế trên 95%. Catalog hiện có 14 mẫu `unverified`.

Xem [README.md](README.md) cho luồng API, cách đánh giá native tùy chọn và phạm vi các kết quả. Giao thức llama-server: https://github.com/ggml-org/llama.cpp/blob/master/tools/server/README.md.
