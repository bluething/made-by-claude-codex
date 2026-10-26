package io.github.bluething.atm;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class Main {
    public static void main(String[] args) throws IOException {
        Atm atm = new Atm(new Bank(), System.out);
        atm.run(new BufferedReader(new InputStreamReader(System.in)));
    }
}
