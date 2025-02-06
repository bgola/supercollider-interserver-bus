ISIn : AbstractIn {
	*ar { arg bus = 0, numChannels = 1;
		^this.multiNew('audio', bus, numChannels)
	}
	init { arg argBus, numChannels;
		inputs = argBus.asArray.collect {|b| if (b.isKindOf(ISBus)) {b.busnum} {b}  };
		^this.initOutputs(numChannels, rate)
	}
}

ISOut : AbstractOut {
	*ar { arg bus, channelsArray;
		bus = bus.asArray.collect {|b| if (b.isKindOf(ISBus)) {b.busnum} {b}  };
		channelsArray = this.replaceZeroesWithSilence(channelsArray.asUGenInput(this).asArray);
		this.multiNewList(['audio', bus] ++ channelsArray)
		^0.0		// Out has no output
	}
	*numFixedArgs { ^1 }
	writesToBus { ^true }
}


ISBus {
	classvar <allocator;
	var <rate, <busnum=nil;

	*initClass {
		allocator = ContiguousBlockAllocator.new(2048);
	}

	*new { arg rate;
		if ([\control, \audio].includes(rate).not) {
			"rate should be \control or \audio".warn;
			^nil;
		};

		^super.newCopyArgs(rate, ISBus.allocator.alloc(1));
	}

	*audio {
		^ISBus.new(\audio);
	}
}


ISNdef {
	classvar <all;
	var <key, <serverName, <server, <ndef, <sdef, <numChannels, <skipJack, <isbus;

	*initClass {
		all = ();
	}

	*new { arg key, object=nil, serverName=nil;
		if (object.isNil) {
			if (all[key].notNil) {
				^all[key];
			} {
				object = {}
			}
		};

		if (serverName.isNil) {
			serverName = "server_%".format(key).asSymbol;
		};

		all[key] = this.newCopyArgs(key, serverName).init(object);
		^all[key];
	}

	init { arg object;
		server = Server.named[serverName];
		if (server.isNil) {
			var portoffset = 1000.rand;
			while { Server.all.asList.collect {|s| s.addr.port }.includes(54000 + portoffset) } {
				portoffset = 1000.rand;
			};
			server = Server(serverName, NetAddr("127.0.0.1", 54000 + portoffset));
			server.options.blockSize = 1;
		};
		isbus = ISBus.audio;
		this.bootAndCreateNdefs(object);
	}

	addSpec { arg key, value;
		ndef.addSpec(key, value);
		sdef.addSpec(key, value);
	}

	bootAndCreateNdefs { arg object;
		fork {
			// Wait some random time because sometimes if booting multiple servers simultaneously
			// something weird happens with sclang. not sure what.
			if (server.hasBooted.not) {
				5.0.rand.wait;
			};
			server.waitForBoot {
				if (object.notNil) {
					sdef = Ndef(key -> serverName);
					sdef[0] = object;
					numChannels = sdef.numChannels;
					sdef[10] = \filter -> {arg in; ISOut.ar(isbus, in) };
					ndef = Ndef(key, {ISIn.ar(isbus, numChannels)});
					// add control keys to local ndef
					sdef.controlKeys.do {|k|
						ndef.set(k, sdef.get(k));
					};

					sdef.getSpec.keysValuesDo {|k,v|
						ndef.addSpec(k, v);
					};
					// keep syncing both, including specs
					skipJack = SkipJack({
						ndef.controlKeys.do {|k|
							sdef.set(k, ndef.get(k));
						};
						/*sdef.getSpec.keysValuesDo {|k,v|
							ndef.addSpec(k, v);
						};*/
					}, (1/20)).play;
				};
			};
		};
	}
}