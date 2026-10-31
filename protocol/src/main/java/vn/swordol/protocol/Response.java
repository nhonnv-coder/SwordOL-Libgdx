package vn.swordol.protocol;

import com.google.gson.JsonObject;

public final class Response {
  public String requestId, type;
  public boolean ok;
  public JsonObject payload = new JsonObject();
  public ErrorInfo error;

  public static final class ErrorInfo {
    public String code, message;

    public ErrorInfo(String code, String message) {
      this.code = code;
      this.message = message;
    }
  }

  public static Response success(Request request, JsonObject payload) {
    Response r = base(request);
    r.ok = true;
    r.payload = payload;
    return r;
  }

  public static Response failure(Request request, String code, String message) {
    Response r = base(request);
    r.error = new ErrorInfo(code, message);
    return r;
  }

  private static Response base(Request request) {
    Response r = new Response();
    if (request != null) {
      r.requestId = request.requestId;
      r.type = request.type;
    }
    return r;
  }
}
