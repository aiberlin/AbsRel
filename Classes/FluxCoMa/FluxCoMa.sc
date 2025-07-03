FluxCoMa {
	var name, nflux, server, serverToInfluence, submix;
	//var name="FluxCoMa";

	*new { arg name, nflux, server=nil, serverToInfluence=nil;
		^this.newCopyArgs(name, nflux, server, serverToInfluence).init;
	}

	makeUpdatedGui { arg gui, func;
		var frk = fork {
			var wouldbe;
			loop {
				(1/25).wait;
				wouldbe = func.value;
				defer {
					var changed = false, val, selection;
					val = gui.value;
					if (gui.isKindOf(ListView)) {
						selection = wouldbe[1];
						wouldbe = wouldbe[0];
						//selection = gui.selection;
						//selection = selection.collect {|idx| gui.items[idx]};
						val = gui.items;
						changed = (
							(wouldbe != val) ||
							(selection.sort != gui.selection.collect {|idx| gui.items[idx]}.sort)
						);
					} {
						changed = wouldbe != val;
					};
					if (wouldbe.isNil.not and: {wouldbe.isNumber or: {wouldbe.isArray} or:
						{wouldbe.isKindOf(Boolean)}} and: {changed}) {
						if (gui.isKindOf(ListView)) {
							gui.items_(wouldbe);
							gui.selection = selection.collect {|item|
								gui.items.indexOf(item);
							};
						} {
							gui.value_(func.value)
						}
					}
				}
			}
		};
		gui.onClose = {frk.stop};
		^gui;
	}

	submix {
		^submix;
	}

	init {
		var defName = "fluxcoma_%".format(name).asSymbol;
		var submixName = "%_sub".format(defName).asSymbol;
		var oscPath = "/fluxcoma_%".format(defName).asSymbol;
		var abs2rel, flux;
		var gui, colors, visualizerView, square, addProxyView, toProxyView, fluxView, ndefView;
		var node;
		var vstack, freqScope, busToFreq;


		if (server.isNil) { server = Server.default };
		if (serverToInfluence.isNil) { serverToInfluence = server };
		server.postln;
		serverToInfluence.postln;
		submix = ProxySubmix.new(submixName);

		busToFreq = Bus.audio(server, 1);
		submix.ar(2);
		/*
		sources.asArray.do {|source|
			if (submix.proxies.asArray.includes(source).not) {
				submix.addMix(source, 1, false);
			}
		};*/

		/*
		Proxy for listening
		*/
		node = NodeProxy.audio(server, 2);
		node.reshaping = \elastic;
		node.source = {
			var isnd = \source.ar(0!2).asArray.sum.equi(prefix: \input);
			var gate = Amplitude.kr(isnd) > 0.01;
			var snd = isnd * gate.lag(0.02);
			// place holder for zooms
			var zooms = [
				\zzoom.kr(0, spec:[0,2]),
				\z_mfccs.kr(0, spec:[0,2]),
				\z_chroma.kr(0, spec:[0,2]),
				\z_shape.kr(0, spec:[0,2]),
				\z_loudness.kr(0, spec:[0,2])
			];
			var rate = \replyrate.kr(50, spec:[1,100,\exp]);
			var lag = \lag.kr(0.5, spec:[0.01, 5]);
			var mfccs = FluidMelBands.kr(snd, 10, 40, 10000, normalize:1, windowSize: 128);
			//var mfccs = FluidMFCC.kr(snd, 10, 40, 1, 40, 10000, windowSize: 1024);
			var chroma = FluidChroma.kr(snd, 10, 440, 1, windowSize: 128);
			var shape = FluidSpectralShape.kr(snd, windowSize: 128);
			var out = CombC.kr([
				(mfccs/RunningMax.kr(mfccs.abs).max(0.0001)),
				(chroma/RunningMax.kr(chroma.abs).max(0.0001)),
				(shape/RunningMax.kr(shape.abs).max(0.0001)),
				FluidLoudness.kr(snd, kWeighting:0, windowSize: 64)[0].dbamp
			].flat, 4, \time.kr(0, spec:[0,4]), \decay.kr(0, spec:[0,1]));
			var trg = Impulse.kr(rate);
			out = Latch.kr(out, trg).lag(lag);
			SendReply.kr(trg, oscPath, out);
			Out.ar(busToFreq, isnd);
			out;
		};
		node.set(\source, submix);
		//EQui(target: node, prefix: \input);
		abs2rel = NfluxAbs2Rel(0!node.numChannels);
		flux = nflux ?? { Nflux(node.numChannels, 42) };

		/*
		GUI
		*/
		gui = Window.new(defName, 800@170);
		gui.background_(Color(0,0.7,0.6));

		/*
		List of proxies
		*/

		/*addProxyView = View();
		addProxyView.layout = HLayout(
		ListView().items_(Ndef.all['localhost'].activeProxies.asArray).selectionMode = \multi
		);*/

		addProxyView = this.makeUpdatedGui(ListView(nil, 20@20).items_(
			if (Ndef.all[server.name].notNil) {
				Ndef.all[server.name].activeProxies.asArray.reject {|label| label == submixName}
			} {
				[]
			}
		).selectionMode = \multi, {
			if (Ndef.all[server.name].notNil) {
				[Ndef.all[server.name].activeProxies.asArray.reject {|label| label == submixName},
					submix.proxies.collect {|proxy| proxy.key }.asArray]
			} {
				[[], flux.relativeAttachedObjects.collect { |obj| obj.object.key }.asArray]
			};
		});
		addProxyView.selection = [];
		addProxyView.selectionAction = {|lv|
			var current = submix.proxies.asArray.collect {|proxy| proxy.key }.asSet;
			var selection = lv.selection.asArray.collect { |idx| lv.items[idx] }.asSet;
			var toRemove = current - selection;
			var toAdd = selection - current;
			toAdd.do {|source|
				if (submix.proxies.asArray.includes(Ndef(source)).not) {
					submix.addMix(Ndef(source), 1, false);
				};
			};
			toRemove.do {|source|
				"to remove: ".post; source.postln;
				if (submix.proxies.asArray.includes(Ndef(source))) {
					submix.removeMix(Ndef(source));
				};
			};
		};

		toProxyView = this.makeUpdatedGui(ListView(nil, 20@20).items_(
			if (Ndef.all[serverToInfluence.name].notNil) {
				Ndef.all[serverToInfluence.name].activeProxies.asArray.reject {|label| label == submixName}
			} {
				[]
			}
		).selectionMode = \multi, {
			//Ndef.all['localhost'].activeProxies.asArray.reject {|label| label == submixName}
			if (Ndef.all[serverToInfluence.name].notNil) {
				[Ndef.all[serverToInfluence.name].activeProxies.asArray.reject {|label| label == submixName},
					flux.relativeAttachedObjects.collect { |obj| obj.object.key }.asArray]
			} {
				[[], flux.relativeAttachedObjects.collect { |obj| obj.object.key }.asArray]
			};
		});

		toProxyView.selection = [];
		toProxyView.selectionAction = {|lv|
			var current = flux.relativeAttachedObjects.collect { |obj| obj.object.key }.asSet;
			var selection = lv.selection.asArray.collect { |idx| lv.items[idx] }.asSet;
			var toRemove = current - selection;
			var toAdd = selection - current;
			toAdd.do {|source|
				flux.attachMappedRelative(Ndef(source), offset: 24.rand);
			};
			toRemove.do {|source|
				flux.detachMappedRelative(Ndef(source));
			};
		};
		//gui.layout.add(addProxyView);
		//EZPopUpMenu.new(addProxyView, nil, "Add a proxy", Ndef.all['localhost'].activeProxies);


		/*
		Visualizer
		*/

		colors = 0!node.numChannels;

		visualizerView = UserView();
		visualizerView.background = Color.black;
		visualizerView.drawFunc = {|uv|
			Pen.use {
				var square = uv.bounds.width / node.numChannels;
				node.numChannels.do {|i|
					Pen.color = Color.red(1.0, colors[i].abs.pow(0.2) * 1);
					Pen.addRect(Rect((i*(square)), 5, square-5, uv.bounds.height-10));
					Pen.fill;
				};
				//colors = colors * 0.95;
			}
		};
		visualizerView.animate = true;
		fluxView = View();
		ndefView = GridLayout.rows(
			*[\zzoom, \z_mfccs, \z_chroma, \z_shape, \z_loudness, \replyrate, \lag, \time].collect {|name|
				var mview = MView(node.get(name), gui);
				var spec = node.getSpec(name);
				mview.putDict(\myspec, spec);
				mview.action.add(\set, {|mv| node.set(name, mv.value) });
				mview.uv.mouseWheelAction = {|uv, x, y, p, o, v|
					mview.value_(
						spec.map(spec.unmap(mview.value) + (v*0.0005))
					);
				};

				[StaticText().string_(name),
					[
						mview,
						columns: 5
					]
				]

				//var mview = View();
				/*EZSlider(
				mview, nil, name, node.getSpec(name),
				{|mv| node.set(name, mv.value) }
				);*/
				/*var slider = Slider().orientation_(\horizontal);
				[
				[StaticText().string_(name), columns: 1, a: \left],
				[slider, columns: 10],
				[NumberBox().value_, columns: 1, a: \right]
				]*/
			};
		);

		gui.layout = GridLayout.rows(
			[StaticText().string_("FluxCoMa for %".format(name)).font_(Font("Arial", 24))],
			[VLayout(StaticText().string_("Listen to:").font_(Font("Arial", 18)), addProxyView), ndefView],
			[[visualizerView.minSize_(100@70), columns: 2]],
			[VLayout(StaticText().string_("Proxies to influence:").font_(Font("Arial", 18)), toProxyView), fluxView],
		);
		flux.gui(fluxView);

		vstack = View(fluxView, 400@190).resize_(5);
		freqScope = FreqScopeView(server:server);
		//freqScope.inBus_(submix.bus);
		freqScope.inBus_(busToFreq);
		freqScope.active_(true);
		freqScope.freqMode_(1);
		freqScope.background_(Color(1,1,1,0.9));
		freqScope.waveColors_([Color(0,0.3,0.3)]);
		vstack.onClose_({freqScope.kill});
		vstack.layout = StackLayout(
			EQui(vstack, 400@190, node, prefix: \input),
			freqScope
		).mode_(1);

		gui.front;

		OSCdef(defName, { arg msg;
			var diffs;
			var mfccs, chroma, shape, loudness;
			var zoom = node.get(\zzoom);
			var vals;
			abs2rel.set(msg[3..]);
			diffs = abs2rel.diff;
			mfccs = diffs[..9] * node.get(\z_mfccs) * zoom;
			chroma = diffs[10..19] * node.get(\z_chroma) * zoom;
			shape = diffs[20..26] * node.get(\z_shape) * zoom;
			loudness = diffs[27] * node.get(\z_loudness) * zoom;
			vals = [mfccs, chroma, shape, loudness].flat;
			colors.do {|c,i|
				colors[i] = vals[i];
			};
			flux.setRel(*vals.collect {|v,i| [i,v]}.flat);
		}, oscPath);

		gui.onClose = {
			OSCdef(defName).clear;
			node.clear;
		};
	}
}