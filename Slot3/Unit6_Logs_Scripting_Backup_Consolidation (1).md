# BÀI 6 — LOGS (JOURNALCTL), TEXT PROCESSING, BASH SCRIPTING, EXIT CODES, CRON, BACKUP (TAR/RSYNC) VÀ TỔNG HỢP LINUX

*Tài liệu lý thuyết chuyên sâu, có so sánh với kiến thức đã học (Bài 1–5) và các tình huống thực chiến. Kèm đào sâu Log Analytics (error rate, Top-N, correlation, JSON logs), tăng chiều sâu Recovery Validation (4 tầng kiểm chứng, Restore vs Recovery), và Full Operations Runbook hoàn chỉnh. Kèm bài tập nâng cấp script health-check & backup, lên lịch, cố ý gây lỗi, và ghi escalation evidence.*

## 1. Logs với `journalctl` — Đào sâu

### 1.1. `journald` khác gì log file truyền thống

Trước `systemd`, log được ghi thẳng vào file text (`/var/log/syslog`, `/var/log/messages`) bởi `syslog`/`rsyslog`. `systemd-journald` thay đổi cách này: log được ghi vào **định dạng binary có cấu trúc (structured, indexed)**, không phải text thuần.

| | Log file truyền thống (syslog) | `journald` |
|---|---|---|
| Định dạng | Text thuần, mỗi dòng 1 entry | Binary, có index sẵn |
| Tìm kiếm | Phải dùng `grep` quét toàn file | Filter trực tiếp theo field (`-u`, `-p`, thời gian...) — nhanh hơn nhiều |
| Cấu trúc | Không cố định, mỗi daemon tự format | Có field chuẩn hoá: `_PID`, `_SYSTEMD_UNIT`, `PRIORITY`... |
| Xem log boot cũ | Khó, tuỳ log rotate | `journalctl -b -1` (boot trước) có sẵn |

> **Liên hệ Bài 4:** Đây là lý do khi học `systemctl`/`journalctl -u <service>`, bạn không cần tự tìm log service đó nằm ở file nào — `journald` đã gắn sẵn field `_SYSTEMD_UNIT` vào mỗi log entry.

### 1.2. Volatile vs Persistent — log có tồn tại sau reboot không

Theo mặc định trên một số distro, `journald` chỉ lưu log **volatile** tại `/run/log/journal` (nằm trong RAM/tmpfs) — **mất hết sau khi reboot**. Muốn log tồn tại lâu dài, cần bật persistent storage:

```bash
sudo mkdir -p /var/log/journal
sudo systemd-tmpfiles --create --prefix /var/log/journal
sudo systemctl restart systemd-journald
```

```bash
journalctl --disk-usage             # xem journal đang chiếm bao nhiêu dung lượng — liên hệ Bài 5 (Capacity Checks)
sudo journalctl --vacuum-size=500M   # giới hạn journal không vượt quá 500MB, tự xoá log cũ nhất
sudo journalctl --vacuum-time=7d      # chỉ giữ lại log trong 7 ngày gần nhất
```

> **Cảnh báo thực chiến:** Nếu không cấu hình `vacuum`, journal binary có thể phình to không kiểm soát và **ăn hết dung lượng đĩa** — đây là một nguyên nhân thực tế khiến server báo lỗi "disk full" mặc dù ứng dụng không hề ghi file gì lớn. Luôn kiểm tra `journalctl --disk-usage` khi chẩn đoán capacity issue (liên hệ Bài 5, mục Capacity Checks).

### 1.3. Các cách filter quan trọng nhất

```bash
journalctl -u nginx                     # log của riêng 1 service (đã học ở Bài 4)
journalctl -u nginx -f                   # theo dõi real-time, giống tail -f
journalctl -p err                         # chỉ log ở mức "error" trở lên
journalctl -b                              # log của LẦN BOOT hiện tại
journalctl -b -1                           # log của lần boot NGAY TRƯỚC — cực hữu ích khi debug máy vừa bị crash/reboot bất ngờ
journalctl --since "2026-09-14 08:00" --until "2026-09-14 09:00"    # lọc theo khoảng thời gian cụ thể
journalctl -k                              # chỉ log của kernel (tương đương dmesg)
journalctl -o json-pretty                  # xuất dạng JSON — hữu ích khi cần đẩy log vào công cụ khác (ELK, script phân tích)
```

**Bảng mức độ ưu tiên (priority) — từ Bài 4 chỉ nhắc sơ, giờ đào sâu:**

| Số | Tên | Ý nghĩa |
|---|---|---|
| 0 | `emerg` | Hệ thống không dùng được nữa |
| 1 | `alert` | Cần xử lý ngay |
| 2 | `crit` | Lỗi nghiêm trọng |
| 3 | `err` | Lỗi thường |
| 4 | `warning` | Cảnh báo |
| 5 | `notice` | Đáng chú ý nhưng bình thường |
| 6 | `info` | Thông tin |
| 7 | `debug` | Chi tiết debug |

```bash
journalctl -p 3            # tương đương -p err — chỉ hiện mức 0,1,2,3 (từ nghiêm trọng nhất đến err)
```

> **Ví dụ thực chiến:** Server tự reboot lúc 3 giờ sáng không rõ lý do. Chạy `journalctl -b -1 -p err --since "-1hour"` (log của lần boot trước, chỉ lỗi, trong 1 giờ cuối trước khi crash) — đây là câu lệnh chẩn đoán "kinh điển" cho tình huống crash bất ngờ, kết hợp `-b -1` (boot cũ) + `-p` (mức độ) + `--since` (thời gian).

### 1.4. `logrotate` vs `journald --vacuum` — hai cơ chế "dọn log" dễ nhầm

Cả hai đều "chống log phình to", nhưng quản lý 2 loại log hoàn toàn khác nhau:

| | `logrotate` | `journald --vacuum` |
|---|---|---|
| Quản lý loại log nào | File log text truyền thống (`/var/log/nginx/access.log`...) do ứng dụng/syslog tự ghi | Binary journal do `systemd-journald` ghi (mục 1.1) |
| Cấu hình ở đâu | `/etc/logrotate.d/<tên>`, chạy định kỳ qua cron/systemd timer riêng | `/etc/systemd/journald.conf` (`SystemMaxUse=`) hoặc chạy tay `--vacuum-size`/`--vacuum-time` |
| Cơ chế | Đổi tên file cũ (`access.log` → `access.log.1` → nén `.gz`...), tạo file mới, xoá bản cũ nhất theo số lượng giữ lại | Xoá thẳng dữ liệu cũ trong journal đến khi đạt giới hạn dung lượng/thời gian |
| Áp dụng cho service tự quản lý log riêng (nginx, mysql...) | Có — đây chính là công cụ chuẩn cho việc này | Không — các log đó không đi qua journald |

> **Ví dụ thực chiến:** Ổ đĩa báo đầy do `/var/log` phình to — cần kiểm tra **cả 2 nơi**: `journalctl --disk-usage` (log service qua systemd) **và** `du -sh /var/log/nginx` (log file truyền thống) — vì `logrotate` chỉ dọn loại thứ 2, không đụng đến journal binary.

## 2. `grep` / `awk` / `sed` — Xử lý văn bản chuyên sâu

Bài 2 đã giới thiệu 3 lệnh này ở mức cơ bản. Giờ đào sâu và **so sánh khi nào dùng cái nào** — đây là câu hỏi thực chiến rất thường gặp.

### 2.1. `grep` — Tìm dòng khớp điều kiện

