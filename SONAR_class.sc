///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// SONAR - Sound Object Notation, Analysis and Realisation
// by Sem R A Wendt
// www.16161d.net
// 2023-2024
SONAR {
	classvar <all, <>default, <>sonacounter = 0;
	var <id;
	var <inputbus;
	var <tempoclock;
	var <quantization;
	var <signalgating;

	//init values
	var <>pitchrange;
	var <>microtonality;
	var	<>onsetSensitivity;
	var <>eventclick;
	var <>metric;

	//input features
	var <maxLoudness;
	var <minLoudness;
	var <noiseFloor;

	//event features
	var <>eventList;
	var <>eventDensityList;
	var <>featureLists;
	var <>featureDict;
	var <>eventclickChannel;

	//
	var <grid;
	var <quantgrid;
	var <quantizedDeltaTList;
	var <checkloudness;

	//hidden variables

	var f;
	var o;

	///////////////////////////////////////////////////////////////////////////////////////////////
	*new { arg inputbus, tempoclock, quantization, pitchrange, triggerSensitivity;
		^super.new.init(inputbus, tempoclock, quantization, pitchrange);
	}
	///////////////////////////////////////////////////////////////////////////////////////////////
	init {arg initInputbus, initTempoclock = TempoClock.new, initQuantization, initPitchrange = (16..112),
		initOnsetSensitivity = 0.5;
		'///////////////////////////////////////////////////////////////////////////////////////////////'.postln;
		'SONAR - Sound Object Notation and Analysis'.postln;
		//initializing outer parameters
		onsetSensitivity = initOnsetSensitivity;
		eventclick = 0;
		eventList= [];
		eventclickChannel = 0;
		eventDensityList = [];
		metric = 9;
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
		//values for Rumberger Insert Mic
		//min -30dB
		//max -6dB



		maxLoudness = -6;
		'maximum expected signal loudness: '.post; maxLoudness.post; ' dB'.postln;
		minLoudness = -30;
		'minimum expected signal loudness: '.post; minLoudness.post; ' dB'.postln;
		noiseFloor = -72;
		'noiseFloor expected: '.post; noiseFloor.post; ' dB'.postln;
		pitchrange = initPitchrange ;
		'expected pitch range: from midi: '.post;
		pitchrange[0].post; ' to midi: '.post;pitchrange.reverse[0].postln;


		/*	checkloudness = CheckLoudness(inputbus: inputbus);
		'CheckLoudness initialized'.postln;*/
		//generate a control bus for triggering new events
		Bus.new('control', 1000+this.id, 1, Server.default);

		SynthDef("getOnsets"++this.id.asString, {
			arg inputbus,
			onsetSensitivity = this.onsetSensitivity,
			eventclick = this.eventclick,
			eventclickChannel=this.eventclickChannel,
			metric = this.metric,
			minSliceLength = 20;

			var sig,  trig,loudness, newOnset;
			sig = In.ar(inputbus);
			//512 samples ~ 10 msec precision => hopSize = 256 Samples ~ 5msec
			//for minSliceLength = 100 msec = 20 x hopSize
			//			newOnset = FluidOnsetSlice.ar(sig, metric: metric, threshold: onsetSensitivity,	minSliceLength: minSliceLength, windowSize: 512);
			newOnset = FluidNoveltySlice.ar(sig, algorithm: metric, threshold: onsetSensitivity,
				minSliceLength: minSliceLength, windowSize: 512);

			loudness = A2K.kr(sig).abs.lagud(0.005, 0.1).ampdb;
			Out.ar(eventclickChannel, newOnset*eventclick);
			Out.kr(1000+this.id, A2K.kr(newOnset));
			SendReply.ar(newOnset, '/newOnset',loudness, replyID: this.id);
		}).add;

		SynthDef("getFeatures"++this.id.asString, {
			arg inputbus;
			var sig, newEvent,pitch, confidence,crest, flatness, kurtosis, centroid,features;
			sig = DelayL.ar(In.ar(inputbus), 1024/48000, 512/48000);
			newEvent = In.kr(1000+this.id);
			# pitch,confidence = FluidPitch.kr(sig, algorithm: 0, minFreq: this.pitchrange[0].midicps,
				maxFreq: this.pitchrange.last.midicps, windowSize: 1024);
			# crest, flatness, kurtosis, centroid = FluidSpectralShape.kr(sig,select:
				[\crest, \flatness, \kurtosis, \centroid]);
			features = [pitch,confidence,crest,flatness,kurtosis,centroid];
			SendReply.kr(newEvent, '/features',features, replyID: this.id);
		}).add;

	}
	startClock{this.tempoclock.play}
	///////////////////////////////////////////////////////////////////////////////////////////////
	stop {
		o.free; f.free;
	}
	///////////////////////////////////////////////////////////////////////////////////////////////
	getOnsetsOnly {
		arg oscchannel = this.id;
		var onsets = [],activepitches = [];
		"calling the getOnset method".postln;
		o = Synth("getOnsets"++this.id.asString, [\inputbus, this.inputbus,
			\onsetSensitivity, this.onsetSensitivity, \eventclick, this.eventclick,
			\eventclickChannel, this.eventclickChannel, \metric, this.metric], addAction: \addToTail);
		OSCdef("onsets"++this.id.asString, {arg oscm;var rcvchannel = oscm[2];
			if (rcvchannel == oscchannel,{
				var event = oscm.drop(3).addFirst(this.tempoclock.beats);
				this.eventList = this.eventList.add(event);
		})},'/newOnset');
		this.startClock;
	}

	setOnsetMetric{arg metric = this.metric;
		this.stop;
		this.metric = metric;
	}
	setOnsetSensitivity{arg sensitivity = this.onsetSensitivity;
		this.stop;
		this.onsetSensitivity = sensitivity;
	}
	setEventClick{arg io = this.eventclick;
		this.stop;
		this.eventclick = io;
	}
	setEventClickChannel{arg chan = this.eventclickChannel;
		this.stop;
		this.eventclickChannel = chan;

	}
	///////////////////////////////////////////////////////////////////////////////////////////////
	getFeatures {
		arg oscchannel = this.id;
		"calling the getFeatures method".postln;
		f = Synth("getFeatures"++this.id.asString, [\inputbus, this.inputbus], addAction: \addToTail);
		OSCdef("eventFeatures"++this.id.asString, {arg oscm;var rcvchannel = oscm[2];
			if (rcvchannel == oscchannel,{
				var features = oscm.drop(3);
				var latestEvent = this.eventList.last++features;
				this.eventList = this.eventList.drop(-1).add(latestEvent);
		})},'/features');
		this.getOnsetsOnly;
	}

	makeFeatureLists {
		var numFeatures = this.eventList[0].size, output, sorted;
		if (numFeatures == 0){Error("Feature Lists are empty!".throw)}{
			output = numFeatures.collect{[]};
			sorted = numFeatures.collect{arg i; this.eventList.collect{arg e; e[i]}};
			^this.featureLists = sorted}
	}

	makeFeaturesDict{
		var features = [\absoluteStartTime, \loudness, \pitch, \confidence, \crest, \flatness,
			\kurtosis, \centroid];
		this.makeFeatureLists;
		this.featureDict = Dictionary.newFrom([features,this.featureLists].lace)
		^this.featureDict;
	}

	//calculates the density of events in blocks of n seconds
	//number of blocks can be specified to either get a single measurement or a curve
	calculateEventDensity{
		arg seconds = 10, numMeasurements = 1 ; //
		var currentTime = this.tempoclock.beats, numEvents = [];
		var absoluteStartTimes = this.eventList.collect{arg e; e[0]};
		^numMeasurements.collect{arg i;
			absoluteStartTimes.removeAllSuchThat({arg ast;
				var lowerLimit = currentTime-((1+i)*seconds);
				var upperLimit = currentTime-(i*seconds);
				lowerLimit < ast && (ast < upperLimit);
			}).size;
		}/seconds;
	}

	////////////////////////////////////////////////////////////////////////////////////////////////////
	//grid hässloich!!!!!
	//generate a numerical grid from gridstart to gridend and apply griddivisions
	setgrid {
		^grid = this.tempoclock.beats.ceil.asInteger.collect({arg i; this.tempoclock.subdiv+i}).flatten
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
		this.noiseFloor = checkloudness.noisefloor;
		'set off threshold to: '.post; this.noiseFloor.post; 'dB'.postln;
	}

	measureMaxLoudness {
		fork {
			this.checkloudness.getmaxloudness;
			6.wait;
			this.maxLoudness = this.checkloudness.maxloudness;
			'set max Loudness to: '.post; this.maxLoudness.post; 'dB'.postln;
		}
	}

	measureMinLoudness {
		fork {
			this.checkloudness.getminloudness;
			6.wait;
			this.minLoudness = checkloudness.minloudness.sum/2;
			'set Onsetthreshold to: '.post; this.minLoudness.post; 'dB'.postln;
		}
	}

	genDeltatlist{arg keep = -24;
		^eventList.keep(keep).collect{arg e; e[0]}
	}


	quantizeDeltaTs {arg keep = -24;
		var grid = tempoclock.subdiv,extendedGrid,eDurs, n = keep;
		var eStarts =(eventList.keep(n-1).collect{|e| e[0]});
		eStarts= eStarts-eStarts[0];
		extendedGrid = eStarts.maxItem.floor.asInteger.collect{|e| e*grid}.flatten.asSet.asList.sort;
		eStarts = extendedGrid[eStarts.collect{|e| extendedGrid.collect{|g|(g-e).abs}.minIndex}];
		eStarts.doAdjacentPairs{|a,b| eDurs = eDurs.add(b-a)};
		^eDurs
	}







}

