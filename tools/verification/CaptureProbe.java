import com.sun.net.httpserver.HttpServer;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

/** End-to-end check using the supplied emulator Connector and loopback servers only. */
public final class CaptureProbe {
  public static void main(String[] args) throws Exception {
    // MicroEmulator's MIDlet enhancer needs its bundled legacy ASM interface.
    Class.forName("org.objectweb.asm.ClassAdapter");
    final byte[] expected = {0, 4, 3, (byte) 232};
    HttpServer http = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    http.createContext("/response", exchange -> {
      exchange.sendResponseHeaders(200, expected.length);
      exchange.getResponseBody().write(expected);
      exchange.close();
    });
    http.start();
    try (ServerSocket tcp = new ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress())) {
      tcp.setSoTimeout(10000);
      Thread server = new Thread(() -> {
        try (Socket socket = tcp.accept()) { socket.getOutputStream().write(expected); }
        catch (Exception error) { throw new RuntimeException(error); }
      });
      server.start();
      Method open = Class.forName("javax.microedition.io.Connector").getMethod("open", String.class);
      consume(open, "http://127.0.0.1:" + http.getAddress().getPort() + "/response", expected);
      consume(open, "socket://127.0.0.1:" + tcp.getLocalPort(), expected);
      server.join(10000);
      Path directory = Paths.get(args[0]);
      for (String name : new String[] {"000001-http", "000002-tcp"}) {
        if (!Arrays.equals(expected, Files.readAllBytes(directory.resolve(name + ".bin")))) {
          throw new AssertionError("Capture mismatch: " + name);
        }
        String metadata = new String(Files.readAllBytes(directory.resolve(name + ".json")), "UTF-8");
        if (!metadata.contains("\"completion\": \"eof\"")) { throw new AssertionError(metadata); }
      }
      System.out.println("PASS: original emulator Connector, HTTP + TCP bytes and finalized metadata");
    } finally { http.stop(0); }
  }

  private static void consume(Method open, String address, byte[] expected) throws Exception {
    Object connection = open.invoke(null, address);
    try {
      Class<?> inputConnection = Class.forName("javax.microedition.io.InputConnection");
      try (InputStream input = (InputStream) inputConnection.getMethod("openInputStream").invoke(connection)) {
        for (byte value : expected) {
          if (input.read() != (value & 255)) { throw new AssertionError("Network bytes changed"); }
        }
        if (input.read() != -1) { throw new AssertionError("Expected EOF"); }
      }
    } finally { Class.forName("javax.microedition.io.Connection").getMethod("close").invoke(connection); }
  }
}
