package com.example.aud;

/**
 * SettingsActivity - Màn hình cài đặt của ứng dụng AUD
 * 
 * Cho phép người dùng:
 * - Cài đặt số ngày tối đa trước khi báo động (mặc định 2 ngày)
 * - Cài đặt giờ nhắc nhở điểm danh hàng ngày (mặc định 09:00)
 * - Quản lý danh sách liên hệ khẩn cấp (thêm/sửa/xóa)
 * 
 * Khi thay đổi settings, alarm sẽ được reschedule tự động
 */

import android.app.Dialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.cardview.widget.CardView;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Activity hiển thị và chỉnh sửa cài đặt
 */
public class SettingsActivity extends AppCompatActivity {
    
    /** Tên mặc định cho liên hệ khẩn cấp - dùng string resource để hỗ trợ đa ngôn ngữ */
    private static final String DEFAULT_CONTACT_NAME = "Người thân"; // fallback cho migration dữ liệu cũ

    // Views
    /** Input số ngày trước khi báo động */
    private EditText etDaysBeforeAlert;
    
    /** Hiển thị giờ nhắc nhở (click để đổi) */
    private TextView tvReminderTime;
    
    /** Mô tả luật báo động (vd: "Nếu bạn không điểm danh quá 2 ngày...") */
    private TextView tvAlertRuleDescription;

    /** Hiển thị phiên bản ứng dụng */
    private TextView tvVersion;

    /** Hiển thị ngôn ngữ hiện tại */
    private TextView tvLanguageValue;
    
    /** Nút hoàn tất - lưu và đóng màn hình */
    private TextView btnDone;
    
    /** Nút thêm liên hệ khẩn cấp */
    private Button btnAddContact;

    /** Card ngôn ngữ */
    private CardView cardLanguage;
    
    /** SharedPreferences để đọc/ghi settings */
    private SharedPreferences prefs;
    
    /** Danh sách liên hệ trong memory */
    private final List<ContactItem> contacts = new ArrayList<>();

