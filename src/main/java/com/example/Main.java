package com.example;

import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;
import org.objectweb.asm.MethodVisitor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class Main {
  public static void main(String[] args) throws IOException {
    // Specify the path to the input file
    String inputFilePath;
    String className;
    if (args != null && args.length > 1) {
      inputFilePath = args[0];
      className = args[1];
    } else {
      inputFilePath = "\\input.simplec";
      className = "DemoClass";
    }
    // Read the file content
    String inputCode = new String(Files.readAllBytes(Paths.get(inputFilePath)));

    // Create a lexer and parser for SimpleC
    CharStream input = CharStreams.fromString(inputCode);
    SimpleCLexer lexer = new SimpleCLexer(input);
    CommonTokenStream tokens = new CommonTokenStream(lexer);
    SimpleCParser parser = new SimpleCParser(tokens);

    // Parse the input code
    ParseTree tree = parser.program();

    // Interpret the code
//        SimpleCVisitorImpl interpreter = new SimpleCVisitorImpl();
//        interpreter.visit(tree);

    // Compile to bytecode
    SimpleCCompiler compiler = new SimpleCCompiler(className);
    compiler.visit(tree);
    compiler.end();
  }
}
