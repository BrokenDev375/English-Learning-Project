# Dữ liệu demo M01 và M02 hoàn chỉnh

Khi `DEMO_ENABLED=true`, `DemoInitializer` tạo tài khoản demo chưa tồn tại theo `.env`, không đổi mật khẩu/quyền của tài khoản đã có. `CatalogDemoInitializer` tạo ba Topic/Unit và ba mục tiêu ban đầu. `CurriculumDemoInitializer` gọi `CurriculumSeedService` để bổ sung ba mục tiêu và bộ học liệu dưới đây. Mật khẩu được băm trước khi lưu; không có SQL chứa mật khẩu người dùng thật.

## Bộ nội dung m02-demo-1.0.0

| Unit/chủ đề | Bài 1 | Bài 2 |
|---|---|---|
| Sinh hoạt hằng ngày | A day with Linh — Morning routine | Nam’s week and weekend |
| Ăn uống và mua sắm | Ordering lunch at a café | Buying fruit and vegetables |
| Đi lại và chỉ đường | Finding places in town | Planning a weekend trip |

Tổng 3 Topic, 3 Unit, 6 mục tiêu, 6 Lesson root và 90 Question root. Mỗi bài có 5 GUIDED, 5 INDEPENDENT, 5 REVIEW_VARIANT, với hướng dẫn, ví dụ, đáp án, gợi ý và giải thích. Toàn bộ có 30 SINGLE_CHOICE, 30 FILL_BLANK, 30 MATCHING. Nhóm vận dụng dùng tình huống/câu mới; không tạo thêm root chỉ bằng đảo lựa chọn. Có câu độc lập, câu dùng chung đoạn đọc và câu dùng chung audio.

Mỗi bài có một đoạn đọc và một audio; bài Linh có thêm một ảnh lịch sinh hoạt. Tổng 13 Material root: 6 READING + 6 AUDIO + 1 IMAGE, với 7 Asset: 6 WAV + 1 PNG. Tài liệu dùng chung không cộng thêm vào số câu. Metadata/nội dung/phiên bản ở MySQL; byte được đưa qua AssetService vào volume `asset_data`, phục vụ qua API có quyền và Range. Resources không phải thư mục file công khai.

## Nguồn và manifest

- [Dataset JSON](../../source/server/src/main/resources/demo/curriculum-v1.json): dữ liệu chuẩn có seedKey, nội dung, đáp án, vai trò/thứ tự và liên kết tài liệu. Topic/Objective/Version ID được giải quyết lúc seed, không cố định ID database trong fixture.
- [Manifest câu hỏi](m02-question-manifest.csv): 90 dòng, liên kết seedKey → Unit/bài/mục tiêu/loại/nhóm/vị trí/tài liệu.
- [Manifest tài liệu](m02-material-manifest.csv): 13 dòng, nguồn, file và SHA-256.
- [Bảng duyệt nội dung](m02-editorial-review.md): prompt, đáp án và giải thích của 90 câu, dùng để rà soát trước công bố.
- [Media gốc](../../source/server/src/main/resources/demo/materials): 6 WAV và ảnh PNG. File gốc được giữ trong repository để khởi động offline, không cần gọi dịch vụ ngoài.

Các đoạn văn, câu hỏi, tình huống và giải thích do dự án tự soạn; nhân vật/địa điểm trong bài là minh họa. Không lấy đoạn văn, ảnh hoặc bản ghi từ website ngoài. Audio là giọng tổng hợp **Microsoft Zira Desktop**, tạo bằng Windows `System.Speech`, tốc độ -1; không phải bản ghi người thật. Ảnh lịch Linh tự vẽ bằng Pillow. Thông tin nguồn/điều kiện sử dụng được lưu trong từng MaterialVersion. Không khẳng định bộ demo này là dữ liệu đánh giá chuẩn hóa hoặc chứng minh hiệu quả học tập.

Sinh lại dữ liệu và kiểm tra tại root dự án:

```powershell
python scripts/build-demo-curriculum.py
# Chỉ cần khi tạo lại audio trên Windows có Microsoft Zira Desktop:
powershell -File scripts/generate-demo-audio.ps1
# Sau khi audio thay đổi, cập nhật lại SHA-256/manifest:
python scripts/build-demo-curriculum.py
python scripts/verify-demo-curriculum.py
```

Verifier dùng Python `jsonschema`; kiểm tra schema DTO, 6/90 và 5/5/5, ba dạng tương tác, seedKey/prompt duy nhất, liên kết manifest, hash media và bằng chứng đáp án của 12 câu điền từ nghe trong transcript. Nghe và rà soát biên tập vẫn là công việc nội dung; script không chứng minh mọi câu có chất lượng sư phạm như nhau.

## Chạy lại và nâng dữ liệu mẫu cũ

SeedKey chuẩn đã có thì bỏ qua, giữ sửa đổi, bản nháp và trạng thái ngừng cấp. Seed không đặt lại tài khoản hoặc tiến độ. Mỗi tài liệu là một checkpoint; câu, bài, publication và receipt của một bài cùng transaction. Nếu bước tạo bài lỗi, không để lại một phần câu/bài hoặc receipt thành công; chạy lại tiếp tục từ tài liệu đã hoàn tất. Hai lượt seed đồng thời được khóa theo actor để tránh trùng.

Hai initializer ba tài liệu/ba câu cũ được thay bằng initializer bộ chuẩn. Khi gặp alias `m02-material-*`, `m02-authoring-question-*` hoặc `m02-authoring-walkthrough`:

- Chỉ nhận lại mẫu cũ còn nguyên nội dung/phiên bản đầu, chưa có nháp hoặc ngừng cấp; Material/Question giữ ID, origin và published version. Đổi seedKey là metadata nội bộ.
- Bài mẫu ba câu được nâng lên 15 câu bằng **LessonVersion mới**; bản công bố ba câu cũ vẫn đọc được theo Version ID. Câu ghép ảnh cũ chuyển vào GUIDED; nhóm REVIEW_VARIANT mới có tình huống mới.
- Nếu mẫu cũ đã tùy chỉnh, giữ nguyên và tạo bộ chuẩn riêng. Khi đó tổng hàng có thể lớn hơn 6 bài/90 câu. Không ghi đè hoặc ép công bố lại nội dung của người dùng.

Không thêm hoặc sửa migration V3. Testcontainers dùng database/container riêng. Từ 18/10, `npm run test:e2e` mặc định dùng stack Docker `english-learning-e2e` với DB/file volume riêng ở cổng 5174/8081; không tạo fixture vào demo 5173. Các bài/câu công bố của fixture biên soạn còn được ngừng cấp khi kết thúc test. Đặt `E2E_BASE_URL` là chủ động chọn môi trường có sẵn, chỉ dùng URL dành cho kiểm thử. Các fixture lịch sử từng sót trên DB demo đã được ngừng cấp qua API, không đổi tên hoặc xóa lịch sử; bộ chuẩn và dữ liệu người dùng được giữ.

Học viên hiện xem được danh mục/giới thiệu/hướng dẫn của sáu bài. Lượt làm, trả câu, chấm và tiến độ thuộc M03. [Kết quả nghiệm thu M02](../results/module-2-seed-validation.md).
