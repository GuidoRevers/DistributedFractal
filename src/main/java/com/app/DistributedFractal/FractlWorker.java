package com.app.DistributedFractal;

import java.util.concurrent.CompletableFuture;

public interface FractlWorker {
	CompletableFuture<Void> run(Param param);
}
