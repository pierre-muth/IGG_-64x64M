package imageProc;

import java.awt.AWTException;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.MouseInfo;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Enumeration;
import java.util.Timer;
import java.util.TimerTask;

import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;

import purejavacomm.CommPortIdentifier;
import purejavacomm.PortInUseException;
import purejavacomm.SerialPort;
import purejavacomm.UnsupportedCommOperationException;
import utils.String2Hex;

public class IGG1Simulator extends JPanel{
	public static final int HEIGHT = 64;
	public static final int WIDTH  = 64;

	JLabel labelComputed;
	
	Robot robot;
	private Timer timer;

	static int[] pixList = new int[HEIGHT * WIDTH *3];
	
	private static coef[] matrixJarvis = new coef[] {
//			 new coef( 1, 0, 7/48.0),
			 new coef( 2, 0, 5/48.0),
//			 new coef(-2, 1, 3/48.0),
			 new coef(-1, 1, 5/48.0),
//			 new coef( 0, 1, 7/48.0),
			 new coef( 1, 1, 5/48.0),
//			 new coef( 2, 1, 3/48.0),
			 new coef(-2, 2, 1/48.0),
//			 new coef(-1, 2, 3/48.0),
			 new coef( 0, 2, 5/48.0),
//			 new coef( 1, 2, 3/48.0),
			 new coef( 2, 2, 1/48.0) 	
			
	};
	
	private static SerialPort serialPort;
	private static String portName = "COM7";
	private static OutputStream outputStream;

	public IGG1Simulator() {
		initGUI();
		initSerial();
		
		CaptureTaskDither task = new CaptureTaskDither();
		timer = new Timer();
		timer.schedule(task, 500, 1000);
		
	}

	private void initSerial() {
		
		CommPortIdentifier portId = null;
		Enumeration<CommPortIdentifier> enumPort = CommPortIdentifier.getPortIdentifiers();

		while(enumPort.hasMoreElements()) {
			CommPortIdentifier cpi = enumPort.nextElement();

			if(cpi.getPortType() == CommPortIdentifier.PORT_SERIAL) {
				System.out.println("SERIAL CommPortIdentifier = " + cpi.getName());
				if(cpi.getName().contains(portName) ) {
					portId = cpi;
				}
			}
		}

		if (portId != null) {
			try {
				serialPort = (SerialPort) portId.open("SerialInterface", 2000);
				outputStream = serialPort.getOutputStream();
				serialPort.setSerialPortParams(115200, SerialPort.DATABITS_8, SerialPort.STOPBITS_1, SerialPort.PARITY_NONE);
				serialPort.setDTR(false);
				serialPort.setRTS(false);
				System.out.println("SERIAL port opened: "+serialPort.getName());
			} catch (UnsupportedCommOperationException | PortInUseException | IOException ex) {
				ex.printStackTrace();
			}
		} else {
			System.out.println("SERIAL port containing "+portName+" is not found.");
		}


	}
	
	private void initGUI() {
		labelComputed = new JLabel();
		labelComputed.setPreferredSize(new Dimension(WIDTH*6, HEIGHT*6));
		labelComputed.setOpaque(true);
		add(labelComputed, BorderLayout.CENTER);

		try {
			robot = new Robot();
		} catch (AWTException e) {
			e.printStackTrace();
		}

	}
	
	private int[] getGreenList(int[] pixList){
		int[] greens = new int[(WIDTH/2)*HEIGHT];
		int greenIndex = 0;
		
		for (int x = 0; x < WIDTH; x++) {
			for (int y = 0; y < HEIGHT; y++) {
				if (y%2 == 0) {
					if (x%2 == 0){
						greens[greenIndex] =  (int) (0* (pixList[0+ (x*3) + (WIDTH*3*y)]));
						greens[greenIndex] =  (int) (1* (pixList[1+ (x*3) + (WIDTH*3*y)]));
						greens[greenIndex] += (int) (0* (pixList[2+ (x*3) + (WIDTH*3*y)]));
						greenIndex++;
					}
				} else {
					if (x%2 == 1){
						greens[greenIndex] =  (int) (0* (pixList[0+ (x*3) + (WIDTH*3*y)]));
						greens[greenIndex] =  (int) (1* (pixList[1+ (x*3) + (WIDTH*3*y)]));
						greens[greenIndex] += (int) (0* (pixList[2+ (x*3) + (WIDTH*3*y)]));
						greenIndex++;
					}
				}
				
			}
		}
		
		return greens;
	}
	
