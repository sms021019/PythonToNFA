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

public class RubyToNFA implements Runnable {

    private List<String> regexes;
    private BufferedWriter writer;
    private AtomicInteger counter;
    // private int threadNumber;

    public RubyToNFA(ArrayList<String> regexes, BufferedWriter writer, AtomicInteger counter,
                       int threadNumber) {
        this.regexes = regexes;
        this.writer = writer;
        this.counter = counter;
        // this.threadNumber = threadNumber;

    }

    public static void main(String[] args) {
        ArgumentParser parser = ArgumentParsers.newFor("RubyToNFA").build()
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
                threads[i] = new Thread(new RubyToNFA(regexes, output, counter, i));
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
        String[] command = { "ruby", "run_and_extract.rb", "\""+regex+"\"", ">","output.log"};
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.directory(new File("/Users/my folder/ReDos/ruby_test"));
        processBuilder.redirectErrorStream(true);

        Process process;
        try {
            process = processBuilder.start();
            File outputFile = new File("/Users/my folder/ReDos/ruby_test/output.log");
            BufferedReader reader = new BufferedReader(new FileReader(outputFile));
            String line;
            String output = "";

            while ((line = reader.readLine()) != null) {
                System.out.println(line);
                output += line + "\n";
            }
            int exitCode = process.waitFor();
            entry.addProperty("ruby_output", output);
            if (exitCode != 0) {
                entry.addProperty("error", exitCode);
                return entry;
            }
            // System.out.println(output);
            Automaton a = rubyToNFA(output);

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


    static class RubyOpcode {
        int lineNumber;
        String instr;
        String text;
        int shift;

        RubyOpcode(int lineNumber, String instr, String text, int shift) {
            this.lineNumber = lineNumber;
            this.instr = instr;
            this.text = text;
            this.shift = shift;
        }

        @Override
        public String toString() {
            return "Instruction{" +
                    "lineNumber=" + lineNumber +
                    ", instr='" + instr +
                    ", text=" + text +
                    ", shift=" + shift +
                    '}';
        }
    }

//    private static final String RUBY_OPCODE_PATTERN = "(\\d+):[(\\w+):?(*.)]";
    private static final String RUBY_OPCODE_SHIFT = "\\(([-+]?\\d+)\\)";

    public static Automaton rubyToNFA(String opcodes) {
        System.out.println(opcodes);
        String[] lines = opcodes.split("\n");
        List<RubyOpcode> opcodeList = new ArrayList<>();
        Map<Integer, Integer> lineNumberToIndex = new HashMap<>();

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
//            Matcher matcher;

//            matcher = Pattern.compile(RUBY_OPCODE_PATTERN).matcher(line);
//            if (!matcher.find()) {
//                System.out.print("This line did not match with the opcode format:\n" + line);
//                throw new IllegalArgumentException();
//            }

            int lineNumber = Integer.parseInt(line.split(":")[0]);
            String opcode_content = line.substring(line.indexOf("[") + 1, line.lastIndexOf("]"));
            String[] opcode_details = opcode_content.split(":");


            String instr = opcode_details[0];
            String text = "";
            int shift = 0;

            switch (instr) {
                case ("exact1"):
                case ("exact2"):
                case ("exact3"):
                case ("exact4"):
                case ("exact5"):
                    text = opcode_details[1];
                    break;

                case ("exactn"):
                    text = opcode_details[2];
                    break;

                case ("push"):
                case ("jump"):
//                    System.out.println(opcode_details[1]);
                    shift = Integer.parseInt(opcode_details[1].substring(opcode_details[1].indexOf("(")+1, opcode_details[1].lastIndexOf(")")).replace("+", ""));
                    break;

                case ("end"):
                    break;
            }
            opcodeList.add(new RubyOpcode(lineNumber, instr, text, shift));
            lineNumberToIndex.put(lineNumber, opcodeList.size() - 1);
        }

        System.out.println();

        State[] states = new State[opcodeList.size()];
        for (int i = 0; i < states.length; i++) {
            states[i] = new State();
        }

        Automaton automaton = new Automaton();
        automaton.setInitialState(states[0]);

        int index = 0;
        while (index < opcodeList.size()) {
            RubyOpcode opcode = opcodeList.get(index);
            State from = states[lineNumberToIndex.get(opcode.lineNumber)];
            System.out.println("Current opcode: " + opcode);

            switch (opcode.instr) {
                case ("exact1"):
                    from.addTransition(new Transition(opcode.text.charAt(0), states[lineNumberToIndex.get(opcode.lineNumber) + 1]));
                    break;
                case ("exact2"):
                case ("exact3"):
                case ("exact4"):
                case ("exact5"):
                case ("exactn"):
                    State currentState = from;
                    char[] characters = opcode.text.toCharArray();

                    // Iterate over each character in the text
                    for (int i = 0; i < characters.length; i++) {
                        char c = characters[i];

                        // For the last character, transition to the next state
                        if (i == characters.length - 1) {
                            currentState.addTransition(new Transition(c, states[lineNumberToIndex.get(opcode.lineNumber) + 1]));
                        } else {
                            // For other characters, create an intermediate state and transition to it
                            State nextState = new State();
                            currentState.addTransition(new Transition(c, nextState));
                            currentState = nextState; // Move to the newly created state
                        }
                    }
                    break;

                case ("push"):
                    from.addTransition(new Transition(states[lineNumberToIndex.get(opcode.lineNumber) + 1]));
                case ("jump"):
                    RubyOpcode nextOpcode = opcodeList.get(index + 1);
                    int targetLineNumber = nextOpcode.lineNumber + opcode.shift;
                    from.addTransition(new Transition(states[lineNumberToIndex.get(targetLineNumber)]));
                    break;

                case ("end"):
                    from.setAccept(true);
                    break;
                default:
                    System.out.println("Unrecognized opcode: " + opcode.instr);
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
        return rubyToNFA(instructions);
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
            entry = RubyToNFA.runEngine(s, entry);
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