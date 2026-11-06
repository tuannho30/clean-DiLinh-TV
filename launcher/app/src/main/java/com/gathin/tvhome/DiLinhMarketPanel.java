package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Native 3-card dashboard panel for Di Linh, Lâm Đồng:
 *   Card 1 (Left):   🌦️ THỜI TIẾT DI LINH - LÂM ĐỒNG (Open-Meteo 11.5815, 108.0764)
 *   Card 2 (Middle): ☕ GIÁ CÀ PHÊ TÂY NGUYÊN (Di Linh/Lâm Đồng, Đắk Lắk, Đắk Nông, Gia Lai, London)
 *   Card 3 (Right):  🪙 GIÁ VÀNG (tr/chỉ) — THU VÀO & BÁN RA (Vàng SJC, Nhẫn 9999, Thế giới)
 *
 * Anchored to the top un-scrolled row of CleanYourTV; rides up and fades out smoothly when
 * the app grid scrolls down.
 */
public final class DiLinhMarketPanel {

    private final Context ctx;
    private final LinearLayout rootRow;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private TextView tvWeatherHeader;
    private TextView tvWeatherMain;
    private TextView tvWeatherRow2;
    private TextView tvWeatherRow3;

    private TextView tvCoffeeRow1;
    private TextView tvCoffeeRow2;
    private TextView tvCoffeeRow3;

    private TextView tvSjcBuy;
    private TextView tvSjcSell;
    private TextView tvRingBuy;
    private TextView tvRingSell;
    private TextView tvWorldGold;

    private long lastFetchMs = 0;

    private final Runnable periodicRefresh = new Runnable() {
        @Override
        public void run() {
            fetchAsync();
            handler.postDelayed(this, 15 * 60 * 1000L);
        }
    };

    public static DiLinhMarketPanel attach(MainActivity activity, FrameLayout root, RecyclerView grid) {
        DiLinhMarketPanel panel = new DiLinhMarketPanel(activity, root, grid);
        panel.updateFromCache();
        panel.fetchAsync();
        return panel;
    }

    private DiLinhMarketPanel(MainActivity activity, FrameLayout root, RecyclerView grid) {
        this.ctx = activity;
        this.rootRow = buildThreeCardRow();

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                dp(848), FrameLayout.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        lp.topMargin = dp(112);
        rootRow.setLayoutParams(lp);
        rootRow.setFocusable(false);
        rootRow.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);

        // Insert right before the floating glass highlight so popups/menus always render above it.
        int glassIdx = root.indexOfChild(root.findViewById(R.id.glass));
        if (glassIdx >= 0) {
            root.addView(rootRow, glassIdx);
        } else {
            root.addView(rootRow);
        }

