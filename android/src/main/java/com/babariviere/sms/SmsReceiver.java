package com.babariviere.sms;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Telephony;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;
import android.util.Log;

import androidx.annotation.NonNull;

import com.babariviere.sms.permisions.Permissions;

import org.json.JSONObject;

import java.util.Date;

import io.flutter.plugin.common.EventChannel;
import io.flutter.plugin.common.EventChannel.EventSink;
import io.flutter.plugin.common.EventChannel.StreamHandler;
import io.flutter.plugin.common.PluginRegistry;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;

/**
 * Created by babariviere on 08/03/18.
 */

public class SmsReceiver implements StreamHandler, PluginRegistry.RequestPermissionsResultListener {
    private final Context context;
    private final Activity activity;
    private final ActivityPluginBinding activityBinding;
    private BroadcastReceiver receiver;
    private final Permissions permissions;
    private final String[] permissionsList = {
        Manifest.permission.RECEIVE_SMS,
        Manifest.permission.READ_SMS
    };
    private EventSink sink;

    public SmsReceiver(Context context, @NonNull ActivityPluginBinding activityBinding) {
        this.context = context;
        this.activityBinding = activityBinding;
        this.activity = activityBinding.getActivity();
        this.permissions = new Permissions(activity);
        activityBinding.addRequestPermissionsResultListener(this);
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    @Override
    public void onListen(Object arguments, EventSink events) {
        this.sink = events;
        receiver = createSmsReceiver(events);
        context.registerReceiver(
            receiver,
            new IntentFilter(Telephony.Sms.Intents.SMS_RECEIVED_ACTION)
        );
        permissions.checkAndRequestPermission(permissionsList, Permissions.RECV_SMS_ID_REQ);
    }

    @Override
    public void onCancel(Object arguments) {
        if (receiver != null) {
            context.unregisterReceiver(receiver);
            receiver = null;
        }
        activityBinding.removeRequestPermissionsResultListener(this);
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    private SmsMessage[] readMessages(Intent intent) {
        return Telephony.Sms.Intents.getMessagesFromIntent(intent);
    }

    private BroadcastReceiver createSmsReceiver(final EventSink events) {
        return new BroadcastReceiver() {
            @TargetApi(Build.VERSION_CODES.KITKAT)
            @Override
            public void onReceive(Context ctx, Intent intent) {
                try {
                    SmsMessage[] msgs = readMessages(intent);
                    if (msgs == null) return;

                    JSONObject obj = new JSONObject();
                    obj.put("address", msgs[0].getOriginatingAddress());
                    obj.put("date", (new Date()).getTime());
                    obj.put("date_sent", msgs[0].getTimestampMillis());
                    obj.put("read", (msgs[0].getStatusOnIcc() == SmsManager.STATUS_ON_ICC_READ) ? 1 : 0);
                    obj.put("thread_id",
                        TelephonyCompat.getOrCreateThreadId(ctx, msgs[0].getOriginatingAddress())
                    );

                    StringBuilder body = new StringBuilder();
                    for (SmsMessage msg : msgs) {
                        body.append(msg.getMessageBody());
                    }
                    obj.put("body", body.toString());

                    events.success(obj);
                } catch (Exception e) {
                    Log.d("SmsReceiver", e.toString());
                }
            }
        };
    }

    @Override
    public boolean onRequestPermissionsResult(int requestCode, String[] perms, int[] grantResults) {
        if (requestCode != Permissions.RECV_SMS_ID_REQ) {
            return false;
        }
        for (int res : grantResults) {
            if (res != PackageManager.PERMISSION_GRANTED) {
                if (sink != null) sink.endOfStream();
                return false;
            }
        }
        // permissions granted: nothing else to do since receiver is already registered
        return true;
    }
}