```bash
grep -i "error" app.log            # -i: không phân biệt hoa/thường
grep -v "DEBUG" app.log             # -v: LOẠI BỎ dòng khớp (invert match) — rất hữu ích để "lọc nhiễu"
grep -c "ERROR" app.log             # -c: chỉ đếm số dòng khớp, không in ra nội dung
grep -n "ERROR" app.log             # -n: in kèm số dòng — giúp mở đúng vị trí trong file để xem thêm ngữ cảnh
grep -A 3 -B 1 "Exception" app.log   # -A: in thêm 3 dòng SAU, -B: 1 dòng TRƯỚC dòng khớp — xem ngữ cảnh quanh lỗi
grep -r "TODO" /opt/myapp/           # -r: tìm đệ quy qua mọi file trong thư mục
grep -E "ERROR|WARN" app.log          # -E: extended regex, cho phép dùng | (OR) trực tiếp
```

> **`grep` thường vs `grep -E` — BRE vs ERE:** Mặc định `grep` dùng **Basic Regular Expression (BRE)**, trong đó các ký tự đặc biệt như `|`, `+`, `?`, `()` phải thêm dấu `\` phía trước mới có tác dụng (ví dụ `grep "err\|warn"`). `grep -E` (tương đương lệnh `egrep` cũ) dùng **Extended Regular Expression (ERE)**, cho phép viết thẳng `|`, `+`, `()` mà không cần escape — đây là lý do rất nhiều người viết `grep "a|b"` mà không hiểu sao không khớp gì cả: vì thiếu `-E`, dấu `|` bị hiểu là ký tự thường, không phải toán tử OR.

### 2.2. `awk` — Xử lý theo cột (field-based)

`awk` mạnh hơn `grep`/`cut` vì nó hiểu dữ liệu theo **cột** và có thể **tính toán**, không chỉ tìm/cắt chuỗi.

```bash
awk '{print $1, $NF}' access.log          # in cột 1 và CỘT CUỐI CÙNG ($NF = Number of Fields, tự thích ứng theo từng dòng)
awk -F',' '{print $2}' data.csv             # -F: đổi delimiter thành dấu phẩy (mặc định awk tách theo khoảng trắng)
awk '$3 > 100 {print $1}' report.txt         # PATTERN { ACTION } — chỉ in cột 1 của các dòng có cột 3 > 100
awk '{sum += $2} END {print "Total:", sum}' sales.txt    # cộng dồn cột 2 qua toàn file, in tổng ở dòng cuối (END block)
awk 'BEGIN {print "Bắt đầu xử lý"} {print $0} END {print "Xong"}' file.txt   # BEGIN/END chạy 1 lần trước/sau khi xử lý toàn file
```

> **So sánh nhanh `awk` vs `cut` (đã học ở Bài 2):** `cut -d"," -f2` chỉ cắt đúng cột theo delimiter cố định, không tính toán được gì thêm. `awk` làm được mọi thứ `cut` làm, cộng thêm điều kiện lọc theo giá trị cột và phép tính — nên trong thực chiến, `awk` thường được chọn khi cần vừa lọc vừa tính (ví dụ: tổng dung lượng, đếm theo điều kiện).

### 2.3. `sed` — Tìm & thay thế theo luồng (stream editor)

```bash
sed 's/error/ERROR/g' app.log            # thay "error" thành "ERROR", g = toàn bộ dòng (global), không chỉ lần đầu
sed -n '10,20p' app.log                    # -n tắt in mặc định, 'p' chỉ in dòng 10 đến 20 — cách nhanh để xem 1 đoạn cụ thể trong file lớn
sed '/DEBUG/d' app.log                      # xoá (delete) mọi dòng chứa "DEBUG" khỏi output
sed -i.bak 's/old_ip/new_ip/g' config.conf   # -i: SỬA TRỰC TIẾP vào file; .bak tự tạo bản backup trước khi sửa — RẤT NÊN dùng kèm .bak
```

> **Cảnh báo an toàn (liên hệ Bài 1 — Safety):** `sed -i` mà **không kèm `.bak`** sẽ sửa file gốc ngay lập tức, không thể hoàn tác nếu regex viết sai. Luôn test trước bằng `sed 's/.../.../ ' file` (không có `-i`) để xem output có đúng ý muốn chưa, rồi mới thêm `-i.bak` khi đã chắc chắn.

### 2.4. Bảng quyết định: dùng `grep`, `awk`, hay `sed`

| Câu hỏi cần trả lời | Công cụ phù hợp |
|---|---|
| "Có dòng nào chứa chuỗi X không, ở đâu?" | `grep` |
| "Lấy đúng cột Y, có thể kèm điều kiện/tính toán" | `awk` |
| "Thay thế/xoá nội dung trong file hoặc trong luồng dữ liệu" | `sed` |
| "Chỉ cần cắt 1 cột theo delimiter cố định, không cần điều kiện" | `cut` (đơn giản hơn `awk` cho việc này) |

### 2.5. Ví dụ thực chiến — kết hợp pipeline nhiều lệnh

```bash
journalctl -u nginx --since today | grep -i error | awk '{print $1, $2, $3}' | sort | uniq -c | sort -rn
```

Đọc từ trái sang phải: lấy log hôm nay của nginx → chỉ giữ dòng có "error" → cắt lấy 3 cột đầu (thường là ngày/giờ) → sắp xếp → đếm số dòng trùng nhau (`uniq -c`) → sắp theo số lượng giảm dần. Kết quả: **bảng thống kê lỗi xảy ra nhiều nhất theo mốc thời gian**, hoàn toàn không cần công cụ giám sát chuyên dụng.

## 3. Bash Scripting — Nền tảng chuyên sâu

### 3.1. Cấu trúc cơ bản

```bash
#!/bin/bash
# Dòng đầu (shebang) báo cho hệ thống biết dùng interpreter nào để chạy file này
```

```bash
chmod +x script.sh      # bắt buộc phải có quyền execute (liên hệ Bài 3 — Permissions) để chạy trực tiếp ./script.sh
```

### 3.2. Biến và quoting

```bash
NAME="Alice"
echo "Xin chào $NAME"        # nên LUÔN đặt biến trong "..." để tránh lỗi word-splitting khi giá trị có khoảng trắng
FILES=$(ls *.txt)             # KHÔNG an toàn nếu tên file có khoảng trắng — dùng array thay thế nếu cần chính xác
```

> **Liên hệ Bài 4 (biến môi trường):** biến khai báo bình thường (`NAME="Alice"`) chỉ tồn tại trong script/shell hiện tại; muốn tiến trình con (ví dụ script gọi 1 script khác) thấy được, phải `export NAME` — đúng nguyên tắc local vs environment variable đã học.

### 3.3. Tham số truyền vào script (Arguments)

```bash
#!/bin/bash
echo "Tên script: $0"
echo "Tham số 1: $1"
echo "Tham số 2: $2"
echo "Tổng số tham số: $#"
echo "Toàn bộ tham số: $@"

shift          # "đẩy" $2 thành $1, $3 thành $2... hữu ích khi xử lý số lượng tham số không cố định
```

### 3.4. Điều kiện (Conditionals)

```bash
if [ "$STATUS" = "active" ]; then
    echo "Service đang chạy"
elif [ "$STATUS" = "failed" ]; then
    echo "Service bị lỗi"
else
    echo "Trạng thái không xác định"
fi
```

| So sánh | Dùng cho |
|---|---|
| `[ "$a" = "$b" ]` | So sánh chuỗi (string) |
| `[ "$a" -eq "$b" ]` | So sánh số (numeric) — `-eq`, `-ne`, `-gt`, `-lt`, `-ge`, `-le` |
| `[[ "$a" == *.txt ]]` | `[[ ]]` (bash mở rộng) hỗ trợ pattern matching, an toàn hơn `[ ]` với biến rỗng/có khoảng trắng |

### 3.5. Vòng lặp (Loops)

```bash
for i in 1 2 3; do echo "Số: $i"; done

