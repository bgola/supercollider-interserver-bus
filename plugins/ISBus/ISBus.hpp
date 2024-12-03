// GstRTP.hpp
// Bruno Gola (me@bgo.la)

#pragma once

#include <boost/interprocess/managed_shared_memory.hpp>
#include <atomic>
#include "SC_PlugIn.hpp"

#define MAX_BUFFER_SIZE 32768
#define DEFAULT_BUFFER_SIZE 4096

static bool ISBusRegistry[99999];

struct RingBuffer {
    alignas(64) std::atomic<size_t> head;
    bool init;
    float buffer[16][MAX_BUFFER_SIZE];

    RingBuffer() : head(0) {};
};

typedef struct _BusData {
    RingBuffer* ringBuffer;
    boost::interprocess::managed_shared_memory* segment;
} ISBusData;


namespace ISBus {

class ISOut : public SCUnit {
public:
    ISOut();

    // Destructor
    ~ISOut();

private:
    // Calc function
    void next(int nSamples);
    ISBusData data;
};


class ISIn : public SCUnit {
public:
    ISIn();

    // Destructor
    ~ISIn();

private:
    // Calc function
    void next(int nSamples);

    ISBusData data;
    size_t tail;
};


} // namespace ISBus
