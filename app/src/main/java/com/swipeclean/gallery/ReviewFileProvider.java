package com.swipeclean.gallery;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.webkit.MimeTypeMap;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Locale;

public class ReviewFileProvider extends ContentProvider {
    static Uri uri(String packageName,File file) {
        try {
            File root=Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                .getCanonicalFile();
            String relative=root.toPath().relativize(file.getCanonicalFile().toPath()).toString();
            return new Uri.Builder().scheme("content").authority(packageName+".documents").appendPath(relative).build();
        }catch(IOException e){throw new IllegalArgumentException(e);}
    }

    @Override public boolean onCreate(){return true;}

    private File resolve(Uri uri) throws FileNotFoundException {
        if(uri.getPathSegments().size()!=1)throw new FileNotFoundException();
        try {
            File root=Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                .getCanonicalFile();
            File file=new File(root,uri.getPathSegments().get(0)).getCanonicalFile();
            if(file.getPath().startsWith(root.getPath()+File.separator)&&file.isFile())return file;
        }catch(IOException ignored){}
        throw new FileNotFoundException();
    }

    @Override public String getType(Uri uri) {
        String name=uri.getLastPathSegment();
        int dot=name==null?-1:name.lastIndexOf('.');
        String mime=dot<0?null:MimeTypeMap.getSingleton().getMimeTypeFromExtension(
            name.substring(dot+1).toLowerCase(Locale.ROOT));
        return mime==null?"application/octet-stream":mime;
    }

    @Override public ParcelFileDescriptor openFile(Uri uri,String mode) throws FileNotFoundException {
        if(!"r".equals(mode))throw new FileNotFoundException();
        return ParcelFileDescriptor.open(resolve(uri),ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override public Cursor query(Uri uri,String[] projection,String selection,String[] args,String order) {
        String[] columns=projection==null?new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE}:projection;
        MatrixCursor result=new MatrixCursor(columns);
        try {
            File file=resolve(uri);
            Object[] values=new Object[columns.length];
            for(int i=0;i<columns.length;i++) {
                if(OpenableColumns.DISPLAY_NAME.equals(columns[i]))values[i]=file.getName();
                else if(OpenableColumns.SIZE.equals(columns[i]))values[i]=file.length();
            }
            result.addRow(values);
        }catch(FileNotFoundException ignored){}
        return result;
    }

    @Override public Uri insert(Uri uri,ContentValues values){throw new UnsupportedOperationException();}
    @Override public int delete(Uri uri,String selection,String[] args){throw new UnsupportedOperationException();}
    @Override public int update(Uri uri,ContentValues values,String selection,String[] args){throw new UnsupportedOperationException();}
}
