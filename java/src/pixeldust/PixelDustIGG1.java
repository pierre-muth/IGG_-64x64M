package pixeldust;

import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;

import javax.swing.JLabel;

import com.pi4j.io.i2c.I2CBus;
import com.pi4j.io.i2c.I2CDevice;
import com.pi4j.io.i2c.I2CFactory;
import com.pi4j.io.i2c.I2CFactory.UnsupportedBusNumberException;
import com.pi4j.io.serial.Serial;
import com.pi4j.io.serial.SerialFactory;

/**
 * Java port of the PixelDust Adafruit original code
 * Written by Phil "PaintYourDragon" Burgess for Adafruit Industries.
 * https://github.com/adafruit/Adafruit_PixelDust
 * @author Pierre Muth
 */

public class PixelDustIGG1 {
	public static final int WIDTH  = 64; 		// Width in pixels
	public static final int HEIGHT = 64; 		// Height in pixels
	public static final int GRAIN_NUMBER = 10*64;	// Number of sand grains
	public static final int SCALE = 256;			// Accelerometer input scaling = scale/256
	public static final int ELASTICITY = 64;	// Grain elasticity (bounce) = elasticity/256
	public static final int ZOOM = 10;	 		// for display, size of the squares representing a pixel
	
	public static final int X_MAX = WIDTH*256-1;  // Max X coordinate in grain space
	public static final int Y_MAX = HEIGHT*256-1; // Max Y coordinate in grain space
	
	public static int[][] bitmap = new int[WIDTH][HEIGHT];		// pixel bitmap
	public static Grain[] grains = new Grain[GRAIN_NUMBER];		//One per grain
	
	private Timer timer;
	private static int[] pixList = new int[HEIGHT * WIDTH];
	private static byte[] imageBytes = new byte[WIDTH * (HEIGHT/8)];
	
	private int ax = 0;
	private int ay = 0;
	
	private final Serial serial;
	private I2CDevice MPU9250device;
	
	public PixelDustIGG1() throws IOException, UnsupportedBusNumberException, InterruptedException{
		serial = SerialFactory.createInstance();

		String comPort = Serial.DEFAULT_COM_PORT;
		int serialSpeed = 115200;

		serial.open(comPort, serialSpeed);
		System.out.println("Opened "+ comPort+" at "+serialSpeed+" bauds.");
		
		final I2CBus bus = I2CFactory.getInstance(I2CBus.BUS_1);

		MPU9250device = bus.getDevice(MPU9250.ADDRESS);
		
		MPU9250device.write(MPU9250.PWR_MGMT_1, (byte) 0x00);
		Thread.sleep(100);
		MPU9250device.write(MPU9250.PWR_MGMT_1, (byte) 0x01);
		Thread.sleep(200);
		
		MPU9250device.write(MPU9250.CONFIG, (byte) 0x03);
		MPU9250device.write(MPU9250.SMPLRT_DIV, (byte) 0x04);
		
		
		MPU9250device.write(MPU9250.ACCEL_CONFIG, (byte) 0x00);
		MPU9250device.write(MPU9250.ACCEL_CONFIG2, (byte) 0x03);
		
		int read = MPU9250device.read(MPU9250.WHO_AM_I_MPU9250);
		System.out.println("WHO_AM_I_MPU9250 > "+read);
		
		for (int i = 0; i < pixList.length; i++) {
			pixList[i] = 0;
		}

		// Set the grains
		for (int i = 0; i < GRAIN_NUMBER; i++) {
			grains[i] = new Grain();
			grains[i].x = (i%64)*256;
			grains[i].y = ( 63-(i/64) ) *256;
		}
		
		// Draw adafruit logo
		for (int i = 0; i < AdaLogo.LOGO_WIDTH; i++) {
			for (int j = 0; j < AdaLogo.LOGO_HEIGHT; j++) {
				bitmap[i+12][j+12] = AdaLogo.LOGO_GRAY[i+j*AdaLogo.LOGO_WIDTH];
			}
		}
		
		// launch the simulator
		SimulateTask simulateTask = new SimulateTask();
		UpdateIGG1Task updateIGG1Task = new UpdateIGG1Task();
		UpdateAccelerationTask updateAccelerationTask = new UpdateAccelerationTask();
		timer = new Timer();
		timer.schedule(simulateTask, 2000, 16);
		timer.schedule(updateIGG1Task, 250, 100);
		timer.schedule(updateAccelerationTask, 200, 100);
		
		
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
		
		for (int i = 0; i < WIDTH; i++) {
			for (int j = 0; j < HEIGHT; j++) {
				pixList[i+j*WIDTH] = bitmap[i][j];
			}
		}
		
		byte aByte;
		byte mask;
		int bit;
		int byteOfCol;
		boolean dotState = false;
		
		for (int col = 0; col < WIDTH; col++) {
			aByte = 0x00;
			for (int row = 0; row < HEIGHT; row++) {
				
				bit = row % 8;
				mask = (byte) (0x01 << bit);
				byteOfCol = row >> 3;
			
				dotState = pixList[ (col*HEIGHT) + row ] > 32;
			
				if (dotState) {
					aByte = (byte) (aByte | (mask));
				}
				
				if (bit >=7 ){
					imageBytes[((WIDTH-1)-col)* (HEIGHT/8) + byteOfCol] = aByte;
//					System.out.println(String2Hex.byteToHex(aByte)+" ");
					aByte = 0x00;
				}
			}
//			System.out.println(".");
		}
		
		try {
			serial.write(imageBytes);
			System.out.println(".");
		} catch (IllegalStateException | IOException e) {
			e.printStackTrace();
		}
		
	}
	
	private void readAccelerometer() throws IOException {
		int x, y, z;
		x = MPU9250device.read(MPU9250.ACCEL_XOUT_H);
		y = MPU9250device.read(MPU9250.ACCEL_YOUT_H);
		z = MPU9250device.read(MPU9250.ACCEL_ZOUT_H);

		ax = -(((x+128)%256)-128);
		ay = ((y+128)%256)-128;

		System.out.println("ax: "+ax+", ay: "+ay);
	}
	
	public static void main(String[] args) throws Exception {
		PixelDustIGG1 pixelDustIGG1 = new PixelDustIGG1();
	}
	
	private class Grain {
		public int x, y;
		public int vx, vy;
	}
	
	private class SimulateTask extends TimerTask {
		
		@Override
		public void run() {
			
			PixelDustIGG1.this.iterate(PixelDustIGG1.this.ax, PixelDustIGG1.this.ay, 50);
			
		}
	}
	
	private class UpdateIGG1Task extends TimerTask {
		
		@Override
		public void run() {
			
			PixelDustIGG1.this.drawImage();
			
		}
	}
	
	private class UpdateAccelerationTask extends TimerTask {
		
		@Override
		public void run() {
			
			try {
				PixelDustIGG1.this.readAccelerometer();
			} catch (IOException e) {
				e.printStackTrace();
			}
			
		}
	}


}
