package com.babariviere.sms;

import android.Manifest;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.ContactsContract;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONException;
import org.json.JSONObject;

import com.babariviere.sms.permisions.Permissions;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.embedding.engine.plugins.activity.ActivityAware;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.PluginRegistry;

/**
 * Created by babariviere on 10/03/18.
 */

public class ContactQuery
    implements FlutterPlugin,
               ActivityAware,
               MethodChannel.MethodCallHandler,
               PluginRegistry.RequestPermissionsResultListener {

    private static final int READ_CONTACT_ID_REQ = Permissions.READ_CONTACT_ID_REQ;
    private final String[] permissionsList = { Manifest.permission.READ_CONTACTS };

    private MethodChannel channel;
    private Context context;
    private ActivityPluginBinding activityBinding;
    private Permissions permissions;
    private MethodChannel.Result pendingResult;
    private String pendingAddress;

    // Registo do canal
    @Override
    public void onAttachedToEngine(@NonNull FlutterPluginBinding binding) {
        context = binding.getApplicationContext();
        channel = new MethodChannel(binding.getBinaryMessenger(), "sms");
        channel.setMethodCallHandler(this);
    }

    @Override
    public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
        channel.setMethodCallHandler(null);
        channel = null;
        context = null;
    }

    // ActivityAware para obter Activity e listener de permissões
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

    // Trata o método Dart "getContact"
    @Override
    public void onMethodCall(@NonNull MethodCall call, @NonNull MethodChannel.Result result) {
        if (!"getContact".equals(call.method)) {
            result.notImplemented();
            return;
        }
        if (!call.hasArgument("address")) {
            result.error("#02", "missing argument 'address'", null);
            return;
        }
        pendingResult = result;
        pendingAddress = call.argument("address");

        if (ContextCompat.checkSelfPermission(context, permissionsList[0])
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                activityBinding.getActivity(),
                permissionsList,
                READ_CONTACT_ID_REQ
            );
        } else {
            queryContact();
        }
    }

    // Callback de permissão
    @Override
    public boolean onRequestPermissionsResult(int requestCode, String[] perms, int[] grantResults) {
        if (requestCode != READ_CONTACT_ID_REQ) {
            return false;
        }
        for (int res : grantResults) {
            if (res != PackageManager.PERMISSION_GRANTED) {
                pendingResult.error("#01", "permission denied", null);
                return false;
            }
        }
        queryContact();
        return true;
    }

    // Lógica original de consulta de contacto
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    private void queryContact() {
        Uri uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(pendingAddress)
        );
        String[] projection = {
            ContactsContract.PhoneLookup.DISPLAY_NAME,
            ContactsContract.PhoneLookup.PHOTO_URI,
            ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI
        };
        JSONObject obj = new JSONObject();
        Cursor cursor = context.getContentResolver().query(uri, projection, null, null, null);
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                try {
                    obj.put("name", cursor.getString(0));
                    obj.put("photo", cursor.getString(1));
                    obj.put("thumbnail", cursor.getString(2));
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
            cursor.close();
        }
        pendingResult.success(obj);
    }
}