for f in /var/log/*.log; do
    echo "Đang xử lý: $f"
done

while read -r line; do
    echo "Dòng: $line"
done < input.txt

until systemctl is-active --quiet nginx; do
    echo "Đang chờ nginx khởi động..."
    sleep 2
done
```

### 3.6. Hàm (Functions)

```bash
check_disk() {
    local threshold=$1     # "local" để biến này KHÔNG rò ra ngoài phạm vi hàm — thực hành tốt, tránh xung đột biến
    local usage=$(df -h / | awk 'NR==2 {print $5}' | tr -d '%')
    if [ "$usage" -ge "$threshold" ]; then
        return 1            # hàm trong bash chỉ "return" được EXIT CODE (số 0-255), không return được giá trị như hàm bình thường
    fi
    return 0
}

if check_disk 80; then
    echo "Disk OK"
else
    echo "Disk cảnh báo"
fi
```

> **Điểm hay nhầm:** Hàm bash **không `return` giá trị dữ liệu** như Python/JS — nó chỉ trả về exit code (0–255). Muốn "trả về" một giá trị dữ liệu thật (ví dụ 1 chuỗi), phải dùng `echo` trong hàm rồi bắt kết quả bằng `$(function_name)` ở nơi gọi.

### 3.7. Xử lý lỗi trong script — thực hành chuyên nghiệp

```bash
#!/bin/bash
set -e            # dừng NGAY nếu bất kỳ lệnh nào exit với mã khác 0 — tránh script "chạy tiếp" khi 1 bước đã lỗi
set -u            # báo lỗi nếu dùng biến CHƯA được khai báo — bắt lỗi typo tên biến
set -o pipefail    # trong 1 pipeline (lệnh | lệnh), lấy exit code của lệnh LỖI ĐẦU TIÊN, không chỉ lệnh cuối

trap 'echo "Lỗi tại dòng $LINENO"; exit 1' ERR      # tự động chạy khi có lỗi xảy ra ở bất kỳ dòng nào
trap 'echo "Script kết thúc, dọn dẹp..."' EXIT        # luôn chạy khi script kết thúc, dù thành công hay lỗi — chỗ tốt để cleanup file tạm
```

> **Ví dụ thực chiến vì sao `pipefail` quan trọng:** `cat khong_ton_tai.txt | grep "abc"` — nếu không có `pipefail`, exit code của cả pipeline là exit code của `grep` (có thể là 0 hoặc 1 tuỳ có tìm thấy "abc" không), **che khuất hoàn toàn** việc `cat` đã lỗi vì file không tồn tại. Với `set -o pipefail`, exit code sẽ đúng là lỗi từ `cat`.

### 3.8. Đọc input

```bash
read -p "Nhập tên service: " SERVICE_NAME     # đọc input tương tác từ người dùng

cat <<EOF > config.txt                          # here-doc — ghi nhiều dòng văn bản vào file mà không cần nhiều lệnh echo
Server: $SERVICE_NAME
Date: $(date)
EOF
```

### 3.9. Các cặp khái niệm dễ nhầm nhất khi viết Bash script

**a) `./script.sh` vs `source script.sh` (hoặc `. script.sh`)**

| | `./script.sh` | `source script.sh` |
|---|---|---|
| Chạy ở đâu | Mở 1 **subshell (tiến trình con) mới** để chạy | Chạy **ngay trong shell hiện tại**, không tạo tiến trình mới |
| Biến/`cd` có ảnh hưởng ra ngoài không | **Không** — mọi biến, `cd`, `export` chỉ tồn tại trong subshell, mất hết khi script kết thúc | **Có** — mọi thay đổi (biến, thư mục hiện tại) áp dụng luôn vào shell đang gõ lệnh |
| Dùng khi nào | Chạy 1 script độc lập, không cần ảnh hưởng đến shell hiện tại | Nạp file cấu hình biến môi trường (`source ~/.bashrc`, `source venv/bin/activate`) — cần thay đổi áp dụng ngay vào shell hiện tại |

> **Lỗi thực chiến kinh điển:** Viết 1 script để `cd` vào thư mục project rồi `export` vài biến, chạy bằng `./setup.sh` — xong quay lại gõ `pwd` thấy vẫn ở thư mục cũ, biến `export` cũng biến mất. Nguyên nhân: script chạy trong subshell riêng, mọi thay đổi "chết" theo subshell đó. Phải chạy `source setup.sh` mới đúng ý muốn.

**b) `exit` vs `return`**

| | `exit N` | `return N` |
|---|---|---|
| Phạm vi ảnh hưởng | Kết thúc **toàn bộ script/shell** ngay lập tức | Chỉ kết thúc **hàm hiện tại**, script vẫn tiếp tục chạy các dòng sau |
| Dùng ngoài hàm | Hợp lệ, kết thúc script | Lỗi ("return: can only \`return' from a function or sourced script") |
| Giá trị trả về | Exit code của **cả script** (`echo $?` sau khi script chạy xong) | Exit code của **riêng hàm đó** (`echo $?` ngay sau khi gọi hàm) |

> **Lỗi thực chiến:** Gọi nhầm `exit 1` bên trong 1 hàm helper (tưởng chỉ muốn thoát hàm) — kết quả là **toàn bộ script dừng luôn**, các bước dọn dẹp/log phía sau không chạy nữa. Nếu chỉ muốn báo lỗi trong phạm vi hàm rồi để script tự quyết định tiếp theo, phải dùng `return`.

**c) `$@` vs `$*`**

Cả hai đều đại diện cho "toàn bộ tham số truyền vào script", nhưng khác nhau khi đặt trong dấu ngoặc kép — điểm này chỉ lộ ra khi tham số có khoảng trắng:

```bash
# Giả sử chạy: ./script.sh "file one.txt" "file two.txt"
for f in "$@"; do echo "[$f]"; done
# In ra: [file one.txt]  [file two.txt]  — ĐÚNG, mỗi tham số giữ nguyên như lúc truyền vào

for f in "$*"; do echo "[$f]"; done
# In ra: [file one.txt file two.txt]  — SAI Ý MUỐN, toàn bộ tham số bị gộp thành 1 chuỗi duy nhất
```

> **Quy tắc thực chiến:** Gần như luôn dùng `"$@"` (có ngoặc kép) khi cần lặp qua từng tham số riêng biệt — đây là lý do bạn sẽ thấy `"$@"` xuất hiện rất nhiều trong script chuyên nghiệp, còn `$*`/`"$*"` chỉ hữu ích khi cố ý muốn gộp tất cả thành 1 chuỗi (ví dụ để log lại toàn bộ câu lệnh đã gọi).

**d) Hard link vs Symbolic link — liên hệ trực tiếp `tar`/backup (mục 6)**

| | Hard link (`ln`) | Symbolic link (`ln -s`) |
|---|---|---|
| Bản chất | Một tên gọi **khác trỏ đến CÙNG inode** — về bản chất là cùng 1 file vật lý | Một file **riêng biệt**, chỉ chứa đường dẫn trỏ đến file gốc |
| Xoá file gốc thì sao | Hard link **vẫn còn dữ liệu** (vì dữ liệu chỉ mất khi *không còn tên nào* trỏ tới inode đó) | Symlink trở thành "broken link" (trỏ đến nơi không còn tồn tại) |
| Trỏ qua được filesystem/partition khác không | Không — phải cùng 1 filesystem | Có — trỏ được đến bất kỳ đường dẫn nào, kể cả khác ổ đĩa |
| Trỏ đến thư mục được không | Không (hầu hết hệ thống chặn) | Có |
| Ảnh hưởng khi `tar`/`rsync` | `tar` có thể phát hiện và lưu hard link hiệu quả (không nhân đôi dữ liệu) nếu dùng đúng tuỳ chọn | `tar`/`rsync` mặc định chỉ backup được **đường dẫn của symlink**, không tự theo (`-h`/`-L`) để backup nội dung file thật nếu không khai báo rõ |

> **Ví dụ thực chiến:** Backup một thư mục chứa nhiều symlink trỏ ra ngoài (ví dụ symlink đến file cấu hình dùng chung) bằng `tar` mặc định — bản backup chỉ lưu lại "đường dẫn trỏ đến đâu", **không lưu nội dung file thật**. Nếu restore trên máy khác không có đúng file gốc ở đúng đường dẫn đó, symlink trong bản backup sẽ "chết" (broken). Cần dùng `tar -h` (dereference — theo symlink để backup nội dung thật) nếu muốn bản backup tự đầy đủ, độc lập với môi trường gốc.



## 4. Exit Codes — Đào sâu

### 4.1. Quy ước chuẩn (0–255)

| Exit code | Ý nghĩa |
|---|---|
| `0` | Thành công |
| `1` | Lỗi chung (generic error) — hay dùng nhất cho lỗi tự định nghĩa |
| `2` | Cú pháp/cách dùng lệnh sai (misuse of shell builtin) |
| `126` | File tồn tại nhưng **không có quyền thực thi** (liên hệ Bài 3 — Permissions) |
| `127` | Lệnh **không tồn tại** ("command not found" — liên hệ vấn đề PATH đã học ở Bài 4/5) |
| `128 + N` | Tiến trình bị kết thúc bởi signal số N (liên hệ Bài 4 — Signals). Ví dụ `SIGKILL` = 9 → exit code `137` |
| `130` | Bị dừng bởi `Ctrl+C` (SIGINT = 2, nên 128+2 = 130) |

```bash
echo $?          # luôn xem exit code của LỆNH VỪA CHẠY NGAY TRƯỚC ĐÓ — nếu chạy lệnh khác ở giữa, giá trị này sẽ mất
```

> **Ví dụ thực chiến chẩn đoán qua exit code:** Một service trong container/systemd báo exit code `137` — không cần đọc log cũng biết ngay: tiến trình đã bị `SIGKILL` (128+9), rất thường gặp khi **OOM Killer** (liên hệ Bài 4 — Memory Internals) can thiệp vì hệ thống hết RAM, hoặc do Docker/orchestrator chủ động kill vì vượt memory limit.

### 4.2. Kết hợp lệnh dựa trên exit code

```bash
mkdir /data && cd /data          # && chỉ chạy lệnh sau nếu lệnh trước THÀNH CÔNG (exit 0)
ping -c 1 8.8.8.8 || echo "Mất kết nối"     # || chỉ chạy lệnh sau nếu lệnh trước THẤT BẠI (exit khác 0)
command1; command2                 # ; luôn chạy command2 KHÔNG QUAN TÂM command1 thành công hay không
```

### 4.3. Thiết kế exit code có ý nghĩa cho script tự viết

Thay vì luôn `exit 1` cho mọi loại lỗi, script chuyên nghiệp nên phân biệt loại lỗi bằng exit code khác nhau — giúp hệ thống giám sát/escalation tự động biết cần làm gì tiếp theo mà không cần đọc log:

```bash
exit 0    # OK
exit 1    # Lỗi cấu hình
exit 2    # Lỗi kết nối mạng
exit 3    # Lỗi dung lượng đĩa
```

> **Liên hệ trực tiếp phần thực hành Bài 5:** Script `health_check.sh` ở Bài 5 chỉ dùng `exit 0`/`exit 1` đơn giản — ở Bài 6 này, phần thực hành sẽ **nâng cấp** để phân biệt rõ loại lỗi qua exit code khác nhau.

## 5. Cron — Đào sâu (mở rộng Bài 5)

### 5.1. Cú pháp đầy đủ và special strings

```
┌───────────── phút (0-59)
│ ┌─────────── giờ (0-23)
│ │ ┌───────── ngày trong tháng (1-31)
│ │ │ ┌─────── tháng (1-12)
│ │ │ │ ┌───── ngày trong tuần (0-6, 0=Chủ Nhật)
│ │ │ │ │
* * * * *  lệnh_cần_chạy
```

```bash
@reboot   /path/to/script.sh      # chạy 1 lần mỗi khi hệ thống boot lên
@daily    /path/to/script.sh      # tương đương "0 0 * * *"
@hourly   /path/to/script.sh      # tương đương "0 * * * *"
```

### 5.2. User crontab vs System crontab

| Loại | Vị trí | Đặc điểm |
|---|---|---|
| User crontab | `crontab -e` (mỗi user riêng) | Chạy với quyền của user đó |
| `/etc/crontab` | File hệ thống | Có thêm cột **user** (chỉ định chạy bằng ai) trước phần lệnh |
| `/etc/cron.d/` | Thư mục | Nơi package (ví dụ `sysstat` cho `sar` đã học ở Bài 2) tự thêm cron job riêng khi cài đặt |

### 5.3. Kiểm soát ai được dùng cron (liên hệ Bài 3 — Least Privilege)

```bash
/etc/cron.allow    # nếu tồn tại, CHỈ user trong danh sách này mới dùng được crontab
/etc/cron.deny      # nếu /cron.allow không có, mọi user KHÔNG có trong /cron.deny đều dùng được crontab
```

> **Ví dụ thực chiến bảo mật:** Trên server nhiều user dùng chung, muốn chỉ cho phép `devops` và `backup-user` tự lên lịch cron, thêm đúng 2 tên đó vào `/etc/cron.allow` — mọi user khác gõ `crontab -e` sẽ bị từ chối, đúng nguyên tắc least-privilege đã học.

### 5.4. Ghi log output của cron job

Mặc định, output của cron job **không hiện ra đâu cả** trừ khi bạn tự redirect — đây là lỗi rất hay gặp ("tôi đặt cron nhưng không biết nó có chạy không"):

```
*/15 * * * * /usr/local/bin/health_check.sh >> /var/log/health_check_cron.log 2>&1
```

`2>&1` gộp stderr vào cùng luồng với stdout, để cả output thường và lỗi đều được ghi vào log — thiếu dòng này, lỗi script sẽ **biến mất hoàn toàn**, không để lại dấu vết.

### 5.5. So sánh cron vs systemd timer (mở rộng bảng đã có ở Bài 5)

| | cron | systemd timer |
|---|---|---|
| Log | Phải tự redirect, dễ quên | Tự động vào `journalctl` |
| Chạy khi máy đang tắt lúc đến giờ | Bỏ lỡ hoàn toàn | `Persistent=true` giúp chạy bù ngay khi máy mở lại |
| Dependency với service khác | Không hỗ trợ | `After=`, `Requires=` như service thường |
| Độ phổ biến/đơn giản | Rất phổ biến, cú pháp ai cũng quen | Cần học thêm cú pháp unit file |
| Phù hợp cho | Task đơn giản, môi trường cũ | Hệ thống hiện đại, cần tích hợp giám sát/log tốt |

> **`anacron` — trường hợp đặc biệt đáng biết:** Trên laptop/máy không phải lúc nào cũng mở, `cron` thường sẽ **bỏ lỡ hẳn** job nếu máy đang tắt đúng giờ chạy. `anacron` giải quyết vấn đề này bằng cách chạy bù job bị lỡ ngay khi máy khởi động lại — cấu hình qua `/etc/cron.daily`, `/etc/cron.weekly`.

## 6. Backup — `tar` vs `rsync`

### 6.1. `tar` — đóng gói/nén (đã dùng ở Bài 5, giờ đào sâu thêm)

```bash
tar -czvf backup.tar.gz /etc/nginx        # c=create, z=gzip, v=verbose, f=tên file output
tar -xzvf backup.tar.gz -C /restore/path    # x=extract, -C=giải nén vào đúng thư mục chỉ định
tar -tzvf backup.tar.gz                      # t=chỉ LIỆT KÊ nội dung, KHÔNG giải nén — dùng để kiểm tra trước khi restore
tar --listed-incremental=snapshot.file -czf backup_incr.tar.gz /data   # tar incremental: chỉ backup phần thay đổi so với snapshot trước
```

### 6.2. `rsync` — đồng bộ hiệu quả, hỗ trợ incremental tự nhiên

```bash
rsync -avz /source/ /destination/                  # -a: archive (giữ permission, symlink, timestamp...), -v: verbose, -z: nén khi truyền
rsync -avz --delete /source/ /destination/            # --delete: xoá ở đích những gì không còn ở nguồn — ĐỒNG BỘ THẬT (mirror), không chỉ thêm mới
rsync -avz --dry-run /source/ /destination/             # xem TRƯỚC những gì sẽ thay đổi mà KHÔNG THỰC THI — luôn nên chạy bước này trước, đặc biệt khi dùng --delete
rsync -avz -e ssh /source/ user@remote_host:/destination/    # đồng bộ qua SSH đến server khác — kế thừa trực tiếp kiến thức SSH key ở Bài 3
```

> **Cơ chế delta-transfer:** `rsync` chỉ truyền đi **phần dữ liệu thay đổi** trong file (không phải toàn bộ file) khi file đích đã có một phiên bản gần giống — đây là lý do lần sync thứ 2 trở đi luôn nhanh hơn nhiều so với `tar` (luôn phải đóng gói lại toàn bộ từ đầu).

> **Cảnh báo an toàn nghiêm trọng với `--delete`:** Gõ nhầm chiều nguồn/đích khi dùng `rsync -avz --delete` có thể **xoá sạch dữ liệu ở phía tưởng là "đích"** nhưng thực ra lại là dữ liệu thật. Luôn `--dry-run` trước, và luôn kiểm tra kỹ dấu `/` ở cuối đường dẫn (`/source` vs `/source/` cho kết quả khác nhau về việc có tạo thêm 1 cấp thư mục con hay không).

### 6.3. Bảng so sánh quyết định `tar` vs `rsync`

| Tiêu chí | `tar` | `rsync` |
|---|---|---|
| Kết quả | 1 file nén duy nhất (`.tar.gz`) | Bản sao thư mục/file, giữ nguyên cấu trúc |
| Phù hợp cho | Lưu trữ lạnh (cold storage), gửi đi 1 nơi khác, archive theo mốc thời gian | Đồng bộ định kỳ, backup incremental thường xuyên, đồng bộ giữa 2 server |
| Tốc độ lần 2 trở đi | Luôn chậm như lần đầu (đóng gói lại toàn bộ) | Nhanh hơn nhiều nhờ delta-transfer |
| Dễ restore 1 file lẻ | Phải giải nén cả gói rồi tìm | Trực tiếp copy lại đúng file cần, không cần giải nén |
| Rủi ro lớn nhất | Quên kiểm tra dung lượng trước khi tạo | `--delete` xoá nhầm nếu sai hướng nguồn/đích |

### 6.4. Ví dụ thực chiến kết hợp cả 2

Một chiến lược backup thực tế phổ biến: dùng `rsync` để đồng bộ liên tục dữ liệu sang 1 server backup (nhanh, incremental) **và** định kỳ (ví dụ hàng tuần) chạy `tar` để tạo 1 bản snapshot đóng gói cố định lưu trữ lâu dài — kết hợp tốc độ của `rsync` với tính "đóng băng tại 1 thời điểm" dễ lưu trữ lạnh của `tar`.

## 7. Linux Consolidation — Tổng hợp lại toàn bộ Bài 1–6

### 7.1. Bản đồ liên kết kiến thức

| Bài | Chủ đề | Liên kết với các bài khác |
|---|---|---|
| 1 | Role, Scope, Safety, Evidence, Runbook, Lab Readiness | Nguyên tắc bao trùm mọi bài sau |
| 2 | Kiến trúc, distro, CLI, file, text processing, monitoring | Nền tảng thao tác — Bài 6 đào sâu lại `grep`/`awk`/`sed` |
| 3 | Users, groups, permissions, sudo, SSH key | Least-privilege — áp dụng lại ở firewall (Bài 5), cron.allow (Bài 6) |
| 4 | Processes, signals, systemd, packages, env vars | Signal → exit code 128+N (Bài 6); env var → vấn đề PATH trong cron (Bài 6) |
| 5 | Networking, storage, health-check/backup, escalation | Nền cho script nâng cấp ở Bài 6 (rsync, exit code chi tiết hơn) |
| 6 | Logs, text processing sâu, bash scripting, exit codes, cron sâu, tar/rsync | Tổng hợp toàn bộ, viết được script hoàn chỉnh, tự chẩn đoán từ log đến khôi phục |

### 7.2. Tình huống thực chiến tổng hợp (end-to-end)

**Kịch bản:** Lúc 3h sáng, hệ thống giám sát báo website không phản hồi.

1. **Bài 5 — Networking:** `curl -I https://myapp.com` → timeout. `ss -tulpn | grep :443` trên server → không thấy listen.
2. **Bài 4 — Systemd:** `systemctl status nginx` → `failed`. `journalctl -u nginx -n 50` (Bài 6 — đào sâu journalctl) → thấy log lỗi.
3. **Bài 6 — Text processing:** `journalctl -u nginx --since "-1hour" | grep -i "error\|fatal"` → lọc đúng dòng lỗi cốt lõi giữa hàng trăm dòng log.
4. **Bài 4 — Memory Internals + Bài 6 — Exit codes:** Thấy exit code `137` trong log → nghi ngờ OOM Killer. Xác nhận bằng `dmesg | grep -i "out of memory"` (Bài 2).
5. **Bài 5 — Capacity:** `free -h`, `df -h` → xác nhận RAM đã cạn do 1 process khác (không phải nginx) chiếm dụng.
6. **Bài 3 — Permissions/Sudo:** Dùng `sudo kill -15 <pid_process_lỗi>` để dừng đúng tiến trình gây ngập RAM (ưu tiên SIGTERM trước — Bài 4).
7. **Bài 4 — Systemd:** `systemctl restart nginx` → xác nhận `active (running)`.
8. **Bài 5 — Networking:** `curl -I https://myapp.com` → `200 OK`, xác nhận đã khôi phục.
9. **Bài 1 — Evidence + Bài 6 — Escalation:** Ghi lại toàn bộ log, lệnh đã chạy, và viết escalation entry đầy đủ — bao gồm cả đề xuất dài hạn (ví dụ: thêm giới hạn memory cho process gây lỗi, hoặc thêm cảnh báo RAM sớm hơn vào script health-check).

