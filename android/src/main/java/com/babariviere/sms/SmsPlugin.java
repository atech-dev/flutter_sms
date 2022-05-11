package com.babariviere.sms;

import com.babariviere.sms.permisions.Permissions;
import com.babariviere.sms.status.SmsStateHandler;

import io.flutter.plugin.common.EventChannel;
import io.flutter.plugin.common.JSONMethodCodec;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.PluginRegistry.Registrar;
import io.flutter.plugin.common.StandardMethodCodec;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.embedding.engine.plugins.activity.ActivityAware;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;

/**
 * SmsPlugin
 */
public class SmsPlugin implements FlutterPlugin, ActivityAware {
    private static final String CHANNEL_RECV = "plugins.babariviere.com/recvSMS";
    private static final String CHANNEL_SMS_STATUS = "plugins.babariviere.com/statusSMS";
    private static final String CHANNEL_SEND = "plugins.babariviere.com/sendSMS";
    private static final String CHANNEL_QUER = "plugins.babariviere.com/querySMS";
    private static final String CHANNEL_QUER_CONT = "plugins.babariviere.com/queryContact";
    private static final String CHANNEL_QUER_CONT_PHOTO = "plugins.babariviere.com/queryContactPhoto";
    private static final String CHANNEL_USER_PROFILE = "plugins.babariviere.com/userProfile";
    private static final String CHANNEL_SIM_CARDS = "plugins.babariviere.com/simCards";
    private ActivityPluginBinding currentActivity;
    
    @Nullable private MethodCallHandlerImpl methodCallHandler;
    @Nullable private UrlLauncher urlLauncher;

    /**
     * Plugin registration.
     */
    public static void registerWith(Registrar registrar) {

        registrar.addRequestPermissionsResultListener(Permissions.getRequestsResultsListener());
        this.registerMethods(registrar, registrar.messange(), null, null);
        
    }
    
    @Override
    public void onAttachedToEngine(@NonNull FlutterPluginBinding binding) {
        // TODO: your plugin is now attached to a Flutter experience.
        this.registerMethods(null, registrar.getBinaryMessenger(), binding, currentActivity);
    }

    @Override
    public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
        // TODO: your plugin is no longer attached to a Flutter experience.
    }

    @Override
    public void onAttachedToActivity(ActivityPluginBinding activityPluginBinding) {
        // TODO: your plugin is now attached to an Activity
        currentActivity = binding.getActivity();
        activityPluginBinding.addRequestPermissionsResultListener(Permissions.getRequestsResultsListener());
    }

    @Override
    public void onReattachedToActivityForConfigChanges(ActivityPluginBinding activityPluginBinding) {
        // TODO: your plugin is now attached to a new Activity
        // after a configuration change.
        currentActivity = binding.getActivity();
        activityPluginBinding.addRequestPermissionsResultListener(Permissions.getRequestsResultsListener());
    }

    @Override
    public void onDetachedFromActivityForConfigChanges() {
        // TODO: the Activity your plugin was attached to was
        // destroyed to change configuration.
        // This call will be followed by onReattachedToActivityForConfigChanges().
        currentActivity = null;
    }

    @Override
    public void onDetachedFromActivity() {
        // TODO: your plugin is no longer associated with an Activity.
        // Clean up references.
        currentActivity = null;
    }
    
    public void registerMethods(Registrar registrar, BinaryMessenger binaryMessenger, FlutterPluginBinding flutterPluginBinding, ActivityPluginBinding activityPluginBinding) {
        // SMS receiver
        final SmsReceiver receiver = new SmsReceiver(registrar, flutterPluginBinding, activityPluginBinding);
        final EventChannel receiveSmsChannel = new EventChannel(binaryMessenger,
                CHANNEL_RECV, JSONMethodCodec.INSTANCE);
        receiveSmsChannel.setStreamHandler(receiver);

        // SMS status receiver
        new EventChannel(binaryMessenger, CHANNEL_SMS_STATUS, JSONMethodCodec.INSTANCE)
                .setStreamHandler(new SmsStateHandler(registrar));

        /// SMS sender
        final SmsSender sender = new SmsSender(registrar);
        final MethodChannel sendSmsChannel = new MethodChannel(binaryMessenger,
                CHANNEL_SEND, JSONMethodCodec.INSTANCE);
        sendSmsChannel.setMethodCallHandler(sender);

        /// SMS query
        final SmsQuery query = new SmsQuery(registrar);
        final MethodChannel querySmsChannel = new MethodChannel(binaryMessenger, CHANNEL_QUER, JSONMethodCodec.INSTANCE);
        querySmsChannel.setMethodCallHandler(query);

        /// Contact query
        final ContactQuery contactQuery = new ContactQuery(registrar);
        final MethodChannel queryContactChannel = new MethodChannel(binaryMessenger, CHANNEL_QUER_CONT, JSONMethodCodec.INSTANCE);
        queryContactChannel.setMethodCallHandler(contactQuery);

        /// Contact Photo query
        final ContactPhotoQuery contactPhotoQuery = new ContactPhotoQuery(registrar);
        final MethodChannel queryContactPhotoChannel = new MethodChannel(binaryMessenger, CHANNEL_QUER_CONT_PHOTO, StandardMethodCodec.INSTANCE);
        queryContactPhotoChannel.setMethodCallHandler(contactPhotoQuery);

        /// User Profile
        final UserProfileProvider userProfileProvider = new UserProfileProvider(registrar);
        final MethodChannel userProfileProviderChannel = new MethodChannel(binaryMessenger, CHANNEL_USER_PROFILE, JSONMethodCodec.INSTANCE);
        userProfileProviderChannel.setMethodCallHandler(userProfileProvider);

        //Sim Cards Provider
        new MethodChannel(binaryMessenger, CHANNEL_SIM_CARDS, JSONMethodCodec.INSTANCE)
                .setMethodCallHandler(new SimCardsProvider(registrar));
    }
}
