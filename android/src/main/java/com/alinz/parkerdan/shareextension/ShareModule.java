package com.alinz.parkerdan.shareextension;

import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.Arguments;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.Set;
import java.util.ArrayList;
import java.text.SimpleDateFormat;
import java.util.Date;

class CameraConstants {
    public static final int PICTURE = 0;
    public static final int VIDEO = 1;

    public static final int JPEG = 0;
    public static final int PNG = 1;
    public static final String JPEG_EXTENSION = ".jpg";
    public static final String PNG_EXTENSION = ".png";
    public static final String MP4_EXTENSION = ".mp4";
    public static final String JPEG_MIME_TYPE = "image/jpeg";
    public static final String PNG_MIME_TYPE = "image/png";
    public static final String MP4_MIME_TYPE = "video/mp4";

    public static final String TIME_FORMAT = "yyyyMMdd_HHmmss";

    public static final int FOCUS_INDICATOR_FADE_DURATION = 750;
    public static final int ZOOM_INDICATOR_FADE_DURATION = 1500;
    public static final int DEFAULT_MAX_VIDEO_DURATION = 120 * 1000;

    public static final int CIF = 0;
    public static final int QUALITY_480P = 1;
    public static final int QUALITY_720P = 2;
    public static final int QUALITY_1080P = 3;

    public static final int DEFAULT_THUMBNAIL_SIZE = 300;

    public static final String GALLERY_COPY_PREFIX = "smx_gallery_copy_";
}

public class ShareModule extends ReactContextBaseJavaModule {
    private static final String TEMP_TAG = "temp";
    private static ReactApplicationContext context;

    public ShareModule(ReactApplicationContext reactContext) {
        super(reactContext);
        ShareModule.context = reactContext;
    }

    @Override
    public String getName() {
        return "ReactNativeShareExtension";
    }

    @ReactMethod
    public void close() {
        clearCache();
        getCurrentActivity().finish();
    }

    @ReactMethod
    public void data(Promise promise) {
        promise.resolve(processIntent());
    }

    private boolean isImage(String type) {
        if (type != null && type.contains("image")) {
            return true;
        } else {
            return false;
        }
    }

    /** ==== File Helper Functions ==== */

    public static String getTempDirectoryPath() {
        File cache = null;

        // SD Card Mounted
        if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            cache = ShareModule.context.getExternalCacheDir();
        }
        // Use internal storage
        else {
            cache = ShareModule.context.getCacheDir();
        }

        // Create the cache directory if it doesn't exist
        cache.mkdirs();
        return cache.getAbsolutePath();
    }

    public static File getTempCacheDirectory() {
        File mediaStorageDir = new File(getTempDirectoryPath(), TEMP_TAG);
        if (!mediaStorageDir.exists()) {
            if (!mediaStorageDir.mkdirs()) {
                return null;
            }
        }
        return mediaStorageDir;
    }

    public static boolean clearCache() {
        File cacheDirectory = getTempCacheDirectory();
        try {
            if (cacheDirectory != null && cacheDirectory.isDirectory()) {
                for (File child: cacheDirectory.listFiles()) {
                    child.delete();
                }
            }
            return cacheDirectory.delete();
        } catch (Exception e) {
            return false;
        }
    }

    public static File createTempCaptureFile(int encodingType) {
        return createTempCaptureFile(encodingType, "");
    }

    public static File createTempCaptureFile(int encodingType, String fileName) {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmssSSSS").format(new Date());
        if (fileName.isEmpty()) {
            fileName = timeStamp;
        }

        if (encodingType == CameraConstants.JPEG) {
            fileName = fileName + CameraConstants.JPEG_EXTENSION;
        } else if (encodingType == CameraConstants.PNG) {
            fileName = fileName + CameraConstants.PNG_EXTENSION;
        } else {
            throw new IllegalArgumentException("Invalid Encoding Type: " + encodingType);
        }

        return new File(getTempCacheDirectory(), fileName);
    }

    public WritableArray processIntent() {
        WritableArray dataArrayMap = Arguments.createArray();
        Set<String> mediaTypesSupported = new HashSet<String>();
        // mediaTypesSupported.add("video");
        // mediaTypesSupported.add("audio");
        mediaTypesSupported.add("image");
        mediaTypesSupported.add("application/pdf");

        String type = "";
        String action = "";
        String typePart = "";

        Activity currentActivity = getCurrentActivity();
        clearCache();

        if (currentActivity != null) {
            Intent intent = currentActivity.getIntent();
            action = intent.getAction();
            type = intent.getType();

            if (type == null) {
                type = "";
            } else {
                typePart = type.substring(0, type.indexOf("/"));
            }
            if (Intent.ACTION_SEND.equals(action) && "text/plain".equals(type)) {
                WritableMap dataMap = Arguments.createMap();
                dataMap.putString("type", type);
                dataMap.putString("value", intent.getStringExtra(Intent.EXTRA_TEXT));
                dataArrayMap.pushMap(dataMap);
            } else if (Intent.ACTION_SEND.equals(action) && (
                    mediaTypesSupported.contains(typePart) || mediaTypesSupported.contains(type))) {
                WritableMap dataMap = Arguments.createMap();
                dataMap.putString("type", type);

                Uri uri = (Uri) intent.getParcelableExtra(Intent.EXTRA_STREAM);
                String path =  "file://" + RealPathUtil.getRealPathFromURI(currentActivity, uri);

                // Check the path coming from the uri for type, intent.getType() only returns image/jpeg for some reason
                if (this.isImage(type) && !path.contains("jpg")) {
                    // make a jpeg copy of the image in the cache directory, return that URI instead
                    try {
                        Bitmap bitmap = BitmapFactory.decodeFile(RealPathUtil.getRealPathFromURI(currentActivity, uri));
                        File outputFile = createTempCaptureFile(CameraConstants.JPEG);
                        OutputStream outStream = new FileOutputStream(outputFile);
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outStream);
                        outStream.flush();
                        outStream.close();
                        dataMap.putString("value", "file://" + outputFile.getAbsolutePath());
                        Log.i("smx-share: succeeded", outputFile.getAbsolutePath());
                    } catch (Exception e) {
                        Log.e("smx-share: exception", e.getMessage());
                        dataMap.putString("value", path);
                    }
                } else {
                    Log.i("smx-share: no convert", type);
                    dataMap.putString("value", path);
                }

                dataArrayMap.pushMap(dataMap);
            } else if (Intent.ACTION_SEND_MULTIPLE.equals(action) && mediaTypesSupported.contains(typePart)) {
                ArrayList<Uri> uris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
                for (Uri uri : uris) {
                    WritableMap dataMap = Arguments.createMap();
                    dataMap.putString("type", type);
                    dataMap.putString("value", "file://" + RealPathUtil.getRealPathFromURI(currentActivity, uri));
                    dataArrayMap.pushMap(dataMap);
                }
            }
        }
        return dataArrayMap;
    }
}