> Đây chính là lý do các bài học được thiết kế nối tiếp nhau — một sự cố thực tế hiếm khi chỉ cần đúng 1 kỹ năng, mà là **chuỗi chẩn đoán đi qua nhiều tầng** (network → service → OS resource → process → quyền hạn → khôi phục → ghi nhận).

## 8. Log Analytics — Đào sâu phân tích log

Mục 2 đã học cách **tìm** đúng dòng log cần thiết. Log Analytics đi xa hơn: biến hàng nghìn dòng log rời rạc thành **số liệu và xu hướng** có thể ra quyết định được.

### 8.1. Tính tỷ lệ lỗi theo khung thời gian (Error Rate Over Time)

```bash
journalctl -u nginx --since "-6hours" -o short-iso | \
  awk '{print substr($1,1,13)}' | \
  sort | uniq -c
```

Lệnh trên cắt lấy đúng phần "giờ" (`substr($1,1,13)` — lấy đến giờ, bỏ phút/giây) từ timestamp mỗi dòng log, rồi đếm số dòng log xuất hiện trong mỗi giờ — cho ra 1 bảng phân bố log theo giờ, tương tự nguyên lý pipeline `journalctl | grep | awk | sort | uniq -c` đã học ở mục 2.5, nhưng áp dụng cho **toàn bộ log** thay vì chỉ lỗi, để so sánh **tỷ lệ** lỗi/tổng log thay vì chỉ đếm số lỗi tuyệt đối (100 lỗi trong 100,000 request khác hẳn ý nghĩa với 100 lỗi trong 200 request).

