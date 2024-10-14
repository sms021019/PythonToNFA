package com.redos;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.HashMap;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import dk.brics.automaton.Automaton;
import dk.brics.automaton.RegExp;
import dk.brics.automaton.State;
import dk.brics.automaton.Transition;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;

public class Main implements Runnable {
    private static Logger logger = new Logger(Logger.DEBUG);
    private List<String> regexes;
    private BufferedWriter writer;
    private AtomicInteger counter;
    // private int threadNumber;

    public static void main(String[] args) {
        ArgumentParser parser = ArgumentParsers.newFor("main").build()
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
        parser.addArgument("-l", "--log")
                .help("log level 0-3")
                .setDefault(2)
                .type(Integer.class);
                
        Namespace ns = null;
        try {
            // Parse arguments
            ns = parser.parseArgs(args);
        } catch (ArgumentParserException e) {
            parser.handleError(e);
            System.exit(1);
        }

        // Retrieve argument values
        int numThreads = ns.getInt("threads");
        int maxLines = 100;
        logger = new Logger(ns.getInt("log"));
        logger.log(Logger.INFO, "Welcome to rex!");
        if (args == null || args.length == 0) {
            logger.log(Logger.ERROR, "Error: no argument was given");
            System.exit(1);
        }
        String fileName = ns.getString("input");
        logger.log(Logger.INFO, fileName);
        BufferedReader reader;
        ArrayList<String> regexes = new ArrayList<String>();
        Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

        Thread[] threads = new Thread[numThreads];

        int cnt = 0;
        int cntCopy = cnt;
        try {
            reader = new BufferedReader(new FileReader(fileName));
            while (cnt < 0) {
                reader.readLine();
                cnt++;
            }
            cntCopy = cnt;
            // cnt = 0;
            String line = reader.readLine();
            while (line != null && cnt < maxLines) {
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
                cnt += 1;
                line = reader.readLine();
            }
        } catch (IOException e) {
            logger.log(Logger.ERROR, e.toString());
        }
        BufferedWriter output = null;
        AtomicInteger counter = new AtomicInteger(cntCopy); // Shared counter
        try {
            File file = new File(ns.getString("output"));
            // File errorFile = new File("error.txt");
            output = new BufferedWriter(new FileWriter(file, false));
            for (int i = 0; i < numThreads; i++) {
                threads[i] = new Thread(new Main(regexes, output, counter, i));
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

    // Exponential

    public static HashMap<String, ArrayList<AttackPattern>> getAllExponentialAttackAutomatons(Automaton a) {
        HashMap<String, ArrayList<AttackPattern>> allAutomatons = new HashMap<>();
        for (State q : a.getStates()) {
            ArrayList<AttackPattern> result = exponentialAttackForPivot(a, q);
            if (result.size() > 0) {
                allAutomatons.put(String.valueOf(AutomatonHelper.getStateNumberFromState(q)), result);
            }
        }
        return allAutomatons;
    }

    private static ArrayList<AttackPattern> exponentialAttackForPivot(Automaton a, State q) {
        ArrayList<AttackPattern> automatonsFromQ = new ArrayList<>();
        Set<Transition> transitions = q.getTransitions();
        List<Transition> transitionList = new ArrayList<>(transitions);
        if (transitions.size() < 2) {
            return automatonsFromQ;
        }
        for (int i = 0; i < transitionList.size(); i++) {
            Transition t1 = transitionList.get(i);
            for (int j = i + 1; j < transitionList.size(); j++) {
                Transition t2 = transitionList.get(j);
                State d1 = t1.getDest();
                State d2 = t2.getDest();
                if (!t1.equals(t2) && !d1.equals(d2) && AutomatonHelper.isOverlappingLabel(t1, t2)) {
                    // logger.log(Logger.DEBUG, t1.toString() + " " + t2.toString());
                    // logger.log(Logger.DEBUG, "Inside " + q.toString());
                    // logger.log(Logger.DEBUG, "T1 " + t1.getMin() + " " + t1.getMax() + " " +
                    // t1.getDest());
                    // logger.log(Logger.DEBUG, "T2 " + t2.getMin() + " " + t2.getMax()+ " " +
                    // t2.getDest());
                    Automaton result = exponentialGetAttackAutomaton(a, q, t1, t2);
                    if (result != null && result.getShortestExample(true) != null) {
                        // logger.log(Logger.DEBUG, "Added: "+ result.getShortestExample(true));
                        Automaton prefix = AutomatonHelper.findPrefix(a, q);
                        Automaton suffix = AutomatonHelper.findSuffix(a, q);
                        automatonsFromQ.add(new AttackPattern(prefix, result, suffix));
                    }
                }
            }
        }
        return automatonsFromQ;

    }

    private static Automaton exponentialGetAttackAutomaton(Automaton a, State q, Transition t1, Transition t2) {
        Automaton a1 = AutomatonHelper.loopBack(a, q, t1);
        if (a1.getShortestExample(true) == null) {
            return null;
        } else {
            // logger.log(Logger.DEBUG, "Reached 1");
            // System.out.println("A1 " + a1.toDot());
            Automaton a2 = AutomatonHelper.loopBack(a, q, t2);
            // logger.log(Logger.DEBUG, "A2 " + a2.toDot());
            if (a2.getShortestExample(true) == null) {
                return null;
            } else {
                // logger.log(Logger.DEBUG, "Reached 2");
                a1.restoreInvariant();
                a2.restoreInvariant();
                Automaton ap = a1.intersection(a2);
                return ap;
            }
        }
    }

    // Super Linear

    public static HashMap<String, ArrayList<AttackPattern>> getAllSuperLinearAttackAutomatons(Automaton a) {
        HashMap<String, ArrayList<AttackPattern>> allAutomatons = new HashMap<>();
        for (State q : a.getStates()) {
            HashMap<String, ArrayList<AttackPattern>> result = superLinearAttackForPivot(a, q);
            if (result.size() > 0) {
                allAutomatons.putAll(result);
            }
        }
        return allAutomatons;
    }

    private static HashMap<String, ArrayList<AttackPattern>> superLinearAttackForPivot(Automaton a, State q) {
        HashMap<String, ArrayList<AttackPattern>> automatonsFromQ = new HashMap<>();
        Set<Transition> transitions = q.getTransitions();
        if (transitions.size() < 2) {
            return automatonsFromQ;
        }
        for (State qPrime : a.getStates()) {
            if (!q.equals(qPrime) && qPrime.getTransitions().size() > 1) {
                ArrayList<AttackPattern> automatonsFromQqPrime = new ArrayList<>();
                for (Transition t1 : transitions) {
                    AttackPattern am = superLinearGetAttackAutomaton(a, q, qPrime, t1);
                    if (am != null && am.pumpable.getShortestExample(true) != null) {
                        automatonsFromQqPrime.add(am);
                    }

                }
                automatonsFromQ.put("" + AutomatonHelper.getStateNumberFromState(q) + "-"
                        + AutomatonHelper.getStateNumberFromState(qPrime), automatonsFromQqPrime);
            }
        }
        return automatonsFromQ;

    }

    private static AttackPattern superLinearGetAttackAutomaton(Automaton a, State q, State qPrime, Transition t1) {
        // System.out.println("---Starting new---");
        // System.out.println("A: " + a.toDot());
        // System.out.println("Q: "+q);
        // System.out.println("QPrime: "+qPrime);
        Automaton subAutomaton = AutomatonHelper.cloneWithDifferentStates(a, q, Collections.singleton(qPrime), t1);
        // System.out.println("Subatomaton: " + subAutomaton.toDot());
        
        // System.out.println(q.toString() + qPrime.toString() + subAutomaton.toDot());
        if (subAutomaton.getInitialState().getTransitions().isEmpty()
                || subAutomaton.getShortestExample(true) == null) {
            return null;
        } else {
            subAutomaton.restoreInvariant();
            Automaton a1 = AutomatonHelper.loopBack(a, q, t1);
            // System.out.println(a1.toDot());
            if (a1.getShortestExample(true) == null) {
                return null;
            } else {
                // System.out.println(q);
                // System.out.println(t1);
                // System.out.println(qPrime);
                // System.out.println(a1.toDot());
                a1.restoreInvariant();
                // System.out.println(a1.toDot());
                Automaton a3 = AutomatonHelper.anyLoopBack(a, qPrime);
                if (a3.getShortestExample(true) == null) {
                    return null;
                } else {
                    a3.restoreInvariant();
                    Automaton a12 = a1.intersection(subAutomaton);
                    // System.out.println(a1.toDot());
                    // System.out.println(subAutomaton.toDot());
                    // System.out.println(a12.toDot());
                    if (a12.getShortestExample(true) == null) {
                        return null;
                    } else {
                        Automaton a123 = a12.intersection(a3);
                        if (a123.getShortestExample(true) == null) {
                            return null;
                        } else {
                            Automaton prefix = AutomatonHelper.findPrefix(a, q);
                            Automaton suffix = AutomatonHelper.findSuffix(a, qPrime);
                            return new AttackPattern(prefix, a123, suffix);
                        }
                    }
                }
            }
        }
    }

    public Main(ArrayList<String> regexes, BufferedWriter writer, AtomicInteger counter, int threadNumber) {
        this.regexes = regexes;
        this.writer = writer;
        this.counter = counter;
        // this.threadNumber = threadNumber;
    }

    public static JsonObject runRex(Automaton x, JsonObject entry) {
        try {
            HashMap<String, ArrayList<AttackPattern>> exp = getAllExponentialAttackAutomatons(x);
            logger.log(Logger.DEBUG, "----------Exponential-----------");
            logger.log(Logger.INFO,
                    "Found exponential pumpable string for : " + exp.size());
            JsonObject expObject = new JsonObject();
            for (Map.Entry<String, ArrayList<AttackPattern>> arr : exp.entrySet()) {
                if (arr.getValue().size() > 0) {
                    JsonObject expObject2 = new JsonObject();
                    for (AttackPattern i : arr.getValue()) {
                        String example = i.pumpable.getShortestExample(true);
                        expObject2.add(example, i.toJsonObject(example));
                        // logger.log(Logger.DEBUG, "Example: " + example);
                    }
                    expObject.add(arr.getKey(), expObject2);
                }
            }

            if(exp.size() > 8){
                throw new IllegalArgumentException("Too high vulnerability");
            }

            HashMap<String, ArrayList<AttackPattern>> sup = getAllSuperLinearAttackAutomatons(x);

            JsonObject supObject = new JsonObject();
            for (Map.Entry<String, ArrayList<AttackPattern>> arr : sup.entrySet()) {
                if (arr.getValue().size() > 0) {
                    JsonObject supObject2 = new JsonObject();
                    for (AttackPattern i : arr.getValue()) {
                        String example = i.pumpable.getShortestExample(true);
                        // System.out.println(i.pumpable.to);
                        supObject2.add(example, i.toJsonObject(example));
                        // logger.log(Logger.DEBUG, "Example: " + example);
                    }
                    supObject.add(arr.getKey(), supObject2);
                }

            }
            logger.log(Logger.DEBUG, "----------Superlinear-----------");
            logger.log(Logger.INFO,
                    "Found superlinear pumpable string for: " +
                            supObject.size());
            entry.addProperty("dot_notation", x.toDot());
            entry.add("exponential", expObject);
            entry.add("super_linear", supObject);
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
            entry.add("exponential", null);
            entry.add("super_linear", null);
            entry.addProperty("error", e.getMessage());
        }
        return entry;
    }

    @Override
    public void run() {
        Gson gson = new GsonBuilder().disableHtmlEscaping().create();
        RegexHelper regexpHelper = new RegexHelper(logger);
        int line = counter.getAndIncrement();
        System.out.println(regexes);
        while (line < regexes.size()) {

            String s = regexes.get(line);
            try {
                JsonObject entry = new JsonObject();
                entry.addProperty("count", line);
                entry.addProperty("pattern", s);
                entry.addProperty("formatted_pattern", s.replace("\\\\", "\\"));
                try {
                    RegExp r = regexpHelper.parse(s);
                    Automaton x = r.toAutomaton(false);
                    Pattern p = Pattern.compile(s.replace("\\\\", "\\"));
                    String shortEx = x.getShortestExample(true);
                    Matcher m = p.matcher(shortEx);
                    boolean matchFound = m.find();
                    // System.out.println("TESTER: " + p.toString() +" "+ shortEx);
                    if (!matchFound) {
                        throw new IllegalArgumentException("Regex feature not handled");
                    }
                    if (x.getNumberOfStates() > 100) {
                        throw new IllegalArgumentException("Too many states");
                    }
                    logger.log(Logger.DEBUG, String.valueOf(line));
                    logger.log(Logger.DEBUG, x.toString());
                    logger.log(Logger.DEBUG, x.toDot());
                    
                    entry = Main.runRex(x, entry);
                } catch (Exception e) {
                    e.printStackTrace();
                    entry.add("exponential", null);
                    entry.add("super_linear", null);
                    entry.addProperty("error", e.getMessage());
                } finally {
                    
                    synchronized (writer) {
                        writer.write(gson.toJson(entry));// .replace("\\\\", "\\"));
                    }
                    writer.newLine();
                    line = counter.getAndIncrement();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

}