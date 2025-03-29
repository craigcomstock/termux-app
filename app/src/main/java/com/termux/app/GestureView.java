package com.termux.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import com.termux.app.terminal.TermuxTerminalViewClient;
import com.termux.shared.logger.Logger;
import com.termux.terminal.TerminalSession;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Properties;

public final class GestureView extends View {

    Paint paint;
    Path path2;
    Bitmap bitmap;
    Canvas canvas;
    Properties gestures;
    int mHandlerCounter = 0; // keep track of which counter is "current" and only allow that one to cancel

    // need the viewclient to change fontsize
    private TermuxTerminalViewClient mTermuxTerminalViewClient;
    // the activity has the terminal session in which we inject characters recognized
    private TermuxActivity mTermuxActivity;
    private InputStream mGestureConfInputStream;

    private static final String LOG_TAG = "GestureView";

    private static Handler mHandler = null;

    public GestureView(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);

        paint = new Paint();
        path2 = new Path();
        paint.setDither(true);
        paint.setColor(Color.parseColor("#FF6600"));
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(8);

        GestureView self = this;

        mHandler = new Handler() {

            @Override
            public void handleMessage(Message msg) {
                if (msg.what == mHandlerCounter) {
                    self.clearDrawing();
                    self.invalidate();
                }
            }
        };

