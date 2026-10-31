package vn.swordol.protocol;

import com.google.gson.JsonObject;

public final class Request {
  public String requestId, type;
  public JsonObject payload;

  public Request() {}

  public Request(String requestId, String type, JsonObject payload) {
    this.requestId = requestId;
    this.type = type;
    this.payload = payload;
  }
}
