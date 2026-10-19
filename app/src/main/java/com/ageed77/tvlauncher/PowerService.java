package com.ageed77.tvlauncher;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Runs the TV's own Sleep and Power off actions. It opens the system power menu, finds the
 * matching button and presses it, so the launcher can show its own pop-up instead.
 */
public class PowerService extends AccessibilityService {
    static PowerService instance;

    final Handler h = new Handler(Looper.getMainLooper());
    Pattern wanted;
    boolean sleepRequested;
    int tries;

    @Override
    protected void onServiceConnected() {
        instance = this;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        instance = null;
        return super.onUnbind(intent);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
    }

    /** Returns false if the service isn't running or the power menu couldn't be opened. */
    static boolean trigger(boolean sleep) {
        PowerService s = instance;
        return s != null && s.begin(sleep);
    }

    boolean begin(boolean sleep) {
        h.removeCallbacks(scan);
        sleepRequested = sleep;
        wanted = Pattern.compile(
                sleep ? "sleep|standby" : "power\\s*off|shut\\s*down|turn\\s*off",
                Pattern.CASE_INSENSITIVE);
        tries = 0;
        if (!performGlobalAction(GLOBAL_ACTION_POWER_DIALOG)) {
            return false;
        }
        h.postDelayed(scan, 500);
        return true;
    }

    final Runnable scan = new Runnable() {
        @Override
        public void run() {
            if (tryClick()) {
                return;
            }
            tries++;
            if (tries < 8) {
                h.postDelayed(this, 300);
                return;
            }
            // The button wasn't found. For sleep, try locking the screen instead.
            // For power off, the system menu is left open so you can pick there.
            if (sleepRequested && Build.VERSION.SDK_INT >= 28) {
                performGlobalAction(GLOBAL_ACTION_BACK);
                h.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN);
                    }
                }, 400);
            }
        }
    };

    boolean tryClick() {
        List<AccessibilityWindowInfo> windows = getWindows();
        if (windows != null) {
            for (AccessibilityWindowInfo w : windows) {
                AccessibilityNodeInfo root = w.getRoot();
                if (root != null && click(root)) {
                    return true;
                }
            }
        }
        AccessibilityNodeInfo active = getRootInActiveWindow();
        return active != null && click(active);
    }

    boolean click(AccessibilityNodeInfo node) {
        if (node == null) {
            return false;
        }
        CharSequence pkg = node.getPackageName();
        if (pkg != null && getPackageName().contentEquals(pkg)) {
            return false; // never press our own buttons
        }
        CharSequence text = node.getText();
        CharSequence desc = node.getContentDescription();
        boolean match = (text != null && wanted.matcher(text).find())
                || (desc != null && wanted.matcher(desc).find());
        if (match) {
            AccessibilityNodeInfo n = node;
            for (int i = 0; i < 5 && n != null; i++) {
                if (n.isClickable()) {
                    return n.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                }
                n = n.getParent();
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (click(node.getChild(i))) {
                return true;
            }
        }
        return false;
    }
}