	private int[] getOrangeList(int[] pixList){
		int[] oranges = new int[(WIDTH/2)*HEIGHT];
		int orangeIndex = 0;
		
		for (int x = 0; x < WIDTH; x++) {
			for (int y = 0; y < HEIGHT; y++) {
				if (y%2 == 1) {
					if (x%2 == 0){
						oranges[orangeIndex] =  (int) (1* (pixList[0+ (x*3) + WIDTH*3*y]));
						oranges[orangeIndex] += (int) (0* (pixList[1+ (x*3) + WIDTH*3*y]));
						oranges[orangeIndex] += (int) (0* (pixList[2+ (x*3) + WIDTH*3*y]));
						orangeIndex++;
					}
				} else {
					if (x%2 == 1){
						oranges[orangeIndex] =  (int) (1* (pixList[0+ (x*3) + WIDTH*3*y]));
						oranges[orangeIndex] += (int) (0* (pixList[1+ (x*3) + WIDTH*3*y]));
						oranges[orangeIndex] += (int) (0* (pixList[2+ (x*3) + WIDTH*3*y]));
						orangeIndex++;
					}
				}
				
			}
		}
		return oranges;
	}
	
	public int[] getJarvisDitheredInts(int[] pixList, int imgWidth, int imgHeight) {
		int[] pixDithered = new int[pixList.length];
		
		for (int i = 0; i < pixList.length; ++i) {
            int o = pixList[i];
            int n = o <= 0x80 ? 0 : 0xff;

            int x = i % imgWidth;
            int y = i / imgWidth;

            pixDithered[i] = n;
            
            for (int j = 0; j != matrixJarvis.length; ++j) {
                int x0 = x + matrixJarvis[j].dx;
                int y0 = y + matrixJarvis[j].dy;
                if (x0 > imgWidth - 1 || x0 < 0 || y0 > imgHeight - 1 || y0 < 0) {
                    continue;
                }
                // the residual quantization error
                // warning! have to overcast to signed int before calculation!
                int d = (int) ((o - n) * matrixJarvis[j].coef);
                // keep a value in the <min; max> interval
                int a = pixList[x0 + imgWidth * y0] + d;
                if (a > 0xff) {
                    a = 0xff;
                }
                else if (a < 0) {
                    a = 0;
                }
                pixList[x0 + imgWidth * y0] = a;
            }
        }

		return pixDithered;
	}
	
	public int[] mergeToColor(int[] greens, int[] oranges) {
		int[] pix = new int[WIDTH * HEIGHT *3];
		int orangeIndex = 0;
		int greenIndex = 0;
		
		for (int x = 0; x < WIDTH; x++) {
			for (int y = 0; y < HEIGHT; y++) {
				if (y%2 == 0) {
					if (x%2 == 0){
						pix[0+ (x*3) + (WIDTH*3*y)] =  0;
						pix[1+ (x*3) + (WIDTH*3*y)] =  (int) (greens[greenIndex]);
						pix[2+ (x*3) + (WIDTH*3*y)] =  0;
						greenIndex++;
					} else {
						pix[0+ (x*3) + (WIDTH*3*y)] =  (int) (oranges[orangeIndex]);
						pix[1+ (x*3) + (WIDTH*3*y)] =  (int) (0.5* oranges[orangeIndex]);
						pix[2+ (x*3) + (WIDTH*3*y)] =  0;
						orangeIndex++;
					}
				} else {
					if (x%2 == 0){
						pix[0+ (x*3) + (WIDTH*3*y)] =  (int) (oranges[orangeIndex]);
						pix[1+ (x*3) + (WIDTH*3*y)] =  (int) (0.5* oranges[orangeIndex]);
						pix[2+ (x*3) + (WIDTH*3*y)] =  0;
						orangeIndex++;
					} else {
						pix[0+ (x*3) + (WIDTH*3*y)] =  0;
						pix[1+ (x*3) + (WIDTH*3*y)] =  (int) (greens[greenIndex]);
						pix[2+ (x*3) + (WIDTH*3*y)] =  0;
						greenIndex++;
					}
				}
				
			}
		}
		
		return pix;
	}

