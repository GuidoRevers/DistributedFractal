/**
 * 
 */
importScripts("/js/lib/decimal/decimal.js");

onmessage = function(e) {
	let job = e.data;
	postMessage([job,lineJob(job)]);
}

function lineJob(job) {
	console.log('start line: ' + job.line);
	let arr = [];
	let width = Number(job.param.width);
	let height = Number(job.param.height);
	let x1 = Number(job.param.x1);
	let x2 = Number(job.param.x2);
	let y1 = Number(job.param.y1);
	let y2 = Number(job.param.y2);
	let max_iteration = Number(job.param.max_iteration);
	let line = Number(job.line);
	let y0 = (y2 - y1) * (line / height) + y1;
	for (let px = 0; px < width; px++) {
		let x0 = (x2 - x1) * (px / width) + x1;
		let iteration = pixelContinuous(x0, y0, max_iteration);
		arr.push(String(iteration));
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