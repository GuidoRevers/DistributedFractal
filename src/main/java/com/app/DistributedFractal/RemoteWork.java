package com.app.DistributedFractal;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.java_websocket.WebSocket;
import org.java_websocket.exceptions.WebsocketNotConnectedException;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import processing.data.JSONArray;
import processing.data.JSONObject;

public class RemoteWork implements FractlWorker {
	public static final int MAX_JOBS_PER_WORKER = 16;

	final Lock lock = new ReentrantLock();
	final Condition notEmpty = lock.newCondition();

	private WebSocketServer server;
	private LinkedBlockingQueue<Job> jobs = new LinkedBlockingQueue<RemoteWork.Job>();
	private Hashtable<Long, Job> runningJobs = new Hashtable<Long, RemoteWork.Job>();
	private Hashtable<WebSocket, HashSet<Job>> worker = new Hashtable<WebSocket, HashSet<Job>>();
	private LinkedBlockingQueue<WebSocket> freeWorker = new LinkedBlockingQueue<WebSocket>();

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
				try {
					worker.put(conn, new HashSet<RemoteWork.Job>());
					freeWorker.add(conn);
					notEmpty.signalAll();
				} finally {
					lock.unlock();
				}
			}

			@Override
			public void onMessage(WebSocket conn, String message) {

				JSONObject data = JSONObject.parse(message);
				String type = data.getString("type");
				if ("result".equals(type)) {
					JSONObject job = data.getJSONObject("data");
					long id = Integer.parseInt(job.getString("id"));
					JSONArray result = job.getJSONArray("result");

					completeJob(conn, id, result);
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
				removeWorker(conn);
			}

		};
		server.start();

		Thread sender = new Thread() {
			@Override
			public void run() {
				try {
					while (!isInterrupted()) {
//						System.out.println(jobs.size() + " " + freeWorker.size());
						Job job = jobs.take();
						WebSocket ws = freeWorker.take();
						lock.lock();
						try {
							try {
								sendJob(ws, lineJob(job));
								runningJobs.put(job.id, job);
								HashSet<Job> wj = worker.get(ws);
								wj.add(job);
								if (wj.size() < MAX_JOBS_PER_WORKER) {
									freeWorker.offer(ws);
								}
							} catch (WebsocketNotConnectedException e) {
								//TODO: put job back? anything else?
								jobs.offer(job);
								e.printStackTrace();
							}
						} finally {
							lock.unlock();
						}
					}
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		};
		sender.start();
	}

	private void removeWorker(WebSocket ws) {
		lock.lock();
		try {
			HashSet<Job> wJobs = worker.remove(ws);
			jobs.addAll(wJobs);
			wJobs.clear();
			freeWorker.remove(ws);
		} finally {
			lock.unlock();
		}
	}

	private WebSocket get() throws InterruptedException {
		lock.lock();
		try {
			while (worker.size() == 0)
				notEmpty.await();
			return worker.keySet().iterator().next();
		} finally {
			lock.unlock();
		}
	}

	private void completeJob(WebSocket ws, long id, JSONArray result) {
		lock.lock();
		try {
			Job job = runningJobs.remove(id);
			if (worker.get(ws).size() == MAX_JOBS_PER_WORKER) {
				freeWorker.offer(ws);
			}
			boolean noError = worker.get(ws).remove(job);
			if (!noError)
				throw new RuntimeException();
			
			
			int pos = job.offset;
			for (int i = 0; i < job.length; i++) {
				job.param.IterationCounts[pos++] = result.getDouble(i);
			}
			job.complete(null);
		} finally {
			lock.unlock();
		}
		//		
	}

	public CompletableFuture<Void> run(Param param) {
		System.out.println("RemoteWorker.run(): param=" + param.toString());
		// TODO: supply parm async
		return CompletableFuture.runAsync(() -> {
			LinkedList<CompletableFuture<Void>> list = new LinkedList<CompletableFuture<Void>>();

			System.out.println("Start.");
			long time1 = System.currentTimeMillis();
			for (int py = 0; py < param.height; py++) {
				list.add(halfLine(param, py));
			}
//			for (int i = 0; i < param.width * param.height; i++) {
//				list.add(pixel(param, i));
//			}
			CompletableFuture<Void>[] test = list.toArray(new CompletableFuture[list.size()]);
			try {
				CompletableFuture.allOf(test).thenRun(() -> {

					System.out.println("Done!");
					System.out.println("Time: " + (System.currentTimeMillis() - time1));
				}).get();
			} catch (InterruptedException | ExecutionException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		});
	}
	private CompletableFuture<Void> pixel(Param _param, int p) {
		Job job = new Job(_param,p , 1);
		jobs.offer(job);
		return job;
	}
	
	private CompletableFuture<Void> line(Param _param, int _py) {
		Job job = new Job(_param, _py * _param.width, _param.width);
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

	private CompletableFuture<Void> halfLine(Param _param, int _py) {
		Job job = new Job(_param, _py * _param.width, _param.width / 2);
		jobs.offer(job);
		job = new Job(_param, _py * _param.width + _param.width / 2, _param.width / 2);
		jobs.offer(job);
		return job;
	}
	
	private CompletableFuture<Void> doubleLine(Param _param, int _py) {
		int remaining = _param.width * _param.height - _py * _param.width;
		int len = Math.min(_param.width * 2, remaining); 
		Job job = new Job(_param, _py * _param.width, len);
		jobs.offer(job);
		return job;
	}
	
	private void sendJob(WebSocket ws, JSONObject job) throws WebsocketNotConnectedException {
		JSONObject data = new JSONObject();
		data.setString("type", "job");
		data.setJSONObject("data", job);
		ws.send(data.toString());
	}

	private JSONObject lineJob(Job job) {
		JSONObject data = new JSONObject();
		data.setJSONObject("param", toJSON(job.param));
		data.setString("offset", String.valueOf(job.offset));
		data.setString("length", String.valueOf(job.length));
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
		private final int offset;
		private final int length;
		private final long id = idGen.getAndIncrement();

		public Job(Param param, int offset, int length) {
			this.param = param;
			this.offset = offset;
			this.length = length;
		}
	}
}
