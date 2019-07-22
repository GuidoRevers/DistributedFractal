/**
 * 
 */
"use strict";
console.log("scripts.js");

let numWorker = 0;
let maxWorkerDetected = window.navigator.hardwareConcurrency === undefined ? 8 : window.navigator.hardwareConcurrency; 
let maxWorker = 1;
let workers = [];
let jobs = [];
let reload = false;
let socket = null;

var myConsole = document.getElementById('myConsole');
myConsole.log = function (msg) {
	let arr = myConsole.value.split(/[\r\n]+/)
	if (arr.length > 20) {
		arr.shift();
	}
	arr.push(msg);
//    myConsole.value += '\r\n' + msg;
	myConsole.value = arr.join('\r\n');
    myConsole.scrollTop = myConsole.scrollHeight;
}

var slider = document.getElementById("myRange");
slider.max = maxWorkerDetected;
slider.value = maxWorker;
var output = document.getElementById("cores");
output.innerHTML = slider.value;

slider.oninput = function() {
  output.innerHTML = this.value;
  maxWorker = Number(this.value);
}



function connect() {
	// Create WebSocket connection.
	if (socket) {
		socket.close();
	}
	socket = new WebSocket('ws://'+ location.host + ':8080');
	
	// Connection opened
	socket.addEventListener('open', function (event) {
		myConsole.log("connected");
	    if (reload) {
	    	location.reload(); 
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

function genWorker() {
	myConsole.log("gen Worker");
	let newWorker = new Worker('js/worker.js');
	newWorker.onmessage = (e) => {
		if (e.data.length == 2) {
			let [job, arr] = e.data;		
			finishJob(newWorker, job, arr)
		} else {
			myConsole.log(e.data[0]);
		}
	};
	return newWorker;
}

function offerJob(job) {		
	let worker = workers.shift();
//	myConsole.log(maxWorker);
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
	if (numWorker > maxWorker) {
		console.log("remove worker");
		numWorker--;
	} else {
		let newJob = jobs.shift();
		if (newJob === undefined) {
			workers.push(worker);
		} else {
			worker.postMessage(newJob);
		}
	}
	sendResult(job, result);
	showResult(job, result);
}

var canvas = getCanvas();
var ctx = canvas.getContext('2d');
var imageData = ctx.createImageData(canvas.width,canvas.height);

function showResult(job, result) {
	
        
//        var imageData = ctx.getImageData(0,Number(job.line),Number(job.param.width),1);
    let resWidth = Number(job.param.width);
    let resHeight = Number(job.param.height);
    let resOffset = Number(job.offset);
    let resLength = Number(job.length);
    
    let resSize = (resWidth * resHeight);
    let dSize = (canvas.width * canvas.height);
    
    let dOffset = Math.floor(resOffset  * dSize / resSize) - 1;
    let dLength = Math.ceil(resLength * dSize / resSize) + 1;
//    let dWidth = Math.ceil(Number(job.length) * canvas.width / resWidth);
    
    let data = imageData.data;
    let max_iteration = Number(job.param.max_iteration);
    
//    let count = 0;
//        count / data.length * resWidth

    for (var i = 0; i < resLength; i++) {        	
//            var avg = (data[i] + data[i + 1] + data[i + 2]) / 3;
//        	console.log(count / canvas.width * resWidth);
    	
    	var avg = result[i] / max_iteration * 255;
    	
    	let x = (resOffset + i) % resWidth;
    	let y = Math.floor((resOffset + i) / resWidth);  
    	let cX = x   * canvas.width / resWidth;
    	let cY =  Math.floor(y    * canvas.height / resHeight);
    	let pos = Math.floor(cY * canvas.width + cX);
//    	pos = Math.floor(pos / 4) * 4 * 4;
    	pos *=4;
//    	console.log(x + " " + y + " <=> " + cX + " " + cY + " = " + pos); 
//    	console.log(pos);
    	
//    	console.log(avg);
//    	var avg = result[count  * resSize / dSize] / max_iteration * 255;
        data[pos ]     = avg; // red
        data[pos + 1] = avg; // green
        data[pos + 2] = avg; // blue
        data[pos + 3] = 255; // alpha
//        count++;
      }        

    putImageData(imageData, dOffset, dLength);
}

function putImageDataPixel(imageData, offset, length) {
	let count = 0;
	let x = offset % canvas.width;
	for(let y = Math.floor(offset / canvas.width); y < canvas.height; y++) {
		for (; x < canvas.width; x++) {
			ctx.putImageData(imageData, 0, 0, x, y,1 , 1)
			count++;
			if (count >= length) {
				return;
			}
		}
		x = 0;		
	}
}

function putImageData(imageData, offset, length) {
	let x = offset % canvas.width;
	for(let y = Math.floor(offset / canvas.width); y < canvas.height; y++) {
		let width = canvas.width - x;
		length -= width;
		if (length <= 0) {
			width = width + length;
		}
		ctx.putImageData(imageData, 0, 0, x, y,width , 1)
		if (length <= 0) {
			return;
		}
		x = 0;		
	}
}

function getCanvas() {
	return document.getElementById('canvas');
}


function printJobs() {
	window.setTimeout(() => {
		console.log(jobs.length + " "  + workers.length)
		printJobs();
	}, 500);
}
//printJobs();