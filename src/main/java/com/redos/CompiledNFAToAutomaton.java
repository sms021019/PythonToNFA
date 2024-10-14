package com.redos;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import dk.brics.automaton.Automaton;
import dk.brics.automaton.State;
import dk.brics.automaton.Transition;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;

public class CompiledNFAToAutomaton implements Runnable {

    private List<String> regexes;
    private BufferedWriter writer;
    private AtomicInteger counter;
    // private int threadNumber;

    public CompiledNFAToAutomaton(ArrayList<String> regexes, BufferedWriter writer, AtomicInteger counter,
                                  int threadNumber) {
        this.regexes = regexes;
        this.writer = writer;
        this.counter = counter;
        // this.threadNumber = threadNumber;

    }

    public static void main(String[] args) {
        ArgumentParser parser = ArgumentParsers.newFor("CompiledNFAToAutomaton").build()
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

        parser.addArgument("-oa")
                .help("Use only ascii chars")
                .required(false)
                .type(Boolean.class)
                .setDefault(false);

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
        boolean onlyASCII = ns.getBoolean("oa");
        Transition.setOnlyASCII(onlyASCII);
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
                threads[i] = new Thread(new CompiledNFAToAutomaton(regexes, output, counter, i));
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
        regex = regex.replace("\\\\", "\\");
        if (!regex.startsWith("^")) {
            regex = "^" + regex;
        }
        if (!regex.endsWith("$")) {
            regex = regex + "$";
        }
        entry.addProperty("updated_pattern", regex);
        String[] command = { "./re", "none", "none", regex, "abc", "singlerlek", "1" };
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        System.out.println(String.join(" ", processBuilder.command().toArray(new String[0])));
        // processBuilder.directory(new File("/Users/aman/sbu/spring2024/cse523/memoized-regex-engine/src-simple/"));
//        processBuilder.directory(new File("/home/amagrawal/memoized-regex-engine/src-simple/"));
        processBuilder.directory(new File("/Users/my folder/ReDos/memoized-regex-engine/src-simple"));
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
            entry.addProperty("re1_output", output);
            if (exitCode != 0) {
                entry.addProperty("error", exitCode);
                return entry;
            }
            // System.out.println(output);
            return processInstructions(output, entry);
            // System.out.println("Exited with code: " + exitCode);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private static JsonObject processInstructions(String instructions, JsonObject entry) {
        String savePattern = "\\s*(\\d+)\\. save (\\d+) \\(.*\\)";
        String inlinePattern = "\\s*(\\d+)\\. inlineZWA ([\\$\\^]) \\(.*\\)";
        String anyPattern = "\\s*(\\d+)\\. any \\(.*\\)";
        String charPattern = "\\s*(\\d+)\\. char (\\d+) \\(.*\\)";
        String splitPattern = "\\s*(\\d+)\\. split (\\d+), (\\d+) \\(.*\\)";
        String jmpPattern = "\\s*(\\d+)\\. jmp (\\d+) \\(.*\\)";
        String matchPattern = "\\s*(\\d+)\\. match \\(.*\\)";
        String charClassPattern = "\\s*(\\d+)\\. charClass ((?:\\^?(\\d+)-(\\d+) )+) \\(.*\\)";
        String splitManyPattern = "\\s*(\\d+)\\. splitmany ((?:(\\d+),? )+) \\(.*\\)";
        String[] lines = instructions.split("\n");
        Automaton a = new Automaton();
        State[] states = new State[lines.length];
        for (int i = 0; i < lines.length; i++) {
            states[i] = new State();
        }
        a.setInitialState(states[0]);
        a.setDeterministic(false);

        for (int i = 0; i < lines.length; i++) {
            Matcher matcher;
            String line = lines[i];
            State from = states[i];
            // System.out.println(line);
            if ((matcher = Pattern.compile(savePattern).matcher(line)).matches()) {
                from.addTransition(new Transition(states[i + 1]));

            } else if ((matcher = Pattern.compile(inlinePattern).matcher(line)).matches()) {
                from.addTransition(new Transition(states[i + 1]));

            } else if ((matcher = Pattern.compile(charPattern).matcher(line)).matches()) {
                char c = (char) Integer.parseInt(matcher.group(2));
                // System.out.println(c);
                from.addTransition(new Transition(c, states[i + 1]));

            } else if ((matcher = Pattern.compile(anyPattern).matcher(line)).matches()) {
                from.addTransition(new Transition(Transition.MIN_VALUE, Transition.MAX_VALUE, states[i + 1]));

            } else if ((matcher = Pattern.compile(splitPattern).matcher(line)).matches()) {
                int value1 = Integer.parseInt(matcher.group(2));
                int value2 = Integer.parseInt(matcher.group(3));
                from.addTransition(new Transition(states[value1]));
                from.addTransition(new Transition(states[value2]));

            } else if ((matcher = Pattern.compile(jmpPattern).matcher(line)).matches()) {
                int value = Integer.parseInt(matcher.group(2));
                from.addTransition(new Transition(states[value]));

            } else if ((matcher = Pattern.compile(matchPattern).matcher(line)).matches()) {
                from.setAccept(true);

            } else if ((matcher = Pattern.compile(charClassPattern).matcher(line)).matches()) {
                String charClasses = matcher.group(2);
                Automaton union = Automaton.makeEmpty();
                boolean negation = false;
                // System.out.println(charClasses);
                for (String c : charClasses.split(" ")) {
                    // char value1;
                    // char value2;
                    if (c.charAt(0) == '^') {
                        negation = true;
                        c = c.substring(1);
                        // value1 = (char) (Integer.parseInt(c.substring(1).split("-")[0]));
                        // value2 = (char) (Integer.parseInt(c.substring(1).split("-")[1]));
                        // System.out.println("Char range: ^" + value1 + " " + value2);
                    }
                    // else {
                    char value1 = (char) (Integer.parseInt(c.split("-")[0]));
                    char value2 = (char) (Integer.parseInt(c.split("-")[1]));
                    // System.out.println("Char range: "+negation + value1 + " " + value2);
                    // }
                    union = union.union(Automaton.makeCharRange(value1, value2));
                }
                // System.out.println("Union: "+ union.toDot());
                if (negation) {
                    union = union.complement();
                }
                // System.out.println(union.toDot());
                for (Transition q : union.getInitialState().getTransitions()) {
                    if (q.getDest().isAccept()) {
                        from.addTransition(new Transition(q.getMin(), q.getMax(), states[i + 1]));
                    }
                }

            } else if ((matcher = Pattern.compile(splitManyPattern).matcher(line)).matches()) {
                String split = matcher.group(2).trim();
                for (String c : split.split(", ")) {
                    from.addTransition(new Transition(states[Integer.parseInt(c)]));
                }
            } else {
                // Handle unrecognized line
                System.out.println("Unrecognized line: " + line);
                entry.addProperty("error", "Unrecognized line: " + line);
                return entry;

            }
        }
        System.out.println(a.toDot());
        System.out.println(a.getShortestExample(true) + " " + a.getShortestExample(true).length());
        JsonObject j = Main.runRex(a, entry);
        // System.out.println(j);
        return j;

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
            entry = CompiledNFAToAutomaton.runEngine(s, entry);
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