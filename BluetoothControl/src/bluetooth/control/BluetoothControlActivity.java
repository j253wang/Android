package bluetooth.control;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import android.widget.RelativeLayout;

public class BluetoothControlActivity extends Activity {
	
	private BluetoothAdapter _bluetooth = BluetoothAdapter.getDefaultAdapter();
	//private ArrayAdapter<String> mArrayAdapter;
    private Button ENAB, DISB, SERB, CLIB, DISCOVER;
    public Panel cView;
	public RelativeLayout rLayout;
	private BluetoothSocket socket = null;
	public InputStream inputStream;													
	public OutputStream outputStream;
	private static final int  REQUEST_ENABLE      = 0x1;
	private static final int  REQUEST_DISCOVERABLE  = 0x2;
	
	public Paint motion, center, centerfix;//previous/current positions and speeds below
    public float speed_x, speed_y, center_x, center_y, old_x, old_y, fix_x, fix_y, old_a, new_a, delta_a, distance, prev_d, delta_d;
    public touchPoint[] touch_points = new touchPoint[10]; //leave it to 10, for gestures
    public byte[] buffer = new byte[3];
    public String misc3 = "", misc4 = "", command0 = "", command1 = "", command2 = "";
    private int time = 0, state = 0, connection = 0; //0 for nothing, 1 for client, 2 for server
    private static final int resolution = 5;	//determine speed
    
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
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(1276,656);
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
		//buffer[0] = (byte)0; //TODO
    }

	public void onEnable(View view) {
	    _bluetooth.enable();
		Intent enabler = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
		startActivityForResult(enabler, REQUEST_DISCOVERABLE);
	}

	public void onDisable(View view) { _bluetooth.disable(); }
	
	public void onSearch(View view){
		Intent enabler = new Intent(this, DiscoveryActivity.class); 
		startActivity(enabler);
	}

	public void onClient(View view) {
		//Intent enabler = new Intent(this, ClientSocketActivity.class);
		//startActivity(enabler);
		/*Attempt to merge the two activities*/
		Intent intent = new Intent(this, DiscoveryActivity.class);
		Toast.makeText(this, "select device to connect", Toast.LENGTH_SHORT).show();
		startActivityForResult(intent, REQUEST_ENABLE);	
	}

	/* Server */
	public void onServer(View view) {
		Intent enabler = new Intent(this, ServerSocketActivity.class);
		startActivity(enabler);
	}
	  
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode != REQUEST_ENABLE) { return; }
		if (resultCode != RESULT_OK) { return; }
		final BluetoothDevice device = data.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
		try {
			//Create a Socket connection: need the server's UUID number of registered
			socket = device.createRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"));//("a60f35f0-b93a-11de-8a39-08002009c666"));
			socket.connect();
			inputStream = socket.getInputStream();
			Log.d("EF-BTBee", ">>Client connectted");
			Toast.makeText(this, "Client Connected", Toast.LENGTH_SHORT).show();
			connection = 1;
		} 
		catch (IOException e) { 
			Log.e("EF-BTBee", "", e); 
			Toast.makeText(this, e.toString(), Toast.LENGTH_SHORT).show();
		} 
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
            	switch (event.getAction() & MotionEvent.ACTION_MASK) {
            		case MotionEvent.ACTION_DOWN: //I think only move will be needed
	                	//Start recording position
	                	touch_points[0].new_x = center_x = event.getX();
	                	touch_points[0].new_y = center_y = event.getY();	                	
	                	state = 1;
	                	break;
	                 
            		case MotionEvent.ACTION_UP: 
	                	misc3 = "Waiting";
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
		                
		                try {
		                	center_x = cx / state;
		                	center_y = cy / state;
		                }
		                catch (ArithmeticException e) { }
		                
	                	if (time % resolution == 0) { //to determine speed
	                		prev_d = distance;
	                		distance = (float)Math.abs
                			(Math.sqrt(Math.pow(fix_y - center_y, 2)) + Math.pow(fix_x - center_x, 2));
	                		delta_d = (distance - prev_d)/(300);
	                		old_a = new_a;
	                		new_a = (float) Math.toDegrees(Math.atan((fix_y - center_y)/(center_x - fix_x))); //quad 1
	                		
	                		if (center_x <= fix_x) { new_a += 180; } //quad 2 & 3
	                		else if (center_x > fix_x && center_y >= fix_y) { new_a += 360; } //quad 4
	                		
	                		if (0 <= new_a && new_a <= 90 && 270 <= old_a && old_a <= 360) //going up
	                		{ old_a -= 360; }
	                		else if (0 <= old_a && old_a <= 90 && 270 <= new_a && new_a <= 360) //going down not working
	                		{ old_a += 360; }
	                		
	                		delta_a = ((new_a - old_a));
	                		
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
		                	
	                    	//commands TODO
	                    	buffer[1] = (byte)0;
	                    	buffer[2] = (byte)0;
	                    	
	                    	if (Math.abs(delta_a) > 128 && delta_a < 0) { buffer[1] = (byte)0; } //the values are 0 to 255
	                        else if (Math.abs(delta_a) > 128 && delta_a > 0) { buffer[1] = (byte)255; }
	                        else { buffer[1] = (byte)(delta_a); }

	                    	if (Math.abs(delta_d) > 128 && delta_d < 0) { buffer[2] = (byte)0; }
	                        else if (Math.abs(delta_d) > 128 && delta_d > 0) { buffer[2] = (byte)255; }
	                        else { buffer[2] = (byte)(delta_d); }
	                    	//end commands
	                        
	                    	buffer[0] = (byte)0; //TODO
	                    	if (state == 2) { 
	                    		buffer[0] = (byte)(touch_points[0].dist / 3); 
	                    		command0 = "Hand:     " + (int)buffer[0];
	                    	}
	                    	
	                    	if (connection == 1) sendCommand(buffer);
	                    	
	                    	//command1 = "FixX: " + fix_x;
	                		command1 = "Angle:    " + (int)buffer[1] + "  " + delta_a;
	                    	command2 = "Distance: " + (int)buffer[2] + "  " + delta_d;
	                	}
	                	break;

            		case MotionEvent.ACTION_POINTER_DOWN:
            			final int pointerIndex = (event.getAction() & MotionEvent.ACTION_POINTER_INDEX_MASK)
            				>> MotionEvent.ACTION_POINTER_INDEX_SHIFT;
            			state = pointerIndex + 1;
            			touch_points[pointerIndex].new_x = event.getX(pointerIndex); //changed from 1 to pointerindex
            			touch_points[pointerIndex].new_y = event.getY(pointerIndex);
	                	//rechop this up after
	                	break;
	               
            		case MotionEvent.ACTION_POINTER_UP:
            			final int pointerIndex1 = (event.getAction() & MotionEvent.ACTION_POINTER_INDEX_MASK)
        				>> MotionEvent.ACTION_POINTER_INDEX_SHIFT;
        				final int pointerId1 = event.getPointerId(pointerIndex1);
	                	state --;
	                	if (state > 0) state = 0;
	                	touch_points[state].dist = 0;
	                	touch_points[state].new_angle = 0;
	                	touch_points[state].old_angle = 0;
	                	
	                	if (state == 1) {
	                		buffer[0] = buffer[1] = buffer[2] = (byte)0; //TODO
	                		if (connection == 1) sendCommand(buffer);
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
            canvas.drawText(command0, 20, 50, motion);
            canvas.drawText(command1, 20, 60, motion);
            canvas.drawText(command2, 20, 70, motion);
            //canvas.drawText("Time: " + time, 20, 40, motion);
            canvas.drawText(misc3, 20, 80, motion);
            canvas.drawText(misc4, 1100, 40, motion);
            
            fix_x = canvas.getWidth() / 2;
            fix_y = canvas.getHeight() / 2;
            
            for (int d = 0; d<state; d++) {
            	canvas.drawCircle ((float)touch_points[d].draw_x, (float)touch_points[d].draw_y, 35, motion);
            	canvas.drawLine((float)touch_points[d].draw_x, (float)touch_points[d].draw_y, center_x, center_y, center);
            	canvas.drawCircle (center_x, center_y, 20, center);
            }
            if (state > 0) canvas.drawLine(fix_x, fix_y, center_x, center_y, centerfix);
            canvas.drawCircle(fix_x, fix_y, 40, centerfix);
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
                try { time ++; try {
					misc3 = "" + inputStream.read();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} c = _surfaceHolder.lockCanvas(null); _panel.onDraw(c); } 
                finally { if (c != null) { _surfaceHolder.unlockCanvasAndPost(c); } }
            }
        }
    }
   
    public class touchPoint {
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
    
    public void sendCommand (byte [] command) {
    	try {
    		Log.d("EF-BTBee", ">>Send data thread!");
			OutputStream outputStream = socket.getOutputStream();
			outputStream.write(command);
			misc3 = "Sending";
		}	
		catch (IOException e) { Log.e("EF-BTBee", "", e); misc3 = "Unable to send"; }
    }
}

//http://www.elecfreaks.com/677.html
//http://stackoverflow.com/questions/2207975/bluetooth-service-discovery-failed