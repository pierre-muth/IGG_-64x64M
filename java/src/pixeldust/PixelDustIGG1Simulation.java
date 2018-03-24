package pixeldust;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.util.Timer;
import java.util.TimerTask;

import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * Java port of the PixelDust Adafruit original code
 * Written by Phil "PaintYourDragon" Burgess for Adafruit Industries.
 * https://github.com/adafruit/Adafruit_PixelDust
 * @author Pierre Muth
 */

public class PixelDustIGG1Simulation extends JFrame implements KeyListener {
	public static final int WIDTH  = 64; 		// Width in pixels
	public static final int HEIGHT = 64; 		// Height in pixels
	public static final int GRAIN_NUMBER = 500;	// Number of sand grains
	public static final int SCALE = 2;			// Accelerometer input scaling = scale/256
	public static final int ELASTICITY = 64;	// Grain elasticity (bounce) = elasticity/256
	public static final int ZOOM = 10;	 		// for display, size of the squares representing a pixel
	
	public static final int X_MAX = WIDTH*256-1;  // Max X coordinate in grain space
	public static final int Y_MAX = HEIGHT*256-1; // Max Y coordinate in grain space
	
	public static int[][] bitmap = new int[WIDTH][HEIGHT];		// pixel bitmap
	public static Grain[] grains = new Grain[GRAIN_NUMBER];		//One per grain
	
	private JLabel labelComputed;
	private Timer timer;
	private static int[] pixList = new int[HEIGHT * WIDTH];
	
	private int ax = 0;
	private int ay = 4000;
	
	public PixelDustIGG1Simulation(){
		initGUI();
		
		for (int i = 0; i < pixList.length; i++) {
			pixList[i] = (i/WIDTH)%256;
		}
		drawImage();
		
		for (int i = 0; i < 50; i++) {
			for (int j = 0; j < 10; j++) {
				grains[i+j*50] = new Grain();
				grains[i+j*50].x = (i%50)*256;
				grains[i+j*50].y = j *256;
			}
		}
		
		for (int i = 0; i < AdaLogo.LOGO_WIDTH; i++) {
			for (int j = 0; j < AdaLogo.LOGO_HEIGHT; j++) {
				bitmap[i+12][j+12] = AdaLogo.LOGO_GRAY[i+j*AdaLogo.LOGO_WIDTH];
			}
		}
		
		SimulateTask simulateTask = new SimulateTask();
		timer = new Timer();
		timer.schedule(simulateTask, 2000, 16);
		
		
	}
	
