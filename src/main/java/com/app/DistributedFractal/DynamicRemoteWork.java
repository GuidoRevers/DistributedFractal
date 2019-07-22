package com.app.DistributedFractal;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.sound.midi.SysexMessage;

import org.java_websocket.WebSocket;
import org.java_websocket.exceptions.WebsocketNotConnectedException;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import com.sun.xml.internal.ws.util.CompletedFuture;

import processing.data.JSONArray;
import processing.data.JSONObject;

public class DynamicRemoteWork implements FractlWorker {
	public static final int MAX_JOBS_PER_WORKER = 16;

	final Lock lock = new ReentrantLock();
	final Condition notEmpty = lock.newCondition();

	private WebSocketServer server;
	private LinkedBlockingQueue<Job> jobs = new LinkedBlockingQueue<DynamicRemoteWork.Job>();
	private Hashtable<Long, Job> runningJobs = new Hashtable<Long, DynamicRemoteWork.Job>();
	private Hashtable<WebSocket, Worker> connectedWorker = new Hashtable<WebSocket, DynamicRemoteWork.Worker>();
	private LinkedBlockingQueue<Worker> freeWorker = new LinkedBlockingQueue<Worker>();

	public DynamicRemoteWork() {
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
					Worker newWorker = new Worker(conn);
					connectedWorker.put(conn, newWorker);
					freeWorker.add(newWorker);
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
						Worker w = freeWorker.take();
						
						double timePerPixel = w.getTimePerPixel(job.param);
						int pixel = (int) Math.max(1,250 / timePerPixel); 
						Job temp = job.splitOf(pixel);
						if (temp != null) {
							jobs.add(job);
							job = temp;	
						}
						
						
						
						lock.lock();
						try {
							try {
								
								job.setStart(System.currentTimeMillis());
								sendJob(w, toJSON(job));
								runningJobs.put(job.id, job);
								w.add(job);
								
								if (!w.isFull()) {
									freeWorker.offer(w);
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
			Worker worker = connectedWorker.remove(ws);
			worker.clear();
			freeWorker.remove(ws);
		} finally {
			lock.unlock();
		}
	}

	private void completeJob(WebSocket ws, long id, JSONArray result) {
		lock.lock();
		try {
			Worker worker = connectedWorker.get(ws);
			Job job = runningJobs.remove(id);
			worker.setTime(job, job.getTime(System.currentTimeMillis()));
			if (worker.queueSize() == MAX_JOBS_PER_WORKER) {
				freeWorker.offer(worker);
			}
			boolean noError = worker.remove(job);
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
			Job job = new Job(param,0 , param.width * param.height);
			jobs.offer(job);
		});
	}
	
	private void sendJob(Worker w, JSONObject job) throws WebsocketNotConnectedException {
		JSONObject data = new JSONObject();
		data.setString("type", "job");
		data.setJSONObject("data", job);
		w.send(data.toString());
	}

	private JSONObject toJSON(Job job) {
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
		jObj.setString("mode", String.valueOf(param.mode));
		return jObj;
	}

	private final static AtomicLong idGen = new AtomicLong();

	class Job extends CompletableFuture<Void> {

		private final Param param;
		private int offset;
		private int length;
		private final long id = idGen.getAndIncrement();
		private long start;

		public Job(Param param, int offset, int length) {
			this.param = param;
			this.offset = offset;
			this.length = length;
		}
		
		public Job splitOf(int length) {
			if (this.length > length) {
				this.length = this.length - length;	
				return new Job(param, this.offset + this.length, length);
			} else {
				return null;
			}
		}

		public void setStart(long start) {
			this.start = start;
		}
		
		public long getTime(long end) {
			return end - start;
		}
	}
	
	class Worker {
		final WebSocket ws;
		final HashSet<DynamicRemoteWork.Job> working = new HashSet<DynamicRemoteWork.Job>();
		
		//TODO: memory leak: they never get removed
		final Hashtable<Param, Double> times = new Hashtable<Param, Double>();
		
		public Worker(WebSocket ws) {
			super();
			this.ws = ws;
		}
		
		public void setTime(Job job, long l) {
			times.put(job.param, l/(double)job.length);
		}
		
		public double getTimePerPixel(Param param) {
			Double time = times.get(param);
			return time == null ? Double.MAX_VALUE : time;
		}
		
		public void clear() {
			jobs.addAll(working);
			working.clear();			
		}
		public boolean remove(Job job) {
			return working.remove(job);			
		}
		public int queueSize() {
			return working.size();
		}
		public boolean isFull() {
			return working.size() >= MAX_JOBS_PER_WORKER;
		}
		public void add(Job job) {
			working.add(job);		
		}
		public void send(String string) {
			ws.send(string);			
		}
	}
}
