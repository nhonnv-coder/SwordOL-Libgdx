package vn.swordol.server;

import java.util.concurrent.CountDownLatch;

public final class ServerMain {
  private ServerMain() {}

  public static void main(String[] args) throws Exception {
    String host = "127.0.0.1";
    int port = 7777;
    String assets = null;
    for (int i = 0; i < args.length; i++) {
      if ("--host".equals(args[i]) && i + 1 < args.length) host = args[++i];
      else if ("--port".equals(args[i]) && i + 1 < args.length) port = Integer.parseInt(args[++i]);
      else if ("--assets".equals(args[i]) && i + 1 < args.length) assets = args[++i];
      else
        throw new IllegalArgumentException(
            "Usage: --host 127.0.0.1 --port 7777 [--assets path/to/bundle.swb]");
    }
    final DemoServer server =
        new DemoServer(
            host,
            port,
            assets == null
                ? DemoWorld.fromClasspath()
                : DemoWorld.fromFile(java.nio.file.Paths.get(assets)));
    final CountDownLatch stopped = new CountDownLatch(1);
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  try {
                    server.close();
                  } catch (Exception ex) {
                    System.err.println(ex.getMessage());
                  } finally {
                    stopped.countDown();
                  }
                },
                "swordol-shutdown"));
    server.start();
    System.out.println(
        "SwordOL demo listening on "
            + host
            + ":"
            + server.getPort()
            + " (demo rules; no production authentication or persistence)");
    stopped.await();
  }
}
