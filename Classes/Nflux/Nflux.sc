NfluxAttachedObject {
	var <object, <>inScaler=1.0, <offset=0;
	var <>controlKeys=#[], <controlSpecs=nil;

	*new { arg object=nil, inScaler=1.0, includeKeys=#[], excludeKeys=#[], offset=0;
		^this.newCopyArgs(object, inScaler, offset).init(includeKeys, excludeKeys);
	}

	init { arg includeKeys=#[], excludeKeys=#[];
		if (includeKeys.isEmpty) {
			includeKeys = object.controlKeys;
		};

		controlKeys = includeKeys.reject(excludeKeys.includes(_));
		controlSpecs = controlKeys.collect {|key| [key, object.getSpec(key)] }.flatten.asDict;
	}
}


NfluxAbs2Rel {
	var <value=nil;
	var <diff=0;

	*new { arg value=nil;
		^this.newCopyArgs(value);
	}

	set {|newValue|
		if (value.notNil) {
			// avoids jumps in diff if value has not been initialized
			diff = newValue - value;
		};
		value = newValue;
	}

	doesNotUnderstand { |selector ... args|
		^diff.perform(selector, *args)
	}
}


NfluxAbs2RelDef : NfluxAbs2Rel {
	classvar <all;
	var <>key;

	*initClass { all = () }

	*new { arg key, value=nil;
		var instance = all[key];
		if (instance.isNil) {
			instance = super.new(value).key_(key);
			all[key] = instance;
		};
		instance.set(value);
		^instance;
	}
}