```bash
TOTAL=$(journalctl -u nginx --since "-1hour" | wc -l)
ERRORS=$(journalctl -u nginx --since "-1hour" | grep -ci "error")
awk -v t="$TOTAL" -v e="$ERRORS" 'BEGIN { printf "Error rate: %.2f%%\n", (e/t)*100 }'
```

### 8.2. Top-N Analysis — tìm "thủ phạm" xuất hiện nhiều nhất

```bash
# Top 10 IP gọi đến nhiều nhất trong access log
awk '{print $1}' access.log | sort | uniq -c | sort -rn | head -10

# Top 5 loại lỗi xuất hiện nhiều nhất (giả sử lỗi có dạng "ERROR: <message>")
grep "ERROR" app.log | awk -F': ' '{print $2}' | sort | uniq -c | sort -rn | head -5
```

> **Mẫu pipeline `sort | uniq -c | sort -rn | head -N` chính là "công thức Top-N" áp dụng được cho gần như mọi loại log** — đáng nhớ vì dùng lặp đi lặp lại trong thực chiến: đếm IP nhiều nhất, lỗi nhiều nhất, endpoint bị gọi nhiều nhất, user đăng nhập thất bại nhiều nhất...

### 8.3. Tương quan log qua nhiều service (Correlation)

Khi 1 request đi qua nhiều service (ví dụ: load balancer → app server → database), việc lần theo đúng 1 request cụ thể qua tất cả các log riêng biệt cần một **điểm chung để nối lại** — thường là **timestamp khớp khoảng** hoặc tốt hơn là **request ID/trace ID** nếu ứng dụng có ghi.

