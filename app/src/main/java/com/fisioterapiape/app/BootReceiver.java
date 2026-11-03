package com.fisioterapiape.app;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        SharedPreferences p = context.getSharedPreferences("fisio", Context.MODE_PRIVATE);
        if (p.getBoolean("reminders", false)) {
            ReminderReceiver.schedule(context, p.getInt("morning", 9), 0, 101);
            ReminderReceiver.schedule(context, p.getInt("evening", 19), 0, 102);
        }
    }
}
