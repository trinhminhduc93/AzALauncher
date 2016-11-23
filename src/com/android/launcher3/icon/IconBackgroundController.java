
package com.android.launcher3.icon;

import java.util.ArrayList;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.widget.ImageView;

public class IconBackgroundController {

    public static final boolean DEBUG = true;
    private static final String TAG = "IconBackgroundController";

    // DucTMc: 20*20 + 20*20 + nd20*20. Khong tinh alpha, chi RGB
    private static final float MAGIC_MIN_DELTA_COLOR = 1200;
    private static final float MAGIC_MIN_DELTA_TRANSPARENT = 20;

    private static final float MAX_SCALE = 0.98f;

    // DucTMc: icon BMS co ti le vao khoang hon 0.8 1 chut
    // Ta bo qua nhung icon be nhu vay
    private static final float MIN_SCALE = 0.85f;
    // Bkav QuangLH: voi icon co bao la mau dac, co the lay nho hon
    private static final float MIN_SCALE_SOLID = 0.79f;
    private static final int NORMALIZE_STEP = 12;
    private static final int ALPHA_MIN = 60;
    private static final int SOLID_COUNT_MIN = 2;
    private Bitmap mBackgroundBitmap;

    private ArrayList<Point> mLeftEdge = new ArrayList<Point>();
    private ArrayList<Point> mRightEdge = new ArrayList<Point>();
    private ArrayList<Point> mTopEdge = new ArrayList<Point>();
    private ArrayList<Point> mBottomEdge = new ArrayList<Point>();

    /**
     * Bkav QuangLH: lay mau tu 4 arraylist tren, duoc dung de it phai xu ly khi kiem tra 1 icon co
     * nenn xet bound hay khong.
     */
    private ArrayList<Point> mNormalizedEdge = new ArrayList<Point>();

    private int mWidth;
    private int mHeight;

    private int mBoundLeftY;
    private int mBoundRightY;
    private int mCenterX;

    private TransparentOnlyValidator mTransparentOnlyValidator;
    private SolidBoundValidator mSolidBoundValidator;

    public IconBackgroundController(Context context, int drawableId) {
        mBackgroundBitmap = createBitmap(context, drawableId);

        mTransparentOnlyValidator = new TransparentOnlyValidator();
        mSolidBoundValidator = new SolidBoundValidator();

        initFourEdges();
    }

    public void drawBoundInRed(ImageView imageVIew) {
        drawBoundInRed(mBackgroundBitmap);
        imageVIew.setImageBitmap(mBackgroundBitmap);
    }

    public Bitmap createBitmap(Context context, int drawableId) {
        Drawable drawable = context.getResources().getDrawable(drawableId);

        Bitmap b = Bitmap.createBitmap(
                Math.max(drawable.getIntrinsicWidth(), 1),
                Math.max(drawable.getIntrinsicHeight(), 1),
                Bitmap.Config.ARGB_8888);

        Canvas c = new Canvas(b);
        drawable.setBounds(0, 0, b.getWidth(), b.getHeight());
        drawable.draw(c);
        c.setBitmap(null);

        return b;
    }