```bash
# Giả sử ứng dụng ghi kèm request ID dạng [req-abc123] trong mọi dòng log liên quan
grep "req-abc123" /var/log/lb/access.log /var/log/app/app.log /var/log/db/slow-query.log
```

> **Giới hạn thực chiến cần biết:** Nếu log của bạn **không có trường ID chung** để nối (nhiều hệ thống cũ chỉ có timestamp), việc tương quan chỉ có thể làm gần đúng bằng cách so khớp cửa sổ thời gian hẹp (`--since`/`--until` — mục 1.3), độ chính xác thấp hơn nhiều so với có sẵn trace ID xuyên suốt — đây là lý do các hệ thống hiện đại luôn khuyến khích ghi log có structured ID ngay từ đầu.

### 8.4. Phân tích log dạng JSON với `jq`

`journalctl -o json-pretty` (mục 1.3) hay log ứng dụng hiện đại thường xuất JSON — `awk`/`grep` xử lý JSON rất vụng về vì JSON không có cấu trúc cột cố định. Công cụ đúng cho việc này là `jq`:

```bash
journalctl -u myapp -o json --since today | jq -r 'select(.PRIORITY=="3") | .MESSAGE'   # chỉ lấy field MESSAGE của các dòng priority=error(3)
cat app.json.log | jq -r '.status_code' | sort | uniq -c | sort -rn                       # top-N status code từ log JSON, kết hợp lại với mẫu ở mục 8.2
```

> **So sánh nhanh — khi nào `awk`/`grep`, khi nào `jq`:** Log dạng text theo cột cố định (access log kiểu Apache/Nginx mặc định) → `awk`/`grep` là đủ và nhanh gọn. Log dạng JSON (phổ biến ở microservices, structured logging) → `jq` mới trích xuất đúng field một cách đáng tin cậy, vì `grep`/`awk` không hiểu được JSON có thể xuống dòng, escape ký tự, hay đổi thứ tự field.

### 8.5. Giới hạn của Log Analytics bằng shell — khi nào cần công cụ tập trung hoá

Pipeline `grep`/`awk`/`jq` rất mạnh cho chẩn đoán tức thời trên 1 server, nhưng có giới hạn rõ ràng:

| Nhu cầu | Shell pipeline (đã học) | Công cụ tập trung (ELK, CloudWatch Logs, Datadog...) |
|---|---|---|
| Log chỉ nằm trên 1 server | Đủ dùng, nhanh, không cần cài thêm gì | Không cần thiết, hơi thừa |
| Log trải trên hàng chục/hàng trăm server | Phải SSH vào từng máy, không khả thi | Tự động gom log về 1 nơi, tìm kiếm tập trung |
| Cần dashboard/biểu đồ trực quan theo thời gian thực | Không hỗ trợ, chỉ ra số liệu tại 1 lần chạy | Có sẵn dashboard, alerting tự động |
| Cần lưu log lâu dài, tìm lại sau nhiều tháng | `journald`/`logrotate` có giới hạn dung lượng (mục 1.2/1.4) | Lưu trữ dài hạn, index sẵn để tìm nhanh |

> **Liên hệ thực chiến:** Kỹ năng `grep`/`awk`/`jq` không "lỗi thời" khi có công cụ tập trung — chúng vẫn là công cụ **đầu tiên** dùng để debug nhanh ngay trên 1 server cụ thể (ví dụ SSH vào server đang gặp sự cố để xem log real-time), trong khi công cụ tập trung phù hợp hơn cho việc **giám sát toàn hệ thống** và tra cứu lịch sử dài hạn. Hai loại công cụ bổ trợ nhau, không thay thế nhau.

## 9. Recovery Validation — Tăng chiều sâu kiểm chứng khôi phục

Bài 5 (mục 12.2) đã giới thiệu restore drill cơ bản (kiểm tra 1 file cấu hình đã xuất hiện đúng vị trí). Mục này đào sâu: **"restore xong" và "recovery thành công" là 2 khái niệm khác nhau.**

### 9.1. "Restore" vs "Recovery" — cặp khái niệm dễ gộp chung

| | Restore | Recovery |
|---|---|---|
| Phạm vi | Đưa **dữ liệu/file** trở lại từ bản backup | Đưa **toàn bộ dịch vụ** trở lại trạng thái hoạt động bình thường, phục vụ được người dùng thật |
| Đủ để coi là hoàn tất khi nào | Khi file đã nằm đúng vị trí (`tar -xzf` chạy xong không lỗi) | Khi service **chạy đúng, kết nối đúng, trả lời đúng dữ liệu**, người dùng thật sự dùng lại được |
| Ví dụ thất bại dù bước trước "thành công" | File config restore xong nhưng **sai quyền** (Bài 3) khiến service không đọc được, hoặc file đúng nhưng **thiếu 1 dependency khác** (Bài 4) không được backup cùng | — |

