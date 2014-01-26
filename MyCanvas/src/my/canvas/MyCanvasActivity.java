package my.canvas;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.Window;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.RadioGroup;

public class MyCanvasActivity extends Activity {
	public Paint drawPaint, bg;
	boolean started = false;
	public EditText Red, Green, Blue, Radius;
	public RadioButton Smudge, Draw;
	public Panel cView;
	public RelativeLayout rLayout;
	int time = 0;
	public SeekBar RSB, BSB, GSB, RadSB, ASB;
	public RadioGroup RadG;
	public RadioButton draw, smudge;
	public int radius, rval, bval, gval, aval;
	public int buttonID;
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
    	super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.main);
        
        RadG = (RadioGroup)this.findViewById(R.id.radioGroup1);
        Smudge = (RadioButton)this.findViewById(R.id.radioButtonSmudge);
        Draw = (RadioButton)this.findViewById(R.id.radioButtonDraw);
        
        RSB = (SeekBar)this.findViewById(R.id.seekBar1);
        BSB = (SeekBar)this.findViewById(R.id.seekBar2);
        GSB = (SeekBar)this.findViewById(R.id.seekBar3);
        RadSB = (SeekBar)this.findViewById(R.id.seekBar4);
        ASB = (SeekBar)this.findViewById(R.id.seekBar5);
        
        RSB.setMax(255);
        BSB.setMax(255);
        GSB.setMax(255);
        ASB.setMax(255);
        RadSB.setMax(100);
        
        bg = new Paint();
        bg.setColor(Color.WHITE);
        drawPaint = new Paint();
		drawPaint.setColor(Color.BLUE);
		
		cView = new Panel(this);
        rLayout = (RelativeLayout)this.findViewById(R.id.canvas);
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(3000,800); //I just toss numbers around
        rLayout.addView(cView, params); 
    }
    
    class Panel extends SurfaceView implements SurfaceHolder.Callback {
        private GameThread _thread;
        private float xcoord, ycoord, radius;
        
        public Panel(Context context) {
            super(context);
            getHolder().addCallback(this);
            _thread = new GameThread(getHolder(), this);
            setFocusable(true);
            radius = 20;
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
        	rval = RSB.getProgress();
        	bval = BSB.getProgress();
        	gval = GSB.getProgress();
        	aval = ASB.getProgress();
        	radius = RadSB.getProgress();
        	
        	buttonID = RadG.getCheckedRadioButtonId();
        	
        	if (buttonID == R.id.radioButtonDraw) { drawPaint.setARGB (aval, rval, gval, bval); }
        	else if (buttonID == R.id.radioButtonSmudge)
        	{
        		drawPaint.setARGB (aval, rval, gval, bval);
        	}

            synchronized (_thread.getSurfaceHolder()) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {  
                	xcoord = event.getX();
                	ycoord = event.getY();
                }
                else if (event.getAction() == MotionEvent.ACTION_MOVE) {
                	xcoord = event.getX();
                	ycoord = event.getY();
                }
                return true;
            }
        }

        @Override
        public void onDraw(Canvas canvas) {
        	canvas.drawCircle(xcoord, ycoord, radius, drawPaint);
        }
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {}
     
        public void surfaceCreated(SurfaceHolder holder) {
            _thread.setRunning(true);
            _thread.start();
        }
     
        public void surfaceDestroyed(SurfaceHolder holder) {
            // simply copied from sample application LunarLander:
            boolean retry = true;
            _thread.setRunning(false);
            while (retry) {
                try { _thread.join(); retry = false; } 
                catch (InterruptedException e) {}// we will try it again and again...}
            }
        }
    }
 
    class GameThread extends Thread {
        private SurfaceHolder _surfaceHolder;
        private Panel _panel;
        private boolean _run = false;
        
        public SurfaceHolder getSurfaceHolder() { return _surfaceHolder; }
        public void setRunning(boolean run) { _run = run; }
        	
        public GameThread(SurfaceHolder surfaceHolder, Panel panel) {
            _surfaceHolder = surfaceHolder;
            _panel = panel;
        }
 
        @Override
        public void run() {
            Canvas c;
            while (_run) {
                c = null;
                try {
                    c = _surfaceHolder.lockCanvas(null);
                    synchronized (_surfaceHolder) { //TODO find the desync
                        _panel.onDraw(c);
                        time++;
                    }
                } 
                finally { if (c != null) { _surfaceHolder.unlockCanvasAndPost(c); } }
            }
        }
    }
}
//http://mitchtech.net/android-arduino-usb-host-simple-analog-output/