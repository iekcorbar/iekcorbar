package com.iekcorbar.steamworks;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

/**
 * The Karakuri Arm's little language (one instruction per line, # = comment,
 * case does not matter). Points are the numbers taught to the arm (1-8).
 *
 *   PICK p [count]     take items from point p into the claw (default a stack)
 *   DROP p             put the claw's items into point p
 *   PLACE p            place the block in the claw at point p      (placer head)
 *   USE p              use the claw's item on point p (bone meal)  (placer head)
 *   BREAK p            break the block at point p                  (drill head)
 *   WAIT seconds | WAIT REDSTONE | WAIT ITEM p
 *   LEVER p ON|OFF     flip a lever at point p
 *   LOOP ... END       forever
 *   REPEAT n ... END   n times
 *   IF EMPTY p | IF FULL p | IF HAS p | IF REDSTONE | IF HOLDING ... END
 *
 * The program starts again from the top when it reaches the end.
 */
final class ArmProgram {
    enum Code { PICK, DROP, PLACE, USE, BREAK, WAIT, WAIT_REDSTONE, WAIT_ITEM, LEVER, LOOP, REPEAT, IF_EMPTY, IF_FULL, IF_HAS, IF_REDSTONE, IF_HOLDING, END }

    /** a/b: arguments; jump: matching END for block starts, matching start for END. */
    record Op(Code code, int a, int b, int line, int jump) {
        Op withJump(int j) {
            return new Op(code, a, b, line, j);
        }

        boolean blockStart() {
            return code == Code.LOOP || code == Code.REPEAT || code.name().startsWith("IF_");
        }
    }

    final List<Op> ops;

    private ArmProgram(List<Op> ops) {
        this.ops = ops;
    }

    static final class ProgramError extends Exception {
        ProgramError(int line, String msg) {
            super("line " + line + ": " + msg);
        }
    }

