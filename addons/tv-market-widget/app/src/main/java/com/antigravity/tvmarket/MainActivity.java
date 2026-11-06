package com.antigravity.tvmarket;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView tvCoffeeMain;
    private TextView tvCoffeeDetails;
    private TextView tvGoldMain;
    private TextView tvGoldDetails;
    private TextView tvStatus;
    private TextView btnPosition;
    private TextView btnToggle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#FF0B0F17"));
        int padH = dp(56);
        int padV = dp(36);
        root.setPadding(padH, padV, padH, padV);

        // Header
        TextView title = new TextView(this);
        title.setText("☕ GIÁ CÀ PHÊ LÂM ĐỒNG & 🪙 GIÁ VÀNG HÔM NAY");
        title.setTextColor(Color.parseColor("#FFD54F"));
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);

        tvStatus = new TextView(this);
        tvStatus.setTextColor(Color.parseColor("#99FFFFFF"));
        tvStatus.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        LinearLayout.LayoutParams lpSub = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpSub.topMargin = dp(6);
        lpSub.bottomMargin = dp(24);
        tvStatus.setLayoutParams(lpSub);
        root.addView(tvStatus);

        // 2-column cards row
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams lpRow = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        row.setLayoutParams(lpRow);

        // Coffee Card
        LinearLayout coffeeCard = createCard();
        LinearLayout.LayoutParams lpLeft = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
        lpLeft.rightMargin = dp(16);
        coffeeCard.setLayoutParams(lpLeft);

        TextView coffeeHeader = new TextView(this);
        coffeeHeader.setText("☕ GIÁ CÀ PHÊ TÂY NGUYÊN & THẾ GIỚI");
        coffeeHeader.setTextColor(Color.parseColor("#69F0AE"));
        coffeeHeader.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
        coffeeHeader.setTypeface(Typeface.DEFAULT_BOLD);
        coffeeCard.addView(coffeeHeader);

        tvCoffeeMain = new TextView(this);
        tvCoffeeMain.setTextColor(Color.WHITE);
        tvCoffeeMain.setTextSize(TypedValue.COMPLEX_UNIT_SP, 30f);
        tvCoffeeMain.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams lpMain = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpMain.topMargin = dp(12);
        lpMain.bottomMargin = dp(14);
        tvCoffeeMain.setLayoutParams(lpMain);
        coffeeCard.addView(tvCoffeeMain);

        tvCoffeeDetails = new TextView(this);
        tvCoffeeDetails.setTextColor(Color.parseColor("#D9FFFFFF"));
        tvCoffeeDetails.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
        tvCoffeeDetails.setLineSpacing(dp(6), 1f);
        coffeeCard.addView(tvCoffeeDetails);

        // Gold Card
        LinearLayout goldCard = createCard();
        LinearLayout.LayoutParams lpRight = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
        lpRight.leftMargin = dp(16);
        goldCard.setLayoutParams(lpRight);

        TextView goldHeader = new TextView(this);
        goldHeader.setText("🪙 GIÁ VÀNG SJC & NHẪN 9999");
        goldHeader.setTextColor(Color.parseColor("#FFD54F"));
        goldHeader.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
        goldHeader.setTypeface(Typeface.DEFAULT_BOLD);
        goldCard.addView(goldHeader);

        tvGoldMain = new TextView(this);
        tvGoldMain.setTextColor(Color.WHITE);
        tvGoldMain.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f);
        tvGoldMain.setTypeface(Typeface.DEFAULT_BOLD);
        tvGoldMain.setLayoutParams(lpMain);
        goldCard.addView(tvGoldMain);

        tvGoldDetails = new TextView(this);
        tvGoldDetails.setTextColor(Color.parseColor("#D9FFFFFF"));
        tvGoldDetails.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
        tvGoldDetails.setLineSpacing(dp(6), 1f);
        goldCard.addView(tvGoldDetails);

        row.addView(coffeeCard);
        row.addView(goldCard);
        root.addView(row);

        // Bottom Controls
        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams lpCtrl = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpCtrl.topMargin = dp(24);
        controls.setLayoutParams(lpCtrl);

        TextView btnRefresh = createButton("🔄 Làm mới ngay");
        btnRefresh.setOnClickListener(v -> refreshNow());

        btnPosition = createButton("");
        btnPosition.setOnClickListener(v -> cyclePosition());

        btnToggle = createButton("");
        btnToggle.setOnClickListener(v -> toggleWidget());

        TextView btnClose = createButton("🏠 Về màn hình chính");
        btnClose.setOnClickListener(v -> finish());

        controls.addView(btnRefresh);
        controls.addView(btnPosition);
        controls.addView(btnToggle);
        controls.addView(btnClose);
        root.addView(controls);

        setContentView(root);
        btnRefresh.requestFocus();

        renderData();
        refreshNow();
    }

    private LinearLayout createCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        int p = dp(24);
        card.setPadding(p, p, p, p);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#18FFFFFF"));
        bg.setCornerRadius(dp(20));
        bg.setStroke(dp(1), Color.parseColor("#33FFFFFF"));
        card.setBackground(bg);
        return card;
    }

    private TextView createButton(String label) {
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setPadding(dp(20), dp(12), dp(20), dp(12));
        tv.setFocusable(true);
        tv.setClickable(true);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = dp(16);
        tv.setLayoutParams(lp);

        styleButton(tv, false);
        tv.setOnFocusChangeListener((v, hasFocus) -> styleButton((TextView) v, hasFocus));
        return tv;
    }

    private void styleButton(TextView tv, boolean focused) {
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(14));
        if (focused) {
            bg.setColor(Color.parseColor("#FFD54F"));
            tv.setTextColor(Color.parseColor("#0B0F17"));
        } else {
            bg.setColor(Color.parseColor("#26FFFFFF"));
            bg.setStroke(dp(1), Color.parseColor("#40FFFFFF"));
            tv.setTextColor(Color.WHITE);
        }
        tv.setBackground(bg);
    }

    private void renderData() {
        MarketFetcher.MarketData d = MarketFetcher.loadCached(this);
        tvStatus.setText("Cập nhật lần cuối: " + d.updatedTime + "  ·  Nguồn: Chợ Giá & WebGiá (Tự động cập nhật mỗi 15 phút)");

        tvCoffeeMain.setText("Di Linh / Lâm Đồng: " + d.coffeeLamDong + " đ/kg (" + d.coffeeLamDongDiff + ")");
        tvCoffeeDetails.setText(
                "• Đắk Lắk: " + d.coffeeDakLak + " đ/kg (" + d.coffeeDakLakDiff + ")\n" +
                "• Đắk Nông: " + d.coffeeDakNong + " đ/kg\n" +
                "• Gia Lai: " + d.coffeeGiaLai + " đ/kg\n" +
                "• Hồ tiêu Tây Nguyên: " + d.pepperPrice + " đ/kg\n" +
                "• Sàn Robusta London: $" + d.robustaLondon + " / tấn (" + d.robustaDiff + ")"
        );

        tvGoldMain.setText("SJC: " + d.sjcBuy + " - " + d.sjcSell + " triệu/chỉ");
        tvGoldDetails.setText(
                "• Vàng SJC (Mua - Bán): " + d.sjcBuy + " - " + d.sjcSell + " tr/chỉ\n" +
                "• Vàng Nhẫn SJC 9999: " + d.ring9999Buy + " - " + d.ring9999Sell + " tr/chỉ\n" +
                "• Giá Vàng Thế Giới (Kitco): " + d.worldGold + " / oz"
        );

        SharedPreferences sp = getSharedPreferences("market_prefs", MODE_PRIVATE);
        int pos = sp.getInt("widget_pos", 0);
        String posLabel = pos == 0 ? "Góc phải trên" : (pos == 1 ? "Góc trái (dưới đồng hồ)" : "Chính giữa trên");
        btnPosition.setText("📍 Vị trí: " + posLabel);

        boolean enabled = sp.getBoolean("widget_enabled", true);
        btnToggle.setText(enabled ? "👁️ Widget: Đang BẬT" : "👁️ Widget: Đang TẮT");
    }

    private void cyclePosition() {
        SharedPreferences sp = getSharedPreferences("market_prefs", MODE_PRIVATE);
        int next = (sp.getInt("widget_pos", 0) + 1) % 3;
        sp.edit().putInt("widget_pos", next).apply();
        renderData();
        notifyWidget(false);
    }

    private void toggleWidget() {
        SharedPreferences sp = getSharedPreferences("market_prefs", MODE_PRIVATE);
        boolean next = !sp.getBoolean("widget_enabled", true);
        sp.edit().putBoolean("widget_enabled", next).apply();
        renderData();
        notifyWidget(false);
    }

    private void refreshNow() {
        tvStatus.setText("Đang tải dữ liệu Giá Vàng & Giá Cà Phê mới nhất...");
        new Thread(() -> {
            MarketFetcher.fetchLive(getApplicationContext());
            handler.post(() -> {
                renderData();
                notifyWidget(false);
            });
        }).start();
    }

    private void notifyWidget(boolean fetchNow) {
        Intent i = new Intent(MarketWidgetService.ACTION_UPDATE_WIDGET);
        i.putExtra("fetch_now", fetchNow);
        sendBroadcast(i);
    }

    private int dp(int v) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()));
    }
}

