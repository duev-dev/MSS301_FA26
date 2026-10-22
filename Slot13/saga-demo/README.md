# Saga Demo – Order Saga (Orchestration)

Java 21 · Spring Boot 4.1.1 · Docker Compose · Postman. Chi tiết đề bài: [`../Saga_guide.md`](../Saga_guide.md).

| Service | Port | Database (Docker) | Local transaction | Compensation |
|---|---|---|---|---|
| `order-service` | 8081 | MySQL 8.4 – `orderdb` (host `3307`) | `createPendingOrder`, `confirmOrder` | `cancelOrder` |
| `inventory-service` | 8082 | MongoDB 8.0 replica set – `inventorydb` (host `27017`) | `reserve` | `release` |
| `payment-service` | 8083 | SQL Server 2022 – `paymentdb` (host `14330`) | `pay` | `refund` |

## 1. Saga xử lý transaction như thế nào

**Vấn đề.** Đặt hàng phải ghi vào 3 database khác loại. `@Transactional` chỉ bao được **một** database của **một** service; nó không đi qua lời gọi HTTP. MongoDB lại không tham gia XA/2PC cùng MySQL và SQL Server, nên không có "một transaction chung".

**Cách Saga giải quyết.** Chia nghiệp vụ thành **chuỗi local transaction**. Mỗi bước commit ngay trên database của nó. Mỗi bước có một **compensating transaction** để hoàn tác về mặt nghiệp vụ nếu bước sau thất bại.

```
Thành công:  createPendingOrder ─► reserve ─► pay ─► confirmOrder
             saga: PENDING ─► EXECUTING ─────────────► COMPLETED   (order CONFIRMED)

Pay lỗi:     createPendingOrder ─► reserve ─► pay ✗
             saga: FAILED ─► COMPENSATING ─► release ─► cancelOrder ─► COMPENSATED  (order CANCELLED)
```

Cách code thực hiện ý tưởng này (`order-service/.../saga/OrderSagaOrchestrator.java`):

1. **Orchestrator không có `@Transactional`.** Nếu bọc cả hàm, khi pay lỗi Spring chỉ rollback được MySQL, còn kho trong MongoDB vẫn bị trừ. Mỗi method của `OrderService` có `@Transactional` riêng, nên trạng thái Saga (`EXECUTING`, `FAILED`…) được lưu ngay cả khi bước sau lỗi.
2. **Stack compensation (LIFO).** Bước nào **thành công** mới đẩy hành động hoàn tác vào `Deque`. Khi lỗi, pop ra và chạy theo thứ tự ngược. Pay lỗi thì stack chỉ có `release`, nên không gọi `refund` vô ích. Reserve lỗi (KB3/KB4) thì stack rỗng, chỉ `cancelOrder`.
3. **Compensation phải đạt 3 yêu cầu:**
   - *Idempotent*: `release`/`refund` kiểm tra trạng thái `RELEASED`/`REFUNDED`; `reserve`/`pay` kiểm tra `orderId` đã tồn tại. Gọi lặp lại không trừ/cộng thêm (KB7).
   - *Reversible*: `Reservation` lưu `quantity`, `Payment` lưu `amount`, nên biết chính xác phải trả lại bao nhiêu.
   - *Reliable*: `RetryTemplate` (Spring Framework 7) thử tối đa 3 lần, cách nhau 1 giây. Hết lượt vẫn lỗi thì Saga **dừng ở `COMPENSATING`** (KB8), không báo `COMPENSATED` sai sự thật.
4. **Một `catch (RestClientException)` bắt mọi lỗi:** 4xx/5xx nghiệp vụ (409 hết hàng, 402 thiếu tiền, 404) và lỗi mạng khi service tắt (`ResourceAccessException`).
5. **Theo dõi trạng thái:** `Order.sagaStatus` (6 trạng thái của Slide 27) và bảng `saga_log`, xem qua `GET /api/orders/{id}/saga-logs`.

**Đánh đổi.** Saga không có *Isolation*: trong lúc Saga chạy, service khác có thể thấy kho "tạm thời bị trừ" khi order còn `PENDING`. Hệ thống đạt **eventual consistency**, không phải nhất quán tức thì như ACID.

## 2. Chạy

**Cách B – tất cả trong Docker:**

```bash
cd Slot13/saga-demo
docker compose up -d --build
docker compose ps                     # 3 DB phải (healthy)
docker compose logs -f order-service  # xem log Saga
```

