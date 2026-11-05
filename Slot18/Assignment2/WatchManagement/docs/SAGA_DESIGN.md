# Saga Design – Watch Management

> [TODO-4.1] Tài liệu thiết kế Saga (Orchestration). Sinh viên: DE190393.

## Participants

| Service | Vai trò | Database | Dữ liệu tham gia Saga |
|---|---|---|---|
| MSWatch | Orchestrator | DBWatch | `Watches`, `SagaStates`, `SagaLogs` |
| MSBrand | Participant | DBBrand | `BrandWatches` |

MSBrand chỉ cho MSWatch gọi 2 API nội bộ (Gateway không route `/internal/**`):

| API | Dùng cho |
|---|---|
| `PUT /internal/brands/{brandId}/watches/{watchId}` | ASSIGN_BRAND, ASSIGN_BRAND_BACK (upsert) |
| `DELETE /internal/brands/watches/{watchId}` | UNASSIGN_BRAND |

## Saga CREATE_WATCH

| # | Local transaction | Service / DB | Compensation | Idempotent? | Cách hiện thực compensation |
|---|---|---|---|---|---|
| 1 | CREATE_WATCH – INSERT Watches | MSWatch / DBWatch | DELETE_WATCH | ✔ | `deleteIfExists(watchId)`: không còn dòng thì bỏ qua |
| 2 | ASSIGN_BRAND – upsert BrandWatches | MSBrand / DBBrand | UNASSIGN_BRAND | ✔ | `DELETE /internal/brands/watches/{id}`: không có gì để gỡ → 200 `NOTHING_TO_DO` |

## Saga UPDATE_WATCH

| # | Local transaction | Service / DB | Compensation | Idempotent? | Cách hiện thực compensation |
|---|---|---|---|---|---|
| 1 | UPDATE_WATCH – ghi dữ liệu mới | MSWatch / DBWatch | RESTORE_WATCH | ✔ | Ghi lại snapshot `before` (chụp trước bước 1); ghi lại nhiều lần cho cùng kết quả |
| 2 | ASSIGN_BRAND – chuyển sang brand mới | MSBrand / DBBrand | ASSIGN_BRAND_BACK | ✔ | `PUT /internal/brands/{brandCũ}/watches/{id}` (upsert) |

## Saga DELETE_WATCH

| # | Local transaction | Service / DB | Compensation | Idempotent? | Cách hiện thực compensation |
|---|---|---|---|---|---|
| 1 | UNASSIGN_BRAND – gỡ watch khỏi brand | MSBrand / DBBrand | ASSIGN_BRAND_BACK | ✔ | `PUT /internal/brands/{brandCũ}/watches/{id}` (upsert) |
| 2 | DELETE_WATCH – xoá watch | MSWatch / DBWatch | — **PIVOT** | — | Không có |

**Vì sao DELETE_WATCH là pivot và đứng cuối:** xoá cứng một dòng có `IDENTITY` thì không khôi phục được đúng `WatchID` cũ, nên sau bước này Saga không thể quay lui. Bước rủi ro cao (gọi MSBrand qua mạng) được làm **trước**, khi còn bù trừ được; bước không bù trừ được làm **cuối**, khi mọi bước trước đã thành công.

## State machine

```
PENDING → EXECUTING ───────────────────────────────→ COMPLETED
               │ (một bước lỗi)
               ▼
            FAILED → COMPENSATING → COMPENSATED      (đã bù trừ xong, dữ liệu nhất quán)
                          │
                          └→ (compensation lỗi sau 3 lần) dừng ở COMPENSATING → xử lý thủ công
```

## Luồng thất bại: CREATE với brand 999

```
MSWatch (Orchestrator)                 DBWatch                 MSBrand / DBBrand
   │ [1] CREATE_WATCH ───────────────► INSERT Watches (id=11) ✔ COMMIT
   │ [2] ASSIGN_BRAND ──── PUT /internal/brands/999/watches/11 ──► 404 Brand not found
   │      → FAILED
   │      → COMPENSATING
   │ [C1] DELETE_WATCH ──────────────► DELETE Watches id=11 ✔
   │      → COMPENSATED
   ▼  HTTP 400 {"sagaStatus":"COMPENSATED","failedStep":"ASSIGN_BRAND"}
```

Watch id=11 **đã commit thật** rồi mới bị xoá (compensation, không phải rollback). Bằng chứng: watch tạo thành công tiếp theo có id = 12.

## Quyết định thiết kế

1. Orchestration (không dùng Choreography) vì chỉ có 2 service, luồng ngắn, dễ quan sát.
2. Orchestrator không có `@Transactional`; mỗi bước là 1 local transaction; nhật ký Saga ghi bằng `REQUIRES_NEW`.
3. Compensation chạy LIFO (ngược thứ tự), retry tối đa 3 lần, yêu cầu idempotent.
4. Bước xoá cứng (`DELETE_WATCH`) là pivot, đặt cuối Saga xoá.
5. Validate rẻ (name, price, releaseDate, category) chạy trước khi Saga bắt đầu → lỗi validation không tạo Saga.
6. Lỗi từ MSBrand luôn dẫn tới compensation, không có fallback "lưu tạm".

## [TODO-4.2] Vì sao không dùng `@Transactional` / 2PC

- `@Transactional` chỉ quản lý **một** database (DBWatch). Lời gọi HTTP sang MSBrand nằm ngoài transaction, nên rollback DBWatch không hoàn tác được DBBrand.
- 2PC phải lock tài nguyên ở cả hai service trong suốt 2 pha; coordinator là single point of failure; MSBrand không hỗ trợ XA qua REST.
- Saga chấp nhận **eventual consistency**: mỗi bước commit ngay, lỗi thì bù trừ bằng compensating transaction.
