package io.github.ableron;

import java.util.concurrent.atomic.LongAdder;
import java.util.function.LongSupplier;

public class CacheStats {

  private final LongAdder hitCount = new LongAdder();
  private final LongAdder missCount = new LongAdder();
  private final LongAdder refreshSuccessCount = new LongAdder();
  private final LongAdder refreshFailureCount = new LongAdder();
  private final LongSupplier itemCountSupplier;

  public CacheStats() {
    this(() -> 0L);
  }

  public CacheStats(LongSupplier itemCountSupplier) {
    this.itemCountSupplier = itemCountSupplier;
  }

  public long itemCount() {
    return itemCountSupplier.getAsLong();
  }

  public long hitCount() {
    return hitCount.sum();
  }

  public void recordHit() {
    hitCount.increment();
  }

  public long missCount() {
    return missCount.sum();
  }

  public void recordMiss() {
    missCount.increment();
  }

  public long refreshSuccessCount() {
    return refreshSuccessCount.sum();
  }

  public void recordRefreshSuccess() {
    refreshSuccessCount.increment();
  }

  public long refreshFailureCount() {
    return refreshFailureCount.sum();
  }

  public void recordRefreshFailure() {
    refreshFailureCount.increment();
  }
}
