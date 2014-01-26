/*2012 Nick'a project
 * This is the server-side software that generates a command based on user input to send through the USB port
 * simpler version based off of the missilelauncher
 */

package controls.tester;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Iterator;

import android.app.Activity;
import android.app.PendingIntent;
import android.hardware.usb.UsbAccessory;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.hardware.usb.UsbRequest;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.view.MotionEvent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.Window;

public class ControlActivity extends Activity {
    /** Called when the activity is first created. */
    
	private UsbManager mUsbManager;
    private UsbDevice mDevice;
    private UsbDeviceConnection mConnection;
    private UsbEndpoint mEndpointIntr;
    private PendingIntent mPermissionIntent;
    private static final String TAG = "ControlActivity";
    private static final String ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION";
    
    UsbAccessory mAccessory;
	ParcelFileDescriptor mFileDescriptor;
	FileInputStream mInputStream;
	FileOutputStream mOutputStream;
	
    private static final int resolution = 5;	//determine speed
    private int time = 0; 
    
    // USB control commands
    private static final int COMMAND_UP = 1;
    private static final int COMMAND_DOWN = 2;
    private static final int COMMAND_RIGHT = 4;
    private static final int COMMAND_LEFT = 8;
    
    public Paint motion;
    public float old_x, old_y, now_x, now_y, draw_x, draw_y;//previous and current positions
    public float speed_x, speed_y;
    public int state = 0;
    public String command1 = "None";
    public String command2 = "None";
    public String misc = "";
    
    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() { //this prolly isnt used
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
	                else {
	                    Log.d(TAG, "permission denied for device " + device);
	                }
	            }
	        }
	        
	        if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
	            UsbDevice device = (UsbDevice)intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
	            if (device != null) {
	                // TODO call your method that cleans up and closes communication with the device
	            }
	        }
	    }
	};
	
    @Override
    public void onCreate(Bundle savedInstanceState) { //half this stuff isnt used
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(new Panel(this));

        mUsbManager = (UsbManager)getSystemService(Context.USB_SERVICE); 

        motion = new Paint();
		motion.setColor(Color.WHITE);
		motion.setAntiAlias(true);
    }
    	
    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
        misc = "Resuming";
        Intent intent = getIntent();
        Log.d(TAG, "intent: " + intent);
        String action = intent.getAction();

        /*UsbDevice device = (UsbDevice)intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
        if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
            setDevice(device);
        } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
            if (mDevice != null && mDevice.equals(device)) {
                setDevice(null);
            }
        }*/
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
                
                if (event.getAction() == MotionEvent.ACTION_DOWN) {//I think only move will be needed
                	//Start recording position
                	now_x = event.getX();
                	now_y = event.getY();
                	state = 1;
                } 
                else if (event.getAction() == MotionEvent.ACTION_MOVE) {
                	//record position, send commands
                	if (time % resolution == 0) { //to determine speed
                		old_x = now_x;
                		old_y = now_y;
                		now_x = event.getX();
                		now_y = event.getY();
                		speed_x = Math.abs((now_x - old_x)/resolution);
                		speed_y = Math.abs((now_y - old_y)/resolution);
                	}
                	draw_x = event.getX();
                	draw_y = event.getY();
                	state = 1;
                } 
                else if (event.getAction() == MotionEvent.ACTION_UP) {
                	now_x = now_y  = old_x = old_y = 0;
                	state = 0;
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
            // draw current graphic at last...
            if (state == 1) { canvas.drawCircle (draw_x, draw_y, 35, motion); }
        }
     
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {}
     
        public void surfaceCreated(SurfaceHolder holder) {
            _thread.setRunning(true);
            _thread.start();
        }
     
        public void surfaceDestroyed(SurfaceHolder holder) {
            boolean retry = true;
            _thread.setRunning(false);
            while (retry) {
                try {
                    _thread.join();
                    retry = false;
                } catch (InterruptedException e) { } // we will try it again and again...           
            }
        }
    }

    class ControlThread extends Thread {
        private SurfaceHolder _surfaceHolder;
        private Panel _panel;
        private boolean _run = false;
     
        public ControlThread(SurfaceHolder surfaceHolder, Panel panel) {
            _surfaceHolder = surfaceHolder;
            _panel = panel;
        }
        
        public SurfaceHolder getSurfaceHolder() { return _surfaceHolder; }
        public void setRunning(boolean run) { _run = run; }
     
        @Override
        public void run() { //check the run in the other program
            ByteBuffer buffer = ByteBuffer.allocate(1);
            UsbRequest request = new UsbRequest();
            request.initialize(mConnection, mEndpointIntr);
        	Canvas c;
            while (_run) {
                c = null;
                try {
                	time ++;
                    c = _surfaceHolder.lockCanvas(null);
                    synchronized (_surfaceHolder) {

                    	if (now_x < old_x) { 
                    		sendCommand(COMMAND_LEFT); 
                    		command1 = "LEFT";
                    	}//go left
                    	else if (now_x > old_x) { 
                    		sendCommand(COMMAND_RIGHT);
                    		command1 = "RIGHT";
                    	}// go right
                    	
                    	if (now_y > old_y) { 
                    		sendCommand (COMMAND_DOWN);
                    		command2 = "DOWN";
                    	}// go down
                    	else if (now_y < old_y) { 
                    		sendCommand (COMMAND_UP);
                    		command2 = "UP";
                    	}// go up
                    	
                        _panel.onDraw(c);
                    }
                } finally {

                    if (c != null) {
                        _surfaceHolder.unlockCanvasAndPost(c);
                    }
                }
            }
        }
    }
    
    //TODO get runnable thing up, the connection is made on resume but the thread starts when the surface is created
    private void setDevice(UsbDevice device) 
    { //fix
        Log.d(TAG, "setDevice " + device);
        if (device.getInterfaceCount() != 1) 
        {
            Log.e(TAG, "could not find interface");
            return;
        }
        UsbInterface intf = device.getInterface(0);
        // device should have one endpoint
        if (intf.getEndpointCount() != 1) 
        {
            Log.e(TAG, "could not find endpoint");
            return;
        }
        // endpoint should be of type interrupt
        UsbEndpoint ep = intf.getEndpoint(0);
        if (ep.getType() != UsbConstants.USB_ENDPOINT_XFER_INT) 
        {
            Log.e(TAG, "endpoint is not interrupt type");
            return;
        }
        mDevice = device;
        mEndpointIntr = ep;
        if (device != null) 
        {
            UsbDeviceConnection connection = mUsbManager.openDevice(device);
            if (connection != null && connection.claimInterface(intf, true)) 
            {
                Log.d(TAG, "open SUCCESS");
                mConnection = connection;
                //Thread thread = new Thread(this);
                //thread.start();
            } 
            else 
            {
                Log.d(TAG, "open FAIL");
                mConnection = null;
            }
         }
    }
    private void sendCommand(int control) { //modify
        synchronized (this) {
            Log.d(TAG, "sendMove " + control);
            if (mConnection != null) {
                byte[] message = new byte[1];
                message[0] = (byte)control;
                // Send command via a control request on endpoint zero
                mConnection.controlTransfer(0x21, 0x9, 0x200, 0, message, message.length, 0);
            }
        }
    }
}

//http://www.circuitsathome.com/mcu/exchanging-data-between-usb-devices-and-android-phone-using-arduino-and-usb-host-shield
//LED blinker: http://allaboutee.com/2011/12/31/arduino-adk-board-blink-an-led-with-your-phone-code-and-explanation/
//and: http://stackoverflow.com/questions/9053809/arduino-adk-android-led-blink-example-compiling-errors
//bluetooth: http://www.elecfreaks.com/677.html
//http://www.zdnet.com/blog/burnette/how-to-use-multi-touch-in-android-2-part-3-understanding-touch-events/1775
//maybe this is the arduino side http://arduino.cc/en/Tutorial/SerialEvent
//motorshield http://www.ladyada.net/make/mshield/use.html
//android reference: http://developer.android.com/guide/topics/usb/host.html
//servo example: http://arduino.cc/en/Tutorial/Sweep