    public void initFourEdges() {
        if (mBackgroundBitmap == null) {
            return;
        }

        mWidth = mBackgroundBitmap.getWidth();
        mHeight = mBackgroundBitmap.getHeight();

        //xac dinh le trai phai.
        int[] bounds = getBoundLeftRight(mBackgroundBitmap, mTransparentOnlyValidator, mWidth / 2);
        mBoundLeftY = bounds[0];
        mBoundRightY = bounds[1];
        mCenterX = (bounds[0] + bounds[1]) / 2;

        if (DEBUG) {
            Log.d(TAG, "initFourEdges: mBoundLeftY = " + mBoundLeftY + "; mBoundRightY = "
                    + mBoundRightY);
        }

        // Lay cac diem thuoc bound trai phai
        for (int i = 0; i < mHeight; i++) {
            int currentColor = mBackgroundBitmap.getPixel(mBoundLeftY, i);
            if (Color.alpha(currentColor) == 255) {
                mLeftEdge.add(new Point(mBoundLeftY, i));
            }

            currentColor = mBackgroundBitmap.getPixel(mBoundRightY, i);
            if (Color.alpha(currentColor) == 255) {
                mRightEdge.add(new Point(mBoundRightY, i));
            }

        }

        // Lay cac diem thuoc bound tren duoi
        for (int w = mBoundLeftY; w <= mBoundRightY; w++) {
            int boundTop = 0;
            int boundBottom = 0;
            for (int h = 0; h < mHeight; h++) {
                int currentColor = mBackgroundBitmap.getPixel(w, h);

                if (boundTop == 0 && Color.alpha(currentColor) == 255) {
                    boundTop = h;
                    /*
                     * if (DEBUG) { Log.d(TAG, "ra bound tren: w = " + w + "; h = " + h +
                     * "; Color.alpha(currentColor) = " + Color.alpha(currentColor)); }
                     */
                } else if (boundTop != 0
                        && Color.alpha(currentColor) == 255) {
                    // Bkav QuangLH: lay diem cuoi cung co mau la 255 de dam
                    // bao van thuc hien duoc hieu ung transparent o mep duoi
                    boundBottom = h;

                    /*
                     * if (DEBUG) { Log.d(TAG, "dang tim bound duoi: w = " + w + "; h = " + h +
                     * "; Color.alpha(currentColor) = " + Color.alpha(currentColor)); }
                     */

                }
            }
            mTopEdge.add(new Point(w, boundTop));
            mBottomEdge.add(new Point(w, boundBottom));
        }

        initNormalizedEdge();
    }

    private void initNormalizedEdge() {
        //chi goi khi 4 edge kia duoc khoi tao roi.
        mNormalizedEdge.clear();
        addToNormalizedEdge(mLeftEdge);
        addToNormalizedEdge(mRightEdge);
        addToNormalizedEdge(mTopEdge);
        addToNormalizedEdge(mBottomEdge);

    }

    private void addToNormalizedEdge(ArrayList<Point> points) {
        int i = 0;
        int size = points.size();
        while (i < size) {
            mNormalizedEdge.add(points.get(i));
            i += NORMALIZE_STEP;
        }
    }

