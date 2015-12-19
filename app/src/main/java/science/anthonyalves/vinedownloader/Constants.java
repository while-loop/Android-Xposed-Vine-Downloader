package science.anthonyalves.vinedownloader;

import android.os.Environment;

/**
 * Created by Anthony on 12/18/2015.
 */
public class Constants {

    public static String PACKAGE_NAME = "co.vine.android";

    public static String DOWNLOAD_LOCATION_KEY = "download_location_et";

    public static String DEFAULT_LOCATION = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getAbsolutePath() + "/Vine";


}
