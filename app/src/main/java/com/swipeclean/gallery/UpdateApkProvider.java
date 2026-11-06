package com.swipeclean.gallery;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileNotFoundException;

public class UpdateApkProvider extends ContentProvider {
    private File apk(){
        return new File(new File(getContext().getCacheDir(),"updates"),"SwipeClean-update.apk");
    }
    @Override public boolean onCreate(){return true;}
    @Override public String getType(Uri uri){
        return "application/vnd.android.package-archive";
    }
    @Override public ParcelFileDescriptor openFile(Uri uri,String mode) throws FileNotFoundException {
        if(!"/apk".equals(uri.getPath())||!"r".equals(mode))throw new FileNotFoundException("Caminho inválido");
        File file=apk();
        if(!file.isFile())throw new FileNotFoundException("APK indisponível");
        return ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY);
    }
    @Override public Cursor query(Uri uri,String[] projection,String selection,String[] args,String sortOrder){
        String[] columns=projection!=null?projection:new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE};
        MatrixCursor cursor=new MatrixCursor(columns);
        Object[] vals=new Object[columns.length];
        for(int i=0;i<columns.length;i++){
            if(OpenableColumns.DISPLAY_NAME.equals(columns[i]))vals[i]="SwipeClean-update.apk";
            else if(OpenableColumns.SIZE.equals(columns[i]))vals[i]=apk().length();
        }
        cursor.addRow(vals);
        return cursor;
    }
    @Override public Uri insert(Uri uri,ContentValues values){throw new UnsupportedOperationException();}
    @Override public int delete(Uri uri,String selection,String[] args){throw new UnsupportedOperationException();}
    @Override public int update(Uri uri,ContentValues values,String selection,String[] args){throw new UnsupportedOperationException();}
}
