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
    private static final String DEFAULT_CONTACT_NAME = "Người thân";

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

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

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        loadData();
        AlarmScheduler.scheduleFromPrefs(this);
        checkAndRequestPermissions();
        updateUI();
        lockUserNameEditing();
        lockPhoneFieldEditing();

        etUserName.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                prefs.edit().putString(KEY_USER_NAME, s.toString()).apply();
            }
        });
        etUserName.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                lockUserNameEditing();
                hideKeyboard(v);
                return true;
            }
            return false;
        });

        etEmergencyPhone.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                prefs.edit().putString(KEY_PHONE, s.toString()).apply();
            }
        });

        btnCheckIn.setOnClickListener(v -> {
            animateTap(btnCheckIn);
            performCheckIn();
        });
        
        btnSettings.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
            startActivity(intent);
        });

        btnEditContactsMain.setOnClickListener(v -> {
            animateTap(btnEditContactsMain);
            unlockUserNameEditing();
        });
        btnEditContactsPhoneMain.setOnClickListener(v -> {
            animateTap(btnEditContactsPhoneMain);
            showContactsPopup();
        });

        etUserName.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                lockUserNameEditing();
            }
        });
    }

    private void checkAndRequestPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && 
             ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)) {
            
            String[] permissions;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissions = new String[]{Manifest.permission.SEND_SMS, Manifest.permission.POST_NOTIFICATIONS};
            } else {
                permissions = new String[]{Manifest.permission.SEND_SMS};
            }
            ActivityCompat.requestPermissions(this, permissions, PERMISSION_REQUEST_CODE);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            if (alarmManager != null && !alarmManager.canScheduleExactAlarms()) {
                Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                startActivity(intent);
            }
        }
    }

    private void startPulseAnimation(float toScale, long durationMs) {
        vPulse.clearAnimation();
        ScaleAnimation pulse = new ScaleAnimation(1.0f, toScale, 1.0f, toScale,
                Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
        pulse.setDuration(durationMs);
        pulse.setRepeatMode(Animation.REVERSE);
        pulse.setRepeatCount(Animation.INFINITE);
        pulse.setInterpolator(new AccelerateDecelerateInterpolator());
        vPulse.startAnimation(pulse);
    }

    private void animateTap(View target) {
        target.animate()
                .scaleX(0.96f)
                .scaleY(0.96f)
                .setDuration(90)
                .withEndAction(() -> target.animate().scaleX(1f).scaleY(1f).setDuration(120).start())
                .start();
    }

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

    @Override
    protected void onResume() {
        super.onResume();
        loadData();
        updateUI();
    }

    private boolean isSameDay(long time1, long time2) {
        Calendar cal1 = Calendar.getInstance();
        cal1.setTimeInMillis(time1);
        Calendar cal2 = Calendar.getInstance();
        cal2.setTimeInMillis(time2);
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
               cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR);
    }

    private boolean isYesterday(long time) {
        Calendar yesterday = Calendar.getInstance();
        yesterday.add(Calendar.DAY_OF_YEAR, -1);
        Calendar target = Calendar.getInstance();
        target.setTimeInMillis(time);
        return yesterday.get(Calendar.YEAR) == target.get(Calendar.YEAR) &&
               yesterday.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR);
    }

    private void performCheckIn() {
        if (isSameDay(lastCheckInTime, System.currentTimeMillis())) {
            Toast.makeText(this, "Hôm nay bạn đã điểm danh rồi.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!hasAnyEmergencyContact()) {
            Toast.makeText(this, "Vui lòng thêm liên hệ khẩn cấp trước.", Toast.LENGTH_SHORT).show();
            return;
        }

        long currentTime = System.currentTimeMillis();

        if (lastCheckInTime > 0) {
            if (isYesterday(lastCheckInTime)) {
                streakCount++;
            } else {
                streakCount = 1;
            }
        } else {
            streakCount = 1;
        }

        lastCheckInTime = currentTime;
        saveData();
        AlarmScheduler.scheduleFromPrefs(this);
        updateUI();

        Toast.makeText(this, "Điểm danh thành công.", Toast.LENGTH_SHORT).show();
    }

    private void updateUI() {
        SimpleDateFormat lastCheckFormat = new SimpleDateFormat("HH:mm dd/MM/yyyy", Locale.getDefault());
        SimpleDateFormat nextCheckFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
        int daysLimit = prefs.getInt(KEY_DAYS_BEFORE_ALERT, 2);
        long alertInterval = daysLimit * 24 * 60 * 60 * 1000L;

        if (lastCheckInTime > 0) {
            tvLastCheckIn.setText("Lần điểm danh gần nhất: " + lastCheckFormat.format(new Date(lastCheckInTime)));
            tvNextCheckIn.setText("Lần điểm danh tới: " + nextCheckFormat.format(new Date(lastCheckInTime + alertInterval)));
        } else {
            tvLastCheckIn.setText("Lần điểm danh gần nhất: --");
            tvNextCheckIn.setText("Lần điểm danh tới: --");
        }
        tvStreak.setText("🔥 Chuỗi " + streakCount + " ngày");

        boolean isCheckedToday = isSameDay(lastCheckInTime, System.currentTimeMillis());

        if (isCheckedToday) {
            btnCheckIn.setBackgroundResource(R.drawable.circle_background_checked);
            vPulse.setBackgroundResource(R.drawable.circle_background_checked);
            tvCheckInStatus.setText("Đã điểm danh");
            vPulse.setVisibility(View.VISIBLE);
            vPulse.setAlpha(0.14f);
            startPulseAnimation(1.10f, 1900);
            if (!wasCheckedToday) {
                playCheckedInAnimation();
            }
        } else {
            btnCheckIn.setBackgroundResource(R.drawable.circle_background_pending);
            vPulse.setBackgroundResource(R.drawable.circle_background_pending);
            tvCheckInStatus.setText("Điểm danh ngay!");
            vPulse.setVisibility(View.VISIBLE);
            vPulse.setAlpha(0.18f);
            startPulseAnimation(1.20f, 1300);
        }
        wasCheckedToday = isCheckedToday;
    }

    private void saveData() {
        prefs.edit()
                .putLong(KEY_LAST_CHECK_IN, lastCheckInTime)
                .putInt(KEY_STREAK_COUNT, streakCount)
                .putInt(KEY_EMERGENCY_COUNT, 0)
                .apply();
    }

    private void loadData() {
        etUserName.setText(prefs.getString(KEY_USER_NAME, ""));
        etEmergencyPhone.setText(getPrimaryEmergencyPhoneFromPrefs());
        lastCheckInTime = prefs.getLong(KEY_LAST_CHECK_IN, 0);
        streakCount = prefs.getInt(KEY_STREAK_COUNT, 0);

        if (lastCheckInTime > 0) {
            long now = System.currentTimeMillis();
            if (!isSameDay(lastCheckInTime, now) && !isYesterday(lastCheckInTime)) {
                streakCount = 0;
                prefs.edit().putInt(KEY_STREAK_COUNT, 0).apply();
            }
        }
    }

    private boolean hasAnyEmergencyContact() {
        return !loadEmergencyPhonesFromPrefs().isEmpty();
    }

    private String getPrimaryEmergencyPhoneFromPrefs() {
        List<String> phones = loadEmergencyPhonesFromPrefs();
        return phones.isEmpty() ? "" : phones.get(0);
    }

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

        if (phones.isEmpty()) {
            String fallback = prefs.getString(KEY_PHONE, "").trim();
            if (!fallback.isEmpty()) phones.add(fallback);
        }
        return phones;
    }

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

        btnAddInPopup.setOnClickListener(v ->
                showContactEditor(null, -1, () -> renderContacts(contactsContainer))
        );
        btnDonePopup.setOnClickListener(v -> {
            syncPrimaryPhoneField();
            dialog.dismiss();
        });

        renderContacts(contactsContainer);
        dialog.show();
    }

    private void renderContacts(LinearLayout container) {
        container.removeAllViews();
        if (contacts.isEmpty()) {
            TextView emptyView = new TextView(this);
            emptyView.setText("Chưa có liên hệ khẩn cấp.");
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
            btnDelete.setOnClickListener(v -> {
                contacts.remove(index);
                saveContacts();
                syncPrimaryPhoneField();
                renderContacts(container);
            });
            btnEdit.setOnClickListener(v ->
                    showContactEditor(contact, index, () -> renderContacts(container))
            );
            row.setOnClickListener(v ->
                    showContactEditor(contact, index, () -> renderContacts(container))
            );

            container.addView(row);
        }
    }

    private void showContactEditor(ContactItem original, int index, Runnable onSaved) {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        form.setPadding(pad, pad, pad, 0);

        EditText etName = new EditText(this);
        etName.setHint("Tên liên hệ");
        etName.setText(original != null ? original.name : "");
        etName.setSingleLine(true);
        etName.setImeOptions(EditorInfo.IME_ACTION_NEXT);
        form.addView(etName);

        EditText etPhone = new EditText(this);
        etPhone.setHint("Số điện thoại");
        etPhone.setInputType(InputType.TYPE_CLASS_PHONE);
        etPhone.setText(original != null ? original.phone : "");
        etPhone.setSingleLine(true);
        etPhone.setImeOptions(EditorInfo.IME_ACTION_DONE);
        form.addView(etPhone);

        AlertDialog alertDialog = new AlertDialog.Builder(this)
                .setTitle(original == null ? "Thêm liên hệ khẩn cấp" : "Sửa liên hệ")
                .setView(form)
                .setNegativeButton("Hủy", null)
                .setPositiveButton("Lưu", null)
                .create();

        alertDialog.setOnShowListener(dialog -> {
            Button btnSave = alertDialog.getButton(AlertDialog.BUTTON_POSITIVE);
            btnSave.setOnClickListener(v -> {
                String name = etName.getText().toString().trim();
                String phone = etPhone.getText().toString().trim();

                if (TextUtils.isEmpty(phone)) {
                    etPhone.setError("Cần nhập số điện thoại");
                    return;
                }
                if (TextUtils.isEmpty(name)) {
                    name = DEFAULT_CONTACT_NAME;
                }

                ContactItem item = new ContactItem(name, phone);
                if (index >= 0 && index < contacts.size()) {
                    contacts.set(index, item);
                } else {
                    contacts.add(item);
                }

                saveContacts();
                syncPrimaryPhoneField();
                onSaved.run();
                alertDialog.dismiss();
            });
        });
        alertDialog.show();
    }

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

        if (contacts.isEmpty()) {
            String legacyPhone = prefs.getString(KEY_PHONE, "").trim();
            if (!TextUtils.isEmpty(legacyPhone)) {
                contacts.add(new ContactItem(DEFAULT_CONTACT_NAME, legacyPhone));
            }
        }
    }

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

    private void syncPrimaryPhoneField() {
        String primaryPhone = contacts.isEmpty() ? "" : contacts.get(0).phone;
        etEmergencyPhone.setText(primaryPhone);
    }

    private void lockUserNameEditing() {
        etUserName.setFocusable(false);
        etUserName.setFocusableInTouchMode(false);
        etUserName.setCursorVisible(false);
    }

    private void unlockUserNameEditing() {
        etUserName.setFocusable(true);
        etUserName.setFocusableInTouchMode(true);
        etUserName.setCursorVisible(true);
        etUserName.requestFocus();
        etUserName.setSelection(etUserName.getText().length());

        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(etUserName, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    private void lockPhoneFieldEditing() {
        etEmergencyPhone.setFocusable(false);
        etEmergencyPhone.setFocusableInTouchMode(false);
        etEmergencyPhone.setCursorVisible(false);
    }

    private void hideKeyboard(View target) {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(target.getWindowToken(), 0);
        }
    }

    private static class ContactItem {
        final String name;
        final String phone;

        ContactItem(String name, String phone) {
            this.name = name;
            this.phone = phone;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            for (int i = 0; i < permissions.length; i++) {
                if (Manifest.permission.SEND_SMS.equals(permissions[i])) {
                    if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                        Toast.makeText(this, "Quyền gửi SMS bị từ chối. Không thể gửi SMS khẩn cấp!", Toast.LENGTH_LONG).show();
                    }
                }
            }
        }
    }
}