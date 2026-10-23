#include "../playback_support.h"
#include <cassert>
#include <chrono>
#include <future>
#include <iostream>
#include <memory>
#include <thread>

using namespace std::chrono_literals;

static void rateChangesDoNotJump() {
    playback::Timeline clock;
    clock.running(true, 0);
    assert(clock.position(10000) == 10000);
    clock.rate(2, 10000);
    assert(clock.position(10000) == 10000);
    assert(clock.position(15000) == 20000);
    clock.rate(.5, 15000);
    assert(clock.position(15000) == 20000);
    assert(clock.position(17000) == 21000);
    clock.running(false, 17000);
    assert(clock.position(100000) == 21000);
    clock.rate(4, 100000);
    clock.running(true, 200000);
    assert(clock.position(200250) == 22000);
    clock.reset(3500, 200250);
    assert(clock.position(200500) == 4500);
}

static void seekingTrimsPcmOnSampleBoundaries() {
    assert(playback::trimSamples(-20000, 48000, 1024, 0) == 960);
    assert(playback::trimSamples(0, 48000, 1024, 100000) == 1024);
    assert(playback::trimSamples(100000, 48000, 1024, 100000) == 0);
    assert(playback::trimSamples(99999, 48000, 1024, 100000) == 1);
}

static void seekCancelsBlockedProducerAndDiscardsOldPackets() {
    playback::EpochQueue<std::unique_ptr<int>> queue(1, 4);
    queue.reset(0, std::make_unique<int>(-1));
    std::promise<void> entered;
    auto producer = std::async(std::launch::async, [&] {
        entered.set_value();
        return queue.push(0, std::make_unique<int>(123), 4);
    });
    entered.get_future().wait();
    assert(producer.wait_for(20ms) == std::future_status::timeout);
    queue.reset(1, std::make_unique<int>(-2));
    assert(producer.wait_for(1s) == std::future_status::ready);
    assert(!producer.get());
    // A stale seek must not replace a newer queue generation.
    queue.reset(0, std::make_unique<int>(-3));
    std::unique_ptr<int> item;
    assert(queue.pop(item) && *item == -2);
    assert(queue.empty());
    assert(queue.push(1, std::make_unique<int>(456), 4));
    assert(queue.pop(item) && *item == 456);
    queue.abort();
    assert(!queue.pop(item));
}

static void stopWakesBlockedConsumersAndProducers() {
    for (int repeat = 0; repeat < 100; ++repeat) {
        playback::EpochQueue<int> empty(1, 4);
        auto consumer = std::async(std::launch::async, [&] { int item; return empty.pop(item); });
        empty.abort();
        assert(consumer.wait_for(1s) == std::future_status::ready);
        assert(!consumer.get());
        playback::EpochQueue<int> full(1, 4);
        full.reset(0, 0);
        auto producer = std::async(std::launch::async, [&] { return full.push(0, 1, 4); });
        full.abort();
        assert(producer.wait_for(1s) == std::future_status::ready);
        assert(!producer.get());
    }
}

static void boundedQueuesPreserveOrderUnderLoad() {
    playback::EpochQueue<int> queue(4, 16);
    auto producer = std::async(std::launch::async, [&] {
        for (int i = 0; i < 10000; ++i) assert(queue.push(0, i, 4));
    });
    for (int i = 0; i < 10000; ++i) {
        int value = -1;
        assert(queue.pop(value));
        assert(value == i);
    }
    assert(producer.wait_for(1s) == std::future_status::ready);
    producer.get();
}

int main() {
    rateChangesDoNotJump();
    seekingTrimsPcmOnSampleBoundaries();
    seekCancelsBlockedProducerAndDiscardsOldPackets();
    stopWakesBlockedConsumersAndProducers();
    boundedQueuesPreserveOrderUnderLoad();
    std::cout << "PASS: continuous clock, PCM seek trim, epoch cancellation, stop wakeups, queue ordering\n";
}
