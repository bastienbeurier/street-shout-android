package com.streetshout.android.utils;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Point;
import android.net.Uri;
import android.os.Environment;
import android.os.Parcelable;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.Toast;
import com.streetshout.android.R;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class ImageUtils {

    public static final int MEDIA_TYPE_IMAGE = 1;

    public static boolean isSDPresent() {
        return android.os.Environment.getExternalStorageState().equals(android.os.Environment.MEDIA_MOUNTED);
    }

    public static File getFileToStoreImage() {
        File photostorage = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        return new File(photostorage, (System.currentTimeMillis()) + ".jpg");
    }

    public static Intent getPhotoChooserIntent(Context ctx, File photoFile) {
        List<Intent> cameraIntents = new ArrayList<Intent>();

        Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(photoFile));
        cameraIntents.add(cameraIntent);

        Intent galleryIntent = new Intent();
        galleryIntent.setType("image/*");
        galleryIntent.setAction(Intent.ACTION_GET_CONTENT);

        Intent chooserIntent = Intent.createChooser(galleryIntent, ctx.getString(R.string.select_picture_from));
        chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, cameraIntents.toArray(new Parcelable[]{}));

        return chooserIntent;
    }

    static public Bitmap getResizedBitmap(Bitmap bm) {
        int width = bm.getWidth();
        int height = bm.getHeight();

        if (height > Constants.SHOUT_BIG_RES && width > Constants.SHOUT_BIG_RES) {

            float scale = ((float) Constants.SHOUT_BIG_RES) / Math.min(width, height);
            // CREATE A MATRIX FOR THE MANIPULATION
            Matrix matrix = new Matrix();
            // RESIZE THE BIT MAP
            matrix.postScale(scale, scale);

            // "RECREATE" THE NEW BITMAP
            Bitmap resizedBitmap = Bitmap.createBitmap(bm, 0, 0, width, height, matrix, false);
            return resizedBitmap;
        } else {
            return bm;
        }
    }

    static public Bitmap decodeFileAndShrinkBitmap(String filePath) {
        Bitmap bitmap = null;

        //Decode image size
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;

        try {
            FileInputStream fis = new FileInputStream(filePath);
            BitmapFactory.decodeStream(fis, null, o);

            fis.close();
            int ratio = 1;
            if (o.outHeight > Constants.SHOUT_BIG_RES && o.outWidth > Constants.SHOUT_BIG_RES) {
                float scale = ((float) Constants.SHOUT_BIG_RES) / Math.min(o.outHeight, o.outWidth);
                ratio = (int) (1 / scale);
            }

            //Decode with inSampleSize
            BitmapFactory.Options o2 = new BitmapFactory.Options();
            o2.inSampleSize = ratio;
            fis = new FileInputStream(filePath);
            bitmap = BitmapFactory.decodeStream(fis, null, o2);
            fis.close();

            return bitmap;
        } catch (IOException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            return null;
        }
    }

    static public Bitmap decodeFileAndShrinkAndMakeSquareBitmap(String filePath) {
        Bitmap bitmap = decodeFileAndShrinkBitmap(filePath);
        bitmap = makeSquareBitmap(bitmap);

        return bitmap;
    }

    static public Bitmap shrinkAndMakeSquareBitmap(Bitmap bitmap) {
        Bitmap newBitmap = getResizedBitmap(bitmap);
        newBitmap = makeSquareBitmap(newBitmap);

        return newBitmap;
    }

    static public Bitmap mirrorBitmap(Bitmap bitmap) {
        Matrix matrix = new Matrix();
        matrix.preScale(-1.0f, 1.0f);
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, false);
    }


    static public Bitmap makeSquareBitmap(Bitmap bitmap) {
        if (bitmap.getWidth() >= bitmap.getHeight()){

            return Bitmap.createBitmap(
                    bitmap,
                    bitmap.getWidth()/2 - bitmap.getHeight()/2,
                    0,
                    bitmap.getHeight(),
                    bitmap.getHeight()
            );

        }else{

            return Bitmap.createBitmap(
                    bitmap,
                    0,
                    bitmap.getHeight()/2 - bitmap.getWidth()/2,
                    bitmap.getWidth(),
                    bitmap.getWidth()
            );
        }
    }

    static public String getPathFromUri(Context ctx, Uri selectedImage) {
        String[] filePathColumn = { MediaStore.Images.Media.DATA };

        Cursor cursor = ctx.getContentResolver().query(selectedImage,
                filePathColumn, null, null, null);
        cursor.moveToFirst();

        int columnIndex = cursor.getColumnIndex(filePathColumn[0]);
        String photoPath = cursor.getString(columnIndex);
        cursor.close();

        return photoPath;
    }

    static public void storeBitmapInFile(String pathName, Bitmap bm) {
        File file = new File(pathName);
        if (file.exists ()) file.delete ();
        try {
            FileOutputStream out = new FileOutputStream(file);
            bm.compress(Bitmap.CompressFormat.JPEG, 90, out);
            out.flush();
            out.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    static public void savePictureToGallery(Context ctx, String photoPath) {
        try {
            String photoName = GeneralUtils.getDeviceId(ctx) + "--" + (new Date()).getTime() + ".jpg";
            MediaStore.Images.Media.insertImage(ctx.getContentResolver(), photoPath, photoName, "Shout photo");
        } catch (FileNotFoundException e) {
            Toast.makeText(ctx, ctx.getString(R.string.failed_saving_photo), Toast.LENGTH_SHORT ).show();
        }
    }

    static public void copyFile(String src, File dst) {
        try {
            InputStream in = new FileInputStream(src);
            OutputStream out = null;
            out = new FileOutputStream(dst);

            // Transfer bytes from in to out
            byte[] buf = new byte[1024];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
            in.close();
            out.close();
        } catch (Exception e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
    }

    /** Create a File for saving an image or video */
    public static File getOutputMediaFile(int type){
        Boolean isSDPresent = android.os.Environment.getExternalStorageState().equals(android.os.Environment.MEDIA_MOUNTED);

        if (isSDPresent) {
            File mediaStorageDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
            // Create the storage directory if it does not exist
            if (! mediaStorageDir.exists()){
                if (!mediaStorageDir.mkdirs()){
                    return null;
                }
            }

            // Create a media file name
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            File mediaFile;
            if (type == MEDIA_TYPE_IMAGE){
                mediaFile = new File(mediaStorageDir.getPath() + File.separator +
                        "IMG_"+ timeStamp + ".jpg");
            } else {
                return null;
            }

            return mediaFile;
        }
        else
        {
        }

        return null;
    }

    static public Bitmap rotateImage(Bitmap bitmap) {
        Matrix matrix = new Matrix();
        matrix.postRotate(90);

        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }

    static public Bitmap reverseRotateImage(Bitmap bitmap) {
        Matrix matrix = new Matrix();
        matrix.postRotate(270);

        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }

    public static Uri storeImage(Bitmap image) {
        FileOutputStream fileOutputStream = null;
        File path = null;

        Boolean isSDPresent = android.os.Environment.getExternalStorageState().equals(android.os.Environment.MEDIA_MOUNTED);
        if (isSDPresent)
        {
            path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
            File file = new File(path, "shout_" + new Date().getTime() + ".jpg");
            path	= file;
        } else {
            return null;
        }

        if (path == null) {
            return null;
        }

        try {
            fileOutputStream = new FileOutputStream(path);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        if (fileOutputStream != null)
        {
            BufferedOutputStream bos = new BufferedOutputStream(fileOutputStream);
            image.compress(Bitmap.CompressFormat.JPEG, 100, bos);
            try {
                bos.flush();
                bos.close();
                fileOutputStream.flush();
                fileOutputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            return Uri.parse("file://" + path.getAbsolutePath());
        }

        return null;
    }

    public static int getScreenWidth(Context context) {
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        return size.x;
    }

    public static int getScreenHeight(Context context) {
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        return size.y - getNavigationBarHeight(context);
    }

    public static int getNavigationBarHeight(Context context) {
        Resources resources = context.getResources();
        int resourceId = resources.getIdentifier("navigation_bar_height", "dimen", "android");
        if (resourceId > 0) {
            return resources.getDimensionPixelSize(resourceId);
        }
        return 0;
    }
}