        bitmap = Bitmap.createBitmap(820,480,Bitmap.Config.ARGB_4444);
        canvas = new Canvas(bitmap);
    }

    public void setTerminalViewClient(TermuxTerminalViewClient client) {
        this.mTermuxTerminalViewClient = client;
    }

    public void setTermuxActivity(TermuxActivity termuxActivity) {
        mTermuxActivity = termuxActivity;
    }

    public void setGestureConfInputStream(InputStream gestureConfInputStream) {
        mGestureConfInputStream = gestureConfInputStream;
    }

    private ArrayList<DrawingClass> DrawingClassArrayList = new ArrayList<DrawingClass>();

    // gesture stuff
    class TsEvent {
        public int x;
        public int y;
        public int type;
    }
    TsEvent[] events = new TsEvent[300];

    boolean slash, dot, shift, control, escape, alt, caps, prefix = false;

    class Point {
        public int x;
        public int y;
    }

    int MAX_POINTS = 300;
    class Gesture {
        public int minx = 0;
        public int maxx = 0 ;
        public int miny = 0;
        public int maxy = 0;
        public int numPoints = 0;
        public Point[] points = new Point[MAX_POINTS]; // how very C of me. :p but a limit is good
    }
    Gesture gs = new Gesture();
    int gi = 0;
    int view_width, view_height = 0;

    @Override
    protected void onSizeChanged(int xNew, int yNew, int xOld, int yOld) {
        super.onSizeChanged(xNew, yNew, xOld, yOld);
        view_width = xNew;
        view_height = yNew;
    }


    /**
     * In one case I ran `termux-setup-storage` and got permission denied which also breaks loading gestures from storage.
     * I was able to restore termux permissions by visiting Settings->Applications->Termux->Permissions
     * and revoking/denying permission and re-running `termux-setup-storage`.
     *
     * Problem is currently this REQUIRES storage access, maybe it shouldn't? Just use resource if no file is present!
     */
    public void loadGestureConf() {
        // TODO, why does logDebug() not show up in emulator logcat output?
        Logger.logError(LOG_TAG, "loadGestureConf()");
        try {
            String gesturesFilePath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/gesture.conf";
            Logger.logError(LOG_TAG, "gesturesFilePath="+gesturesFilePath);
            File gesturesFile = new File(gesturesFilePath);
            Logger.logError(LOG_TAG, "gesturesFile="+gesturesFile);

            if (gesturesFile.exists() && gesturesFile.length() == 0) {
                // empty file? remove it, reload from resources
                gesturesFile.delete();
            }
            // if gesture.conf isn't at /sdcard/gesture.conf then copy from resources
            if (!gesturesFile.exists()) {
                Logger.logError(LOG_TAG, "gesturesFile doesn't exist, try copying from resources...");
                InputStream is = mGestureConfInputStream;
                OutputStream os = null;
                try {
                    // copy from resources
                    os = new FileOutputStream(gesturesFile);
                    byte[] buffer = new byte[1024];
                    int length;
                    while ((length = is.read(buffer)) > 0) {
                        os.write(buffer, 0, length);
                    }
                } catch(Exception e) {
                    Logger.logError(LOG_TAG, "copy raw resource gesture.conf failed: "+e);
                } finally {
                    if (is != null) {
                        is.close();
                    }
                    if (os != null) {
                        os.close();
                    }
                }
            }
            /**
             * we prefer gesture.conf but if the above copy didn't work (no permissions for example)
             * then we use the resource instead.
             */
            BufferedReader reader = null;
            gestures = new Properties();
            if (gesturesFile.exists()) {
                Logger.logError(LOG_TAG, "trying to read gestures from gesturesFile: " + gesturesFile.getAbsolutePath());
                reader = new BufferedReader(new FileReader(gesturesFile));
            } else {
                Logger.logError(LOG_TAG, "copy of gestures resource failed, read gestures from resource");
                reader = new BufferedReader(new InputStreamReader(mGestureConfInputStream));
            }
            try {
                String line = reader.readLine();
                while (line != null) {
                    if (line.startsWith("#")) {
                        Logger.logError(LOG_TAG, "comment line: "+line);
                    } else {
                        String parts[] = line.split(" ");
                        if (parts.length != 2) {
                            Logger.logError(LOG_TAG, "bad line: "+line);
                        } else {
//                            Logger.logError(LOG_TAG, "key: "+parts[0]+", value: "+parts[1]);
                            gestures.setProperty(parts[0],parts[1]);
                        }
                    }
                    line = reader.readLine();
                }
            } finally {
                if (reader != null) {
                    reader.close();
                }
            }
            if (gestures.size() == 0) {
                // file was read but was empty, delete file to reload from resources next time
            }
//            Logger.logError(LOG_TAG, "gestures="+gestures);
        } catch (Exception e) {
            Logger.logError(LOG_TAG, "Error in loadGestureConf(): " + e);
        }
    }

    protected void clearDrawing() {
        DrawingClassArrayList.clear();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        DrawingClass pathWithPaint = new DrawingClass();
        canvas.drawPath(path2, paint);
        if (event.getAction() == MotionEvent.ACTION_DOWN) {

            mHandlerCounter++;
            if (mHandlerCounter > 50) { // certainly you can't draw more than 50 gestures in a second right?
                mHandlerCounter = 0;
            }

            int x = (int)event.getX();
            int y = (int)event.getY();
            //		Log.e("GESTURE", "ACTION_DOWN, x="+x+", y="+y);

            path2.reset(); // each gesture is separate
            path2.moveTo(x, y);
            path2.lineTo(x+1, y+1);
            pathWithPaint.setPath(path2);
            pathWithPaint.setPaint(paint);
            DrawingClassArrayList.add(pathWithPaint);

            // init a new gesture
            gs = new Gesture();
            gi = 0;
            gs.minx = view_width;
            gs.miny = view_height;

            // update things as normal
            updateMax(gs, x, y);
            gs.points[gi] = new Point();
            gs.points[gi].x = x;
            gs.points[gi].y = y;
            gi++;
            if (gi > MAX_POINTS - 1) {
                gi--; // just keep pushing the last point into the last slot
            }
        } else if (event.getAction() == MotionEvent.ACTION_UP) {
            gs.numPoints = gi;

            // TODO how to get the physical size of the screen so we can make the
            // minimum chunk ratio (fourth parameter to handleGesture()) be about
            // the size of the average human finger?
            // TODO maybe add the minimum chunk ratio as a config in gesture.conf?
            // For big screen phone like nexus5 view_width / 6 was good
            // but for kc05 watch I need something a bit more forgiving view_width / 4? (nope, try 5)
            // watch 240x240, chunk (width/4)=60
            // nexus5 1080x1920, chunk (width/6)=320
            String output = handleGesture(gs, view_width, view_height, (int)(view_width / 4.5));
            Logger.logDebug(LOG_TAG, "handleGesture()=>'"+output+"'");
            gs = new Gesture();
            gi = 0;

            mHandler.sendEmptyMessageDelayed(mHandlerCounter, 1000); // TODO delay should be configurable in a file

        } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
            int x = (int)event.getX();
            int y = (int)event.getY();
            //		Log.e("GESTURE", "ACTION_MOVE, x="+x+", y="+y);

            path2.lineTo(event.getX(), event.getY());
            pathWithPaint.setPath(path2);
            pathWithPaint.setPaint(paint);
            DrawingClassArrayList.add(pathWithPaint);

            updateMax(gs, x, y);
            gs.points[gi] = new Point();
            gs.points[gi].x = x;
            gs.points[gi].y = y;
            gi++;
            if (gi > MAX_POINTS - 1) {
                gi--; // just keep pushing the last point into the last slot
            }
        }

        Logger.logDebug(LOG_TAG, "CRAIG: onTouchEvent(), calling invalidate()");
        invalidate();
        return true;
    }

    void updateMax(Gesture gs, int x, int y) {
        if (x > gs.maxx) {
            gs.maxx = x;
        }
        if (x < gs.minx) {
            gs.minx = x;
        }
        if (y > gs.maxy) {
            gs.maxy = y;
        }
        if (y < gs.miny) {
            gs.miny = y;
        }
    }

    // TODO UTF-8, other character set support? Use a String instead? auto-support for such things?
    String handleGesture(Gesture gs, int screen_width, int screen_height, int minimum_chunk_size) {
        //	    Log.e("TERMUX_ACTIVITY", "handleGesture(), screen_width="+screen_width+", screen_height="+screen_height+", minimum_chunk_size="+minimum_chunk_size);

        String toput = "";
        int MAX_KEYS = 50;
        int key_x[] = new int[MAX_KEYS];
        int key_y[] = new int[MAX_KEYS];
        int i, kxi, kyi;
        int sx, sy;
        int tx, ty;
        int rx, ry;
        i = 0;
        kxi = kyi = -1;
        sx = (gs.maxx - gs.minx) / 3;
        sy = (gs.maxy - gs.miny) / 3;
        if (sx < minimum_chunk_size) {
            sx = minimum_chunk_size;
        }
        if (sy < minimum_chunk_size) {
            sy = minimum_chunk_size;
        }
        int nw, ne, se, sw;
        nw = ne = se = sw = 0;
        //	    Log.e("TERMUX_ACTIVITY", "handleGesture(), numPoints="+gs.numPoints+", minx="+gs.minx+", maxx="+gs.maxx+", miny="+gs.miny+", maxy="+gs.maxy+", sx="+sx+", sy="+sy);
        for (; i < gs.numPoints; i++) {
            rx = gs.points[i].x - gs.minx;
            tx = rx / sx;
            ry = gs.points[i].y - gs.miny;
            ty = ry / sy;
            if (tx == 3) {
                tx = 2;
            }
            if (ty == 3) {
                ty = 2;
            }
            //		Logger.logError(LOG_TAG, "handleGesture(), rx="+rx+", tx="+tx+", ry="+ry+", ty="+ty);

            if (kxi == -1 || key_x[kxi] != tx) {
                key_x[++kxi] = tx;
                if (kxi > MAX_KEYS - 2) {
                    kxi = MAX_KEYS - 2;
                }
            }
            if (kyi == -1 || key_y[kyi] != ty) {
                key_y[++kyi] = ty;
                if (kyi > MAX_KEYS - 2) {
                    kyi = MAX_KEYS - 2;
                }
            }

            if (tx == 0 && ty == 0) {
                nw = 1;
            }
            if (tx == 2 && ty == 0) {
                ne = 1;
            }
            if (tx == 2 && ty == 2) {
                se = 1;
            }
            if (tx == 0 && ty == 2) {
                sw = 1;
            }
        }
        if (kxi == -1) {
            key_x[++kxi] = 0;
            if (kxi > MAX_KEYS - 2) {
                kxi = MAX_KEYS - 2;
            }
        }
        if (kyi == -1) {
            key_y[++kyi] = 0;
            if (kyi > MAX_KEYS - 2) {
                kyi = MAX_KEYS - 2;
            }
        }
        String tmp, key = "";
        if (dot) {
            key += ".";
            dot = false;
        }
        if (slash) {
            key += "/";
            slash = false;
        }

        i = 0;
        for (; i <= kxi; i++) {
            key += key_x[i];
        }
        key += ":";
        i = 0;
        for (; i <= kyi; i++) {
            key += key_y[i];
        }
        if (key.equals("0:0") || key.equals(".0:0")) {
            //		Log.d("TERMUX_ACTIVITY", "gesture, gs.maxy="+gs.maxy+", gs.miny="+gs.min
            if (gs.maxy > screen_height - minimum_chunk_size) {
                key += "s";
            }
            if (gs.miny < minimum_chunk_size) {
                key += "n";
            }
            if (gs.maxx > screen_width - minimum_chunk_size) {
                key += "e";
            }
            if (gs.minx < minimum_chunk_size) {
                key += "w";
            }
        }

        if (nw == 1 || ne == 1 || sw == 1 || se == 1) {
            key += "x";
            if (nw == 1) {
                key += "1";
            }
            if (ne == 1) {
                key += "2";
            }
            if (se == 1) {
                key += "3";
            }
            if (sw == 1) {
                key += "4";
            }
        }

        // at this point we have our key, I think, let's just print it out and see if that much works. :+1:
//        Logger.logError(LOG_TAG, "handleGesture(), key='"+key+"'");

        if (gestures == null || gestures.size() == 0) {
            // TODO this might slow down the first recog but how else to do it?
            Logger.logError(LOG_TAG, "gesture.conf not loaded, do it now");
            //if (ensureStoragePermissionGranted()) {
            loadGestureConf();
            //} else {
            //    Logger.logError(LOG_TAG, "unable to get storage permission, can't load gesture, bailing");
            //   return "";
            //}
        }
        String value = gestures.getProperty(key);
//        Logger.logError(LOG_TAG, "value from gesture.conf: "+value);
        //letterView.setGravity(Gravity.CENTER_VERTICAL | Gravity.CENTER_HORIZONTAL);
        if (value != null) {
            // first translate some special names to single character
            if (value.equals("enter")) {
                toput = "" + (char)0x0d;
            } else if (value.equals("prefix")) {
                // TODO 2023-may-17, remove all 2-line business
                // TODO if in prefix mode already and keys are up or down or home or end
                // then move 2-line "first line" around but keep current line (prompt)
                // as second line in 2-line display
/*
                    if (prefix) {
                        Log.e("CRAIG", "prefix-prefix entered, toggle visibility of lineView and mTerminalView");
                        toggleViewVisibility(lineView);
                        toggleViewVisibility(mTerminalView);
                        updateLineView();
                    }
 */
                prefix = !prefix;
                // gesture prefix char
                // TODO might be nice to have some graphical indication
                // of being in prefix mode or control, shift, etc
                toput = "";
            } else if (value.equals("tab")) {
                toput = "" + (char)0x09;
            } else if (value.equals("backspace")) {
                toput = "" + (char)0x08;
            } else if (value.equals("space")) {
                toput = " ";
            } else if (value.equals("dot")) {
                if (dot) {
                    toput = ".";
                }
                dot = !dot;
            } else if (value.equals("shift")) {
                if (shift && caps) {
                    caps = false; shift = false;
                } else if (shift && !caps) {
                    caps = true; shift = false;
                } else if (!shift && caps) {
                    shift = false; caps = false;
                } else {
                    shift = true;
                }
            } else if (value.equals("control")) {
                control = !control;
            } else {
                toput = value;

                if (value.length() == 1) {
                    if (caps || shift) {
                        toput = "" + (char)(toput.charAt(0) - 32);
                    }
                    if (shift && !caps) {
                        shift = !shift;
                    }
                    if (prefix) {
                        // TODO 2023-may-17, remove 2-line stuff
                        // TODO for both 2-line and console view, need to manage keyboard input focus
                            /*
                            if (toput.equals("b")) { // big letter display
                                toggleViewVisibility(letterView);
                            } else if (toput.equals("i")) { // image display
                                //                        toggleViewVisibility(graphicsView); // TODO
                            } else if (toput.equals("g")) { // gesture layer
                                toggleViewVisibility(gestureView);
                            }
                             */
                        prefix = !prefix; // regardless, get out of prefix mode
                        toput = ""; // empty out the char, don't put anything
                    }
                    if (control) {
                        toput = "" + (char)(toput.charAt(0) - 96);
                        control = !control;
                    }
                } // value is length 1, simple char

                if (prefix) {
                    // font size controls to replace pinch zoom
                    if (toput.equals("\u001BOA")) { // up
                        mTermuxTerminalViewClient.changeFontSize(true);
                        toput = ""; // no output, just font size change
                    }
                    if (toput.equals("\u001BOB")) { // down
                        mTermuxTerminalViewClient.changeFontSize(false);
                        toput = ""; // no output, just font size change
                    }
                    prefix = !prefix;
                }
            }

//            Logger.logError(LOG_TAG, "toput='"+toput+"' toput.length="+toput.length());

            if (toput.length() > 0) {
                TerminalSession session = mTermuxActivity.getCurrentTermSession();
                if (session != null) {
                    if (session.isRunning()) {
                        // todo check that toput is "disaplayable" :)
                        session.write(toput);
                    }
                }
            }
            // TODO this is odd, but wanted to avoid printing out OD OC etc for arrow keys
/*
                if (toput.length() == 1) {
                    letterView.setTextColor(Color.GREEN);
                    letterView.setText(toput);
            } else {
                //		letterView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 30);
                letterView.setTextColor(Color.RED);
                letterView.setText("key not found: "+key);
             */
        }
        return toput;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        Log.e("GESTURE", "CRAIG: GestureView, onDraw");
        super.onDraw(canvas);
        //	    Log.e("GESTURE", "onDraw(), DrawingClassArrayList.size="+DrawingClassArrayList.size());

        if (DrawingClassArrayList.size() > 0) {
            canvas.drawPath(
                DrawingClassArrayList.get(DrawingClassArrayList.size() - 1).getPath(),
                DrawingClassArrayList.get(DrawingClassArrayList.size() - 1).getPaint());
        } else {
            canvas.drawColor(Color.TRANSPARENT);
        }
    }


}