    /**
     * xu ly bao vien bitmap, lam cac viec sau:
     * <ul>
     * <li>1- Kiem tra xem bitmap truyen vao co can bao vien hay khong. Neu toan bo vien mLeftEdge,
     * mRightEdge, mTopEdge, mBottomEdge cua bitmap nay deu co alpha du lon (VD 128 tro len) thi co
     * the tien hanh bao vien.
     * <li>2- Neu can bao vien, tien hanh scale bitmap ve cung kich thuoc voi bitmap background.
     * <li>3- Tien hanh bao: voi toan bo cac diem nam ngoai 4 edge, chuyen RGB ve 000000 va xet
     * alpha bang alpha cua background.
     * </ul>
     * 
     * @return true neu process thanh cong, false neu khong can process hoac that bai
     */
    public Bitmap process(Bitmap inBitmap) {
        inBitmap = nomalizeBitmap(inBitmap);

        int[] lines = new int[3];
        lines[0] = inBitmap.getWidth() / 4;
        lines[1] = lines[0] * 2;
        lines[2] = lines[0] * 3;
        int[] bounds = getBoundLeftRight(inBitmap, mTransparentOnlyValidator, lines[1]);
        int[] bounds2 = getBoundLeftRight(inBitmap, mSolidBoundValidator, lines);
        float scale2 = getScale(bounds2);
        float scale = getScale(bounds);
        if (DEBUG) {
            Log.d(TAG, "process: scale: " + scale + "; scale2: " + scale2);
        }   

        // Bkav QuangLH:
        // 1- Neu bounds qua be, xem bounds2 co dung duoc hay khong, duoc thi
        // dung
        // 2- Neu bounds hop le, chi dung bounds2 neu bounds2 hep hon bounds va
        // co scale phu hop
        if (scale < MIN_SCALE) {
            if (scale2 > MIN_SCALE_SOLID) {
                bounds = bounds2;
                scale = scale2;
            } else {
                // Bkav QuangLH: neu icon o giua anh qua be thi khong xu ly
                if (DEBUG) {
                    Log.d(TAG, "bo qua process vi scale be qua. scale = "
                            + scale);
                }
                return null;
            }
        } else if (scale2 > MIN_SCALE_SOLID && bounds2[0] > bounds[0] && bounds2[1] < bounds[1]) {
            bounds = bounds2;
            scale = scale2;
        }

        float delta = 0f;
        // Bkav QuangLH: neu inBitmap co xu huong lon hon background, khong
        // can xu ly gi them. Chi scale cho background be lai neu can.
        if (scale > 1f) {
            delta = 0f;
            scale = 1f;
        } else {
            // Bkav QuangLH: 0.95f nay cung la 1 so dang magic. Cho scale
            // to them 1 chut de tang ti le bound nam trong icon. VD de so
            // nay la 0.9 thi scale to qua, lai cho ca Google Play Store
            // vao.
            scale = scale * 0.95f;

            // Bkav QuangLH: nhu cau o day la thu nho mBackgroundBitmap lai
            // 1 khoang scale. Sau do dat trung tam voi inBitmap ket qua la A.
            //
            // So sanh vien cua A (tinh tu mNormalizedEdge) tren inBitmap.
            // neu hau het cac diem la solid thi Ok.
            float inCenterX = (bounds[0] + bounds[1]) / 2;
            delta = inCenterX - mCenterX * scale;
        }

        /*
         * if (DEBUG) { // Bkav QuangLH: XXX: khong hieu sao khong ve de len anh duoc for (Point p :
         * mNormalizedEdge) { int w = (int) (p.x * scale + delta); int h = (int) (p.y * scale +
         * delta); inBitmap.setPixel(w, h, Color.RED); } return inBitmap; }
         */

        if (needSetBound(inBitmap, scale, delta)) {
            if (DEBUG) {
                Log.d(TAG, "process: can xu ly bound, 2 bound cua inBitmap: "
                        + bounds[0] + "; " + bounds[1]);
            }

            if (scale == 1f) {
                // Bkav QuangLH: Khong de 1f de khong lay phan vien cua icon
                // goc, tang ti le bao dep hon.
                scale = 0.95f;
            }

            return processInternal(inBitmap, scale, delta);
        }
        if (DEBUG) {
            Log.d(TAG, "process: khong xu ly bound, 2 bound cua inBitmap: "
                    + bounds[0] + "; " + bounds[1]);
        }

        return null;
    }

    /**
     * Bkav QuangLH: Tien hanh bao: voi toan bo cac diem nam ngoai 4 edge, chuyen RGB ve 000000 va
     * xet alpha bang alpha cua background.
     */
    private Bitmap processInternal(Bitmap inBitmap, float scale, float delta) {
        // Scale lai cho vua bao neu scale < 1. Scale == 1f thi bo qua
        // XXX: MAX_SCALE, 0.98f la gia tri magic de dam bao delta luon lon hon
        // 1. Neu lon hon gia tri nay, thuc ra cung khong can xu ly scale
        // lam gi cho ton tai nguyen.
        if (scale < MAX_SCALE) {
            int oldWidth = inBitmap.getWidth();
            int oldHeight = inBitmap.getHeight();
            int newDelta = (int) (oldWidth * (1 - scale) / 2);
            if (DEBUG) {
                Log.d(TAG,
                        "processInternal: oldWidth = " + oldWidth + "; oldHeight = " + oldHeight
                                + "; newWidth = " + inBitmap.getWidth() + "; newHeight = "
                                + inBitmap.getHeight() + "; newDelta = " + newDelta + "; delta = "
                                + delta + "; scale = " + scale);
            }

            inBitmap = Bitmap.createBitmap(inBitmap, (int) newDelta, (int) newDelta,
                    oldWidth - 2 * (int) newDelta, oldHeight - 2 * (int) newDelta);
            inBitmap = Bitmap.createScaledBitmap(inBitmap, oldWidth, oldHeight, true);

        }

        // Chuyen toan bo cac diem nam ngoai 2 edge trai phai thanh khong
        // mau va transparent.

        for (int w = 0; w < mBoundLeftY; w++) {
            for (int h = 0; h < mHeight; h++) {
                changePixel(inBitmap, w, h);
            }
        }

        for (int w = mBoundRightY + 1; w < mWidth; w++) {
            for (int h = 0; h < mHeight; h++) {
                changePixel(inBitmap, w, h);
            }
        }

        // Chuyen toan bo cac diem nam ngoai 2 edge tren duoi thanh khong
        // mau va transparent
        for (Point p : mTopEdge) {
            for (int h = 0; h < p.y; h++) {
                changePixel(inBitmap, p.x, h);
            }
        }

        for (Point p : mBottomEdge) {
            for (int h = p.y + 1; h < mHeight; h++)
            {
                changePixel(inBitmap, p.x, h);
            }
        }

        return inBitmap;
    }

