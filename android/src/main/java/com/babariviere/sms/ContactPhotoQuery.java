package com.babariviere.sms;

import android.Manifest;
import android.annotation.TargetApi;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.ContactsContract;
import android.content.Context;
import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.babariviere.sms.permisions.Permissions;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.embedding.engine.plugins.activity.ActivityAware;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.PluginRegistry;

/**
 * Created by joanpablo on 18/03/18.
 */

public class ContactPhotoQuery
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
    private String pendingPhotoUri;
    private boolean pendingFullSize;

    // 1) Regista o canal ao anexar o engine
    @Override
    public void onAttachedToEngine(@NonNull FlutterPluginBinding binding) {
        context = binding.getApplicationContext();
        channel = new MethodChannel(binding.getBinaryMessenger(), "sms");
        channel.setMethodCallHandler(this);
    }

    // 2) Limpa ao desanexar o engine
    @Override
    public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
        channel.setMethodCallHandler(null);
        channel = null;
        context = null;
    }

    // 3) ActivityAware: recebe a Activity e adiciona o listener de permissões
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

    // 4) Trata a chamada Dart "getContactPhoto"
    @Override
    public void onMethodCall(@NonNull MethodCall call, @NonNull MethodChannel.Result result) {
        if (!"getContactPhoto".equals(call.method)) {
            result.notImplemented();
            return;
        }
        if (!call.hasArgument("photoUri")) {
            result.error("#02", "missing argument 'photoUri'", null);
            return;
        }
        pendingResult = result;
        pendingPhotoUri = call.argument("photoUri");
        pendingFullSize = call.hasArgument("fullSize") && (boolean) call.argument("fullSize");

        if (ContextCompat.checkSelfPermission(context, permissionsList[0])
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                activityBinding.getActivity(),
                permissionsList,
                READ_CONTACT_ID_REQ
            );
        } else {
            handleContactPhoto();
        }
    }

    // 5) Lógica original para ler thumbnail ou foto completa
    @TargetApi(Build.VERSION_CODES.ECLAIR)
    private void handleContactPhoto() {
        if (pendingFullSize) {
            queryContactPhoto();
        } else {
            queryContactThumbnail();
        }
    }

    @TargetApi(Build.VERSION_CODES.ECLAIR)
    private void queryContactThumbnail() {
        Uri uri = Uri.withAppendedPath(ContactsContract.AUTHORITY_URI, pendingPhotoUri);
        Cursor cursor = context.getContentResolver().query(
            uri,
            new String[]{ ContactsContract.CommonDataKinds.Photo.PHOTO },
            null, null, null
        );
        if (cursor == null) {
            pendingResult.success(null);
            return;
        }
        try {
            if (cursor.moveToFirst()) {
                pendingResult.success(cursor.getBlob(0));
            } else {
                pendingResult.success(null);
            }
        } finally {
            cursor.close();
        }
    }

    public static byte[] getBytesFromInputStream(InputStream is) throws IOException {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        byte[] buffer = new byte[0xFFFF];
        for (int len = is.read(buffer); len != -1; len = is.read(buffer)) {
            os.write(buffer, 0, len);
        }
        return os.toByteArray();
    }

    @TargetApi(Build.VERSION_CODES.ECLAIR)
    private void queryContactPhoto() {
        Uri uri = Uri.withAppendedPath(ContactsContract.AUTHORITY_URI, pendingPhotoUri);
        try {
            AssetFileDescriptor fd = context.getContentResolver()
                .openAssetFileDescriptor(uri, "r");
            if (fd != null) {
                InputStream stream = fd.createInputStream();
                byte[] bytes = getBytesFromInputStream(stream);
                stream.close();
                pendingResult.success(bytes);
            } else {
                pendingResult.success(null);
            }
        } catch (IOException e) {
            e.printStackTrace();
            pendingResult.error("IO_ERROR", e.getMessage(), null);
        }
    }

    // 6) Callback de resultado de permissão
    @Override
    public boolean onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode != READ_CONTACT_ID_REQ) {
            return false;
        }
        for (int res : grantResults) {
            if (res != PackageManager.PERMISSION_GRANTED) {
                pendingResult.error("#01", "permission denied", null);
                return false;
            }
        }
        handleContactPhoto();
        return true;
    }
}
