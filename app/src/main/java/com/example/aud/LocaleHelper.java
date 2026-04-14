package com.example.aud;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Build;

import java.util.Locale;

public class LocaleHelper {

    public static final String PREFS_NAME = "aud_prefs";
    public static final String KEY_LANGUAGE = "language";
    public static final String KEY_LANGUAGE_BACKUP = "language_backup";

    public static final String LANG_VIETNAMESE = "vi";
    public static final String LANG_ENGLISH = "en";

    /**
     * Áp dụng ngôn ngữ đã lưu vào context
     */
    public static Context applyLanguage(Context context) {
        String language = getLanguage(context);
        return updateContextLocale(context, language);
    }

    /**
     * Lấy ngôn ngữ đã lưu trong SharedPreferences
     * Mặc định lần đầu là tiếng Việt
     */
    public static String getLanguage(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_LANGUAGE, LANG_VIETNAMESE);
    }

    /**
     * Lưu ngôn ngữ vào SharedPreferences (đồng bộ)
     */
    public static boolean saveLanguage(Context context, String language) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.edit().putString(KEY_LANGUAGE, language).commit();
    }

    /**
     * Thiết lập locale cho context
     */
    private static Context updateContextLocale(Context context, String language) {
        Locale locale;

        if (language.equals(LANG_ENGLISH)) {
            locale = new Locale(LANG_ENGLISH);
        } else {
            locale = new Locale(LANG_VIETNAMESE);
        }

        Locale.setDefault(locale);

        Configuration config = new Configuration(context.getResources().getConfiguration());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            config.setLocale(locale);
            return context.createConfigurationContext(config);
        } else {
            config.locale = locale;
            return context;
        }
    }

    /**
     * Lấy tên hiển thị của ngôn ngữ
     */
    public static String getLanguageDisplayName(Context context, String language) {
        switch (language) {
            case LANG_ENGLISH:
                return context.getString(R.string.language_english);
            default:
                return context.getString(R.string.language_vietnamese);
        }
    }
}
