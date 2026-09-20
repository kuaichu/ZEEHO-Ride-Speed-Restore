package io.github.codex.zeehospeed;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Restores the speed fields on ZEEHO's ride-record screens.
 *
 * The target APK is protected and its business classes are loaded dynamically,
 * so the hook deliberately uses stable Android view/activity APIs and resource
 * entry names instead of obfuscated application classes.
 */
public final class MainHook implements IXposedHookLoadPackage {
    private static final String TARGET_PACKAGE = "com.cfmoto";

    private static final String HISTORY_ACTIVITY =
            "com.cfmoto.ui.moto.HistoryTravelActivity";
    private static final String DETAIL_ACTIVITY =
            "com.cfmoto.ui.moto.TravelDetailActivity";
    private static final String MY_RIDE_ACTIVITY =
            "com.cfmoto.ui.moto.myrideinfo.MyRideInfoActivity";

    private static final Set<TextView> LOGGED_TEXT =
            Collections.newSetFromMap(new WeakHashMap<TextView, Boolean>());
    private static WeakReference<Activity> currentRideActivity = new WeakReference<>(null);

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!TARGET_PACKAGE.equals(lpparam.packageName)) {
            return;
        }

        try {
            hookActivityLifecycle();
            hookVisibility();
            hookAttachedViews();
            hookSpeedTextLogging();
            XposedBridge.log("[ZeehoSpeed] ride-record hooks installed");
        } catch (Throwable t) {
            // A failed optional hook must never prevent ZEEHO from starting.
            XposedBridge.log("[ZeehoSpeed] install failed: " + t);
        }
    }

    private static void hookActivityLifecycle() {
        XposedHelpers.findAndHookMethod(Activity.class, "onPostResume", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                Activity activity = (Activity) param.thisObject;
                if (!isRideRecordActivity(activity)) {
                    return;
                }
                currentRideActivity = new WeakReference<>(activity);
                View root = activity.getWindow() == null
                        ? null : activity.getWindow().getDecorView();
                if (root != null) {
                    scheduleScan(activity, root, 0);
                    // Network data and RecyclerView rows arrive after onPostResume.
                    for (int delay = 500; delay <= 12_000; delay += 500) {
                        scheduleScan(activity, root, delay);
                    }
                }
            }
        });
    }

    private static void hookVisibility() {
        XposedHelpers.findAndHookMethod(View.class, "setVisibility", int.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        View view = (View) param.thisObject;
                        if (isSpeedView(view) && (Integer) param.args[0] != View.VISIBLE) {
                            // The new build hides these fields at runtime even though
                            // the XML still contains the same record-speed views.
                            param.args[0] = View.VISIBLE;
                        }
                    }
                });
    }

    private static void hookAttachedViews() {
        XposedHelpers.findAndHookMethod(View.class, "onAttachedToWindow",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        View view = (View) param.thisObject;
                        if (isSpeedView(view)) {
                            // RecyclerView rows are created after the Activity resumes.
                            view.post(() -> revealView(view));
                        }
                    }
                });
    }

    private static void hookSpeedTextLogging() {
        XposedHelpers.findAndHookMethod(TextView.class, "setText",
                CharSequence.class, TextView.BufferType.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        TextView view = (TextView) param.thisObject;
                        if (!isSpeedView(view)) {
                            return;
                        }
                        revealView(view);
                        CharSequence text = view.getText();
                        if (text != null && text.length() > 0) {
                            synchronized (LOGGED_TEXT) {
                                if (LOGGED_TEXT.add(view)) {
                                    XposedBridge.log("[ZeehoSpeed] " + resourceName(view)
                                            + " = " + text);
                                }
                            }
                        }
                    }
                });
    }

    private static void scheduleScan(Activity activity, View root, long delay) {
        if (root == null) {
            return;
        }
        Runnable task = () -> {
            if (!activity.isFinishing() && isRideRecordActivity(activity)) {
                scanTree(activity, root);
            }
        };
        if (delay == 0 && Looper.myLooper() == Looper.getMainLooper()) {
            task.run();
        } else {
            root.postDelayed(task, delay);
        }
    }

    private static void scanTree(Activity activity, View view) {
        if (isSpeedView(view)) {
            revealView(view);
        }

        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                scanTree(activity, group.getChildAt(i));
            }
        }

        // These containers start as GONE in activity_travel_detail.xml and are
        // made visible by the app only after data arrives. If the new build
        // hides the card after binding, restore it once another record value is
        // present (distance/time is enough even if the speed value is blank).
        if (isDetailActivity(activity) && isRecordContainer(view)
                && hasMeaningfulRecordValue(activity.getWindow().getDecorView())) {
            view.setVisibility(View.VISIBLE);
        }
    }

    private static void revealView(View view) {
        if (view.getVisibility() != View.VISIBLE) {
            view.setVisibility(View.VISIBLE);
        }
    }

    private static boolean isSpeedView(View view) {
        Activity activity = findActivity(view.getContext());
        if (!isRideRecordActivity(activity)) {
            activity = currentRideActivity.get();
        }
        if (!isRideRecordActivity(activity)) {
            return false;
        }

        String name = resourceName(view);
        if (name == null) {
            return false;
        }
        if (isHistoryActivity(activity)) {
            return "tv_travel_speed".equals(name);
        }
        if (isMyRideActivity(activity)) {
            // layout_today_data_2 contains this third column. In 3.0.4 it is
            // hidden at runtime, so the card collapses to mileage + duration.
            return "tv_speed".equals(name);
        }
        return "tv_speed".equals(name)
                || "tv_speed_playing".equals(name)
                || "tv_travel_speed".equals(name)
                || "tv_max_speed".equals(name)
                || "tv_avg_speed".equals(name);
    }

    private static boolean isRecordContainer(View view) {
        String name = resourceName(view);
        return "cl_ride_info".equals(name)
                || "group_rideinfo".equals(name);
    }

    private static boolean hasMeaningfulRecordValue(View root) {
        if (root == null) {
            return false;
        }
        if (root instanceof TextView) {
            CharSequence text = ((TextView) root).getText();
            if (text != null && isMeaningful(text.toString())) {
                String name = resourceName(root);
                if ("tv_all_km".equals(name) || "tv_hour".equals(name)
                        || "tv_min".equals(name) || "tv_speed".equals(name)
                        || "tv_speed_playing".equals(name)
                        || "tv_travel_speed".equals(name)) {
                    return true;
                }
            }
        }
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                if (hasMeaningfulRecordValue(group.getChildAt(i))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isMeaningful(String text) {
        String value = text.trim();
        return !value.isEmpty() && !"--".equals(value) && !"无数据".equals(value);
    }

    private static boolean isRideRecordActivity(Activity activity) {
        return isHistoryActivity(activity) || isDetailActivity(activity)
                || isMyRideActivity(activity);
    }

    private static boolean isHistoryActivity(Activity activity) {
        return activity != null && HISTORY_ACTIVITY.equals(activity.getClass().getName());
    }

    private static boolean isDetailActivity(Activity activity) {
        return activity != null && DETAIL_ACTIVITY.equals(activity.getClass().getName());
    }

    private static boolean isMyRideActivity(Activity activity) {
        return activity != null && MY_RIDE_ACTIVITY.equals(activity.getClass().getName());
    }

    private static Activity findActivity(Context context) {
        Context current = context;
        while (current instanceof ContextWrapper) {
            if (current instanceof Activity) {
                return (Activity) current;
            }
            Context base = ((ContextWrapper) current).getBaseContext();
            if (base == current) {
                break;
            }
            current = base;
        }
        return current instanceof Activity ? (Activity) current : null;
    }

    private static String resourceName(View view) {
        int id = view.getId();
        if (id == View.NO_ID) {
            return null;
        }
        try {
            return view.getResources().getResourceEntryName(id);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
