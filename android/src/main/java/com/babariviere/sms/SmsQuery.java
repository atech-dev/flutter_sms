package com.babariviere.sms;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.babariviere.sms.permisions.Permissions;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.embedding.engine.plugins.activity.ActivityAware;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.PluginRegistry;

/**
 * Created by babariviere on 09/03/18.
 */

enum SmsQueryRequest {
    Inbox, Sent, Draft;

    Uri toUri() {
        switch (this) {
            case Sent:
                return Uri.parse("content://sms/sent");
            case Draft:
                return Uri.parse("content://sms/draft");
            default:
                return Uri.parse("content://sms/inbox");
        }
    }
}

public class SmsQuery
        implements FlutterPlugin,
                   ActivityAware,
                   MethodChannel.MethodCallHandler,
                   PluginRegistry.RequestPermissionsResultListener {

    private static final int READ_SMS_REQ = Permissions.READ_SMS_ID_REQ;
    private final String[] permissionsList = { Manifest.permission.READ_SMS };

    private MethodChannel channel;
    private Context context;
    private ActivityPluginBinding activityBinding;
    private Permissions permissions;

    // pending query parameters
    private MethodChannel.Result pendingResult;
    private SmsQueryRequest pendingRequest;
    private int pendingStart;
    private int pendingCount;
    private int pendingThreadId;
    private String pendingAddress;

    // 1) Register MethodChannel
    @Override
    public void onAttachedToEngine(@NonNull FlutterPluginBinding binding) {
        context = binding.getApplicationContext();
        channel = new MethodChannel(binding.getBinaryMessenger(), "sms");
        channel.setMethodCallHandler(this);
    }

    // 2) Clean up on detach
    @Override
    public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
        channel.setMethodCallHandler(null);
        channel = null;
        context = null;
    }

    // 3) ActivityAware to obtain Activity and permissions listener
    @Override
    public void onAttachedToActivity(@NonNull ActivityPluginBinding binding) {
        activityBinding = binding;
        permissions = new Permissions(binding.getActivity());
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
        permissions = new Permissions(binding.getActivity());
        binding.addRequestPermissionsResultListener(this);
    }

    @Override
    public void onDetachedFromActivity() {
        activityBinding.removeRequestPermissionsResultListener(this);
        activityBinding = null;
    }

    // 4) Handle Dart calls
    @Override
    public void onMethodCall(@NonNull MethodCall call, @NonNull MethodChannel.Result result) {
        switch (call.method) {
            case "getInbox":
                pendingRequest = SmsQueryRequest.Inbox;
                break;
            case "getSent":
                pendingRequest = SmsQueryRequest.Sent;
                break;
            case "getDraft":
                pendingRequest = SmsQueryRequest.Draft;
                break;
            default:
                result.notImplemented();
                return;
        }

        // read optional arguments
        pendingStart = call.hasArgument("start") ? call.argument("start") : 0;
        pendingCount = call.hasArgument("count") ? call.argument("count") : -1;
        pendingThreadId = call.hasArgument("thread_id") ? call.argument("thread_id") : -1;
        pendingAddress = call.hasArgument("address") ? call.argument("address") : null;

        pendingResult = result;

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                activityBinding.getActivity(),
                permissionsList,
                READ_SMS_REQ
            );
        } else {
            querySms();
        }
    }

    // 5) Permissions callback
    @Override
    public boolean onRequestPermissionsResult(int requestCode, String[] perms, int[] grantResults) {
        if (requestCode != READ_SMS_REQ) {
            return false;
        }
        for (int res : grantResults) {
            if (res != PackageManager.PERMISSION_GRANTED) {
                pendingResult.error("#01", "permission denied", null);
                return false;
            }
        }
        querySms();
        return true;
    }

    // 6) Original query logic
    private void querySms() {
        ArrayList<JSONObject> list = new ArrayList<>();
        Cursor cursor = context.getContentResolver()
            .query(pendingRequest.toUri(), null, null, null, null);

        if (cursor == null) {
            pendingResult.error("#01", "permission denied", null);
            return;
        }
        if (!cursor.moveToFirst()) {
            cursor.close();
            pendingResult.success(list);
            return;
        }

        do {
            JSONObject obj = new JSONObject();
            // read all columns
            for (int i = 0; i < cursor.getColumnCount(); i++) {
                String col = cursor.getColumnName(i);
                try {
                    switch (col) {
                        case "address":
                        case "body":
                            obj.put(col, cursor.getString(i));
                            break;
                        case "date":
                        case "date_sent":
                            obj.put(col, cursor.getLong(i));
                            break;
                        default:
                            obj.put(col, cursor.getInt(i));
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
            // apply filters
            try {
                if (pendingThreadId >= 0 && obj.getInt("thread_id") != pendingThreadId) {
                    continue;
                }
                if (pendingAddress != null && !obj.getString("address").equals(pendingAddress)) {
                    continue;
                }
            } catch (JSONException ignored) {}

            if (pendingStart > 0) {
                pendingStart--;
                continue;
            }

            list.add(obj);
            if (pendingCount > 0 && --pendingCount == 0) {
                break;
            }
        } while (cursor.moveToNext());

        cursor.close();
        pendingResult.success(list);
    }
}
