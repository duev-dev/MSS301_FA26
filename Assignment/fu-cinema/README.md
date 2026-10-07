# FUCinemaBookingSystem

Cinema Ticket Booking System using API Gateway – MSS301 Assignment 01.

**Stack:** Java 21 · Spring Boot 4.1.0 · Spring Cloud 2025.1.3 · SQL Server 2022 · MongoDB 7.0.5 · MySQL 8.3.0 (Docker) · Postman

## Kiến trúc

```
Postman ──► api-gateway :9000 ──┬──► customer-service :8081  (SQL Server – cinema_customer, Flyway)
            (JWT HS256,         ├──► movie-service    :8082  (MongoDB    – cinema_movie, DataSeeder)
             role rules,        └──► booking-service  :8083  (MySQL      – cinema_booking, Flyway)
             X-User-* headers)                              │ OpenFeign
                                                            └──► movie-service :8082
```

- Chỉ **api-gateway** kiểm tra JWT; các service phía sau đọc header `X-User-Id`, `X-User-Email`, `X-User-Role` do Gateway chèn (header client tự gửi bị xóa).
- customer-service ký JWT HS256 với claim `sub`, `uid`, `role`; Gateway verify bằng cùng secret `app.jwt.secret`.

| Thư mục | Nội dung |
|---|---|
| `docker-compose.yml`, `sqlserver/`, `mysql/` | 3 database (SQL Server + init container, MongoDB, MySQL) |
| `customer-service/` | F1 Authentication, F2 Register & profile, F3 Admin quản lý customer |
| `movie-service/` | F4 Genre & Room, F5 Movie, F6 Showtime |
| `booking-service/` | F7 Đặt vé, F8 Lịch sử & hủy vé, F9 Báo cáo doanh thu |
| `api-gateway/` | F10 Routing + bảo mật JWT theo role |
| `postman/` | F11 Collection + Environment |

## Chạy hệ thống

Yêu cầu: JDK 21, Maven 3.9+, Docker Desktop (≥ 4 GB RAM), Postman.

```bash
# 1. Database (đợi cinema-sqlserver "healthy", cinema-sqlserver-init "Exited (0)")
docker compose up -d
docker compose ps -a

# 2. Các service – mỗi lệnh 1 terminal, theo đúng thứ tự
mvn -f customer-service/pom.xml spring-boot:run   # :8081
mvn -f movie-service/pom.xml spring-boot:run      # :8082 (tự seed MongoDB lần đầu)
mvn -f booking-service/pom.xml spring-boot:run    # :8083
mvn -f api-gateway/pom.xml spring-boot:run        # :9000

# 3. Kiểm tra nhanh
curl http://localhost:9000/actuator/health
curl http://localhost:9000/api/movies
```

Làm lại dữ liệu từ đầu: `docker compose down -v`, xóa thư mục `docker/`, rồi `docker compose up -d`.

## Tài khoản test

| Vai trò | Email | Password | Ghi chú |
|---|---|---|---|
| Admin | `admin@fucinema.com` | `@@abc123@@` | Cấu hình trong `application.properties` (userId = 0) |
| Customer | `an@gmail.com` | `123456` | ID 1, ACTIVE |
| Customer | `binh@gmail.com` | `123456` | ID 2, ACTIVE |
| Customer | `chi@gmail.com` | `123456` | ID 3, INACTIVE (login → 403) |

Dữ liệu mẫu MongoDB (ID cố định): genre `66f0…001`–`…005`, room `66f1…001`–`…004` (`…004` MAINTENANCE), movie `66f2…001`–`…004` (`…004` ENDED), showtime `66f3…001`–`…005` (`…005` CANCELLED).

| Database | Kết nối | User / Password |
|---|---|---|
| SQL Server | `jdbc:sqlserver://localhost:1433;databaseName=cinema_customer;encrypt=true;trustServerCertificate=true` | `sa` / `Fucinema@2026` |
| MongoDB | `mongodb://localhost:27017/?authSource=admin` | `root` / `password` |
| MySQL | `jdbc:mysql://localhost:3306/cinema_booking` | `root` / `mysql` |

## Kiểm thử bằng Postman

1. Import `postman/FUCinemaBookingSystem.postman_collection.json` và `postman/FUCinema-Local.postman_environment.json`.
2. Chọn environment **FUCinema-Local**.
3. Chuột phải collection → **Run collection**, giữ thứ tự folder `01-Auth` → `08-Report`, bấm **Run**.

Mọi request đều gọi qua Gateway `{{gateway}}` = `http://localhost:9000`. Request sau dùng biến do request trước lưu (`adminToken`, `customerToken`, `showtimeId`, `bookingId`…).

Trên DB sạch, báo cáo 8.1 kỳ vọng: `totalBookings = 2`, `totalTickets = 3`, `totalRevenue = 285000` (booking đã hủy không được tính).

Test thủ công BR14 (không có trong Runner): tắt movie-service → gửi lại request 6.13 với ghế `B2` → kỳ vọng **503** `Movie service is unavailable...`.
