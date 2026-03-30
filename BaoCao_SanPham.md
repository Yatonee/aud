# BÁO CÁO TỔNG QUAN SẢN PHẨM: ARE YOU ALIVE (BẠN ỔN KHÔNG)

## 1. Giới thiệu tổng quan về sản phẩm
**"Are You Alive" (Bạn ổn không)** là một ứng dụng di động trên nền tảng Android được thiết kế với mục đích bảo vệ và theo dõi an toàn cho những người sống một mình, đặc biệt là người cao tuổi hoặc người có vấn đề về sức khỏe. 

Cơ chế hoạt động của ứng dụng rất đơn giản: Mỗi ngày người dùng chỉ cần mở app và bấm nút **"Điểm danh"**. Nếu người dùng quên điểm danh, ứng dụng sẽ gửi thông báo nhắc nhở. Trong trường hợp xấu nhất, nếu quá số ngày quy định (do người dùng tự cài đặt) mà vẫn không có hoạt động điểm danh nào, ứng dụng sẽ **tự động gửi tin nhắn SMS khẩn cấp** đến danh sách người thân đã được thiết lập sẵn để họ kịp thời ứng cứu.

---

## 2. Danh sách các chức năng và Ảnh chụp màn hình

1. **Điểm danh an toàn hàng ngày:** Nút bấm điểm danh to, rõ ràng ở màn hình chính với hiệu ứng radar (pulse) thu hút sự chú ý.
2. **Theo dõi chuỗi ngày (Streak):** Ghi nhận và hiển thị số ngày liên tiếp người dùng đã điểm danh an toàn.
3. **Nhắc nhở tự động (Local Notification):** Tự động đẩy thông báo (Push Notification) về điện thoại theo khung giờ đã cài đặt để nhắc người dùng vào điểm danh.
4. **Cảnh báo khẩn cấp qua SMS:** Chạy ngầm và tự động gửi tin nhắn SMS đến các số điện thoại khẩn cấp nếu quá hạn điểm danh.
5. **Quản lý liên hệ khẩn cấp:** Cho phép thêm, sửa, xóa nhiều số điện thoại người thân.
6. **Tùy chỉnh linh hoạt:** Cài đặt giờ nhắc nhở hàng ngày và số ngày chờ (ví dụ: 2 ngày không điểm danh thì mới báo động).

*(Lưu ý: Các bạn thay thế đoạn text trong ngoặc vuông bằng ảnh chụp màn hình thực tế của app nhé)*
- **[Chèn ảnh màn hình chính - Nút điểm danh]**
- **[Chèn ảnh màn hình Cài đặt - Chọn giờ và ngày cảnh báo]**
- **[Chèn ảnh Popup quản lý danh sách Liên hệ khẩn cấp]**
- **[Chèn ảnh Thông báo nhắc nhở hiển thị trên màn hình khóa]**

---

## 3. Điểm khác biệt (USP) của sản phẩm so với các sản phẩm khác hiện có

- **Tối giản và thân thiện với người già:** Không có các tính năng thừa, giao diện chữ to, màu sắc tương phản cao, tập trung duy nhất vào nút "Điểm danh".
- **Hoạt động Offline 100% (Không phụ thuộc Internet):** Khác với các app dùng server/mạng xã hội, ứng dụng sử dụng **mạng viễn thông (SMS)** để gửi cảnh báo. Điều này đảm bảo tin nhắn khẩn cấp vẫn được gửi đi ngay cả khi điện thoại mất kết nối Wifi/4G hoặc người dùng ở khu vực sóng yếu chỉ có mạng 2G.
- **Tự động hóa hoàn toàn ở chế độ ngầm (Background):** Chỉ cần cài đặt một lần, hệ thống Alarm của app sẽ tự động tính toán thời gian và kích hoạt ngầm. Ngay cả khi người dùng khởi động lại điện thoại (Reboot), app vẫn tự động khôi phục lịch trình theo dõi mà không cần mở lại app.

---

## 4. Techstack sử dụng của hệ thống

- **Nền tảng:** Android Native
- **Ngôn ngữ lập trình:** Java, XML
- **Công cụ phát triển:** Android Studio
- **Lưu trữ dữ liệu:** SharedPreferences (Lưu trữ trạng thái cục bộ)
- **Định dạng dữ liệu:** JSON (Xử lý danh sách liên hệ)
- **Giao tiếp hệ thống:** Android SDK (SmsManager, NotificationManager, AlarmManager)

---

## 5. Danh sách các kiến thức của môn học đã được áp dụng vào sản phẩm

1. **Thiết kế Giao diện (UI/UX & Layouts):**
   - Sử dụng linh hoạt `ConstraintLayout`, `LinearLayout`, `CardView`, `ScrollView`.
   - Custom UI components: Tạo các file Drawable (Shape, Vector Asset) để làm góc bo tròn, icon, và hiệu ứng màu sắc.
   - Xử lý Animation: Sử dụng `ObjectAnimator` và `ScaleAnimation` để tạo hiệu ứng nhịp đập (pulse) và hiệu ứng nhấn nút (tap animation).

2. **Vòng đời Ứng dụng & Chuyển màn hình (Activity & Intent):**
   - Quản lý Activity Lifecycle.
   - Sử dụng `Intent` để chuyển đổi giữa `MainActivity` và `SettingsActivity`.

3. **Lưu trữ dữ liệu cục bộ (Data Storage):**
   - Sử dụng `SharedPreferences` để lưu trữ các cấu hình (giờ nhắc, số ngày, thời gian điểm danh cuối).
   - Áp dụng `JSONArray` và `JSONObject` để serialize/deserialize danh sách liên hệ khẩn cấp lưu vào SharedPreferences.

4. **Xử lý tác vụ chạy ngầm (Background Tasks & Services):**
   - **`AlarmManager`**: Lên lịch chính xác (Exact Alarms) cho các sự kiện nhắc nhở hàng ngày và sự kiện cảnh báo khẩn cấp.
   - **`BroadcastReceiver`**: 
     - Xây dựng `AlertReceiver` để lắng nghe sự kiện từ AlarmManager, từ đó kích hoạt gửi SMS hoặc hiển thị Notification khi app đang bị đóng.
     - Xây dựng `BootReceiver` lắng nghe action `BOOT_COMPLETED` để tự động lên lịch lại các Alarm sau khi điện thoại khởi động lại.

5. **Tương tác với phần cứng & Dịch vụ hệ thống:**
   - **`SmsManager`**: Gửi tin nhắn văn bản tự động (ẩn) đến các số điện thoại.
   - **`NotificationManager`**: Xây dựng Notification Channel và hiển thị thông báo đẩy (Push Notification) tương thích với các phiên bản Android mới (Android 13+).

6. **Quản lý Quyền (Runtime Permissions):**
   - Yêu cầu và xử lý các quyền nguy hiểm (Dangerous Permissions) tại runtime: `SEND_SMS`, `POST_NOTIFICATIONS`.
   - Khai báo các quyền hệ thống trong Manifest: `SCHEDULE_EXACT_ALARM`, `RECEIVE_BOOT_COMPLETED`, `READ_PHONE_STATE`.