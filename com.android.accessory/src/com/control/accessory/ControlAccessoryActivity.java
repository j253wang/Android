/*2012 Nick'a project
 * This is the server-side software that generates a command based on user input to send through the USB port
 * more complicated version
 */
package com.control.accessory;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.hardware.usb.UsbAccessory;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.Window;

public class ControlAccessoryActivity extends Activity {
	private UsbManager mUsbManager; 
	private PendingIntent mPermissionIntent;
	private boolean mPermissionRequestPending;
	private static final String TAG = "ArduinoAccessory";
	private static final String ACTION_USB_PERMISSION = "com.google.android.DemoKit.action.USB_PERMISSION";
	private static final int resolution = 30;	//determine speed
    private int time = 0, state = 0; 
    public Paint motion, center, centerfix;//previous/current positions and speeds below
    public float speed_x, speed_y, center_x, center_y, old_x, old_y, fix_x, fix_y, old_a, new_a, delta_a, distance, prev_d, delta_d;
    public touchPoint[] touch_points = new touchPoint[10]; //leave it to 10, for gestures
    public byte[] buffer = new byte[3];
    public String command1 = "None", command2 = "None", misc = "What?", misc2 = "What?", misc3 = "", misc4 = "";
    private ControlThread _thread;
    
    UsbAccessory mAccessory;
	ParcelFileDescriptor mFileDescriptor;
	FileInputStream mInputStream;
	FileOutputStream mOutputStream;
	
    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			if (ACTION_USB_PERMISSION.equals(action)) {
				synchronized (this) {
					UsbAccessory accessory = (UsbAccessory) intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY);
					if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) { openAccessory(accessory);} 
					else {Log.d(TAG, "permission denied for accessory " + accessory);}
					mPermissionRequestPending = false;
				}
			} 
			else if (UsbManager.ACTION_USB_ACCESSORY_DETACHED.equals(action)) {
				UsbAccessory accessory = (UsbAccessory) intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY);
				if (accessory != null && accessory.equals(mAccessory)) { 
					try { if (mFileDescriptor != null) { mFileDescriptor.close(); } } 
					catch (IOException e) {} 
					finally {
						mFileDescriptor = null;
						mAccessory = null;
					}
				}
			}
		}
	};
	
	private void openAccessory(UsbAccessory accessory) {
		mFileDescriptor = mUsbManager.openAccessory(accessory);
		if (mFileDescriptor != null) {
			mAccessory = accessory;
			FileDescriptor fd = mFileDescriptor.getFileDescriptor();
			mInputStream = new FileInputStream(fd);
			mOutputStream = new FileOutputStream(fd);
			Log.d(TAG, "accessory opened");
			misc = "All good";
		} 
		else { Log.d(TAG, "accessory open fail"); misc = "Accessory Open fail";}
	}
    
    @Override	/** Called when the activity is first created. */
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        
        mUsbManager = (UsbManager)getSystemService(Context.USB_SERVICE); 
        mPermissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0);
        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        filter.addAction(UsbManager.ACTION_USB_ACCESSORY_DETACHED);
        registerReceiver(mUsbReceiver, filter);
        	
        if (getLastNonConfigurationInstance() != null) {
			mAccessory = (UsbAccessory) getLastNonConfigurationInstance();
			openAccessory(mAccessory);
		}
        
		for (int e = 0; e<10; e++) { touch_points[e] = new touchPoint(); }
		buffer[0] = buffer[1] = 0;
        setContentView(new Panel(this));
        motion = new Paint();
		motion.setColor(Color.WHITE);
		motion.setAntiAlias(true);
		center = new Paint();
		center.setColor(Color.BLUE);
		centerfix = new Paint();
		centerfix.setColor(Color.GREEN);
    }
    
    @Override
	public void onResume() {
		super.onResume();
		if (mInputStream != null && mOutputStream != null) { return; }
		try {
			UsbAccessory[] accessories = mUsbManager.getAccessoryList();
			UsbAccessory accessory = (accessories == null ? null : accessories[0]);
			if (accessory != null) {
				if (mUsbManager.hasPermission(accessory)) { openAccessory(accessory); } 
				else {
					synchronized (mUsbReceiver) {
						if (!mPermissionRequestPending) {
							mUsbManager.requestPermission(accessory,mPermissionIntent);
							mPermissionRequestPending = true;
						}
					}
				}
			} 
			else { Log.d(TAG, "mAccessory is null"); misc2 = "mAccessory is null"; }
		}
		catch (NullPointerException e) { misc2 = e.toString(); }
	}
    
    @Override
	public void onPause() { super.onPause(); }
 
	@Override
	public void onDestroy() {
		unregisterReceiver(mUsbReceiver);
		super.onDestroy();
	}
	
    class Panel extends SurfaceView implements SurfaceHolder.Callback {
        
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
		                
		                center_x = cx / state;
		                center_y = cy / state;
		                
		                
	                	if (time % resolution == 0) { //to determine speed
	                		prev_d = distance;
	                		distance = (float)Math.abs
                			(Math.sqrt(Math.pow(fix_y - center_y, 2))+Math.pow(fix_x - center_x, 2));
	                		delta_d = distance - prev_d;
	                		old_a = new_a;
	                		new_a = (float) Math.toDegrees(Math.atan((center_y - fix_y)/(center_x - fix_x)));
	                		if (center_y < fix_y) new_a += 180;
	                		
	                		if (0 <= new_a && new_a <= 90 && 270 <= old_a && old_a <= 360) //going up
	                		{ old_a -= 360; }
	                		else if (0 <= old_a && old_a <= 90 && 270 <= new_a && new_a <= 360)
	                		{ new_a -= 360; }
	                		
	                		delta_a = new_a - old_a;
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
		                	
	                		command1 = "Angle:    " + delta_a;
	                    	command2 = "Distance: " + delta_d;
	                		
	                    	//commands
	                    	buffer[1] = (byte)0;
	                    	buffer[2] = (byte)0;
	                    	
	                    	if (Math.abs(delta_a) > 128 && delta_a < 0) { buffer[1] = (byte)0; }
	                        else if (Math.abs(delta_a) > 128 && delta_a > 0) { buffer[1] = (byte)255; }
	                        else { buffer[1] = (byte)(128 + (delta_a)); }

	                    	if (Math.abs(delta_d) > 128 && delta_d < 0) { buffer[2] = (byte)0; }
	                        else if (Math.abs(delta_d) > 128 && delta_d > 0) { buffer[2] = (byte)255; }
	                        else { buffer[2] = (byte)(128 + (delta_d)); }
	                    	//end commands
	                        
	                    	buffer[0] = (byte)0;
	                    	if (state == 2) { buffer[0] = (byte)(touch_points[0].dist / 3); }
	                    	
	                    	sendCommand(buffer);
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
	                		buffer[0] = buffer[1] = buffer[2] = (byte)0;
	                		sendCommand(buffer);
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
            /*
            canvas.drawText (command1, 20, 20, motion);
            canvas.drawText ("" + speed_x, 20, 30, motion);
            canvas.drawText (command2, 20, 40, motion);
            canvas.drawText ("" + speed_y, 20, 50, motion);
            canvas.drawText(misc2, 20, 70, motion);
            canvas.drawText(misc3, 20, 80, motion);
            canvas.drawText(misc4, 1100, 40, motion);
            */
            fix_x = canvas.getWidth();
            fix_y = canvas.getHeight();
            
            /*
            for (int d = 0; d<state; d++) {
            	canvas.drawCircle ((float)touch_points[d].draw_x, (float)touch_points[d].draw_y, 35, motion);
            	canvas.drawLine((float)touch_points[d].draw_x, (float)touch_points[d].draw_y, center_x, center_y, center);
            	canvas.drawCircle (center_x, center_y, 20, center);
            }
            
            if (state > 0) canvas.drawLine(fix_x/2, fix_y/2, center_x, center_y, centerfix);
            canvas.drawCircle(fix_x/2, fix_y/2, 40, centerfix);
            */
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
                	int lolz = mInputStream.read();
                	misc = ("" + lolz);
                	time++; 
                	c = _surfaceHolder.lockCanvas(null); _panel.onDraw(c); 
                } 
                catch (IOException e) {e.printStackTrace();} 
                finally { if (c != null) { _surfaceHolder.unlockCanvasAndPost(c); } }
            }
        }
    }
    
    void sendCommand (byte[] command){
    	if (mOutputStream != null) {	
			try { mOutputStream.write(command); misc2 = "Can write";} 
			catch (IOException e) { Log.e(TAG, "write failed", e); misc2 = "Write Failed " + e.toString();}
		}
    	else misc3 = "Nowhere to write to";	
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

//needed: speed of x and y

//LED blinker: http://allaboutee.com/2011/12/31/arduino-adk-board-blink-an-led-with-your-phone-code-and-explanation/
//and: http://stackoverflow.com/questions/9053809/arduino-adk-android-led-blink-example-compiling-errors
//bluetooth: http://www.elecfreaks.com/677.html
//http://www.zdnet.com/blog/burnette/how-to-use-multi-touch-in-android-2/1747?tag=content;siu-container
//motorshield http://www.ladyada.net/make/mshield/use.html
//servo example: http://arduino.cc/en/Tutorial/Sweep
//this might solve all my problems http://android.serverbox.ch/?p=549

//http://mitchtech.net/android-arduino-usb-host-simple-digital-input/ try server method