    /**
     * Bkav QuangLH: chuyen pixel tai vi tri [w, h] cua inBitmap thanh co alpha giong pixel o cung
     * vi tri trong mBackgroundBitmap va co mau la 000000
     */
    private void changePixel(Bitmap inBitmap, int w, int h) {
        int alpha = Color.alpha(mBackgroundBitmap.getPixel(w, h));
        int alphaMask = alpha << 24;

        int mask = (255 << 16) + (255 << 8) + 255; // 0x00ffffff
        int inColor = (inBitmap.getPixel(w, h) & mask) | alphaMask;

        inBitmap.setPixel(w, h, inColor);
    }

    /**
     * <p>
     * Bkav QuangLH: Kiem tra xem bitmap truyen vao co can bao vien hay khong.
     * <p>
     * Dau tien can xu ly de xac dinh xem can xet bound tren vung nao cua anh inBitmap. Neu anh
     * inBitmap nho hon anh background thi can lay vung o giua anh de xu ly.
     * <p>
     * Cach lam la:
     * <ul>
     * <li>1- Xac dinh boundLeftY, boundRightY cua inBitmap.
     * <li>2- Dua vao boundLeftY, boundRightY xac dinh xem vung nao o giua anh inBitmap can lay ra
     * de xet bao.
     * <li>3- Tinh tien va scale mNormalizedEdge phu hop khi xet bao
     * </ul>
     * <p>
     * Neu toan bo vien mLeftEdge, mRightEdge, mTopEdge, mBottomEdge cua bitmap nay deu co alpha du
     * lon (VD 128 tro len) thi co the tien hanh bao vien
     */
    public boolean needSetBound(Bitmap inBitmap, float scale, float delta) {

        int nonSolidCount = 0;
        for (Point p : mNormalizedEdge) {
            int w = (int) (p.x * scale + delta);
            int h = (int) (p.y * scale + delta);

            if (Color.alpha(inBitmap.getPixel(w, h)) < ALPHA_MIN) {
                nonSolidCount++;

                if (DEBUG) {
                    Log.d(TAG, "No: w = " + w + "; h = " + h + "; p.x = " + p.x +
                            "; p.y = " + p.y + "; alpha = " + Color.alpha(inBitmap.getPixel(w, h)));
                }

            } else if (DEBUG) {
                Log.d(TAG, "Yes: w = " + w + "; h = " + h + "; p.x = " + p.x + "; p.y = " + p.y +
                        "; alpha = " + Color.alpha(inBitmap.getPixel(w, h)));
            }
        }

        if (DEBUG) {
            Log.d(TAG, "needSetBound: nonSolidCOunt = " + nonSolidCount
                    + "; scale = " + scale + "; delta = " + delta);
        }

        // Bkav QuangLH: luc dau de la == 0 nhung khong can thiet. Minh
        // co khoang hon 600 diem can so sanh. Dung 80% la dat roi.
        return nonSolidCount < SOLID_COUNT_MIN;
    }