	private void iterate(int ax, int ay, int az) {
		
		ax = ax * SCALE / 256;  // Scale down raw accelerometer
		ay = ay * SCALE / 256;  // inputs to manageable range.
		az = az * SCALE / 2048; // Z is further scaled down 1:8
		// A tiny bit of random motion is applied to each grain, so that tall
		// stacks of pixels tend to topple (else the whole stack slides across
		// the display).  This is a function of the Z axis input, so it's more
		// pronounced the more the display is tilted (else the grains shift
		// around too much when the display is held level).
		az  = (az >= 4) ? 1 : 5 - az; // Clip & invert
		ax -= az;                     // Subtract Z motion factor from X, Y,
		ay -= az;                     // then...
		int az2 = az * 2 + 1;     	  // max random motion to add back in


		// Apply 2D accel vector to grain velocities...
		int v2; // Velocity squared
		double   v;  // Absolute velocity
		for(int i=0; i<grains.length; i++) {
			grains[i].vx += ax + Math.random()*az2;
			grains[i].vy += ay + Math.random()*az2;
			// Terminal velocity (in any direction) is 256 units -- equal to
			// 1 pixel -- which keeps moving grains from passing through each other
			// and other such mayhem.  Though it takes some extra math, velocity is
			// clipped as a 2D vector (not separately-limited X & Y) so that
			// diagonal movement isn't faster than horizontal/vertical.
			v2 = grains[i].vx*grains[i].vx+grains[i].vy*grains[i].vy;
			if(v2 > 65536) { // If v^2 > 65536, then v > 256
				v = Math.sqrt(v2); // Velocity vector magnitude
				grains[i].vx = (int)(256.0*(float)grains[i].vx/v); // Maintain heading &
				grains[i].vy = (int)(256.0*(float)grains[i].vy/v); // limit magnitude
			}
		}

		// ...then update position of each grain, one at a time, checking for
		// collisions and having them react.  This really seems like it shouldn't
		// work, as only one grain is considered at a time while the rest are
		// regarded as stationary.  Yet this naive algorithm, taking many not-
		// technically-quite-correct steps, and repeated quickly enough,
		// visually integrates into something that somewhat resembles physics.
		// (I'd initially tried implementing this as a bunch of concurrent and
		// "realistic" elastic collisions among circular grains, but the
		// calculations and volume of code quickly got out of hand for both
		// the tiny 8-bit AVR microcontroller and my tiny dinosaur brain.)

		for(int i=0; i<grains.length; i++) {
			int newx = grains[i].x + grains[i].vx; // New position in grain space
			int newy = grains[i].y + grains[i].vy;
			if(newx < 0) {         // If grain would go out of bounds
				newx = 0;            // keep it inside,
				grains[i].vx = bounce(grains[i].vx); // and bounce off wall
			} else if(newx > X_MAX) {
				newx = X_MAX;
				grains[i].vx = bounce(grains[i].vx);
			}
			if(newy < 0) {
				newy = 0;
				grains[i].vy = bounce(grains[i].vy);
			} else if(newy > Y_MAX) {
				newy = Y_MAX;
				grains[i].vy = bounce(grains[i].vy);
			}

			// oldidx/newidx are the prior and new pixel index for this grain.
			// It's a little easier to check motion vs handling X & Y separately.
			int oldidx = (grains[i].y / 256) * WIDTH + (grains[i].x / 256);
			int newidx = (newy       / 256) * WIDTH + (newx       / 256);

			if((oldidx != newidx) && bitmap[newx/256][newy/256] !=0) {      // If grain is moving to a new pixel but already occupied..
				int delta = Math.abs(newidx - oldidx); // What direction when blocked?
				if(delta == 1) {              // 1 pixel left or right)
					newx = grains[i].x;          // Cancel X motion
					grains[i].vx = bounce(grains[i].vx);        // and bounce X velocity (Y is OK)
				} else if(delta == WIDTH) {   // 1 pixel up or down
					newy = grains[i].y;          // Cancel Y motion
					grains[i].vy = bounce(grains[i].vy);        // and bounce Y velocity (X is OK)
				} else { // Diagonal intersection is more tricky...
					// Try skidding along just one axis of motion if possible
					// (start w/faster axis).
					if(Math.abs(grains[i].vx) >= Math.abs(grains[i].vy)) { // X axis is faster
						if(bitmap[newx / 256][grains[i].y / 256] == 0) { // newx, oldy
							// That pixel's free!  Take it!  But...
							newy = grains[i].y;      // Cancel Y motion
							grains[i].vy = bounce(grains[i].vy);    // and bounce Y velocity
						} else { // X pixel is taken, so try Y...
							if( bitmap[grains[i].x / 256][ newy / 256] == 0) { // oldx, newy
								// Pixel is free, take it, but first...
								newx = grains[i].x;    // Cancel X motion
								grains[i].vx = bounce(grains[i].vx);  // and bounce X velocity
							} else { // Both spots are occupied
								newx = grains[i].x;    // Cancel X & Y motion
								newy = grains[i].y;
								grains[i].vx = bounce(grains[i].vx);  // Bounce X & Y velocity
								grains[i].vy = bounce(grains[i].vy);
							}
						}
					} else { // Y axis is faster, start there
						if(bitmap[grains[i].x / 256][ newy / 256] == 0) { // oldx, newy
							// Pixel's free!  Take it!  But...
							newx = grains[i].x;      // Cancel X motion
							grains[i].vx = bounce(grains[i].vx);    // and bounce X velocity
						} else { // Y pixel is taken, so try X...
							if(bitmap[newx / 256][ grains[i].y / 256] == 0) { // newx, oldy
								// Pixel is free, take it, but first...
								newy = grains[i].y;    // Cancel Y motion
								grains[i].vy = bounce(grains[i].vy);  // and bounce Y velocity
							} else { // Both spots are occupied
								newx = grains[i].x;    // Cancel X & Y motion
								newy = grains[i].y;
								grains[i].vx = bounce(grains[i].vx);  // Bounce X & Y velocity
								grains[i].vy = bounce(grains[i].vy);
							}
						}
					}
				}
			}
			bitmap[grains[i].x / 256][ grains[i].y / 256] = 0; // Clear old spot
			grains[i].x = newx;                              // Update grain position
			grains[i].y = newy;
			bitmap[newx / 256][ newy / 256] = 255 ;               // Set new spot
		}
		
	}
	
