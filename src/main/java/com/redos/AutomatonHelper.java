package com.redos;

import java.util.Collections;
import java.util.HashMap;
import java.util.Set;

import dk.brics.automaton.Automaton;
import dk.brics.automaton.State;
import dk.brics.automaton.Transition;

class AutomatonHelper {
    public static Automaton cloneWithDifferentStates(Automaton oldAutomaton, State initial, Set<State> accepting,
            Transition exclude) {
        Automaton a = Automaton.makeEmpty();
        Set<State> states = oldAutomaton.getStates();
        HashMap<State, State> oldToNewStates = new HashMap<>();
        for (State s : states) {
            oldToNewStates.put(s, new State());
        }
        for (State s : states) {
            State newState = oldToNewStates.get(s);

            newState.setAccept(accepting.contains(s));
            if (s == initial) {
                a.setInitialState(newState);
            }
            for (Transition t : s.getTransitions()) {
                if (!t.equals(exclude)) {
                    Transition newTransition = t.isEpsilon() ? new Transition(oldToNewStates.get(t.getDest()))
                            : new Transition(t.getMin(), t.getMax(), oldToNewStates.get(t.getDest()));
                    newState.addTransition(newTransition);
                }

            }
        }
        a.setDeterministic(false);
        return a;
    }

    public static Automaton anyLoopBack(Automaton a, State s) {
        Automaton result = cloneWithDifferentStates(a, s, Collections.singleton(s), null);
        State init = new State();
        for (Transition t : result.getInitialState().getTransitions()) {
            init.addTransition(
                    t.isEpsilon() ? new Transition(t.getDest()) : new Transition(t.getMin(), t.getMax(), t.getDest()));
        }
        result.getStates().add(init);
        result.setInitialState(init);
        return result;
    }

    public static Automaton loopBack(Automaton a, State dest, Transition out) {
        Automaton la = cloneWithDifferentStates(a, out.getDest(), Collections.singleton(dest), null);
        State init = new State();
        init.addTransition(out.isEpsilon() ? new Transition(la.getInitialState())
                : new Transition(out.getMin(), out.getMax(), la.getInitialState()));
        la.getStates().add(init);
        la.setInitialState(init);
        return la;
    }

    public static boolean isOverlappingLabel(Transition t1, Transition t2) {
        return t1.isEpsilon() || t2.isEpsilon()
                || Math.max(t1.getMin(), t2.getMin()) <= Math.min(t1.getMax(), t2.getMax());
    }

    public static int getStateNumberFromState(State s) {
        return s.getStateNumber();
        // return Integer.parseInt(s.toString().substring(6, s.toString().indexOf(" ",
        // 6)));
    }

    public static Automaton findPrefix(Automaton a, State c) {
        Automaton ap = cloneWithDifferentStates(a, a.getInitialState(), Collections.singleton(c), null);
        return ap;
    }

    public static Automaton findSuffix(Automaton a, State c) {
        if (!c.isAccept()) {
            return Automaton.makeEmptyString();
        }
        Automaton as = cloneWithDifferentStates(a, c, a.getAcceptStates(), null);
        State s3 = new State();
        s3.setAccept(as.getInitialState().isAccept());

        for (Transition t : as.getInitialState().getTransitions()) {
            s3.addTransition(
                    t.isEpsilon() ? new Transition(t.getDest()) : new Transition(t.getMin(), t.getMax(), t.getDest()));
        }

        as.getStates().add(s3);
        as.setInitialState(s3);
        as.restoreInvariant();

        // Automaton asl = BasicAutomata.makeAnyChar();

        // for (Transition t: s3.getTransitions()) {
        // if (t.getDest().isAccept()) {
        // asl = asl.minus(BasicAutomata.makeCharRange(t.getMin(), t.getMax()));
        // }
        // }

        // z = asl.getShortestExample(true);
        // if (z != null) {
        // return z;
        // }

        return as.complement();
    }
}