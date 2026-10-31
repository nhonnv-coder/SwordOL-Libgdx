package vn.swordol.capture;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.nio.file.Paths;
import java.security.ProtectionDomain;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public final class CaptureAgent {
  private CaptureAgent() {}

  public static void premain(String directory, Instrumentation instrumentation) throws Exception {
    Capture.configure(Paths.get(directory), 64L * 1024 * 1024);
    Runtime.getRuntime().addShutdownHook(new Thread(Capture::finishAll, "capture-finalize"));
    instrumentation.addTransformer(
        new ClassFileTransformer() {
          @Override
          public byte[] transform(
              ClassLoader loader,
              String name,
              Class<?> type,
              ProtectionDomain domain,
              byte[] bytes) {
            if (!"javax/microedition/io/Connector".equals(name)) {
              return null;
            }
            byte[] result = instrument(bytes);
            System.err.println("SwordOL capture: Java ME HTTP/TCP recording active.");
            return result;
          }
        });
  }

  static byte[] instrument(byte[] bytes) {
    ClassReader reader = new ClassReader(bytes);
    ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
    reader.accept(
        new ClassVisitor(Opcodes.ASM9, writer) {
          @Override
          public MethodVisitor visitMethod(
              int access, String name, String descriptor, String signature, String[] exceptions) {
            MethodVisitor delegate =
                super.visitMethod(access, name, descriptor, signature, exceptions);
            if (!name.equals("open")
                || !descriptor.endsWith(")Ljavax/microedition/io/Connection;")) {
              return delegate;
            }
            return new MethodVisitor(Opcodes.ASM9, delegate) {
              @Override
              public void visitInsn(int opcode) {
                if (opcode == Opcodes.ARETURN) {
                  super.visitVarInsn(Opcodes.ALOAD, 0);
                  super.visitMethodInsn(
                      Opcodes.INVOKESTATIC,
                      "vn/swordol/capture/Capture",
                      "wrap",
                      "(Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/Object;",
                      false);
                  super.visitTypeInsn(Opcodes.CHECKCAST, "javax/microedition/io/Connection");
                }
                super.visitInsn(opcode);
              }
            };
          }
        },
        0);
    return writer.toByteArray();
  }
}
