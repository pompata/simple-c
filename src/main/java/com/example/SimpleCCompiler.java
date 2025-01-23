package com.example;

import org.objectweb.asm.MethodVisitor;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SimpleCCompiler extends SimpleCBaseVisitor<Integer> {
  private final Map<String, Integer> variables = new HashMap<>();
  private final Map<String, Integer> vars = new HashMap<>();
  private static final Pattern ARITHMETICS = Pattern.compile("([+-])?(([a-zA-Z]+)([+-])([a-zA-Z]+))");

  private final ClassBytecodeWriter classBytecodeWriter;
  private final MethodVisitor codeHandler;

  public SimpleCCompiler(String className) {
    this.classBytecodeWriter = new ClassBytecodeWriter(className);
    this.codeHandler = classBytecodeWriter.begin();
  }

  @Override
  public Integer visitProgram(SimpleCParser.ProgramContext ctx) {
    for (SimpleCParser.StatementContext stmt : ctx.statement()) {
      visit(stmt);
    }
    return 0;
  }

  @Override
  public Integer visitStatement(SimpleCParser.StatementContext ctx) {
    visit(ctx.expression());
    return 0;
  }

  @Override
  public Integer visitAssignment(SimpleCParser.AssignmentContext ctx) {
    if (ctx.getChildCount() == 3) {
      String varName = ctx.Ident().getText();
      if ("scanf()".equals(ctx.getChild(2).getChild(0).getText())) {
        visit(ctx.expression());
      } else {
        int value = Integer.parseInt(ctx.getChild(2).getText());
        int varIndex = classBytecodeWriter.assignValue(codeHandler, value);
        setVar(varName, varIndex);
      }

      return 0;
    } else if (ctx.getChildCount() == 2) {
      String varName = ctx.Ident().getText();
      int var = getParam(varName);
      if (ctx.getChild(1).getText().equals("++")) {
        // Handle A++
        classBytecodeWriter.incrementValue(codeHandler, var);
        return 0;
      } else if (ctx.getChild(1).getText().equals("--")) {
        // Handle A--
        classBytecodeWriter.decrementValue(codeHandler, var);
        return 0;
      }
    }
    throw new RuntimeException("Unknown assignment: " + ctx.getText());
  }

  @Override
  public Integer visitPrintfFunc(SimpleCParser.PrintfFuncContext ctx) {
    // append the print stream before adding arguments
    classBytecodeWriter.appendPrintStream(codeHandler);
    String args = ctx.expression().getText();
    Integer var = handleParams(args);

    classBytecodeWriter.createPrintInvocation(codeHandler, var);
    return 0;
  }

  @Override
  public Integer visitScanfFunc(SimpleCParser.ScanfFuncContext ctx) {
    int localIndex = classBytecodeWriter.createScanInvocation(codeHandler);
    final String var = ctx.getParent().getParent().getStart().getText();
    setVar(var, localIndex);
    return 0;

  }

  /**
   * Process parameter values, handling arithmetic operations
   * appending operand instructions on the stack.
   *
   * @param expression the expression  (eg. A+B+C)
   * @return the var index if it's a single value, -1 if equation.
   */
  public Integer handleParams(String expression) {
    Matcher matcher = ARITHMETICS.matcher(expression);
    if (matcher.find()) {
      // reset position to zero and start parsing
      matcher.reset();
      while (matcher.find()) {
        String firstParam = matcher.group(3);
        String sign = matcher.group(4);
        String secondParam = matcher.group(5);

        // check for a previous equation on the stack
        String previousEquation = matcher.group(1);
        if (previousEquation == null) {
          Integer a = getParam(firstParam);
          Integer b = getParam(secondParam);
          classBytecodeWriter.addEquation(codeHandler, sign, a, b);
        } else {
          classBytecodeWriter.addEquation(codeHandler, previousEquation, getParam(firstParam));
          classBytecodeWriter.addEquation(codeHandler, sign, getParam(secondParam));
        }
      }
      return -1;
    }
    return getParam(expression);
  }

  private Integer getParam(final String paramName) {
    if (vars.containsKey(paramName)) {
      return vars.get(paramName);
    }
    // crash it otherwise!
    throw new RuntimeException("Parameter " + paramName + " has not been defined!");
  }

  void setVar(String var, int localIndex) {
    if (null == var || var.length() == 0) {
      throw new RuntimeException("Variable " + var + " cannot be null or empty.");
    }
    vars.put(var, localIndex);
  }

  @Override
  public Integer visitAdditiveExpression(SimpleCParser.AdditiveExpressionContext ctx) {
    int result = visit(ctx.multiplicativeExpression(0));
    for (int i = 1; i < ctx.multiplicativeExpression().size(); i++) {
      int value = visit(ctx.multiplicativeExpression(i));
      if (ctx.getChild(i * 2 - 1).getText().equals("+")) {
        result += value;
      } else {
        result -= value;
      }
    }
    return result;
  }

  @Override
  public Integer visitMultiplicativeExpression(SimpleCParser.MultiplicativeExpressionContext ctx) {
    int result = visit(ctx.primaryExpression(0));
    for (int i = 1; i < ctx.primaryExpression().size(); i++) {
      int value = visit(ctx.primaryExpression(i));
      String op = ctx.getChild(i * 2 - 1).getText();
      if (op.equals("*")) {
        result *= value;
      } else if (op.equals("/")) {
        result /= value;
      } else if (op.equals("%")) {
        result %= value;
      }
    }
    return result;
  }

  @Override
  public Integer visitPrimaryExpression(SimpleCParser.PrimaryExpressionContext ctx) {
    if (ctx.Number() != null) {
      return Integer.parseInt(ctx.Number().getText());
    } else if (ctx.Ident() != null) {
      String varName = ctx.Ident().getText();
      if (!variables.containsKey(varName)) {
        throw new RuntimeException("Variable " + varName + " is not defined.");
      }
      return variables.get(varName);
    } else if (ctx.expression() != null) {
      return visit(ctx.expression());
    }
    throw new RuntimeException("Unknown primary expression: " + ctx.getText());
  }

  /**
   * Concludes the process of adding bytecode to the method.
   */
  public void end() {
    this.classBytecodeWriter.end(this.codeHandler);
    try {
      this.classBytecodeWriter.write();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
