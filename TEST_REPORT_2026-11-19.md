# Báo cáo kiểm thử Fabric AI Advisor — 19/11/2026

Phạm vi là phiên bản làm việc trên Windows sau bản sửa xếp hạng form, với Java 21, Spring Boot 4.1.1, SQLite mẫu 14 bản ghi, llama.cpp b12444, Gemma 3 4B và Qwen 2.5 7B. Các kết quả mới của bản sửa form chưa được gắn với một commit mới tại thời điểm ghi nhận.

## Môi trường và cấu hình

- Backend: cổng 8080.
- Model chính: `gemma-main`, cổng 8081; Gemma dùng GPU trong cấu hình đã thử.
- Model kiểm chứng: `qwen-eval`, cổng 8082; Qwen dùng CPU trong cấu hình đã thử.
- Hai vai trò giữ nguyên. Các phương án thử prompt/schema và dùng Gemma làm evaluator không được áp dụng vào mã ứng dụng.
- Cả hai model đã được bật lại và xác nhận `/health` trả `ok` sau thử nghiệm mất kết nối.

## Các kết quả đạt trong phạm vi đã thử

| Nhóm | Kết quả |
|---|---|
| Kiểm thử mã sau bản sửa form | 366 test, 0 failure, 0 error, 0 skipped trên máy Windows |
| Form sơ mi, trần 200.000 VND/m, không có sở thích | FAB001/FAB005/FAB006, đều 0 điểm |
| Form sơ mi, trần 90.000 VND/m | FAB001/FAB005 |
| Form ưu tiên thoáng mát | FAB001/FAB006 có bằng chứng hỗ trợ; FAB005 chưa đủ thông tin |
| Hủy sở thích bằng yêu cầu bổ sung | Sau bản sửa, sở thích bị hủy không còn làm tăng điểm; cả ba mẫu 0 điểm ở ca thử |
| Thuộc tính bắt buộc chưa có bằng chứng | Hiển thị ứng viên chưa được xác nhận và nêu thiếu thông tin |
| Ngân sách không có vải phù hợp | Trả không có kết quả |
| API sơ mi dưới 90.000 VND/m | Chỉ FAB005 trong bộ lọc vì giới hạn giá là loại trừ |
| Bộ live API được lưu trong phiên | Warm-up và 12/12 ca hợp đồng API đạt; phân biệt rõ với đánh giá model độc lập |
| Đầu vào sai qua HTTP | Query rỗng, thiếu query, query số và dài 1001 ký tự đều trả HTTP 400 |
| Ngắt Qwen, giữ Gemma | `fallback`; `rulesPassed=true`; `evaluationStatus=unavailable`; `validated=false`; trả ba ứng viên từ catalog và lý do quy tắc |
| Bật lại Qwen | API tư vấn khôi phục mà không khởi động lại backend; một yêu cầu đúng trả `success`, evaluator `passed` và ba vải đúng bộ lọc |
| Ngắt Gemma, giữ Qwen | `needs_clarification`; `criteria=null`; không đề xuất vải; evaluator `not_run`; `validated=false`; không tạo giải thích AI |
| Bật lại Gemma | Startup báo sẵn sàng; health hiện tại của cả Gemma và Qwen đều `ok` |
| Catalog và UTF-8 | GET `/api/fabrics` có đủ 14 bản ghi tiếng Việt đúng; 182 trường khớp database đã rà soát |

Ca Qwen mất kết nối mất khoảng 10,26 giây trong lớp dịch vụ. Ca tư vấn sau khi bật lại Qwen mất khoảng 16,80 giây. Ca Gemma mất kết nối được xử lý khoảng 2,57 ms. Đây là thời gian từng ca, không phải ước lượng p95 thực tế.

## Chế độ catalog trên Windows

- Project chính sau khi tích hợp chế độ tùy chọn đạt 370 test, 0 failure, 0 error, 0 skipped.
- Project chính ở chế độ mặc định trả success, evaluator passed và FAB001/FAB005/FAB006 ở ca sơ mi không quá 200.000 VND/m.
- Project chính ở chế độ catalog trả fallback, evaluator not_run và cùng ba mẫu. Ca dưới 90.000 VND/m chỉ trả FAB005. Ca bắt buộc mềm trả insufficient_data với 3 ứng viên tham khảo.
- Ba lượt đo trên project gốc 8080 và bản thử catalog 8085, cùng query, ghi trung vị lần lượt 10.301,227 ms và 1.997,017 ms. Mức giảm 80,6% áp dụng cho câu hỏi đã đo. Các phép thử kiểm chứng độc lập và giới hạn catalog ở dưới vẫn còn hiệu lực.

