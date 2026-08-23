package space.aapp.liberadorespacio;

import android.app.Activity;
import android.app.AppOpsManager;
import android.app.usage.StorageStats;
import android.app.usage.StorageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Process;
import android.os.StatFs;
import android.os.storage.StorageManager;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private LinearLayout appsContainer;
    private TextView storageText, permissionText;
    private Button analyzeButton;
    private ProgressBar loading;

    private int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }
    private GradientDrawable rounded(int color, float radius) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d;
    }
    private TextView text(String value, float sp, int color, boolean bold) {
        TextView v = new TextView(this); v.setText(value); v.setTextSize(sp); v.setTextColor(color);
        if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD); return v;
    }
    private Button button(String label, boolean primary) {
        Button b = new Button(this); b.setText(label); b.setAllCaps(false); b.setTextSize(14);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setTextColor(primary ? Color.WHITE : Color.rgb(31,122,90));
        b.setBackground(rounded(primary ? Color.rgb(31,122,90) : Color.rgb(232,244,239), 14)); return b;
    }

    @Override public void onCreate(Bundle b) { super.onCreate(b); buildUi(); refreshStorage(); refreshPermission(); }
    @Override protected void onResume() { super.onResume(); if (permissionText != null) refreshPermission(); }

    private void buildUi() {
        int dark=Color.rgb(24,28,32), muted=Color.rgb(98,105,112);
        ScrollView scroll=new ScrollView(this); scroll.setBackgroundColor(Color.rgb(247,248,250));
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(18),dp(24),dp(18),dp(32)); scroll.addView(root);
        root.addView(text("Liberador de espacio",28,dark,true));
        TextView sub=text("Encontrá qué ocupa tu teléfono y liberá almacenamiento con pocos toques.",15,muted,false); sub.setPadding(0,dp(6),0,0); root.addView(sub);
        LinearLayout card=new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); card.setPadding(dp(16),dp(16),dp(16),dp(16)); card.setBackground(rounded(Color.WHITE,18));
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2); cp.topMargin=dp(18); root.addView(card,cp);
        storageText=text("Calculando almacenamiento…",18,dark,true); card.addView(storageText);
        Button manage=button("Liberar espacio con Android",true); LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,-2); bp.topMargin=dp(14); card.addView(manage,bp); manage.setOnClickListener(v->openStorage());
        TextView title=text("Aplicaciones que más ocupan",21,dark,true); LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(-1,-2); tp.topMargin=dp(24); root.addView(title,tp);
        permissionText=text("",13,muted,false); root.addView(permissionText);
        analyzeButton=button("Analizar aplicaciones",true); LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(-1,-2); ap.topMargin=dp(12); root.addView(analyzeButton,ap);
        analyzeButton.setOnClickListener(v->{ if(!hasUsageAccess()) openUsageAccess(); else loadApps(); });
        loading=new ProgressBar(this); loading.setVisibility(View.GONE); root.addView(loading);
        appsContainer=new LinearLayout(this); appsContainer.setOrientation(LinearLayout.VERTICAL); LinearLayout.LayoutParams alp=new LinearLayout.LayoutParams(-1,-2); alp.topMargin=dp(12); root.addView(appsContainer,alp);
        TextView note=text("Android protege los datos de otras apps: para limpiar caché o datos, esta herramienta abre directamente la pantalla correcta de cada aplicación.",12,muted,false); LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(-1,-2); np.topMargin=dp(20); root.addView(note,np);
        setContentView(scroll);
    }

    private void refreshStorage(){
        StatFs s=new StatFs(Environment.getDataDirectory().getPath()); long total=s.getTotalBytes(), free=s.getAvailableBytes();
        storageText.setText(format(total-free)+" usados de "+format(total)+" · "+format(free)+" libres");
    }
    private boolean hasUsageAccess(){
        AppOpsManager a=(AppOpsManager)getSystemService(Context.APP_OPS_SERVICE); return a!=null && a.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,Process.myUid(),getPackageName())==AppOpsManager.MODE_ALLOWED;
    }
    private void refreshPermission(){ boolean ok=hasUsageAccess(); permissionText.setText(ok?"Acceso habilitado. Podés ordenar las apps por tamaño.":"Habilitá “Acceso de uso” una sola vez para medir otras aplicaciones."); analyzeButton.setText(ok?"Analizar aplicaciones":"Habilitar acceso y analizar"); }
    private void openUsageAccess(){ try{Intent i=new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS); i.setData(Uri.parse("package:"+getPackageName())); startActivity(i);}catch(Exception e){startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));} }
    private void openStorage(){ try{startActivity(new Intent(StorageManager.ACTION_MANAGE_STORAGE));}catch(Exception e){startActivity(new Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS));} }

    private void loadApps(){
        loading.setVisibility(View.VISIBLE); analyzeButton.setEnabled(false); appsContainer.removeAllViews();
        executor.execute(()->{
            List<AppRow> rows=new ArrayList<>(); PackageManager pm=getPackageManager(); StorageStatsManager sm=(StorageStatsManager)getSystemService(Context.STORAGE_STATS_SERVICE);
            try{
                for(ApplicationInfo ai:pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))){
                    if(ai.packageName.equals(getPackageName())) continue;
                    boolean system=(ai.flags & ApplicationInfo.FLAG_SYSTEM)!=0, updated=(ai.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)!=0; if(system&&!updated) continue;
                    long total=0,cache=0; try{StorageStats st=sm.queryStatsForPackage(StorageManager.UUID_DEFAULT,ai.packageName,Process.myUserHandle()); total=st.getAppBytes()+st.getDataBytes()+st.getCacheBytes(); cache=st.getCacheBytes();}catch(Exception ignored){}
                    rows.add(new AppRow(pm.getApplicationLabel(ai).toString(),ai.packageName,total,cache));
                }
            }catch(Exception ignored){}
            rows.sort(Comparator.comparingLong((AppRow r)->r.total).reversed());
            runOnUiThread(()->{loading.setVisibility(View.GONE); analyzeButton.setEnabled(true); int n=0; for(AppRow r:rows){if(n++>=40)break; addApp(r);} });
        });
    }
    private void addApp(AppRow r){
        LinearLayout c=new LinearLayout(this); c.setOrientation(LinearLayout.VERTICAL); c.setPadding(dp(14),dp(12),dp(14),dp(12)); c.setBackground(rounded(Color.WHITE,16)); LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2); p.bottomMargin=dp(9); appsContainer.addView(c,p);
        c.addView(text(r.name,16,Color.rgb(24,28,32),true)); c.addView(text((r.total>0?format(r.total):"tamaño no disponible")+(r.cache>0?" · caché "+format(r.cache):""),13,Color.rgb(98,105,112),false));
        LinearLayout actions=new LinearLayout(this); actions.setOrientation(LinearLayout.HORIZONTAL); c.addView(actions);
        Button manage=button("Gestionar",true); LinearLayout.LayoutParams a=new LinearLayout.LayoutParams(0,-2,1); actions.addView(manage,a); manage.setOnClickListener(v->openApp(r.pkg));
        Button uninstall=button("Desinstalar",false); LinearLayout.LayoutParams u=new LinearLayout.LayoutParams(0,-2,1); u.leftMargin=dp(8); actions.addView(uninstall,u); uninstall.setOnClickListener(v->{Intent i=new Intent(Intent.ACTION_DELETE,Uri.parse("package:"+r.pkg)); startActivity(i);});
    }
    private void openApp(String pkg){ try{startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+pkg)));}catch(Exception e){Toast.makeText(this,"No pude abrir la app",Toast.LENGTH_SHORT).show();} }
    private String format(long b){ if(b<1024)return b+" B"; double k=b/1024d;if(k<1024)return String.format(Locale.getDefault(),"%.1f KB",k);double m=k/1024d;if(m<1024)return String.format(Locale.getDefault(),"%.1f MB",m);return String.format(Locale.getDefault(),"%.2f GB",m/1024d); }
    static class AppRow{String name,pkg;long total,cache;AppRow(String n,String p,long t,long c){name=n;pkg=p;total=t;cache=c;}}
}
