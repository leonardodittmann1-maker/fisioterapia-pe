package com.swipeclean.gallery;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.ClipData;
import android.content.ContentUris;
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
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Size;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.InputStream;
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
    static final int REQ_PERMISSION=11, REQ_CLOUD=12, REQ_TRASH=13;
    final Handler ui=new Handler(Looper.getMainLooper());
    final ExecutorService worker=Executors.newSingleThreadExecutor();
    final ArrayList<Item> localItems=new ArrayList<>(), cloudItems=new ArrayList<>();
    final ArrayDeque<Move> history=new ArrayDeque<>();
    Set<String> kept=new HashSet<>(), trash=new HashSet<>(), cloudKept=new HashSet<>(), cloudTrash=new HashSet<>();
    SharedPreferences prefs;
    LinearLayout page, filterBar;
    FrameLayout card;
    ImageView picture;
    TextView status, headline, hint, badge, subtitle, trashButton, undoButton, sourceButton, allChip, photoChip, videoChip;
    boolean cloudMode=false, oldest=false, loading=false;
    int filter=0, imageVersion=0;
    float startX,startY;
    boolean swiping=false;
    Item displayed;

    static class Item {
        String key, name, mime;
        Uri uri;
        long size, when;
        boolean video, cloud;
        Item(String key, Uri uri, String name, String mime, long size, long when, boolean video, boolean cloud) {
            this.key=key; this.uri=uri; this.name=name; this.mime=mime;
            this.size=size; this.when=when; this.video=video; this.cloud=cloud;
        }
    }
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
        order.setOnClickListener(v->{oldest=!oldest;sortLocal();update();});
        titleLine.addView(order);

        LinearLayout sources=new LinearLayout(this); sources.setPadding(0,dp(10),0,dp(10));sources.setGravity(Gravity.CENTER_VERTICAL);
        page.addView(sources);
        sourceButton=button("▣  Galeria Samsung / celular",BLUE,13);
        sources.addView(sourceButton,new LinearLayout.LayoutParams(0,dp(44),1));
        sourceButton.setOnClickListener(v->{
            if(cloudMode){ cloudMode=false;history.clear();update(); }
            else {new AlertDialog.Builder(this).setTitle("Galeria do celular")
                .setMessage("Esta área lê as fotos e os vídeos locais que aparecem na Galeria Samsung e no Google Fotos. Fotos somente na nuvem precisam ser selecionadas à parte.")
                .setPositiveButton("OK",null).show();}
        });
        TextView cloudBtn=button("☁ Google Fotos",PANEL,12);
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,dp(44),.78f);cp.leftMargin=dp(8);
        sources.addView(cloudBtn,cp);
        cloudBtn.setOnClickListener(v->chooseCloud());
        status=label("Preparando galeria…",12,MUTED,false);
        page.addView(status,new LinearLayout.LayoutParams(-1,dp(32)));

        filterBar=new LinearLayout(this);filterBar.setGravity(Gravity.CENTER);
        page.addView(filterBar,new LinearLayout.LayoutParams(-1,dp(45)));
        allChip=button("Todas",PANEL,12);photoChip=button("Fotos",PANEL,12);videoChip=button("Vídeos",PANEL,12);
        addChip(allChip,0);addChip(photoChip,1);addChip(videoChip,2);

        card=new FrameLayout(this);card.setBackground(shape(PANEL,22));
        LinearLayout.LayoutParams cardp=new LinearLayout.LayoutParams(-1,0,1);cardp.topMargin=dp(10);cardp.bottomMargin=dp(10);
        page.addView(card,cardp);
        picture=new ImageView(this);picture.setScaleType(ImageView.ScaleType.FIT_CENTER);
        picture.setBackgroundColor(0xff111727);
        card.addView(picture,new FrameLayout.LayoutParams(-1,-1));
        picture.setOnTouchListener((v,e)->onSwipe(e));

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

        hint=label("← DESCARTAR                                     MANTER →",10,MUTED,true);
        hint.setGravity(Gravity.CENTER);page.addView(hint,new LinearLayout.LayoutParams(-1,dp(29)));

        LinearLayout actions=new LinearLayout(this);actions.setGravity(Gravity.CENTER);
        page.addView(actions,new LinearLayout.LayoutParams(-1,dp(65)));
        TextView no=button("✕",0xff793148,29);TextView yes=button("♥",0xff125d53,28);
        TextView undo=button("↶",PANEL,27);undoButton=undo;
        actions.addView(no,new LinearLayout.LayoutParams(0,dp(62),1));
        LinearLayout.LayoutParams mid=new LinearLayout.LayoutParams(dp(66),dp(62));mid.setMargins(dp(10),0,dp(10),0);
        actions.addView(undo,mid);actions.addView(yes,new LinearLayout.LayoutParams(0,dp(62),1));
        no.setOnClickListener(v->decide(true));yes.setOnClickListener(v->decide(false));undo.setOnClickListener(v->undo());
        LinearLayout bottom=new LinearLayout(this);bottom.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams bottomParams=new LinearLayout.LayoutParams(-1,dp(53));bottomParams.topMargin=dp(10);
        page.addView(bottom,bottomParams);
        trashButton=button("🗑  Revisar descartes",PINK,13);
        bottom.addView(trashButton,new LinearLayout.LayoutParams(0,-1,1));
        trashButton.setOnClickListener(v->review());
        TextView reset=button("⟳",PANEL,23);
        LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(dp(54),-1);rp.leftMargin=dp(8);
        bottom.addView(reset,rp);
        reset.setOnClickListener(v->resetDecisions());
    }
    void addChip(TextView t,int value) {
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(38),1);
        if(value>0)p.leftMargin=dp(9);
        filterBar.addView(t,p);
        t.setOnClickListener(v->{filter=value;update();});
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
                MediaStore.Files.FileColumns.DATE_ADDED,MediaStore.Files.FileColumns.MEDIA_TYPE};
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
                    Uri itemUri=ContentUris.withAppendedId(files,id);
                    found.add(new Item("local:"+id,itemUri,
                        name==null?"Sem nome":name,mime==null?"image/*":mime,size,date,
                        type==MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO,false));
                }
            } catch(Exception e) { error=e.getMessage(); }
            String finalError=error;
            ui.post(()->{
                loading=false;localItems.clear();localItems.addAll(found);sortLocal();
                if(finalError!=null)Toast.makeText(this,"Erro ao ler: "+finalError,Toast.LENGTH_LONG).show();
                update();
            });
        });
    }
    void sortLocal() {
        localItems.sort((a,b)->oldest?Long.compare(a.when,b.when):Long.compare(b.when,a.when));
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
        if(req==REQ_TRASH) {
            if(result==RESULT_OK) {
                HashSet<String> done=new HashSet<>(trash);
                localItems.removeIf(it->done.contains(it.key));
                trash.clear();persist();history.clear();
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
        for(Item it:all)if(filter==0||(filter==1&&!it.video)||(filter==2&&it.video))result.add(it);
        return result;
    }
    Set<String> keepSet(){return cloudMode?cloudKept:kept;}
    Set<String> trashSet(){return cloudMode?cloudTrash:trash;}
    Item nextItem() {
        for(Item it:visiblePool())if(!keepSet().contains(it.key)&&!trashSet().contains(it.key))return it;
        return null;
    }
    void persist() {
        prefs.edit().putStringSet("keep",new HashSet<>(kept))
            .putStringSet("trash",new HashSet<>(trash)).apply();
    }
    void decide(boolean discard) {
        Item it=displayed;if(it==null)return;
        if(discard)trashSet().add(it.key);else keepSet().add(it.key);
        history.addLast(new Move(it.key,discard,cloudMode));
        if(history.size()>100)history.removeFirst();
        if(!cloudMode)persist();
        update();
    }
    void undo() {
        while(!history.isEmpty()){
            Move move=history.removeLast();
            if(move.discarded)(move.cloud?cloudTrash:trash).remove(move.key);
            else (move.cloud?cloudKept:kept).remove(move.key);
            persist();update();return;
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
        ArrayList<Item> pool=visiblePool();
        int reviewed=0;
        for(Item it:pool)if(keepSet().contains(it.key)||trashSet().contains(it.key))reviewed++;
        status.setText((cloudMode?"Nuvem (seleção)":"Galeria local")+"   •   "+reviewed+" de "+pool.size()+" analisados");
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
                history.clear();update();
            }).show();
    }
    @Override public void onDestroy() {
        imageVersion++;worker.shutdownNow();super.onDestroy();
    }
}
