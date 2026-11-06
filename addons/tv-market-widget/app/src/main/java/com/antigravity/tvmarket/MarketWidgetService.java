package com.antigravity.tvmarket;

import android.accessibilityservice.AccessibilityService;
import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.widget.LinearLayout;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import java.util.List;

public class MarketWidgetService extends AccessibilityService {

    public static final String ACTION_UPDATE_WIDGET = "com.antigravity.tvmarket.UPDATE_WIDGET";

    private WindowManager wm;
    private LinearLayout rootRow;
    private WindowManager.LayoutParams params;

    // Card 1 (Left): Weather (Di Linh, Lam Dong)
    private TextView tvWeatherHeader;
    private TextView tvWeatherMain;
    private TextView tvWeatherRow2;
    private TextView tvWeatherRow3;

    // Card 2 (Middle): Coffee
    private TextView tvCoffeeRow1;
    private TextView tvCoffeeRow2;
    private TextView tvCoffeeRow3;

    // Card 3 (Right): Gold Table
    private TextView tvSjcBuy;
    private TextView tvSjcSell;
    private TextView tvRingBuy;
    private TextView tvRingSell;
    private TextView tvWorldGold;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean isScreenAwake = true;
    private long lastFetchTime = 0;

    private final Runnable periodicRefresh = new Runnable() {
        @Override
        public void run() {
            triggerFetchAsync();
            handler.postDelayed(this, 15 * 60 * 1000L);
        }
    };

    private final Runnable uiStatePoll = new Runnable() {
        @Override
        public void run() {
            evaluateScreenAndScroll();
            handler.postDelayed(this, 180L);
        }
    };

