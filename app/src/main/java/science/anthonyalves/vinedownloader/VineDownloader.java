package science.anthonyalves.vinedownloader;


import android.app.Activity;
import android.app.DownloadManager;
import android.app.DownloadManager.Request;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.support.v4.app.FragmentActivity;
import android.view.View;
import android.widget.ListView;
import android.widget.Toast;

import java.io.File;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Iterator;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class VineDownloader implements IXposedHookLoadPackage {

    private static int VINE_ACTIVITY_RESULT = 999;
    private static int VINE_DOWNLOADER_RESULT = 99;
    private static int VINE_POST_ID_ERROR = -91;

    private static String PACKAGE_NAME = "co.vine.android";

    private Context mContext;
    private long mPostId;

    @Override
    public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable {

        if (!lpparam.packageName.equals(PACKAGE_NAME)) {
            return;
        }

        final Constructor<?> optionConstructor = XposedHelpers.findConstructorExact(XposedHelpers.findClass("co.vine.android.PostOptionsDialogActivity$Option", lpparam.classLoader), int.class, String.class);
        Class<?> postOptionsDialogActivityClass = XposedHelpers.findClass("co.vine.android.PostOptionsDialogActivity", lpparam.classLoader);

        /**
         *  Before the invalidateOptions method starts, add the Download option to the ArrayList
         *  that will populate the ArrayAdapter
         */
        XposedBridge.hookAllMethods(postOptionsDialogActivityClass, "invalidateOptions", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {


                Activity activity = (Activity) param.thisObject;
                VineDownloader.this.mContext = activity.getApplicationContext();

                ArrayList arraylist = (ArrayList<?>) param.args[0];
                arraylist.add(0, optionConstructor.newInstance(VineDownloader.VINE_DOWNLOADER_RESULT, "Download"));
            }
        });


        /**
         *  Intercept the onListItemClick method if the value of the item is equal to the Download
         *  int value (VINE_DOWNLOADER_RESULT)
         *
         *  Finish and return the function.
         */
        XposedHelpers.findAndHookMethod(postOptionsDialogActivityClass, "onListItemClick", ListView.class, View.class, int.class, long.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {

                FragmentActivity sdf;
                View view = (View) param.args[1];
                int tag = (int) view.getTag();
                if (tag == VineDownloader.VINE_DOWNLOADER_RESULT) {
                    VineDownloader.this.mPostId = (long) XposedHelpers.getObjectField(param.thisObject, "mPostId");

                    Intent intent = new Intent();
                    intent.putExtra("post_id", VineDownloader.this.mPostId);

                    Activity activity = (Activity) param.thisObject;
                    activity.setResult(VineDownloader.VINE_ACTIVITY_RESULT, intent);
                    activity.finish();
                    param.setResult(null);
                }
            }
        });


        /**
         *  If the request code is VINE_ACTIVITY_RESULT, then this is where we download the video
         *  using the passed post_id in the intent.
         *
         */
        XposedHelpers.findAndHookMethod("co.vine.android.BaseTimelineFragment", lpparam.classLoader, "onActivityResult", int.class, int.class, Intent.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {

                // check if it is the right requestCode
                int requestCode = (int) param.args[1];
                if (requestCode != VineDownloader.VINE_ACTIVITY_RESULT) {
                    param.setResult(false);
                }

                // make sure the intent isn't empty
                Intent data = (Intent) param.args[2];
                if (data == null) {
                    param.setResult(false);
                }

                // make sure we have a post ID to download from
                String str = "post_id";
                long postId = data.getLongExtra(str, (long) VineDownloader.VINE_POST_ID_ERROR);
                if (postId == ((long) VineDownloader.VINE_POST_ID_ERROR)) {
                    param.setResult(false);
                }

                /*
                    BaseTimelineFragment has (FeedAdapter) mFeedAdapter
                        FeedAdapter has (FeedAdapterPosts) mPosts
                            FeedAdapterPosts has (ArrayList<VinePost>) mPosts
                 */
                Object mFeedAdapter = XposedHelpers.getObjectField(param.thisObject, "mFeedAdapter");
                Object mFeedAdapterPosts = XposedHelpers.getObjectField(mFeedAdapter, "mPosts");
                ArrayList<?> vinePostArray = (ArrayList) XposedHelpers.getObjectField(mFeedAdapterPosts, "mPosts");

                Object vinePost = null;

                if (vinePostArray == null) {
                    param.setResult(false);
                }

                Iterator it = vinePostArray.iterator();
                while (it.hasNext()) {
                    Object obj = it.next();
                    if (XposedHelpers.getLongField(obj, "postId") == postId) {
                        vinePost = obj;
                        break;
                    }
                }

                if (vinePost == null) {
                    param.setResult(false);
                }

                String videoUrl = (String) XposedHelpers.getObjectField(vinePost, "videoUrl");
                if (videoUrl != null && videoUrl.isEmpty()) {
                    videoUrl = (String) XposedHelpers.getObjectField(vinePost, "videoLowURL");

                    if (videoUrl.isEmpty()) {
                        Toast.makeText(VineDownloader.this.mContext, "Failed to get video URL", Toast.LENGTH_SHORT).show();
                        return;
                    }
                }

                Toast.makeText(VineDownloader.this.mContext, "Downloading video", Toast.LENGTH_SHORT).show();
                String description = (String) XposedHelpers.getObjectField(vinePost, "description");
                File directory = new File(new StringBuilder(String.valueOf(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getAbsolutePath())).append("/Vine").toString());
                if (!directory.exists()) {
                    directory.mkdirs();
                }
                Request request = new Request(Uri.parse(videoUrl));
                request.setDescription(description);
                request.setTitle("Vine Video");
                request.allowScanningByMediaScanner();
                request.setNotificationVisibility(1);
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "Vine/" + postId + ".mp4");
                ((DownloadManager) VineDownloader.this.mContext.getSystemService(Context.DOWNLOAD_SERVICE)).enqueue(request);
                param.setResult(false);
            }
        });
    }
}
