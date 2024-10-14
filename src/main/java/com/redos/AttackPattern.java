package com.redos;

import com.google.gson.JsonObject;

import dk.brics.automaton.Automaton;

public class AttackPattern {
   public final Automaton prefix;
   public final Automaton pumpable;
   public final Automaton suffix;
   private String representation;

   public AttackPattern(Automaton prefix, Automaton pumpable, Automaton suffix) {
      this.prefix = prefix;
      this.pumpable = pumpable;
      this.suffix = suffix;
   }

   @Override
   public String toString() {
      if (this.representation == null) {
         this.representation = prefix.getShortestExample(true) + "(" + pumpable.getShortestExample(true) + ")"
               + suffix.getShortestExample(true);
      }
      return this.representation;
   }

   public JsonObject toJsonObject(String examplePump) {
      JsonObject j = new JsonObject();
      // String examplePump = this.pumpable.getShortestExample(true);
      j.addProperty("prefix", this.prefix.getShortestExample(true));
      j.addProperty("suffix", this.suffix.getShortestExample(true));
      j.addProperty("pump", examplePump);
      j.addProperty("pump_length", examplePump.length());
      j.addProperty("c_pump_length", CStringLength.getStringLength(examplePump));
      return j;
   }

}
