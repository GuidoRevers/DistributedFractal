/**
 * 
 */
self.importScripts("/js/lib/decimal/decimal.js");
//Decimal.set({ precision: 5, rounding: 4 });

onmessage = function(e) {
	let job = e.data;
	postMessage(["Job start: " + job.id]);
	let ms = new Date().getTime();
	let result = lineJob(job);
	job.time =  new Date().getTime() - ms;
	postMessage([job,result]);
	postMessage(["Job end: " + job.id + " Time:" + job.time + "ms"]);
}


function lineJob(job) {
//	console.log('start line: ' + job.line);
	let arr = [];
	let width = Number(job.param.width);
	let height = Number(job.param.height);
	let x1 = Number(job.param.x1);
	let x2 = Number(job.param.x2);
	let y1 = Number(job.param.y1);
	let y2 = Number(job.param.y2);
	let max_iteration = Number(job.param.max_iteration);
	let offset = Number(job.offset);
	let length = Number(job.length);
	
	let px = offset % width;
	for (let py = Math.floor(offset / width) ; py < height; py++) {
		let y0 = (y2 - y1) * (py / height) + y1;
		for (; px < width; px++) {
			let x0 = (x2 - x1) * (px / width) + x1;
			let iteration = pixelContinuous(x0, y0, max_iteration);
			arr.push(String(iteration));
			if (arr.length >= length) return arr;
		}
		px = 0;
	}
	return arr;
}

function bigLineJob(job) {
//	console.log('start line: ' + job.line);	
	let arr = [];
	let width = Number(job.param.width);
	let height = Number(job.param.height);
	let x1 = new Decimal(job.param.x1);
	let x2 = new Decimal(job.param.x2);
	let y1 = new Decimal(job.param.y1);
	let y2 = new Decimal(job.param.y2);
	let max_iteration = Number(job.param.max_iteration);
	let offset = Number(job.offset);
	let length = Number(job.length);
	
	let px = offset % width;
	for (let py = Math.floor(offset / width) ; py < height; py++) {
		let y0 = y2.minus(y1).times(py / height).plus(y1);
		for (; px < width; px++) {
			let x0 = x2.minus(x1).times(px / width).plus(x1);
			let iteration = bigPixelContinuous(x0, y0, max_iteration);
			arr.push(String(iteration));
			if (arr.length >= length) return arr;
		}
		px = 0;
	}
	return arr;
}

function pixelContinuous(x0, y0, max_iteration) {
	var x = 0.0;
	var y = 0.0;
	var xtemp = 0.0;
	var iteration = 0.0;
	while (x * x + y * y <= (1 << 16) && iteration < max_iteration) {
		xtemp = x * x - y * y + x0;
		y = 2 * x * y + y0;
		x = xtemp;
		iteration = iteration + 1;
	}
	
	// Used to avoid floating point issues with points inside the set.
	if (iteration < max_iteration) {
		// sqrt of inner term removed using log simplification rules.
		let log_zn = Math.log(x * x + y * y) / 2.0;
		let nu = Math.log(log_zn / Math.log(2)) / Math.log(2);

		// Rearranging the potential function.
		// Dividing log_zn by log(2) instead of log(N = 1<<8)
		// because we want the entire palette to range from the
		// center to radius 2, NOT our bailout radius.
		iteration = iteration + 1 - nu;
	}
	
	return iteration;
}

function bigPixelContinuous(x0, y0, max_iteration) {
	var x = new Decimal(0.0);
	var y = new Decimal(0.0);
	var xtemp;
	var iteration = 0.0;
	var limit = (1 << 16);
	var xx = x.times(x);
	var yy = y.times(y);
	while (xx.plus(yy) <= limit && iteration < max_iteration) {
		xtemp = xx.minus(yy).plus(x0);
		y = x.times(y).times(2).plus(y0);
		x = xtemp;
		iteration = iteration + 1;
		
		xx = x.times(x);
		yy = y.times(y);
	}
	
	// Used to avoid floating point issues with points inside the set.
	if (iteration < max_iteration) {
		// sqrt of inner term removed using log simplification rules.
		let log_zn = Decimal.log(xx.plus(yy).dividedBy(2.0));
		let nu = Decimal.log(log_zn.dividedBy(Decimal.log(2))).dividedBy(Decimal.log(2));

		// Rearranging the potential function.
		// Dividing log_zn by log(2) instead of log(N = 1<<8)
		// because we want the entire palette to range from the
		// center to radius 2, NOT our bailout radius.
		iteration = iteration + 1 - nu;
	}
	
	return iteration;
}