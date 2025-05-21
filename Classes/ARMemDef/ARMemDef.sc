ARMemDef {
	classvar all;
	var <name, <buffer;

	*initClass {
		all = ();
	}

	*new { arg name;
		var obj;
		if (all.keys.includes(name).not) {
			all[name] = super.new.init(name);
		};
		obj = all[name];
		^obj;
	}

	init { arg name;
		buffer = Buffer.alloc(Server.default,1);
	}

	kr { arg sig;
		var diff = sig - Delay1.kr(sig);
		var newvalue = Index.kr(buffer, 0) + diff;
		BufWr.kr(newvalue, buffer, DC.kr(0));
		^newvalue;
	}
}