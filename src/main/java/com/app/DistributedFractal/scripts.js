/**
 * 
 */
"use strict";
console.log("scripts.js");

let numWorker = 0;
let maxWorker = 8;
let workers = [];
let jobs = [];
let reload = false;
let socket = null;

function connect() {
	// Create WebSocket connection.
	socket = new WebSocket('ws://'+ location.host + ':8080');
	
	// Connection opened
	socket.addEventListener('open', function (event) {
	    if (reload) {
//	    	location.reload(); 
	    }
	});
	
	// Listen for messages
	socket.addEventListener('message', function (event) {
	// console.log('Message from server ', event.data);
	    let data = JSON.parse(event.data);
	    let type = data.type;
	    if (type == 'job') {
	    	let job = data.data;
	    	offerJob(job);
	    }
	});
	
	// Listen for messages
	socket.addEventListener('close', function (event) {
	    console.log('Close ', event.data);
	    reconnectReload();
	});
	
	//Listen for messages
	socket.addEventListener('error', function (error) {
	    console.log('error ', error);
	    reconnectReload();
	});
}
connect();
let reconnectTimeout = null;
function reconnectReload() {
	jobs.length = 0;
	
	if (reconnectTimeout) {
		clearTimeout(reconnectTimeout);
		reconnectTimeout=null;
	}
	reload = true;
	reconnectTimeout = window.setTimeout(() => {
		connect();		
	}, 3000);
	
}

function sendResult(job, result) {
	let data = {
		'type' : 'result',
		'data': {
			'id' : job.id,
			'result' : result
		}
	}
	socket.send(JSON.stringify(data));
}

function initWorker() {
	workers = [];
	for (let i = 0; i < NUM_WORKER; i++) {
		let newWorker = new Worker('js/worker.js');
		newWorker.id = i;
		newWorker.onmessage = (e) => {
			let [job, arr] = e.data;
			finishJob(newWorker, job, arr)
// sendResult(job, arr);
// console.log("origin: " + newWorker.id);
		};
		workers.push(newWorker);
	}
}

function genWorker() {
	let newWorker = new Worker('js/worker.js');
	newWorker.onmessage = (e) => {
		let [job, arr] = e.data;
		finishJob(newWorker, job, arr)		
	};
	return newWorker;
}

function offerJob(job) {	
	let worker = workers.shift();
	if (worker === undefined && numWorker < maxWorker) {
		worker = genWorker();
		numWorker++;
	}
	if (worker === undefined) {
		jobs.push(job);
	} else {
		worker.postMessage(job);
	}
}

function finishJob(worker, job, result) {
	if (socket.readyState !== socket.OPEN){
		workers.push(worker);
		return;
	}
	sendResult(job, result);
	let newJob = jobs.shift();
	if (newJob === undefined) {
		if (numWorker > maxWorker) {
			numworker--;
		} else {
			workers.push(worker);
		}
	} else {
		worker.postMessage(newJob);
	}
	showResult(job, result);
}

function showResult(job, result) {
	let canvas = getCanvas();
	
	if(canvas.getContext){
        var ctx = canvas.getContext('2d');
//        var imageData = ctx.getImageData(0,Number(job.line),Number(job.param.width),1);
        var imageData = ctx.createImageData(canvas.width,1);
        let data = imageData.data;
        let max_iteration = Number(job.param.max_iteration);
        let resWidth = result.length;
        let resHeight = Number(job.param.height);
        let resLine = Number(job.line);
        let count = 0;
//        count / data.length * resWidth
        
        for (var i = 0; i < data.length; i += 4) {        	
//            var avg = (data[i] + data[i + 1] + data[i + 2]) / 3;
//        	console.log(count / canvas.width * resWidth);
        	var avg = result[count * resWidth / canvas.width ] / max_iteration * 255;
            data[i]     = avg; // red
            data[i + 1] = avg; // green
            data[i + 2] = avg; // blue
            data[i + 3] = 255; // alpha
            count++;
          }        
    
        ctx.putImageData(imageData, 0, Math.floor(resLine * canvas.height / resHeight ));
//        ctx.putImageData(imageData, 0, 150);
    }
	
}

function getCanvas() {
	return document.getElementById('canvas');
}
