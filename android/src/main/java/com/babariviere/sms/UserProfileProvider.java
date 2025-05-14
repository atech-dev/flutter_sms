package com.babariviere.sms;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONArray;
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
 * Created by Joan Pablo on 4/11/2018.
 */

class UserProfileProvider implements FlutterPlugin, MethodChannel.MethodCallHandler, ActivityAware,
               PluginRegistry.RequestPermissionsResultListener {
    
    private static final int READ_CONTACT_ID_REQ = Permissions.READ_CONTACT_ID_REQ;
    private final String[] permissionsList = { Manifest.permission.READ_CONTACTS };

    private MethodChannel channel;
    private Context context;
    private ActivityPluginBinding activityBinding;
    private Permissions permissions;
    private MethodChannel.Result pendingResult;

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

    @Override
    public void onAttachedToActivity(@NonNull ActivityPluginBinding binding) {
        activityBinding = binding;
        binding.addRequestPermissionsResultListener(this);
        permissions = new Permissions(binding.getActivity());
    }

    @Override
    public void onDetachedFromActivity() {
        activityBinding.removeRequestPermissionsResultListener(this);
        activityBinding = null;
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
        permissions = new Permissions(binding.getActivity());
    }

    @Override
    public void onMethodCall(@NonNull MethodCall call, @NonNull MethodChannel.Result result) {
        if (!"getUserProfile".equals(call.method)) {
            result.notImplemented();
            return;
        }
        pendingResult = result;
        if (ContextCompat.checkSelfPermission(context, permissionsList[0])
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                activityBinding.getActivity(),
                permissionsList,
                READ_CONTACT_ID_REQ
            );
        } else {
            queryUserProfile();
        }
    }

    @Override
    public boolean onRequestPermissionsResult(int requestCode, String[] perms, int[] grantResults) {
        if (requestCode != READ_CONTACT_ID_REQ) {
            return false;
        }
        for (int res : grantResults) {
            if (res != PackageManager.PERMISSION_GRANTED) {
                pendingResult.error("PERMISSION_DENIED", "Permissão negada", null);
                return false;
            }
        }
        queryUserProfile();
        return true;
    }

    private void queryUserProfile() {
        try {
            JSONObject obj = getProfileObject();
            if (obj != null) {
                obj.put("addresses", getProfileAddresses(obj.getString("id")));
            }
            pendingResult.success(obj);
        } catch (JSONException e) {
            e.printStackTrace();
            pendingResult.error("JSON_ERROR", e.getMessage(), null);
        }
    }

    private JSONObject getProfileObject() {
        JSONObject obj = null;
        String[] projection = {
            ContactsContract.Profile._ID,
            ContactsContract.Profile.DISPLAY_NAME,
            ContactsContract.Profile.PHOTO_URI,
            ContactsContract.Profile.PHOTO_THUMBNAIL_URI,
        };
        Cursor cursor = context.getContentResolver()
            .query(ContactsContract.Profile.CONTENT_URI, projection, null, null, null);
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                obj = new JSONObject();
                try {
                    obj.put("id", cursor.getString(0));
                    obj.put("name", cursor.getString(1));
                    obj.put("photo", cursor.getString(2));
                    obj.put("thumbnail", cursor.getString(3));
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
            cursor.close();
        }
        return obj;
    }

    private JSONArray getProfileAddresses(String profileId) {
        JSONArray addressCollection = new JSONArray();
        if (profileId != null) {
            Uri contentUri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_URI, profileId);
            Uri uri = Uri.withAppendedPath(contentUri, ContactsContract.Contacts.Entity.CONTENT_DIRECTORY);
            String[] projection = {
                ContactsContract.Contacts.Entity.DATA1,
                ContactsContract.Contacts.Entity.MIMETYPE
            };
            Cursor cursor = context.getContentResolver().query(uri, projection, null, null, null);
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    if ("vnd.android.cursor.item/phone_v2".equals(cursor.getString(1))) {
                        addressCollection.put(cursor.getString(0));
                    }
                }
                cursor.close();
            }
        }
        return addressCollection;
    }
}