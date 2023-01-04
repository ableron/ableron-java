package io.ableron;

public class TransclusionResult {

  /**
   * Content with resolved fragments.
   */
  private String content;

  /**
   * Number of fragments that have been processed in the content.
   */
  private int processedFragmentsCount;

  /**
   * Time in milliseconds it took to resolve the fragments in the content.
   */
  private long processingTimeMillis;

  public String getContent() {
    return content;
  }

  public void setContent(String content) {
    this.content = content;
  }

  public int getProcessedFragmentsCount() {
    return processedFragmentsCount;
  }

  public void setProcessedFragmentsCount(int processedFragmentsCount) {
    this.processedFragmentsCount = processedFragmentsCount;
  }

  public long getProcessingTimeMillis() {
    return processingTimeMillis;
  }

  public void setProcessingTimeMillis(long processingTimeMillis) {
    this.processingTimeMillis = processingTimeMillis;
  }
}
