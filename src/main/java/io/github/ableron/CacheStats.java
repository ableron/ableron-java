package io.github.ableron;

import java.util.concurrent.atomic.AtomicLong;

public class CacheStats {

  private final AtomicLong itemCount = new AtomicLong();
  private final AtomicLong hitCount = new AtomicLong();
  private final AtomicLong missCount = new AtomicLong();
  private final AtomicLong refreshSuccessCount = new AtomicLong();
  private final AtomicLong refreshFailureCount = new AtomicLong();

  public long itemCount() {
    return itemCount.get();
  }

  public void setItemCount(long itemCount) {
    this.itemCount.set(itemCount);
  }

  public long hitCount() {
    return hitCount.get();
  }

  public void recordHit() {
    hitCount.incrementAndGet();
  }

  public long missCount() {
    return missCount.get();
  }

  public void recordMiss() {
    missCount.incrementAndGet();
  }

  public long refreshSuccessCount() {
    return refreshSuccessCount.get();
  }

  public void recordRefreshSuccess() {
    refreshSuccessCount.incrementAndGet();
  }

  public long refreshFailureCount() {
    return refreshFailureCount.get();
  }

  public void recordRefreshFailure() {
    refreshFailureCount.incrementAndGet();
  }
}
