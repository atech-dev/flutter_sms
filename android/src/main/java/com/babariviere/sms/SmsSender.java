package com.babariviere.sms;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Telephony;
import android.telephony.SmsManager;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.babariviere.sms.permisions.Permissions;

import java.util.UUID;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.embedding.engine.plugins.activity.ActivityAware;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.PluginRegistry;
import io.flutter.plugin.common.JSONMethodCodec;

/**
 * Created by babariviere on 08/03/18.
 */

public class SmsSender
    implements FlutterPlugin,
               ActivityAware,
               MethodChannel.MethodCallHandler,
               PluginRegistry.RequestPermissionsResultListener {

    private static final String CHANNEL_SEND = "plugins.babariviere.com/sendSMS";
    private static final int PERMISSION_REQUEST_CODE = Permissions.SEND_SMS_ID_REQ;
    private final String[] permissionsList = {
        Manifest.permission.SEND_SMS,
        Manifest.permission.READ_PHONE_STATE
    };

    private MethodChannel channel;
    private Context context;
    private ActivityPluginBinding activityBinding;

    // pending parameters for SMS send
    private MethodChannel.Result pendingResult;
    private String pendingAddress;
    private String pendingBody;
    private int pendingSentId;
    private Integer pendingSubId;

    /** 
     * Register the MethodChannel when engine is attached 
     */
    @Override
    public void onAttachedToEngine(@NonNull FlutterPluginBinding binding) {
        context = binding.getApplicationContext();
        channel = new MethodChannel(binding.getBinaryMessenger(), CHANNEL_SEND, JSONMethodCodec.INSTANCE);
        channel.setMethodCallHandler(this);
    }

    /**
     * Clean up the MethodChannel when engine is detached
     */
    @Override
    public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
        channel.setMethodCallHandler(null);
        channel = null;
        context = null;
    }

    /** 
     * Obtain Activity reference and register permission listener 
     */
    @Override
    public void onAttachedToActivity(@NonNull ActivityPluginBinding binding) {
        activityBinding = binding;
        activityBinding.addRequestPermissionsResultListener(this);
    }

    @Override
    public void onDetachedFromActivityForConfigChanges() {
        activityBinding.removeRequestPermissionsResultListener(this);
        activityBinding = null;
    }

    @Override
    public void onReattachedToActivityForConfigChanges(@NonNull ActivityPluginBinding binding) {
        activityBinding = binding;
        activityBinding.addRequestPermissionsResultListener(this);
    }

    @Override
    public void onDetachedFromActivity() {
        activityBinding.removeRequestPermissionsResultListener(this);
        activityBinding = null;
    }

    /**
     * Handle Dart method calls
     */
    @Override
    public void onMethodCall(@NonNull MethodCall call, @NonNull MethodChannel.Result result) {
        if (!"sendSMS".equals(call.method)) {
            result.notImplemented();
            return;
        }

        // extract arguments
        pendingAddress = call.argument("address");
        pendingBody    = call.argument("body");
        pendingSentId  = call.argument("sentId");
        pendingSubId   = call.argument("subId");

        pendingResult = result;

        // check and request permissions if needed
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS)
            != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE)
            != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(
                activityBinding.getActivity(),
                permissionsList,
                PERMISSION_REQUEST_CODE
            );

        } else {
            sendSmsMessage();
        }
    }

    /**
     * Callback for permission request results
     */
    @Override
    public boolean onRequestPermissionsResult(int requestCode, String[] perms, int[] grantResults) {
        if (requestCode != PERMISSION_REQUEST_CODE) {
            return false;
        }

        // verify all permissions granted
        for (int res : grantResults) {
            if (res != PackageManager.PERMISSION_GRANTED) {
                pendingResult.error("#01", "permission denied for sending sms", null);
                return false;
            }
        }

        // permissions granted, proceed to send SMS
        sendSmsMessage();
        return true;
    }

    /**
     * Original logic to send an SMS message
     */
    @TargetApi(Build.VERSION_CODES.KITKAT)
    private void sendSmsMessage() {
        // prepare intents for sent and delivered events
        Intent sentIntent = new Intent("SMS_SENT");
        sentIntent.putExtra("sentId", pendingSentId);
        PendingIntent sentPI = PendingIntent.getBroadcast(
            context,
            0,
            sentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT
        );

        Intent deliveredIntent = new Intent("SMS_DELIVERED");
        deliveredIntent.putExtra("sentId", pendingSentId);
        PendingIntent deliveredPI = PendingIntent.getBroadcast(
            context,
            UUID.randomUUID().hashCode(),
            deliveredIntent,
            PendingIntent.FLAG_UPDATE_CURRENT
        );

        // choose appropriate SmsManager instance
        SmsManager smsManager;
        if (pendingSubId == null) {
            smsManager = SmsManager.getDefault();
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            smsManager = SmsManager.getSmsManagerForSubscriptionId(pendingSubId);
        } else {
            pendingResult.error("#03", "this version of android does not support multicard SIM", null);
            return;
        }

        // send the text message
        smsManager.sendTextMessage(pendingAddress, null, pendingBody, sentPI, deliveredPI);
        pendingResult.success(null);
    }
}

