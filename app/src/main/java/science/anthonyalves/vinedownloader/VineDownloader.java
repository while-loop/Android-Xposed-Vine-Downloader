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
import java.util.Map;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;


public class VineDownloader implements IXposedHookLoadPackage, IXposedHookZygoteInit {

    private static int VINE_ACTIVITY_RESULT = 999;
    private static int VINE_DOWNLOADER_RESULT = 99;
    private static int VINE_POST_ID_ERROR = -91;

    String PACKAGE_NAME = VineDownloader.class.getPackage().getName();

    XSharedPreferences xSharedPreferences;

    private Context mContext;
    private long mPostId;

    @Override
    public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable {

        if (!lpparam.packageName.equals(Constants.PACKAGE_NAME)) {
            return;
        }

        final Constructor<?> optionConstructor = XposedHelpers.findConstructorExact(XposedHelpers.findClass("co.vine.android.PostOptionsDialogActivity$Option", lpparam.classLoader), int.class, String.class);
        Class<?> postOptionsDialogActivityClass = XposedHelpers.findClass("co.vine.android.PostOptionsDialogActivity", lpparam.classLoader);


        log("PACKAGE_NAME: " + PACKAGE_NAME);

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
                        FeedAdapter has (FeedAdapterItems) mItems
                            FeedAdapterPosts has (ArrayList<TimelineItem>) mItems
                 */
                Object mFeedAdapter = XposedHelpers.getObjectField(param.thisObject, "mFeedAdapter");
                ArrayList<?> vinePostArray = null;

                final String appVersionString = mContext.getPackageManager().getPackageInfo(Constants.PACKAGE_NAME, 0).versionName;

                if (versionCompare("3.3.15", appVersionString) > 0) { //versions under 3.3.15 aka, old feedadapter
                    Object mFeedAdapterPosts = XposedHelpers.getObjectField(mFeedAdapter, "mPosts");
                    vinePostArray = (ArrayList) XposedHelpers.getObjectField(mFeedAdapterPosts, "mPosts");
                } else {
                    // version 1.0.1 vine 3.3.15 - FeedAdapterPosts is now the FeedAdapterItems object
                    // mPosts -> mItems
                    Object mFeedAdapterPosts = XposedHelpers.getObjectField(mFeedAdapter, "mItems");
                    vinePostArray = (ArrayList) XposedHelpers.getObjectField(mFeedAdapterPosts, "mItems");
                }

                if (vinePostArray == null) {
                    param.setResult(false);
                }

                Object vinePost = null;

                assert vinePostArray != null;
                for (Object obj : vinePostArray) {
                    if (XposedHelpers.getLongField(obj, "postId") == postId) {
                        vinePost = obj;
                        break;
                    }
                }

                if (vinePost == null) {
                    param.setResult(false);
                }

                assert vinePost != null;
                String videoUrl = (String) XposedHelpers.getObjectField(vinePost, "videoUrl");
                //log("videoUrl: " + videoUrl);
                if (videoUrl != null && videoUrl.isEmpty()) {
                    videoUrl = (String) XposedHelpers.getObjectField(vinePost, "videoLowURL");

                    if (videoUrl.isEmpty()) {
                        Toast.makeText(VineDownloader.this.mContext, "Failed to get video URL", Toast.LENGTH_SHORT).show();
                        return;
                    }
                }

                Toast.makeText(VineDownloader.this.mContext, "Downloading video", Toast.LENGTH_SHORT).show();
                String description = (String) XposedHelpers.getObjectField(vinePost, "description");

                xSharedPreferences.reload();
                xSharedPreferences.makeWorldReadable();

                String def = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getAbsolutePath() + "/Vine";
                String dir = xSharedPreferences.getString(Constants.DOWNLOAD_LOCATION_KEY, def);
                if (dir.charAt(dir.length()-1) != '/') {
                    dir += '/';
                }
                File directory = new File(dir);
                if (!directory.exists()) {
                    directory.mkdirs();
                }

                Request request = new Request(Uri.parse(videoUrl));
                request.setDescription(description);
                request.setTitle(description);
                request.allowScanningByMediaScanner();
                request.setNotificationVisibility(1);
                String fileName = postId + ".mp4";
                dir = "file:" + dir + fileName;
                request.setDestinationUri(Uri.parse(dir));
                //log("uri" + Uri.parse(dir).toString());
                ((DownloadManager) VineDownloader.this.mContext.getSystemService(Context.DOWNLOAD_SERVICE)).enqueue(request);
                param.setResult(false);
            }
        });
    }

    private void toast(String s) {
        Toast.makeText(VineDownloader.this.mContext, s, Toast.LENGTH_SHORT).show();
    }

    private void log(String s) {
        XposedBridge.log(s);
    }

    /**
     * http://stackoverflow.com/a/6702029
     * Compares two version strings.
     * <p>
     * Use this instead of String.compareTo() for a non-lexicographical
     * comparison that works for version strings. e.g. "1.10".compareTo("1.6").
     *
     * @param str1 a string of ordinal numbers separated by decimal points.
     * @param str2 a string of ordinal numbers separated by decimal points.
     * @return The result is a negative integer if str1 is _numerically_ less than str2.
     * The result is a positive integer if str1 is _numerically_ greater than str2.
     * The result is zero if the strings are _numerically_ equal.
     * @note It does not work if "1.10" is supposed to be equal to "1.10.0".
     */
    public Integer versionCompare(String str1, String str2) {
        String[] vals1 = str1.split("\\.");
        String[] vals2 = str2.split("\\.");
        int i = 0;
        // set index to first non-equal ordinal or length of shortest version string
        while (i < vals1.length && i < vals2.length && vals1[i].equals(vals2[i])) {
            i++;
        }
        // compare first non-equal ordinal number
        if (i < vals1.length && i < vals2.length) {
            int diff = Integer.valueOf(vals1[i]).compareTo(Integer.valueOf(vals2[i]));
            return Integer.signum(diff);
        }
        // the strings are equal or one string is a substring of the other
        // e.g. "1.2.3" = "1.2.3" or "1.2.3" < "1.2.3.4"
        else {
            return Integer.signum(vals1.length - vals2.length);
        }
    }

    @Override
    public void initZygote(StartupParam startupParam) throws Throwable {
        xSharedPreferences = new XSharedPreferences(PACKAGE_NAME);
        xSharedPreferences.makeWorldReadable();
    }
}
