package com.ageed77.tvlauncher;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Path;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

/**
 * Home screen for the TV. Laid out on a 1280x720 design canvas that is scaled to the real screen,
 * so it looks the same at any resolution or density.
 */
public class MainActivity extends Activity {

    // Weather location (Baghdad). Change these two numbers to move it.
    static final double LAT = 33.3152;
    static final double LON = 44.3661;

    static final String YOUTUBE = "com.google.android.youtube.tv";
    static final String NETFLIX = "com.netflix.ninja";

    static final int TEXT = 0xFFF1F1F4;
    static final int MUTED = 0xFFB9BCCB;

    interface Pick {
        void on(String id);
    }

    interface IndexPick {
        void on(int index);
    }

    static class Item {
        String label;
        Drawable img;
        Bitmap bmp;
        Runnable click;
    }

    float s = 1f;
    final Handler h = new Handler(Looper.getMainLooper());
    SharedPreferences prefs;
    TextView greeting, temp, cond, rowLabel, watchLabel;
    LinearLayout mainRow, watchRow;
    View firstTile;
    FrameLayout rootView, overlay;
    View focusBefore;

    int u(float v) {
        return Math.round(v * s);
    }

    // ---------------------------------------------------------------- lifecycle

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        DisplayMetrics dm = getResources().getDisplayMetrics();
        s = Math.min(dm.widthPixels / 1280f, dm.heightPixels / 720f);
        prefs = getSharedPreferences("launcher", MODE_PRIVATE);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF14161F);
        root.setClipChildren(false);
        rootView = root;

        // soft background circles
        place(root, circle(0xFF2B2F5C), u(460), u(460), u(910), u(-150));
        place(root, circle(0xFF3A2447), u(420), u(420), u(-80), u(500));

        // greeting, clock, weather, date
        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        greeting = text("Good morning", 24, MUTED, false);
        left.addView(greeting);

        LinearLayout clockRow = new LinearLayout(this);
        clockRow.setOrientation(LinearLayout.HORIZONTAL);
        clockRow.setGravity(Gravity.BOTTOM);
        TextClock clock = new TextClock(this);
        clock.setFormat12Hour("h:mm");
        clock.setFormat24Hour("h:mm");
        clock.setTextColor(TEXT);
        clock.setTextSize(TypedValue.COMPLEX_UNIT_PX, u(104));
        clock.setIncludeFontPadding(false);
        clock.setLetterSpacing(-0.02f);
        clock.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        clockRow.addView(clock);

        LinearLayout weather = new LinearLayout(this);
        weather.setOrientation(LinearLayout.VERTICAL);
        weather.setPadding(0, 0, 0, u(14));
        temp = text("", 48, TEXT, true);
        cond = text("", 22, MUTED, false);
        weather.addView(temp);
        weather.addView(cond);
        LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        wlp.leftMargin = u(36);
        clockRow.addView(weather, wlp);
        left.addView(clockRow);

        TextClock date = new TextClock(this);
        date.setFormat12Hour("EEEE, MMMM d");
        date.setFormat24Hour("EEEE, MMMM d");
        date.setTextColor(MUTED);
        date.setTextSize(TypedValue.COMPLEX_UNIT_PX, u(24));
        date.setIncludeFontPadding(false);
        left.addView(date);
        place(root, left, FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                u(64), u(52));

        // top right: last input, inputs, wi-fi, settings, power
        LinearLayout pills = new LinearLayout(this);
        pills.setOrientation(LinearLayout.HORIZONTAL);
        pills.setGravity(Gravity.CENTER_VERTICAL);
        pills.setClipChildren(false);
        addPill(pills, pill("Last input", new Runnable() {
            @Override
            public void run() {
                openLastInput();
            }
        }));
        addPill(pills, pill("Inputs", new Runnable() {
            @Override
            public void run() {
                pickInput("Inputs", new Pick() {
                    @Override
                    public void on(String id) {
                        openInput(id);
                    }
                });
            }
        }));
        addPill(pills, pill("Wi-Fi", new Runnable() {
            @Override
            public void run() {
                startSafe(new Intent(Settings.ACTION_WIFI_SETTINGS));
            }
        }));
        addPill(pills, pill("Settings", new Runnable() {
            @Override
            public void run() {
                startSafe(new Intent(Settings.ACTION_SETTINGS));
            }
        }));

        FrameLayout power = new FrameLayout(this);
        power.addView(new PowerIcon(this), new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        focusable(power, 99, new Runnable() {
            @Override
            public void run() {
                powerMenu();
            }
        });
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(u(58), u(58));
        pills.addView(power, plp);

        FrameLayout.LayoutParams pillsLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        pillsLp.gravity = Gravity.TOP | Gravity.RIGHT;
        pillsLp.rightMargin = u(64);
        pillsLp.topMargin = u(56);
        root.addView(pills, pillsLp);

        // main row
        rowLabel = text("YouTube", 26, TEXT, true);
        place(root, rowLabel, FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                u(64), u(372));
        mainRow = new LinearLayout(this);
        mainRow.setOrientation(LinearLayout.HORIZONTAL);
        mainRow.setClipChildren(false);
        place(root, mainRow, FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                u(64), u(416));
        buildMainRow();

        // second row
        watchLabel = text("Continue watching", 22, MUTED, false);
        place(root, watchLabel, FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                u(64), u(576));
        watchRow = new LinearLayout(this);
        watchRow.setOrientation(LinearLayout.HORIZONTAL);
        watchRow.setClipChildren(false);
        place(root, watchRow, FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                u(64), u(616));

        setContentView(root);
        if (firstTile != null) {
            firstTile.requestFocus();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        greetTask.run();
        weatherTask.run();
        loadWatchNext();
    }

    @Override
    protected void onPause() {
        super.onPause();
        h.removeCallbacks(greetTask);
        h.removeCallbacks(weatherTask);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        closeOverlay();
        if (firstTile != null) {
            firstTile.requestFocus();
        }
    }

    @Override
    public void onBackPressed() {
        // Back only closes the pop-up menus. On the home screen itself it does nothing.
        closeOverlay();
    }

    // ---------------------------------------------------------------- periodic work

    final Runnable greetTask = new Runnable() {
        @Override
        public void run() {
            int hr = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
            String g = hr < 12 ? "Good morning" : (hr < 18 ? "Good afternoon" : "Good evening");
            if (greeting != null) {
                greeting.setText(g);
            }
            h.postDelayed(this, 60000L);
        }
    };

    final Runnable weatherTask = new Runnable() {
        @Override
        public void run() {
            loadWeather();
            h.postDelayed(this, 30L * 60L * 1000L);
        }
    };

    void loadWeather() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    URL url = new URL("https://api.open-meteo.com/v1/forecast?latitude=" + LAT
                            + "&longitude=" + LON + "&current=temperature_2m,weather_code");
                    HttpURLConnection c = (HttpURLConnection) url.openConnection();
                    c.setConnectTimeout(8000);
                    c.setReadTimeout(8000);
                    BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = r.readLine()) != null) {
                        sb.append(line);
                    }
                    r.close();
                    JSONObject cur = new JSONObject(sb.toString()).getJSONObject("current");
                    final int t = (int) Math.round(cur.getDouble("temperature_2m"));
                    final String d = describe(cur.getInt("weather_code"));
                    h.post(new Runnable() {
                        @Override
                        public void run() {
                            temp.setText(t + "°");
                            cond.setText(d);
                        }
                    });
                } catch (Exception e) {
                    // keep whatever is showing
                }
            }
        }).start();
    }

    static String describe(int code) {
        if (code == 0) return "Clear";
        if (code == 1 || code == 2) return "Partly cloudy";
        if (code == 3) return "Cloudy";
        if (code == 45 || code == 48) return "Fog";
        if (code >= 51 && code <= 57) return "Drizzle";
        if (code >= 61 && code <= 67) return "Rain";
        if (code >= 71 && code <= 77) return "Snow";
        if (code >= 80 && code <= 82) return "Showers";
        if (code >= 95) return "Thunderstorm";
        return "";
    }

    // ---------------------------------------------------------------- rows

    void buildMainRow() {
        mainRow.removeAllViews();

        View yt = tile("YouTube", appImage(YOUTUBE), null, 200, 112, launch(YOUTUBE), null, rowLabel);
        addTo(mainRow, yt, 200, 112);
        firstTile = yt;

        Drawable xb = getResources().getDrawable(R.drawable.xbox, getTheme());
        View xbox = tile("Xbox", xb, null, 200, 112, new Runnable() {
            @Override
            public void run() {
                openXbox();
            }
        }, new Runnable() {
            @Override
            public void run() {
                chooseXbox();
            }
        }, rowLabel);
        addTo(mainRow, xbox, 200, 112);

        addTo(mainRow, tile("Netflix", appImage(NETFLIX), null, 200, 112, launch(NETFLIX), null, rowLabel),
                200, 112);

        addTo(mainRow, tile("Apps", null, null, 112, 112, new Runnable() {
            @Override
            public void run() {
                showApps();
            }
        }, null, rowLabel), 112, 112);
    }

    void fillWatch(String label, List<Item> items) {
        watchLabel.setText(label);
        watchRow.removeAllViews();
        for (Item it : items) {
            addTo(watchRow, tile(it.label, it.img, it.bmp, 200, 112, it.click, null, null), 200, 112);
        }
    }

    /** Fills the second row from the TV's Watch Next list, or with your apps if that isn't readable. */
    void loadWatchNext() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<Item> items = new ArrayList<Item>();
                if (Build.VERSION.SDK_INT >= 26) {
                    Cursor c = null;
                    try {
                        c = getContentResolver().query(
                                Uri.parse("content://android.media.tv/watch_next_program"),
                                null, null, null, null);
                        if (c != null) {
                            int ti = c.getColumnIndex("title");
                            int po = c.getColumnIndex("poster_art_uri");
                            int th = c.getColumnIndex("thumbnail_uri");
                            int ii = c.getColumnIndex("intent_uri");
                            while (c.moveToNext() && items.size() < 4) {
                                String title = ti >= 0 ? c.getString(ti) : "";
                                String art = po >= 0 ? c.getString(po) : null;
                                if (art == null && th >= 0) {
                                    art = c.getString(th);
                                }
                                final String iu = ii >= 0 ? c.getString(ii) : null;
                                if (iu == null) {
                                    continue;
                                }
                                Item it = new Item();
                                it.label = title == null ? "" : title;
                                it.bmp = art == null ? null : fetchBitmap(art);
                                it.click = new Runnable() {
                                    @Override
                                    public void run() {
                                        try {
                                            Intent i = Intent.parseUri(iu, Intent.URI_INTENT_SCHEME);
                                            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                            startActivity(i);
                                        } catch (Exception e) {
                                            toast("Couldn't open that");
                                        }
                                    }
                                };
                                items.add(it);
                            }
                        }
                    } catch (Exception e) {
                        items.clear();
                    } finally {
                        if (c != null) {
                            c.close();
                        }
                    }
                }
                final String label;
                if (items.isEmpty()) {
                    label = "Your apps";
                    items.addAll(appItems(4));
                } else {
                    label = "Continue watching";
                }
                h.post(new Runnable() {
                    @Override
                    public void run() {
                        fillWatch(label, items);
                    }
                });
            }
        }).start();
    }

    List<Item> appItems(int max) {
        final PackageManager pm = getPackageManager();
        List<Item> out = new ArrayList<Item>();
        for (final ResolveInfo ri : apps()) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(YOUTUBE) || pkg.equals(NETFLIX)) {
                continue;
            }
            Item it = new Item();
            it.label = ri.loadLabel(pm).toString();
            Drawable d = ri.activityInfo.loadBanner(pm);
            it.img = d != null ? d : ri.loadIcon(pm);
            it.click = launchInfo(ri);
            out.add(it);
            if (out.size() >= max) {
                break;
            }
        }
        return out;
    }

    // ---------------------------------------------------------------- apps

    List<ResolveInfo> apps() {
        final PackageManager pm = getPackageManager();
        Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        List<ResolveInfo> all = pm.queryIntentActivities(i, 0);
        List<ResolveInfo> out = new ArrayList<ResolveInfo>();
        for (ResolveInfo r : all) {
            if (!r.activityInfo.packageName.equals(getPackageName())) {
                out.add(r);
            }
        }
        Collections.sort(out, new java.util.Comparator<ResolveInfo>() {
            @Override
            public int compare(ResolveInfo a, ResolveInfo b) {
                return a.loadLabel(pm).toString().compareToIgnoreCase(b.loadLabel(pm).toString());
            }
        });
        return out;
    }

    void showApps() {
        final PackageManager pm = getPackageManager();
        final List<ResolveInfo> list = apps();
        CharSequence[] names = new CharSequence[list.size()];
        for (int k = 0; k < names.length; k++) {
            names[k] = list.get(k).loadLabel(pm);
        }
        showList("Apps", names, new IndexPick() {
            @Override
            public void on(int which) {
                launchInfo(list.get(which)).run();
            }
        });
    }

    Runnable launchInfo(final ResolveInfo ri) {
        return new Runnable() {
            @Override
            public void run() {
                Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
                i.setClassName(ri.activityInfo.packageName, ri.activityInfo.name);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startSafe(i);
            }
        };
    }

    Runnable launch(final String pkg) {
        return new Runnable() {
            @Override
            public void run() {
                PackageManager pm = getPackageManager();
                Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
                if (i == null) {
                    i = pm.getLaunchIntentForPackage(pkg);
                }
                if (i == null) {
                    toast("Not installed");
                    return;
                }
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startSafe(i);
            }
        };
    }

    Drawable appImage(String pkg) {
        PackageManager pm = getPackageManager();
        try {
            Intent li = pm.getLeanbackLaunchIntentForPackage(pkg);
            if (li != null) {
                Drawable b = pm.getActivityBanner(li);
                if (b != null) {
                    return b;
                }
            }
            Drawable b2 = pm.getApplicationBanner(pkg);
            if (b2 != null) {
                return b2;
            }
            return pm.getApplicationIcon(pkg);
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    // ---------------------------------------------------------------- TV inputs

    List<TvInputInfo> inputs() {
        List<TvInputInfo> out = new ArrayList<TvInputInfo>();
        try {
            TvInputManager m = (TvInputManager) getSystemService(Context.TV_INPUT_SERVICE);
            if (m != null) {
                for (TvInputInfo i : m.getTvInputList()) {
                    if (i.isPassthroughInput()) {
                        out.add(i);
                    }
                }
            }
        } catch (Exception e) {
            // fall through with whatever we have
        }
        return out;
    }

    boolean inputExists(String id) {
        for (TvInputInfo i : inputs()) {
            if (i.getId().equals(id)) {
                return true;
            }
        }
        return false;
    }

    void pickInput(String title, final Pick cb) {
        List<TvInputInfo> list = inputs();
        if (list.isEmpty()) {
            toast("No TV inputs found");
            return;
        }
        final String[] ids = new String[list.size()];
        CharSequence[] names = new CharSequence[list.size()];
        for (int k = 0; k < ids.length; k++) {
            TvInputInfo i = list.get(k);
            ids[k] = i.getId();
            CharSequence n = i.loadCustomLabel(this);
            if (n == null || n.length() == 0) {
                n = i.loadLabel(this);
            }
            names[k] = n;
        }
        showList(title, names, new IndexPick() {
            @Override
            public void on(int which) {
                cb.on(ids[which]);
            }
        });
    }

    void openInput(String id) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(id));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
            prefs.edit().putString("last_input", id).apply();
        } catch (Exception e) {
            toast("Couldn't open that input");
        }
    }

    void openLastInput() {
        String id = prefs.getString("last_input", null);
        if (id != null && inputExists(id)) {
            openInput(id);
        } else {
            pickInput("Inputs", new Pick() {
                @Override
                public void on(String picked) {
                    openInput(picked);
                }
            });
        }
    }

    void openXbox() {
        String id = prefs.getString("xbox_input", null);
        if (id != null && inputExists(id)) {
            openInput(id);
        } else {
            chooseXbox();
        }
    }

    void chooseXbox() {
        pickInput("Which input is your Xbox on?", new Pick() {
            @Override
            public void on(String id) {
                prefs.edit().putString("xbox_input", id).apply();
                openInput(id);
            }
        });
    }

    void doPower(boolean sleep) {
        closeOverlay();
        if (!PowerService.trigger(sleep)) {
            toast("Turn on the power service first (see README)");
            startSafe(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        }
    }

    /** The Sleep / Turn off pop-up. */
    void powerMenu() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setClipChildren(false);

        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tl.bottomMargin = u(36);
        col.addView(text("Power", 34, TEXT, true), tl);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setClipChildren(false);
        View sleep = powerOption("Sleep", "Screen off, wakes quickly", new MoonIcon(this), new Runnable() {
            @Override
            public void run() {
                doPower(true);
            }
        });
        View off = powerOption("Turn off", "Powers down completely", new PowerIcon(this), new Runnable() {
            @Override
            public void run() {
                doPower(false);
            }
        });
        LinearLayout.LayoutParams a = new LinearLayout.LayoutParams(u(300), u(210));
        a.rightMargin = u(24);
        row.addView(sleep, a);
        row.addView(off, new LinearLayout.LayoutParams(u(300), u(210)));
        col.addView(row);

        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        hl.topMargin = u(32);
        col.addView(text("Press Back to cancel", 20, MUTED, false), hl);

        showOverlay(col);
        sleep.requestFocus();
    }

    View powerOption(String label, String sub, View icon, final Runnable action) {
        FrameLayout box = new FrameLayout(this);
        LinearLayout in = new LinearLayout(this);
        in.setOrientation(LinearLayout.VERTICAL);
        in.setGravity(Gravity.CENTER);
        in.addView(icon, new LinearLayout.LayoutParams(u(76), u(76)));
        LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        ll.topMargin = u(14);
        in.addView(text(label, 30, TEXT, true), ll);
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        sl.topMargin = u(6);
        in.addView(text(sub, 18, MUTED, false), sl);
        box.addView(in, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        box.setFocusable(true);
        box.setClickable(true);
        box.setBackground(glass(false, 28));
        box.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View x) {
                action.run();
            }
        });
        box.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View x, boolean f) {
                x.setBackground(glass(f, 28));
                float k = f ? 1.06f : 1f;
                x.animate().scaleX(k).scaleY(k).setDuration(120).start();
            }
        });
        return box;
    }

    // ---------------------------------------------------------------- pop-ups

    void showOverlay(View content) {
        closeOverlay();
        focusBefore = getCurrentFocus();
        setHomeFocusBlocked(true);
        overlay = new FrameLayout(this);
        overlay.setBackgroundColor(0xE60B0D14);
        overlay.setClickable(true);
        overlay.setClipChildren(false);
        overlay.addView(content, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER));
        rootView.addView(overlay, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
    }

    void closeOverlay() {
        if (overlay == null) {
            return;
        }
        rootView.removeView(overlay);
        overlay = null;
        setHomeFocusBlocked(false);
        if (focusBefore != null) {
            focusBefore.requestFocus();
        } else if (firstTile != null) {
            firstTile.requestFocus();
        }
        focusBefore = null;
    }

    /** Stops the D-pad from wandering onto the home screen while a pop-up is open. */
    void setHomeFocusBlocked(boolean block) {
        for (int i = 0; i < rootView.getChildCount(); i++) {
            View c = rootView.getChildAt(i);
            if (c != overlay && c instanceof ViewGroup) {
                ((ViewGroup) c).setDescendantFocusability(
                        block ? ViewGroup.FOCUS_BLOCK_DESCENDANTS : ViewGroup.FOCUS_AFTER_DESCENDANTS);
            }
        }
    }

    void showList(String title, CharSequence[] names, final IndexPick cb) {
        if (names.length == 0) {
            toast("Nothing to show");
            return;
        }
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setClipChildren(false);

        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tl.bottomMargin = u(24);
        col.addView(text(title, 32, TEXT, true), tl);

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        for (int k = 0; k < names.length; k++) {
            final int idx = k;
            TextView r = text(names[k].toString(), 24, TEXT, false);
            r.setGravity(Gravity.CENTER_VERTICAL);
            r.setPadding(u(28), u(16), u(28), u(16));
            focusable(r, 18, new Runnable() {
                @Override
                public void run() {
                    closeOverlay();
                    cb.on(idx);
                }
            });
            LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            rl.bottomMargin = u(8);
            list.addView(r, rl);
        }
        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        sv.addView(list);
        int boxHeight = Math.min(u(420), names.length * u(70));
        col.addView(sv, new LinearLayout.LayoutParams(u(560), boxHeight));

        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        hl.topMargin = u(24);
        col.addView(text("Press Back to cancel", 20, MUTED, false), hl);

        showOverlay(col);
        list.getChildAt(0).requestFocus();
    }

    // ---------------------------------------------------------------- view helpers

    void startSafe(Intent i) {
        try {
            startActivity(i);
        } catch (Exception e) {
            toast("Couldn't open that");
        }
    }

    void toast(final String msg) {
        h.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainActivity.this, msg, Toast.LENGTH_LONG).show();
            }
        });
    }

    TextView text(String t, float px, int color, boolean medium) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextColor(color);
        v.setTextSize(TypedValue.COMPLEX_UNIT_PX, u(px));
        v.setIncludeFontPadding(false);
        if (medium) {
            v.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        }
        return v;
    }

    View circle(int color) {
        View v = new View(this);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(color);
        v.setBackground(g);
        return v;
    }

    void place(FrameLayout parent, View v, int w, int hh, int l, int t) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w, hh);
        lp.gravity = Gravity.TOP | Gravity.LEFT;
        lp.leftMargin = l;
        lp.topMargin = t;
        parent.addView(v, lp);
    }

    void addTo(LinearLayout row, View v, int w, int hh) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(u(w), u(hh));
        lp.rightMargin = u(18);
        row.addView(v, lp);
    }

    void addPill(LinearLayout row, View v) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = u(12);
        row.addView(v, lp);
    }

    GradientDrawable glass(boolean focus, float radius) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(u(radius));
        g.setColor(focus ? 0x40FFFFFF : 0x24FFFFFF);
        g.setStroke(Math.max(1, u(focus ? 3 : 1)), focus ? 0xFFFFFFFF : 0x47FFFFFF);
        return g;
    }

    GradientDrawable border(boolean focus) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(u(17));
        if (focus) {
            g.setStroke(u(3), 0xFFFFFFFF);
        }
        return g;
    }

    void focusable(View v, final float radius, final Runnable action) {
        v.setFocusable(true);
        v.setClickable(true);
        v.setBackground(glass(false, radius));
        v.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View x) {
                action.run();
            }
        });
        v.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View x, boolean f) {
                x.setBackground(glass(f, radius));
            }
        });
    }

    View pill(String label, Runnable action) {
        TextView v = text(label, 22, TEXT, false);
        v.setGravity(Gravity.CENTER);
        v.setPadding(u(22), 0, u(22), 0);
        v.setMinHeight(u(58));
        focusable(v, 99, action);
        return v;
    }

    View tile(final String label, Drawable d, Bitmap b, int w, int hh, final Runnable click,
              final Runnable longClick, final TextView labelTarget) {
        FrameLayout box = new FrameLayout(this);
        int m = u(3);
        View content;
        if (d != null || b != null) {
            ImageView iv = new ImageView(this);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            if (b != null) {
                iv.setImageBitmap(b);
            } else {
                iv.setImageDrawable(d);
            }
            content = iv;
        } else {
            TextView tv = text(label, 22, TEXT, false);
            tv.setGravity(Gravity.CENTER);
            tv.setBackgroundColor(0x33FFFFFF);
            content = tv;
        }
        content.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), u(14));
            }
        });
        content.setClipToOutline(true);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(u(w) - 2 * m, u(hh) - 2 * m);
        lp.setMargins(m, m, m, m);
        box.addView(content, lp);

        box.setFocusable(true);
        box.setClickable(true);
        box.setBackground(border(false));
        box.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View x) {
                click.run();
            }
        });
        if (longClick != null) {
            box.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View x) {
                    longClick.run();
                    return true;
                }
            });
        }
        box.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View x, boolean f) {
                x.setBackground(border(f));
                float k = f ? 1.06f : 1f;
                x.animate().scaleX(k).scaleY(k).setDuration(120).start();
                if (f && labelTarget != null) {
                    labelTarget.setText(label);
                }
            }
        });
        return box;
    }

    Bitmap fetchBitmap(String src) {
        InputStream in = null;
        try {
            Uri u = Uri.parse(src);
            String sch = u.getScheme();
            if ("http".equals(sch) || "https".equals(sch)) {
                HttpURLConnection c = (HttpURLConnection) new URL(src).openConnection();
                c.setConnectTimeout(8000);
                c.setReadTimeout(8000);
                in = c.getInputStream();
            } else {
                in = getContentResolver().openInputStream(u);
            }
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inSampleSize = 2;
            return BitmapFactory.decodeStream(in, null, o);
        } catch (Exception e) {
            return null;
        } finally {
            try {
                if (in != null) {
                    in.close();
                }
            } catch (IOException e) {
                // ignore
            }
        }
    }

    /** Crescent moon drawn in code, used for Sleep. */
    class MoonIcon extends View {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        MoonIcon(Context c) {
            super(c);
            p.setStyle(Paint.Style.FILL);
            p.setColor(TEXT);
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth();
            float hh = getHeight();
            float r = Math.min(w, hh) * 0.34f;
            float cx = w / 2f;
            float cy = hh / 2f;
            Path moon = new Path();
            moon.addCircle(cx, cy, r, Path.Direction.CW);
            Path bite = new Path();
            bite.addCircle(cx + r * 0.55f, cy - r * 0.35f, r * 0.85f, Path.Direction.CW);
            moon.op(bite, Path.Op.DIFFERENCE);
            c.drawPath(moon, p);
        }
    }

    /** Simple power symbol drawn in code so no icon files are needed. */
    class PowerIcon extends View {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        PowerIcon(Context c) {
            super(c);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setColor(TEXT);
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth();
            float hh = getHeight();
            float r = Math.min(w, hh) * 0.26f;
            float cx = w / 2f;
            float cy = hh / 2f + r * 0.1f;
            p.setStrokeWidth(Math.max(2f, u(3)));
            RectF o = new RectF(cx - r, cy - r, cx + r, cy + r);
            c.drawArc(o, -60f, 300f, false, p);
            c.drawLine(cx, cy - r * 1.2f, cx, cy - r * 0.1f, p);
        }
    }
}