> **Nguyên tắc cốt lõi của mục này:** Restore chỉ là **1 bước con** trong Recovery — một quy trình chỉ dừng lại ở "restore xong file" mà chưa xác minh **service thực sự hoạt động đúng** là một quy trình khôi phục **chưa hoàn chỉnh**, dù trông có vẻ đã xong.

### 9.2. Bốn tầng kiểm chứng Recovery Validation

| Tầng | Kiểm tra gì | Công cụ (liên hệ bài đã học) |
|---|---|---|
| 1. File-level | File có tồn tại đúng vị trí, đúng nội dung (không bị hỏng giữa chừng khi backup/restore) | `md5sum`/`sha256sum` so khớp checksum trước và sau |
| 2. Structural | Đúng owner, đúng permission (liên hệ Bài 3) | `ls -l`, `stat` |
| 3. Functional | Service khởi động được, không rơi vào trạng thái `failed`/crash loop (liên hệ Bài 4, mục 8) | `systemctl status`, `journalctl -u <service> -n 50` |
| 4. Data Integrity | Dữ liệu bên trong đúng và dùng được ở tầng ứng dụng, không chỉ "file tồn tại" | Query thử database, `curl` health endpoint (Bài 5), so sánh số bản ghi/checksum dữ liệu với thời điểm backup |

```bash
#!/bin/bash
# recovery_validate.sh — kiểm chứng đủ 4 tầng sau khi restore
# Liên hệ: Bài 3 (Permissions), Bài 4 (systemd), Bài 5 (curl health-check)

CONFIG_FILE="/etc/nginx/nginx.conf"
EXPECTED_CHECKSUM="d41d8cd98f00b204e9800998ecf8427e"   # checksum đã lưu TỪ LÚC BACKUP, dùng để so sánh

# Tầng 1 — File-level
ACTUAL_CHECKSUM=$(md5sum "$CONFIG_FILE" | awk '{print $1}')
if [ "$ACTUAL_CHECKSUM" != "$EXPECTED_CHECKSUM" ]; then
    echo "FAIL — Tầng 1 (File-level): checksum không khớp, file có thể bị hỏng"
    exit 1
fi

# Tầng 2 — Structural
OWNER=$(stat -c '%U' "$CONFIG_FILE")
if [ "$OWNER" != "root" ]; then
    echo "FAIL — Tầng 2 (Structural): owner sai, đang là $OWNER thay vì root"
    exit 1
fi

# Tầng 3 — Functional
if ! systemctl is-active --quiet nginx; then
    echo "FAIL — Tầng 3 (Functional): service không active sau restore"
    exit 1
fi

# Tầng 4 — Data Integrity
HTTP_CODE=$(curl -o /dev/null -s -w "%{http_code}" --connect-timeout 5 http://localhost/health)
if [ "$HTTP_CODE" != "200" ]; then
    echo "FAIL — Tầng 4 (Data Integrity): health endpoint không trả 200 sau restore"
    exit 1
fi

echo "PASS — Recovery Validation đủ 4 tầng, dịch vụ đã khôi phục hoàn chỉnh"
exit 0
```

> **Liên hệ trực tiếp Bài 5 (mục 12.1 — RTO):** Script này chính là công cụ để **đo RTO thực tế** một cách khách quan — RTO không nên tính từ lúc "gõ lệnh restore xong", mà nên tính đến đúng thời điểm **script Recovery Validation trả về PASS ở cả 4 tầng** — đó mới là lúc dịch vụ thật sự sẵn sàng phục vụ lại.

## 10. Full Operations Runbook — Runbook vận hành đầy đủ

### 10.1. Runbook vs Escalation Entry — cặp khái niệm dễ nhầm cuối cùng

Bài 1 đã giới thiệu cả 2 khái niệm riêng lẻ — giờ đặt cạnh nhau để thấy rõ chúng **bổ trợ nhau theo 2 chiều thời gian khác nhau**:

| | Runbook | Escalation Entry |
|---|---|---|
| Thời điểm tồn tại | Viết **TRƯỚC** khi sự cố xảy ra — chuẩn bị sẵn | Viết **SAU** khi sự cố đã xảy ra và được xử lý |
| Vai trò | "Nếu X xảy ra, làm theo các bước sau" — hướng dẫn hành động | "X đã xảy ra, đây là những gì đã quan sát và đã làm" — ghi nhận thực tế |
| Ai dùng | Người trực (on-call) dùng để **thực hiện** xử lý sự cố nhất quán | Người quản lý/đội khác dùng để **hiểu lại** những gì đã xảy ra, phục vụ post-mortem |

### 10.2. Cấu trúc chuẩn của 1 Full Operations Runbook

| Phần | Nội dung |
|---|---|
| **Purpose** | Runbook này xử lý đúng loại sự cố gì |
| **Scope** | Áp dụng cho service/môi trường nào (liên hệ Bài 1 — Scope) |
| **Pre-checks** | Quyền hạn cần có trước khi thực hiện (liên hệ Bài 3 — Access Boundary), công cụ cần sẵn sàng |
| **Detection** | Dấu hiệu nhận biết sự cố này (alert nào, log nào) |
| **Diagnosis Steps** | Các bước chẩn đoán theo thứ tự (liên hệ mục 8 — Log Analytics, và Bài 5 — Network Troubleshooting theo tầng) |
| **Remediation Steps** | Các bước khắc phục cụ thể, có lệnh chính xác |
| **Verification** | Cách xác nhận đã khắc phục xong — **bắt buộc dùng Recovery Validation (mục 9)**, không chỉ "nhìn có vẻ ổn" |
| **Rollback Plan** | Nếu bước khắc phục làm tình hình tệ hơn, cách quay lại trạng thái an toàn |
| **Escalation Path** | Nếu tự xử lý không được, liên hệ ai/team nào tiếp theo |

### 10.3. Ví dụ Full Operations Runbook hoàn chỉnh — "Web service ngừng phản hồi (HTTP 5xx tăng đột biến)"

```markdown
# RUNBOOK: Web Service HTTP 5xx Tăng Đột Biến

## Purpose
Xử lý tình huống web service trả về tỷ lệ lỗi 5xx cao bất thường, ảnh hưởng người dùng cuối.

## Scope
Áp dụng cho: nginx + app backend trên server production nhóm web-tier.
KHÔNG áp dụng cho: lỗi ở tầng database riêng (xem runbook DB riêng).

## Pre-checks
- Cần quyền: sudo trên server web-tier, quyền đọc CloudWatch/journalctl.
- Công cụ cần sẵn: SSH key đã cấu hình (Bài 3), AWS CLI profile đúng quyền (Bài 7).

## Detection
- Alert từ health-check script (Bài 5) báo exit code != 0.
- Hoặc CloudWatch Alarm (Bài 7-8) báo tỷ lệ lỗi 5xx > 5% trong 5 phút.

## Diagnosis Steps
1. `curl -I https://app.example.com` — xác nhận hiện tượng từ góc nhìn bên ngoài.
2. `systemctl status nginx app-backend` — kiểm tra trạng thái service (Bài 4, mục 8).
3. `journalctl -u app-backend --since "-15min" | grep -i "error\|exception"` — tìm log lỗi gần nhất (mục 2, mục 8.1).
4. Tính error rate theo mục 8.1 để xác định mức độ nghiêm trọng thực sự (không chỉ dựa cảm tính).
5. `free -h`, `df -h` — loại trừ nguyên nhân tài nguyên (Bài 5).

