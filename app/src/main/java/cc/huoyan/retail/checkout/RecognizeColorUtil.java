package cc.huoyan.retail.checkout;

import android.graphics.Color;
import android.text.TextUtils;


import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class RecognizeColorUtil {
    public static final int DEFAULT_COLOR = Color.parseColor("#4361EE");
    public static final int UNSELECTED_LEADING_COLOR = Color.rgb(183, 190, 202);
    private static final int[] COLOR_PALETTE = {
            Color.parseColor("#4361EE"),
            Color.parseColor("#4CC9F0"),
            Color.parseColor("#7209B7"),
            Color.parseColor("#F72585"),
            Color.parseColor("#3A0CA3"),
            Color.parseColor("#00A6A6"),
            Color.parseColor("#4895EF"),
            Color.parseColor("#B5179E"),
            Color.parseColor("#577590"),
            Color.parseColor("#9B5DE5")
    };

    private RecognizeColorUtil() {
    }

    public static Map<String, Integer> buildGoodsColorMap(List<GoodsVo> goodsVos, boolean shuffle) {
        Map<String, Integer> colorMap = new LinkedHashMap<>();
        if (goodsVos == null || goodsVos.isEmpty()) {
            return colorMap;
        }
        List<Integer> colors = new ArrayList<>();
        for (int color : COLOR_PALETTE) {
            colors.add(color);
        }
        if (shuffle) {
            Collections.shuffle(colors, new Random(System.currentTimeMillis()));
        }
        int colorIndex = 0;
        for (GoodsVo goodsVo : goodsVos) {
            if (goodsVo == null || goodsVo.getGoods() == null || TextUtils.isEmpty(goodsVo.getGoods().getPlu())) {
                continue;
            }
            String plu = goodsVo.getGoods().getPlu();
            if (colorMap.containsKey(plu)) {
                continue;
            }
            colorMap.put(plu, colors.get(colorIndex % colors.size()));
            colorIndex++;
        }
        return colorMap;
    }

    public static void syncGoodsColorMap(Map<String, Integer> colorMap, List<GoodsVo> goodsVos) {
        if (colorMap == null) {
            return;
        }
        List<String> currentPlus = new ArrayList<>();
        if (goodsVos != null) {
            for (GoodsVo goodsVo : goodsVos) {
                if (goodsVo == null || goodsVo.getGoods() == null || TextUtils.isEmpty(goodsVo.getGoods().getPlu())) {
                    continue;
                }
                String plu = goodsVo.getGoods().getPlu();
                if (!currentPlus.contains(plu)) {
                    currentPlus.add(plu);
                }
            }
        }
        colorMap.keySet().removeIf(plu -> !currentPlus.contains(plu));
        for (String plu : currentPlus) {
            if (!colorMap.containsKey(plu)) {
                colorMap.put(plu, findNextColor(colorMap));
            }
        }
    }

    private static int findNextColor(Map<String, Integer> colorMap) {
        for (int color : COLOR_PALETTE) {
            if (!colorMap.containsValue(color)) {
                return color;
            }
        }
        return COLOR_PALETTE[colorMap.size() % COLOR_PALETTE.length];
    }

    public static int getGoodsColor(Map<String, Integer> colorMap, GoodsVo goodsVo) {
        if (goodsVo != null && goodsVo.getGoods() != null && colorMap != null) {
            Integer color = colorMap.get(goodsVo.getGoods().getPlu());
            if (color != null) {
                return color;
            }
        }
        return DEFAULT_COLOR;
    }
}
