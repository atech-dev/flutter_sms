package com.babariviere.sms.status;

import android.Manifest;
import android.annotation.TargetApi;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.annotation.NonNull;

import com.babariviere.sms.permisions.Permissions;

import io.flutter.embedding.engine.plugins.activity.ActivityAware;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.plugin.common.EventChannel;
import io.flutter.plugin.common.PluginRegistry;

/**
 * Created by Joan Pablo on 4/17/2018.
 */

public class SmsStateHandler
    implements EventChannel.StreamHandler,
               PluginRegistry.RequestPermissionsResultListener,
               ActivityAware {

    private BroadcastReceiver smsStateChangeReceiver;
    private ActivityPluginBinding activityBinding;
    private Permissions permissions;
    private EventChannel.EventSink eventSink;

    // ---------------- ActivityAware ----------------

    @Override
    public void onAttachedToActivity(@NonNull ActivityPluginBinding binding) {
        this.activityBinding = binding;
        this.permissions = new Permissions(binding.getActivity());
        binding.addRequestPermissionsResultListener(this);
    }

    @Override
    public void onDetachedFromActivityForConfigChanges() {
        activityBinding.removeRequestPermissionsResultListener(this);
        this.activityBinding = null;
    }

    @Override
    public void onReattachedToActivityForConfigChanges(@NonNull ActivityPluginBinding binding) {
        this.activityBinding = binding;
        this.permissions = new Permissions(binding.getActivity());
        binding.addRequestPermissionsResultListener(this);
    }

    @Override
    public void onDetachedFromActivity() {
        activityBinding.removeRequestPermissionsResultListener(this);
        this.activityBinding = null;
    }

    // ------------ EventChannel.StreamHandler ------------

    @Override
    public void onListen(Object arguments, EventChannel.EventSink eventSink) {
        this.eventSink = eventSink;
        smsStateChangeReceiver = new SmsStateChangeReceiver(eventSink);

        if (permissions.checkAndRequestPermission(
                new String[]{ Manifest.permission.RECEIVE_SMS },
                Permissions.BROADCAST_SMS)) {
            registerDeliveredReceiver();
            registerSentReceiver();
        }
    }

    @Override
    public void onCancel(Object arguments) {
        if (smsStateChangeReceiver != null && activityBinding != null) {
            activityBinding.getActivity().unregisterReceiver(smsStateChangeReceiver);
            smsStateChangeReceiver = null;
        }
    }

    // ----------- Receivers Registration ------------

    @TargetApi(Build.VERSION_CODES.KITKAT)
    private void registerDeliveredReceiver() {
        activityBinding.getActivity().registerReceiver(
            smsStateChangeReceiver,
            new IntentFilter("SMS_DELIVERED")
        );
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    private void registerSentReceiver() {
        activityBinding.getActivity().registerReceiver(
            smsStateChangeReceiver,
            new IntentFilter("SMS_SENT")
        );
    }

    // ------ PluginRegistry.RequestPermissionsResultListener ------

    @TargetApi(Build.VERSION_CODES.KITKAT)
    @Override
    public boolean onRequestPermissionsResult(int requestCode, String[] perms, int[] grantResults) {
        if (requestCode != Permissions.BROADCAST_SMS) {
            return false;
        }
        boolean isOk = true;
        for (int res : grantResults) {
            if (res != PackageManager.PERMISSION_GRANTED) {
                isOk = false;
                break;
            }
        }
        if (isOk) {
            registerDeliveredReceiver();
            registerSentReceiver();
            return true;
        }
        if (eventSink != null) {
            eventSink.error("#01", "permission denied", null);
        }
        return false;
    }
}

