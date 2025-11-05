Trajectories {
	var <>action, <trajectories, <canvas;

	*new { arg action;
		^super.newCopyArgs(action).init;
	}


	init {
		var win = Window.new("Trajectories", Rect(0,0,400, 800));
		var view = UserView(win, win.view.bounds.insetBy(5,5));
		var colors = {Color.rand}!9;
		var speeds = 1!9;
		var generalSpeed = 1;
		var loops = -1!9;
		var track = 0;
		var mouse = false;
		var ms = 9.collect {|idx|Slider.new().knobColor_(colors[idx]).value_(0.5).action_({|el| speeds[idx] = el.value.lincurve(0,1,0,10,3)})};
		var fit_speeds = 1!9;
		var mode = \loose;
		var movemode = \shot;
		var buts;
		trajectories = {[]}!9;
		canvas = view;
		buts = [
			[Button().string_("loose").action_({mode=\loose}), columns: 4],
			[Button().string_("fit").action_({
				var ttime;
				mode=\fit;
				ttime = trajectories[track].size * speeds[track];
				speeds.do {|speed, t|
					if (t == track or: {trajectories[t].size == 0}) {
						fit_speeds[t] = speed;
					} {
						fit_speeds[t] = (trajectories[t].size) / (ttime);
					}
				}
			}), columns: 5]
		];

		//var ms = MultiSliderView(win);
		view.resize = 5;
		view.background_(Color.rand);
		view.drawFunc={|uview|
			trajectories.size.do {|tr|
				var last, pos=0;
				var aspeeds = speeds;
				var initval, endval, floatPos;
				if (mode == \fit) {
					aspeeds = fit_speeds;
				};
				if (trajectories[tr].size > 0) {
					if (mouse and: {tr == track}) {
						pos = trajectories[tr].size-1
					} {
						if (movemode == \pingpong) {
							pos = (loops[tr] - (trajectories[tr].size-1)).abs.asInteger;
							loops[tr] = ((loops[tr] + (aspeeds[tr] * generalSpeed)) % (trajectories[tr].size*2-2));
						} {
							pos = loops[tr].asInteger.abs;
							loops[tr] = ((loops[tr] + (aspeeds[tr] * generalSpeed)) % trajectories[tr].size);
						};
					};

					last = trajectories[tr][0];
					trajectories[tr][1..].do {|xy,idx|
						//xy.postln;
						Pen.moveTo(Point(last[0], last[1]));
						Pen.lineTo(Point(xy[0], xy[1]));
						Pen.strokeColor_(colors[tr+5%colors.size]);
						Pen.stroke;
						last=xy;
					};
					initval = trajectories[tr][pos];
					endval = trajectories[tr][pos+1 % trajectories[tr].size];
					floatPos = ((loops[tr] - pos).linlin(0, 1, initval, endval));
					action.value(tr, floatPos/[view.bounds.width, view.bounds.height]);
					//~abs2rel.set([~trajectory[pos][0]/v.bounds.width-0.5, ~trajectory[pos][1]/v.bounds.height-0.5]);
					//~abs2rel.diff.postln;
					//n.setRel(0, ~abs2rel.diff[0].clip(-0.1, 0.1), 1, ~abs2rel.diff[1].clip(-0.1, 0.1));
					Pen.addOval(Rect(floatPos[0]-5, floatPos[1]-5, 10, 10));
					Pen.fillColor_(colors[tr]);
					Pen.fill;
				};
			};
		};
		view.animate = true;
		view.mouseDownAction={
			//track.postln;
			trajectories[track] = [];
			mouse = true;
		};
		view.mouseUpAction={
			//track.postln;
			mouse = false;
			loops[track]=0
		};
		win.view.keyDownAction = {|uv, key|
			key = key.asInteger-48;
			if (mouse.not and: {key > 0}) {
				ms[track].background =  nil;

				track = key-1;

				ms[track].background = Color.red;
			};
			//track.postln;
		};
		view.mouseMoveAction={|uv, x,y|
			if (mouse) {
				//track.postln;
				trajectories[track] = trajectories[track].add([x,y]);
			};
		};
		win.layout = GridLayout.rows(
			[[view.minSize_(400@400), columns: 9]],
			ms,
			buts,
			[[Slider().orientation_(\horizontal).value_(1).action_({|el| generalSpeed = el.value.linlin(0,1,-1,1)}), columns: 7],
				[CheckBox().string_("pingpong").action_({|el| if(el.value) { movemode = \pingpong } {movemode = \shot} }), columns: 2]
			]
		);//, HLayout(*ms));
		win.front;
	}

	toNflux { arg nflux;
		var abs2rel = {NfluxAbs2Rel([0,0])}!9;
		action = {|a,b|
			abs2rel[a].set(b);
			nflux.setRel(a*2, abs2rel[a].diff[0], (a+1)*2, abs2rel[a].diff[1]);
		}
	}


	toNTMI { arg influxOffset=3;
		var abs2rel = {NfluxAbs2Rel([0,0])}!9;

		if (NTMI.makeInfluxSource(\trajectories, \trajectories).isNil) {
			"NTMI is not running, not doing anything".warn;
			^this;
		};

		action = { |a, b|
			var diffs = abs2rel[a].set(b).diff;
			MKtl(\trajectories).set(\dummy, 0.5);
			MFdef(\setRelInf).value(diffs.size.collect{|idx| influxOffset+idx}, diffs, NTMI.zoom * NTMI.at(\trajectories).zoom);
		};
	}

	mapPairs { arg ...pairs;
		// pairs of object that accepts the .setUni method + symbol
		action = {|a, b|
			var idx = a * 4;
			var obj1 = pairs[idx];
			var obj2 = pairs[(idx)+2];
			var param = pairs[idx+1];
			var param2 = pairs[idx+3];
			param2.postln;
			if(obj1.notNil) {
				obj1.setUni(param, b[0]);
			};
			if(obj2.notNil) {
				obj2.setUni(param2, b[1]);
			};
		};
	}

	mapPairsRel { arg ...pairs;
		var abs2rel = {NfluxAbs2Rel([0,0])}!9;
		// pairs of object that accepts the .setUni method + symbol
		action = {|a, b|
			var idx = a * 4;
			var obj1 = pairs[idx];
			var obj2 = pairs[(idx)+2];
			var param = pairs[idx+1];
			var param2 = pairs[idx+3];
			abs2rel[a].set(b);
			if(obj1.notNil) {
				obj1.setRel(param, abs2rel[a].diff[0]);
			};
			if(obj2.notNil) {
				obj2.setRel(param2, abs2rel[a].diff[1]);
			};
		};
	}
}