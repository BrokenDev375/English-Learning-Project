# Nghe audio tối đa hai lượt

Ngày 29/10/2026, working tree trên `3f3b0c5`. Áp dụng cho audio trong lượt LEARN/PRACTICE/REVIEW và phần chữa của chính lượt đó. Preview học liệu của tác giả dùng luồng riêng.

## Quy tắc

- Một hạn mức **2 lượt bắt đầu phát** cho mỗi cặp Attempt + MaterialVersion. Các câu dùng chung đoạn nghe dùng chung hạn mức; lượt làm mới có hạn mức mới. Không tính theo số lần pause/play của trình duyệt.
- Tạm dừng và nghe tiếp, đổi câu rồi trở lại trong cùng phiên giao diện vẫn dùng lượt đã được cấp. Hết đoạn thì phải xin lượt tiếp theo; sau lượt thứ hai nút phát mới bị khóa. Phần chữa sau nộp không cấp lại hạn mức.
- Tải lại trang/đăng nhập lại kết thúc quyền phát đang giữ tại trang đó; số lượt đã dùng không được hoàn lại. Nếu còn lượt, bắt đầu lượt tiếp theo từ đầu đoạn. Quy tắc này có nhãn ngay dưới bộ phát; không còn khôi phục vị trí tự do sau reload như bộ phát cũ.
- Bộ điều khiển có phát/tạm dừng và tắt/bật tiếng. Tiến độ chỉ đọc; không có thanh tua, menu tốc độ hoặc controls native. Seeking ngoài vị trí phát hợp lệ được trả lại; tốc độ giữ 1x. Start/end của đoạn do MaterialVersion quy định.
- Cần kết nối để đọc hạn mức/cấp lượt mới. Một lượt đã cấp có thể tiếp tục từ dữ liệu media có sẵn; mạng lỗi không tạo hạn mức mới. Lỗi phát file có thể tải lại và dùng tiếp lượt đã cấp trong trang đó.
- Cấp lượt được ghi trước khi phát file. Khi mất phản hồi, nút Xác nhận lại gửi cùng requestId/payload; không tự tạo key mới hoặc trừ hai lượt cho cùng yêu cầu. Reload sau mất phản hồi không hoàn lại lượt đã commit, kể cả chưa nghe được file.

## Backend và dữ liệu

Flyway **V18__attempt_audio_limits.sql** thêm `attempt_audio_listens`, unique `(attempt_id, material_version_id)`, FK tới Attempt/MaterialVersion và CHECK `plays_used BETWEEN 0 AND 2`. Sau tích hợp main, V14 dành cho lesson activities. Database từng chạy V14 audio của nhánh cũ cần chuyển lịch sử riêng trước khi dùng source tích hợp; xem [hướng dẫn tích hợp](ADMIN_MAIN_INTEGRATION.md#database-đã-chạy-trước-tích-hợp). Lượt làm có sẵn theo main bắt đầu ở 0 khi chưa có bản ghi; dữ liệu bài làm/nộp/tiến độ cũ giữ nguyên.

`AttemptAudioController → AttemptAudioService/impl → AttemptAudioRepository/impl → JPA`. Start chạy READ_COMMITTED: kiểm tra STUDENT → receipt/actor lock → lock và đọc lại Attempt owner → xác minh AUDIO thuộc Version đã chốt của ít nhất một câu → tăng bộ đếm → receipt → commit. Replay xảy ra trước kiểm tra đủ hai lượt. Receipt lỗi rollback cả bộ đếm. Nhiều tab/thiết bị cùng user được phân xử bởi actor/Attempt lock; không dựa vào bộ đếm localStorage.

Schema chính xác tại [OpenAPI audio](../source/common/openapi-attempt-audio.json).

Không tăng revision Attempt, không sửa PlayerState DTO hoặc hash của pending command M03 cũ; không tạo trial/Submission, không chấm và không cộng thưởng. GET không cấp lượt. Không trả entity hoặc keys/transcript cho client.

| Endpoint | Hành vi |
|---|---|
| GET `/api/attempts/{id}/audio/{materialVersionId}` | Owner STUDENT, đọc hạn mức, không tạo row |
| POST `/api/attempts/{id}/audio/{materialVersionId}/start` | Session + CSRF; body chỉ `{ "requestId": "UUID" }`; cấp một lượt mới |

Response 200, `Cache-Control: no-store, private`:

```json
{"attemptId":1,"materialVersionId":10,"playsUsed":1,"maxPlays":2,"remaining":1}
```

Replay thêm `Idempotent-Replayed: true`. Lần cấp thứ ba trả 409 `AUDIO_LIMIT_REACHED`; requestId sai/trường lạ 400, thiếu CSRF 403, tài nguyên ngoại lai/không phải audio thuộc lượt 404, không phải STUDENT 403, phiên hết/thu hồi 401. Guard tài khoản hiện hành giữ theo M01.

## Frontend

`RestrictedAudio.tsx` và `audioSession.ts` dùng API chung cookie/CSRF/auth epoch. Cache quyền phát theo user/Attempt/MaterialVersion và vòng auth trong RAM để giữ pause/resume khi đổi câu; không chứa đáp án, không dùng cache làm nguồn cấp lượt mới. Request start chưa rõ kết quả giữ nguyên key trong document. Callback auth cũ không cập nhật cache mới.

`LearningMaterial` nhận attemptId/userId từ AttemptRunner và AttemptResult. Vị trí đang nghe vẫn được chuyển cho working copy M03 như trước; PlayerState không cấp quyền tua hoặc hoàn lại lượt. Quy tắc hạn mức không dựa vào dữ liệu playerState do client tự gửi.

Đây là giới hạn của luồng học/bộ phát/API start, không phải DRM ngăn người dùng có kỹ thuật can thiệp trình duyệt hoặc đọc URL media đã được cấp quyền. Không thay cơ chế AssetReadGrant/HTTP Range hiện có.

## Chạy và thử

Rebuild cả backend/frontend bằng `docker compose up -d --build`; V18 chạy trên database theo main V1–V17, không cần reset dữ liệu. Backend restart làm mất session RAM, cần login lại. Không `down -v`.

Mở một câu nghe: bấm Nghe lần 1/2, tạm dừng/nghe tiếp, đổi sang câu cùng audio, nghe hết và bấm Nghe lần 2/2. Sau khi hết, nút bị khóa; reload/tab khác không cấp lại lượt. Thanh tiến độ không kéo/bấm để tua. Thử lại file lỗi không trừ thêm quyền phát đã cấp trong cùng document.

Bằng chứng, source/lệnh/kết quả/giới hạn tại [biên bản kiểm tra](../statics/results/audio-listening-validation.md). Các ca kiểm tra seek/khôi phục tự do của UI cũ đã thay bằng các ca no-seek/quota, không dùng kết quả lịch sử đó làm bằng chứng chính sách mới.
