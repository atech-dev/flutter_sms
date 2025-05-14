package com.babariviere.sms;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.babariviere.sms.permisions.Permissions;
import com.babariviere.sms.telephony.TelephonyManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.embedding.engine.plugins.activity.ActivityAware;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.PluginRegistry;

public class SimCardsProvider
    implements FlutterPlugin,
               ActivityAware,
               MethodChannel.MethodCallHandler,
               PluginRegistry.RequestPermissionsResultListener {

    private static final int READ_PHONE_STATE_REQ = Permissions.READ_PHONE_STATE;
    private final String[] permissionsList = { Manifest.permission.READ_PHONE_STATE };

    private MethodChannel channel;
    private Context context;
    private ActivityPluginBinding activityBinding;
    private MethodChannel.Result pendingResult;

    // Regista o canal ao anexar o engine
    @Override
    public void onAttachedToEngine(@NonNull FlutterPluginBinding binding) {
        context = binding.getApplicationContext();
        channel = new MethodChannel(binding.getBinaryMessenger(), "sms");
        channel.setMethodCallHandler(this);
    }

    // Limpa ao desanexar o engine
    @Override
    public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
        channel.setMethodCallHandler(null);
        channel = null;
        context = null;
    }

    // ActivityAware: obtém a Activity e adiciona listener de permissões
    @Override
    public void onAttachedToActivity(@NonNull ActivityPluginBinding binding) {
        activityBinding = binding;
        binding.addRequestPermissionsResultListener(this);
    }

    @Override
    public void onDetachedFromActivityForConfigChanges() {
        activityBinding.removeRequestPermissionsResultListener(this);
        activityBinding = null;
    }

    @Override
    public void onReattachedToActivityForConfigChanges(@NonNull ActivityPluginBinding binding) {
        activityBinding = binding;
        binding.addRequestPermissionsResultListener(this);
    }

    @Override
    public void onDetachedFromActivity() {
        activityBinding.removeRequestPermissionsResultListener(this);
        activityBinding = null;
    }

    // Trata a chamada Dart "getSimCards"
    @Override
    public void onMethodCall(@NonNull MethodCall call, @NonNull MethodChannel.Result result) {
        if (!"getSimCards".equals(call.method)) {
            result.notImplemented();
            return;
        }
        pendingResult = result;
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                activityBinding.getActivity(),
                permissionsList,
                READ_PHONE_STATE_REQ
            );
        } else {
            getSimCards();
        }
    }

    // Callback de permissão
    @Override
    public boolean onRequestPermissionsResult(int requestCode, String[] perms, int[] grantResults) {
        if (requestCode != READ_PHONE_STATE_REQ) {
            return false;
        }
        for (int res : grantResults) {
            if (res != PackageManager.PERMISSION_GRANTED) {
                pendingResult.error("#01", "permission denied", null);
                return false;
            }
        }
        getSimCards();
        return true;
    }

    // Lógica original de obtenção dos SIM cards
    private void getSimCards() {
        JSONArray simCards = new JSONArray();
        try {
            TelephonyManager telephonyManager = new TelephonyManager(context);
            int phoneCount = telephonyManager.getSimCount();
            for (int i = 0; i < phoneCount; i++) {
                JSONObject simCard = new JSONObject();
                simCard.put("slot", i + 1);
                simCard.put("imei", telephonyManager.getSimId(i));
                simCard.put("state", telephonyManager.getSimState(i));
                simCards.put(simCard);
            }
        } catch (JSONException e) {
            e.printStackTrace();
            pendingResult.error("2", e.getMessage(), null);
            return;
        }
        pendingResult.success(simCards);
    }
}