Nflux {
	var <nInputs, <nOutputs;
	var <weights, in, out, <>inScaler=1.0;
	var <attachedObjects;
	var <relativeAttachedObjects;
	var <>cb;

	*new {|nInputs=2, nOutputs=8|
		^super.newCopyArgs(nInputs, nOutputs).init;
	}

	kr {}

	init {
		weights = {{1.0.rand2}!nOutputs}!nInputs;
		in = 0.0!nInputs;
		attachedObjects = Set(128);
		relativeAttachedObjects = Set(128);
		this.cb = {};
		this.calculateOut;
	}

	/*inScaler_ { arg scaler;
		inScaler = scaler;
		//this.calculateOut;
	}*/

	calculateOut {
		out = nInputs.collect {|i|
			in[i] * weights[i] * inScaler
		}.sum.clip(-1,1);
		this.applyAttached;
	}

	getOuts {
		^out[0..];
 	}

	getIns {
		^in[0..];
	}

	set { arg ...pairs;
		pairs.asDict.keysValuesDo { |k, v|
			in[k] = v.clip(-1,1);
		};
		this.calculateOut;
	}

	rand {
		weights = {{1.0.rand2}!nOutputs}!nInputs;
	}
	setw { | arrays |
		if (arrays.shape == weights.shape) {
			weights = arrays;
		} {
			warn("Nflux - new weights have wrong shape: %.\n"
				.format(weights.shape))
		}
	}

	project { arg ...pairs;
		var dict = pairs.asDict;
		^nInputs.collect {|i|
			var val = dict[i] ? 0;
			val * weights[i]
		}.sum;
	}

	callback { arg pairs, result;
		this.cb.value(pairs, result);
	}

	setRel { arg ...pairs;
		var result = this.project(*pairs);
		this.callback(pairs, result);
		this.applyRelativeAttached(result);
	}

	applyAttached {
		attachedObjects.do {|obj|
			var outIdx = 0;
			var params = [];
			obj.controlKeys.do {|ctrlKey, i|
				var ctrlVal = obj.object.get(ctrlKey);
				var ctrlRetVal;
				if (ctrlVal.isArray) {
					ctrlRetVal = ctrlVal.collect {|currValue, j|
						var retVal = obj.controlSpecs[ctrlKey].map(out[outIdx % out.size] + 1 * 0.5 * obj.inScaler);
						outIdx = outIdx + 1;
						retVal;
					};
				} {
					ctrlRetVal = obj.controlSpecs[ctrlKey].map(out[outIdx % out.size] + 1 * 0.5 * obj.inScaler);
					outIdx = outIdx + 1;
				};
				params = params ++ [ctrlKey, ctrlRetVal];
			};
			obj.object.set(*params);
		};
	}

	applyRelativeAttached { arg vecs;
		relativeAttachedObjects.do {|obj|
			var zoffset = 0;
			var params = [];
			obj.controlKeys.do {|ctrlKey,zdx|
				var ctrlVal = obj.object.get(ctrlKey);
				var ctrlRelVal;
				if (ctrlVal.isNumber or: ctrlVal.isArray) {
					if (ctrlVal.isArray) {
						ctrlRelVal = ctrlVal.collect {|val, hdx| vecs[(zdx+zoffset+hdx+obj.offset) % vecs.size]};
						zoffset = zoffset + (ctrlVal.size - 1);
					} {
						ctrlRelVal = vecs[(zdx+zoffset+obj.offset)%vecs.size];

					};
					params = params ++ [ctrlKey, ctrlRelVal * inScaler * obj.inScaler];
				};
			};
			obj.object.relSet(*params);
		}
	}

	attachMapped { arg object, exclude=#[], offset=0;
		if (object.isKindOf(NfluxAttachedObject).not) {
			object = NfluxAttachedObject(object, 1.0, excludeKeys: exclude, offset: offset);
		};
		attachedObjects.add(object);
	}

	attachMappedRelative { arg object, exclude=#[], offset=0;
		if (object.isKindOf(NfluxAttachedObject).not) {
			object = NfluxAttachedObject(object, 1.0, excludeKeys: exclude, offset: offset);
		};
		relativeAttachedObjects.add(object);
	}

	attachMappedAll { arg object, include=#[], exclude=#[];
		this.attahMapped(object, include, exclude);
		this.attahMappedRelative(object, include, exclude);
	}

	detachMapped { arg object;
		if (object.isKindOf(NfluxAttachedObject)) {
			object = object.object;
		};
		this.attachedObjects.asList.do {|obj|
			if (obj.object == object) {
				this.attachedObjects.remove(obj);
			};
		};
	}

	detachMappedRelative { arg object;
		if (object.isKindOf(NfluxAttachedObject)) {
			object = object.object;
		};
		this.relativeAttachedObjects.asList.do {|obj|
			if (obj.object == object) {
				this.relativeAttachedObjects.remove(obj);
			};
		};
	}

	detach { arg object;
		this.detachMapped(object);
		this.detachMappedRelative(object);
	}

	gui {
		var vals = nInputs.collect { NfluxAbs2Rel() };
		//var yval = NfluxAbs2Rel();
		//var sldrx = 0;
		//var sldry = 0;
		var win = Window.new;
		var frk;
		var gui;
		var zoomSlider = Slider(win).value_(this.inScaler).action_({|slider| this.inScaler = slider.value});

		if (nInputs == 1) {
			gui = Slider(win).value_(0.5).action_({|slider| this.setRel(0, vals[0]) });
		} {
			if (nInputs == 2) {
				gui = Slider2D(win).x_(0.5).y_(0.5).action_({|slider| this.setRel(0, vals[0].set(slider.x), 1, vals[1].set(slider.y)) });
			} {
				gui = MultiSliderView(win)
				.value_(0.5!nInputs)
				.action_({|mslider| var values = vals.collect {|val,idx| [idx, val.set(mslider.value[idx])] }; this.setRel(*values.flatten)})
				.elasticMode_(1)
				.isFilled_(true);
		} };

		win.layout = HLayout(
			gui,
			zoomSlider
		);
		frk = fork {
			loop {
				(1/20).wait;
				defer {
					zoomSlider.value_(this.inScaler);
				};
			};
		};
		win.onClose = {frk.stop};
		win.front;
		^win;
	}
}

+NodeProxy {
	relSet { | ...args |
		//var diffs=[], keys=[];
		//var values = [];
		var keys = [];
		var values = (args.size/2).asInteger.collect {|idx|
			var diff, key, spec;
			//diffs = diffs ++ [args[idx*2+1]];
			keys = keys ++ [args[idx*2]];

			diff = args[idx*2+1];
			key = args[idx*2];
			spec = this.getSpec(key);
			if (spec.isNil) {
				"No spec for control key: %".format(key).warn;
			} {
				//values = [spec.map(spec.unmap(this.get(key)) + diff)];
				spec.map(diff + spec.unmap(this.get(key)));
				//spec.map(spec.unmap(this.get(key)) + diff);
			};
		};
		//values.postln;
		//keys.postln;
		//var keys = (args.size/2).asInteger.collect {|idx| args[idx*2]};
		//var values = keys.collect {|ar,idx|
		//	var spec = this.getSpec(ar)
		//	spec.map(spec.unmap(this.get(ar)) + diffs[idx]);//.clip(this.getSpec(ar).minval, this.getSpec(ar).maxval);
		//};

		//this.set(*(keys +++ values).flatten);
		//(keys.collect {|key, idx| [key, values[idx]]}.flatten);
		this.set(*(keys.collect {|key, idx| [key, values[idx]]}.flatten))
	}
}
