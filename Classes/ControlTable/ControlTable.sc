/*

Things TODO

- method for updating all table values
- support multiple attachments / map so one control table can control multiple objects
- different clock per table, but option to sync all tables as well

Usage example:

t = 4.collect { Signal.sineFill(2048, { 1.0.linrand }!10, { pi.rand2 }!10) };
c = ControlTable(\test, t).gui;

c.play;
c.duration = 0.5

(
Ndef(\form1).addSpec(\fund, [1, 900, \exp]);
Ndef(\form1).addSpec(\form1, [100, 1000, \exp]);
Ndef(\form1).addSpec(\form2, [100, 1000, \exp]);

Ndef(\form1, { |amp = 0.25, fund = 10, form1 = 500, form2 = 1200|
	Formant.ar(fund.lag(0.03), [form1, form2].lag(0.03),
		[form1, form2].lag(0.03) * 0.2) * amp;
}).play;
)

c.attach(Ndef(\form1));

// exclude a key
c.attach(Ndef(\form1), exclude: [\amp]);

(
Ndef(\form2).addSpec(\fund, [1, 900, \exp]);
Ndef(\form2).addSpec(\form1, [100, 1000, \exp]);
Ndef(\form2).addSpec(\form2, [100, 1000, \exp]);

Ndef(\form2, { |amp = 0.25, fund = 10, form1 = 500, form2 = 1200|
	Formant.ar(fund.lag(0.03), [form1, form2].lag(0.03),
		[form1, form2].lag(0.03) * 0.2) * amp;
}).play;
)

(
// map specific diff objects and attrs
c.mapPairs(
	Ndef(\form1), \fund, Ndef(\form1), \form1, Ndef(\form1), \form2,
	Ndef(\form2), \form2, Ndef(\form2), \form1, Ndef(\form2), \fund,
)
)

(
Ndef(\form1).gui;
Ndef(\form2).gui;
)


// Syncing tables over the network with OSCRouter
(
o = OSCRouterClient(\user, userPassword: \123456); o.join;
z = ControlTableOSC(c, o);
z.start;
)

z.stop;


*/

