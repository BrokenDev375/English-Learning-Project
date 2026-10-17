# HỌC PHẦN: LẬP TRÌNH MẠNG (NETWORK PROGRAMMING 2026–2027)

Thư mục này chứa tài liệu học tập, bài giảng, tài liệu tham khảo và dự án bài tập lớn.

---

## 📁 Bài Tập Lớn: Air Hockey Multiplayer System

Toàn bộ mã nguồn, cấu hình, kịch bản chạy và tài liệu đặc tả của bài tập lớn đã được đóng gói và cấu trúc chuẩn theo yêu cầu của `Instruction.md` (Mục 12 & 13) tại thư mục:

👉 **[assignment-network-project/](./assignment-network-project/)**

### Cấu trúc dự án:
```text
assignment-network-project/
├── README.md               # Tài liệu báo cáo kỹ thuật & hướng dẫn chạy chi tiết
├── INSTRUCTION.md          # Hướng dẫn bài tập lớn
├── TOPICS.md               # Danh sách chủ đề
├── SPEC.md                 # Đặc tả giao thức nhị phân và mô hình vật lý
├── PROBLEMS.md             # Tổng hợp các bài toán kỹ thuật thực tế & giải pháp
├── pom.xml                 # Cấu hình Spring Boot 3 & Binary WebSocket
├── run_server.bat          # File thực thi chạy máy chủ
├── run_client.bat          # File thực thi chạy client
├── compile.bat             # File biên dịch nhanh
│
├── report/                 # Báo cáo học phần
│
├── statics/                # Sơ đồ kiến trúc & kết quả thực nghiệm
│   ├── architecture.png    # Sơ đồ kiến trúc phân tầng
│   ├── protocol.png        # Sơ đồ cấu trúc gói tin nhị phân
│   ├── results/            # Kết quả đo lường, benchmark và ảnh chụp
│   └── dataset/
│
├── source/                 # Toàn bộ mã nguồn Java Backend & Desktop Client
│   ├── common/             # Model, Protocol, Codec nhị phân
│   ├── server/             # Spring Boot 3, WebSocketHandler, PhysicsEngine 60Hz
│   └── client/             # Java Swing Desktop Client
│
├── web-client/             # Ứng dụng Web Client React.js 18 + HTML5 Canvas 60 FPS
│
└── test/                   # Bộ kiểm thử tự động (Unit & Integration Tests)
```
