package com.antigravity.tvmarket;

import android.content.Context;
import android.content.SharedPreferences;

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

public class MarketFetcher {

    private static final String PREFS = "market_prefs";

    public static class MarketData {
        // Weather (Di Linh, Lam Dong)
        public String tempNow = "24°C";
        public String weatherDesc = "Mưa rào nhẹ";
        public String weatherIcon = "🌦️";
        public String feelsLike = "28°C";
        public String humidity = "80%";
        public String tempMax = "27°";
        public String tempMin = "19°";
        public String rainProb = "85%";
        public String windSpeed = "4 km/h";

        // Coffee
        public String coffeeLamDong = "83.600";
        public String coffeeLamDongDiff = "+100";
        public String coffeeDakLak = "84.200";
        public String coffeeDakLakDiff = "+100";
        public String coffeeGiaLai = "84.000";
        public String coffeeDakNong = "84.500";
        public String pepperPrice = "135.500";
        public String robustaLondon = "3.241";
        public String robustaDiff = "+13";

        // Gold
        public String sjcBuy = "13,80";
        public String sjcSell = "14,10";
        public String ring9999Buy = "13,65";
        public String ring9999Sell = "14,05";
        public String worldGold = "$4,043";
        public String updatedTime = "";
    }

    public static MarketData loadCached(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        MarketData d = new MarketData();
        d.tempNow = sp.getString("tempNow", d.tempNow);
        d.weatherDesc = sp.getString("weatherDesc", d.weatherDesc);
        d.weatherIcon = sp.getString("weatherIcon", d.weatherIcon);
        d.feelsLike = sp.getString("feelsLike", d.feelsLike);
        d.humidity = sp.getString("humidity", d.humidity);
        d.tempMax = sp.getString("tempMax", d.tempMax);
        d.tempMin = sp.getString("tempMin", d.tempMin);
        d.rainProb = sp.getString("rainProb", d.rainProb);
        d.windSpeed = sp.getString("windSpeed", d.windSpeed);

        d.coffeeLamDong = sp.getString("coffeeLamDong", d.coffeeLamDong);
        d.coffeeLamDongDiff = sp.getString("coffeeLamDongDiff", d.coffeeLamDongDiff);
        d.coffeeDakLak = sp.getString("coffeeDakLak", d.coffeeDakLak);
        d.coffeeDakLakDiff = sp.getString("coffeeDakLakDiff", d.coffeeDakLakDiff);
        d.coffeeGiaLai = sp.getString("coffeeGiaLai", d.coffeeGiaLai);
        d.coffeeDakNong = sp.getString("coffeeDakNong", d.coffeeDakNong);
        d.pepperPrice = sp.getString("pepperPrice", d.pepperPrice);
        d.robustaLondon = sp.getString("robustaLondon", d.robustaLondon);
        d.robustaDiff = sp.getString("robustaDiff", d.robustaDiff);
        d.sjcBuy = sp.getString("sjcBuy", d.sjcBuy);
        d.sjcSell = sp.getString("sjcSell", d.sjcSell);
        d.ring9999Buy = sp.getString("ring9999Buy", d.ring9999Buy);
        d.ring9999Sell = sp.getString("ring9999Sell", d.ring9999Sell);
        d.worldGold = sp.getString("worldGold", d.worldGold);
        d.updatedTime = sp.getString("updatedTime", "Hôm nay");
        return d;
    }