	private class CaptureTaskDither extends TimerTask {

		int[] oranges = new int[WIDTH/2 * HEIGHT];
		int[] greens = new int[WIDTH/2 * HEIGHT];
		byte[] imageBytes = new byte[WIDTH * (HEIGHT/8)];
		
		@Override
		public void run() {
			Rectangle screenRectangle = new Rectangle(
					MouseInfo.getPointerInfo().getLocation().x - (WIDTH/2)
					, MouseInfo.getPointerInfo().getLocation().y - (HEIGHT/2),
					WIDTH, HEIGHT);
			final BufferedImage sourceImage = robot.createScreenCapture(screenRectangle);
			
			sourceImage.getData().getPixels(0, 0, WIDTH, HEIGHT, pixList);
			
			oranges = getOrangeList(pixList);
			greens = getGreenList(pixList);
			
			oranges = getJarvisDitheredInts(oranges, WIDTH/2, HEIGHT);
			greens = getJarvisDitheredInts(greens, WIDTH/2, HEIGHT);
			
			pixList = mergeToColor(greens, oranges);
	        
			WritableRaster wr = sourceImage.getData().createCompatibleWritableRaster();
			wr.setPixels(0, 0, WIDTH, HEIGHT, pixList);

			sourceImage.setData(wr);
			
			BufferedImage imageResized = new BufferedImage(WIDTH*6, HEIGHT*6, BufferedImage.TYPE_3BYTE_BGR);
			Graphics2D g = imageResized.createGraphics();
			g = imageResized.createGraphics();
			g.drawImage(sourceImage, 0, 0, WIDTH*6, HEIGHT*6, null);
			g.dispose();

			javax.swing.SwingUtilities.invokeLater(new Runnable() {
				public void run() {
					labelComputed.setIcon(new ImageIcon(imageResized));
				}
			});
			
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
				
					dotState = pixList[1+ 3*( (col*HEIGHT) + row) ] > 1;
				
					if (dotState) {
						aByte = (byte) (aByte | (mask));
					}
					
					if (bit >=7 ){
						imageBytes[((WIDTH-1)-col)* (HEIGHT/8) + byteOfCol] = aByte;
						System.out.println(String2Hex.byteToHex(aByte)+" ");
						aByte = 0x00;
					}
				}
				System.out.println(".");
			}

			if (outputStream != null) {
				try {
					outputStream.write(imageBytes);
					outputStream.flush();
				} catch (IOException e) {
					e.printStackTrace();
				}
			} else {
				System.out.println("Serial port not initialized");
			}

		}
	}

	private static void createAndShowGUI() {
		//Create and set up the window.
		JFrame frame = new JFrame("FrameDemo");
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.getContentPane().add(new IGG1Simulator(), BorderLayout.CENTER );
		//Display the window.
		frame.pack();
		frame.setVisible(true);
	}

	public static void main(String[] args) {
		//Schedule a job for the event-dispatching thread:
		//creating and showing this application's GUI.
		javax.swing.SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				createAndShowGUI();
			}
		});
	}
	
	private static class coef {
		public int dx;
		public int dy;
		public double coef;

		public coef(int dx, int dy, double coef) {
			this.dx = dx;
			this.dy = dy;
			this.coef = coef;
		}
	}
}