    // Bkav QuangLH: neu bitmap truyen vao chua to bang bitmap de scale
    // background
    // thi scale cho bang. Mac dinh la bitmap vuong, khong kiem tra dieu
    // kien
    private Bitmap nomalizeBitmap(Bitmap inBitmap) {
        int inHeight = inBitmap.getHeight();
        int inWidth = inBitmap.getWidth();

        boolean useWidth = false;
        int scaledLength = 0;
        if (inWidth > inHeight) {
            scaledLength = (int) (inBitmap.getHeight() * ((float) mHeight / inHeight));
        } else {
            scaledLength = (int) (inBitmap.getWidth() * ((float) mWidth / inWidth));
            useWidth = true;
        }

        if (inHeight != mHeight || inWidth != mWidth) {
            if (DEBUG) {
                Log.d(TAG, "nomalizeBitmap: sacle lai: inHeight = " + inHeight + "; inWidth = "
                        + inWidth
                        + "; scale cho to bang mWidth = " + mWidth + "; mHeight = " + mHeight
                        + "; scaledWidth = " + scaledLength);
            }
            if (useWidth) {
                return Bitmap.createScaledBitmap(inBitmap, mWidth, scaledLength, true);
            } else {
                return Bitmap.createScaledBitmap(inBitmap, scaledLength, mHeight, true);
            }
        } else {
            if (DEBUG) {
                Log.d(TAG,
                        "nomalizeBitmap: khong can scale: mHeight = inHeight = "
                                + inHeight);
            }
        }
        return inBitmap;
    }

    /** Bkav QuangLH: de debug */
    private void drawBoundInRed(Bitmap bitmap) {
        for (Point p : mLeftEdge) {
            bitmap.setPixel(p.x, p.y, Color.RED);
        }
        for (Point p : mRightEdge) {
            bitmap.setPixel(p.x, p.y, Color.RED);
        }
        for (Point p : mTopEdge) {
            bitmap.setPixel(p.x, p.y, Color.RED);
        }
        for (Point p : mBottomEdge) {
            bitmap.setPixel(p.x, p.y, Color.RED);
        }
    }

    private int[] getBoundLeftRight(Bitmap inBitmap, Validator validator, int... lines) {
        int[] bounds = new int[2];
        bounds[0] = 0;
        bounds[1] = 0;

        int width = inBitmap.getWidth();

        // Bkav QuangLH: tim bound left
        int i = 0;
        while (i < width && bounds[0] == 0) {
            int[] colors = getColorArray(inBitmap, i, lines);

            // Bkav QuangLH: XXX: chon gia tri 128 do thi thoang co nhung icon
            // van co cac diem transparent nam o giua icon lam tinh sai gia tri.
            // Mot dang magic.
            if (validator.isValid(colors)) {
                bounds[0] = i;

                if (DEBUG) {
                    StringBuilder sb = new StringBuilder(
                            "getBoundLeftRight: thay bound left mau la: i = " + i);
                    for (int j = 0; j < colors.length; j++) {
                        sb.append("; " + Integer.toHexString(colors[j]));
                    }
                    Log.d(TAG,
                            sb.toString());
                    validator.isValid(true, colors);
                }
            }
            i++;
        }

        // Bkav QuangLH: tim bound phai
        if (bounds[0] != 0) {
            i = width - 1;
            while (i > 0 && bounds[1] == 0) {
                int[] colors = new int[lines.length];
                for (int j = 0; j < colors.length; j++) {
                    colors[j] = inBitmap.getPixel(i, lines[j]);
                }

                if (validator.isValid(colors)) {

                    // Bkav QuangLH: neu kiem tra toi sat mep phai roi ma khong
                    // duoc
                    // thi lay mep phai lam bound phai. Neu phat hien diem co
                    // transparent thi lay truoc do 1 diem.
                    bounds[1] = i;
                    if (DEBUG) {
                        StringBuilder sb = new StringBuilder(
                                "getBoundLeftRight: thay bound right mau la: i = " + i);
                        for (int j = 0; j < colors.length; j++) {
                            sb.append("; " + Integer.toHexString(colors[j]));
                        }
                        Log.d(TAG,
                                sb.toString());
                        validator.isValid(true, colors);
                    }
                }
                i--;
            }
        }

        return bounds;
    }

