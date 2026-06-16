package com.wall.mob;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.telephony.TelephonyManager;

public class NetworkUtils {
    
    public enum ConnectionSpeed {
        SLOW, MEDIUM, FAST
    }
    
    public static ConnectionSpeed getConnectionSpeed(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo info = cm.getActiveNetworkInfo();
        
        if (info == null || !info.isConnected()) {
            return ConnectionSpeed.SLOW;
        }
        
        int type = info.getType();
        int subType = info.getSubtype();
        
        if (type == ConnectivityManager.TYPE_WIFI) {
            return ConnectionSpeed.FAST;
        } else if (type == ConnectivityManager.TYPE_MOBILE) {
            switch (subType) {
                case TelephonyManager.NETWORK_TYPE_1xRTT:
                case TelephonyManager.NETWORK_TYPE_CDMA:
                case TelephonyManager.NETWORK_TYPE_EDGE:
                case TelephonyManager.NETWORK_TYPE_GPRS:
                case TelephonyManager.NETWORK_TYPE_IDEN:
                    return ConnectionSpeed.SLOW;
                case TelephonyManager.NETWORK_TYPE_UMTS:
                case TelephonyManager.NETWORK_TYPE_EVDO_0:
                case TelephonyManager.NETWORK_TYPE_EVDO_A:
                case TelephonyManager.NETWORK_TYPE_HSDPA:
                case TelephonyManager.NETWORK_TYPE_HSUPA:
                case TelephonyManager.NETWORK_TYPE_HSPA:
                case TelephonyManager.NETWORK_TYPE_EVDO_B:
                case TelephonyManager.NETWORK_TYPE_EHRPD:
                case TelephonyManager.NETWORK_TYPE_HSPAP:
                    return ConnectionSpeed.MEDIUM;
                case TelephonyManager.NETWORK_TYPE_LTE:
                case TelephonyManager.NETWORK_TYPE_NR: // 5G
                    return ConnectionSpeed.FAST;
                default:
                    return ConnectionSpeed.MEDIUM;
            }
        }
        
        return ConnectionSpeed.MEDIUM;
    }
    
    public static boolean isSlowConnection(Context context) {
        return getConnectionSpeed(context) == ConnectionSpeed.SLOW;
    }
}
// test
