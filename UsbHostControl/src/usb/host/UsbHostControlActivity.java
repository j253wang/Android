package usb.host;


import java.util.HashMap;
import java.util.Iterator;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

public class UsbHostControlActivity extends Activity {
	
	private static final String TAG = "Host";
	private static final String ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION";
	private static final int resolution = 100;	//determine speed
    private int time = 0, state = 0; 
    public Paint motion, center, centerfix;//previous/current positions and speeds below
    public float speed_x, speed_y, center_x, center_y, old_x, old_y, fix_x, fix_y, old_a = 0, new_a = 0, distance = 0;
    public touchPoint[] touch_points = new touchPoint[10]; //leave it to 10, for gestures
    private UsbManager mUsbManager;
    private UsbDevice mDevice;
    private UsbDeviceConnection mConnection;
    private UsbEndpoint mEndpointIntr;
    private UsbInterface intf;
    private PendingIntent mPermissionIntent;
    public String command1 = "None", command2 = "None", misc = "What?", misc2 = "What?", misc3 = "", misc4 = "";
    private ControlThread _thread;
    
    private Byte[] bytes;
    private static int TIMEOUT = 0;
    private boolean forceClaim = true;
    
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        
        mUsbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        HashMap<String, UsbDevice> deviceList = mUsbManager.getDeviceList();
        Iterator<UsbDevice> deviceIterator = deviceList.values().iterator();
        while(deviceIterator.hasNext()){
            mDevice = deviceIterator.next();
        }

        mPermissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0);
        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        registerReceiver(mUsbReceiver, filter);
        mUsbManager.requestPermission(mDevice, mPermissionIntent);
        
        intf = mDevice.getInterface(0);
        UsbEndpoint endpoint = intf.getEndpoint(0);
        UsbDeviceConnection connection = mUsbManager.openDevice(mDevice); 
        connection.claimInterface(intf, forceClaim);
        
        for (int e = 0; e<10; e++) { touch_points[e] = new touchPoint(); }
        setContentView(new Panel(this));
        motion = new Paint();
		motion.setColor(Color.WHITE);
		motion.setAntiAlias(true);
		center = new Paint();
		center.setColor(Color.BLUE);
		centerfix = new Paint();
		centerfix.setColor(Color.GREEN);
    }
    
    public void onResume() {
        super.onResume();
        Intent intent = getIntent();
        Log.d(TAG, "intent: " + intent);
        String action = intent.getAction();

        UsbDevice device = (UsbDevice)intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
        if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
            setDevice(device);
        } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
            if (mDevice != null && mDevice.equals(device)) {
                setDevice(null);
            }
        }
    }
    
    private void setDevice(UsbDevice device) {
        Log.d(TAG, "setDevice " + device);
        if (device.getInterfaceCount() != 1) {
            Log.e(TAG, "could not find interface");
            return;
        }
        UsbInterface intf = device.getInterface(0);
        // device should have one endpoint
        if (intf.getEndpointCount() != 1) {
            Log.e(TAG, "could not find endpoint");
            return;
        }
        // endpoint should be of type interrupt
        UsbEndpoint ep = intf.getEndpoint(0);
        if (ep.getType() != UsbConstants.USB_ENDPOINT_XFER_INT) {
            Log.e(TAG, "endpoint is not interrupt type");
            return;
        }
        mDevice = device;
        mEndpointIntr = ep;
        if (device != null) {
            UsbDeviceConnection connection = mUsbManager.openDevice(device);
            if (connection != null && connection.claimInterface(intf, true)) {
                Log.d(TAG, "open SUCCESS");
                mConnection = connection;
            } else {
                Log.d(TAG, "open FAIL");
                mConnection = null;
            }
         }
    }
    
    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
    	public void onReceive(Context context, Intent intent) {
    		String action = intent.getAction();
    		if (ACTION_USB_PERMISSION.equals(action)) {
    			synchronized (this) {
    				UsbDevice device = (UsbDevice)intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
    				if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
    					if(device != null){
    						//call method to set up device communication
    					}
    				} 
    				else { Log.d(TAG, "permission denied for device " + device); }
    			}
    		}
    		else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
    			UsbDevice device = (UsbDevice)intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
    			if (device != null) {
                // call your method that cleans up and closes communication with the device
    			}
    		}
    	}
    };
    	
    private void sendCommand(int control) {
        synchronized (this) {
            if (mConnection != null) {
                byte[] message = new byte[1];
                message[0] = (byte)control;
                // Send command via a control request on endpoint zero
                mConnection.claimInterface(intf, forceClaim);
                mConnection.bulkTransfer(mEndpointIntr, message, message.length, 0); //do in another thread
            }
        }
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
                int command = 0;
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
                    		}
                    		
    	                	for (int d = 0; d<state; d++) { 
    	                		touch_points[d].findDist(center_x, center_y); 
    	                		touch_points[d].findAngles(center_x, center_y);
    	                	}
    	                	
                    		if (touch_points[0].new_x < touch_points[0].old_x) { 
                        		command += 20; 
                        		command1 = "LEFT"; 
                        	}//go left
                        	else if (touch_points[0].new_x > touch_points[0].old_x) { 
                        		command += 10; 
                        		command1 = "RIGHT"; 
                        	}// go right
                        	if (touch_points[0].new_y > touch_points[0].old_y) { 
                        		command += 1; 
                        		command2 = "DOWN";
                        	}// go down
                        	else if (touch_points[0].new_y < touch_points[0].old_y) {
                        		command += 2; 
                        		command2 = "UP"; 
                        	}// go up 
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
   	                	break;
               	}
                   return true;
               }
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
            canvas.drawText(misc4, 1200, 40, motion);
              
            fix_x = canvas.getWidth();
            fix_y = canvas.getHeight();
            
            for (int d = 0; d<state; d++) {
             	canvas.drawCircle (touch_points[d].draw_x, touch_points[d].draw_y, 35, motion);
               	canvas.drawLine(touch_points[d].draw_x, touch_points[d].draw_y, center_x, center_y, center);
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
      	float old_x, old_y, new_x, new_y, draw_x, draw_y;
       	double dist = 0, old_dist = 0, old_angle = 0, new_angle = 0;	//distance from center
       	public touchPoint() { old_x = old_y = new_x = new_y = draw_x = draw_y = 0; }
       	
       	void findDist(double XC, double YC) { 
       		this.old_dist = this.dist;
       		this.dist = Math.abs(Math.sqrt(Math.pow(XC - new_x, 2.0) - Math.pow(YC - new_y, 2.0))); 
       	}
       	
       	void findAngles(double XC, double YC) {
       		this.old_angle = new_angle;
       		this.new_angle = Math.toDegrees(Math.atan((YC - new_y)/(XC - new_x))); //we need to know change in angle only
       	}
    }
}

//https://github.com/peterdn/CameraBuddy/tree/master/src/com/peterdn/camerabuddy
//http://android.serverbox.ch/?p=549