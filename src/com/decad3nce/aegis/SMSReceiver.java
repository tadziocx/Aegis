package com.decad3nce.aegis;

import com.decad3nce.aegis.Fragments.SMSAlarmFragment;
import com.decad3nce.aegis.Fragments.SMSLocateFragment;
import com.decad3nce.aegis.Fragments.SMSLockFragment;
import com.decad3nce.aegis.Fragments.SMSWipeFragment;

import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.telephony.SmsMessage;
import android.util.Log;

public class SMSReceiver extends BroadcastReceiver {
    private static final String TAG = "aeGis";
    
    private static final String ACTION_SMS_RECEIVED = "android.provider.Telephony.SMS_RECEIVED";
    private static final String EXTRA_SMS_PDUS = "pdus";
    protected static String address;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction().equals(ACTION_SMS_RECEIVED)) {
            Bundle extras = intent.getExtras();
            if (extras != null) {
                SharedPreferences preferences = PreferenceManager
                        .getDefaultSharedPreferences(context);

                Log.i(TAG, "Received SMS");

                SmsMessage[] messages = getMessagesFromIntent(intent);
                for (SmsMessage sms : messages) {
                    String body = sms.getMessageBody();
                    address = sms.getOriginatingAddress();
                    // TODO: whitelist/blacklist of allowed senders

                    boolean alarmEnabled = preferences.getBoolean(
                            AegisActivity.PREFERENCES_ALARM_ENABLED,
                            context.getResources().getBoolean(
                                    R.bool.config_default_alarm_enabled));
                    boolean lockEnabled = preferences.getBoolean(
                            AegisActivity.PREFERENCES_LOCK_ENABLED,
                            context.getResources().getBoolean(
                                    R.bool.config_default_lock_enabled));
                    boolean wipeEnabled = preferences.getBoolean(
                            AegisActivity.PREFERENCES_WIPE_ENABLED,
                            context.getResources().getBoolean(
                                    R.bool.config_default_wipe_enabled));
                    boolean locateEnabled = preferences.getBoolean(
                            AegisActivity.PREFERENCES_LOCATE_ENABLED,
                            context.getResources().getBoolean(
                                    R.bool.config_default_locate_enabled));

                    String activationAlarmSms = preferences
                            .getString(
                                    SMSAlarmFragment.PREFERENCES_ALARM_ACTIVATION_SMS,
                                    context.getResources()
                                            .getString(
                                                    R.string.config_default_alarm_activation_sms));
                    String activationLockSms = preferences
                            .getString(
                                    SMSLockFragment.PREFERENCES_LOCK_ACTIVATION_SMS,
                                    context.getResources()
                                            .getString(
                                                    R.string.config_default_lock_activation_sms));
                    String activationWipeSms = preferences
                            .getString(
                                    SMSWipeFragment.PREFERENCES_WIPE_ACTIVATION_SMS,
                                    context.getResources()
                                            .getString(
                                                    R.string.config_default_wipe_activation_sms));
                    String activationLocateSms = preferences
                            .getString(
                                    SMSLocateFragment.PREFERENCES_LOCATE_ACTIVATION_SMS,
                                    context.getResources()
                                            .getString(
                                                    R.string.config_default_locate_activation_sms));
                    boolean locateLockPref = preferences.getBoolean(
                            SMSLocateFragment.PREFERENCES_LOCATE_LOCK_PREF,
                            context.getResources().getBoolean(
                                    R.bool.config_default_locate_lock_pref));

                    if (alarmEnabled && body.startsWith(activationAlarmSms)) {
                        try {
                            Utils.alarmNotification(context);
                            Log.i(TAG, "Alarm successfully started");
                            Utils.sendSMS(context, address,
                                    context.getResources().getString(R.string.util_sendsms_alarm_pass));
                        } catch (Exception e) {
                            Log.e(TAG, "Failed to alarm");
                            Log.e(TAG, e.toString());
                            Utils.sendSMS(context, address,
                                    context.getResources().getString(R.string.util_sendsms_alarm_fail) + " " + e.toString());
                        }
                    }

                    if ((lockEnabled && body.startsWith(activationLockSms))
                            || (locateLockPref && body
                                    .startsWith(activationLocateSms))) {
                        Utils.lockDevice(context, body, activationLockSms, activationLocateSms);
                    }

                    if (wipeEnabled && body.startsWith(activationWipeSms)) {
                        DevicePolicyManager devicePolicyManager = (DevicePolicyManager) context
                                .getSystemService(Context.DEVICE_POLICY_SERVICE);
                        if (devicePolicyManager
                                .isAdminActive(AegisActivity.DEVICE_ADMIN_COMPONENT)) {
                            try {
                                Log.i(TAG, "Wiping device");
                                devicePolicyManager.wipeData(0);
                                Utils.sendSMS(context, address,
                                        context.getResources().getString(R.string.util_sendsms_wipe_pass));
                            } catch (Exception e) {
                                Log.e(TAG, "Failed to wipe device");
                                Log.e(TAG, e.toString());
                                Utils.sendSMS(context, address,
                                        context.getResources().getString(R.string.util_sendsms_wipe_fail) + " " + e.toString());
                            }
                        }
                    }

                    if (locateEnabled && body.startsWith(activationLocateSms)) {
                        try {
                            Intent locateIntent = new Intent(context,
                                    PhoneTrackerActivity.class);
                            locateIntent
                                    .addFlags(
                                            Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    .addFlags(
                                            Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                                    .putExtra("address", address);
                            context.startActivity(locateIntent);

                            Log.i(TAG, "Locate intent sent");
                        } catch (Exception e) {
                            Log.e(TAG, "Failed to locate device");
                            Log.e(TAG, e.toString());
                            Utils.sendSMS(context, address,
                                    context.getResources().getString(R.string.util_sendsms_locate_fail) + " "
                                            + e.toString());
                        }
                    }
                }
            }
        }
    }

    private SmsMessage[] getMessagesFromIntent(Intent intent) {
        Object[] messages = (Object[]) intent
                .getSerializableExtra(EXTRA_SMS_PDUS);
        byte[][] pduObjs = new byte[messages.length][];

        for (int i = 0; i < messages.length; i++) {
            pduObjs[i] = (byte[]) messages[i];
        }
        byte[][] pdus = new byte[pduObjs.length][];
        int pduCount = pdus.length;
        SmsMessage[] msgs = new SmsMessage[pduCount];
        for (int i = 0; i < pduCount; i++) {
            pdus[i] = pduObjs[i];
            msgs[i] = SmsMessage.createFromPdu(pdus[i]);
        }
        return msgs;
    }
}
