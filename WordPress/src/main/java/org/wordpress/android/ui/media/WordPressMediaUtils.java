package org.wordpress.android.ui.media;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v4.content.FileProvider;
import android.view.ViewConfiguration;

import com.android.volley.toolbox.NetworkImageView;

import org.wordpress.android.R;
import org.wordpress.android.fluxc.model.MediaModel;
import org.wordpress.android.fluxc.model.SiteModel;
import org.wordpress.android.ui.RequestCodes;
import org.wordpress.android.util.AppLog;
import org.wordpress.android.util.AppLog.T;
import org.wordpress.android.util.DeviceUtils;
import org.wordpress.android.util.MediaUtils;
import org.wordpress.android.widgets.WPNetworkImageView;
import org.wordpress.passcodelock.AppLockManager;

import java.io.File;
import java.io.IOException;

public class WordPressMediaUtils {
    public interface LaunchCameraCallback {
        void onMediaCapturePathReady(String mediaCapturePath);
    }

    private static void showSDCardRequiredDialog(Context context) {
        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(context);
        dialogBuilder.setTitle(context.getResources().getText(R.string.sdcard_title));
        dialogBuilder.setMessage(context.getResources().getText(R.string.sdcard_message));
        dialogBuilder.setPositiveButton(context.getString(android.R.string.ok), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                dialog.dismiss();
            }
        });
        dialogBuilder.setCancelable(true);
        dialogBuilder.create().show();
    }

    public static void launchVideoLibrary(Activity activity) {
        AppLockManager.getInstance().setExtendedTimeout();
        activity.startActivityForResult(prepareVideoLibraryIntent(activity),
                RequestCodes.VIDEO_LIBRARY);
    }

    private static Intent prepareVideoLibraryIntent(Context context) {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("video/*");
        return Intent.createChooser(intent, context.getString(R.string.pick_video));
    }

    public static void launchVideoCamera(Activity activity) {
        AppLockManager.getInstance().setExtendedTimeout();
        activity.startActivityForResult(prepareVideoCameraIntent(), RequestCodes.TAKE_VIDEO);
    }

    private static Intent prepareVideoCameraIntent() {
        return new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
    }

    public static void launchPictureLibrary(Activity activity) {
        AppLockManager.getInstance().setExtendedTimeout();
        activity.startActivityForResult(preparePictureLibraryIntent(activity.getString(R.string.pick_photo)),
                RequestCodes.PICTURE_LIBRARY);
    }

    private static Intent preparePictureLibraryIntent(String title) {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        return Intent.createChooser(intent, title);
    }

    private static Intent prepareGalleryIntent(String title) {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("image/*");
        return Intent.createChooser(intent, title);
    }

    public static void launchCamera(Activity activity, String applicationId, LaunchCameraCallback callback) {
        Intent intent = prepareLaunchCamera(activity, applicationId, callback);
        if (intent != null) {
            AppLockManager.getInstance().setExtendedTimeout();
            activity.startActivityForResult(intent, RequestCodes.TAKE_PHOTO);
        }
    }

    private static Intent prepareLaunchCamera(Context context, String applicationId, LaunchCameraCallback callback) {
        String state = android.os.Environment.getExternalStorageState();
        if (!state.equals(android.os.Environment.MEDIA_MOUNTED)) {
            showSDCardRequiredDialog(context);
            return null;
        } else {
            try {
                return getLaunchCameraIntent(context, applicationId, callback);
            } catch (IOException e) {
                // No need to write log here
                return null;
            }
        }
    }

    private static Intent getLaunchCameraIntent(Context context, String applicationId, LaunchCameraCallback callback)
            throws IOException {
        File externalStoragePublicDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);
        String mediaCapturePath = externalStoragePublicDirectory + File.separator + "Camera" + File.separator + "wp-" + System
                .currentTimeMillis() + ".jpg";

        // make sure the directory we plan to store the recording in exists
        File directory = new File(mediaCapturePath).getParentFile();
        if (directory == null || (!directory.exists() && !directory.mkdirs())) {
            try {
                throw new IOException("Path to file could not be created: " + mediaCapturePath);
            } catch (IOException e) {
                AppLog.e(T.MEDIA, e);
                throw e;
            }
        }

        Uri fileUri;
        try {
            fileUri = FileProvider.getUriForFile(context, applicationId + ".provider", new File(mediaCapturePath));
        } catch (IllegalArgumentException e) {
            AppLog.e(T.MEDIA, "Cannot access the file planned to store the new media", e);
            throw new IOException("Cannot access the file planned to store the new media");
        } catch (NullPointerException e) {
            AppLog.e(T.MEDIA, "Cannot access the file planned to store the new media - " +
                    "FileProvider.getUriForFile cannot find a valid provider for the authority: " + applicationId + ".provider", e);
            throw new IOException("Cannot access the file planned to store the new media");
        }

        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        intent.putExtra(android.provider.MediaStore.EXTRA_OUTPUT, fileUri);

        if (callback != null) {
            callback.onMediaCapturePathReady(mediaCapturePath);
        }
        return intent;
    }

    private static Intent makePickOrCaptureIntent(Context context, String applicationId, LaunchCameraCallback callback) {
        Intent pickPhotoIntent = prepareGalleryIntent(context.getString(R.string.capture_or_pick_photo));

        if (DeviceUtils.getInstance().hasCamera(context)) {
            try {
                Intent cameraIntent = getLaunchCameraIntent(context, applicationId, callback);
                pickPhotoIntent.putExtra(
                        Intent.EXTRA_INITIAL_INTENTS,
                        new Intent[]{ cameraIntent });
            } catch (IOException e) {
                // No need to write log here
            }
        }

        return pickPhotoIntent;
    }

    static int getPlaceholder(String url) {
        if (MediaUtils.isValidImage(url)) {
            return R.drawable.ic_gridicons_image;
        } else if (MediaUtils.isDocument(url)) {
            return R.drawable.ic_gridicons_page;
        } else if (MediaUtils.isPowerpoint(url)) {
            return R.drawable.media_powerpoint;
        } else if (MediaUtils.isSpreadsheet(url)) {
            return R.drawable.media_spreadsheet;
        } else if (MediaUtils.isVideo(url)) {
            return R.drawable.ic_gridicons_video_camera;
        } else if (MediaUtils.isAudio(url)) {
            return R.drawable.ic_gridicons_audio;
        } else {
            return 0;
        }
    }

    static boolean canDeleteMedia(MediaModel mediaModel) {
        String state = mediaModel.getUploadState();
        return state == null || (!state.equalsIgnoreCase("uploading") && !state.equalsIgnoreCase("deleted"));
    }

    /**
     * Loads the given network image URL into the {@link NetworkImageView}.
     */
    public static void loadNetworkImage(String imageUrl, WPNetworkImageView imageView) {
        if (imageUrl != null) {
            Uri uri = Uri.parse(imageUrl);
            String filepath = uri.getLastPathSegment();

            // re-use the default background drawable as error image for now.
            // See: https://github.com/wordpress-mobile/WordPress-Android/pull/6295#issuecomment-315129759
            imageView.setErrorImageResId(R.drawable.media_item_background);

            // default image while downloading
            imageView.setDefaultImageResId(R.drawable.media_item_background);

            if (MediaUtils.isValidImage(filepath)) {
                imageView.setTag(imageUrl);
                imageView.setImageUrl(imageUrl, WPNetworkImageView.ImageType.PHOTO);
            } else {
                imageView.setImageResource(R.drawable.media_item_background);
            }
        } else {
            imageView.setImageResource(0);
        }
    }

    /**
     * Returns a poster (thumbnail) URL given a VideoPress video URL
     * @param videoUrl the remote URL to the VideoPress video
     */
    public static String getVideoPressVideoPosterFromURL(String videoUrl) {
        String posterUrl = "";

        if (videoUrl != null) {
            int fileTypeLocation = videoUrl.lastIndexOf(".");
            if (fileTypeLocation > 0) {
                posterUrl = videoUrl.substring(0, fileTypeLocation) + "_std.original.jpg";
            }
        }

        return posterUrl;
    }

    /*
     * passes a newly-created media file to the media scanner service so it's available to
     * the media content provider - use this after capturing or downloading media to ensure
     * that it appears in the stock Gallery app
     */
    public static void scanMediaFile(@NonNull Context context, @NonNull String localMediaPath) {
        MediaScannerConnection.scanFile(context,
                new String[]{localMediaPath}, null,
                new MediaScannerConnection.OnScanCompletedListener() {
                    public void onScanCompleted(String path, Uri uri) {
                        AppLog.d(T.MEDIA, "Media scanner finished scanning " + path);
                    }
                });
    }

    /*
     * returns true if the current user has permission to upload new media to the passed site
     */
    public static boolean currentUserCanUploadMedia(@NonNull SiteModel site) {
        if (site.isUsingWpComRestApi()) {
            return site.getHasCapabilityUploadFiles();
        } else {
            // self-hosted sites don't have capabilities so always return true
            return true;
        }
    }

    public static boolean currentUserCanDeleteMedia(@NonNull SiteModel site) {
        return currentUserCanUploadMedia(site);
    }
  
    /*
     * returns the minimum distance for a fling which determines whether to disable loading
     * thumbnails in the media grid or photo picker - used to conserve memory usage during
     * a reasonably-sized fling
     */
    public static int getFlingDistanceToDisableThumbLoading(@NonNull Context context) {
        return ViewConfiguration.get(context).getScaledMaximumFlingVelocity() / 2;
    }
}
