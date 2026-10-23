#pragma once

#include <algorithm>
#include <condition_variable>
#include <cstddef>
#include <cstdint>
#include <deque>
#include <mutex>
#include <utility>

namespace playback {
// Call under the player's control mutex. Rate changes preserve media time at the anchor.
class Timeline {
public:
    double position(int64_t nowMs) const {
        return baseMs_ + (running_ ? std::max<int64_t>(0, nowMs - anchorMs_) * rate_ : 0);
    }
    void reset(double positionMs, int64_t nowMs) { baseMs_ = positionMs; anchorMs_ = nowMs; }
    void running(bool value, int64_t nowMs) {
        reset(position(nowMs), nowMs);
        running_ = value;
    }
    void rate(double value, int64_t nowMs) {
        reset(position(nowMs), nowMs);
        rate_ = value;
    }
private:
    double baseMs_ = 0;
    int64_t anchorMs_ = 0;
    double rate_ = 1;
    bool running_ = false;
};

inline int trimSamples(int64_t startUs, int rate, int samples, int64_t targetUs) {
    if (startUs >= targetUs) return 0;
    int64_t skip = ((targetUs - startUs) * rate + 999999) / 1000000;
    return static_cast<int>(std::min<int64_t>(samples, skip));
}

// A seek replaces queued data and wakes blocked producers as well as consumers.
// T may own a packet; discarded entries are destroyed by the queue.
template<class T> class EpochQueue {
public:
    EpochQueue(size_t maxItems, size_t maxBytes) : maxItems_(maxItems), maxBytes_(maxBytes) { }
    void reset(uint64_t epoch, T marker) {
        std::lock_guard<std::mutex> lock(mutex_);
        if (aborted_ || epoch < epoch_) return;
        epoch_ = epoch;
        entries_.clear();
        bytes_ = 0;
        entries_.push_back({std::move(marker), 0});
        changed_.notify_all();
    }
    bool push(uint64_t epoch, T value, size_t bytes = 0) {
        std::unique_lock<std::mutex> lock(mutex_);
        changed_.wait(lock, [&] {
            return aborted_ || epoch != epoch_ || entries_.empty() ||
                   (entries_.size() < maxItems_ && bytes_ + bytes <= maxBytes_);
        });
        if (aborted_ || epoch != epoch_) return false;
        entries_.push_back({std::move(value), bytes});
        bytes_ += bytes;
        changed_.notify_all();
        return true;
    }
    bool pop(T &value) {
        std::unique_lock<std::mutex> lock(mutex_);
        changed_.wait(lock, [&] { return aborted_ || !entries_.empty(); });
        if (aborted_) return false;
        bytes_ -= entries_.front().bytes;
        value = std::move(entries_.front().value);
        entries_.pop_front();
        changed_.notify_all();
        return true;
    }
    void abort() {
        std::lock_guard<std::mutex> lock(mutex_);
        aborted_ = true;
        entries_.clear();
        bytes_ = 0;
        changed_.notify_all();
    }
    bool empty() {
        std::lock_guard<std::mutex> lock(mutex_);
        return entries_.empty();
    }
private:
    struct Entry { T value; size_t bytes; };
    std::mutex mutex_;
    std::condition_variable changed_;
    std::deque<Entry> entries_;
    size_t bytes_ = 0;
    uint64_t epoch_ = 0;
    bool aborted_ = false;
    const size_t maxItems_;
    const size_t maxBytes_;
};
} // namespace playback
