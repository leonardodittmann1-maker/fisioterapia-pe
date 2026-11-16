package com.swipeclean.gallery;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.usage.StorageStatsManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.provider.DocumentsContract.Document;
import android.provider.Settings;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.text.format.Formatter;
import android.util.Size;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class DeviceReviewActivity extends Activity {
    static final String EXTRA_MODE="mode";
    private static final int CHOOSE_FILES=21, CHOOSE_FOLDER=22, UNINSTALL=23;
    private static final int PAD=14;
    private final ExecutorService scanner=Executors.newSingleThreadExecutor();
    private final ExecutorService previews=Executors.newSingleThreadExecutor();
    private final List<Entry> entries=new ArrayList<>();
    private final ArrayDeque<Entry> history=new ArrayDeque<>();
    private final ArrayDeque<Entry> uninstallQueue=new ArrayDeque<>();
    private SharedPreferences prefs;
    private Set<String> keep,remove,files,folders;
    private Entry current,undoFocus,uninstalling;
    private TextView info,title,detail,caption,feedback,review,undo,keepButton,removeButton,source;
    private ImageView preview;
    private FrameLayout card;
    private boolean apps, loading, applying, includeSystem, downloads;
    private int scanVersion, previewVersion, sortMode, fileFilter;
    private float downX, downY;

    private record Entry(String key, String name, String location, String mime, long bytes,
                         long changed, long lastUsed, Uri uri, File file, boolean deletable) {}

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        apps="apps".equals(getIntent().getStringExtra(EXTRA_MODE));
        prefs=getSharedPreferences(apps?"review_apps":"review_files",MODE_PRIVATE);
        keep=loadSet("keep");
        remove=loadSet("remove");
        files=loadSet("files");
        folders=loadSet("folders");
        downloads=prefs.getBoolean("downloads",false);
        includeSystem=prefs.getBoolean("system",false);
        sortMode=prefs.getInt("sort",0);
        fileFilter=prefs.getInt("filter",0);
        build();
    }

    @Override protected void onResume() {
        super.onResume();
        if(!applying)reload();
    }

    @Override protected void onDestroy() {
        scanner.shutdown();
        previews.shutdownNow();
        super.onDestroy();
    }

    private Set<String> loadSet(String name) {
        return new HashSet<>(prefs.getStringSet(name,Collections.emptySet()));
    }

    private void saveDecisions() {
        prefs.edit().putStringSet("keep",new HashSet<>(keep))
            .putStringSet("remove",new HashSet<>(remove)).apply();
    }

    private int dp(float value) {
        return Math.round(value*getResources().getDisplayMetrics().density);
    }

    private GradientDrawable background(int color,int radius) {
        GradientDrawable shape=new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(radius));
        return shape;
    }

    private TextView text(String value,int size,int color,boolean bold) {
        TextView view=new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER_VERTICAL);
        if(bold)view.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        return view;
    }

    private TextView button(String value,int color,Runnable action) {
        TextView view=text(value,13,MainActivity.TEXT,true);
        view.setGravity(Gravity.CENTER);
        view.setMinHeight(dp(46));
        view.setPadding(dp(9),dp(7),dp(9),dp(7));
        view.setBackground(background(color,15));
        view.setOnClickListener(v->action.run());
        return view;
    }

    private void addSplit(LinearLayout row,View left,View right) {
        row.addView(left,new LinearLayout.LayoutParams(0,-2,1));
        LinearLayout.LayoutParams other=new LinearLayout.LayoutParams(0,-2,1);
        other.leftMargin=dp(8);
        row.addView(right,other);
    }

    private LinearLayout row(LinearLayout parent,int margin) {
        LinearLayout row=new LinearLayout(this);
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);
        params.topMargin=dp(margin);
        parent.addView(row,params);
        return row;
    }

    private void build() {
        LinearLayout page=new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(MainActivity.BG);
        page.setPadding(dp(PAD),dp(10),dp(PAD),dp(PAD));
        MainActivity.insetPage(page);
        setContentView(page);
        LinearLayout header=row(page,0);
        header.addView(button("‹ Voltar",MainActivity.PANEL,this::finish));
        TextView heading=text(apps?"Apps instalados":"Arquivos e PDFs",23,MainActivity.TEXT,true);
        LinearLayout.LayoutParams headingParams=new LinearLayout.LayoutParams(0,-1,1);
        headingParams.leftMargin=dp(12);
        header.addView(heading,headingParams);
        source=button(apps?"Fonte e acesso":"Escolher arquivos",MainActivity.PANEL,this::chooseSource);
        String sortLabel=apps?"Ordenar":"Filtro e ordem";
        addSplit(row(page,10),source,button(sortLabel,MainActivity.PANEL,this::chooseSort));
        info=text("",13,MainActivity.MUTED,false);
        page.addView(info,new LinearLayout.LayoutParams(-1,dp(37)));

        card=new FrameLayout(this);
        card.setBackground(background(MainActivity.PANEL,22));
        card.setClipToOutline(true);
        page.addView(card,new LinearLayout.LayoutParams(-1,0,1));
        card.setOnTouchListener((v,event)->onSwipe(event));
        preview=new ImageView(this);
        preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int inset=dp(apps?74:24);
        preview.setPadding(inset,dp(37),inset,dp(106));
        card.addView(preview,new FrameLayout.LayoutParams(-1,-1));

        caption=text("",13,MainActivity.TEXT,true);
        caption.setBackground(background(0xea11192a,9));
        caption.setPadding(dp(9),dp(5),dp(9),dp(5));
        FrameLayout.LayoutParams tag=new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.START);
        tag.setMargins(dp(10),dp(12),0,0);
        card.addView(caption,tag);
        TextView open=button(apps?"Detalhes":"Abrir",0xee354161,this::openCurrent);
        FrameLayout.LayoutParams openParams=new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.END);
        openParams.setMargins(0,dp(9),dp(10),0);
        card.addView(open,openParams);
        feedback=text("",14,MainActivity.TEXT,true);
        feedback.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams notice=new FrameLayout.LayoutParams(-1,dp(31),Gravity.TOP);
        notice.topMargin=dp(53);
        card.addView(feedback,notice);

        LinearLayout labels=new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setBackground(background(0xf012192b,16));
        labels.setPadding(dp(12),dp(9),dp(12),dp(9));
        FrameLayout.LayoutParams bottom=new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM);
        bottom.setMargins(dp(8),0,dp(8),dp(8));
        card.addView(labels,bottom);
        title=text("",18,MainActivity.TEXT,true);
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        detail=text("",13,MainActivity.MUTED,false);
        detail.setMaxLines(3);
        detail.setEllipsize(TextUtils.TruncateAt.END);
        labels.addView(title);
        labels.addView(detail);

        removeButton=button(apps?"← Marcar para desinstalar":"← Descartar",0xff793148,()->decide(true));
        keepButton=button("Manter →",0xff125d53,()->decide(false));
        addSplit(row(page,11),removeButton,keepButton);
        LinearLayout actions=row(page,9);
        review=button("Revisar descartes",0xff374d97,this::review);
        actions.addView(review,new LinearLayout.LayoutParams(0,-2,1));
        undo=button("↶ Desfazer",MainActivity.PANEL,this::undo);
        LinearLayout.LayoutParams undoParams=new LinearLayout.LayoutParams(-2,-2);
        undoParams.leftMargin=dp(8);
        actions.addView(undo,undoParams);
        render();
    }

    private void chooseSource() {
        if(apps) {
            String[] options={includeSystem?"Ocultar apps de sistema":"Mostrar apps de sistema",
                "Autorizar tamanho e último uso","Recomeçar decisões"};
            new AlertDialog.Builder(this).setTitle("Apps instalados").setItems(options,(d,which)->{
                if(which==0) {
                    includeSystem=!includeSystem;
                    prefs.edit().putBoolean("system",includeSystem).apply();
                    reload();
                } else if(which==1)launch(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS),0);
                else reset();
            }).show();
            return;
        }
        String[] options={"Selecionar arquivos","Autorizar uma pasta",
            downloads?"Deixar de examinar Downloads":"Examinar Downloads","Recomeçar decisões"};
        new AlertDialog.Builder(this).setTitle("Arquivos e pastas").setItems(options,(d,which)->{
            if(which==0) {
                Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*");
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);
                launch(intent,CHOOSE_FILES);
            } else if(which==1) {
                launch(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE),CHOOSE_FOLDER);
            } else if(which==2) {
                downloads=!downloads;
                prefs.edit().putBoolean("downloads",downloads).apply();
                if(downloads&&!Environment.isExternalStorageManager()) {
                    new AlertDialog.Builder(this).setTitle("Examinar Downloads")
                        .setMessage("O Android pede acesso aos arquivos compartilhados para examinar a pasta Downloads inteira. Também é possível selecionar arquivos manualmente.")
                        .setNegativeButton("Voltar",null).setPositiveButton("Abrir configuração",(a,b)->
                            launch(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                Uri.parse("package:"+getPackageName())),0)).show();
                } else reload();
            } else reset();
        }).show();
    }

    private void reset() {
        new AlertDialog.Builder(this).setTitle("Recomeçar decisões?")
            .setMessage("Isso limpa as marcações deste modo.")
            .setNegativeButton("Voltar",null).setPositiveButton("Recomeçar",(d,w)->{
                keep.clear();
                remove.clear();
                history.clear();
                undoFocus=null;
                saveDecisions();
                render();
            }).show();
    }

    private void chooseSort() {
        if(apps) {
            showOrder(new String[]{"Nome","Maior tamanho","Usados há mais tempo"});
            return;
        }
        String[] types={"Todos","PDFs","Documentos e planilhas","ZIP e compactados","Áudio","APK",
            "Ordenar arquivos"};
        new AlertDialog.Builder(this).setTitle("Filtro e ordem").setItems(types,(d,which)->{
            if(which==types.length-1)showOrder(new String[]{"Nome","Maior tamanho","Mais antigos"});
            else {
                fileFilter=which;
                prefs.edit().putInt("filter",fileFilter).apply();
                render();
            }
        }).show();
    }

    private void showOrder(String[] choices) {
        new AlertDialog.Builder(this).setTitle("Ordenar por").setSingleChoiceItems(choices,sortMode,(d,w)->{
            sortMode=w;
            prefs.edit().putInt("sort",w).apply();
            d.dismiss();
            sortEntries();
            render();
        }).show();
    }

    private void reload() {
        if(isDestroyed())return;
        int version=++scanVersion;
        loading=true;
        render();
        Set<String> selectedFiles=new HashSet<>(files),selectedFolders=new HashSet<>(folders);
        boolean scanDownloads=downloads&&Environment.isExternalStorageManager();
        boolean showSystem=includeSystem;
        scanner.execute(()->{
            List<Entry> rows=apps?findApps(showSystem):findFiles(selectedFiles,selectedFolders,scanDownloads);
            runOnUiThread(()->{
                if(isDestroyed()||version!=scanVersion)return;
                entries.clear();
                entries.addAll(rows);
                sortEntries();
                loading=false;
                render();
            });
        });
    }

    private List<Entry> findApps(boolean showSystem) {
        PackageManager packages=getPackageManager();
        List<Entry> found=new ArrayList<>();
        long now=System.currentTimeMillis();
        Map<String,UsageStats> usage=Collections.emptyMap();
        try {
            usage=getSystemService(UsageStatsManager.class).queryAndAggregateUsageStats(
                now-180L*24*60*60*1000,now);
        } catch(RuntimeException ignored){}
        StorageStatsManager sizes=getSystemService(StorageStatsManager.class);
        for(PackageInfo pkg:packages.getInstalledPackages(0)) {
            String name=pkg.packageName;
            ApplicationInfo app=pkg.applicationInfo;
            if(app==null||name.equals(getPackageName()))continue;
            boolean system=(app.flags&ApplicationInfo.FLAG_SYSTEM)!=0;
            if(system&&!showSystem)continue;
            long bytes=-1;
            try {
                android.app.usage.StorageStats size=sizes.queryStatsForPackage(
                    app.storageUuid,name,android.os.Process.myUserHandle());
                bytes=size.getAppBytes()+size.getDataBytes();
            } catch(IOException|RuntimeException|PackageManager.NameNotFoundException ignored){}
            UsageStats stats=usage.get(name);
            long lastUsed=stats==null?0:stats.getLastTimeUsed();
            found.add(new Entry(name+":"+pkg.firstInstallTime,
                packages.getApplicationLabel(app).toString(),name,"app",bytes,pkg.firstInstallTime,
                lastUsed,Uri.parse("package:"+name),null,!system));
        }
        return found;
    }

    private List<Entry> findFiles(Set<String> selected,Set<String> trees,boolean scanDownloads) {
        Map<String,Entry> found=new LinkedHashMap<>();
        Set<String> grants=new HashSet<>();
        getContentResolver().getPersistedUriPermissions().forEach(permission->{
            if(permission.isWritePermission())grants.add(permission.getUri().toString());
        });
        for(String raw:selected) {
            Uri uri=Uri.parse(raw);
            try(Cursor cursor=getContentResolver().query(uri,columns(),null,null,null)) {
                if(cursor!=null&&cursor.moveToFirst()) {
                    readDocument(cursor,uri,uri.getAuthority(),grants.contains(raw),found);
                }
            }catch(RuntimeException ignored){}
        }
        for(String raw:trees) {
            Uri tree=Uri.parse(raw);
            try {readTree(tree,grants.contains(raw),found);}
            catch(RuntimeException ignored){}
        }
        if(scanDownloads) {
            File root=Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            try {root=root.getCanonicalFile();}catch(IOException ignored){return new ArrayList<>(found.values());}
            ArrayDeque<File> dirs=new ArrayDeque<>();
            dirs.add(root);
            while(!dirs.isEmpty()) {
                File[] children=dirs.removeFirst().listFiles();
                if(children==null)continue;
                for(File child:children) {
                    if(Files.isSymbolicLink(child.toPath()))continue;
                    if(child.isDirectory()){dirs.add(child);continue;}
                    if(!child.isFile())continue;
                    String path=child.getAbsolutePath();
                    String relative=path.substring(root.getAbsolutePath().length()+1);
                    String key="local:"+Environment.DIRECTORY_DOWNLOADS+"/"+relative;
                    String ext=MimeTypeMap.getFileExtensionFromUrl(Uri.encode(child.getName(),"."));
                    String mime=MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.toLowerCase(Locale.ROOT));
                    Entry file=new Entry(key+":"+child.lastModified()+":"+child.length(),child.getName(),
                        child.getParent(),"".equals(mime)||mime==null?"application/octet-stream":mime,
                        child.length(),child.lastModified(),0,null,child,true);
                    found.putIfAbsent(key,file);
                }
            }
        }
        return new ArrayList<>(found.values());
    }

    private String[] columns() {
        return new String[]{Document.COLUMN_DOCUMENT_ID,Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE,Document.COLUMN_SIZE,Document.COLUMN_LAST_MODIFIED,Document.COLUMN_FLAGS};
    }

    private void readTree(Uri tree,boolean writable,Map<String,Entry> found) {
        record Folder(String id,String path) {}
        ArrayDeque<Folder> pending=new ArrayDeque<>();
        Set<String> visited=new HashSet<>();
        String root=DocumentsContract.getTreeDocumentId(tree);
        pending.add(new Folder(root,root.substring(root.indexOf(':')+1)));
        while(!pending.isEmpty()) {
            Folder folder=pending.removeFirst();
            if(!visited.add(folder.id))continue;
            Uri children=DocumentsContract.buildChildDocumentsUriUsingTree(tree,folder.id);
            try(Cursor rows=getContentResolver().query(children,columns(),null,null,null)) {
                if(rows==null)continue;
                while(rows.moveToNext()) {
                    String id=rows.getString(0);
                    if(Document.MIME_TYPE_DIR.equals(rows.getString(2))) {
                        pending.add(new Folder(id,folder.path+"/"+rows.getString(1)));
                    } else {
                        readDocument(rows,DocumentsContract.buildDocumentUriUsingTree(tree,id),
                            folder.path,writable,found);
                    }
                }
            }catch(RuntimeException ignored){}
        }
    }

    private void readDocument(Cursor row,Uri uri,String path,boolean writable,Map<String,Entry> found) {
        String id=row.getString(0);
        String name=row.getString(1),mime=row.getString(2);
        if(Document.MIME_TYPE_DIR.equals(mime)||id==null)return;
        long bytes=row.isNull(3)?-1:row.getLong(3),changed=row.getLong(4);
        boolean local="com.android.externalstorage.documents".equals(uri.getAuthority())&&id.startsWith("primary:");
        String key=local?"local:"+id.substring(8):uri.getAuthority()+":"+id;
        boolean deletable=writable&&(row.getInt(5)&Document.FLAG_SUPPORTS_DELETE)!=0;
        String folder=path;
        if(local) {
            int lastSlash=id.lastIndexOf('/');
            folder=lastSlash>=8?id.substring(8,lastSlash):"Armazenamento interno";
        }
        Entry item=new Entry(key+":"+changed+":"+bytes,name==null?"Arquivo":name,folder,
            mime==null?"application/octet-stream":mime,bytes,changed,0,uri,null,deletable);
        found.merge(key,item,(old,added)->old.deletable?old:added);
    }

    private void sortEntries() {
        Comparator<Entry> name=Comparator.comparing(Entry::name,String.CASE_INSENSITIVE_ORDER);
        Comparator<Entry> order=switch(sortMode) {
            case 1 -> Comparator.comparingLong(Entry::bytes).reversed().thenComparing(name);
            case 2 -> Comparator.comparingLong((Entry e)->{
                long date=apps?e.lastUsed:e.changed;
                return date==0?Long.MAX_VALUE:date;
            }).thenComparing(name);
            default -> name;
        };
        entries.sort(order);
    }

    private boolean matches(Entry e) {
        if(apps||fileFilter==0)return true;
        String name=e.name.toLowerCase(Locale.ROOT);
        return switch(fileFilter) {
            case 1 -> e.mime.equals("application/pdf")||name.endsWith(".pdf");
            case 2 -> name.matches(".*\\.(docx?|xlsx?|pptx?|odt|ods|csv|txt|rtf)$")||e.mime.startsWith("text/");
            case 3 -> name.matches(".*\\.(zip|rar|7z|gz|tar)$");
            case 4 -> e.mime.startsWith("audio/");
            case 5 -> name.endsWith(".apk");
            default -> true;
        };
    }

    private void render() {
        int stamp=++previewVersion;
        card.animate().cancel();
        card.setTranslationX(0);
        card.setRotation(0);
        List<Entry> shown=entries.stream().filter(this::matches).collect(Collectors.toList());
        long reviewed=shown.stream().filter(e->keep.contains(e.key)||remove.contains(e.key)).count();
        List<Entry> pending=markedEntries();
        info.setText(loading?"Carregando…":reviewed+" de "+shown.size()+" revisados");
        review.setText("Revisar descartes ("+pending.size()+")");
        undo.setEnabled(!applying&&!history.isEmpty());
        undo.setAlpha(undo.isEnabled()?1f:.45f);
        current=null;
        if(!applying&&!loading) {
            if(undoFocus!=null&&shown.contains(undoFocus))current=undoFocus;
            if(current==null) {
                for(Entry entry:shown) {
                    if(!keep.contains(entry.key)&&!remove.contains(entry.key)){current=entry;break;}
                }
            }
        }
        keepButton.setEnabled(current!=null);
        removeButton.setEnabled(current!=null&&current.deletable);
        removeButton.setAlpha(removeButton.isEnabled()?1f:.45f);
        preview.setImageDrawable(null);
        if(current==null) {
            caption.setText("");
            title.setText(applying?"Aplicando seleção…":loading?"Carregando…":shown.isEmpty()?"Nenhum item":"Revisão concluída");
            detail.setText(applying||loading?"":shown.isEmpty()
                ?(apps?"Veja as opções de fonte e acesso.":"Escolha arquivos ou pastas para começar.")
                :"Abra os descartes para confirmar sua seleção.");
            return;
        }
        Entry e=current;
        String size=e.bytes>=0?Formatter.formatShortFileSize(this,e.bytes):"Tamanho indisponível";
        title.setText(e.name);
        if(apps) {
            caption.setText(e.deletable?"APP":"SISTEMA");
            String use=e.lastUsed>0?"Último uso "+DateFormat.getDateFormat(this).format(new Date(e.lastUsed))
                :"Último uso indisponível";
            detail.setText(size+"  ·  "+use+"\n"+e.location);
            try {preview.setImageDrawable(getPackageManager().getApplicationIcon(e.location));}
            catch(PackageManager.NameNotFoundException ignored){}
        } else {
            boolean pdf="application/pdf".equals(e.mime)||e.name.toLowerCase(Locale.ROOT).endsWith(".pdf");
            String extension=MimeTypeMap.getFileExtensionFromUrl(Uri.encode(e.name,"."));
            caption.setText(pdf?"PDF":extension.isEmpty()?"ARQUIVO":extension.toUpperCase(Locale.ROOT));
            detail.setText(size+"  ·  "+e.location+(e.deletable?"":"\nSomente leitura"));
            previews.execute(()->{
                Bitmap thumbnail=preview(e,pdf);
                runOnUiThread(()->{
                    if(isDestroyed()||stamp!=previewVersion)return;
                    if(thumbnail!=null)preview.setImageBitmap(thumbnail);
                    else preview.setImageResource(android.R.drawable.ic_menu_agenda);
                });
            });
        }
    }

    private Bitmap preview(Entry entry,boolean pdf) {
        if(pdf) {
            try {
                ParcelFileDescriptor fd=entry.file!=null
                    ?ParcelFileDescriptor.open(entry.file,ParcelFileDescriptor.MODE_READ_ONLY)
                    :getContentResolver().openFileDescriptor(entry.uri,"r");
                if(fd==null)return null;
                try(fd;PdfRenderer renderer=new PdfRenderer(fd)) {
                    if(renderer.getPageCount()==0)return null;
                    try(PdfRenderer.Page page=renderer.openPage(0)) {
                        float scale=850f/Math.max(page.getWidth(),page.getHeight());
                        Bitmap bitmap=Bitmap.createBitmap(Math.max(1,Math.round(page.getWidth()*scale)),
                            Math.max(1,Math.round(page.getHeight()*scale)),Bitmap.Config.ARGB_8888);
                        bitmap.eraseColor(Color.WHITE);
                        page.render(bitmap,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                        return bitmap;
                    }
                }
            }catch(IOException|RuntimeException ignored){}
        } else if(entry.uri!=null) {
            try {return getContentResolver().loadThumbnail(entry.uri,new Size(700,700),null);}
            catch(IOException|RuntimeException ignored){}
        }
        return null;
    }

    private boolean onSwipe(MotionEvent event) {
        if(current==null)return false;
        switch(event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX=event.getRawX();
                downY=event.getRawY();
                return true;
            case MotionEvent.ACTION_MOVE:
                float delta=event.getRawX()-downX;
                card.setTranslationX(delta*.72f);
                card.setRotation(Math.max(-8,Math.min(8,delta/dp(33))));
                return true;
            case MotionEvent.ACTION_UP:
                float x=event.getRawX()-downX,y=event.getRawY()-downY;
                if(Math.abs(x)>dp(75)&&Math.abs(x)>Math.abs(y))decide(x<0);
                else card.animate().translationX(0).rotation(0).setDuration(140).start();
                return true;
            case MotionEvent.ACTION_CANCEL:
                card.animate().translationX(0).rotation(0).setDuration(140).start();
                return true;
            default:return true;
        }
    }

    private void decide(boolean discard) {
        if(current==null)return;
        if(discard&&!current.deletable) {
            render();
            flash(apps?"App de sistema":"Arquivo disponível só para leitura");
            return;
        }
        (discard?remove:keep).add(current.key);
        history.addLast(current);
        undoFocus=null;
        saveDecisions();
        render();
        flash(discard?"Marcado para revisão":"Mantido");
    }

    private void undo() {
        if(history.isEmpty())return;
        Entry previous=history.removeLast();
        keep.remove(previous.key);
        remove.remove(previous.key);
        undoFocus=previous;
        saveDecisions();
        render();
        flash("Desfeito");
    }

    private void flash(String message) {
        feedback.setText(message);
        feedback.postDelayed(()->{
            if(feedback.getText().toString().equals(message))feedback.setText("");
        },650);
    }

    private void openCurrent() {
        if(current==null)return;
        if(apps) {
            launch(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,current.uri),0);
            return;
        }
        try {
            Uri uri=current.file==null?current.uri:ReviewFileProvider.uri(getPackageName(),current.file);
            launch(new Intent(Intent.ACTION_VIEW).setDataAndType(uri,current.mime)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),0);
        }catch(IllegalArgumentException e){toast("O arquivo não está mais disponível.");}
    }

    private List<Entry> markedEntries() {
        return entries.stream().filter(e->remove.contains(e.key)&&e.deletable).collect(Collectors.toList());
    }

    private void review() {
        if(applying||loading)return;
        List<Entry> marked=markedEntries();
        if(marked.isEmpty()) {
            toast("Deslize para a esquerda para marcar itens.");
            return;
        }
        ScrollView scroll=new ScrollView(this);
        LinearLayout reviewRows=new LinearLayout(this);
        reviewRows.setOrientation(LinearLayout.VERTICAL);
        reviewRows.setPadding(dp(21),dp(4),dp(21),dp(4));
        scroll.addView(reviewRows);
        long bytes=marked.stream().mapToLong(e->Math.max(0,e.bytes)).sum();
        TextView help=text((bytes>0?Formatter.formatShortFileSize(this,bytes)+"  ·  ":"")+
            (apps?"O Android pedirá confirmação para cada app.":"A exclusão aqui é permanente."),14,0xff30394c,false);
        reviewRows.addView(help);
        ArrayList<Entry> selected=new ArrayList<>(marked);
        for(Entry e:marked) {
            android.widget.CheckBox check=new android.widget.CheckBox(this);
            check.setText(e.name+"\n"+e.location);
            check.setChecked(true);
            check.setOnCheckedChangeListener((v,checked)->{
                if(checked)selected.add(e);
                else selected.remove(e);
            });
            reviewRows.addView(check);
        }
        new AlertDialog.Builder(this).setTitle(apps?"Revisar desinstalações":"Revisar arquivos")
            .setView(scroll).setNegativeButton("Voltar",null)
            .setPositiveButton(apps?"Desinstalar":"Excluir arquivos",(d,w)->{
                if(selected.isEmpty())return;
                if(apps) {
                    applying=true;
                    uninstallQueue.clear();
                    uninstallQueue.addAll(selected);
                    render();
                    nextUninstall();
                } else deleteFiles(List.copyOf(selected));
            }).show();
    }

    private void nextUninstall() {
        uninstalling=uninstallQueue.pollFirst();
        if(uninstalling==null) {
            applying=false;
            saveDecisions();
            reload();
            return;
        }
        Intent request=new Intent(Intent.ACTION_UNINSTALL_PACKAGE,uninstalling.uri);
        request.putExtra(Intent.EXTRA_RETURN_RESULT,true);
        if(!launch(request,UNINSTALL)) {
            uninstallQueue.clear();
            nextUninstall();
        }
    }

    private void deleteFiles(List<Entry> selected) {
        applying=true;
        render();
        scanner.execute(()->{
            Set<String> done=new HashSet<>(),deletedUris=new HashSet<>();
            for(Entry e:selected) {
                try {
                    boolean deleted;
                    if(e.file!=null) {
                        deleted=e.file.isFile()&&!Files.isSymbolicLink(e.file.toPath())
                            &&e.file.getAbsoluteFile().equals(e.file.getCanonicalFile())
                            &&e.file.length()==e.bytes&&e.file.lastModified()==e.changed
                            &&Files.deleteIfExists(e.file.toPath());
                    } else {
                        deleted=stillSameDocument(e)&&DocumentsContract.deleteDocument(getContentResolver(),e.uri);
                    }
                    if(deleted) {
                        done.add(e.key);
                        if(e.uri!=null)deletedUris.add(e.uri.toString());
                    }
                } catch(IOException|RuntimeException ignored){}
            }
            runOnUiThread(()->{
                remove.removeAll(done);
                files.removeAll(deletedUris);
                prefs.edit().putStringSet("files",new HashSet<>(files)).apply();
                history.removeIf(e->done.contains(e.key));
                saveDecisions();
                if(isDestroyed())return;
                applying=false;
                toast(done.size()+" excluído(s). "+(selected.size()-done.size())+" não excluído(s).");
                reload();
            });
        });
    }

    private boolean stillSameDocument(Entry item) {
        try(Cursor row=getContentResolver().query(item.uri,columns(),null,null,null)) {
            return row!=null&&row.moveToFirst()&&item.name.equals(row.getString(1))
                &&(row.isNull(3)?-1:row.getLong(3))==item.bytes
                &&row.getLong(4)==item.changed;
        }
    }

    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if(request==UNINSTALL) {
            if(uninstalling!=null&&result==RESULT_OK) {
                remove.remove(uninstalling.key);
                history.remove(uninstalling);
                saveDecisions();
            }
            if(result!=RESULT_OK)uninstallQueue.clear();
            nextUninstall();
            return;
        }
        if(result!=RESULT_OK||data==null||(request!=CHOOSE_FILES&&request!=CHOOSE_FOLDER))return;
        if(request==CHOOSE_FILES) {
            ClipData clips=data.getClipData();
            if(clips!=null)for(int i=0;i<clips.getItemCount();i++)grant(clips.getItemAt(i).getUri(),data,files);
            if(data.getData()!=null)grant(data.getData(),data,files);
        } else if(request==CHOOSE_FOLDER&&data.getData()!=null)grant(data.getData(),data,folders);
        prefs.edit().putStringSet("files",new HashSet<>(files))
            .putStringSet("folders",new HashSet<>(folders)).apply();
        reload();
    }

    private void grant(Uri uri,Intent data,Set<String> destination) {
        int mode=data.getFlags()&(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        try {
            getContentResolver().takePersistableUriPermission(uri,mode);
            destination.add(uri.toString());
        } catch(SecurityException e){toast("O Android não concedeu acesso a "+uri.getLastPathSegment());}
    }

    private boolean launch(Intent intent,int request) {
        try {
            if(request>0)startActivityForResult(intent,request);
            else startActivity(intent);
            return true;
        } catch(ActivityNotFoundException|SecurityException e){
            toast("O Android não conseguiu abrir esta ação.");
            return false;
        }
    }

    private void toast(String message) {
        Toast.makeText(this,message,Toast.LENGTH_SHORT).show();
    }
}
