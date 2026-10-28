package com.ananta.tvlauncher;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.view.KeyEvent;
import android.view.View;
import android.animation.ValueAnimator;
import android.view.animation.DecelerateInterpolator;
import java.text.SimpleDateFormat;
import java.util.*;

/** Lightweight, remote-first Android TV home screen. No firmware changes or root required. */
public class MainActivity extends Activity {
    private LauncherView launcherView;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        hideSystemBars();
        launcherView = new LauncherView();
        setContentView(launcherView);
    }

    private void hideSystemBars() {
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    @Override protected void onResume() {
        super.onResume();
        hideSystemBars();
        if (launcherView != null) launcherView.reloadApps();
    }

    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (launcherView != null && launcherView.handleKey(keyCode)) return true;
        return super.onKeyDown(keyCode, event);
    }

    private class LauncherView extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final List<AppEntry> apps = new ArrayList<>();
        private final String[] shortcuts = {"LIVE TV", "MOVIES", "SERIES", "SETTINGS"};
        private int selectedApp = 0;
        private int selectedShortcut = 0;
        private int row = 0; // 0 = app dock, 1 = shortcuts
        private float focusProgress = 1f;
        private ValueAnimator focusAnimator;
        private final int cyan = Color.rgb(79, 224, 255);
        private final int purple = Color.rgb(157, 105, 255);
        private final int visibleApps = 7;

        LauncherView() {
            super(MainActivity.this);
            setFocusable(true);
            setFocusableInTouchMode(true);
            setContentDescription("Ananta TV home launcher");
            reloadApps();
        }

        void reloadApps() {
            apps.clear();
            PackageManager pm = getPackageManager();
            Intent tvIntent = new Intent(Intent.ACTION_MAIN);
            tvIntent.addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
            collect(pm, tvIntent);
            Intent mobileIntent = new Intent(Intent.ACTION_MAIN);
            mobileIntent.addCategory(Intent.CATEGORY_LAUNCHER);
            collect(pm, mobileIntent);
            for (Iterator<AppEntry> it = apps.iterator(); it.hasNext();) {
                if (getPackageName().equals(it.next().packageName)) it.remove();
            }
            Collections.sort(apps, new Comparator<AppEntry>() {
                @Override public int compare(AppEntry a, AppEntry b) {
                    return a.label.compareToIgnoreCase(b.label);
                }
            });
            if (selectedApp >= apps.size()) selectedApp = Math.max(0, apps.size() - 1);
            animateFocus();
            invalidate();
        }

        private void collect(PackageManager pm, Intent query) {
            List<ResolveInfo> found = pm.queryIntentActivities(query, 0);
            for (ResolveInfo ri : found) {
                if (ri.activityInfo == null) continue;
                String pkg = ri.activityInfo.packageName;
                boolean exists = false;
                for (AppEntry a : apps) if (a.packageName.equals(pkg)) { exists = true; break; }
                if (!exists) {
                    CharSequence label = ri.loadLabel(pm);
                    apps.add(new AppEntry(label == null ? pkg : label.toString(), pkg,
                        ri.loadIcon(pm), ri.activityInfo.name));
                }
            }
        }

        private void animateFocus() {
            if (focusAnimator != null) focusAnimator.cancel();
            focusAnimator = ValueAnimator.ofFloat(0.90f, 1f);
            focusAnimator.setDuration(170);
            focusAnimator.setInterpolator(new DecelerateInterpolator(1.7f));
            focusAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override public void onAnimationUpdate(ValueAnimator animation) {
                    focusProgress = (Float) animation.getAnimatedValue();
                    invalidate();
                }
            });
            focusAnimator.start();
        }

        boolean handleKey(int keyCode) {
            if (keyCode == KeyEvent.KEYCODE_MENU) { reloadApps(); return true; }
            switch (keyCode) {
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    if (row == 0 && !apps.isEmpty()) selectedApp = (selectedApp - 1 + apps.size()) % apps.size();
                    else if (row == 1) selectedShortcut = (selectedShortcut - 1 + shortcuts.length) % shortcuts.length;
                    else return true;
                    animateFocus(); invalidate(); return true;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    if (row == 0 && !apps.isEmpty()) selectedApp = (selectedApp + 1) % apps.size();
                    else if (row == 1) selectedShortcut = (selectedShortcut + 1) % shortcuts.length;
                    else return true;
                    animateFocus(); invalidate(); return true;
                case KeyEvent.KEYCODE_DPAD_UP:
                    if (row != 0) { row = 0; animateFocus(); invalidate(); }
                    return true;
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    if (row != 1) { row = 1; animateFocus(); invalidate(); }
                    return true;
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                case KeyEvent.KEYCODE_BUTTON_A:
                    activate(); return true;
                default: return false;
            }
        }

        private void activate() {
            if (row == 1) {
                if (selectedShortcut == 3) {
                    try { startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS)); }
                    catch (Exception ignored) { }
                } else {
                    // Reserved content destinations; this starter does not bundle TV/movie streams.
                    invalidate();
                }
                return;
            }
            if (selectedApp < 0 || selectedApp >= apps.size()) return;
            AppEntry a = apps.get(selectedApp);
            Intent launch = getPackageManager().getLaunchIntentForPackage(a.packageName);
            if (launch == null && a.activityName != null) {
                launch = new Intent(Intent.ACTION_MAIN);
                launch.setClassName(a.packageName, a.activityName);
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            }
            if (launch != null) {
                try { startActivity(launch); } catch (Exception ignored) { }
            }
        }

        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);
            float w = getWidth(), h = getHeight();
            if (w < 1 || h < 1) return;
            drawBackground(c, w, h);
            drawTop(c, w, h);
            drawHero(c, w, h);
            drawDock(c, w, h);
            drawShortcuts(c, w, h);
            drawFooter(c, w, h);
        }

        private void drawBackground(Canvas c, float w, float h) {
            p.setShader(new LinearGradient(0, 0, w, h,
                new int[]{0xFF080A16, 0xFF100D24, 0xFF061521}, null, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, h, p); p.setShader(null);
            p.setShader(new RadialGradient(w*.79f, h*.12f, w*.53f,
                new int[]{0x553B75FF, 0x221B397C, 0x00081020}, null, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, h, p); p.setShader(null);
            p.setShader(new RadialGradient(w*.10f, h*.72f, w*.38f,
                new int[]{0x443E16A8, 0x00101020}, null, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, h, p); p.setShader(null);
        }

        private void drawTop(Canvas c, float w, float h) {
            text(c, "ANANTA", w*.055f, h*.075f, 25, Color.WHITE, true);
            p.setShader(new LinearGradient(w*.055f, h*.09f, w*.13f, h*.09f, purple, cyan, Shader.TileMode.CLAMP));
            c.drawRoundRect(w*.055f, h*.087f, w*.13f, h*.094f, 4, 4, p); p.setShader(null);
            text(c, "TV", w*.14f, h*.075f, 20, cyan, true);
            String time = new SimpleDateFormat("EEE, MMM d  •  h:mm a", Locale.getDefault()).format(new Date());
            textRight(c, time, w*.945f, h*.075f, 15, 0xFFD5D9EB, false);
        }

        private void drawHero(Canvas c, float w, float h) {
            float l=w*.055f, t=h*.14f, rr=w*.945f, b=h*.475f;
            p.setShader(new LinearGradient(l,t,rr,b,
                new int[]{0xFF29234F,0xFF151D3D,0xFF10303D},null,Shader.TileMode.CLAMP));
            c.drawRoundRect(l,t,rr,b,28,28,p); p.setShader(null);
            p.setShader(new RadialGradient(rr-w*.17f,t+h*.13f,w*.30f,
                new int[]{0xBB824CFF,0x553A64FF,0x0030DDF5},null,Shader.TileMode.CLAMP));
            c.drawRoundRect(l,t,rr,b,28,28,p); p.setShader(null);
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1.5f); p.setColor(0x6677CFFF);
            c.drawRoundRect(l,t,rr,b,28,28,p); p.setStyle(Paint.Style.FILL);
            text(c,"A MORE PERSONAL TV EXPERIENCE",l+w*.035f,t+h*.10f,14,cyan,true);
            text(c,"Everything you love. One place.",l+w*.035f,t+h*.165f,30,Color.WHITE,true);
            text(c,"Your entertainment starts here.",l+w*.035f,t+h*.22f,18,0xFFD0D5E8,false);
            drawPill(c,"EXPLORE YOUR APPS",l+w*.035f,t+h*.265f,w*.15f,h*.045f);
            p.setShader(new RadialGradient(rr-w*.16f,t+h*.15f,w*.105f,
                new int[]{0xEEFFFFFF,0xAA8C7BFF,0x5546D9FF,0x0036B8FF},null,Shader.TileMode.CLAMP));
            c.drawCircle(rr-w*.16f,t+h*.15f,w*.105f,p); p.setShader(null);
            // thin decorative light streak
            p.setShader(new LinearGradient(rr-w*.30f,t+h*.30f,rr-w*.02f,t+h*.28f,
                new int[]{0x007FEAFF,0xAA7FEAFF,0x00FFFFFF},null,Shader.TileMode.CLAMP));
            c.drawRoundRect(rr-w*.30f,t+h*.29f,rr-w*.02f,t+h*.295f,3,3,p); p.setShader(null);
        }

        private void drawDock(Canvas c, float w, float h) {
            text(c,"YOUR APPS",w*.06f,h*.535f,16,0xFFE4E8F8,true);
            textRight(c,apps.size()+" INSTALLED",w*.94f,h*.535f,12,0xFF9AA5C2,false);
            if (apps.isEmpty()) {
                text(c,"No launchable apps found. Press MENU to refresh.",w*.06f,h*.63f,18,Color.WHITE,false);
                return;
            }
            int count=Math.min(visibleApps,apps.size());
            int first=Math.max(0,Math.min(selectedApp-count/2,apps.size()-count));
            float gap=w*.014f, cardW=(w*.88f-gap*(count-1))/count, cardH=h*.145f;
            float start=w*.06f, top=h*.57f;
            for(int slot=0;slot<count;slot++) {
                int appIndex=first+slot;
                AppEntry a=apps.get(appIndex);
                boolean focused=row==0 && appIndex==selectedApp;
                float scale=focused?focusProgress:1f;
                float cx=start+slot*(cardW+gap)+cardW/2f;
                float cy=top+cardH/2f;
                c.save(); c.scale(scale,scale,cx,cy);
                p.setShader(new LinearGradient(cx-cardW/2,top,cx+cardW/2,top+cardH,
                    focused?new int[]{0xFF37365F,0xFF193248}:new int[]{0xCC202438,0xCC111927},null,Shader.TileMode.CLAMP));
                c.drawRoundRect(cx-cardW/2,top,cx+cardW/2,top+cardH,18,18,p); p.setShader(null);
                p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(focused?3f:1f);
                p.setColor(focused?cyan:0x445F729D);
                c.drawRoundRect(cx-cardW/2,top,cx+cardW/2,top+cardH,18,18,p); p.setStyle(Paint.Style.FILL);
                if(focused) {
                    p.setShader(new LinearGradient(cx-cardW*.28f,top+cardH+5,cx+cardW*.28f,top+cardH+5,purple,cyan,Shader.TileMode.CLAMP));
                    c.drawRoundRect(cx-cardW*.28f,top+cardH+5,cx+cardW*.28f,top+cardH+8,3,3,p); p.setShader(null);
                }
                float iconSize=Math.min(cardH*.48f,cardW*.36f);
                float ix=cx-iconSize/2f, iy=top+cardH*.11f;
                try { a.icon.setBounds((int)ix,(int)iy,(int)(ix+iconSize),(int)(iy+iconSize)); a.icon.draw(c); }
                catch(Exception ignored) { }
                textCentered(c,ellipsize(a.label,13),cx,top+cardH*.80f,Math.min(14,cardW*.082f),focused?Color.WHITE:0xFFCFD5E7,focused);
                c.restore();
            }
            if(apps.size()>count) {
                String range=(first+1)+"–"+(first+count)+" / "+apps.size()+" apps";
                textRight(c,range,w*.94f,h*.755f,12,0xFF8F9BB8,false);
            }
        }

        private void drawShortcuts(Canvas c, float w, float h) {
            float y=h*.795f;
            text(c,"DISCOVER",w*.06f,y-12,13,0xFF9BA7C6,true);
            float gap=w*.014f, cardW=w*.205f, cardH=h*.075f, x0=w*.06f;
            for(int i=0;i<shortcuts.length;i++) {
                float x=x0+i*(cardW+gap); boolean focused=row==1 && selectedShortcut==i;
                p.setShader(new LinearGradient(x,y,x+cardW,y+cardH,
                    focused?new int[]{0xAA39325F,0xAA164155}:new int[]{0x551C2B47,0x33101D30},null,Shader.TileMode.CLAMP));
                c.drawRoundRect(x,y,x+cardW,y+cardH,14,14,p); p.setShader(null);
                p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(focused?2.5f:1f); p.setColor(focused?purple:0x445F729D);
                c.drawRoundRect(x,y,x+cardW,y+cardH,14,14,p); p.setStyle(Paint.Style.FILL);
                textCentered(c,shortcuts[i],x+cardW/2,y+cardH*.62f,14,Color.WHITE,focused);
            }
        }

        private void drawFooter(Canvas c,float w,float h) {
            text(c,"◀ ▶ Navigate     ▲ ▼ Switch row     OK Open     MENU Refresh",w*.06f,h*.96f,12,0xFF7F8BA8,false);
            textRight(c,"ANANTA TV  •  PREVIEW",w*.94f,h*.96f,11,0xFF66728F,false);
        }

        private void drawPill(Canvas c,String label,float x,float y,float ww,float hh) {
            p.setShader(new LinearGradient(x,y,x+ww,y+hh,purple,cyan,Shader.TileMode.CLAMP));
            c.drawRoundRect(x,y,x+ww,y+hh,hh/2,hh/2,p); p.setShader(null);
            textCentered(c,label,x+ww/2,y+hh*.66f,12,Color.WHITE,true);
        }
        private void text(Canvas c,String s,float x,float y,float size,int color,boolean bold) {
            p.setShader(null); p.setStyle(Paint.Style.FILL); p.setColor(color); p.setTextSize(size);
            p.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL)); c.drawText(s,x,y,p);
        }
        private void textRight(Canvas c,String s,float x,float y,float size,int color,boolean bold) {
            p.setTextSize(size); p.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));
            text(c,s,x-p.measureText(s),y,size,color,bold);
        }
        private void textCentered(Canvas c,String s,float x,float y,float size,int color,boolean bold) {
            p.setTextSize(size); p.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));
            text(c,s,x-p.measureText(s)/2f,y,size,color,bold);
        }
        private String ellipsize(String s,int max) {
            if(s==null) return "";
            return s.length()<=max?s:s.substring(0,Math.max(1,max-1))+"…";
        }
    }

    private static class AppEntry {
        final String label, packageName, activityName;
        final Drawable icon;
        AppEntry(String label,String packageName,Drawable icon,String activityName) {
            this.label=label; this.packageName=packageName; this.icon=icon; this.activityName=activityName;
        }
    }
}
