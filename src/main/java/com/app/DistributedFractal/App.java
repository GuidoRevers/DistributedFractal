package com.app.DistributedFractal;

import java.awt.Color;
import java.awt.HeadlessException;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.UnknownHostException;
import java.util.LinkedList;
import java.util.concurrent.CompletableFuture;

import javax.imageio.ImageIO;
import javax.servlet.http.HttpSessionListener;
import javax.swing.JColorChooser;

import org.apache.poi.sl.draw.DrawPaint;

import processing.core.PApplet;
import processing.event.KeyEvent;
import processing.event.MouseEvent;

/**
 * Hello world!
 *
 */
public class App extends PApplet {
	final static int WIDTH = 800;
	final static int HEIGHT = 600;
	final static double[] IterationCounts = new double[WIDTH * HEIGHT];
	static Param param = new Param(IterationCounts, WIDTH, HEIGHT, -2.5d, 1d, -1d, 1d, 100);
	static int[] _p;
//	Server server = new Server();
	// method used only for setting the size of the window
	final FractlWorker frac;

	public App() throws UnknownHostException {
		super();
		frac = new DynamicRemoteWork();
		
	}
	
	public void settings() {
		
		size(WIDTH, HEIGHT);
	}

	// identical use to setup in Processing IDE except for size()
	public void setup() {
		frameRate(24);
		loadPixels();
		_p = pixels;

//		LocalWork.run(param);
		frac.run(param);
	}

	

	public void draw() {
		//**********keyPressed after start BugFix*********
		if (millis() < 1000)
            ((java.awt.Canvas) surface.getNative()).requestFocus();
		//************************************************
		
//		loadPixels();

		double[] table = Fractal.postHistogramHueLookup_(param);

		double iterations;
		float color;
		for (int i = 0; i < WIDTH * HEIGHT; i++) {
			iterations = param.IterationCounts[i];
			color = lerp((float) table[(int) Math.floor(iterations)], (float) table[(int) Math.ceil(iterations)],
					(float) (iterations % 1));
			Color c = DrawPaint.HSL2RGB(241d, 100d, iterations < param.max_iteration ? color * 100d : 0, 1d);
//			Color c = HSLColor.toRGB(241, 100, (float) (iterations < param.max_iteration ? color * 100f : 0));
			pixels[i] = c.getRGB();
		}

		updatePixels();

	}

	@Override
	public void mouseClicked(MouseEvent event) {		
		if (event.getButton() == LEFT) {
			System.out.println("clicked: " + event.getX() + "/" + event.getY());
			double x = event.getX() * (param.dx2 - param.dx1) / WIDTH + param.dx1;
			double y = event.getY() * (param.dy2 - param.dy1) / HEIGHT + param.dy1;
			param = zoomParam(param, 0.5d, x, y);
			frac.run(param);
			super.mouseClicked(event);
		}
	}

	@Override
	public void keyPressed(KeyEvent event) {
		if (event.getKey() == '+') {
			param = increaseMaxIterParam(param, 2d);
			System.out.println("+: max_iterations = " + param.max_iteration);
			frac.run(param);			
		}
		if (event.getKey() == '-') {
			param = increaseMaxIterParam(param, 0.5d);
			System.out.println("-: max_iterations = " + param.max_iteration);
			frac.run(param);			
		}
		if (event.getKey() == 's') {
			save();
		}
		
		if (event.getKey() == 'm') {
			param = swapModeParam(param);
			System.out.println("mode = " + param.mode);
			frac.run(param);	
		}
		if (event.getKey() == 'l') {
			try {
				String data = (String) Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor);
				String[] split = data.trim().split(" ");
				param = new Param(new double[param.IterationCounts.length],
						Integer.parseInt(split[0]),
						Integer.parseInt(split[1]),
						Double.parseDouble(split[2]),
						Double.parseDouble(split[3]),
						Double.parseDouble(split[4]),
						Double.parseDouble(split[5]),
						Integer.parseInt(split[6]));
				frac.run(param);
			} catch (HeadlessException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (UnsupportedFlavorException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} 
		}
		super.keyPressed(event);
	}

	public static void save() {
		try {
		    // retrieve image
		    BufferedImage bi = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
		    bi.setRGB(0, 0, WIDTH, HEIGHT, _p, 0, WIDTH);
		    File outputfile = new File("saved.png");
		    ImageIO.write(bi, "png", outputfile);
		} catch (IOException e) {
		    e.printStackTrace();
		}	
		
	}

	public static void main(String[] args) {
		PApplet.main("com.app.DistributedFractal.App");

	}

	private Param zoomParam(Param param, double zoom, double x, double y) {
		double x1 = param.dx1;
		double x2 = param.dx2;
		double y1 = param.dy1;
		double y2 = param.dy2;
		double xh = (x2 - x1) / 2;
//		double xc = xh + x1;
		double yh = (y2 - y1) / 2;
//		double yc = yh + y1;

//		x1 = (x1 - xc) * zoom + x;		
//		x2 = (x2 - xc) * zoom + x;
//		y1 = (y1 - yc) * zoom + y;
//		y2 = (y2 - yc) * zoom + y;

		x1 = x - xh * zoom;
		x2 = x + xh * zoom;
		y1 = y - yh * zoom;
		y2 = y + yh * zoom;

		return new Param(new double[param.IterationCounts.length], param.width, param.height, x1, x2, y1, y2,
				param.max_iteration, param.mode);
	}
	
	private Param increaseMaxIterParam(Param param, Double mult) {
		return new Param(new double[param.IterationCounts.length], param.width, param.height, param.dx1, param.dx2, param.dy1, param.dy2, (int) (param.max_iteration * mult), param.mode);
	}
	private Param swapModeParam(Param param) {
		return new Param(new double[param.IterationCounts.length], param.width, param.height, param.dx1, param.dx2, param.dy1, param.dy2, param.max_iteration, param.mode == 0 ? 1 : 0);
	}

}
