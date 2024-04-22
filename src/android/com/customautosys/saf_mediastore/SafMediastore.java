package com.customautosys.saf_mediastore;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.FileUtils;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Base64InputStream;
import android.util.Base64OutputStream;
import android.util.Log;
import android.webkit.MimeTypeMap;
import android.webkit.ValueCallback;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaArgs;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

/**
 * @noinspection StringEqualsEmptyString, Convert2Lambda
 */
public class SafMediastore extends CordovaPlugin implements ValueCallback<String> {
    private short lastCallbackIndex = 0;
    private final HashMap<Short, CallbackContext> callbackContexts = new HashMap<>();
    private final HashMap<String, String> saveFileData = new HashMap<>();
    private CordovaInterface cordovaInterface;
    private CordovaWebView cordovaWebView;

    public enum Action {
        selectFolder((short) 0x5162),
        selectFile((short) 0x5163),
        saveFile((short) 0x5164);

        private static final Map<Short, Action> BY_VALUE = new HashMap<>();

        static {
            for (Action a : values()) {
                BY_VALUE.put(a.value, a);
            }
        }

        public final short value;

        Action(short value) {
            this.value = value;
        }

        public static @Nullable Action valueOf(short value) {
            return BY_VALUE.get(value);
        }
    }

    @Override
    public void initialize(@NonNull CordovaInterface cordovaInterface, @NonNull CordovaWebView cordovaWebView) {
        this.cordovaInterface = cordovaInterface;
        this.cordovaWebView = cordovaWebView;
    }

    @Override
    public boolean execute(@NonNull String action, @NonNull CordovaArgs args, @NonNull CallbackContext callbackContext) {
        try {
            if (action.equals("selectFolder")) {
                selectFolder(args, callbackContext);

                return true;
            }

            if (action.equals("selectFile")) {
                selectFile(args, callbackContext);

                return true;
            }

            if (action.equals("openFolder")) {
                openFolder(args, callbackContext);

                return true;
            }

            if (action.equals("openFile")) {
                openFile(args, callbackContext);

                return true;
            }

            if (action.equals("readFile")) {
                readFile(args, callbackContext);

                return true;
            }

            if (action.equals("saveFile")) {
                saveFile(args, callbackContext);

                return true;
            }

            if (action.equals("writeFile")) {
                writeFile(args, callbackContext);

                return true;
            }

            if (action.equals("writeMedia")) {
                writeMedia(args, callbackContext);

                return true;
            }

            if (action.equals("overwriteFile")) {
                overwriteFile(args, callbackContext);

                return true;
            }

            if (action.equals("deleteFile")) {
                deleteFile(args, callbackContext);

                return true;
            }

            if (action.equals("getInfo")) {
                getInfo(args, callbackContext);

                return true;
            }

            if (action.equals("getUri")) {
                getUri(args, callbackContext);

                return true;
            }

            return false;
        } catch (Throwable t) {
            onError(t, callbackContext);

            return true;
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, final Intent intent) {
        short actionCode = (short) ((requestCode >> 16) & 0xFFFF);
        short callbackIndex = (short) (requestCode & 0xFFFF);

        final CallbackContext callbackContext = callbackContexts.get(callbackIndex);
        if (callbackContext == null) {
            onError("callbackContext==null in onActivityResult");
            return;
        }

        final Action action = Action.valueOf(actionCode);
        if (action == null) {
            onError("Invalid request code: " + actionCode);
            return;
        }

        callbackContexts.remove(callbackIndex);

        cordovaInterface.getThreadPool().execute(new Runnable() {
            @Override
            public void run() {
                try {
                    if (action == Action.saveFile) {
                        Uri uri = intent.getData();
                        if (resultCode != Activity.RESULT_OK || uri == null) {
                            onError("Cancelled", callbackContext);
                            return;
                        }

                        String data = saveFileData.remove(callbackContext.getCallbackId());
                        if (data == null) {
                            onError("No saveFileData in onActivityResult", callbackContext);
                            return;
                        }

                        writeFile(uri, data, callbackContext);

                        return;
                    }

                    Uri uri = intent.getData();
                    if (resultCode != Activity.RESULT_OK || uri == null) {
                        onError("Cancelled", callbackContext);
                        return;
                    }

                    ContentResolver resolver = cordovaInterface.getContext().getContentResolver();

                    int permissions = intent.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                    resolver.takePersistableUriPermission(uri, permissions);

                    fileInfo(uri, callbackContext);
                } catch (Throwable t) {
                    onError(t, callbackContext);
                }
            }
        });
    }

    @Override
    public void onReceiveValue(String value) {
    }

    private void selectFolder(@NonNull CordovaArgs args, @NonNull CallbackContext callbackContext) {
        JSONObject params = args.optJSONObject(0);
        String folder = params.optString("folder").trim();
        String title = params.optString("title", "Select Folder").trim();
        boolean write = params.optBoolean("writable", true);

        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        if (!folder.equals("")) {
            intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, folder);
        }

        intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        if (write) {
            intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        }

        startActivity(Intent.createChooser(intent, title), Action.selectFolder, callbackContext);
    }

