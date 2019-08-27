package dem.vac.androidndkopencv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.WindowManager;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 作者：Vaccae
 * 邮箱：3657447@qq.com
 * 创建时间：2019-06-12 15:42
 * 功能模块说明：
 */
public class VaccaeSurfaceView extends SurfaceView implements SurfaceHolder.Callback {

    //每五帧识别一次
    private int ocrtimes=0;

    private String showtext="";

    public void Setshowtext(String string) {
        showtext=string;
    }

    //Camera相关
    //设置摄像头ID，我们默认为后置
    private int mCameraIndex=Camera.CameraInfo.CAMERA_FACING_BACK;
    //定义摄像机
    private Camera camera;
    //定义Camera的回调方法
    private Camera.PreviewCallback previewCallback=new Camera.PreviewCallback() {
        @Override
        public void onPreviewFrame(byte[] bytes, Camera camera) {
            synchronized (this) {
                int width=camera.getParameters().getPreviewSize().width;
                int height=camera.getParameters().getPreviewSize().height;
                Log.d("frame", "width:" + width + " height:" + height);
                Canvas canvas=holder.lockCanvas();
                if (canvas != null) {
                    Log.i("frame", "canvas width:" + canvas.getWidth() + " height:" + canvas.getHeight());
                    canvas.drawColor(0, android.graphics.PorterDuff.Mode.CLEAR);
                    List<Bitmap> cacheBitmaps=nv21ToBitmap(bytes, width, height);

                    //画图出来
                    for (int i=0; i < cacheBitmaps.size(); i++) {
                        //计算出当前图的矩形然后画出来
                        RectF rectF=CreateBitmapRect(i, cacheBitmaps.size(), canvas.getWidth(), canvas.getHeight());
                        canvas.drawBitmap(cacheBitmaps.get(i), null, rectF, null);
                    }


                    holder.unlockCanvasAndPost(canvas);
                }
            }
        }
    };

    //定义SurfaceHolder
    private SurfaceHolder holder;
    //定义SurfaceTexture;
    private SurfaceTexture surfaceTexture;

    //WindowManager 用于获取摄像机方向
    private WindowManager windowManager;
    //当前相机角度
    private int rotatedegree=0;

    //构造函数
    public VaccaeSurfaceView(Context context) {
        super(context);


        //获取WindowManager
        windowManager=(WindowManager) context.getSystemService(Context.WINDOW_SERVICE);

        //实例化Surfacetexture
        surfaceTexture=new SurfaceTexture(2);
        //获取Holder
        holder=getHolder();
        //加入SurfaceHolder.Callback在类中implements
        holder.addCallback(this);
        holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        //保持屏幕常亮
        holder.setKeepScreenOn(true);


    }

    @Override
    public void surfaceCreated(SurfaceHolder surfaceHolder) {
        //开启摄像机
        startCamera(mCameraIndex);
    }

    @Override
    public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i1, int i2) {
        int angel=windowManager.getDefaultDisplay().getRotation();
        switch (angel) {
            case Surface.ROTATION_0:
                rotatedegree=90;
                break;
            case Surface.ROTATION_90:
                rotatedegree=0;
                break;
            case Surface.ROTATION_180:
                rotatedegree=270;
                break;
            case Surface.ROTATION_270:
                rotatedegree=180;
                break;
            default:
                rotatedegree=0;
                break;
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
        //关闭摄像机
        stopCamera();
    }

    //region 开启关闭Camera
    //开启摄像机
    private void startCamera(int mCameraIndex) {
        // 初始化并打开摄像头
        if (camera == null) {
            try {
                camera=Camera.open(mCameraIndex);
            } catch (Exception e) {
                return;
            }
            //获取Camera参数
            Camera.Parameters params=camera.getParameters();
            if (params.getSupportedFocusModes().contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
                // 自动对焦
                params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
            }
            params.setPreviewFormat(ImageFormat.NV21); // 设置预览图片格式
            params.setPictureFormat(ImageFormat.JPEG); // 设置拍照图片格式

            camera.setParameters(params);

            try {
                camera.setPreviewCallback(previewCallback);
                camera.setPreviewTexture(surfaceTexture);
//                camera.setPreviewDisplay(holder);
                //旋转90度
//                camera.setDisplayOrientation(90);
                camera.startPreview();
            } catch (Exception ex) {
                ex.printStackTrace();
                camera.release();
                camera=null;
            }
        }
    }

    //关闭摄像机
    private void stopCamera() {
        if (camera != null) {
            camera.setPreviewCallback(null);
            camera.stopPreview();
            camera.release();
            camera=null;
        }

    }
    //endregion

    private List<Bitmap> nv21ToBitmap(byte[] nv21, int width, int height) {
        Bitmap bitmap=null;
        List<Bitmap> bitmaps=new ArrayList<>();
        try {
            YuvImage image=new YuvImage(nv21, ImageFormat.NV21, width, height, null);
            ByteArrayOutputStream stream=new ByteArrayOutputStream();
            image.compressToJpeg(new Rect(0, 0, width, height), 80, stream);
            //将rawImage转换成bitmap
            BitmapFactory.Options options=new BitmapFactory.Options();
            options.inPreferredConfig=Bitmap.Config.ARGB_8888;
            bitmap=BitmapFactory.decodeByteArray(stream.toByteArray(), 0, stream.size(), options);

            //加入图像旋转
            Matrix m=new Matrix();
            m.postRotate(rotatedegree);
            bitmap=Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(),
                    m, true);

            ocrtimes++;
            //调用JNI方法处理图像
            bitmaps=VaccaeOpenCVJNI.getCameraframebitbmp(bitmap, showtext);

            stream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return bitmaps;
    }


    //根据总的图片数生成当前的图片矩形
    private RectF CreateBitmapRect(int bitmapidx, int totalbitmap, int totalweight, int totalheight) {
        int rectleft, rectright, recttop, rectbottom;
        int bitmapweight, bitmapheight;

        RectF rectF=null;
        if (totalbitmap > 1 && bitmapidx <= totalbitmap) {
            //求最合适的比例,求平方根
            int rowcol=(int) Math.ceil(Math.sqrt(totalbitmap));

            //计算矩形的，如果总数和计算的平方根数一样，那只分列，不分行
            if (rowcol == totalbitmap) {
                bitmapweight=totalweight / rowcol; //宽度
                bitmapheight=totalheight;          //高度
                rectleft=bitmapidx * bitmapweight;//左
                recttop=0;                        //上
                rectright=rectleft + bitmapweight;           //右
                rectbottom=recttop + bitmapheight;          //下
            } else {
                int row=bitmapidx / rowcol;  //第几行
                int col=bitmapidx % rowcol;  //第几列
                bitmapweight=totalweight / rowcol; //宽度
                bitmapheight=totalheight / rowcol;//高度
                rectleft=col * bitmapweight;  //左
                recttop=row * bitmapheight;   //上
                rectright=rectleft + bitmapweight;  //右
                rectbottom=recttop + bitmapheight; //下
            }
            rectF=new RectF(rectleft, recttop, rectright, rectbottom);
        } else {
            rectF=new RectF(0, 0, totalweight, totalheight);
        }

        return rectF;
    }

}