    /**
     * Áp dụng ngôn ngữ đã lưu trước khi activity được tạo
     */
    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.applyLanguage(newBase));
    }

    /**
     * Lifecycle: onCreate
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        // Ánh xạ views
        etDaysBeforeAlert = findViewById(R.id.etDaysBeforeAlert);
        tvReminderTime = findViewById(R.id.tvReminderTime);
        tvAlertRuleDescription = findViewById(R.id.tvAlertRuleDescription);
        btnDone = findViewById(R.id.btnDone);
        btnAddContact = findViewById(R.id.btnAddContact);
        tvVersion = findViewById(R.id.tvVersion);
        cardLanguage = findViewById(R.id.cardLanguage);
        tvLanguageValue = findViewById(R.id.tvLanguageValue);
        
        prefs = getSharedPreferences(MainActivity.PREFS_NAME, MODE_PRIVATE);

        // Load dữ liệu đã lưu vào UI
        etDaysBeforeAlert.setText(String.valueOf(prefs.getInt(MainActivity.KEY_DAYS_BEFORE_ALERT, 2)));
        tvReminderTime.setText(prefs.getString(MainActivity.KEY_REMINDER_TIME, "09:00"));
        updateAlertRuleDescription(parseDays(etDaysBeforeAlert.getText().toString().trim()));
        loadContacts();

        // Hiển thị phiên bản ứng dụng
        try {
            String versionName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            tvVersion.setText(getString(R.string.version_format, versionName));
        } catch (PackageManager.NameNotFoundException ignored) {
        }

        // Hiển thị ngôn ngữ hiện tại
        updateLanguageDisplay();

        // Click vào card ngôn ngữ → hiện dialog chọn ngôn ngữ
        cardLanguage.setOnClickListener(v -> showLanguageDialog());

        // Nút thêm liên hệ
        btnAddContact.setOnClickListener(v -> showContactsPopup());
        
        // TextWatcher: cập nhật mô tả khi thay đổi số ngày
        etDaysBeforeAlert.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                updateAlertRuleDescription(parseDays(s.toString().trim()));
            }
        });
        
        // Xử lý khi nhấn Done trên bàn phím
        etDaysBeforeAlert.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                hideKeyboard(v);
                v.clearFocus();
                return true;
            }
            return false;
        });

        // Click vào giờ nhắc nhở → hiện TimePicker
        tvReminderTime.setOnClickListener(v -> {
            String[] parts = tvReminderTime.getText().toString().split(":");
            new TimePickerDialog(this, (view, h, m) -> {
                // Format giờ và lưu
                String time = String.format(Locale.getDefault(), "%02d:%02d", h, m);
                tvReminderTime.setText(time);
                prefs.edit().putString(MainActivity.KEY_REMINDER_TIME, time).apply();
                // Reschedule alarm với giờ mới
                AlarmScheduler.scheduleFromPrefs(this);
            }, Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), true).show();
        });

        // Nút hoàn tất: lưu settings và đóng
        btnDone.setOnClickListener(v -> {
            saveAlertDays();
            AlarmScheduler.scheduleFromPrefs(this);
            finish();
        });
    }

    /**
     * Lưu số ngày trước khi báo động vào SharedPreferences
     */
    private void saveAlertDays() {
        String rawValue = etDaysBeforeAlert.getText().toString().trim();
        int days = parseDays(rawValue);

        prefs.edit().putInt(MainActivity.KEY_DAYS_BEFORE_ALERT, days).apply();
        etDaysBeforeAlert.setText(String.valueOf(days));
        updateAlertRuleDescription(days);
    }

    /**
     * Parse số ngày từ string, với validation
     * 
     * Rules:
     * - Empty → 2 (default)
     * - Invalid format → 2 (default)
     * - < 1 → 1 (tối thiểu)
     * - > 30 → 30 (tối đa)
     * 
     * @param rawValue String từ input
     * @return Số ngày đã validate
     */
    private int parseDays(String rawValue) {
        if (TextUtils.isEmpty(rawValue)) {
            return 2;
        }

        int days;
        try {
            days = Integer.parseInt(rawValue);
        } catch (NumberFormatException e) {
            days = 2;
        }

        if (days < 1) {
            days = 1;
        }
        if (days > 30) {
            days = 30;
        }
        return days;
    }

    /**
     * Cập nhật mô tả luật báo động dựa trên số ngày
     * 
     * @param days Số ngày
     */
    private void updateAlertRuleDescription(int days) {
        int hours = days * 24;
        tvAlertRuleDescription.setText(getString(R.string.alert_rule_description, days, hours));
    }

    private void updateLanguageDisplay() {
        String currentLang = LocaleHelper.getLanguage(this);
        tvLanguageValue.setText(LocaleHelper.getLanguageDisplayName(this, currentLang));
    }

    private void showLanguageDialog() {
        String[] languages = {
                getString(R.string.language_vietnamese),
                getString(R.string.language_english)
        };
        String[] languageCodes = {
                LocaleHelper.LANG_VIETNAMESE,
                LocaleHelper.LANG_ENGLISH
        };

        String currentLang = LocaleHelper.getLanguage(this);
        int selectedIndex = currentLang.equals(LocaleHelper.LANG_ENGLISH) ? 1 : 0;

        new AlertDialog.Builder(this)
                .setTitle(R.string.language)
                .setSingleChoiceItems(languages, selectedIndex, (dialog, which) -> {
                    dialog.dismiss();
                    String selected = languageCodes[which];

                    // Lưu đồng bộ trước khi restart
                    LocaleHelper.saveLanguage(this, selected);

                    // Restart app để áp dụng ngôn ngữ
                    Intent restartIntent = new Intent(this, MainActivity.class);
                    restartIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(restartIntent);

                    // Finish tất cả activities hiện tại
                    finishAffinity();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private String getAppName() {
        try {
            return getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(getPackageName(), 0)).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return getString(R.string.app_name);
        }
    }
    private void showContactsPopup() {
        Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.dialog_contacts);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        Button btnAddInPopup = dialog.findViewById(R.id.btnAddInPopup);
        TextView btnDonePopup = dialog.findViewById(R.id.btnDonePopup);
        LinearLayout contactsContainer = dialog.findViewById(R.id.contactsContainer);

        // Nút thêm liên hệ
        btnAddInPopup.setOnClickListener(v ->
                showContactEditor(null, -1, () -> renderContacts(contactsContainer))
        );
        // Nút đóng popup
        btnDonePopup.setOnClickListener(v -> dialog.dismiss());

        renderContacts(contactsContainer);
        dialog.show();
    }

    /**
     * Render danh sách liên hệ vào container
     */
    private void renderContacts(LinearLayout container) {
        container.removeAllViews();
        
        // Hiện thông báo nếu rỗng
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
            // Xóa liên hệ
            btnDelete.setOnClickListener(v -> {
                contacts.remove(index);
                saveContacts();
                renderContacts(container);
            });
            // Sửa liên hệ (nút edit)
            btnEdit.setOnClickListener(v ->
                    showContactEditor(contact, index, () -> renderContacts(container))
            );
            // Sửa liên hệ (click row)
            row.setOnClickListener(v ->
                    showContactEditor(contact, index, () -> renderContacts(container))
            );

            container.addView(row);
        }
    }

    /**
     * Hiện dialog thêm/sửa liên hệ
     */
    private void showContactEditor(ContactItem original, int index, Runnable onSaved) {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        form.setPadding(pad, pad, pad, 0);

        // Input tên
        EditText etName = new EditText(this);
        etName.setHint("Tên liên hệ");
        etName.setText(original != null ? original.name : "");
        etName.setSingleLine(true);
        etName.setImeOptions(EditorInfo.IME_ACTION_NEXT);
        form.addView(etName);

        // Input số điện thoại
        EditText etPhone = new EditText(this);
        etPhone.setHint("Số điện thoại");
        etPhone.setInputType(android.text.InputType.TYPE_CLASS_PHONE);
        etPhone.setText(original != null ? original.phone : "");
        etPhone.setSingleLine(true);
        etPhone.setImeOptions(EditorInfo.IME_ACTION_DONE);
        form.addView(etPhone);

        AlertDialog alertDialog = new AlertDialog.Builder(this)
                .setTitle(original == null ? "Thêm liên hệ khẩn cấp" : "Sửa liên hệ")
                .setView(form)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.save, null)
                .create();

        // Xử lý validation và lưu
        alertDialog.setOnShowListener(dialog -> {
            Button btnSave = alertDialog.getButton(AlertDialog.BUTTON_POSITIVE);
            btnSave.setOnClickListener(v -> {
                String name = etName.getText().toString().trim();
                String phone = etPhone.getText().toString().trim();

                // Validation: bắt buộc nhập số điện thoại
                if (TextUtils.isEmpty(phone)) {
                    etPhone.setError("Cần nhập số điện thoại");
                    return;
                }
                // Tên mặc định nếu không nhập
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
                onSaved.run();
                alertDialog.dismiss();
            });
        });
        alertDialog.show();
    }

    /**
     * Load danh sách liên hệ từ JSON
     */
    private void loadContacts() {
        contacts.clear();

        String json = prefs.getString(MainActivity.KEY_CONTACTS_JSON, "");
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
            String legacyPhone = prefs.getString(MainActivity.KEY_PHONE, "").trim();
            if (!TextUtils.isEmpty(legacyPhone)) {
                contacts.add(new ContactItem(DEFAULT_CONTACT_NAME, legacyPhone));
                saveContacts();
            }
        }
    }

    /**
     * Lưu danh sách liên hệ vào JSON
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

        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(MainActivity.KEY_CONTACTS_JSON, arr.toString());
        editor.putString(MainActivity.KEY_PHONE, contacts.isEmpty() ? "" : contacts.get(0).phone);
        editor.apply();
    }

    /**
     * Ẩn bàn phím ảo
     */
    private void hideKeyboard(View target) {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
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
}