package io.github.ableron;

public class Content {

  private String content;

  public Content(String content) {
    this.content = content;
  }

  public static Content of(String content) {
    return new Content(content);
  }

  public String get() {
    return content;
  }

  public synchronized void replace(String text, String replacement) {
    this.content = content.replace(text, replacement);
  }
}
