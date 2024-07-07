package io.github.ableron;

import java.util.concurrent.atomic.AtomicLong;

public class CacheStats {

  private final AtomicLong totalCacheHits = new AtomicLong();
  private final AtomicLong totalCacheMisses = new AtomicLong();
  private final AtomicLong totalSuccessfulCacheRefreshs = new AtomicLong();
  private final AtomicLong totalFailedCacheRefreshs = new AtomicLong();

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

  public long getTotalSuccessfulCacheRefreshs() {
    return totalSuccessfulCacheRefreshs.get();
  }

  public void recordSuccessfulCacheRefresh() {
    totalSuccessfulCacheRefreshs.incrementAndGet();
  }

  public long getTotalFailedCacheRefreshs() {
    return totalFailedCacheRefreshs.get();
  }

  public void recordFailedCacheRefresh() {
    totalFailedCacheRefreshs.incrementAndGet();
  }
}