## Bộ kiểm chứng độc lập chưa đạt

Các nhãn của bộ kiểm tra được đối chiếu thủ công với catalog trong phiên. Kết quả vẫn chỉ phản ánh các ca cố định đã thử.

| Điều kiện thử | Khớp nhãn | Điểm cần lưu ý |
|---|---:|---|
| Qwen, phần độc lập trong lượt live ban đầu của ngày 19/11 | 4/11 | Chấp nhận cả 7 dự thảo được gài lỗi |
| Qwen, prompt ngắn và nguồn catalog gọn | 6/11 | Từ chối cả 4 dự thảo hợp lệ, chấp nhận một ca nói ngược cảnh báo Linen; có lý do sai nguồn |
| Qwen, schema nêu lỗi trước kết luận | 2/4 | Từ chối cả hai dự thảo hợp lệ; một ca sai được từ chối nhưng diễn giải sai nguồn |
| Gemma làm ứng viên evaluator, giữ nguyên cùng prompt và dữ liệu của 4 ca trên | 2/4 | Từ chối cả bốn; có nhận định sai như 160.000 lớn hơn 200.000 |

Ví dụ lỗi quan sát được:

- Nguồn có giá 45.000–90.000 VND/m, nhưng model cho rằng không đáp ứng trần 200.000 VND/m.
- Nguồn liệt kê áo sơ mi trong các công dụng, nhưng model coi tư vấn may áo sơ mi là mâu thuẫn.
- Có ca không phát hiện lời tư vấn nói ngược cảnh báo Linen dễ nhăn.
- Có phán quyết từ chối đúng nhãn nhưng lý do lại nhầm co sau giặt với co giãn hoặc bỏ qua URL bịa.

Một phản hồi API `success` sau phục hồi chỉ chứng minh các bước chạy lại được ở ca đó. Không dùng nó để kết luận model kiểm chứng đã được sửa. Không thay nhãn hoặc đưa các quy tắc Java vào điểm đo model để làm báo cáo đạt.

## Giới hạn còn lại

- Chất lượng kiểm chứng AI độc lập chưa đáng tin cậy trong các điều kiện đã thử.
- Chưa có bằng chứng đáp ứng p95 dưới 5 giây hoặc độ chính xác ngoài thực tế trên 95%.
- Catalog mẫu chỉ có 14 bản ghi và giữ `verification_status=unverified`; giá, thành phần, cảnh báo và nguồn cần kiểm tra theo mã trước khi mua.
- Luồng AI hiện tập trung tư vấn chọn vải theo trang phục và giới hạn giá VND/m; chưa có cơ sở mô tả nó là chatbot giải đáp mọi câu hỏi về vải.
- Các kiểm thử hữu hạn không xác nhận hệ thống đã sẵn sàng cho production.

## Cách đọc các trạng thái khi demo

- `success`: quy tắc đã qua và evaluator chấp nhận dự thảo; chất lượng đánh giá của evaluator vẫn có giới hạn nói trên.
- `fallback`: trả thông tin catalog và kết quả quy tắc sau khi đã nhận diện được điều kiện, kèm thông báo chưa hoàn tất kiểm chứng AI.
- `needs_clarification`: chưa đủ điều kiện hợp lệ hoặc model chính không thể phân tích; dùng form lọc để nhập điều kiện rõ ràng.
- `insufficient_data`: chưa có bằng chứng xác nhận những đặc tính được yêu cầu; ứng viên chưa được xác nhận đáp ứng.
- `no_results`: không có vải đáp ứng bộ lọc và điều kiện trong dữ liệu hiện có.
- `validation_failed`: chưa xác nhận được dự thảo sau số lần sửa được phép; không trả các đề xuất đó như kết quả đã kiểm chứng.

Khi ghi báo cáo hoặc trình bày, phân biệt kết quả test mã, hợp đồng API, tính nhất quán với catalog và chất lượng model độc lập. Không coi catalog chưa xác minh là dữ liệu đã được xác thực ngoài thực tế.
