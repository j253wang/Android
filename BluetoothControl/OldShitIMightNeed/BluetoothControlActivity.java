package bluetooth.control;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;

import android.widget.RelativeLayout;

public class BluetoothControlActivity extends Activity {
    /** Called when the activity is first created. */
	
	private BluetoothAdapter _bluetooth = BluetoothAdapter.getDefaultAdapter();
	private ArrayAdapter<String> mArrayAdapter;
    private Button ENAB, DISB, SERB, CLIB, DISCOVER;
    public Panel cView;
	public RelativeLayout rLayout;
	private BluetoothSocket socket = null;
	
	private static final int  REQUEST_ENABLE      = 0x1;
	private static final int  REQUEST_DISCOVERABLE  = 0x2;
	
	public Paint motion, center, centerfix;//previous/current positions and speeds below
    public float speed_x, speed_y, center_x, center_y, old_x, old_y, fix_x, fix_y, old_a = 0, new_a = 0, distance = 0;
    public touchPoint[] touch_points = new touchPoint[10]; //leave it to 10, for gestures
    public byte[] buffer = new byte[2];
    public String misc = "What?", misc2 = "What?", misc3 = "", misc4 = "";
    private int time = 0, state = 0; 
    private static final int resolution = 30;	//determine speed
    
	protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(0x2);
        setContentView(R.layout.main);
        ENAB = (Button)this.findViewById(R.id.BTENABLE);
        DISB = (Button)this.findViewById(R.id.BTDISABLE);
        SERB = (Button)this.findViewById(R.id.RUNSERVER);
        CLIB = (Button)this.findViewById(R.id.RUNCLIENT);
        DISCOVER = (Button)this.findViewById(R.id.SEARCH);
        
        cView = new Panel(this);
        rLayout = (RelativeLayout)this.findViewById(R.id.canvas);
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(1276,656); //I just toss numbers around
        rLayout.addView(cView, params); 

