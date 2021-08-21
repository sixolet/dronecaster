// old version way too complicated
// we don't want xfades, we want down+up
DroneCaster_SynthSocket {
	var server, group, synth, controls, out;
	var cuedSource, doneResponder;
	var fadeTime = 1.0;

	*new {
		arg server, out, controls;
		^super.new.init(server, out, controls);
	}

	wrapDef {
		arg fn, name;
		var def, nope = false;
		postln("building synthdef: "++name);
		def = SynthDef.new(name, {
			arg hz, amp=1, out=0, gate=1, attack=4.0, release=4.0;

			var aenv, snd;
			snd = { fn.value(K2A.ar(hz), K2A.ar(amp)) }.try { 
				arg err;				
				postln("failed to wrap ugen graph! error:");
				err.postln;
				nope = true;
				[Silent.ar]
			};
			aenv = EnvGen.kr(Env.asr(attack, 1, release), gate, doneAction:2);
			// force mix down to stereo (interleaved)
			snd = Mix.new(snd.flatten.clump(2)) * aenv;
			
			Out.ar(out, snd);
		});
		if (nope, {
			^nil
		}, {
			def.load(server);
			^def;
		});
	}


	stop {
		if (synth.notNil, {
			synth.set(\gate, 0);
		});
	}

	setSource { arg def;
		postf("socket requested new source: %; current synth = %\n", def.name, synth);
		cuedSource = def;
		if (synth.isNil, {
			postln("no current synth; playing now");
			this.performSource;
		}, {
			postln("stopping current synth...");
			synth.set(\gate, 0);
		});
	}


	setControl { arg k, v;
		//postf("setting control % = %\n", k, v);
		controls[k].set(v);
	}

	init { arg aServer, aOut, aControls;
		server = aServer;
		out = aOut;
		controls = Dictionary.new;
		aControls.do({ arg k;
			postln("creating control bus: " ++ k);
			controls[k] = Bus.control(server, 1);
		});

		group = Group.new(server);
		synth = nil;
		cuedSource = nil;

		doneResponder =  OSCFunc({ arg msg;
			var nodeId, id;
			nodeId = msg[1];
			postln("handling node end for ID: " ++ nodeId);
			if (synth.notNil, {
				postln("(our synth ID: " ++ synth.nodeID ++ ")");
				if (nodeId == synth.nodeID, {
					this.performSource;
				});
			});
		}, '/n_end', server.addr);
	}

	free {
		controls.do({ arg bus; bus.free; });
		if (synth.notNil, { synth.free; });
		group.free;
	}

	///// private

	performSource {
		postln(controls);		
		if (cuedSource.notNil, {
			var controlVals = Dictionary.new;
			var condition = Condition.new;
			var synthArgs = Dictionary.new;

			postf("performing cued source: % (%)\n", cuedSource, cuedSource.name);
			controls.keys.do({ arg k;
				controlVals[k] = controls[k].getSynchronous;
			});
			
			synthArgs = [\out, out] ++ controlVals.getPairs;
			postln("synthArgs = " ++ synthArgs);
			postln("cuedSource = " ++ cuedSource);
			
			synth = Synth.new(cuedSource.name, synthArgs, target:group);
			postf("new synth = %\n", synth);
			controls.keys.do({ arg k; synth.map(k, controls[k]); });
			cuedSource = nil;
		}, {
			synth = nil;
		});
	}
}
