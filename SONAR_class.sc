////////////////////////////////////////////////////////////////////////////////////////////////////
// SONAR - Sound Object Notation, Analysis and Resynthesis
SONAR {
	classvar <all, <>default, <>sonacounter = 0;
	var <id;
	var <>inputbus;
	var <>tempoclock;
	var <>quantization;
	var <>signalgating;

	//init values
	var <>pitchrange;
	var <>microtonality;
	var <>npartials;
	var <>onthreshold;
	var <>offthreshold;
	var <>maxloudness;
	var <>minsinepartialsloudness;
	var <>updatefreq;
	var <>minpitchdur;
	var	<>triggersensitivity;
	var <>eventclick;

	//event features
	var <>eventStarts;
	var <>eventDensityList;
	var <>sineslist;
	var <>pitchlist;
	var <>confidencelist;
	var <>loudnesslist;
	var <>crestlist;
	var <>skewnesslist;
	var <>flatnesslist;
	var <>centroidlist;
	var <>spreadlist;
	var <>kurtosislist;
	var <>deltatlist;
	var <>eventList;

	//
	var <>grid;
	var <>quantgrid;
	var <>quantizedDeltaTList;
	var <>checkloudness;

	//hidden variables
	var beatdur;
	var l;

	///////////////////////////////////////////////////////////////////////////////////////////////
	*new { arg inputbus, tempoclock, quantization;
		^super.new.init(inputbus, tempoclock, quantization);
	}
	///////////////////////////////////////////////////////////////////////////////////////////////
	init {arg initInputbus, initTempoclock = TempoClock.default, initQuantization, initSignalgating;
		'///////////////////////////////////////////////////////////////////////////////////////////////'.postln;
		'SONAR - Sound Object Notation and Analysis'.postln;
		//initializing outer parameters
		eventclick = 0;
		eventStarts= [];
		eventDensityList = [];
		sineslist= [];
		pitchlist= [];
		confidencelist= [];
		loudnesslist= [];
		crestlist= [];
		skewnesslist= [];
		flatnesslist= [];
		centroidlist= [];
		spreadlist= [];
		kurtosislist= [];
		deltatlist= [];
		eventList = [];
		inputbus = initInputbus;
		'inputbus: '.post; inputbus.postln;
		tempoclock = initTempoclock;
		'TempoClock: ';
		quantization = initQuantization;
		'quantization: '.post; quantization.postln;
		id = sonacounter;
		'SONA-ID: '.post; id.postln;
		sonacounter = sonacounter + 1;
		'sonacounter updated to: '.post; sonacounter.postln;
		//initializing optional parameters
		signalgating = 1;
		'signalgating: '.post; signalgating.postln; //implement turnoff!!
		microtonality = 2; // to be implemented later!
		'(microtonal) division of major second: '.post; microtonality.postln;
		npartials = 8;// to be implemented later!
		'number of partials to analyse: '.post; npartials.postln;
		onthreshold = -100;
		'gate on threshold: '.post; onthreshold.postln;
		offthreshold = -100;
		'gate off threshold: '.post; offthreshold.postln;
		maxloudness = 0.9;
		'maximum expected signal loudness: '.post; maxloudness.ampdb.post; ' dB'.postln;
		pitchrange = (16..112) ;
		'expected pitch range: from midi: '.post;
		pitchrange[0].post; ' to midi: '.post;pitchrange.reverse[0].postln;
		updatefreq = 50;
		'update frequency: '.post; updatefreq.post; ' Hz'.postln;
		minsinepartialsloudness = -40;
		'Minimum loudness for Sine partials to be detected: '.post; minsinepartialsloudness.postln;
		minpitchdur = 0.1;
		'Minimum pitch duration: '.post; (1000 * minpitchdur).post; 'msec'.postln;
		//initialize hidden parameters and local variables

		checkloudness = CheckLoudness(inputbus: inputbus);
		//this.checkloudness.noisefloor = -74;
		'CheckLoudness initialized'.postln;
		(fork {

			SynthDef("listen"++this.id.asString, {
				arg inputbus, updatefreq,triggersensitivity = 0,
				offthreshold = this.offthreshold, onthresold = this.onthreshold;
				var sig, res,out, gated, gating, imp, loudness,nov1, nov2, pitch, confidence,
				crest, spread, flatness, kurtosis, skewness, centroid,
				comp, sines, eventclick = this.eventclick;
				sig = In.ar(inputbus);
				imp = Impulse.ar(updatefreq);
				sines = FluidSineFeature.kr(sig, order: 1,numPeaks: 10, freqUnit: 1, windowSize: 4096)[1..8];
				gating = FluidAmpGate.ar(sig, rampUp:24, rampDown:2400,
					onThreshold: triggersensitivity, offThreshold: offthreshold,
					minSilenceLength:48, lookBack:48, highPassFreq:0);
				//increase contrast
				gated = (sig*gating);
				comp = Compander.ar(gated, gated, 0.5, 0.05, 0.1, 0.5, 0.01, 0.01).tanh;
				//get sharp attacks and some kind of activity density, retriggers in long notes
				nov1 = FluidAmpSlice.ar(sig,fastRampUp: 10,fastRampDown: 2400,slowRampUp: 4800,
					slowRampDown: 4800,onThreshold: triggersensitivity,offThreshold: 3,floor: -40,
					minSliceLength: this.minpitchdur*48000,highPassFreq: 20);
				Out.ar(0, nov1*eventclick);

				loudness = Changed.ar(Latch.ar(FluidLoudness.kr(sig, select: [\peak]), nov1));

				# pitch,confidence = FluidPitch.kr(sig, algorithm: 1, minFreq: this.pitchrange[0].midicps,
					maxFreq: this.pitchrange.reverse[0].midicps, windowSize: 2048);
				# crest, spread, flatness, kurtosis, skewness, centroid = FluidSpectralShape.kr(sig,select:
					[\crest, \spread, \flatness, \kurtosis, \skewness, \centroid]);
				SendReply.ar(nov1, "/sines", sines, replyID: this.id);
				SendReply.ar(nov1, "/pitch", pitch, replyID: this.id);
				SendReply.ar(nov1, "/confidence", confidence, replyID: this.id);
				SendReply.ar(nov1, "/loudness", loudness, replyID: this.id);
				SendReply.ar(nov1, "/crest", crest, replyID: this.id);
				SendReply.ar(nov1, "/spread", sines, replyID: this.id);
				SendReply.ar(nov1, "/centroid", centroid, replyID: this.id);
				SendReply.ar(nov1, "/flatness", flatness, replyID: this.id);
				SendReply.ar(nov1, "/kurtosis", kurtosis, replyID: this.id);
				SendReply.ar(nov1, "/skewness", skewness, replyID: this.id);
				SendTrig.ar(in: nov1, id: this.id, value: nov1);
			}).add;
		})
	}
	///////////////////////////////////////////////////////////////////////////////////////////////
	start {
		'starting live-transcription'.postln;
		this.listen;
	}
	///////////////////////////////////////////////////////////////////////////////////////////////
	stop {
		l.free
		// how to free all wire bufs when stopping???
	}
	///////////////////////////////////////////////////////////////////////////////////////////////
	listen {
		arg oscchannel = this.id, minpitchdur = this.minpitchdur;
		var onsets = [],activepitches = [];
		"calling the listen method".postln;
		l = Synth("listen"++this.id.asString, [\inputbus, this.inputbus, \updatefreq, this.updatefreq,
			\onThreshold: this.onthreshold, \offThreshold: this.offthreshold,
			\triggersensitivity, this.triggersensitivity, \eventclick, this.eventclick], addAction: \addToTail);

		OSCdef(\sines, {arg oscm;var rcvchannel = oscm[2]; if (rcvchannel == oscchannel,{
			this.sineslist = this.sineslist.add(oscm[3..(oscm.size-3)]);
		})},"/sines");

		OSCdef(\pitch, {arg oscm;var rcvchannel = oscm[2];	if (rcvchannel == oscchannel,{
			this.pitchlist = this.pitchlist.add(oscm[3]);
		})},"/pitch");

		OSCdef(\confidence, {arg oscm;var rcvchannel = oscm[2];	if (rcvchannel == oscchannel,{
			this.confidencelist = this.confidencelist.add(oscm[3]);
		})},"/confidence");

		OSCdef(\loudness, {arg oscm;var rcvchannel = oscm[2];	if (rcvchannel == oscchannel,{
			this.loudnesslist = this.loudnesslist.add(oscm[3]);
		})},"/loudness");

		OSCdef(\crest, {arg oscm;var rcvchannel = oscm[2];	if (rcvchannel == oscchannel,{
			this.crestlist = this.crestlist.add(oscm[3]);
		})},"/crest");

		OSCdef(\spread, {arg oscm;var rcvchannel = oscm[2];	if (rcvchannel == oscchannel,{
			this.spreadlist = this.spreadlist.add(oscm[3]);
		})},"/spread");

		OSCdef(\centroid, {arg oscm;var rcvchannel = oscm[2];	if (rcvchannel == oscchannel,{
			this.centroidlist = this.centroidlist.add(oscm[3]);
		})},"/centroid");

		OSCdef(\flatness, {arg oscm;var rcvchannel = oscm[2];	if (rcvchannel == oscchannel,{
			this.flatnesslist = this.flatnesslist.add(oscm[3]);
		})},"/flatness");

		OSCdef(\kurtosis, {arg oscm;var rcvchannel = oscm[2];	if (rcvchannel == oscchannel,{
			this.kurtosislist = this.kurtosislist.add(oscm[3]);
		})},"/kurtosis");

		OSCdef(\skewness, {arg oscm;var rcvchannel = oscm[2];	if (rcvchannel == oscchannel,{
			this.skewnesslist = this.skewnesslist.add(oscm[3]);
		})},"/skewness");

		OSCdef(\tr, {arg oscm;
			var rcvchannel = oscm[2];
			if (rcvchannel == oscchannel,{
				var oneMinDensity,tenSecDensity,oneSecDensity;
				var currentBeat = this.tempoclock.beats;
				var lastMin = this.eventStarts.copy.removeAllSuchThat({arg e; e > (currentBeat-60)});
				var lastTenSec = this.eventStarts.copy.removeAllSuchThat({arg e; e > (currentBeat-10)});
				var lastOneSec = this.eventStarts.copy.removeAllSuchThat({arg e; e > (currentBeat-1)});
				this.eventStarts =	this.eventStarts.add(currentBeat);
				oneMinDensity = lastTenSec.size/60;
				tenSecDensity = lastTenSec.size/10;
				oneSecDensity = lastTenSec.size;
				this.eventDensityList = this.eventDensityList.add(
					Dictionary.newFrom([\oneSecDensity, oneSecDensity,
						\tenSecDensity, tenSecDensity,
						\oneMinDensity, oneMinDensity
					]);
				);
				this.eventList = this.eventList.add(
					Dictionary.newFrom([
						\start, this.eventStarts.last,
						\pitch, this.pitchlist.last,
						\confidence, this.confidencelist.last,
						\loudness, this.loudnesslist.last,
						\crest, this.crestlist.last,
						\spread, this.spreadlist.last,
						\centroid, this.centroidlist.last,
						\flatness, this.flatnesslist.last,
						\kurtosis, this.kurtosislist.last,
						\skewness, this,skewnesslist.last,
						\densities, this.eventDensityList.last
					]);
			)})
		}, "/tr");



	}
	////////////////////////////////////////////////////////////////////////////////////////////////////
	//grid hässloich!!!!!
	//generate a numerical grid from gridstart to gridend and apply griddivisions
	setgrid {
		this.grid = this.tempoclock.beats.ceil.asInteger.collect({arg i; this.tempoclock.subdiv+i}).flatten
	}

	smallestdiff  {arg lst , e ;
		var diffs, i;
		diffs = (e - lst).abs;
		i = diffs.minIndex;
		^lst[i]; //huetchen fuer die methode!
	}
	////////////////////////////////////////////////////////////////////////////////////////////////////
	//Define a method that counts elements in an array
	counter {
		arg lst, elem;
		var count = 0;
		lst.do({arg item; if (item == elem,{count = count+1}) });
		^[elem, count];
	}


	////////////////////////////////////////////////////////////////////////////////////////////////////
	//Define a method that produces an histogram of an array

	histogram  {
		arg lst;
		var unique = lst.asSet.asArray.sort;
		var histo = [];
		unique.do({arg item ; histo = histo.add(this.counter(lst,item)); histo});
		//	'current histo: '.post; histo.postln;
		^histo;
	}

	////////////////////////////////////////////////////////////////////////////////////////////////////
	//Define a method that produces an histogram of the pitches in pitchevents


	measureNoisefloor {
		this.checkloudness.getnoisefloor;
		this.offthreshold = checkloudness.noisefloor + checkloudness.noisedist;
		'set off threshold to: '.post; this.offthreshold.post; 'dB'.postln;
	}

	measureMaxLoudness {
		fork {
			this.checkloudness.getmaxloudness;
			6.wait;
			this.maxloudness = this.checkloudness.maxloudness;
			'set max Loudness to: '.post; this.maxloudness.post; 'dB'.postln;
		}
	}

	measureMinLoudness {
		fork {
			this.checkloudness.getminloudness;
			6.wait;
			this.onthreshold = checkloudness.minloudness.sum/2;
			'set Onsetthreshold to: '.post; this.onthreshold.post; 'dB'.postln;
			this.triggersensitivity = (this.onthreshold.dbamp / this.maxloudness.dbamp / 10 + 0.05);
			'set triggersensitivity to: '.post; this.triggersensitivity.postln;
		}
	}

	setTriggerSensitivity {arg value;
		this.stop;
		this.triggersensitivity = value;
		this.start;
	}
	seteventclick {arg io;
		this.stop;
		this.eventclick = io;
		this.start;

	}
	//
	// quantizeDeltaTs {
	// 	var quantized;
	// 	this.setgrid;
	// 	quantized = this.genDeltatlist.collect({arg item;
	// 		this.smallestdiff(this.grid, item);
	// 	});
	// 	this.quantizedDeltaTList = quantized;
	// 	^quantized;
	// }
}


