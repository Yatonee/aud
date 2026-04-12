package com.example.aud;

/**
 * BootReceiver - BroadcastReceiver khôi phục alarm sau khi khởi động lại thiết bị
 * 
 * Vấn đề: Khi điện thoại khởi động lại, tất cả alarm đã đặt sẽ bị mất.
 * 
 * Giải pháp: BootReceiver lắng nghe:
 * - ACTION_BOOT_COMPLETED: Khi thiết bị khởi động xong
 * - ACTION_MY_PACKAGE_REPLACED: Khi app được cập nhật
 * 
 * Khi nhận được broadcast, gọi AlarmScheduler.scheduleFromPrefs()
 * để khôi phục tất cả alarm đã đặt trước đó.
 * 
 * Đăng ký trong AndroidManifest.xml:
 * <receiver android:name=".BootReceiver">
 *     <intent-filter>
 *         <action android:name="android.intent.action.BOOT_COMPLETED" />
 *         <action android:name="android.intent.action.MY_PACKAGE_REPLACED" />
 *     </intent-filter>
 * </receiver>
 */

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * BroadcastReceiver nhận boot completed và package replaced broadcasts
 */
public class BootReceiver extends BroadcastReceiver {
    
    /**
     * Callback được gọi khi nhận được boot completed hoặc package replaced broadcast
     * 
     * @param context Context của app
     * @param intent Intent chứa action
     */
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) {
            return;
        }

        String action = intent.getAction();
        
        // Khôi phục alarm khi:
        // 1. Thiết bị khởi động xong (BOOT_COMPLETED)
        // 2. App được cập nhật phiên bản mới (MY_PACKAGE_REPLACED)
        if (Intent.ACTION_BOOT_COMPLETED.equals(action) || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            AlarmScheduler.scheduleFromPrefs(context);
        }
    }
}