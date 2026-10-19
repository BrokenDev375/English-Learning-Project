# Cấu trúc backend M01 và M02 hoàn chỉnh

M03 bước 1 bổ sung nền V4 và 7 entity học tập, `AttemptRepository`/implementation và JPA repositories, DTO `AttemptData/AttemptRequests/AttemptViews`, `AnswerValidator` và `AnswerGrader`/implementation. Chưa có controller/service nghiệp vụ Attempt hoặc progress. Theo dõi [tiến độ M03](M03_PROGRESS.md), [nghiệm thu nền](../statics/results/module-3-step1-validation.md). V4 chỉ được áp dụng ở test trong lượt triển khai này; backend build mới sẽ tự migrate database được cấu hình khi khởi động.

M02 bước 1 bổ sung AuthorCatalogController → CatalogService/impl → CatalogRepository/impl → JPA cho Topic/Objective/Unit, cùng RequestReceiptService dùng chung. Theo dõi phạm vi hiện tại ở [tiến độ M02](M02_PROGRESS.md). Mô tả M01 bên dưới vẫn áp dụng cho nền tài khoản.

M02 bước 2 bổ sung AssetController → AssetService/impl → AssetRepository/impl và AuthorMaterialController → MaterialService/impl → MaterialRepository/impl. AssetStorage giữ file trong volume, dọn file khi transaction rollback; MediaInspector kiểm tra PNG/JPEG bằng ImageIO và MP3/WAV/OGG bằng ffprobe có giới hạn thời gian. MaterialData/MaterialRequests kiểm tra DTO theo từng loại, MaterialViews tạo response. Bản công bố bất biến; write khóa actor trước material root, dùng RequestReceiptService và revision/previewToken.

AssetReadGrant là điểm mở rộng quyền đọc file. MaterialAssetReadGrant cấp cho tác giả theo tài liệu sở hữu/tài liệu đang công bố; ADMIN được đọc mọi asset. STUDENT chưa có grant cho đến khi M03 thêm AttemptAssetReadGrant. Các entity QuestionRootReference/QuestionVersionReference/LessonQuestionReference và LessonReference chỉ phục vụ kiểm tra tài liệu đang được nội dung khác tham chiếu; không phải API câu hỏi/bài học.

Bước 3 thêm AuthorContentController/AuthorStructureController → AuthoringService/impl → ContentRepository/impl → Question/Lesson JPA repositories. AuthoringRequests parse nghiêm DTO một trong ba dạng; QuestionValidator kiểm tra điều kiện công bố. QuestionVersionEntity và LessonVersionEntity kế thừa AuthoredVersion; JSON dùng JsonNode đã kiểm tra schema, join mục tiêu/câu hỏi ánh xạ ElementCollection. Không serialize entity trực tiếp.

AuthoringPublication thu closure theo Version ID, khóa Material → Question → Lesson tăng dần ID, refresh dữ liệu sau lock rồi tính lại token. Các mutation câu/bài dùng READ_COMMITTED để kiểm tra phụ thuộc sau khi chờ lock; retire Material cũng dùng READ_COMMITTED để thấy bài/câu vừa được publish trước đó. Validation, thay state/con trỏ và receipt cùng transaction, rollback không để lại phần phụ thuộc đã công bố riêng lẻ.

LearnerCatalogController → LearnerCatalogService/impl chỉ trả danh mục/giới thiệu từ active published, không trả câu/keys. PublishedContentReader/impl là API nội bộ cho M03, trả snapshot theo Version ID kể cả lịch sử; DTO này có answerKey/transcript và tuyệt đối không dùng trực tiếp làm learner response. Nó chưa cấp Attempt hoặc quyền file theo Attempt.

Bước 4 dùng `DemoCurriculum` đọc và kiểm tra `demo/curriculum-v1.json` cùng SHA-256 của media. `CurriculumDemoInitializer` chạy sau danh mục, gọi `CurriculumSeedService`: tạo mục tiêu còn thiếu, đưa media qua AssetService, tài liệu/câu/bài qua service biên soạn thật. Mỗi tài liệu là một checkpoint; toàn bộ câu, bài và receipt của một bài commit/rollback cùng transaction READ_COMMITTED. Khóa actor ngăn hai lượt seed đồng thời tạo trùng. SeedKey đã có thì bỏ qua, giữ sửa đổi/nháp/ngừng cấp. Hai initializer mẫu cũ được thay thế; seed mới chỉ nhận lại các root mẫu cũ còn nguyên, nâng bài bằng version mới và giữ lịch sử. Root mẫu cũ đã tùy chỉnh được giữ, bộ chuẩn được tạo riêng. Không sửa V3.

Package vn.edu.ptit.english:
- controller: AuthController, AdminUserController, ClassroomController, HealthController.
- service và service.impl: hợp đồng nghiệp vụ và implementation riêng.
- repository và repository.impl: hợp đồng truy cập dữ liệu, adapter dùng Spring Data JPA.
- entity/enums/dto: ánh xạ ORM, trạng thái, dữ liệu request/response.
- security: CSRF/session, kiểm tra tài khoản, khóa ghi, thu hồi kết nối.
- socket/config: đăng ký WebSocket, giới hạn origin, xác thực phiên.
- bootstrap: admin đầu tiên và tài khoản demo có cấu hình.

Admin không lấy passwordHash qua API. Mọi endpoint admin kiểm tra ROLE_ADMIN và trạng thái hiện tại trong DB. Các thay đổi quyền/trạng thái lấy account_control trước, rồi khóa người thực hiện và tài khoản đích. Các thao tác ghi nghiệp vụ lấy khóa người thực hiện trước khóa tài nguyên; module sau cần giữ thứ tự này.

AccountGuardFilter xác nhận phiên bản quyền cho request đã xác thực. AccountGuard.lockCurrent chạy trong transaction ghi để xử lý request đang chạy khi admin khóa tài khoản. AccountAccessChanged chỉ thu hồi session/socket sau commit; security_version trong DB ngăn session cũ hoạt động kể cả khi sự kiện RAM không còn.

Đăng nhập lưu SecurityContext qua HttpSessionSecurityContextRepository và đổi session id. WebSocket handshake kiểm tra quyền, origin, phiên HTTP; mở kết nối xong kiểm tra lại để tránh race với thao tác thu hồi.
