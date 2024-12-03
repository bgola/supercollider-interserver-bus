ISIn : AbstractIn {
	*ar { arg bus = 0, numChannels = 1;
		^this.multiNew('audio', bus, numChannels)
	}
	init { arg argBus, numChannels;
		inputs = argBus.asArray;
		^this.initOutputs(numChannels, rate)
	}
}

ISOut : AbstractOut {
	*ar { arg bus, channelsArray;
		channelsArray = this.replaceZeroesWithSilence(channelsArray.asUGenInput(this).asArray);
		this.multiNewList(['audio', bus] ++ channelsArray)
		^0.0		// Out has no output
	}
	*numFixedArgs { ^1 }
	writesToBus { ^true }
}