ControlTable {
	classvar <all, <>clipboard;

	var <name, <tables, <tdef, <window, key, plots;

	*initClass {
		all = ();
		clipboard = nil;
	}

	*new { arg name, data=#[], nTables=nil;
		var object;
		if (ControlTable.all[name].notNil) {
			if (data.notEmpty) {
				// TODO: method to update all table information
				"ControlTable with name % already exists, ignoring new table data".format(name).warn;
			};
			^ControlTable.all[name];
		};

		if (data.isEmpty) {
			data = nTables.collect { Signal.fill(2048, {|idx| idx.linlin(0,2047, -1,1)}) };
		};
		object = super.newCopyArgs(name, data);
		ControlTable.all[name] = object;
		^object.init;
	}

	clear {
		ControlTable.all[name] = nil;
		tdef.clear;
		window.close;
	}

	copyTable { arg tableNumber=0;
		ControlTable.clipboard = tables[tableNumber].copy;
	}

	pasteTable { arg toTableNumber=0;
		this.updateValues(toTableNumber, ControlTable.clipboard);
	}

	init {
		var tableScaleNames = [];
		var tablePosNames = [];
		key = "ctlTable_%".format(name).asSymbol;
		tdef = Tdef(key);
		tdef.addSpec(\usesTables, [0, 1, \lin, 1]);
		tdef.addSpec(\once, [0, 1, \lin, 1]);
		tdef.addSpec(\loopDir, [-1, 1, \lin, 1]);
		tdef.addSpec(\loopDur, [0.01, 100, \exp]);
		tdef.addSpec(\loopPos, [0, 1]);
		// update rate
		tdef.addSpec(\loopDT, [0.001, 0.1, \exp]);

		tdef.addHalo(\namesToStore,
			[\loopPos, \loopDur, \loopDir, \once, \usesTables, \loopDT ]);
		// initialise params
		tdef.set(\usesTables, 1, \once, 0, \loopDir, 1);
		tdef.set(\loopDur, 6, \loopPos, 0, \loopDT, 0.01);
		tdef.addSpec(\tablesSync, [0,1,\lin,1]);
		tdef.set(\tablesSync, 1);
		// do this at every loop step:
		tdef.set(\loopFunc, { |envir, vals|
			// if (0.1.coin) { "loopFunc".rotate(8.rand).postln }
		});
		// this only if usesTables
		tdef.set(\tableFunc, { |envir, vals|
			//if (0.1.coin) { vals.round(0.01).postln }
		});

		if (tables[0].isCollection.not) {
			tables = [tables];
		};

		tables.do { |table, idx|
			var tableScaleName = "t%_scale".format(idx).asSymbol;
			var tablePosName = "t%_pos".format(idx).asSymbol;
			tableScaleNames = tableScaleNames.add(tableScaleName);
			tablePosNames = tablePosNames.add(tablePosName);
			tdef.addSpec(tableScaleName, [0,16,5.415]);
			tdef.set(tableScaleName, 1);
			tdef.addSpec(tablePosName, [0,1]);
			tdef.set(tablePosName, 0);
			//tdef.set(\t0_pos.postcs);
		};

		Tdef(key, { |e|
			var count = 0;
			var keepGoing = true;
			var oldLoopPos = e.loopPos;
			var oldLoopDir = e.loopDir;
			while { keepGoing } {
				var loopPos = e.loopPos;
				var stepsInLoop = (e.loopDur / e.loopDT);
				var increment = stepsInLoop.reciprocal;
				var tableVals;

				// do this func on every step
				e.loopFunc;

				// this only when tables are active
				if (e.usesTables > 0) {
					// read table values ...
					tableVals = e.tables.collect { |table, i|
						// could be interpolating
						//var tabIndex = (loopPos * table.lastIndex).round.asInteger;
						var tabIndex = (e.at(tablePosNames[i]) * table.lastIndex).round.asInteger;
						table.at(tabIndex)
					};
					// and do something with them,
					// eg set synth params somewhere
					e.tableFunc(tableVals);
				};

				e.loopDT.wait;

				loopPos = e.loopPos + (increment * e.loopDir);
				e.tables.do  { |table, idx|
					e[tablePosNames[idx]] = (
						e[tablePosNames[idx]] + (increment * e.loopDir * e.at(tableScaleNames[idx]))
					).wrap(0,1);
				};

				if (loopPos < 0 or: { loopPos > 1 }) {
					if (e.once > 0) { keepGoing = false };
					//"% wrapping\n".postf(name);
					loopPos = loopPos.wrap(0, 1);
					if (e.tablesSync > 0) {
						e.tables.do {|table, idx|
							e[tablePosNames[idx]] = loopPos;
						}
					};
				};

				if (e.loopDir == 0 and: {oldLoopDir == 0}) {
					if (loopPos != oldLoopPos) {
						e.tables.do {|table, idx|
							e[tablePosNames[idx]] = loopPos;
						}
					};
				};
				oldLoopPos = loopPos;
				oldLoopDir = e.loopDir;
				e.loopPos = loopPos;
				count = count + 1;
			};
			"% stopped.\n".postf(tdef);
		});


		tdef.set(\tables, tables);
		//window.front;
	}

	gui {
		window = Window("Control Table %".format(name), Rect(0,0, 430, 900));
		window.layout = VLayout();

		tables.do {|table, idx|
			var tablePosName = "t%_pos".format(idx).asSymbol;
			var color = Color.rand.alpha_(0.5);
			var plot, plotStackView, plotUserView, drawingView, plotBounds;
			plotStackView = View().resize_(5);
			plotUserView = UserView().resize_(5);
			plot = Plotter("~tables",
				Rect(0,0, window.bounds.width - 20, window.bounds.height/(tables.size+1) - 20),
				plotUserView).value_(table).editMode_(true).editFunc_({this.changed(\plots, idx)});

			drawingView = UserView().resize_(5).background_(Color.red(0.5, 0)).acceptsMouse_(false).animate_(true).drawFunc_({|uview|
				var pos = plot.plots[0].plotBounds.left + (tdef.get(tablePosName) * (plot.plots[0].plotBounds.width-10));
				Pen.color_(color);
				Pen.addRect(Rect(pos, plot.plots[0].plotBounds.top, 10, plot.plots[0].plotBounds.height));
				Pen.perform(\fill);
			});
			plotUserView.keyDownAction = { arg view, char;
				if (char == $c) { this.copyTable(idx) };
				if (char == $v) { this.pasteTable(idx) };
			};
			plotStackView.layout = StackLayout(drawingView, plotUserView).mode_(1).index_(0);
			window.layout.add(plotStackView);
			plots = plots ++ [plot];
		};

		TdefGui(tdef, 14 + (2 * tables.size), window, Rect(0,0,400,400));
		window.front;
	}

	updateValues { |tidx, values=#[]|
		if (values.notEmpty) {
			values.do {|value, idx|
				tables[tidx][idx] = value;
			};
		};
		defer { plots[tidx].setValue(tables[tidx]) };
	}

	play {
		tdef.play;
	}

	stop {
		tdef.stop;
	}

	set { arg ...pairs;
		tdef.set(*pairs);
	}

	duration {
		^tdef.get(\loopDur);
	}

	duration_ { arg value;
		tdef.set(\loopDur, value);
	}


	direction {
		^tdef.get(\loopDir);
	}

	direction_ { arg value;
		tdef.set(\loopDir, value);
	}

	// TODO:
	//   support multiple attachments / actions so one table can control multiple objects
	mapPairs { arg ...pairs;
		// pairs of object that accepts the .setUni method + symbol
		tdef.set(\tableFunc, {|e, tableVals|
			pairs.pairsDo { |obj, symbol, idx|
				obj.setUni(symbol, tableVals.wrapAt(idx/2).biuni);
			};
		});
	}

	toNflux { arg nflux, indexes=#[];
		var abs2rel;
		if (indexes.isEmpty) {
			indexes = (0..nflux.nInputs-1);
		};

		abs2rel = indexes.collect {
			NfluxAbs2Rel();
		};
		tdef.set(\tableFunc, {|e, tableVals|
			nflux.setRel(*indexes.collect {|index, i|
				[index, abs2rel[i].set(tableVals[i]).diff]
			}.flat);
		});
	}

	toNTMI { arg influxOffset=3;
		var oldZoom = 0;
		if (NTMI.makeInfluxSource(key, \controlTable).isNil) {
			"NTMI is not running, not doing anything".warn;
			^this;
		};
		tdef.addSpec(\ntmiOffset, [0, NTMI.inphlux.weights[0].size-tables.size,\lin,1]);
		tdef.addSpec(\zoom, [0,4,2]);
		tdef.set(\zoom, 0);
		tdef.set(\ntmiOffset, influxOffset);
		tdef.set(\tableFunc, { |e, tableVals|
			var prevTableVals = tdef.getHalo(\prevTableVals);
			var diffs;
			// |inIndices, diffs, zoom = 0.5|
			if (NTMI.at(key).zoom != e.zoom) {
				if (oldZoom == e.zoom) {
					e.zoom = NTMI.at(key).zoom;
				} {
					NTMI.at(key).zoom = e.zoom;
				};
			};

			if (prevTableVals.notNil) {
				diffs = tableVals - prevTableVals;
				MKtl(key).set(\dummy, 0.5);
				MFdef(\setRelInf).value(diffs.size.collect{|idx| e.ntmiOffset+idx}, diffs, NTMI.zoom * NTMI.at(key).zoom);
			};
			tdef.addHalo(\prevTableVals, tableVals);
			oldZoom = e.zoom;
		});
	}

	attach { arg obj, exclude=#[];
		var controlKeys = obj.controlKeys;
		this.mapPairs(*controlKeys.reject {|key| exclude.includes(key) }.collect { |controlKey, idx|
			[obj, controlKey]
		}.flat);
	}
}


