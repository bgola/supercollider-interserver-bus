// New version with ISNdef, a convinience to create an ndef on a special blockSize=1 server and mirrors the local ndef:

(
ISNdef(\xfm, { arg freqA = 32, freqB = 9, modAtoB=540, modBtoA=240, delayedSamples;
	var fbIn = LocalIn.ar(2);
	var sigs = SinOsc.ar([freqA, freqB] + (fbIn.reverse * [modBtoA, modAtoB]));
	LocalOut.ar(sigs);
	sigs*0.5;
});
ISNdef(\xfm).addSpec(\freqA, [0.01, 20000, \exp]);
ISNdef(\xfm).addSpec(\freqB, [0.01, 20000, \exp]);
ISNdef(\xfm).addSpec(\modAtoB, [0,1000]);
ISNdef(\xfm).addSpec(\modBtoA, [0,1000]);
)

// inspect the local ndef
ISNdef(\xfm).ndef.gui;

// inspect the ndef running on a seprate server with blockSize = 1
ISNdef(\xfm).sdef.gui;

// you can reuse a server to save resources
ISNdef(\reuse, { LFSaw.ar(\freq.kr) }, ISNdef(\xfm).serverName);
ISNdef(\reuse).ndef.gui
ISNdef(\reuse).sdef.gui;




/* OLD EXAMPLE WITHOUT ISBus and ISNdef */

(
s.waitForBoot {
	z = Server.new(\singleSample, NetAddr("127.0.0.1", 57140));
	z.options.blockSize = 1;
	z.waitForBoot {
		Ndef(\singleSampleXFM -> \singleSample).addSpec(\freqA, [0.01, 20000, \exp]);
		Ndef(\singleSampleXFM -> \singleSample).addSpec(\freqB, [0.01, 20000, \exp]);
		Ndef(\singleSampleXFM -> \singleSample).addSpec(\modAtoB, [0,1000]);
		Ndef(\singleSampleXFM -> \singleSample).addSpec(\modBtoA, [0,1000]);
		Ndef(\singleSampleXFM -> \singleSample).addSpec(\delayedSamples, [0,2, \lin, 1]);

		Ndef(\singleSampleXFM -> \singleSample, { arg freqA = 32, freqB = 9, modAtoB=540, modBtoA=240, delayedSamples;
			var fbIn = LocalIn.ar(2);
			var sigs = SinOsc.ar([freqA, freqB] + (fbIn.reverse * [modBtoA, modAtoB]));
			LocalOut.ar(
				Select.ar(delayedSamples, [
					sigs,
					Delay1.ar(sigs),
					Delay2.ar(sigs)
				]);
			);
			ISOut.ar(0, sigs * 0.5);
		}).gui;

		Ndef(\listen, {
			ISIn.ar(0, 2);
		}).play;
	}
};
)