    private final BroadcastReceiver updateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent != null) {
                String action = intent.getAction();
                if (Intent.ACTION_SCREEN_OFF.equals(action)
                        || Intent.ACTION_DREAMING_STARTED.equals(action)) {
                    isScreenAwake = false;
                    setWidgetVisible(false);
                    return;
                } else if (Intent.ACTION_SCREEN_ON.equals(action)
                        || Intent.ACTION_DREAMING_STOPPED.equals(action)) {
                    isScreenAwake = true;
                }
            }
            evaluateScreenAndScroll();
            updateTextFromCache();
            if (intent != null && intent.getBooleanExtra("fetch_now", false)) {
                triggerFetchAsync();
            }
        }
    };

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        createOverlay();
        updateTextFromCache();
        triggerFetchAsync();
        evaluateScreenAndScroll();

        IntentFilter filter = new IntentFilter(ACTION_UPDATE_WIDGET);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_DREAMING_STARTED);
        filter.addAction(Intent.ACTION_DREAMING_STOPPED);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(updateReceiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(updateReceiver, filter);
        }

        handler.postDelayed(periodicRefresh, 15 * 60 * 1000L);
        handler.postDelayed(uiStatePoll, 180L);
    }

    private GradientDrawable makeGlassBg() {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#A80D1117"));
        bg.setCornerRadius(dp(18));
        bg.setStroke(dp(1), Color.parseColor("#45FFFFFF"));
        return bg;
    }

    private void setSingleLine(TextView tv) {
        tv.setSingleLine(true);
        tv.setEllipsize(TextUtils.TruncateAt.END);
    }

    private void createOverlay() {
        if (rootRow != null) return;

        // Outer Horizontal Row holding 3 separate Cards: [Weather] + [Coffee] + [Gold]
        rootRow = new LinearLayout(this);
        rootRow.setOrientation(LinearLayout.HORIZONTAL);
        rootRow.setGravity(Gravity.CENTER_VERTICAL);

        int padH = dp(15);
        int padV = dp(12);

        // ================= CARD 1 (LEFT): WEATHER (DI LINH - LÂM ĐỒNG) =================
        LinearLayout weatherCard = new LinearLayout(this);
        weatherCard.setOrientation(LinearLayout.VERTICAL);
        weatherCard.setPadding(padH, padV, padH, padV);
        weatherCard.setBackground(makeGlassBg());
        LinearLayout.LayoutParams lpWeather = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.MATCH_PARENT, 0.90f);
        lpWeather.rightMargin = dp(10);
        weatherCard.setLayoutParams(lpWeather);

        tvWeatherHeader = new TextView(this);
        tvWeatherHeader.setText("🌦️ THỜI TIẾT DI LINH - LÂM ĐỒNG");
        tvWeatherHeader.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        tvWeatherHeader.setTextColor(Color.parseColor("#4FC3F7"));
        tvWeatherHeader.setTypeface(Typeface.DEFAULT_BOLD);
        tvWeatherHeader.setLetterSpacing(0.03f);
        setSingleLine(tvWeatherHeader);
        weatherCard.addView(tvWeatherHeader);

        tvWeatherMain = new TextView(this);
        tvWeatherMain.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f);
        tvWeatherMain.setTextColor(Color.WHITE);
        setSingleLine(tvWeatherMain);
        LinearLayout.LayoutParams lpWM = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpWM.topMargin = dp(6);
        tvWeatherMain.setLayoutParams(lpWM);
        weatherCard.addView(tvWeatherMain);

        tvWeatherRow2 = new TextView(this);
        tvWeatherRow2.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        tvWeatherRow2.setTextColor(Color.parseColor("#E6FFFFFF"));
        setSingleLine(tvWeatherRow2);
        LinearLayout.LayoutParams lpWR2 = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpWR2.topMargin = dp(5);
        tvWeatherRow2.setLayoutParams(lpWR2);
        weatherCard.addView(tvWeatherRow2);

        tvWeatherRow3 = new TextView(this);
        tvWeatherRow3.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        tvWeatherRow3.setTextColor(Color.parseColor("#CCFFFFFF"));
        setSingleLine(tvWeatherRow3);
        LinearLayout.LayoutParams lpWR3 = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpWR3.topMargin = dp(4);
        tvWeatherRow3.setLayoutParams(lpWR3);
        weatherCard.addView(tvWeatherRow3);

        rootRow.addView(weatherCard);

        // ================= CARD 2 (MIDDLE): COFFEE (TÂY NGUYÊN) =================
        LinearLayout coffeeCard = new LinearLayout(this);
        coffeeCard.setOrientation(LinearLayout.VERTICAL);
        coffeeCard.setPadding(padH, padV, padH, padV);
        coffeeCard.setBackground(makeGlassBg());
        LinearLayout.LayoutParams lpCoffee = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.MATCH_PARENT, 1.00f);
        lpCoffee.rightMargin = dp(10);
        coffeeCard.setLayoutParams(lpCoffee);

        TextView tvCoffeeHeader = new TextView(this);
        tvCoffeeHeader.setText("☕ GIÁ CÀ PHÊ TÂY NGUYÊN");
        tvCoffeeHeader.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        tvCoffeeHeader.setTextColor(Color.parseColor("#69F0AE"));
        tvCoffeeHeader.setTypeface(Typeface.DEFAULT_BOLD);
        tvCoffeeHeader.setLetterSpacing(0.03f);
        setSingleLine(tvCoffeeHeader);
        coffeeCard.addView(tvCoffeeHeader);

        tvCoffeeRow1 = new TextView(this);
        tvCoffeeRow1.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15.5f);
        tvCoffeeRow1.setTextColor(Color.WHITE);
        setSingleLine(tvCoffeeRow1);
        LinearLayout.LayoutParams lpC1 = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpC1.topMargin = dp(6);
        tvCoffeeRow1.setLayoutParams(lpC1);
        coffeeCard.addView(tvCoffeeRow1);

        tvCoffeeRow2 = new TextView(this);
        tvCoffeeRow2.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        tvCoffeeRow2.setTextColor(Color.parseColor("#E6FFFFFF"));
        setSingleLine(tvCoffeeRow2);
        LinearLayout.LayoutParams lpC2 = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpC2.topMargin = dp(5);
        tvCoffeeRow2.setLayoutParams(lpC2);
        coffeeCard.addView(tvCoffeeRow2);

        tvCoffeeRow3 = new TextView(this);
        tvCoffeeRow3.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        tvCoffeeRow3.setTextColor(Color.parseColor("#CCFFFFFF"));
        setSingleLine(tvCoffeeRow3);
        LinearLayout.LayoutParams lpC3 = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpC3.topMargin = dp(4);
        tvCoffeeRow3.setLayoutParams(lpC3);
        coffeeCard.addView(tvCoffeeRow3);

        rootRow.addView(coffeeCard);

        // ================= CARD 3 (RIGHT): GOLD TABLE (THU VÀO - BÁN RA) =================
        LinearLayout goldCard = new LinearLayout(this);
        goldCard.setOrientation(LinearLayout.VERTICAL);
        goldCard.setPadding(padH, padV, padH, padV);
        goldCard.setBackground(makeGlassBg());
        LinearLayout.LayoutParams lpGoldCard = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.MATCH_PARENT, 1.10f);
        goldCard.setLayoutParams(lpGoldCard);

        TableLayout colGold = new TableLayout(this);
        colGold.setColumnStretchable(0, true);
        LinearLayout.LayoutParams lpTable = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        colGold.setLayoutParams(lpTable);

        TableRow headerRow = new TableRow(this);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView hType = makeCell("🪙 GIÁ VÀNG (tr/chỉ)", 12f, Color.parseColor("#FFD54F"), true, Gravity.LEFT);
        hType.setLetterSpacing(0.02f);
        TextView hBuy = makeCell("THU VÀO", 11f, Color.parseColor("#B3FFFFFF"), true, Gravity.RIGHT);
        hBuy.setPadding(dp(8), 0, 0, 0);
        TextView hSell = makeCell("BÁN RA", 11f, Color.parseColor("#B3FFFFFF"), true, Gravity.RIGHT);
        hSell.setPadding(dp(10), 0, dp(2), 0);
        headerRow.addView(hType);
        headerRow.addView(hBuy);
        headerRow.addView(hSell);
        colGold.addView(headerRow);

        TableRow sjcRow = new TableRow(this);
        sjcRow.setGravity(Gravity.CENTER_VERTICAL);
        sjcRow.setPadding(0, dp(6), 0, 0);
        TextView lblSjc = makeCell("Vàng SJC", 14.5f, Color.WHITE, true, Gravity.LEFT);
        tvSjcBuy = makeCell("", 15f, Color.parseColor("#69F0AE"), true, Gravity.RIGHT);
        tvSjcBuy.setPadding(dp(8), 0, 0, 0);
        tvSjcSell = makeCell("", 15f, Color.parseColor("#FFD54F"), true, Gravity.RIGHT);
        tvSjcSell.setPadding(dp(10), 0, dp(2), 0);
        sjcRow.addView(lblSjc);
        sjcRow.addView(tvSjcBuy);
        sjcRow.addView(tvSjcSell);
        colGold.addView(sjcRow);

        TableRow ringRow = new TableRow(this);
        ringRow.setGravity(Gravity.CENTER_VERTICAL);
        ringRow.setPadding(0, dp(4), 0, 0);
        TextView lblRing = makeCell("Nhẫn 9999", 14.5f, Color.WHITE, true, Gravity.LEFT);
        tvRingBuy = makeCell("", 15f, Color.parseColor("#69F0AE"), true, Gravity.RIGHT);
        tvRingBuy.setPadding(dp(8), 0, 0, 0);
        tvRingSell = makeCell("", 15f, Color.parseColor("#FFD54F"), true, Gravity.RIGHT);
        tvRingSell.setPadding(dp(10), 0, dp(2), 0);
        ringRow.addView(lblRing);
        ringRow.addView(tvRingBuy);
        ringRow.addView(tvRingSell);
        colGold.addView(ringRow);

        TableRow worldRow = new TableRow(this);
        worldRow.setGravity(Gravity.CENTER_VERTICAL);
        worldRow.setPadding(0, dp(4), 0, 0);
        tvWorldGold = makeCell("", 12f, Color.parseColor("#CCFFFFFF"), false, Gravity.LEFT);
        TableRow.LayoutParams span3 = new TableRow.LayoutParams();
        span3.span = 3;
        tvWorldGold.setLayoutParams(span3);
        worldRow.addView(tvWorldGold);
        colGold.addView(worldRow);

        goldCard.addView(colGold);
        rootRow.addView(goldCard);

        params = new WindowManager.LayoutParams(
                dp(848),
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );

        configureGravity(params);

        try {
            wm.addView(rootRow, params);
        } catch (Exception ignored) {
        }
    }

    private TextView makeCell(String text, float spSize, int color, boolean bold, int gravity) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, spSize);
        tv.setTextColor(color);
        tv.setGravity(gravity);
        setSingleLine(tv);
        if (bold) {
            tv.setTypeface(Typeface.DEFAULT_BOLD);
        }
        return tv;
    }

    private void configureGravity(WindowManager.LayoutParams p) {
        p.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        p.x = 0;
        p.y = dp(112);
    }

    private void setWidgetVisible(boolean visible) {
        if (rootRow == null) return;
        int target = visible ? View.VISIBLE : View.GONE;
        if (rootRow.getVisibility() != target) {
            rootRow.setVisibility(target);
        }
    }

    /**
     * Inspects live screen state, lock/clock screensaver state, PopupWindow (e.g. "Nguồn vào" HDMI list),
     * and app grid scroll / row focus so the widget ONLY shows when the user is on the top un-scrolled row
     * of CleanYourTV and no popup/lockscreen is open.
     */
    private void evaluateScreenAndScroll() {
        if (rootRow == null || wm == null) return;

        SharedPreferences sp = getSharedPreferences("market_prefs", MODE_PRIVATE);
        if (!sp.getBoolean("widget_enabled", true) || !isScreenAwake) {
            setWidgetVisible(false);
            return;
        }

        // 1. Check PowerManager interactive state & Keyguard lock state
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (pm != null && !pm.isInteractive()) {
            setWidgetVisible(false);
            return;
        }
        KeyguardManager km = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        if (km != null && km.isKeyguardLocked()) {
            setWidgetVisible(false);
            return;
        }

        // 2. Check interactive windows for any popup/dialog (such as "Nguồn vào" HDMI 1..4 PopupWindow)
        try {
            List<AccessibilityWindowInfo> windows = getWindows();
            if (windows != null && !windows.isEmpty()) {
                int tvHomeWindowCount = 0;
                for (AccessibilityWindowInfo w : windows) {
                    if (w.getType() == AccessibilityWindowInfo.TYPE_APPLICATION) {
                        AccessibilityNodeInfo wRoot = w.getRoot();
                        if (wRoot != null) {
                            CharSequence wPkg = wRoot.getPackageName();
                            if (wPkg != null && "com.gathin.tvhome".equals(wPkg.toString())) {
                                tvHomeWindowCount++;
                            } else if (wPkg != null && !"com.antigravity.tvmarket".equals(wPkg.toString())) {
                                // Another app is covering the screen
                                setWidgetVisible(false);
                                return;
                            }
                        }
                    }
                }
                // If CleanYourTV has more than 1 application sub-window open (e.g. "Nguồn vào" HDMI PopupWindow or Dialog), hide!
                if (tvHomeWindowCount > 1) {
                    setWidgetVisible(false);
                    return;
                }
            }
        } catch (Exception ignored) {
        }

        // 3. Inspect active window root
        AccessibilityNodeInfo root = null;
        try {
            root = getRootInActiveWindow();
        } catch (Exception ignored) {
        }
        if (root == null) {
            setWidgetVisible(false);
            return;
        }

        CharSequence rootPkg = root.getPackageName();
        if (rootPkg == null || !"com.gathin.tvhome".equals(rootPkg.toString())) {
            setWidgetVisible(false);
            return;
        }

        // 4. Find CleanYourTV's app grid (com.gathin.tvhome:id/grid).
        // When TV goes to Clock lock screen (ClockActivity / ClockDream) or when "Nguồn vào" popup is active,
        // the active window does NOT contain com.gathin.tvhome:id/grid!
        AccessibilityNodeInfo gridNode = null;
        List<AccessibilityNodeInfo> grids = root.findAccessibilityNodeInfosByViewId("com.gathin.tvhome:id/grid");
        if (grids != null && !grids.isEmpty()) {
            gridNode = grids.get(0);
        } else {
            gridNode = findRecyclerViewFallback(root);
        }

        if (gridNode == null || gridNode.getChildCount() == 0) {
            // Lock screen (ClockActivity / ClockDream) or PopupWindow ("Nguồn vào") is active -> Hide!
            setWidgetVisible(false);
            return;
        }

        // 5. Check Row 1 position and focused row inside grid:
        // In CleanYourTV, Row 1 (Dock: YouTube, Beecube, Timers & Clock, Settings) is at the bottom (top >= dp(325))
        // when un-scrolled. When pressing Down to Row 2+, Row 1 scrolls up into the middle of the screen!
        AccessibilityNodeInfo firstChild = gridNode.getChild(0);
        if (firstChild == null) {
            setWidgetVisible(false);
            return;
        }

        Rect firstBounds = new Rect();
        firstChild.getBoundsInScreen(firstBounds);

        // Check if any tile in Row 2+ (index >= 4) has focus
        int childCount = gridNode.getChildCount();
        for (int i = 4; i < childCount; i++) {
            AccessibilityNodeInfo child = gridNode.getChild(i);
            if (child != null && hasFocusRecursive(child)) {
                setWidgetVisible(false);
                return;
            }
        }

        // Un-scrolled Row 1 top on 1080p is ~707px (dp(353)).
        // If Row 1 has scrolled up above dp(325), hide immediately so it never overlaps the app cards!
        int unScrolledTopThreshold = dp(325);
        if (firstBounds.top < unScrolledTopThreshold) {
            setWidgetVisible(false);
            return;
        }

        setWidgetVisible(true);
    }

    private AccessibilityNodeInfo findRecyclerViewFallback(AccessibilityNodeInfo node) {
        if (node == null) return null;
        CharSequence cls = node.getClassName();
        if (cls != null && cls.toString().contains("RecyclerView")) {
            return node;
        }
        int count = node.getChildCount();
        for (int i = 0; i < count; i++) {
            AccessibilityNodeInfo found = findRecyclerViewFallback(node.getChild(i));
            if (found != null) return found;
        }
        return null;
    }

    private boolean hasFocusRecursive(AccessibilityNodeInfo node) {
        if (node == null) return false;
        if (node.isFocused() || node.isAccessibilityFocused()) return true;
        int count = node.getChildCount();
        for (int i = 0; i < count; i++) {
            if (hasFocusRecursive(node.getChild(i))) return true;
        }
        return false;
    }

    public void updateTextFromCache() {
        if (tvCoffeeRow1 == null) return;
        MarketFetcher.MarketData d = MarketFetcher.loadCached(this);

        // ================= CARD 1: WEATHER =================
        tvWeatherHeader.setText(d.weatherIcon + " THỜI TIẾT DI LINH - LÂM ĐỒNG");

        SpannableStringBuilder w1 = new SpannableStringBuilder();
        appendColored(w1, d.tempNow + " ", Color.parseColor("#FFD54F"), true);
        appendColored(w1, "· " + d.weatherDesc, Color.WHITE, true);
        tvWeatherMain.setText(w1);

        SpannableStringBuilder w2 = new SpannableStringBuilder();
        appendColored(w2, "Cảm giác: ", Color.parseColor("#B3FFFFFF"), false);
        appendColored(w2, d.feelsLike, Color.parseColor("#FFE082"), true);
        appendColored(w2, "  ·  Độ ẩm: ", Color.parseColor("#B3FFFFFF"), false);
        appendColored(w2, d.humidity, Color.parseColor("#81D4FA"), true);
        tvWeatherRow2.setText(w2);

        SpannableStringBuilder w3 = new SpannableStringBuilder();
        appendColored(w3, "Ngày " + d.tempMax + " / Đêm " + d.tempMin, Color.parseColor("#E6FFFFFF"), true);
        appendColored(w3, "  ·  Mưa: ", Color.parseColor("#B3FFFFFF"), false);
        appendColored(w3, d.rainProb, Color.parseColor("#4FC3F7"), true);
        tvWeatherRow3.setText(w3);

        // ================= CARD 2: COFFEE =================
        SpannableStringBuilder sb1 = new SpannableStringBuilder();
        appendColored(sb1, "Di Linh (LĐ): ", Color.WHITE, true);
        appendColored(sb1, d.coffeeLamDong + " đ/kg", Color.parseColor("#FFD54F"), true);
        if (d.coffeeLamDongDiff != null && !d.coffeeLamDongDiff.isEmpty() && !d.coffeeLamDongDiff.equals("-")) {
            int diffColor = d.coffeeLamDongDiff.startsWith("-") ? Color.parseColor("#FF6E6E") : Color.parseColor("#69F0AE");
            appendColored(sb1, " (" + d.coffeeLamDongDiff + ")", diffColor, true);
        }

        SpannableStringBuilder sb2 = new SpannableStringBuilder();
        appendColored(sb2, "Đắk Lắk: ", Color.parseColor("#B3FFFFFF"), false);
        appendColored(sb2, d.coffeeDakLak, Color.parseColor("#FFE082"), true);
        appendColored(sb2, "  ·  Đắk Nông: ", Color.parseColor("#B3FFFFFF"), false);
        appendColored(sb2, d.coffeeDakNong, Color.parseColor("#FFE082"), true);

        SpannableStringBuilder sb3 = new SpannableStringBuilder();
        appendColored(sb3, "Gia Lai: ", Color.parseColor("#B3FFFFFF"), false);
        appendColored(sb3, d.coffeeGiaLai, Color.parseColor("#FFE082"), true);
        appendColored(sb3, " · London: ", Color.parseColor("#B3FFFFFF"), false);
        appendColored(sb3, "$" + d.robustaLondon + " (" + d.robustaDiff + ")", Color.parseColor("#69F0AE"), true);

        tvCoffeeRow1.setText(sb1);
        tvCoffeeRow2.setText(sb2);
        tvCoffeeRow3.setText(sb3);

        // ================= CARD 3: GOLD =================
        tvSjcBuy.setText(d.sjcBuy + " tr");
        tvSjcSell.setText(d.sjcSell + " tr");
        tvRingBuy.setText(d.ring9999Buy + " tr");
        tvRingSell.setText(d.ring9999Sell + " tr");

        SpannableStringBuilder sbWorld = new SpannableStringBuilder();
        appendColored(sbWorld, "Thế giới: ", Color.parseColor("#B3FFFFFF"), false);
        appendColored(sbWorld, d.worldGold + "/oz", Color.parseColor("#FFE082"), true);
        appendColored(sbWorld, "  ·  Cập nhật: " + d.updatedTime, Color.parseColor("#80FFFFFF"), false);
        tvWorldGold.setText(sbWorld);
    }

    private void appendColored(SpannableStringBuilder sb, String text, int color, boolean bold) {
        int start = sb.length();
        sb.append(text);
        sb.setSpan(new ForegroundColorSpan(color), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (bold) {
            sb.setSpan(new StyleSpan(Typeface.BOLD), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    private void triggerFetchAsync() {
        long now = System.currentTimeMillis();
        if (now - lastFetchTime < 5_000L) return;
        lastFetchTime = now;

        new Thread(() -> {
            MarketFetcher.fetchLive(getApplicationContext());
            handler.post(this::updateTextFromCache);
        }).start();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;
        evaluateScreenAndScroll();
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            if (rootRow != null && rootRow.getVisibility() == View.VISIBLE
                    && System.currentTimeMillis() - lastFetchTime > 10 * 60 * 1000L) {
                triggerFetchAsync();
            }
        }
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(periodicRefresh);
        handler.removeCallbacks(uiStatePoll);
        try {
            unregisterReceiver(updateReceiver);
        } catch (Exception ignored) {
        }
        if (wm != null && rootRow != null) {
            try {
                wm.removeView(rootRow);
            } catch (Exception ignored) {
            }
            rootRow = null;
        }
    }

    private int dp(int v) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()));
    }
}