	private int bounce(int n) {
		return (int) ((-n) * ELASTICITY / 256.0);	//< 1-axis elastic bounce
	}
	
	private void drawImage() {
		
		BufferedImage sourceImage = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_BYTE_GRAY);
		
		WritableRaster wr = sourceImage.getData().createCompatibleWritableRaster();
		wr.setPixels(0, 0, WIDTH, HEIGHT, pixList);
		
		sourceImage.setData(wr);
		
		BufferedImage imageResized = new BufferedImage(WIDTH*ZOOM, HEIGHT*ZOOM, BufferedImage.TYPE_BYTE_GRAY);
		Graphics2D g = imageResized.createGraphics();
		g = imageResized.createGraphics();
		g.drawImage(sourceImage, 0, 0, WIDTH*ZOOM, HEIGHT*ZOOM, null);
		g.dispose();
		
		javax.swing.SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				labelComputed.setIcon(new ImageIcon(imageResized));
			}
		});
		
	}
	
	private void initGUI() {
		JPanel panel = new JPanel();
		labelComputed = new JLabel();
		labelComputed.setPreferredSize(new Dimension(WIDTH*ZOOM, HEIGHT*ZOOM));
		labelComputed.setOpaque(true);
		panel.add(labelComputed, BorderLayout.CENTER);
		getContentPane().add(panel, BorderLayout.CENTER );
		addKeyListener(this);
	}
	
	@Override
	public void keyTyped(KeyEvent e) {
	}

	@Override
	public void keyPressed(KeyEvent e) {
//		System.out.println("keyPressed "+e);
		if(e.getKeyCode() == 37) { // left
			ax = -4000;
			ay = 0;
		}
		if(e.getKeyCode() == 38) { // up
			ax = 0;
			ay = -4000;
		}
		if(e.getKeyCode() == 39) { // right
			ax = 4000;
			ay = 0;
		}
		if(e.getKeyCode() == 40) { // down
			ax = 0;
			ay = 4000;
		}
	}

	@Override
	public void keyReleased(KeyEvent e) {
	}
	
	public static void main(String[] args) {
		javax.swing.SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				PixelDustIGG1Simulation frame = new PixelDustIGG1Simulation();
				frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
				frame.pack();
				frame.setVisible(true);
			}
		});
	}
	
	private class Grain {
		public int x, y;
		public int vx, vy;
	}
	
	private class SimulateTask extends TimerTask {
		
		@Override
		public void run() {
			
			PixelDustIGG1Simulation.this.iterate(PixelDustIGG1Simulation.this.ax, PixelDustIGG1Simulation.this.ay, 50);
			
			for (int i = 0; i < WIDTH; i++) {
				for (int j = 0; j < HEIGHT; j++) {
					pixList[i+j*WIDTH] = bitmap[i][j];
				}
			}
			
			drawImage();
			
		}
	}


}