    private int[] getColorArray(Bitmap inBitmap, int widthPixel, int[] lines) {
        int[] colors = new int[lines.length];
        for (int j = 0; j < colors.length; j++) {
            colors[j] = inBitmap.getPixel(widthPixel, lines[j]);
        }

        return colors;
    }

    private float getScale(int[] bounds) {
        float inIconSolidWidth = bounds[1] - bounds[0] + 1;
        float backgroundIconSolidWidth = mBoundRightY - mBoundLeftY + 1;

        return inIconSolidWidth / backgroundIconSolidWidth;
    }

    /**
     * <p>
     * Bkav QuangLH: cat 3 duong ngang o 25%, 50% va 75%. Lan luot so sanh cac diem thuoc 3 duong
     * nay voi nhau. Lan dau tien ca 3 gia tri khac nhau la mep trai. Lan cuoi cung ca 3 gia tri
     * khac nhau la mep phai.
     * <p>
     * XXX: Hien tai chi lay 3 duong, co the la phu hop ve thuat toan, khong nang lam, tuy the co
     * the tinh nham neu icon thuoc dang nhu cua Calculator (o day, may ma lay duoc diem giua nen
     * van tinh dung. Muon khac phuc thi khong co cach nao la phai lay rat nhieu diem hon. VD toan
     * bo cac diem tu 1/4 toi 3/4.
     * <p>
     * XXX 2: Can luu y cac icon co gradient do bong tu tren xuong duoi nhu Facebook. Viec so sanh
     * firstColor va thirdColor la can thiet de xac dinh bien cho cac icon nay.
     */
    private static class TransparentOnlyValidator implements Validator {

        @Override
        public boolean isValid(int... color) {
            return Color.alpha(color[0]) == 255;
        }

        @Override
        public boolean isValid(boolean print, int... color) {
            return Color.alpha(color[0]) == 255;
        }

    }

    /**
     * Bkav QuangLH: Cat o giua bitmap 1 duong ngang, xac dinh 2 diem co transparent la 255 o 2 dau
     * cua bitmap va goi do la 2 mep trai phai cua Bitmap. TODO: khong xu ly duoc neu icon co vien
     * la mau dac.
     */
    private static class SolidBoundValidator implements Validator {

        @Override
        public boolean isValid(int... color) {
            return isValid(false, color);
        }

        @Override
        public boolean isValid(boolean print, int... color) {
            return !isSameColor(color[0], color[1], print) || !isSameColor(color[0],
                    color[2], print);
        }

        private boolean isSameColor(int colorOne, int colorTwo, boolean print) {
            // int deltaAlpha = colorOne >> 24 - colorTwo >> 24;
            int deltaRed = ((colorOne & 0x00ffffff) >> 16) - ((colorTwo & 0x00ffffff) >> 16);
            int deltaGreen = ((colorOne & 0x0000ffff) >> 8) - ((colorTwo & 0x0000ffff) >> 8);
            int deltaBlue = (colorOne & 0x000000ff) - (colorTwo & 0x000000ff);

            if (DEBUG && print) {
                Log.d(TAG,
                        "isSameColor: 2 mau la: " + Integer.toHexString(colorOne) + "; "
                                + Integer.toHexString(colorTwo) + "; deltaRed = " + deltaRed
                                + "; deltaGreen = " + deltaGreen + "; deltaBlue = " + deltaBlue);
            }

            return deltaRed * deltaRed + deltaGreen * deltaGreen + deltaBlue
                    * deltaBlue < MAGIC_MIN_DELTA_COLOR;
        }

        private boolean isSameColor(int colorOne, int colorTwo) {
            return isSameColor(colorOne, colorTwo, false);
        }
    }

    private static interface Validator {
        boolean isValid(int... color);

        boolean isValid(boolean print, int... color);
    }
}