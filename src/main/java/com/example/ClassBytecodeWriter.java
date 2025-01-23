package com.example;

import org.objectweb.asm.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.objectweb.asm.Opcodes.*;

/**
 * A Class writer that will be used to store bytecode operations during processing and then writing the class file.
 */
public class ClassBytecodeWriter {
  private final ClassWriter classWriter;
  private final String className;
  private int maxLocals = 1;

  /**
   * @param className the name of the class (without .class)
   */
  public ClassBytecodeWriter(String className) {
    this.className = className;
    classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);
    classWriter
        .visit(V1_8, ACC_PUBLIC, className, null, Type.getInternalName(Object.class), null);
    // Create class initializer and the Scanner property
    this.createStaticScanner();
    this.createClassInitializer();
  }

  /**
   * Begin writing instructions to the main method.
   *
   * @return the method visitor
   */
  public MethodVisitor begin() {
    return classWriter.visitMethod(ACC_PUBLIC + ACC_STATIC, "main", "([Ljava/lang/String;)V", null, null);
  }

  /**
   * End the writing of instructions to the method.
   *
   * @param visitor the static method
   */
  public void end(MethodVisitor visitor) {
    visitor.visitInsn(RETURN);
    visitor.visitMaxs(2, maxLocals);
    visitor.visitEnd();
  }

  /**
   * Write the class to a .class file.
   *
   * @throws IOException
   */
  public void write() throws IOException {
    Path currentRelativePath = Paths.get("");
    String absolutePath = currentRelativePath.toAbsolutePath().toString();
    try (OutputStream f = new FileOutputStream(new File(absolutePath, className + ".class"))) {
      f.write(classWriter.toByteArray());
    }
  }

  /**
   * Creates an instance initializer and instantiates the Scanner field.
   */
  private void createClassInitializer() {
    MethodVisitor constructor = classWriter
        .visitMethod(ACC_STATIC, "<clinit>", "()V", null, null);

    constructor.visitTypeInsn(Opcodes.NEW, "java/util/Scanner");
    constructor.visitInsn(Opcodes.DUP);

    constructor.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/System", "in", "Ljava/io/InputStream;");
    constructor.visitMethodInsn(INVOKESPECIAL, "java/util/Scanner", "<init>", "(Ljava/io/InputStream;)V", false);
    constructor.visitFieldInsn(Opcodes.PUTSTATIC, className, "scanner", "Ljava/util/Scanner;");
    constructor.visitInsn(RETURN);
    constructor.visitMaxs(3, 0);
    constructor.visitEnd();
  }

  /**
   * Create the field:
   * private static Scanner scanner;
   */
  private void createStaticScanner() {
    FieldVisitor sc = classWriter.visitField(ACC_PRIVATE + ACC_STATIC, "scanner", "Ljava/util/Scanner;", null, null);
    sc.visitEnd();
  }

  /**
   * Scans for input, stores the value to a variable and returns the local index of the stored variable.
   *
   * @param methodVisitor the method visitor.
   * @return the index of the stored variable.
   */
  public int createScanInvocation(MethodVisitor methodVisitor) {
    methodVisitor
        .visitFieldInsn(GETSTATIC, className, "scanner", "Ljava/util/Scanner;");

    methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/util/Scanner", "nextInt", "()I", false);
    methodVisitor.visitVarInsn(Type.INT_TYPE.getOpcode(ISTORE), ++maxLocals);
    return maxLocals;
  }

  /**
   * Invokes the System.out.println method.
   *
   * @param methodVisitor the method visitor
   * @param varIndex      the index of the variable to push on the stack
   */
  public void createPrintInvocation(MethodVisitor methodVisitor, int varIndex) {
    // If varIndex = -1 then we don't want to push anything new on the stack. Just pop the last element in this case.
    if (-1 != varIndex) {
      methodVisitor.visitIntInsn(ILOAD, varIndex);
    }
    methodVisitor.visitMethodInsn(INVOKEVIRTUAL, Type.getInternalName(PrintStream.class), "println",
        "(I)V", false);
  }

  /**
   * Push the PrintStream on the stack, so we can use it to invoke System.out.println
   *
   * @param methodVisitor the method visitor
   */
  public void appendPrintStream(MethodVisitor methodVisitor) {
    methodVisitor.visitFieldInsn(GETSTATIC, Type.getInternalName(System.class), "out",
        Type.getDescriptor(PrintStream.class));
  }

  /**
   * Does an equation and pushes the result back on the stack.
   *
   * @param methodVisitor the method visitor
   * @param sign          the equation sign (+,- etc...)
   * @param localParams   the indexes of local parameters to be pushed to the stack for calculation.
   */
  public void addEquation(MethodVisitor methodVisitor, String sign, Integer... localParams) {
    for (Integer param : localParams) {
      methodVisitor.visitIntInsn(ILOAD, param);
    }
    // do the equation and push the result on the stack:
    if ("-".equals(sign)) {
      methodVisitor.visitInsn(ISUB);
    } else if ("+".equals(sign)) {
      methodVisitor.visitInsn(IADD);
    }
  }

  /**
   * Assigns a value to a variable.
   * eg. A = 100;
   *
   * @param methodVisitor the current method visitor
   * @param value         the value
   * @return the stack index of the newly created variable
   */
  public int assignValue(MethodVisitor methodVisitor, int value) {
    methodVisitor.visitLdcInsn(value);
    methodVisitor.visitVarInsn(ISTORE, ++maxLocals);
    return maxLocals;
  }

  /**
   * Increments a variable by one.
   * eg. A++;
   *
   * @param methodVisitor the MethodVisitor
   * @param param         the stack index of the variable
   */
  public void incrementValue(MethodVisitor methodVisitor, int param) {
    methodVisitor.visitVarInsn(ILOAD, param);
    methodVisitor.visitVarInsn(ISTORE, ++maxLocals);
    methodVisitor.visitVarInsn(ILOAD, param);
    methodVisitor.visitInsn(ICONST_1);
    methodVisitor.visitInsn(IADD);
    methodVisitor.visitVarInsn(ISTORE, param);
    methodVisitor.visitVarInsn(ILOAD, maxLocals);
    methodVisitor.visitInsn(POP);
    maxLocals--; // decrement the number the JVM reuses the reserved slot
  }

  /**
   * Decrements a variable by one.
   * eg. A--;
   *
   * @param methodVisitor the MethodVisitor
   * @param param         the stack index of the variable
   */
  public void decrementValue(MethodVisitor methodVisitor, int param) {
    methodVisitor.visitVarInsn(ILOAD, param);
    methodVisitor.visitVarInsn(ISTORE, ++maxLocals);
    methodVisitor.visitVarInsn(ILOAD, param);
    methodVisitor.visitInsn(ICONST_1);
    methodVisitor.visitInsn(ISUB);
    methodVisitor.visitVarInsn(ISTORE, param);
    methodVisitor.visitVarInsn(ILOAD, maxLocals);
    methodVisitor.visitInsn(POP);
    maxLocals--; // decrement the number the JVM reuses the reserved slot
  }
}