        // Scroll & fade with the app grid so Row 1 never collides with the 3 cards when scrolling down.
        grid.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                syncScroll(rv);
            }
        });

        handler.postDelayed(periodicRefresh, 15 * 60 * 1000L);
    }

    public void syncScroll(RecyclerView rv) {
        if (isStandaloneOverlayEnabled()) {
            rootRow.setVisibility(View.GONE);
            return;
        }
        int shift = rv.canScrollVertically(-1) ? rv.computeVerticalScrollOffset() : 0;
        rootRow.setTranslationY(-shift);
        float alpha = Math.max(0f, 1f - shift / (float) dp(110));
        rootRow.setAlpha(alpha);
        rootRow.setVisibility(alpha <= 0.02f ? View.GONE : View.VISIBLE);
    }

    public void onResume(RecyclerView rv) {
        syncScroll(rv);
        if (System.currentTimeMillis() - lastFetchMs > 10 * 60 * 1000L) {
            fetchAsync();
        }
    }

    public void onDestroy() {
        handler.removeCallbacks(periodicRefresh);
    }

    /**
     * Avoids drawing twice if the standalone TV Market Widget accessibility overlay is already active.
     */
    private boolean isStandaloneOverlayEnabled() {
        try {
            String enabled = Settings.Secure.getString(
                    ctx.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            return enabled != null && enabled.contains("com.antigravity.tvmarket");
        } catch (Exception ignored) {
            return false;
        }
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

    private LinearLayout buildThreeCardRow() {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        int padH = dp(15);
        int padV = dp(12);

        // ================= CARD 1 (LEFT): WEATHER (DI LINH - LÂM ĐỒNG) =================
        LinearLayout weatherCard = new LinearLayout(ctx);
        weatherCard.setOrientation(LinearLayout.VERTICAL);
        weatherCard.setPadding(padH, padV, padH, padV);
        weatherCard.setBackground(makeGlassBg());
        LinearLayout.LayoutParams lpWeather = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.MATCH_PARENT, 0.90f);
        lpWeather.rightMargin = dp(10);
        weatherCard.setLayoutParams(lpWeather);

        tvWeatherHeader = new TextView(ctx);
        tvWeatherHeader.setText("🌦️ THỜI TIẾT DI LINH - LÂM ĐỒNG");
        tvWeatherHeader.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        tvWeatherHeader.setTextColor(Color.parseColor("#4FC3F7"));
        tvWeatherHeader.setTypeface(Typeface.DEFAULT_BOLD);
        tvWeatherHeader.setLetterSpacing(0.03f);
        setSingleLine(tvWeatherHeader);
        weatherCard.addView(tvWeatherHeader);

        tvWeatherMain = new TextView(ctx);
        tvWeatherMain.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f);
        tvWeatherMain.setTextColor(Color.WHITE);
        setSingleLine(tvWeatherMain);
        LinearLayout.LayoutParams lpWM = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpWM.topMargin = dp(6);
        tvWeatherMain.setLayoutParams(lpWM);
        weatherCard.addView(tvWeatherMain);

        tvWeatherRow2 = new TextView(ctx);
        tvWeatherRow2.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        tvWeatherRow2.setTextColor(Color.parseColor("#E6FFFFFF"));
        setSingleLine(tvWeatherRow2);
        LinearLayout.LayoutParams lpWR2 = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpWR2.topMargin = dp(5);
        tvWeatherRow2.setLayoutParams(lpWR2);
        weatherCard.addView(tvWeatherRow2);

        tvWeatherRow3 = new TextView(ctx);
        tvWeatherRow3.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        tvWeatherRow3.setTextColor(Color.parseColor("#CCFFFFFF"));
        setSingleLine(tvWeatherRow3);
        LinearLayout.LayoutParams lpWR3 = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpWR3.topMargin = dp(4);
        tvWeatherRow3.setLayoutParams(lpWR3);
        weatherCard.addView(tvWeatherRow3);

        row.addView(weatherCard);

        // ================= CARD 2 (MIDDLE): COFFEE (TÂY NGUYÊN) =================
        LinearLayout coffeeCard = new LinearLayout(ctx);
        coffeeCard.setOrientation(LinearLayout.VERTICAL);
        coffeeCard.setPadding(padH, padV, padH, padV);
        coffeeCard.setBackground(makeGlassBg());
        LinearLayout.LayoutParams lpCoffee = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.MATCH_PARENT, 1.00f);
        lpCoffee.rightMargin = dp(10);
        coffeeCard.setLayoutParams(lpCoffee);

        TextView tvCoffeeHeader = new TextView(ctx);
        tvCoffeeHeader.setText("☕ GIÁ CÀ PHÊ TÂY NGUYÊN");
        tvCoffeeHeader.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        tvCoffeeHeader.setTextColor(Color.parseColor("#69F0AE"));
        tvCoffeeHeader.setTypeface(Typeface.DEFAULT_BOLD);
        tvCoffeeHeader.setLetterSpacing(0.03f);
        setSingleLine(tvCoffeeHeader);
        coffeeCard.addView(tvCoffeeHeader);

        tvCoffeeRow1 = new TextView(ctx);
        tvCoffeeRow1.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15.5f);
        tvCoffeeRow1.setTextColor(Color.WHITE);
        setSingleLine(tvCoffeeRow1);
        LinearLayout.LayoutParams lpC1 = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpC1.topMargin = dp(6);
        tvCoffeeRow1.setLayoutParams(lpC1);
        coffeeCard.addView(tvCoffeeRow1);

        tvCoffeeRow2 = new TextView(ctx);
        tvCoffeeRow2.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        tvCoffeeRow2.setTextColor(Color.parseColor("#E6FFFFFF"));
        setSingleLine(tvCoffeeRow2);
        LinearLayout.LayoutParams lpC2 = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpC2.topMargin = dp(5);
        tvCoffeeRow2.setLayoutParams(lpC2);
        coffeeCard.addView(tvCoffeeRow2);

        tvCoffeeRow3 = new TextView(ctx);
        tvCoffeeRow3.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        tvCoffeeRow3.setTextColor(Color.parseColor("#CCFFFFFF"));
        setSingleLine(tvCoffeeRow3);
        LinearLayout.LayoutParams lpC3 = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpC3.topMargin = dp(4);
        tvCoffeeRow3.setLayoutParams(lpC3);
        coffeeCard.addView(tvCoffeeRow3);

        row.addView(coffeeCard);

        // ================= CARD 3 (RIGHT): GOLD TABLE (THU VÀO - BÁN RA) =================
        LinearLayout goldCard = new LinearLayout(ctx);
        goldCard.setOrientation(LinearLayout.VERTICAL);
        goldCard.setPadding(padH, padV, padH, padV);
        goldCard.setBackground(makeGlassBg());
        LinearLayout.LayoutParams lpGoldCard = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.MATCH_PARENT, 1.10f);
        goldCard.setLayoutParams(lpGoldCard);

        TableLayout colGold = new TableLayout(ctx);
        colGold.setColumnStretchable(0, true);
        LinearLayout.LayoutParams lpTable = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        colGold.setLayoutParams(lpTable);

        TableRow headerRow = new TableRow(ctx);
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

        TableRow sjcRow = new TableRow(ctx);
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

        TableRow ringRow = new TableRow(ctx);
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

        TableRow worldRow = new TableRow(ctx);
        worldRow.setGravity(Gravity.CENTER_VERTICAL);
        worldRow.setPadding(0, dp(4), 0, 0);
        tvWorldGold = makeCell("", 12f, Color.parseColor("#CCFFFFFF"), false, Gravity.LEFT);
        TableRow.LayoutParams span3 = new TableRow.LayoutParams();
        span3.span = 3;
        tvWorldGold.setLayoutParams(span3);
        worldRow.addView(tvWorldGold);
        colGold.addView(worldRow);

        goldCard.addView(colGold);
        row.addView(goldCard);
        return row;
    }

    private TextView makeCell(String text, float spSize, int color, boolean bold, int gravity) {
        TextView tv = new TextView(ctx);
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

    private void appendColored(SpannableStringBuilder sb, String text, int color, boolean bold) {
        int start = sb.length();
        sb.append(text);
        sb.setSpan(new ForegroundColorSpan(color), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (bold) {
            sb.setSpan(new StyleSpan(Typeface.BOLD), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    public void updateFromCache() {
        SharedPreferences sp = ctx.getSharedPreferences("dilinh_market_cache", Context.MODE_PRIVATE);
        String weatherIcon = sp.getString("weatherIcon", "🌦️");
        String tempNow = sp.getString("tempNow", "24°C");
        String weatherDesc = sp.getString("weatherDesc", "Mưa rào nhẹ");
        String feelsLike = sp.getString("feelsLike", "28°C");
        String humidity = sp.getString("humidity", "82%");
        String tempMax = sp.getString("tempMax", "27°");
        String tempMin = sp.getString("tempMin", "19°");
        String rainProb = sp.getString("rainProb", "100%");

        String coffeeLamDong = sp.getString("coffeeLamDong", "83.600");
        String coffeeLamDongDiff = sp.getString("coffeeLamDongDiff", "+100");
        String coffeeDakLak = sp.getString("coffeeDakLak", "84.200");
        String coffeeDakNong = sp.getString("coffeeDakNong", "84.500");
        String coffeeGiaLai = sp.getString("coffeeGiaLai", "84.000");
        String robustaLondon = sp.getString("robustaLondon", "3.241");
        String robustaDiff = sp.getString("robustaDiff", "+13");

        String sjcBuy = sp.getString("sjcBuy", "13,80");
        String sjcSell = sp.getString("sjcSell", "14,10");
        String ring9999Buy = sp.getString("ring9999Buy", "13,65");
        String ring9999Sell = sp.getString("ring9999Sell", "14,05");
        String worldGold = sp.getString("worldGold", "$4,046");
        String updatedTime = sp.getString("updatedTime",
                new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date()));

        tvWeatherHeader.setText(weatherIcon + " THỜI TIẾT DI LINH - LÂM ĐỒNG");

        SpannableStringBuilder w1 = new SpannableStringBuilder();
        appendColored(w1, tempNow + " ", Color.parseColor("#FFD54F"), true);
        appendColored(w1, "· " + weatherDesc, Color.WHITE, true);
        tvWeatherMain.setText(w1);

        SpannableStringBuilder w2 = new SpannableStringBuilder();
        appendColored(w2, "Cảm giác: ", Color.parseColor("#B3FFFFFF"), false);
        appendColored(w2, feelsLike, Color.parseColor("#FFE082"), true);
        appendColored(w2, "  ·  Độ ẩm: ", Color.parseColor("#B3FFFFFF"), false);
        appendColored(w2, humidity, Color.parseColor("#81D4FA"), true);
        tvWeatherRow2.setText(w2);

        SpannableStringBuilder w3 = new SpannableStringBuilder();
        appendColored(w3, "Ngày " + tempMax + " / Đêm " + tempMin, Color.parseColor("#E6FFFFFF"), true);
        appendColored(w3, "  ·  Mưa: ", Color.parseColor("#B3FFFFFF"), false);
        appendColored(w3, rainProb, Color.parseColor("#4FC3F7"), true);
        tvWeatherRow3.setText(w3);

        SpannableStringBuilder sb1 = new SpannableStringBuilder();
        appendColored(sb1, "Di Linh (LĐ): ", Color.WHITE, true);
        appendColored(sb1, coffeeLamDong + " đ/kg", Color.parseColor("#FFD54F"), true);
        if (coffeeLamDongDiff != null && !coffeeLamDongDiff.isEmpty() && !coffeeLamDongDiff.equals("-")) {
            int diffColor = coffeeLamDongDiff.startsWith("-") ? Color.parseColor("#FF6E6E") : Color.parseColor("#69F0AE");
            appendColored(sb1, " (" + coffeeLamDongDiff + ")", diffColor, true);
        }

        SpannableStringBuilder sb2 = new SpannableStringBuilder();
        appendColored(sb2, "Đắk Lắk: ", Color.parseColor("#B3FFFFFF"), false);
        appendColored(sb2, coffeeDakLak, Color.parseColor("#FFE082"), true);
        appendColored(sb2, "  ·  Đắk Nông: ", Color.parseColor("#B3FFFFFF"), false);
        appendColored(sb2, coffeeDakNong, Color.parseColor("#FFE082"), true);

        SpannableStringBuilder sb3 = new SpannableStringBuilder();
        appendColored(sb3, "Gia Lai: ", Color.parseColor("#B3FFFFFF"), false);
        appendColored(sb3, coffeeGiaLai, Color.parseColor("#FFE082"), true);
        appendColored(sb3, " · London: ", Color.parseColor("#B3FFFFFF"), false);
        appendColored(sb3, "$" + robustaLondon + " (" + robustaDiff + ")", Color.parseColor("#69F0AE"), true);

        tvCoffeeRow1.setText(sb1);
        tvCoffeeRow2.setText(sb2);
        tvCoffeeRow3.setText(sb3);

        tvSjcBuy.setText(sjcBuy + " tr");
        tvSjcSell.setText(sjcSell + " tr");
        tvRingBuy.setText(ring9999Buy + " tr");
        tvRingSell.setText(ring9999Sell + " tr");

        SpannableStringBuilder sbWorld = new SpannableStringBuilder();
        appendColored(sbWorld, "Thế giới: ", Color.parseColor("#B3FFFFFF"), false);
        appendColored(sbWorld, worldGold + "/oz", Color.parseColor("#FFE082"), true);
        appendColored(sbWorld, "  ·  Cập nhật: " + updatedTime, Color.parseColor("#80FFFFFF"), false);
        tvWorldGold.setText(sbWorld);
    }

    private void fetchAsync() {
        long now = System.currentTimeMillis();
        if (now - lastFetchMs < 5_000L) return;
        lastFetchMs = now;

        new Thread(() -> {
            SharedPreferences sp = ctx.getSharedPreferences("dilinh_market_cache", Context.MODE_PRIVATE);
            SharedPreferences.Editor ed = sp.edit();

            // 1. Live Di Linh Weather (Open-Meteo: 11.5815, 108.0764)
            try {
                String wJson = httpGet("https://api.open-meteo.com/v1/forecast?latitude=11.5815&longitude=108.0764"
                        + "&current=temperature_2m,relative_humidity_2m,apparent_temperature,weather_code"
                        + "&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max&timezone=Asia%2FHo_Chi_Minh");
                if (!wJson.isEmpty()) {
                    JSONObject root = new JSONObject(wJson);
                    JSONObject cur = root.optJSONObject("current");
                    if (cur != null) {
                        ed.putString("tempNow", Math.round(cur.optDouble("temperature_2m", 24)) + "°C");
                        ed.putString("feelsLike", Math.round(cur.optDouble("apparent_temperature", 25)) + "°C");
                        ed.putString("humidity", Math.round(cur.optDouble("relative_humidity_2m", 80)) + "%");
                        int code = cur.optInt("weather_code", 2);
                        ed.putString("weatherDesc", wmoToVietnamese(code));
                        ed.putString("weatherIcon", wmoToEmoji(code));
                    }
                    JSONObject daily = root.optJSONObject("daily");
                    if (daily != null) {
                        JSONArray maxArr = daily.optJSONArray("temperature_2m_max");
                        JSONArray minArr = daily.optJSONArray("temperature_2m_min");
                        JSONArray rainArr = daily.optJSONArray("precipitation_probability_max");
                        if (maxArr != null && maxArr.length() > 0) ed.putString("tempMax", Math.round(maxArr.getDouble(0)) + "°");
                        if (minArr != null && minArr.length() > 0) ed.putString("tempMin", Math.round(minArr.getDouble(0)) + "°");
                        if (rainArr != null && rainArr.length() > 0) ed.putString("rainProb", Math.round(rainArr.getDouble(0)) + "%");
                    }
                }
            } catch (Exception ignored) {
            }

            // 2. Live Coffee prices (chogia.vn)
            try {
                String html = httpGet("https://chogia.vn/gia-ca-phe/");
                if (!html.isEmpty()) {
                    String ld = extractProvincePrice(html, "Lâm Đồng|Di Linh|Bảo Lộc");
                    if (ld != null) ed.putString("coffeeLamDong", ld);
                    String ldDiff = extractProvinceDiff(html, "Lâm Đồng|Di Linh|Bảo Lộc");
                    if (ldDiff != null) ed.putString("coffeeLamDongDiff", ldDiff);
                    String dl = extractProvincePrice(html, "Đắk Lắk|Buôn Hồ|Cư M'gar");
                    if (dl != null) ed.putString("coffeeDakLak", dl);
                    String dn = extractProvincePrice(html, "Đắk Nông|Gia Nghĩa");
                    if (dn != null) ed.putString("coffeeDakNong", dn);
                    String gl = extractProvincePrice(html, "Gia Lai|Pleiku");
                    if (gl != null) ed.putString("coffeeGiaLai", gl);
                }
            } catch (Exception ignored) {
            }

            // 3. Live SJC Gold prices (webgia.com)
            try {
                String goldHtml = httpGet("https://webgia.com/gia-vang/sjc/");
                if (!goldHtml.isEmpty()) {
                    Matcher m = Pattern.compile("(?:Vàng SJC|SJC 1L)[^0-9]{1,120}([0-9]{2,3}[.,][0-9]{2,3})[^0-9]{1,80}([0-9]{2,3}[.,][0-9]{2,3})",
                            Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(goldHtml);
                    if (m.find()) {
                        ed.putString("sjcBuy", normalizeGold(m.group(1)));
                        ed.putString("sjcSell", normalizeGold(m.group(2)));
                    }
                    Matcher mRing = Pattern.compile("(?:Nhẫn SJC|Vàng nhẫn 9999|Nhẫn 99,99)[^0-9]{1,120}([0-9]{2,3}[.,][0-9]{2,3})[^0-9]{1,80}([0-9]{2,3}[.,][0-9]{2,3})",
                            Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(goldHtml);
                    if (mRing.find()) {
                        ed.putString("ring9999Buy", normalizeGold(mRing.group(1)));
                        ed.putString("ring9999Sell", normalizeGold(mRing.group(2)));
                    }
                }
            } catch (Exception ignored) {
            }

            ed.putString("updatedTime", new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date()));
            ed.apply();

            handler.post(this::updateFromCache);
        }).start();
    }

    private static String wmoToVietnamese(int code) {
        switch (code) {
            case 0: return "Trời quang";
            case 1: return "Ít mây";
            case 2: return "Có mây";
            case 3: return "Nhiều mây";
            case 45: case 48: return "Sương mù";
            case 51: case 53: case 55: return "Mưa phùn";
            case 61: return "Mưa nhẹ";
            case 63: return "Mưa vừa";
            case 65: return "Mưa to";
            case 80: return "Mưa rào nhẹ";
            case 81: return "Mưa rào vừa";
            case 82: return "Mưa rào to";
            case 95: case 96: case 99: return "Mưa dông";
            default: return "Có mây";
        }
    }

    private static String wmoToEmoji(int code) {
        if (code == 0 || code == 1) return "☀️";
        if (code == 2 || code == 3) return "⛅";
        if (code == 45 || code == 48) return "🌫️";
        if (code >= 95) return "⛈️";
        if (code >= 51) return "🌦️";
        return "🌤️";
    }

    private static String extractProvincePrice(String html, String regexName) {
        Matcher m = Pattern.compile("(?:" + regexName + ").{1,180}?([0-9]{2,3}\\.[0-9]{3})",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(html);
        if (m.find()) {
            String val = m.group(1);
            int num = Integer.parseInt(val.replace(".", ""));
            if (num >= 50000 && num <= 250000) return val;
        }
        return null;
    }

    private static String extractProvinceDiff(String html, String regexName) {
        Matcher m = Pattern.compile("(?:" + regexName + ").{1,180}?[0-9]{2,3}\\.[0-9]{3}.{1,120}?([+-]?[0-9]{1,2}\\.?[0-9]{2,3})",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(html);
        if (m.find()) {
            String d = m.group(1).trim();
            if (!d.startsWith("+") && !d.startsWith("-")) d = "+" + d;
            return d;
        }
        return null;
    }

    private static String normalizeGold(String raw) {
        try {
            double v = Double.parseDouble(raw.replace(".", "").replace(",", "."));
            while (v > 30.0) v /= 10.0;
            return String.format(Locale.US, "%.2f", v).replace('.', ',');
        } catch (Exception e) {
            return raw;
        }
    }

    private static String httpGet(String urlStr) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(7000);
            conn.setReadTimeout(7000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android TV; CleanYourTV DiLinh)");
            if (conn.getResponseCode() == 200) {
                BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line).append('\n');
                br.close();
                return sb.toString();
            }
        } catch (Exception ignored) {
        } finally {
            if (conn != null) conn.disconnect();
        }
        return "";
    }

    private int dp(int v) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, v, ctx.getResources().getDisplayMetrics()));
    }
}
