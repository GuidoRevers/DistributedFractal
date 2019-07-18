package com.app.DistributedFractal;

import java.util.Arrays;

public class Fractal {
	public static int pixel(double x0, double y0, int max_iteration) {
		double x = 0d;
		double y = 0d;
		double xtemp = 0d;
		int iteration = 0;
		while (x * x + y * y <= 2 * 2 && iteration < max_iteration) {
			xtemp = x * x - y * y + x0;
			y = 2 * x * y + y0;
			x = xtemp;
			iteration = iteration + 1;
		}
		return iteration;
	}

	public static double pixelContinuous(double x0, double y0, int max_iteration) {
		double x = 0d;
		double y = 0d;
		double xtemp = 0d;
		double iteration = 0;
		while (x * x + y * y <= (1 << 16) && iteration < max_iteration) {
			xtemp = x * x - y * y + x0;
			y = 2 * x * y + y0;
			x = xtemp;
			iteration = iteration + 1;
		}

		// Used to avoid floating point issues with points inside the set.
		if (iteration < max_iteration) {
			// sqrt of inner term removed using log simplification rules.
			double log_zn = Math.log(x * x + y * y) / 2d;
			double nu = Math.log(log_zn / Math.log(2)) / Math.log(2);

			// Rearranging the potential function.
			// Dividing log_zn by log(2) instead of log(N = 1<<8)
			// because we want the entire palette to range from the
			// center to radius 2, NOT our bailout radius.
			iteration = iteration + 1 - nu;
		}
		return iteration;
	}


	public static double[] postHistogramHueLookup(Param param) {
		int[] NumIterationsPerPixel = new int[param.max_iteration + 1];

		// Histogram coloring

		// Step1
		for (int y = 0; y < param.height; y++) {
			for (int x = 0; x < param.width; x++) {
				int i = (int) param.IterationCounts[y * param.width + x];
				NumIterationsPerPixel[i]++;
			}
		}

		// Step 2
		// TODO: optimize possible total = param.width * param.height -
		// NumIterationsPerPixel[param.max_iteration];
		int total = 0;
		for (int i = 0; i < param.max_iteration; i++) {
			total += NumIterationsPerPixel[i];
		}

		// Step 3
		double h = 0;
		double[] hue = new double[param.max_iteration + 1];
		for (int i = 0; i < param.max_iteration; i++) {
			h += NumIterationsPerPixel[i];
			hue[i] = h / total;
		}
		hue[param.max_iteration] = h / total;
		
		return hue;
	}
	
	public static double[] postHistogramHueLookup_(Param param) {
		return postHistogramHueLookup_(param, new double[param.max_iteration + 1]);
	}
	
	public static double[] postHistogramHueLookup_(Param param, double[] output) {
		Arrays.fill(output,0);

		// Histogram coloring

		// Step1
		for (int y = 0; y < param.height; y++) {
			for (int x = 0; x < param.width; x++) {
				int i = (int) param.IterationCounts[y * param.width + x];
				output[i]++;
			}
		}

		// Step 2
		// TODO: optimize possible total = param.width * param.height -
		// NumIterationsPerPixel[param.max_iteration];
		int total = 0;
		for (int i = 0; i < param.max_iteration; i++) {
			total += output[i];
		}

		// Step 3
		double h = 0;
		for (int i = 0; i < param.max_iteration; i++) {
			h += output[i];
			output[i] = h / total;
		}
		output[param.max_iteration] = h / total;
		
		return output;
	}
}