ControlTableOSC {
	var <controlTable, <oscrouter, lastSent;
	*new { arg controlTable, oscrouter;
		^super.newCopyArgs(controlTable, oscrouter).init;
	}

	init {

	}

	start {
		oscrouter.addResp("controlTable_%".format(controlTable.name).asSymbol, { arg msg;
			controlTable.updateValues(msg[1], msg[2..]);
		});
		lastSent = Date.getDate.rawSeconds;
		controlTable.addDependant(this);
	}

	stop {
		controlTable.removeDependant(this);
		oscrouter.removeResp("controlTable_%".format(controlTable.name).asSymbol);
	}

	sendNewValues { arg tableIdx;
		var now = Date.getDate.rawSeconds;
		if (now - lastSent > (1/10)) {
			fork { oscrouter.sendMsg("controlTable_%".format(controlTable.name).asSymbol, tableIdx, *(controlTable.tables[tableIdx].asList.asArray)) };
			lastSent = now;
		} {
			// wait a bit and try to send the last one if we have no other
			fork {
				var now;
				(1/5).wait;
				now = Date.getDate.rawSeconds;
				if (now - lastSent > (1/5)) {
					fork { oscrouter.sendMsg("controlTable_%".format(controlTable.name).asSymbol, tableIdx, *(controlTable.tables[tableIdx].asList.asArray)) };
				};
			}
		}
	}

	update { arg controlTable, what, which;
		if (what == \plots) {
			this.sendNewValues(which);
		}
	}

	free {
		controlTable.removeDependant(this);
		super.free;
	}
}