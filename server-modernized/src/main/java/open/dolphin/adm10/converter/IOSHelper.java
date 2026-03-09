package open.dolphin.adm10.converter;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.logging.Logger;

/**
 *
 * @author kazushi
 */
public class IOSHelper {
    private static final Logger LOGGER = Logger.getLogger(IOSHelper.class.getName());
    
    // IOS5 JSON DATE
    //private static final String IOS_DATE_FORMAT = "yyyy-MM-dd HH:mm:ss";
    private static final String IOS_DATE_FORMAT = "yyyy-MM-dd HH:mm:ss.SSS";
    private static final String IOS_DATE_FORMAT_OLD = "yyyy-MM-dd HH:mm:ss";
    
    public static Date toDate(String dateStr) {
        if (dateStr==null) {
            return null;
        }
        Date ret = null;
        try {
            ret = new SimpleDateFormat(IOS_DATE_FORMAT).parse(dateStr);
        } catch (Exception e) {
            try {
                ret = new SimpleDateFormat(IOS_DATE_FORMAT_OLD).parse(dateStr);
            } catch (ParseException ex) {
            }
        }
        return ret;
    }
    
    public static String toDateStr(Date d) {
        if (d==null) {
            return null;
        }
        String ret = null;
        try {
            ret = new SimpleDateFormat(IOS_DATE_FORMAT).format(d);
        } catch (Exception e) {
            ret = new SimpleDateFormat(IOS_DATE_FORMAT_OLD).format(d);
        }
        return ret;
    }
    
    public static boolean toBool(String bStr) {
        if (bStr!=null) {
            return Boolean.parseBoolean(bStr);
        }
        return false;
    }
    
    public static String toBoolStr(boolean b) {
        return String.valueOf(b);
    }
    
    public static void printProperty(String key, Object value) {
        String redacted = isSensitiveKey(key) ? "<redacted>" : (value != null ? value.toString() : "NULL");
        LOGGER.fine(() -> key + "=" + redacted);
    }

    private static boolean isSensitiveKey(String key) {
        if (key == null) {
            return false;
        }
        String lower = key.toLowerCase(Locale.ROOT);
        return lower.contains("password")
                || lower.contains("token")
                || lower.contains("secret");
    }
}