    private void selectFile(@NonNull CordovaArgs args, @NonNull CallbackContext callbackContext) throws JSONException {
        JSONObject params = args.optJSONObject(0);
        String folder = params.optString("folder").trim();
        String title = params.optString("title", "Select File").trim();
        boolean write = params.optBoolean("write", true);

        JSONArray mimeTypesArray = params.getJSONArray("mimeTypes");
        String[] supportedMimeTypes;
        if (mimeTypesArray.length() == 0) {
            supportedMimeTypes = new String[]{"*/*"};
        } else {
            supportedMimeTypes = new String[mimeTypesArray.length()];
            for (int i = 0; i < mimeTypesArray.length(); i++) {
                supportedMimeTypes[i] = mimeTypesArray.getString(i);
            }
        }

        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            intent.setType(supportedMimeTypes.length == 1 ? supportedMimeTypes[0] : "*/*");
            if (supportedMimeTypes.length > 0) {
                intent.putExtra(Intent.EXTRA_MIME_TYPES, supportedMimeTypes);
            }
        } else {
            intent.setType(String.join("|", supportedMimeTypes));
        }

        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        if (write) {
            intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        }

        if (!folder.equals("")) {
            intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, folder);
        }

        startActivity(Intent.createChooser(intent, title), Action.selectFile, callbackContext);
    }

    public void openFolder(@NonNull CordovaArgs args, @NonNull CallbackContext callbackContext) throws JSONException {
        JSONObject params = args.getJSONObject(0);
        Uri uri = Uri.parse(params.getString("uri"));
        String title = params.optString("title", "Open Folder");

        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, DocumentsContract.Document.MIME_TYPE_DIR);

        cordovaInterface.getContext().startActivity(Intent.createChooser(intent, title));

        sendResult(callbackContext);
    }

    private void openFile(@NonNull CordovaArgs args, @NonNull CallbackContext callbackContext) throws JSONException {
        JSONObject params = args.getJSONObject(0);
        Uri uri = Uri.parse(params.getString("uri"));
        String title = params.optString("title", "Open File");

        String mimeType = cordovaInterface.getContext().getContentResolver().getType(uri);
        if (mimeType == null) {
            mimeType = "*/*";
        }

        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, mimeType);

        cordovaInterface.getContext().startActivity(Intent.createChooser(intent, title));

        sendResult(callbackContext);
    }

    private void readFile(@NonNull final CordovaArgs args, @NonNull final CallbackContext callbackContext) {
        cordovaInterface.getThreadPool().execute(new Runnable() {
            @Override
            public void run() {
                try {
                    JSONObject params = args.getJSONObject(0);
                    Uri uri = Uri.parse(params.getString("uri"));

                    DocumentFile file = DocumentFile.fromSingleUri(cordovaInterface.getContext(), uri);

                    if (file == null || file.isDirectory() || !file.canRead()) {
                        onError("could not open file", callbackContext);
                        return;
                    }

                    int size = (int) file.length();
                    String type = file.getType();

                    try (
                            InputStream inputStream = cordovaInterface.getContext().getContentResolver().openInputStream(file.getUri());
                            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream(size);
                            Base64OutputStream outputStream = new Base64OutputStream(byteArrayOutputStream, Base64.DEFAULT)
                    ) {
                        if (inputStream == null) {
                            onError("could not open file", callbackContext);
                            return;
                        }

                        FileUtils.copy(inputStream, outputStream);

                        JSONObject result = new JSONObject();
                        result.put("data", byteArrayOutputStream.toString());
                        result.put("type", type);

                        sendResult(result, callbackContext);
                    }
                } catch (Throwable t) {
                    onError(t, callbackContext);
                }
            }
        });
    }

    private void saveFile(@NonNull CordovaArgs args, @NonNull CallbackContext callbackContext) throws JSONException {
        JSONObject params = args.getJSONObject(0);
        String data = params.getString("data").trim();
        String folder = params.optString("folder").trim();
        String filename = params.optString("filename").trim();
        String mimeType = params.optString("mimeType").trim();

        if (mimeType.equals("")) {
            if (filename.equals("")) {
                mimeType = "*/*";
            } else {
                mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(filename.substring(filename.lastIndexOf('.') + 1));

                if (mimeType == null) {
                    mimeType = "*/*";
                }
            }
        }

        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(mimeType);

        if (!folder.equals("")) {
            intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, folder);
        }

        if (!filename.equals("")) {
            intent.putExtra(Intent.EXTRA_TITLE, filename);
        }

        String callbackId = callbackContext.getCallbackId();
        try {
            saveFileData.put(callbackId, data);
            startActivity(intent, Action.saveFile, callbackContext);
        } catch (Throwable t) {
            saveFileData.remove(callbackId);

            throw t;
        }
    }

    private void writeFile(@NonNull final CordovaArgs args, @NonNull final CallbackContext callbackContext) throws JSONException {
        final JSONObject params = args.getJSONObject(0);
        final String uriString = params.optString("uri").trim();

        if (uriString.equals("")) {
            writeMedia(args, callbackContext);

            return;
        }

        cordovaInterface.getThreadPool().execute(new Runnable() {
            @Override
            public void run() {
                try {
                    String data = params.getString("data").trim();
                    Uri uri = Uri.parse(uriString);

                    DocumentFile file;
                    try {
                        file = DocumentFile.fromTreeUri(cordovaInterface.getContext(), uri);
                    } catch (IllegalArgumentException e) {
                        file = DocumentFile.fromSingleUri(cordovaInterface.getContext(), uri);
                    }

                    if (file == null) {
                        onError("Could not open: " + uriString, callbackContext);
                        return;
                    }

                    if (file.isFile()) {
                        writeFile(file, data, callbackContext);
                        return;
                    }

                    String path = params.getString("path").trim();
                    String mimeType = params.optString("mimeType").trim();

                    if (mimeType.equals("")) {
                        mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(path.substring(path.lastIndexOf('.') + 1));

                        if (mimeType == null) {
                            mimeType = "*/*";
                        }
                    }

                    if (path.startsWith("/")) {
                        path = path.substring(1);
                    }

                    writeFile(file, path, data, mimeType, callbackContext);
                } catch (Throwable t) {
                    onError(t, callbackContext);
                }
            }
        });
    }

    private void writeMedia(@NonNull final CordovaArgs args, @NonNull final CallbackContext callbackContext) {
        cordovaInterface.getThreadPool().execute(new Runnable() {
            @Override
            public void run() {
                try {
                    JSONObject params = args.getJSONObject(0);
                    String data = params.getString("data").trim();
                    String path = params.getString("path").trim();
                    String mimeType = params.optString("mimeType").trim();

                    if (mimeType.equals("")) {
                        mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(path.substring(path.lastIndexOf('.') + 1));

                        if (mimeType == null) {
                            mimeType = "*/*";
                        }
                    }

                    if (path.startsWith("/")) {
                        path = path.substring(1);
                    }

                    writeMedia(path, data, mimeType, callbackContext);
                } catch (Throwable t) {
                    onError(t, callbackContext);
                }
            }
        });
    }

    private void overwriteFile(@NonNull final CordovaArgs args, @NonNull final CallbackContext callbackContext) {
        cordovaInterface.getThreadPool().execute(new Runnable() {
            @Override
            public void run() {
                try {
                    JSONObject params = args.getJSONObject(0);
                    String uriString = params.getString("uri").trim();
                    String data = params.getString("data").trim();

                    writeFile(Uri.parse(uriString), data, callbackContext);
                } catch (Throwable t) {
                    onError(t, callbackContext);
                }
            }
        });
    }

    private void deleteFile(@NonNull CordovaArgs args, @NonNull CallbackContext callbackContext) {
        cordovaInterface.getThreadPool().execute(new Runnable() {
            @Override
            public void run() {
                try {
                    JSONObject params = args.getJSONObject(0);
                    String uriString = params.getString("uri").trim();

                    int deleted = cordovaInterface.getContext().getContentResolver().delete(Uri.parse(uriString), null);

                    sendResult(deleted, callbackContext);
                } catch (Throwable t) {
                    onError(t, callbackContext);
                }
            }
        });
    }

    private void getInfo(@NonNull CordovaArgs args, @NonNull CallbackContext callbackContext) {
        cordovaInterface.getThreadPool().execute(new Runnable() {
            @Override
            public void run() {
                try {
                    JSONObject params = args.getJSONObject(0);
                    String uriString = params.getString("uri").trim();
                    String path = params.optString("path").trim();

                    Uri uri = Uri.parse(uriString);
                    if (!path.equals("")) {
                        uri = resolveContentUri(uri, path);

                        if (uri == null) {
                            onError("could not find file: " + uriString + " : " + path, callbackContext);
                            return;
                        }
                    }

                    fileInfo(uri, callbackContext);
                } catch (Throwable t) {
                    onError(t, callbackContext);
                }
            }
        });
    }

    private void getUri(@NonNull CordovaArgs args, @NonNull CallbackContext callbackContext) {
        cordovaInterface.getThreadPool().execute(new Runnable() {
            @Override
            public void run() {
                try {
                    JSONObject params = args.getJSONObject(0);
                    String uriString = params.getString("uri").trim();
                    String path = params.getString("path").trim();

                    Uri uri = resolveContentUri(Uri.parse(uriString), path);

                    JSONObject result = new JSONObject();
                    result.put("uri", uri == null ? JSONObject.NULL : uri);

                    sendResult(result, callbackContext);
                } catch (Throwable t) {
                    onError(t, callbackContext);
                }
            }
        });
    }

    private void startActivity(@NonNull Intent intent, @NonNull Action action, @NonNull CallbackContext callbackContext) {
        short index = ++lastCallbackIndex;
        callbackContexts.put(index, callbackContext);

        cordovaInterface.startActivityForResult(
                this,
                intent,
                (action.value << 16) | index
        );
    }

    private void fileInfo(@NonNull Uri uri, @NonNull CallbackContext callbackContext) throws JSONException {
        DocumentFile file = DocumentFile.fromSingleUri(cordovaInterface.getContext(), uri);
        if (file == null) {
            onError("could not open file: " + uri, callbackContext);
            return;
        }

        JSONObject result = new JSONObject();
        result.put("uri", uri.toString());
        result.put("name", file.getName());
        result.put("lastModified", file.lastModified());
        result.put("writable", file.canWrite());

        if (file.isFile()) {
            result.put("type", file.getType());
            result.put("size", file.length());
        }

        sendResult(result, callbackContext);
    }

    private @Nullable Uri resolveContentUri(@NonNull Uri uri, @NonNull String path) {
        DocumentFile parent = DocumentFile.fromTreeUri(
                cordovaInterface.getContext(),
                uri
        );
        if (parent == null || !parent.isDirectory()) {
            return null;
        }

        String[] subFolders;
        String filename;

        int index = path.lastIndexOf("/");
        if (index > 0) {
            subFolders = path.substring(0, index).split("/");
            filename = path.substring(index + 1);
        } else {
            subFolders = new String[]{};
            filename = path;
        }

        StringBuilder currentPath = new StringBuilder();
        for (String subFolder : subFolders) {
            currentPath.append(subFolder);

            DocumentFile currentFile = parent.findFile(subFolder);
            if (currentFile == null || !currentFile.isDirectory()) {
                return null;
            }

            parent = currentFile;
            currentPath.append("/");
        }

        DocumentFile target = parent.findFile(filename);
        if (target == null) {
            return null;
        }

        return target.getUri();
    }

    private void writeFile(@NonNull DocumentFile parent, @NonNull String path, @NonNull String data, @NonNull String mimeType, @NonNull CallbackContext callbackContext) throws JSONException, IOException {
        String[] subFolders;
        String filename;

        int index = path.lastIndexOf("/");
        if (index > 0) {
            subFolders = path.substring(0, index).split("/");
            filename = path.substring(index + 1);
        } else {
            subFolders = new String[]{};
            filename = path;
        }

        StringBuilder currentPath = new StringBuilder();
        for (String subFolder : subFolders) {
            currentPath.append(subFolder);

            DocumentFile currentFile = parent.findFile(subFolder);
            if (currentFile == null) {
                currentFile = parent.createDirectory(subFolder);

                if (currentFile == null) {
                    onError("Could not create sub-folder: " + parent.getUri() + "/" + currentPath, callbackContext);
                    return;
                }
            } else if (!currentFile.isDirectory()) {
                onError("Target is not a directory: " + parent.getUri() + "/" + currentPath, callbackContext);
                return;
            }

            parent = currentFile;
            currentPath.append("/");
        }

        DocumentFile file = parent.findFile(filename);
        if (file == null) {
            file = parent.createFile(mimeType, filename);

            if (file == null) {
                onError("Could not create file: " + parent.getUri() + "/" + currentPath + "/" + filename, callbackContext);
                return;
            }
        } else if (!file.isFile()) {
            onError("Target is not a file file: " + parent.getUri() + "/" + currentPath + "/" + filename, callbackContext);
            return;
        }

        writeFile(file, data, callbackContext);
    }

    private void writeMedia(@NonNull String fullPath, @NonNull String data, @NonNull String mimeType, @NonNull CallbackContext callbackContext) throws IOException, JSONException {
        ContentResolver contentResolver = cordovaInterface.getContext().getContentResolver();

        ContentValues contentValues = new ContentValues();
        contentValues.put(MediaStore.MediaColumns.MIME_TYPE, mimeType);

        int index = fullPath.lastIndexOf("/");
        Uri volume;
        if (index > 0) {
            contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, fullPath.substring(0, index));
            contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, fullPath.substring(index + 1));
            contentValues.put(MediaStore.MediaColumns.IS_PENDING, 1);

            if (fullPath.startsWith("DCIM/") || fullPath.startsWith("Pictures/")) {
                boolean isImage = mimeType.startsWith("image/");

                if (!isImage && !mimeType.startsWith("video/")) {
                    onError("can only store image or video files in DCIM/ or Pictures/ folder", callbackContext);
                    return;
                }

                volume = isImage
                        ? MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
                        : MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL);
            } else if (fullPath.startsWith("Movies")) {
                if (!mimeType.startsWith("video/")) {
                    onError("can only store video files in Movies/ folder", callbackContext);
                    return;
                }

                volume = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL);
            } else if (
                    fullPath.startsWith("Alarms/") ||
                            fullPath.startsWith("Audiobooks/") ||
                            fullPath.startsWith("Music/") ||
                            fullPath.startsWith("Notifications/") ||
                            fullPath.startsWith("Podcasts/") ||
                            fullPath.startsWith("Recordings/") ||
                            fullPath.startsWith("Ringtones/")
            ) {
                if (!mimeType.startsWith("audio/")) {
                    onError("can only store audio files in Alarms/, Audiobooks/, Music/, Notifications/, Podcasts/, Recordings/ or Ringtones/ folder", callbackContext);
                    return;
                }

                volume = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL);
            } else if (fullPath.startsWith("Downloads/")) {
                volume = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL);
            } else {
                volume = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL);
            }
        } else {
            contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, fullPath);
            volume = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL);
        }

        Uri uri = contentResolver.insert(volume, contentValues);
        if (uri == null) {
            onError("Could not create file: " + fullPath, callbackContext);
            return;
        }

        try (
                OutputStream outputStream = contentResolver.openOutputStream(uri, "wt");
                InputStream inputStream = new Base64InputStream(
                        new ByteArrayInputStream(data.getBytes()),
                        Base64.DEFAULT
                )
        ) {
            if (outputStream == null) {
                onError("Could not open file for writing: " + uri, callbackContext);
                return;
            }

            FileUtils.copy(inputStream, outputStream);
        }

        contentValues.clear();
        contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0);
        contentResolver.update(uri, contentValues, null, null);

        fileInfo(uri, callbackContext);
    }

    private void writeFile(@NonNull DocumentFile file, @NonNull String data, @NonNull CallbackContext callbackContext) throws JSONException, IOException {
        writeFile(file.getUri(), data, callbackContext);
    }

    private void writeFile(@NonNull final Uri uri, @NonNull final String data, @NonNull final CallbackContext callbackContext) throws IOException, JSONException {
        try (
                OutputStream outputStream = cordovaInterface.getContext().getContentResolver().openOutputStream(uri, "wt");
                InputStream inputStream = new Base64InputStream(
                        new ByteArrayInputStream(data.getBytes()),
                        Base64.DEFAULT
                )
        ) {
            if (outputStream == null) {
                onError("Could not open file for writing: " + uri, callbackContext);
                return;
            }

            FileUtils.copy(inputStream, outputStream);
        }

        fileInfo(uri, callbackContext);
    }

    private void onError(@NonNull Throwable throwable, @Nullable CallbackContext callbackContext) {
        Log.w(getClass().getName(), throwable.getLocalizedMessage(), throwable);

        String message;
        try {
            String stackTrace;
            try (
                    StringWriter stringWriter = new StringWriter();
                    PrintWriter printWriter = new PrintWriter(stringWriter)
            ) {
                throwable.printStackTrace(printWriter);
                stackTrace = stringWriter.toString();
            }

            message = throwable.getLocalizedMessage() + "\n" + stackTrace;
        } catch (Throwable e) {
            Log.d(getClass().getName(), e.getLocalizedMessage(), e);

            message = throwable.getLocalizedMessage();
        }

        if (message == null) {
            message = "Unknown error: " + throwable.getClass().getName();
        }

        sendError(message, callbackContext);
    }

    private void onError(@NonNull String message) {
        onError(message, null);
    }

    private void onError(@NonNull String message, @Nullable CallbackContext callbackContext) {
        Log.d(getClass().getName(), message);

        sendError(message, callbackContext);
    }

    private void sendResult(@NonNull final CallbackContext callbackContext) {
        cordovaInterface.getActivity().runOnUiThread(new Runnable() {
            public void run() {
                callbackContext.success();
            }
        });
    }

    private void sendResult(final int result, @NonNull final CallbackContext callbackContext) {
        cordovaInterface.getActivity().runOnUiThread(new Runnable() {
            public void run() {
                callbackContext.success(result);
            }
        });
    }

    private void sendResult(@NonNull final JSONObject result, @NonNull final CallbackContext callbackContext) {
        cordovaInterface.getActivity().runOnUiThread(new Runnable() {
            public void run() {
                callbackContext.success(result);
            }
        });
    }

    private void sendError(@NonNull final String message, @Nullable final CallbackContext callbackContext) {
        cordovaInterface.getActivity().runOnUiThread(new Runnable() {
            public void run() {
                if (callbackContext != null) {
                    callbackContext.error(message);
                }

                String jsMessage = message.replace("'", "\\'")
                        .replace("\n", "\\n")
                        .replace("\t", "\\t");

                cordovaWebView.getEngine().evaluateJavascript(
                        "console.error('" + jsMessage + "');",
                        SafMediastore.this
                );
            }
        });
    }
}
