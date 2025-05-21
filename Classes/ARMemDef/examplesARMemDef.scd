
Ndef(\w, {ARMemDef(\x).kr(LFDNoise3.kr(2, 1)).poll}).end
Ndef(\z, {ARMemDef(\x).kr(SinOsc.kr(1.1))}).end;

Ndef(\s)[0] = {arg val=0; ARMemDef(\x).kr(val)};
Ndef(\s)[1] = \set -> Pbind(\val, Pseq([1,0,0,0], inf), \dur, 0.1);
Ndef(\s).end;

Ndef(\n, {arg val=0; ARMemDef(\x).kr(val)}).gui
ARMemDef(\x).buffer.set(0,0)


Ndef(\v)[0] = {arg val=0; ARMemDef(\x).kr(val)};
Ndef(\v)[1] = \set -> Pbind(\val, Pseq([0,0,-0.6], inf), \dur, 0.13);
Ndef(\v).end;


// multiple sequences (could be two players
Ndef(\trg, {var trg = Impulse.kr(\spd.kr(1, spec:[0.1,30, \exp])); [trg, Stepper.kr(trg, 0, 0, 15)]}).gui

(
Ndef(\si, {
	var stepval = ARMemDef(\x).kr(0);
	Env.perc(0.01, 0.3).ar(0, (stepval > 0) & Ndef(\trg).kr(2)[0]) *
	SinOsc.ar(1000 + (stepval*800)) * stepval
}).scope
)
(
Ndef(\zs, {
	var seq = [ 1, -1, 0, 1,  0, 0, 0.5, 0,  1, 0, 0, 1,  -1,0,0,0];
	ARMemDef(\x).kr(Select.kr(Ndef(\trg).kr[1] % seq.size,seq))
});
)

(
Ndef(\zz, {
	var seq = [ 0, 1, 0, -1,  0, 0, -0.5, 0,    -1, 0, 0, -1,  1, 0, 0, 0];
	ARMemDef(\x).kr(Select.kr(Ndef(\trg).kr[1] % seq.size,seq))
});
)
ARMemDef(\x).buffer.set(0,0)


Ndef(\n, {arg val=0; ARMemDef(\x).kr(val)}).gui

Ndef(\zc, {ARMemDef(\x).kr(0)}).scope

Ndef(\si).gui