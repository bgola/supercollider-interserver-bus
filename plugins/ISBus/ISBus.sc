ISIn : AbstractIn {
	*ar { arg bus = 0, numChannels = 1;
		var bufferSize;

		bufferSize = [];
		bus = bus.asArray.collect {|b|
			if (b.isKindOf(Integer)) {
				// tries to allocate the busnum
				b = ISBus.audio(4096, b);
			};

			bufferSize = bufferSize.add(b.bufferSize);
			b.busnum;
		};

		bufferSize = bufferSize.unbubble;
		bus = bus.unbubble;

		^this.multiNewList(['audio', bus, numChannels, bufferSize]);
	}

	init { arg argBus, numChannels, bufferSize;
		inputs = [argBus, bufferSize];
		^this.initOutputs(numChannels, rate);
	}
}

ISOut : AbstractOut {
	*ar { arg bus, channelsArray;
		var bufferSize;
		bufferSize = [];
		bus = bus.asArray.collect {|b|
			if (b.isKindOf(Integer)) {
				b = ISBus.audio(4096, b);
			};

			bufferSize = bufferSize.add(b.bufferSize);
			b.busnum;
		};

		bufferSize = bufferSize.unbubble;
		bus = bus.unbubble;

		channelsArray = this.replaceZeroesWithSilence(channelsArray.asUGenInput(this).asArray);
		this.multiNewList((['audio', bus, bufferSize] ++ channelsArray));// ++ [bufferSize])
		^0.0		// Out has no output
	}
	*numFixedArgs { ^2 }
	writesToBus { ^true }
}


ISBus {
	classvar <allocator;
	var <busnum=nil, <bufferSize;

	*initClass {
		allocator = ContiguousBlockAllocator.new(2048);
	}

	*new { arg bufferSize=4096, busnum=nil;
		if (busnum.isNil) {
			busnum = ISBus.allocator.alloc(1);
		} {
			ISBus.allocator.reserve(busnum, 1);
		}
		^super.newCopyArgs(busnum, bufferSize);
	}

	*audio { arg bufferSize=4096, busnum=nil;
		^ISBus.new(bufferSize, busnum);
	}
}


ISNdef {
	classvar <all;
	var <key, <serverName, <blockSize, <bufferSize, <source, <server, <ndef,
	<sdef, <numChannels, <skipJack, <isbus, initialized=false;

	*initClass {
		all = ();
	}

	*new { arg key, source=nil, serverName=nil, blockSize=1, bufferSize=4096;
		var obj;
		if (all[key].notNil) {
			obj = all[key];

			if (source.notNil) {
				obj.source = source;
			}
		} {
			if (serverName.isNil) {
				serverName = "server_%".format(key).asSymbol;
			};

			obj = this.newCopyArgs(key, serverName, blockSize, bufferSize, source).init;
			all[key] = obj;
		};

		^obj;
	}

	init {
		server = Server.named[serverName];
		isbus = ISBus.audio(bufferSize);

		if (server.isNil) {
			var portoffset = 1000.rand;
			while { Server.all.asList.collect {|s| s.addr.port }.includes(54000 + portoffset) } {
				portoffset = 1000.rand;
			};
			server = Server(serverName, NetAddr("127.0.0.1", 54000 + portoffset), Server.default.options.copy);
			server.options.blockSize = blockSize;
		};
		ndef = Ndef(key);
		sdef = Ndef(key -> serverName);
		if (source.notNil) {
			this.source_(source);
		};
	}

	addSpec { arg key, value;
		ndef.addSpec(key, value);
		sdef.addSpec(key, value);
	}

	source_ { arg object;
		var ctls;
		source = object;
		if (initialized.not) {
			this.bootAndCreateNdefs;
		};
		sdef[0] = source;

		numChannels = sdef.numChannels;

		ctls = sdef.controlKeys.reject {|key| key == \wet99}.collect {|k|
			[k, sdef.get(k)];
		};
		ndef.source = {
			// add control keys to local ndef as .kr to keep order of parameters consistent
			ctls.do {|keyVal|
				var key = keyVal[0];
				var val = keyVal[1];
				key.kr(val);
			};
			ISIn.ar(isbus, numChannels)
		};



		sdef.getSpec.keysValuesDo {|k,v|
			ndef.addSpec(k, v);
		};
	}

	bootAndCreateNdefs {
		initialized = true;
		fork {
			// Wait some random time because sometimes if booting multiple servers simultaneously
			// something weird happens with sclang. not sure what.
			if (server.hasBooted.not) {
				2.0.rand.wait;
			};
			server.waitForBoot {
				if (source.notNil) {
					sdef[99] = \filter -> {arg in; ISOut.ar(isbus, in); in };

					this.source_(source);
					// keep syncing both, including specs
					skipJack = SkipJack({
						ndef.controlKeys.reject {|key| key == \wet99}.do {|k|
							sdef.set(k, ndef.get(k));
						};
					}, 1/30).play;
				};
			};
		};
	}
}