package io.ableron;

public class TransclusionResult {

  private String body;
  private int processedFragmentsCount;
  private long processingTimeMillis;

  public String getBody() {
    return body;
  }

  public void setBody(String body) {
    this.body = body;
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
