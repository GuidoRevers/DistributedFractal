package com.app.DistributedFractal;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.stream.Collectors;

import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;

public class HttpServer {

	public HttpServer(int port) throws IOException {

		try (InputStream stream = HttpServer.class.getResourceAsStream("index.html");
				InputStream stream_script = HttpServer.class.getResourceAsStream("scripts.js");
				InputStream stream_worker = HttpServer.class.getResourceAsStream("worker.js");
				InputStream stream_decimal = HttpServer.class.getResourceAsStream("js/lib/decimal/decimal.js");
				) {			
			String index = inToString(stream, "UTF-8");
			String scripts = inToString(stream_script, "UTF-8");
			String worker = inToString(stream_worker, "UTF-8");
			String decimal = inToString(stream_decimal, "UTF-8");
			Undertow server = Undertow.builder().addHttpListener(port, InetAddress.getLocalHost().getHostAddress()).setHandler(new HttpHandler() {
				@Override
				public void handleRequest(final HttpServerExchange exchange) throws Exception {

					String path = exchange.getRequestPath();
					if ("/".equals(path)) {
						exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/html");
						exchange.getResponseSender().send(index, Charset.forName("UTF-8"));
					} else if ("/js/scripts.js".equals(path)) {
							exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/html");
							exchange.getResponseSender().send(scripts, Charset.forName("UTF-8"));
					} else if ("/js/worker.js".equals(path)) {
						exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/javascript");
						exchange.getResponseSender().send(worker, Charset.forName("UTF-8"));
					} else if ("/js/lib/decimal/decimal.js".equals(path)) {
						exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/javascript");
						exchange.getResponseSender().send(decimal, Charset.forName("UTF-8"));
					} else if ("/saved.png".equals(path)) {
							exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "image/png");
							App.save();
							exchange.getResponseSender().send(ByteBuffer.wrap(Files.readAllBytes(Paths.get("saved.png"))));
					} else {
						exchange.setStatusCode(404);
						exchange.getResponseSender().send("not Found");
					}
				}
			}).build();
			server.start();
		}
	}

	private String inToString(InputStream inputStream, String charsetName) throws UnsupportedEncodingException {
		return new BufferedReader(new InputStreamReader(inputStream, charsetName)).lines().parallel()
				.collect(Collectors.joining("\n"));
	}

}
