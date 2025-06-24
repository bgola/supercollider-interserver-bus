// GstRTP.cpp
// Bruno Gola (me@bgo.la)

#include "SC_PlugIn.hpp"
#include "ISBus.hpp"
#include <boost/interprocess/managed_shared_memory.hpp>

static InterfaceTable* ft;

using namespace boost::interprocess;

namespace ISBus {

ISOut::ISOut() {
    mCalcFunc = make_calc_function<ISOut, &ISOut::next>();
    const float* id = in(0);
    int key = *id;
    char shmname[50];
    Unit* unit = (Unit*) this;

    data.bufferSize = *in(1);

    if (data.bufferSize <= 0) { 
        data.bufferSize = DEFAULT_BUFFER_SIZE;
    }
 
    // TODO: check for a legal size of the id (should be less than 44 characters)
    std::sprintf(shmname, "ISBus_%d", key);
    data.segment = new managed_shared_memory(open_or_create, shmname, sizeof(RingBuffer)*2);
    data.ringBuffer = data.segment->find_or_construct<RingBuffer>("RingBuffer")();
    if (!data.ringBuffer) {
        Print("Failed to create RingBuffer\n");
    }

    if (!data.ringBuffer->init) {
        for (int channel = 0; channel < (numInputs() - 2); channel++) {
            for (int i=0; i<MAX_BUFFER_SIZE; i++) 
                data.ringBuffer->buffer[channel][i] = 0.0;
        }
        data.ringBuffer->init = true;
    }

    char debug[50];
    //std::sprintf(debug, "bufSize is %d, numIns is %d, bufnum is %d\n", data.bufferSize, numInputs(), key);
    //Print(debug);
    
    next(1);
}

ISOut::~ISOut() {
    delete data.segment;
}

void ISOut::next(int nSamples) {
    Unit* unit = (Unit*) this;
    //size_t systemBufferSize = DEFAULT_BUFFER_SIZE;
    size_t systemBufferSize = data.bufferSize;
    for (int i=0; i < nSamples; ++i) {
        size_t head = data.ringBuffer->head.load(std::memory_order_acquire);
        size_t nextHead = (head+1) % systemBufferSize;
        for (int channel=0; channel < (numInputs() - 2); channel++) {
            const float* input = in(2+channel);
            data.ringBuffer->buffer[channel][head] = input[i];
        }
        data.ringBuffer->head.store(nextHead, std::memory_order_release); 
    }
}


ISIn::ISIn() {
    mCalcFunc = make_calc_function<ISIn, &ISIn::next>();
    const float* id = in(0);
    int key = *id;
    char shmname[50];
    data.bufferSize = *in(1);

    if (data.bufferSize <= 0) { 
        data.bufferSize = DEFAULT_BUFFER_SIZE;
    }

    char debug[50];
    //std::sprintf(debug, "bufSize is %d, numOuts is %d, bufnum is %d\n", data.bufferSize, numOutputs(), key);
    //Print(debug);
    

    std::sprintf(shmname, "ISBus_%d", key);
    data.segment = new managed_shared_memory(open_or_create, shmname, sizeof(RingBuffer)*2);
    data.ringBuffer = data.segment->find_or_construct<RingBuffer>("RingBuffer")();
    if (!data.ringBuffer) {
        Print("Failed to create RingBuffer\n");
    }

    if (!data.ringBuffer->init) {
        for (int channel = 0; channel < numOutputs(); channel++) {
            for (int i=0; i<MAX_BUFFER_SIZE; i++) 
                data.ringBuffer->buffer[channel][i] = 0.0;
        }
        data.ringBuffer->init = true;
    }

    //size_t systemBufferSize = DEFAULT_BUFFER_SIZE;
    size_t systemBufferSize = data.bufferSize;
    tail = (data.ringBuffer->head.load() + (systemBufferSize/2)) % systemBufferSize;
    next(1);
}

ISIn::~ISIn() {
    delete data.segment;
}

void ISIn::next(int nSamples) {
    //size_t systemBufferSize = DEFAULT_BUFFER_SIZE;
    size_t systemBufferSize = data.bufferSize;
    size_t currentHead = data.ringBuffer->head.load(std::memory_order_acquire);
    if ((tail - currentHead) < systemBufferSize / 4) {
        //Print("Resetting tail... %d\n", tail - currentHead);
        tail = (data.ringBuffer->head.load() + (systemBufferSize/2)) % systemBufferSize;

    }

    for (int i = 0; i < nSamples; ++i) {
        for (int channel = 0; channel < numOutputs(); channel += 1) {
            float* outbuf = out(channel);
            float sample = 0.0f;
            sample = data.ringBuffer->buffer[channel][tail];
            outbuf[i] = sample;
        }
        tail = (tail + 1) % systemBufferSize;
    }
}


} // namespace ISBus


/*
void getNewBus(World *inWorld, void* inUserData, struct sc_msg_iter *args, void *replyAddr) {
    //const char *addr = args->gets("");
    //int port = args->geti(9999);
    Print("%s:%d\n", registryAddrs[key], registryPorts[key]);
    
}
*/

PluginLoad(ISBusUGens) {
    // Plugin magic
    ft = inTable;
    registerUnit<ISBus::ISOut>(ft, "ISOut", false);
    registerUnit<ISBus::ISIn>(ft, "ISIn", false);
    //DefinePlugInCmd("/isb_get_new_bus", getNewBus, nullptr);
}
