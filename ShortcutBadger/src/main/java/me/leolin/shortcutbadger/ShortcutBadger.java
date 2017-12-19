package me.leolin.shortcutbadger;

import android.app.Notification;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedList;
import java.util.List;

import me.leolin.shortcutbadger.impl.AdwHomeBadger;
import me.leolin.shortcutbadger.impl.ApexHomeBadger;
import me.leolin.shortcutbadger.impl.AsusHomeBadger;
import me.leolin.shortcutbadger.impl.DefaultBadger;
import me.leolin.shortcutbadger.impl.EverythingMeHomeBadger;
import me.leolin.shortcutbadger.impl.HuaweiHomeBadger;
import me.leolin.shortcutbadger.impl.NewHtcHomeBadger;
import me.leolin.shortcutbadger.impl.NovaHomeBadger;
import me.leolin.shortcutbadger.impl.OPPOHomeBader;
import me.leolin.shortcutbadger.impl.SamsungHomeBadger;
import me.leolin.shortcutbadger.impl.SonyHomeBadger;
import me.leolin.shortcutbadger.impl.VivoHomeBadger;
import me.leolin.shortcutbadger.impl.ZTEHomeBadger;
import me.leolin.shortcutbadger.impl.ZukHomeBadger;


/**
 * @author Leo Lin
 */
public final class ShortcutBadger {

    private static final String LOG_TAG = "ShortcutBadger";
    private static final int SUPPORTED_CHECK_ATTEMPTS = 3;

    private static final List<Class<? extends Badger>> BADGERS = new LinkedList<Class<? extends Badger>>();

    private volatile static Boolean sIsBadgeCounterSupported;
    private final static Object sCounterSupportedLock = new Object();

    static {
        BADGERS.add(AdwHomeBadger.class);
        BADGERS.add(ApexHomeBadger.class);
        BADGERS.add(DefaultBadger.class);
        BADGERS.add(NewHtcHomeBadger.class);
        BADGERS.add(NovaHomeBadger.class);
        BADGERS.add(SonyHomeBadger.class);
        BADGERS.add(AsusHomeBadger.class);
        BADGERS.add(HuaweiHomeBadger.class);
        BADGERS.add(OPPOHomeBader.class);
        BADGERS.add(SamsungHomeBadger.class);
        BADGERS.add(ZukHomeBadger.class);
        BADGERS.add(VivoHomeBadger.class);
        BADGERS.add(ZTEHomeBadger.class);
        BADGERS.add(EverythingMeHomeBadger.class);
    }

    private static List<Badger> sShortcutBadgers;
    private static ComponentName sComponentName;

    /**
     * Tries to update the notification count
     *
     * @param context    Caller context
     * @param badgeCount Desired badge count
     * @return true in case of success, false otherwise
     */
    public static boolean applyCount(Context context, int badgeCount) {
        try {
            applyCountOrThrow(context, badgeCount);
            return true;
        } catch (ShortcutBadgeException e) {
            if (Log.isLoggable(LOG_TAG, Log.DEBUG)) {
                Log.d(LOG_TAG, "Unable to execute badge", e);
            }
            return false;
        }
    }

    /**
     * Tries to update the notification count, throw a {@link ShortcutBadgeException} if it fails
     *
     * @param context    Caller context
     * @param badgeCount Desired badge count
     */
    public static void applyCountOrThrow(Context context, int badgeCount) throws ShortcutBadgeException {
        if (sShortcutBadgers == null) {
            boolean launcherReady = initBadger(context);

            if (!launcherReady)
                throw new ShortcutBadgeException("No default launcher available");
        }
        boolean isSuccess = false;
        for (Badger badger : sShortcutBadgers) {
            try {
                badger.executeBadge(context, sComponentName, badgeCount);
            } catch (Exception e) {
                continue;
            }
            isSuccess = true;
        }
        if (!isSuccess) {
            throw new ShortcutBadgeException("Unable to execute badge");
        }
    }

    /**
     * Tries to remove the notification count
     *
     * @param context Caller context
     * @return true in case of success, false otherwise
     */
    public static boolean removeCount(Context context) {
        return applyCount(context, 0);
    }

    /**
     * Tries to remove the notification count, throw a {@link ShortcutBadgeException} if it fails
     *
     * @param context Caller context
     */
    public static void removeCountOrThrow(Context context) throws ShortcutBadgeException {
        applyCountOrThrow(context, 0);
    }

