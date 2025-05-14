package com.babariviere.sms;

import android.content.Context;

import androidx.annotation.NonNull;

import com.babariviere.sms.permisions.Permissions;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.embedding.engine.plugins.activity.ActivityAware;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.plugin.common.EventChannel;
import io.flutter.plugin.common.JSONMethodCodec;

/**
 * SmsPlugin
 */
public class SmsPlugin implements FlutterPlugin, ActivityAware {
    private static final String CHANNEL_RECV = "plugins.babariviere.com/recvSMS";

    private EventChannel receiveSmsChannel;
    private Context context;
    private ActivityPluginBinding activityBinding;

    // 1) Regista o canal quando o engine é anexado
    @Override
    public void onAttachedToEngine(@NonNull FlutterPluginBinding binding) {
        this.context = binding.getApplicationContext();
        receiveSmsChannel = new EventChannel(
            binding.getBinaryMessenger(),
            CHANNEL_RECV,
            JSONMethodCodec.INSTANCE
        );
    }

    // 2) Limpa o canal quando o engine é desanexado
    @Override
    public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
        if (receiveSmsChannel != null) {
            receiveSmsChannel.setStreamHandler(null);
            receiveSmsChannel = null;
        }
        context = null;
    }

    // 3) Quando a Activity é anexada, regista permissões e o receiver
    @Override
    public void onAttachedToActivity(@NonNull ActivityPluginBinding binding) {
        this.activityBinding = binding;
        binding.addRequestPermissionsResultListener(Permissions.getRequestsResultsListener());

        // SMS receiver
        SmsReceiver receiver = new SmsReceiver(
            context,
            binding
        );
        receiveSmsChannel.setStreamHandler(receiver);
    }

    // 4) Config change: a Activity foi destruída, mas o plugin permanece vivo
    @Override
    public void onDetachedFromActivityForConfigChanges() {
        // Não remove o StreamHandler aqui, pois será reatached a seguir
    }

    // 5) Config change: nova Activity anexada
    @Override
    public void onReattachedToActivityForConfigChanges(@NonNull ActivityPluginBinding binding) {
        this.activityBinding = binding;
        binding.addRequestPermissionsResultListener(Permissions.getRequestsResultsListener());

        // Re-regista o receiver na nova Activity
        SmsReceiver receiver = new SmsReceiver(
            context,
            binding
        );
        receiveSmsChannel.setStreamHandler(receiver);
    }

    // 6) Quando a Activity final é desanexada, limpa o receiver
    @Override
    public void onDetachedFromActivity() {
        if (receiveSmsChannel != null) {
            receiveSmsChannel.setStreamHandler(null);
        }
        activityBinding = null;
    }

    // Note que removemos completamente o método estático registerWith,
    // pois a integração agora é feita via onAttachedToEngine / ActivityAware.
}
