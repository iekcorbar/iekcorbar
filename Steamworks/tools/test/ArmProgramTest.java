package com.iekcorbar.steamworks;

/** Offline checks of the Karakuri Arm language compiler (no server). */
public class ArmProgramTest {
    static int fails = 0;

    static void check(String what, boolean ok) {
        if (!ok) fails++;
        System.out.printf("%s %s%n", ok ? "OK  " : "FAIL", what);
    }

    static String error(String src, int points) {
        try {
            ArmProgram.compile(src, points);
            return null;
        } catch (ArmProgram.ProgramError e) {
            return e.getMessage();
        }
    }

    public static void main(String[] args) throws Exception {
        String demo = """
                # sort: wait for a signal, move a stack, flag when full
                LOOP
                  WAIT REDSTONE
                  PICK 1 16
                  DROP 3
                  IF FULL 3
                    LEVER 4 ON
                  END
                  REPEAT 2
                    WAIT 0.5
                  END
                END
                """;
        ArmProgram p = ArmProgram.compile(demo, 4);
        check("demo compiles to 11 ops", p.ops.size() == 11);
        check("LOOP jumps to its END", p.ops.get(0).jump() == 10 && p.ops.get(10).jump() == 0);
        check("IF jumps past its END", p.ops.get(4).code() == ArmProgram.Code.IF_FULL && p.ops.get(4).jump() == 6);
        check("PICK count parsed", p.ops.get(2).b() == 16);
        check("WAIT 0.5 = 10 ticks", p.ops.get(8).a() == 10);
        check("lower case works", error("pick 1\ndrop 2", 2) == null);

        check("unknown instruction reports its line", "line 2: unknown instruction 'JUMP'".equals(error("PICK 1\nJUMP 3", 2)));
        check("point not taught", error("DROP 5", 2) != null && error("DROP 5", 2).contains("not taught"));
        check("END without block", error("END", 1) != null);
        check("missing END", error("LOOP\nPICK 1", 1) != null && error("LOOP\nPICK 1", 1).contains("no END"));
        check("LEVER needs ON/OFF", error("LEVER 1 MAYBE", 1) != null);
        check("WAIT needs a number", error("WAIT soon", 1) != null);

        check("default gripper program", ArmProgram.compile(ArmProgram.fallback(MaterialType.ARM_GRIPPER, 3), 3).ops.size() == 4);
        check("default drill program", ArmProgram.compile(ArmProgram.fallback(MaterialType.ARM_DRILL, 2), 2).ops.size() == 2);
        check("default placer program", ArmProgram.compile(ArmProgram.fallback(MaterialType.ARM_PLACER, 3), 3).ops.size() == 5);
        check("no points = no default", ArmProgram.fallback(MaterialType.ARM_GRIPPER, 0).isEmpty());

        System.out.println(fails == 0 ? "ALL PASSED" : fails + " FAILED");
        System.exit(fails == 0 ? 0 : 1);
    }
}
