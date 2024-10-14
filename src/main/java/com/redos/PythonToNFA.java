package com.redos;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import dk.brics.automaton.Automaton;
import dk.brics.automaton.State;
import dk.brics.automaton.Transition;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;
import org.apache.commons.lang3.ObjectUtils;

public class PythonToNFA implements Runnable {

    private List<String> regexes;
    private BufferedWriter writer;
    private AtomicInteger counter;
    // private int threadNumber;

    public PythonToNFA(ArrayList<String> regexes, BufferedWriter writer, AtomicInteger counter,
                       int threadNumber) {
        this.regexes = regexes;
        this.writer = writer;
        this.counter = counter;
        // this.threadNumber = threadNumber;

    }

    public static void main(String[] args) {
        ArgumentParser parser = ArgumentParsers.newFor("PythonToNFA").build()
                .defaultHelp(true)
                .description("Rex");

        // Define arguments
        parser.addArgument("-t", "--threads")
                .help("number of threads")
                .setDefault(1).type(Integer.class);

        parser.addArgument("-i", "--input")
                .help("input file .txt or .json")
                .required(true)
                .type(String.class);

        parser.addArgument("-o", "--output")
                .help("output file .json")
                .required(true)
                .type(String.class);

        Namespace ns = null;
        try {
            // Parse arguments
            ns = parser.parseArgs(args);
        } catch (ArgumentParserException e) {
            parser.handleError(e);
            System.exit(1);
        }
        int numThreads = ns.getInt("threads");
        String fileName = ns.getString("input");
        ArrayList<String> regexes = new ArrayList<String>();
        Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

        Thread[] threads = new Thread[numThreads];
        BufferedReader reader;
        try {
            reader = new BufferedReader(new FileReader(fileName));
            // cnt = 0;
            String line = reader.readLine();
            while (line != null) {
                if (fileName.endsWith("txt")) {
                    // logger.log(Logger.DEBUG, line);
                    regexes.add(line);
                } else if (fileName.endsWith(".json")) {
                    // System.out.println(line.replace("\\", "\\"));
                    RegexDatasetEntry entry = gson.fromJson(line, RegexDatasetEntry.class);
                    regexes.add(entry.pattern.replace("\\", "\\\\"));
                    // logger.log(Logger.DEBUG, entry.pattern);
                    // break;
                }
                line = reader.readLine();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        BufferedWriter output = null;
        AtomicInteger counter = new AtomicInteger(0); // Shared counter
        try {
            File file = new File(ns.getString("output"));
            output = new BufferedWriter(new FileWriter(file, false));
            for (int i = 0; i < numThreads; i++) {
                threads[i] = new Thread(new PythonToNFA(regexes, output, counter, i));
                threads[i].start();
            }
            for (Thread thread : threads) {
                thread.join(); // Wait for all threads to finish
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            if (output != null) {
                try {
                    output.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

    }

    private static JsonObject runEngine(String regex, JsonObject entry) {
        // Prepare the command to run the Python script
        regex = regex.replace("\\\\", "\\");
//        String[] command = { "python", "sre_debug.py", regex};
        String[] command = { "./python.exe", "sre_debug.py", regex};
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.directory(new File("/Users/my folder/ReDos/CPython/cpython"));
//        processBuilder.directory(new File("/home/minseok/cpython/"));
        processBuilder.redirectErrorStream(true);

        Process process;
        try {
            process = processBuilder.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            String output = "";
            boolean capture = false;
            while ((line = reader.readLine()) != null) {
                if (line.equals("END")) {
                    capture = false;
                    System.out.println(line);
                    break;
                }
                if (capture) {
                    output += line + "\n";
                }
                if (line.equals("BEGIN")) {
                    capture = true;
                }
                System.out.println(line);
            }
            int exitCode = process.waitFor();
            entry.addProperty("python_output", output);
            if (exitCode != 0) {
                entry.addProperty("error", exitCode);
                return entry;
            }
            // System.out.println(output);
            Automaton a = pythonToNFA(output);
            if (a == null) {
                entry.addProperty("error", "NFA compile failed");
                return entry;
            }
            System.out.println(a.getShortestExample(true) + " " + a.getShortestExample(true).length());
            System.out.println(regex);
            if (a.getNumberOfStates() > 100) {
                entry.addProperty("error", "Too many states");
                return entry;
            }
            JsonObject j = Main.runRex(a, entry);
            return j;
            // System.out.println("Exited with code: " + exitCode);
        } catch (Exception e) {
            e.printStackTrace();
            entry.addProperty("error", e.getMessage());
        }
        return null;
    }

    static class Instruction {
        int lineNumber;
        String opcode;
        Integer toState;
        Integer min;
        Integer max;
        Character literal;
        Character literal2;
        String category;

        Instruction(int lineNumber, String opcode, Integer toState, Integer min, Integer max, Character literal, Character literal2, String category) {
            this.lineNumber = lineNumber;
            this.opcode = opcode;
            this.toState = toState;
            this.min = min;
            this.max = max;
            this.literal = literal;
            this.literal2 = literal2;
            this.category = category;
        }

        @Override
        public String toString() {
            return "Instruction{" +
                    "lineNumber=" + lineNumber +
                    ", opcode='" + opcode + '\'' +
                    ", toState=" + toState +
                    ", min=" + min +
                    ", max=" + max +
                    ", literal=" + literal +
                    ", literal2=" + literal2 +
                    '}';
        }
    }

    private static final String BRANCH_PATTERN = "(?i)\\s*(\\d+)[.:]?\\s*BRANCH\\s+(\\d+)\\s*\\(to\\s*(\\d+)\\)";
    private static final String MARK_PATTERN = "\\s*(\\d+)[.:]?\\s*MARK\\s+(\\d+)";
    private static final String IN_IGNORE_PATTERN = "\\s*(\\d+)[.:]?\\s*IN_IGNORE\\s+(\\d+)\\s*\\(to\\s*(\\d+)\\)";
    private static final String LITERAL_PATTERN = "\\s*(\\d+)[.:]\\s*LITERAL\\s+0x([0-9a-f]+)\\s*\\(['\"](.+)['\"]\\)";
    private static final String NOT_LITERAL_PATTERN = "\\s*(\\d+)[.:]\\s*NOT_LITERAL\\s+0x([0-9a-f]+)\\s*\\(.*\\)";
    private static final String REPEAT_ONE_PATTERN = "\\s*(\\d+)[.:]?\\s*(MIN_)?REPEAT_ONE\\s+(\\d+)\\s+(\\d+)\\s+(\\d+|MAXREPEAT)\\s*\\(to\\s*(\\d+)\\)";
    private static final String REPEAT_PATTERN = "\\s*(\\d+)[.:]?\\s*(MIN_)?REPEAT\\s+(\\d+)\\s+(\\d+)\\s+(\\d+|MAXREPEAT)\\s*\\(to\\s*(\\d+)\\)";
    private static final String SUCCESS_PATTERN = "\\s*(\\d+)[.:]?\\s*SUCCESS";
    private static final String FAILURE_PATTERN = "\\s*(\\d+)[.:]?\\s*FAILURE";
    private static final String JUMP_PATTERN = "\\s*(\\d+)[.:]?\\s*JUMP\\s+(\\d+)\\s*\\(to\\s*(\\d+)\\)";
    private static final String ANY_PATTERN = "\\s*(\\d+)[.:]?\\s*ANY";
    private static final String CATEGORY_PATTERN = "\\s*(\\d+)[.:]?\\s*CATEGORY\\s+([A-Z_]+)";
    private static final String MAX_UNTIL_PATTERN = "\\s*(\\d+)[.:]?\\s*(MIN|MAX)_UNTIL";
    private static final String RANGE_PATTERN = "\\s*(\\d+)[.:]?\\s*RANGE\\s+0x([0-9a-f]+)\\s+0x([0-9a-f]+)\\s+\\(.*\\)";
    private static final String NEGATE_PATTERN = "\\s*(\\d+)[.:]?\\s*NEGATE";
    private static final String AT_PATTERN = "\\s*(\\d+)[.:]?\\s*AT\\s+([A-Z]+)";
    public static Automaton pythonToNFA(String instructions) {
        String[] lines = instructions.split("\n");
        List<Instruction> instructionList = new ArrayList<>();
        Map<Integer, Integer> lineNumberToIndex = new HashMap<>();

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            Matcher matcher;

            if ((matcher = Pattern.compile(BRANCH_PATTERN).matcher(line)).matches()) {
                instructionList.add(new Instruction(
                        Integer.parseInt(matcher.group(1)),
                        "BRANCH",
                        Integer.parseInt(matcher.group(3)),
                        null, null, null, null, null));
            } else if ((matcher = Pattern.compile(MARK_PATTERN).matcher(line)).matches()) {
                instructionList.add(new Instruction(
                        Integer.parseInt(matcher.group(1)),
                        "MARK",
                        null, null, null, null, null, null));
            } else if ((matcher = Pattern.compile(IN_IGNORE_PATTERN).matcher(line)).matches()) {
                instructionList.add(new Instruction(
                        Integer.parseInt(matcher.group(1)),
                        "IN_IGNORE",
                        Integer.parseInt(matcher.group(3)),
                        null, null, null, null, null));
            } else if ((matcher = Pattern.compile(LITERAL_PATTERN).matcher(line)).matches()) {
                String hexValue = matcher.group(2);
                int codePoint = Integer.parseInt(hexValue, 16);
                char lit = (char) codePoint;
//                Character lit = convertToCharacter(matcher.group(3));
                instructionList.add(new Instruction(
                        Integer.parseInt(matcher.group(1)),
                        "LITERAL",
                        null, null, null, lit, null, null));
            } else if ((matcher = Pattern.compile(NOT_LITERAL_PATTERN).matcher(line)).matches()) {
                String hexValue = matcher.group(2);
                int codePoint = Integer.parseInt(hexValue, 16);
                char lit = (char) codePoint;
//                Character lit = convertToCharacter(matcher.group(3));
                instructionList.add(new Instruction(
                        Integer.parseInt(matcher.group(1)),
                        "NOT_LITERAL",
                        null, null, null, lit, null, null));
            }else if ((matcher = Pattern.compile(REPEAT_ONE_PATTERN).matcher(line)).matches()) {
                instructionList.add(new Instruction(
                        Integer.parseInt(matcher.group(1)),
                        "REPEAT_ONE",
                        Integer.parseInt(matcher.group(6)),
                        Integer.parseInt(matcher.group(4)),
                        "MAXREPEAT".equals(matcher.group(5)) ? Integer.MAX_VALUE : Integer.parseInt(matcher.group(5)), null, null, null));
            } else if ((matcher = Pattern.compile(REPEAT_PATTERN).matcher(line)).matches()) {
                instructionList.add(new Instruction(
                        Integer.parseInt(matcher.group(1)),
                        "REPEAT",
                        Integer.parseInt(matcher.group(6)),
                        Integer.parseInt(matcher.group(4)),
                        "MAXREPEAT".equals(matcher.group(5)) ? Integer.MAX_VALUE : Integer.parseInt(matcher.group(5)), null, null, null));
            } else if ((matcher = Pattern.compile(SUCCESS_PATTERN).matcher(line)).matches()) {
                instructionList.add(new Instruction(
                        Integer.parseInt(matcher.group(1)),
                        "SUCCESS",
                        null, null, null, null, null, null));
            } else if ((matcher = Pattern.compile(FAILURE_PATTERN).matcher(line)).matches()) {
                instructionList.add(new Instruction(
                        Integer.parseInt(matcher.group(1)),
                        "FAILURE",
                        null, null, null, null, null, null));
            } else if ((matcher = Pattern.compile(JUMP_PATTERN).matcher(line)).matches()) {
                instructionList.add(new Instruction(
                        Integer.parseInt(matcher.group(1)),
                        "JUMP",
                        Integer.parseInt(matcher.group(3)),
                        null, null, null, null, null));
            } else if ((matcher = Pattern.compile(ANY_PATTERN).matcher(line)).matches()) {
                instructionList.add(new Instruction(
                        Integer.parseInt(matcher.group(1)),
                        "ANY",
                        null, null, null, null, null, null));
            } else if ((matcher = Pattern.compile(CATEGORY_PATTERN).matcher(line)).matches()) {
                instructionList.add(new Instruction(
                        Integer.parseInt(matcher.group(1)),
                        "CATEGORY",
                        null, null, null, null, null, matcher.group(2)));
            } else if ((matcher = Pattern.compile(MAX_UNTIL_PATTERN).matcher(line)).matches()) {
                instructionList.add(new Instruction(
                        Integer.parseInt(matcher.group(1)),
                        "MAX_UNTIL",
                        null, null, null, null, null, null));
            } else if ((matcher = Pattern.compile(RANGE_PATTERN).matcher(line)).matches()) {
                String hexValue1 = matcher.group(2);
                String hexValue2 = matcher.group(3);
                int codePoint1 = Integer.parseInt(hexValue1, 16);
                int codePoint2 = Integer.parseInt(hexValue2, 16);

                char lit1 = (char) codePoint1;
                char lit2 = (char) codePoint2;

//                Character lit1 = convertToCharacter(matcher.group(4));
//                Character lit2 = convertToCharacter(matcher.group(5));
                instructionList.add(new Instruction(
                        Integer.parseInt(matcher.group(1)),
                        "RANGE",
                        null, null, null, lit1, lit2, null));
            } else if ((matcher = Pattern.compile(NEGATE_PATTERN).matcher(line)).matches()) {
                instructionList.add(new Instruction(
                        Integer.parseInt(matcher.group(1)),
                        "NEGATE",
                        null, null, null, null, null, null));
            } else if ((matcher = Pattern.compile(AT_PATTERN).matcher(line)).matches()) {
                instructionList.add(new Instruction(
                        Integer.parseInt(matcher.group(1)),
                        "AT",
                        null, null, null, null, null, null));
            } else {
                System.out.println("Unknown line: " + line);
                return null;
            }
            lineNumberToIndex.put(Integer.parseInt(matcher.group(1)), instructionList.size() - 1);
        }

        System.out.println();

        State[] states = new State[instructionList.size()];
        for (int i = 0; i < states.length; i++) {
            states[i] = new State();
        }

        Automaton automaton = new Automaton();
        automaton.setInitialState(states[0]);

        int index = 0;
        while (index < instructionList.size()) {
            Instruction instr = instructionList.get(index);
            State from = states[lineNumberToIndex.get(instr.lineNumber)];
            System.out.println("Current instruction: " + instr);

            switch (instr.opcode) {
                case "BRANCH":
                    Instruction branch_target = instructionList.get(lineNumberToIndex.get(instr.toState));
                    if(branch_target.opcode != "FAILURE") {
                        from.addTransition(new Transition(states[lineNumberToIndex.get(instr.toState)]));
                    }
                    from.addTransition(new Transition(states[lineNumberToIndex.get(instr.lineNumber) + 1]));
                    break;
                case "MARK":
                case "AT":
                    from.addTransition(new Transition(states[lineNumberToIndex.get(instr.lineNumber) + 1]));
                    break;
                case "IN_IGNORE":
                    handleIn(states, instructionList, instr, lineNumberToIndex);
//                    System.out.println("Automaton after IN_IGNORE: " + automaton.toDot());
                    index = lineNumberToIndex.get(instr.toState) - 1;
                    break;
                case "LITERAL":
                    from.addTransition(new Transition(instr.literal, states[lineNumberToIndex.get(instr.lineNumber) + 1]));
                    break;
                case "NOT_LITERAL":
                    addComplementTransitions(from, states[lineNumberToIndex.get(instr.lineNumber) + 1], instr.literal.toString());
                    break;
                case "REPEAT_ONE":
                    State endOfRepeatOne = handleRepeat(states, lines, instr, true, lineNumberToIndex);
//                    System.out.println("Automaton after REPEAT_ONE: " + automaton.toDot());
                    index = lineNumberToIndex.get(instr.toState) - 1;
                    endOfRepeatOne.addTransition(new Transition(states[index + 1]));
                    break;
                case "REPEAT":
                    State endOfRepeat = handleRepeat(states, lines, instr, false, lineNumberToIndex);
                    System.out.println("Automaton after REPEAT: " + automaton.toDot());
                    index = lineNumberToIndex.get(instr.toState);
                    endOfRepeat.addTransition(new Transition(states[index + 1]));
                    break;
                case "SUCCESS":
                    from.setAccept(true);
                    break;
                case "FAILURE":
                    // Handle FAILURE as a no-op
                    break;
                case "JUMP":
                    from.addTransition(new Transition(states[lineNumberToIndex.get(instr.toState)]));
                    break;
                case "ANY":
                    from.addTransition(new Transition(Character.MIN_VALUE, Character.MAX_VALUE, states[lineNumberToIndex.get(instr.lineNumber) + 1]));
                    break;
                case "CATEGORY":
                    handleCategory(from, states[lineNumberToIndex.get(instr.lineNumber) + 1], instr);
                    break;
                case "MAX_UNTIL":
                    if(index < instructionList.size() - 1) {        // FIXME
                        from.addTransition(new Transition(states[lineNumberToIndex.get(instr.lineNumber) + 1]));
                    }
                    break;
                case "RANGE":
                    from.addTransition(new Transition(instr.literal, instr.literal2, states[lineNumberToIndex.get(instr.lineNumber) + 1]));
                    break;
                default:
                    System.out.println("Unrecognized opcode: " + instr.opcode);
                    break;
            }

            index++;
        }

        System.out.println(automaton.toDot());
        return automaton;
    }

    // ---------------- Helper Functions ----------------

    private static void handleCategory(State from, State to, Instruction instruction) {
        String category = instruction.category;

        switch (category) {
            case "WORD":
            case "UNI_WORD":
                from.addTransition(new Transition('A', 'Z', to));
                from.addTransition(new Transition('a', 'z', to));
                from.addTransition(new Transition('0', '9', to));
                from.addTransition(new Transition('_', '_', to));
                break;
            case "UNI_SPACE":
                from.addTransition(new Transition(' ', to));
                from.addTransition(new Transition('\t', to)); // Tab
                from.addTransition(new Transition('\n', to)); // Newline
                from.addTransition(new Transition('\r', to)); // Carriage return
                from.addTransition(new Transition('\f', to)); // Form feed
                from.addTransition(new Transition('\u000b', to)); // Vertical tab
                break;
            case "UNI_DIGIT":
                from.addTransition(new Transition('0', '9', to));
                break;
            case "UNI_NOT_WORD":
                addComplementTransitions(from, to, "A-Z a-z 0-9 _");
                break;
            case "UNI_NOT_SPACE":
                addComplementTransitions(from, to, " \t\n\r\f\u000b");
                break;
            case "UNI_NOT_DIGIT":
                addComplementTransitions(from, to, "0-9");
                break;
            default:
                throw new IllegalArgumentException("Unknown category: " + instruction.category);
        }
    }

    private static void addComplementTransitions(State from, State to, String charClasses) {
        Automaton union = Automaton.makeEmpty();

        if (charClasses.equals(" ")) {
            // Handle the case where the character class is a space
            union = union.union(Automaton.makeChar(' '));
        } else {
            for (String c : charClasses.split(" ")) {
                char value1;
                char value2;
                if (c.length() == 1) {
                    value1 = c.charAt(0);
                    value2 = c.charAt(0);
                } else {
                    value1 = c.charAt(0);
                    value2 = c.charAt(2);
                }
                union = union.union(Automaton.makeCharRange(value1, value2));
            }
        }

        // Complement the automaton
        Automaton complement = union.complement();

        // Add transitions from the complemented automaton to the specified state
        for (Transition q : complement.getInitialState().getTransitions()) {
            if (q.getDest().isAccept()) {
                from.addTransition(new Transition(q.getMin(), q.getMax(), to));
            }
        }
    }

    private static void handleIn(State[] states, List<Instruction> instructions, Instruction instr, Map<Integer, Integer> lineNumberToIndex) {
        int toIndex = lineNumberToIndex.get(instr.toState);
        Automaton a = new Automaton();
        State from = states[lineNumberToIndex.get(instr.lineNumber)];
        State to = states[toIndex];

        List<Instruction> inInstructions = new ArrayList<>();
        for (int i = lineNumberToIndex.get(instr.lineNumber) + 1; i < toIndex - 1; i++) {
            inInstructions.add(instructions.get(i));
        }

//        System.out.println("inInstructions: " + inInstructions);
        boolean isNegate = false;
        for(Instruction instruction : inInstructions) {
            switch (instruction.opcode) {
                case "NEGATE":
                    isNegate = true;
                    break;
                case "LITERAL":
                    a = a.union(Automaton.makeChar(instruction.literal));
                    break;
                case "RANGE":
                    a = a.union(Automaton.makeCharRange(instruction.literal, instruction.literal2));
                    break;
                case "CATEGORY":
                    if (instruction.category.contains("NOT")) {
                        isNegate = true;
                    }
                    a = handleCategoryUnion(a, instruction);
                    break;
                default:
                    System.out.println("Unrecognized opcode: " + instruction.opcode);
                    break;
            }
        }
        if (isNegate) {
            a = a.complement();
        }

        for (Transition q : a.getInitialState().getTransitions()) {
            if (q.getDest().isAccept()) {
                from.addTransition(new Transition(q.getMin(), q.getMax(), to));
            }
        }
    }

    private static Automaton handleCategoryUnion (Automaton a, Instruction instr) {
        String category = instr.category;

        switch (category) {
            case "WORD":
            case "UNI_WORD":
            case "UNI_NOT_WORD":
                a = a.union(Automaton.makeCharRange('A', 'Z'));
                a = a.union(Automaton.makeCharRange('a', 'z'));
                a = a.union(Automaton.makeCharRange('0', '9'));
                a = a.union(Automaton.makeCharRange('_', '_'));
                break;
            case "UNI_SPACE":
            case "UNI_NOT_SPACE":
                a = a.union(Automaton.makeChar(' '));
                a = a.union(Automaton.makeChar('\t'));
                a = a.union(Automaton.makeChar('\n'));
                a = a.union(Automaton.makeChar('\r'));
                a = a.union(Automaton.makeChar('\f'));
                a = a.union(Automaton.makeChar('\u000b'));
                break;
            case "UNI_DIGIT":
            case "UNI_NOT_DIGIT":
                a = a.union(Automaton.makeCharRange('0', '9'));
                break;
            default:
                throw new IllegalArgumentException("Unknown category: " + instr.category);
        }
        return a;
    }

    private static State handleRepeat(State[] states, String[] lines, Instruction instr, boolean isRepeatOne, Map<Integer, Integer> lineNumberToIndex) {
        int min = instr.min;
        int max = instr.max;
        int toIndex = isRepeatOne ? lineNumberToIndex.get(instr.toState) - 1 : lineNumberToIndex.get(instr.toState);
        State from = states[lineNumberToIndex.get(instr.lineNumber)];
        State endOfRepeat = states[toIndex];
        State lastRepeat = new State();

        List<String> repeatInstructions = new ArrayList<>();
        for (int i = lineNumberToIndex.get(instr.lineNumber) + 1; i <= toIndex; i++) {
            repeatInstructions.add(lines[i]);
        }

        System.out.println("repeatInstructions: " + repeatInstructions);
        Automaton repeatedAutomaton = buildAutomatonFromInstructions(String.join("\n", repeatInstructions));

        if (min > 0) {
            lastRepeat = appendAutomatonMultipleTimes(from, repeatedAutomaton, min - 1);
            from = appendAutomatonMultipleTimes(lastRepeat, repeatedAutomaton, 1);
        } else {
            from = appendAutomatonMultipleTimes(from, repeatedAutomaton, min);
        }

        if (max != Integer.MAX_VALUE) {
            State maxEndState = new State();
            for (int j = min; j < max; j++) {
                from.addTransition(new Transition(maxEndState));
                from = appendAutomatonMultipleTimes(from, repeatedAutomaton, 1);
            }
            from.addTransition(new Transition(maxEndState));
            return maxEndState;
        } else {
            // Handle MAXREPEAT case
            if (min > 0) {
                from.addTransition(new Transition(lastRepeat));
                return from;
            }
            State loopState = new State();
            from.addTransition(new Transition(loopState));
            endOfRepeat = appendAutomatonMultipleTimes(loopState, repeatedAutomaton, 1);
            endOfRepeat.addTransition(new Transition(loopState));
            return loopState;
        }
    }

    private static State appendAutomatonMultipleTimes(State fromState, Automaton automatonToAppend, int times) {
        State currentState = fromState;

        for (int i = 0; i < times; i++) {
            // Clone the automaton to append
            Automaton clonedAutomaton = automatonToAppend.clone();

            // Get the initial state of the cloned automaton
            State initialClonedState = clonedAutomaton.getInitialState();

            // Map from original states to cloned states to handle transitions correctly
            Map<State, State> stateMapping = new HashMap<>();
            Set<State> clonedStates = clonedAutomaton.getStates();
            for (State state : clonedStates) {
                stateMapping.put(state, new State());
            }

            // Copy transitions to the new states
            for (State state : clonedStates) {
                State newState = stateMapping.get(state);
                for (Transition transition : state.getTransitions()) {
                    if (transition.isEpsilon()) {
                        newState.addTransition(new Transition(stateMapping.get(transition.getDest())));
                    } else {
                        newState.addTransition(new Transition(transition.getMin(), transition.getMax(), stateMapping.get(transition.getDest())));
                    }
                }
            }

            // Append the cloned automaton from the current state
            currentState.addTransition(new Transition(stateMapping.get(initialClonedState)));

            // Update current state to the last state of the cloned automaton
            currentState = stateMapping.get(getLastState(clonedAutomaton));
        }
        return currentState;
    }

    private static State getLastState(Automaton automaton) {
        Set<State> states = automaton.getStates();
        State lastState = null;
        for (State state : states) {
            if (state.getTransitions().isEmpty()) {
                lastState = state;
            }
        }
        return lastState;
    }

    private static Automaton buildAutomatonFromInstructions(String instructions) {
        // Recursively build the automaton from instructions
        return pythonToNFA(instructions);
    }


    @Override
    public void run() {
        Gson gson = new GsonBuilder().disableHtmlEscaping().create();
        int line = counter.getAndIncrement();
        while (line < regexes.size()) {
            String s = regexes.get(line);
            System.out.println(s);
            JsonObject entry = new JsonObject();
            entry.addProperty("count", line);
            entry.addProperty("pattern", s);
            entry.addProperty("formatted_pattern", s.replace("\\\\", "\\"));
            entry = PythonToNFA.runEngine(s, entry);
            try {
                try {
                    synchronized (writer) {
                        writer.write(gson.toJson(entry));// .replace("\\\\", "\\"));
                    }
                } finally {
                    writer.newLine();
                    line = counter.getAndIncrement();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

        }
    }
}