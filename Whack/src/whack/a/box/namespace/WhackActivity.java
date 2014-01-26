package whack.a.box.namespace;
 
import java.util.Random;
import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.Window;
 
public class WhackActivity extends Activity {
	public int times = 0;
	public Paint words;
	public Paint backgroundPaint;
	public Paint squaresPaint;
	public Paint hamsterPaint;
	public int state = 0;                 //0 for starting, 1 for started, not up, 2 = up, not hit, 3 = up, hit, 4 = done
	public int current_x = 500, current_y = 500;      //position of the object when up
	public int points = 0, misses = 0, total = 0 ;    //hits and misses and total times appeared
	Random generator = new Random();
	public int screen_x, screen_y;        			  //size of screen
	public int rad;                       			  //radius of circle to draw
	public long time = 0, next_time = 0; 	  //time of state changes
	public long up_time = 150, down_time = 150;
	private long min_time = 30;
	private int num_boxes = 25; 
	public boolean reached = false;
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(new Panel(this));
        
        words = new Paint();
		words.setColor(Color.GREEN);
		words.setAntiAlias(true);
		
    	backgroundPaint = new Paint();
		backgroundPaint.setColor(Color.BLUE);

		squaresPaint = new Paint();
		squaresPaint.setColor(Color.YELLOW);
		squaresPaint.setAntiAlias(true);
		
		hamsterPaint = new Paint();
		hamsterPaint.setColor(Color.RED);
		hamsterPaint.setAntiAlias(true);
    }
 
    class Panel extends SurfaceView implements SurfaceHolder.Callback {
        private GameThread _thread;
        private GraphicObject _currentGraphic = null;
     
        public Panel(Context context) {
            super(context);
            getHolder().addCallback(this);
            _thread = new GameThread(getHolder(), this);
            setFocusable(true);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            synchronized (_thread.getSurfaceHolder()) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    //This is touch screen event.
        			switch (state){
        	               case 0:
        	                    state = 1;
        	                    points = 0;
                    			misses = 0;
        	                    next_time = generator.nextInt() % 100 + 100;
        	                    break;
        	               case 1:
								misses++;
        	                    break;
        	               case 2:
        	                    if (Math.sqrt (Math.pow((event.getX()-current_x), 2) +  
        	                    		Math.pow((event.getY()-current_y), 2)) <= rad) //within confines of circle or a hit
        	                    {
        	                        points++;
        	                        state = 3;
        	                        if (up_time>min_time) {
        	                        	up_time *= 0.93;
        	                        	rad *= 0.97;	
        	                        }
        	                    }
        	                    else
        	                        misses++;
        	                    break;
        	               case 4:
        	            	   state = 0;
        	            	   time = 0;
        	            	   up_time = 150;
        	            	   total = 0;
        	            	   break;
        	               default:
        	                    break;
        	        }
                }
                return true;
            }
        }

        @Override
        public void onDraw(Canvas canvas) {
            canvas.drawColor(Color.BLACK);
            Bitmap bitmap;
            GraphicObject.Coordinates coords;
            screen_x = canvas.getWidth();
            screen_y = canvas.getHeight();
            
            //canvas.drawText(Integer.toString((int)time) + "   " + Integer.toString((int)next_time), 20, 40, words);
            //canvas.drawText(current_x + " " + current_y, 20, 50, words);
            
			switch (state){
	               case 0:
	                   canvas.drawText ("Hit those blue things. Tap anywhere to start", screen_x/2, screen_y/2, words);
	                   rad = screen_y/12;
	                   break;
	               case 1:
	                   canvas.drawText("Hits: " + points + "Misses: " + misses, 20, 20, words);
	                   canvas.drawText("Number" + total + " of " + num_boxes, 20, 30, words);
	                   break;
	               case 2:
	            	   canvas.drawText("Hits: " + points + "Misses: " + misses, 20, 20, words);
	                   canvas.drawText("Number" + total + " of " + num_boxes, 20, 30, words);
	            	   canvas.drawCircle (current_x, current_y, rad, backgroundPaint);
	            	   break;
	               case 3:
	            	   canvas.drawText("Hits: " + points + "Misses: " + misses, 20, 20, words);
	                   canvas.drawText("Number" + total + " of " + num_boxes, 20, 30, words);
	            	   canvas.drawCircle (current_x, current_y, rad, hamsterPaint);
	            	   break;
	               case 4:
	            	   canvas.drawText ("Hit anywhere to try again", screen_x/2, screen_y/2, words);
	            	   canvas.drawText ("Your score is "+points+"/"+total, screen_x/2, screen_y/2 - 10, words);
	            	   break;
	               default:
	                    break;
	        }
            
            // draw current graphic at last...
            if (_currentGraphic != null){
                bitmap = _currentGraphic.getGraphic();
                coords = _currentGraphic.getCoordinates();
                canvas.drawBitmap(bitmap, coords.getX(), coords.getY(), null);
            }
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
                try {
                    _thread.join();
                    retry = false;
                } catch (InterruptedException e) {}// we will try it again and again...}
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
                    time++;
                    if(time >= next_time && !reached){
                    	reached = true;
                    	if (state == 1) { //if it's down, go up
                    		current_x = Math.abs(generator.nextInt() % screen_x);
                    		current_y = Math.abs(generator.nextInt() % screen_y);
                    		state = 2;
                    		next_time += up_time;
                    	}
                    	else if (state == 2 || state == 3) { //if it's already up, go down
                    		state = 1;
                    		down_time = Math.abs(generator.nextInt() % 200);
                    		next_time += down_time;
                    		total ++;
                    		if (total == num_boxes) {
                    			state = 4;
                    		}
                    	}
                    	reached = false;
                    }
                    synchronized (_surfaceHolder) {
                        _panel.onDraw(c);
                        times++;
                    }
                } 
                finally {
                    // do this in a finally so that if an exception is thrown
                    // during the above, we don't leave the Surface in an inconsistent state
                    if (c != null) { _surfaceHolder.unlockCanvasAndPost(c); }
                }
            }
        }
    }
    
    class GraphicObject {
        private Bitmap _bitmap;
        private Coordinates _coordinates;
        
        public GraphicObject(Bitmap bitmap) {
            _bitmap = bitmap;
            _coordinates = new Coordinates();
        }
     
        public Bitmap getGraphic() { return _bitmap; }
        public Coordinates getCoordinates() { return _coordinates; }
     
        //Contains the coordinates of the graphic.
        public class Coordinates {
            private int _x = 100;
            private int _y = 0;
            public int getX() { return _x + _bitmap.getWidth() / 2; }
            public void setX(int value) { _x = value - _bitmap.getWidth() / 2; }
            public int getY() { return _y + _bitmap.getHeight() / 2; }
            public void setY(int value) { _y = value - _bitmap.getHeight() / 2; }
            public String toString() { return "Coordinates: (" + _x + "/" + _y + ")"; }
        }
    }
}

//Maybe: bigger letters
//reference: http://stuffthathappens.com/blog/2009/03/31/android-popping-bubbles/
//http://code.google.com/p/rps-game/source/browse/trunk/android/src/com/kahweh/rps/RockPaperScissors.java?r=34