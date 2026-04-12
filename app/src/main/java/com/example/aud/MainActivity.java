package com.example.aud;

import android.Manifest;
import android.app.AlarmManager;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.ScaleAnimation;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    public static final String PREFS_NAME = "aud_prefs";
    public static final String KEY_PHONE = "emergency_phone";
    public static final String KEY_USER_NAME = "user_name";
    public static final String KEY_LAST_CHECK_IN = "last_check_in";
    public static final String KEY_STREAK_COUNT = "streak_count";
    public static final String KEY_DAYS_BEFORE_ALERT = "days_before_alert";
    public static final String KEY_REMINDER_TIME = "reminder_time";
    public static final String KEY_CONTACTS_JSON = "emergency_contacts_json";
    public static final String KEY_EMERGENCY_COUNT = "emergency_count";
    private static final int PERMISSION_REQUEST_CODE = 123;
    private static final String DEFAULT_CONTACT_NAME = "Người thân"; // fallback cho migration dữ liệu cũ

    private EditText etUserName, etEmergencyPhone;
    private TextView tvLastCheckIn, tvNextCheckIn, tvStreak, tvCheckInStatus;
    private LinearLayout btnCheckIn;
    private ImageButton btnSettings;
    private ImageView btnEditContactsMain;
    private ImageView btnEditContactsPhoneMain;
    private View vPulse;

    private SharedPreferences prefs;
    private final List<ContactItem> contacts = new ArrayList<>();
    private long lastCheckInTime;
    private int streakCount;
    private boolean wasCheckedToday;

    /**
     * Áp dụng ngôn ngữ đã lưu trước khi activity được tạo
     */
    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.applyLanguage(newBase));
    }

    /**
     * Lifecycle: onCreate - Khởi tạo Activity
     *
     * Thiết lập layout, ánh xạ views, load dữ liệu, đặt alarm,
     * kiểm tra quyền, và thiết lập các event listener
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // EdgeToEdge: Hỗ trợ màn hình full edge-to-edge (không có navigation bar che content)
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        // Xử lý window insets để tránh bị che bởi status bar / navigation bar
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Ánh xạ các view từ layout XML vào biến Java
        etUserName = findViewById(R.id.etUserName);
        etEmergencyPhone = findViewById(R.id.etEmergencyPhone);
        tvLastCheckIn = findViewById(R.id.tvLastCheckIn);
        tvNextCheckIn = findViewById(R.id.tvNextCheckIn);
        tvStreak = findViewById(R.id.tvStreak);
        tvCheckInStatus = findViewById(R.id.tvCheckInStatus);
        btnCheckIn = findViewById(R.id.btnCheckIn);
        btnSettings = findViewById(R.id.btnSettings);
        btnEditContactsMain = findViewById(R.id.btnEditContactsMain);
        btnEditContactsPhoneMain = findViewById(R.id.btnEditContactsPhoneMain);
        vPulse = findViewById(R.id.vPulse);

        // Khởi tạo SharedPreferences để đọc/ghi dữ liệu cục bộ
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        // Load dữ liệu đã lưu vào các biến thành viên
        loadData();
        
        // Đặt alarm nhắc nhở và alarm khẩn cấp dựa trên prefs hiện tại
        AlarmScheduler.scheduleFromPrefs(this);
        
        // Kiểm tra và xin quyền SMS + Notification nếu chưa có
        checkAndRequestPermissions();
        
        // Cập nhật UI với dữ liệu đã load
        updateUI();
        
        // Khóa các trường input để chỉ hiển thị (click để sửa)
        lockUserNameEditing();
        lockPhoneFieldEditing();

        // TextWatcher: Tự động lưu tên người dùng khi người dùng gõ
        etUserName.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            
            // Sau khi text thay đổi, lưu ngay vào SharedPreferences
            @Override public void afterTextChanged(Editable s) {
                prefs.edit().putString(KEY_USER_NAME, s.toString()).apply();
            }
        });
        
        // Xử lý khi người dùng nhấn Done trên bàn phím ảo
        etUserName.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                lockUserNameEditing();  // Khóa lại sau khi xong
                hideKeyboard(v);         // Ẩn bàn phím
                return true;
            }
            return false;
        });

        // TextWatcher: Tự động lưu số điện thoại khẩn cấp khi người dùng gõ
        etEmergencyPhone.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                prefs.edit().putString(KEY_PHONE, s.toString()).apply();
            }
        });

        // Xử lý sự kiện click nút điểm danh
        btnCheckIn.setOnClickListener(v -> {
            animateTap(btnCheckIn);    // Animation nhấn nút
            performCheckIn();          // Thực hiện điểm danh
        });
        
        // Xử lý nút mở màn hình cài đặt
        btnSettings.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
            startActivity(intent);
        });

        // Nút chỉnh sửa tên người dùng - mở khóa để edit
        btnEditContactsMain.setOnClickListener(v -> {
            animateTap(btnEditContactsMain);
            unlockUserNameEditing();
        });
        
        // Nút chỉnh sửa liên hệ khẩn cấp - hiện popup quản lý liên hệ
        btnEditContactsPhoneMain.setOnClickListener(v -> {
            animateTap(btnEditContactsPhoneMain);
            showContactsPopup();
        });

        // Khi mất focus khỏi trường tên -> khóa lại
        etUserName.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                lockUserNameEditing();
            }
        });
    }

    /**
     * Kiểm tra và xin các quyền cần thiết từ người dùng
     * 
     * Quyền cần thiết:
     * - SEND_SMS: Để gửi SMS khẩn cấp
     * - POST_NOTIFICATIONS (Android 13+): Để hiện thông báo
     * - SCHEDULE_EXACT_ALARM (Android 12+): Để đặt alarm chính xác
     */
    private void checkAndRequestPermissions() {
        // Kiểm tra quyền gửi SMS và notification
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && 
             ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)) {
            
            // Xây dựng danh sách quyền cần xin dựa trên Android version
            String[] permissions;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissions = new String[]{Manifest.permission.SEND_SMS, Manifest.permission.POST_NOTIFICATIONS};
            } else {
                permissions = new String[]{Manifest.permission.SEND_SMS};
            }
            ActivityCompat.requestPermissions(this, permissions, PERMISSION_REQUEST_CODE);
        }

        // Android 12+: Kiểm tra quyền đặt exact alarm
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            if (alarmManager != null && !alarmManager.canScheduleExactAlarms()) {
                // Mở settings để người dùng bật quyền
                Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                startActivity(intent);
            }
        }
    }

    /**
     * Bắt đầu animation pulse (phồng lên xung quanh nút điểm danh)
     * 
     * @param toScale     Độ phồng tối đa (vd: 1.2f = phồng 20%)
     * @param durationMs  Thời gian một chu kỳ (ms)
     */
    private void startPulseAnimation(float toScale, long durationMs) {
        vPulse.clearAnimation();
        
        // ScaleAnimation: hiệu ứng phóng to/thu nhỏ
        ScaleAnimation pulse = new ScaleAnimation(1.0f, toScale, 1.0f, toScale,
                Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
        pulse.setDuration(durationMs);
        pulse.setRepeatMode(Animation.REVERSE);        // Lặp lại ngược (phóng rồi thu)
        pulse.setRepeatCount(Animation.INFINITE);      // Lặp vô hạn
        pulse.setInterpolator(new AccelerateDecelerateInterpolator()); // Easing mượt
        vPulse.startAnimation(pulse);
    }

    /**
     * Animation nhấn nút (scale down rồi bounce back)
     * 
     * @param target View cần animate
     */
    private void animateTap(View target) {
        target.animate()
                .scaleX(0.96f)          // Thu nhỏ 4%
                .scaleY(0.96f)
                .setDuration(90)        // 90ms
                .withEndAction(() ->    // Sau đó phóng lại
                    target.animate().scaleX(1f).scaleY(1f).setDuration(120).start())
                .start();
    }

    /**
     * Animation khi điểm danh thành công (bounce effect)
     * Chỉ chạy một lần khi vừa điểm danh xong
     */
    private void playCheckedInAnimation() {
        btnCheckIn.setScaleX(0.9f);
        btnCheckIn.setScaleY(0.9f);
        btnCheckIn.animate()
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(240)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .start();
    }

    /**
     * Lifecycle: onResume - Khi quay lại app
     * 
     * Load lại dữ liệu và cập nhật UI (phòng trường hợp user vào Settings thay đổi gì đó)
     */
    @Override
    protected void onResume() {
        super.onResume();
        loadData();
        updateUI();
    }

    /**
     * Thuật toán so sánh ngày
     * Kiểm tra xem hai timestamp có cùng một ngày theo lịch hay không
     * 
     * @param time1 Timestamp thứ nhất (ms)
     * @param time2 Timestamp thứ hai (ms)
     * @return true nếu cùng ngày, false nếu khác ngày
     * 
     * Ví dụ: 2024-01-01 23:59 và 2024-01-02 00:01 → false (khác ngày)
     */
    private boolean isSameDay(long time1, long time2) {
        Calendar cal1 = Calendar.getInstance();
        cal1.setTimeInMillis(time1);
        Calendar cal2 = Calendar.getInstance();
        cal2.setTimeInMillis(time2);
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
               cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR);
    }

    /**
     * Thuật toán kiểm tra "hôm qua"
     * Kiểm tra xem time1 có phải là ngày hôm trước của time2 không
     * 
     * @param time Timestamp cần kiểm tra
     * @return true nếu time là ngày hôm qua so với hiện tại
     * 
     * Dùng Calendar thay vì so sánh ms để tránh lỗi ở ranh giới ngày:
     * Ví dụ: điểm danh 23:59 → 00:01 ngày hôm sau cách nhau 2 phút
     * nhưng vẫn phải reset streak vì đã sang ngày mới
     */
    private boolean isYesterday(long time) {
        Calendar yesterday = Calendar.getInstance();
        yesterday.add(Calendar.DAY_OF_YEAR, -1);  // Lùi 1 ngày
        Calendar target = Calendar.getInstance();
        target.setTimeInMillis(time);
        return yesterday.get(Calendar.YEAR) == target.get(Calendar.YEAR) &&
               yesterday.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR);
    }

    /**
     * Xử lý sự kiện điểm danh
     * 
     * Algorithm:
     * 1. Kiểm tra đã điểm danh hôm nay chưa → không cho điểm lại
     * 2. Kiểm tra đã có liên hệ khẩn cấp chưa → bắt buộc phải có
     * 3. Tính streak:
     *    - Nếu lần cuối là hôm qua → streak++
     *    - Nếu lần cuối là hơn 1 ngày trước → streak = 1
     *    - Nếu chưa từng điểm danh → streak = 1
     * 4. Lưu dữ liệu và reschedule alarm
     */
    private void performCheckIn() {
        // Bước 1: Kiểm tra đã điểm danh hôm nay chưa
        if (isSameDay(lastCheckInTime, System.currentTimeMillis())) {
            Toast.makeText(this, R.string.checked_in_today, Toast.LENGTH_SHORT).show();
            return;
        }

        // Bước 2: Kiểm tra có liên hệ khẩn cấp chưa
        if (!hasAnyEmergencyContact()) {
            Toast.makeText(this, R.string.add_contact_first, Toast.LENGTH_SHORT).show();
            return;
        }

        long currentTime = System.currentTimeMillis();

        // Bước 3: Tính streak
        if (lastCheckInTime > 0) {
            if (isYesterday(lastCheckInTime)) {
                streakCount++;    // Tiếp tục chuỗi
            } else {
                streakCount = 1;   // Bắt đầu chuỗi mới
            }
        } else {
            streakCount = 1;       // Lần đầu tiên điểm danh
        }

        // Lưu dữ liệu
        lastCheckInTime = currentTime;
        saveData();
        
        // Reschedule alarm (tính lại thời điểm alert mới)
        AlarmScheduler.scheduleFromPrefs(this);
        updateUI();

        Toast.makeText(this, R.string.check_in_success, Toast.LENGTH_SHORT).show();
    }

    /**
     * Cập nhật toàn bộ UI dựa trên trạng thái hiện tại
     * 
     * Bao gồm:
     * - Hiển thị thời gian điểm danh cuối / điểm danh tới
     * - Hiển thị streak count
     * - Thay đổi màu nút và animation dựa trên trạng thái điểm danh hôm nay
     * - Overdue (quá 2 ngày): nút tròn đổi sang màu đỏ
     */
    private void updateUI() {
        SimpleDateFormat lastCheckFormat = new SimpleDateFormat("HH:mm dd/MM/yyyy", Locale.getDefault());
        SimpleDateFormat nextCheckFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
        int daysLimit = prefs.getInt(KEY_DAYS_BEFORE_ALERT, 2);
        long alertInterval = daysLimit * 24 * 60 * 60 * 1000L;

        // Hiển thị thời gian điểm danh
        if (lastCheckInTime > 0) {
            tvLastCheckIn.setText(getString(R.string.last_check_in).replace("--", lastCheckFormat.format(new Date(lastCheckInTime))));
            tvNextCheckIn.setText(getString(R.string.next_check_in).replace("--", nextCheckFormat.format(new Date(lastCheckInTime + alertInterval))));
        } else {
            tvLastCheckIn.setText(R.string.last_check_in);
            tvNextCheckIn.setText(R.string.next_check_in);
        }
        tvStreak.setText(getString(R.string.streak, streakCount));

        // Kiểm tra trạng thái
        boolean isCheckedToday = isSameDay(lastCheckInTime, System.currentTimeMillis());
        boolean overdue = isOverdue();

        // Cập nhật UI theo trạng thái điểm danh
        if (isCheckedToday) {
            // Đã điểm danh hôm nay: nút xanh lá
            btnCheckIn.setBackgroundResource(R.drawable.circle_background_checked);
            vPulse.setBackgroundResource(R.drawable.circle_background_checked);
            tvCheckInStatus.setText(R.string.checked_in);
            vPulse.setVisibility(View.VISIBLE);
            vPulse.setAlpha(0.14f);
            startPulseAnimation(1.10f, 1900);
            if (!wasCheckedToday) {
                playCheckedInAnimation();
            }
        } else if (overdue) {
            // Overdue: nút đỏ
            btnCheckIn.setBackgroundResource(R.drawable.circle_background_emergency);
            vPulse.setBackgroundResource(R.drawable.circle_background_emergency);
            tvCheckInStatus.setText(R.string.check_in_now);
            vPulse.setVisibility(View.VISIBLE);
            vPulse.setAlpha(0.22f);
            startPulseAnimation(1.20f, 1100);
        } else {
            // Chưa điểm danh: nút xanh dương
            btnCheckIn.setBackgroundResource(R.drawable.circle_background_pending);
            vPulse.setBackgroundResource(R.drawable.circle_background_pending);
            tvCheckInStatus.setText(R.string.check_in_now);
            vPulse.setVisibility(View.VISIBLE);
            vPulse.setAlpha(0.18f);
            startPulseAnimation(1.20f, 1300);
        }
        wasCheckedToday = isCheckedToday;
    }

    /**
     * Lưu dữ liệu điểm danh vào SharedPreferences
     */
    private void saveData() {
        prefs.edit()
                .putLong(KEY_LAST_CHECK_IN, lastCheckInTime)
                .putInt(KEY_STREAK_COUNT, streakCount)
                .putInt(KEY_EMERGENCY_COUNT, 0)     // Reset số lần emergency khi điểm danh
                .apply();
    }

    /**
     * Load dữ liệu từ SharedPreferences vào các biến thành viên
     * 
     * Đồng thời kiểm tra và reset streak nếu đã quá lâu không điểm danh
     * (quá 1 ngày không điểm danh thì streak reset về 0)
     */
    private void loadData() {
        etUserName.setText(prefs.getString(KEY_USER_NAME, ""));
        etEmergencyPhone.setText(getPrimaryEmergencyPhoneFromPrefs());
        lastCheckInTime = prefs.getLong(KEY_LAST_CHECK_IN, 0);
        streakCount = prefs.getInt(KEY_STREAK_COUNT, 0);

        // Kiểm tra streak: nếu lần điểm danh cuối không phải hôm nay 
        // và cũng không phải hôm qua → reset streak
        if (lastCheckInTime > 0) {
            long now = System.currentTimeMillis();
            if (!isSameDay(lastCheckInTime, now) && !isYesterday(lastCheckInTime)) {
                streakCount = 0;
                prefs.edit().putInt(KEY_STREAK_COUNT, 0).apply();
            }
        }
    }

    /**
     * Kiểm tra đã có ít nhất 1 liên hệ khẩn cấp chưa
     */
    private boolean hasAnyEmergencyContact() {
        return !loadEmergencyPhonesFromPrefs().isEmpty();
    }

    /**
     * Kiểm tra người dùng có đang overdue (quá N ngày không điểm danh) hay không
     *
     * @return true nếu đã quá daysLimit ngày kể từ lần điểm danh cuối
     */
    private boolean isOverdue() {
        if (lastCheckInTime <= 0) {
            return false;
        }
        int daysLimit = prefs.getInt(KEY_DAYS_BEFORE_ALERT, 2);
        long overdueThreshold = daysLimit * 24L * 60L * 60L * 1000L;
        return (System.currentTimeMillis() - lastCheckInTime) > overdueThreshold;
    }

    /**
     * Lấy số điện thoại khẩn cấp chính (số đầu tiên trong danh sách)
     */
    private String getPrimaryEmergencyPhoneFromPrefs() {
        List<String> phones = loadEmergencyPhonesFromPrefs();
        return phones.isEmpty() ? "" : phones.get(0);
    }

    /**
     * Load danh sách số điện thoại khẩn cấp từ JSON trong SharedPreferences
     * 
     * Fallback sang KEY_PHONE cũ nếu danh sách JSON rỗng
     * (để hỗ trợ migrate dữ liệu từ phiên bản cũ chỉ có 1 số điện thoại)
     */
    private List<String> loadEmergencyPhonesFromPrefs() {
        List<String> phones = new ArrayList<>();
        String contactsJson = prefs.getString(KEY_CONTACTS_JSON, "");
        if (contactsJson != null && !contactsJson.trim().isEmpty()) {
            try {
                JSONArray arr = new JSONArray(contactsJson);
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject obj = arr.optJSONObject(i);
                    if (obj == null) continue;
                    String phone = obj.optString("phone", "").trim();
                    if (!phone.isEmpty()) phones.add(phone);
                }
            } catch (Exception ignored) {
            }
        }

        // Fallback: dùng số điện thoại cũ nếu không có JSON contacts
        if (phones.isEmpty()) {
            String fallback = prefs.getString(KEY_PHONE, "").trim();
            if (!fallback.isEmpty()) phones.add(fallback);
        }
        return phones;
    }

    /**
     * Hiện popup quản lý liên hệ khẩn cấp
     * 
     * Popup bao gồm:
     * - Danh sách liên hệ hiện tại (có thể sửa/xóa)
     * - Nút thêm liên hệ mới
     * - Nút đóng
     */
    private void showContactsPopup() {
        loadContacts();

        Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.dialog_contacts);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        Button btnAddInPopup = dialog.findViewById(R.id.btnAddInPopup);
        TextView btnDonePopup = dialog.findViewById(R.id.btnDonePopup);
        LinearLayout contactsContainer = dialog.findViewById(R.id.contactsContainer);

        // Nút thêm liên hệ: mở form nhập liên hệ mới
        btnAddInPopup.setOnClickListener(v ->
                showContactEditor(null, -1, () -> renderContacts(contactsContainer))
        );
        
        // Nút đóng: đồng bộ số điện thoại chính rồi đóng popup
        btnDonePopup.setOnClickListener(v -> {
            syncPrimaryPhoneField();
            dialog.dismiss();
        });

        renderContacts(contactsContainer);
        dialog.show();
    }

    /**
     * Render danh sách liên hệ vào container
     * 
     * Mỗi liên hệ hiển thị:
     * - Tên và số điện tho���i
     * - Nút xóa và nút sửa
     * 
     * @param container LinearLayout chứa các contact card
     */
    private void renderContacts(LinearLayout container) {
        container.removeAllViews();
        
        // Hiện thông báo nếu danh sách rỗng
        if (contacts.isEmpty()) {
            TextView emptyView = new TextView(this);
            emptyView.setText(R.string.no_contacts);
            emptyView.setTextSize(14f);
            emptyView.setTextColor(0xFF777777);
            container.addView(emptyView);
            return;
        }

        LayoutInflater inflater = LayoutInflater.from(this);
        for (int i = 0; i < contacts.size(); i++) {
            ContactItem contact = contacts.get(i);
            View row = inflater.inflate(R.layout.item_contact_card, container, false);
            TextView tvName = row.findViewById(R.id.tvContactName);
            TextView tvPhone = row.findViewById(R.id.tvContactPhone);
            ImageView btnDelete = row.findViewById(R.id.btnDeleteContact);
            ImageView btnEdit = row.findViewById(R.id.btnEditContact);

            tvName.setText(contact.name);
            tvPhone.setText(contact.phone);

            final int index = i;
            // Xóa liên hệ
            btnDelete.setOnClickListener(v -> {
                contacts.remove(index);
                saveContacts();
                syncPrimaryPhoneField();
                renderContacts(container);
            });
            // Sửa liên hệ (nút edit)
            btnEdit.setOnClickListener(v ->
                    showContactEditor(contact, index, () -> renderContacts(container))
            );
            // Sửa liên hệ (click vào row)
            row.setOnClickListener(v ->
                    showContactEditor(contact, index, () -> renderContacts(container))
            );

            container.addView(row);
        }
    }

    /**
     * Hiện dialog thêm/sửa liên hệ khẩn cấp
     * 
     * @param original  Liên hệ cần sửa, null nếu thêm mới
     * @param index     Vị trí trong danh sách, -1 nếu thêm mới
     * @param onSaved   Callback sau khi lưu thành công
     */
    private void showContactEditor(ContactItem original, int index, Runnable onSaved) {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        form.setPadding(pad, pad, pad, 0);

        // Input tên
        EditText etName = new EditText(this);
        etName.setHint(R.string.contact_name_hint);
        etName.setText(original != null ? original.name : "");
        etName.setSingleLine(true);
        etName.setImeOptions(EditorInfo.IME_ACTION_NEXT);
        form.addView(etName);

        // Input số điện thoại
        EditText etPhone = new EditText(this);
        etPhone.setHint(R.string.contact_phone_hint);
        etPhone.setInputType(InputType.TYPE_CLASS_PHONE);
        etPhone.setText(original != null ? original.phone : "");
        etPhone.setSingleLine(true);
        etPhone.setImeOptions(EditorInfo.IME_ACTION_DONE);
        form.addView(etPhone);

        AlertDialog alertDialog = new AlertDialog.Builder(this)
                .setTitle(original == null ? R.string.add_emergency_contact : R.string.edit_contact)
                .setView(form)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.save, null)
                .create();

        // Xử lý validation trước khi lưu
        alertDialog.setOnShowListener(dialog -> {
            Button btnSave = alertDialog.getButton(AlertDialog.BUTTON_POSITIVE);
            btnSave.setOnClickListener(v -> {
                String name = etName.getText().toString().trim();
                String phone = etPhone.getText().toString().trim();

                // Validation: bắt buộc nhập số điện thoại
                if (TextUtils.isEmpty(phone)) {
                    etPhone.setError(getString(R.string.phone_required));
                    return;
                }
                // Nếu không nhập tên, dùng tên mặc định
                if (TextUtils.isEmpty(name)) {
                    name = DEFAULT_CONTACT_NAME;
                }

                ContactItem item = new ContactItem(name, phone);
                if (index >= 0 && index < contacts.size()) {
                    contacts.set(index, item);  // Sửa: cập nhật vào vị trí index
                } else {
                    contacts.add(item);          // Thêm mới: thêm vào cuối danh sách
                }

                saveContacts();
                syncPrimaryPhoneField();
                onSaved.run();
                alertDialog.dismiss();
            });
        });
        alertDialog.show();
    }

    /**
     * Load danh sách liên hệ từ JSON vào memory
     */
    private void loadContacts() {
        contacts.clear();

        String json = prefs.getString(KEY_CONTACTS_JSON, "");
        if (!TextUtils.isEmpty(json)) {
            try {
                JSONArray arr = new JSONArray(json);
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject obj = arr.optJSONObject(i);
                    if (obj == null) continue;
                    String name = obj.optString("name", DEFAULT_CONTACT_NAME).trim();
                    String phone = obj.optString("phone", "").trim();
                    if (!TextUtils.isEmpty(phone)) {
                        contacts.add(new ContactItem(TextUtils.isEmpty(name) ? DEFAULT_CONTACT_NAME : name, phone));
                    }
                }
            } catch (Exception ignored) {
            }
        }

        // Fallback: load số điện thoại cũ nếu không có JSON
        if (contacts.isEmpty()) {
            String legacyPhone = prefs.getString(KEY_PHONE, "").trim();
            if (!TextUtils.isEmpty(legacyPhone)) {
                contacts.add(new ContactItem(DEFAULT_CONTACT_NAME, legacyPhone));
            }
        }
    }

    /**
     * Lưu danh sách liên hệ từ memory vào SharedPreferences (JSON)
     */
    private void saveContacts() {
        JSONArray arr = new JSONArray();
        for (ContactItem contact : contacts) {
            JSONObject obj = new JSONObject();
            try {
                obj.put("name", contact.name);
                obj.put("phone", contact.phone);
                arr.put(obj);
            } catch (Exception ignored) {
            }
        }

        prefs.edit()
                .putString(KEY_CONTACTS_JSON, arr.toString())
                .putString(KEY_PHONE, contacts.isEmpty() ? "" : contacts.get(0).phone)
                .apply();
    }

    /**
     * Đồng bộ số điện thoại khẩn cấp chính vào EditText trên màn hình
     */
    private void syncPrimaryPhoneField() {
        String primaryPhone = contacts.isEmpty() ? "" : contacts.get(0).phone;
        etEmergencyPhone.setText(primaryPhone);
    }

    /**
     * Khóa trường tên người dùng (chỉ hiển thị, không edit)
     */
    private void lockUserNameEditing() {
        etUserName.setFocusable(false);
        etUserName.setFocusableInTouchMode(false);
        etUserName.setCursorVisible(false);
    }

    /**
     * Mở khóa trường tên người dùng để edit và tự động hiện bàn phím
     */
    private void unlockUserNameEditing() {
        etUserName.setFocusable(true);
        etUserName.setFocusableInTouchMode(true);
        etUserName.setCursorVisible(true);
        etUserName.requestFocus();
        etUserName.setSelection(etUserName.getText().length());  // Đặt con trỏ ở cuối

        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(etUserName, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    /**
     * Khóa trường số điện thoại (chỉ hiển thị, click để mở popup)
     */
    private void lockPhoneFieldEditing() {
        etEmergencyPhone.setFocusable(false);
        etEmergencyPhone.setFocusableInTouchMode(false);
        etEmergencyPhone.setCursorVisible(false);
    }

    /**
     * Ẩn bàn phím ảo
     */
    private void hideKeyboard(View target) {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(target.getWindowToken(), 0);
        }
    }

    /**
     * Model class cho liên hệ khẩn cấp
     */
    private static class ContactItem {
        final String name;
        final String phone;

        ContactItem(String name, String phone) {
            this.name = name;
            this.phone = phone;
        }
    }

    /**
     * Callback xử lý kết quả xin quyền
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            for (int i = 0; i < permissions.length; i++) {
                if (Manifest.permission.SEND_SMS.equals(permissions[i])) {
                    if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                        Toast.makeText(this, R.string.sms_permission_denied, Toast.LENGTH_LONG).show();
                    }
                }
            }
        }
    }
}