    static ArmProgram compile(String source, int maxPoint) throws ProgramError {
        List<Op> ops = new ArrayList<>();
        Deque<Integer> open = new ArrayDeque<>();
        String[] lines = source == null ? new String[0] : source.split("\\R");
        for (int i = 0; i < lines.length; i++) {
            int ln = i + 1;
            String line = lines[i];
            int hash = line.indexOf('#');
            if (hash >= 0) line = line.substring(0, hash);
            line = line.trim();
            if (line.isEmpty()) continue;
            String[] w = line.toUpperCase(Locale.ROOT).split("\\s+");
            Op op = switch (w[0]) {
                case "PICK" -> new Op(Code.PICK, point(w, 1, ln, maxPoint), w.length > 2 ? num(w[2], ln, 1, 64) : 64, ln, -1);
                case "DROP" -> new Op(Code.DROP, point(w, 1, ln, maxPoint), 0, ln, -1);
                case "PLACE" -> new Op(Code.PLACE, point(w, 1, ln, maxPoint), 0, ln, -1);
                case "USE" -> new Op(Code.USE, point(w, 1, ln, maxPoint), 0, ln, -1);
                case "BREAK" -> new Op(Code.BREAK, point(w, 1, ln, maxPoint), 0, ln, -1);
                case "LEVER" -> {
                    if (w.length < 3 || !(w[2].equals("ON") || w[2].equals("OFF"))) throw new ProgramError(ln, "LEVER needs a point and ON or OFF");
                    yield new Op(Code.LEVER, point(w, 1, ln, maxPoint), w[2].equals("ON") ? 1 : 0, ln, -1);
                }
                case "WAIT" -> {
                    if (w.length < 2) throw new ProgramError(ln, "WAIT needs seconds, REDSTONE or ITEM p");
                    if (w[1].equals("REDSTONE")) yield new Op(Code.WAIT_REDSTONE, 0, 0, ln, -1);
                    if (w[1].equals("ITEM")) yield new Op(Code.WAIT_ITEM, point(w, 2, ln, maxPoint), 0, ln, -1);
                    double secs;
                    try {
                        secs = Double.parseDouble(w[1]);
                    } catch (NumberFormatException e) {
                        throw new ProgramError(ln, "'" + w[1] + "' is not a number of seconds");
                    }
                    if (secs < 0 || secs > 3600) throw new ProgramError(ln, "WAIT seconds must be 0-3600");
                    yield new Op(Code.WAIT, (int) Math.round(secs * 20), 0, ln, -1);
                }
                case "LOOP" -> new Op(Code.LOOP, 0, 0, ln, -1);
                case "REPEAT" -> new Op(Code.REPEAT, w.length > 1 ? num(w[1], ln, 0, 10000) : bad(ln, "REPEAT needs a count"), 0, ln, -1);
                case "IF" -> {
                    if (w.length < 2) throw new ProgramError(ln, "IF needs EMPTY p, FULL p, HAS p, REDSTONE or HOLDING");
                    yield switch (w[1]) {
                        case "EMPTY" -> new Op(Code.IF_EMPTY, point(w, 2, ln, maxPoint), 0, ln, -1);
                        case "FULL" -> new Op(Code.IF_FULL, point(w, 2, ln, maxPoint), 0, ln, -1);
                        case "HAS" -> new Op(Code.IF_HAS, point(w, 2, ln, maxPoint), 0, ln, -1);
                        case "REDSTONE" -> new Op(Code.IF_REDSTONE, 0, 0, ln, -1);
                        case "HOLDING" -> new Op(Code.IF_HOLDING, 0, 0, ln, -1);
                        default -> throw new ProgramError(ln, "unknown IF condition '" + w[1] + "'");
                    };
                }
                case "END" -> {
                    if (open.isEmpty()) throw new ProgramError(ln, "END without LOOP, REPEAT or IF");
                    int start = open.pop();
                    ops.set(start, ops.get(start).withJump(ops.size()));
                    yield new Op(Code.END, 0, 0, ln, start);
                }
                default -> throw new ProgramError(ln, "unknown instruction '" + w[0] + "'");
            };
            if (op.blockStart()) open.push(ops.size());
            ops.add(op);
            if (ops.size() > 512) throw new ProgramError(ln, "program too long (max 512 instructions)");
        }
        if (!open.isEmpty()) throw new ProgramError(ops.get(open.peek()).line(), ops.get(open.peek()).code() + " has no END");
        return new ArmProgram(ops);
    }

    private static int bad(int ln, String msg) throws ProgramError {
        throw new ProgramError(ln, msg);
    }

    private static int point(String[] w, int i, int ln, int maxPoint) throws ProgramError {
        if (w.length <= i) throw new ProgramError(ln, w[0] + " needs a point number");
        int p = num(w[i], ln, 1, 8);
        if (p > maxPoint) throw new ProgramError(ln, "point " + p + " is not taught yet (the arm has " + maxPoint + ")");
        return p;
    }

    private static int num(String s, int ln, int min, int max) throws ProgramError {
        try {
            int v = Integer.parseInt(s);
            if (v < min || v > max) throw new ProgramError(ln, s + " must be " + min + "-" + max);
            return v;
        } catch (NumberFormatException e) {
            throw new ProgramError(ln, "'" + s + "' is not a number");
        }
    }

    /** Default behaviour when no program is loaded, from the tool head and taught points. */
    static String fallback(MaterialType tool, int points) {
        if (points == 0 || tool == null) return "";
        StringBuilder sb = new StringBuilder();
        switch (tool) {
            case ARM_DRILL -> {
                for (int p = 1; p <= points; p++) sb.append("BREAK ").append(p).append('\n');
            }
            case ARM_PLACER -> {
                if (points < 2) return "";
                sb.append("IF HOLDING\n");
                for (int p = 2; p <= points; p++) sb.append("PLACE ").append(p).append('\n');
                sb.append("END\nPICK 1 ").append(points - 1).append('\n');
            }
            default -> {
                if (points < 2) return "";
                for (int p = 2; p <= points; p++) sb.append("PICK 1\nDROP ").append(p).append('\n');
            }
        }
        return sb.toString();
    }
}
