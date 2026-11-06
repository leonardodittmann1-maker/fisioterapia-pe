package com.swipeclean.gallery;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.app.PendingIntent;
import android.content.ClipData;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Environment;
import android.os.Looper;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Size;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.EditText;
import android.text.Editable;
import android.text.TextWatcher;
import java.util.LinkedHashMap;
import java.util.Map;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import org.json.JSONObject;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    static final int BG=0xff0b1020, PANEL=0xff182136, TEXT=0xfff3f6ff, MUTED=0xff9caaca, PINK=0xfffc537e, GREEN=0xff29d5a5, BLUE=0xff7a82ff;
    static final int REQ_PERMISSION=11, REQ_CLOUD=12, REQ_TRASH=13, REQ_INSTALL_SOURCE=14, REQ_MOVE=15;
    static final String VERSION_URL="https://raw.githubusercontent.com/leonardodittmann1-maker/fisioterapia-pe/swipe-clean-gallery-app/updates/version.json";
    static final String APK_URL="https://raw.githubusercontent.com/leonardodittmann1-maker/fisioterapia-pe/swipe-clean-gallery-app/downloads/SwipeClean.apk";
    final Handler ui=new Handler(Looper.getMainLooper());
    final ExecutorService worker=Executors.newSingleThreadExecutor();
    final ExecutorService coverWorker=Executors.newFixedThreadPool(2);
    // Atualizações não esperam as miniaturas/álbuns serem carregados.
    final ExecutorService updateWorker=Executors.newSingleThreadExecutor();
    final ArrayList<Item> localItems=new ArrayList<>(), cloudItems=new ArrayList<>();
    final ArrayDeque<Move> history=new ArrayDeque<>();
    Set<String> kept=new HashSet<>(), trash=new HashSet<>(), cloudKept=new HashSet<>(), cloudTrash=new HashSet<>();
    SharedPreferences prefs;
    LinearLayout page, filterBar;
    FrameLayout card;
    ImageView picture;
    LinearLayout feedbackBox;
    TextView feedbackIcon, feedbackMessage;
    Runnable hideFeedback;
    String undoFocusKey="";
    TextView status, headline, hint, badge, subtitle, trashButton, undoButton, sourceButton, allChip, photoChip, videoChip, updateButton;
    Uri pendingInstallUri;
    boolean checkingUpdate=false, verifyingDownload=false;
    boolean cloudMode=false, oldest=false, loading=false, movePending=false;
    int filter=0, imageVersion=0;
    float startX,startY;
    boolean swiping=false;
    Item displayed;

    static class Item {
        String key, name, mime, albumId, albumName, relativePath="";
        Uri uri;
        long size, when;
        boolean video, cloud;
        Item(String key, Uri uri, String name, String mime, long size, long when, boolean video, boolean cloud) {
            this.key=key; this.uri=uri; this.name=name; this.mime=mime;
            this.size=size; this.when=when; this.video=video; this.cloud=cloud;
            this.albumId=ALL_ALBUMS;this.albumName="Todos";
        }
        Item(String key,Uri uri,String name,String mime,long size,long when,
             boolean video,boolean cloud,String albumId,String albumName,String relativePath){
            this(key,uri,name,mime,size,when,video,cloud);
            this.albumId=albumId;this.albumName=albumName;this.relativePath=relativePath==null?"":relativePath;
        }
    }
    static final String ALL_ALBUMS="__all__";
    static class Album {
        String id,name,relativePath="";
        Item cover;
        int total,reviewed;
        Album(String id,String name){this.id=id;this.name=name;}
    }
    final ArrayList<Album> albums=new ArrayList<>();
    String selectedAlbum=ALL_ALBUMS;
    boolean albumTab=false,browsingAlbum=false;
    LinearLayout swipeSection,albumSection,albumRows,navBar,sourceRow,albumBrowseRow;
    TextView albumSelector,navSwipe,navAlbums,albumBrowseTitle,albumBrowseSwitch,albumBrowseBack;
    EditText albumSearch;
    static class Move {
        String key;
        boolean discarded, cloud;
        Move(String k,boolean d,boolean c){key=k;discarded=d;cloud=c;}
    }

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        prefs=getSharedPreferences("swipe_clean_state",MODE_PRIVATE);
        kept=new HashSet<>(prefs.getStringSet("keep",Collections.emptySet()));
        trash=new HashSet<>(prefs.getStringSet("trash",Collections.emptySet()));
        undoFocusKey=prefs.getString("undo_focus_key","");
        selectedAlbum=prefs.getString("selected_album",ALL_ALBUMS);
        browsingAlbum=prefs.getBoolean("browsing_album",false);
        oldest=prefs.getBoolean("oldest",false);
        filter=prefs.getInt("filter",0);
        buildUi();
        if (hasAccess()) scanLocal(); else requestAccess();
    }

    int dp(float n) { return Math.round(getResources().getDisplayMetrics().density*n); }
    GradientDrawable shape(int color,int radius) {
        GradientDrawable d=new GradientDrawable();
        d.setColor(color); d.setCornerRadius(dp(radius)); return d;
    }
    TextView label(String txt,int sp,int color,boolean bold) {
        TextView t=new TextView(this); t.setText(txt);t.setTextColor(color);t.setTextSize(sp);
        if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        t.setGravity(Gravity.CENTER_VERTICAL);return t;
    }
    TextView button(String txt,int color,int sp) {
        TextView t=label(txt,sp,TEXT,true);
        t.setGravity(Gravity.CENTER);t.setBackground(shape(color,18));
        t.setPadding(dp(12),dp(11),dp(12),dp(11));return t;
    }
    void buildUi() {
        page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(BG);page.setPadding(dp(15),dp(9),dp(15),dp(13));
        setContentView(page);
        LinearLayout titleLine=new LinearLayout(this);titleLine.setGravity(Gravity.CENTER_VERTICAL);
        page.addView(titleLine,new LinearLayout.LayoutParams(-1,dp(45)));
        TextView logo=label("♥  SwipeClean",24,TEXT,true);
        titleLine.addView(logo,new LinearLayout.LayoutParams(0,-1,1));
        TextView order=button("↕ Data",PANEL,12);
        order.setOnClickListener(v->{
            oldest=!oldest;prefs.edit().putBoolean("oldest",oldest).apply();
            sortLocal();buildAlbums();update();
        });
        titleLine.addView(order);
        LinearLayout updateRow=new LinearLayout(this);
        updateRow.setGravity(Gravity.CENTER_VERTICAL);
        updateRow.setPadding(0,dp(2),0,dp(2));
        page.addView(updateRow,new LinearLayout.LayoutParams(-1,dp(41)));
        TextView versionLabel=label("SwipeClean v1.6",11,MUTED,false);
        updateRow.addView(versionLabel,new LinearLayout.LayoutParams(0,-1,1));
        updateButton=button("↻ Verificar atualização",PANEL,12);
        updateRow.addView(updateButton,new LinearLayout.LayoutParams(-2,dp(37)));
        updateButton.setOnClickListener(v->checkForUpdates());

        swipeSection=new LinearLayout(this);
        swipeSection.setOrientation(LinearLayout.VERTICAL);
        page.addView(swipeSection,new LinearLayout.LayoutParams(-1,0,1));

        // Barra exclusiva da visualização de álbum, com ações sempre visíveis.
        albumBrowseRow=new LinearLayout(this);
        albumBrowseRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams browseParams=new LinearLayout.LayoutParams(-1,dp(50));
        browseParams.topMargin=dp(3);
        swipeSection.addView(albumBrowseRow,browseParams);
        albumBrowseBack=button("‹ Álbuns",PANEL,13);
        albumBrowseRow.addView(albumBrowseBack,new LinearLayout.LayoutParams(-2,dp(45)));
        albumBrowseBack.setOnClickListener(v->switchTab(true));
        albumBrowseTitle=label("Câmera",15,TEXT,true);
        albumBrowseTitle.setGravity(Gravity.CENTER);
        albumBrowseTitle.setSingleLine(true);
        albumBrowseTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams titleP=new LinearLayout.LayoutParams(0,dp(45),1);
        titleP.leftMargin=dp(5);titleP.rightMargin=dp(5);
        albumBrowseRow.addView(albumBrowseTitle,titleP);
        albumBrowseSwitch=button("📁 Mover",BLUE,13);
        albumBrowseRow.addView(albumBrowseSwitch,new LinearLayout.LayoutParams(-2,dp(45)));
        albumBrowseSwitch.setOnClickListener(v->chooseMoveDestination());

        sourceRow=new LinearLayout(this);
        sourceRow.setPadding(0,dp(7),0,dp(7));
        sourceRow.setGravity(Gravity.CENTER_VERTICAL);
        swipeSection.addView(sourceRow);
        sourceButton=button("▣  Galeria Samsung / celular",BLUE,13);
        sourceRow.addView(sourceButton,new LinearLayout.LayoutParams(0,dp(44),1));
        sourceButton.setOnClickListener(v->{
            if(cloudMode){ cloudMode=false;history.clear();update(); }
            else {new AlertDialog.Builder(this).setTitle("Galeria do celular")
                .setMessage("Esta área lê as fotos e os vídeos locais que aparecem na Galeria Samsung e no Google Fotos. Fotos somente na nuvem precisam ser selecionadas à parte.")
                .setPositiveButton("OK",null).show();}
        });
        TextView cloudBtn=button("☁ Google Fotos",PANEL,12);
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,dp(44),.78f);cp.leftMargin=dp(8);
        sourceRow.addView(cloudBtn,cp);
        cloudBtn.setOnClickListener(v->chooseCloud());
        albumSelector=button("▾  Todos os álbuns",PANEL,13);
        LinearLayout.LayoutParams selectorParams=new LinearLayout.LayoutParams(-1,dp(39));
        selectorParams.topMargin=dp(2);
        swipeSection.addView(albumSelector,selectorParams);
        albumSelector.setOnClickListener(v->quickAlbumPicker());
        status=label("Preparando galeria…",12,MUTED,false);
        swipeSection.addView(status,new LinearLayout.LayoutParams(-1,dp(29)));

        filterBar=new LinearLayout(this);filterBar.setGravity(Gravity.CENTER);
        swipeSection.addView(filterBar,new LinearLayout.LayoutParams(-1,dp(43)));
        allChip=button("Todas",PANEL,12);photoChip=button("Fotos",PANEL,12);videoChip=button("Vídeos",PANEL,12);
        addChip(allChip,0);addChip(photoChip,1);addChip(videoChip,2);

        card=new FrameLayout(this);card.setBackground(shape(PANEL,22));
        LinearLayout.LayoutParams cardp=new LinearLayout.LayoutParams(-1,0,1);cardp.topMargin=dp(10);cardp.bottomMargin=dp(10);
        swipeSection.addView(card,cardp);
        picture=new ImageView(this);picture.setScaleType(ImageView.ScaleType.FIT_CENTER);
        picture.setBackgroundColor(0xff111727);
        card.addView(picture,new FrameLayout.LayoutParams(-1,-1));
        picture.setOnTouchListener((v,e)->onSwipe(e));
        card.setOnTouchListener((v,e)->onSwipe(e));

        badge=label("",13,TEXT,true);badge.setGravity(Gravity.CENTER);
        badge.setPadding(dp(14),dp(8),dp(14),dp(8));badge.setBackground(shape(0xcc101827,13));
        FrameLayout.LayoutParams bp=new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.LEFT);
        bp.setMargins(dp(12),dp(12),0,0);card.addView(badge,bp);
        TextView open=button("⤢  Abrir",0xbb28334e,12);
        FrameLayout.LayoutParams op=new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.RIGHT);
        op.setMargins(0,dp(12),dp(12),0);card.addView(open,op);
        open.setOnClickListener(v->openDisplayed());

        LinearLayout footerInfo=new LinearLayout(this);footerInfo.setOrientation(LinearLayout.VERTICAL);
        footerInfo.setPadding(dp(10),dp(10),dp(10),dp(10));
        footerInfo.setBackground(shape(0xe3131b2b,18));
        FrameLayout.LayoutParams fp=new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM);fp.setMargins(dp(8),0,dp(8),dp(9));
        card.addView(footerInfo,fp);
        headline=label("Selecione as fotos",16,TEXT,true);headline.setSingleLine(true);headline.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        subtitle=label("Deslize para organizar",12,MUTED,false);
        footerInfo.addView(headline);footerInfo.addView(subtitle);

        // Confirmação breve de cada gesto ou botão, sem bloquear a próxima foto.
        feedbackBox=new LinearLayout(this);
        feedbackBox.setOrientation(LinearLayout.HORIZONTAL);
        feedbackBox.setGravity(Gravity.CENTER);
        feedbackBox.setPadding(dp(12),dp(7),dp(12),dp(7));
        feedbackBox.setVisibility(View.GONE);
        feedbackBox.setClickable(false);
        feedbackBox.setFocusable(false);
        feedbackIcon=label("♥",21,GREEN,true);
        feedbackIcon.setGravity(Gravity.CENTER);
        feedbackMessage=label("Mantida",11,TEXT,true);
        feedbackMessage.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams feedbackTextParams=new LinearLayout.LayoutParams(-2,-2);
        feedbackTextParams.leftMargin=dp(7);
        feedbackBox.addView(feedbackIcon,new LinearLayout.LayoutParams(-2,-2));
        feedbackBox.addView(feedbackMessage,feedbackTextParams);
        FrameLayout.LayoutParams feedbackParams=new FrameLayout.LayoutParams(
            -2,-2,Gravity.TOP|Gravity.CENTER_HORIZONTAL);
        feedbackParams.topMargin=dp(64);
        card.addView(feedbackBox,feedbackParams);

        hint=label("← DESCARTAR                                     MANTER →",10,MUTED,true);
        hint.setGravity(Gravity.CENTER);swipeSection.addView(hint,new LinearLayout.LayoutParams(-1,dp(26)));

        LinearLayout actions=new LinearLayout(this);actions.setGravity(Gravity.CENTER);
        swipeSection.addView(actions,new LinearLayout.LayoutParams(-1,dp(65)));
        TextView no=button("✕ Descartar",0xff793148,13);
        TextView yes=button("♥ Manter",0xff125d53,13);
        TextView move=button("📁 Mover",0xff354b9e,13);
        actions.addView(no,new LinearLayout.LayoutParams(0,dp(62),1));
        LinearLayout.LayoutParams gap1=new LinearLayout.LayoutParams(0,dp(62),1);gap1.leftMargin=dp(6);
        actions.addView(yes,gap1);
        LinearLayout.LayoutParams gap2=new LinearLayout.LayoutParams(0,dp(62),1);gap2.leftMargin=dp(6);
        actions.addView(move,gap2);
        no.setOnClickListener(v->decide(true));yes.setOnClickListener(v->decide(false));
        move.setOnClickListener(v->chooseMoveDestination());
        LinearLayout bottom=new LinearLayout(this);bottom.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams bottomParams=new LinearLayout.LayoutParams(-1,dp(53));bottomParams.topMargin=dp(10);
        swipeSection.addView(bottom,bottomParams);
        trashButton=button("🗑  Revisar descartes",PINK,13);
        bottom.addView(trashButton,new LinearLayout.LayoutParams(0,-1,1));
        trashButton.setOnClickListener(v->review());
        TextView undo=button("↶",PANEL,23);undoButton=undo;
        LinearLayout.LayoutParams up=new LinearLayout.LayoutParams(dp(48),-1);up.leftMargin=dp(7);
        bottom.addView(undo,up);undo.setOnClickListener(v->undo());
        TextView reset=button("⟳",PANEL,23);
        LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(dp(54),-1);rp.leftMargin=dp(8);
        bottom.addView(reset,rp);
        reset.setOnClickListener(v->resetDecisions());

        albumSection=new LinearLayout(this);
        albumSection.setOrientation(LinearLayout.VERTICAL);
        albumSection.setVisibility(View.GONE);
        page.addView(albumSection,new LinearLayout.LayoutParams(-1,0,1));
        TextView albumTitle=label("Álbuns da galeria",23,TEXT,true);
        albumSection.addView(albumTitle,new LinearLayout.LayoutParams(-1,dp(45)));
        TextView albumHelp=label("Toque em um álbum para abrir as fotos e decidir uma por uma.",12,MUTED,false);
        albumSection.addView(albumHelp,new LinearLayout.LayoutParams(-1,dp(33)));
        albumSearch=new EditText(this);
        albumSearch.setSingleLine(true);
        albumSearch.setTextColor(TEXT);
        albumSearch.setHintTextColor(MUTED);
        albumSearch.setTextSize(14);
        albumSearch.setHint("Buscar álbum...");
        albumSearch.setBackground(shape(PANEL,14));
        albumSearch.setPadding(dp(14),0,dp(14),0);
        LinearLayout.LayoutParams searchParams=new LinearLayout.LayoutParams(-1,dp(46));
        searchParams.bottomMargin=dp(10);
        albumSection.addView(albumSearch,searchParams);
        albumSearch.addTextChangedListener(new TextWatcher(){
            @Override public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            @Override public void onTextChanged(CharSequence s,int start,int before,int count){renderAlbums();}
            @Override public void afterTextChanged(Editable s){}
        });
        ScrollView albumScroll=new ScrollView(this);
        albumScroll.setFillViewport(true);
        albumSection.addView(albumScroll,new LinearLayout.LayoutParams(-1,0,1));
        albumRows=new LinearLayout(this);
        albumRows.setOrientation(LinearLayout.VERTICAL);
        albumScroll.addView(albumRows,new ScrollView.LayoutParams(-1,-2));

        navBar=new LinearLayout(this);
        navBar.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams navParams=new LinearLayout.LayoutParams(-1,dp(57));
        navParams.topMargin=dp(7);
        page.addView(navBar,navParams);
        navSwipe=button("♥  Deslizar",BLUE,15);
        navAlbums=button("▣  Álbuns",PANEL,15);
        navBar.addView(navSwipe,new LinearLayout.LayoutParams(0,-1,1));
        LinearLayout.LayoutParams albumsNavParams=new LinearLayout.LayoutParams(0,-1,1);
        albumsNavParams.leftMargin=dp(10);
        navBar.addView(navAlbums,albumsNavParams);
        navSwipe.setOnClickListener(v->{
            browsingAlbum=false;
            selectedAlbum=ALL_ALBUMS;
            cloudMode=false;
            filter=0;
            prefs.edit().putBoolean("browsing_album",false)
                .putString("selected_album",ALL_ALBUMS)
                .putInt("filter",0).apply();
            history.clear();
            switchTab(false);
        });
        navAlbums.setOnClickListener(v->switchTab(true));
        switchTab(false);
    }
    void addChip(TextView t,int value) {
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(38),1);
        if(value>0)p.leftMargin=dp(9);
        filterBar.addView(t,p);
        t.setOnClickListener(v->{filter=value;prefs.edit().putInt("filter",filter).apply();update();});
    }
    boolean hasAccess() {
        if(Build.VERSION.SDK_INT>=34) {
            return checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES)==PackageManager.PERMISSION_GRANTED
                ||checkSelfPermission(Manifest.permission.READ_MEDIA_VIDEO)==PackageManager.PERMISSION_GRANTED
                ||checkSelfPermission(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)==PackageManager.PERMISSION_GRANTED;
        }
        if(Build.VERSION.SDK_INT>=33)
            return checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES)==PackageManager.PERMISSION_GRANTED
                ||checkSelfPermission(Manifest.permission.READ_MEDIA_VIDEO)==PackageManager.PERMISSION_GRANTED;
        return checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)==PackageManager.PERMISSION_GRANTED;
    }
    void requestAccess() {
        status.setText("Permita acesso às mídias para organizar sua galeria.");
        if(Build.VERSION.SDK_INT>=34) requestPermissions(new String[]{
            Manifest.permission.READ_MEDIA_IMAGES,Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED},REQ_PERMISSION);
        else if(Build.VERSION.SDK_INT>=33) requestPermissions(new String[]{
            Manifest.permission.READ_MEDIA_IMAGES,Manifest.permission.READ_MEDIA_VIDEO},REQ_PERMISSION);
        else requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},REQ_PERMISSION);
    }
    @Override public void onRequestPermissionsResult(int req,String[] ps,int[] rs){
        super.onRequestPermissionsResult(req,ps,rs);
        if(req==REQ_PERMISSION) {
            if(hasAccess())scanLocal();
            else {
                update();
                new AlertDialog.Builder(this).setTitle("Acesso necessário")
                    .setMessage("Para ler a Galeria Samsung é necessário permitir o acesso às fotos e vídeos. Nada será enviado à internet.")
                    .setPositiveButton("Permitir", (d,w)->requestAccess())
                    .setNegativeButton("Agora não",null).show();
            }
        }
    }
    void scanLocal() {
        if(loading)return;
        loading=true;status.setText("Lendo as fotos e vídeos do celular…");
        worker.execute(()->{
            ArrayList<Item> found=new ArrayList<>();
            Uri files=MediaStore.Files.getContentUri("external");
            String[] cols={MediaStore.Files.FileColumns._ID, MediaStore.Files.FileColumns.DISPLAY_NAME,
                MediaStore.Files.FileColumns.MIME_TYPE,MediaStore.Files.FileColumns.SIZE,
                MediaStore.Files.FileColumns.DATE_ADDED,MediaStore.Files.FileColumns.MEDIA_TYPE,
                MediaStore.Images.Media.BUCKET_ID,MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
                MediaStore.MediaColumns.RELATIVE_PATH};
            String where=MediaStore.Files.FileColumns.MEDIA_TYPE+" IN (?,?)";
            if(Build.VERSION.SDK_INT>=30)where+=" AND "+MediaStore.MediaColumns.IS_TRASHED+"=0";
            String error=null;
            try(Cursor c=getContentResolver().query(files,cols,where,new String[]{
                String.valueOf(MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE),
                String.valueOf(MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO)},
                MediaStore.Files.FileColumns.DATE_ADDED+" DESC")) {
                if(c!=null) while(c.moveToNext()) {
                    long id=c.getLong(0);String name=c.getString(1),mime=c.getString(2);
                    long size=c.getLong(3), date=c.getLong(4);
                    int type=c.getInt(5);
                    String bucket=c.getString(6), bucketName=c.getString(7), relativePath=c.getString(8);
                    if(relativePath==null)relativePath="";
                    if(bucketName==null||bucketName.trim().isEmpty()){
                        String path=relativePath.endsWith("/")?relativePath.substring(0,relativePath.length()-1):relativePath;
                        int slash=path.lastIndexOf('/');
                        bucketName=path.isEmpty()?"Outros":path.substring(slash+1);
                    }
                    String albumKey="bucket:"+(bucket==null||bucket.isEmpty()?relativePath:bucket);
                    Uri itemUri=ContentUris.withAppendedId(
                        type==MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
                          ? MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                          : MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id);
                    found.add(new Item("local:"+id,itemUri,
                        name==null?"Sem nome":name,mime==null?"image/*":mime,size,date,
                        type==MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO,false,albumKey,bucketName,relativePath));
                }
            } catch(Exception e) { error=e.getMessage(); }
            String finalError=error;
            ui.post(()->{
                loading=false;localItems.clear();localItems.addAll(found);sortLocal();buildAlbums();
                if(finalError!=null)Toast.makeText(this,"Erro ao ler: "+finalError,Toast.LENGTH_LONG).show();
                update();
            });
        });
    }
    void sortLocal() {
        localItems.sort((a,b)->oldest?Long.compare(a.when,b.when):Long.compare(b.when,a.when));
    }
    void buildAlbums() {
        LinkedHashMap<String,Album> grouped=new LinkedHashMap<>();
        Album all=new Album(ALL_ALBUMS,"Todos os arquivos");
        grouped.put(ALL_ALBUMS,all);
        for(Item item:localItems) {
            Album a=grouped.get(item.albumId);
            if(a==null){a=new Album(item.albumId,item.albumName);a.relativePath=item.relativePath;grouped.put(item.albumId,a);}
            a.total++;all.total++;
            if(a.cover==null || item.when>a.cover.when)a.cover=item;
            if(all.cover==null || item.when>all.cover.when)all.cover=item;
            if(kept.contains(item.key)||trash.contains(item.key)){a.reviewed++;all.reviewed++;}
        }
        albums.clear();albums.add(all);
        ArrayList<Album> others=new ArrayList<>(grouped.values());
        others.remove(all);
        others.sort((a,b)->{
            long d=Long.compare(b.cover==null?0:b.cover.when,a.cover==null?0:a.cover.when);
            return d!=0?(int)d:a.name.compareToIgnoreCase(b.name);
        });
        albums.addAll(others);
        if(!browsingAlbum && !ALL_ALBUMS.equals(selectedAlbum) && !grouped.containsKey(selectedAlbum)){
            selectedAlbum=ALL_ALBUMS;
            prefs.edit().putString("selected_album",selectedAlbum).apply();
        }
        if(albumRows!=null && albumSection.getVisibility()==View.VISIBLE)renderAlbums();
    }
    String currentAlbumName(){
        if(cloudMode)return "Seleção do Google Fotos";
        for(Album a:albums)if(a.id.equals(selectedAlbum))return a.name;
        return prefs.getString("selected_album_name","Todos os arquivos");
    }
    void selectAlbum(String id){
        // Entrar imediatamente no visualizador do álbum, sem uma etapa intermediária.
        selectedAlbum=id;
        cloudMode=false;
        browsingAlbum=true;
        filter=0; // Não deixar o filtro anterior de vídeos esconder as fotos do álbum.
        String label="Todos os arquivos";
        for(Album a:albums)if(a.id.equals(id)){label=a.name;break;}
        prefs.edit().putString("selected_album",id).putString("selected_album_name",label)
            .putBoolean("browsing_album",true).putInt("filter",0).apply();
        history.clear();
        clearUndoFocus();
        switchTab(false);
    }
    void switchTab(boolean albumsActive){
        albumTab=albumsActive;
        swipeSection.setVisibility(albumsActive?View.GONE:View.VISIBLE);
        albumSection.setVisibility(albumsActive?View.VISIBLE:View.GONE);
        navSwipe.setBackground(shape(albumsActive?PANEL:BLUE,18));
        navAlbums.setBackground(shape(albumsActive?BLUE:PANEL,18));
        if(albumsActive)renderAlbums();else update();
    }
    void quickAlbumPicker(){
        if(albums.isEmpty()){
            Toast.makeText(this,"A galeria ainda está sendo lida.",Toast.LENGTH_SHORT).show();return;
        }
        String[] titles=new String[albums.size()];
        for(int i=0;i<albums.size();i++){
            Album a=albums.get(i);
            titles[i]=a.name+"  ("+a.total+")";
        }
        new AlertDialog.Builder(this).setTitle("Abrir álbum")
            .setItems(titles,(dialog,index)->selectAlbum(albums.get(index).id))
            .setNegativeButton("Cancelar",null).show();
    }
    void renderAlbums(){
        if(albumRows==null)return;
        albumRows.removeAllViews();
        String q=albumSearch==null?"":albumSearch.getText().toString().trim().toLowerCase(Locale.ROOT);
        for(Album a:albums) {
            if(!a.name.toLowerCase(Locale.ROOT).contains(q))continue;
            LinearLayout itemRow=new LinearLayout(this);
            itemRow.setGravity(Gravity.CENTER_VERTICAL);
            itemRow.setPadding(dp(9),dp(9),dp(12),dp(9));
            itemRow.setBackground(shape(PANEL,16));
            LinearLayout.LayoutParams rowParams=new LinearLayout.LayoutParams(-1,dp(91));
            rowParams.bottomMargin=dp(9);
            albumRows.addView(itemRow,rowParams);
            ImageView cover=new ImageView(this);
            cover.setBackground(shape(0xff293452,12));
            cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
            itemRow.addView(cover,new LinearLayout.LayoutParams(dp(70),dp(70)));
            if(a.cover!=null)loadAlbumCover(a.cover,cover);
            LinearLayout info=new LinearLayout(this);
            info.setPadding(dp(13),0,0,0);
            info.setOrientation(LinearLayout.VERTICAL);
            info.setGravity(Gravity.CENTER_VERTICAL);
            itemRow.addView(info,new LinearLayout.LayoutParams(0,-1,1));
            TextView title=label(a.name,16,TEXT,true);
            title.setSingleLine(true);
            title.setEllipsize(android.text.TextUtils.TruncateAt.END);
            info.addView(title);
            TextView progress=label(a.reviewed+" de "+a.total+" analisadas",12,MUTED,false);
            info.addView(progress);
            TextView arrow=label("›",28,BLUE,true);
            itemRow.addView(arrow);
            itemRow.setOnClickListener(v->selectAlbum(a.id));
        }
        if(albumRows.getChildCount()==0){
            TextView empty=label("Nenhum álbum encontrado.",14,MUTED,false);
            empty.setGravity(Gravity.CENTER);
            albumRows.addView(empty,new LinearLayout.LayoutParams(-1,dp(110)));
        }
    }
    void loadAlbumCover(Item item,ImageView view){
        coverWorker.execute(()->{
            Bitmap bmp=null;
            try{
                bmp=getContentResolver().loadThumbnail(item.uri,new Size(180,180),null);
            }catch(Exception ignored){}
            final Bitmap result=bmp;
            ui.post(()->{if(view.getParent()!=null)view.setImageBitmap(result);});
        });
    }
    void chooseCloud() {
        new AlertDialog.Builder(this).setTitle("Google Fotos – arquivos da nuvem")
          .setMessage("Selecione fotos ou vídeos pelo seletor do Android. O acesso aos itens da nuvem depende da configuração do Google Fotos.\n\nImportante: o Google não permite que este app apague arquivos exclusivamente na nuvem. Os descartes desse modo serão uma lista de revisão; você precisará excluir os originais pelo Google Fotos.")
          .setNegativeButton("Cancelar",null)
          .setPositiveButton("Selecionar mídias",(d,w)->openCloudPicker()).show();
    }
    void openCloudPicker() {
        Intent i;
        if(Build.VERSION.SDK_INT>=33) {
            i=new Intent(MediaStore.ACTION_PICK_IMAGES);
            i.setType("*/*");
            i.putExtra(MediaStore.EXTRA_PICK_IMAGES_MAX,Math.min(50,MediaStore.getPickImagesMaxLimit()));
        } else {
            i=new Intent(Intent.ACTION_GET_CONTENT);
            i.setType("*/*");i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);
            i.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"image/*","video/*"});
            i.addCategory(Intent.CATEGORY_OPENABLE);
        }
        try{startActivityForResult(i,REQ_CLOUD);}
        catch(Exception e){Toast.makeText(this,"Não foi possível abrir o seletor de fotos.",Toast.LENGTH_LONG).show();}
    }
    @Override protected void onActivityResult(int req,int result,Intent data) {
        super.onActivityResult(req,result,data);
        if(req==REQ_MOVE){
            if(result==RESULT_OK)completeMove();
            else {clearPendingMove();Toast.makeText(this,"Movimentação cancelada.",Toast.LENGTH_SHORT).show();}
            return;
        }
        if(req==REQ_INSTALL_SOURCE) {
            if(pendingInstallUri!=null && getPackageManager().canRequestPackageInstalls())
                openSystemInstaller(pendingInstallUri);
            return;
        }
        if(req==REQ_TRASH) {
            if(result==RESULT_OK) {
                HashSet<String> done=new HashSet<>(trash);
                localItems.removeIf(it->done.contains(it.key));
                trash.clear();persist();history.clear();buildAlbums();
                Toast.makeText(this,done.size()+" mídia(s) movidas para a lixeira.",Toast.LENGTH_LONG).show();
            } else Toast.makeText(this,"Exclusão cancelada. Seleção mantida.",Toast.LENGTH_SHORT).show();
            update();
        } else if(req==REQ_CLOUD && result==RESULT_OK && data!=null) {
            ArrayList<Uri> picked=new ArrayList<>();
            if(data.getClipData()!=null) {
                ClipData c=data.getClipData();
                for(int j=0;j<c.getItemCount();j++)picked.add(c.getItemAt(j).getUri());
            }
            if(data.getData()!=null)picked.add(data.getData());
            cloudItems.clear();cloudKept.clear();cloudTrash.clear();history.clear();
            for(Uri uri:picked){
                String mime=getContentResolver().getType(uri);
                if(mime==null)mime="image/jpeg";
                String name="Mídia selecionada";long size=0;
                try(Cursor c=getContentResolver().query(uri,new String[]{
                    android.provider.OpenableColumns.DISPLAY_NAME,
                    android.provider.OpenableColumns.SIZE},null,null,null)){
                    if(c!=null&&c.moveToFirst()){
                        if(!c.isNull(0))name=c.getString(0);
                        if(!c.isNull(1))size=c.getLong(1);
                    }
                }catch(Exception ignored){}
                boolean video=mime.startsWith("video/");
                String key="cloud:"+uri;
                boolean present=false;for(Item old:cloudItems)if(old.key.equals(key))present=true;
                if(!present)cloudItems.add(new Item(key,uri,name,mime,size,0,video,true));
            }
            cloudMode=true;filter=0;update();
        }
    }
    ArrayList<Item> visiblePool() {
        ArrayList<Item> all=cloudMode?cloudItems:localItems;
        ArrayList<Item> result=new ArrayList<>();
        for(Item it:all)if((cloudMode||ALL_ALBUMS.equals(selectedAlbum)||selectedAlbum.equals(it.albumId))
            &&(filter==0||(filter==1&&!it.video)||(filter==2&&it.video)))result.add(it);
        return result;
    }
    Set<String> keepSet(){return cloudMode?cloudKept:kept;}
    Set<String> trashSet(){return cloudMode?cloudTrash:trash;}
    Item nextItem() {
        ArrayList<Item> pool=visiblePool();
        if(pool.isEmpty())return null;
        if(!undoFocusKey.isEmpty()){
            for(Item item:pool){
                if(item.key.equals(undoFocusKey)
                    &&!keepSet().contains(item.key)
                    &&!trashSet().contains(item.key))return item;
            }
        }
        String anchor=cloudMode?"":prefs.getString("last_media:"+selectedAlbum,"");
        int start=0;
        if(!anchor.isEmpty()){
            for(int i=0;i<pool.size();i++)if(pool.get(i).key.equals(anchor)){
                start=(i+1)%pool.size();break;
            }
        }
        for(int i=0;i<pool.size();i++){
            Item item=pool.get((start+i)%pool.size());
            if(!keepSet().contains(item.key)&&!trashSet().contains(item.key))return item;
        }
        return null;
    }
    void persist() {
        prefs.edit().putStringSet("keep",new HashSet<>(kept))
            .putStringSet("trash",new HashSet<>(trash)).apply();
    }

    // Mover altera a pasta física da mídia, diferente de trocar a pasta exibida.
    boolean isMovePathAllowed(Item item,String path){
        if(path==null||path.isEmpty())return false;
        String p=path.replace('\\','/').toLowerCase(Locale.ROOT);
        if(!p.endsWith("/")||p.contains("../")||p.startsWith("/"))return false;
        String root=p.split("/")[0];
        if(item.video)return root.equals("dcim")||root.equals("movies")||root.equals("pictures");
        return root.equals("dcim")||root.equals("pictures");
    }
    void chooseMoveDestination(){
        Item item=displayed;
        if(item==null){
            Toast.makeText(this,"Não há mídia selecionada.",Toast.LENGTH_SHORT).show();return;
        }
        if(item.cloud){
            new AlertDialog.Builder(this).setTitle("Mídia somente na nuvem")
                .setMessage("Mover entre pastas funciona para arquivos locais. Para fotos só na nuvem, organize pelo Google Fotos.")
                .setPositiveButton("OK",null).show();
            return;
        }
        if(movePending)return;
        ArrayList<Album> targets=new ArrayList<>();
        for(Album a:albums)
            if(!ALL_ALBUMS.equals(a.id)&&!a.id.equals(item.albumId)
                &&isMovePathAllowed(item,a.relativePath))targets.add(a);
        targets.sort((a,b)->a.name.compareToIgnoreCase(b.name));
        String[] choices=new String[targets.size()+1];
        for(int i=0;i<targets.size();i++)
            choices[i]=targets.get(i).name+" ("+targets.get(i).total+")";
        choices[targets.size()]="+ Criar novo álbum";
        new AlertDialog.Builder(this)
            .setTitle("Mover "+item.name+" para...")
            .setItems(choices,(d,index)->{
                if(index==targets.size())createMoveAlbum(item);
                else{
                    Album destination=targets.get(index);
                    new AlertDialog.Builder(this).setTitle("Mover para "+destination.name+"?")
                        .setMessage("Esta foto sairá da pasta atual e irá para "+destination.name+". Não será duplicada.")
                        .setNegativeButton("Cancelar",null)
                        .setPositiveButton("Mover",(dialog,which)->
                            requestMove(item,destination.relativePath,destination.name))
                        .show();
                }
            })
            .setNegativeButton("Cancelar",null).show();
    }
    void createMoveAlbum(Item item){
        EditText field=new EditText(this);
        field.setSingleLine(true);
        field.setHint("Nome do álbum");
        LinearLayout form=new LinearLayout(this);
        form.setPadding(dp(20),0,dp(20),0);
        form.addView(field,new LinearLayout.LayoutParams(-1,dp(60)));
        AlertDialog dialog=new AlertDialog.Builder(this)
            .setTitle("Criar álbum e mover")
            .setView(form)
            .setNegativeButton("Cancelar",null)
            .setPositiveButton("Criar",null).create();
        dialog.setOnShowListener(v->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(button->{
            String name=field.getText().toString().trim();
            if(name.isEmpty()||name.length()>60||name.equals(".")||name.equals("..")
                ||name.contains("/")||name.contains("\\")||name.contains(":")){
                field.setError("Escolha um nome de 1 a 60 caracteres sem barras ou :");
                return;
            }
            dialog.dismiss();
            requestMove(item,(item.video?"Movies/":"Pictures/")+name+"/",name);
        }));
        dialog.show();
    }


    // O Android autoriza por arquivo alterações de mídias de outros aplicativos.
    void requestMove(Item item,String destination,String title){
        if(item==null||item.cloud||movePending)return;
        if(!isMovePathAllowed(item,destination)){
            new AlertDialog.Builder(this).setTitle("Destino incompatível")
                .setMessage("O Android não permite colocar este tipo de mídia nessa pasta.")
                .setPositiveButton("OK",null).show();
            return;
        }
        if(destination.equalsIgnoreCase(item.relativePath)){
            Toast.makeText(this,"A mídia já está neste álbum.",Toast.LENGTH_SHORT).show();return;
        }
        movePending=true;
        prefs.edit().putString("pending_move_key",item.key)
            .putString("pending_move_uri",item.uri.toString())
            .putString("pending_move_dest",destination)
            .putString("pending_move_title",title)
            .putString("pending_move_source",selectedAlbum).apply();
        try{
            PendingIntent permission=MediaStore.createWriteRequest(
                getContentResolver(),Collections.singletonList(item.uri));
            startIntentSenderForResult(permission.getIntentSender(),REQ_MOVE,null,0,0,0);
        }catch(Exception exception){
            clearPendingMove();
            new AlertDialog.Builder(this).setTitle("Sem acesso para mover")
                .setMessage("O Android não autorizou a solicitação. Nenhuma foto foi movida.\n"+
                    exception.getMessage()).setPositiveButton("OK",null).show();
        }
    }
    void clearPendingMove(){
        movePending=false;
        prefs.edit().remove("pending_move_key").remove("pending_move_uri")
            .remove("pending_move_dest").remove("pending_move_title")
            .remove("pending_move_source").apply();
    }
    void completeMove(){
        String key=prefs.getString("pending_move_key","");
        String uriText=prefs.getString("pending_move_uri","");
        String destination=prefs.getString("pending_move_dest","");
        String targetName=prefs.getString("pending_move_title","");
        String origin=prefs.getString("pending_move_source",selectedAlbum);
        if(key.isEmpty()||uriText.isEmpty()||destination.isEmpty()){
            clearPendingMove();return;
        }
        updateWorker.execute(()->{
            String error=null;
            try{
                ContentValues cv=new ContentValues();
                cv.put(MediaStore.MediaColumns.RELATIVE_PATH,destination);
                Uri uri=Uri.parse(uriText);
                int rows=getContentResolver().update(uri,cv,null,null);
                if(rows<1)throw new Exception("Nenhuma mídia foi atualizada.");
                try(Cursor cursor=getContentResolver().query(uri,
                    new String[]{MediaStore.MediaColumns.RELATIVE_PATH},null,null,null)){
                    if(cursor!=null&&cursor.moveToFirst()){
                        String actual=cursor.getString(0);
                        if(actual!=null&&!actual.equalsIgnoreCase(destination))
                            throw new Exception("A pasta de destino não foi confirmada.");
                    }
                }
            }catch(Exception exception){error=exception.getMessage();}
            String outcome=error;
            ui.post(()->{
                clearPendingMove();
                if(isFinishing()||isDestroyed())return;
                if(outcome!=null){
                    new AlertDialog.Builder(this).setTitle("Não foi possível mover")
                        .setMessage("Verifique a pasta atual na Galeria Samsung antes de tentar de novo.\n\n"+
                            outcome).setPositiveButton("OK",null).show();
                    scanLocal();
                    return;
                }
                // Após mover, já está organizada e não precisa reaparecer no swipe.
                kept.add(key);trash.remove(key);
                prefs.edit().putString("last_media:"+origin,key)
                    .putString("last_media:"+ALL_ALBUMS,key).apply();
                persist();history.clear();clearUndoFocus();
                Toast.makeText(this,"Movida para "+targetName,Toast.LENGTH_SHORT).show();
                showFeedback("📁","Movida",BLUE);
                scanLocal();
            });
        });
    }

    void decide(boolean discard) {
        if(movePending)return;
        Item it=displayed;if(it==null)return;
        clearUndoFocus();
        if(discard)trashSet().add(it.key);else keepSet().add(it.key);
        history.addLast(new Move(it.key,discard,cloudMode));
        if(history.size()>100)history.removeFirst();
        if(!cloudMode) {
            prefs.edit().putString("last_media:"+selectedAlbum,it.key)
                .putString("last_media:"+ALL_ALBUMS,it.key).apply();
            persist();buildAlbums();
        }
        update();
        showFeedback(discard ? "✕" : "♥",
            discard ? (it.cloud ? "Revisar na nuvem" : "Marcada para descarte") : "Mantida",
            discard ? PINK : GREEN);
        card.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK);
    }
    // Confirmação discreta no topo da foto: curta, sem popup modal e sem cobrir a mídia.
    void showFeedback(String icon,String message,int color){
        if(hideFeedback!=null)ui.removeCallbacks(hideFeedback);
        feedbackBox.animate().cancel();
        feedbackIcon.setText(icon);
        feedbackIcon.setTextColor(color);
        feedbackMessage.setText(message);
        feedbackBox.setBackground(shape(0xee162033,12));
        feedbackBox.setVisibility(View.VISIBLE);
        feedbackBox.setAlpha(0f);
        feedbackBox.animate().alpha(1f).setDuration(90).start();
        hideFeedback=()->feedbackBox.animate().alpha(0f).setDuration(140)
            .withEndAction(()->feedbackBox.setVisibility(View.GONE)).start();
        ui.postDelayed(hideFeedback,440);
    }
    void clearUndoFocus(){
        undoFocusKey="";
        prefs.edit().remove("undo_focus_key").apply();
    }
    void undo(){
        if(movePending)return;
        while(!history.isEmpty()){
            Move move=history.removeLast();
            if(move.cloud!=cloudMode)continue;
            if(move.discarded)(move.cloud?cloudTrash:trash).remove(move.key);
            else (move.cloud?cloudKept:kept).remove(move.key);
            // Exibir a foto desfeita imediatamente, ignorando a âncora de ordenação.
            undoFocusKey=move.key;
            if(!move.cloud){
                prefs.edit().putString("undo_focus_key",move.key).apply();
                persist();buildAlbums();
            }
            update();
            showFeedback("↶","Desfeito",BLUE);
            return;
        }
        Toast.makeText(this,"Nenhuma decisão para desfazer.",Toast.LENGTH_SHORT).show();
    }
    void update() {
        imageVersion++;
        card.setTranslationX(0);card.setRotation(0);card.setAlpha(1);
        allChip.setBackground(shape(filter==0?BLUE:PANEL,16));
        photoChip.setBackground(shape(filter==1?BLUE:PANEL,16));
        videoChip.setBackground(shape(filter==2?BLUE:PANEL,16));
        sourceButton.setText(cloudMode?"☁  Seleção do Google Fotos":"▣  Galeria Samsung / celular");
        albumSelector.setText("▾  "+currentAlbumName());
        boolean albumDetails=browsingAlbum&&!cloudMode;
        albumBrowseRow.setVisibility(albumDetails?View.VISIBLE:View.GONE);
        sourceRow.setVisibility(albumDetails?View.GONE:View.VISIBLE);
        albumSelector.setVisibility(albumDetails?View.GONE:View.VISIBLE);
        albumBrowseTitle.setText(currentAlbumName());
        ArrayList<Item> pool=visiblePool();
        int reviewed=0;
        for(Item it:pool)if(keepSet().contains(it.key)||trashSet().contains(it.key))reviewed++;
        status.setText((cloudMode?"Nuvem":currentAlbumName())+"   •   "+reviewed+" de "+pool.size()+" analisados");
        trashButton.setText("🗑  "+trashSet().size()+(cloudMode?" para revisar":" para a lixeira"));
        undoButton.setAlpha(history.isEmpty()?.5f:1f);
        displayed=nextItem();
        if(displayed==null) {
            picture.setImageDrawable(null);picture.setBackground(shape(PANEL,20));
            badge.setText(pool.isEmpty()?"Sem arquivos":"✓ CONCLUÍDO");
            headline.setText(pool.isEmpty()?"Nenhuma mídia encontrada":"Você terminou esta seleção!");
            subtitle.setText(cloudMode?"Selecione outro lote ou revise seus descartes.":
                (pool.isEmpty()?"Autorize acesso completo à galeria ou troque o filtro.":"Revise os descartes ou reinicie as decisões."));
            return;
        }
        picture.setBackgroundColor(0xff111727);
        Item it=displayed;
        badge.setText(it.video?"▶  VÍDEO":"▧  FOTO");
        headline.setText(it.name);
        String date=it.when>0?new SimpleDateFormat("dd/MM/yyyy",new Locale("pt","BR")).format(new java.util.Date(it.when*1000L)):"Google Fotos";
        subtitle.setText(date+"    •    "+prettySize(it.size)+(it.cloud?"   •   seleção da nuvem":""));
        picture.setImageDrawable(null);
        int version=imageVersion;
        worker.execute(()->{
            Bitmap result=null;
            try {
                if(Build.VERSION.SDK_INT>=29) result=getContentResolver().loadThumbnail(it.uri,new Size(1080,1440),null);
                else {
                    BitmapFactory.Options opt=new BitmapFactory.Options();opt.inSampleSize=4;
                    try(InputStream s=getContentResolver().openInputStream(it.uri)){result=BitmapFactory.decodeStream(s,null,opt);}
                }
            }catch(Exception ignored){}
            Bitmap ready=result;
            ui.post(()->{if(version==imageVersion && displayed==it)picture.setImageBitmap(ready);});
        });
    }
    String prettySize(long v) {
        if(v<=0)return "Tamanho indisponível";
        if(v>=1048576)return String.format(Locale.getDefault(),"%.1f MB",v/1048576f);
        return String.format(Locale.getDefault(),"%.0f KB",v/1024f);
    }
    boolean onSwipe(MotionEvent e) {
        if(displayed==null)return true;
        switch(e.getActionMasked()){
            case MotionEvent.ACTION_DOWN:
                startX=e.getRawX();startY=e.getRawY();swiping=false;return true;
            case MotionEvent.ACTION_MOVE:
                float diff=e.getRawX()-startX;
                if(Math.abs(diff)>dp(10))swiping=true;
                if(swiping) {
                    card.setTranslationX(diff*.85f);card.setRotation(Math.max(-10,Math.min(10,diff/dp(30))));
                    card.setAlpha(Math.max(.5f,1-Math.abs(diff)/dp(650)));
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                float delta=e.getRawX()-startX;
                if(e.getActionMasked()==MotionEvent.ACTION_UP && Math.abs(delta)>dp(85)){
                    decide(delta<0);
                }else{
                    card.animate().translationX(0).rotation(0).alpha(1).setDuration(170).start();
                    if(!swiping && Math.abs(e.getRawY()-startY)<dp(12))openDisplayed();
                }
                return true;
        }
        return true;
    }
    void openDisplayed() {
        if(displayed==null)return;
        Intent intent=new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(displayed.uri,displayed.mime);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try{startActivity(intent);}catch(Exception e){
            Toast.makeText(this,"Não foi possível abrir esta mídia em outro aplicativo.",Toast.LENGTH_LONG).show();
        }
    }
    void review() {
        Collection<String> marked=trashSet();
        if(marked.isEmpty()){
            new AlertDialog.Builder(this).setTitle("Nenhum descarte")
                .setMessage("Deslize as mídias para a esquerda para adicioná-las à lista de descarte.")
                .setPositiveButton("Entendi",null).show();return;
        }
        int count=marked.size();long bytes=0;StringBuilder names=new StringBuilder();
        for(Item it:(cloudMode?cloudItems:localItems))if(marked.contains(it.key)){
            bytes+=it.size;if(names.length()<700)names.append("• ").append(it.name).append("\n");
        }
        if(cloudMode) {
            new AlertDialog.Builder(this).setTitle(count+" itens para revisar no Google Fotos")
                .setMessage("ATENÇÃO: esses itens NÃO foram excluídos da nuvem. O Google impede a exclusão de fotos da conta por apps de terceiros.\n\n"+names)
                .setNegativeButton("Voltar",null)
                .setNeutralButton("Compartilhar lista",(d,w)->{
                    Intent send=new Intent(Intent.ACTION_SEND);send.setType("text/plain");
                    send.putExtra(Intent.EXTRA_TEXT,"Mídias para excluir manualmente no Google Fotos:\n"+names);
                    startActivity(Intent.createChooser(send,"Compartilhar lista"));
                })
                .setPositiveButton("Abrir Google Fotos",(d,w)->{
                    try{startActivity(getPackageManager().getLaunchIntentForPackage("com.google.android.apps.photos"));}
                    catch(Exception e){Toast.makeText(this,"Abra o aplicativo Google Fotos.",Toast.LENGTH_SHORT).show();}
                }).show();
            return;
        }
        String msg=count+" mídias selecionadas ("+prettySize(bytes)+").\n\n"+
            "O Android pedirá sua confirmação e moverá os arquivos à lixeira, não apagará permanentemente de imediato.\n\n"+
            "Se uma foto tiver backup no Google Fotos, apagar a cópia local NÃO garante excluir a cópia na nuvem.\n\n"+
            "Exemplos:\n"+names;
        new AlertDialog.Builder(this).setTitle("Revisar descarte")
            .setMessage(msg).setNegativeButton("Cancelar",null)
            .setPositiveButton("Mover para lixeira",(d,w)->trashLocal()).show();
    }
    void trashLocal() {
        ArrayList<Uri> uris=new ArrayList<>();
        for(Item it:localItems)if(trash.contains(it.key))uris.add(it.uri);
        if(uris.isEmpty())return;
        try {
            PendingIntent req=MediaStore.createTrashRequest(getContentResolver(),uris,true);
            startIntentSenderForResult(req.getIntentSender(),REQ_TRASH,null,0,0,0);
        }catch(Exception e){
            new AlertDialog.Builder(this).setTitle("Não foi possível enviar para a lixeira")
                .setMessage("Nenhum arquivo foi apagado. Detalhes: "+e.getMessage())
                .setPositiveButton("OK",null).show();
        }
    }
    void resetDecisions() {
        new AlertDialog.Builder(this).setTitle("Recomeçar seleção?")
            .setMessage("Isso apaga somente as decisões salvas no SwipeClean, não suas fotos ou vídeos.")
            .setNegativeButton("Cancelar",null)
            .setPositiveButton("Recomeçar",(d,w)->{
                if(cloudMode){cloudKept.clear();cloudTrash.clear();}
                else {kept.clear();trash.clear();persist();}
                history.clear();clearUndoFocus();buildAlbums();update();
            }).show();
    }


    // A atualização acontece sem precisar abrir o navegador. O único passo
    // obrigatório do usuário é a confirmação de instalação do próprio Android.
    void checkForUpdates() {
        if(checkingUpdate || verifyingDownload) {
            Toast.makeText(this,"Já existe uma verificação ou download em andamento.",Toast.LENGTH_SHORT).show();
            return;
        }
        checkingUpdate=true;
        updateButton.setText("↻ Verificando...");
        updateWorker.execute(()->{
            String error=null;
            int newestCode=0;
            String newestName="",notes="",sha="";
            HttpURLConnection connection=null;
            try {
                URL url=new URL(VERSION_URL+"?v="+System.currentTimeMillis());
                connection=(HttpURLConnection)url.openConnection();
                connection.setInstanceFollowRedirects(true);
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(15000);
                connection.setUseCaches(false);
                connection.setRequestProperty("Accept","application/json");
                if(connection.getResponseCode()!=200)throw new Exception("Servidor respondeu "+connection.getResponseCode());
                java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();
                try(InputStream input=connection.getInputStream()) {
                    byte[] buffer=new byte[4096];int n;
                    while((n=input.read(buffer))!=-1) {
                        bytes.write(buffer,0,n);
                        if(bytes.size()>32768)throw new Exception("Arquivo de versão inválido");
                    }
                }
                JSONObject json=new JSONObject(bytes.toString("UTF-8"));
                newestCode=json.getInt("versionCode");
                newestName=json.getString("versionName");
                notes=json.optString("notes","Melhorias e correções");
                sha=json.getString("sha256").toLowerCase(Locale.ROOT).trim();
                if(!sha.matches("[a-f0-9]{64}"))throw new Exception("Hash de segurança ausente");
                if(newestCode<=0)throw new Exception("Número de versão inválido");
            }catch(Exception e){error=e.getMessage();}
            finally{if(connection!=null)connection.disconnect();}
            final String problem=error,version=newestName,details=notes,expected=sha;
            final int code=newestCode;
            ui.post(()->{
                checkingUpdate=false;
                if(isFinishing()||isDestroyed())return;
                updateButton.setText("↻ Verificar atualização");
                if(problem!=null){
                    new AlertDialog.Builder(this).setTitle("Não foi possível verificar")
                        .setMessage("Falha na conexão ou leitura da atualização: "+problem+
                            "\n\nVocê pode baixar a versão publicada diretamente pelo GitHub.")
                        .setNegativeButton("Fechar",null)
                        .setPositiveButton("Abrir download",(d,w)->openGithubApk()).show();
                    return;
                }
                long installed=0;
                try{installed=getPackageManager().getPackageInfo(getPackageName(),0).getLongVersionCode();}
                catch(Exception ignored){}
                if(code<=installed) {
                    new AlertDialog.Builder(this).setTitle("SwipeClean atualizado ✓")
                        .setMessage("Você está usando a versão "+getInstalledVersion()+". Nenhuma atualização disponível.")
                        .setNegativeButton("Fechar",null)
                        .setPositiveButton("Ver APK no GitHub",(d,w)->openGithubApk()).show();
                } else {
                    new AlertDialog.Builder(this).setTitle("Atualização disponível: "+version)
                        .setMessage("Instalada: "+getInstalledVersion()+"\n\n"+details+
                            "\n\nO SwipeClean vai baixar e verificar o APK. Depois, o Android solicitará sua confirmação para instalar.")
                        .setNegativeButton("Mais tarde",null)
                        .setPositiveButton("Baixar e instalar",(d,w)->downloadUpdate(version,expected)).show();
                }
            });
        });
    }
    String getInstalledVersion() {
        try{return getPackageManager().getPackageInfo(getPackageName(),0).versionName;}
        catch(Exception e){return "desconhecida";}
    }
    void openGithubApk() {
        try{
            Intent browser=new Intent(Intent.ACTION_VIEW,Uri.parse(APK_URL));
            startActivity(browser);
        }catch(Exception e){
            Toast.makeText(this,"Abra o link do projeto no navegador.",Toast.LENGTH_LONG).show();
        }
    }
    void downloadUpdate(String version,String expectedSha){
        if(verifyingDownload)return;
        verifyingDownload=true;
        updateButton.setText("↓ Baixando...");
        updateWorker.execute(()->{
            String error=null;
            java.io.File partial=null,finalFile=null;
            HttpURLConnection connection=null;
            try {
                java.io.File dir=new java.io.File(getCacheDir(),"updates");
                if(!dir.exists()&&!dir.mkdirs())throw new Exception("Não foi possível criar a pasta temporária");
                partial=new java.io.File(dir,"SwipeClean-update.part");
                finalFile=new java.io.File(dir,"SwipeClean-update.apk");
                if(partial.exists())partial.delete();
                URL url=new URL(APK_URL+"?download="+System.currentTimeMillis());
                connection=(HttpURLConnection)url.openConnection();
                connection.setInstanceFollowRedirects(true);
                connection.setConnectTimeout(20000);
                connection.setReadTimeout(25000);
                connection.setUseCaches(false);
                if(connection.getResponseCode()!=200)throw new Exception("Falha HTTP "+connection.getResponseCode());
                long expectedLength=connection.getContentLengthLong();
                if(expectedLength>200L*1024*1024)throw new Exception("Arquivo maior que o permitido");
                MessageDigest dig=MessageDigest.getInstance("SHA-256");
                long received=0;
                int lastPercent=-1;
                try(InputStream in=connection.getInputStream();
                    java.io.FileOutputStream out=new java.io.FileOutputStream(partial)){
                    byte[] buffer=new byte[32768];int n;
                    while((n=in.read(buffer))!=-1){
                        out.write(buffer,0,n);
                        dig.update(buffer,0,n);
                        received+=n;
                        if(received>200L*1024*1024)throw new Exception("Arquivo excedeu o tamanho máximo");
                        if(expectedLength>0) {
                            int percent=(int)(received*100/expectedLength);
                            if(percent/10!=lastPercent/10){
                                lastPercent=percent;
                                int shown=percent;
                                ui.post(()->{if(!isFinishing()&&!isDestroyed())
                                    updateButton.setText("↓ Baixando "+shown+"%");});
                            }
                        }
                    }
                    out.getFD().sync();
                }
                if(received<20000)throw new Exception("Download incompleto");
                StringBuilder digest=new StringBuilder(64);
                for(byte b:dig.digest())digest.append(String.format(Locale.ROOT,"%02x",b&0xff));
                if(!digest.toString().equalsIgnoreCase(expectedSha))
                    throw new Exception("Hash SHA-256 diferente. Atualização cancelada por segurança.");
                if(finalFile.exists()&&!finalFile.delete())throw new Exception("Não foi possível substituir o APK antigo");
                if(!partial.renameTo(finalFile))throw new Exception("Não foi possível finalizar o download");
            }catch(Exception e){error=e.getMessage();}
            finally{
                if(connection!=null)connection.disconnect();
                if(error!=null&&partial!=null)partial.delete();
            }
            final String failure=error;
            ui.post(()->{
                verifyingDownload=false;
                if(isFinishing()||isDestroyed())return;
                updateButton.setText("↻ Verificar atualização");
                if(failure!=null){
                    new AlertDialog.Builder(this).setTitle("Atualização não instalada")
                        .setMessage("Não foi possível concluir o download: "+failure)
                        .setNegativeButton("Fechar",null)
                        .setPositiveButton("Baixar no navegador",(d,w)->openGithubApk()).show();
                    return;
                }
                Uri uri=Uri.parse("content://"+getPackageName()+".updates/apk");
                requestApkInstall(uri);
            });
        });
    }
    void requestApkInstall(Uri uri){
        pendingInstallUri=uri;
        if(!getPackageManager().canRequestPackageInstalls()){
            new AlertDialog.Builder(this).setTitle("Permitir instalação pelo SwipeClean")
                .setMessage("O Android exige que você autorize uma vez a instalação pelo SwipeClean. Ative 'Permitir desta fonte' e volte ao app para concluir.")
                .setNegativeButton("Depois",null)
                .setPositiveButton("Autorizar",(d,w)->{
                    try{
                        Intent settings=new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:"+getPackageName()));
                        startActivityForResult(settings,REQ_INSTALL_SOURCE);
                    }catch(Exception e){
                        new AlertDialog.Builder(this).setTitle("Configuração indisponível")
                            .setMessage(e.getMessage())
                            .setPositiveButton("Abrir download",(dx,wx)->openGithubApk()).show();
                    }
                }).show();
        } else openSystemInstaller(uri);
    }
    void openSystemInstaller(Uri uri){
        try {
            Intent install=new Intent(Intent.ACTION_VIEW);
            install.setDataAndType(uri,"application/vnd.android.package-archive");
            install.setClipData(ClipData.newUri(getContentResolver(),"SwipeClean-update.apk",uri));
            install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(install);
            pendingInstallUri=null;
        }catch(Exception e){
            new AlertDialog.Builder(this).setTitle("Instalador não abriu")
                .setMessage("O APK foi baixado e verificado, mas o Android não conseguiu abrir a instalação: "+e.getMessage()+
                    "\n\nVocê pode abrir a versão publicada pelo navegador.")
                .setNegativeButton("Fechar",null)
                .setPositiveButton("Abrir download",(d,w)->openGithubApk()).show();
        }
    }
    @Override protected void onResume(){
        super.onResume();
        if(pendingInstallUri!=null && getPackageManager().canRequestPackageInstalls()){
            Uri uri=pendingInstallUri;
            ui.postDelayed(()->{
                if(!isFinishing()&&!isDestroyed()&&pendingInstallUri!=null)
                    openSystemInstaller(uri);
            },200);
        }
    }

    @Override public void onDestroy() {
        if(hideFeedback!=null)ui.removeCallbacks(hideFeedback);
        imageVersion++;worker.shutdownNow();coverWorker.shutdownNow();updateWorker.shutdownNow();super.onDestroy();
    }
}
