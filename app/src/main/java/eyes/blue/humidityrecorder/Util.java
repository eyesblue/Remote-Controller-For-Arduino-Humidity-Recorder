package eyes.blue.humidityrecorder;

import android.os.Bundle;

import com.google.firebase.analytics.FirebaseAnalytics;

public class Util {
    public static String MENU_CLICK = "MENU_CLICK";
    public static String BUTTON_CLICK = "BUTTON_CLICK";
    public static String SPEND_TIME = "SPEND_TIME";
    public static String STATISTICS="STATISTICS";

    //Util.fireSelectEvent(mFirebaseAnalytics, getClass().getName(), Util.MENU_CLICK, "SHARE_DATA");
    public static void fireSelectEvent(FirebaseAnalytics mFirebaseAnalytics, String activity, String type, String name){
        Bundle bundle = new Bundle();
        bundle.putString(FirebaseAnalytics.Param.ITEM_CATEGORY, activity);
        bundle.putString(FirebaseAnalytics.Param.CONTENT_TYPE, type);
        bundle.putString(FirebaseAnalytics.Param.ITEM_ID, name);
        mFirebaseAnalytics.logEvent(FirebaseAnalytics.Event.SELECT_ITEM, bundle);
    }
}