    /**
     * Whether this platform launcher supports shortcut badges. Doing this check causes the side
     * effect of resetting the counter if it's supported, so this method should be followed by
     * a call that actually sets the counter to the desired value, if the counter is supported.
     */
    public static boolean isBadgeCounterSupported(Context context) {
        // Checking outside synchronized block to avoid synchronization in the common case (flag
        // already set), and improve perf.
        if (sIsBadgeCounterSupported == null) {
            synchronized (sCounterSupportedLock) {
                // Checking again inside synch block to avoid setting the flag twice.
                if (sIsBadgeCounterSupported == null) {
                    String lastErrorMessage = null;
                    for (int i = 0; i < SUPPORTED_CHECK_ATTEMPTS; i++) {
                        try {
                            Log.i(LOG_TAG, "Checking if platform supports badge counters, attempt "
                                    + String.format("%d/%d.", i + 1, SUPPORTED_CHECK_ATTEMPTS));
                            if (initBadger(context)) {
                                for (Badger badger : sShortcutBadgers) {
                                    badger.executeBadge(context, sComponentName, 0);
                                }
                                sIsBadgeCounterSupported = true;
                                Log.i(LOG_TAG, "Badge counter is supported in this platform.");
                                break;
                            } else {
                                lastErrorMessage = "Failed to initialize the badge counter.";
                            }
                        } catch (Exception e) {
                            // Keep retrying as long as we can. No need to dump the stack trace here
                            // because this error will be the norm, not exception, for unsupported
                            // platforms. So we just save the last error message to display later.
                            lastErrorMessage = e.getMessage();
                        }
                    }

                    if (sIsBadgeCounterSupported == null) {
                        Log.w(LOG_TAG, "Badge counter seems not supported for this platform: "
                                + lastErrorMessage);
                        sIsBadgeCounterSupported = false;
                    }
                }
            }
        }
        return sIsBadgeCounterSupported;
    }

    /**
     * @param context      Caller context
     * @param notification
     * @param badgeCount
     */
    public static void applyNotification(Context context, Notification notification, int badgeCount) {
        if (Build.MANUFACTURER.equalsIgnoreCase("Xiaomi")) {
            try {
                Field field = notification.getClass().getDeclaredField("extraNotification");
                Object extraNotification = field.get(notification);
                Method method = extraNotification.getClass().getDeclaredMethod("setMessageCount", int.class);
                method.invoke(extraNotification, badgeCount);
            } catch (Exception e) {
                if (Log.isLoggable(LOG_TAG, Log.DEBUG)) {
                    Log.d(LOG_TAG, "Unable to execute badge", e);
                }
            }
        }
    }

    // Initialize Badger if a launcher is availalble (eg. set as default on the device)
    // Returns true if a launcher is available, in this case, the Badger will be set and sShortcutBadger will be non null.
    private static boolean initBadger(Context context) {
        Intent launchIntent = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
        if (launchIntent == null) {
            Log.e(LOG_TAG, "Unable to find launch intent for package " + context.getPackageName());
            return false;
        }

        sShortcutBadgers = new LinkedList<Badger>();

        sComponentName = launchIntent.getComponent();

        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_HOME);
        List<ResolveInfo> resolveInfos = context.getPackageManager().queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);

        for (ResolveInfo resolveInfo : resolveInfos) {
            String currentHomePackage = resolveInfo.activityInfo.packageName;

            for (Class<? extends Badger> badger : BADGERS) {
                Badger shortcutBadger = null;
                try {
                    shortcutBadger = badger.newInstance();
                } catch (Exception ignored) {
                }
                if (shortcutBadger != null && shortcutBadger.getSupportLaunchers().contains(currentHomePackage)) {
                    sShortcutBadgers.add(shortcutBadger);
                    break;
                }
            }
        }

        if (sShortcutBadgers.size() == 0) {
            if (Build.MANUFACTURER.equalsIgnoreCase("ZUK"))
                sShortcutBadgers.add(new ZukHomeBadger());
            else if (Build.MANUFACTURER.equalsIgnoreCase("OPPO"))
                sShortcutBadgers.add(new OPPOHomeBader());
            else if (Build.MANUFACTURER.equalsIgnoreCase("VIVO"))
                sShortcutBadgers.add(new VivoHomeBadger());
            else if (Build.MANUFACTURER.equalsIgnoreCase("ZTE"))
                sShortcutBadgers.add(new ZTEHomeBadger());
            else
                sShortcutBadgers.add(new DefaultBadger());
        }

        return true;
    }

    // Avoid anybody to instantiate this class
    private ShortcutBadger() {

    }
}
