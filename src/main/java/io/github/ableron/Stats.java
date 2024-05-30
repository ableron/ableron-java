package io.github.ableron;

import java.util.concurrent.atomic.AtomicLong;

public class Stats {

  private final AtomicLong totalCacheHits = new AtomicLong();
  private final AtomicLong totalCacheMisses = new AtomicLong();

  public long getTotalCacheHits() {
    return totalCacheHits.get();
  }

  public void recordCacheHit() {
    totalCacheHits.incrementAndGet();
  }

  public long getTotalCacheMisses() {
    return totalCacheMisses.get();
  }

  public void recordCacheMiss() {
    totalCacheMisses.incrementAndGet();
  }
}
