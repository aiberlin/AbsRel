ARMemDef {
	classvar all;
	var <name, <buffer, <spec;

	*initClass {
		all = ();
	}

	*new { arg name, spec=nil;
		var obj;
		if (all.keys.includes(name).not) {
			spec = spec.asSpec;
			all[name] = super.new.init(name, spec);
		};
		obj = all[name];
		^obj;
	}

	init { arg name, controlspec;
		buffer = Buffer.alloc(Server.default,1);
		spec = controlspec;
	}

	kr { arg sig;
		var diff = sig - Delay1.kr(sig);
		var newvalue = Index.kr(buffer, 0) + diff;
		BufWr.kr(newvalue.clip(spec.minval, spec.maxval), buffer, DC.kr(0));
		^newvalue;
	}
}