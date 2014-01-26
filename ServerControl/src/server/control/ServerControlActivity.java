package server.control;

import java.io.IOException;

import org.microbridge.server.Server;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

public class ServerControlActivity extends Activity {
    /** Called when the activity is first created. */
	private static final int resolution = 20;	//determine speed
    private int state = 0; 
    public Paint motion;
    public float old_x, old_y, now_x, now_y, draw_x, draw_y, speed_x, speed_y;//previous and current positions and speeds
    public String command1 = "None", command2 = "None", misc = "What?", misc2 = "What?", misc3 = "", misc4 = "";
    private ControlThread _thread;
    public static final String TAG = ServerControlActivity.class.getSimpleName();
	SharedPreferences mPrefs;
	Server mServer = null;
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(new Panel(this));
        motion = new Paint();
		motion.setColor(Color.WHITE);
		motion.setAntiAlias(true);
		
        try {
			mServer = new Server(4567);
			mServer.start();
		} 
        catch (IOException e) {
			Log.e("microbridge", "Unable to start TCP server", e);
			misc = e.toString();
			System.exit(-1);
		}
    }

    @Override
	protected void onStart() {super.onStart();}
	@Override
	protected void onStop() {super.onStop();}
	@Override
	protected void onPause() {super.onStart();}
	@Override
	protected void onResume() {super.onStart();}
	
    class Panel extends SurfaceView implements SurfaceHolder.Callback {
        
        public Panel(Context context) {
            super(context);
            getHolder().addCallback(this);
            _thread = new ControlThread(getHolder(), this);
            setFocusable(true);
        }
      
        @Override
        public boolean onTouchEvent(MotionEvent event) {
            //synchronized (_thread.getSurfaceHolder()) {
                
                if (event.getAction() == MotionEvent.ACTION_DOWN) {//I think only move will be needed
                	//Start recording position
                	now_x = event.getX();
                	now_y = event.getY();
                	state = 1;
                } 
                else if (event.getAction() == MotionEvent.ACTION_MOVE) {
                	//record position, send commands
                	if (System.currentTimeMillis() % resolution == 0) { //to determine speed
                		int command = 0;
                		old_x = now_x;
                		old_y = now_y;
                		now_x = event.getX();
                		now_y = event.getY();
                		speed_x = Math.abs((now_x - old_x)/resolution);
                		speed_y = Math.abs((now_y - old_y)/resolution);
                		
                		if (now_x < old_x) { 
                    		command += 20; 
                    		command1 = "LEFT"; 
                    	}//go left
                    	else if (now_x > old_x) { 
                    		command += 10; 
                    		command1 = "RIGHT"; 
                    	}// go right
                    	if (now_y > old_y) { 
                    		command += 1; 
                    		command2 = "DOWN";
                    	}// go down
                    	else if (now_y < old_y) {
                    		command += 2; 
                    		command2 = "UP"; 
                    	}// go up 
                    	
                		sendCommand(command);
                	}
                	draw_x = event.getX();
                	draw_y = event.getY();
                	state = 1;
                } 
                else if (event.getAction() == MotionEvent.ACTION_UP) { 
                	now_x = now_y  = old_x = old_y = 0; 
                	misc2 = "Waiting";
                	state = 0;
                }
                return true;
           // }
        }
     
        @Override
        public void onDraw(Canvas canvas) {
            canvas.drawColor(Color.RED);  
            canvas.drawText (command1, 20, 20, motion);
            canvas.drawText ("" + speed_x, 20, 30, motion);
            canvas.drawText (command2, 20, 40, motion);
            canvas.drawText ("" + speed_y, 20, 50, motion);
            canvas.drawText(misc, 20, 60, motion);
            canvas.drawText(misc2, 20, 70, motion);
            canvas.drawText(misc3, 20, 80, motion);
            canvas.drawText(misc4, 700, 500, motion);
            // draw current graphic at last...
            if (state == 1) { canvas.drawCircle (draw_x, draw_y, 35, motion); }
        }
     
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {}
        public void surfaceCreated(SurfaceHolder holder) { _thread.setRunning(true); _thread.start(); }
     
        public void surfaceDestroyed(SurfaceHolder holder) {
            boolean retry = true;
            _thread.setRunning(false);
            while (retry) {
                try {  _thread.join(); retry = false; } 
                catch (InterruptedException e) { } // we will try it again and again...           
            }
        }
    }

    class ControlThread extends Thread {
        private SurfaceHolder _surfaceHolder;
        private Panel _panel;
        private boolean _run = false;
     
        public ControlThread(SurfaceHolder surfaceHolder, Panel panel) { _surfaceHolder = surfaceHolder; _panel = panel; }
        public SurfaceHolder getSurfaceHolder() { return _surfaceHolder; }
        public void setRunning(boolean run) { _run = run; }
     
        @Override
        public void run() { //check the run in the other program
        	Canvas c;
            while (_run) {
                c = null;
                try {
                    c = _surfaceHolder.lockCanvas(null);
                    _panel.onDraw(c);
                } 
                finally { if (c != null) { _surfaceHolder.unlockCanvasAndPost(c); } }
                
                //try {sleep(10);} catch (InterruptedException e) {e.printStackTrace();}
            }
        }
    }
    
    void sendCommand (int command){
    	try { mServer.send(new byte[] { (byte) command }); misc2 = "Sending";} 
    	catch (IOException e) { Log.e(TAG, "problem sending TCP message", e); misc3 = "problem sending TCP message"; }
    }
}