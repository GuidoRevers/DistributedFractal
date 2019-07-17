package com.app.DistributedFractal;

import java.util.LinkedList;
import java.util.concurrent.CompletableFuture;

public class LocalWork {
	public static void run(Param param) {
		CompletableFuture.runAsync(() -> {
			LinkedList<CompletableFuture<Void>> list = new LinkedList<CompletableFuture<Void>>();
//			try {
//				Thread.sleep(2000);
//			} catch (InterruptedException e1) {
//				// TODO Auto-generated catch block
//				e1.printStackTrace();
//			}
			System.out.println("Start.");
			long time1 = System.currentTimeMillis();
			for (int py = 0; py < param.height; py++) {
				list.add(line(param, py));
			}
			CompletableFuture<Void>[] test = list.toArray(new CompletableFuture[list.size()]);

			CompletableFuture.allOf(test).thenRun(() -> {

				System.out.println("Done!");
				System.out.println("Time: " + (System.currentTimeMillis() - time1));
			});
		});
	}
	
	private static CompletableFuture<Void> line(Param _param, int _py) {

		return CompletableFuture.completedFuture(_param).thenAcceptBothAsync(CompletableFuture.completedFuture(_py),
				(param, py) -> {
					double y0 = (param.dy2 - param.dy1) * (py / (double) param.height) + param.dy1;
					for (int px = 0; px < (double) param.width; px++) {
						double x0 = (param.dx2 - param.dx1) * (px / (double) param.width) + param.dx1;
						double iteration = Fractal.pixelContinuous(x0, y0, param.max_iteration);
						param.IterationCounts[param.width * py + px] = iteration;
					}
				});
	}
}
