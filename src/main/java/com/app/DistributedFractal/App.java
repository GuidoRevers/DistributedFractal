package com.app.DistributedFractal;

import java.awt.Color;
import java.util.LinkedList;
import java.util.concurrent.CompletableFuture;

import javax.servlet.http.HttpSessionListener;
import javax.swing.JColorChooser;

import org.apache.poi.sl.draw.DrawPaint;

import processing.core.PApplet;

/**
 * Hello world!
 *
 */
public class App extends PApplet {
	final static int WIDTH = 800;
	final static int HEIGHT = 600;
	final static double[] IterationCounts = new double[WIDTH * HEIGHT];
	final static Param param = new Param(IterationCounts, WIDTH, HEIGHT, -2.5d, 1d, -1d, 1d, 1000);
	Server server = new Server();
	// method used only for setting the size of the window
	public void settings() {
		
		size(WIDTH, HEIGHT);
	}

	// identical use to setup in Processing IDE except for size()
	public void setup() {
		frameRate(24);
		loadPixels();
		
//		LocalWork.run(param);
		new RemoteWork().run(param);
	}
	
	static double[] hue = new double[param.max_iteration + 1];
	
	public void draw() {
//		loadPixels();
		
		
		
		double[] table = Fractal.postHistogramHueLookup_(param, hue);
		
		double iterations;
		float color;
		for (int i = 0; i < WIDTH * HEIGHT; i++) {
			iterations = param.IterationCounts[i];
			color = lerp((float)table[(int) Math.floor(iterations)],
					(float)table[(int)Math.ceil(iterations)],
					(float)(iterations %1));
			Color c = DrawPaint.HSL2RGB(241d, 100d, iterations < param.max_iteration ? color * 100d : 0, 1d);
//			Color c = HSLColor.toRGB(241, 100, (float) (iterations < param.max_iteration ? color * 100f : 0));
			pixels[i] = c.getRGB();
		}
		
		
		
		
		updatePixels();

	}

	public static void main(String[] args) {
		PApplet.main("com.app.DistributedFractal.App");

	}


}
