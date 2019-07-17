package com.app.DistributedFractal;

public class Param {

	public double[] IterationCounts;
	public int width;
	public int height;
	public double dx1;
	public double dx2;
	public double dy1;
	public double dy2;
	public int max_iteration;

	public Param(double[] pixels, int width, int height, double dx1, double dx2, double dy1, double dy2,
			int max_iteration) {
		this.IterationCounts = pixels;
		this.width = width;
		this.height = height;
		this.dx1 = dx1;
		this.dx2 = dx2;
		this.dy1 = dy1;
		this.dy2 = dy2;
		this.max_iteration = max_iteration;

	}

}