        for (int e = 0; e<10; e++) { touch_points[e] = new touchPoint(); }
		buffer[0] = buffer[1] = 0;
        motion = new Paint();
		motion.setColor(Color.WHITE);
		motion.setAntiAlias(true);
		center = new Paint();
		center.setColor(Color.BLUE);
		centerfix = new Paint();
		centerfix.setColor(Color.GREEN);
    }

	 /* Enable BT */
	  public void onEnable(View view)
	  {
	      //Intent enabler = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
	      //startActivityForResult(enabler, REQUEST_ENABLE);
	      //enable
		  _bluetooth.enable();
		  Intent enabler = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
		  startActivityForResult(enabler, REQUEST_DISCOVERABLE);
	  }

	  /* Close BT */
	  public void onDisable(View view) { _bluetooth.disable(); }

	  /* Start search */
	  public void onSearch(View view)
	  {
		  Intent enabler = new Intent(this, DiscoveryActivity.class);
		  startActivity(enabler);
	  }

	  /* Client */
	  public void onClient(View view)
	  {
		  Intent enabler = new Intent(this, ClientSocketActivity.class);
		  startActivity(enabler);
	  }

	  /* Server */
	  public void onServer(View view)
	  {
		  Intent enabler = new Intent(this, ServerSocketActivity.class);
		  startActivity(enabler);
	  }
	  
	  class Panel extends SurfaceView implements SurfaceHolder.Callback {
	        private ControlThread _thread;
	        
	        public Panel(Context context) {
	            super(context);
	            getHolder().addCallback(this);
	            _thread = new ControlThread(getHolder(), this);
	            setFocusable(true);
	        }
	      
	        @Override
	        public boolean onTouchEvent(MotionEvent event) {
	            synchronized (_thread.getSurfaceHolder()) {
	            	switch (event.getAction() & MotionEvent.ACTION_MASK)
	            	{
	            	case MotionEvent.ACTION_DOWN: //I think only move will be needed
		               	//Start recording position
		               	touch_points[0].new_x = center_x = event.getX();
		               	touch_points[0].new_y = center_y = event.getY();	                	
		               	state = 1;
		               	break;
		                
	            	case MotionEvent.ACTION_UP: 
		               	misc2 = "Waiting";
		               	state = 0;
		               	distance = 0;
		               	break;
		               	
	            	case MotionEvent.ACTION_MOVE: 
		               	//record position, send commands
	            		int cx = 0, cy = 0;
			            for (int d = 0; d<state; d++) {
			              	touch_points[d].draw_x = event.getX(d);
				            touch_points[d].draw_y = event.getY(d);
				            cx += touch_points[d].draw_x;
				            cy += touch_points[d].draw_y;
			            }
			                
			            center_x = cx / state; //lol if state is 0
			            center_y = cy / state;
			            distance = (float)Math.abs
			            (Math.sqrt(Math.pow(fix_y - center_y, 2))+Math.pow(fix_x - center_x, 2));
			                
		                if (time % resolution == 0) { //to determine speed
		                	
		                	old_a = new_a;
		                	new_a = (float) Math.toDegrees(Math.atan((center_y - fix_y)/(center_x - fix_x)));
		                	speed_x = Math.abs((center_x - old_x)/resolution);
		                	speed_y = Math.abs((center_y - old_y)/resolution);
		                	old_x = center_x;
		                	old_y = center_y;
		                	
		                	for (int d = 0; d<state; d++) {
		                		touch_points[d].old_x = touch_points[d].new_x;
		                		touch_points[d].old_y = touch_points[d].new_y;
		                		touch_points[d].new_x = event.getX(d);
		                		touch_points[d].new_y = event.getY(d); 
			               		touch_points[d].findDist(center_x, center_y); 
			               		touch_points[d].findAngles(center_x, center_y);
		                	} 
		                   	
		                   	buffer[0] = (byte)0;
		                   	if (state == 2)
		                   	{	
		                   		buffer[1] = (byte)(touch_points[0].dist / 3);
		                   		//misc4 = "lol "+(int)buffer[1];
		                   	}
		                   	else buffer[1] = (byte)0;
		                   	
		                   	//sendCommand(buffer);
		                }
	                	break;
	            		case MotionEvent.ACTION_POINTER_DOWN:
            			final int pointerIndex = (event.getAction() & MotionEvent.ACTION_POINTER_INDEX_MASK)
           				>> MotionEvent.ACTION_POINTER_INDEX_SHIFT;
            			final int pointerId = event.getPointerId(pointerIndex);
            			state = pointerIndex + 1;
            			touch_points[pointerIndex].new_x = event.getX(pointerIndex); //changed from 1 to pointerindex
            			touch_points[pointerIndex].new_y = event.getY(pointerIndex);
	                	//rechop this up after
	                	misc4 = pointerIndex + " " + pointerId;
	                	break;
	               
            		case MotionEvent.ACTION_POINTER_UP:
            			final int pointerIndex1 = (event.getAction() & MotionEvent.ACTION_POINTER_INDEX_MASK)
        				>> MotionEvent.ACTION_POINTER_INDEX_SHIFT;
        				final int pointerId1 = event.getPointerId(pointerIndex1);
	                	misc4 = pointerIndex1 + " " + pointerId1;
	                	state --;
	                	touch_points[state].dist = 0;
	                	touch_points[state].new_angle = 0;
	                	touch_points[state].old_angle = 0;
	                	
	                	if (state == 1)
	                	{
	                		buffer[0] = buffer[1] = (byte)0;
	                		//sendCommand(buffer);
	                	}
	                	break;
            	}
            	misc4 = "Pressure: " + event.getPressure();
                return true;
            }            
        }
     
        @Override
        public void onDraw(Canvas canvas) {
            canvas.drawColor(Color.RED);  
            canvas.drawText(misc, 20, 60, motion);
            canvas.drawText(misc2, 20, 70, motion);
            canvas.drawText(misc3, 20, 80, motion);
            canvas.drawText(misc4, 1100, 40, motion);
            
            fix_x = canvas.getWidth();
            fix_y = canvas.getHeight();
            
            for (int d = 0; d<state; d++) {
            	canvas.drawCircle ((float)touch_points[d].draw_x, (float)touch_points[d].draw_y, 35, motion);
            	canvas.drawLine((float)touch_points[d].draw_x, (float)touch_points[d].draw_y, center_x, center_y, center);
            	canvas.drawCircle (center_x, center_y, 20, center);
            }
            if (state > 0) canvas.drawLine(fix_x/2, fix_y/2, center_x, center_y, centerfix);
            canvas.drawCircle(fix_x/2, fix_y/2, 40, centerfix);
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
                try { time ++; c = _surfaceHolder.lockCanvas(null); _panel.onDraw(c); } 
                finally { if (c != null) { _surfaceHolder.unlockCanvasAndPost(c); } }
            }
        }
    }
   
    public class touchPoint
    {
    	double dist, old_dist, old_angle, new_angle, old_x, old_y, new_x, new_y, draw_x, draw_y;//distance from center
    	public touchPoint() { old_x = old_y = new_x = new_y = draw_x = draw_y = 0; }
    	
    	void findDist(double XC, double YC) { 
    		old_dist = dist;
    		dist = Math.abs(Math.sqrt(Math.pow(XC - new_x, 2.0) + Math.pow(YC - new_y, 2.0))); 
    	}
    	
    	void findAngles(double XC, double YC) {
    		this.old_angle = new_angle;
    		this.new_angle = Math.toDegrees(Math.atan((YC - new_y)/(XC - new_x))); //we need to know change in angle only
    	}
    }
}

//http://www.elecfreaks.com/677.html