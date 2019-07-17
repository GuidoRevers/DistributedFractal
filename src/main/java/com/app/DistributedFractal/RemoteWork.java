package com.app.DistributedFractal;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.sound.midi.SysexMessage;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import processing.data.JSONArray;
import processing.data.JSONObject;

public class RemoteWork {
	final Lock lock = new ReentrantLock();
	final Condition notEmpty = lock.newCondition();

	private WebSocketServer server;
	private LinkedBlockingQueue<Job> jobs = new LinkedBlockingQueue<RemoteWork.Job>();
	private Hashtable<Long, Job> working = new Hashtable<Long, RemoteWork.Job>();

	public RemoteWork() {
		try {
			new HttpServer(80);
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		InetSocketAddress isa = new InetSocketAddress(8080);
		server = new WebSocketServer(isa) {

			@Override
			public void onStart() {
				System.out.println("Start");

			}

			@Override
			public void onOpen(WebSocket conn, ClientHandshake handshake) {
				System.out.println("Open");
				lock.lock();
				notEmpty.signalAll();
				lock.unlock();
			}

			@Override
			public void onMessage(WebSocket conn, String message) {

				JSONObject data = JSONObject.parse(message);
				String type = data.getString("type");
				if ("result".equals(type)) {
					JSONObject job = data.getJSONObject("data");
					long id = Integer.parseInt(job.getString("id"));
					JSONArray result = job.getJSONArray("result");
					completeJob(id, result);					
				}

			}

			@Override
			public void onError(WebSocket conn, Exception ex) {
				System.out.println("error: ");
				ex.printStackTrace();

			}

			@Override
			public void onClose(WebSocket conn, int code, String reason, boolean remote) {
				System.out.println("close");

			}

		};
		server.start();

		Thread sender = new Thread() {
			@Override
			public void run() {
				try {
					while (!isInterrupted()) {
						Job job = jobs.take();

						WebSocket ws = get();

						sendJob(ws, lineJob(job));

						working.put(job.id, job);
					}
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		};
		sender.start();
	}

	private void completeJob(long id, JSONArray result) {
		Job job = working.get(id);
		int pos = job.line * job.param.width;
		for (int i = 0; i < result.size(); i++) {
			job.param.IterationCounts[pos++] = result.getDouble(i);
		}
		job.complete(null);
	}
	
	public void run(Param param) {
		CompletableFuture.runAsync(() -> {
			LinkedList<CompletableFuture<Void>> list = new LinkedList<CompletableFuture<Void>>();

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

	private CompletableFuture<Void> line(Param _param, int _py) {
		Job job = new Job(_param, _py);
		jobs.offer(job);
		return job;

//		return CompletableFuture.completedFuture(_param).thenAcceptBothAsync(CompletableFuture.completedFuture(_py),
//				(param, py) -> {
//					double y0 = (param.dy2 - param.dy1) * (py / (double) param.height) + param.dy1;
//					for (int px = 0; px < (double) param.width; px++) {
//						double x0 = (param.dx2 - param.dx1) * (px / (double) param.width) + param.dx1;
//						double iteration = Fractal.pixelContinuous(x0, y0, param.max_iteration);
//						param.IterationCounts[param.width * py + px] = iteration;
//					}
//				});
	}

	public WebSocket get() throws InterruptedException {
		lock.lock();
		try {
			while (server.getConnections().size() == 0)
				notEmpty.await();
			return server.getConnections().iterator().next();
		} finally {
			lock.unlock();
		}
	}

	private void sendJob(WebSocket ws, JSONObject job) {
		JSONObject data = new JSONObject();
		data.setString("type", "job");
		data.setJSONObject("data", job);
		ws.send(data.toString());
	}

	private JSONObject lineJob(Job job) {
		JSONObject data = new JSONObject(); 
		data.setJSONObject("param", toJSON(job.param));		
		data.setString("line", String.valueOf(job.line));
		data.setString("id", String.valueOf(job.id));
		return data;
	}

	private JSONObject toJSON(Param param) {
		JSONObject jObj = new JSONObject();
		jObj.setString("x1", String.valueOf(param.dx1));
		jObj.setString("x2", String.valueOf(param.dx2));
		jObj.setString("y1", String.valueOf(param.dy1));
		jObj.setString("y2", String.valueOf(param.dy2));
		jObj.setString("width", String.valueOf(param.width));
		jObj.setString("height", String.valueOf(param.height));
		jObj.setString("max_iteration", String.valueOf(param.max_iteration));
		return jObj;
	}

	private final static AtomicLong idGen = new AtomicLong(); 
	class Job extends CompletableFuture<Void> {
		
		private final Param param;
		private final int line;
		private final long id = idGen.getAndIncrement(); 
		public Job(Param param, int line) {
			this.param = param;
			this.line = line;
		}
	}
}
