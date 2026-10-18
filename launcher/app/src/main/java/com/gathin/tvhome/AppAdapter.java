package com.gathin.tvhome;

import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.provider.Settings;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * App tiles. The first {@link MainActivity#COLUMNS} positions are the pinned dock (favourites, fixed order),
 * the rest is alphabetical.
 */
public class AppAdapter extends RecyclerView.Adapter<AppAdapter.Holder> {

    private static final String TAG = "TvHome";

    interface TileListener {
        void onTileFocused(int position, View card);

        /** Called for D-pad Up/Down on a tile. Return true when handled. */
        boolean onTileVerticalKey(int position, int keyCode);
    }

    static final class Entry {
        final String label;
        final ComponentName component;
        final String pkg;
        final Drawable banner;
        final Drawable icon;
        int ambient;

        Entry(String label, ComponentName component, Drawable banner, Drawable icon) {
            this.label = label;
            this.component = component;
            this.pkg = component != null ? component.getPackageName() : "";
            this.banner = banner;
            this.icon = icon;
        }
    }

    static final float FOCUS_SCALE = 1.10f;

    private final Context ctx;
    private final TileListener listener;
    private final int dockSize;
    /** Dock order (res/values/config.xml, dock_apps). */
    private final List<String> favourites;
    /** Never shown as tiles (res/values/config.xml, hidden_apps). */
    private final Set<String> hidden;
    private final List<Entry> items = new ArrayList<>();

    AppAdapter(Context ctx, TileListener listener, int dockSize) {
        this.ctx = ctx;
        this.listener = listener;
        this.dockSize = dockSize;
        this.favourites = Arrays.asList(ctx.getResources().getStringArray(R.array.dock_apps));
        this.hidden = new HashSet<>(Arrays.asList(ctx.getResources().getStringArray(R.array.hidden_apps)));
    }

    void reload() {
        PackageManager pm = ctx.getPackageManager();
        Set<String> seen = new HashSet<>();
        List<Entry> fresh = new ArrayList<>();

        // 1. Query Leanback launcher activities
        Intent probeLeanback = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        List<ResolveInfo> found = pm.queryIntentActivities(probeLeanback, 0);
        for (ResolveInfo ri : found) {
            if (ri.activityInfo == null) continue;
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(ctx.getPackageName()) || hidden.contains(pkg) || seen.contains(pkg)) continue;
            seen.add(pkg);
            Drawable banner = ri.activityInfo.loadBanner(pm);
            if (banner == null) banner = ri.activityInfo.applicationInfo.loadBanner(pm);
            Drawable icon = ri.loadIcon(pm);
            String label = String.valueOf(ri.loadLabel(pm));
            fresh.add(new Entry(label, new ComponentName(pkg, ri.activityInfo.name), banner, icon));
        }

        // 2. Query standard launcher activities
        Intent probeStandard = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> foundStandard = pm.queryIntentActivities(probeStandard, 0);
        for (ResolveInfo ri : foundStandard) {
            if (ri.activityInfo == null) continue;
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(ctx.getPackageName()) || hidden.contains(pkg) || seen.contains(pkg)) continue;
            seen.add(pkg);
            Drawable banner = ri.activityInfo.loadBanner(pm);
            if (banner == null) banner = ri.activityInfo.applicationInfo.loadBanner(pm);
            Drawable icon = ri.loadIcon(pm);
            String label = String.valueOf(ri.loadLabel(pm));
            fresh.add(new Entry(label, new ComponentName(pkg, ri.activityInfo.name), banner, icon));
        }

        // 3. Fallback for dock apps: explicitly check if installed
        for (String fav : favourites) {
            if (seen.contains(fav) || hidden.contains(fav)) continue;
            try {
                ApplicationInfo ai = pm.getApplicationInfo(fav, 0);
                if (!ai.enabled) continue;
                Intent launchIntent = pm.getLeanbackLaunchIntentForPackage(fav);
                if (launchIntent == null) launchIntent = pm.getLaunchIntentForPackage(fav);
                ComponentName comp = launchIntent != null ? launchIntent.getComponent() : new ComponentName(fav, "");
                Drawable banner = ai.loadBanner(pm);
                Drawable icon = ai.loadIcon(pm);
                String label = String.valueOf(ai.loadLabel(pm));
                fresh.add(new Entry(label, comp, banner, icon));
                seen.add(fav);
            } catch (Exception ignored) {
            }
        }

        Collections.sort(fresh, (a, b) -> {
            int fa = favourites.indexOf(a.pkg), fb = favourites.indexOf(b.pkg);
            if (fa >= 0 || fb >= 0) {
                if (fa < 0) return 1;
                if (fb < 0) return -1;
                return Integer.compare(fa, fb);
            }
            return a.label.compareToIgnoreCase(b.label);
        });

        items.clear();
        items.addAll(fresh);

        for (int i = 0; i < items.size(); i++) {
            Entry e = items.get(i);
            Log.i(TAG, "Item " + i + ": pkg=" + e.pkg + ", label=" + e.label + ", hasBanner=" + (e.banner != null));
        }

        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_app, parent, false);
        return new Holder(v, listener);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        Entry e = items.get(position);
        h.name.setText(e.label);
        h.name.setVisibility(position < dockSize ? View.INVISIBLE : View.VISIBLE);   // the dock has no labels below tile
        h.name.setAlpha(0f);
        h.card.setScaleX(1f);
        h.card.setScaleY(1f);

        boolean validBanner = e.banner != null && e.banner.getIntrinsicWidth() > 0 && e.banner.getIntrinsicHeight() > 0;
        if (validBanner) {
            h.banner.setImageDrawable(e.banner);
            h.banner.setVisibility(View.VISIBLE);
            h.icon.setVisibility(View.GONE);
            h.label.setVisibility(View.GONE);
        } else {
            h.banner.setImageDrawable(null);
            h.banner.setVisibility(View.GONE);
            h.icon.setImageDrawable(e.icon);
            h.icon.setVisibility(View.VISIBLE);
            h.label.setText(e.label);
            h.label.setVisibility(View.VISIBLE);
        }

        h.card.setOnClickListener(v -> launch(e));
        h.card.setOnLongClickListener(v -> {
            Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + e.pkg));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                ctx.startActivity(i);
            } catch (ActivityNotFoundException ignored) {
            }
            return true;
        });
    }

    private void launch(Entry e) {
        Log.i(TAG, "Launching: " + e.pkg + " (" + e.label + ")");
        Intent i = ctx.getPackageManager().getLeanbackLaunchIntentForPackage(e.pkg);
        if (i == null) {
            i = ctx.getPackageManager().getLaunchIntentForPackage(e.pkg);
        }
        if (i == null && e.component != null && !e.component.getClassName().isEmpty()) {
            i = new Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                    .setComponent(e.component);
        }
        if (i != null) {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            try {
                ctx.startActivity(i);
                return;
            } catch (Exception ex) {
                Log.e(TAG, "Failed to launch " + e.pkg, ex);
            }
        }
        Toast.makeText(ctx, "Can't open " + e.label, Toast.LENGTH_SHORT).show();
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    String labelAt(int position) {
        return position >= 0 && position < items.size() ? items.get(position).label : "";
    }

    /** A vivid colour sampled from the app's banner (or icon), cached per entry. */
    int ambientColorAt(int position) {
        if (position < 0 || position >= items.size()) return 0xFF444444;
        Entry e = items.get(position);
        if (e.ambient == 0) e.ambient = vividColor(e.banner != null ? e.banner : e.icon);
        return e.ambient;
    }

    private static int vividColor(Drawable live) {
        if (live == null) return 0xFF444444;
        // Draw a private copy: changing the bounds of the drawable an ImageView shows would blank the tile.
        Drawable d = live.getConstantState() != null ? live.getConstantState().newDrawable().mutate() : live;
        Rect saved = new Rect(d.getBounds());
        int n = 12;
        Bitmap bmp = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        d.setBounds(0, 0, n, n);
        d.draw(c);
        d.setBounds(saved);
        float[] hsv = new float[3];
        int best = 0xFF444444;
        float bestScore = -1f;
        long r = 0, g = 0, b = 0;
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                int pixel = bmp.getPixel(x, y);
                int alpha = (pixel >>> 24);
                if (alpha < 128) continue;
                Color.colorToHSV(pixel, hsv);
                float sat = hsv[1], val = hsv[2];
                if (val < 0.2f || val > 0.95f) continue;
                float score = sat * 2.0f + val;
                if (score > bestScore) {
                    bestScore = score;
                    best = pixel;
                }
                r += (pixel >> 16) & 0xFF;
                g += (pixel >> 8) & 0xFF;
                b += pixel & 0xFF;
            }
        }
        bmp.recycle();
        if (bestScore >= 0.6f) return best | 0xFF000000;
        int count = n * n;
        return 0xFF000000 | (((int) (r / count)) << 16) | (((int) (g / count)) << 8) | ((int) (b / count));
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final View card;
        final ImageView banner;
        final ImageView icon;
        final TextView label;
        final TextView name;

        Holder(@NonNull View itemView, TileListener listener) {
            super(itemView);
            card = itemView.findViewById(R.id.card);
            banner = itemView.findViewById(R.id.banner);
            icon = itemView.findViewById(R.id.icon);
            label = itemView.findViewById(R.id.label);
            name = itemView.findViewById(R.id.name);

            card.setClipToOutline(true);
            card.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    float r = 16f * view.getResources().getDisplayMetrics().density;
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), r);
                }
            });

            card.setOnFocusChangeListener((v, hasFocus) -> {
                int pos = getBindingAdapterPosition();
                if (pos == RecyclerView.NO_POSITION) return;
                if (hasFocus) {
                    card.animate().scaleX(FOCUS_SCALE).scaleY(FOCUS_SCALE).translationZ(8f).setDuration(160)
                            .setInterpolator(new OvershootInterpolator(1.4f)).start();
                    name.animate().alpha(1f).setDuration(120).start();
                    listener.onTileFocused(pos, card);
                } else {
                    card.animate().scaleX(1f).scaleY(1f).translationZ(0f).setDuration(130).start();
                    name.animate().alpha(0f).setDuration(100).start();
                }
            });

            card.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) {
                        card.performClick();
                        return true;
                    }
                    if (keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                        int pos = getBindingAdapterPosition();
                        if (pos != RecyclerView.NO_POSITION) {
                            return listener.onTileVerticalKey(pos, keyCode);
                        }
                    }
                }
                return false;
            });
        }
    }
}
