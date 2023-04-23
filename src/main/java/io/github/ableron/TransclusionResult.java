package io.github.ableron;

public class TransclusionResult {

  /**
   * Content with resolved includes.
   */
  private String content;

  /**
   * Status code set by a primary include which is to be sent along the content.
   */
  private Integer statusCodeOverride;

  /**
   * Number of includes that have been processed.
   */
  private int processedIncludesCount;

  /**
   * Time in milliseconds it took to resolve the includes in the content.
   */
  private long processingTimeMillis;

  public String getContent() {
    return content;
  }

  public void setContent(String content) {
    this.content = content;
  }

  public Integer getStatusCodeOverride() {
    return statusCodeOverride;
  }

  public void setStatusCodeOverride(Integer statusCodeOverride) {
    this.statusCodeOverride = statusCodeOverride;
  }

  public int getProcessedIncludesCount() {
    return processedIncludesCount;
  }

  public void setProcessedIncludesCount(int processedIncludesCount) {
    this.processedIncludesCount = processedIncludesCount;
  }

  public long getProcessingTimeMillis() {
    return processingTimeMillis;
  }

  public void setProcessingTimeMillis(long processingTimeMillis) {
    this.processingTimeMillis = processingTimeMillis;
  }
}