    public static void saveCached(Context ctx, MarketData d) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("tempNow", d.tempNow)
                .putString("weatherDesc", d.weatherDesc)
                .putString("weatherIcon", d.weatherIcon)
                .putString("feelsLike", d.feelsLike)
                .putString("humidity", d.humidity)
                .putString("tempMax", d.tempMax)
                .putString("tempMin", d.tempMin)
                .putString("rainProb", d.rainProb)
                .putString("windSpeed", d.windSpeed)
                .putString("coffeeLamDong", d.coffeeLamDong)
                .putString("coffeeLamDongDiff", d.coffeeLamDongDiff)
                .putString("coffeeDakLak", d.coffeeDakLak)
                .putString("coffeeDakLakDiff", d.coffeeDakLakDiff)
                .putString("coffeeGiaLai", d.coffeeGiaLai)
                .putString("coffeeDakNong", d.coffeeDakNong)
                .putString("pepperPrice", d.pepperPrice)
                .putString("robustaLondon", d.robustaLondon)
                .putString("robustaDiff", d.robustaDiff)
                .putString("sjcBuy", d.sjcBuy)
                .putString("sjcSell", d.sjcSell)
                .putString("ring9999Buy", d.ring9999Buy)
                .putString("ring9999Sell", d.ring9999Sell)
                .putString("worldGold", d.worldGold)
                .putString("updatedTime", d.updatedTime)
                .putLong("lastFetchMs", System.currentTimeMillis())
                .apply();
    }

    public static MarketData fetchLive(Context ctx) {
        MarketData d = loadCached(ctx);

        // 1. Fetch Di Linh Weather from Open-Meteo
        try {
            String weatherJson = httpGet("https://api.open-meteo.com/v1/forecast?latitude=11.5815&longitude=108.0764&current=temperature_2m,relative_humidity_2m,apparent_temperature,weather_code,wind_speed_10m&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max&timezone=Asia%2FHo_Chi_Minh&forecast_days=1");
            if (weatherJson != null && !weatherJson.isEmpty()) {
                parseWeatherDiLinh(weatherJson, d);
            }
        } catch (Exception ignored) {
        }

        // 2. Fetch Coffee Prices
        try {
            String htmlCoffee = httpGet("https://chogia.vn/gia-ca-phe/");
            if (htmlCoffee != null && !htmlCoffee.isEmpty()) {
                parseChoGiaCoffee(htmlCoffee, d);
            }
        } catch (Exception ignored) {
        }

        // 3. Fetch Gold Prices
        try {
            String htmlGold = httpGet("https://webgia.com/gia-vang/sjc/");
            if (htmlGold != null && !htmlGold.isEmpty()) {
                parseWebGiaGold(htmlGold, d);
            }
        } catch (Exception ignored) {
        }

        d.updatedTime = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());
        saveCached(ctx, d);
        return d;
    }

    private static void parseWeatherDiLinh(String jsonStr, MarketData d) throws Exception {
        JSONObject root = new JSONObject(jsonStr);
        JSONObject cur = root.optJSONObject("current");
        if (cur != null) {
            int t = (int) Math.round(cur.optDouble("temperature_2m", 24));
            int feel = (int) Math.round(cur.optDouble("apparent_temperature", 27));
            int hum = cur.optInt("relative_humidity_2m", 80);
            int code = cur.optInt("weather_code", 2);
            int wind = (int) Math.round(cur.optDouble("wind_speed_10m", 4));

            d.tempNow = t + "°C";
            d.feelsLike = feel + "°C";
            d.humidity = hum + "%";
            d.windSpeed = wind + " km/h";
            String[] descIcon = wmoToVietnamese(code);
            d.weatherDesc = descIcon[0];
            d.weatherIcon = descIcon[1];
        }
        JSONObject daily = root.optJSONObject("daily");
        if (daily != null) {
            JSONArray maxArr = daily.optJSONArray("temperature_2m_max");
            JSONArray minArr = daily.optJSONArray("temperature_2m_min");
            JSONArray rainArr = daily.optJSONArray("precipitation_probability_max");
            if (maxArr != null && maxArr.length() > 0) {
                d.tempMax = Math.round(maxArr.getDouble(0)) + "°";
            }
            if (minArr != null && minArr.length() > 0) {
                d.tempMin = Math.round(minArr.getDouble(0)) + "°";
            }
            if (rainArr != null && rainArr.length() > 0) {
                d.rainProb = rainArr.getInt(0) + "%";
            }
        }
    }

    private static String[] wmoToVietnamese(int code) {
        switch (code) {
            case 0: return new String[]{"Trời quang đãng", "☀️"};
            case 1: return new String[]{"Ít mây, nắng đẹp", "🌤️"};
            case 2: return new String[]{"Có mây rải rác", "⛅"};
            case 3: return new String[]{"Nhiều mây âm u", "☁️"};
            case 45:
            case 48: return new String[]{"Sương mù nhẹ", "🌫️"};
            case 51:
            case 53:
            case 55: return new String[]{"Mưa phùn nhẹ", "🌦️"};
            case 61: return new String[]{"Mưa nhỏ rải rác", "🌧️"};
            case 63: return new String[]{"Mưa vừa", "🌧️"};
            case 65: return new String[]{"Mưa to", "🌧️"};
            case 80: return new String[]{"Mưa rào nhẹ", "🌦️"};
            case 81: return new String[]{"Mưa rào rải rác", "🌧️"};
            case 82: return new String[]{"Mưa rào lớn", "⛈️"};
            case 95:
            case 96:
            case 99: return new String[]{"Mưa dông sấm sét", "⛈️"};
            default: return new String[]{"Có mây", "⛅"};
        }
    }

    private static void parseChoGiaCoffee(String html, MarketData d) {
        String[] ld = extractChoGiaRow(html, "Lâm Đồng");
        if (ld != null) {
            d.coffeeLamDong = ld[0];
            d.coffeeLamDongDiff = ld[1];
        }
        String[] dl = extractChoGiaRow(html, "Đắk Lắk");
        if (dl != null) {
            d.coffeeDakLak = dl[0];
            d.coffeeDakLakDiff = dl[1];
        }
        String[] gl = extractChoGiaRow(html, "Gia Lai");
        if (gl != null) {
            d.coffeeGiaLai = gl[0];
        }
        String[] dn = extractChoGiaRow(html, "Đắk Nông");
        if (dn != null) {
            d.coffeeDakNong = dn[0];
        }
        String[] pepper = extractChoGiaRow(html, "Hồ tiêu");
        if (pepper != null) {
            d.pepperPrice = pepper[0];
        }
        Pattern pRob = Pattern.compile("<td>\\d{2}/\\d{2}</td>\\s*<td[^>]*>([0-9.,]+)</td>\\s*<td[^>]*>([+\\-0-9.,—\\-]+)</td>");
        Matcher mRob = pRob.matcher(html);
        while (mRob.find()) {
            String price = mRob.group(1).trim();
            String diff = mRob.group(2).trim();
            d.robustaLondon = price;
            if (!diff.equals("—") && !diff.equals("-")) {
                d.robustaDiff = diff;
            }
            if (!diff.equals("—")) break;
        }
    }

    private static String[] extractChoGiaRow(String html, String label) {
        Pattern p = Pattern.compile(Pattern.quote(label) + "</a></td>\\s*<td[^>]*>([0-9.,]+)</td>\\s*<td[^>]*>([^<]+)</td>", Pattern.DOTALL);
        Matcher m = p.matcher(html);
        if (m.find()) {
            return new String[]{m.group(1).trim(), m.group(2).trim()};
        }
        return null;
    }

    private static void parseWebGiaGold(String html, MarketData d) {
        Pattern pSjc = Pattern.compile("Vàng SJC 1L, 10L, 1KG</td>\\s*<td[^>]*>([0-9.,]+)</td>\\s*<td[^>]*>([0-9.,]+)</td>");
        Matcher mSjc = pSjc.matcher(html);
        if (mSjc.find()) {
            d.sjcBuy = formatGoldPerChi(mSjc.group(1).trim());
            d.sjcSell = formatGoldPerChi(mSjc.group(2).trim());
        }

        Pattern pRing = Pattern.compile("Vàng nhẫn SJC 99,99% 1 chỉ, 2 chỉ, 5 chỉ</td>\\s*<td[^>]*>([0-9.,]+)</td>\\s*<td[^>]*>([0-9.,]+)</td>");
        Matcher mRing = pRing.matcher(html);
        if (mRing.find()) {
            d.ring9999Buy = formatGoldPerChi(mRing.group(1).trim());
            d.ring9999Sell = formatGoldPerChi(mRing.group(2).trim());
        }

        Pattern pWorld = Pattern.compile("\\$([0-9],?[0-9]{3})(?:\\.[0-9]+)?");
        Matcher mWorld = pWorld.matcher(html);
        if (mWorld.find()) {
            d.worldGold = "$" + mWorld.group(1);
        }
    }

    private static String formatGoldPerChi(String raw) {
        String digits = raw.replaceAll("[^0-9]", "");
        if (digits.length() >= 7) {
            try {
                double val = Double.parseDouble(digits) / 1_000_000.0;
                return String.format(Locale.FRANCE, "%.2f", val);
            } catch (Exception ignored) {
            }
        }
        return raw;
    }

    private static String httpGet(String urlStr) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(6000);
        conn.setReadTimeout(6000);
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
        try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append('\n');
            }
            return sb.toString();
        } finally {
            conn.disconnect();
        }
    }
}
