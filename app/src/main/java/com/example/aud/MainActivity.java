package com.example.aud;

import android.Manifest;
import android.app.AlarmManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.ScaleAnimation;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
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
    private static final int PERMISSION_REQUEST_CODE = 123;

    private EditText etUserName, etEmergencyPhone;
    private TextView tvLastCheckIn, tvNextCheckIn, tvStreak, tvCheckInStatus;
    private LinearLayout btnCheckIn;
    private ImageButton btnSettings;
    private View vPulse;

    private SharedPreferences prefs;
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
        vPulse = findViewById(R.id.vPulse);

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        loadData();
        AlarmScheduler.scheduleFromPrefs(this);
        checkAndRequestPermissions();
        updateUI();

        etUserName.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                prefs.edit().putString(KEY_USER_NAME, s.toString()).apply();
            }
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

    private void performCheckIn() {
        if (isSameDay(lastCheckInTime, System.currentTimeMillis())) {
            Toast.makeText(this, "Hôm nay bạn đã điểm danh rồi.", Toast.LENGTH_SHORT).show();
            return;
        }

        String phone = etEmergencyPhone.getText().toString().trim();
        if (phone.isEmpty()) {
            Toast.makeText(this, "Vui lòng thêm liên hệ khẩn cấp trước.", Toast.LENGTH_SHORT).show();
            return;
        }

        long currentTime = System.currentTimeMillis();
        long oneDayMillis = 24 * 60 * 60 * 1000L;
        
        if (lastCheckInTime > 0) {
            // Nếu check-in vào ngày kế tiếp (trong khoảng 48h)
            if (currentTime - lastCheckInTime < oneDayMillis * 2) streakCount++;
            else streakCount = 1;
        } else streakCount = 1;

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
        prefs.edit().putLong(KEY_LAST_CHECK_IN, lastCheckInTime).putInt(KEY_STREAK_COUNT, streakCount).apply();
    }

    private void loadData() {
        etUserName.setText(prefs.getString(KEY_USER_NAME, ""));
        etEmergencyPhone.setText(prefs.getString(KEY_PHONE, ""));
        lastCheckInTime = prefs.getLong(KEY_LAST_CHECK_IN, 0);
        streakCount = prefs.getInt(KEY_STREAK_COUNT, 0);
    }
}