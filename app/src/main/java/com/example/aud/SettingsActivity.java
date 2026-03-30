package com.example.aud;

import android.app.Dialog;
import android.app.TimePickerDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SettingsActivity extends AppCompatActivity {
    private static final String DEFAULT_CONTACT_NAME = "Người thân";

    private EditText etDaysBeforeAlert;
    private TextView tvReminderTime;
    private TextView btnDone;
    private Button btnAddContact;
    private SharedPreferences prefs;
    private final List<ContactItem> contacts = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        etDaysBeforeAlert = findViewById(R.id.etDaysBeforeAlert);
        tvReminderTime = findViewById(R.id.tvReminderTime);
        btnDone = findViewById(R.id.btnDone);
        btnAddContact = findViewById(R.id.btnAddContact);
        
        prefs = getSharedPreferences(MainActivity.PREFS_NAME, MODE_PRIVATE);

        etDaysBeforeAlert.setText(String.valueOf(prefs.getInt(MainActivity.KEY_DAYS_BEFORE_ALERT, 2)));
        tvReminderTime.setText(prefs.getString(MainActivity.KEY_REMINDER_TIME, "09:00"));
        loadContacts();

        btnAddContact.setOnClickListener(v -> showContactsPopup());

        tvReminderTime.setOnClickListener(v -> {
            String[] parts = tvReminderTime.getText().toString().split(":");
            new TimePickerDialog(this, (view, h, m) -> {
                String time = String.format(Locale.getDefault(), "%02d:%02d", h, m);
                tvReminderTime.setText(time);
                prefs.edit().putString(MainActivity.KEY_REMINDER_TIME, time).apply();
                AlarmScheduler.scheduleFromPrefs(this);
            }, Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), true).show();
        });

        btnDone.setOnClickListener(v -> {
            saveAlertDays();
            AlarmScheduler.scheduleFromPrefs(this);
            finish();
        });
    }

    private void saveAlertDays() {
        String rawValue = etDaysBeforeAlert.getText().toString().trim();
        if (TextUtils.isEmpty(rawValue)) {
            rawValue = "2";
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

        prefs.edit().putInt(MainActivity.KEY_DAYS_BEFORE_ALERT, days).apply();
        etDaysBeforeAlert.setText(String.valueOf(days));
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

        btnAddInPopup.setOnClickListener(v ->
                showContactEditor(null, -1, () -> renderContacts(contactsContainer))
        );
        btnDonePopup.setOnClickListener(v -> dialog.dismiss());

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
            ImageButton btnDelete = row.findViewById(R.id.btnDeleteContact);
            ImageButton btnEdit = row.findViewById(R.id.btnEditContact);

            tvName.setText(contact.name);
            tvPhone.setText(contact.phone);

            final int index = i;
            btnDelete.setOnClickListener(v -> {
                contacts.remove(index);
                saveContacts();
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
        form.addView(etName);

        EditText etPhone = new EditText(this);
        etPhone.setHint("Số điện thoại");
        etPhone.setInputType(android.text.InputType.TYPE_CLASS_PHONE);
        etPhone.setText(original != null ? original.phone : "");
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
                onSaved.run();
                alertDialog.dismiss();
            });
        });
        alertDialog.show();
    }

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

        if (contacts.isEmpty()) {
            String legacyPhone = prefs.getString(MainActivity.KEY_PHONE, "").trim();
            if (!TextUtils.isEmpty(legacyPhone)) {
                contacts.add(new ContactItem(DEFAULT_CONTACT_NAME, legacyPhone));
                saveContacts();
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

        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(MainActivity.KEY_CONTACTS_JSON, arr.toString());
        editor.putString(MainActivity.KEY_PHONE, contacts.isEmpty() ? "" : contacts.get(0).phone);
        editor.apply();
    }

    private static class ContactItem {
        final String name;
        final String phone;

        ContactItem(String name, String phone) {
            this.name = name;
            this.phone = phone;
        }
    }
}