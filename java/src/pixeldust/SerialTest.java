package pixeldust;

import com.pi4j.io.i2c.I2CBus;
import com.pi4j.io.i2c.I2CDevice;
import com.pi4j.io.i2c.I2CFactory;
import com.pi4j.io.serial.Serial;
import com.pi4j.io.serial.SerialFactory;

public class SerialTest {
	private final Serial serial;

	public SerialTest () throws Exception {
		serial = SerialFactory.createInstance();

		String comPort = Serial.DEFAULT_COM_PORT;
		int serialSpeed = 115200;

		serial.open(comPort, serialSpeed);
		System.out.println("Opened "+ comPort+" at "+serialSpeed+" bauds.");

//		serial.write('>');
		
		final I2CBus bus = I2CFactory.getInstance(I2CBus.BUS_1);

		I2CDevice MPU9250device = bus.getDevice(MPU9250.ADDRESS);
		
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
		
		int x, y, z;
		for (int i = 0; i < 1000; i++) {
			x = MPU9250device.read(MPU9250.ACCEL_XOUT_H);
			y = MPU9250device.read(MPU9250.ACCEL_YOUT_H);
			z = MPU9250device.read(MPU9250.ACCEL_ZOUT_H);

			x = ((x+128)%256)-128;
			y = ((y+128)%256)-128;
			
			System.out.println("X: "+x+", Y: "+y+", Z: "+z);
			Thread.sleep(100);
		}
	}


	public static void main(String[] args) {

		try {
			new SerialTest();
		} catch (Exception e) {
			e.printStackTrace();
		}

	}
}