## Remediation Steps
- Nếu service ở trạng thái `failed`/crash loop (Bài 4, mục 8.3): `systemctl reset-failed app-backend && systemctl restart app-backend`.
- Nếu do thiếu tài nguyên: scale thêm instance (nếu có Auto Scaling — Bài 7) hoặc restart để giải phóng (liên hệ OOM Killer, Bài 4).
- Nếu do 1 bản deploy vừa gây lỗi: rollback về version trước.

## Verification
- Chạy `recovery_validate.sh` (mục 9.2) — PHẢI trả PASS đủ 4 tầng.
- Theo dõi error rate (mục 8.1) trở lại mức bình thường (<1%) trong ít nhất 10 phút liên tục trước khi coi là ổn định.

## Rollback Plan
- Nếu remediation làm tình hình tệ hơn: rollback code deploy gần nhất, hoặc chuyển traffic sang server dự phòng (nếu có).

## Escalation Path
- Nếu không xác định được nguyên nhân trong 30 phút → escalate lên Senior DevOps.
- Nếu nghi ngờ liên quan database → escalate sang DBA team, kèm evidence log đã thu thập ở bước Diagnosis.
- Ghi Escalation Entry đầy đủ (Bài 1, Bài 5 mục 14.4) ngay sau khi sự cố được xử lý xong, dù tự giải quyết được hay đã escalate.
```

> **Đây chính là "sản phẩm cuối cùng" của toàn bộ khoá học:** Một Runbook hoàn chỉnh như trên là nơi **hội tụ mọi kỹ năng đã học từ Bài 1 đến Bài 7-8** — Scope/Access Boundary (Bài 1/3), systemd troubleshooting (Bài 4), network/capacity diagnostics (Bài 5), log analytics (mục 8), recovery validation (mục 9), và cả AWS/CloudWatch nếu hạ tầng chạy trên cloud (Bài 7-8). Khả năng tự viết được 1 runbook như thế này — không chỉ theo runbook có sẵn — là dấu hiệu rõ ràng nhất cho thấy đã thật sự làm chủ kiến thức, không chỉ nhớ từng lệnh riêng lẻ.

---

# BÀI TẬP THỰC HÀNH — Nâng cấp Health-check/Backup, Journalctl, Bash Scripting, Cron, Inject Failure, Escalation

Thực hành trên VM/lab. **Bắt buộc ghi Command Log và Evidence.**

## Danh sách nhiệm vụ

1. Kiểm tra journal đang persistent hay volatile (`journalctl --disk-usage`, kiểm tra `/var/log/journal` có tồn tại không). Nếu chưa, bật persistent theo mục 1.2.
2. Chạy `journalctl -u <1 service đang chạy> --since today | grep -i error` — nếu không có lỗi thật, tạm dùng `grep -i info` để luyện cú pháp.
3. Viết 1 lệnh `awk` tính tổng dung lượng (cột size) của các file trong output `ls -l` (gợi ý: `ls -l *.log | awk '{sum+=$5} END {print sum}'`).
4. Dùng `sed` để thay thế 1 chuỗi trong 1 file test (không phải file hệ thống), có kèm `-i.bak`, xác nhận file `.bak` được tạo.
5. Nâng cấp `health_check.sh` từ Bài 5: đổi từ `exit 0/1` đơn giản sang **exit code phân loại** (0=OK, 1=lỗi cấu hình, 2=lỗi mạng, 3=lỗi dung lượng đĩa) theo mục 4.3.
6. Thêm `set -euo pipefail` vào đầu script, thêm 1 `trap ERR` để tự log khi có lỗi bất kỳ dòng nào.
7. Viết 1 hàm `check_network()` riêng trong script, dùng `local` cho biến nội bộ, trả về đúng exit code tương ứng.
8. Đổi phần backup trong Bài 5 từ `tar` sang **kết hợp `rsync`**: dùng `rsync -avz --dry-run` trước để xem thay đổi, sau đó chạy thật `rsync -avz` đồng bộ 1 thư mục test sang 1 thư mục đích khác trên cùng máy (hoặc sang VM khác qua SSH nếu có).
9. Lên lịch script health-check bằng cron, **nhớ thêm `>> logfile 2>&1`** để không mất log lỗi (mục 5.4) — xác nhận cron job chạy đúng bằng cách xem logfile sau vài phút.
10. Kiểm tra `/etc/cron.allow`/`/etc/cron.deny` trên hệ thống lab (nếu có) — ghi lại user nào được/không được dùng cron.
11. Cố ý gây lỗi mạng (ví dụ tạm chặn 1 IP bằng `iptables` như Bài 5 mục 14.2), chạy health-check, xác nhận đúng exit code `2` (lỗi mạng) theo phân loại mới.
12. Cố ý gây lỗi cấu hình (ví dụ sửa `HEALTH_URL` trong script thành URL sai), chạy lại, xác nhận đúng exit code `1`.
13. Dùng `journalctl` (nếu health-check log qua `logger`/journald) hoặc đọc trực tiếp log file, kết hợp `awk`/`grep` để đếm số lần script fail trong 1 khoảng thời gian.
14. Khôi phục lại toàn bộ (network, config), xác nhận health-check trả về `exit 0`.
15. Viết escalation entry hoàn chỉnh (theo mẫu Bài 5, mục 14.4) cho CẢ 2 lỗi đã gây ở bước 11 và 12, phân biệt rõ severity và exit code tương ứng của từng lỗi trong bản ghi.
16. Áp dụng mục 8.1–8.2: chạy pipeline tính error rate theo giờ và Top-5 lỗi xuất hiện nhiều nhất trên log của 1 service bất kỳ trong lab — ghi lại kết quả.
17. Viết script `recovery_validate.sh` theo mẫu mục 9.2 cho đúng service đang dùng trong lab, chạy thử ngay sau 1 lần restore config — xác nhận script phân biệt đúng PASS/FAIL ở từng tầng (thử cố tình làm sai 1 tầng, ví dụ đổi owner file, để xác nhận script bắt đúng lỗi tầng 2).
18. Dựa trên mẫu mục 10.3, tự viết 1 Full Operations Runbook hoàn chỉnh (đủ 9 phần) cho đúng 1 loại sự cố đã thực hành ở Bài 4/5/6 (ví dụ: "Disk gần đầy do log không rotate", hoặc "Service crash loop do sai cấu hình").

## Command Log & Escalation Evidence

| STT | Lệnh đã chạy | Mục đích | Output / Evidence | Thời gian |
|---|---|---|---|---|
| 1 |  |  |  |  |
| 2 |  |  |  |  |
| 3 |  |  |  |  |
| 4 |  |  |  |  |
| 5 |  |  |  |  |
| 6 |  |  |  |  |
| 7 |  |  |  |  |
| 8 |  |  |  |  |
| 9 |  |  |  |  |
| 10 |  |  |  |  |
| 11 |  |  |  |  |
| 12 |  |  |  |  |
| 13 |  |  |  |  |
| 14 |  |  |  |  |
| 15 |  |  |  |  |
| 16 |  |  |  |  |
| 17 |  |  |  |  |
| 18 |  |  |  |  |

> **Ghi chú:** Bước 11–15 là phần đánh giá quan trọng nhất — cần thể hiện được: (1) script phân loại đúng loại lỗi qua exit code khác nhau, không chỉ "pass/fail" chung; (2) log đầy đủ qua `journalctl`/log file; (3) escalation entry viết rõ, có thể dùng để người khác đọc lại và hiểu ngay chuyện gì đã xảy ra mà không cần hỏi thêm.
