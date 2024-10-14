This is the NFA generator.
This project is proceed from the REDoS research which held in Stony Brook University with Prof. Dongyoon Lee.
I participated in PythonToNFA file to extract the NFA from Python.
The rest of code was written by Aman Agrawal from also Stony Brook University.

Main flow of the idea is extracting compiled code from CPython source code and parse them into states, then go over them with the Brics library to generate the according NFA.
Resulted NFA graph is then used as an input to the Aman's tool which detects the super-linear or exponential vulnerability for the given NFA. This tool is implement based on
the study of Russ Cox.

Then with the resulted json file I compare the result with that of RE1 result to see which difference in compilation resulted in different vulnerability for the same regex.

How to run:
(You need CPython in the same directory of the root of this project)
1. Edit pom.xml file
     where the line is <mainClass>com.redos.RubyToNFA</mainClass>
     change to <mainClass>com.redos.PythonToNFA</mainClass>
2. On the terminal,
   mvn clean package
3. On the terminal,
   java -jar target/rex-1.0-SNAPSHOT-jar-with-dependencies.jar -i regex.txt -o output.json

You can paste the NFA graph DOT result into graphviz Online to visualize your regex.
