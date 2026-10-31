package vn.swordol.server;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import vn.swordol.protocol.*;

/** Loopback-only demo gateway. Each worker owns one socket and its session. */
public final class DemoServer implements AutoCloseable {
  public static final int READ_TIMEOUT_MS = 15000;
  private final ServerSocket listener;
  private final Set<Socket> sockets = Collections.synchronizedSet(new HashSet<Socket>());
  private final ThreadPoolExecutor workers =
      new ThreadPoolExecutor(8, 8, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(16));
  private volatile boolean running;
  private Thread acceptor;

  private final DemoWorld world;

  public DemoServer(int port) throws IOException {
    this("127.0.0.1", port);
  }

  public DemoServer(String host, int port) throws IOException {
    this(host, port, DemoWorld.fromClasspath());
  }

  public DemoServer(String host, int port, DemoWorld world) throws IOException {
    this.world = world;
    listener = new ServerSocket();
    listener.bind(new InetSocketAddress(InetAddress.getByName(host), port), 16);
  }

  public int getPort() {
    return listener.getLocalPort();
  }

  public synchronized void start() {
    if (running) throw new IllegalStateException("Already started");
    running = true;
    acceptor = new Thread(this::accept, "swordol-accept");
    acceptor.start();
  }

  private void accept() {
    while (running)
      try {
        Socket socket = listener.accept();
        socket.setSoTimeout(READ_TIMEOUT_MS);
        sockets.add(socket);
        try {
          workers.execute(() -> serve(socket));
        } catch (RejectedExecutionException ex) {
          sockets.remove(socket);
          socket.close();
        }
      } catch (IOException ex) {
        if (running) System.err.println("Accept failed: " + ex.getMessage());
      }
  }

  private void serve(Socket socket) {
    try (Socket owned = socket) {
      DemoSession session = new DemoSession(world);
      String frame;
      while (running && (frame = FrameCodec.read(owned.getInputStream())) != null) {
        Response response;
        try {
          response = session.handle(FrameCodec.decodeRequest(frame));
        } catch (IllegalArgumentException ex) {
          response = Response.failure(null, "BAD_REQUEST", "Invalid JSON request");
        }
        FrameCodec.write(owned.getOutputStream(), FrameCodec.encode(response));
      }
    } catch (IOException ex) {
      /* Bad frame, timeout or peer shutdown closes only this connection. */
    } finally {
      sockets.remove(socket);
    }
  }

  @Override
  public void close() throws IOException {
    running = false;
    listener.close();
    synchronized (sockets) {
      for (Socket s : sockets)
        try {
          s.close();
        } catch (IOException ignored) {
        }
    }
    workers.shutdownNow();
    try {
      if (acceptor != null) acceptor.join(2000);
      workers.awaitTermination(2, TimeUnit.SECONDS);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
    }
  }
}