**Cách A – DB trong Docker, service chạy từ IDE:** `docker compose up -d mysql sqlserver mongodb`, rồi chạy lần lượt inventory → payment → order (`./mvnw spring-boot:run`).

**Reset dữ liệu:** `docker compose down -v`.

## 3. Kiểm thử bằng Postman

Import [`Saga_Postman_Collection.json`](Saga_Postman_Collection.json). Collection dùng biến `orderUrl`, `inventoryUrl`, `paymentUrl` (mặc định `localhost:8081/8082/8083`). Các test so sánh **độ chênh lệch** kho/ví trước–sau, nên chạy được cả khi chưa reset DB.

- **Runner:** chạy collection, **bỏ chọn** thư mục *Thủ công*, giữ thứ tự `0 → KB1 → KB2 → KB3 → KB4 → KB5 → KB7 → 9 - Dọn dẹp`.
  - KB1 thành công sẽ trừ hẳn 2000 của C1 và 2 P1. Thư mục *9 - Dọn dẹp* gọi `refund` + `release` order KB1 để trả lại, nhờ vậy Runner chạy lặp lại bao nhiêu lần cũng được.
  - Snapshot của KB1 kiểm tra trước `C1 >= 2000` và `P1 >= 2`. Nếu test này fail (ví dụ đã chạy Runner nhiều lần mà bỏ thư mục Dọn dẹp), chạy `docker compose down -v` rồi `docker compose up -d` để reset dữ liệu.
  - KB6/KB8 phải **tắt service trước khi gửi** request đặt hàng. Nếu quên, đơn sẽ `CONFIRMED` và trừ 20 của C1.
- **Newman:**

  ```bash
  npx newman run Saga_Postman_Collection.json \
    --folder "0 - Kiểm tra service" --folder "KB1 - Happy path" \
    --folder "KB2 - Thanh toán thất bại (402)" --folder "KB3 - Hết hàng (409)" \
    --folder "KB4 - Sản phẩm không tồn tại (404 inventory)" --folder "KB5 - Ví không tồn tại (404 payment)" \
    --folder "KB7 - Idempotent (gọi lặp lại)" --folder "9 - Dọn dẹp (trả lại tiền & kho của KB1)"
  ```

- **KB6 (thủ công):** `[KB6] 1` → `docker compose stop payment-service` → `[KB6] 2` → `docker compose start payment-service` → `[KB6] 3, 4`.
- **KB8 (thủ công):** `$env:PAYMENT_DELAY_MS=15000; docker compose up -d order-service` → gửi `[KB8] 2` và trong 15 giây chạy `docker compose stop payment-service inventory-service` → có response thì `docker compose start inventory-service payment-service` → `[KB8] 3 … 6` (bước 5 khôi phục thủ công bằng `release`, an toàn vì idempotent) → `$env:PAYMENT_DELAY_MS=0; docker compose up -d order-service`.

| KB | Request | Kết quả mong đợi | Saga logs |
|---|---|---|---|
| KB1 | `C1, P1, 2, 2000` | `CONFIRMED / COMPLETED`, P1 −2, C1 −2000 | CREATE_ORDER, RESERVE_INVENTORY, PROCESS_PAYMENT, CONFIRM_ORDER |
| KB2 | `C2, P1, 1, 1000` | `CANCELLED / COMPENSATED`, `402…`, kho/ví không đổi, reservation `RELEASED` | CREATE_ORDER, RESERVE_INVENTORY, SAGA, COMPENSATION, CANCEL_ORDER |
| KB3 | `C1, P2, 100, 2000` | `CANCELLED / COMPENSATED`, `409…` | CREATE_ORDER, SAGA, CANCEL_ORDER |
| KB4 | `C1, P9, 1, 10` | `CANCELLED / COMPENSATED`, `404 - Không tìm thấy sản phẩm P9` | CREATE_ORDER, SAGA, CANCEL_ORDER |
| KB5 | `C9, P2, 1, 20` | `CANCELLED / COMPENSATED`, `404 - Không tìm thấy ví của C9` | giống KB2 |
| KB6 | `C1, P2, 1, 20`, payment tắt | `CANCELLED / COMPENSATED`, `Service không phản hồi…` | giống KB2 |
| KB7 | gọi lặp release/reserve/pay/refund | kho/ví không đổi | – |
| KB8 | `C1, P2, 1, 20`, tắt payment + inventory | `PENDING / COMPENSATING`, P2 bị giữ 1 | 3 dòng COMPENSATION FAILED, không có CANCEL_ORDER |
