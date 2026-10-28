package org.fotlim.nfcreader;

import android.app.Activity;
import java.util.List;
import java.util.Comparator;
import java.util.Collections;
import java.util.ArrayList;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.view.WindowManager;
import android.view.Window;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ColorDrawable;
import android.content.pm.ResolveInfo;
import android.content.pm.PackageManager;
import android.content.ComponentName;
import android.app.Dialog;
import android.app.PendingIntent;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.IsoDep;
import android.nfc.tech.MifareClassic;
import android.nfc.tech.MifareUltralight;
import android.nfc.tech.Ndef;
import android.nfc.tech.NfcA;
import android.nfc.tech.NfcB;
import android.nfc.tech.NfcF;
import android.nfc.tech.NfcV;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {

    private int cPrimary, cOnPrimary, cPrimaryContainer, cOnPrimaryContainer;
    private int cSurface, cSurfaceContainer, cSurfaceContainerLow;
    private int cOnSurface, cOnSurfaceVariant, cSuccess;
    private int cErrorContainer, cOnErrorContainer;

    private NfcAdapter mAdapter;
    private PendingIntent mPending;
    private TextView mStatusPill;
    private LinearLayout mChipRow;
    private TextView mUidText;
    private TextView mOutput;
    private final Handler mUi = new Handler(Looper.getMainLooper());
    private final StringBuilder mReport = new StringBuilder();
    private float mDp;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mDp = getResources().getDisplayMetrics().density;
        applyPalette();
        buildUi();

        mAdapter = NfcAdapter.getDefaultAdapter(this);
        if (mAdapter == null) {
            setStatus("本机没有 NFC 硬件", cOnErrorContainer, cErrorContainer);
            return;
        }

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 31) {
            flags |= PendingIntent.FLAG_MUTABLE;
        }
        Intent ni = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        mPending = PendingIntent.getActivity(this, 0, ni, flags);

        handleIntent(getIntent());
    }

    private boolean isNight() {
        int m = getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        return m == Configuration.UI_MODE_NIGHT_YES;
    }

    private void applyPalette() {
        if (isNight()) {
            cPrimary = 0xFF9FCBFF;
            cOnPrimary = 0xFF00325A;
            cPrimaryContainer = 0xFF00497D;
            cOnPrimaryContainer = 0xFFCFE5FF;
            cSurface = 0xFF111318;
            cSurfaceContainer = 0xFF1D2024;
            cSurfaceContainerLow = 0xFF191C20;
            cOnSurface = 0xFFE2E2E9;
            cOnSurfaceVariant = 0xFFC4C6D0;
            cSuccess = 0xFF8FD9A8;
            cErrorContainer = 0xFF8C1D18;
            cOnErrorContainer = 0xFFF9DEDC;
        } else {
            cPrimary = 0xFF00639B;
            cOnPrimary = 0xFFFFFFFF;
            cPrimaryContainer = 0xFFCFE5FF;
            cOnPrimaryContainer = 0xFF001D31;
            cSurface = 0xFFFAF9FD;
            cSurfaceContainer = 0xFFEEEDF4;
            cSurfaceContainerLow = 0xFFF5F4F9;
            cOnSurface = 0xFF1A1C1E;
            cOnSurfaceVariant = 0xFF43474E;
            cSuccess = 0xFF146C2E;
            cErrorContainer = 0xFFFFDAD6;
            cOnErrorContainer = 0xFF410002;
        }
    }

    private int dp(float v) {
        return (int) (v * mDp + 0.5f);
    }

    /** 读取系统状态栏高度，避免标题被状态栏遮挡 */
    private int statusBarHeight() {
        int id = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (id > 0) {
            int h = getResources().getDimensionPixelSize(id);
            if (h > 0) return h;
        }
        return dp(28);
    }

    /** 读取系统导航栏高度 */
    private int navBarHeight() {
        int id = getResources().getIdentifier("navigation_bar_height", "dimen", "android");
        if (id > 0) {
            int h = getResources().getDimensionPixelSize(id);
            if (h > 0) return h;
        }
        return dp(16);
    }

    private GradientDrawable rounded(int color, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.RECTANGLE);
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    private RippleDrawable ripple(int bg, float radiusDp, int rippleColor) {
        return new RippleDrawable(ColorStateList.valueOf(rippleColor),
                rounded(bg, radiusDp), null);
    }

    private TextView text(String s, float sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(cSurface);

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.VERTICAL);
        bar.setPadding(dp(20), dp(18) + statusBarHeight(), dp(20), dp(14));
        bar.setBackgroundColor(cSurfaceContainer);
        bar.setElevation(dp(2));

        bar.addView(text("NFC 读卡器", 24f, cOnSurface, true));

        TextView sub = text("免费 · 无内购 · 全功能", 12f, cOnSurfaceVariant, false);
        sub.setPadding(0, dp(2), 0, 0);
        bar.addView(sub);
        root.addView(bar);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(16), dp(16), dp(16), dp(8));

        mStatusPill = text("等待贴卡…", 14f, cOnPrimaryContainer, true);
        mStatusPill.setGravity(Gravity.CENTER);
        mStatusPill.setPadding(dp(16), dp(9), dp(16), dp(9));
        mStatusPill.setBackground(rounded(cPrimaryContainer, 20f));
        body.addView(mStatusPill, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(20), dp(18), dp(20), dp(18));
        card.setBackground(rounded(cSurfaceContainerLow, 24f));
        card.setElevation(dp(1));

        card.addView(text("UID", 11f, cOnSurfaceVariant, false));

        mUidText = text("—", 26f, cPrimary, true);
        mUidText.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        mUidText.setTextIsSelectable(true);
        mUidText.setPadding(0, dp(2), 0, dp(10));
        card.addView(mUidText);

        mChipRow = new LinearLayout(this);
        mChipRow.setOrientation(LinearLayout.HORIZONTAL);
        HorizontalScrollView chipScroll = new HorizontalScrollView(this);
        chipScroll.setHorizontalScrollBarEnabled(false);
        chipScroll.addView(mChipRow);
        card.addView(chipScroll);

        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.topMargin = dp(14);
        body.addView(card, cardLp);

        LinearLayout detailCard = new LinearLayout(this);
        detailCard.setOrientation(LinearLayout.VERTICAL);
        detailCard.setPadding(dp(18), dp(16), dp(18), dp(16));
        detailCard.setBackground(rounded(cSurfaceContainerLow, 24f));
        detailCard.setElevation(dp(1));

        detailCard.addView(text("卡片详情", 13f, cOnSurfaceVariant, true));

        mOutput = new TextView(this);
        mOutput.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        mOutput.setTextColor(cOnSurface);
        mOutput.setTypeface(Typeface.MONOSPACE);
        mOutput.setTextIsSelectable(true);
        mOutput.setPadding(0, dp(8), 0, 0);
        mOutput.setText("把卡片贴到手机背面中上部（NFC 天线位置）。\n\n"
                + "可读取：\n"
                + "  · UID（正序 / 反序）\n"
                + "  · ATQA / SAK 参数\n"
                + "  · 全部技术协议\n"
                + "  · Mifare Classic 扇区认证 + 逐块数据\n"
                + "  · Mifare Ultralight 逐页数据\n"
                + "  · NDEF 内容（文本 / 链接 / 名片）\n"
                + "  · ISO-DEP 历史字节 + APDU 收发");
        detailCard.addView(mOutput);

        LinearLayout.LayoutParams dcLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        dcLp.topMargin = dp(14);
        body.addView(detailCard, dcLp);

        scroll.addView(body);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setPadding(dp(16), dp(8), dp(16), dp(16) + navBarHeight());
        actions.setBackgroundColor(cSurface);

        actions.addView(mdButton("复制", true), weight());
        actions.addView(mdButton("分享", false), weight());
        actions.addView(mdButton("清空", false), weight());
        root.addView(actions);

        setContentView(root);
    }

    private LinearLayout.LayoutParams weight() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        p.leftMargin = dp(4);
        p.rightMargin = dp(4);
        return p;
    }

    private Button mdButton(String label, boolean filled) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        b.setAllCaps(false);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setStateListAnimator(null);
        b.setMinHeight(dp(44));
        b.setPadding(dp(8), dp(10), dp(8), dp(10));

        if (filled) {
            b.setTextColor(cOnPrimary);
            b.setBackground(ripple(cPrimary, 22f, 0x33FFFFFF));
        } else {
            b.setTextColor(cOnPrimaryContainer);
            b.setBackground(ripple(cPrimaryContainer, 22f, 0x3300639B));
        }

        if (label.equals("复制")) {
            b.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    ClipboardManager cm = (ClipboardManager)
                            getSystemService(Context.CLIPBOARD_SERVICE);
                    if (cm != null) {
                        cm.setPrimaryClip(ClipData.newPlainText("nfc", mReport.toString()));
                        toast("已复制到剪贴板");
                    }
                }
            });
        } else if (label.equals("分享")) {
            b.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    if (mReport.length() == 0) {
                        toast("还没有数据");
                        return;
                    }
                    Intent s = new Intent(Intent.ACTION_SEND);
                    s.setType("text/plain");
                    s.putExtra(Intent.EXTRA_TEXT, mReport.toString());
                    startActivity(Intent.createChooser(s, "分享读卡结果"));
                }
            });
        } else {
            b.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    mReport.setLength(0);
                    mOutput.setText("");
                    mUidText.setText("—");
                    mChipRow.removeAllViews();
                    setStatus("等待贴卡…", cOnPrimaryContainer, cPrimaryContainer);
                }
            });
        }
        return b;
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    private void setStatus(String s, int textColor, int bgColor) {
        if (mStatusPill == null) return;
        mStatusPill.setText(s);
        mStatusPill.setTextColor(textColor);
        mStatusPill.setBackground(rounded(bgColor, 20f));
    }

    private void addChip(String label) {
        TextView c = text(label, 12f, cOnPrimaryContainer, false);
        c.setPadding(dp(12), dp(6), dp(12), dp(6));
        c.setBackground(rounded(cPrimaryContainer, 10f));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        p.rightMargin = dp(6);
        mChipRow.addView(c, p);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mAdapter == null) return;
        if (!mAdapter.isEnabled()) {
            setStatus("NFC 未开启 · 请到设置里打开", cOnErrorContainer, cErrorContainer);
            return;
        }
        IntentFilter[] filters = new IntentFilter[]{
                new IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED),
                new IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED),
                new IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED),
        };
        try {
            mAdapter.enableForegroundDispatch(this, mPending, filters, null);
            if (mReport.length() == 0) {
                setStatus("就绪 · 请贴卡", cOnPrimaryContainer, cPrimaryContainer);
            }
        } catch (Throwable e) {
            setStatus("启动失败：" + e.getMessage(), cOnErrorContainer, cErrorContainer);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mAdapter != null) {
            try {
                mAdapter.disableForegroundDispatch(this);
            } catch (Throwable ignored) {
            }
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent it) {
        if (it == null) return;
        Tag tag = null;
        try {
            tag = (Tag) it.getParcelableExtra(NfcAdapter.EXTRA_TAG);
        } catch (Throwable ignored) {
        }
        if (tag == null) return;
        final Tag t = tag;
        setStatus("读取中…", cOnPrimaryContainer, cPrimaryContainer);
        new Thread(new Runnable() {
            public void run() {
                final String rep = buildReport(t);
                final String[] techs = t.getTechList();
                final String uid = hex(t.getId(), false);
                mUi.post(new Runnable() {
                    public void run() {
                        mReport.setLength(0);
                        mReport.append(rep);
                        mOutput.setText(rep);
                        mUidText.setText(uid);
                        mChipRow.removeAllViews();
                        for (String tc : techs) addChip(simple(tc));
                        setStatus("读取成功", cSuccess, cPrimaryContainer);
                        showActionChooser(t);
                    }
                });
            }
        }).start();
    }

    // ======================================================================
    // 读卡后的「选择操作」底部弹窗
    // ======================================================================

    /** 一个候选操作 */
    private static class ActionItem {
        String label;
        Drawable icon;
        Intent intent;   // 非空 = 可启动的外部应用
        int kind;        // 0=应用 1=内置动作
        ActionItem(String label, Drawable icon, Intent intent, int kind) {
            this.label = label; this.icon = icon; this.intent = intent; this.kind = kind;
        }
    }

    /** 读出所有能处理这张卡的应用，弹出选择面板 */
    private void showActionChooser(Tag tag) {
        final List<ActionItem> items = new ArrayList<ActionItem>();

        // 1) 内置动作：查看详情 / 复制 UID
        items.add(new ActionItem("查看详情", rounded(cPrimaryContainer, 22f), null, 1));
        items.add(new ActionItem("复制 UID", rounded(cPrimaryContainer, 22f), null, 2));

        // 2) 系统里能处理这张卡的应用
        PackageManager pm = getPackageManager();
        String[] techs = tag.getTechList();
        Intent techIntent = new Intent(NfcAdapter.ACTION_TECH_DISCOVERED);
        techIntent.putExtra(NfcAdapter.EXTRA_TAG, tag);
        techIntent.putExtra(NfcAdapter.EXTRA_ID, tag.getId());
        if (techs != null) {
            // NfcAdapter.EXTRA_TECH 是 @SystemApi，public SDK 里没有，这里直接用字面量
            techIntent.putExtra("android.nfc.extra.TECH", techs);
        }

        List<ResolveInfo> resolved = new ArrayList<ResolveInfo>();
        try {
            resolved.addAll(pm.queryIntentActivities(techIntent,
                    PackageManager.MATCH_DEFAULT_ONLY | PackageManager.GET_RESOLVED_FILTER));
        } catch (Throwable ignored) {
        }
        try {
            Intent tagIntent = new Intent(NfcAdapter.ACTION_TAG_DISCOVERED);
            tagIntent.putExtra(NfcAdapter.EXTRA_TAG, tag);
            for (ResolveInfo ri : pm.queryIntentActivities(tagIntent,
                    PackageManager.MATCH_DEFAULT_ONLY | PackageManager.GET_RESOLVED_FILTER)) {
                boolean dup = false;
                for (ResolveInfo ex : resolved) {
                    if (ex.activityInfo.packageName.equals(ri.activityInfo.packageName)
                            && ex.activityInfo.name.equals(ri.activityInfo.name)) {
                        dup = true;
                        break;
                    }
                }
                if (!dup) resolved.add(ri);
            }
        } catch (Throwable ignored) {
        }

        for (ResolveInfo ri : resolved) {
            String pkg = ri.activityInfo.packageName;
            if (getPackageName().equals(pkg)) continue;   // 跳过自己
            CharSequence label;
            Drawable icon;
            try {
                label = ri.loadLabel(pm);
            } catch (Throwable e) {
                label = pkg;
            }
            try {
                icon = ri.loadIcon(pm);
            } catch (Throwable e) {
                icon = null;
            }
            if (icon == null) icon = rounded(cSurfaceContainer, 22f);

            Intent launch = new Intent(techIntent);
            launch.setComponent(new ComponentName(pkg, ri.activityInfo.name));
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            items.add(new ActionItem(label.toString(), icon, launch, 0));
        }

        buildChooserDialog(items);
    }

    private void buildChooserDialog(final List<ActionItem> items) {
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout sheet = new LinearLayout(this);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setPadding(dp(20), dp(18), dp(20), dp(12));

        GradientDrawable sheetBg = new GradientDrawable();
        sheetBg.setColor(cSurface);
        sheetBg.setCornerRadii(new float[]{
                dp(28), dp(28), dp(28), dp(28), 0, 0, 0, 0});
        sheet.setBackground(sheetBg);

        // 标题
        TextView title = new TextView(this);
        title.setText("选择操作");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(cOnSurface);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, dp(16));
        sheet.addView(title);

        // 应用网格（4 列）
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(4);
        grid.setRowCount((items.size() + 3) / 4);
        grid.setPadding(0, dp(4), 0, dp(8));

        for (final ActionItem item : items) {
            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER_HORIZONTAL);
            cell.setPadding(dp(4), dp(10), dp(4), dp(10));
            cell.setClickable(true);
            cell.setFocusable(true);
            cell.setBackground(rippleTransparent());

            ImageView iv = new ImageView(this);
            iv.setImageDrawable(item.icon);
            int sz = dp(50);
            LinearLayout.LayoutParams ivLp = new LinearLayout.LayoutParams(sz, sz);
            ivLp.gravity = Gravity.CENTER_HORIZONTAL;
            cell.addView(iv, ivLp);

            TextView tv = new TextView(this);
            tv.setText(item.label);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
            tv.setTextColor(cOnSurfaceVariant);
            tv.setGravity(Gravity.CENTER);
            tv.setMaxLines(2);
            tv.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams tvLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            tvLp.topMargin = dp(6);
            cell.addView(tv, tvLp);

            GridLayout.LayoutParams cellLp = new GridLayout.LayoutParams();
            cellLp.width = 0;
            cellLp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            cellLp.setMargins(dp(2), dp(2), dp(2), dp(2));
            grid.addView(cell, cellLp);

            cell.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    dialog.dismiss();
                    onActionPicked(item);
                }
            });
        }
        sheet.addView(grid);

        // 取消按钮
        Button cancel = mdButton("取消", false);
        cancel.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                dialog.dismiss();
            }
        });
        LinearLayout.LayoutParams cLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cLp.topMargin = dp(6);
        sheet.addView(cancel, cLp);

        dialog.setContentView(sheet);
        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(0x00000000));
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            w.setGravity(Gravity.BOTTOM);
            WindowManager.LayoutParams lp = w.getAttributes();
            lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
            w.setAttributes(lp);
        }
        dialog.show();
    }

    private void onActionPicked(ActionItem item) {
        if (item.kind == 1) {
            // 查看详情：本来就是当前页面，滚到顶即可
            toast("详情已在页面中显示");
            return;
        }
        if (item.kind == 2) {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null && mUidText != null) {
                cm.setPrimaryClip(ClipData.newPlainText("uid", mUidText.getText()));
                toast("UID 已复制");
            }
            return;
        }
        try {
            startActivity(item.intent);
        } catch (Throwable e) {
            toast("打开失败：" + e.getClass().getSimpleName());
        }
    }

    private RippleDrawable rippleTransparent() {
        return new RippleDrawable(
                ColorStateList.valueOf(0x2200639B),
                null,
                new android.graphics.drawable.ColorDrawable(0xFFFFFFFF));
    }

    private String buildReport(Tag tag) {
        StringBuilder sb = new StringBuilder();
        String[] techs = tag.getTechList();

        String ts = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)
                .format(new Date());
        sb.append("时间   ").append(ts).append('\n');
        byte[] id = tag.getId();
        sb.append("UID    ").append(hex(id, false)).append('\n');
        sb.append("UID反序 ").append(hex(id, true)).append('\n');
        sb.append("长度   ").append(id == null ? 0 : id.length).append(" 字节\n");
        sb.append("技术   ");
        for (int i = 0; i < techs.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(simple(techs[i]));
        }
        sb.append("\n");

        for (String t : techs) {
            try {
                String s = null;
                if (t.equals(NfcA.class.getName())) s = dumpNfcA(NfcA.get(tag));
                else if (t.equals(NfcB.class.getName())) s = dumpNfcB(NfcB.get(tag));
                else if (t.equals(NfcF.class.getName())) s = dumpNfcF(NfcF.get(tag));
                else if (t.equals(NfcV.class.getName())) s = dumpNfcV(NfcV.get(tag));
                else if (t.equals(IsoDep.class.getName())) s = dumpIsoDep(IsoDep.get(tag));
                else if (t.equals(MifareClassic.class.getName())) s = dumpMifareClassic(MifareClassic.get(tag));
                else if (t.equals(MifareUltralight.class.getName())) s = dumpUltralight(MifareUltralight.get(tag));
                else if (t.equals(Ndef.class.getName())) s = dumpNdef(Ndef.get(tag));
                if (s != null) sb.append(s);
            } catch (Throwable e) {
                sb.append('\n').append(simple(t)).append(" 读取失败：")
                        .append(e.getClass().getSimpleName()).append('\n');
            }
        }
        return sb.toString();
    }

    private String dumpNfcA(NfcA n) {
        StringBuilder sb = new StringBuilder("\nATQA   ");
        sb.append(hex(n.getAtqa(), false)).append('\n');
        sb.append("SAK    ").append(String.format(Locale.US, "%02X", n.getSak())).append('\n');
        sb.append("最大收发 ").append(n.getMaxTransceiveLength()).append(" 字节\n");
        return sb.toString();
    }

    private String dumpNfcB(NfcB n) {
        StringBuilder sb = new StringBuilder("\n应用数据 ");
        sb.append(hex(n.getApplicationData(), false)).append('\n');
        sb.append("协议信息 ").append(hex(n.getProtocolInfo(), false)).append('\n');
        sb.append("最大收发 ").append(n.getMaxTransceiveLength()).append(" 字节\n");
        return sb.toString();
    }

    private String dumpNfcF(NfcF n) {
        StringBuilder sb = new StringBuilder("\n厂商代码 ");
        sb.append(hex(n.getManufacturer(), false)).append('\n');
        sb.append("系统代码 ").append(hex(n.getSystemCode(), false)).append('\n');
        sb.append("最大收发 ").append(n.getMaxTransceiveLength()).append(" 字节\n");
        return sb.toString();
    }

    private String dumpNfcV(NfcV n) {
        StringBuilder sb = new StringBuilder("\nDSF ID ");
        sb.append(String.format(Locale.US, "%02X", n.getDsfId())).append('\n');
        sb.append("响应标志 ").append(String.format(Locale.US, "%02X", n.getResponseFlags())).append('\n');
        sb.append("最大收发 ").append(n.getMaxTransceiveLength()).append(" 字节\n");
        return sb.toString();
    }

    private String dumpIsoDep(IsoDep d) {
        StringBuilder sb = new StringBuilder("\n历史字节 ");
        byte[] hist = d.getHistoricalBytes();
        byte[] hi = d.getHiLayerResponse();
        sb.append(hist == null ? "(无)" : hex(hist, false)).append('\n');
        sb.append("高层响应 ").append(hi == null ? "(无)" : hex(hi, false)).append('\n');
        sb.append("扩展APDU ").append(d.isExtendedLengthApduSupported() ? "支持" : "不支持").append('\n');
        sb.append("最大收发 ").append(d.getMaxTransceiveLength()).append(" 字节\n");
        sb.append("超时   ").append(d.getTimeout()).append(" ms\n");

        try {
            d.connect();
            try {
                byte[] sel = d.transceive(new byte[]{0x00, (byte) 0xA4, 0x04, 0x00, 0x00});
                sb.append("SELECT MF → ").append(hex(sel, false)).append('\n');
            } catch (Throwable e) {
                sb.append("SELECT MF → 卡不支持\n");
            }
            try {
                byte[] uid = d.transceive(new byte[]{0x00, (byte) 0xCA, 0x00, 0x00, 0x00});
                sb.append("GET UID   → ").append(hex(uid, false)).append('\n');
            } catch (Throwable e) {
                sb.append("GET UID   → 卡不支持\n");
            }
            d.close();
        } catch (Throwable ignored) {
        }
        return sb.toString();
    }

    private String dumpMifareClassic(MifareClassic m) {
        StringBuilder sb = new StringBuilder();
        int type = m.getType();
        String tn = type == MifareClassic.TYPE_CLASSIC ? "Classic"
                : type == MifareClassic.TYPE_PLUS ? "Plus"
                : type == MifareClassic.TYPE_PRO ? "Pro" : "未知(" + type + ")";
        int size = m.getSize();
        int sectors = m.getSectorCount();
        sb.append("\n类型   MIFARE ").append(tn).append('\n');
        sb.append("容量   ").append(size).append(" 字节\n");
        sb.append("扇区数 ").append(sectors).append('\n');
        sb.append("块总数 ").append(m.getBlockCount()).append('\n');

        byte[][] keyAs = new byte[][]{
                MifareClassic.KEY_DEFAULT,
                MifareClassic.KEY_MIFARE_APPLICATION_DIRECTORY,
                MifareClassic.KEY_NFC_FORUM,
                hexToBytes("A0A1A2A3A4A5"),
                hexToBytes("D3F7D3F7D3F7"),
                hexToBytes("000000000000"),
        };
        String[] keyNames = new String[]{
                "FFFFFFFFFFFF", "A0A1A2A3A4A5", "D3F7D3F7D3F7",
                "A0A1A2A3A4A5", "D3F7D3F7D3F7", "000000000000"};

        try {
            m.connect();
            int budget = 512;
            int authed = 0;
            for (int sec = 0; sec < sectors && budget > 0; sec++) {
                int k = -1;
                for (int i = 0; i < keyAs.length; i++) {
                    try {
                        if (m.authenticateSectorWithKeyA(sec, keyAs[i])) {
                            k = i;
                            break;
                        }
                    } catch (Throwable ignored) {
                    }
                }
                if (k < 0) {
                    sb.append("\n扇区 ").append(sec).append(" 认证失败（加密/未知密钥）\n");
                    continue;
                }
                authed++;
                int first = blockOfSector(sec, size);
                int count = blocksInSector(sec, size);
                sb.append("\n扇区 ").append(sec).append("  KeyA=").append(keyNames[k]).append('\n');
                for (int b = 0; b < count && budget > 0; b++) {
                    int blk = first + b;
                    if (blk >= m.getBlockCount()) break;
                    budget--;
                    try {
                        byte[] data = m.readBlock(blk);
                        sb.append(String.format(Locale.US, "  %3d ", blk))
                                .append(hex(data, false)).append('\n');
                    } catch (Throwable e) {
                        sb.append("  ").append(blk).append(" 读失败\n");
                    }
                }
            }
            if (budget <= 0) sb.append("\n（达到读取上限，已停止）\n");
            sb.append("\n可认证扇区 ").append(authed).append(" / ").append(sectors).append('\n');
            m.close();
        } catch (Throwable ignored) {
        }
        return sb.toString();
    }

    private static int blockOfSector(int sec, int size) {
        if (size >= 4096) {
            if (sec < 32) return sec * 4;
            return 128 + (sec - 32) * 16;
        }
        return sec * 4;
    }

    private static int blocksInSector(int sec, int size) {
        if (size >= 4096 && sec >= 32) return 16;
        return 4;
    }

    private String dumpUltralight(MifareUltralight u) {
        StringBuilder sb = new StringBuilder();
        int t = u.getType();
        String tn = t == MifareUltralight.TYPE_ULTRALIGHT ? "Ultralight"
                : t == MifareUltralight.TYPE_ULTRALIGHT_C ? "Ultralight C"
                : t == MifareUltralight.TYPE_UNKNOWN ? "Unknown" : "(" + t + ")";
        sb.append("\n类型   ").append(tn).append('\n');
        try {
            u.connect();
            try {
                byte[] p0 = u.readPages(0);
                sb.append("页 0-3 ").append(hex(p0, false)).append('\n');
                sb.append("  序列号 ").append(hex(slice(p0, 0, 3), false)).append('\n');
                sb.append("  BCC0   ").append(hex(slice(p0, 3, 4), false)).append('\n');
            } catch (Throwable e) {
                sb.append("页 0-3 读失败\n");
            }
            for (int p = 4; p < 44; p += 4) {
                try {
                    byte[] d = u.readPages(p);
                    sb.append(String.format(Locale.US, "页 %2d-%2d ", p, p + 3))
                            .append(hex(d, false)).append('\n');
                } catch (Throwable e) {
                    break;
                }
            }
            u.close();
        } catch (Throwable ignored) {
        }
        return sb.toString();
    }

    private String dumpNdef(Ndef n) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n类型   ").append(n.getType()).append('\n');
        sb.append("最大容量 ").append(n.getMaxSize()).append(" 字节\n");
        sb.append("可写   ").append(n.isWritable() ? "是" : "否").append('\n');
        sb.append("可设只读 ").append(n.canMakeReadOnly() ? "是" : "否").append('\n');
        try {
            n.connect();
            NdefMessage msg = n.getNdefMessage();
            n.close();
            if (msg == null) {
                sb.append("内容   (空)\n");
                return sb.toString();
            }
            NdefRecord[] recs = msg.getRecords();
            sb.append("记录数 ").append(recs.length).append('\n');
            for (int i = 0; i < recs.length; i++) {
                NdefRecord r = recs[i];
                sb.append("  [").append(i).append("] TNF=").append(r.getTnf())
                        .append(" type=").append(hex(r.getType(), false)).append('\n');
                sb.append("      ").append(describe(r)).append('\n');
            }
        } catch (Throwable e) {
            sb.append("读取失败 ").append(e.getClass().getSimpleName()).append('\n');
        }
        return sb.toString();
    }

    private String describe(NdefRecord r) {
        try {
            String mime = r.toMimeType();
            if (mime != null) return "MIME " + mime;
            if (r.getTnf() == NdefRecord.TNF_WELL_KNOWN) {
                byte[] t = r.getType();
                if (t != null && t.length == 1) {
                    if (t[0] == 'T') return "文本 " + decodeText(r.getPayload());
                    if (t[0] == 'U') return "链接 " + r.toUri();
                    return "类型 '" + (char) t[0] + "' 原始 " + hex(r.getPayload(), false);
                }
            }
            if (r.getTnf() == NdefRecord.TNF_MIME_MEDIA) {
                return "MIME " + new String(r.getType(), StandardCharsets.US_ASCII)
                        + " 长度 " + r.getPayload().length;
            }
            if (r.getTnf() == NdefRecord.TNF_ABSOLUTE_URI) {
                return "URI " + new String(r.getType(), StandardCharsets.UTF_8);
            }
            if (r.getTnf() == NdefRecord.TNF_EXTERNAL_TYPE) {
                return "外部类型 " + new String(r.getType(), StandardCharsets.US_ASCII);
            }
            return hex(r.getPayload(), false);
        } catch (Throwable e) {
            return "(解析失败 " + e.getClass().getSimpleName() + ")";
        }
    }

    private String decodeText(byte[] payload) {
        if (payload == null || payload.length == 0) return "(空)";
        int status = payload[0] & 0xFF;
        int langLen = status & 0x3F;
        boolean utf16 = (status & 0x80) != 0;
        if (payload.length < 1 + langLen) return hex(payload, false);
        String lang = new String(payload, 1, langLen, StandardCharsets.US_ASCII);
        try {
            String text = utf16
                    ? new String(payload, 1 + langLen, payload.length - 1 - langLen,
                            StandardCharsets.UTF_16)
                    : new String(payload, 1 + langLen, payload.length - 1 - langLen,
                            StandardCharsets.UTF_8);
            return "\"" + text + "\"  [lang=" + lang + "]";
        } catch (Throwable e) {
            return hex(payload, false);
        }
    }

    private static String simple(String fqcn) {
        int i = fqcn.lastIndexOf('.');
        return i < 0 ? fqcn : fqcn.substring(i + 1);
    }

    private static byte[] slice(byte[] a, int from, int to) {
        if (a == null) return new byte[0];
        to = Math.min(to, a.length);
        if (from >= to) return new byte[0];
        byte[] r = new byte[to - from];
        System.arraycopy(a, from, r, 0, to - from);
        return r;
    }

    private static String hex(byte[] a, boolean reverse) {
        if (a == null || a.length == 0) return "(无)";
        StringBuilder sb = new StringBuilder(a.length * 2);
        if (reverse) {
            for (int i = a.length - 1; i >= 0; i--) {
                sb.append(String.format(Locale.US, "%02X", a[i]));
            }
        } else {
            for (int i = 0; i < a.length; i++) {
                sb.append(String.format(Locale.US, "%02X", a[i]));
            }
        }
        return sb.toString();
    }

    private static byte[] hexToBytes(String s) {
        int n = s.length() / 2;
        byte[] r = new byte[n];
        for (int i = 0; i < n; i++) {
            r[i] = (byte) Integer.parseInt(s.substring(i * 2, i * 2 + 2), 16);
        }
        return r;
    }
}
